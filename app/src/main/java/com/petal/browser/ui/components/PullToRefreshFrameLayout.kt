package com.petal.browser.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/**
 * FrameLayout used for the browser's content container (R.id.main_content)
 * that lets a downward drag from the top of the page drive pull-to-refresh.
 */
class PullToRefreshFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Whether the current page is scrolled to the top and may start a pull. */
    fun interface CanPull {
        fun canPull(): Boolean
    }

    /** Fired repeatedly while dragging, with progress in [0f, 1f]. */
    fun interface OnPullListener {
        fun onPull(progress: Float)
    }

    /** Fired on release; triggered is true once the pull passed the threshold. */
    fun interface OnReleaseListener {
        fun onRelease(triggered: Boolean)
    }

    private var canPull: CanPull = CanPull { true }
    private var onPullListener: OnPullListener? = null
    private var onReleaseListener: OnReleaseListener? = null

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var pullDistancePx: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragging: Boolean = false
    private var intercepting: Boolean = false

    init {
        pullDistancePx = DEFAULT_PULL_DISTANCE_DP * context.resources.displayMetrics.density
    }

    fun setCanPull(canPull: CanPull) {
        this.canPull = canPull
    }

    fun setOnPullListener(onPullListener: OnPullListener?) {
        this.onPullListener = onPullListener
    }

    fun setOnReleaseListener(onReleaseListener: OnReleaseListener?) {
        this.onReleaseListener = onReleaseListener
    }

    fun setPullDistanceDp(dp: Float) {
        this.pullDistancePx = dp * context.resources.displayMetrics.density
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                intercepting = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (intercepting) return true
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dy > touchSlop && dy > Math.abs(dx) * 1.2f) {
                    if (canPull.canPull()) {
                        intercepting = true
                        dragging = true
                        downY = ev.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                intercepting = false
                dragging = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled) return super.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                if (!dragging && dy > touchSlop) {
                    if (canPull.canPull()) {
                        dragging = true
                        downY = ev.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (dragging) {
                    val rawDy = Math.max(0f, ev.y - downY)
                    val progress = (rawDy / pullDistancePx).coerceIn(0f, 1f)
                    onPullListener?.onPull(progress)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val rawDy = Math.max(0f, ev.y - downY)
                    val progress = (rawDy / pullDistancePx).coerceIn(0f, 1f)
                    val triggered = progress >= TRIGGER_THRESHOLD && ev.actionMasked == MotionEvent.ACTION_UP
                    onReleaseListener?.onRelease(triggered)
                    dragging = false
                    intercepting = false
                    return true
                }
                intercepting = false
                dragging = false
            }
        }
        return super.onTouchEvent(ev)
    }

    companion object {
        private const val DEFAULT_PULL_DISTANCE_DP = 260f
        private const val TRIGGER_THRESHOLD = 0.75f
    }
}
