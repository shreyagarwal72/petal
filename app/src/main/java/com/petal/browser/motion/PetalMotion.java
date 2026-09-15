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
                .scaleX(pressed ? 0.97f : 1f)
                .scaleY(pressed ? 0.97f : 1f)
                .setDuration(MICRO)
                .setInterpolator(EMPHASIZED)
                .start();
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
