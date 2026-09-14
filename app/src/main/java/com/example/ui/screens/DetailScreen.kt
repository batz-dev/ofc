package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.data.db.WatchlistEntity
import com.example.data.model.EpisodeInfo
import com.example.data.model.MediaDetail
import com.example.data.model.MediaItem
import com.example.data.model.SeasonInfo
import com.example.data.repository.MovieRepository
import com.example.ui.components.DetailScreenSkeleton
import com.example.ui.components.DownloadOptionsDialog
import com.example.ui.components.EpisodeDownloadItem
import com.example.ui.components.MediaCard
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(
        val detail: MediaDetail,
        val seasons: List<SeasonInfo>,
        val related: List<MediaItem>,
        val isInWatchlist: Boolean,
        val availableQualities: List<String> = emptyList(),
        val streams: List<com.example.data.model.StreamOption> = emptyList()
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class DetailViewModel(
    private val repository: MovieRepository,
    val subjectId: String
) : ViewModel() {
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _isPreparingDownload = MutableStateFlow(false)
    val isPreparingDownload: StateFlow<Boolean> = _isPreparingDownload.asStateFlow()

    fun prepareDownload(
        se: Int = 0,
        ep: Int = 0,
        onReady: (streams: List<com.example.data.model.StreamOption>, qualities: List<String>) -> Unit
    ) {
        viewModelScope.launch {
            _isPreparingDownload.value = true
            try {
                val streams = repository.getPlayInfo(subjectId, se, ep)
                val qualities = streams.map { "${it.resolution}p" }.distinct().let { list ->
                    if (list.isEmpty()) listOf("1080p", "720p", "480p", "360p") else list
                }
                val current = _uiState.value
                if (current is DetailUiState.Success) {
                    _uiState.value = current.copy(
                        streams = streams,
                        availableQualities = qualities
                    )
                }
                _isPreparingDownload.value = false
                onReady(streams, qualities)
            } catch (_: Exception) {
                _isPreparingDownload.value = false
                val current = _uiState.value
                val fallbackStreams = (current as? DetailUiState.Success)?.streams ?: emptyList()
                val fallbackQualities = (current as? DetailUiState.Success)?.availableQualities ?: listOf("1080p", "720p", "480p", "360p")
                onReady(fallbackStreams, fallbackQualities)
            }
        }
    }

    init {
        loadDetail()
        observeWatchlist()
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            repository.isInWatchlist(subjectId).collect { inList ->
                val current = _uiState.value
                if (current is DetailUiState.Success) {
                    _uiState.value = current.copy(isInWatchlist = inList)
                }
            }
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            // Check cache first for instant zero-lag presentation
            val cachedDetail = repository.mediaDetailCache[subjectId]
            val cachedSeasons = repository.seasonInfoCache[subjectId] ?: emptyList()
            if (cachedDetail != null) {
                if (cachedSeasons.isNotEmpty()) {
                    _selectedSeason.value = cachedSeasons.first().seasonNumber
                }
                _uiState.value = DetailUiState.Success(
                    detail = cachedDetail,
                    seasons = cachedSeasons,
                    related = emptyList(),
                    isInWatchlist = false,
                    availableQualities = listOf("1080p", "720p", "480p", "360p")
                )
            } else {
                _uiState.value = DetailUiState.Loading
            }

            try {
                val detail = repository.getSubjectDetail(subjectId)
                if (detail == null) {
                    if (_uiState.value !is DetailUiState.Success) {
                        _uiState.value = DetailUiState.Error("Media item not found")
                    }
                    return@launch
                }

                // Launch parallel background tasks so seasons, related, and available streams load concurrently
                val seasonsDeferred = async(Dispatchers.IO) {
                    if (detail.subjectType == 2) {
                        try { repository.getSeasonInfo(subjectId) } catch (_: Exception) { emptyList<SeasonInfo>() }
                    } else emptyList<SeasonInfo>()
                }
                val relatedDeferred = async(Dispatchers.IO) {
                    try { repository.getRelated(subjectId) } catch (_: Exception) { emptyList<MediaItem>() }
                }
                val streamsDeferred = async(Dispatchers.IO) {
                    try {
                        repository.getPlayInfo(
                            subjectId = subjectId,
                            se = if (detail.subjectType == 2) 1 else 0,
                            ep = if (detail.subjectType == 2) 1 else 0
                        )
                    } catch (_: Exception) { emptyList<com.example.data.model.StreamOption>() }
                }

                val seasons: List<SeasonInfo> = seasonsDeferred.await()
                if (seasons.isNotEmpty()) {
                    _selectedSeason.value = seasons.first().seasonNumber
                }
                val related: List<MediaItem> = relatedDeferred.await()
                val streams: List<com.example.data.model.StreamOption> = streamsDeferred.await()
                val availableQualities: List<String> = streams.map { "${it.resolution}p" }.distinct().let { list ->
                    if (list.isEmpty()) listOf("1080p", "720p", "480p", "360p") else list
                }

                _uiState.value = DetailUiState.Success(
                    detail = detail,
                    seasons = seasons,
                    related = related,
                    isInWatchlist = false,
                    availableQualities = availableQualities,
                    streams = streams
                )
            } catch (e: Exception) {
                if (_uiState.value !is DetailUiState.Success) {
                    _uiState.value = DetailUiState.Error(e.localizedMessage ?: "Failed to load")
                }
            }
        }
    }

    fun selectSeason(seasonNum: Int) {
        _selectedSeason.value = seasonNum
    }

    fun toggleWatchlist(detail: MediaDetail) {
        viewModelScope.launch {
            val current = _uiState.value
            if (current is DetailUiState.Success) {
                if (current.isInWatchlist) {
                    repository.removeFromWatchlist(detail.id)
                } else {
                    repository.addToWatchlist(
                        WatchlistEntity(
                            subjectId = detail.id,
                            title = detail.title,
                            coverUrl = detail.coverUrl,
                            subjectType = detail.subjectType,
                            score = detail.score,
                            releaseDate = detail.releaseDate
                        )
                    )
                }
            }
        }
    }

    fun startDownloads(
        context: Context,
        detail: MediaDetail,
        quality: String,
        audioTrack: String,
        episodes: List<EpisodeDownloadItem>
    ) {
        val currentStreams = (_uiState.value as? DetailUiState.Success)?.streams ?: emptyList()
        val resInt = quality.replace("p", "", ignoreCase = true).replace("k", "000", ignoreCase = true).toIntOrNull() ?: 1080
        val isSeries = detail.subjectType == 2
        val matchedStream = currentStreams.find { it.resolution == resInt && it.sizeBytes > 0L }
            ?: currentStreams.find { it.sizeBytes > 0L && "${it.resolution}p".equals(quality, ignoreCase = true) }
            ?: currentStreams.minByOrNull { kotlin.math.abs(it.resolution - resInt) }
        val knownSize = if (matchedStream != null && matchedStream.sizeBytes > 0L) {
            matchedStream.sizeBytes
        } else {
            DownloadStorageHelper.getEstimatedSizeBytes(quality, isSeries = isSeries)
        }

        viewModelScope.launch {
            episodes.forEach { epItem ->
                repository.startDownload(
                    context = context,
                    subjectId = detail.id,
                    title = detail.title,
                    coverUrl = detail.coverUrl,
                    se = epItem.se,
                    ep = epItem.ep,
                    episodeTitle = epItem.title,
                    quality = quality,
                    audioTrack = audioTrack,
                    knownSizeBytes = knownSize
                )
            }
        }
    }
}

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit,
    onPlayClick: (subjectId: String, se: Int, ep: Int, title: String, episodeTitle: String, audioTrack: String) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onGoToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val isPreparingDownload by viewModel.isPreparingDownload.collectAsState()
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    var selectedEpisodeNum by remember { mutableIntStateOf(1) }
    var selectedDub by remember { mutableStateOf("Hindi (हिन्दी)") }

    var isDownloadOptionsVisible by remember { mutableStateOf(false) }
    var targetDownloadSeason by remember { mutableIntStateOf(1) }
    var targetDownloadEpisode by remember { mutableIntStateOf(1) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingDownloadAction?.invoke()
        pendingDownloadAction = null
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = data.visuals.message,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        data.visuals.actionLabel?.let { actionLabel ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { data.performAction() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = actionLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    DetailScreenSkeleton()
                }

                is DetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadDetail() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                is DetailUiState.Success -> {
                    val detail = state.detail

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("detail_scroll_view"),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        // Hero Backdrop Banner
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                            ) {
                                AsyncImage(
                                    model = com.example.ui.components.ImageHelper.getCompressedUrl(detail.backdropUrl.ifEmpty { detail.coverUrl }, isBackdrop = true),
                                    contentDescription = "${detail.title} backdrop",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Gradient overlays
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                                    MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                    )

                                // Top navigation bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = onBackClick,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                                            .testTag("detail_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.toggleWatchlist(detail) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                                            .testTag("detail_top_watchlist_button")
                                    ) {
                                        Icon(
                                            imageVector = if (state.isInWatchlist) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Toggle Watchlist",
                                            tint = if (state.isInWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Title and Metadata Details
                        item {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 18.dp)
                                    .offset(y = (-20).dp)
                            ) {
                                Text(
                                    text = detail.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Metadata Badges Row: Rating, Type, Year, Duration, 4K
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Star Rating Badge
                                    if (detail.score != null && detail.score > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Rating score",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = String.format("%.1f", detail.score),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Type badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = if (detail.subjectType == 2) "TV SERIES" else "MOVIE",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    // Year
                                    val year = detail.releaseDate.split("-").firstOrNull() ?: ""
                                    if (year.isNotEmpty()) {
                                        Text(
                                            text = year,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Duration
                                    if (detail.duration > 0) {
                                        val hrs = detail.duration / 60
                                        val mins = detail.duration % 60
                                        val durText = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = durText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Genre Tag Pills
                                if (detail.genre.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        detail.genre.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(4).forEach { genre ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = genre,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Main Action Buttons: Play + Download + Watchlist
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Play Button
                                    Button(
                                        onClick = {
                                            val firstSeason = state.seasons.firstOrNull()?.seasonNumber ?: 0
                                            val firstEp = state.seasons.firstOrNull()?.episodes?.firstOrNull()?.episodeNumber ?: 0
                                            val epTitle = state.seasons.firstOrNull()?.episodes?.firstOrNull()?.title ?: ""
                                            onPlayClick(detail.id, firstSeason, firstEp, detail.title, epTitle, selectedDub)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(48.dp)
                                            .testTag("detail_play_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (detail.subjectType == 2) "Watch S1:E1" else "Play Movie",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Download Button (triggers audio & quality & episodes modal)
                                    OutlinedButton(
                                        onClick = {
                                            val se = if (detail.subjectType == 2) selectedSeason else 0
                                            val ep = if (detail.subjectType == 2) 1 else 0
                                            targetDownloadSeason = se
                                            targetDownloadEpisode = ep
                                            viewModel.prepareDownload(se, ep) { _, _ ->
                                                isDownloadOptionsVisible = true
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("detail_download_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = "Download",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Download",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Watchlist Button
                                    IconButton(
                                        onClick = { viewModel.toggleWatchlist(detail) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (state.isInWatchlist) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                            )
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (state.isInWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .testTag("detail_watchlist_button")
                                    ) {
                                        Icon(
                                            imageVector = if (state.isInWatchlist) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Watchlist",
                                            tint = if (state.isInWatchlist) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Storyline Section
                                if (detail.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(22.dp))
                                    Text(
                                        text = "Storyline",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = detail.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 21.sp,
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .animateContentSize()
                                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                    )
                                    Text(
                                        text = if (isDescriptionExpanded) "Show Less" else "Read More",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                    )
                                }
                            }
                        }

                        // Unified Episode & Dubbing Selector Card (for TV Series and Movies)
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            val isSeries = state.seasons.isNotEmpty()
                            val currentSeasonObj = state.seasons.firstOrNull { it.seasonNumber == selectedSeason } ?: state.seasons.firstOrNull()
                            val seasonEpisodes = currentSeasonObj?.episodes ?: emptyList()
                            val currentEpisodeObj = seasonEpisodes.firstOrNull { it.episodeNumber == selectedEpisodeNum } ?: seasonEpisodes.firstOrNull()

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .testTag("episode_and_dub_selector")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Selector Header
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isSeries) "Episode & Dubbing Selector" else "Dubbing & Audio Selector",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (isSeries) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = "S${selectedSeason}:E${currentEpisodeObj?.episodeNumber ?: 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // If series with multiple seasons: Season Selector
                                    if (isSeries && state.seasons.size > 1) {
                                        Text(
                                            text = "Season",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(state.seasons) { season ->
                                                val isSelected = season.seasonNumber == selectedSeason
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        viewModel.selectSeason(season.seasonNumber)
                                                        selectedEpisodeNum = 1
                                                    },
                                                    label = { Text("Season ${season.seasonNumber}") },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }

                                    // Episode Selector for TV Series
                                    if (isSeries && seasonEpisodes.isNotEmpty()) {
                                        Text(
                                            text = "Choose Episode",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(seasonEpisodes) { ep ->
                                                val isSelected = (currentEpisodeObj?.episodeNumber == ep.episodeNumber)
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                    ),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { selectedEpisodeNum = ep.episodeNumber }
                                                        .testTag("select_ep_${ep.episodeNumber}")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "Ep ${ep.episodeNumber}",
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (currentEpisodeObj != null && currentEpisodeObj.title.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Title: ${currentEpisodeObj.title}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                    }

                                    // Dubbing / Audio Language Selector
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Audiotrack,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Audio Dub / Language",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))

                                    val dubList = listOf(
                                        "Hindi (हिन्दी)",
                                        "English (Original)",
                                        "Spanish (Español)",
                                        "French (Français)",
                                        "German (Deutsch)",
                                        "Japanese (日本語)"
                                    )

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(dubList) { dub ->
                                            val isSelected = selectedDub == dub
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { selectedDub = dub }
                                                    .testTag("select_dub_$dub")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = dub,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Action buttons for the chosen episode & dub
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val epNum = if (isSeries) (currentEpisodeObj?.episodeNumber ?: 1) else 0
                                                val epTitle = if (isSeries) (currentEpisodeObj?.title ?: "Episode $epNum") else ""
                                                val seNum = if (isSeries) selectedSeason else 0
                                                onPlayClick(detail.id, seNum, epNum, detail.title, epTitle, selectedDub)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(44.dp)
                                                .testTag("play_selected_item")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isSeries) "Watch S${selectedSeason}:E${currentEpisodeObj?.episodeNumber ?: 1}" else "Play in ${selectedDub.split(" ").first()}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val se = if (isSeries) selectedSeason else 0
                                                val ep = if (isSeries) (currentEpisodeObj?.episodeNumber ?: 1) else 0
                                                targetDownloadSeason = se
                                                targetDownloadEpisode = ep
                                                viewModel.prepareDownload(se, ep) { _, _ ->
                                                    isDownloadOptionsVisible = true
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("download_selected_item")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = "Download",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Download",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // More Like This / Recommended For You (Directly below the selector!)
                        if (state.related.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = "Recommended For You",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                                    )

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 18.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(state.related, key = { it.id }) { item ->
                                            MediaCard(
                                                item = item,
                                                onClick = { onMediaClick(item) },
                                                cardWidth = 115
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Loading / Preparing Download Links Dialog
                    if (isPreparingDownload) {
                        Dialog(onDismissRequest = { /* Non-dismissible while preparing links */ }) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(44.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = "Preparing Download Links",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Fetching high-speed server links & verifying accurate stream sizes...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }
                            }
                        }
                    }

                    // Download Options Dialog
                    if (isDownloadOptionsVisible) {
                        DownloadOptionsDialog(
                            title = detail.title,
                            coverUrl = detail.coverUrl,
                            isSeries = detail.subjectType == 2,
                            seasons = state.seasons,
                            availableQualities = state.availableQualities,
                            availableStreams = state.streams,
                            initialSeason = targetDownloadSeason,
                            initialEpisode = targetDownloadEpisode,
                            initialAudio = selectedDub,
                            onDismiss = { isDownloadOptionsVisible = false },
                            onConfirm = { quality, audioTrack, selectedEpisodes ->
                                isDownloadOptionsVisible = false
                                val triggerDownloadAction: () -> Unit = {
                                    viewModel.startDownloads(
                                        context = context,
                                        detail = detail,
                                        quality = quality,
                                        audioTrack = audioTrack,
                                        episodes = selectedEpisodes
                                    )
                                    coroutineScope.launch {
                                        val countText = if (selectedEpisodes.size > 1) "${selectedEpisodes.size} episodes" else "download"
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Started $countText of ${detail.title} ($quality • $audioTrack) in background",
                                            actionLabel = "Go to Downloads",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onGoToDownloads()
                                        }
                                    }
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                        triggerDownloadAction()
                                    } else {
                                        pendingDownloadAction = triggerDownloadAction
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    triggerDownloadAction()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRowItem(
    episode: EpisodeInfo,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("episode_${episode.episodeNumber}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Number Box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${episode.episodeNumber}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPlay() }
            ) {
                Text(
                    text = episode.title.ifEmpty { "Episode ${episode.episodeNumber}" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Stream or Download",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Download Episode button (48dp touch target)
            IconButton(
                onClick = onDownload,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("download_episode_${episode.episodeNumber}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Download Episode ${episode.episodeNumber}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Play Episode button (48dp touch target)
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("play_episode_${episode.episodeNumber}")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Episode ${episode.episodeNumber}",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
