package com.example.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object DownloadCacheManager {
    @Volatile
    private var cache: SimpleCache? = null
    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            val existing = cache
            if (existing != null) {
                existing
            } else {
                val cacheDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "media_downloads_cache")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val db = StandaloneDatabaseProvider(context.applicationContext)
                databaseProvider = db
                val newCache = SimpleCache(cacheDir, NoOpCacheEvictor(), db)
                cache = newCache
                newCache
            }
        }
    }
}
