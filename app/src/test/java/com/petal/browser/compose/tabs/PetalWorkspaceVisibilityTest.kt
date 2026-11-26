package com.petal.browser.compose.tabs

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.petal.browser.browser.PetalTabMetadataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetalWorkspaceVisibilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences("petal_workspaces_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        PetalTabMetadataStore.clear(context)
        PetalWorkspaceManager.resetForTest()
    }

    @After
    fun tearDown() {
        PetalWorkspaceManager.resetForTest()
    }

    @Test
    fun `migration keeps workspace chips off on fresh install`() {
        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)

        val defaultSp = PreferenceManager.getDefaultSharedPreferences(context)
        assertFalse(defaultSp.getBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))
        assertTrue(defaultSp.getBoolean(PetalWorkspaceManager.PREF_WORKSPACE_CHIPS_MIGRATED, false))
    }

    @Test
    fun `migration enables workspace chips when user already created a custom workspace`() {
        PetalWorkspaceManager.createWorkspace(context, "Gaming", "Work", "#10B981")

        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)

        assertTrue(PetalWorkspaceManager.isWorkspaceChipsEnabled(context))
    }

    @Test
    fun `migration enables workspace chips when any tab is assigned to a non-default workspace`() {
        PetalTabMetadataStore.setWorkspace(context, "tab-work", "ws_work")

        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)

        assertTrue(PetalWorkspaceManager.isWorkspaceChipsEnabled(context))
    }

    @Test
    fun `migration never overrides an explicit user preference later or beforehand`() {
        val defaultSp = PreferenceManager.getDefaultSharedPreferences(context)
        // Explicit OFF before migration even with custom workspace persisted:
        PetalWorkspaceManager.createWorkspace(context, "Custom", "Home", "#3B82F6")
        defaultSp.edit().putBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false).commit()

        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)
        assertFalse(PetalWorkspaceManager.isWorkspaceChipsEnabled(context))

        // Reset custom state, flip ON, verify re-running migration does not flip OFF:
        defaultSp.edit().putBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, true).commit()
        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)
        assertTrue(PetalWorkspaceManager.isWorkspaceChipsEnabled(context))

        // After migration completed, even if user flips to OFF and migration runs again, stays OFF:
        defaultSp.edit().putBoolean(PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false).commit()
        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)
        assertFalse(PetalWorkspaceManager.isWorkspaceChipsEnabled(context))
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

        // When workspace chips are OFF, all regular non-incognito tabs appear together regardless of workspaceId.
        val whenOff = PetalWorkspaceManager.filterRegularTabs(
            tabs = tabs,
            activeWorkspaceId = "ws_work",
            showWorkspaceChips = false,
        )
        assertEquals(listOf("t1", "t2", "t3", "t4"), whenOff.map { it.id })

        // When workspace chips are ON for ws_work, only ws_work + global pinned tabs appear.
        val whenOnWork = PetalWorkspaceManager.filterRegularTabs(
            tabs = tabs,
            activeWorkspaceId = "ws_work",
            showWorkspaceChips = true,
        )
        assertEquals(listOf("t3", "t4"), whenOnWork.map { it.id })

        // When workspace chips are ON for General (default), default/null + global pinned tabs appear.
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

    @Test
    fun `persisted custom workspaces array parses cleanly after migration flag`() {
        val wsPrefs = context.getSharedPreferences("petal_workspaces_prefs", Context.MODE_PRIVATE)
        val customOnly = listOf(
            PetalWorkspace(PetalWorkspaceManager.DEFAULT_WORKSPACE_ID, "General", "Home", "#3B82F6", 0, true),
            PetalWorkspace("ws_custom1", "Study", "School", "#F59E0B", 1, false),
        )
        wsPrefs.edit().putString("workspaces_json", Gson().toJson(customOnly)).commit()

        PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)
        assertTrue(PetalWorkspaceManager.isWorkspaceChipsEnabled(context))
    }
}
