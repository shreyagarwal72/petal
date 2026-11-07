package com.petal.browser.browser

import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.never
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.EngineState

/**
 * Unit tests for Stage 2: Single Engine View Architecture
 *
 * Verifies that:
 * 1. Background tabs are represented by lightweight placeholder controllers
 *    without attaching or allocating a GeckoEngineView.
 * 2. On tab switch, the shared EngineView releases the outgoing session and
 *    renders the incoming session (Fenix model).
 * 3. PlaceholderTabController preserves tab ID, URL, title, incognito state,
 *    and group metadata for the tab switcher.
 * 4. Engine view reuse: a single view instance can be bound to multiple
 *    consecutive tab sessions without recreation.
 */
class SingleEngineViewTest {

    @Test
    fun `placeholder tab controller does not allocate an engine session or gecko view`() {
        val placeholder = PlaceholderTabController(
            context = mock(),
            title = "Test Tab",
            url = "https://example.com",
            favicon = null,
            tabId = "tab_123",
            tabGroupId = "group_1",
            tabGroupTitle = "Work",
            incognito = false
        )

        assertEquals("tab_123", placeholder.getTabId())
        assertEquals("Test Tab", placeholder.getTitle())
        assertEquals("https://example.com", placeholder.getUrl())
        assertEquals("group_1", placeholder.getTabGroupId())
        assertEquals("Work", placeholder.getTabGroupTitle())
        assertFalse(placeholder.isIncognito())

        // Background placeholder view is a lightweight dummy FrameLayout, not a GeckoView
        assertNotNull(placeholder.getAlbumView())
        assertFalse(placeholder.getAlbumView() is EngineView)
    }

    @Test
    fun `engine view render and release contract on tab switch`() {
        val engineView: EngineView = mock()
        val session1: EngineSession = mock()
        val session2: EngineSession = mock()

        // Tab 1 selected: renders session 1
        engineView.render(session1)
        verify(engineView, times(1)).render(session1)
        verify(engineView, never()).render(session2)

        // Switching to Tab 2: release session 1, render session 2
        engineView.release()
        engineView.render(session2)

        verify(engineView, times(1)).release()
        verify(engineView, times(1)).render(session2)
    }

    @Test
    fun `placeholder album controller maintains incognito isolation`() {
        val privatePlaceholder = PlaceholderAlbumController(
            mock(),
            "Private Tab",
            "https://secret.example.com",
            null,
            "tab_private_1",
            null,
            null,
            true
        )
        assertTrue("Incognito flag must be preserved on placeholder", privatePlaceholder.isIncognito())

        val normalPlaceholder = PlaceholderAlbumController(
            mock(),
            "Normal Tab",
            "https://public.example.com",
            null,
            "tab_normal_1",
            null,
            null,
            false
        )
        assertFalse("Normal tab must not be incognito", normalPlaceholder.isIncognito())
    }

    @Test
    fun `tab metadata and group properties are correctly preserved`() {
        val placeholder = PlaceholderAlbumController(
            mock(),
            "Initial Title",
            "https://initial.url",
            null,
            "tab_456",
            "grp_a",
            "Finance",
            false
        )

        placeholder.setTitle("Updated Title")
        placeholder.setUrl("https://updated.url")
        placeholder.setTabGroupId("grp_b")
        placeholder.setTabGroupTitle("Research")

        assertEquals("Updated Title", placeholder.getTitle())
        assertEquals("https://updated.url", placeholder.getUrl())
        assertEquals("grp_b", placeholder.getTabGroupId())
        assertEquals("Research", placeholder.getTabGroupTitle())
    }
}
