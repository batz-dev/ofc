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
    private val downloadDao: DownloadDao,
    private val context: Context? = null
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
            // Prioritize exact resolution match first
            val exactDirectMatch = streams.find { !it.isDash && it.directUrl.isNotEmpty() && it.resolution == requestedRes }
            val exactDashMatch = streams.find { it.isDash && it.mpdUrl.isNotEmpty() && it.resolution == requestedRes }
            val matchedStream = exactDirectMatch
                ?: exactDashMatch
                ?: streams.find { it.resolution == requestedRes }
                ?: streams.minByOrNull { kotlin.math.abs(it.resolution - requestedRes) }
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
                    ?: CatalogData.sampleStreams.minByOrNull { kotlin.math.abs(it.resolution - requestedRes) }
                    ?: CatalogData.sampleStreams.firstOrNull()
                if (fallbackStream != null) {
                    resolvedDownloadUrl = fallbackStream.directUrl
                    if (resolvedSizeBytes <= 0L && fallbackStream.sizeBytes > 0L) {
                        resolvedSizeBytes = fallbackStream.sizeBytes
                    }
                }
            }
        }

        if (resolvedSizeBytes <= 0L) {
            val isSeries = (se > 0 || ep > 0)
            resolvedSizeBytes = com.example.data.download.DownloadStorageHelper.getEstimatedSizeBytes(quality, isSeries = isSeries)
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
        val resolvedCover = if (coverUrl.isNotBlank()) coverUrl
            else mediaItemCache[subjectId]?.coverUrl
            ?: mediaDetailCache[subjectId]?.backdropUrl
            ?: mediaDetailCache[subjectId]?.coverUrl
            ?: CatalogData.builtInMediaList.find { it.id == subjectId }?.coverUrl
            ?: ""

        watchHistoryDao.insertHistory(
            WatchHistoryEntity(
                id = id,
                subjectId = subjectId,
                title = title,
                coverUrl = resolvedCover,
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

    private val homeFeedCacheManager = context?.let { HomeFeedCacheManager(it) }

    init {
        // Pre-populate cache with built-in catalog
        for (item in CatalogData.builtInMediaList) {
            mediaItemCache[item.id] = item
        }
        // Pre-warm memory cache with persistent disk feeds for instant cold-start
        homeFeedCacheManager?.let { mgr ->
            for (tab in 0..3) {
                val diskData = mgr.loadHomeFeed(tab)
                if (diskData != null && (diskData.banners.isNotEmpty() || diskData.sections.isNotEmpty())) {
                    homeFeedCache[tab] = Pair(System.currentTimeMillis(), diskData)
                    diskData.sections.forEach { sec ->
                        sec.items.forEach { item -> mediaItemCache[item.id] = item }
                    }
                }
            }
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
            // Check persistent disk cache for immediate offline or cold start return
            val diskData = homeFeedCacheManager?.loadHomeFeed(tabId)
            if (diskData != null && (diskData.banners.isNotEmpty() || diskData.sections.isNotEmpty())) {
                homeFeedCache[tabId] = Pair(now, diskData)
                diskData.sections.forEach { sec ->
                    sec.items.forEach { item -> mediaItemCache[item.id] = item }
                }
                return diskData
            }
        }

        try {
            val remote = apiService.getHomeFeed(tabId)
            if (remote.sections.isNotEmpty() || remote.banners.isNotEmpty()) {
                // Cache items in memory and persistent disk
                remote.sections.forEach { sec ->
                    sec.items.forEach { item -> mediaItemCache[item.id] = item }
                }
                homeFeedCache[tabId] = Pair(now, remote)
                homeFeedCacheManager?.saveHomeFeed(tabId, remote)
                return remote
            }
        } catch (_: Exception) { }

        // If remote failed (e.g. offline), try disk cache first
        val diskData = homeFeedCacheManager?.loadHomeFeed(tabId)
        if (diskData != null && (diskData.banners.isNotEmpty() || diskData.sections.isNotEmpty())) {
            homeFeedCache[tabId] = Pair(now, diskData)
            return diskData
        }

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

    // Query caching for instantaneous response and data conservation
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, List<MediaItem>>()

    suspend fun search(keyword: String, page: Int = 1, subjectType: Int = 0): List<MediaItem> {
        val clean = keyword.trim()
        if (clean.isEmpty()) return emptyList()

        val cacheKey = "${clean.lowercase()}_${page}_${subjectType}"
        val cached = searchCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            return cached
        }

        // 1. Attempt remote API search
        var remoteResults: List<MediaItem> = emptyList()
        try {
            remoteResults = apiService.search(clean, page, perPage = 20, subjectType = subjectType)
            if (remoteResults.isNotEmpty()) {
                remoteResults.forEach { mediaItemCache[it.id] = it }
                searchCache[cacheKey] = remoteResults
                return remoteResults
            }
        } catch (_: Exception) { }

        // If specific subjectType filter returned empty on remote, try unfiltered remote search
        if (remoteResults.isEmpty() && subjectType != 0) {
            try {
                val broadRemote = apiService.search(clean, page, perPage = 20, subjectType = 0)
                if (broadRemote.isNotEmpty()) {
                    broadRemote.forEach { mediaItemCache[it.id] = it }
                    val filtered = broadRemote.filter { it.subjectType == subjectType }
                    val result = if (filtered.isNotEmpty()) filtered else broadRemote
                    searchCache[cacheKey] = result
                    return result
                }
            } catch (_: Exception) { }
        }

        // 2. Resilient Hybrid Fallback: Search local cache & built-in catalog with fuzzy matching
        val localMatches = searchLocalCatalog(clean, subjectType)
        if (localMatches.isNotEmpty()) {
            searchCache[cacheKey] = localMatches
        }
        return localMatches
    }

    fun searchLocalCatalog(query: String, subjectType: Int = 0): List<MediaItem> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return emptyList()

        // Normalize query: remove punctuation, extract keywords
        val normalizedQuery = clean.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        val queryTokens = normalizedQuery.split(" ").filter { it.length >= 2 }
        val compactQuery = clean.replace(Regex("[^a-z0-9]"), "")

        val allAvailable = (mediaItemCache.values + CatalogData.builtInMediaList).distinctBy { it.id }

        data class ScoredItem(val item: MediaItem, val score: Int)
        val scoredList = mutableListOf<ScoredItem>()

        for (item in allAvailable) {
            // Apply subjectType filter if requested
            if (subjectType != 0 && item.subjectType != subjectType) {
                continue
            }

            val titleLower = item.title.lowercase()
            val normalizedTitle = titleLower.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
            val compactTitle = titleLower.replace(Regex("[^a-z0-9]"), "")
            val genreLower = item.genre.lowercase()
            val descLower = item.description.lowercase()

            var score = 0

            // 1. Exact or Prefix Title Matching
            if (titleLower == clean || normalizedTitle == normalizedQuery) {
                score += 1000
            } else if (titleLower.startsWith(clean) || normalizedTitle.startsWith(normalizedQuery)) {
                score += 600
            } else if (titleLower.contains(clean) || normalizedTitle.contains(normalizedQuery)) {
                score += 400
            } else if (compactQuery.isNotEmpty() && compactTitle.contains(compactQuery)) {
                // Matches "spiderman" with "spider-man" or "spider man"
                score += 350
            }

            // 2. Token Matching (multi-word searches like "avengers endgame", "stranger things 4")
            if (queryTokens.isNotEmpty()) {
                var tokensInTitle = 0
                var tokensInOther = 0

                for (token in queryTokens) {
                    if (normalizedTitle.contains(token) || compactTitle.contains(token)) {
                        tokensInTitle++
                    } else if (genreLower.contains(token) || descLower.contains(token)) {
                        tokensInOther++
                    }
                }

                if (tokensInTitle == queryTokens.size) {
                    score += 300
                } else {
                    score += tokensInTitle * 60
                }
                score += tokensInOther * 20
            }

            // 3. Fallback partial description & genre search
            if (score == 0) {
                if (genreLower.contains(clean) || descLower.contains(clean)) {
                    score += 50
                }
            }

            if (score > 0) {
                val ratingBonus = ((item.score ?: 7.0) * 2).toInt()
                score += ratingBonus
                scoredList.add(ScoredItem(item, score))
            }
        }

        // If no matches found with strict filter, relax filter to ensure user never gets false empty state
        if (scoredList.isEmpty() && subjectType != 0) {
            return searchLocalCatalog(query, subjectType = 0)
        }

        return scoredList
            .sortedByDescending { it.score }
            .map { it.item }
    }

    suspend fun getSuggestions(keyword: String): List<String> {
        val clean = keyword.trim()
        if (clean.isEmpty()) return emptyList()

        val results = mutableListOf<String>()

        // 1. Live search-suggest API
        try {
            val remote = apiService.getSuggest(clean)
            results.addAll(remote)
        } catch (_: Exception) { }

        // 2. Match local catalog titles
        val lowerClean = clean.lowercase()
        val compactClean = lowerClean.replace(Regex("[^a-z0-9]"), "")
        val normalizedClean = lowerClean.replace(Regex("[^a-z0-9\\s]"), " ").trim()

        val allItems = (mediaItemCache.values + CatalogData.builtInMediaList).distinctBy { it.id }
        val matchingTitles = allItems
            .filter { item ->
                val titleLower = item.title.lowercase()
                val compactTitle = titleLower.replace(Regex("[^a-z0-9]"), "")
                titleLower.contains(lowerClean) ||
                (compactClean.isNotEmpty() && compactTitle.contains(compactClean)) ||
                (normalizedClean.isNotEmpty() && titleLower.contains(normalizedClean))
            }
            .map { it.title }

        results.addAll(matchingTitles)

        return results
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(8)
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
