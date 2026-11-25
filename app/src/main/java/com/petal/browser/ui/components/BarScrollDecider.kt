package com.petal.browser.ui.components

import android.os.SystemClock
import kotlin.math.abs

/**
 * The single place that decides whether page scrolling should collapse or expand the bars.
 *
 * It is fed raw Gecko scroll offsets by [com.petal.browser.compose.engine.ComposeEngineViewport] and
 * nothing else. The nested-scroll connection and the legacy tab controller no longer decide anything.
 *
 * Everything here is driven by distances and by the Gecko viewport settle state in
 * [BarCollapseState], never by a clock tuned to one phone:
 *  - Hysteresis: hiding needs more same-direction travel than showing, and reversing a flip right
 *    after it happened needs twice the travel.
 *  - Slop: a reversal smaller than [REVERSAL_SLOP_DP] is treated as jitter and does not reset the run,
 *    so tiny slow-scroll deltas can neither flip the bars nor keep cancelling a real run.
 *  - Resize immunity: while [isSettling] is true every offset is only used to rebase, so the scroll
 *    shift Gecko reports after an actual viewport resize is never read as input.
 *  - Finger gating: an offset change with no finger down and no recent touch is a layout shift, not a
 *    scroll, and is ignored.
 */
class BarScrollDecider(
    private val isSettling: () -> Boolean = { BarCollapseState.isViewportSettling() },
    private val onSettlingScroll: () -> Unit = { BarCollapseState.onGeckoScrollWhileSettling() },
    private val nowMs: () -> Long = { SystemClock.uptimeMillis() }
) {

    companion object {
        const val HIDE_AFTER_DP = 40f
        const val SHOW_AFTER_DP = 24f
        const val REVERSAL_SLOP_DP = 6f
        const val TOP_ZONE_DP = 4f
        /** After a flip, undoing it needs this many times the normal travel... */
        const val REVERSE_DWELL_FACTOR = 2f
        /** ...for this long. Short stability guard against single-frame bounce without blocking quick reversals. */
        const val REVERSE_DWELL_MS = 250L
        /** Offset changes this long after the finger lifted still count as the user's fling. */
        const val FLING_WINDOW_MS = 1500L
    }

    @Volatile private var touching = false
    @Volatile private var lastTouchEndMs = 0L

    fun isFlingCandidate(): Boolean =
        touchTrackingAvailable && !touching && lastTouchEndMs > 0L && (nowMs() - lastTouchEndMs) < FLING_WINDOW_MS

    /** Set once a touch listener is installed. Without one every offset change is trusted. */
    @Volatile var touchTrackingAvailable = false

    private var needsRebase = true
    private var lastY = 0
    private var dir = 0
    private var runStartY = 0
    private var extremumY = 0
    private var lastFlipMs = 0L
    private var lastFlipWasCollapse: Boolean? = null

    fun onTouch(down: Boolean) {
        touching = down
        if (!down) lastTouchEndMs = nowMs()
    }

    fun isTouching(): Boolean = touching

    /** Tab switch, navigation or a new page: the next offset only rebases. */
    fun reset() {
        needsRebase = true
        dir = 0
        lastFlipWasCollapse = null
    }

    private fun restart(y: Int) {
        lastY = y
        dir = 0
        runStartY = y
        extremumY = y
        needsRebase = false
    }

    private fun userDriven(now: Long): Boolean =
        !touchTrackingAvailable || touching || now - lastTouchEndMs < FLING_WINDOW_MS

    /**
     * @return 1 = collapse the bars, -1 = expand them, 0 = leave them as they are.
     */
    fun onScroll(scrollY: Int, density: Float, collapsed: Boolean): Int {
        val now = nowMs()
        if (needsRebase) { restart(scrollY); return 0 }

        val delta = scrollY - lastY
        lastY = scrollY

        // Gecko is absorbing a real viewport resize: the offset is not the user's. Rebase on it.
        if (isSettling()) {
            onSettlingScroll()
            restart(scrollY)
            return 0
        }

        // At the top the bars are always shown.
        if (scrollY <= (TOP_ZONE_DP * density).toInt()) {
            restart(scrollY)
            return if (collapsed) -1 else 0
        }

        // No finger and no fling: a layout shift or programmatic scroll. Never a reason to flip.
        if (!userDriven(now)) { restart(scrollY); return 0 }

        if (delta == 0) return 0

        val moveDir = if (delta > 0) 1 else -1
        val slop = REVERSAL_SLOP_DP * density
        if (dir == 0) {
            dir = moveDir
            runStartY = scrollY - delta
            extremumY = scrollY
        } else if (moveDir == dir) {
            extremumY = if (dir > 0) maxOf(extremumY, scrollY) else minOf(extremumY, scrollY)
        } else if (abs(scrollY - extremumY) > slop) {
            // A real reversal: start a new run from where the previous one peaked.
            dir = moveDir
            runStartY = extremumY
            extremumY = scrollY
        }
        // else: jitter inside the slop, the current run stays alive and unchanged.

        val wantCollapse = dir > 0
        if (wantCollapse == collapsed) return 0

        var needed = (if (wantCollapse) HIDE_AFTER_DP else SHOW_AFTER_DP) * density
        val lastFlip = lastFlipWasCollapse
        if (lastFlip != null && lastFlip != wantCollapse && now - lastFlipMs < REVERSE_DWELL_MS) {
            needed *= REVERSE_DWELL_FACTOR
        }
        if (abs(extremumY - runStartY) >= needed) {
            lastFlipMs = now
            lastFlipWasCollapse = wantCollapse
            restart(scrollY)
            return if (wantCollapse) 1 else -1
        }
        return 0
    }
}
