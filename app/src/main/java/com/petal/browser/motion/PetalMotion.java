/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */
package com.petal.browser.motion;

import android.animation.TimeInterpolator;
import android.provider.Settings;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Central motion primitives for the Petal View system.
 *
 * Keep browser/web content lifecycle independent from these animations: this class only
 * changes visual View properties and never creates, opens, closes, or reloads sessions.
 */
public final class PetalMotion {
    public static final long MICRO = 140L;
    public static final long COMPONENT = 220L;
    public static final long TRANSITION = 350L;
    public static final long EXPRESSIVE = 450L;

    // Material 3 emphasized easing: quick start, gentle settle.
    public static final TimeInterpolator EMPHASIZED =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final TimeInterpolator EMPHASIZED_ACCELERATE =
            new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private PetalMotion() {}

    /** Scales duration based on user's Motion Duration Scale setting. */
    public static long scaleDuration(long duration) {
        try {
            float s = com.petal.browser.ui.theme.layout.PetalLayoutState.INSTANCE.getSettings().getMotionDurationScale();
            return (long) (duration * Math.max(0.25f, Math.min(3.0f, s)));
        } catch (Throwable t) {
            return duration;
        }
    }

    /** Animate a newly presented surface without touching its lifecycle. */
    public static void enter(View view) {
        enter(view, TRANSITION);
    }

