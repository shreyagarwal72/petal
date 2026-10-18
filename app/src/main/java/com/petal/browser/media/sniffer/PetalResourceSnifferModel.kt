package com.petal.browser.media.sniffer

import android.net.Uri
import java.util.Locale

/**
 * Sniffed resource classification matching Soul Browser's 4 core buckets.
 */
enum class PetalResourceCategory(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    AUDIO("Audio"),
    IMAGE("Image"),
    DOCUMENT("Doc");

    companion object {
        fun fromUrlAndMime(url: String, mimeType: String?): PetalResourceCategory? {
            val lowerMime = mimeType?.lowercase(Locale.ROOT) ?: ""
            val lowerUrl = url.lowercase(Locale.ROOT)

            if (lowerMime.startsWith("video/") ||
                lowerUrl.contains(".m3u8") ||
                lowerUrl.contains(".mpd") ||
                lowerUrl.contains(".mp4") ||
                lowerUrl.contains(".webm") ||
                lowerUrl.contains(".mkv") ||
                lowerUrl.contains(".flv") ||
                lowerUrl.contains("/hls/") ||
                lowerUrl.contains("/dash/") ||
                lowerUrl.contains("videoplayback") ||
                lowerUrl.contains("googlevideo.com")
            ) {
                return VIDEO
            }

            if (lowerMime.startsWith("audio/") ||
                lowerUrl.contains(".mp3") ||
                lowerUrl.contains(".m4a") ||
                lowerUrl.contains(".aac") ||
                lowerUrl.contains(".flac") ||
                lowerUrl.contains(".ogg") ||
                lowerUrl.contains(".wav")
            ) {
                return AUDIO
            }

            if (lowerMime.startsWith("image/") ||
                lowerUrl.contains(".png") ||
                lowerUrl.contains(".jpg") ||
                lowerUrl.contains(".jpeg") ||
                lowerUrl.contains(".webp") ||
                lowerUrl.contains(".svg") ||
                lowerUrl.contains(".gif")
            ) {
                return IMAGE
            }

            if (lowerMime.contains("pdf") ||
                lowerMime.contains("zip") ||
                lowerMime.contains("rar") ||
                lowerMime.contains("octet-stream") ||
                lowerMime.contains("application/vnd.android.package-archive") ||
                lowerUrl.contains(".pdf") ||
                lowerUrl.contains(".zip") ||
                lowerUrl.contains(".rar") ||
                lowerUrl.contains(".7z") ||
                lowerUrl.contains(".apk") ||
                lowerUrl.contains(".tar") ||
                lowerUrl.contains(".gz") ||
                lowerUrl.contains(".epub")
            ) {
                return DOCUMENT
            }

            return null
        }
    }
}

/**
 * Represents a sniffed resource captured directly from the page or network.
 */
data class SniffedResource(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String,
    val category: PetalResourceCategory,
    val title: String,
    val mimeType: String?,
    val sizeBytes: Long? = null,
    val pageUrl: String = "",
    val detectedAt: Long = System.currentTimeMillis(),
    val cookies: String? = null,
    val referrer: String? = null
) {
    val displaySize: String
        get() {
            val size = sizeBytes ?: return ""
            if (size <= 0) return ""
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
                else -> "$size B"
            }
        }

    val fileExtension: String
        get() {
            return try {
                val path = Uri.parse(url).path ?: ""
                val dot = path.lastIndexOf('.')
                if (dot != -1 && dot < path.length - 1) {
                    path.substring(dot + 1).take(5).uppercase(Locale.ROOT)
                } else {
                    category.name
                }
            } catch (_: Exception) {
                category.name
            }
        }
}
