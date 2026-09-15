package com.petal.browser.media.ytdlp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.petal.browser.media.sniffer.YouTubeExtractor
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
 * Enhanced following Seal's proven architecture:
 * - Proper Netscape cookiejar generation from SQLite / CookieManager instead of brittle --add-header
 * - YouTube player_client extractor fallbacks (Android, Web Safari, MWeb, iOS)
 * - Automatic and on-demand yt-dlp extractor updates from GitHub
 * - Built-in InnerTube YouTubeExtractor fallback for resilient stream recovery
 * - Aria2c multi-connection accelerated downloading
 */
object PetalYtDlpEngine {

    private const val TAG = "PetalYtDlpEngine"
    private const val OUTPUT_TEMPLATE = "%(title).100B [%(id)s].%(ext)s"
    private const val PREFS_NAME = "petal_ytdlp_engine_prefs"
    private const val KEY_LAST_UPDATE = "last_ytdlp_update_time"
    private const val KEY_VERSION = "ytdlp_version"

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

            // Match Seal's startup order. These calls are idempotent in youtubedl-android.
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            Aria2c.getInstance().init(context.applicationContext)
            initialized.set(true)
            Log.i(TAG, "yt-dlp engine initialized")
        }
    }

    /**
     * Updates the yt-dlp extractor to the latest version directly from GitHub releases/nightlies.
     * Matches Seal's UpdateUtil implementation.
     */
    suspend fun updateEngine(
        context: Context,
        channel: YoutubeDL.UpdateChannel = YoutubeDL.UpdateChannel.STABLE
    ): Result<YoutubeDL.UpdateStatus?> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized(context)
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, channel)
            val currentVer = version(context)
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sp.edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .putString(KEY_VERSION, currentVer)
                .apply()
            Log.i(TAG, "yt-dlp update status: $status, current version: $currentVer")
            Result.success(status)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to update yt-dlp", t)
            Result.failure(t)
        }
    }

    /**
     * Periodically checks if yt-dlp is outdated and performs background update.
     */
    suspend fun checkAutoUpdate(context: Context) = withContext(Dispatchers.IO) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastUpdate = sp.getLong(KEY_LAST_UPDATE, 0L)
            val now = System.currentTimeMillis()
            // Auto-update if never updated or older than 24 hours
            if (lastUpdate == 0L || (now - lastUpdate) > 86_400_000L) {
                updateEngine(context, YoutubeDL.UpdateChannel.STABLE)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "checkAutoUpdate background check skipped", t)
        }
    }

    fun isYouTube(url: String): Boolean {
        val host = try {
            Uri.parse(url).host?.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            null
        } ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be")
    }

    suspend fun fetchInfo(
        context: Context,
        url: String,
        cookies: String? = null
    ): YtDlpMediaInfo? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return@withContext null

        ensureInitialized(context)

        // 1. Attempt primary extraction with yt-dlp
        var info = fetchInfoInternal(context, trimmed, cookies)

        // 2. If it failed and extractor might be outdated, attempt update and retry once
        if (info == null || info.formats.isEmpty()) {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastUpdate = sp.getLong(KEY_LAST_UPDATE, 0L)
            // Update if never updated or older than 6 hours
            if (System.currentTimeMillis() - lastUpdate > 6 * 3600_000L) {
                Log.i(TAG, "yt-dlp extraction failed for $trimmed, checking update...")
                val updateRes = updateEngine(context, YoutubeDL.UpdateChannel.STABLE)
                if (updateRes.isSuccess) {
                    info = fetchInfoInternal(context, trimmed, cookies)
                }
            }
        }

        // 3. Resilient fallback: For YouTube videos, if yt-dlp is blocked or fails,
        // use Petal's native InnerTube YouTubeExtractor
        if ((info == null || info.formats.isEmpty()) && isYouTube(trimmed)) {
            Log.i(TAG, "yt-dlp extraction failed on YouTube; activating native YouTubeExtractor fallback")
            info = fetchInfoWithYouTubeExtractor(trimmed)
        }

        info
    }

    private fun fetchInfoInternal(
        context: Context,
        url: String,
        cookies: String?
    ): YtDlpMediaInfo? {
        val cookiesFile = prepareCookies(context, url, cookies)
        return try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--socket-timeout", "15")
                addOption("--retries", "2")
                addOption("--skip-download")

                if (isYouTube(url)) {
                    addOption("--extractor-args", "youtube:player_client=android,web,web_safari,mweb,ios")
                    addOption("--compat-options", "no-youtube-unavailable-videos")
                }

                if (cookiesFile != null && cookiesFile.exists()) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }

                val ua = getUserAgent(context)
                if (ua.isNotBlank()) {
                    addOption("--add-header", "User-Agent:$ua")
                }
            }

            val processId = "petal_social_info_${System.nanoTime()}"
            val response = YoutubeDL.getInstance().execute(request, processId, null)
            parseInfo(url, response.out)
        } catch (t: Throwable) {
            Log.e(TAG, "fetchInfoInternal failed for $url: ${t.message}")
            null
        } finally {
            cookiesFile?.delete()
        }
    }

    private suspend fun fetchInfoWithYouTubeExtractor(url: String): YtDlpMediaInfo? {
        return try {
            val result = YouTubeExtractor.extractStreams(url) ?: return null
            if (result.streams.isEmpty()) return null

            val videoId = YouTubeExtractor.extractVideoId(url)
            val thumbnail = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null

            val formats = mutableListOf<YtDlpFormat>()

            // Multiplexed formats (video + audio)
            val muxed = result.streams.filter { !it.isAudio && !it.isVideoOnly && it.url.isNotBlank() }
            muxed.forEach { stream ->
                formats += YtDlpFormat(
                    formatId = stream.url,
                    label = "${stream.quality} (MP4)",
                    isAudioOnly = false,
                    fileSizeApprox = stream.sizeBytes,
                    ext = "mp4",
                    isDirectUrl = true
                )
            }

            // Audio-only formats
            val audios = result.streams.filter { it.isAudio && it.url.isNotBlank() }
            audios.forEach { stream ->
                formats += YtDlpFormat(
                    formatId = stream.url,
                    label = "Audio ${stream.quality}",
                    isAudioOnly = true,
                    fileSizeApprox = stream.sizeBytes,
                    ext = "m4a",
                    isDirectUrl = true
                )
            }

            // Fallback to any valid stream if no muxed
            if (formats.isEmpty()) {
                result.streams.filter { it.url.isNotBlank() }.forEach { stream ->
                    formats += YtDlpFormat(
                        formatId = stream.url,
                        label = "${stream.quality} (${if (stream.isAudio) "Audio" else "Video"})",
                        isAudioOnly = stream.isAudio,
                        fileSizeApprox = stream.sizeBytes,
                        ext = if (stream.isAudio) "m4a" else "mp4",
                        isDirectUrl = true
                    )
                }
            }

            if (formats.isEmpty()) return null

            YtDlpMediaInfo(
                url = url,
                title = result.title,
                uploader = "YouTube",
                thumbnailUrl = thumbnail,
                durationSeconds = null,
                formats = formats
            )
        } catch (t: Throwable) {
            Log.w(TAG, "YouTubeExtractor fallback failed", t)
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

        // Build selectors from heights actually advertised by yt-dlp
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
        val cookiesFile = prepareCookies(context, url, cookies)
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

                if (isYouTube(url)) {
                    addOption("--extractor-args", "youtube:player_client=android,web,web_safari,mweb,ios")
                    addOption("--compat-options", "no-youtube-unavailable-videos")
                }

                if (format.isAudioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "m4a")
                    addOption("--audio-quality", "0")
                } else {
                    addOption("-f", format.formatId)
                }

                if (cookiesFile != null && cookiesFile.exists()) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }

                val ua = getUserAgent(context)
                if (ua.isNotBlank()) {
                    addOption("--add-header", "User-Agent:$ua")
                }

                try {
                    addOption("--downloader", "libaria2c.so")
                    addOption("--downloader-args", "aria2c:-s 4 -x 4 -k 1M")
                } catch (_: Exception) {}

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
        } finally {
            cookiesFile?.delete()
        }
    }

    private fun prepareCookies(context: Context, targetUrl: String, rawCookies: String?): File? {
        return try {
            try {
                val cm = CookieManager.getInstance()
                if (cm.hasCookies()) cm.flush()
            } catch (_: Exception) {}

            val parsedCookies = mutableListOf<CookieEntry>()

            // 1. Try reading from WebView's SQLite cookie store (matches Seal's implementation)
            val dbCandidates = listOf(
                File(context.dataDir, "app_webview/Default/Cookies"),
                File(context.dataDir, "app_webview/Cookies")
            )
            val dbFile = dbCandidates.firstOrNull { it.exists() && it.canRead() }
            if (dbFile != null) {
                try {
                    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        val projection = arrayOf("host_key", "expires_utc", "path", "name", "value", "is_secure")
                        db.query("cookies", projection, null, null, null, null, null).use { cursor ->
                            val hostIdx = cursor.getColumnIndex("host_key")
                            val expIdx = cursor.getColumnIndex("expires_utc")
                            val pathIdx = cursor.getColumnIndex("path")
                            val nameIdx = cursor.getColumnIndex("name")
                            val valIdx = cursor.getColumnIndex("value")
                            val secIdx = cursor.getColumnIndex("is_secure")

                            while (cursor.moveToNext()) {
                                val hostKey = if (hostIdx >= 0) cursor.getString(hostIdx) else ""
                                val expiry = if (expIdx >= 0) cursor.getLong(expIdx) else 0L
                                val path = if (pathIdx >= 0) cursor.getString(pathIdx) else "/"
                                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else ""
                                val value = if (valIdx >= 0) cursor.getString(valIdx) else ""
                                val isSecure = if (secIdx >= 0) cursor.getLong(secIdx) == 1L else true

                                if (name.isNotBlank()) {
                                    val domain = if (hostKey.startsWith(".")) hostKey else ".$hostKey"
                                    parsedCookies.add(CookieEntry(domain, path, isSecure, expiry, name, value))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read SQLite cookie db", e)
                }
            }

            // 2. Fallback to parsing CookieManager or provided string for the target URL
            if (parsedCookies.isEmpty()) {
                val cookieString = rawCookies?.takeIf { it.isNotBlank() }
                    ?: try { CookieManager.getInstance().getCookie(targetUrl) } catch (_: Exception) { null }

                if (!cookieString.isNullOrBlank()) {
                    val host = try { Uri.parse(targetUrl).host } catch (_: Exception) { null } ?: "youtube.com"
                    val domain = if (host.startsWith(".")) host else ".$host"
                    cookieString.split(";").forEach { pair ->
                        val eq = pair.indexOf('=')
                        if (eq > 0) {
                            val name = pair.substring(0, eq).trim()
                            val value = pair.substring(eq + 1).trim()
                            if (name.isNotEmpty()) {
                                parsedCookies.add(CookieEntry(domain, "/", true, 2147483647L, name, value))
                            }
                        }
                    }
                }
            }

            if (parsedCookies.isEmpty()) return null

            val cookiesFile = File(context.cacheDir, "petal_cookies_${System.currentTimeMillis()}.txt")
            cookiesFile.bufferedWriter().use { writer ->
                writer.write("# Netscape HTTP Cookie File\n")
                writer.write("# Auto-generated by Petal Browser\n")
                for (c in parsedCookies) {
                    writer.write("${c.domain}\tTRUE\t${if (c.path.isBlank()) "/" else c.path}\t${c.isSecure.toString().uppercase(Locale.ROOT)}\t${if (c.expiry > 0) c.expiry else 2147483647L}\t${c.name}\t${c.value}\n")
                }
            }
            cookiesFile
        } catch (t: Throwable) {
            Log.w(TAG, "prepareCookies failed", t)
            null
        }
    }

    private data class CookieEntry(
        val domain: String,
        val path: String,
        val isSecure: Boolean,
        val expiry: Long,
        val name: String,
        val value: String
    )

    private fun getUserAgent(context: Context): String = try {
        WebSettings.getDefaultUserAgent(context)
    } catch (_: Exception) {
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
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
        YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
}
