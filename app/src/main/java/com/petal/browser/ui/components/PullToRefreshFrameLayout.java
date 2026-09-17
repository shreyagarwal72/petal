package com.petal.browser.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * FrameLayout used for the browser's content container ({@code R.id.main_content})
 * that provides smooth, Chrome/Firefox-like pull-to-refresh UX.
 *
 * It filters gestures to match standard mobile browser touch areas:
 * - Restricts pull initiation touch area to the top portion of the viewport.
 * - Rejects multi-touch gestures (pinch-to-zoom protection like Firefox / Fennec).
 * - Filters quick-scale / double-tap drag zoom gestures.
 * - Enforces vertical swipe dominance over horizontal drags.
 * - Damps pull travel distance to standard 100dp for snappy, tactile refresh.
 * - Respects child disallow-intercept requests.
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

    private static final float DEFAULT_PULL_DISTANCE_DP = 80f;
    private static final float EDGE_THRESHOLD_DP = 120f;
    private static final float TRIGGER_THRESHOLD = 1.0f;
    private static final float DRAG_DAMPING = 0.40f;

    private CanPull canPull = () -> true;
    private OnPullListener onPullListener;
    private OnReleaseListener onReleaseListener;

    private final int touchSlop;
    private final int doubleTapTimeout;
    private final int doubleTapSlopSquare;
    private float pullDistancePx;
    private float edgeThresholdPx;
    private float downX;
    private float downY;
    private float previousX;
    private float previousY;
    private boolean dragging;
    private boolean intercepting;
    private boolean hadMultiTouch;
    private boolean isQuickScaleInProgress;
    private boolean disallowIntercept;

    private MotionEvent firstDownEvent;
    private MotionEvent upEvent;
    private MotionEvent secondDownEvent;

    public PullToRefreshFrameLayout(Context context) {
        this(context, null);
    }

    public PullToRefreshFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PullToRefreshFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
        int doubleTapSlop = vc.getScaledDoubleTapSlop();
        doubleTapSlopSquare = doubleTapSlop * doubleTapSlop;
        pullDistancePx = DEFAULT_PULL_DISTANCE_DP * context.getResources().getDisplayMetrics().density;
        edgeThresholdPx = EDGE_THRESHOLD_DP * context.getResources().getDisplayMetrics().density;
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

    /** Drag distance (in dp) that maps to 100% pull progress. Defaults to 80dp like omni-browser. */
    public void setPullDistanceDp(float dp) {
        this.pullDistancePx = dp * getResources().getDisplayMetrics().density;
    }

    /** Top edge initiation touch area threshold (in dp). Defaults to 120dp like omni-browser. */
    public void setEdgeThresholdDp(float dp) {
        this.edgeThresholdPx = dp * getResources().getDisplayMetrics().density;
    }

    private boolean canChildScrollUp() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE && child.canScrollVertically(-1)) {
                return true;
            }
        }
        return false;
    }

    private void maybeAddDoubleTapEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (upEvent != null) {
                if (event.getEventTime() - upEvent.getEventTime() > doubleTapTimeout) {
                    forgetQuickScaleEvents();
                    firstDownEvent = MotionEvent.obtain(event);
                } else {
                    secondDownEvent = MotionEvent.obtain(event);
                }
            } else {
                forgetQuickScaleEvents();
                firstDownEvent = MotionEvent.obtain(event);
            }
        } else if (action == MotionEvent.ACTION_UP && firstDownEvent != null) {
            upEvent = MotionEvent.obtain(event);
        }
    }

    private boolean isQuickScaleGesture() {
        if (firstDownEvent == null || upEvent == null || secondDownEvent == null) {
            return false;
        }
        if (secondDownEvent.getEventTime() - upEvent.getEventTime() > doubleTapTimeout) {
            return false;
        }
        int deltaX = (int) (firstDownEvent.getX() - secondDownEvent.getX());
        int deltaY = (int) (firstDownEvent.getY() - secondDownEvent.getY());
        return (deltaX * deltaX + deltaY * deltaY) < doubleTapSlopSquare;
    }

    private void forgetQuickScaleEvents() {
        if (firstDownEvent != null) {
            firstDownEvent.recycle();
            firstDownEvent = null;
        }
        if (upEvent != null) {
            upEvent.recycle();
            upEvent = null;
        }
        if (secondDownEvent != null) {
            secondDownEvent.recycle();
            secondDownEvent = null;
        }
        isQuickScaleInProgress = false;
    }

    private void cancelDrag() {
        if (dragging) {
            dragging = false;
            intercepting = false;
            if (onReleaseListener != null) {
                onReleaseListener.onRelease(false);
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled() || disallowIntercept) {
            return false;
        }

        // Multi-touch rejection (pinch-to-zoom protection like Firefox / Chrome)
        if (ev.getPointerCount() > 1 || hadMultiTouch) {
            hadMultiTouch = true;
            if (dragging) {
                cancelDrag();
            }
            return false;
        }

        int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_CANCEL || (action == MotionEvent.ACTION_UP && isQuickScaleInProgress)) {
            forgetQuickScaleEvents();
            cancelDrag();
            return false;
        }

        maybeAddDoubleTapEvent(ev);
        if (isQuickScaleGesture()) {
            isQuickScaleInProgress = true;
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                previousX = ev.getX();
                previousY = ev.getY();
                dragging = false;
                intercepting = false;
                hadMultiTouch = false;
                // Reset on every new gesture sequence. GeckoView calls
                // requestDisallowInterceptTouchEvent(true) while scrolling, which would
                // permanently block subsequent pull-to-refresh attempts without this reset.
                disallowIntercept = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float currentX = ev.getX();
                float currentY = ev.getY();
                float xDistance = Math.abs(currentX - previousX);
                float yDistance = Math.abs(currentY - previousY);
                previousX = currentX;
                previousY = currentY;

                // Disable pull to refresh if the movement is horizontal (like Firefox / Chrome)
                if (xDistance > yDistance && !dragging) {
                    return false;
                }

                // Match omni-browser: pull-to-refresh initiates when touch starts within the top edge threshold (120dp),
                // the child cannot scroll up (scrollY <= 0), deltaY > touchSlop, and gesture is vertically dominant.
                if (!intercepting && downY < edgeThresholdPx && !canChildScrollUp() && canPull.canPull()) {
                    float dx = currentX - downX;
                    float dy = currentY - downY;
                    if (dy > touchSlop && dy > Math.abs(dx)) {
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
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || disallowIntercept) {
            return false;
        }

        if (event.getPointerCount() > 1 || hadMultiTouch) {
            hadMultiTouch = true;
            if (dragging) {
                cancelDrag();
            }
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                previousX = event.getX();
                previousY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float pullDistance = Math.max(0f, (event.getY() - downY) - touchSlop);
                    float dampedDy = pullDistance * DRAG_DAMPING;
                    float progress = Math.min(1f, dampedDy / pullDistancePx);
                    if (onPullListener != null) {
                        onPullListener.onPull(progress);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    float pullDistance = Math.max(0f, (event.getY() - downY) - touchSlop);
                    float dampedDy = pullDistance * DRAG_DAMPING;
                    float progress = dampedDy / pullDistancePx;
                    dragging = false;
                    intercepting = false;
                    if (onReleaseListener != null) {
                        onReleaseListener.onRelease(progress >= TRIGGER_THRESHOLD);
                    }
                }
                forgetQuickScaleEvents();
                break;

            default:
                break;
        }
        return true;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        this.disallowIntercept = disallowIntercept;
        if (disallowIntercept && dragging) {
            cancelDrag();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean gatherTransparentRegion(android.graphics.Region region) {
        try {
            return super.gatherTransparentRegion(region);
        } catch (Exception e) {
            return false;
        }
    }
}
