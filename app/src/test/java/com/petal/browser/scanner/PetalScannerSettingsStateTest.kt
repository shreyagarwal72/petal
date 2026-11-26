package com.petal.browser.scanner

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class PetalScannerSettingsStateTest {

    private class FakePreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()
        private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.remove(listener)
        }

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
                val changed = mutableListOf<String>()
                if (clearAll) {
                    changed.addAll(map.keys)
                    map.clear()
                }
                for ((k, v) in pending) {
                    if (v === REMOVE) map.remove(k) else map[k] = v
                    changed.add(k)
                }
                val snapshot = listeners.toList()
                for (k in changed) {
                    for (listener in snapshot) {
                        listener.onSharedPreferenceChanged(this@FakePreferences, k)
                    }
                }
            }
        }

        private companion object {
            val REMOVE = Any()
        }
    }

    private lateinit var context: Context
    private lateinit var prefs: FakePreferences

    @Before
    fun setUp() {
        context = mock()
        prefs = FakePreferences()
    }

    @Test
    fun `state holder reads default scanner settings`() {
        val holder = PetalScannerSettingsStateHolder(context, prefs)
        try {
            holder.start()
            val state = holder.state.value
            assertFalse(state.autoOpen)
            assertTrue(state.vibrate)
            assertFalse(state.beep)
            assertTrue(state.historyEnabled)
            assertTrue(state.safetyWarnings)
        } finally {
            holder.dispose()
        }
    }

    @Test
    fun `state holder updates both SharedPreferences and UI state`() {
        val holder = PetalScannerSettingsStateHolder(context, prefs)
        try {
            holder.start()
            holder.setAutoOpenEnabled(true)
            holder.setVibrateEnabled(false)
            holder.setBeepEnabled(true)
            holder.setSafetyWarningsEnabled(false)
            holder.setHistoryEnabled(false)

            val state = holder.state.value
            assertTrue(state.autoOpen)
            assertFalse(state.vibrate)
            assertTrue(state.beep)
            assertFalse(state.safetyWarnings)
            assertFalse(state.historyEnabled)

            val reloaded = PetalScannerSettings.readState(prefs)
            assertEquals(state, reloaded)
        } finally {
            holder.dispose()
        }
    }

    @Test
    fun `state holder reacts to external preference changes and stops after dispose`() {
        val holder = PetalScannerSettingsStateHolder(context, prefs)
        holder.start()
        prefs.edit().putBoolean(PetalScannerSettings.PREF_AUTO_OPEN, true).commit()
        prefs.edit().putBoolean(PetalScannerSettings.PREF_SAFETY_WARNINGS, false).commit()

        assertEquals(true, holder.state.value.autoOpen)
        assertEquals(false, holder.state.value.safetyWarnings)

        holder.dispose()
        prefs.edit().putBoolean(PetalScannerSettings.PREF_AUTO_OPEN, false).commit()
        assertEquals(true, holder.state.value.autoOpen)
    }
}
