package com.petal.browser.media.sniffer.download

import okhttp3.Request

class StreamCancelledException : Exception()

enum class TrackKind { VIDEO, AUDIO }

enum class StreamContainerFormat { HLS, DASH }

const val STREAM_FORMAT_HLS_SUBTITLE = "HLS_SUBTITLE"

data class HlsKeyInfo(
    val keyUri: String,
    val ivHex: String? = null,
    val method: String = "AES-128"
)

data class SegmentSpec(
    val url: String,
    val byteRangeOffset: Long? = null,
    val byteRangeLength: Long? = null,
    val sequenceNumber: Long = 0L,
    val key: HlsKeyInfo? = null
)

data class ResolvedTrack(
    val kind: TrackKind,
    val initSegment: SegmentSpec? = null,
    val segments: List<SegmentSpec> = emptyList(),
    val durationSeconds: Double? = null,
    val containerHintExtension: String = "ts",
    val isLive: Boolean = false,
    val targetDurationSeconds: Double? = null
)

data class StreamDownloadRequest(
    val format: StreamContainerFormat,
    val videoUrl: String?,
    val audioUrl: String? = null,
    val subtitleUrl: String? = null,
    val pageUrl: String = "",
    val referer: String = "",
    val cookies: String = "",
    val userAgent: String = "",
    val filename: String,
    val headers: Map<String, String> = emptyMap(),
    val estimatedTotalBytes: Long = 0L,
    val speedLimitBytesPerSec: Long = 0L,
    val concurrentSegments: Int = 3,
    val noAudio: Boolean = false,
    val isLive: Boolean = false,
    val convertTsToMp4: Boolean = true,
    val subtitleOnly: Boolean = false,
    val audioLanguage: String? = null,
    val audioLabel: String? = null,
    val id: String = java.util.UUID.randomUUID().toString()
)

object StreamBackoff {
    suspend fun wait(attempt: Int, isCancelled: () -> Boolean) {
        if (isCancelled()) return
        val base = (250L shl attempt.coerceIn(0, 5)).coerceAtMost(8000L)
        val jitter = (0..(base / 4).coerceAtLeast(1)).random()
        kotlinx.coroutines.delay(base + jitter)
    }
}

object StreamRequestHeaders {
    fun apply(
        builder: Request.Builder,
        url: String,
        referer: String,
        cookies: String,
        userAgent: String,
        extraHeaders: Map<String, String>
    ) {
        buildMap(url, referer, cookies, userAgent, extraHeaders).forEach { (key, value) ->
            runCatching { builder.header(key, value) }
        }
    }

    fun buildMap(
        url: String,
        referer: String,
        cookies: String,
        userAgent: String,
        extraHeaders: Map<String, String>
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        if (userAgent.isNotBlank()) result["User-Agent"] = userAgent
        if (referer.isNotBlank()) result["Referer"] = referer
        if (cookies.isNotBlank()) result["Cookie"] = cookies
        extraHeaders.forEach { (key, value) ->
            if (!result.containsKey(key)) result[key] = value
        }
        return result
    }
}
