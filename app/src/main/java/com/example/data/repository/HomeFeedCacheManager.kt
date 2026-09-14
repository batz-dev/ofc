package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.BannerItem
import com.example.data.model.HomeData
import com.example.data.model.HomeSection
import com.example.data.model.MediaItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent disk cache for Home feeds to enable instant cold starts and full offline support.
 */
class HomeFeedCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "HomeFeedCacheManager"
        private const val CACHE_DIR_NAME = "home_feeds"
    }

    private val cacheDir: File by lazy {
        val dir = File(context.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private fun getCacheFile(tabId: Int): File {
        return File(cacheDir, "feed_tab_${tabId}.json")
    }

    fun saveHomeFeed(tabId: Int, data: HomeData) {
        try {
            if (data.banners.isEmpty() && data.sections.isEmpty()) return

            val rootObj = JSONObject()
            val bannersArr = JSONArray()
            data.banners.forEach { banner ->
                val bObj = JSONObject().apply {
                    put("id", banner.id)
                    put("title", banner.title)
                    put("imageUrl", banner.imageUrl)
                    put("subjectId", banner.subjectId)
                    put("subjectType", banner.subjectType)
                    if (banner.score != null) put("score", banner.score)
                    put("description", banner.description)
                }
                bannersArr.put(bObj)
            }
            rootObj.put("banners", bannersArr)

            val sectionsArr = JSONArray()
            data.sections.forEach { section ->
                val sObj = JSONObject().apply {
                    put("title", section.title)
                    put("type", section.type)
                    val itemsArr = JSONArray()
                    section.items.forEach { item ->
                        val iObj = JSONObject().apply {
                            put("id", item.id)
                            put("title", item.title)
                            put("coverUrl", item.coverUrl)
                            put("description", item.description)
                            put("subjectType", item.subjectType)
                            if (item.score != null) put("score", item.score)
                            put("releaseDate", item.releaseDate)
                            put("genre", item.genre)
                            put("duration", item.duration)
                            put("country", item.country)
                        }
                        itemsArr.put(iObj)
                    }
                    put("items", itemsArr)
                }
                sectionsArr.put(sObj)
            }
            rootObj.put("sections", sectionsArr)
            rootObj.put("timestamp", System.currentTimeMillis())

            val file = getCacheFile(tabId)
            file.writeText(rootObj.toString(), Charsets.UTF_8)
            Log.d(TAG, "Saved persistent home feed for tab $tabId (${data.sections.size} sections)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save persistent home feed for tab $tabId: ${e.message}")
        }
    }

    fun loadHomeFeed(tabId: Int): HomeData? {
        val file = getCacheFile(tabId)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val rootObj = JSONObject(jsonStr)

            val banners = mutableListOf<BannerItem>()
            val bannersArr = rootObj.optJSONArray("banners") ?: JSONArray()
            for (i in 0 until bannersArr.length()) {
                val b = bannersArr.optJSONObject(i) ?: continue
                banners.add(
                    BannerItem(
                        id = b.optString("id"),
                        title = b.optString("title"),
                        imageUrl = b.optString("imageUrl"),
                        subjectId = b.optString("subjectId"),
                        subjectType = b.optInt("subjectType", 1),
                        score = if (b.has("score")) b.optDouble("score") else null,
                        description = b.optString("description")
                    )
                )
            }

            val sections = mutableListOf<HomeSection>()
            val sectionsArr = rootObj.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until sectionsArr.length()) {
                val s = sectionsArr.optJSONObject(i) ?: continue
                val items = mutableListOf<MediaItem>()
                val itemsArr = s.optJSONArray("items") ?: JSONArray()
                for (j in 0 until itemsArr.length()) {
                    val itm = itemsArr.optJSONObject(j) ?: continue
                    items.add(
                        MediaItem(
                            id = itm.optString("id"),
                            title = itm.optString("title"),
                            coverUrl = itm.optString("coverUrl"),
                            description = itm.optString("description"),
                            subjectType = itm.optInt("subjectType", 1),
                            score = if (itm.has("score")) itm.optDouble("score") else null,
                            releaseDate = itm.optString("releaseDate"),
                            genre = itm.optString("genre"),
                            duration = itm.optInt("duration", 0),
                            country = itm.optString("country")
                        )
                    )
                }
                sections.add(
                    HomeSection(
                        title = s.optString("title"),
                        type = s.optString("type"),
                        items = items
                    )
                )
            }

            if (banners.isNotEmpty() || sections.isNotEmpty()) {
                Log.d(TAG, "Loaded persistent home feed for tab $tabId (${sections.size} sections)")
                HomeData(banners = banners, sections = sections)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load persistent home feed for tab $tabId: ${e.message}")
            null
        }
    }
}
