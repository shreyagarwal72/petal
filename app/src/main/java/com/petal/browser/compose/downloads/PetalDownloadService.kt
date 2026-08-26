/*
 * PetalDownloadService.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Foreground Service for Petal Browser Download Engine.
 * Keeps Fetch2 background download threads alive with a persistent Foreground
 * Notification even when the main app Activity is minimized, swiped away, or killed.
 */

package com.petal.browser.compose.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class PetalDownloadService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PetalDownloadService created")
        PetalFetchDownloadBridge.ensureInitialized(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "File"

        // 1. Synchronously create notification channel and build valid initial notification
        LiveUpdateNotificationManager.ensureChannelCreated(applicationContext)

        val initialNotif = LiveUpdateNotificationManager.buildLiveNotification(
            applicationContext,
            if (downloadId > 0) downloadId else FOREGROUND_NOTIF_ID.toLong(),
            "Downloading $fileName",
            "Download active in background...",
            0,
            true,
            false,
            "Active",
            null,
            null,
            null
        )

        // 2. VERY FIRST synchronous call: startForeground() on main thread within 5s window
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIF_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIF_ID, initialNotif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: ${e.message}")
        }

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        // 3. Perform download tracking without re-triggering startForegroundService loop (startService = false)
        if (downloadId > 0L) {
            PetalLiveAlertManager.trackDownload(
                context = applicationContext,
                downloadId = downloadId,
                fileName = fileName,
                startService = false
            )
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PetalDownloadService destroyed")
    }

    private fun stopForegroundAndSelf() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing foreground notification: ${e.message}")
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "PetalDownloadService"
        const val FOREGROUND_NOTIF_ID = 888123
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val ACTION_STOP_SERVICE = "com.petal.browser.action.STOP_DOWNLOAD_SERVICE"

        @JvmStatic
        fun start(context: Context, downloadId: Long, fileName: String) {
            try {
                val intent = Intent(context, PetalDownloadService::class.java).apply {
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                    putExtra(EXTRA_FILE_NAME, fileName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service: ${e.message}")
            }
        }

        @JvmStatic
        fun stopIfNoActiveDownloads(context: Context) {
            try {
                val activeItems = PetalFetchDownloadBridge.downloadItems.value.filter {
                    it.status == android.app.DownloadManager.STATUS_RUNNING || it.status == android.app.DownloadManager.STATUS_PENDING
                }
                if (activeItems.isEmpty()) {
                    val intent = Intent(context, PetalDownloadService::class.java).apply {
                        action = ACTION_STOP_SERVICE
                    }
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping download service: ${e.message}")
            }
        }
    }
}
