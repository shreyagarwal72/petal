package com.petal.browser.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarCollapseMathTest {
    @Test fun hide_slidesByFraction_andNeverShowsPill() {
        val v = BarCollapseMath.compute(BarCollapseStyle.HIDE, 0.5f, isBottom = false)
        assertEquals(0.5f, v.slideFraction, 0f); assertFalse(v.showPill); assertTrue(v.barVisible)
        assertFalse(BarCollapseMath.compute(BarCollapseStyle.HIDE, 1f, true).barVisible)
    }

    @Test fun pill_fadesBarAndShowsPill() {
        val open = BarCollapseMath.compute(BarCollapseStyle.PILL, 0f, true)
        assertEquals(1f, open.fullBarAlpha, 0f); assertFalse(open.showPill)
        val closed = BarCollapseMath.compute(BarCollapseStyle.PILL, 1f, true)
        assertFalse(closed.barVisible); assertTrue(closed.showPill); assertEquals(0f, closed.slideFraction, 0f)
    }

    @Test fun pill_disjointHandoff_andSyncedNav() {
        val atHandoff = BarCollapseMath.compute(BarCollapseStyle.PILL, BarCollapseMath.PILL_HANDOFF, false)
        assertFalse(atHandoff.barVisible)
        assertFalse(atHandoff.showPill)
        assertFalse(atHandoff.navVisible)

        val midBar = BarCollapseMath.compute(BarCollapseStyle.PILL, 0.21f, true)
        assertTrue(midBar.barVisible)
        assertFalse(midBar.showPill)
        assertEquals(midBar.fullBarAlpha, midBar.navAlpha, 0.0001f)
        assertEquals(midBar.fullBarAlpha, midBar.sideButtonAlpha, 0.0001f)

        val midPill = BarCollapseMath.compute(BarCollapseStyle.PILL, 0.70f, true)
        assertFalse(midPill.barVisible)
        assertTrue(midPill.showPill)
    }

    @Test fun pill_skipsVisibilityBelow005Alpha() {
        // Near PILL_HANDOFF where fullAlpha drops below 0.05f, both barVisible and navVisible are false.
        val justBelow = BarCollapseMath.compute(BarCollapseStyle.PILL, 0.405f, isBottom = false)
        assertTrue(justBelow.fullBarAlpha < 0.05f)
        assertFalse(justBelow.barVisible)
        assertFalse(justBelow.navVisible)
    }

    @Test fun viewportSettleOnlyActiveInLegacyPaddingMode() {
        BarCollapseState.uptimeMillisProvider = { 5000L }
        try {
            BarCollapseState.legacyPaddingViewport = false
            BarCollapseState.updateCollapsed(false)
            BarCollapseState.beginViewportCollapse()
            assertFalse(BarCollapseState.isViewportSettling())

            BarCollapseState.legacyPaddingViewport = true
            BarCollapseState.updateCollapsed(false)
            BarCollapseState.beginViewportCollapse()
            assertTrue(BarCollapseState.isViewportSettling())
        } finally {
            BarCollapseState.legacyPaddingViewport = false
            BarCollapseState.updateCollapsed(false)
            BarCollapseState.uptimeMillisProvider = { android.os.SystemClock.uptimeMillis() }
        }
    }

    @Test fun scrollDeciderDetectsFlingCandidateAfterTouchRelease() {
        var now = 1000L
        val decider = BarScrollDecider(nowMs = { now })
        decider.touchTrackingAvailable = true
        decider.onTouch(true)
        assertFalse(decider.isFlingCandidate())
        decider.onTouch(false)
        assertTrue(decider.isFlingCandidate())
        now += BarScrollDecider.FLING_WINDOW_MS + 10L
        assertFalse(decider.isFlingCandidate())
    }
}
