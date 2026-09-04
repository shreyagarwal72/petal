package com.petal.browser.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * FrameLayout used for the browser's content container ({@code R.id.main_content})
 * that lets a downward drag from the top of the page drive pull-to-refresh, no
 * matter what's currently inside it - a {@code NinjaWebView}, or one of the
 * Compose screens (home / settings / downloads).
 *
 * A plain {@code View.OnTouchListener} on this container never fires while a
 * WebView (or a Compose child) is on top consuming every touch event itself.
 * This class fixes that the same way {@code SwipeRefreshLayout} does: it
 * intercepts the touch stream itself via {@link #onInterceptTouchEvent} as
 * soon as a clearly vertical, downward drag starts and {@link #canPull} says
 * the content is scrolled to the top - stealing the gesture from the child
 * before it ever gets to consume it.
 */
public class PullToRefreshFrameLayout extends FrameLayout {

    /** Whether the current page is scrolled to the top and may start a pull. */
    public interface CanPull {
        boolean canPull();
    }

    /** Fired repeatedly while dragging, with progress in [0f, 1f]. */
    public interface OnPullListener {
        void onPull(float progress);
    }

    /** Fired on release; {@code triggered} is true once the pull passed the threshold. */
    public interface OnReleaseListener {
        void onRelease(boolean triggered);
    }

    private static final float DEFAULT_PULL_DISTANCE_DP = 260f;
    private static final float TRIGGER_THRESHOLD = 0.75f;

    private CanPull canPull = () -> true;
    private OnPullListener onPullListener;
    private OnReleaseListener onReleaseListener;

    private final int touchSlop;
    private float pullDistancePx;
    private float downX;
    private float downY;
    private boolean dragging;
    private boolean intercepting;

    public PullToRefreshFrameLayout(Context context) {
        this(context, null);
    }

    public PullToRefreshFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PullToRefreshFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        pullDistancePx = DEFAULT_PULL_DISTANCE_DP * context.getResources().getDisplayMetrics().density;
    }

    public void setCanPull(CanPull canPull) {
        this.canPull = canPull != null ? canPull : () -> true;
    }

    public void setOnPullListener(OnPullListener listener) {
        this.onPullListener = listener;
    }

    public void setOnReleaseListener(OnReleaseListener listener) {
        this.onReleaseListener = listener;
    }

    /** Drag distance (in dp) that maps to 100% pull progress. Defaults to 260dp. */
    public void setPullDistanceDp(float dp) {
        this.pullDistancePx = dp * getResources().getDisplayMetrics().density;
    }

    private boolean canChildScrollUp() {
        for (int i = 0; i < getChildCount(); i++) {
            android.view.View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE && child.canScrollVertically(-1)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                intercepting = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!intercepting && !canChildScrollUp() && canPull.canPull()) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    // Allow pulling when dragging downward from top of web content
                    if (dy > touchSlop * 1.5f && dy > Math.abs(dx) * 1.2f) {
                        intercepting = true;
                        dragging = true;
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                intercepting = false;
                break;

            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float dy = Math.max(0f, event.getY() - downY);
                    if (onPullListener != null) {
                        onPullListener.onPull(Math.min(1f, dy / pullDistancePx));
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    float dy = Math.max(0f, event.getY() - downY);
                    float progress = Math.min(1f, dy / pullDistancePx);
                    dragging = false;
                    intercepting = false;
                    if (onReleaseListener != null) {
                        onReleaseListener.onRelease(progress >= TRIGGER_THRESHOLD);
                    }
                }
                break;

            default:
                break;
        }
        return true;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // If the web content is scrolled to the top and pull-to-refresh is enabled,
        // do not let child views (e.g. NestedScrollWebView) disallow interception on ACTION_DOWN.
        // This mirrors SwipeRefreshLayout's standard behavior and allows pull-to-refresh gestures.
        if (canPull != null && canPull.canPull() && !canChildScrollUp()) {
            return;
        }
        // Let a child that wants the gesture for itself (e.g. an inner
        // horizontal swipe or scrolled webview) cancel our intercept.
        if (disallowIntercept) {
            dragging = false;
            intercepting = false;
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }
}
