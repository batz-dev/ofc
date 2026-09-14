package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tv
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.data.db.DownloadEntity
import com.example.data.db.DownloadStatus
import com.example.data.download.DownloadStorageHelper
import com.example.data.repository.MovieRepository
import com.example.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(private val repository: MovieRepository) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadSpeeds: StateFlow<Map<String, String>> = repository.downloadSpeeds

    fun pauseDownload(id: String) {
        repository.pauseDownload(id)
    }

    fun resumeDownload(context: Context, id: String) {
        viewModelScope.launch {
            repository.resumeDownload(context, id)
        }
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            repository.deleteDownload(id)
        }
    }
}

sealed interface DownloadGroupItem {
    data class MovieItem(val entity: DownloadEntity) : DownloadGroupItem
    data class SeriesGroup(
        val subjectId: String,
        val title: String,
        val coverUrl: String,
        val episodes: List<DownloadEntity>
    ) : DownloadGroupItem
}

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onPlayDownload: (DownloadEntity) -> Unit,
    onExploreClick: () -> Unit,
    onOpenDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState()
    val downloadSpeeds by viewModel.downloadSpeeds.collectAsState()
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Downloading, 2 = Completed
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }

    val filteredDownloads = remember(downloads, selectedFilter) {
        when (selectedFilter) {
            1 -> downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
            2 -> downloads.filter { it.status == DownloadStatus.COMPLETED }
            else -> downloads
        }
    }

    val groupedItems = remember(filteredDownloads) {
        val result = mutableListOf<DownloadGroupItem>()
        val (seriesEpisodes, movies) = filteredDownloads.partition { it.se > 0 || it.ep > 0 }

        // Group series by subjectId (fallback to title)
        val seriesBySubject = seriesEpisodes.groupBy { it.subjectId.ifEmpty { it.title } }
        seriesBySubject.forEach { (subjectId, episodes) ->
            val first = episodes.first()
            result.add(
                DownloadGroupItem.SeriesGroup(
                    subjectId = subjectId,
                    title = first.title,
                    coverUrl = first.coverUrl,
                    episodes = episodes.sortedWith(compareBy({ it.se }, { it.ep }))
                )
            )
        }

        // Add movies
        movies.forEach { movie ->
            result.add(DownloadGroupItem.MovieItem(movie))
        }

        result
    }

    val selectedSeriesGroup = remember(downloads, selectedSeriesId) {
        if (selectedSeriesId == null) null
        else {
            val seriesEpisodes = downloads.filter { it.se > 0 || it.ep > 0 }
            val matchingEpisodes = seriesEpisodes.filter { (it.subjectId.ifEmpty { it.title }) == selectedSeriesId }
            if (matchingEpisodes.isEmpty()) null
            else {
                val first = matchingEpisodes.first()
                DownloadGroupItem.SeriesGroup(
                    subjectId = selectedSeriesId!!,
                    title = first.title,
                    coverUrl = first.coverUrl,
                    episodes = matchingEpisodes.sortedWith(compareBy({ it.se }, { it.ep }))
                )
            }
        }
    }

    // If a series is selected, show dedicated Series Episodes Screen
    if (selectedSeriesGroup != null) {
        SeriesDetailDownloadScreen(
            seriesGroup = selectedSeriesGroup,
            downloadSpeeds = downloadSpeeds,
            onBack = { selectedSeriesId = null },
            onPlayEpisode = onPlayDownload,
            onPauseEpisode = { epId -> viewModel.pauseDownload(epId) },
            onResumeEpisode = { epId -> viewModel.resumeDownload(context, epId) },
            onDeleteEpisode = { epId ->
                viewModel.deleteDownload(epId)
                if (selectedSeriesGroup.episodes.size <= 1) {
                    selectedSeriesId = null
                }
            },
            onDownloadMore = {
                onOpenDetail(selectedSeriesGroup.subjectId)
            },
            modifier = modifier
        )
        return
    }

    val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED }
    val completedCount = downloads.count { it.status == DownloadStatus.COMPLETED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Offline Library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Storage indicator badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "App Storage • Offline",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "All" to downloads.size,
                "Active" to activeCount,
                "Completed" to completedCount
            ).forEachIndexed { index, (label, count) ->
                val isSelected = selectedFilter == index
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = index },
                    label = {
                        Text(
                            text = "$label ($count)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("download_filter_$label")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content
        if (groupedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No Downloads Found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Download movies and TV series to watch offline anywhere. Background processing saves media directly to app storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onExploreClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Explore Catalog", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("downloads_list"),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedItems.forEach { groupItem ->
                    when (groupItem) {
                        is DownloadGroupItem.MovieItem -> {
                            item(key = groupItem.entity.id) {
                                DownloadItemCard(
                                    item = groupItem.entity,
                                    speedText = downloadSpeeds[groupItem.entity.id] ?: "",
                                    onPlay = { onPlayDownload(groupItem.entity) },
                                    onPause = { viewModel.pauseDownload(groupItem.entity.id) },
                                    onResume = { viewModel.resumeDownload(context, groupItem.entity.id) },
                                    onDelete = { viewModel.deleteDownload(groupItem.entity.id) }
                                )
                            }
                        }
                        is DownloadGroupItem.SeriesGroup -> {
                            item(key = "series_${groupItem.subjectId}") {
                                SeriesMainCard(
                                    group = groupItem,
                                    onClick = { selectedSeriesId = groupItem.subjectId }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Series Card shown in main Downloads screen:
 * Displays Series thumbnail, Name, Total episodes, Storage, and arrow to enter Series Detail screen.
 */
@Composable
fun SeriesMainCard(
    group: DownloadGroupItem.SeriesGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalBytes = remember(group.episodes) {
        group.episodes.sumOf { it.downloadedBytes }
    }
    val completedEpisodes = remember(group.episodes) {
        group.episodes.count { it.status == DownloadStatus.COMPLETED }
    }
    val hasActiveDownload = remember(group.episodes) {
        group.episodes.any { it.status == DownloadStatus.DOWNLOADING }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("series_main_card_${group.subjectId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Series Cover Thumbnail
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = group.coverUrl,
                    contentDescription = "${group.title} poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "SERIES",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${group.episodes.size} Episodes",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (totalBytes > 0) {
                        Text(
                            text = DownloadStorageHelper.formatFileSize(totalBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasActiveDownload) {
                        val activeEp = group.episodes.find { it.status == DownloadStatus.DOWNLOADING }
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (activeEp != null) "Downloading E${activeEp.ep} (${activeEp.progress}%)" else "Downloading...",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "$completedEpisodes of ${group.episodes.size} downloaded",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Right Chevron Arrow
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View all episodes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (hasActiveDownload) {
            val activeEp = group.episodes.find { it.status == DownloadStatus.DOWNLOADING }
            if (activeEp != null) {
                LinearProgressIndicator(
                    progress = { (activeEp.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Dedicated Screen for Series Episodes:
 * Shows Series Header with Poster, Name, Total Storage, and "Download More" button,
 * plus the full list of downloaded episodes with thumbnails.
 */
@Composable
fun SeriesDetailDownloadScreen(
    seriesGroup: DownloadGroupItem.SeriesGroup,
    downloadSpeeds: Map<String, String>,
    onBack: () -> Unit,
    onPlayEpisode: (DownloadEntity) -> Unit,
    onPauseEpisode: (String) -> Unit,
    onResumeEpisode: (String) -> Unit,
    onDeleteEpisode: (String) -> Unit,
    onDownloadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalBytes = remember(seriesGroup.episodes) {
        seriesGroup.episodes.sumOf { it.downloadedBytes }
    }
    val completedCount = remember(seriesGroup.episodes) {
        seriesGroup.episodes.count { it.status == DownloadStatus.COMPLETED }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("series_detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to downloads",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesGroup.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${seriesGroup.episodes.size} Episodes • ${DownloadStorageHelper.formatFileSize(totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("series_episodes_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Series Header Card with "Download More" button
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(108.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                AsyncImage(
                                    model = seriesGroup.coverUrl,
                                    contentDescription = seriesGroup.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = seriesGroup.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "$completedCount of ${seriesGroup.episodes.size} downloaded",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Total storage: ${DownloadStorageHelper.formatFileSize(totalBytes)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // "Download More Episodes" Action Button
                        Button(
                            onClick = onDownloadMore,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("download_more_episodes_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download More Episodes",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Episodes Section Header
            item {
                Text(
                    text = "Downloaded Episodes (${seriesGroup.episodes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Episodes list
            items(seriesGroup.episodes, key = { it.id }) { ep ->
                val speed = downloadSpeeds[ep.id] ?: ""
                SeriesEpisodeRow(
                    item = ep,
                    seriesCoverUrl = seriesGroup.coverUrl,
                    speedText = speed,
                    onPlay = { onPlayEpisode(ep) },
                    onPause = { onPauseEpisode(ep.id) },
                    onResume = { onResumeEpisode(ep.id) },
                    onDelete = { onDeleteEpisode(ep.id) }
                )
            }
        }
    }
}

/**
 * Individual Episode Row with Thumbnail, Speed, Progress & Play/Delete Controls
 */
@Composable
private fun SeriesEpisodeRow(
    item: DownloadEntity,
    seriesCoverUrl: String,
    speedText: String,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultEst = DownloadStorageHelper.getEstimatedSizeBytes(item.quality, isSeries = true)
    val safeTotalBytes = maxOf(if (item.totalBytes > 0) item.totalBytes else defaultEst, item.downloadedBytes)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("series_episode_row_${item.id}")
            .clickable {
                if (item.status == DownloadStatus.COMPLETED) {
                    onPlay()
                }
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Episode Thumbnail Box
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = item.coverUrl.ifEmpty { seriesCoverUrl },
                        contentDescription = "Episode ${item.ep}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Episode badge overlay
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "E${item.ep}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    if (item.status == DownloadStatus.COMPLETED) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircle,
                                contentDescription = "Play offline",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Details Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (item.episodeTitle.isNotEmpty()) "S${item.se}:E${item.ep} • ${item.episodeTitle}" else "Season ${item.se} • Episode ${item.ep}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = item.quality,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = item.audioTrack.split(" ").first(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        if (item.status == DownloadStatus.COMPLETED) {
                            Text(
                                text = DownloadStorageHelper.formatFileSize(if (item.downloadedBytes > 0) item.downloadedBytes else safeTotalBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status and real-time speed
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.progress}% (${DownloadStorageHelper.formatFileSize(item.downloadedBytes)} / ${DownloadStorageHelper.formatFileSize(safeTotalBytes)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (speedText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ⚡ $speedText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            Text(
                                text = "Paused • ${item.progress}% (${DownloadStorageHelper.formatFileSize(item.downloadedBytes)} / ${DownloadStorageHelper.formatFileSize(safeTotalBytes)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DownloadStatus.COMPLETED -> {
                            Text(
                                text = "Ready offline • ${DownloadStorageHelper.formatFileSize(if (item.downloadedBytes > 0) item.downloadedBytes else safeTotalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Text(
                                text = "Failed. Tap to retry.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                text = "Queued...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action buttons with 48dp touch target
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                        }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(
                                onClick = onPlay,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("play_series_ep_${item.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Progress bar
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (item.status == DownloadStatus.PAUSED) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

/**
 * Standard Movie Download Card with speed display and progress bar
 */
@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    speedText: String = "",
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultEst = DownloadStorageHelper.getEstimatedSizeBytes(item.quality, isSeries = false)
    val safeTotalBytes = maxOf(if (item.totalBytes > 0) item.totalBytes else defaultEst, item.downloadedBytes)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}")
            .clickable {
                if (item.status == DownloadStatus.COMPLETED) {
                    onPlay()
                }
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Thumbnail
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = "${item.title} poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (item.status == DownloadStatus.COMPLETED) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircle,
                                contentDescription = "Play offline",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Metadata Badges: Quality & Audio
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = item.quality,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = item.audioTrack.split(" ").first(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (item.status == DownloadStatus.COMPLETED) {
                            Text(
                                text = DownloadStorageHelper.formatFileSize(if (item.downloadedBytes > 0) item.downloadedBytes else safeTotalBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status Message & Speed
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.progress}% (${DownloadStorageHelper.formatFileSize(item.downloadedBytes)} / ${DownloadStorageHelper.formatFileSize(safeTotalBytes)})",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (speedText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ⚡ $speedText",
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            Text(
                                text = "Paused • ${item.progress}% (${DownloadStorageHelper.formatFileSize(item.downloadedBytes)} / ${DownloadStorageHelper.formatFileSize(safeTotalBytes)})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        DownloadStatus.COMPLETED -> {
                            Text(
                                text = "Ready for offline playback • ${DownloadStorageHelper.formatFileSize(if (item.downloadedBytes > 0) item.downloadedBytes else safeTotalBytes)}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Text(
                                text = "Download failed. Tap to retry.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            Text(
                                text = "Queued...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Action Button (Pause/Resume/Play & Delete) with 48dp touch targets
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause download",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(
                                onClick = onPlay,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Progress bar if downloading or paused
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (item.status == DownloadStatus.PAUSED) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
