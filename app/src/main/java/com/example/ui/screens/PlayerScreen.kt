package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.db.DownloadStatus
import com.example.data.download.DownloadCacheManager
import com.example.data.model.CatalogData
import com.example.data.model.StreamOption
import com.example.data.repository.MovieRepository
import com.example.ui.components.MaterialBufferingLoader
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

sealed interface PlayerUiState {
    object Loading : PlayerUiState
    data class Ready(
        val streams: List<StreamOption>,
        val currentStream: StreamOption,
        val resumePositionMs: Long = 0L,
        val isOffline: Boolean = false
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(
    private val repository: MovieRepository,
    val subjectId: String,
    val se: Int,
    val ep: Int,
    val title: String,
    val episodeTitle: String,
    val filePath: String = "",
    val initialAudioTrack: String = ""
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow(initialAudioTrack)
    val selectedAudioTrack: StateFlow<String> = _selectedAudioTrack.asStateFlow()

    init {
        loadMedia()
    }

    fun setAudioTrack(track: String) {
        _selectedAudioTrack.value = track
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            // Resolve audioTrack from download entity if not passed
            if (_selectedAudioTrack.value.isEmpty()) {
                val download = repository.getDownload(subjectId, se, ep)
                    ?: if (filePath.isNotEmpty()) {
                        repository.allDownloads.firstOrNull()?.find { it.filePath == filePath }
                    } else null
                if (download != null && download.audioTrack.isNotEmpty()) {
                    _selectedAudioTrack.value = download.audioTrack
                }
            }

            // Check if downloaded entity exists and is completed
            val download = repository.getDownload(subjectId, se, ep)
                ?: if (filePath.isNotEmpty()) {
                    repository.allDownloads.firstOrNull()?.find { it.filePath == filePath }
                } else null

            if (download != null && download.status == DownloadStatus.COMPLETED) {
                val file = File(download.filePath)
                val isDash = download.downloadUrl.contains(".mpd", ignoreCase = true)
                if (!isDash && file.exists() && file.length() > 500L) {
                    val localStream = StreamOption(
                        resolution = download.quality.replace("p", "", ignoreCase = true).toIntOrNull() ?: 1080,
                        title = "${download.quality} (Offline)",
                        codec = "h264",
                        isDash = false,
                        directUrl = download.filePath,
                        sizeBytes = file.length()
                    )
                    _uiState.value = PlayerUiState.Ready(
                        streams = listOf(localStream),
                        currentStream = localStream,
                        resumePositionMs = 0L,
                        isOffline = true
                    )
                    return@launch
                } else if (isDash) {
                    val localStream = StreamOption(
                        resolution = download.quality.replace("p", "", ignoreCase = true).toIntOrNull() ?: 1080,
                        title = "${download.quality} (Downloaded)",
                        codec = "hevc",
                        isDash = true,
                        mpdUrl = download.downloadUrl,
                        signCookie = download.signCookie,
                        sizeBytes = download.totalBytes
                    )
                    _uiState.value = PlayerUiState.Ready(
                        streams = listOf(localStream),
                        currentStream = localStream,
                        resumePositionMs = 0L,
                        isOffline = true
                    )
                    return@launch
                } else if (file.exists() && file.length() > 500L) {
                    val localStream = StreamOption(
                        resolution = download.quality.replace("p", "", ignoreCase = true).toIntOrNull() ?: 1080,
                        title = "${download.quality} (Offline)",
                        codec = "h264",
                        isDash = false,
                        directUrl = download.filePath,
                        sizeBytes = file.length()
                    )
                    _uiState.value = PlayerUiState.Ready(
                        streams = listOf(localStream),
                        currentStream = localStream,
                        resumePositionMs = 0L,
                        isOffline = true
                    )
                    return@launch
                }
            } else if (filePath.isNotEmpty()) {
                val file = File(filePath)
                if (file.exists() && file.length() > 500L) {
                    val localStream = StreamOption(
                        resolution = 1080,
                        title = "Offline File",
                        codec = "h264",
                        isDash = false,
                        directUrl = filePath,
                        sizeBytes = file.length()
                    )
                    _uiState.value = PlayerUiState.Ready(
                        streams = listOf(localStream),
                        currentStream = localStream,
                        resumePositionMs = 0L,
                        isOffline = true
                    )
                    return@launch
                }
            }

            // Stream remotely
            try {
                val streams = repository.getPlayInfo(subjectId, se, ep)
                if (streams.isEmpty()) {
                    _uiState.value = PlayerUiState.Error("No playable streams available for this title.")
                    return@launch
                }

                val savedHistory = repository.getWatchHistory(subjectId, se, ep)
                val resumePos = if (savedHistory != null && savedHistory.positionMs > 10_000L) {
                    savedHistory.positionMs
                } else 0L

                val isDataSaver = repository.isDataSaverEnabled.value
                val bestStream = if (isDataSaver) {
                    streams.find { it.resolution == 720 }
                        ?: streams.find { it.resolution <= 720 }
                        ?: streams.first()
                } else {
                    streams.first()
                }
                _uiState.value = PlayerUiState.Ready(
                    streams = streams,
                    currentStream = bestStream,
                    resumePositionMs = resumePos,
                    isOffline = false
                )
            } catch (e: Exception) {
                _uiState.value = PlayerUiState.Error(e.localizedMessage ?: "Failed to retrieve media stream")
            }
        }
    }

    val isDataSaver: StateFlow<Boolean> = repository.isDataSaverEnabled

    fun toggleDataSaver(currentPosMs: Long) {
        val newState = !repository.isDataSaverEnabled.value
        repository.setDataSaver(newState)
        val current = _uiState.value
        if (current is PlayerUiState.Ready) {
            val targetStream = if (newState) {
                current.streams.find { it.resolution == 720 }
                    ?: current.streams.find { it.resolution <= 720 }
                    ?: current.streams.last()
            } else {
                current.streams.first()
            }
            switchStream(targetStream, currentPosMs)
        }
    }

    fun switchStream(newStream: StreamOption, currentPosMs: Long) {
        val current = _uiState.value
        if (current is PlayerUiState.Ready) {
            _uiState.value = current.copy(
                currentStream = newStream,
                resumePositionMs = currentPosMs
            )
        }
    }

    fun tryFallbackStream(currentPosMs: Long) {
        val current = _uiState.value
        if (current is PlayerUiState.Ready) {
            val currentIndex = current.streams.indexOf(current.currentStream)
            if (currentIndex in 0 until current.streams.size - 1) {
                switchStream(current.streams[currentIndex + 1], currentPosMs)
            } else {
                val fallbackStream = CatalogData.sampleStreams.firstOrNull { it.directUrl != current.currentStream.directUrl }
                if (fallbackStream != null) {
                    _uiState.value = PlayerUiState.Ready(
                        streams = listOf(fallbackStream),
                        currentStream = fallbackStream,
                        resumePositionMs = currentPosMs,
                        isOffline = false
                    )
                }
            }
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long, coverUrl: String = "") {
        if (durationMs <= 0) return
        viewModelScope.launch {
            repository.saveWatchProgress(
                subjectId = subjectId,
                title = title,
                coverUrl = coverUrl,
                se = se,
                ep = ep,
                episodeTitle = episodeTitle,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }
}

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isQualitySheetVisible by remember { mutableStateOf(false) }
    var isAudioSheetVisible by remember { mutableStateOf(false) }
    val resolvedAudio by viewModel.selectedAudioTrack.collectAsState()
    var currentAudioTrack by remember(resolvedAudio) {
        mutableStateOf(if (resolvedAudio.isNotEmpty()) resolvedAudio else "English (Original)")
    }
    var currentLoadedUri by remember { mutableStateOf<String?>(null) }

    // Always rotate player into landscape
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            playWhenReady = true
        }
    }

    // Configure video resolution on ExoPlayer
    fun applyVideoQuality(resolution: Int) {
        val maxW = when (resolution) {
            2160 -> 3840
            1440 -> 2560
            1080 -> 1920
            720 -> 1280
            480 -> 854
            360 -> 640
            else -> 1920
        }
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(maxW, resolution)
            .setMinVideoSize(0, 0)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)

        for (group in exoPlayer.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.height == resolution || (resolution >= 2160 && format.height >= 2160)) {
                        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        break
                    }
                }
            }
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    // Configure audio track language on ExoPlayer
    fun applyAudioLanguage(trackName: String) {
        val langCodes = when {
            trackName.contains("hindi", ignoreCase = true) || trackName.contains("हिन्दी") -> listOf("hi", "hin", "hindi")
            trackName.contains("spanish", ignoreCase = true) || trackName.contains("español", ignoreCase = true) -> listOf("es", "spa", "spanish")
            trackName.contains("french", ignoreCase = true) || trackName.contains("français", ignoreCase = true) -> listOf("fr", "fra", "fre", "french")
            trackName.contains("german", ignoreCase = true) || trackName.contains("deutsch", ignoreCase = true) -> listOf("de", "deu", "ger", "german")
            trackName.contains("japanese", ignoreCase = true) || trackName.contains("日本語", ignoreCase = true) -> listOf("ja", "jpn", "japanese")
            trackName.contains("english", ignoreCase = true) -> listOf("en", "eng", "english")
            else -> emptyList()
        }
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)

        if (langCodes.isNotEmpty()) {
            builder.setPreferredAudioLanguages(*langCodes.toTypedArray())
        } else {
            builder.setPreferredAudioLanguages()
        }

        for (group in exoPlayer.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = (format.language ?: "").lowercase()
                    val label = (format.label ?: "").lowercase()
                    val matches = langCodes.any { code ->
                        lang == code || lang.startsWith(code) || label.contains(code)
                    }
                    if (matches) {
                        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        break
                    }
                }
            }
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(currentAudioTrack) {
        applyAudioLanguage(currentAudioTrack)
    }

    // Auto-save watch progress every 5 seconds
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(5000)
            if (exoPlayer.duration > 0) {
                viewModel.saveProgress(exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
    }

    // Auto-hide controls after 4 seconds of idle
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Cleanup and listeners on dispose
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                applyAudioLanguage(currentAudioTrack)
                val readyState = uiState as? PlayerUiState.Ready
                if (readyState != null) {
                    applyVideoQuality(readyState.currentStream.resolution)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("PlayerScreen", "Player playback error: ${error.message}, attempting stream fallback")
                viewModel.tryFallbackStream(exoPlayer.currentPosition.coerceAtLeast(0L))
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            viewModel.saveProgress(exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Position ticker
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    // Bind stream source or local offline file
    LaunchedEffect(uiState) {
        val readyState = uiState as? PlayerUiState.Ready ?: return@LaunchedEffect
        val stream = readyState.currentStream

        if (readyState.isOffline && stream.directUrl.isNotEmpty() && !stream.isDash) {
            if (currentLoadedUri != stream.directUrl) {
                currentLoadedUri = stream.directUrl
                val localUri = Uri.fromFile(File(stream.directUrl))
                val mediaItem = ExoMediaItem.fromUri(localUri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (readyState.resumePositionMs > 0L) {
                    exoPlayer.seekTo(readyState.resumePositionMs)
                }
                exoPlayer.play()
            }
            return@LaunchedEffect
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ExoPlayerLib/2.19.1")
            .setAllowCrossProtocolRedirects(true)

        val headers = mutableMapOf<String, String>()
        val streamTarget = if (stream.isDash && stream.mpdUrl.isNotEmpty()) stream.mpdUrl else stream.directUrl
        if (streamTarget.contains("moviebox") || stream.signCookie.isNotEmpty()) {
            headers["Referer"] = "https://www.movieboxpro.app/"
        }
        if (stream.signCookie.isNotEmpty()) {
            val cookieHeader = stream.signCookie.replace("&", "; ")
            headers["Cookie"] = cookieHeader
        }
        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(DownloadCacheManager.getCache(context))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        if (currentLoadedUri != streamTarget) {
            currentLoadedUri = streamTarget
            val mediaSource = if (stream.isDash && stream.mpdUrl.isNotEmpty()) {
                val mediaItem = ExoMediaItem.Builder()
                    .setUri(stream.mpdUrl)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                DashMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem)
            } else {
                val directUri = stream.directUrl.ifEmpty { stream.mpdUrl }
                val mediaItem = ExoMediaItem.fromUri(directUri)
                ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem)
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()

            if (readyState.resumePositionMs > 0L) {
                exoPlayer.seekTo(readyState.resumePositionMs)
            }
            exoPlayer.play()
        }

        applyVideoQuality(stream.resolution)
        applyAudioLanguage(currentAudioTrack)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen_container")
    ) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialBufferingLoader(
                        size = 56.dp,
                        label = "Loading media stream..."
                    )
                }
            }

            is PlayerUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Go Back")
                    }
                }
            }

            is PlayerUiState.Ready -> {
                // Video Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            keepScreenOn = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isControlsVisible = !isControlsVisible
                        }
                )

                // Buffering Spinner with MaterialBufferingLoader
                if (isBuffering) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialBufferingLoader(
                            size = 52.dp,
                            label = "Buffering stream..."
                        )
                    }
                }

                // Controls Overlay with Transparent Buttons
                AnimatedVisibility(
                    visible = isControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        CinemaBlack.copy(alpha = 0.75f),
                                        Color.Transparent,
                                        CinemaBlack.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    ) {
                        // Top Bar: Back, Title, Audio Selector, Quality Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                // Transparent Back Button
                                IconButton(
                                    onClick = onBackClick,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("player_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Text(
                                        text = viewModel.title,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    if (viewModel.episodeTitle.isNotEmpty()) {
                                        Text(
                                            text = "S${viewModel.se}:E${viewModel.ep} • ${viewModel.episodeTitle}",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Transparent Top Action Buttons (Audio & Quality)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Audio Track Switcher (Transparent Button)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isAudioSheetVisible = true }
                                        .testTag("audio_selector_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Audiotrack,
                                            contentDescription = "Audio Track",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = currentAudioTrack.split(" ").first(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Quality Switcher (Transparent Button)
                                if (!state.isOffline) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { isQualitySheetVisible = true }
                                            .testTag("quality_selector_chip")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.HighQuality,
                                                contentDescription = "Quality",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${state.currentStream.resolution}P",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "Offline",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Center Controls: -10s, Play/Pause, +10s (ALL TRANSPARENT BUTTONS)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(42.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Transparent Rewind 10s Button
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), CircleShape)
                                    .clickable {
                                        val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                        currentPositionMs = target
                                    }
                                    .testTag("player_seek_back"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Transparent Play / Pause Button
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)), CircleShape)
                                    .clickable {
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    }
                                    .testTag("player_play_pause"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            // Transparent Fast Forward 10s Button
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), CircleShape)
                                    .clickable {
                                        val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration)
                                        exoPlayer.seekTo(target)
                                        currentPositionMs = target
                                    }
                                    .testTag("player_seek_forward"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Bottom Bar: Progress scrubber and timestamps
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Slider(
                                value = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                onValueChange = { fraction ->
                                    val target = (fraction * totalDurationMs).toLong()
                                    currentPositionMs = target
                                    exoPlayer.seekTo(target)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatTime(currentPositionMs)} / ${formatTime(totalDurationMs)}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = "Landscape Cinema Mode",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Audio Track Selection Dialog
                if (isAudioSheetVisible) {
                    AlertDialog(
                        onDismissRequest = { isAudioSheetVisible = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Audiotrack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.isOffline) "Offline Audio Track" else "Change Audio Track",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            if (state.isOffline) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = currentAudioTrack,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Downloaded Audio Track (Active)",
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active Track",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "This offline video is playing in $currentAudioTrack (as selected during download).",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            } else {
                                val languages = listOf(
                                    "English (Original)" to "en",
                                    "Spanish (Español)" to "es",
                                    "French (Français)" to "fr",
                                    "Hindi (हिन्दी)" to "hi",
                                    "German (Deutsch)" to "de",
                                    "Japanese (日本語)" to "ja",
                                    "Default Stream Audio" to ""
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    languages.forEach { (label, code) ->
                                        val isSelected = currentAudioTrack == label
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    currentAudioTrack = label
                                                    viewModel.setAudioTrack(label)
                                                    isAudioSheetVisible = false
                                                    applyAudioLanguage(label)
                                                }
                                                .testTag("audio_track_$label")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { isAudioSheetVisible = false }) {
                                Text("Close", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }

                // Quality Selector Dialog
                if (isQualitySheetVisible && !state.isOffline) {
                    AlertDialog(
                        onDismissRequest = { isQualitySheetVisible = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.HighQuality,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select Stream Quality",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.streams.forEach { streamOption ->
                                    val isSelected = streamOption.resolution == state.currentStream.resolution
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                isQualitySheetVisible = false
                                                viewModel.switchStream(streamOption, exoPlayer.currentPosition)
                                                applyVideoQuality(streamOption.resolution)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${streamOption.resolution}P",
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = if (streamOption.isDash) "DASH Adaptive • ${streamOption.codec.uppercase()}" else "Direct MP4",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { isQualitySheetVisible = false }) {
                                Text("Close", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
