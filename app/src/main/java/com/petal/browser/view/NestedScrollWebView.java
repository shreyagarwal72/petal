package com.petal.browser.view;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.webkit.WebView;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.ViewCompat;

import com.petal.browser.engine.petal.BrowserMomentumRecoveryRules;
import com.petal.browser.engine.petal.BrowserMomentumWatchdogDecision;
import com.petal.browser.engine.petal.BrowserMomentumWatchdogObservation;
import com.petal.browser.engine.petal.PetalBrowserInputDiagnostics;
import com.petal.browser.engine.petal.PetalBrowserMomentumInterruption;
import com.petal.browser.engine.petal.PetalBrowserPointerSessionSnapshot;
import com.petal.browser.engine.petal.PetalBrowserPointerSessionState;
import com.petal.browser.engine.petal.PetalBrowserWebViewScrollMetrics;

/**
 * Custom NestedScrollWebView supporting:
 * - NestedScrollingChild3 interface implementation
 * - Parent touch stream ownership retention (requestDisallowInterceptTouchEvent)
 * - Momentum recovery and watchdog physics Petal momentum physics engine
 * - Interrupted fling recovery and velocity preservation
 * - Pointer session tracking and scroll metrics snapshotting
 * - Gesture arbitration preventing horizontal swipe navigation conflicts
 */
public class NestedScrollWebView extends WebView implements NestedScrollingChild3 {

    private static final long MAX_FLING_GESTURE_DURATION_MS = 250L;
    private static final int MIN_FLING_TOUCH_SLOP_MULTIPLIER = 4;
    private static final int MOMENTUM_RESPONSE_TOUCH_SLOP_MULTIPLIER = 2;
    private static final int VELOCITY_UNITS_PER_SECOND = 1000;
    private static final long FLING_CONFIRMATION_WINDOW_MS = 500L;
    private static final long FLING_INTERRUPTION_WINDOW_MS = 120L;
    private static final int MIN_RECOVERY_VELOCITY_MULTIPLIER = 4;
    private static final int NATIVE_FLING_STALL_FRAMES = 3;

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

    // Advanced touch stream ownership & momentum recovery physics
    private final PetalBrowserPointerSessionState pointerSessions = new PetalBrowserPointerSessionState();
    private ViewConfiguration viewConfiguration;
    private OverScroller recoveryScroller;
    private OverScroller recoveryVelocityScroller;
    private float gestureDownX = 0f;
    private float gestureDownY = 0f;
    private boolean gestureCanFling = false;
    private VelocityTracker gestureVelocityTracker = null;
    private long gestureGeneration = 0L;
    private boolean touchActive = false;
    private ViewParent touchStreamParent = null;
    private int expectedFlingDirection = 0;
    private long expectedFlingAtMs = Long.MIN_VALUE;
    private int confirmedFlingDirection = 0;
    private long confirmedFlingAtMs = Long.MIN_VALUE;
    private PetalBrowserMomentumInterruption momentumInterruption = null;
    private long recoveryGeneration = Long.MIN_VALUE;

    private final Runnable recoveryFrame = new Runnable() {
        @Override
        public void run() {
            if (recoveryGeneration != gestureGeneration ||
                touchActive ||
                !isAttachedToWindow() ||
                !recoveryScroller.computeScrollOffset()) {
                return;
            }
            scrollTo(getScrollX(), recoveryScroller.getCurrY());
            ViewCompat.postOnAnimation(NestedScrollWebView.this, this);
        }
    };

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
     * belongs to the app or to a predictive back/forward edge swipe.
     */
    protected void onGestureExclusionRefreshNeeded() {
    }

