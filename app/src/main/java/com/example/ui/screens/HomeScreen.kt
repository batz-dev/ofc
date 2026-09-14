package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.WatchHistoryEntity
import com.example.data.model.BannerItem
import com.example.data.model.HomeData
import com.example.data.model.HomeSection
import com.example.data.model.MediaItem
import com.example.data.repository.MovieRepository
import com.example.ui.components.ContinueWatchingCard
import com.example.ui.components.HeroBannerCarousel
import com.example.ui.components.HomeScreenSkeleton
import com.example.ui.components.MediaCard
import com.example.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val homeData: HomeData) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(private val repository: MovieRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0=All, 1=Movies, 2=TV Shows
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val isDataSaver: StateFlow<Boolean> = repository.isDataSaverEnabled

    private val tabFeedCache = java.util.concurrent.ConcurrentHashMap<Int, HomeData>()

    init {
        loadFeed(0)
    }

    fun toggleDataSaver() {
        repository.setDataSaver(!repository.isDataSaverEnabled.value)
    }

    fun selectTab(tabId: Int) {
        if (_selectedTab.value == tabId) return
        _selectedTab.value = tabId

        val cached = tabFeedCache[tabId]
        if (cached != null) {
            _uiState.value = HomeUiState.Success(cached)
        } else {
            loadFeed(tabId)
        }
    }

    fun loadFeed(tabId: Int, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val cached = tabFeedCache[tabId]
            if (cached != null && !forceRefresh) {
                _uiState.value = HomeUiState.Success(cached)
                return@launch
            }

            if (cached == null) {
                _uiState.value = HomeUiState.Loading
            }

            try {
                val data = repository.getHomeFeed(tabId, forceRefresh = forceRefresh)
                if (data.sections.isNotEmpty() || data.banners.isNotEmpty()) {
                    tabFeedCache[tabId] = data
                    _uiState.value = HomeUiState.Success(data)
                } else if (cached != null) {
                    _uiState.value = HomeUiState.Success(cached)
                } else {
                    _uiState.value = HomeUiState.Error("Unable to fetch catalog. Please verify connection.")
                }
            } catch (e: Exception) {
                if (cached != null) {
                    _uiState.value = HomeUiState.Success(cached)
                } else {
                    _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun deleteHistory(id: String) {
        viewModelScope.launch {
            repository.deleteWatchHistory(id)
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    watchHistoryList: List<WatchHistoryEntity>,
    onMediaClick: (MediaItem) -> Unit,
    onBannerClick: (BannerItem) -> Unit,
    onResumeWatching: (WatchHistoryEntity) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isDataSaver by viewModel.isDataSaver.collectAsState()

    val tabs = listOf(
        0 to "All",
        1 to "Movies",
        2 to "TV Series"
    )

    val currentThemeMode = LocalCurrentThemeMode.current
    val onThemeChange = LocalThemeController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // --- FIXED TOP HEADER: BRAND + ACTION SHORTCUTS + CATEGORY TABS ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                // Top Row: Brand Header & Action Icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand Header with Cinema icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.LocalMovies,
                                    contentDescription = "MovieStream Logo",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "MovieStream",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Theme Switcher Button
                        IconButton(
                            onClick = {
                                val nextMode = when (currentThemeMode) {
                                    ThemeMode.DARK -> ThemeMode.LIGHT
                                    ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                    ThemeMode.SYSTEM -> ThemeMode.DARK
                                }
                                onThemeChange(nextMode)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("theme_toggle_button")
                        ) {
                            val themeIcon = when (currentThemeMode) {
                                ThemeMode.DARK -> Icons.Outlined.LightMode
                                ThemeMode.LIGHT -> Icons.Outlined.DarkMode
                                ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                            }
                            Icon(
                                imageVector = themeIcon,
                                contentDescription = "Switch theme mode (currently $currentThemeMode)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Quick Search Icon Button
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("home_search_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search Movies and Shows",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Bottom Row: Category Navigation Tabs (All, Movies, TV Series, Data Saver)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (tabId, label) ->
                        val isSelected = selectedTab == tabId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tabId) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("tab_${label.lowercase()}")
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Data Saver Filter Chip
                    FilterChip(
                        selected = isDataSaver,
                        onClick = { viewModel.toggleDataSaver() },
                        label = {
                            Text(
                                text = if (isDataSaver) "Saver" else "Saver Off",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Data Saver status indicator",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isDataSaver,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("data_saver_chip")
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )

        // --- MAIN FEED CONTENT ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    HomeScreenSkeleton()
                }

                is HomeUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry icon",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Unable to connect",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.loadFeed(selectedTab) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("home_retry_button")
                        ) {
                            Text("Retry", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is HomeUiState.Success -> {
                    val data = state.homeData

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_scroll_list"),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        // Hero Banner Carousel
                        if (data.banners.isNotEmpty()) {
                            item {
                                HeroBannerCarousel(
                                    banners = data.banners,
                                    onPlayClick = onBannerClick,
                                    onDetailClick = onBannerClick
                                )
                            }
                        }

                        // Continue Watching Rail
                        if (watchHistoryList.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Continue Watching",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text = "${watchHistoryList.size} in progress",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(watchHistoryList, key = { it.id }) { historyItem ->
                                            ContinueWatchingCard(
                                                item = historyItem,
                                                onClick = { onResumeWatching(historyItem) },
                                                onDelete = { viewModel.deleteHistory(historyItem.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Dynamic Curated Sections
                        items(data.sections) { section ->
                            SectionItem(
                                section = section,
                                onMediaClick = onMediaClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionItem(
    section: HomeSection,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )

            Text(
                text = "${section.items.size} titles",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(section.items, key = { it.id }) { media ->
                MediaCard(
                    item = media,
                    onClick = { onMediaClick(media) }
                )
            }
        }
    }
}
