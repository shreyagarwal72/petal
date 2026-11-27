package com.petal.browser.tabs

import android.content.Context
import android.content.SharedPreferences
import com.petal.browser.browser.PetalTabMetadataStore
import com.petal.browser.compose.tabs.PetalTabGroupManager
import com.petal.browser.compose.tabs.PetalTabItem
import com.petal.browser.unit.PetalRecentlyClosedManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TabManagerAndVaultAuditTest {

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
    private lateinit var context: Context

    @Before
    fun setUp() {
        prefs = FakePreferences()
        context = mock()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.packageName).thenReturn("com.petal.browser")
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        PetalRecentlyClosedManager.resetForTest()
        PetalRecentlyClosedManager.init(context)
        PetalTabGroupManager.resetForTest()
    }

    @Test
    fun `recently closed filters home and blank urls and excludes private tabs`() {
        PetalRecentlyClosedManager.pushClosedTab("p1", "Secret", "https://secret.example", isIncognito = true)
        PetalRecentlyClosedManager.pushClosedTab("h1", "Home", "about:home", isIncognito = false)
        PetalRecentlyClosedManager.pushClosedTab("h2", "Blank", "about:blank", isIncognito = false)
        PetalRecentlyClosedManager.pushClosedTab("h3", "Petal", "petal://home", isIncognito = false)
        PetalRecentlyClosedManager.pushClosedTab("h4", "Asset", "file:///android_asset/petal_home.html", isIncognito = false)
        PetalRecentlyClosedManager.pushClosedTab(
            id = "t1",
            title = "Mozilla",
            url = "https://mozilla.org",
            originalIndex = 2,
            isIncognito = false,
            engineStateJson = "{\"history\":{\"entries\":[{\"url\":\"https://mozilla.org\"}]}}",
            workspaceId = "ws_work",
            isPinned = true,
            parentId = "t0"
        )

        val records = PetalRecentlyClosedManager.getRecentlyClosedTabs()
        assertEquals(1, records.size)
        val rec = records.first()
        assertEquals("t1", rec.id)
        assertEquals(2, rec.originalIndex)
        assertEquals("ws_work", rec.workspaceId)
        assertTrue(rec.isPinned)
        assertEquals("t0", rec.parentId)
        assertNotNull(rec.engineStateJson)
    }

    @Test
    fun `recently closed deduplicates matching urls anywhere in history`() {
        PetalRecentlyClosedManager.pushClosedTab("t1", "One", "https://one.example")
        PetalRecentlyClosedManager.pushClosedTab("t2", "Two", "https://two.example")
        PetalRecentlyClosedManager.pushClosedTab("t3", "One Again", "https://one.example")

        val urls = PetalRecentlyClosedManager.getRecentlyClosedTabs().map { it.url }
        assertEquals(listOf("https://one.example", "https://two.example"), urls)
    }

    @Test
    fun `vault password verification rejects blank hash and clears hash when lock disabled`() {
        prefs.edit().putString(PetalRecentlyClosedManager.PREF_LOCK_TYPE, "password").commit()
        // Empty hash must never unlock
        assertFalse(PetalRecentlyClosedManager.verifyPassword(context, "anything"))
        assertFalse(PetalRecentlyClosedManager.verifyPassword(context, ""))

        PetalRecentlyClosedManager.setPassword(context, "1234")
        assertTrue(PetalRecentlyClosedManager.hasPasswordConfigured(context))
        assertTrue(PetalRecentlyClosedManager.verifyPassword(context, "1234"))
        assertFalse(PetalRecentlyClosedManager.verifyPassword(context, "wrong"))

        // Switching to none must remove stored hash so a later password switch cannot reuse the old one
        PetalRecentlyClosedManager.setLockType(context, "none")
        assertFalse(PetalRecentlyClosedManager.hasPasswordConfigured(context))
        assertFalse(PetalRecentlyClosedManager.verifyPassword(context, "1234"))
    }

    @Test
    fun `tab group sync ignores empty open list and preserves protected undo ids`() {
        val t1 = PetalTabItem(id = "t1", title = "One", url = "https://one.example")
        val t2 = PetalTabItem(id = "t2", title = "Two", url = "https://two.example")
        PetalTabGroupManager.init(context)
        val group = PetalTabGroupManager.createGroupWithTabs(context, t1, t2, title = "Docs")
        assertEquals(listOf("t1", "t2"), group.tabIds)

        // Cold-start empty emission must not delete groups
        PetalTabGroupManager.syncWithOpenTabs(context, emptySet())
        assertEquals(1, PetalTabGroupManager.getAllGroups(context).size)

        // Closing t2 optimistically with t2 still protected by undo keeps both tabs in the group
        PetalTabGroupManager.syncWithOpenTabs(context, setOf("t1"), protectedTabIds = setOf("t2"))
        val kept = PetalTabGroupManager.getGroup(context, group.id)
        assertNotNull(kept)
        assertEquals(listOf("t1", "t2"), kept?.tabIds)

        // Once t2 expires from undo, only remaining open tab stays
        PetalTabGroupManager.syncWithOpenTabs(context, setOf("t1"))
        val narrowed = PetalTabGroupManager.getGroup(context, group.id)
        assertEquals(listOf("t1"), narrowed?.tabIds)
    }

    @Test
    fun `createGroupWithTabs with identical tab id does not duplicate entry`() {
        val t1 = PetalTabItem(id = "t1", title = "One", url = "https://one.example")
        PetalTabGroupManager.init(context)
        val group = PetalTabGroupManager.createGroupWithTabs(context, t1, t1, title = "Single")
        assertEquals(listOf("t1"), group.tabIds)
    }

    @Test
    fun `incognito tabs cannot join persistent groups`() {
        val priv = PetalTabItem(id = "p1", title = "Private", url = "https://p.example", isIncognito = true)
        PetalTabGroupManager.init(context)
        val group = PetalTabGroupManager.createGroupWithTab(context, priv)
        assertTrue(group.id.isBlank())
        assertNull(PetalTabGroupManager.findGroupByTabId(context, "p1"))
    }

    @Test
    fun `metadata mute state tracks per tab independently`() {
        PetalTabMetadataStore.setMuted("t_mute", true)
        assertTrue(PetalTabMetadataStore.isMuted("t_mute"))
        PetalTabMetadataStore.setMuted("t_mute", false)
        assertFalse(PetalTabMetadataStore.isMuted("t_mute"))
    }
}
