package com.example.ui.components

/**
 * High-efficiency Image Compression & URL Optimizer:
 * - Compresses TMDB image sizes (w342 for posters, w780 for hero backdrops)
 * - Compresses Unsplash photos (q=70, auto=format, downsampled width)
 * - Prevents high RAM consumption by ensuring thumbnails don't request raw 4K/UHD originals
 */
object ImageHelper {
    fun getCompressedUrl(url: String?, isBackdrop: Boolean = false): String {
        if (url.isNullOrBlank()) return ""
        val trimmed = url.trim()

        // TMDB CDN compression
        if (trimmed.contains("image.tmdb.org/t/p/")) {
            return if (isBackdrop) {
                trimmed.replace("/original/", "/w780/").replace("/w1280/", "/w780/")
            } else {
                trimmed.replace("/original/", "/w342/").replace("/w500/", "/w342/")
            }
        }

        // Unsplash CDN compression
        if (trimmed.contains("images.unsplash.com")) {
            val base = trimmed.substringBefore("?")
            val targetWidth = if (isBackdrop) 800 else 400
            val quality = if (isBackdrop) 75 else 70
            return "$base?w=$targetWidth&q=$quality&auto=format&fit=crop"
        }

        return trimmed
    }
}
