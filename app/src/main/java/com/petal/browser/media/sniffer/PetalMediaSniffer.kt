package com.petal.browser.media.sniffer

import android.content.Context
import android.webkit.MimeTypeMap
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.download.SafeDownloadValues
import java.util.Locale

/**
 * App-level facade for the media sniffer.
 *
 * Detection remains independent from the UI. User-initiated direct-media
 * downloads are handed to Petal's own Fetch2/OkHttp download manager so they
 * appear in Petal's Download Manager and get Petal's pause/resume/retry,
 * progress and notification handling.
 */
object PetalMediaSniffer {
    private val UNSAFE_DOWNLOAD_HEADERS = setOf(
        "host",
        "content-length",
        "content-encoding",
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade"
    )

    val interceptor = MediaInterceptor()

    fun setActivePage(pageId: String) {
        interceptor.setActivePage(pageId)
    }

    fun setActivePage(pageId: String, pageUrl: String) {
        interceptor.setActivePage(pageId, pageUrl)
        PetalMediaSnifferOverlayBridge.setCurrentPageUrl(pageUrl)
    }

    fun clear() {
        interceptor.clear()
        PetalMediaSnifferOverlayBridge.setCurrentPageUrl("")
    }

    fun onNetworkMedia(url: String, headers: Map<String, String> = emptyMap()) =
        interceptor.onMediaRequestDetected(url, headers)

    fun onAggressiveMedia(
        url: String,
        mimeType: String,
        cookies: String? = null,
        sizeBytes: Long? = null
    ) = interceptor.onAggressiveMediaGrabbed(url, mimeType, cookies, sizeBytes)

    /**
     * Sends a detected direct media URL through Petal's native download manager.
     *
     * HLS/DASH manifests intentionally return without enqueueing because Petal's
     * normal Fetch2 downloader downloads files/byte streams; it does not remux a
     * segmented .m3u8/.mpd presentation into a single media file.
     *
     * [onEnqueued] is called only after Fetch2 has accepted the request and
     * assigned its Petal download ID. This lets the UI close the media sheet only
     * after the download is actually handed to Petal's Download Manager.
     */
    fun download(
        context: Context,
        item: MediaInterceptor.DetectedMedia,
        onEnqueued: ((Long) -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        val url = item.url.trim()
        if (!SafeDownloadValues.isHttpUrl(url)) {
            onFailed?.invoke()
            return
        }

        val extension = extensionFor(item)
        if (extension == null) {
            onFailed?.invoke()
            return
        }

        val mimeType = resolveMimeType(item, extension)
        val fileName = SafeDownloadValues.fileName(url, null, mimeType)
            .let { ensureExtension(it, extension) }

        val userAgent = findHeader(item.headers, "User-Agent")
        val cookie = item.cookies?.takeIf { it.isNotBlank() }
            ?: findHeader(item.headers, "Cookie")

        // PetalDownloadEngine already handles User-Agent and Cookie explicitly.
        // Do not pass them again in extraHeaders, which would create duplicate
        // request headers with some HTTP clients.
        val extraHeaders = LinkedHashMap<String, String>()
        item.headers.forEach { (name, value) ->
            if (!isSafeDownloadHeader(name, value)) return@forEach
            if (name.equals("User-Agent", ignoreCase = true)) return@forEach
            if (name.equals("Cookie", ignoreCase = true)) return@forEach
            extraHeaders[name] = value
        }

        // The dedicated fields are the authoritative values captured from the
        // media request. Preserve Origin/Referer as normal request headers too.
        item.referrer?.takeIf { it.isNotBlank() }?.let {
            extraHeaders["Referer"] = it
        }
        item.origin?.takeIf { it.isNotBlank() }?.let {
            extraHeaders["Origin"] = it
        }

        try {
            PetalFetchDownloadBridge.enqueueMediaDownload(
                context = context.applicationContext,
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                userAgent = userAgent,
                cookie = cookie,
                headers = extraHeaders,
                onEnqueued = onEnqueued,
                onFailed = onFailed
            )
        } catch (_: RuntimeException) {
            // Download initiation must never crash GeckoView/browser UI.
            onFailed?.invoke()
        }
    }

    private fun extensionFor(item: MediaInterceptor.DetectedMedia): String? = when (item.type) {
        MediaInterceptor.MediaType.MP4 -> ".mp4"
        MediaInterceptor.MediaType.WEBM -> ".webm"
        MediaInterceptor.MediaType.AUDIO -> {
            val mime = findHeader(item.headers, "Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.ROOT)

            when (mime) {
                "audio/mp4", "audio/x-m4a" -> ".m4a"
                "audio/ogg" -> ".ogg"
                "audio/webm" -> ".webm"
                "audio/wav", "audio/x-wav" -> ".wav"
                "audio/aac" -> ".aac"
                "audio/mpeg", "audio/mp3" -> ".mp3"
                else -> MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mime)
                    ?.let { ".${it}" }
                    ?: ".mp3"
            }
        }
        MediaInterceptor.MediaType.HLS,
        MediaInterceptor.MediaType.DASH -> null
    }

    private fun resolveMimeType(
        item: MediaInterceptor.DetectedMedia,
        extension: String
    ): String {
        val capturedMime = findHeader(item.headers, "Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)

        if (!capturedMime.isNullOrBlank() && capturedMime.contains('/')) {
            return capturedMime
        }

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            extension.removePrefix(".")
        ) ?: when (item.type) {
            MediaInterceptor.MediaType.AUDIO -> "audio/mpeg"
            MediaInterceptor.MediaType.WEBM -> "video/webm"
            else -> "video/mp4"
        }
    }

    private fun ensureExtension(fileName: String, extension: String): String {
        if (fileName.endsWith(extension, ignoreCase = true)) return fileName
        val base = fileName.substringBeforeLast('.', fileName)
        return base + extension
    }

    private fun findHeader(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }

    private fun isSafeDownloadHeader(name: String, value: String): Boolean {
        if (name.isBlank() || value.isBlank()) return false
        if (name.lowercase(Locale.ROOT) in UNSAFE_DOWNLOAD_HEADERS) return false
        if ('\r' in name || '\n' in name || '\r' in value || '\n' in value) return false
        return true
    }
}
