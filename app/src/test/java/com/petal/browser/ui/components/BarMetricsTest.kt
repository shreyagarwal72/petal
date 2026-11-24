package com.petal.browser.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BarMetricsTest {

    private fun pad(
        bottomAddress: Boolean,
        top: Int = 0, estTop: Int = 200,
        addr: Int = 0, estAddr: Int = 150,
        nav: Int = 0, estNav: Int = 180,
        navVisible: Boolean = true,
        overlay: Int = 100, inset: Int = 40
    ) = BarMetrics.pagePadding(bottomAddress, top, estTop, addr, estAddr, nav, estNav, navVisible, overlay, inset)

    @Test fun topBar_usesMeasuredTop_andNav() {
        val p = pad(false, top = 320, nav = 210)
        assertEquals(320, p.top); assertEquals(210, p.bottom)
    }

    @Test fun topBar_fallsBackToEstimatesBeforeMeasurement() {
        val p = pad(false)
        assertEquals(200, p.top); assertEquals(180, p.bottom)
    }

    @Test fun topBar_navHidden_reservesOnlyGestureInset() {
        val p = pad(false, top = 320, nav = 210, navVisible = false)
        assertEquals(40, p.bottom)
    }

    @Test fun bottomBar_stacksAddressBarAboveNav_andReservesBoth() {
        val p = pad(true, addr = 160, nav = 210)
        assertEquals(100, p.top); assertEquals(370, p.bottom)
    }

    @Test fun bottomBar_navHidden_reservesAddressBarOnly() {
        val p = pad(true, addr = 160, nav = 210, navVisible = false)
        assertEquals(160, p.bottom)
    }

    @Test fun stackedMergeBarIsJustALargerMeasuredHeight() {
        assertEquals(420, pad(false, top = 420).top)
    }
}
