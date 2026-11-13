package com.petal.browser.media.ytdlp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.petal.browser.activity.MainActivity
import com.petal.browser.compose.downloads.DownloadFailure
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.compose.downloads.PetalDownloadCancelReceiver
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.download.DownloadSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Petal Social Downloader — foreground service for yt-dlp downloads.
 *
 * Keeps yt-dlp alive across app minimization and screen-off. Every task is a tracked row in
 * [PetalFetchDownloadBridge] (source [DownloadSource.YTDLP]) with live progress, speed and ETA,
 * and working Cancel and Retry, so the Downloads screen treats it like any other download.
 *
 * Notifications: the service only supplies the initial foreground notification (Android requires
 * one within seconds). From then on the notification for each row is rendered by
 * PetalLiveAlertManager through LiveUpdateNotificationManager, under the same notification id
 * (the row id), so there is one consistent notification per download instead of a parallel set.
 *
 * Use [PetalSocialDownloadService.enqueue] to start a download. Several can run at once.
 */
class PetalSocialDownloadService : Service() {

    private class Task(
        val url: String,
        val format: YtDlpFormat,
        val cookies: String?,
        val title: String
    )

    @Volatile private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LiveUpdateNotificationManager.ensureChannelCreated(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                jobs.keys.toList().forEach { cancelTask(it) }
                stopIfIdle(removeNotification = true)
                return START_NOT_STICKY
            }

            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: run {
                    stopIfIdle(removeNotification = false)
                    return START_NOT_STICKY
                }
                val task = Task(
                    url = url,
                    format = YtDlpFormat(
                        formatId = intent.getStringExtra(EXTRA_FORMAT_ID) ?: "best",
                        label = intent.getStringExtra(EXTRA_FORMAT_LABEL) ?: "Best quality",
                        isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO_ONLY, false),
                        ext = intent.getStringExtra(EXTRA_FORMAT_EXT) ?: "mp4"
                    ),
                    cookies = intent.getStringExtra(EXTRA_COOKIES),
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading…"
                )
                startTask(task)
            }

            else -> stopIfIdle(removeNotification = false)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Tasks outlive nothing: if the system destroys the service, stop the yt-dlp processes.
        jobs.keys.toList().forEach { cancelTask(it) }
        super.onDestroy()
    }

    private fun startTask(task: Task) {
        val appContext = applicationContext
        val taskId = "petal_social_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val rowId = PetalFetchDownloadBridge.stableId(taskId)

        if (!foregroundStarted) {
            try {
                startForeground(rowId.toInt(), initialNotification(rowId, task.title))
                foregroundStarted = true
            } catch (t: Throwable) {
                Log.e(TAG, "Could not enter foreground", t)
                PetalFetchDownloadBridge.updateLocalRow(
                    appContext, taskId, DownloadSource.YTDLP, task.title, task.url,
                    DownloadStatus.STATUS_FAILED,
                    failureReason = "Android did not allow the download service to start. Open Petal and try again."
                )
                PetalFetchDownloadBridge.setLocalHandlers(taskId, null, retryHandler(appContext, taskId, task))
                stopIfIdle(removeNotification = false)
                return
            }
        }

        PetalFetchDownloadBridge.updateLocalRow(
            appContext, taskId, DownloadSource.YTDLP, task.title, task.url, DownloadStatus.STATUS_PENDING
        )
        PetalFetchDownloadBridge.setLocalHandlers(
            taskId,
            onCancel = { cancelTask(taskId) },
            onRetry = retryHandler(appContext, taskId, task)
        )
        jobs[taskId] = scope.launch { runTask(appContext, taskId, task) }
    }

    private fun retryHandler(appContext: Context, taskId: String, task: Task): () -> Unit = {
        PetalFetchDownloadBridge.removeLocalRow(taskId)
        enqueue(appContext, task.url, task.format, task.cookies, task.title)
    }

    private suspend fun runTask(appContext: Context, taskId: String, task: Task) {
        val bridge = PetalFetchDownloadBridge
        val outputDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "Petal Social"
        )
        var lastPublish = 0L
        var wasCancelled = false
        try {
            val result = PetalYtDlpEngine.download(
                context = appContext,
                url = task.url,
                format = task.format,
                outputDir = outputDir,
                taskId = taskId,
                cookies = task.cookies
            ) { progress, line, etaSeconds ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastPublish >= 400L || progress >= 1f) {
                    lastPublish = now
                    val sizes = parseSizes(line)
                    val bytes = if (sizes.total > 0L) (sizes.total * progress).toLong() else 0L
                    bridge.updateLocalRow(
                        appContext, taskId, DownloadSource.YTDLP, task.title, task.url,
                        DownloadStatus.STATUS_RUNNING,
                        progress = progress,
                        bytes = bytes,
                        total = sizes.total,
                        speed = sizes.speed,
                        eta = etaSeconds.coerceAtLeast(0L)
                    )
                }
            }

            if (cancelledTasks.remove(taskId)) {
                wasCancelled = true
                return // row already removed by the bridge's cancel
            }

            result.fold(
                onSuccess = { filePath ->
                    var name = task.title
                    var size = 0L
                    if (filePath != null) {
                        val file = File(filePath)
                        name = file.name
                        size = file.length()
                        try { MediaScannerConnection.scanFile(appContext, arrayOf(filePath), null, null) }
                        catch (_: Exception) {}
                    }
                    bridge.updateLocalRow(
                        appContext, taskId, DownloadSource.YTDLP, name, task.url,
                        DownloadStatus.STATUS_SUCCESSFUL,
                        progress = 1f, bytes = size, total = size,
                        localUri = filePath?.let { "file://$it" }
                    )
                    bridge.setLocalHandlers(taskId, null, null)
                },
                onFailure = { err ->
                    Log.e(TAG, "Social download failed", err)
                    bridge.updateLocalRow(
                        appContext, taskId, DownloadSource.YTDLP, task.title, task.url,
                        DownloadStatus.STATUS_FAILED,
                        failureReason = describeFailure(err)
                    )
                    bridge.setLocalHandlers(taskId, null, retryHandler(appContext, taskId, task))
                }
            )
        } finally {
            jobs.remove(taskId)
            // A coroutine cancellation throws before the check above, so look at the marker here too.
            if (!wasCancelled) wasCancelled = cancelledTasks.remove(taskId)
            stopIfIdle(removeNotification = wasCancelled)
        }
    }

    private fun stopIfIdle(removeNotification: Boolean) {
        if (jobs.isNotEmpty()) return
        if (foregroundStarted) {
            if (removeNotification) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
                else @Suppress("DEPRECATION") stopForeground(true)
            } else {
                // Leave the completion/failure notification that PetalLiveAlertManager posts.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_DETACH)
                else @Suppress("DEPRECATION") stopForeground(false)
            }
            foregroundStarted = false
        }
        stopSelf()
    }

    // ── Notification (initial foreground notification only) ────────────────

    private fun initialNotification(rowId: Long, title: String): Notification {
        val cancelIntent = Intent(this, PetalDownloadCancelReceiver::class.java).apply {
            action = PetalDownloadCancelReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(PetalDownloadCancelReceiver.EXTRA_DOWNLOAD_ID, rowId)
        }
        val cancelPending = PendingIntent.getBroadcast(
            this, rowId.toInt(), cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_downloads", true)
        }
        val openPending = PendingIntent.getActivity(
            this, rowId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return LiveUpdateNotificationManager.buildLiveNotification(
            this, rowId, "Downloading $title", "Starting…", 0, true, false, "Starting…",
            openPending, cancelPending, null
        )
    }

    // ── yt-dlp output parsing ───────────────────────────────────────────────

    private class Sizes(val total: Long, val speed: Long)

    /** Best-effort parse of "[download]  45.0% of ~10.00MiB at 1.20MiB/s ETA 00:05". */
    private fun parseSizes(line: String): Sizes {
        if (line.isEmpty()) return Sizes(0L, 0L)
        val total = TOTAL_REGEX.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        val speed = SPEED_REGEX.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        return Sizes(total, speed)
    }

    private fun toBytes(number: String, unit: String): Long {
        val value = number.toDoubleOrNull() ?: return 0L
        val multiplier = when (unit.uppercase()) {
            "B" -> 1.0
            "KIB" -> 1024.0
            "MIB" -> 1024.0 * 1024
            "GIB" -> 1024.0 * 1024 * 1024
            "TIB" -> 1024.0 * 1024 * 1024 * 1024
            "KB" -> 1000.0
            "MB" -> 1000.0 * 1000
            "GB" -> 1000.0 * 1000 * 1000
            else -> return 0L
        }
        return (value * multiplier).toLong()
    }

    companion object {
        private const val TAG = "PetalSocialDlService"

        const val ACTION_DOWNLOAD    = "com.petal.browser.action.SOCIAL_DOWNLOAD"
        const val ACTION_CANCEL      = "com.petal.browser.action.SOCIAL_DOWNLOAD_CANCEL"
        const val EXTRA_URL          = "url"
        const val EXTRA_FORMAT_ID    = "format_id"
        const val EXTRA_FORMAT_EXT   = "format_ext"
        const val EXTRA_FORMAT_LABEL = "format_label"
        const val EXTRA_IS_AUDIO_ONLY = "is_audio_only"
        const val EXTRA_COOKIES      = "cookies"
        const val EXTRA_TITLE        = "title"

        private val TOTAL_REGEX = Regex("""of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)""", RegexOption.IGNORE_CASE)
        private val SPEED_REGEX = Regex("""at\s+([\d.]+)\s*([KMGT]?i?B)/s""", RegexOption.IGNORE_CASE)

        // Process-wide so cancel/retry handlers registered with the bridge never capture a Service.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val jobs = ConcurrentHashMap<String, Job>()
        private val cancelledTasks: MutableSet<String> =
            java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        /** Cancels one task: stops the yt-dlp process and the coroutine. The row is removed by the bridge. */
        private fun cancelTask(taskId: String) {
            cancelledTasks.add(taskId)
            PetalYtDlpEngine.cancel(taskId)
            jobs[taskId]?.cancel()
        }

        private fun describeFailure(err: Throwable): String {
            val raw = err.message.orEmpty()
            return when {
                raw.contains("No space left", ignoreCase = true) -> DownloadFailure.INSUFFICIENT_STORAGE.userMessage
                raw.contains("Unable to resolve host", ignoreCase = true) ||
                    raw.contains("Network is unreachable", ignoreCase = true) ||
                    raw.contains("timed out", ignoreCase = true) -> DownloadFailure.NO_NETWORK.userMessage
                raw.contains("Unsupported URL", ignoreCase = true) -> "This site or link isn't supported."
                raw.isBlank() -> "Download failed. Tap Retry to try again."
                else -> raw.lineSequence().lastOrNull { it.isNotBlank() }?.trim()?.take(160) ?: "Download failed."
            }
        }

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
