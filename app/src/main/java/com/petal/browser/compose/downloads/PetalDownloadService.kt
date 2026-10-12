package com.petal.browser.compose.downloads

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.petal.browser.activity.BrowserActivity
import java.io.File

/**
 * Petal Download Foreground Service
 *
 * Implements Mozilla Firefox's download architecture:
 * - Independent process survival on task removal (swiping app away from recents).
 * - Dual WakeLock (CPU partial wake lock) and WifiLock (high-performance WiFi mode) to ensure
 *   streaming packets never halt when the device screen turns off or enters Doze mode.
 * - Automatic queue restore and START_STICKY lifecycle for resilient background transfers.
 */
class PetalDownloadService : Service() {

    companion object {
        private const val TAG = "PetalDownloadService"
        const val FOREGROUND_NOTIF_ID = 888123
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val ACTION_STOP_SERVICE = "com.petal.browser.action.STOP_DOWNLOAD_SERVICE"
        const val ACTION_UPDATE_NOTIFICATION = "com.petal.browser.action.UPDATE_DOWNLOAD_NOTIFICATION"
        const val ACTION_RESTORE_DOWNLOADS = "com.petal.browser.action.RESTORE_DOWNLOADS"

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
    private var wifiLock: WifiManager.WifiLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        PetalFetchDownloadBridge.ensureInitialized(applicationContext)
        acquireLocks()
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
        acquireLocks()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When user swipes Petal away from the Recent Apps screen, do NOT kill the downloads.
        // Foreground download services remain independent of activity task stacks in Firefox.
        Log.i(TAG, "Petal task swiped/removed; keeping foreground download service alive and locks active")
        super.onTaskRemoved(rootIntent)
        acquireLocks()
        updatePersistentNotification()
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
            acquireLocks()
        }
    }

    /** Keeps one non-dismissible foreground notification alive independently of the Activity. */
    fun updatePersistentNotification() {
        val active = PetalFetchDownloadBridge.downloadItems.value.filter { isActive(it.status) }
        if (active.isEmpty()) {
            mainHandler.post { stopForegroundAndSelf() }
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
        val openAppIntent = Intent(applicationContext, BrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_downloads", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            applicationContext,
            FOREGROUND_NOTIF_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = item.status == android.app.DownloadManager.STATUS_PAUSED
        val toggleIntent = Intent(applicationContext, PetalDownloadCancelReceiver::class.java).apply {
            action = if (isPaused) PetalDownloadCancelReceiver.ACTION_RESUME_DOWNLOAD else PetalDownloadCancelReceiver.ACTION_PAUSE_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, item.id)
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            FOREGROUND_NOTIF_ID + 10,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(applicationContext, PetalDownloadCancelReceiver::class.java).apply {
            action = PetalDownloadCancelReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, item.id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            FOREGROUND_NOTIF_ID + 20,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val chipText = "${formatSpeed(item.speedBytesPerSec)} • $percent%"
        val notification = LiveUpdateNotificationManager.buildLiveNotification(
            applicationContext,
            FOREGROUND_NOTIF_ID.toLong(),
            title,
            text,
            percent,
            total <= 0,
            isPaused,
            chipText,
            openAppPendingIntent,
            cancelPendingIntent,
            togglePendingIntent
        )

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(FOREGROUND_NOTIF_ID, notification)
        mainHandler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(FOREGROUND_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(FOREGROUND_NOTIF_ID, notification)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update persistent notification", e)
            }
        }
    }

    /**
     * Acquires both CPU Partial WakeLock and high-performance WifiLock to protect downloads
     * against device screen off, deep sleep, and Doze network throttling.
     */
    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PetalBrowser:DownloadWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }

        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "PetalBrowser:DownloadWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to acquire wifi lock", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {}
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {}
        wifiLock = null
    }

    private fun stopForegroundAndSelf() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Throwable) {}
        releaseLocks()
        stopSelf()
    }

    override fun onDestroy() {
        runningInstance = null
        releaseLocks()
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
