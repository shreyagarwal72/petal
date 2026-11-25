package com.petal.browser.now

import androidx.compose.ui.geometry.Rect
import com.petal.browser.live.LiveEventCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetalNowMapperTest {

    @Test
    fun `settings enum parsing preserves defaults and legacy ease_in_out`() {
        assertEquals(PetalNowMode.AUTO, PetalNowMode.fromPref(null))
        assertEquals(PetalNowMode.AUTO, PetalNowMode.fromPref("AUTO"))
        assertEquals(PetalNowMode.ALWAYS, PetalNowMode.fromPref("ALWAYS"))
        assertEquals(PetalNowMode.OFF, PetalNowMode.fromPref("OFF"))

        assertEquals(PetalNowAnimStyle.EXPRESSIVE, PetalNowAnimStyle.fromPref(null))
        assertEquals(PetalNowAnimStyle.CALM, PetalNowAnimStyle.fromPref("EASE_IN_OUT"))
        assertEquals(PetalNowAnimStyle.CALM, PetalNowAnimStyle.fromPref("CALM"))

        assertEquals(PetalNowAnimSpeed.SLOW, PetalNowAnimSpeed.fromPref("SLOW"))
        assertEquals(PetalNowAnimSpeed.DEFAULT, PetalNowAnimSpeed.fromPref("UNKNOWN"))
        assertEquals(PetalNowAnimSpeed.FAST, PetalNowAnimSpeed.fromPref("FAST"))

        assertEquals(PetalNowAnimBounce.SMALL, PetalNowAnimBounce.fromPref("SMALL"))
        assertEquals(PetalNowAnimBounce.NORMAL, PetalNowAnimBounce.fromPref(null))
        assertEquals(PetalNowAnimBounce.BIG, PetalNowAnimBounce.fromPref("BIG"))
    }

    @Test
    fun `category filter requires both master switch and per category switch`() {
        val prefsMasterOff = PetalNowPrefs(eventsMaster = false)
        assertFalse(prefsMasterOff.isCategoryOn(LiveEventCategory.ADBLOCK))

        val prefsAdblockOff = PetalNowPrefs(
            eventsMaster = true,
            categoryEnabled = LiveEventCategory.entries.associateWith { it != LiveEventCategory.ADBLOCK },
        )
        assertFalse(prefsAdblockOff.isCategoryOn(LiveEventCategory.ADBLOCK))
        assertTrue(prefsAdblockOff.isCategoryOn(LiveEventCategory.DOWNLOADS))
    }

    @Test
    fun `anchor returns null when invisible and exact center offset when visible`() {
        PetalNowAnchor.clear()
        val sheetRect = Rect(0f, 1400f, 1080f, 2200f)
        assertNull(PetalNowAnchor.computeMorphDelta(sheetRect))

        PetalNowAnchor.update(Rect(400f, 80f, 680f, 160f), visible = true)
        val delta = PetalNowAnchor.computeMorphDelta(sheetRect)
        assertNotNull(delta)
        assertEquals(0f, delta!!.x, 0.01f)
        assertEquals(-1680f, delta.y, 0.01f)
        PetalNowAnchor.clear()
    }

    @Test
    fun `page context extracts clean host without www`() {
        val ctx = PetalNowPageContext(
            activeUrl = "https://www.Example.com/path?a=1",
            isHome = false,
            globalAdBlockEnabled = true,
            adBlockRevision = 1L,
        )
        assertEquals("example.com", ctx.host)

        val homeCtx = PetalNowPageContext(
            activeUrl = "about:home",
            isHome = true,
            globalAdBlockEnabled = true,
            adBlockRevision = 1L,
        )
        assertNull(homeCtx.host)
    }
}
