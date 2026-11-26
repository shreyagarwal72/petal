package com.petal.browser.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarScrollAndSettleTest {

    private val density = 3f // 1dp = 3px (xxhdpi)

    private fun px(dp: Float): Int = (dp * density).toInt()

    @Test
    fun decider_requiresFullHideThreshold_andIgnoresTinyJitterInsideSlop() {
        var now = 1000L
        val decider = BarScrollDecider(
            isSettling = { false },
            isBarMoving = { false },
            onSettlingScroll = {},
            nowMs = { now },
        ).apply {
            touchTrackingAvailable = true
        }

        decider.onTouch(true)
        // Rebase past top zone.
        assertEquals(0, decider.onScroll(px(20f), density, collapsed = false))

        // Scroll down 25dp (< 40dp HIDE_AFTER_DP) -> stay expanded.
        assertEquals(0, decider.onScroll(px(45f), density, collapsed = false))

        // Tiny 4dp reversal (< 6dp REVERSAL_SLOP_DP) should be treated as jitter, keeping the run alive.
        assertEquals(0, decider.onScroll(px(41f), density, collapsed = false))

        // Continue scrolling down to reach 40dp total from 20dp -> 60dp triggers collapse.
        assertEquals(1, decider.onScroll(px(61f), density, collapsed = false))
    }

    @Test
    fun decider_realReversalOutsideSlop_resetsRunAndAppliesHysteresis() {
        var now = 1000L
        val decider = BarScrollDecider(
            isSettling = { false },
            isBarMoving = { false },
            onSettlingScroll = {},
            nowMs = { now },
        ).apply {
            touchTrackingAvailable = true
        }

        decider.onTouch(true)
        assertEquals(0, decider.onScroll(px(100f), density, collapsed = true))

        // Scroll up 15dp (to 85dp), then reverse down 10dp (> 6dp slop, to 95dp) -> resets run.
        assertEquals(0, decider.onScroll(px(85f), density, collapsed = true))
        assertEquals(0, decider.onScroll(px(95f), density, collapsed = true))

        // Now move up 18dp from 95dp -> 77dp (< 24dp SHOW_AFTER_DP): still collapsed.
        assertEquals(0, decider.onScroll(px(77f), density, collapsed = true))
        // Move up past 24dp from peak 95dp -> 70dp expands bars.
        assertEquals(-1, decider.onScroll(px(70f), density, collapsed = true))
    }

    @Test
    fun decider_reverseDwell_doublesThresholdImmediatelyAfterFlip_thenRelaxes() {
        var now = 1000L
        val decider = BarScrollDecider(
            isSettling = { false },
            isBarMoving = { false },
            onSettlingScroll = {},
            nowMs = { now },
        ).apply {
            touchTrackingAvailable = true
        }

        decider.onTouch(true)
        assertEquals(0, decider.onScroll(px(50f), density, collapsed = false))
        // Collapse at 50 + 40 = 90dp.
        assertEquals(1, decider.onScroll(px(91f), density, collapsed = false))

        // Immediately (within REVERSE_DWELL_MS = 250ms) reverse upward by 30dp (> 24dp SHOW_AFTER_DP,
        // but < 48dp = 24dp * 2 REVERSE_DWELL_FACTOR): must NOT expand yet.
        now += 50L
        assertEquals(0, decider.onScroll(px(61f), density, collapsed = true))

        // Advance past REVERSE_DWELL_MS -> same scroll direction with >= 24dp now expands.
        now += BarScrollDecider.REVERSE_DWELL_MS
        assertEquals(-1, decider.onScroll(px(60f), density, collapsed = true))
    }

    @Test
    fun decider_flingWindowAcceptsAfterTouchUp_andIgnoresProgrammaticAfterExpiry() {
        var now = 1000L
        val decider = BarScrollDecider(
            isSettling = { false },
            isBarMoving = { false },
            onSettlingScroll = {},
            nowMs = { now },
        ).apply {
            touchTrackingAvailable = true
        }

        decider.onTouch(true)
        assertEquals(0, decider.onScroll(px(20f), density, collapsed = false))
        decider.onTouch(false)

        // Inside FLING_WINDOW_MS (1500ms): momentum scroll still flips bars.
        now += 500L
        assertEquals(1, decider.onScroll(px(65f), density, collapsed = false))

        // Past FLING_WINDOW_MS without touch: scroll offset shift is treated as layout shift and ignored.
        now += BarScrollDecider.FLING_WINDOW_MS + 10L
        assertEquals(0, decider.onScroll(px(20f), density, collapsed = true))
    }

    @Test
    fun decider_topZoneForcesExpand_andResetRebasesNextOffset() {
        var now = 1000L
        val decider = BarScrollDecider(
            isSettling = { false },
            isBarMoving = { false },
            onSettlingScroll = {},
            nowMs = { now },
        ).apply {
            touchTrackingAvailable = true
        }

        decider.onTouch(true)
        assertEquals(0, decider.onScroll(px(100f), density, collapsed = true))
        // Reaching top zone (<= TOP_ZONE_DP = 4dp) always forces bars back open when collapsed.
        assertEquals(-1, decider.onScroll(px(3f), density, collapsed = true))

        // Reset on tab switch: big initial jump should rebase silently without flipping bars.
        decider.reset()
        assertEquals(0, decider.onScroll(px(500f), density, collapsed = false))
        // Only subsequent 40dp downward travel collapses.
        assertEquals(1, decider.onScroll(px(545f), density, collapsed = false))
    }

    @Test
    fun settleTracker_finishesOnLayoutAndScrollEnd_neverExtendedByContinuousScroll() {
        var now = 1000L
        val tracker = ViewportSettleTracker(nowMs = { now })

        tracker.beginSettle()
        assertTrue(tracker.isSettling())

        // Continuous scroll before layout arrives keeps settle active (still waiting for layout).
        now += 30L
        tracker.onGeckoScroll()
        assertTrue(tracker.isSettling())

        // Layout arrives: still settling briefly until scroll-end or POST_LAYOUT_FALLBACK_MS.
        tracker.onLayoutApplied()
        assertTrue(tracker.isSettling())

        // Explicit Gecko scroll-end finishes immediately right after layout.
        tracker.onGeckoScrollEnd()
        assertFalse(tracker.isSettling())

        // Test fallback without explicit scroll-end: continuous scrolling must NOT extend past 80ms after layout.
        now = 2000L
        tracker.beginSettle()
        tracker.onLayoutApplied()
        assertTrue(tracker.isSettling())
        now += 40L
        tracker.onGeckoScroll()
        assertTrue(tracker.isSettling())
        now += 45L // 85ms total since layout (> POST_LAYOUT_FALLBACK_MS = 80ms)
        tracker.onGeckoScroll()
        assertFalse(tracker.isSettling())
    }

    @Test
    fun settleTracker_scrollEndBeforeLayoutFinishesOnLayout_andHardCapBoundsLostCallbacks() {
        var now = 5000L
        val tracker = ViewportSettleTracker(nowMs = { now })

        tracker.beginSettle()
        tracker.onGeckoScrollEnd()
        // Still waits for the layout pass that caused the settle.
        assertTrue(tracker.isSettling())
        tracker.onLayoutApplied()
        assertFalse(tracker.isSettling())

        // Lost layout callback safety net: HARD_CAP_MS stops settling even if nothing arrives.
        now = 6000L
        tracker.beginSettle()
        assertTrue(tracker.isSettling())
        now += ViewportSettleTracker.HARD_CAP_MS
        assertFalse(tracker.isSettling())

        // reset() clears active settle cleanly.
        tracker.beginSettle()
        assertTrue(tracker.isSettling())
        tracker.reset()
        assertFalse(tracker.isSettling())
    }

    @Test
    fun decider_rebasesWhileSettling() {
        var now = 1000L
        val tracker = ViewportSettleTracker(nowMs = { now })
        val decider = BarScrollDecider(
            isSettling = { tracker.isSettling() },
            isBarMoving = { false },
            onSettlingScroll = { tracker.onGeckoScroll() },
            nowMs = { now },
        ).apply {
            touchTrackingAvailable = true
        }

        decider.onTouch(true)
        assertEquals(0, decider.onScroll(px(100f), density, collapsed = false))

        tracker.beginSettle()
        // Big jump during settle (>= 40dp) is absorbed as rebase, not a bar collapse.
        assertEquals(0, decider.onScroll(px(200f), density, collapsed = false))

        tracker.onLayoutApplied()
        now += ViewportSettleTracker.POST_LAYOUT_FALLBACK_MS
        // Settle completes cleanly; next 30dp is < 40dp from rebased 200dp so no spurious flip yet.
        assertEquals(0, decider.onScroll(px(230f), density, collapsed = false))
        // Reaching 242dp collapses normally.
        assertEquals(1, decider.onScroll(px(242f), density, collapsed = false))
    }

    @Test fun offsetShiftWhileBarsMove_isIgnored() {
        var now = 0L
        var moving = false
        val decider = BarScrollDecider(
            isSettling = { false },
            isBarMoving = { moving },
            onSettlingScroll = {},
            nowMs = { now },
        ).apply { touchTrackingAvailable = true; onTouch(true) }
        val d = 3f
        decider.onScroll(1000, d, false)
        var flip = 0
        var y = 1000
        repeat(20) { y += 10; now += 16; flip = decider.onScroll(y, d, false); if (flip != 0) return@repeat }
        // Bars now animating: Gecko reports a large synthetic upward shift. It must not expand the bars.
        moving = true
        now += 16
        assertEquals(0, decider.onScroll(y - 400, d, true))
    }
}
