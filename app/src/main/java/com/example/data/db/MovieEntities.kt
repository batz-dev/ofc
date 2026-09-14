package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object DownloadStatus {
    const val QUEUED = 0
    const val DOWNLOADING = 1
    const val COMPLETED = 2
    const val PAUSED = 3
    const val FAILED = 4
}

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String, // subjectId_se_ep
    val subjectId: String,
    val title: String,
    val coverUrl: String,
    val se: Int = 0,
    val ep: Int = 0,
    val episodeTitle: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val subjectId: String,
    val title: String,
    val coverUrl: String,
    val subjectType: Int = 1,
    val score: Double? = null,
    val releaseDate: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String, // subjectId_se_ep
    val subjectId: String,
    val title: String,
    val coverUrl: String,
    val se: Int = 0,
    val ep: Int = 0,
    val episodeTitle: String = "",
    val quality: String = "1080p",
    val audioTrack: String = "English",
    val filePath: String = "",
    val downloadUrl: String = "",
    val signCookie: String = "",
    val progress: Int = 0, // 0 to 100
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: Int = DownloadStatus.QUEUED,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
