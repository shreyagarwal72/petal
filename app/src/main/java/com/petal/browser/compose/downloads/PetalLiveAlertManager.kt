package com.petal.browser.compose.downloads

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
import com.petal.browser.activity.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object PetalLiveAlertManager {

    private const val CHANNEL_ID = "petal_live_downloads"
    private const val CHANNEL_NAME = "Live Downloader & Alerts"
    private const val TAG = "PetalLiveAlertManager"

    private val trackingJobs = ConcurrentHashMap<Long, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val lastNotifTimeMap = ConcurrentHashMap<Long, Long>()
    private val handledTerminalStatusMap = ConcurrentHashMap<Long, Int>()

    // Single collector job — kept so we can cancel + relaunch it reliably.
    @Volatile private var collectorJob: Job? = null
    private val observerLock = Any()

    @JvmOverloads
    @JvmStatic
    fun trackDownload(context: Context, downloadId: Long, fileName: String) {
        if (downloadId <= 0L) return
        val appContext = context.applicationContext

        ensureNotificationChannel(appContext)
        PetalFetchDownloadBridge.ensureInitialized(appContext)
        // Process lifetime is held by the engine that owns the row: Mozilla's download service for
        // HTTP downloads, PetalSocialDownloadService for yt-dlp. This manager only renders
        // notifications for rows Mozilla's service does not own.
        startGlobalDownloadObserver(appContext)
    }

    @JvmStatic
    fun startGlobalDownloadObserver(context: Context) {
        synchronized(observerLock) {
            // If a collector job is already active, do nothing.
            if (collectorJob?.isActive == true) return
            collectorJob = scope.launch {
                try {
                    PetalFetchDownloadBridge.downloadItems.collect { allItems ->
                        // Mozilla-owned downloads are notified by PetalMozillaDownloadService
                        // (official Firefox notifications). Posting here too would duplicate them.
                        val items = allItems.filter { !PetalFetchDownloadBridge.isMozillaOwned(it.id) }
                        val activeItems = items.filter {
                            it.status == DownloadStatus.STATUS_RUNNING ||
                            it.status == DownloadStatus.STATUS_PENDING ||
                            it.status == DownloadStatus.STATUS_PAUSED
                        }

                        val now = System.currentTimeMillis()
                        items.forEach { item ->
                            when (item.status) {
                                DownloadStatus.STATUS_RUNNING, DownloadStatus.STATUS_PENDING -> {
                                    handledTerminalStatusMap.remove(item.id)
                                    val lastTime = lastNotifTimeMap[item.id] ?: 0L
                                    if (now - lastTime >= 400L || lastTime == 0L) {
                                        lastNotifTimeMap[item.id] = now
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
                                }
                                DownloadStatus.STATUS_PAUSED -> {
                                    if (handledTerminalStatusMap[item.id] != DownloadStatus.STATUS_PAUSED) {
                                        handledTerminalStatusMap[item.id] = DownloadStatus.STATUS_PAUSED
                                        showPausedNotification(context, item.id)
                                    }
                                }
                                DownloadStatus.STATUS_SUCCESSFUL -> {
                                    if (handledTerminalStatusMap[item.id] != DownloadStatus.STATUS_SUCCESSFUL) {
                                        handledTerminalStatusMap[item.id] = DownloadStatus.STATUS_SUCCESSFUL
                                        lastNotifTimeMap.remove(item.id)
                                        showCompletionNotification(context, item.id, item.fileName, item.totalSize)
                                    }
                                }
                                DownloadStatus.STATUS_FAILED -> {
                                    if (handledTerminalStatusMap[item.id] != DownloadStatus.STATUS_FAILED) {
                                        handledTerminalStatusMap[item.id] = DownloadStatus.STATUS_FAILED
                                        lastNotifTimeMap.remove(item.id)
                                        showFailureNotification(context, item.id, item.fileName, item.failureReason, item.source.isMozillaOwned.not() && item.source != com.petal.browser.download.DownloadSource.BLOB)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // Normal cancellation (e.g., during resume restart) — do not log as error.
                    throw e
                } catch (e: Throwable) {
                    // Flow collection unexpectedly failed — clear the job so the next
                    // trackDownload() call can relaunch it automatically.
                    Log.e(TAG, "Global download observer crashed; will restart on next download", e)
                    synchronized(observerLock) { collectorJob = null }
                }
            }
        }
    }

    @JvmStatic
    fun stopTracking(context: Context, downloadId: Long) {
        trackingJobs[downloadId]?.cancel()
        trackingJobs.remove(downloadId)
        lastNotifTimeMap.remove(downloadId)
        handledTerminalStatusMap.remove(downloadId)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(downloadId.toInt())
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live real-time alerts for active downloads with progress, velocity, and controls"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    @JvmStatic
    fun pauseDownload(context: Context, downloadId: Long) {
        // android.app.DownloadManager has no public pause API - the "pauseDownload" method
        // this used to reach via reflection doesn't exist on that class, so this always threw
        // and silently did nothing. The bridge pauses through Mozilla's download service.
        PetalFetchDownloadBridge.pause(context, downloadId)
        // Mozilla-owned downloads already get their own paused notification from Mozilla's
        // service; posting ours as well produced two notifications for one download, and ours was
        // never cleared on cancel because nothing tracks it.
        if (!PetalFetchDownloadBridge.isMozillaOwned(downloadId)) {
            showPausedNotification(context, downloadId)
        }
    }

    @JvmStatic
    fun resumeDownload(context: Context, downloadId: Long) {
        // Clear pause dedup and rate-limit so the observer fires a live notification immediately.
        handledTerminalStatusMap.remove(downloadId)
        lastNotifTimeMap.remove(downloadId)
        PetalFetchDownloadBridge.resume(context, downloadId)

        // Cancel the old collector and relaunch so the freshly-resumed download is immediately
        // observed. Without cancellation, resetting the old guard flag would race with the
        // existing coroutine and potentially launch a second parallel collector.
        synchronized(observerLock) {
            collectorJob?.cancel()
            collectorJob = null
        }
        startGlobalDownloadObserver(context)
    }

    @JvmStatic
    fun retryDownload(context: Context, downloadId: Long) {
        PetalFetchDownloadBridge.retry(context, downloadId)
    }

    private fun showFailureNotification(
        context: Context,
        downloadId: Long,
        fileName: String,
        reason: String?,
        canRetry: Boolean
    ) {
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
            .setContentText(reason?.takeIf { it.isNotBlank() }?.let { "$fileName: $it" } ?: "Failed to download $fileName.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason?.takeIf { it.isNotBlank() }?.let { "$fileName\n$it" } ?: fileName))
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (canRetry) builder.addAction(R.drawable.icon_refresh, "Retry Download", retryPendingIntent)

        nm.notify(downloadId.toInt(), builder.build())
    }

    private fun showPausedNotification(context: Context, downloadId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_downloads", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            openAppIntent,
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
            // Rows rendered here (yt-dlp, page data) cannot be paused, so no toggle action.
            null
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

        // downloadId is the bridge's stable row id. Resolve the actual file via the bridge and
        // hand it to a FileProvider content:// uri (or the MediaStore uri for page-data rows).
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
            com.petal.browser.download.PetalFileOpener.createOpenIntent(context, fileUri, fileName)
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
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

        val accentColor = LiveUpdateNotificationManager.getLiveThemeAccentColor(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.check_rounded)
            .setContentTitle("Download Complete")
            .setContentText(contentText)
            .setSubText("Finished")
            .setOngoing(false)
            .setAutoCancel(true)
            .setColor(accentColor)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openFilePendingIntent)
            .addAction(R.drawable.icon_download, "Open File", openFilePendingIntent)

        nm.notify(downloadId.toInt(), builder.build())
    }



    @JvmStatic
    fun trackOfflinePage(context: Context, title: String, url: String, filePath: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
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

    // ── Formatting helpers ────────────────────────────────────────────────

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

    private fun formatEta(etaSeconds: Long): String {
        if (etaSeconds <= 0) return "calculating"
        return when {
            etaSeconds < 60 -> "${etaSeconds}s"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }
    }
}
