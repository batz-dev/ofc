package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.data.download.DownloadStorageHelper
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MovieApiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var authToken: String? = null
    private val tokenMutex = Mutex()

    companion object {
        private const val TAG = "MovieApiService"
    }

    private suspend fun getOrRefreshToken(forceRefresh: Boolean = false): String? {
        return tokenMutex.withLock {
            if (!forceRefresh && !authToken.isNullOrEmpty()) {
                return@withLock authToken
            }

            // Bootstrap token from tab-operating
            for (base in listOf(MovieSigner.PRIMARY_BASE_URL) + MovieSigner.FALLBACK_URLS) {
                val url = "$base/wefeed-mobile-bff/tab-operating?page=1&tabId=0&version="
                try {
                    val headers = MovieSigner.buildHeaders("GET", url)
                    val reqBuilder = Request.Builder().url(url).get()
                    headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                    val response = withContext(Dispatchers.IO) {
                        client.newCall(reqBuilder.build()).execute()
                    }

                    val xUser = response.header("x-user") ?: response.header("X-User")
                    response.close()

                    if (!xUser.isNullOrEmpty()) {
                        val obj = JSONObject(xUser)
                        val token = obj.optString("token")
                        if (token.isNotEmpty()) {
                            authToken = token
                            Log.d(TAG, "Bootstrapped auth token successfully from $base")
                            return@withLock token
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed token bootstrap from $base: ${e.message}")
                }
            }
            null
        }
    }

    private suspend fun executeSignedRequest(
        method: String,
        path: String,
        queryParams: Map<String, String>? = null,
        bodyJson: String? = null
    ): JSONObject? {
        var token = getOrRefreshToken()

        val queryString = queryParams?.entries?.joinToString("&") { "${it.key}=${it.value}" }
        val allHosts = listOf(MovieSigner.PRIMARY_BASE_URL) + MovieSigner.FALLBACK_URLS

        for (host in allHosts) {
            val fullUrl = if (!queryString.isNullOrEmpty()) "$host$path?$queryString" else "$host$path"
            try {
                val contentTypeStr = "application/json"
                var headers = MovieSigner.buildHeaders(method, fullUrl, bodyJson, token, contentType = contentTypeStr)
                var reqBuilder = Request.Builder().url(fullUrl)
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }

                if (method.equals("POST", ignoreCase = true)) {
                    val rawBytes = (bodyJson ?: "{}").toByteArray(Charsets.UTF_8)
                    val body = rawBytes.toRequestBody(contentTypeStr.toMediaType())
                    reqBuilder.post(body)
                } else {
                    reqBuilder.get()
                }

                var response = withContext(Dispatchers.IO) {
                    client.newCall(reqBuilder.build()).execute()
                }

                // If 401 Unauthorized, 407 Signature invalid, or 441 Miss token, refresh token and retry once
                if (response.code == 401 || response.code == 407 || response.code == 441) {
                    response.close()
                    token = getOrRefreshToken(forceRefresh = true)
                    headers = MovieSigner.buildHeaders(method, fullUrl, bodyJson, token, contentType = contentTypeStr)
                    reqBuilder = Request.Builder().url(fullUrl)
                    headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                    if (method.equals("POST", ignoreCase = true)) {
                        val rawBytes = (bodyJson ?: "{}").toByteArray(Charsets.UTF_8)
                        val body = rawBytes.toRequestBody(contentTypeStr.toMediaType())
                        reqBuilder.post(body)
                    } else {
                        reqBuilder.get()
                    }
                    response = withContext(Dispatchers.IO) {
                        client.newCall(reqBuilder.build()).execute()
                    }
                }

                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: ""
                    response.close()
                    if (rawBody.isNotEmpty()) {
                        return JSONObject(rawBody)
                    }
                } else {
                    Log.w(TAG, "Request to $host$path failed with HTTP ${response.code}: ${response.message}")
                    response.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request to $host$path failed: ${e.message}")
            }
        }
        return null
    }

    suspend fun getHomeFeed(tabId: Int = 0): HomeData {
        val root = executeSignedRequest(
            method = "GET",
            path = "/wefeed-mobile-bff/tab-operating",
            queryParams = mapOf("page" to "1", "tabId" to tabId.toString(), "version" to "")
        ) ?: return HomeData(emptyList(), emptyList())

        val data = root.optJSONObject("data") ?: return HomeData(emptyList(), emptyList())
        val rawItems = data.optJSONArray("items") ?: JSONArray()

        val banners = mutableListOf<BannerItem>()
        val sections = mutableListOf<HomeSection>()

        for (i in 0 until rawItems.length()) {
            val itemObj = rawItems.optJSONObject(i) ?: continue
            val itemType = itemObj.optString("type")
            val title = itemObj.optString("title")

            // Skip non-family or adult filter
            val lowerTitle = title.lowercase()
            if (lowerTitle.contains("short tv") || lowerTitle.contains("18+") || lowerTitle.contains("adult")) {
                continue
            }

            if (itemType.equals("BANNER", ignoreCase = true)) {
                val bannerObj = itemObj.optJSONObject("banner")
                val bannerArr = bannerObj?.optJSONArray("banners") ?: JSONArray()
                for (b in 0 until bannerArr.length()) {
                    val bObj = bannerArr.optJSONObject(b) ?: continue
                    val imgObj = bObj.optJSONObject("image")
                    val imgUrl = imgObj?.optString("url") ?: ""
                    val bTitle = bObj.optString("title").ifEmpty { title }
                    val sid = bObj.optString("subjectId")
                    val sType = bObj.optInt("subjectType", 1)
                    if (imgUrl.isNotEmpty()) {
                        banners.add(
                            BannerItem(
                                id = "b_$b",
                                title = bTitle,
                                imageUrl = imgUrl,
                                subjectId = sid,
                                subjectType = sType
                            )
                        )
                    }
                }
            } else {
                val subjectsArr = itemObj.optJSONArray("subjects") ?: JSONArray()
                val mediaList = mutableListOf<MediaItem>()
                for (s in 0 until subjectsArr.length()) {
                    val sObj = subjectsArr.optJSONObject(s) ?: continue
                    val sid = sObj.optString("subjectId").ifEmpty { sObj.optString("id") }
                    val sTitle = sObj.optString("title")
                    val cover = sObj.optJSONObject("cover")?.optString("url") ?: sObj.optString("coverUrl")
                    val sType = sObj.optInt("subjectType", 1)
                    val score = if (sObj.has("score")) sObj.optDouble("score") else null
                    val releaseDate = sObj.optString("releaseDate")
                    val genre = sObj.optString("genre")
                    val desc = sObj.optString("description").ifEmpty { sObj.optString("desc") }

                    if (sid.isNotEmpty() && sTitle.isNotEmpty()) {
                        mediaList.add(
                            MediaItem(
                                id = sid,
                                title = sTitle,
                                coverUrl = cover,
                                description = desc,
                                subjectType = sType,
                                score = if (score != null && score > 0) score else null,
                                releaseDate = releaseDate,
                                genre = genre
                            )
                        )
                    }
                }

                if (mediaList.isNotEmpty()) {
                    sections.add(
                        HomeSection(
                            title = if (title.isNotEmpty()) title else "Featured",
                            type = itemType,
                            items = mediaList
                        )
                    )
                }
            }
        }

        return HomeData(banners = banners, sections = sections)
    }

    suspend fun search(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20,
        subjectType: Int = 0
    ): List<MediaItem> {
        val safePerPage = perPage.coerceIn(1, 20)
        val bodyObj = JSONObject().apply {
            put("keyword", keyword)
            put("page", page)
            put("perPage", safePerPage)
            put("subjectType", subjectType)
        }

        val root = executeSignedRequest(
            method = "POST",
            path = "/wefeed-mobile-bff/subject-api/search",
            bodyJson = bodyObj.toString()
        ) ?: return emptyList()

        val data = root.optJSONObject("data") ?: return emptyList()
        val itemsArr = data.optJSONArray("items") ?: data.optJSONArray("list") ?: JSONArray()
        val result = mutableListOf<MediaItem>()

        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.optJSONObject(i) ?: continue
            val sid = obj.optString("subjectId").ifEmpty { obj.optString("id") }
            val title = obj.optString("title")
            val cover = obj.optJSONObject("cover")?.optString("url")
                ?: obj.optString("coverUrl")
                ?: obj.optString("cover")
            val sType = obj.optInt("subjectType", 1)
            val score = if (obj.has("imdbRatingValue")) {
                obj.optString("imdbRatingValue").toDoubleOrNull()
            } else if (obj.has("score")) {
                obj.optDouble("score").takeIf { it > 0 }
            } else null
            val releaseDate = obj.optString("releaseDate").ifEmpty { obj.optString("year") }
            val genre = obj.optString("genre")
            val desc = obj.optString("description").ifEmpty { obj.optString("desc") }

            if (sid.isNotEmpty() && title.isNotEmpty()) {
                result.add(
                    MediaItem(
                        id = sid,
                        title = title,
                        coverUrl = cover,
                        description = desc,
                        subjectType = sType,
                        score = score,
                        releaseDate = releaseDate,
                        genre = genre
                    )
                )
            }
        }
        return result
    }

    suspend fun getSuggest(keyword: String): List<String> {
        if (keyword.isEmpty()) return emptyList()
        val root = executeSignedRequest(
            method = "GET",
            path = "/wefeed-mobile-bff/subject-api/search-suggest",
            queryParams = mapOf("keyword" to keyword, "perPage" to "8")
        ) ?: return emptyList()

        val data = root.optJSONObject("data") ?: return emptyList()
        val itemsArr = data.optJSONArray("items") ?: data.optJSONArray("list") ?: JSONArray()
        val list = mutableListOf<String>()
        for (i in 0 until itemsArr.length()) {
            val item = itemsArr.opt(i)
            if (item is String) {
                list.add(item)
            } else if (item is JSONObject) {
                val title = item.optString("word")
                    .ifEmpty { item.optString("title") }
                    .ifEmpty { item.optString("keyword") }
                if (title.isNotEmpty()) list.add(title)
            }
        }
        return list
    }

    suspend fun getSubjectDetail(subjectId: String): MediaDetail? {
        val root = executeSignedRequest(
            method = "GET",
            path = "/wefeed-mobile-bff/subject-api/get",
            queryParams = mapOf("subjectId" to subjectId, "host" to "api.inmoviebox.com")
        ) ?: executeSignedRequest(
            method = "GET",
            path = "/wefeed-mobile-bff/subject-api/get",
            queryParams = mapOf("subjectId" to subjectId)
        ) ?: return null

        val data = root.optJSONObject("data") ?: return null
        val title = data.optString("title")
        val desc = data.optString("description")
            .ifEmpty { data.optString("desc") }
            .ifEmpty { data.optString("introduction") }

        val cover = data.optJSONObject("cover")?.optString("url") ?: data.optString("coverUrl")
        val backdrop = data.optJSONObject("backdrop")?.optString("url")
            ?: data.optJSONObject("banner")?.optString("url")
            ?: cover

        val releaseDate = data.optString("releaseDate")
        val score = if (data.has("imdbRatingValue")) {
            data.optString("imdbRatingValue").toDoubleOrNull()
        } else if (data.has("score")) {
            data.optDouble("score").takeIf { it > 0 }
        } else null
        val genre = data.optString("genre")
        val duration = data.optInt("duration", 0)
        val country = data.optString("country")
        val subjectType = data.optInt("subjectType", 1)

        val dubsList = mutableListOf<DubEdition>()
        val dubsArr = data.optJSONArray("dubs") ?: JSONArray()
        for (i in 0 until dubsArr.length()) {
            val dObj = dubsArr.optJSONObject(i) ?: continue
            val dId = dObj.optString("subjectId")
            val dLang = dObj.optString("lanName").ifEmpty { dObj.optString("lan") }
            val dTitle = dObj.optString("title")
            if (dId.isNotEmpty()) {
                dubsList.add(DubEdition(subjectId = dId, language = dLang, title = dTitle))
            }
        }

        return MediaDetail(
            id = subjectId,
            title = title,
            description = desc,
            coverUrl = cover,
            backdropUrl = backdrop,
            releaseDate = releaseDate,
            score = if (score != null && score > 0) score else null,
            genre = genre,
            duration = duration,
            country = country,
            subjectType = subjectType,
            dubs = dubsList
        )
    }

    suspend fun getSeasonInfo(subjectId: String): List<SeasonInfo> {
        val root = executeSignedRequest(
            method = "GET",
            path = "/wefeed-mobile-bff/subject-api/season-info",
            queryParams = mapOf("subjectId" to subjectId)
        ) ?: return emptyList()

        val data = root.optJSONObject("data") ?: return emptyList()
        val seasonsArr = data.optJSONArray("seasons") ?: data.optJSONArray("list") ?: JSONArray()
        val list = mutableListOf<SeasonInfo>()

        for (i in 0 until seasonsArr.length()) {
            val sObj = seasonsArr.optJSONObject(i) ?: continue
            val seNum = sObj.optInt("season", sObj.optInt("se", i + 1))
            val epCount = sObj.optInt("maxEp", sObj.optInt("epCount", sObj.optInt("episodeCount", 1)))
            val epArr = sObj.optJSONArray("episodes") ?: JSONArray()
            val episodes = mutableListOf<EpisodeInfo>()

            if (epArr.length() > 0) {
                for (e in 0 until epArr.length()) {
                    val epObj = epArr.optJSONObject(e) ?: continue
                    val epNum = epObj.optInt("ep", epObj.optInt("episode", e + 1))
                    val epTitle = epObj.optString("title").ifEmpty { "Episode $epNum" }
                    val epCover = epObj.optJSONObject("cover")?.optString("url") ?: ""
                    val epDur = epObj.optInt("duration", 0)
                    episodes.add(
                        EpisodeInfo(
                            seasonNumber = seNum,
                            episodeNumber = epNum,
                            title = epTitle,
                            coverUrl = epCover,
                            duration = epDur
                        )
                    )
                }
            } else {
                for (ep in 1..epCount) {
                    episodes.add(
                        EpisodeInfo(
                            seasonNumber = seNum,
                            episodeNumber = ep,
                            title = "Episode $ep"
                        )
                    )
                }
            }

            list.add(
                SeasonInfo(
                    seasonNumber = seNum,
                    episodeCount = episodes.size,
                    episodes = episodes
                )
            )
        }
        return list
    }

    private fun extractBaseDashUrl(cookie: String): String? {
        if (!cookie.contains("CloudFront-Policy=")) return null
        try {
            val policyPart = cookie.substringAfter("CloudFront-Policy=").substringBefore(";")
            val paddedPolicy = policyPart + "=".repeat((-policyPart.length % 4 + 4) % 4)
            val decoded = Base64.decode(paddedPolicy, Base64.URL_SAFE)
            val jsonStr = String(decoded, Charsets.UTF_8)
            val cleanJson = jsonStr.substring(0, jsonStr.lastIndexOf("}") + 1)
            val obj = JSONObject(cleanJson)
            val resourcePattern = obj.getJSONArray("Statement").getJSONObject(0).getString("Resource")
            return resourcePattern.replace("/*", "")
        } catch (e: Exception) {
            val matcher = Pattern.compile("(https://[^\\s\"';]+)/\\*").matcher(cookie)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun calculateSizeForResolution(
        res: Int,
        baseSize: Long,
        allResolutions: List<Int>,
        duration: Int,
        isSeries: Boolean
    ): Long {
        val resFactor = when {
            res >= 2160 -> 2.5
            res >= 1440 -> 1.6
            res >= 1080 -> 1.0
            res >= 720 -> 0.54
            res >= 480 -> 0.30
            res >= 360 -> 0.18
            else -> 0.12
        }

        if (baseSize > 0L) {
            val maxRes = allResolutions.maxOrNull() ?: 1080
            val baseFactor = when {
                maxRes >= 2160 -> 2.5
                maxRes >= 1440 -> 1.6
                maxRes >= 1080 -> 1.0
                maxRes >= 720 -> 0.54
                maxRes >= 480 -> 0.30
                maxRes >= 360 -> 0.18
                else -> 0.12
            }
            return ((baseSize * resFactor) / baseFactor).toLong().coerceAtLeast(15_000_000L)
        }

        if (duration > 0) {
            val bitrateBps = when {
                res >= 2160 -> 7_000_000L
                res >= 1440 -> 4_000_000L
                res >= 1080 -> 2_400_000L
                res >= 720 -> 1_300_000L
                res >= 480 -> 750_000L
                res >= 360 -> 420_000L
                else -> 280_000L
            }
            return (bitrateBps * duration) / 8L
        }

        return DownloadStorageHelper.getEstimatedSizeBytes("${res}p", isSeries = isSeries)
    }

    suspend fun getPlayInfo(subjectId: String, se: Int = 0, ep: Int = 0): List<StreamOption> {
        val root = executeSignedRequest(
            method = "GET",
            path = "/wefeed-mobile-bff/subject-api/play-info",
            queryParams = mapOf(
                "subjectId" to subjectId,
                "se" to se.toString(),
                "ep" to ep.toString()
            )
        ) ?: return emptyList()

        val data = root.optJSONObject("data") ?: return emptyList()
        val streamsArr = data.optJSONArray("streams") ?: JSONArray()
        val options = mutableListOf<StreamOption>()
        val isSeries = (se > 0 || ep > 0)

        for (i in 0 until streamsArr.length()) {
            val s = streamsArr.optJSONObject(i) ?: continue
            val cookie = s.optString("signCookie")
            val resolutionsStr = s.optString("resolutions", "1080")
            val size = s.optLong("size", 0L)
            val duration = s.optInt("duration", 0)
            val codec = s.optString("codecName", "hevc")

            if (cookie.isNotEmpty() && cookie.contains("CloudFront-Policy=")) {
                val baseDashUrl = extractBaseDashUrl(cookie)
                if (baseDashUrl != null) {
                    val mpdUrl = "$baseDashUrl/index.mpd"
                    val resList = resolutionsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val finalResolutions = if (resList.isNotEmpty()) resList else listOf(1080)

                    for (res in finalResolutions) {
                        val sizeForRes = calculateSizeForResolution(
                            res = res,
                            baseSize = size,
                            allResolutions = finalResolutions,
                            duration = duration,
                            isSeries = isSeries
                        )
                        options.add(
                            StreamOption(
                                resolution = res,
                                title = "${res}P (HD)",
                                codec = codec,
                                isDash = true,
                                mpdUrl = mpdUrl,
                                signCookie = cookie,
                                sizeBytes = sizeForRes,
                                durationSeconds = duration
                            )
                        )
                    }
                }
            } else {
                val url = s.optString("url")
                // Exclude dummy "update app" video
                if (url.isNotEmpty() && !url.contains("9a0461bc39da389663bf3dbb17091d3f") && !url.contains("/other/2026/09/01/")) {
                    val resList = resolutionsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val finalResolutions = if (resList.isNotEmpty()) resList else listOf(s.optInt("resolution", 720))
                    for (res in finalResolutions) {
                        val sizeForRes = calculateSizeForResolution(
                            res = res,
                            baseSize = size,
                            allResolutions = finalResolutions,
                            duration = duration,
                            isSeries = isSeries
                        )
                        options.add(
                            StreamOption(
                                resolution = res,
                                title = "${res}P (MP4)",
                                codec = codec,
                                isDash = false,
                                directUrl = url,
                                signCookie = cookie,
                                sizeBytes = sizeForRes,
                                durationSeconds = duration
                            )
                        )
                    }
                }
            }
        }

        // Sort descending by resolution (1080p, 720p, etc.)
        return options.sortedByDescending { it.resolution }
    }

    suspend fun getRelatedRecommendations(subjectId: String): List<MediaItem> {
        val bodyObj = JSONObject().apply {
            put("subjectId", subjectId)
        }
        val root = executeSignedRequest(
            method = "POST",
            path = "/wefeed-mobile-bff/subject-api/play-related-rec",
            bodyJson = bodyObj.toString()
        ) ?: return emptyList()

        val data = root.optJSONObject("data") ?: return emptyList()
        val itemsArr = data.optJSONArray("items") ?: JSONArray()
        val result = mutableListOf<MediaItem>()

        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.optJSONObject(i) ?: continue
            val sid = obj.optString("subjectId").ifEmpty { obj.optString("id") }
            val title = obj.optString("title")
            val cover = obj.optJSONObject("cover")?.optString("url")
                ?: obj.optString("coverUrl")
                ?: obj.optString("cover")
            val sType = obj.optInt("subjectType", 1)
            val score = if (obj.has("imdbRatingValue")) {
                obj.optString("imdbRatingValue").toDoubleOrNull()
            } else if (obj.has("score")) {
                obj.optDouble("score").takeIf { it > 0 }
            } else null
            val releaseDate = obj.optString("releaseDate").ifEmpty { obj.optString("year") }
            val genre = obj.optString("genre")
            val desc = obj.optString("description").ifEmpty { obj.optString("desc") }

            if (sid.isNotEmpty() && title.isNotEmpty()) {
                result.add(
                    MediaItem(
                        id = sid,
                        title = title,
                        coverUrl = cover,
                        description = desc,
                        subjectType = sType,
                        score = score,
                        releaseDate = releaseDate,
                        genre = genre
                    )
                )
            }
        }
        return result
    }
}