    private void initSmoothScrolling(Context context) {
        viewConfiguration = ViewConfiguration.get(context);
        touchSlop = viewConfiguration.getScaledTouchSlop();
        minimumVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        maximumVelocity = viewConfiguration.getScaledMaximumFlingVelocity();

        customScroller = new OverScroller(context);
        customScroller.setFriction(0.015f);

        recoveryScroller = new OverScroller(context);
        recoveryVelocityScroller = new OverScroller(context);

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

    public void retainParentTouchStreamOwnership(MotionEvent event) {
        releaseParentTouchStreamOwnership();
        // If touch begins near the screen edges (within 24dp), do not disallow parent intercept,
        // allowing system predictive back gesture and parent gesture handlers to operate.
        if (event != null) {
            float edgeMarginPx = 24f * getResources().getDisplayMetrics().density;
            float x = event.getX();
            int width = getWidth();
            if (x < edgeMarginPx || (width > 0 && x > (width - edgeMarginPx))) {
                return;
            }
        }
        touchStreamParent = getParent();
        if (touchStreamParent != null) {
            touchStreamParent.requestDisallowInterceptTouchEvent(true);
        }
    }

    public void retainParentTouchStreamOwnership() {
        retainParentTouchStreamOwnership(null);
    }

    public void releaseParentTouchStreamOwnership() {
        if (touchStreamParent != null) {
            touchStreamParent.requestDisallowInterceptTouchEvent(false);
            touchStreamParent = null;
        }
    }

    private boolean isSingleTouchscreenPointer(MotionEvent event) {
        return event.getPointerCount() == 1 &&
            (event.getSource() & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN;
    }

    private PetalBrowserMomentumInterruption momentumInterruptionFor(MotionEvent event) {
        if (event.getPointerCount() != 1 ||
            (event.getSource() & InputDevice.SOURCE_TOUCHSCREEN) != InputDevice.SOURCE_TOUCHSCREEN ||
            confirmedFlingDirection == 0 ||
            event.getEventTime() - confirmedFlingAtMs > FLING_INTERRUPTION_WINDOW_MS) {
            return null;
        }
        return new PetalBrowserMomentumInterruption(
            event.getX(),
            event.getY(),
            getScrollY(),
            confirmedFlingDirection,
            false
        );
    }

    private void interruptMomentumScroll(MotionEvent event) {
        PetalBrowserMomentumInterruption interruption = momentumInterruption;
        if (interruption == null) return;
        if (event.getPointerCount() != 1) {
            momentumInterruption = null;
            return;
        }
        float fingerTravelY = event.getY() - interruption.getDownY();
        float fingerTravelX = event.getX() - interruption.getDownX();
        if (Math.abs(fingerTravelY) <= touchSlop || Math.abs(fingerTravelY) <= Math.abs(fingerTravelX)) {
            return;
        }
        float desiredScrollDelta = -fingerTravelY;
        if (desiredScrollDelta * interruption.getMomentumDirection() >= 0f) return;
        float responseDistancePx = touchSlop * MOMENTUM_RESPONSE_TOUCH_SLOP_MULTIPLIER;
        int targetScrollY;
        if (interruption.getMomentumDirection() > 0) {
            interruption.setMomentumEdgeScrollY(Math.max(interruption.getMomentumEdgeScrollY(), getScrollY()));
            targetScrollY = (int) (interruption.getMomentumEdgeScrollY() - responseDistancePx);
        } else {
            interruption.setMomentumEdgeScrollY(Math.min(interruption.getMomentumEdgeScrollY(), getScrollY()));
            targetScrollY = (int) (interruption.getMomentumEdgeScrollY() + responseDistancePx);
        }
        boolean nativeScrollInterrupted = interruption.getMomentumDirection() > 0 ? (getScrollY() <= targetScrollY) : (getScrollY() >= targetScrollY);
        if (!nativeScrollInterrupted) {
            int maximumScrollY = Math.max(getScrollY(), computeVerticalScrollRange() - computeVerticalScrollExtent());
            int clampedTarget = Math.max(0, Math.min(targetScrollY, maximumScrollY));
            scrollTo(getScrollX(), clampedTarget);
            interruption.setManualCorrectionApplied(true);
        }
    }

    private void rememberPotentialFling(MotionEvent event) {
        float fingerTravelY = event.getY() - gestureDownY;
        float fingerTravelX = event.getX() - gestureDownX;
        long durationMs = event.getEventTime() - event.getDownTime();
        if (gestureCanFling &&
            durationMs <= MAX_FLING_GESTURE_DURATION_MS &&
            Math.abs(fingerTravelY) >= touchSlop * MIN_FLING_TOUCH_SLOP_MULTIPLIER &&
            Math.abs(fingerTravelY) > Math.abs(fingerTravelX)) {
            expectedFlingDirection = Float.compare(-fingerTravelY, 0f);
            expectedFlingAtMs = SystemClock.uptimeMillis();
        } else {
            expectedFlingDirection = 0;
            expectedFlingAtMs = Long.MIN_VALUE;
        }
        confirmedFlingDirection = 0;
        confirmedFlingAtMs = Long.MIN_VALUE;
    }

    private void beginVelocityTracking(MotionEvent event) {
        recycleGestureVelocityTracker();
        gestureVelocityTracker = VelocityTracker.obtain();
        gestureVelocityTracker.addMovement(event);
    }

    private void recycleGestureVelocityTracker() {
        if (gestureVelocityTracker != null) {
            gestureVelocityTracker.recycle();
            gestureVelocityTracker = null;
        }
    }

    private float trackedScrollVelocityY() {
        if (gestureVelocityTracker == null) return 0f;
        gestureVelocityTracker.computeCurrentVelocity(VELOCITY_UNITS_PER_SECOND, viewConfiguration.getScaledMaximumFlingVelocity());
        return -gestureVelocityTracker.getYVelocity();
    }

    private float getMinimumRecoveryVelocity() {
        return viewConfiguration.getScaledMinimumFlingVelocity() * (float) MIN_RECOVERY_VELOCITY_MULTIPLIER;
    }

    private void stopRecoveryFling() {
        recoveryGeneration = Long.MIN_VALUE;
        removeCallbacks(recoveryFrame);
        if (recoveryScroller != null) recoveryScroller.abortAnimation();
        if (recoveryVelocityScroller != null) recoveryVelocityScroller.abortAnimation();
    }

    private void clearConfirmedFling() {
        expectedFlingDirection = 0;
        expectedFlingAtMs = Long.MIN_VALUE;
        confirmedFlingDirection = 0;
        confirmedFlingAtMs = Long.MIN_VALUE;
    }

    private void recoverInterruptedFling(float scrollVelocityY) {
        final PetalBrowserMomentumInterruption interruption = momentumInterruption;
        if (interruption == null) return;
        final int direction = expectedFlingDirection;
        if (direction == 0 ||
            direction * interruption.getMomentumDirection() >= 0 ||
            Math.abs(scrollVelocityY) < getMinimumRecoveryVelocity() ||
            scrollVelocityY * direction <= 0f) {
            return;
        }

        final long capturedGeneration = gestureGeneration;
        final int scrollYAtUp = getScrollY();
        final int maximumScrollY = Math.max(scrollYAtUp, computeVerticalScrollRange() - computeVerticalScrollExtent());

        recoveryVelocityScroller.fling(0, scrollYAtUp, 0, Math.round(scrollVelocityY), 0, 0, 0, maximumScrollY);

        final Runnable replacementFling = new Runnable() {
            @Override
            public void run() {
                if (gestureGeneration != capturedGeneration || touchActive || !isAttachedToWindow()) {
                    return;
                }
                recoveryVelocityScroller.computeScrollOffset();
                int recoveryVelocityY = Math.round(recoveryVelocityScroller.getCurrVelocity() * direction);
                if (Math.abs(recoveryVelocityY) < viewConfiguration.getScaledMinimumFlingVelocity()) {
                    return;
                }
                flingScroll(0, 0);
                recoveryGeneration = capturedGeneration;
                recoveryScroller.fling(0, getScrollY(), 0, recoveryVelocityY, 0, 0, 0, Math.max(getScrollY(), maximumScrollY));
                ViewCompat.postOnAnimation(NestedScrollWebView.this, recoveryFrame);
            }
        };

        final Runnable nativeFlingWatchdog = new Runnable() {
            private int previousScrollY = scrollYAtUp;
            private int stalledFrames = 0;

            @Override
            public void run() {
                if (gestureGeneration != capturedGeneration || touchActive || !isAttachedToWindow()) {
                    return;
                }
                boolean shadowRunning = recoveryVelocityScroller.computeScrollOffset();
                float shadowVelocity = recoveryVelocityScroller.getCurrVelocity();
                BrowserMomentumWatchdogObservation observation = BrowserMomentumRecoveryRules.observe(
                    previousScrollY,
                    getScrollY(),
                    direction,
                    stalledFrames,
                    shadowRunning,
                    shadowVelocity,
                    getMinimumRecoveryVelocity(),
                    NATIVE_FLING_STALL_FRAMES
                );
                stalledFrames = observation.getStalledFrames();
                previousScrollY = getScrollY();

                BrowserMomentumWatchdogDecision decision = observation.getDecision();
                if (decision == BrowserMomentumWatchdogDecision.Continue) {
                    ViewCompat.postOnAnimation(NestedScrollWebView.this, this);
                    return;
                } else if (decision == BrowserMomentumWatchdogDecision.Stop) {
                    recoveryVelocityScroller.abortAnimation();
                    return;
                }

                replacementFling.run();
            }
        };

        ViewCompat.postOnAnimation(this, nativeFlingWatchdog);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            retainParentTouchStreamOwnership(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                gestureGeneration++;
                momentumInterruption = momentumInterruptionFor(event);
                stopRecoveryFling();
                clearConfirmedFling();
                beginVelocityTracking(event);
                gestureDownX = event.getX();
                gestureDownY = event.getY();
                gestureCanFling = isSingleTouchscreenPointer(event);
                touchActive = true;
                pointerSessions.begin();
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                gestureGeneration++;
                momentumInterruption = null;
                gestureCanFling = false;
                stopRecoveryFling();
                recycleGestureVelocityTracker();
                clearConfirmedFling();
                pointerSessions.end();
                break;
            }
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP: {
                if (gestureVelocityTracker != null) {
                    gestureVelocityTracker.addMovement(event);
                }
                break;
            }
        }

        boolean handled = super.dispatchTouchEvent(event);

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !handled) {
            releaseParentTouchStreamOwnership();
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            interruptMomentumScroll(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float scrollVelocityY = trackedScrollVelocityY();
            rememberPotentialFling(event);
            recoverInterruptedFling(scrollVelocityY);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP ||
            event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            gestureCanFling = false;
            touchActive = false;
            momentumInterruption = null;
            recycleGestureVelocityTracker();
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                gestureGeneration++;
                stopRecoveryFling();
                clearConfirmedFling();
            }
            pointerSessions.end();
            releaseParentTouchStreamOwnership();
        }

        return handled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        initVelocityTracker();

        MotionEvent vtev = MotionEvent.obtain(event);
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsets[0] = 0;
            nestedOffsets[1] = 0;
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
                        ViewParent p = getParent();
                        if (p != null) {
                            p.requestDisallowInterceptTouchEvent(true);
                        }
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

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (touchActive || t == oldt || expectedFlingDirection == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - expectedFlingAtMs > FLING_CONFIRMATION_WINDOW_MS) {
            clearConfirmedFling();
            return;
        }
        int direction = Integer.compare(t - oldt, 0);
        if (direction == expectedFlingDirection) {
            expectedFlingAtMs = now;
            confirmedFlingDirection = direction;
            confirmedFlingAtMs = now;
        }
    }

