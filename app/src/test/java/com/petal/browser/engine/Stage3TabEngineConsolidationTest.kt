package com.petal.browser.engine

import com.petal.browser.browser.PetalTabSurface
import com.petal.browser.browser.PlaceholderTabController
import org.junit.Assert.*
import org.junit.Test

/**
 * Stage 3 Verification Suite:
 * Validates the tab/engine consolidation:
 * - Unified PetalTabSurface contract
 * - Placeholder background tab isolation without active session allocation
 * - State and url metadata retrieval
 */
class Stage3TabEngineConsolidationTest {

    @Test
    fun testPlaceholderTabSurface_lightweightContract() {
        val placeholder = PlaceholderTabController(
            null,
            "Example Domain",
            "https://example.com",
            null,
            "tab_12345",
            "group_1",
            "Main Group",
            false
        )

        assertTrue(placeholder is PetalTabSurface)
        assertEquals("Example Domain", placeholder.title)
        assertEquals("https://example.com", placeholder.url)
        assertEquals("tab_12345", placeholder.tabId)
        assertEquals("group_1", placeholder.tabGroupId)
        assertEquals("Main Group", placeholder.tabGroupTitle)
        assertFalse(placeholder.isIncognito)
        assertNotNull(placeholder.tabView)
    }

    @Test
    fun testPrivateTabPlaceholder_incognitoFlagPreserved() {
        val privatePlaceholder = PlaceholderTabController(
            null,
            "Private Window",
            "https://secure.example.com",
            null,
            "private_tab_999",
            null,
            null,
            true
        )

        assertTrue(privatePlaceholder is PetalTabSurface)
        assertTrue(privatePlaceholder.isIncognito)
        assertEquals("private_tab_999", privatePlaceholder.tabId)
    }
}
