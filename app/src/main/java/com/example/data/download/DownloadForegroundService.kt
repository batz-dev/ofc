package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

/**
 * Foreground service providing sustained background processing and Android 13+ notification updates
 * for media downloads.
 */
class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "movie_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.example.action.START_DOWNLOAD"
        const val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_DOWNLOADED_BYTES = "extra_downloaded_bytes"
        const val EXTRA_SPEED = "extra_speed"

        fun start(context: Context, title: String, totalBytes: Long = 0L) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TOTAL_BYTES, totalBytes)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Background start restriction handling
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun updateProgress(
            context: Context,
            title: String,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            speed: String = ""
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
                putExtra(EXTRA_TOTAL_BYTES, totalBytes)
                putExtra(EXTRA_SPEED, speed)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MovieStream::DownloadWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes timeout per acquire
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading media"
                val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                startForegroundWithNotification(title, 0, 0, totalBytes, "")
            }
            ACTION_UPDATE_PROGRESS -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading media"
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val downloadedBytes = intent.getLongExtra(EXTRA_DOWNLOADED_BYTES, 0L)
                val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                val speed = intent.getStringExtra(EXTRA_SPEED) ?: ""
                updateNotification(title, progress, downloadedBytes, totalBytes, speed)
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(
        title: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String
    ) {
        val notification = buildNotification(title, progress, downloadedBytes, totalBytes, speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        title: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String
    ) {
        val notification = buildNotification(title, progress, downloadedBytes, totalBytes, speed)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String
    ): android.app.Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeTotal = if (totalBytes > 0L) {
            totalBytes
        } else {
            1_850_000_000L
        }

        val progressText = if (progress >= 100) {
            "Download complete • ${DownloadStorageHelper.formatFileSize(downloadedBytes)}"
        } else {
            buildString {
                append(DownloadStorageHelper.formatFileSize(downloadedBytes))
                append(" / ")
                append(DownloadStorageHelper.formatFileSize(safeTotal))
                append(" ($progress%)")
                if (speed.isNotEmpty()) {
                    append(" • ")
                    append(speed)
                }
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(progress < 100)
            .setAutoCancel(progress >= 100)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active media downloads"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundAndSelf() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
