package com.petal.browser.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.ViewCompat;

/**
 * Custom NestedScrollWebView supporting:
 * - NestedScrollingChild3 interface implementation
 * - Hardware acceleration (LAYER_TYPE_HARDWARE) for 120Hz scrolling
 * - Custom fling physics via OverScroller with custom deceleration friction
 * - Gesture arbitration preventing horizontal swipe navigation conflicts
 */
public class NestedScrollWebView extends WebView implements NestedScrollingChild3 {

    private final NestedScrollingChildHelper childHelper;
    private final int[] scrollOffset = new int[2];
    private final int[] scrollConsumed = new int[2];
    private final int[] nestedOffsets = new int[2];

    private int lastMotionY;
    private int lastMotionX;
    private int initialDownX;
    private int initialDownY;
    private int activePointerId = INVALID_POINTER;
    private static final int INVALID_POINTER = -1;

    private VelocityTracker velocityTracker;
    private int touchSlop;
    private int minimumVelocity;
    private int maximumVelocity;
    private OverScroller customScroller;
    private boolean isBeingDragged = false;

    public NestedScrollWebView(@NonNull Context context) {
        this(context, null);
    }

    public NestedScrollWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.webViewStyle);
    }

    public NestedScrollWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        childHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
        initSmoothScrolling(context);
    }

    /**
     * Hook called on every ACTION_DOWN so a subclass (NinjaWebView) can re-apply its
     * system gesture exclusion rects right before the OS decides whether this touch
     * belongs to the app or to a predictive back/forward edge swipe. No-op here -
     * this base class has no concept of gesture exclusion on its own.
     */
    protected void onGestureExclusionRefreshNeeded() {
    }

    private void initSmoothScrolling(Context context) {
        // NOTE: do NOT force LAYER_TYPE_HARDWARE here. NinjaWebView.initPreferences()
        // deliberately sets LAYER_TYPE_NONE right after this view is constructed, so this
        // just created a hardware layer/surface for a brief moment and then abandoned it.
        // On several OEM builds (ColorOS/OnePlus/Oppo/Realme included) that hand-off leaves
        // a stale cached layer behind: page overlays (menus, dropdowns, sheets) can stop
        // getting repainted/removed from the screen after their DOM state changes, because
        // the compositor is still presenting the old cached frame instead of asking the
        // WebView to redraw. Chrome never hits this because it never toggles a view-level
        // hardware layer on and off like this. Leaving layer type untouched here (its
        // default) avoids creating that stale layer in the first place.

        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        // Custom OverScroller with smooth deceleration friction
        customScroller = new OverScroller(context);
        customScroller.setFriction(0.015f);

        // Disable native webview overscroll effect so custom pull-to-refresh handles gestures cleanly without distortion
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void flingScroll(int vx, int vy) {
        super.flingScroll(vx, vy);
        if (customScroller != null) {
            int scrollY = getScrollY();
            customScroller.fling(0, scrollY, 0, vy, 0, 0, 0, Math.max(0, computeVerticalScrollRange() - getHeight()), 0, 0);
            ViewCompat.postInvalidateOnAnimation(this);
        }
        dispatchNestedFling(0, vy, true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        initVelocityTracker();

        MotionEvent vtev = MotionEvent.obtain(event);
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsets[0] = 0;
            nestedOffsets[1] = 0;
            // Chromium's WebView internals manage system gesture exclusion rects on
            // their own and will silently widen them back over the screen edges as
            // soon as the page is scrolled/interacted with - overriding whatever the
            // app set at layout/page-load time. Re-claiming the edges right here, on
            // every touch-down, wins that race so the system's predictive back/forward
            // edge swipe keeps reaching the app instead of being swallowed by the page.
            onGestureExclusionRefreshNeeded();
        }

        vtev.offsetLocation(nestedOffsets[0], nestedOffsets[1]);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                activePointerId = event.getPointerId(0);
                initialDownX = (int) event.getX();
                initialDownY = (int) event.getY();
                lastMotionX = initialDownX;
                lastMotionY = initialDownY;
                isBeingDragged = false;

                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex == -1) break;

                final int x = (int) event.getX(pointerIndex);
                final int y = (int) event.getY(pointerIndex);
                int deltaX = lastMotionX - x;
                int deltaY = lastMotionY - y;

                // Gesture arbitration: Allow vertical nested scrolling while preserving native webview horizontal gestures
                if (!isBeingDragged) {
                    int xDiff = Math.abs(x - initialDownX);
                    int yDiff = Math.abs(y - initialDownY);
                    if (yDiff > touchSlop && yDiff > xDiff) {
                        isBeingDragged = true;
                        if (deltaY > 0) {
                            deltaY -= touchSlop;
                        } else {
                            deltaY += touchSlop;
                        }
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                if (isBeingDragged) {
                    if (dispatchNestedPreScroll(deltaX, deltaY, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                        deltaY -= scrollConsumed[1];
                        vtev.offsetLocation(0, scrollOffset[1]);
                        nestedOffsets[1] += scrollOffset[1];
                    }

                    lastMotionY = y - scrollOffset[1];
                    int oldY = getScrollY();
                    int newScrollY = Math.max(0, oldY + deltaY);
                    int unconsumedY = deltaY - (newScrollY - oldY);

                    dispatchNestedScroll(0, newScrollY - unconsumedY, 0, unconsumedY, scrollOffset, ViewCompat.TYPE_TOUCH, scrollConsumed);
                    lastMotionY -= scrollOffset[1];
                    vtev.offsetLocation(0, scrollOffset[1]);
                    nestedOffsets[1] += scrollOffset[1];
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (isBeingDragged && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                    int initialVelocity = (int) velocityTracker.getYVelocity(activePointerId);

                    if ((Math.abs(initialVelocity) > minimumVelocity)) {
                        flingScroll(0, -initialVelocity);
                    } else {
                        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH);
                    }
                } else {
                    // This was a tap, not a drag. Force a redraw request after the page has
                    // had a chance to process the click (e.g. closing a menu/dropdown), as a
                    // safety net against the stale-frame compositor issue described above -
                    // this is a no-op if the page already redrew correctly on its own.
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                endTouch();
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                endTouch();
                break;
            }
        }

        if (velocityTracker != null) {
            velocityTracker.addMovement(vtev);
        }
        vtev.recycle();

        return super.onTouchEvent(event);
    }

    private void initVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
    }

    private void endTouch() {
        isBeingDragged = false;
        activePointerId = INVALID_POINTER;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    // NestedScrollingChild3 Implementation Methods

    @Override
    public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type, @NonNull int[] consumed) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed);
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return childHelper.startNestedScroll(axes, type);
    }

    @Override
    public void stopNestedScroll(int type) {
        childHelper.stopNestedScroll(type);
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return childHelper.hasNestedScrollingParent(type);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type) {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow, int type) {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        childHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return childHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return startNestedScroll(axes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return hasNestedScrollingParent(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow) {
        return dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow) {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY);
    }
}
