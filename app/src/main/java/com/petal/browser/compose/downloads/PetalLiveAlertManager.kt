package com.petal.browser.compose.downloads

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object PetalLiveAlertManager {

    private const val CHANNEL_ID = "petal_live_downloads"
    private const val CHANNEL_NAME = "Live Downloader & Alerts"
    private const val TAG = "PetalLiveAlertManager"

    private val trackingJobs = ConcurrentHashMap<Long, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isGlobalCollectorStarted = false

    @JvmOverloads
    @JvmStatic
    fun trackDownload(context: Context, downloadId: Long, fileName: String, startService: Boolean = true) {
        if (downloadId <= 0L) return
        val appContext = context.applicationContext

        ensureNotificationChannel(appContext)
        PetalFetchDownloadBridge.ensureInitialized(appContext)

        // Only start service if requested
        if (startService) {
            try {
                PetalDownloadService.start(appContext, downloadId, fileName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start PetalDownloadService: ${e.message}")
            }
        }

        startGlobalDownloadObserver(appContext)
    }

    @JvmStatic
    private fun startGlobalDownloadObserver(context: Context) {
        if (isGlobalCollectorStarted) return
        synchronized(this) {
            if (isGlobalCollectorStarted) return
            isGlobalCollectorStarted = true

            scope.launch {
                PetalFetchDownloadBridge.downloadItems.collect { items ->
                    val activeItems = items.filter {
                        it.status == DownloadManager.STATUS_RUNNING ||
                        it.status == DownloadManager.STATUS_PENDING ||
                        it.status == DownloadManager.STATUS_PAUSED
                    }

                    if (activeItems.isEmpty()) {
                        PetalDownloadService.stopIfNoActiveDownloads(context)
                    }

                    items.forEach { item ->
                        when (item.status) {
                            DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                                showLiveNotification(
                                    context,
                                    downloadId = item.id,
                                    fileName = item.fileName,
                                    soFar = item.bytesDownloaded,
                                    total = item.totalSize,
                                    speedBytesPerSec = item.speedBytesPerSec,
                                    etaSeconds = item.etaSeconds
                                )
                            }
                            DownloadManager.STATUS_PAUSED -> {
                                showPausedNotification(context, item.id)
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                showCompletionNotification(context, item.id, item.fileName, item.totalSize)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                showFailureNotification(context, item.id, item.fileName)
                            }
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun stopTracking(context: Context, downloadId: Long) {
        trackingJobs[downloadId]?.cancel()
        trackingJobs.remove(downloadId)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(downloadId.toInt())
        PetalDownloadService.stopIfNoActiveDownloads(context.applicationContext)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Live real-time alerts for active downloads with progress, velocity, and controls"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    @JvmStatic
    fun pauseDownload(context: Context, downloadId: Long) {
        // android.app.DownloadManager has no public pause API - the "pauseDownload" method
        // this used to reach via reflection doesn't exist on that class, so this always threw
        // and silently did nothing. Fetch2 (via the bridge) genuinely supports pausing.
        PetalFetchDownloadBridge.pause(context, downloadId)
        showPausedNotification(context, downloadId)
    }

    @JvmStatic
    fun retryDownload(context: Context, downloadId: Long) {
        PetalFetchDownloadBridge.retry(context, downloadId)
    }

    private fun showFailureNotification(context: Context, downloadId: Long, fileName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val retryIntent = Intent(context, PetalDownloadCancelReceiver::class.java).apply {
            action = PetalDownloadCancelReceiver.ACTION_RETRY_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.toInt() + 40000,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_alert)
            .setContentTitle("Download Stopped / Failed")
            .setContentText("Failed to download $fileName. Tap Retry to restart.")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.icon_refresh, "Retry Download", retryPendingIntent)

        nm.notify(downloadId.toInt(), builder.build())
    }

    private fun showPausedNotification(context: Context, downloadId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openAppIntent = Intent(context, BrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(context, PetalDownloadCancelReceiver::class.java).apply {
            action = PetalDownloadCancelReceiver.ACTION_RESUME_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.toInt() + 30000,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, PetalDownloadCancelReceiver::class.java).apply {
            action = PetalDownloadCancelReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.toInt(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val item = PetalFetchDownloadBridge.downloadItems.value.firstOrNull { it.id == downloadId }
        val titleText = if (item != null && item.fileName.isNotBlank()) "Paused: ${item.fileName}" else "Download Paused"

        val builderNotif = LiveUpdateNotificationManager.buildLiveNotification(
            context,
            downloadId,
            titleText,
            "Tap to resume downloading",
            0,
            false,
            true,
            "Paused",
            openAppPendingIntent,
            cancelPendingIntent,
            resumePendingIntent
        )

        nm.notify(downloadId.toInt(), builderNotif)
    }

    private fun showLiveNotification(
        context: Context,
        downloadId: Long,
        fileName: String,
        soFar: Long,
        total: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val progressPercent = if (total > 0) ((soFar * 100L) / total).toInt().coerceIn(0, 100) else 0
        val isIndeterminate = total <= 0

        val speedText = formatSpeed(speedBytesPerSec)
        val etaText = formatEta(etaSeconds)
        val soFarText = formatBytes(soFar)
        val totalText = if (total > 0) formatBytes(total) else "Unknown"

        // Open Downloads manager intent
        val openAppIntent = Intent(context, BrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Toggle action intent (Pause)
        val pauseIntent = Intent(context, PetalDownloadCancelReceiver::class.java).apply {
            action = PetalDownloadCancelReceiver.ACTION_PAUSE_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.toInt() + 20000,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel action intent
        val cancelIntent = Intent(context, PetalDownloadCancelReceiver::class.java).apply {
            action = PetalDownloadCancelReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.toInt(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val liveAlertChip = "$speedText • $progressPercent%"
        val contentText = "$soFarText / $totalText • $etaText left"

        val builderNotif = LiveUpdateNotificationManager.buildLiveNotification(
            context,
            downloadId,
            "Downloading $fileName",
            contentText,
            progressPercent,
            isIndeterminate,
            false,
            liveAlertChip,
            openAppPendingIntent,
            cancelPendingIntent,
            pausePendingIntent
        )

        nm.notify(downloadId.toInt(), builderNotif)
    }

    private fun showCompletionNotification(
        context: Context,
        downloadId: Long,
        fileName: String,
        totalBytes: Long
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // downloadId is Fetch2's own id now (not a system DownloadManager row id, since
        // addCompletedDownload() creates a separate row with its own id purely so the file
        // shows up in the system Downloads app/Files app). Resolve the actual file via the
        // bridge and hand it to a FileProvider content:// uri instead.
        val item = PetalFetchDownloadBridge.downloadItems.value.firstOrNull { it.id == downloadId }
        val fileUri = try {
            val path = item?.localUri?.removePrefix("file://")
            if (!path.isNullOrEmpty()) {
                val file = java.io.File(path)
                if (file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }

        val openFileIntent = if (fileUri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(context, BrowserActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_downloads", true)
            }
        }

        val openFilePendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt() + 10000,
            openFileIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val totalText = if (totalBytes > 0) formatBytes(totalBytes) else ""
        val contentText = if (totalText.isNotEmpty()) "$fileName ($totalText)" else fileName

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_check)
            .setContentTitle("Download Complete")
            .setContentText(contentText)
            .setSubText("Completed")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openFilePendingIntent)
            .addAction(R.drawable.icon_check, "Open File", openFilePendingIntent)

        nm.notify(downloadId.toInt(), builder.build())
    }



    @JvmStatic
    fun trackOfflinePage(context: Context, title: String, url: String, filePath: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureNotificationChannel(context)

        val intent = Intent(context, BrowserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("file://$filePath")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            filePath.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_download)
            .setContentTitle("Website Saved Offline")
            .setContentText(title.ifBlank { url })
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        nm.notify(filePath.hashCode(), builder.build())
    }
}
