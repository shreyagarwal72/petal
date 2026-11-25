package com.petal.browser.ui.components

import java.util.Locale

private val COMMON_WEB_TLDS = setOf(
    "com", "org", "net", "edu", "gov", "mil", "int",
    "io", "dev", "app", "co", "uk", "in", "de", "fr",
    "jp", "cn", "ru", "info", "biz", "me", "tv", "xyz",
    "gg", "ly", "ai", "us", "ca", "au", "br", "it",
    "es", "nl", "se", "no", "pl", "ch", "be", "at",
    "eu", "to", "cc", "sh", "fm", "so", "page", "site",
    "online", "store", "tech", "blog", "cloud"
)

private val COMPOUND_ARCHIVE_PREFIXES = setOf("gz", "bz2", "xz", "zst")

/**
 * Returns `true` when [text] looks like a standalone filename with an extension (for example
 * `petal-browser-arm64-v8a-release(1).apk`) rather than a webpage title or bare hostname.
 */
fun looksLikeFileName(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    val lastSegment = trimmed.substringAfterLast('/')
    val dotIdx = lastSegment.lastIndexOf('.')
    if (dotIdx <= 0 || dotIdx >= lastSegment.length - 1) return false
    val ext = lastSegment.substring(dotIdx + 1).lowercase(Locale.US)
    if (ext.length !in 2..8 || !ext.all { it.isLetterOrDigit() } || ext.none { it.isLetter() }) return false
    return ext !in COMMON_WEB_TLDS
}

/**
 * Middle-truncates long file names so both the leading identifier and trailing file extension
 * (e.g. `.apk`) remain readable inside compact sheets and dialogs.
 */
fun formatMiddleEllipsisFileName(fileName: String, maxChars: Int = 30): String {
    val trimmed = fileName.trim()
    if (trimmed.length <= maxChars || maxChars < 10) return trimmed

    val slashIdx = trimmed.lastIndexOf('/')
    val lastSegStart = slashIdx + 1
    val lastSeg = trimmed.substring(lastSegStart)
    val dotInSeg = lastSeg.lastIndexOf('.')

    if (dotInSeg > 0 && dotInSeg < lastSeg.length - 1) {
        var dotAbsolute = lastSegStart + dotInSeg
        val rawExt = trimmed.substring(dotAbsolute + 1)
        if (rawExt.length in 1..10 && rawExt.all { it.isLetterOrDigit() }) {
            // Preserve compound archive suffixes such as .tar.gz
            if (rawExt.lowercase(Locale.US) in COMPOUND_ARCHIVE_PREFIXES) {
                val stemBefore = trimmed.substring(lastSegStart, dotAbsolute)
                val prevDotInSeg = stemBefore.lastIndexOf('.')
                if (prevDotInSeg > 0 && stemBefore.substring(prevDotInSeg + 1).equals("tar", ignoreCase = true)) {
                    dotAbsolute = lastSegStart + prevDotInSeg
                }
            }
            val extWithDot = trimmed.substring(dotAbsolute)
            if (extWithDot.length <= maxChars - 5) {
                val tailLen = (extWithDot.length + 6).coerceAtMost((maxChars - 5).coerceAtLeast(extWithDot.length))
                val headLen = (maxChars - 1 - tailLen).coerceAtLeast(4)
                return trimmed.take(headLen) + "\u2026" + trimmed.takeLast(tailLen)
            }
        }
    }

    val tailLen = (maxChars / 3).coerceAtLeast(4)
    val headLen = (maxChars - 1 - tailLen).coerceAtLeast(4)
    return trimmed.take(headLen) + "\u2026" + trimmed.takeLast(tailLen)
}

/**
 * Formats [rawUrl] for display only: strips query strings (`?…`) and fragments (`#…`), shows the
 * clean domain, and appends at most a short cut-off path. Copy/share actions continue to use the
 * unedited raw URL.
 */
fun formatDisplayUrl(rawUrl: String, maxPathChars: Int = 24): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return ""

    if (trimmed.startsWith("data:", ignoreCase = true)) {
        val header = trimmed.substringAfter(':').substringBefore(',').take(32)
        return if (header.isNotEmpty()) "data:$header\u2026" else "data:\u2026"
    }

    if (trimmed.startsWith("magnet:", ignoreCase = true)) {
        val clean = trimmed.substringBefore('&')
        val cap = maxPathChars + 24
        return if (clean.length > cap) clean.take(cap) + "\u2026" else clean
    }

    val noQuery = trimmed.substringBefore('?').substringBefore('#')
    val isBlob = noQuery.startsWith("blob:", ignoreCase = true)
    val target = if (isBlob) noQuery.removePrefix("blob:") else noQuery
    val schemeIdx = target.indexOf("://")
    if (schemeIdx >= 0) {
        val rest = target.substring(schemeIdx + 3)
        val slashIdx = rest.indexOf('/')
        val authority = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
        val host = authority.substringAfter('@').substringBefore(':').removePrefix("www.").trim()
        val domain = if (host.length > 60) host.take(59) + "\u2026" else host
        val rawPath = if (slashIdx >= 0) rest.substring(slashIdx) else ""
        val cleanPath = if (rawPath == "/") "" else rawPath
        val shortPath = when {
            cleanPath.isEmpty() -> ""
            cleanPath.length <= maxPathChars -> cleanPath
            else -> cleanPath.take(maxPathChars) + "\u2026"
        }
        val formatted = (if (isBlob && domain.isNotEmpty()) "blob:" else "") + domain + shortPath
        if (formatted.isNotEmpty()) return formatted
    }

    val cap = maxPathChars + 32
    return if (noQuery.length > cap) noQuery.take(cap) + "\u2026" else noQuery
}
