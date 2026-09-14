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
            val targetWidth = if (isBackdrop) 720 else 360
            val quality = if (isBackdrop) 70 else 65
            return "$base?w=$targetWidth&q=$quality&auto=format&fit=crop"
        }

        // Aoneroom / Inmoviebox OSS image compression
        if (trimmed.contains("inmoviebox.com") || trimmed.contains("aoneroom.com")) {
            if (!trimmed.contains("x-oss-process")) {
                val separator = if (trimmed.contains("?")) "&" else "?"
                val width = if (isBackdrop) 720 else 360
                return "$trimmed${separator}x-oss-process=image/resize,w_$width/format,webp/quality,q_75"
            }
        }

        return trimmed
    }
}
