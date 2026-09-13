package com.petal.browser.media.sniffer

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.StateFlow
import java.net.URLDecoder

/** App-level facade for the media sniffer. Keeps detection independent from UI. */
object PetalMediaSniffer {
    val interceptor = MediaInterceptor()

    fun setActivePage(pageId: String) = interceptor.setActivePage(pageId)
    fun clear() = interceptor.clear()

    fun onNetworkMedia(url: String, headers: Map<String, String> = emptyMap()) =
        interceptor.onMediaRequestDetected(url, headers)

    fun onAggressiveMedia(url: String, mimeType: String, cookies: String? = null, sizeBytes: Long? = null) =
        interceptor.onAggressiveMediaGrabbed(url, mimeType, cookies, sizeBytes)

    fun download(context: Context, item: MediaInterceptor.DetectedMedia): Long? {
        val url = item.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val extension = when (item.type) {
            MediaInterceptor.MediaType.MP4 -> ".mp4"
            MediaInterceptor.MediaType.WEBM -> ".webm"
            MediaInterceptor.MediaType.AUDIO -> ".mp3"
            MediaInterceptor.MediaType.HLS, MediaInterceptor.MediaType.DASH -> return null
        }
        val decoded = try { URLDecoder.decode(Uri.parse(url).lastPathSegment ?: "", "UTF-8") } catch (_: Exception) { "" }
        val base = decoded.substringBeforeLast('.', decoded).takeIf { it.isNotBlank() } ?: "petal_media"
        val fileName = base.take(80) + extension
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.removePrefix("."))
            ?: if (item.type == MediaInterceptor.MediaType.AUDIO) "audio/mpeg" else "video/mp4"
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(item.title ?: fileName)
            setDescription("Downloaded by Petal Media Sniffer")
            setMimeType(mime)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            item.cookies?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
            item.referrer?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Referer", it) }
            item.headers.forEach { (k, v) ->
                if (k.equals("Cookie", true) || k.equals("Referer", true) || k.equals("User-Agent", true)) {
                    addRequestHeader(k, v)
                }
            }
        }
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
