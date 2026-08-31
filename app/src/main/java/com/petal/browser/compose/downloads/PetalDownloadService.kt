/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.compose.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class PetalDownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PetalDownloadService created")
        PetalFetchDownloadBridge.ensureInitialized(applicationContext)
        acquireWakeLock()
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

        // 2. Synchronous call: startForeground() on main thread within 5s window
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

        // 3. Perform download tracking without re-triggering startForegroundService loop
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
        releaseWakeLock()
        Log.d(TAG, "PetalDownloadService destroyed")
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PetalBrowser:DownloadWakeLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hour max safety timeout
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock: ${e.message}")
        }
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
        releaseWakeLock()
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
