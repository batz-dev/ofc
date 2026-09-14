package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.download.DownloadStorageHelper
import com.example.data.model.EpisodeInfo
import com.example.data.model.SeasonInfo
import com.example.ui.theme.*

data class EpisodeDownloadItem(
    val se: Int,
    val ep: Int,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsDialog(
    title: String,
    coverUrl: String,
    isSeries: Boolean,
    seasons: List<SeasonInfo> = emptyList(),
    availableQualities: List<String> = emptyList(),
    availableStreams: List<com.example.data.model.StreamOption> = emptyList(),
    initialSeason: Int = 1,
    initialEpisode: Int = 1,
    initialAudio: String = "English (Original)",
    onDismiss: () -> Unit,
    onConfirm: (quality: String, audioTrack: String, episodes: List<EpisodeDownloadItem>) -> Unit
) {
    val standardOptions = listOf(
        "1080p" to "FHD",
        "720p" to "HD",
        "480p" to "SD",
        "360p" to "Saver"
    )

    val qualityOptions = remember(availableQualities) {
        if (availableQualities.isNotEmpty()) {
            val normalized = availableQualities.map { it.lowercase().trim() }
            val matched = mutableListOf<Pair<String, String>>()
            if (normalized.any { it.contains("2160") || it.contains("4k") }) {
                matched.add("4K" to "UHD")
            }
            if (normalized.any { it == "1080p" || it == "1080" }) {
                matched.add("1080p" to "FHD")
            }
            if (normalized.any { it == "720p" || it == "720" }) {
                matched.add("720p" to "HD")
            }
            if (normalized.any { it == "480p" || it == "480" }) {
                matched.add("480p" to "SD")
            }
            if (normalized.any { it == "360p" || it == "360" }) {
                matched.add("360p" to "Saver")
            }
            if (matched.isEmpty()) {
                availableQualities.forEach { q ->
                    matched.add(q to "HD")
                }
            }
            matched
        } else {
            standardOptions
        }
    }

    var selectedQuality by remember(qualityOptions) {
        mutableStateOf(qualityOptions.firstOrNull()?.first ?: "1080p")
    }
    var selectedAudio by remember(initialAudio) { mutableStateOf(initialAudio) }

    fun getQualitySizeBytes(res: String): Long {
        val resInt = res.replace("p", "", ignoreCase = true).replace("k", "000", ignoreCase = true).toIntOrNull() ?: 1080
        val matchedStream = availableStreams.find { it.resolution == resInt && it.sizeBytes > 0L }
            ?: availableStreams.find { it.sizeBytes > 0L && "${it.resolution}p".equals(res, ignoreCase = true) }
        return if (matchedStream != null && matchedStream.sizeBytes > 0L) {
            matchedStream.sizeBytes
        } else {
            DownloadStorageHelper.getEstimatedSizeBytes(res, isSeries = isSeries)
        }
    }

    // Flatten all episodes or current season episodes
    val currentSeason = seasons.find { it.seasonNumber == initialSeason } ?: seasons.firstOrNull()
    val allEpisodes = remember(seasons) {
        if (seasons.isNotEmpty()) {
            seasons.flatMap { s ->
                s.episodes.map { ep ->
                    EpisodeDownloadItem(
                        se = s.seasonNumber,
                        ep = ep.episodeNumber,
                        title = ep.title.ifEmpty { "Episode ${ep.episodeNumber}" }
                    )
                }
            }
        } else if (isSeries) {
            // Fallback generated list if seasons not loaded yet
            (1..10).map { epNum ->
                EpisodeDownloadItem(se = 1, ep = epNum, title = "Episode $epNum")
            }
        } else {
            emptyList()
        }
    }

    // Selected episode IDs: "se_ep"
    val selectedEpisodeSet = remember {
        mutableStateListOf<String>().apply {
            if (isSeries) {
                // Default to first episode or target episode
                add("${initialSeason}_${initialEpisode}")
            }
        }
    }

    val selectedCount = if (isSeries) selectedEpisodeSet.size else 1
    val singleItemSizeBytes = getQualitySizeBytes(selectedQuality)
    val totalDownloadSizeBytes = singleItemSizeBytes * selectedCount.coerceAtLeast(1)
    val formattedTotalSize = DownloadStorageHelper.formatFileSize(totalDownloadSizeBytes)

    val audioOptions = listOf(
        "English (Original)",
        "Spanish (Español)",
        "French (Français)",
        "Hindi (हिन्दी)",
        "German (Deutsch)",
        "Default Audio"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("download_options_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp, 68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "$title cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Options",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_download_options")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close dialog",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Quality Selector + Total Size Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HighQuality,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Select Quality",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Est. Total: ~$formattedTotalSize",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualityOptions.forEach { (res, label) ->
                    val isSelected = selectedQuality == res
                    val itemSize = DownloadStorageHelper.formatFileSize(getQualitySizeBytes(res))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedQuality = res }
                            .testTag("download_quality_$res")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = res,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "~$itemSize",
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: Audio Track Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Select Audio Track",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(audioOptions) { audio ->
                    val isSelected = selectedAudio == audio
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedAudio = audio }
                            .testTag("download_audio_$audio")
                    ) {
                        Text(
                            text = audio,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Section 3: For TV Series, Select How Many Episodes
            if (isSeries) {
                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "How Many Episodes?",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "${selectedEpisodeSet.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "First 1" to 1,
                        "First 3" to 3,
                        "First 5" to 5,
                        "All (${allEpisodes.size})" to allEpisodes.size
                    ).forEach { (label, count) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedEpisodeSet.clear()
                                    allEpisodes.take(count).forEach { ep ->
                                        selectedEpisodeSet.add("${ep.se}_${ep.ep}")
                                    }
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable episode list checkboxes
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allEpisodes, key = { "${it.se}_${it.ep}" }) { epItem ->
                        val epKey = "${epItem.se}_${epItem.ep}"
                        val isChecked = selectedEpisodeSet.contains(epKey)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isChecked) {
                                        selectedEpisodeSet.remove(epKey)
                                    } else {
                                        selectedEpisodeSet.add(epKey)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedEpisodeSet.add(epKey) else selectedEpisodeSet.remove(epKey)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = "S${epItem.se}:E${epItem.ep} • ${epItem.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage Folder & Total Size Summary Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "App Private Storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Total Size: ~$formattedTotalSize",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download Trigger Button
            val canDownload = if (isSeries) selectedEpisodeSet.isNotEmpty() else true
            val buttonLabel = if (isSeries) {
                "Download $selectedCount Episodes • ~$formattedTotalSize"
            } else {
                "Download Movie • ~$formattedTotalSize ($selectedQuality)"
            }

            Button(
                onClick = {
                    val targetEpisodes = if (isSeries) {
                        allEpisodes.filter { selectedEpisodeSet.contains("${it.se}_${it.ep}") }
                    } else {
                        listOf(EpisodeDownloadItem(se = 0, ep = 0, title = title))
                    }
                    onConfirm(selectedQuality, selectedAudio, targetEpisodes)
                },
                enabled = canDownload,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_download_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
