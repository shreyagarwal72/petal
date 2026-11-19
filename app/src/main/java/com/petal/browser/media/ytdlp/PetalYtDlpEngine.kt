package com.petal.browser.media.ytdlp

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.compose.downloads.PetalLiveAlertManager
import com.petal.browser.download.DownloadSource
import com.petal.browser.download.PetalSmartDownloadRouter
import com.petal.browser.media.sniffer.PetalMediaSniffer
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.DownloadProgressCallback
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Petal's native yt-dlp engine. yt-dlp, Python and ffmpeg ship inside the app (youtubedl-android),
 * so YouTube and other extractor-backed sites download with no external app involved.
 * Progress is published as a Petal-owned row ([DownloadSource.YTDLP]) in the download manager.
 */
object PetalYtDlpEngine {
    private const val TAG = "PetalYtDlpEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private val _state = MutableStateFlow<YtDlpEngineState>(YtDlpEngineState.Idle)
    val state: StateFlow<YtDlpEngineState> = _state.asStateFlow()

    /** Initialises yt-dlp and ffmpeg once. Safe to call repeatedly; a failed init can be retried. */
    suspend fun ensureInitialized(context: Context): Boolean = initMutex.withLock {
        if (_state.value is YtDlpEngineState.Ready) return@withLock true
        _state.value = YtDlpEngineState.Initializing
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                _state.value = YtDlpEngineState.Ready
                true
            } catch (t: Throwable) {
                Log.e(TAG, "yt-dlp init failed", t)
                _state.value = YtDlpEngineState.Failed(t.message ?: "yt-dlp could not start")
                false
            }
        }
    }

    /** Updates the bundled yt-dlp to the latest stable build. Failure is non-fatal. */
    suspend fun updateBinary(context: Context): Boolean {
        if (!ensureInitialized(context)) return false
        return withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
                true
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp update failed", t)
                false
            }
        }
    }

    private fun YoutubeDLRequest.applyNetwork(pageUrl: String, userAgent: String?, referer: String?, cookies: String?) {
        addOption("--no-playlist")
        addOption("--no-warnings")
        userAgent?.takeIf { it.isNotBlank() }?.let { addOption("--user-agent", it) }
        referer?.takeIf { it.isNotBlank() }?.let { addOption("--referer", it) }
        val cookie = cookies?.takeIf { it.isNotBlank() } ?: PetalMediaSniffer.getCookiesForUrl(pageUrl)
        cookie?.takeIf { it.isNotBlank() }?.let { addOption("--add-header", "Cookie:$it") }
    }

    /** Reads title, thumbnail and the available formats of [pageUrl]. */
    suspend fun fetchInfo(
        context: Context,
        pageUrl: String,
        userAgent: String? = null,
        referer: String? = null,
        cookies: String? = null
    ): Result<YtDlpInfo> {
        if (!ensureInitialized(context)) {
            val msg = (_state.value as? YtDlpEngineState.Failed)?.message ?: "yt-dlp is not ready"
            return Result.failure(IllegalStateException(msg))
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = YoutubeDLRequest(pageUrl).apply { applyNetwork(pageUrl, userAgent, referer, cookies) }
                val info = YoutubeDL.getInstance().getInfo(request)
                val raw = (info.formats ?: arrayListOf()).map { f ->
                    YtDlpRawFormat(
                        formatId = f.formatId ?: "",
                        ext = f.ext,
                        height = f.height,
                        fps = f.fps,
                        vcodec = f.vcodec,
                        acodec = f.acodec,
                        sizeBytes = (if (f.fileSize > 0) f.fileSize else f.fileSizeApproximate).takeIf { it > 0 }
                    )
                }
                val (video, audio) = YtDlpOptionBuilder.build(raw)
                Result.success(
                    YtDlpInfo(
                        pageUrl = pageUrl,
                        title = info.title?.takeIf { it.isNotBlank() } ?: pageUrl,
                        thumbnail = info.thumbnail,
                        uploader = info.uploader,
                        durationSeconds = info.duration.toLong().takeIf { it > 0 },
                        videoOptions = video,
                        audioOptions = audio
                    )
                )
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp getInfo failed for $pageUrl", t)
                Result.failure(t)
            }
        }
    }

    fun isDownloading(taskId: String): Boolean = jobs[taskId]?.isActive == true

    fun cancel(taskId: String) {
        cancelled.add(taskId)
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) }
        jobs.remove(taskId)?.cancel()
    }

    /** Starts a download. Returns the task key used by the download manager row. */
    fun download(
        context: Context,
        info: YtDlpInfo,
        option: YtDlpOption,
        userAgent: String? = null,
        referer: String? = null,
        cookies: String? = null,
        taskId: String = "ytdlp-" + UUID.randomUUID(),
        onFinished: (File?, String?) -> Unit = { _, _ -> }
    ): String {
        val app = context.applicationContext
        val safeTitle = sanitize(info.title)
        val displayName = "$safeTitle.${option.extension}"
        cancelled.remove(taskId)

        fun row(status: Int, progress: Float? = null, speed: Long = 0L, eta: Long = 0L,
                file: File? = null, failure: String? = null) {
            val total = option.sizeBytes ?: 0L
            PetalFetchDownloadBridge.updateLocalRow(
                context = app,
                key = taskId,
                source = DownloadSource.YTDLP,
                fileName = displayName,
                fileUrl = info.pageUrl,
                status = status,
                progress = progress,
                bytes = if (file != null) file.length() else ((progress ?: 0f) * total).toLong(),
                total = if (file != null) file.length() else total,
                speed = speed,
                eta = eta,
                localUri = file?.let { Uri.fromFile(it).toString() },
                failureReason = failure
            )
        }

        PetalFetchDownloadBridge.setLocalHandlers(
            key = taskId,
            onCancel = {
                cancel(taskId)
                row(DownloadStatus.STATUS_FAILED, failure = "Cancelled")
            },
            onRetry = { download(app, info, option, userAgent, referer, cookies, taskId, onFinished) }
        )
        row(DownloadStatus.STATUS_PENDING, progress = 0f)
        PetalLiveAlertManager.trackDownload(app, PetalFetchDownloadBridge.stableId(taskId), displayName)

        jobs[taskId] = scope.launch {
            val workDir = File(app.cacheDir, "ytdlp_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                if (!ensureInitialized(app)) {
                    throw IllegalStateException((_state.value as? YtDlpEngineState.Failed)?.message ?: "yt-dlp is not ready")
                }
                row(DownloadStatus.STATUS_RUNNING, progress = 0f)

                val request = YoutubeDLRequest(info.pageUrl).apply {
                    applyNetwork(info.pageUrl, userAgent, referer, cookies)
                    addOption("-f", option.selector)
                    addOption("-o", File(workDir, "%(title).120B.%(ext)s").absolutePath)
                    addOption("--windows-filenames")
                    addOption("--no-mtime")
                    addOption("--concurrent-fragments", "4")
                    addOption("--embed-metadata")
                    if (option.isAudio) {
                        addOption("-x")
                        addOption("--audio-format", option.extension)
                        addOption("--audio-quality", "0")
                    } else {
                        addOption("--merge-output-format", option.extension)
                    }
                }

                YoutubeDL.getInstance().execute(request, taskId, DownloadProgressCallback { percent, eta, line ->
                    if (taskId in cancelled) return@DownloadProgressCallback
                    val fraction = (percent / 100f).coerceIn(0f, 0.99f)
                    row(
                        status = DownloadStatus.STATUS_RUNNING,
                        progress = fraction,
                        speed = YtDlpOptionBuilder.parseSpeed(line),
                        eta = eta.coerceAtLeast(0L)
                    )
                })

                if (taskId in cancelled) throw InterruptedException("Cancelled")

                val produced = workDir.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && it.length() > 0L }
                    ?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("yt-dlp produced no file")

                val finalName = produced.name
                val mime = mimeFor(produced.extension)
                val destDir = PetalSmartDownloadRouter.resolveDestinationDirectory(app, finalName, mime)
                destDir.mkdirs()
                val target = uniqueFile(destDir, finalName)
                if (!produced.renameTo(target)) {
                    produced.copyTo(target, overwrite = false)
                    produced.delete()
                }
                runCatching { MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), arrayOf(mime), null) }

                PetalFetchDownloadBridge.updateLocalRow(
                    context = app,
                    key = taskId,
                    source = DownloadSource.YTDLP,
                    fileName = target.name,
                    fileUrl = info.pageUrl,
                    status = DownloadStatus.STATUS_SUCCESSFUL,
                    progress = 1f,
                    bytes = target.length(),
                    total = target.length(),
                    localUri = Uri.fromFile(target).toString()
                )
                withContext(Dispatchers.Main) { onFinished(target, null) }
            } catch (t: Throwable) {
                val wasCancelled = taskId in cancelled
                val reason = if (wasCancelled) "Cancelled" else (t.message?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: "Download failed")
                Log.w(TAG, "yt-dlp download ended: $reason", t)
                row(DownloadStatus.STATUS_FAILED, failure = reason)
                withContext(Dispatchers.Main) { onFinished(null, reason) }
            } finally {
                jobs.remove(taskId)
                cancelled.remove(taskId)
                runCatching { workDir.deleteRecursively() }
            }
        }
        return taskId
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifBlank { "video_${System.currentTimeMillis()}" }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
