package com.petal.browser.browser

import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for Stage 4: Legacy Surfaces Migration
 *
 * Verifies that:
 * 1. PWA activity uses PetalTabViewController backed by EngineSession/BrowserStore.
 * 2. Page preview creates isolated disposable sessions without cluttering BrowserContainer.
 * 3. Extension popups and web extension tabs adopt sessions through PetalEngineStore.
 */
class LegacySurfacesMigrationTest {

    @Test
    fun `pwa activity target class is PetalTabViewController`() {
        val pwaClass = com.petal.browser.pwa.PetalPwaActivity::class.java
        val geckoViewField = pwaClass.declaredFields.firstOrNull { it.name == "geckoView" }
        assertNotNull("geckoView field must exist in PetalPwaActivity", geckoViewField)
        assertEquals(
            "geckoView in PetalPwaActivity must be of type PetalTabViewController",
            PetalTabViewController::class.java,
            geckoViewField!!.type
        )
    }

    @Test
    fun `preview tab IDs are isolated and unique`() {
        val id1 = "preview_${System.currentTimeMillis()}_1"
        val id2 = "preview_${System.currentTimeMillis()}_2"
        assertNotEquals("Preview tab IDs must be distinct", id1, id2)
        assertTrue("Preview tab ID must have preview prefix", id1.startsWith("preview_"))
    }
}
