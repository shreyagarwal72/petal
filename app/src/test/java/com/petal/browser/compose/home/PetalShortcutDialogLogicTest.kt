package com.petal.browser.compose.home

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PetalShortcutDialogLogicTest {

    private class FakePreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()

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
            override fun remove(key: String?) = apply { if (key != null) pending[key] = REMOVE }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                for ((k, v) in pending) {
                    if (v === REMOVE) map.remove(k) else map[k] = v
                }
            }
        }

        private companion object {
            val REMOVE = Any()
        }
    }

    private lateinit var prefs: FakePreferences

    @Before
    fun setUp() {
        prefs = FakePreferences()
    }

    @Test
    fun `validateShortcutUrl rejects empty whitespace and unsafe schemes`() {
        assertFalse(validateShortcutUrl("").isValid)
        assertFalse(validateShortcutUrl("   ").isValid)
        assertFalse(validateShortcutUrl("not a url").isValid)
        assertFalse(validateShortcutUrl("javascript:alert(1)").isValid)
        assertFalse(validateShortcutUrl("localhost_no_dot").isValid)
    }

    @Test
    fun `validateShortcutUrl normalizes valid domains and preserves https or http`() {
        val bare = validateShortcutUrl("petalbrowser.org")
        assertTrue(bare.isValid)
        assertEquals("https://petalbrowser.org", bare.normalizedUrl)
        assertNull(bare.errorRes)

        val explicitHttp = validateShortcutUrl("http://localhost:8080/test")
        assertTrue(explicitHttp.isValid)
        assertEquals("http://localhost:8080/test", explicitHttp.normalizedUrl)
    }

    @Test
    fun `saveHomeShortcutsToPrefs and loadHomeShortcutsFromPrefs preserve id color and shape`() {
        val list = listOf(
            PetalShortcut(label = "First", url = "https://same.example", siteId = "globe", containerColor = Color(0xFF34A853), shapeName = "Flower", id = "id-1"),
            PetalShortcut(label = "Second", url = "https://same.example", siteId = "globe", containerColor = Color(0xFFEA4335), shapeName = "Burst", id = "id-2"),
        )
        val key = "sp_custom_home_shortcuts_json_v3"
        saveHomeShortcutsToPrefs(prefs, key, list)
        val restored = loadHomeShortcutsFromPrefs(prefs, key)
        assertEquals(2, restored.size)
        assertEquals("id-1", restored[0].id)
        assertEquals("id-2", restored[1].id)
        assertNotEquals(restored[0].containerColor, restored[1].containerColor)
        assertEquals(Color(0xFF34A853), restored[0].containerColor)
        assertEquals(Color(0xFFEA4335), restored[1].containerColor)
        assertEquals("Flower", restored[0].shapeName)
        assertEquals("Burst", restored[1].shapeName)
    }
}
