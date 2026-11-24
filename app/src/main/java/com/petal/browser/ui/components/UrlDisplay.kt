package com.petal.browser.ui.components

import android.net.Uri

/**
 * Splits a URL into the pieces the address pill draws.
 *
 * Only [host] is emphasised. Text before an `@` is user-info (`https://google.com@evil.test/` is a
 * login to evil.test, not google.com), so it is kept apart and never styled as the domain.
 */
data class UrlDisplayParts(
    /** Muted text drawn before the host, e.g. `user:pw@`. Empty for normal URLs. */
    val userInfo: String,
    /** The real host with a leading `www.` removed. This is the bold part. */
    val host: String,
    /** Port, path, query and fragment, drawn muted. */
    val rest: String,
    /** True when any host label is punycode (`xn--`), a common lookalike-domain vector. */
    val isPunycode: Boolean,
    val isHttps: Boolean,
    val isHttp: Boolean
)

object UrlDisplay {

    @JvmStatic
    fun isPunycodeHost(host: String?): Boolean =
        host != null && host.split('.').any { it.startsWith("xn--", ignoreCase = true) }

    @JvmStatic
    fun stripWww(host: String): String =
        if (host.startsWith("www.", ignoreCase = true) && host.length > 4) host.substring(4) else host

    /** Parses with [Uri]. */
    @JvmStatic
    fun parse(url: String): UrlDisplayParts = split(url, runCatching { Uri.parse(url).host }.getOrNull())

    /**
     * Pure part of [parse] (host is passed in) so it can be unit tested without Android.
     * Falls back to the raw authority when [parsedHost] is null.
     */
    @JvmStatic
    fun split(url: String, parsedHost: String?): UrlDisplayParts {
        val isHttps = url.startsWith("https://", ignoreCase = true)
        val isHttp = url.startsWith("http://", ignoreCase = true)
        val afterScheme = when {
            isHttps -> url.substring(8)
            isHttp -> url.substring(7)
            else -> url
        }
        val authorityEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#')).let { if (it == -1) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd)
        val tail = afterScheme.substring(authorityEnd)

        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at + 1) else ""
        val hostAndPort = if (at >= 0) authority.substring(at + 1) else authority

        val rawHost = (parsedHost?.takeIf { it.isNotEmpty() }) ?: hostAndPort.substringBefore(':').trim('[', ']')
        // Port is whatever follows the host in the authority.
        val portIdx = hostAndPort.indexOf(rawHost, ignoreCase = true).let { if (it >= 0) it + rawHost.length else -1 }
        val port = if (portIdx in 0..hostAndPort.length) hostAndPort.substring(portIdx) else ""

        return UrlDisplayParts(
            userInfo = userInfo,
            host = stripWww(rawHost),
            rest = port + tail,
            isPunycode = isPunycodeHost(rawHost),
            isHttps = isHttps,
            isHttp = isHttp
        )
    }
}
