package com.petal.browser.ui.components

import android.content.SharedPreferences

/**
 * Configuration and preference accessors for Petal toolbar layout and buttons.
 *
 * Enforces business rules:
 * - At most 5 buttons total across leading and trailing lists.
 * - The MENU button must always be present (appended to trailing if omitted).
 * - Duplicate and unavailable actions are filtered out on read.
 */
object BarPrefs {

    const val KEY_ADDRESS_BAR_STYLE = "sp_address_bar_style"
    const val KEY_MERGE_LEADING = "sp_merge_leading"
    const val KEY_MERGE_TRAILING = "sp_merge_trailing"
    const val KEY_MERGE_LAYOUT = "sp_merge_layout"
    const val KEY_BAR_COLLAPSE_STYLE = "sp_bar_collapse_style"
    const val KEY_ADDRESS_BAR_POSITION = "sp_address_bar_position"
    const val KEY_HIDE_BARS_ON_SCROLL = "sp_hide_bars_on_scroll"

    const val STYLE_CLASSIC = "CLASSIC"
    const val STYLE_MERGE = "MERGE"

    const val LAYOUT_SINGLE = "SINGLE"
    const val LAYOUT_STACKED = "STACKED"

    const val COLLAPSE_PILL = "PILL"
    const val COLLAPSE_HIDE = "HIDE"

    const val DEFAULT_MERGE_LEADING = "TABS"
    const val DEFAULT_MERGE_TRAILING = "NEW_TAB,MENU"

    const val MAX_MERGE_ACTIONS_TOTAL = 5

    @JvmStatic
    fun isMergeStyle(sp: SharedPreferences): Boolean =
        STYLE_MERGE.equals(sp.getString(KEY_ADDRESS_BAR_STYLE, STYLE_CLASSIC), ignoreCase = true)

    @JvmStatic
    fun isStackedLayout(sp: SharedPreferences): Boolean =
        LAYOUT_STACKED.equals(sp.getString(KEY_MERGE_LAYOUT, LAYOUT_SINGLE), ignoreCase = true)

    @JvmStatic
    fun isPillCollapse(sp: SharedPreferences): Boolean =
        COLLAPSE_PILL.equals(sp.getString(KEY_BAR_COLLAPSE_STYLE, COLLAPSE_PILL), ignoreCase = true)

    @JvmStatic
    fun isBottomPosition(sp: SharedPreferences): Boolean =
        "BOTTOM".equals(sp.getString(KEY_ADDRESS_BAR_POSITION, "TOP"), ignoreCase = true)

    @JvmStatic
    fun isHideOnScrollEnabled(sp: SharedPreferences): Boolean =
        sp.getBoolean(KEY_HIDE_BARS_ON_SCROLL, true)

    /**
     * Reads leading and trailing merge button actions from SharedPreferences, enforcing all
     * toolbar rules (no duplicates, at most 5 total, MENU always present).
     */
    @JvmStatic
    fun getSanitizedMergeActions(sp: SharedPreferences): Pair<List<BarAction>, List<BarAction>> {
        val rawLeading = BarAction.parseActionList(sp.getString(KEY_MERGE_LEADING, DEFAULT_MERGE_LEADING))
        val rawTrailing = BarAction.parseActionList(sp.getString(KEY_MERGE_TRAILING, DEFAULT_MERGE_TRAILING))

        val seen = mutableSetOf<BarAction>()
        val leading = mutableListOf<BarAction>()
        for (action in rawLeading) {
            if (action.isAvailable && seen.add(action)) {
                leading.add(action)
            }
        }

        val trailing = mutableListOf<BarAction>()
        for (action in rawTrailing) {
            if (action.isAvailable && seen.add(action)) {
                trailing.add(action)
            }
        }

        // Rule: MENU must always be present in the toolbar
        if (!seen.contains(BarAction.MENU)) {
            trailing.add(BarAction.MENU)
            seen.add(BarAction.MENU)
        }

        // Rule: At most 5 actions total across both lists
        while (leading.size + trailing.size > MAX_MERGE_ACTIONS_TOTAL) {
            // Trim non-MENU actions, preferring to shrink leading first
            if (leading.isNotEmpty()) {
                val nonMenuIdx = leading.indexOfLast { it != BarAction.MENU }
                if (nonMenuIdx >= 0) {
                    leading.removeAt(nonMenuIdx)
                } else if (trailing.isNotEmpty()) {
                    val trailingNonMenuIdx = trailing.indexOfFirst { it != BarAction.MENU }
                    if (trailingNonMenuIdx >= 0) trailing.removeAt(trailingNonMenuIdx) else break
                } else break
            } else if (trailing.isNotEmpty()) {
                val trailingNonMenuIdx = trailing.indexOfFirst { it != BarAction.MENU }
                if (trailingNonMenuIdx >= 0) trailing.removeAt(trailingNonMenuIdx) else break
            } else break
        }

        return Pair(leading, trailing)
    }

    @JvmStatic
    fun saveMergeActions(sp: SharedPreferences, leading: List<BarAction>, trailing: List<BarAction>) {
        sp.edit()
            .putString(KEY_MERGE_LEADING, BarAction.serializeActionList(leading))
            .putString(KEY_MERGE_TRAILING, BarAction.serializeActionList(trailing))
            .apply()
    }
}
