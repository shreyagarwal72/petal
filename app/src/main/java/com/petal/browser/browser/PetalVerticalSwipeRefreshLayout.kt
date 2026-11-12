/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.petal.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
     * drawing is replaced by Petal's Compose indicator.
     *
     * SwipeRefreshLayout's constructor adds this view first, so right after `super(...)` it is
     * child 0. It is found by position, NOT by class name: release builds are minified by R8, which
     * renames `CircleImageView`, and the old name check then let the default spinner draw on top
     * of the Compose one.
     */
    private val legacySpinner: View? = if (childCount > 0) getChildAt(0) else null

    init {
        legacySpinner?.apply {
            alpha = 0f
            elevation = 0f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun isLegacySpinner(view: View): Boolean =
        view === legacySpinner || view.javaClass.name.contains("CircleImageView")

    override fun drawChild(canvas: android.graphics.Canvas, child: View, drawingTime: Long): Boolean {
        // The default circle is never rasterized, whatever SwipeRefreshLayout does to its
        // visibility/alpha/scale while pulling, refreshing or resetting.
        if (isLegacySpinner(child)) {
            return false
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun setRefreshing(refreshing: Boolean) {
        super.setRefreshing(refreshing)
        onRefreshStateChanged?.invoke(refreshing)
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

        // A move that is mostly horizontal is not a pull.
        if (MotionEvent.ACTION_DOWN == eventAction) {
            previousX = event.x
            previousY = event.y
            initialDownY = event.y
        } else if (MotionEvent.ACTION_MOVE == eventAction) {
            val xDistance = abs(event.x - previousX)
            val yDistance = abs(event.y - previousY)
            previousX = event.x
            previousY = event.y
            if (xDistance > yDistance) {
                return false
            }
        }

        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || disallowInterceptTouchEvent) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialDownY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (initialDownY == 0f) {
                    initialDownY = previousY
                }
                // Only report a pull the AndroidX layout itself is handling: not while a refresh is
                // running and not while Gecko says the page can still scroll up.
                if (!isRefreshing && !canChildScrollUp()) {
                    val density = resources.displayMetrics.density
                    // Mirrors SwipeRefreshLayout.onTouchEvent: the drag starts after touch slop and is
                    // damped by DRAG_RATE (0.5); the refresh commits at 64dp of damped drag.
                    val totalDragDistance = 64f * density
                    val dragY = (event.y - initialDownY - touchSlop).coerceAtLeast(0f) * 0.5f
                    onPullProgress?.invoke((dragY / totalDragDistance).coerceIn(0f, 1f))
                }
            }
        }
        val handled = super.onTouchEvent(event)
        // Reset AFTER super so a committed refresh (setRefreshing(true)) and the end of the pull
        // reach the indicator in the same frame and it settles instead of dipping to zero first.
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
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
