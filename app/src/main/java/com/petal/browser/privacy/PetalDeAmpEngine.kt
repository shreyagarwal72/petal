package com.petal.browser.privacy

import android.net.Uri

/**
 * PetalDeAmpEngine
 * ─────────────────────────────────────────────────────────────────────────
 * Automatically unwraps Google Accelerated Mobile Pages (AMP) to genuine publisher URLs.
 * Equivalent to Brave's "De-AMP" feature.
 */
object PetalDeAmpEngine {

    /**
     * Inspects a URL and extracts the canonical original URL if it is an AMP proxy link.
     * Returns null if the URL is not an AMP link.
     */
    @JvmStatic
    fun unwrapAmpUrl(urlString: String?): String? {
        if (urlString.isNullOrBlank()) return null
        val trimmed = urlString.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return null
        }

        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase() ?: return null

            // 1. Google AMP Viewer: google.com/amp/s/example.com/page or google.com/amp/example.com
            if (host.contains("google.") && uri.pathSegments.contains("amp")) {
                val segments = uri.pathSegments
                val ampIndex = segments.indexOf("amp")
                if (ampIndex >= 0 && ampIndex < segments.size - 1) {
                    val remaining = segments.subList(ampIndex + 1, segments.size)
                    val isHttps = remaining.firstOrNull()?.equals("s", ignoreCase = true) == true
                    val targetSegments = if (isHttps && remaining.size > 1) remaining.subList(1, remaining.size) else remaining
                    if (targetSegments.isNotEmpty()) {
                        val scheme = if (isHttps) "https://" else "http://"
                        val reconstructed = scheme + targetSegments.joinToString("/")
                        val query = uri.query
                        return if (!query.isNullOrEmpty()) "$reconstructed?$query" else reconstructed
                    }
                }
            }

            // 2. Google AMP Cache: *.cdn.ampproject.org/c/s/example.com/page
            if (host.endsWith(".ampproject.org") || host.endsWith(".amp.cloudflare.com")) {
                val segments = uri.pathSegments
                // Pattern /c/s/domain/path or /c/domain/path
                val cIndex = segments.indexOf("c")
                if (cIndex >= 0 && cIndex < segments.size - 1) {
                    val remaining = segments.subList(cIndex + 1, segments.size)
                    val isHttps = remaining.firstOrNull()?.equals("s", ignoreCase = true) == true
                    val targetSegments = if (isHttps && remaining.size > 1) remaining.subList(1, remaining.size) else remaining
                    if (targetSegments.isNotEmpty()) {
                        val scheme = if (isHttps) "https://" else "http://"
                        val reconstructed = scheme + targetSegments.joinToString("/")
                        val query = uri.query
                        return if (!query.isNullOrEmpty()) "$reconstructed?$query" else reconstructed
                    }
                }
            }

            null
        } catch (_: Throwable) {
            null
        }
    }
}