    public PetalBrowserPointerSessionSnapshot pointerSessionSnapshot() {
        return pointerSessions.snapshot();
    }

    public boolean acceptsPointerSession(PetalBrowserPointerSessionSnapshot captured) {
        return pointerSessions.accepts(captured);
    }

    public PetalBrowserWebViewScrollMetrics scrollMetricsSnapshot() {
        return new PetalBrowserWebViewScrollMetrics(
            Math.max(0, computeVerticalScrollOffset()),
            Math.max(0, computeVerticalScrollExtent()),
            Math.max(0, computeVerticalScrollRange())
        );
    }

    public void scrollToVerticalOffset(int offsetPx) {
        gestureGeneration++;
        momentumInterruption = null;
        stopRecoveryFling();
        clearConfirmedFling();
        flingScroll(0, 0);
        PetalBrowserWebViewScrollMetrics metrics = scrollMetricsSnapshot();
        int maxOffset = Math.max(0, metrics.getRangePx() - metrics.getExtentPx());
        scrollTo(getScrollX(), Math.max(0, Math.min(offsetPx, maxOffset)));
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) {
            releaseParentTouchStreamOwnership();
            gestureGeneration++;
            gestureCanFling = false;
            touchActive = false;
            momentumInterruption = null;
            stopRecoveryFling();
            recycleGestureVelocityTracker();
            clearConfirmedFling();
            pointerSessions.end();
        }
        super.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseParentTouchStreamOwnership();
        gestureGeneration++;
        gestureCanFling = false;
        touchActive = false;
        momentumInterruption = null;
        stopRecoveryFling();
        recycleGestureVelocityTracker();
        clearConfirmedFling();
        pointerSessions.end();
        super.onDetachedFromWindow();
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
