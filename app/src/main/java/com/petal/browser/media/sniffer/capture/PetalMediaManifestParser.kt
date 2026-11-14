package com.petal.browser.media.sniffer.capture

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.util.UUID

object PetalMediaManifestParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9\-]+)=("([^"]*)"|[^,]*)""")
    private val VIDEO_CODEC_PREFIXES = listOf("avc1", "avc3", "hev1", "hvc1", "vp8", "vp9", "vp09", "av01", "mp4v", "theora")
    private val AUDIO_CODEC_PREFIXES = listOf("mp4a", "ac-3", "ec-3", "opus", "vorbis", "flac", "alac", "mp3")

    private fun kindFromCodecs(codecs: String?): MediaKind? {
        if (codecs.isNullOrBlank()) return null
        val parts = codecs.split(",").map { it.trim().lowercase() }
        val hasVideo = parts.any { part -> VIDEO_CODEC_PREFIXES.any { part.startsWith(it) } }
        val hasAudio = parts.any { part -> AUDIO_CODEC_PREFIXES.any { part.startsWith(it) } }
        return when {
            hasVideo -> MediaKind.VIDEO
            hasAudio -> MediaKind.AUDIO
            else -> null
        }
    }

    private fun codecsHaveAudio(codecs: String?): Boolean {
        if (codecs.isNullOrBlank()) return false
        val parts = codecs.split(",").map { it.trim().lowercase() }
        return parts.any { part -> AUDIO_CODEC_PREFIXES.any { part.startsWith(it) } }
    }

    private fun parseAttributeList(s: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (m in ATTR_REGEX.findAll(s)) {
            val key = m.groupValues[1].uppercase()
            val quoted = m.groupValues[3]
            val value = if (quoted.isNotEmpty()) quoted else m.groupValues[2].trim().trim('"')
            result[key] = value
        }
        return result
    }

    private fun resolveUrl(base: String, relative: String): String {
        val trimmed = relative.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return try {
            URI(base).resolve(trimmed).toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    fun parseHls(text: String, manifestUrl: String): List<CapturedMediaItem> {
        val lines = text.lines()
        val isMaster = lines.any { it.trim().startsWith("#EXT-X-STREAM-INF") }

        if (!isMaster) {
            var durationTotal = 0.0
            var isLive = true
            for (raw in lines) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF:")) {
                    val d = line.removePrefix("#EXTINF:").substringBefore(',').toDoubleOrNull() ?: 0.0
                    durationTotal += d
                } else if (line.startsWith("#EXT-X-ENDLIST")) {
                    isLive = false
                }
            }
            return listOf(
                CapturedMediaItem(
                    id = UUID.randomUUID().toString(),
                    url = manifestUrl,
                    kind = MediaKind.VIDEO,
                    format = "HLS",
                    mimeType = "application/vnd.apple.mpegurl",
                    durationSeconds = durationTotal.takeIf { it > 0.0 },
                    isLive = isLive,
                    groupUrl = manifestUrl,
                    isHlsPlaylist = true
                )
            )
        }

        val results = mutableListOf<CapturedMediaItem>()
        var pendingVariantAttrs: Map<String, String>? = null

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    pendingVariantAttrs = parseAttributeList(line.substringAfter(":"))
                }
                line.startsWith("#EXT-X-MEDIA:") -> {
                    val attrs = parseAttributeList(line.substringAfter(":"))
                    val uri = attrs["URI"] ?: continue
                    val kind = when (attrs["TYPE"]) {
                        "AUDIO" -> MediaKind.AUDIO
                        "SUBTITLES" -> MediaKind.SUBTITLE
                        else -> null
                    } ?: continue
                    val resolvedUri = resolveUrl(manifestUrl, uri)
                    val uriExt = resolvedUri.substringAfterLast('/').substringAfterLast('.', "").lowercase()
                    val mime = when {
                        kind == MediaKind.SUBTITLE && uriExt == "srt" -> "application/x-subrip"
                        kind == MediaKind.SUBTITLE -> "text/vtt"
                        uriExt == "mp3" -> "audio/mpeg"
                        uriExt == "aac" -> "audio/aac"
                        uriExt == "ac3" -> "audio/ac3"
                        uriExt == "eac3" -> "audio/eac3"
                        else -> "audio/mp4"
                    }
                    results += CapturedMediaItem(
                        id = UUID.randomUUID().toString(),
                        url = resolvedUri,
                        kind = kind,
                        format = "HLS",
                        mimeType = mime,
                        label = attrs["NAME"],
                        groupUrl = manifestUrl,
                        mediaGroupId = attrs["GROUP-ID"],
                        language = attrs["LANGUAGE"],
                        isDefaultTrack = attrs["DEFAULT"]?.equals("YES", ignoreCase = true) == true,
                        codecs = attrs["CODECS"],
                        channels = attrs["CHANNELS"]?.substringBefore('/')?.trim()?.toIntOrNull()
                    )
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val attrs = pendingVariantAttrs
                    pendingVariantAttrs = null
                    val resolution = attrs?.get("RESOLUTION")
                    val parts = resolution?.split("x")
                    val width = parts?.getOrNull(0)?.toIntOrNull()
                    val height = parts?.getOrNull(1)?.toIntOrNull()
                    val bandwidth = (attrs?.get("AVERAGE-BANDWIDTH") ?: attrs?.get("BANDWIDTH"))?.toLongOrNull()
                    val codecs = attrs?.get("CODECS")
                    val kind = when {
                        width != null || height != null -> MediaKind.VIDEO
                        else -> kindFromCodecs(codecs) ?: MediaKind.VIDEO
                    }
                    val hasAudio = when {
                        kind != MediaKind.VIDEO -> null
                        attrs?.get("AUDIO") != null -> false
                        codecs != null -> codecsHaveAudio(codecs)
                        else -> null
                    }
                    val label = if (height != null) "${height}p" else attrs?.get("NAME")
                    results += CapturedMediaItem(
                        id = UUID.randomUUID().toString(),
                        url = resolveUrl(manifestUrl, line),
                        kind = kind,
                        format = "HLS",
                        mimeType = "application/vnd.apple.mpegurl",
                        label = label,
                        width = width,
                        height = height,
                        bandwidthBitsPerSec = bandwidth,
                        hasAudio = hasAudio,
                        groupUrl = manifestUrl,
                        audioGroupRef = attrs?.get("AUDIO"),
                        codecs = codecs,
                        frameRate = attrs?.get("FRAME-RATE")?.toDoubleOrNull(),
                        isHlsPlaylist = true
                    )
                }
            }
        }
        return results
    }

    fun parseDash(xmlText: String, manifestUrl: String): List<CapturedMediaItem> {
        val results = mutableListOf<CapturedMediaItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlText))

        var eventType = parser.eventType
        var currentAdaptationSetMime: String? = null
        var currentContentType: String? = null
        var currentLang: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "AdaptationSet" -> {
                            currentAdaptationSetMime = parser.getAttributeValue(null, "mimeType")
                            currentContentType = parser.getAttributeValue(null, "contentType")
                            currentLang = parser.getAttributeValue(null, "lang")
                        }
                        "Representation" -> {
                            val id = parser.getAttributeValue(null, "id") ?: UUID.randomUUID().toString()
                            val bandwidth = parser.getAttributeValue(null, "bandwidth")?.toLongOrNull()
                            val width = parser.getAttributeValue(null, "width")?.toIntOrNull()
                            val height = parser.getAttributeValue(null, "height")?.toIntOrNull()
                            val mime = parser.getAttributeValue(null, "mimeType") ?: currentAdaptationSetMime
                            val codecs = parser.getAttributeValue(null, "codecs")

                            val kind = when {
                                mime?.startsWith("video/") == true || currentContentType == "video" || width != null || height != null -> MediaKind.VIDEO
                                mime?.startsWith("audio/") == true || currentContentType == "audio" -> MediaKind.AUDIO
                                mime?.startsWith("text/") == true || currentContentType == "text" -> MediaKind.SUBTITLE
                                else -> kindFromCodecs(codecs) ?: MediaKind.VIDEO
                            }
                            val label = if (height != null) "${height}p" else id

                            results += CapturedMediaItem(
                                id = UUID.randomUUID().toString(),
                                url = manifestUrl,
                                kind = kind,
                                format = "DASH",
                                mimeType = mime ?: "application/dash+xml",
                                label = label,
                                width = width,
                                height = height,
                                bandwidthBitsPerSec = bandwidth,
                                groupUrl = manifestUrl,
                                representationId = id,
                                codecs = codecs,
                                language = currentLang
                            )
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "AdaptationSet") {
                        currentAdaptationSetMime = null
                        currentContentType = null
                        currentLang = null
                    }
                }
            }
            eventType = parser.next()
        }
        return results
    }
}
