package com.petal.browser.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.OverScroller
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs

/**
 * Custom NestedScrollWebView supporting:
 * - NestedScrollingChild3 interface implementation
 * - Hardware acceleration (LAYER_TYPE_HARDWARE) for 120Hz scrolling
 * - Custom fling physics via OverScroller with custom deceleration friction
 * - Gesture arbitration preventing horizontal swipe navigation conflicts
 */
open class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr), NestedScrollingChild3 {

    private val childHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private val nestedOffsets = IntArray(2)

    private var lastMotionY: Int = 0
    private var lastMotionX: Int = 0
    private var initialDownX: Int = 0
    private var initialDownY: Int = 0
    private var activePointerId: Int = INVALID_POINTER

    private var velocityTracker: VelocityTracker? = null
    private var touchSlop: Int = 0
    private var minimumVelocity: Int = 0
    private var maximumVelocity: Int = 0
    private var customScroller: OverScroller? = null
    private var isBeingDragged: Boolean = false

    init {
        isNestedScrollingEnabled = true
        initSmoothScrolling(context)
    }

    private fun initSmoothScrolling(context: Context) {
        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        minimumVelocity = configuration.scaledMinimumFlingVelocity
        maximumVelocity = configuration.scaledMaximumFlingVelocity

        // High refresh rate (90Hz / 120Hz / 144Hz) rendering pipeline
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER

        customScroller = OverScroller(context)
        customScroller?.setFriction(0.0085f)
        com.petal.browser.unit.PetalHighRefreshRateManager.applySurfaceFrameRate(this)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }

        val vtev = MotionEvent.obtain(event)
        val action = event.actionMasked
        val actionIndex = event.actionIndex

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsets[0] = 0
            nestedOffsets[1] = 0
        }
        vtev.offsetLocation(nestedOffsets[0].toFloat(), nestedOffsets[1].toFloat())

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                initialDownX = (event.x + 0.5f).toInt()
                initialDownY = (event.y + 0.5f).toInt()
                lastMotionX = initialDownX
                lastMotionY = initialDownY
                isBeingDragged = false

                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                velocityTracker?.addMovement(vtev)
                super.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    vtev.recycle()
                    return false
                }

                val x = (event.getX(pointerIndex) + 0.5f).toInt()
                val y = (event.getY(pointerIndex) + 0.5f).toInt()
                var dx = lastMotionX - x
                var dy = lastMotionY - y

                if (!isBeingDragged) {
                    val deltaX = abs(x - initialDownX)
                    val deltaY = abs(y - initialDownY)

                    // Horizontal gesture arbitration: If moving mostly sideways, yield to horizontal gesture navigation
                    if (deltaX > touchSlop && deltaX > deltaY * 1.35f) {
                        vtev.recycle()
                        return super.onTouchEvent(event)
                    }

                    if (deltaY > touchSlop) {
                        isBeingDragged = true
                        dy = if (dy > 0) dy - touchSlop else dy + touchSlop
                    }
                }

                if (isBeingDragged) {
                    if (dispatchNestedPreScroll(dx, dy, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                        dy -= scrollConsumed[1]
                        dx -= scrollConsumed[0]
                        vtev.offsetLocation(scrollOffset[0].toFloat(), scrollOffset[1].toFloat())
                        nestedOffsets[0] += scrollOffset[0]
                        nestedOffsets[1] += scrollOffset[1]
                    }

                    lastMotionX = x - scrollOffset[0]
                    lastMotionY = y - scrollOffset[1]

                    val oldY = scrollY
                    val maxScrollY = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)

                    // Clamp standard vertical scrolling
                    val newY = (oldY + dy).coerceIn(0, maxScrollY)
                    val unconsumedY = dy - (newY - oldY)

                    if (dispatchNestedScroll(0, newY - oldY, 0, unconsumedY, scrollOffset, ViewCompat.TYPE_TOUCH, scrollConsumed)) {
                        lastMotionY -= scrollOffset[1]
                        lastMotionX -= scrollOffset[0]
                        vtev.offsetLocation(scrollOffset[0].toFloat(), scrollOffset[1].toFloat())
                        nestedOffsets[0] += scrollOffset[0]
                        nestedOffsets[1] += scrollOffset[1]
                    }

                    velocityTracker?.addMovement(vtev)
                    vtev.recycle()
                    return super.onTouchEvent(event)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerId = event.getPointerId(actionIndex)
                lastMotionX = (event.getX(actionIndex) + 0.5f).toInt()
                lastMotionY = (event.getY(actionIndex) + 0.5f).toInt()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(event)
                val pIndex = event.findPointerIndex(activePointerId)
                if (pIndex >= 0) {
                    lastMotionX = (event.getX(pIndex) + 0.5f).toInt()
                    lastMotionY = (event.getY(pIndex) + 0.5f).toInt()
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(vtev)
                velocityTracker?.computeCurrentVelocity(1000, maximumVelocity.toFloat())
                val initialVelocityY = -(velocityTracker?.getYVelocity(activePointerId) ?: 0f)

                if (abs(initialVelocityY) > minimumVelocity) {
                    if (!dispatchNestedPreFling(0f, initialVelocityY)) {
                        dispatchNestedFling(0f, initialVelocityY, true)
                        flingWithFriction(initialVelocityY.toInt())
                    }
                }

                endTouch()
            }

            MotionEvent.ACTION_CANCEL -> {
                endTouch()
            }
        }

        velocityTracker?.addMovement(vtev)
        vtev.recycle()
        return super.onTouchEvent(event)
    }

    private fun flingWithFriction(velocityY: Int) {
        val maxScrollY = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
        customScroller?.fling(
            scrollX, scrollY,
            0, velocityY,
            0, 0,
            0, maxScrollY,
            0, 0
        )
        ViewCompat.postInvalidateOnAnimation(this)
    }

    override fun computeScroll() {
        val scroller = customScroller
        if (scroller != null && scroller.computeScrollOffset()) {
            val y = scroller.currY
            scrollTo(scrollX, y)
            ViewCompat.postInvalidateOnAnimation(this)
        } else {
            super.computeScroll()
        }
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == activePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            activePointerId = ev.getPointerId(newPointerIndex)
        }
    }

    private fun endTouch() {
        isBeingDragged = false
        activePointerId = INVALID_POINTER
        velocityTracker?.recycle()
        velocityTracker = null
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    // NestedScrollingChild3 delegate implementations
    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return childHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return childHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return startNestedScroll(axes, ViewCompat.TYPE_TOUCH)
    }

    override fun stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    override fun hasNestedScrollingParent(): Boolean {
        return hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean {
        return dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    companion object {
        private const val INVALID_POINTER = -1
    }
}
