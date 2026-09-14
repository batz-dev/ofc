package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clean Animated Shimmer Modifier for placeholder loading skeletons.
 * Replaces intrusive circular progress bars with a subtle, fluid monochrome gradient shine.
 */
fun Modifier.shimmerLoading(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceContainer
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(x = translateAnim - 300f, y = translateAnim - 300f),
        end = Offset(x = translateAnim, y = translateAnim)
    )

    this.background(brush)
}

/**
 * Modern Material 3 Buffering Loader component reserved for Video Player and active stream buffering.
 */
@Composable
fun MaterialBufferingLoader(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    strokeWidth: Dp = 3.5.dp,
    label: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "buffering_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffering_scale"
    )

    Column(
        modifier = modifier
            .semantics { contentDescription = label ?: "Buffering content" }
            .testTag("material_buffering_loader"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(pulseScale)
        ) {
            Box(
                modifier = Modifier
                    .size(size + 12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            )
            CircularProgressIndicator(
                modifier = Modifier.size(size),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = strokeWidth,
                strokeCap = StrokeCap.Round
            )
        }

        if (!label.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shapeRadius: Int = 12
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius.dp))
            .shimmerLoading()
    )
}

@Composable
fun MediaCardSkeleton(
    modifier: Modifier = Modifier,
    cardWidth: Int = 120
) {
    val posterHeight = (cardWidth * 1.5).toInt()
    Card(
        modifier = modifier
            .width(cardWidth.dp)
            .testTag("media_card_buffering"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(posterHeight.dp)
                    .shimmerLoading()
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerLoading()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .shimmerLoading()
                )
            }
        }
    }
}

@Composable
fun HeroBannerSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .shimmerLoading()
            .testTag("hero_banner_buffering"),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmerLoading()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerLoading()
            )
        }
    }
}

@Composable
fun HomeScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen_buffering")
    ) {
        HeroBannerSkeleton()

        Spacer(modifier = Modifier.height(16.dp))

        // Row Section Skeletons
        repeat(2) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerLoading()
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(4) {
                        MediaCardSkeleton(cardWidth = 125)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreenSkeleton(
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("search_screen_buffering")
    ) {
        items(9) {
            MediaCardSkeleton(cardWidth = 110)
        }
    }
}

@Composable
fun DetailScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("detail_screen_buffering")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .shimmerLoading()
        )
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmerLoading()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerLoading()
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmerLoading()
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmerLoading()
                )
            }
        }
    }
}
