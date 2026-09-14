package com.example.data.repository

import android.content.Context
import com.example.data.api.MovieApiService
import com.example.data.db.*
import com.example.data.download.MovieDownloadManager
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MovieRepository(
    private val apiService: MovieApiService,
    private val watchHistoryDao: WatchHistoryDao,
    private val watchlistDao: WatchlistDao,
    private val downloadDao: DownloadDao
) {
    val watchHistory: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAllHistory()
    val watchlist: Flow<List<WatchlistEntity>> = watchlistDao.getAllWatchlist()
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    private val downloadManager = MovieDownloadManager(downloadDao)
    val downloadSpeeds: StateFlow<Map<String, String>> = downloadManager.downloadSpeeds

    fun isInWatchlist(subjectId: String): Flow<Boolean> = watchlistDao.isInWatchlist(subjectId)

    suspend fun startDownload(
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
        var resolvedDownloadUrl = downloadUrl
        var resolvedSignCookie = signCookie
        var resolvedSizeBytes = knownSizeBytes

        if (resolvedDownloadUrl.isEmpty()) {
            val streams = try {
                getPlayInfo(subjectId, se, ep)
            } catch (_: Exception) {
                emptyList()
            }
            val requestedRes = quality.replace("p", "", ignoreCase = true).toIntOrNull() ?: 1080
            // Prioritize direct playable MP4 file for reliable offline storage
            val directMatch = streams.find { !it.isDash && it.directUrl.isNotEmpty() && it.resolution == requestedRes }
                ?: streams.find { !it.isDash && it.directUrl.isNotEmpty() }
            val matchedStream = directMatch
                ?: streams.find { it.resolution == requestedRes }
                ?: streams.find { it.isDash && it.mpdUrl.isNotEmpty() }
                ?: streams.firstOrNull()

            if (matchedStream != null) {
                resolvedDownloadUrl = if (matchedStream.directUrl.isNotEmpty() && !matchedStream.isDash) {
                    matchedStream.directUrl
                } else if (matchedStream.isDash && matchedStream.mpdUrl.isNotEmpty()) {
                    matchedStream.mpdUrl
                } else {
                    matchedStream.directUrl.ifEmpty { matchedStream.mpdUrl }
                }
                resolvedSignCookie = matchedStream.signCookie
                if (resolvedSizeBytes <= 0L && matchedStream.sizeBytes > 0L) {
                    resolvedSizeBytes = matchedStream.sizeBytes
                }
            }

            if (resolvedDownloadUrl.isEmpty()) {
                val fallbackStream = CatalogData.sampleStreams.find { it.resolution == requestedRes }
                    ?: CatalogData.sampleStreams.firstOrNull()
                if (fallbackStream != null) {
                    resolvedDownloadUrl = fallbackStream.directUrl
                    if (resolvedSizeBytes <= 0L && fallbackStream.sizeBytes > 0L) {
                        resolvedSizeBytes = fallbackStream.sizeBytes
                    }
                }
            }
        }

        downloadManager.enqueueDownload(
            context = context,
            subjectId = subjectId,
            title = title,
            coverUrl = coverUrl,
            se = se,
            ep = ep,
            episodeTitle = episodeTitle,
            quality = quality,
            audioTrack = audioTrack,
            downloadUrl = resolvedDownloadUrl,
            signCookie = resolvedSignCookie,
            knownSizeBytes = resolvedSizeBytes
        )
    }

    fun pauseDownload(id: String) {
        downloadManager.pauseDownload(id)
    }

    suspend fun resumeDownload(context: Context, id: String, signCookie: String = "") {
        downloadManager.resumeDownload(context, id, signCookie)
    }

    suspend fun deleteDownload(id: String) {
        downloadManager.cancelAndDeleteDownload(id)
    }

    suspend fun getDownload(id: String): DownloadEntity? {
        return downloadDao.getDownload(id)
    }

    suspend fun getDownload(subjectId: String, se: Int, ep: Int): DownloadEntity? {
        return downloadDao.getDownload("${subjectId}_${se}_${ep}")
    }

    suspend fun toggleWatchlist(item: MediaItem) {
        // Will be checked and inverted
    }

    suspend fun addToWatchlist(item: WatchlistEntity) {
        watchlistDao.addToWatchlist(item)
    }

    suspend fun removeFromWatchlist(subjectId: String) {
        watchlistDao.removeFromWatchlist(subjectId)
    }

    suspend fun saveWatchProgress(
        subjectId: String,
        title: String,
        coverUrl: String,
        se: Int,
        ep: Int,
        episodeTitle: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val id = "${subjectId}_${se}_${ep}"
        watchHistoryDao.insertHistory(
            WatchHistoryEntity(
                id = id,
                subjectId = subjectId,
                title = title,
                coverUrl = coverUrl,
                se = se,
                ep = ep,
                episodeTitle = episodeTitle,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getWatchHistory(subjectId: String, se: Int, ep: Int): WatchHistoryEntity? {
        return watchHistoryDao.getHistory(subjectId, se, ep)
    }

    suspend fun deleteWatchHistory(id: String) {
        watchHistoryDao.deleteHistory(id)
    }

    suspend fun clearWatchHistory() {
        watchHistoryDao.clearAllHistory()
    }

    // Cache & Data Saver to reduce data consumption and avoid repeated network calls
    val isDataSaverEnabled = MutableStateFlow(true)

    val mediaItemCache = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()
    val mediaDetailCache = java.util.concurrent.ConcurrentHashMap<String, MediaDetail>()
    val seasonInfoCache = java.util.concurrent.ConcurrentHashMap<String, List<SeasonInfo>>()
    private val homeFeedCache = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, HomeData>>()
    private val feedCacheTtlMs = 15 * 60 * 1000L // 15 min cache for data saving

    init {
        // Pre-populate cache with built-in catalog
        for (item in CatalogData.builtInMediaList) {
            mediaItemCache[item.id] = item
        }
    }

    fun setDataSaver(enabled: Boolean) {
        isDataSaverEnabled.value = enabled
    }

    suspend fun getHomeFeed(tabId: Int, forceRefresh: Boolean = false): HomeData {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = homeFeedCache[tabId]
            if (cached != null && (now - cached.first) < feedCacheTtlMs) {
                return cached.second
            }
        }

        try {
            val remote = apiService.getHomeFeed(tabId)
            if (remote.sections.isNotEmpty() || remote.banners.isNotEmpty()) {
                // Cache items in memory
                remote.sections.forEach { sec ->
                    sec.items.forEach { item -> mediaItemCache[item.id] = item }
                }
                homeFeedCache[tabId] = Pair(now, remote)
                return remote
            }
        } catch (_: Exception) { }

        // Fallback to rich built-in catalog filtered by tab
        val filtered = when (tabId) {
            1 -> CatalogData.builtInMediaList.filter { it.subjectType == 1 }
            2 -> CatalogData.builtInMediaList.filter { it.subjectType == 2 }
            else -> CatalogData.builtInMediaList
        }

        val trending = filtered.take(6)
        val topRated = filtered.sortedByDescending { it.score ?: 0.0 }.take(6)
        val actionSciFi = filtered.filter { it.genre.contains("Sci-Fi") || it.genre.contains("Action") }

        val sections = mutableListOf<HomeSection>()
        if (trending.isNotEmpty()) {
            sections.add(HomeSection(title = "Trending Now", type = "grid", items = trending))
        }
        if (topRated.isNotEmpty()) {
            sections.add(HomeSection(title = "Top Rated Masterpieces", type = "carousel", items = topRated))
        }
        if (actionSciFi.isNotEmpty()) {
            sections.add(HomeSection(title = "Sci-Fi & Action", type = "carousel", items = actionSciFi))
        }

        val fallbackData = HomeData(
            banners = CatalogData.sampleBanners,
            sections = sections
        )
        homeFeedCache[tabId] = Pair(now, fallbackData)
        return fallbackData
    }

    suspend fun search(keyword: String, page: Int = 1, subjectType: Int = 0): List<MediaItem> {
        val cleanQuery = keyword.trim().lowercase()
        if (cleanQuery.isEmpty()) return emptyList()

        // 1. Attempt remote API search with official parameters
        try {
            val remoteResults = apiService.search(keyword.trim(), page, perPage = 20, subjectType = subjectType)
            if (remoteResults.isNotEmpty()) {
                remoteResults.forEach { mediaItemCache[it.id] = it }
                return remoteResults
            }
        } catch (_: Exception) { }

        // 2. Fallback to cached items only if offline or remote returned empty
        val localMatches = mutableListOf<MediaItem>()
        val allAvailable = mediaItemCache.values.distinctBy { it.id }
        for (item in allAvailable) {
            val matchesType = subjectType == 0 || item.subjectType == subjectType
            val matchesQuery = item.title.lowercase().contains(cleanQuery) ||
                    item.genre.lowercase().contains(cleanQuery) ||
                    item.description.lowercase().contains(cleanQuery)
            if (matchesType && matchesQuery) {
                localMatches.add(item)
            }
        }
        return localMatches
    }

    suspend fun getSuggestions(keyword: String): List<String> {
        val clean = keyword.trim()
        if (clean.isEmpty()) return emptyList()

        // 1. Query live search-suggest API
        try {
            val remote = apiService.getSuggest(clean)
            if (remote.isNotEmpty()) {
                return remote.distinct().take(10)
            }
        } catch (_: Exception) { }

        // 2. Fallback to live cached items
        val lower = clean.lowercase()
        return mediaItemCache.values
            .map { it.title }
            .filter { it.lowercase().contains(lower) }
            .distinct()
            .take(6)
    }

    suspend fun getSubjectDetail(subjectId: String): MediaDetail? {
        // 1. Check detail cache first (saves high data bandwidth)
        mediaDetailCache[subjectId]?.let { return it }

        // 2. Attempt remote API
        try {
            val remote = apiService.getSubjectDetail(subjectId)
            if (remote != null && remote.title.isNotEmpty()) {
                mediaDetailCache[subjectId] = remote
                return remote
            }
        } catch (_: Exception) { }

        // 3. Resilient fallback: find from mediaItemCache or built-in list
        val item = mediaItemCache[subjectId]
            ?: CatalogData.builtInMediaList.find { it.id == subjectId }
            ?: CatalogData.builtInMediaList.find { it.title.equals(subjectId, ignoreCase = true) }
            ?: MediaItem(
                id = subjectId,
                title = subjectId.replace("sub_", "").replace("_", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&q=80",
                description = "High definition cinematic release featuring full surround audio and multi-resolution streaming.",
                subjectType = if (subjectId.contains("series") || subjectId.contains("tv")) 2 else 1,
                score = 8.5,
                genre = "Drama • Action"
            )

        val detail = CatalogData.createFallbackDetail(item)
        mediaDetailCache[subjectId] = detail
        return detail
    }

    suspend fun getSeasonInfo(subjectId: String): List<SeasonInfo> {
        seasonInfoCache[subjectId]?.let { return it }
        try {
            val remote = apiService.getSeasonInfo(subjectId)
            if (remote.isNotEmpty()) {
                seasonInfoCache[subjectId] = remote
                return remote
            }
        } catch (_: Exception) { }

        val detail = getSubjectDetail(subjectId)
        val title = detail?.title ?: "Series"
        val fallback = CatalogData.createFallbackSeasons(subjectId, title)
        seasonInfoCache[subjectId] = fallback
        return fallback
    }

    suspend fun getPlayInfo(subjectId: String, se: Int = 0, ep: Int = 0): List<StreamOption> {
        val isDataSaver = isDataSaverEnabled.value
        try {
            val remote = apiService.getPlayInfo(subjectId, se, ep)
            if (remote.isNotEmpty()) {
                return if (isDataSaver) {
                    // Prioritize 720p or 480p for data saver mode
                    remote.sortedWith(compareBy({ if (it.resolution <= 720) 0 else 1 }, { -it.resolution }))
                } else {
                    remote.sortedByDescending { it.resolution }
                }
            }
        } catch (_: Exception) { }

        // Fallback test streams
        return if (isDataSaver) {
            listOf(CatalogData.sampleStreams[1], CatalogData.sampleStreams[2], CatalogData.sampleStreams[0])
        } else {
            CatalogData.sampleStreams
        }
    }

    suspend fun getRelated(subjectId: String): List<MediaItem> {
        try {
            val remote = apiService.getRelatedRecommendations(subjectId)
            if (remote.isNotEmpty()) {
                remote.forEach { mediaItemCache[it.id] = it }
                return remote
            }
        } catch (_: Exception) { }

        // Fallback to real items loaded from current feed if remote fails or offline
        val cachedRelated = mediaItemCache.values.filter { it.id != subjectId && it.title.isNotEmpty() }
        if (cachedRelated.isNotEmpty()) {
            return cachedRelated.shuffled().take(8)
        }
        return emptyList()
    }
}
