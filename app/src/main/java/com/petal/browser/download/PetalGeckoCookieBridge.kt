package com.petal.browser.download

import android.content.Context
import android.net.Uri
import com.petal.browser.media.sniffer.PetalMediaSniffer
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalGeckoCookieBridge
 * ─────────────────────────────────────────────────────────────────────────────
 * Authoritative cookie provider for Petal downloads, media extractors, yt-dlp,
 * and external managers.
 *
 * Problem Solved:
 * GeckoView maintains its own internal cookie jar and never writes to Chromium's
 * legacy [android.webkit.CookieManager]. Previously, download paths queried
 * webkit's CookieManager, returning empty or stale cookies which broke authenticated
 * downloads.
 *
 * This bridge captures and serves cookies from Gecko's active sessions and network
 * traffic, strictly separating standard from private browsing sessions.
 */
object PetalGeckoCookieBridge {

    private val standardCookieCache = ConcurrentHashMap<String, String>()
    private val privateCookieCache = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun recordCookies(url: String, cookies: String, isPrivate: Boolean = false) {
        if (url.isBlank() || cookies.isBlank()) return
        val host = extractHost(url) ?: return
        val cache = if (isPrivate) privateCookieCache else standardCookieCache
        cache[host] = cookies
        if (!isPrivate) {
            PetalMediaSniffer.recordCookiesForUrl(url, cookies)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getCookies(url: String, isPrivate: Boolean = false): String? {
        if (url.isBlank()) return null
        val host = extractHost(url) ?: return null
        val cache = if (isPrivate) privateCookieCache else standardCookieCache

        // 1. Direct or suffix domain match from Gecko cookie cache
        val cached = cache[host] ?: cache.entries.firstOrNull { (k, _) ->
            host == k || host.endsWith(".$k") || k.endsWith(".$host")
        }?.value

        if (!cached.isNullOrBlank()) {
            return cached
        }

        // 2. Fall back to PetalMediaSniffer captured cookies ONLY for standard sessions.
        // Private sessions must NEVER access standard session cookies or sniffer storage.
        if (!isPrivate) {
            val snifferCookies = PetalMediaSniffer.getCookiesForUrl(url)
            if (!snifferCookies.isNullOrBlank()) {
                cache[host] = snifferCookies
                return snifferCookies
            }
        }

        return null
    }

    @JvmStatic
    @JvmOverloads
    fun hasCookies(url: String, isPrivate: Boolean = false): Boolean {
        return !getCookies(url, isPrivate).isNullOrBlank()
    }

    @JvmStatic
    @JvmOverloads
    fun getCookieCount(url: String, isPrivate: Boolean = false): Int {
        val cookies = getCookies(url, isPrivate) ?: return 0
        return cookies.split(";").count { it.isNotBlank() }
    }

    @JvmStatic
    fun clearPrivateCookies() {
        privateCookieCache.clear()
    }

    @JvmStatic
    fun clearAllCookies() {
        standardCookieCache.clear()
        privateCookieCache.clear()
    }

    @JvmStatic
    fun clearCookiesForHost(hostOrUrl: String) {
        val host = extractHost(hostOrUrl) ?: return
        standardCookieCache.remove(host)
        privateCookieCache.remove(host)
        standardCookieCache.keys.filter { it.endsWith(".$host") || host.endsWith(".$it") }.forEach {
            standardCookieCache.remove(it)
        }
        privateCookieCache.keys.filter { it.endsWith(".$host") || host.endsWith(".$it") }.forEach {
            privateCookieCache.remove(it)
        }
    }

    private fun extractHost(url: String): String? {
        return try {
            val host = if (url.contains("://")) {
                Uri.parse(url).host
            } else {
                Uri.parse("https://$url").host
            }
            host?.lowercase()?.removePrefix("www.")
        } catch (_: Throwable) {
            null
        }
    }
}
