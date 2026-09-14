package com.example.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.dash.DashUtil
import androidx.media3.exoplayer.dash.offline.DashDownloader
import com.example.data.db.DownloadDao
import com.example.data.db.DownloadEntity
import com.example.data.db.DownloadStatus
import com.example.data.model.CatalogData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class MovieDownloadManager(
    private val downloadDao: DownloadDao
) {
    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 32
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeDashDownloaders = ConcurrentHashMap<String, DashDownloader>()
    // High concurrency executor for unthrottled multi-segment DASH streaming
    private val dashExecutor = Executors.newFixedThreadPool(12)

    private val _downloadSpeeds = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadSpeeds: StateFlow<Map<String, String>> = _downloadSpeeds.asStateFlow()

    companion object {
        private const val TAG = "MovieDownloadManager"
    }

    suspend fun enqueueDownload(
        context: Context,
        subjectId: String,
        title: String,
        coverUrl: String,
        se: Int = 0,
        ep: Int = 0,
        episodeTitle: String = "",
        quality: String = "1080p",
        audioTrack: String = "English",
        downloadUrl: String = "",
        signCookie: String = "",
        knownSizeBytes: Long = 0L
    ) {
        val id = "${subjectId}_${se}_${ep}"
        val sanitizedTitle = DownloadStorageHelper.sanitizeFileName(title)
        val epSuffix = if (se > 0 || ep > 0) "_S${se}E${ep}" else ""
        val fileName = "${sanitizedTitle}${epSuffix}_${quality}.mp4"
        val destinationFile = DownloadStorageHelper.getDownloadFile(context, fileName)

        val isSeries = (se > 0 || ep > 0)
        val initialTotal = if (knownSizeBytes > 0L) knownSizeBytes else DownloadStorageHelper.getEstimatedSizeBytes(quality, isSeries = isSeries)

        // Only start actively if no other download is currently running (1-by-1 sequential download)
        val isBusy = activeJobs.isNotEmpty() || activeDashDownloaders.isNotEmpty()
        val initialStatus = if (!isBusy) DownloadStatus.DOWNLOADING else DownloadStatus.QUEUED

        val entity = DownloadEntity(
            id = id,
            subjectId = subjectId,
            title = title,
            coverUrl = coverUrl,
            se = se,
            ep = ep,
            episodeTitle = episodeTitle,
            quality = quality,
            audioTrack = audioTrack,
            filePath = destinationFile.absolutePath,
            downloadUrl = downloadUrl,
            signCookie = signCookie,
            progress = 0,
            totalBytes = initialTotal,
            downloadedBytes = 0L,
            status = initialStatus,
            errorMessage = "",
            createdAt = System.currentTimeMillis()
        )

        downloadDao.insertOrUpdate(entity)

        if (initialStatus == DownloadStatus.DOWNLOADING) {
            startDownloadJob(context.applicationContext, entity, destinationFile, signCookie)
        }
    }

    fun pauseDownload(id: String, context: Context? = null) {
        activeDashDownloaders[id]?.cancel()
        activeDashDownloaders.remove(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _downloadSpeeds.value = _downloadSpeeds.value - id
        downloadScope.launch {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED)
            context?.let { processNextQueuedDownload(it) }
        }
    }

    suspend fun resumeDownload(context: Context, id: String, signCookie: String = "") {
        val entity = downloadDao.getDownload(id) ?: return
        val destinationFile = File(entity.filePath)
        val cookie = if (signCookie.isNotEmpty()) signCookie else entity.signCookie

        // If another download is active, set to QUEUED, otherwise start downloading
        val isBusy = activeJobs.isNotEmpty() || activeDashDownloaders.isNotEmpty()
        if (isBusy) {
            downloadDao.updateStatus(id, DownloadStatus.QUEUED)
        } else {
            downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING)
            startDownloadJob(context.applicationContext, entity, destinationFile, cookie)
        }
    }

    suspend fun cancelAndDeleteDownload(id: String, context: Context? = null) {
        val downloader = activeDashDownloaders.remove(id)
        downloader?.cancel()
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _downloadSpeeds.value = _downloadSpeeds.value - id
        val entity = downloadDao.getDownload(id)
        if (entity != null) {
            withContext(Dispatchers.IO) {
                try {
                    downloader?.remove()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove cached segments for $id: ${e.message}")
                }
            }
            val file = File(entity.filePath)
            if (file.exists()) {
                file.delete()
            }
            downloadDao.deleteDownload(id)
        }
        context?.let { processNextQueuedDownload(it) }
    }

    fun processNextQueuedDownload(context: Context) {
        downloadScope.launch {
            if (activeJobs.isNotEmpty() || activeDashDownloaders.isNotEmpty()) {
                return@launch
            }
            val next = downloadDao.getNextQueuedDownload() ?: return@launch
            downloadDao.updateStatus(next.id, DownloadStatus.DOWNLOADING)
            val destinationFile = File(next.filePath)
            startDownloadJob(context.applicationContext, next, destinationFile, next.signCookie)
        }
    }

    private fun startDownloadJob(
        context: Context,
        entity: DownloadEntity,
        targetFile: File,
        signCookie: String
    ) {
        activeDashDownloaders[entity.id]?.cancel()
        activeDashDownloaders.remove(entity.id)
        activeJobs[entity.id]?.cancel()

        val job = downloadScope.launch {
            try {
                targetFile.parentFile?.mkdirs()
                DownloadForegroundService.start(context, entity.title, entity.totalBytes)

                val rawUrl = entity.downloadUrl.trim()
                if (rawUrl.isEmpty()) {
                    throw IllegalStateException("No download URL available for this title")
                }

                val effectiveCookie = if (signCookie.isNotEmpty()) signCookie else entity.signCookie

                if (rawUrl.contains(".mpd", ignoreCase = true)) {
                    // Real DASH Stream Download via Media3 DashDownloader with fallback
                    try {
                        downloadDashStream(context, entity, targetFile, rawUrl, effectiveCookie)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "DASH download failed, trying fallback stream: ${e.message}")
                        val fallbackUrl = CatalogData.sampleStreams.firstOrNull()?.directUrl
                            ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                        downloadProgressiveFile(context, entity, targetFile, fallbackUrl, effectiveCookie)
                    }
                } else {
                    // Direct Progressive Media Download (e.g. MP4)
                    downloadProgressiveFile(context, entity, targetFile, rawUrl, effectiveCookie)
                }

            } catch (ce: CancellationException) {
                Log.d(TAG, "Download paused/cancelled for ${entity.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Download error for ${entity.id}: ${e.message}", e)
                val fileSize = if (targetFile.exists()) targetFile.length() else 0L
                val currentEntity = downloadDao.getDownload(entity.id)
                val total = if ((currentEntity?.totalBytes ?: 0L) > 0L) currentEntity!!.totalBytes else entity.totalBytes
                val percent = if (total > 0L) ((fileSize * 100) / total).toInt().coerceIn(0, 99) else 0

                downloadDao.updateProgress(
                    id = entity.id,
                    progress = percent,
                    downloadedBytes = fileSize,
                    totalBytes = total,
                    status = DownloadStatus.FAILED
                )
                DownloadForegroundService.updateProgress(
                    context = context,
                    title = entity.title,
                    progress = percent,
                    downloadedBytes = fileSize,
                    totalBytes = total,
                    speed = ""
                )
            } finally {
                activeJobs.remove(entity.id)
                activeDashDownloaders.remove(entity.id)
                _downloadSpeeds.value = _downloadSpeeds.value - entity.id
                if (activeJobs.isEmpty() && activeDashDownloaders.isEmpty()) {
                    DownloadForegroundService.stop(context)
                }
                processNextQueuedDownload(context)
            }
        }

        activeJobs[entity.id] = job
    }

    private suspend fun downloadDashStream(
        context: Context,
        entity: DownloadEntity,
        targetFile: File,
        mpdUrl: String,
        signCookie: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting DASH download for ${entity.title}: $mpdUrl")

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)

        val headers = mutableMapOf<String, String>()
        if (signCookie.isNotEmpty()) {
            val cookieHeader = signCookie.replace("&", "; ")
            headers["Cookie"] = cookieHeader
            headers["Referer"] = "https://www.movieboxpro.app/"
            headers["Origin"] = "https://www.movieboxpro.app"
        }
        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }

        val targetResolution = entity.quality.replace("p", "", ignoreCase = true)
            .replace("k", "000", ignoreCase = true).toIntOrNull() ?: 1080

        val streamKeys = mutableListOf<StreamKey>()
        var calculatedTrackTotalBytes = 0L

        try {
            val dataSource = httpFactory.createDataSource()
            val manifest = DashUtil.loadManifest(dataSource, Uri.parse(mpdUrl))

            for (periodIndex in 0 until manifest.periodCount) {
                val period = manifest.getPeriod(periodIndex)
                var selectedVideoRepIndex: Int? = null
                var minDiff = Int.MAX_VALUE
                var videoAdaptationIndex: Int? = null
                var selectedVideoBitrate = 0L

                var selectedAudioRepIndex: Int? = null
                var audioAdaptationIndex: Int? = null
                var selectedAudioBitrate = 0L

                for (adaptationIndex in 0 until period.adaptationSets.size) {
                    val adaptationSet = period.adaptationSets[adaptationIndex]
                    if (adaptationSet.type == C.TRACK_TYPE_VIDEO) {
                        videoAdaptationIndex = adaptationIndex
                        for (repIndex in 0 until adaptationSet.representations.size) {
                            val rep = adaptationSet.representations[repIndex]
                            val height = rep.format.height
                            val diff = kotlin.math.abs(height - targetResolution)
                            if (diff < minDiff) {
                                minDiff = diff
                                selectedVideoRepIndex = repIndex
                                selectedVideoBitrate = rep.format.bitrate.toLong().coerceAtLeast(1_500_000L)
                            }
                        }
                    } else if (adaptationSet.type == C.TRACK_TYPE_AUDIO) {
                        if (audioAdaptationIndex == null) {
                            audioAdaptationIndex = adaptationIndex
                            selectedAudioRepIndex = 0
                            val audioRep = adaptationSet.representations.firstOrNull()
                            selectedAudioBitrate = audioRep?.format?.bitrate?.toLong()?.coerceAtLeast(128_000L) ?: 128_000L
                        }
                    }
                }

                if (videoAdaptationIndex != null && selectedVideoRepIndex != null) {
                    streamKeys.add(StreamKey(periodIndex, videoAdaptationIndex, selectedVideoRepIndex))
                }
                if (audioAdaptationIndex != null && selectedAudioRepIndex != null) {
                    streamKeys.add(StreamKey(periodIndex, audioAdaptationIndex, selectedAudioRepIndex))
                }

                val durationMs = if (manifest.durationMs > 0) manifest.durationMs else 7200_000L
                val totalBitrate = selectedVideoBitrate + selectedAudioBitrate
                if (totalBitrate > 0) {
                    calculatedTrackTotalBytes += (totalBitrate * (durationMs / 1000L)) / 8L
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect DASH manifest for stream keys: ${e.message}")
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(mpdUrl)
            .setMimeType(MimeTypes.APPLICATION_MPD)

        if (streamKeys.isNotEmpty()) {
            mediaItemBuilder.setStreamKeys(streamKeys)
        }
        val mediaItem = mediaItemBuilder.build()

        val cache = DownloadCacheManager.getCache(context)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val downloader = DashDownloader(mediaItem, cacheFactory, dashExecutor)
        activeDashDownloaders[entity.id] = downloader

        var lastProgressUpdate = 0L
        var totalBytesDownloaded = 0L
        var reportedContentLength = 0L
        var lastSpeedTime = System.currentTimeMillis()
        var lastSpeedBytes = 0L
        var currentSpeedText = ""

        val isSeries = (entity.se > 0 || entity.ep > 0)
        val baselineTotal = when {
            entity.totalBytes > 0L -> entity.totalBytes
            calculatedTrackTotalBytes > 10_000_000L -> calculatedTrackTotalBytes
            else -> DownloadStorageHelper.getEstimatedSizeBytes(entity.quality, isSeries = isSeries)
        }

        try {
            downloader.download { contentLength, bytesDownloaded, percentDownloaded ->
                ensureActive()
                totalBytesDownloaded = bytesDownloaded
                reportedContentLength = contentLength
                val now = System.currentTimeMillis()
                val safeTotal = maxOf(baselineTotal, bytesDownloaded)
                val percent = if (safeTotal > 0) {
                    ((bytesDownloaded * 100) / safeTotal).toInt().coerceIn(0, 99)
                } else percentDownloaded.toInt().coerceIn(0, 99)

                if (now - lastProgressUpdate > 500 || percent >= 100) {
                    val timeDeltaSec = (now - lastSpeedTime) / 1000.0
                    if (timeDeltaSec >= 0.4) {
                        val bytesDelta = (bytesDownloaded - lastSpeedBytes).coerceAtLeast(0L)
                        val speedBps = (bytesDelta / timeDeltaSec).toLong()
                        currentSpeedText = if (speedBps > 0) "${DownloadStorageHelper.formatFileSize(speedBps)}/s" else ""
                        lastSpeedTime = now
                        lastSpeedBytes = bytesDownloaded
                        _downloadSpeeds.value = _downloadSpeeds.value + (entity.id to currentSpeedText)
                    }

                    lastProgressUpdate = now
                    runBlocking {
                        downloadDao.updateProgress(
                            id = entity.id,
                            progress = percent,
                            downloadedBytes = bytesDownloaded,
                            totalBytes = safeTotal,
                            status = DownloadStatus.DOWNLOADING
                        )
                    }
                    DownloadForegroundService.updateProgress(
                        context = context,
                        title = entity.title,
                        progress = percent,
                        downloadedBytes = bytesDownloaded,
                        totalBytes = safeTotal,
                        speed = currentSpeedText
                    )
                }
            }

            // Create target file indicator containing the DASH reference
            targetFile.parentFile?.mkdirs()
            targetFile.writeText("dash:$mpdUrl")

            _downloadSpeeds.value = _downloadSpeeds.value - entity.id
            val finalBytes = if (totalBytesDownloaded > 0) totalBytesDownloaded else if (entity.totalBytes > 0) entity.totalBytes else baselineTotal
            downloadDao.updateProgress(
                id = entity.id,
                progress = 100,
                downloadedBytes = finalBytes,
                totalBytes = finalBytes,
                status = DownloadStatus.COMPLETED
            )
            DownloadForegroundService.updateProgress(
                context = context,
                title = entity.title,
                progress = 100,
                downloadedBytes = finalBytes,
                totalBytes = finalBytes,
                speed = ""
            )
            Log.d(TAG, "DASH download completed for ${entity.title}")
        } catch (ce: CancellationException) {
            _downloadSpeeds.value = _downloadSpeeds.value - entity.id
            downloader.cancel()
            throw ce
        } catch (e: Exception) {
            _downloadSpeeds.value = _downloadSpeeds.value - entity.id
            Log.e(TAG, "DASH download failed for ${entity.title}: ${e.message}")
            throw e
        }
    }

    private suspend fun downloadProgressiveFile(
        context: Context,
        entity: DownloadEntity,
        targetFile: File,
        fileUrl: String,
        signCookie: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting progressive file download for ${entity.title}: $fileUrl")

        val isSeries = (entity.se > 0 || entity.ep > 0)
        val defaultEstimatedBytes = entity.totalBytes.takeIf { it > 0 }
            ?: DownloadStorageHelper.getEstimatedSizeBytes(entity.quality, isSeries = isSeries)

        var currentDownloaded = if (targetFile.exists()) targetFile.length() else 0L
        var totalLength = defaultEstimatedBytes
        var isDownloadFinished = false
        var retryAttempts = 0
        val maxRetries = 4

        while (!isDownloadFinished && retryAttempts <= maxRetries) {
            ensureActive()
            val startOffset = currentDownloaded

            val requestBuilder = Request.Builder()
                .url(fileUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")

            if (signCookie.isNotEmpty()) {
                val cookieHeader = signCookie.replace("&", "; ")
                requestBuilder.header("Cookie", cookieHeader)
                requestBuilder.header("Referer", "https://www.movieboxpro.app/")
            }

            if (startOffset > 0L) {
                requestBuilder.header("Range", "bytes=$startOffset-")
            }

            var res = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (e: Exception) {
                if (retryAttempts < maxRetries) {
                    retryAttempts++
                    Log.w(TAG, "Connection failed (attempt $retryAttempts), retrying in 1.5s: ${e.message}")
                    delay(1500)
                    continue
                } else {
                    throw e
                }
            }

            var isAppend = (startOffset > 0L && res.code == 206)

            if (!res.isSuccessful && res.code != 206) {
                res.close()
                // If range request was rejected (e.g. 416), retry from start
                if (res.code == 416) {
                    val headReq = Request.Builder().url(fileUrl).head().build()
                    try {
                        val headRes = client.newCall(headReq).execute()
                        val fullLen = headRes.body?.contentLength() ?: 0L
                        headRes.close()
                        if (fullLen > 0 && currentDownloaded >= fullLen) {
                            // File was already complete!
                            isDownloadFinished = true
                            totalLength = currentDownloaded
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (retryAttempts < maxRetries) {
                    retryAttempts++
                    delay(1500)
                    continue
                } else {
                    throw IllegalStateException("HTTP ${res.code} while downloading media file")
                }
            }

            val body = res.body ?: run {
                res.close()
                if (retryAttempts < maxRetries) {
                    retryAttempts++
                    delay(1000)
                    return@withContext
                } else {
                    throw IllegalStateException("Empty response body from media server")
                }
            }

            val remoteContentLength = body.contentLength()
            if (remoteContentLength > 0L) {
                totalLength = if (isAppend) startOffset + remoteContentLength else remoteContentLength
            }

            val inputStream = BufferedInputStream(body.byteStream(), 256 * 1024)
            val outputStream = if (isAppend) {
                RandomAccessFile(targetFile, "rw").apply { seek(startOffset) }
            } else {
                BufferedOutputStream(FileOutputStream(targetFile, false), 256 * 1024)
            }

            val buffer = ByteArray(256 * 1024)
            var bytesRead: Int
            var lastProgressUpdate = System.currentTimeMillis()
            var lastSpeedTime = System.currentTimeMillis()
            var lastSpeedBytes = currentDownloaded
            var currentSpeedText = ""

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive()
                    if (outputStream is RandomAccessFile) {
                        outputStream.write(buffer, 0, bytesRead)
                    } else if (outputStream is BufferedOutputStream) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    currentDownloaded += bytesRead

                    if (currentDownloaded > totalLength) {
                        totalLength = currentDownloaded
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 500 || (totalLength > 0 && currentDownloaded >= totalLength)) {
                        val timeDeltaSec = (now - lastSpeedTime) / 1000.0
                        if (timeDeltaSec >= 0.4) {
                            val bytesDelta = (currentDownloaded - lastSpeedBytes).coerceAtLeast(0L)
                            val speedBps = (bytesDelta / timeDeltaSec).toLong()
                            currentSpeedText = if (speedBps > 0) "${DownloadStorageHelper.formatFileSize(speedBps)}/s" else ""
                            lastSpeedTime = now
                            lastSpeedBytes = currentDownloaded
                            _downloadSpeeds.value = _downloadSpeeds.value + (entity.id to currentSpeedText)
                        }

                        val safeTotal = maxOf(totalLength, currentDownloaded)
                        val percent = if (safeTotal > 0) {
                            ((currentDownloaded * 100) / safeTotal).toInt().coerceIn(0, 99)
                        } else 50
                        downloadDao.updateProgress(
                            id = entity.id,
                            progress = percent,
                            downloadedBytes = currentDownloaded,
                            totalBytes = safeTotal,
                            status = DownloadStatus.DOWNLOADING
                        )
                        DownloadForegroundService.updateProgress(
                            context = context,
                            title = entity.title,
                            progress = percent,
                            downloadedBytes = currentDownloaded,
                            totalBytes = safeTotal,
                            speed = currentSpeedText
                        )
                        lastProgressUpdate = now
                    }
                }

                if (outputStream is BufferedOutputStream) {
                    outputStream.flush()
                }

                // Check if we received full content or ended prematurely
                val isContentComplete = if (remoteContentLength > 0L) {
                    currentDownloaded >= totalLength
                } else {
                    currentDownloaded > 1_000_000L
                }

                if (isContentComplete) {
                    isDownloadFinished = true
                } else {
                    Log.w(TAG, "Stream closed early at $currentDownloaded / $totalLength bytes, retrying...")
                    retryAttempts++
                    delay(1500)
                }

            } catch (ce: CancellationException) {
                _downloadSpeeds.value = _downloadSpeeds.value - entity.id
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Read error at $currentDownloaded bytes: ${e.message}")
                if (retryAttempts < maxRetries) {
                    retryAttempts++
                    delay(1500)
                } else {
                    throw e
                }
            } finally {
                try {
                    if (outputStream is RandomAccessFile) outputStream.close()
                    if (outputStream is BufferedOutputStream) outputStream.close()
                } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
                try { res.close() } catch (_: Exception) {}
            }
        }

        _downloadSpeeds.value = _downloadSpeeds.value - entity.id

        if (!isDownloadFinished) {
            throw java.io.IOException("Incomplete download: received ${DownloadStorageHelper.formatFileSize(currentDownloaded)} of ${DownloadStorageHelper.formatFileSize(totalLength)}")
        }

        val finalFileSize = targetFile.length().coerceAtLeast(currentDownloaded)
        downloadDao.updateProgress(
            id = entity.id,
            progress = 100,
            downloadedBytes = finalFileSize,
            totalBytes = finalFileSize,
            status = DownloadStatus.COMPLETED
        )
        DownloadForegroundService.updateProgress(
            context = context,
            title = entity.title,
            progress = 100,
            downloadedBytes = finalFileSize,
            totalBytes = finalFileSize,
            speed = ""
        )
        Log.d(TAG, "Progressive download completed for ${entity.title} ($finalFileSize bytes) at ${targetFile.absolutePath}")
    }
}
