package com.petal.browser.ui.components;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

/**
 * WallpaperBlurEngine applies real-time frosted glass backdrop blur to views.
 */
public class WallpaperBlurEngine {

    /**
     * Applies RenderEffect blur on Android 12+ (API 31+).
     */
    public static void applyBlur(View view, float radius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && view != null) {
            try {
                RenderEffect blurEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
                view.setRenderEffect(blurEffect);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Clears blur effect from view.
     */
    public static void clearBlur(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && view != null) {
            try {
                view.setRenderEffect(null);
            } catch (Exception ignored) {}
        }
    }
}