    public static void enter(View view, long duration) {
        if (view == null) return;
        final long effDuration = scaleDuration(duration);
        if (!animationsEnabled(view)) {
            view.setAlpha(1f);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationX(view.getResources().getDisplayMetrics().widthPixels * 0.08f);
        view.setScaleX(0.985f);
        view.setScaleY(0.985f);
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(effDuration)
                .setInterpolator(EMPHASIZED)
                .withLayer()
                .start();
    }

    /** Receives container-transform progress: 0 = collapsed into the bar, 1 = full screen. */
    public interface ProgressListener {
        void onProgress(float t);
    }

    /**
     * Container transform (Gmail-style search open): the page grows out of {@code origin}
     * (a rect in screen coordinates, e.g. the tapped search/address bar) to full size, corners
     * going from the bar's radius to square. Only the visible clip changes, so Compose content and
     * keyboard focus inside the view are untouched. Falls back to {@link #enter} when animations
     * are off or the origin is unusable.
     */
    public static void containerEnter(final View view, android.graphics.Rect origin, float radiusPx,
                                      long duration, ProgressListener progress, final Runnable onEnd) {
        if (view == null) return;
        if (origin == null || origin.isEmpty() || !animationsEnabled(view)
                || view.getWidth() == 0 || view.getHeight() == 0) {
            enter(view, duration);
            if (progress != null) progress.onProgress(1f);
            if (onEnd != null) onEnd.run();
            return;
        }
        runContainer(view, origin, radiusPx, duration, true, progress, onEnd);
    }

    /** Reverse of {@link #containerEnter}: the page collapses back into {@code origin}. */
    public static void containerExit(final View view, android.graphics.Rect origin, float radiusPx,
                                     long duration, ProgressListener progress, final Runnable onEnd) {
        if (view == null || origin == null || origin.isEmpty() || !animationsEnabled(view)
                || view.getWidth() == 0 || view.getHeight() == 0) {
            if (onEnd != null) onEnd.run();
            return;
        }
        runContainer(view, origin, radiusPx, duration, false, progress, onEnd);
    }

    private static void runContainer(final View view, android.graphics.Rect originOnScreen,
                                     float radiusPx, long duration, final boolean opening,
                                     final ProgressListener progress, final Runnable onEnd) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        final int oL = originOnScreen.left - loc[0];
        final int oT = originOnScreen.top - loc[1];
        final int oR = originOnScreen.right - loc[0];
        final int oB = originOnScreen.bottom - loc[1];
        final float startRadius = radiusPx >= 0f ? radiusPx : originOnScreen.height() / 2f;
        final float[] t = {opening ? 0f : 1f};

        view.setClipToOutline(true);
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                // Read the live size: the keyboard resizes the window while this runs.
                float p = t[0];
                int w = v.getWidth();
                int h = v.getHeight();
                float deviceRadius = 28f * v.getResources().getDisplayMetrics().density;
                float currentRadius = startRadius + (deviceRadius - startRadius) * p;
                outline.setRoundRect(
                        Math.round(oL + (0 - oL) * p),
                        Math.round(oT + (0 - oT) * p),
                        Math.round(oR + (w - oR) * p),
                        Math.round(oB + (h - oB) * p),
                        currentRadius);
            }
        });
        // The bar ghost (drawn above) hides the page's contents at first, so the page itself can
        // be fully opaque from frame one: no fade of the page surface, no see-through pill.
        view.setAlpha(1f);
        if (progress != null) progress.onProgress(t[0]);

        final String transName = opening ? "container_enter" : "container_exit";
        MotionGate.INSTANCE.startTransition(transName);
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(
                opening ? 0f : 1f, opening ? 1f : 0f);
        a.setDuration(scaleDuration(duration));
        a.setInterpolator(EMPHASIZED);
        a.addUpdateListener(anim -> {
            t[0] = (float) anim.getAnimatedValue();
            view.invalidateOutline();
            if (progress != null) progress.onProgress(t[0]);
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                try {
                    if (opening) {
                        view.setClipToOutline(false);
                        view.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                        view.setAlpha(1f);
                    } else {
                        view.setClipToOutline(false);
                        view.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                    }
                    if (onEnd != null) onEnd.run();
                } finally {
                    MotionGate.INSTANCE.endTransition(transName);
                }
            }
        });
        a.start();
    }

    /**
     * The tapped bar's own pixels (cropped from a screenshot), drawn over the growing page and
     * faded out as the page takes over, so the bar visibly turns into the search page (and back).
     * Add it to {@code container.getOverlay()}; it never becomes a child view.
     */
    public static final class BarGhost extends android.graphics.drawable.Drawable {
        private final android.graphics.Bitmap bitmap;
        private final float radius;
        private final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final android.graphics.RectF rectF = new android.graphics.RectF();
        private int alpha = 255;

        public BarGhost(android.graphics.Bitmap bitmap, float radius) {
            this.bitmap = bitmap;
            this.radius = radius;
        }

        /** Ghost is fully visible while collapsed and gone by 30% of the way open. */
        public void setProgress(float t) {
            int a = Math.round(255f * Math.max(0f, Math.min(1f, 1f - t / 0.3f)));
            if (a != alpha) {
                alpha = a;
                invalidateSelf();
            }
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (bitmap == null || bitmap.isRecycled() || b.isEmpty() || alpha <= 0) return;
            paint.setAlpha(alpha);
            rectF.set(b);
            path.reset();
            path.addRoundRect(rectF, radius, radius, android.graphics.Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(path);
            canvas.drawBitmap(bitmap, null, b, paint);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Animate a tab/card into a container with a smaller, faster motion. */
    public static void tabEnter(View view) {
        if (view == null) return;
        if (!animationsEnabled(view)) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(18f);
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(scaleDuration(COMPONENT))
                .setInterpolator(EMPHASIZED)
                .withLayer()
                .start();
    }

    /** Small press feedback for any interactive View. */
    public static void press(View view, boolean pressed) {
        if (view == null || !animationsEnabled(view)) return;
        view.animate()
                .scaleX(pressed ? 0.95f : 1f)
                .scaleY(pressed ? 0.95f : 1f)
                .setDuration(scaleDuration(MICRO))
                .setInterpolator(EMPHASIZED)
                .start();
    }

    /** Smooth bottom sheet slide-up entrance with subtle scale. */
    public static void sheetEnter(View view) {
        if (view == null) return;
        if (!animationsEnabled(view)) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(120f);
        view.setScaleX(0.97f);
        view.setScaleY(0.97f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(scaleDuration(TRANSITION))
                .setInterpolator(EMPHASIZED)
                .withLayer()
                .start();
    }

    /** Smooth bottom sheet slide-down exit. */
    public static void sheetExit(View view, Runnable onComplete) {
        if (view == null) return;
        if (!animationsEnabled(view)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        view.animate()
                .alpha(0f)
                .translationY(160f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(scaleDuration(COMPONENT))
                .setInterpolator(EMPHASIZED_ACCELERATE)
                .withLayer()
                .withEndAction(() -> {
                    if (onComplete != null) onComplete.run();
                })
                .start();
    }

    /** Premium modal dialog pop-in entrance. */
    public static void dialogEnter(View view) {
        if (view == null) return;
        if (!animationsEnabled(view)) {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        view.setAlpha(0f);
        view.setScaleX(0.90f);
        view.setScaleY(0.90f);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(scaleDuration(COMPONENT))
                .setInterpolator(EMPHASIZED)
                .withLayer()
                .start();
    }

    /** Smooth cross-fade for seamless surface switching without blank frames. */
    public static void crossfade(View outgoing, View incoming, long duration) {
        final long effDuration = scaleDuration(duration);
        if (incoming != null) {
            incoming.setVisibility(View.VISIBLE);
            incoming.setAlpha(0f);
            incoming.animate()
                    .alpha(1f)
                    .setDuration(effDuration)
                    .setInterpolator(EMPHASIZED)
                    .start();
        }
        if (outgoing != null) {
            outgoing.animate()
                    .alpha(0f)
                    .setDuration(effDuration)
                    .setInterpolator(EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> outgoing.setVisibility(View.GONE))
                    .start();
        }
    }

    private static boolean animationsEnabled(View view) {
        try {
            if (view != null && MotionGate.INSTANCE.isReduceMotion(view.getContext())) return false;
            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return false;
            float scale = Settings.Global.getFloat(
                    view.getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f);
            return scale > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }
}
