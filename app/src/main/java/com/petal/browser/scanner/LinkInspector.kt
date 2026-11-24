package com.petal.browser.scanner

import java.util.Locale

/**
 * Risk classification for scanned links.
 */
enum class RiskLevel {
    SAFE,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Individual safety warning detected by [LinkInspector].
 */
data class LinkSafetyWarning(
    val id: String,
    val title: String,
    val description: String,
    val riskLevel: RiskLevel
)

/**
 * Security audit result for a scanned URL.
 */
data class LinkSafetyReport(
    val url: String,
    val host: String,
    val isSuspicious: Boolean,
    val riskLevel: RiskLevel,
    val warnings: List<LinkSafetyWarning>
)

/**
 * Pure Kotlin link inspector that identifies potential phishing, homoglyphs,
 * punycode disguise, URL shorteners, unencrypted HTTP, and IP hosts.
 */
object LinkInspector {

    private val IPV4_REGEX = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
    private val IPV6_REGEX = Regex("^\\[?[0-9a-fA-F:]+\\]?$")

    // Common URL shorteners used to conceal destination hosts
    private val KNOWN_SHORTENERS = setOf(
        "bit.ly",
        "tinyurl.com",
        "t.co",
        "goo.gl",
        "is.gd",
        "buff.ly",
        "ow.ly",
        "rb.gy",
        "cutt.ly",
        "rebrand.ly",
        "shorturl.at",
        "clck.ru",
        "v.gd",
        "bc.vc",
        "trib.al",
        "ift.tt",
        "adf.ly",
        "bl.ink",
        "soo.gd",
        "s.id",
        "qr.ae",
        "qr.net",
        "linktr.ee",
        "t.me",
        "wa.me"
    )

    // Homoglyph Cyrillic / Greek characters commonly substituted for ASCII
    private val CONFUSABLE_CHARS = setOf(
        '\u0430', '\u0435', '\u043E', '\u0440', '\u0441', '\u0443', '\u0445', // Cyrillic a, e, o, p, c, y, x
        '\u0410', '\u0412', '\u0415', '\u041A', '\u041C', '\u041D', '\u041E', '\u0420', '\u0421', '\u0422', '\u0425', // Cyrillic caps
        '\u03BF', '\u03C1', '\u03B1', '\u03B5', '\u03B9', '\u03BA', '\u03BD', '\u03C5', // Greek o, p, a, e, i, k, v, u
        '\u0456', '\u0458' // Cyrillic i, je
    )

    fun inspect(url: String, host: String? = null): LinkSafetyReport {
        val trimmed = url.trim()
        val parsedHost = (host ?: extractHost(trimmed)).lowercase(Locale.ROOT)
        val scheme = extractScheme(trimmed).lowercase(Locale.ROOT)

        val warnings = mutableListOf<LinkSafetyWarning>()

        // 1. Punycode check (disguised internationalized domain)
        if (parsedHost.startsWith("xn--") || parsedHost.contains(".xn--")) {
            warnings.add(
                LinkSafetyWarning(
                    id = "punycode",
                    title = "Punycode Domain",
                    description = "Uses encoded characters (xn--) that can visually mimic known websites.",
                    riskLevel = RiskLevel.HIGH
                )
            )
        }

        // 2. Homoglyph / lookalike characters in host
        val hasConfusable = parsedHost.any { it in CONFUSABLE_CHARS }
        val hasNonAscii = parsedHost.any { it.code > 127 && !it.isWhitespace() }
        if (hasConfusable || (hasNonAscii && !parsedHost.startsWith("xn--"))) {
            warnings.add(
                LinkSafetyWarning(
                    id = "lookalike",
                    title = "Lookalike Characters",
                    description = "Contains foreign script or confusable characters that look identical to standard letters.",
                    riskLevel = RiskLevel.HIGH
                )
            )
        }

        // 3. Known URL Shortener
        if (isShortener(parsedHost)) {
            warnings.add(
                LinkSafetyWarning(
                    id = "shortener",
                    title = "Shortened URL",
                    description = "Uses a link shortener ($parsedHost) which obscures the true destination.",
                    riskLevel = RiskLevel.MEDIUM
                )
            )
        }

        // 4. Insecure HTTP
        if (scheme == "http") {
            warnings.add(
                LinkSafetyWarning(
                    id = "plain_http",
                    title = "Insecure Connection",
                    description = "Unencrypted HTTP connection. Passwords and information can be intercepted.",
                    riskLevel = RiskLevel.MEDIUM
                )
            )
        }

        // 5. IP Address Host
        if (isIpAddress(parsedHost)) {
            warnings.add(
                LinkSafetyWarning(
                    id = "ip_host",
                    title = "Direct IP Address",
                    description = "Points to a numeric IP address ($parsedHost) instead of an authenticated domain name.",
                    riskLevel = RiskLevel.HIGH
                )
            )
        }

        // 6. Very long URL (> 256 characters)
        if (trimmed.length > 256) {
            warnings.add(
                LinkSafetyWarning(
                    id = "long_url",
                    title = "Unusually Long Link",
                    description = "Length exceeds 256 characters (${trimmed.length} chars) and may conceal tracking tokens or redirects.",
                    riskLevel = RiskLevel.LOW
                )
            )
        }

        val riskLevel = when {
            warnings.any { it.riskLevel == RiskLevel.HIGH } -> RiskLevel.HIGH
            warnings.any { it.riskLevel == RiskLevel.MEDIUM } -> RiskLevel.MEDIUM
            warnings.any { it.riskLevel == RiskLevel.LOW } -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }

        return LinkSafetyReport(
            url = trimmed,
            host = parsedHost,
            isSuspicious = warnings.isNotEmpty(),
            riskLevel = riskLevel,
            warnings = warnings
        )
    }

    private fun extractScheme(url: String): String {
        val idx = url.indexOf("://")
        return if (idx != -1) url.substring(0, idx) else ""
    }

    private fun extractHost(url: String): String {
        val afterScheme = if (url.contains("://")) url.substring(url.indexOf("://") + 3) else url
        val hostEndIdx = listOf(
            afterScheme.indexOf('/'),
            afterScheme.indexOf('?'),
            afterScheme.indexOf('#')
        ).filter { it != -1 }.minOrNull() ?: afterScheme.length

        var hostWithPort = afterScheme.substring(0, hostEndIdx)
        val atIdx = hostWithPort.indexOf('@')
        if (atIdx != -1) {
            hostWithPort = hostWithPort.substring(atIdx + 1)
        }
        val colonIdx = hostWithPort.indexOf(':')
        return if (colonIdx != -1) hostWithPort.substring(0, colonIdx) else hostWithPort
    }

    private fun isShortener(host: String): Boolean {
        if (host in KNOWN_SHORTENERS) return true
        for (shortener in KNOWN_SHORTENERS) {
            if (host.endsWith(".$shortener")) return true
        }
        return false
    }

    private fun isIpAddress(host: String): Boolean {
        if (IPV4_REGEX.matches(host)) {
            val parts = host.split('.')
            return parts.all { it.toIntOrNull() in 0..255 }
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            return true
        }
        if (host.count { it == ':' } >= 2 && IPV6_REGEX.matches(host)) {
            return true
        }
        return false
    }
}
