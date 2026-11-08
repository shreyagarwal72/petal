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

    // Auth and SSO domains that must NEVER be unwrapped to avoid breaking logins
    private val AUTH_DOMAIN_KEYWORDS = listOf(
        "login", "signin", "auth", "oauth", "sso", "saml", "accounts.google", "appleid.apple",
        "github.com", "gitlab.com", "microsoftonline", "live.com", "okta.com", "auth0.com",
        "paypal.com", "stripe.com", "discord.com", "facebook.com", "twitter.com", "x.com"
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
            val host = uri.host?.lowercase() ?: return null
            val path = uri.path?.lowercase() ?: ""

            // Never unwrap authentication, login, or SSO endpoints
            if (AUTH_DOMAIN_KEYWORDS.any { host.contains(it) } ||
                path.contains("login") || path.contains("signin") || path.contains("oauth") || path.contains("auth")) {
                return null
            }

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
