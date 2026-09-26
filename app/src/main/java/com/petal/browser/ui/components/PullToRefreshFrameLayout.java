package com.petal.browser.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * High-performance full-viewport pull-to-refresh container for browser content
 * ({@code R.id.main_content}) engineered from Mozilla Firefox / Fenix SwipeRefreshLayout
 * and GeckoView touch architecture.
 *
 * Firefox Touch Features & Architecture:
 * - Full-screen viewport touch initiation: Any touch gesture starting on the web page / viewport
 *   when page is scrolled to top (scrollY <= 0) can initiate pull-to-refresh.
 * - Multi-touch & Pinch-to-zoom protection: Multi-touch gestures (zoom, quick-scale, pinch)
 *   are detected and immediately cancel/prevent pull-to-refresh interception so webpage gestures work 100%.
 * - Multi-pointer switching: Seamlessly transfers pointer tracking if primary finger lifts (ACTION_POINTER_UP).
 * - Vertical dominance filtering (Firefox VerticalSwipeRefreshLayout): Ignores horizontal swipes
 *   (e.g., carousels, text selection, swiping tabs) using strict touch slop & directional dominance.
 * - Firefox / AOSP Slingshot tension physics: Non-linear damping curve based on slingshot distance
 *   giving a realistic, physical rubber-band pull experience.
 * - Haptic feedback: Subtle tactile click when passing the release trigger threshold.
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

    // Firefox / AOSP SwipeRefreshLayout physics constants
    private static final float DEFAULT_PULL_DISTANCE_DP = 80f;
    private static final float TRIGGER_THRESHOLD = 0.70f;
    private static final float DRAG_RATE = 0.5f;
    private static final float VERTICAL_DOMINANCE = 1.35f;

    private CanPull canPull = () -> true;
    private OnPullListener onPullListener;
    private OnReleaseListener onReleaseListener;

    private final int touchSlop;
    private final int doubleTapTimeout;
    private final int doubleTapSlopSquare;
    private float pullDistancePx;

    private float initialDownX;
    private float initialDownY;
    private float initialMotionY;
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

    /** Backward compatibility: touch area is the full viewport when at scroll top. */
    public void setEdgeThresholdDp(float dp) {
        // Maintained for binary compatibility - covers full viewport
    }

    /**
     * Determines whether child views can scroll upwards.
     * Aligned with Firefox Android: if canPull callback is supplied, it serves
     * as the authoritative source for whether the active web/doc surface is at top.
     * We only fall back to child.canScrollVertically(-1) if canPull allows it.
     */
    private boolean canChildScrollUp() {
        if (canPull != null && !canPull.canPull()) {
            return true;
        }
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

    /**
     * Resets touch state and cancels any in-progress drag.
     */
    public void reset() {
        cancelDrag();
        disallowIntercept = false;
        hasMultiTouch = false;
        isQuickScaleInProgress = false;
        forgetQuickScaleEvents();
    }

    /**
     * Official Firefox / AOSP slingshot tension calculation.
     * Computes the non-linear pull progress [0f, 1f+] with elastic resistance as user drags down.
     */
    private float calculateProgress(float overscrollTop) {
        if (overscrollTop <= 0 || pullDistancePx <= 0) {
            return 0f;
        }
        float originalDragPercent = overscrollTop / pullDistancePx;
        float dragPercent = Math.min(1.0f, Math.abs(originalDragPercent));
        float extraOS = Math.abs(overscrollTop) - pullDistancePx;
        float slingshotDist = pullDistancePx;
        float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2) / slingshotDist);
        float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow((tensionSlingshotPercent / 4), 2)) * 2f;
        float extraMove = slingshotDist * tensionPercent * 2;
        float targetY = (slingshotDist * dragPercent) + extraMove;
        return Math.max(0f, targetY / pullDistancePx);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }

        int action = ev.getActionMasked();

        // Always reset disallowIntercept and state on a fresh ACTION_DOWN (pure Firefox pattern)
        if (action == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
            hasMultiTouch = false;
            isDragging = false;
            isIntercepting = false;
            hasTriggeredHaptic = false;
        }

        if (disallowIntercept) {
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
                initialDownX = ev.getX();
                initialDownY = ev.getY();
                initialMotionY = initialDownY;
                isDragging = false;
                isIntercepting = false;
                hasMultiTouch = false;
                disallowIntercept = false;
                hasTriggeredHaptic = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN: {
                // Second pointer touches down; multi-touch should yield to page zoom/gestures
                hasMultiTouch = true;
                if (isDragging) {
                    cancelDrag();
                }
                return false;
            }

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
                float dx = curX - initialDownX;
                float dy = curY - initialDownY;

                // Only downwards travel starts pull
                if (dy <= touchSlop) {
                    break;
                }

                // Vertical dominance: Must be noticeably more vertical than horizontal (Firefox VerticalSwipeRefreshLayout)
                if (dy <= Math.abs(dx) * VERTICAL_DOMINANCE) {
                    break;
                }

                // Page position validation (Firefox model): Full touch area across viewport is valid when scrolled to top
                if (!canChildScrollUp() && (canPull == null || canPull.canPull())) {
                    initialMotionY = initialDownY + touchSlop;
                    isIntercepting = true;
                    isDragging = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    sendCancelEvent(ev);
                    return true;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
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

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
        }

        if (disallowIntercept && !isDragging) {
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
                initialDownX = event.getX();
                initialDownY = event.getY();
                initialMotionY = initialDownY;
                break;

            case MotionEvent.ACTION_POINTER_DOWN: {
                hasMultiTouch = true;
                if (isDragging) {
                    cancelDrag();
                }
                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                float curY = event.getY(pointerIndex);

                if (!isDragging && !disallowIntercept) {
                    float curX = event.getX(pointerIndex);
                    float dx = curX - initialDownX;
                    float dy = curY - initialDownY;
                    if (dy > touchSlop && dy > Math.abs(dx) * VERTICAL_DOMINANCE && !canChildScrollUp() && (canPull == null || canPull.canPull())) {
                        initialMotionY = initialDownY + touchSlop;
                        isDragging = true;
                        isIntercepting = true;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }

                if (isDragging) {
                    float overscrollTop = (curY - initialMotionY) * DRAG_RATE;
                    if (overscrollTop > 0) {
                        float progress = calculateProgress(overscrollTop);

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
                    } else {
                        // User pushed back past the start
                        if (onPullListener != null) {
                            onPullListener.onPull(0f);
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isDragging) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    float curY = pointerIndex >= 0 ? event.getY(pointerIndex) : initialMotionY;
                    float overscrollTop = (curY - initialMotionY) * DRAG_RATE;
                    float progress = calculateProgress(overscrollTop);

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

    private void onSecondaryPointerUp(MotionEvent ev) {
        int pointerIndex = ev.getActionIndex();
        int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == activePointerId) {
            // Pick a new active pointer
            int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            if (newPointerIndex < ev.getPointerCount()) {
                activePointerId = ev.getPointerId(newPointerIndex);
                initialDownY = ev.getY(newPointerIndex);
                initialMotionY = initialDownY;
            }
        }
    }

    private void sendCancelEvent(MotionEvent ev) {
        try {
            MotionEvent cancelEvent = MotionEvent.obtain(ev);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).dispatchTouchEvent(cancelEvent);
            }
            cancelEvent.recycle();
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        // Ignoring nested scrolls from descendants while enabled (Firefox VerticalSwipeRefreshLayout):
        // Allowing descendants to trigger nested scrolls would defeat the purpose of this class
        // and result in pull to refresh to happen for all movements on the Y axis
        // (even as part of scale/quick scale gestures) while also doubling the throbber with the overscroll shadow.
        if (isEnabled()) {
            return false;
        }
        return super.onStartNestedScroll(child, target, nestedScrollAxes);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // Match Firefox VerticalSwipeRefreshLayout: disable Pull to Refresh on this layout
        // when requested by child, but do NOT propagate to parent.
        if (isDragging || isIntercepting) {
            return;
        }
        this.disallowIntercept = disallowIntercept;
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
