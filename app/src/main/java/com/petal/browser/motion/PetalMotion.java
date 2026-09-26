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

    /** Animate a newly presented surface without touching its lifecycle. */
    public static void enter(View view) {
        enter(view, TRANSITION);
    }

    public static void enter(View view, long duration) {
        if (view == null) return;
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
                .setDuration(duration)
                .setInterpolator(EMPHASIZED)
                .withLayer()
                .start();
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
                .setDuration(COMPONENT)
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
                .setDuration(MICRO)
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
                .setDuration(TRANSITION)
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
                .setDuration(COMPONENT)
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
                .setDuration(COMPONENT)
                .setInterpolator(EMPHASIZED)
                .withLayer()
                .start();
    }

    /** Smooth cross-fade for seamless surface switching without blank frames. */
    public static void crossfade(View outgoing, View incoming, long duration) {
        if (incoming != null) {
            incoming.setVisibility(View.VISIBLE);
            incoming.setAlpha(0f);
            incoming.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(EMPHASIZED)
                    .start();
        }
        if (outgoing != null) {
            outgoing.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setInterpolator(EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> outgoing.setVisibility(View.GONE))
                    .start();
        }
    }

    private static boolean animationsEnabled(View view) {
        try {
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
