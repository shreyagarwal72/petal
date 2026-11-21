package com.petal.browser.browser

/** Minimal tab view used by the pure hygiene rules below (keeps them JVM-testable). */
data class HygieneTab(
    val id: String,
    val url: String,
    val isPrivate: Boolean,
    val isSelected: Boolean,
    val isPinned: Boolean,
    val lastAccess: Long = 0L,
    val hasActiveMedia: Boolean = false
)

/** Duplicate-tab detection for the tab manager's "Close duplicates" action. */
object PetalTabDuplicates {
    /** Normalises a URL for comparison: scheme/host case, fragment and trailing slash ignored. */
    @JvmStatic
    fun normalize(url: String): String? {
        val u = url.trim()
        if (u.isEmpty() || u.equals("about:blank", true) || u.startsWith("about:home", true) ||
            u.startsWith("petal://", true) || u.startsWith("file:///android_asset/", true)) return null
        val noFragment = u.substringBefore('#')
        val schemeEnd = noFragment.indexOf("://")
        val lowered = if (schemeEnd > 0) {
            val hostEnd = noFragment.indexOf('/', schemeEnd + 3).let { if (it < 0) noFragment.length else it }
            noFragment.substring(0, hostEnd).lowercase() + noFragment.substring(hostEnd)
        } else noFragment
        return lowered.trimEnd('/')
    }

    /**
     * Ids to close so that exactly one tab per distinct page remains. Private and normal tabs are
     * never compared with each other. The survivor is the selected tab, else a pinned one, else
     * the first in list order.
     */
    @JvmStatic
    fun idsToClose(tabs: List<HygieneTab>): List<String> {
        val result = ArrayList<String>()
        for (privateGroup in listOf(false, true)) {
            val byUrl = LinkedHashMap<String, MutableList<HygieneTab>>()
            for (t in tabs) {
                if (t.isPrivate != privateGroup) continue
                val key = normalize(t.url) ?: continue
                byUrl.getOrPut(key) { mutableListOf() }.add(t)
            }
            for (group in byUrl.values) {
                if (group.size < 2) continue
                val keep = group.firstOrNull { it.isSelected } ?: group.firstOrNull { it.isPinned } ?: group.first()
                group.filter { it.id != keep.id && !it.isSelected }.forEach { result.add(it.id) }
            }
        }
        return result
    }
}

/** Scheduled auto-archive: stale tabs move into Recently Closed (never deleted outright). */
object PetalTabArchiver {
    const val PREF_AUTO_ARCHIVE_DAYS = "sp_tab_auto_archive_days" // "0" = off (default)
    private const val DAY_MS = 24L * 60 * 60 * 1000

    @JvmStatic
    fun idsToArchive(tabs: List<HygieneTab>, now: Long, days: Int): List<String> {
        if (days <= 0) return emptyList()
        val limit = days * DAY_MS
        return tabs.filter {
            !it.isPrivate && !it.isSelected && !it.isPinned && !it.hasActiveMedia &&
                it.lastAccess > 0L && now - it.lastAccess >= limit
        }.map { it.id }
    }
}
