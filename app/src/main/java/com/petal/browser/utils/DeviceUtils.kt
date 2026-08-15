/*
 * File: DeviceUtils.kt
 * Description: Device capability helpers used to safely gate expensive
 * rendering effects (e.g. RuntimeShader-based progressive blur).
 * Adapted from sameerasw/essentials (MIT License).
 */

package com.petal.browser.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager

object DeviceUtils {

    /**
     * Some Samsung devices on One UI 7 (Android 15) or below have a broken
     * RuntimeShader/RenderEffect implementation that can cause a gray
     * screen overlay. Disable shader-based blur for them.
     */
    fun isBlurProblematicDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Build.VERSION.SDK_INT <= 35 // Android 15
    }

    /**
     * Skip expensive per-frame blur shaders while the device is in
     * power save mode to avoid extra battery/GPU load.
     */
    fun isPowerSaveMode(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }
}
