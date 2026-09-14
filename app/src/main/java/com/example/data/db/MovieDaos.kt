package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE subjectId = :subjectId AND se = :se AND ep = :ep LIMIT 1")
    suspend fun getHistory(subjectId: String, se: Int, ep: Int): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE subjectId = :subjectId)")
    fun isInWatchlist(subjectId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE subjectId = :subjectId")
    suspend fun removeFromWatchlist(subjectId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownload(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE subjectId = :subjectId")
    fun getDownloadsBySubject(subjectId: String): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, downloadedBytes: Long, totalBytes: Long, status: Int)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: Int)

    @Query("SELECT * FROM downloads WHERE status = 0 ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextQueuedDownload(): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 1")
    suspend fun getActiveDownloadingCount(): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()
}
