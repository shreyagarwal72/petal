package com.petal.browser.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

object WallpaperBlurEngine {

    /**
     * Applies RenderEffect blur on Android 12+ (API 31+).
     */
    @JvmStatic
    fun applyBlur(view: View?, radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && view != null) {
            try {
                val blurEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                view.setRenderEffect(blurEffect)
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Clears blur effect from view.
     */
    @JvmStatic
    fun clearBlur(view: View?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && view != null) {
            try {
                view.setRenderEffect(null)
            } catch (ignored: Exception) {
            }
        }
    }
}
