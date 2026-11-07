package com.petal.browser.browser

import com.petal.browser.engine.gecko.PetalEngineBridge
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for Stage 5: Legacy Removal Verification
 *
 * Verifies that:
 * 1. PetalGeckoView and BrowserWebViewController have been completely deleted.
 * 2. PetalEngineBridge provides the extracted static utilities (profile, user-agent, browser controller ref).
 * 3. PetalTabViewController exposes the complete consolidated tab contract (clearWebSelection, loadDataWithBaseURL, etc.).
 * 4. BrowserActivity has removed legacy activeGeckoView static references and PetalGeckoView branches.
 */
class LegacyRemovalVerificationTest {

    @Test
    fun `legacy PetalGeckoView is deleted from codebase`() {
        var classFound = false
        try {
            Class.forName("com.petal.browser.view.PetalGeckoView")
            classFound = true
        } catch (_: ClassNotFoundException) {
            classFound = false
        }
        assertFalse("PetalGeckoView must be completely deleted from codebase", classFound)
    }

    @Test
    fun `legacy BrowserWebViewController is deleted from codebase`() {
        var classFound = false
        try {
            Class.forName("com.petal.browser.controller.BrowserWebViewController")
            classFound = true
        } catch (_: ClassNotFoundException) {
            classFound = false
        }
        assertFalse("BrowserWebViewController must be completely deleted from codebase", classFound)
    }

    @Test
    fun `petal engine bridge provides extracted desktop user agent`() {
        val mockContext = mock<android.content.Context>()
        val ua = PetalEngineBridge.getDerivedDesktopUserAgent(mockContext)
        assertTrue("Desktop user agent must contain Gecko/Firefox token", ua.contains("Gecko/") || ua.contains("Firefox/"))
        assertTrue("Desktop user agent must declare desktop platform", ua.contains("X11") || ua.contains("Linux"))
    }

    @Test
    fun `petal engine bridge manages browser controller reference`() {
        val mockController = mock<BrowserController>()
        PetalEngineBridge.setBrowserController(mockController)
        assertSame("BrowserController must be retrievable from PetalEngineBridge", mockController, PetalEngineBridge.getBrowserController())
        PetalEngineBridge.setBrowserController(null)
        assertNull("BrowserController reference must be clearable", PetalEngineBridge.getBrowserController())
    }

    @Test
    fun `petal tab view controller declares required consolidated methods`() {
        val tabClass = PetalTabViewController::class.java
        val methodNames = tabClass.declaredMethods.map { it.name }.toSet()

        assertTrue("Must declare loadDataWithBaseURL", methodNames.contains("loadDataWithBaseURL"))
        assertTrue("Must declare clearWebSelection", methodNames.contains("clearWebSelection"))
        assertTrue("Must declare canClearSelection", methodNames.contains("canClearSelection"))
        assertTrue("Must declare captureVideoHandoffState", methodNames.contains("captureVideoHandoffState"))
        assertTrue("Must declare setProfileChanged", methodNames.contains("setProfileChanged"))
        assertTrue("Must declare reloadBypassCache", methodNames.contains("reloadBypassCache"))
    }

    @Test
    fun `browser activity no longer declares activeGeckoView of legacy type`() {
        val actClass = com.petal.browser.activity.BrowserActivity::class.java
        val field = actClass.declaredFields.firstOrNull { it.name == "activeGeckoView" }
        assertNull("activeGeckoView field must be removed from BrowserActivity", field)
    }
}
