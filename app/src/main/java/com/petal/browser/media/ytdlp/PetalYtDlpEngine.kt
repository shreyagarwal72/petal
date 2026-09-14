package com.petal.browser.media.ytdlp

import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Petal Social Downloader — yt-dlp Engine
 *
 * Thin wrapper around youtubedl-android that provides the primitives needed
 * by Petal's social download feature:
 *  - Metadata fetch (title, thumbnail, formats, duration)
 *  - File download with real-time progress callbacks
 *  - Task cancellation
 *
 * All operations are suspend functions — call from a coroutine.
 * They execute on [Dispatchers.IO] internally and are safe to call
 * from any thread.
 *
 * This object has NO knowledge of UI state — that lives in the composable.
 */
object PetalYtDlpEngine {

    private const val TAG = "PetalYtDlpEngine"

    /**
     * Fetch media metadata for [url] without downloading.
     *
     * Passes [cookies] (from GeckoView's CookieManager) when provided, so
     * login-gated content (Instagram, Twitter, etc.) resolves correctly.
     *
     * @return [YtDlpMediaInfo] on success, null on any failure.
     */
    suspend fun fetchInfo(url: String, cookies: String? = null): YtDlpMediaInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = YoutubeDLRequest(url).apply {
                    addOption("--dump-single-json")
                    addOption("--no-playlist")
                    addOption("--no-warnings")
                    if (!cookies.isNullOrBlank()) {
                        addOption("--add-header", "Cookie:$cookies")
                    }
                }
                val info = YoutubeDL.getInstance().getInfo(request)
                if (info == null) {
                    Log.w(TAG, "yt-dlp returned null info for $url")
                    return@withContext null
                }
                val isAudioOnly = info.ext != null &&
                    (info.ext == "m4a" || info.ext == "mp3" || info.ext == "ogg" || info.ext == "opus")
                YtDlpMediaInfo(
                    url = url,
                    title = info.title ?: url,
                    uploader = info.uploader ?: info.channel,
                    thumbnailUrl = info.thumbnail,
                    durationSeconds = info.duration?.toInt(),
                    formats = YtDlpFormat.buildOptions(hasVideo = !isAudioOnly)
                )
            } catch (t: Throwable) {
                Log.e(TAG, "fetchInfo failed for $url", t)
                null
            }
        }

    /**
     * Download [url] to [outputDir] using [format].
     *
     * @param taskId   Unique ID for this download task — used for cancellation.
     * @param cookies  Optional cookie string from GeckoView CookieManager.
     * @param onProgress  Called with (0f–1f progress, current yt-dlp output line).
     * @return [Result] wrapping the downloaded file path (may be null if not parseable).
     */
    suspend fun download(
        url: String,
        format: YtDlpFormat,
        outputDir: File,
        taskId: String,
        cookies: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val ffmpegPath = try {
                FFmpeg.getInstance().ffmpegPath?.absolutePath
            } catch (_: Exception) { null }

            val request = YoutubeDLRequest(url).apply {
                addOption("-f", format.formatId)
                addOption("-o", "%(title).100B [%(id)s].%(ext)s")
                addOption("--merge-output-format", format.ext)
                addOption("--no-playlist")
                if (!ffmpegPath.isNullOrBlank()) {
                    addOption("--ffmpeg-location", ffmpegPath)
                }
                // Aria2c for faster parallel-segment downloads
                addOption("--downloader", "aria2c")
                addOption("--downloader-args", "aria2c:-x 16 -k 1M")
                // Audio extras
                if (format.isAudioOnly) {
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")
                }
                // Subtitles for video
                if (!format.isAudioOnly) {
                    addOption("--embed-subs")
                    addOption("--sub-lang", "en.*,en")
                }
                addOption("-P", outputDir.absolutePath)
                if (!cookies.isNullOrBlank()) {
                    addOption("--add-header", "Cookie:$cookies")
                }
                addOption("--retries", "5")
                addOption("--fragment-retries", "5")
            }

            var lastFilePath: String? = null
            YoutubeDL.getInstance().execute(request, taskId) { progress, _, line ->
                onProgress(progress / 100f, line ?: "")
                if (line != null) {
                    when {
                        line.contains("[download] Destination:") ->
                            lastFilePath = line.substringAfter("[download] Destination:").trim()
                        line.contains("Merging formats into") ->
                            lastFilePath = line.substringAfter("Merging formats into")
                                .trim().removeSurrounding("\"")
                    }
                }
            }
            Result.success(lastFilePath)
        } catch (t: Throwable) {
            Log.e(TAG, "download failed for $url", t)
            Result.failure(t)
        }
    }

    /** Cancel a running yt-dlp task by [taskId]. Safe to call from any thread. */
    fun cancel(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessGroup(taskId)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to cancel task $taskId", t)
        }
    }

    /** Returns the currently installed yt-dlp version string. */
    fun version(): String = try {
        YoutubeDL.getInstance().version(null) ?: "unknown"
    } catch (_: Exception) { "unknown" }
}
