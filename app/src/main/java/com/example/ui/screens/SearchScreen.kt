package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaItem
import com.example.data.repository.MovieRepository
import com.example.data.repository.SearchHistoryManager
import com.example.ui.components.MediaCard
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val items: List<MediaItem>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val repository: MovieRepository,
    private val searchHistoryManager: SearchHistoryManager? = null
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _subjectType = MutableStateFlow(0) // 0=All, 1=Movie, 2=Series, 5=Anime
    val subjectType: StateFlow<Int> = _subjectType.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = searchHistoryManager?.recentSearches ?: MutableStateFlow(emptyList())

    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var searchRequestId = 0L

    val popularSearches = listOf(
        "Avengers", "Breaking Bad", "Spider-Man", "Interstellar", "Stranger Things", "Inception", "Anime"
    )

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        val trimmed = newQuery.trim()
        if (trimmed.isBlank()) {
            suggestJob?.cancel()
            searchJob?.cancel()
            _suggestions.value = emptyList()
            _uiState.value = SearchUiState.Idle
            return
        }

        // Fetch suggestions debounced (150ms)
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(150)
            try {
                val list = repository.getSuggestions(trimmed)
                _suggestions.value = list
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }

        // Debounced search (350ms) to avoid intermediate flicker while typing
        searchJob?.cancel()
        val currentId = ++searchRequestId
        searchJob = viewModelScope.launch {
            delay(350)
            executeSearch(trimmed, _subjectType.value, currentId)
        }
    }

    fun selectSubjectType(type: Int) {
        _subjectType.value = type
        val trimmed = _query.value.trim()
        if (trimmed.isNotBlank()) {
            searchJob?.cancel()
            val currentId = ++searchRequestId
            searchJob = viewModelScope.launch {
                executeSearch(trimmed, type, currentId)
            }
        }
    }

    fun submitSearch(searchQuery: String = _query.value) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) return
        _query.value = trimmed
        _suggestions.value = emptyList()
        searchHistoryManager?.addSearch(trimmed)

        searchJob?.cancel()
        val currentId = ++searchRequestId
        searchJob = viewModelScope.launch {
            executeSearch(trimmed, _subjectType.value, currentId)
        }
    }

    private suspend fun executeSearch(keyword: String, type: Int, reqId: Long) {
        if (reqId != searchRequestId) return
        _uiState.value = SearchUiState.Loading
        try {
            val results = repository.search(keyword, page = 1, subjectType = type)
            if (reqId == searchRequestId) {
                _uiState.value = SearchUiState.Success(results)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (reqId == searchRequestId) {
                val fallbackResults = repository.searchLocalCatalog(keyword, type)
                _uiState.value = SearchUiState.Success(fallbackResults)
            }
        }
    }

    fun removeRecentSearch(term: String) {
        searchHistoryManager?.removeSearch(term)
    }

    fun clearRecentSearches() {
        searchHistoryManager?.clearAll()
    }

    fun clearSearch() {
        searchJob?.cancel()
        suggestJob?.cancel()
        _query.value = ""
        _suggestions.value = emptyList()
        _uiState.value = SearchUiState.Idle
    }
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val subjectType by viewModel.subjectType.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val filters = listOf(
        0 to "All",
        1 to "Movies",
        2 to "Series",
        5 to "Anime"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Search Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )

                TextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = {
                        Text(
                            "Search movies, shows, genres...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.submitSearch()
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input_field")
                )

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearSearch() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("search_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Filter Pills
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { (type, label) ->
                val isSelected = subjectType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectSubjectType(type) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
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
                    modifier = Modifier.testTag("filter_$label")
                )
            }
        }

        // Suggestions row
        if (suggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { sugg ->
                    SuggestionChip(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.submitSearch(sugg)
                        },
                        label = {
                            Text(
                                sugg,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.testTag("suggestion_${sugg.take(10)}")
                    )
                }
            }
        }

        // Main content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp)
                    ) {
                        if (recentSearches.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp
                                )
                                TextButton(
                                    onClick = { viewModel.clearRecentSearches() },
                                    modifier = Modifier.testTag("clear_recent_searches_button")
                                ) {
                                    Text(
                                        text = "Clear all",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                recentSearches.take(8).forEach { term ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                focusManager.clearFocus()
                                                viewModel.submitSearch(term)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                            .testTag("recent_search_item_$term"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = term,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.removeRecentSearch(term) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("delete_recent_$term")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove $term",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        Text(
                            text = "Popular Searches",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.popularSearches) { pop ->
                                SuggestionChip(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.submitSearch(pop)
                                    },
                                    label = {
                                        Text(
                                            text = pop,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.testTag("popular_$pop")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Find your next favorite movie or TV series",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is SearchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }

                is SearchUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is SearchUiState.Success -> {
                    if (state.items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = "No matches found for \"$query\"",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Try searching for one of these popular titles:",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    items(viewModel.popularSearches) { pop ->
                                        SuggestionChip(
                                            onClick = {
                                                focusManager.clearFocus()
                                                viewModel.submitSearch(pop)
                                            },
                                            label = {
                                                Text(
                                                    text = pop,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                                            ),
                                            border = SuggestionChipDefaults.suggestionChipBorder(
                                                enabled = true,
                                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(110.dp),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_results_grid")
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                MediaCard(
                                    item = item,
                                    onClick = { onMediaClick(item) },
                                    cardWidth = 110
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
