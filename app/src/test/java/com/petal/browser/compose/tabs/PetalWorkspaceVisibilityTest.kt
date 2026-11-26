package com.petal.browser.compose.tabs

import android.content.SharedPreferences
import com.google.gson.Gson
import com.petal.browser.browser.PetalTabMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PetalWorkspaceVisibilityTest {

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

    private lateinit var defaultSp: FakePreferences
    private lateinit var wsPrefs: FakePreferences

    @Before
    fun setUp() {
        defaultSp = FakePreferences()
        wsPrefs = FakePreferences()
        PetalWorkspaceManager.resetForTest()
    }

    @Test
    fun `migration keeps workspace chips off on fresh install`() {
        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(defaultSp, wsPrefs, emptyList())

        assertFalse(defaultSp.getBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))
        assertTrue(defaultSp.getBoolean(PetalWorkspaceManager.PREF_WORKSPACE_CHIPS_MIGRATED, false))
    }

    @Test
    fun `migration enables workspace chips when user already persisted a non-default workspace`() {
        val custom = listOf(
            PetalWorkspace(PetalWorkspaceManager.DEFAULT_WORKSPACE_ID, "General", "Home", "#3B82F6", 0, true),
            PetalWorkspace("ws_custom1", "Gaming", "Work", "#10B981", 1, false),
        )
        wsPrefs.edit().putString("workspaces_json", Gson().toJson(custom)).commit()

        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(defaultSp, wsPrefs, emptyList())

        assertTrue(defaultSp.getBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))
        assertTrue(defaultSp.getBoolean(PetalWorkspaceManager.PREF_WORKSPACE_CHIPS_MIGRATED, false))
    }

    @Test
    fun `migration enables workspace chips when any tab is assigned to a non-default workspace`() {
        val meta = listOf(PetalTabMetadata(tabId = "tab-work", workspaceId = "ws_work"))

        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(defaultSp, wsPrefs, meta)

        assertTrue(defaultSp.getBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))
    }

    @Test
    fun `migration never overrides an explicit user preference before or after running`() {
        val custom = listOf(
            PetalWorkspace(PetalWorkspaceManager.DEFAULT_WORKSPACE_ID, "General", "Home", "#3B82F6", 0, true),
            PetalWorkspace("ws_custom1", "Custom", "Home", "#3B82F6", 1, false),
        )
        wsPrefs.edit().putString("workspaces_json", Gson().toJson(custom)).commit()
        defaultSp.edit().putBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false).commit()

        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(defaultSp, wsPrefs, emptyList())
        assertFalse(defaultSp.getBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))

        defaultSp.edit().putBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, true).commit()
        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(defaultSp, wsPrefs, emptyList())
        assertTrue(defaultSp.getBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))

        defaultSp.edit().putBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false).commit()
        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(defaultSp, wsPrefs, emptyList())
        assertFalse(defaultSp.getBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))
    }

    @Test
    fun `tab filtering returns all regular tabs when workspace chips are off and filters when on`() {
        val tabs = listOf(
            PetalTabItem(id = "t1", title = "Default", url = "https://a.example", workspaceId = null, isIncognito = false),
            PetalTabItem(id = "t2", title = "Personal", url = "https://b.example", workspaceId = "ws_personal", isIncognito = false),
            PetalTabItem(id = "t3", title = "Work", url = "https://c.example", workspaceId = "ws_work", isIncognito = false),
            PetalTabItem(id = "t4", title = "PinnedGlobal", url = "https://d.example", workspaceId = "ws_work", isPinned = true, isPinnedGlobal = true),
            PetalTabItem(id = "t5", title = "Private", url = "https://e.example", workspaceId = null, isIncognito = true),
        )

        val whenOff = PetalWorkspaceManager.filterRegularTabs(
            tabs = tabs,
            activeWorkspaceId = "ws_work",
            showWorkspaceChips = false,
        )
        assertEquals(listOf("t1", "t2", "t3", "t4"), whenOff.map { it.id })

        val whenOnWork = PetalWorkspaceManager.filterRegularTabs(
            tabs = tabs,
            activeWorkspaceId = "ws_work",
            showWorkspaceChips = true,
        )
        assertEquals(listOf("t3", "t4"), whenOnWork.map { it.id })

        val whenOnDefault = PetalWorkspaceManager.filterRegularTabs(
            tabs = tabs,
            activeWorkspaceId = PetalWorkspaceManager.DEFAULT_WORKSPACE_ID,
            showWorkspaceChips = true,
        )
        assertEquals(listOf("t1", "t4"), whenOnDefault.map { it.id })
    }

    @Test
    fun `effectiveActiveWorkspaceId restores stored workspace if it exists or falls back to default`() {
        val workspaces = listOf(
            PetalWorkspace(PetalWorkspaceManager.DEFAULT_WORKSPACE_ID, "General", "Home", "#3B82F6", isDefault = true),
            PetalWorkspace("ws_work", "Work", "Work", "#10B981"),
        )
        assertEquals(
            PetalWorkspaceManager.DEFAULT_WORKSPACE_ID,
            PetalWorkspaceManager.effectiveActiveWorkspaceId(workspaces, "ws_work", showWorkspaceChips = false)
        )
        assertEquals(
            "ws_work",
            PetalWorkspaceManager.effectiveActiveWorkspaceId(workspaces, "ws_work", showWorkspaceChips = true)
        )
        assertEquals(
            PetalWorkspaceManager.DEFAULT_WORKSPACE_ID,
            PetalWorkspaceManager.effectiveActiveWorkspaceId(workspaces, "ws_deleted", showWorkspaceChips = true)
        )
    }
}
