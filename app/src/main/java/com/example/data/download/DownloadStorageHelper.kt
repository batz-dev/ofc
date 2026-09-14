package com.example.data.download

import android.content.Context
import android.os.Environment
import java.io.File

object DownloadStorageHelper {
    const val FOLDER_NAME = "ofcmovies"

    /**
     * Resolves the target download directory:
     * 1. Checks if direct `/sdcard/Android/data/ofcmovies` can be accessed/created.
     * 2. Otherwise uses the app's standard scoped external directory:
     *    `/storage/emulated/0/Android/data/<package_id>/files/ofcmovies`.
     * Both reside inside `Android/data` and are fully reliable on all Android versions.
     */
    fun getOfcMoviesDirectory(context: Context): File {
        // Primary: App-specific external files directory (guaranteed writable on Android 10, 11, 12, 13, 14, 15+)
        // Path: /storage/emulated/0/Android/data/<applicationId>/files/ofcmovies
        try {
            val appExternal = context.getExternalFilesDir(FOLDER_NAME)
            if (appExternal != null) {
                if (!appExternal.exists()) {
                    appExternal.mkdirs()
                }
                if (appExternal.exists() && appExternal.canWrite()) {
                    return appExternal
                }
            }
        } catch (_: Exception) {}

        // Secondary: App-specific external files root
        try {
            val appExternalRoot = context.getExternalFilesDir(null)
            if (appExternalRoot != null) {
                val dir = File(appExternalRoot, FOLDER_NAME)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                if (dir.exists() && dir.canWrite()) {
                    return dir
                }
            }
        } catch (_: Exception) {}

        // Fallback: Internal storage files
        val internalDir = File(context.filesDir, FOLDER_NAME)
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    fun getDownloadFile(context: Context, fileName: String): File {
        val dir = getOfcMoviesDirectory(context)
        return File(dir, sanitizeFileName(fileName))
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    fun getEstimatedSizeBytes(quality: String, isSeries: Boolean = false): Long {
        val q = quality.lowercase().trim()
        return if (isSeries) {
            // TV Series Episodes (~40-45 minutes runtime)
            when {
                q.contains("2160") || q.contains("4k") -> 1_400_000_000L // ~1.4 GB 4K UHD
                q.contains("1080") -> 650_000_000L                        // ~650 MB FHD
                q.contains("720") -> 380_000_000L                         // ~380 MB HD
                q.contains("480") -> 200_000_000L                         // ~200 MB SD
                q.contains("360") -> 110_000_000L                         // ~110 MB Data Saver
                else -> 400_000_000L
            }
        } else {
            // Feature Movies (~100-130 minutes runtime)
            when {
                q.contains("2160") || q.contains("4k") -> 3_800_000_000L // ~3.8 GB 4K UHD
                q.contains("1080") -> 1_850_000_000L                     // ~1.85 GB FHD
                q.contains("720") -> 950_000_000L                         // ~950 MB HD
                q.contains("480") -> 450_000_000L                         // ~450 MB SD
                q.contains("360") -> 220_000_000L                         // ~220 MB Data Saver
                else -> 1_200_000_000L
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
