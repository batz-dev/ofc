package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.WatchHistoryEntity
import com.example.data.model.BannerItem
import com.example.data.model.MediaItem
import com.example.ui.theme.*

/**
 * Sleek theatrical poster card (2:3 aspect ratio) with refined typography,
 * subtle border rim, and elegant rating badges.
 */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Int = 124
) {
    val cardHeight = (cardWidth * 1.5).toInt()

    Column(
        modifier = modifier
            .width(cardWidth.dp)
            .testTag("media_card_${item.id}")
            .clickable { onClick() }
    ) {
        // Poster Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    RoundedCornerShape(12.dp)
                )
        ) {
            // Poster Image
            AsyncImage(
                model = ImageHelper.getCompressedUrl(item.coverUrl),
                contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top-to-bottom subtle gradient for badge readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Rating badge (top right)
            if (item.score != null && item.score > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Rating score",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", item.score),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Media Type badge (top left)
            val isSeries = item.subjectType == 2
            val isAnime = item.subjectType == 5
            if (isSeries || isAnime) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isAnime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = if (isAnime) "ANIME" else "SERIES",
                        color = if (isAnime) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Metadata: Year • Genre
        val year = item.releaseDate.take(4).ifEmpty { null }
        val genrePrimary = item.genre.split(",").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val metaList = listOfNotNull(year, genrePrimary)

        if (metaList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metaList.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 16:9 Continue Watching Card with clear progress indicator and instant resume tap.
 */
@Composable
fun ContinueWatchingCard(
    item: WatchHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .width(220.dp)
            .testTag("continue_card_${item.id}")
            .clickable { onClick() }
    ) {
        val effectiveCover = item.coverUrl.ifEmpty {
            CatalogData.builtInMediaList.find { it.id == item.subjectId }?.coverUrl ?: ""
        }

        Box(
            modifier = Modifier
                .width(220.dp)
                .height(125.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    RoundedCornerShape(14.dp)
                )
        ) {
            AsyncImage(
                model = ImageHelper.getCompressedUrl(effectiveCover, isBackdrop = true),
                contentDescription = "${item.title} preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // Centered Frosted Play Icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Resume watching ${item.title}",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Progress bar at bottom edge
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val episodeSubtitle = if (item.se > 0 || item.ep > 0) {
            "S${item.se}:E${item.ep} • ${item.episodeTitle.ifEmpty { "Episode" }}"
        } else {
            "${(progress * 100).toInt()}% completed"
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = episodeSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Immersive Hero Banner Carousel with cinematic vignette gradient,
 * typography hierarchy, and refined action buttons.
 */
@Composable
fun HeroBannerCarousel(
    banners: List<BannerItem>,
    onPlayClick: (BannerItem) -> Unit,
    onDetailClick: (BannerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size })

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .testTag("hero_banner_carousel")
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val banner = banners[page]
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageHelper.getCompressedUrl(banner.imageUrl, isBackdrop = true),
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Double Gradient Scrim: Subtle top darkening + deep bottom vignette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                // Content Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .fillMaxWidth()
                ) {
                    // Feature Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 1.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = if (banner.subjectType == 2) "FEATURED SERIES" else "PREMIERE MOVIE",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Display Title
                    Text(
                        text = banner.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons (with 48dp min touch targets)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayClick(banner) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("hero_play_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play icon",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Watch Now",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onDetailClick(banner) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("hero_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Details icon",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Pager indicators with smooth animated width
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(banners.size) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 6.dp, label = "indicator")
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(width)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
}
