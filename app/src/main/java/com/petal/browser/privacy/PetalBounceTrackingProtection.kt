package com.petal.browser.privacy

import android.net.Uri

/**
 * PetalBounceTrackingProtection
 * ─────────────────────────────────────────────────────────────────────────
 * Brave "Bounce Tracking Defense" for Petal Browser.
 * Detects redirect intermediary URLs that pass target destination URLs via query parameters,
 * and skips directly to the clean target URL before intermediary trackers can drop cookies.
 */
object PetalBounceTrackingProtection {

    private val BOUNCE_KEYS = listOf(
        "target", "url", "u", "dest", "destination", "redirect", "redirect_url", "redirect_to", "next", "link", "r"
    )

    @JvmStatic
    fun detectAndUnwrapBounceUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return null
        }

        return try {
            val uri = Uri.parse(trimmed)
            for (key in BOUNCE_KEYS) {
                val candidate = uri.getQueryParameter(key)
                if (!candidate.isNullOrBlank()) {
                    val decoded = candidate.trim()
                    if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                        // Found bounce redirect destination
                        return decoded
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}
