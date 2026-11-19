package com.petal.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source-level guards for the crash/history/runtime fixes (no device needed). */
class WebsiteStabilityWiringTest {

    private fun src(path: String): String {
        val base = "src/main/java/com/petal/browser/"
        val f = File(base + path).takeIf { it.exists() } ?: File("app/$base$path")
        assertTrue("$path must exist", f.exists())
        return f.readText()
    }

    @Test
    fun glassHeaderLayoutNeverForcesInfiniteContentHeight() {
        val t = src("ui/components/ExpressiveHeader.kt")
        assertTrue(t.contains("hasBoundedHeight"))
        assertFalse(t.contains("Constraints(w, w, contentHeight, contentHeight))\n        layout(w, constraints.maxHeight)"))
    }

    @Test
    fun historyUsesMozillaDelegateAndRecordsEachVisitOnce() {
        assertTrue(src("engine/gecko/PetalEngineStore.kt").contains("feature.session.HistoryDelegate"))
        val tab = src("browser/PetalTabViewController.kt")
        assertTrue(tab.contains("engineDelegate.onVisited("))
        assertFalse(tab.contains("recordHistoryVisit(pageUrl, pageTitle)"))
        assertFalse(tab.contains("recordHistoryVisit(pageUrl, title, force = true)"))
    }

    @Test
    fun sessionHistoryClearHitsPlaces() {
        assertTrue(src("unit/PetalSessionHistoryManager.kt").contains("PetalPlacesProvider.deleteVisitsSince"))
    }

    @Test
    fun contentTrackingOnlyUnderStrictAndAboutConfigDebugOnly() {
        val t = src("engine/gecko/PetalGeckoRuntime.kt")
        assertTrue(t.contains(".aboutConfigEnabled(isDebug)"))
        assertFalse(t.contains("ContentBlocking.AntiTracking.CONTENT or ContentBlocking.AntiTracking.STP"))
    }
}
