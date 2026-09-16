package com.example

import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
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

        // Enable highest supported display refresh rate (90Hz / 120Hz / 144Hz) by default for ultra-smooth UI
        enableHighRefreshRate()

        // Configure high-efficiency image caching to drastically reduce data usage
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("movie_images_cache"))
                    .maxSizeBytes(300L * 1024 * 1024) // 300 MB high-capacity disk cache
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

    /**
     * Automatically requests the highest supported refresh rate (90Hz, 120Hz, 144Hz)
     * supported by the device screen for fluid, jank-free 120fps animations and scrolling.
     */
    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }

                val modes = currentDisplay?.supportedModes
                if (!modes.isNullOrEmpty()) {
                    val highestMode = modes.maxByOrNull { it.refreshRate }
                    if (highestMode != null && highestMode.refreshRate > 60f) {
                        val lp = window.attributes
                        lp.preferredDisplayModeId = highestMode.modeId
                        window.attributes = lp
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

