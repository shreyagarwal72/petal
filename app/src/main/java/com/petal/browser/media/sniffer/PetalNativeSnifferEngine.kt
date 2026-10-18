package com.petal.browser.media.sniffer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.download.SafeDownloadValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalNativeSnifferEngine
 *
 * Full native replacement for the WebExtension-based media grabber.
 * Intercepts, probes, and classifies network and DOM resources across 4 Soul-style
 * categories: Video, Audio, Images, and Documents.
 */
object PetalNativeSnifferEngine {
    private const val TAG = "PetalNativeSniffer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tab-scoped live sniffer store: tabId -> List<SniffedResource>
    private val tabResourcesMap = ConcurrentHashMap<String, MutableList<SniffedResource>>()
    private val activeTabId = MutableStateFlow("")
    private val activePageUrl = MutableStateFlow("")

    private val _currentTabResources = MutableStateFlow<List<SniffedResource>>(emptyList())
    val currentTabResources: StateFlow<List<SniffedResource>> = _currentTabResources.asStateFlow()

    // Deduplication set: tabId + url
    private val sniffedUrlKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Cookies map
    private val hostCookiesMap = ConcurrentHashMap<String, String>()

    fun setActiveTab(tabId: String, pageUrl: String) {
        activeTabId.value = tabId
        activePageUrl.value = pageUrl
        val list = tabResourcesMap.getOrPut(tabId) { mutableListOf() }
        synchronized(list) {
            _currentTabResources.value = ArrayList(list)
        }
    }

    fun clearTab(tabId: String) {
        tabResourcesMap.remove(tabId)
        val prefix = "$tabId|"
        sniffedUrlKeys.removeIf { it.startsWith(prefix) }
        if (activeTabId.value == tabId) {
            _currentTabResources.value = emptyList()
        }
    }

