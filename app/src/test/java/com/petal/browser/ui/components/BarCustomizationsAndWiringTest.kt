package com.petal.browser.ui.components

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarCustomizationsAndWiringTest {

    private class MemoryPrefs(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
        private val map = linkedMapOf<String, Any?>().apply { putAll(initial) }
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }
    }

    @Test
    fun newCustomizationEnums_roundTripAndFallbackCleanly() {
        UrlDisplayMode.entries.forEach { assertEquals(it, UrlDisplayMode.fromPref(it.pref)) }
        assertEquals(UrlDisplayMode.DOMAIN_ONLY, UrlDisplayMode.fromPref("domain_only"))
        assertEquals(UrlDisplayMode.FULL, UrlDisplayMode.fromPref(null))
        assertEquals(UrlDisplayMode.FULL, UrlDisplayMode.fromPref("unknown"))

        BarProgressStyle.entries.forEach { assertEquals(it, BarProgressStyle.fromPref(it.pref)) }
        assertEquals(BarProgressStyle.HIDDEN, BarProgressStyle.fromPref("hidden"))
        assertEquals(BarProgressStyle.LINE, BarProgressStyle.fromPref(null))

        ClassicOuterAction.entries.forEach { assertEquals(it, ClassicOuterAction.fromPref(it.pref)) }
        assertEquals(ClassicOuterAction.BOOKMARKS, ClassicOuterAction.fromPref("bookmarks"))
        assertEquals(ClassicOuterAction.SHARE, ClassicOuterAction.fromPref("invalid"))

        NavSearchButtonStyle.entries.forEach { assertEquals(it, NavSearchButtonStyle.fromPref(it.pref)) }
        assertEquals(NavSearchButtonStyle.INTEGRATED, NavSearchButtonStyle.fromPref("integrated"))
        assertEquals(NavSearchButtonStyle.SEPARATE, NavSearchButtonStyle.fromPref(null))

        NavMidSlot.entries.forEach { assertEquals(it, NavMidSlot.fromPref(it.pref)) }
        assertEquals(NavMidSlot.DOWNLOADS, NavMidSlot.fromPref("downloads"))
        assertEquals(NavMidSlot.NEW_TAB, NavMidSlot.fromPref("unknown"))
    }

    @Test
    fun aiResearchLegacyAlias_isHiddenFromPicker_andMigratesWithoutDuplicate() {
        assertFalse(BarAction.AI_RESEARCH.isAvailable)
        assertEquals(BarAction.AI, BarAction.fromId("AI_RESEARCH"))

        val sp = MemoryPrefs(
            mapOf(
                BarPrefs.KEY_MERGE_LEADING to "HOME,AI,AI_RESEARCH",
                BarPrefs.KEY_MERGE_TRAILING to "NEW_TAB,TABS"
            )
        )
        val (leading, trailing) = BarPrefs.getSanitizedMergeActions(sp)
        val combined = leading + trailing
        assertEquals(1, combined.count { it == BarAction.AI })
        assertTrue(combined.contains(BarAction.MENU))
        assertTrue(combined.size <= BarPrefs.MAX_MERGE_ACTIONS_TOTAL)
    }
}
