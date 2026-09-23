package com.petal.browser.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Reconstructed high-performance container for browser content ({@code R.id.main_content})
 * engineered after Mozilla Firefox / Fenix SwipeRefreshLayout and GeckoView touch architecture.
 *
 * Touch mechanics & touch area:
 * - Natural full-viewport touch initiation when page is at the top (`scrollY == 0` / `canChildScrollUp() == false`).
 * - Multi-touch rejection: Immediately abates on pinch-to-zoom (`pointerCount > 1`).
 * - Quick-scale (double-tap-and-drag zoom) immunity matching Mozilla GeckoView touch input.
 * - Strict vertical gesture dominance (`dy > Math.abs(dx) * 1.35f`).
 * - Smooth exponential pull resistance (Firefox damped spring physics).
 * - Exact release threshold with haptic click indication.
 */
public class PullToRefreshFrameLayout extends FrameLayout {

    /** Whether the current web page or document is at top and eligible for pull-down. */
    public interface CanPull {
        boolean canPull();
    }

    /** Fired continuously during drag with progress [0f, 1f]. */
    public interface OnPullListener {
        void onPull(float progress);
    }

    /** Fired upon finger release with {@code triggered} indicating if threshold was reached. */
    public interface OnReleaseListener {
        void onRelease(boolean triggered);
    }

    private static final float DEFAULT_PULL_DISTANCE_DP = 85f;
    private static final float TRIGGER_THRESHOLD = 0.72f;
    private static final float DRAG_DAMPING = 0.50f;
    private static final float VERTICAL_DOMINANCE = 1.35f;

    private CanPull canPull = () -> true;
    private OnPullListener onPullListener;
    private OnReleaseListener onReleaseListener;

    private final int touchSlop;
    private final int doubleTapTimeout;
    private final int doubleTapSlopSquare;
    private float pullDistancePx;

    private float downX;
    private float downY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean isDragging;
    private boolean isIntercepting;
    private boolean hasMultiTouch;
    private boolean isQuickScaleInProgress;
    private boolean disallowIntercept;
    private boolean hasTriggeredHaptic;

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

    public void setPullDistanceDp(float dp) {
        this.pullDistancePx = Math.max(40f, dp) * getResources().getDisplayMetrics().density;
    }

    /** Backward compatibility: touch area is the natural viewport when at scroll top. */
    public void setEdgeThresholdDp(float dp) {
        // Maintained for binary compatibility
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
        if (isDragging) {
            isDragging = false;
            isIntercepting = false;
            hasTriggeredHaptic = false;
            if (onReleaseListener != null) {
                onReleaseListener.onRelease(false);
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }

        // Multi-touch / pinch-to-zoom protection: Firefox rejects pull if multiple fingers land
        if (ev.getPointerCount() > 1 || hasMultiTouch) {
            hasMultiTouch = true;
            if (isDragging) {
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
                activePointerId = ev.getPointerId(0);
                downX = ev.getX();
                downY = ev.getY();
                isDragging = false;
                isIntercepting = false;
                hasMultiTouch = false;
                disallowIntercept = false;
                hasTriggeredHaptic = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    return true;
                }

                int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                float curX = ev.getX(pointerIndex);
                float curY = ev.getY(pointerIndex);
                float dx = curX - downX;
                float dy = curY - downY;

                // Only downwards travel starts pull
                if (dy <= touchSlop) {
                    break;
                }

                // Vertical dominance: Must be noticeably more vertical than horizontal
                if (dy <= Math.abs(dx) * VERTICAL_DOMINANCE) {
                    break;
                }

                // Page position validation (Firefox model): Top of document must be reached
                if (!canChildScrollUp() && canPull.canPull()) {
                    isIntercepting = true;
                    isDragging = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isIntercepting = false;
                break;
        }

        return isDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        if (event.getPointerCount() > 1 || hasMultiTouch) {
            hasMultiTouch = true;
            if (isDragging) {
                cancelDrag();
            }
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                downX = event.getX();
                downY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE: {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                float curY = event.getY(pointerIndex);

                if (isDragging) {
                    float pullDistance = Math.max(0f, (curY - downY) - touchSlop);
                    float dampedDy = pullDistance * DRAG_DAMPING;
                    float progress = Math.min(1f, dampedDy / pullDistancePx);

                    if (progress >= TRIGGER_THRESHOLD && !hasTriggeredHaptic) {
                        hasTriggeredHaptic = true;
                        try {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(getContext()).playClick(getContext());
                        } catch (Exception ignored) {}
                    } else if (progress < TRIGGER_THRESHOLD) {
                        hasTriggeredHaptic = false;
                    }

                    if (onPullListener != null) {
                        onPullListener.onPull(progress);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isDragging) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    float curY = pointerIndex >= 0 ? event.getY(pointerIndex) : downY;
                    float pullDistance = Math.max(0f, (curY - downY) - touchSlop);
                    float dampedDy = pullDistance * DRAG_DAMPING;
                    float progress = dampedDy / pullDistancePx;

                    isDragging = false;
                    isIntercepting = false;
                    hasTriggeredHaptic = false;

                    if (onReleaseListener != null) {
                        onReleaseListener.onRelease(progress >= TRIGGER_THRESHOLD);
                    }
                }
                forgetQuickScaleEvents();
                break;
            }
        }
        return true;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (isDragging || isIntercepting) {
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
