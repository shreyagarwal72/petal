package com.petal.browser.unit

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.preference.PreferenceManager

/**
 * PetalHighRefreshRateManager
 * Provides system-wide 120Hz (and up to 144Hz/165Hz/240Hz) high refresh rate locking and surface pacing.
 *
 * Mechanisms utilized:
 * 1. WindowManager.LayoutParams.preferredDisplayModeId targeting the highest refresh rate Display.Mode.
 * 2. WindowManager.LayoutParams.preferredMinDisplayRefreshRate & preferredMaxDisplayRefreshRate (API 30+).
 * 3. Surface.setFrameRate() for View surfaces and SurfaceFlinger synchronization (API 30+).
 * 4. Choreographer & Window refresh rate alignment with user preferences (`sp_high_refresh_rate`).
 */
object PetalHighRefreshRateManager {

    private const val TAG = "PetalRefreshRate"
    const val PREF_HIGH_REFRESH_RATE = "sp_high_refresh_rate"

    /**
     * Queries display modes to determine the highest supported hardware refresh rate.
     */
    @JvmStatic
    fun getMaxSupportedRefreshRate(context: Context?): Float {
        if (context == null) return 60f
        return try {
            val display = getDisplay(context) ?: return 60f
            var maxRate = 60f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = display.supportedModes
                if (modes != null) {
                    for (mode in modes) {
                        if (mode.refreshRate > maxRate) {
                            maxRate = mode.refreshRate
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                maxRate = display.refreshRate
            }
            maxRate
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get max refresh rate: ${e.message}")
            60f
        }
    }

    /**
     * Applies high refresh rate parameters to the given Activity's window if enabled by user settings.
     */
    @JvmStatic
    fun applyHighRefreshRate(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return

        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        val isEnabled = sp.getBoolean(PREF_HIGH_REFRESH_RATE, true)
        if (!isEnabled) {
            resetRefreshRate(activity)
            return
        }

        try {
            val window = activity.window ?: return
            val layoutParams = window.attributes ?: return
            val display = getDisplay(activity) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = display.supportedModes
                if (modes != null && modes.isNotEmpty()) {
                    // Find mode with highest refresh rate, prioritizing modes with the same resolution
                    val currentMode = display.mode
                    var bestMode: Display.Mode? = null
                    var maxRate = 0f

                    for (mode in modes) {
                        val isSameResolution = (mode.physicalWidth == currentMode.physicalWidth &&
                                mode.physicalHeight == currentMode.physicalHeight)
                        val rate = mode.refreshRate

                        if (rate > maxRate || (rate == maxRate && isSameResolution)) {
                            maxRate = rate
                            bestMode = mode
                        }
                    }

                    if (bestMode != null && maxRate >= 90f) {
                        layoutParams.preferredDisplayModeId = bestMode.modeId
                    }
                }
            }

            // Android 11+ (API 30+) explicit refresh rate bounding via reflection / layout params
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val maxRate = getMaxSupportedRefreshRate(activity)
                if (maxRate >= 90f) {
                    setRefreshRateBounds(layoutParams, maxRate)
                }
            }

            window.attributes = layoutParams

            // Apply frame rate hint to decor view surface
            applySurfaceFrameRate(window.decorView, getMaxSupportedRefreshRate(activity))
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying high refresh rate: ${e.message}")
        }
    }

    /**
     * Resets the Activity window refresh rate back to system default/variable.
     */
    @JvmStatic
    fun resetRefreshRate(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        try {
            val window = activity.window ?: return
            val layoutParams = window.attributes ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                layoutParams.preferredDisplayModeId = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setRefreshRateBounds(layoutParams, 0f)
            }
            window.attributes = layoutParams
        } catch (e: Exception) {
            Log.w(TAG, "Failed resetting refresh rate: ${e.message}")
        }
    }

    /**
     * Hints SurfaceFlinger on Android 11+ (API 30+) to pace view surface frame generation at the high rate.
     */
    @JvmStatic
    fun applySurfaceFrameRate(view: View?, targetFrameRate: Float = 120f) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val rate = if (targetFrameRate >= 90f) targetFrameRate else getMaxSupportedRefreshRate(view.context)
                if (rate >= 90f) {
                    val method = View::class.java.getMethod("setFrameRate", Float::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    method.invoke(view, rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Surface frame rate hint not supported or failed: ${e.message}")
            }
        }
    }

    private fun setRefreshRateBounds(layoutParams: WindowManager.LayoutParams, rate: Float) {
        try {
            val minField = WindowManager.LayoutParams::class.java.getField("preferredMinDisplayRefreshRate")
            val maxField = WindowManager.LayoutParams::class.java.getField("preferredMaxDisplayRefreshRate")
            minField.setFloat(layoutParams, rate)
            maxField.setFloat(layoutParams, rate)
        } catch (ignored: Exception) {
            try {
                val prefField = WindowManager.LayoutParams::class.java.getField("preferredRefreshRate")
                prefField.setFloat(layoutParams, rate)
            } catch (ignored2: Exception) {}
        }
    }

    private fun getDisplay(context: Context): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display
            } catch (e: Exception) {
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                dm?.getDisplay(Display.DEFAULT_DISPLAY)
            }
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay
        }
    }
}
