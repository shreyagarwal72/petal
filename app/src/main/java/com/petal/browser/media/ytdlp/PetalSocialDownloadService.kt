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
 * notification with real-time progress and a Cancel action.
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
        ensureChannel()
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

                startForeground(NOTIF_ID, buildProgressNotif(title, 0f, taskId))

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
                            nm.notify(NOTIF_ID, buildProgressNotif(title, progress, taskId))
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
                        stopForeground(STOP_FOREGROUND_DETACH)
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

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "Social Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress for Petal Social Downloader (yt-dlp)"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun cancelIntent(): PendingIntent {
        val i = Intent(this, PetalSocialDownloadService::class.java).apply { action = ACTION_CANCEL }
        return PendingIntent.getService(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildProgressNotif(title: String, progress: Float, taskId: String): Notification {
        val p = (progress * 100).toInt()
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setProgress(100, p, p == 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent())
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .build()
    }

    private fun buildDoneNotif(title: String) =
        NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setAutoCancel(true).build()

    private fun buildErrNotif(title: String, err: String) =
        NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("$title: $err")
            .setAutoCancel(true).build()

    companion object {
        private const val TAG          = "PetalSocialDlService"
        private const val CH_ID        = "petal_social_download"
        private const val NOTIF_ID     = 50100
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
