package com.petal.browser.media.sniffer

import android.util.Log
import com.petal.browser.media.sniffer.capture.CapturedMediaItem
import com.petal.browser.media.sniffer.capture.MediaKind
import com.petal.browser.media.sniffer.capture.PetalFileMetadataProbe
import com.petal.browser.media.sniffer.capture.PetalMediaManifestParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object PetalMediaCaptureStore {
    private const val TAG = "PetalMediaCaptureStore"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Active tab and page URL
    val activeTabId = MutableStateFlow("")
    val activePageUrl = MutableStateFlow("")

    // Tab-scoped media store: tabId -> List<CapturedMediaItem>
    private val tabMediaMap = ConcurrentHashMap<String, MutableList<CapturedMediaItem>>()
    private val seenUrls = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val _currentTabMedia = MutableStateFlow<List<CapturedMediaItem>>(emptyList())
    val currentTabMedia: StateFlow<List<CapturedMediaItem>> = _currentTabMedia.asStateFlow()

    val videoItems: StateFlow<List<CapturedMediaItem>> = _currentTabMedia.map { list ->
        list.filter { it.kind == MediaKind.VIDEO }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val audioItems: StateFlow<List<CapturedMediaItem>> = _currentTabMedia.map { list ->
        list.filter { it.kind == MediaKind.AUDIO }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val subtitleItems: StateFlow<List<CapturedMediaItem>> = _currentTabMedia.map { list ->
        list.filter { it.kind == MediaKind.SUBTITLE }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val totalMediaCount: StateFlow<Int> = _currentTabMedia.map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    fun setActiveTab(tabId: String, pageUrl: String) {
        activeTabId.value = tabId
        activePageUrl.value = pageUrl
        val list = tabMediaMap.getOrPut(tabId) { mutableListOf() }
        synchronized(list) {
            _currentTabMedia.value = ArrayList(list)
        }
    }

    fun clearTab(tabId: String) {
        tabMediaMap.remove(tabId)
        if (activeTabId.value == tabId) {
            _currentTabMedia.value = emptyList()
        }
    }

    fun clearAll() {
        tabMediaMap.clear()
        seenUrls.clear()
        _currentTabMedia.value = emptyList()
    }

    fun registerMediaCandidate(
        url: String,
        tabId: String = activeTabId.value,
        pageUrl: String = activePageUrl.value,
        typeHint: String? = null,
        label: String? = null,
        language: String? = null,
        headers: Map<String, String> = emptyMap()
    ) {
        if (url.isBlank() || url.startsWith("blob:") || url.startsWith("data:")) return
        val dedupKey = "$tabId|$url"
        if (!seenUrls.add(dedupKey)) return

        scope.launch {
            val cleanUrl = url.trim()
            val lowerUrl = cleanUrl.lowercase()

            when {
                lowerUrl.contains(".m3u8") -> {
                    // Fetch and parse HLS playlist
                    fetchAndParseHls(cleanUrl, tabId, pageUrl, headers)
                }
                lowerUrl.contains(".mpd") -> {
                    // Fetch and parse DASH manifest
                    fetchAndParseDash(cleanUrl, tabId, pageUrl, headers)
                }
                lowerUrl.endsWith(".vtt") || lowerUrl.endsWith(".srt") || typeHint == "s" -> {
                    val item = CapturedMediaItem(
                        id = UUID.randomUUID().toString(),
                        url = cleanUrl,
                        kind = MediaKind.SUBTITLE,
                        format = if (lowerUrl.endsWith(".srt")) "SRT" else "VTT",
                        mimeType = if (lowerUrl.endsWith(".srt")) "application/x-subrip" else "text/vtt",
                        label = label ?: (if (lowerUrl.endsWith(".srt")) "SubRip Subtitle" else "WebVTT Subtitle"),
                        language = language,
                        pageUrl = pageUrl,
                        requestHeaders = headers
                    )
                    addItemToTab(tabId, item)
                }
                typeHint == "a" || lowerUrl.matches(Regex(".*\\.(mp3|m4a|aac|ogg|oga|opus|flac|wav|weba)(\\?.*)?$")) -> {
                    val item = CapturedMediaItem(
                        id = UUID.randomUUID().toString(),
                        url = cleanUrl,
                        kind = MediaKind.AUDIO,
                        format = cleanUrl.substringAfterLast('.').substringBefore('?').uppercase(),
                        mimeType = "audio/*",
                        label = label ?: "Audio Track",
                        language = language,
                        pageUrl = pageUrl,
                        requestHeaders = headers
                    )
                    addItemToTab(tabId, item)
                    probeAsync(item, pageUrl, tabId)
                }
                else -> {
                    // Standard direct video file
                    val ext = cleanUrl.substringAfterLast('.').substringBefore('?').uppercase().takeIf { it.length in 3..4 } ?: "MP4"
                    val item = CapturedMediaItem(
                        id = UUID.randomUUID().toString(),
                        url = cleanUrl,
                        kind = MediaKind.VIDEO,
                        format = ext,
                        mimeType = "video/mp4",
                        label = label ?: ext,
                        pageUrl = pageUrl,
                        requestHeaders = headers
                    )
                    addItemToTab(tabId, item)
                    probeAsync(item, pageUrl, tabId)
                }
            }
        }
    }

    fun parseDomScanResult(tabId: String, pageUrl: String, itemsJson: String) {
        scope.launch {
            try {
                val array = JSONArray(itemsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val url = obj.optString("url")
                    val type = obj.optString("type")
                    val label = obj.optString("label").takeIf { it.isNotBlank() }
                    val lang = obj.optString("language").takeIf { it.isNotBlank() }
                    registerMediaCandidate(
                        url = url,
                        tabId = tabId,
                        pageUrl = pageUrl,
                        typeHint = type,
                        label = label,
                        language = lang
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse DOM scan items: ${e.message}")
            }
        }
    }

    private fun addItemToTab(tabId: String, item: CapturedMediaItem) {
        val list = tabMediaMap.getOrPut(tabId) { mutableListOf() }
        synchronized(list) {
            // Avoid duplicates by url
            if (list.none { it.url == item.url }) {
                list.add(item)
                if (activeTabId.value == tabId) {
                    _currentTabMedia.value = ArrayList(list)
                }
            }
        }
        // Also register with PetalNativeSnifferEngine so both the bottom sheet and address pill reflect it
        PetalNativeSnifferEngine.registerResource(
            tabId = tabId,
            url = item.url,
            mimeType = item.mimeType,
            title = item.label,
            sizeBytes = item.sizeBytes,
            pageUrl = item.pageUrl
        )
    }

    private fun updateItemInTab(tabId: String, updated: CapturedMediaItem) {
        val list = tabMediaMap[tabId] ?: return
        synchronized(list) {
            val index = list.indexOfFirst { it.id == updated.id }
            if (index != -1) {
                list[index] = updated
                if (activeTabId.value == tabId) {
                    _currentTabMedia.value = ArrayList(list)
                }
            }
        }
    }

    private suspend fun fetchAndParseHls(
        manifestUrl: String,
        tabId: String,
        pageUrl: String,
        headers: Map<String, String>
    ) {
        try {
            val req = Request.Builder().url(manifestUrl)
            if (pageUrl.isNotBlank()) req.header("Referer", pageUrl)
            headers.forEach { (k, v) -> req.header(k, v) }

            val response = okHttpClient.newCall(req.build()).execute()
            if (!response.isSuccessful) return
            val body = response.body?.string() ?: return
            val parsed = PetalMediaManifestParser.parseHls(body, manifestUrl)
            for (item in parsed) {
                addItemToTab(tabId, item.copy(pageUrl = pageUrl, requestHeaders = headers))
            }
        } catch (e: Exception) {
            Log.e(TAG, "HLS parse error for $manifestUrl: ${e.message}")
        }
    }

    private suspend fun fetchAndParseDash(
        manifestUrl: String,
        tabId: String,
        pageUrl: String,
        headers: Map<String, String>
    ) {
        try {
            val req = Request.Builder().url(manifestUrl)
            if (pageUrl.isNotBlank()) req.header("Referer", pageUrl)
            headers.forEach { (k, v) -> req.header(k, v) }

            val response = okHttpClient.newCall(req.build()).execute()
            if (!response.isSuccessful) return
            val body = response.body?.string() ?: return
            val parsed = PetalMediaManifestParser.parseDash(body, manifestUrl)
            for (item in parsed) {
                addItemToTab(tabId, item.copy(pageUrl = pageUrl, requestHeaders = headers))
            }
        } catch (e: Exception) {
            Log.e(TAG, "DASH parse error for $manifestUrl: ${e.message}")
        }
    }

    private fun probeAsync(item: CapturedMediaItem, pageUrl: String, tabId: String) {
        scope.launch {
            val info = PetalFileMetadataProbe.probe(item, pageUrl) ?: return@launch
            val updated = item.copy(
                width = info.width ?: item.width,
                height = info.height ?: item.height,
                durationSeconds = info.durationSeconds ?: item.durationSeconds,
                bandwidthBitsPerSec = info.bitrateBitsPerSec ?: item.bandwidthBitsPerSec,
                label = if (info.height != null) "${info.height}p" else item.label
            )
            updateItemInTab(tabId, updated)
        }
    }
}
