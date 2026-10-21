package com.petal.browser.privacy

import android.net.Uri

/**
 * PetalUrlCleaner
 * ─────────────────────────────────────────────────────────────────────────
 * Strips tracking parameters, click identifiers, and telemetry tokens from URLs
 * before copying, sharing, or navigating. Equivalent to Brave's "Copy Clean Link".
 */
object PetalUrlCleaner {

    private val TRACKING_PARAMS = setOf(
        // Google / Analytics
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_reader",
        "gclid", "gbraid", "wbraid", "dclid", "_ga", "_gl",
        // Meta / Facebook / Instagram
        "fbclid", "igshid", "fb_action_ids", "fb_action_types", "fb_source", "fb_ref",
        // Microsoft / Bing
        "msclkid",
        // Twitter / X
        "twclid",
        // TikTok
        "tt_medium", "tt_content",
        // Hubspot / Mailchimp
        "_hsenc", "_hsmi", "mc_cid", "mc_eid",
        // Yandex
        "yclid", "_openstat",
        // Misc affiliate & ad tracking
        "zanpid", "sc_customer", "sc_channel", "si"
    )

    @JvmStatic
    fun cleanUrl(urlString: String?): String {
        if (urlString.isNullOrBlank()) return ""
        val trimmed = urlString.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }

        return try {
            val uri = Uri.parse(trimmed)
            val queryNames = uri.queryParameterNames
            if (queryNames.isEmpty()) return trimmed

            val hasTrackingParam = queryNames.any { it.lowercase() in TRACKING_PARAMS || it.startsWith("utm_", ignoreCase = true) }
            if (!hasTrackingParam) return trimmed

            val builder = uri.buildUpon().clearQuery()
            for (name in queryNames) {
                if (name.lowercase() !in TRACKING_PARAMS && !name.startsWith("utm_", ignoreCase = true)) {
                    val values = uri.getQueryParameters(name)
                    for (v in values) {
                        builder.appendQueryParameter(name, v)
                    }
                }
            }
            builder.build().toString()
        } catch (_: Throwable) {
            trimmed
        }
    }
}
