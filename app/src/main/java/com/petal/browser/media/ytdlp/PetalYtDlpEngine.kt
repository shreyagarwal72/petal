package com.petal.browser.media.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Petal Social Downloader engine.
 *
 * The implementation follows Seal's proven youtubedl-android flow:
 * initialize the yt-dlp/FFmpeg/aria2c components before use, fetch the actual
 * yt-dlp JSON response, and pass a real yt-dlp format selector to downloads.
 *
 * The previous implementation used getInfo() but then discarded the formats
 * returned by yt-dlp and replaced them with hard-coded selectors. It also did
 * not protect the UI from the application's asynchronous yt-dlp initialization.
 */
object PetalYtDlpEngine {

    private const val TAG = "PetalYtDlpEngine"
    private const val OUTPUT_TEMPLATE = "%(title).100B [%(id)s].%(ext)s"

    private val initialized = AtomicBoolean(false)
    private val initLock = Any()

    /** Eager initialization hook used by PetalApplication. Safe to call repeatedly. */
    fun initialize(context: Context) {
        ensureInitialized(context)
    }

    private fun ensureInitialized(context: Context) {
        if (initialized.get()) return

        synchronized(initLock) {
            if (initialized.get()) return

            // Match Seal's startup order. These calls are idempotent in
            // youtubedl-android and make this engine safe even when the user
            // taps "Fetch" before PetalApplication's background init finishes.
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            Aria2c.getInstance().init(context.applicationContext)
            initialized.set(true)
            Log.i(TAG, "yt-dlp engine initialized")
        }
    }

    suspend fun fetchInfo(
        context: Context,
        url: String,
        cookies: String? = null
    ): YtDlpMediaInfo? = withContext(Dispatchers.IO) {
        try {
            ensureInitialized(context)

            val request = YoutubeDLRequest(url.trim()).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--socket-timeout", "15")
                addOption("--retries", "2")
                addOption("--skip-download")
                if (!cookies.isNullOrBlank()) {
                    addOption("--add-header", "Cookie:$cookies")
                }
            }

            val processId = "petal_social_info_${System.nanoTime()}"
            val response = YoutubeDL.getInstance().execute(request, processId, null)
            parseInfo(url, response.out)
        } catch (t: Throwable) {
            Log.e(TAG, "fetchInfo failed for $url", t)
            null
        }
    }

    private fun parseInfo(url: String, json: String): YtDlpMediaInfo? {
        val root = JSONObject(json)
        val title = root.optString("title").ifBlank { "Media" }
        val uploader = root.optString("uploader")
            .ifBlank { root.optString("channel") }
            .ifBlank { null }
        val thumbnail = root.optString("thumbnail").ifBlank { null }
        val duration = root.optDouble("duration", Double.NaN)
            .takeUnless { it.isNaN() }
            ?.toInt()

        val heights = mutableSetOf<Int>()
        var hasAudioVideo = false
        val formats = root.optJSONArray("formats")

        if (formats != null) {
            for (i in 0 until formats.length()) {
                val format = formats.optJSONObject(i) ?: continue
                val height = format.optInt("height", 0)
                if (height > 0) heights += height

                val vcodec = format.optString("vcodec")
                val acodec = format.optString("acodec")
                if (vcodec.isNotBlank() && vcodec != "none" &&
                    acodec.isNotBlank() && acodec != "none"
                ) {
                    hasAudioVideo = true
                }
            }
        }

        // Some extractors omit a useful formats array in their compact result.
        // "best" still works in that case, so do not reject the media item.
        val videoOptions = YtDlpFormat.buildVideoOptions(
            heights = heights,
            hasAudioVideo = hasAudioVideo
        )
        val options = (videoOptions + YtDlpFormat.audioOption())
            .distinctBy { it.formatId }

        return YtDlpMediaInfo(
            url = url,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnail,
            durationSeconds = duration,
            formats = options
        )
    }

    suspend fun download(
        context: Context,
        url: String,
        format: YtDlpFormat,
        outputDir: File,
        taskId: String,
        cookies: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized(context)
            outputDir.mkdirs()

            val startedAt = System.currentTimeMillis()
            val request = YoutubeDLRequest(url.trim()).apply {
                addOption("-o", OUTPUT_TEMPLATE)
                addOption("--no-mtime")
                addOption("--no-playlist")
                addOption("--newline")
                addOption("--no-warnings")
                addOption("--retries", "5")
                addOption("--fragment-retries", "5")
                addOption("--socket-timeout", "15")
                addOption("--concurrent-fragments", "4")
                addOption("--add-metadata")

                if (format.isAudioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "m4a")
                    addOption("--audio-quality", "0")
                } else {
                    addOption("-f", format.formatId)
                }

                if (cookies.isNullOrBlank().not()) {
                    addOption("--add-header", "Cookie:$cookies")
                }

                addOption("-P", outputDir.absolutePath)
            }

            YoutubeDL.getInstance().execute(request, taskId) { progress, _, line ->
                onProgress(
                    (progress / 100f).coerceIn(0f, 1f),
                    line.orEmpty()
                )
            }

            val completedFile = findCompletedFile(outputDir, startedAt, format)
            Result.success(completedFile?.absolutePath)
        } catch (t: Throwable) {
            Log.e(TAG, "download failed for $url", t)
            Result.failure(t)
        }
    }

    private fun findCompletedFile(
        outputDir: File,
        startedAt: Long,
        format: YtDlpFormat
    ): File? {
        val allowedExtensions = if (format.isAudioOnly) {
            setOf("m4a", "mp3", "opus", "ogg", "wav", "aac", "webm")
        } else {
            setOf("mp4", "mkv", "webm", "mov", "m4v")
        }

        return outputDir.walkTopDown()
            .filter { it.isFile }
            .filter { !it.name.endsWith(".part", ignoreCase = true) }
            .filter { it.extension.lowercase(Locale.ROOT) in allowedExtensions }
            .filter { it.length() > 0L }
            .filter { it.lastModified() >= startedAt - 5_000L }
            .maxByOrNull { it.lastModified() }
    }

    fun cancel(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to cancel task $taskId", t)
        }
    }

    fun version(context: Context): String = try {
        ensureInitialized(context)
        YoutubeDL.getInstance().version(null) ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
}
