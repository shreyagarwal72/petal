package com.petal.browser.download

import java.util.Locale

/**
 * Extracts downloadable links from pasted text / a text file for the batch-import sheet.
 * Pure Kotlin (no Android types) so it is unit-testable.
 */
object PetalLinkImport {

    enum class Kind { FILE, MEDIA, MAGNET }
    enum class Filter { ALL, FILES, MEDIA, MAGNET }

    data class ImportLink(val url: String, val kind: Kind, val suggestedName: String)

    private val URL_REGEX = Regex("""(?i)\b(?:https?://|magnet:\?)[^\s<>"'`]+""")
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '\'', '"')
    private val MEDIA_EXT = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "flv", "ts", "m3u8", "mpd",
        "mp3", "flac", "wav", "ogg", "m4a", "opus", "aac"
    )

    /** Links in order of first appearance, de-duplicated after normalisation. */
    @JvmStatic
    fun extract(text: String?, maxLinks: Int = 500): List<ImportLink> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        val out = ArrayList<ImportLink>()
        for (m in URL_REGEX.findAll(text)) {
            var raw = m.value.trimEnd(*TRAILING)
            // Keep a closing paren that balances an opening one inside the URL (wikipedia style).
            if (m.value.endsWith(")") && raw.count { it == '(' } > raw.count { it == ')' }) raw += ")"
            val link = normalize(raw) ?: continue
            if (!seen.add(dedupeKey(link))) continue
            out.add(ImportLink(link, kindOf(link), suggestName(link)))
            if (out.size >= maxLinks) break
        }
        return out
    }

    @JvmStatic
    fun filter(links: List<ImportLink>, filter: Filter): List<ImportLink> = when (filter) {
        Filter.ALL -> links
        Filter.FILES -> links.filter { it.kind == Kind.FILE }
        Filter.MEDIA -> links.filter { it.kind == Kind.MEDIA }
        Filter.MAGNET -> links.filter { it.kind == Kind.MAGNET }
    }

    private fun normalize(raw: String): String? {
        if (raw.startsWith("magnet:?", ignoreCase = true)) {
            return if (raw.contains("xt=urn:btih:", ignoreCase = true) || raw.contains("xt=urn:btmh:", ignoreCase = true)) raw else null
        }
        return PetalDownloadRouter.normalizeDownloadUrl(raw)
    }

    /** Case-insensitive on scheme+host, ignores a trailing slash and the fragment. */
    private fun dedupeKey(url: String): String {
        if (url.startsWith("magnet:", ignoreCase = true)) {
            val hash = Regex("""(?i)xt=urn:bt(?:ih|mh):([^&]+)""").find(url)?.groupValues?.get(1)
            return "magnet:" + (hash ?: url).lowercase(Locale.ROOT)
        }
        val noFragment = url.substringBefore('#')
        val schemeEnd = noFragment.indexOf("://")
        if (schemeEnd < 0) return noFragment
        val pathStart = noFragment.indexOf('/', schemeEnd + 3).let { if (it < 0) noFragment.length else it }
        val head = noFragment.substring(0, pathStart).lowercase(Locale.ROOT)
        return (head + noFragment.substring(pathStart)).trimEnd('/')
    }

    private fun kindOf(url: String): Kind {
        if (url.startsWith("magnet:", ignoreCase = true)) return Kind.MAGNET
        val path = url.substringBefore('#').substringBefore('?')
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (ext in MEDIA_EXT) Kind.MEDIA else Kind.FILE
    }

    private fun suggestName(url: String): String {
        if (url.startsWith("magnet:", ignoreCase = true)) {
            val dn = Regex("""(?i)[?&]dn=([^&]+)""").find(url)?.groupValues?.get(1)
            return dn?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() } ?: "Magnet link"
        }
        val last = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val decoded = runCatching { java.net.URLDecoder.decode(last.replace("+", "%2B"), "UTF-8") }.getOrDefault(last)
        return decoded.ifBlank { url.substringAfter("://").substringBefore('/') }
    }
}
