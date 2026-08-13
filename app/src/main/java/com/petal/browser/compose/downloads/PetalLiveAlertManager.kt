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

    @JvmStatic
    fun trackDownload(context: Context, downloadId: Long, fileName: String) {
        if (downloadId <= 0L) return
        val appContext = context.applicationContext

        ensureNotificationChannel(appContext)

        // Cancel existing job if re-tracking
        trackingJobs[downloadId]?.cancel()

        val job = scope.launch {
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return@launch
            var prevSoFar = 0L
            var lastTime = System.currentTimeMillis()

            while (isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = try { dm.query(query) } catch (e: Exception) { null }

                if (cursor == null || !cursor.moveToFirst()) {
                    cursor?.close()
                    break
                }

                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val soFarCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)

                val status = if (statusCol >= 0) cursor.getInt(statusCol) else 0
                val soFar = if (soFarCol >= 0) cursor.getLong(soFarCol) else 0L
                val total = if (totalCol >= 0) cursor.getLong(totalCol) else 0L
                val titleFromCursor = if (titleCol >= 0) cursor.getString(titleCol) else null
                val displayTitle = if (!titleFromCursor.isNullOrBlank()) titleFromCursor else fileName
                cursor.close()

                val now = System.currentTimeMillis()
                val elapsedTime = (now - lastTime).coerceAtLeast(100L)
                val bytesDiff = (soFar - prevSoFar).coerceAtLeast(0L)
                val speed = if (elapsedTime > 0 && bytesDiff > 0) (bytesDiff * 1000L) / elapsedTime else 0L
                val remainingBytes = (total - soFar).coerceAtLeast(0L)
                val eta = if (speed > 0) remainingBytes / speed else 0L

                prevSoFar = soFar
                lastTime = now

                when (status) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                        showLiveNotification(
                            appContext,
                            downloadId = downloadId,
                            fileName = displayTitle,
                            soFar = soFar,
                            total = total,
                            speedBytesPerSec = speed,
                            etaSeconds = eta
                        )
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        showCompletionNotification(appContext, downloadId, displayTitle, total)
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        showFailureNotification(appContext, downloadId, displayTitle)
                        break
                    }
                }

                delay(750L)
            }
            trackingJobs.remove(downloadId)
        }

        trackingJobs[downloadId] = job
    }

    @JvmStatic
    fun stopTracking(context: Context, downloadId: Long) {
        trackingJobs[downloadId]?.cancel()
        trackingJobs.remove(downloadId)
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
            liveAlertChip,
            openAppPendingIntent,
            cancelPendingIntent
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

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        val fileUri = dm?.getUriForDownloadedFile(downloadId)

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

    private fun showFailureNotification(context: Context, downloadId: Long, fileName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_alert)
            .setContentTitle("Download Failed")
            .setContentText("Could not download $fileName")
            .setOngoing(false)
            .setAutoCancel(true)

        nm.notify(downloadId.toInt(), builder.build())
    }
}
