package com.petal.browser.database

import android.content.Context

/**
 * Which bookmarks are on the reading list. Bookmarks live in the Places database, which has no such flag,
 * so the flag is kept here as a set of URLs. Sync reads and writes it through the bookmark list.
 */
object PetalReadingList {
    private const val PREFS = "petal_reading_list"
    private const val KEY = "urls"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(c: Context): Set<String> = try {
        HashSet(prefs(c).getStringSet(KEY, emptySet()) ?: emptySet())
    } catch (_: Throwable) { emptySet() }

    fun set(c: Context, url: String?, flagged: Boolean) {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return
        val s = HashSet(all(c))
        val changed = if (flagged) s.add(u) else s.remove(u)
        if (changed) prefs(c).edit().putStringSet(KEY, s).apply()
    }

    fun clear(c: Context) { prefs(c).edit().remove(KEY).apply() }
}
