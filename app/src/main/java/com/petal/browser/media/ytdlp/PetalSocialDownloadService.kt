package com.petal.browser.media.ytdlp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Petal Social Downloader — Foreground Download Service
 *
 * Runs yt-dlp downloads as a foreground service so they survive app
 * minimization, screen-off, and configuration changes. Shows a persistent
 * live-update notification (using Petal's LiveUpdateNotificationManager)
 * with real-time progress and a Cancel action.
 *
 * Use [PetalSocialDownloadService.enqueue] to start a download.
 */
class PetalSocialDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeTaskId: String? = null
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LiveUpdateNotificationManager.ensureChannelCreated(applicationContext)
        ensureSocialChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                activeTaskId?.let { PetalYtDlpEngine.cancel(it) }
                activeJob?.cancel()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_DOWNLOAD -> {
                val url      = intent.getStringExtra(EXTRA_URL) ?: run { stopSelf(); return START_NOT_STICKY }
                val fmtId    = intent.getStringExtra(EXTRA_FORMAT_ID) ?: "best"
                val fmtExt   = intent.getStringExtra(EXTRA_FORMAT_EXT) ?: "mp4"
                val fmtLabel = intent.getStringExtra(EXTRA_FORMAT_LABEL) ?: "Best quality"
                val isAudio  = intent.getBooleanExtra(EXTRA_IS_AUDIO_ONLY, false)
                val cookies  = intent.getStringExtra(EXTRA_COOKIES)
                val title    = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading…"
                val taskId   = "petal_social_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                activeTaskId = taskId

                val format = YtDlpFormat(
                    formatId = fmtId, label = fmtLabel,
                    isAudioOnly = isAudio, ext = fmtExt
                )
                val outputDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "Petal Social"
                )

                startForeground(NOTIF_ID, buildProgressNotif(title, 0f))

                activeJob = scope.launch {
                    try {
                        PetalYtDlpEngine.download(
                            context = applicationContext,
                            url = url,
                            format = format,
                            outputDir = outputDir,
                            taskId = taskId,
                            cookies = cookies
                        ) { progress, _ ->
                            nm.notify(NOTIF_ID, buildProgressNotif(title, progress))
                        }.fold(
                            onSuccess = { filePath ->
                                if (filePath != null) {
                                    try { MediaScannerConnection.scanFile(applicationContext, arrayOf(filePath), null, null) }
                                    catch (_: Exception) {}
                                }
                                nm.notify(NOTIF_DONE_ID, buildDoneNotif(title))
                            },
                            onFailure = { err ->
                                Log.e(TAG, "Social download failed", err)
                                nm.notify(NOTIF_ERR_ID, buildErrNotif(title, err.message ?: "Download failed"))
                            }
                        )
                    } finally {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                        } else {
                            @Suppress("DEPRECATION") stopForeground(false)
                        }
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeJob?.cancel()
        activeTaskId?.let { PetalYtDlpEngine.cancel(it) }
        super.onDestroy()
    }

    // ── Notification helpers ───────────────────────────────────────────────

    private fun ensureSocialChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Only create if the shared live-downloads channel isn't enough.
            // Social downloads now share the same "Live Downloader & Alerts" channel
            // so the user manages one consistent channel. No separate channel needed.
        }
    }

    private fun cancelPendingIntent(): PendingIntent {
        val i = Intent(this, PetalSocialDownloadService::class.java).apply { action = ACTION_CANCEL }
        return PendingIntent.getService(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openDownloadsPendingIntent(): PendingIntent {
        val i = Intent(this, BrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_downloads", true)
        }
        return PendingIntent.getActivity(this, NOTIF_ID + 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildProgressNotif(title: String, progress: Float): Notification {
        val p = (progress * 100).toInt().coerceIn(0, 100)
        val isIndeterminate = p == 0
        val chipText = if (isIndeterminate) "Starting…" else "$p%"
        return LiveUpdateNotificationManager.buildLiveNotification(
            this,
            NOTIF_ID.toLong(),
            "Downloading",
            title,
            p,
            isIndeterminate,
            false,
            chipText,
            openDownloadsPendingIntent(),
            cancelPendingIntent(),
            null
        )
    }

    private fun buildDoneNotif(title: String): Notification {
        val accentColor = LiveUpdateNotificationManager.getLiveThemeAccentColor(this)
        return NotificationCompat.Builder(this, LiveUpdateNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.check_rounded)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setColor(accentColor)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openDownloadsPendingIntent())
            .build()
    }

    private fun buildErrNotif(title: String, err: String): Notification {
        val accentColor = LiveUpdateNotificationManager.getLiveThemeAccentColor(this)
        return NotificationCompat.Builder(this, LiveUpdateNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_alert)
            .setContentTitle("Download failed")
            .setContentText("$title: $err")
            .setColor(accentColor)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        private const val TAG          = "PetalSocialDlService"
        private const val NOTIF_ID      = 50100
        private const val NOTIF_DONE_ID = 50101
        private const val NOTIF_ERR_ID  = 50102

        const val ACTION_DOWNLOAD    = "com.petal.browser.action.SOCIAL_DOWNLOAD"
        const val ACTION_CANCEL      = "com.petal.browser.action.SOCIAL_DOWNLOAD_CANCEL"
        const val EXTRA_URL          = "url"
        const val EXTRA_FORMAT_ID    = "format_id"
        const val EXTRA_FORMAT_EXT   = "format_ext"
        const val EXTRA_FORMAT_LABEL = "format_label"
        const val EXTRA_IS_AUDIO_ONLY = "is_audio_only"
        const val EXTRA_COOKIES      = "cookies"
        const val EXTRA_TITLE        = "title"

        /** Start a social download from any context. */
        fun enqueue(context: Context, url: String, format: YtDlpFormat, cookies: String?, title: String) {
            val intent = Intent(context, PetalSocialDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT_ID, format.formatId)
                putExtra(EXTRA_FORMAT_EXT, format.ext)
                putExtra(EXTRA_FORMAT_LABEL, format.label)
                putExtra(EXTRA_IS_AUDIO_ONLY, format.isAudioOnly)
                putExtra(EXTRA_COOKIES, cookies)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
