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

    @Volatile
    private var isApplying = false

    /**
     * Applies high refresh rate parameters to the given Activity's window if enabled by user settings.
     */
    @JvmStatic
    fun applyHighRefreshRate(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        if (isApplying) return
        isApplying = true

        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val isEnabled = sp.getBoolean(PREF_HIGH_REFRESH_RATE, true)
            if (!isEnabled) {
                resetRefreshRate(activity)
                return
            }

            val window = activity.window ?: return
            val layoutParams = window.attributes ?: return
            val display = getDisplay(activity) ?: return

            var attributesChanged = false

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
                        if (!isSameResolution) continue

                        if (rate > maxRate) {
                            maxRate = rate
                            bestMode = mode
                        }
                    }

                    if (bestMode != null && maxRate >= 90f) {
                        if (layoutParams.preferredDisplayModeId != bestMode.modeId) {
                            layoutParams.preferredDisplayModeId = bestMode.modeId
                            attributesChanged = true
                        }
                    }
                }
            }

            // Android 11+ (API 30+) explicit refresh rate bounding via reflection / layout params
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val maxRate = getMaxSupportedRefreshRate(activity)
                if (maxRate >= 90f) {
                    if (setRefreshRateBounds(layoutParams, maxRate)) {
                        attributesChanged = true
                    }
                }
            }

            if (attributesChanged) {
                window.attributes = layoutParams
                Log.d(TAG, "Applied: modeId=${layoutParams.preferredDisplayModeId} max=${getMaxSupportedRefreshRate(activity)}Hz current=${display.refreshRate}Hz")
            }

            // Apply frame rate hint to decor view surface
            applySurfaceFrameRate(window.decorView, getMaxSupportedRefreshRate(activity))
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying high refresh rate: ${e.message}")
        } finally {
            isApplying = false
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
            var changed = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (layoutParams.preferredDisplayModeId != 0) {
                    layoutParams.preferredDisplayModeId = 0
                    changed = true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (setRefreshRateBounds(layoutParams, 0f)) {
                    changed = true
                }
            }
            if (changed) {
                window.attributes = layoutParams
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed resetting refresh rate: ${e.message}")
        }
    }

    /**
     * Hints the compositor to pace [view] at the high rate.
     * API 35+: View.setRequestedFrameRate() on the root view.
     * Older/SurfaceView: Surface.setFrameRate() on SurfaceView found in tree.
     */
    @JvmStatic
    fun applySurfaceFrameRate(view: View?, targetFrameRate: Float = 120f) {
        if (view == null) return
        val rate = if (targetFrameRate >= 90f) targetFrameRate else getMaxSupportedRefreshRate(view.context)
        if (rate < 90f) return
        try {
            if (Build.VERSION.SDK_INT >= 35) {
                try {
                    view.requestedFrameRate = rate
                } catch (_: Throwable) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                paceSurfaceViews(view, rate, 0)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Frame rate hint failed: ${e.message}")
        }
    }

    private fun paceSurfaceViews(view: View, rate: Float, depth: Int) {
        if (depth > 6) return
        if (view is android.view.SurfaceView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val surface = view.holder.surface
            if (surface != null && surface.isValid) {
                try {
                    surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (e: Exception) {
                    Log.d(TAG, "Surface.setFrameRate failed: ${e.message}")
                }
            }
            return
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                paceSurfaceViews(view.getChildAt(i), rate, depth + 1)
            }
        }
    }

    /**
     * DisplayListener registration is made a safe no-op.
     * Listening to DisplayManager.onDisplayChanged to mutate WindowManager.LayoutParams
     * triggers severe feedback loops and main-thread ANRs on dynamic refresh rate (LTPO)
     * devices when refresh rates shift during user touch events.
     * Window attributes are already persisted by WindowManagerService and re-evaluated
     * on standard Activity lifecycle events (onResume, onConfigurationChanged, onWindowFocusChanged).
     */
    @JvmStatic
    fun registerDisplayListener(activity: Activity?) {
        // Safe no-op to eliminate ANR feedback loop
    }

    @JvmStatic
    fun unregisterDisplayListener(activity: Activity?) {
        // Safe no-op
    }

    private fun setRefreshRateBounds(layoutParams: WindowManager.LayoutParams, rate: Float): Boolean {
        var changed = false
        try {
            val minField = WindowManager.LayoutParams::class.java.getField("preferredMinDisplayRefreshRate")
            val maxField = WindowManager.LayoutParams::class.java.getField("preferredMaxDisplayRefreshRate")
            // Floor at 60Hz (or 0 on reset) so the system may still drop for battery / static content;
            // pinning min == max forced a single mode and was ignored by many OEM governors.
            val targetMin = if (rate > 0f) 60f else 0f
            val currentMin = minField.getFloat(layoutParams)
            val currentMax = maxField.getFloat(layoutParams)
            if (currentMin != targetMin) {
                minField.setFloat(layoutParams, targetMin)
                changed = true
            }
            if (currentMax != rate) {
                maxField.setFloat(layoutParams, rate)
                changed = true
            }
        } catch (ignored: Exception) {
            try {
                val prefField = WindowManager.LayoutParams::class.java.getField("preferredRefreshRate")
                val currentPref = prefField.getFloat(layoutParams)
                if (currentPref != rate) {
                    prefField.setFloat(layoutParams, rate)
                    changed = true
                }
            } catch (ignored2: Exception) {}
        }
        return changed
    }

    private fun getDisplay(context: Context): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display ?: (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
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
