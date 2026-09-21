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
    private static final float TRIGGER_THRESHOLD = 0.70f;
    private static final float DRAG_DAMPING = 0.55f;
    /** Downward travel must beat horizontal travel by this factor (matches architecture docs). */
    private static final float VERTICAL_DOMINANCE = 1.35f;

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

    /**
     * Kept for source compatibility. The pull no longer depends on where the finger lands;
     * the page's scroll position decides whether a pull may start.
     */
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
        // GeckoView may request disallow-intercept while its compositor is scrolling.
        // Keep observing the top-edge downward gesture so pull-to-refresh can still
        // take ownership once the page is at scrollY == 0.
        if (!isEnabled()) {
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
                dragging = false;
                intercepting = false;
                hadMultiTouch = false;
                // Reset on every new gesture sequence so a stale flag from a previous
                // touch (e.g. the legacy WebView requesting disallow-intercept) can never
                // block the next pull.
                disallowIntercept = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    // Already owned by us; keep the stream.
                    return true;
                }

                // Measure against where the finger first landed, not the previous
                // event. Per-event deltas are dominated by thumb jitter on the first
                // few MOVEs, which used to abort the whole gesture.
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;

                // Not a downward pull: leave the touch to the page.
                if (dy <= 0f) {
                    break;
                }

                // Still inside touch slop: undecided, keep watching.
                if (dy <= touchSlop) {
                    break;
                }

                // Clearly horizontal or diagonal: the page or a swipe owns it.
                if (dy <= Math.abs(dx) * VERTICAL_DOMINANCE) {
                    break;
                }

                // Page position is the source of truth for "at top". The old
                // finger-position band (downY <= 120dp) was measured in this
                // container's coordinates, which the floating address bar overlaps,
                // so it rejected valid pulls (bottom address bar, large font scale).
                if (!canChildScrollUp() && canPull.canPull()) {
                    intercepting = true;
                    dragging = true;
                    // Claim the gesture for the rest of this sequence so ancestors
                    // cannot take it back while Gecko is still processing it.
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
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
        if (!isEnabled()) {
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
        if (dragging || intercepting) {
            return;
        }
        this.disallowIntercept = disallowIntercept;
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
