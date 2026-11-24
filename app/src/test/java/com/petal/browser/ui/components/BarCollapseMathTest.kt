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
}
