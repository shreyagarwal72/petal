package com.petal.browser.engine.gecko

import mozilla.components.browser.state.state.BrowserState

/** Pure (Android-free) session rules, kept out of PetalEngineStore so they are JVM-testable. */
object PetalSessionRules {
    /**
     * Custom Tab and PWA windows keep their tab in the shared store but are not browser tabs: they
     * must never be persisted, listed in the tab manager, or suspended/archived/de-duplicated.
     */
    @JvmStatic
    fun isEphemeralTabId(id: String): Boolean = id.startsWith("tab_custom_") || id.startsWith("pwa_")

    /** Copy of [state] without ephemeral tabs and with a selection that still exists. */
    @JvmStatic
    fun withoutEphemeralTabs(state: BrowserState, fallbackSelectedId: String? = null): BrowserState {
        if (state.tabs.none { isEphemeralTabId(it.id) }) return state
        val kept = state.tabs.filterNot { isEphemeralTabId(it.id) }
        val sel = state.selectedTabId
        val newSel = when {
            sel != null && kept.any { it.id == sel } -> sel
            fallbackSelectedId != null && kept.any { it.id == fallbackSelectedId } -> fallbackSelectedId
            else -> kept.firstOrNull()?.id
        }
        return state.copy(tabs = kept, selectedTabId = newSel)
    }

    /**
     * Tolerant probe of the serialized Gecko state: finds the history entry array and reports
     * whether it has more than one entry. Unknown layouts count as "has history" (trust Back).
     */
    @JvmStatic
    fun historyHasBack(json: org.json.JSONObject): Boolean {
        fun findEntries(o: Any?): Int? {
            when (o) {
                is org.json.JSONObject -> {
                    for (k in listOf("entries", "history")) {
                        val v = o.opt(k)
                        if (v is org.json.JSONArray) return v.length()
                    }
                    val it = o.keys()
                    while (it.hasNext()) { findEntries(o.opt(it.next()))?.let { n -> return n } }
                }
                is org.json.JSONArray -> for (i in 0 until o.length()) findEntries(o.opt(i))?.let { n -> return n }
            }
            return null
        }
        val n = findEntries(json) ?: return true
        return n > 1
    }
}