    fun recordCookies(url: String, cookies: String) {
        if (cookies.isBlank()) return
        try {
            val host = Uri.parse(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return
            hostCookiesMap[host] = cookies
        } catch (_: Exception) {}
    }

    fun getCookies(url: String): String? {
        return try {
            val host = Uri.parse(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
            hostCookiesMap[host] ?: hostCookiesMap.entries.firstOrNull { (k, _) -> host.endsWith(k) || k.endsWith(host) }?.value
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Native registration of an intercepted URL from network or DOM inspection.
     */
    fun registerResource(
        tabId: String,
        url: String,
        mimeType: String? = null,
        title: String? = null,
        sizeBytes: Long? = null,
        pageUrl: String = activePageUrl.value
    ) {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("about:") || url.startsWith("blob:http://localhost")) return
        if (isSearchOrInternalUrl(url) || isSearchOrInternalUrl(pageUrl)) return

        val key = "$tabId|$url"
        if (!sniffedUrlKeys.add(key)) {
            // Already captured
            return
        }

        val category = PetalResourceCategory.fromUrlAndMime(url, mimeType) ?: return
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: deriveFilename(url, category)

        val resource = SniffedResource(
            url = url,
            category = category,
            title = resolvedTitle,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            pageUrl = pageUrl,
            cookies = getCookies(url) ?: getCookies(pageUrl),
            referrer = pageUrl.takeIf { it.isNotBlank() }
        )

        val list = tabResourcesMap.getOrPut(tabId) { mutableListOf() }
        synchronized(list) {
            list.add(0, resource)
            if (activeTabId.value == tabId) {
                _currentTabResources.value = ArrayList(list)
            }
        }

        // Bridge to existing PetalMediaSniffer interceptor for video/audio so native player picks it up
        if (category == PetalResourceCategory.VIDEO || category == PetalResourceCategory.AUDIO) {
            PetalMediaSniffer.onAggressiveMedia(url, mimeType ?: "video/mp4", resource.cookies, sizeBytes)
        }

        // Asynchronously probe size if missing
        if (sizeBytes == null || sizeBytes <= 0L) {
            probeResourceSize(tabId, resource)
        }
    }

    private fun probeResourceSize(tabId: String, resource: SniffedResource) {
        scope.launch {
            try {
                val conn = URL(resource.url).openConnection() as? HttpURLConnection ?: return@launch
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.instanceFollowRedirects = true
                resource.cookies?.let { conn.setRequestProperty("Cookie", it) }
                resource.referrer?.let { conn.setRequestProperty("Referer", it) }
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")

                val length = conn.contentLengthLong
                val contentType = conn.contentType
                conn.disconnect()

                if (length > 0L) {
                    val list = tabResourcesMap[tabId] ?: return@launch
                    synchronized(list) {
                        val index = list.indexOfFirst { it.id == resource.id }
                        if (index != -1) {
                            val updated = list[index].copy(
                                sizeBytes = length,
                                mimeType = list[index].mimeType ?: contentType
                            )
                            list[index] = updated
                            if (activeTabId.value == tabId) {
                                _currentTabResources.value = ArrayList(list)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun deriveFilename(url: String, category: PetalResourceCategory): String {
        return try {
            val uri = Uri.parse(url)
            val lastSegment = uri.lastPathSegment ?: ""
            if (lastSegment.isNotBlank() && lastSegment.contains(".")) {
                lastSegment.substringBefore("?").take(48)
            } else {
                "${category.name.lowercase(Locale.ROOT)}_${System.currentTimeMillis() % 10000}"
            }
        } catch (_: Exception) {
            "${category.name.lowercase(Locale.ROOT)}_file"
        }
    }

    fun isSearchOrInternalUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase(Locale.ROOT)
        return lower.startsWith("about:") ||
               lower.startsWith("chrome:") ||
               lower.startsWith("resource:") ||
               lower.contains("google.com/search") ||
               lower.contains("bing.com/search") ||
               lower.contains("duckduckgo.com/?q=")
    }

    /**
     * JavaScript snippet executed directly on page load/stop to extract all DOM media elements,
     * links, and images without any WebExtension overhead.
     */
    const val DOM_SCAN_JS = """
        (function() {
            try {
                const results = [];
                // 1. Videos & Audio
                document.querySelectorAll('video, audio, source').forEach(el => {
                    const src = el.src || el.currentSrc || el.getAttribute('data-src');
                    if (src && src.startsWith('http')) {
                        results.push({ url: src, type: el.tagName.toLowerCase() === 'audio' ? 'audio' : 'video' });
                    }
                });
                // 2. High-res images
                document.querySelectorAll('img').forEach(img => {
                    const src = img.currentSrc || img.src || img.getAttribute('data-src');
                    if (src && src.startsWith('http') && (img.naturalWidth > 120 || img.width > 120)) {
                        results.push({ url: src, type: 'image' });
                    }
                });
                // 3. Document links
                document.querySelectorAll('a[href]').forEach(a => {
                    const href = a.href;
                    if (href && /\.(pdf|zip|rar|7z|apk|tar|gz|epub)(\?|$)/i.test(href)) {
                        results.push({ url: href, type: 'doc', title: a.innerText || a.title || '' });
                    }
                });
                return JSON.stringify(results);
            } catch(e) {
                return '[]';
            }
        })();
    """

    fun parseDomScanResult(tabId: String, pageUrl: String, jsonString: String?) {
        if (jsonString.isNullOrBlank()) return
        scope.launch {
            try {
                val cleanJson = if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                    org.json.JSONTokener(jsonString).nextValue().toString()
                } else {
                    jsonString
                }
                val arr = JSONArray(cleanJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optString("url")
                    val type = obj.optString("type")
                    val title = obj.optString("title").takeIf { it.isNotBlank() }
                    val mime = when (type) {
                        "video" -> "video/mp4"
                        "audio" -> "audio/mpeg"
                        "image" -> "image/jpeg"
                        "doc" -> "application/octet-stream"
                        else -> null
                    }
                    registerResource(tabId, url, mime, title, null, pageUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse DOM sniffer scan results", e)
            }
        }
    }

    /**
     * Download a sniffed resource via Petal's native download manager.
     */
    fun downloadResource(context: Context, resource: SniffedResource) {
        val fileName = resource.title.ifBlank {
            resource.url.substringAfterLast('/').substringBefore('?').ifBlank { "download_${System.currentTimeMillis()}" }
        }
        PetalFetchDownloadBridge.enqueueGeckoDownload(
            context = context,
            url = resource.url,
            fileName = fileName,
            mimeType = resource.mimeType
        )
    }

    /**
     * Batch download a list of sniffed resources.
     */
    fun downloadBatch(context: Context, resources: List<SniffedResource>) {
        resources.forEach { downloadResource(context, it) }
    }
}
