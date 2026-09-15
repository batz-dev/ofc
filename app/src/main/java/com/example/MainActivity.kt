package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.api.MovieApiService
import com.example.data.db.MovieDatabase
import com.example.data.repository.MovieRepository
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.LocalCurrentThemeMode
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure high-efficiency image caching to drastically reduce data usage
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("movie_images_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB high-capacity disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .allowRgb565(true)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        val database = MovieDatabase.getDatabase(this)
        val apiService = MovieApiService(applicationContext)
        val repository = MovieRepository(
            apiService = apiService,
            watchHistoryDao = database.watchHistoryDao(),
            watchlistDao = database.watchlistDao(),
            downloadDao = database.downloadDao(),
            context = applicationContext
        )

        setContent {
            var currentThemeMode by remember { mutableStateOf(ThemeMode.DARK) }

            CompositionLocalProvider(
                LocalThemeController provides { newMode -> currentThemeMode = newMode },
                LocalCurrentThemeMode provides currentThemeMode
            ) {
                MyApplicationTheme(themeMode = currentThemeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainAppNavigation(repository = repository)
                    }
                }
            }
        }
    }
}
