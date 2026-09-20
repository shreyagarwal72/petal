package com.petal.browser.compose.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.io.File

class PetalDownloadService : Service() {

    companion object {
        private const val TAG = "PetalDownloadService"
        const val FOREGROUND_NOTIF_ID = 888123
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val ACTION_STOP_SERVICE = "com.petal.browser.action.STOP_DOWNLOAD_SERVICE"
        const val ACTION_UPDATE_NOTIFICATION = "com.petal.browser.action.UPDATE_DOWNLOAD_NOTIFICATION"

        @Volatile private var runningInstance: PetalDownloadService? = null

        @JvmStatic
        fun start(context: Context, downloadId: Long, fileName: String) {
            try {
                val intent = Intent(context, PetalDownloadService::class.java).apply {
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                    putExtra(EXTRA_FILE_NAME, fileName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start foreground download service", e)
            }
        }

        @JvmStatic
        fun updatePersistentNotification(context: Context) {
            runningInstance?.let {
                it.updatePersistentNotification()
                return
            }
            val active = PetalFetchDownloadBridge.downloadItems.value.firstOrNull { isActive(it.status) }
            if (active != null) start(context, active.id, active.fileName)
        }

        @JvmStatic
        fun stopIfNoActiveDownloads(context: Context) {
            if (PetalFetchDownloadBridge.downloadItems.value.none { isActive(it.status) }) {
                runningInstance?.stopForegroundAndSelf()
            }
        }

        private fun isActive(status: Int): Boolean =
            status == android.app.DownloadManager.STATUS_RUNNING ||
            status == android.app.DownloadManager.STATUS_PENDING ||
            status == android.app.DownloadManager.STATUS_PAUSED
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        PetalFetchDownloadBridge.ensureInitialized(applicationContext)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LiveUpdateNotificationManager.ensureChannelCreated(applicationContext)

        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "Downloads"

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        // Android requires startForeground() immediately after startForegroundService().
        // This notification is intentionally the service's single persistent notification.
        val initial = LiveUpdateNotificationManager.buildLiveNotification(
            applicationContext,
            FOREGROUND_NOTIF_ID.toLong(),
            "Downloading $fileName",
            "Download active in background",
            0,
            true,
            false,
            "Background download",
            null,
            null,
            null
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_NOTIF_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(FOREGROUND_NOTIF_ID, initial)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startForeground failed", e)
        }

        if (downloadId > 0) {
            PetalLiveAlertManager.trackDownload(applicationContext, downloadId, fileName, startService = false)
        } else {
            restoreActiveDownloads()
        }
        updatePersistentNotification()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The UI task can be swiped away; downloads belong to this foreground service.
        Log.d(TAG, "Petal task removed; keeping foreground download service alive")
        super.onTaskRemoved(rootIntent)
    }

    private fun restoreActiveDownloads() {
        PetalFetchDownloadBridge.ensureInitialized(applicationContext)
        PetalFetchDownloadBridge.fetchInstance(applicationContext).getDownloads { downloads ->
            val active = downloads.filter {
                it.status == com.tonyodev.fetch2.Status.DOWNLOADING ||
                it.status == com.tonyodev.fetch2.Status.QUEUED ||
                it.status == com.tonyodev.fetch2.Status.PAUSED
            }
            if (active.isEmpty()) {
                stopForegroundAndSelf()
                return@getDownloads
            }
            active.forEach {
                val name = File(it.file).name.ifBlank { it.url }
                PetalLiveAlertManager.trackDownload(applicationContext, it.id.toLong(), name, startService = false)
            }
            updatePersistentNotification()
        }
    }

    /** Keeps one non-dismissible foreground notification alive independently of the Activity. */
    fun updatePersistentNotification() {
        val active = PetalFetchDownloadBridge.downloadItems.value.filter { isActive(it.status) }
        if (active.isEmpty()) {
            stopForegroundAndSelf()
            return
        }
        val item = active.first()
        val total = item.totalSize
        val percent = if (total > 0) ((item.bytesDownloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
        val title = if (active.size == 1) "Downloading ${item.fileName}" else "Downloading ${active.size} files"
        val text = if (active.size == 1) {
            "${formatBytes(item.bytesDownloaded)} / ${if (total > 0) formatBytes(total) else "Unknown"} • ${formatSpeed(item.speedBytesPerSec)}"
        } else {
            "${active.size} downloads active in background"
        }
        val notification = LiveUpdateNotificationManager.buildLiveNotification(
            applicationContext,
            FOREGROUND_NOTIF_ID.toLong(),
            title,
            text,
            percent,
            total <= 0,
            false,
            "Background download",
            null,
            null,
            null
        )
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(FOREGROUND_NOTIF_ID, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(FOREGROUND_NOTIF_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update persistent notification", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PetalBrowser:DownloadWakeLock")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Throwable) {}
        wakeLock = null
    }

    private fun stopForegroundAndSelf() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Throwable) {}
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        runningInstance = null
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(java.util.Locale.US, kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(java.util.Locale.US, mb)
        return "%.1f GB".format(java.util.Locale.US, mb / 1024.0)
    }

    private fun formatSpeed(bytesPerSecond: Long): String =
        if (bytesPerSecond > 0) "${formatBytes(bytesPerSecond)}/s" else "waiting"
}
