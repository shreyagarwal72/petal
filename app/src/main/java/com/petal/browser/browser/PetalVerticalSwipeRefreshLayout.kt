/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.petal.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.os.SystemClock
import android.view.ViewConfiguration
import android.view.WindowInsets
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.PetalPullRefreshStyle
import kotlin.math.abs

/**
 * [SwipeRefreshLayout] that only reacts to vertical pulls, the same way Firefox for Android does.
 *
 * This is a port of `mozilla.components.ui.widgets.VerticalSwipeRefreshLayout` (the layout Fenix
 * puts around its engine view). Mozilla's class is `final`, so it cannot be extended by
 * `PetalTabViewController`; the logic is kept identical so Petal behaves like Firefox:
 *
 * - a gesture that moves more sideways than up/down never starts a refresh
 * - a pinch (two fingers) never starts a refresh
 * - a double-tap-and-drag "quick scale" zoom never starts a refresh
 * - nested scrolls coming from the page are ignored. Whether the page is at the top is decided only
 *   by Gecko through `SwipeRefreshFeature`'s child-scroll callback, so the spinner is not drawn
 *   twice and does not appear while the page is still scrolling.
 *
 * Use [View.isEnabled] to switch pull-to-refresh on or off.
 */
open class PetalVerticalSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    /** Callback dispatched during pull gestures with normalized progress in [0f, 1f]. */
    var onPullProgress: ((progress: Float) -> Unit)? = null

    /** Callback dispatched whenever refresh state commits or completes. */
    var onRefreshStateChanged: ((isRefreshing: Boolean) -> Unit)? = null

    /**
     * Tells the layout whether the page is still loading. Used only to recover from a refresh that
     * never got its "finished" signal, which otherwise leaves SwipeRefreshLayout's private
     * `mRefreshing` stuck true and makes every later pull a silent no-op ("works only once").
     */
    var isPageLoading: (() -> Boolean)? = null

    private var refreshCommittedAt = 0L
    private val refreshWatchdog = object : Runnable {
        override fun run() {
            if (!isRefreshing) {
                refreshCommittedAt = 0L
                return
            }
            val elapsed = SystemClock.uptimeMillis() - refreshCommittedAt
            val stillLoading = isPageLoading?.invoke() ?: true
            if (elapsed >= REFRESH_MAX_VISIBLE_MS || (!stillLoading && elapsed >= REFRESH_FINISH_GRACE_MS)) {
                isRefreshing = false
            } else {
                postDelayed(this, REFRESH_POLL_MS)
            }
        }
    }

    private var activeFingerDown = false
    private var pulledPastSlop = false
    private var isQuickScaleInProgress = false
    private var quickScaleEvents = QuickScaleEvents()
    private var previousX = 0f
    private var previousY = 0f
    private var initialDownY = 0f
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()
    private val doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
    private val doubleTapSlopSquare = doubleTapSlop * doubleTapSlop
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var hadMultiTouch = false
    private var disallowInterceptTouchEvent = false

    /**
     * SwipeRefreshLayout's own spinner (its private CircleImageView). The gesture, the Gecko
     * "can the page scroll up" check and the refresh commit stay 100% Mozilla/AndroidX; only its
     * drawing differs per [PetalPullRefreshStyle]:
     *  - every style except FIREFOX: this view is never rasterized and Petal's Compose indicator
     *    draws instead (exactly the historic behavior);
     *  - FIREFOX: this view is the indicator (and the Compose overlay draws nothing).
     *
     * SwipeRefreshLayout's constructor adds this view first, so right after `super(...)` it is
     * child 0. It is found by position, NOT by class name: release builds are minified by R8, which
     * renames `CircleImageView`, and the old name check then let the default spinner draw on top
     * of the Compose one.
     */
    private val legacySpinner: View? = if (childCount > 0) getChildAt(0) else null

    /** True while the style is FIREFOX and [legacySpinner] is the visible indicator. */
    private var nativeSpinnerShown = false

    /** Extra top inset (px) a host can add when its content starts below the layout's own top. */
    var extraTopInsetPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            applyNativeOffset()
        }

    private var nativeArrowColor = 0
    private var nativeDiscColor = 0
    private var hasNativeTheme = false
    private var appliedOffsetStart = Int.MIN_VALUE
    private var appliedOffsetEnd = Int.MIN_VALUE
    private var offsetDirty = false

    // Observes PetalLayoutState (Compose snapshot state) so a style change applies live, even
    // mid-refresh. The executor hops to the main thread through the view.
    private val styleObserver = SnapshotStateObserver { callback -> post { callback() } }
    private val onStyleStateChanged: (PetalVerticalSwipeRefreshLayout) -> Unit = { it.observeStyle() }

    init {
        PetalLayoutState.install(context)
        applyNativeSpinnerShown(PetalLayoutState.settings.pullRefreshStyle == PetalPullRefreshStyle.FIREFOX)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyNativeOffset() }
    }

    private fun observeStyle() {
        var style = PetalPullRefreshStyle.CURRENT
        styleObserver.observeReads(this, onStyleStateChanged) {
            style = PetalLayoutState.settings.pullRefreshStyle
        }
        applyNativeSpinnerShown(style == PetalPullRefreshStyle.FIREFOX)
    }

    /** Cheap, non-observed re-check used at the start of every gesture as a safety net. */
    private fun syncStyleNow() {
        val shouldShow = PetalLayoutState.settings.pullRefreshStyle == PetalPullRefreshStyle.FIREFOX
        if (shouldShow != nativeSpinnerShown) applyNativeSpinnerShown(shouldShow)
    }

    /**
     * Shows exactly one spinner: either this view's own circle (FIREFOX) or none here (the
     * Compose overlay draws). Safe to call at any time, including mid-refresh: it only changes
     * how the circle is drawn, never SwipeRefreshLayout's refreshing state.
     */
    private fun applyNativeSpinnerShown(show: Boolean) {
        nativeSpinnerShown = show
        legacySpinner?.apply {
            // Keep alpha = 1f so ViewGroup.dispatchDraw never short-circuits drawChild (and thus
            // legacy View Animation stepping on mCircleView); visual suppression in custom modes
            // is handled by zero elevation here plus an empty clipRect in drawChild().
            alpha = 1f
            elevation = if (show) 4f * resources.displayMetrics.density else 0f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            invalidate()
        }
        if (show) {
            applyNativeThemeColors()
            applyNativeOffset()
        }
        invalidate()
    }

    /** Colors for the Firefox-style spinner, fed from the live MaterialTheme by the host. */
    fun applyNativeSpinnerTheme(arrowColor: Int, discColor: Int) {
        if (hasNativeTheme && arrowColor == nativeArrowColor && discColor == nativeDiscColor) return
        nativeArrowColor = arrowColor
        nativeDiscColor = discColor
        hasNativeTheme = true
        applyNativeThemeColors()
    }

    private fun applyNativeThemeColors() {
        if (!hasNativeTheme) return
        setColorSchemeColors(nativeArrowColor)
        setProgressBackgroundColorSchemeColor(nativeDiscColor)
    }

    private fun computeTopInsetPx(): Int {
        val bars = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val loc = IntArray(2)
        getLocationInWindow(loc)
        return maxOf(extraTopInsetPx, (bars - loc[1]).coerceAtLeast(0))
    }

    /**
     * Places the FIREFOX spinner below the status bar / top bar. setProgressViewOffset() resets
     * SwipeRefreshLayout's private refreshing flag, so it is only called while idle; otherwise it
     * is deferred to the start of the next gesture.
     */
    private fun applyNativeOffset() {
        if (!nativeSpinnerShown) return
        if (isRefreshing) {
            offsetDirty = true
            return
        }
        val density = resources.displayMetrics.density
        val inset = computeTopInsetPx()
        // scale = true: the disc scales in while it is pulled; it rests 12dp below the inset
        // (same rest line as the Compose glyph styles).
        val start = inset - (8f * density).toInt()
        val end = inset + (12f * density).toInt()
        offsetDirty = false
        if (start == appliedOffsetStart && end == appliedOffsetEnd) return
        appliedOffsetStart = start
        appliedOffsetEnd = end
        setProgressViewOffset(true, start, end)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val result = super.onApplyWindowInsets(insets)
        post { applyNativeOffset() }
        return result
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        styleObserver.start()
        observeStyle()
        post { applyNativeOffset() }
    }

    private fun isLegacySpinner(view: View): Boolean =
        view === legacySpinner || view.javaClass.name.contains("CircleImageView")

    override fun drawChild(canvas: android.graphics.Canvas, child: View, drawingTime: Long): Boolean {
        // Except in FIREFOX mode the default circle is never shown on screen, whatever
        // SwipeRefreshLayout does to its visibility/alpha/scale. We STILL call super.drawChild
        // inside an empty clip so SwipeRefreshLayout's mCircleView animations advance and
        // mRefreshListener.onAnimationEnd() -> SwipeRefreshFeature.onRefresh() fires reliably.
        if (!nativeSpinnerShown && isLegacySpinner(child)) {
            val save = canvas.save()
            canvas.clipRect(0, 0, 0, 0)
            val drawn = super.drawChild(canvas, child, drawingTime)
            canvas.restoreToCount(save)
            return drawn
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun setRefreshing(refreshing: Boolean) {
        if (!refreshing) {
            pulledPastSlop = false
            onPullProgress?.invoke(0f)
        }
        super.setRefreshing(refreshing)
        if (refreshing) markRefreshCommitted() else clearRefreshCommitted()
        onRefreshStateChanged?.invoke(refreshing)
    }

    /** Immediately clears any active pull or committed spinner state once loading finishes. */
    fun forceStopRefresh() {
        pulledPastSlop = false
        activeFingerDown = false
        initialDownY = 0f
        if (isRefreshing) {
            super.setRefreshing(false)
        }
        clearRefreshCommitted()
        onPullProgress?.invoke(0f)
        onRefreshStateChanged?.invoke(false)
    }

    private fun markRefreshCommitted() {
        if (refreshCommittedAt == 0L) {
            refreshCommittedAt = SystemClock.uptimeMillis()
            removeCallbacks(refreshWatchdog)
            postDelayed(refreshWatchdog, REFRESH_FINISH_GRACE_MS)
        }
    }

    private fun clearRefreshCommitted() {
        refreshCommittedAt = 0L
        removeCallbacks(refreshWatchdog)
    }

    /** A new touch while a finished/never-started reload still holds the spinner: release it now. */
    private fun healStuckRefresh() {
        if (!isRefreshing) return
        if (isPageLoading?.invoke() == false) {
            forceStopRefresh()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            // requestDisallowInterceptTouchEvent() is swallowed below, so the framework's per-gesture
            // reset of its disallow flag never applies to ours. Without this reset one page-handled
            // touch disabled pull-to-refresh for the rest of the session.
            disallowInterceptTouchEvent = false
            activeFingerDown = true
            pulledPastSlop = false
            initialDownY = ev.y
            healStuckRefresh()
            syncStyleNow()
            if (offsetDirty && !isRefreshing) applyNativeOffset()
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeFingerDown = false
            disallowInterceptTouchEvent = false
        }
        return handled
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshWatchdog)
        styleObserver.stop()
        styleObserver.clear()
        super.onDetachedFromWindow()
    }

    @Suppress("ReturnCount")
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // When pull-to-refresh is disabled (full screen, PDF, ...) skip all the work below.
        if (!isEnabled || disallowInterceptTouchEvent) {
            return false
        }

        if (MotionEvent.ACTION_DOWN == event.action) {
            hadMultiTouch = false
        }

        // Cheap two-finger (pinch) detector: once a second finger is seen the whole gesture is
        // treated as a scale gesture and never refreshes.
        if (event.pointerCount > 1 || hadMultiTouch) {
            hadMultiTouch = true
            return false
        }

        val eventAction = event.action

        // Gesture aborted, or a quick-scale just ended: clean up.
        if (MotionEvent.ACTION_CANCEL == eventAction ||
            (MotionEvent.ACTION_UP == eventAction && isQuickScaleInProgress)
        ) {
            forgetQuickScaleEvents()
            initialDownY = 0f
            onPullProgress?.invoke(0f)
            return super.onInterceptTouchEvent(event)
        }

        // Quick scale (double tap, then drag) is a zoom, not a refresh.
        maybeAddDoubleTapEvent(event)
        if (isQuickScaleInProgress(quickScaleEvents)) {
            isQuickScaleInProgress = true
            return false
        }

        // A move that is mostly horizontal is not a pull, and only a genuine downward pull
        // starting at the top past touchSlop (never a fling to the top) may trigger refresh.
        if (MotionEvent.ACTION_DOWN == eventAction) {
            activeFingerDown = true
            pulledPastSlop = false
            previousX = event.x
            previousY = event.y
            initialDownY = event.y
        } else if (MotionEvent.ACTION_MOVE == eventAction) {
            if (!activeFingerDown || com.petal.browser.ui.glass.GeckoGlassBackend.isFlinging) {
                pulledPastSlop = false
                onPullProgress?.invoke(0f)
                return false
            }
            val xDistance = abs(event.x - previousX)
            val yDistance = abs(event.y - previousY)
            previousX = event.x
            previousY = event.y
            if (xDistance > yDistance) {
                return false
            }
            if (canChildScrollUp()) {
                // Re-anchor while the page itself is still scrolling toward the top so prior drag
                // distance cannot spill over into an accidental pull-to-refresh once it hits top.
                initialDownY = event.y
                pulledPastSlop = false
                if (!isRefreshing) onPullProgress?.invoke(0f)
                return false
            }
            val totalDy = event.y - initialDownY
            if (totalDy <= touchSlop) {
                pulledPastSlop = false
                return false
            }
            pulledPastSlop = true
        }

        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || disallowInterceptTouchEvent) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeFingerDown = true
                pulledPastSlop = false
                initialDownY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (initialDownY == 0f) {
                    initialDownY = event.y
                }
                if (!activeFingerDown || com.petal.browser.ui.glass.GeckoGlassBackend.isFlinging || canChildScrollUp()) {
                    pulledPastSlop = false
                    if (!isRefreshing) onPullProgress?.invoke(0f)
                } else {
                    val rawDelta = event.y - initialDownY - touchSlop
                    if (rawDelta > 0f) pulledPastSlop = true
                    // Only report a pull the AndroidX layout itself is handling after passing slop.
                    if (!isRefreshing && pulledPastSlop) {
                        val density = resources.displayMetrics.density
                        val totalDragDistance = 64f * density
                        val dragY = rawDelta.coerceAtLeast(0f) * 0.5f
                        onPullProgress?.invoke((dragY / totalDragDistance).coerceIn(0f, 1f))
                    } else if (!isRefreshing) {
                        onPullProgress?.invoke(0f)
                    }
                }
            }
        }
        val wasRefreshing = isRefreshing
        val handled = super.onTouchEvent(event)
        // A pull that crosses the threshold calls SwipeRefreshLayout's private setRefreshing(true, true),
        // which bypasses the override above: tell the indicator and arm the watchdog ourselves.
        if (!wasRefreshing && isRefreshing) {
            if (!pulledPastSlop || com.petal.browser.ui.glass.GeckoGlassBackend.isFlinging) {
                isRefreshing = false
            } else {
                markRefreshCommitted()
                onRefreshStateChanged?.invoke(true)
            }
        }
        // Reset AFTER super so a committed refresh (setRefreshing(true)) and the end of the pull
        // reach the indicator in the same frame and it settles instead of dipping to zero first.
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeFingerDown = false
            pulledPastSlop = false
            initialDownY = 0f
            onPullProgress?.invoke(0f)
        }
        return handled
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        // Ignore nested scrolls from the page. Accepting them would make every vertical movement
        // (including zoom gestures) refresh the page and double the spinner with the overscroll glow.
        return if (isEnabled) {
            false
        } else {
            super.onStartNestedScroll(child, target, nestedScrollAxes)
        }
    }

    // Matches Mozilla: the request is swallowed instead of being passed to the parent, because the
    // parent may use the gesture for something else (toolbar collapsing, predictive back, ...).
    override fun requestDisallowInterceptTouchEvent(b: Boolean) {
        this.disallowInterceptTouchEvent = b
    }

    @SuppressLint("Recycle") // The events are recycled in forgetQuickScaleEvents().
    private fun maybeAddDoubleTapEvent(event: MotionEvent) {
        val currentEventAction = event.action

        // A double tap is ACTION_DOWN, ACTION_UP, ACTION_DOWN inside the system double-tap timeout.
        if (MotionEvent.ACTION_DOWN == currentEventAction) {
            val up = quickScaleEvents.upEvent
            if (up != null) {
                if (event.eventTime - up.eventTime > doubleTapTimeout) {
                    // Too slow to be a quick-scale gesture: start counting again.
                    forgetQuickScaleEvents()
                    quickScaleEvents.firstDownEvent = MotionEvent.obtain(event)
                } else {
                    quickScaleEvents.secondDownEvent = MotionEvent.obtain(event)
                }
            } else {
                // First touch, or the previous gesture did not end with ACTION_UP.
                forgetQuickScaleEvents()
                quickScaleEvents.firstDownEvent = MotionEvent.obtain(event)
            }
        } else if (MotionEvent.ACTION_UP == currentEventAction && quickScaleEvents.firstDownEvent != null) {
            quickScaleEvents.upEvent = MotionEvent.obtain(event)
        }
    }

    private fun forgetQuickScaleEvents() {
        quickScaleEvents.firstDownEvent?.recycle()
        quickScaleEvents.upEvent?.recycle()
        quickScaleEvents.secondDownEvent?.recycle()
        quickScaleEvents.firstDownEvent = null
        quickScaleEvents.upEvent = null
        quickScaleEvents.secondDownEvent = null

        isQuickScaleInProgress = false
    }

    private fun isQuickScaleInProgress(events: QuickScaleEvents): Boolean {
        val firstDown = events.firstDownEvent
        val firstUp = events.upEvent
        val secondDown = events.secondDownEvent
        return if (firstDown != null && firstUp != null && secondDown != null) {
            isQuickScaleInProgress(firstDown, firstUp, secondDown)
        } else {
            false
        }
    }

    // Closely follows GestureDetectorCompat#isConsideredDoubleTap, evaluated event by event
    // instead of through callbacks.
    private fun isQuickScaleInProgress(
        firstDown: MotionEvent,
        firstUp: MotionEvent,
        secondDown: MotionEvent
    ): Boolean {
        if (secondDown.eventTime - firstUp.eventTime > doubleTapTimeout) {
            return false
        }

        val deltaX = firstDown.x.toInt() - secondDown.x.toInt()
        val deltaY = firstDown.y.toInt() - secondDown.y.toInt()

        return deltaX * deltaX + deltaY * deltaY < doubleTapSlopSquare
    }

    /** The MotionEvents that make up a quick-scale gesture. */
    private data class QuickScaleEvents(
        var firstDownEvent: MotionEvent? = null,
        var upEvent: MotionEvent? = null,
        var secondDownEvent: MotionEvent? = null
    )
}

private const val REFRESH_FINISH_GRACE_MS = 500L
private const val REFRESH_POLL_MS = 500L
private const val REFRESH_MAX_VISIBLE_MS = 20_000L
