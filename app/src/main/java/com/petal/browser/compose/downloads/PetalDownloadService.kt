/*
 * PetalDownloadService.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Foreground Service for Petal Browser Download Engine.
 * Keeps Fetch2 background download threads alive with a persistent Foreground
 * Notification even when the main app Activity is minimized, swiped away, or killed.
 */

package com.petal.browser.compose.downloads

import android.app.NotificationManager
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

        LiveUpdateNotificationManager.ensureChannelCreated(applicationContext)

        // Start foreground service with initial ongoing notification
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIF_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIF_ID, initialNotif)
        }

        if (downloadId > 0L) {
            PetalLiveAlertManager.trackDownload(applicationContext, downloadId, fileName)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PetalDownloadService destroyed")
    }

    companion object {
        private const val TAG = "PetalDownloadService"
        const val FOREGROUND_NOTIF_ID = 888123
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FILE_NAME = "extra_file_name"

        @JvmStatic
        fun start(context: Context, downloadId: Long, fileName: String) {
            val intent = Intent(context, PetalDownloadService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stopIfNoActiveDownloads(context: Context) {
            val activeItems = PetalFetchDownloadBridge.downloadItems.value.filter {
                it.status == android.app.DownloadManager.STATUS_RUNNING || it.status == android.app.DownloadManager.STATUS_PENDING
            }
            if (activeItems.isEmpty()) {
                val intent = Intent(context, PetalDownloadService::class.java)
                context.stopService(intent)
            }
        }
    }
}
