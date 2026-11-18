/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.petal.browser.ui.blur

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.util.function.Consumer

/**
 * Universal live background blur: blurs whatever is visible BEHIND a window (overflow menu, bottom
 * sheet, dialog, popup). It uses the OS compositor blur (`blurBehindRadius`, Android 12+), so it is
 * truly live, works over Gecko's SurfaceView and over video, and costs nothing on the UI thread.
 *
 * It never touches the surface that sits on top, so it works the same in Material 3 Expressive and in
 * Liquid Glass, and it never reads any Liquid Glass preference. The only thing a style changes is the
 * fallback dim the caller passes in.
 */
enum class PetalBlurSurface(val key: String) {
    OVERFLOW_MENU("sp_blur_overflow_menu"),
    SHEETS("sp_blur_sheets"),
    DIALOGS("sp_blur_dialogs"),
    TAB_MANAGER("sp_blur_tab_manager"),
    MEDIA("sp_blur_media"),
}

object PetalBlurPrefs {
    const val KEY_ENABLED = "sp_blur_enabled"
    const val KEY_STRENGTH = "sp_blur_strength"
    const val KEY_DIM = "sp_blur_dim"
    const val PREFIX = "sp_blur_"

    /** Pre-rework per-surface key for the overflow menu. Read once as a fallback, never written. */
    private const val LEGACY_OVERFLOW_KEY = "sp_overflow_menu_blur_bg"

    const val DEFAULT_STRENGTH_DP = 24f
    const val DEFAULT_DIM = 0.12f
    const val MAX_STRENGTH_DP = 60f
    const val MAX_DIM = 0.5f

    /** Hard cap on the radius handed to the compositor, in px. */
    private const val MAX_RADIUS_PX = 200

    @JvmStatic
    fun surfaceEnabled(sp: SharedPreferences, surface: PetalBlurSurface): Boolean {
        if (!sp.getBoolean(KEY_ENABLED, true)) return false
        val default = if (surface == PetalBlurSurface.OVERFLOW_MENU && sp.contains(LEGACY_OVERFLOW_KEY)) {
            sp.getBoolean(LEGACY_OVERFLOW_KEY, true)
        } else true
        return sp.getBoolean(surface.key, default)
    }

    /** Value shown by the settings toggle for [surface] (ignores the master switch). */
    @JvmStatic
    fun surfaceToggle(sp: SharedPreferences, surface: PetalBlurSurface): Boolean {
        val default = if (surface == PetalBlurSurface.OVERFLOW_MENU && sp.contains(LEGACY_OVERFLOW_KEY)) {
            sp.getBoolean(LEGACY_OVERFLOW_KEY, true)
        } else true
        return sp.getBoolean(surface.key, default)
    }

    @JvmStatic
    fun strengthDp(sp: SharedPreferences): Float =
        sp.getFloat(KEY_STRENGTH, DEFAULT_STRENGTH_DP).coerceIn(0f, MAX_STRENGTH_DP)

    @JvmStatic
    fun dim(sp: SharedPreferences): Float =
        sp.getFloat(KEY_DIM, DEFAULT_DIM).coerceIn(0f, MAX_DIM)

    /** True when the OS can blur behind windows right now (Android 12+, not disabled by policy or battery saver). */
    @JvmStatic
    fun isOsBlurAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun radiusPx(strengthDp: Float, density: Float): Int =
        (strengthDp * density).toInt().coerceIn(0, MAX_RADIUS_PX)
}

/**
 * Resolved blur settings for one surface. [enabled] is what the user asked for; [active] additionally
 * requires the OS to be able to blur right now. When [enabled] but not [active] callers use a dim.
 */
@Immutable
data class PetalBlurState(
    val enabled: Boolean,
    val active: Boolean,
    val strengthDp: Float,
    val dim: Float,
)

/** Where a blur is written: a real [Window] (Dialog) or a view added straight to the window manager. */
internal interface PetalBlurTarget {
    /** [dim] null leaves the window's own dim untouched. */
    fun set(radiusPx: Int, dim: Float?)
    fun reset()
}

private class WindowBlurTarget(private val window: Window) : PetalBlurTarget {
    override fun set(radiusPx: Int, dim: Float?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (radiusPx > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = window.attributes
                lp.blurBehindRadius = radiusPx
                window.attributes = lp
            }
            if (dim != null) {
                if (dim > 0f) window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(dim)
            }
        } catch (_: Throwable) {
        }
    }

    override fun reset() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = window.attributes
                lp.blurBehindRadius = 0
                window.attributes = lp
            }
        } catch (_: Throwable) {
        }
    }
}

/** For windows Compose adds as a bare view (Material3 ModalBottomSheet in 1.5), which have no [Window]. */
private class ViewBlurTarget(private val view: View) : PetalBlurTarget {
    private fun apply(radiusPx: Int, dim: Float?) {
        try {
            // Resolved per call: the view may not be attached yet when the target is created.
            val root = view.rootView
            val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                lp.blurBehindRadius = radiusPx
                lp.flags = if (radiusPx > 0) lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                else lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            }
            if (dim != null) {
                lp.dimAmount = dim
                lp.flags = if (dim > 0f) lp.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                else lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            }
            root.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, lp)
        } catch (_: Throwable) {
        }
    }

    override fun set(radiusPx: Int, dim: Float?) = apply(radiusPx, dim)
    override fun reset() = apply(0, null)
}

/**
 * Drives one window's blur and fades it in and out. Main thread only.
 * [update] is cheap and idempotent: call it whenever any input changes.
 */
internal class PetalBlurController(
    private val target: PetalBlurTarget,
    private val manageDim: Boolean = true,
) {
    private var animator: ValueAnimator? = null
    private var animGoal = -1f
    private var fraction = 0f
    private var active = false
    private var radiusPx = 0
    private var blurDim = 0f
    private var fallbackDim: Float? = 0f
    private var released = false

    /** [fallbackDim] null leaves the window's own dim alone while blur is not running. */
    fun update(active: Boolean, visible: Boolean, radiusPx: Int, blurDim: Float, fallbackDim: Float?) {
        if (released) return
        this.active = active
        this.radiusPx = radiusPx
        this.blurDim = blurDim
        this.fallbackDim = fallbackDim
        if (!active) {
            animator?.cancel()
            animator = null
            animGoal = -1f
            fraction = 0f
            render()
            return
        }
        val goal = if (visible) 1f else 0f
        if (fraction == goal) {
            animator?.cancel()
            animator = null
            animGoal = -1f
            render()
            return
        }
        if (animator != null && animGoal == goal) {
            render()
            return
        }
        animator?.cancel()
        animGoal = goal
        animator = ValueAnimator.ofFloat(fraction, goal).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fraction = it.animatedValue as Float
                render()
            }
            start()
        }
    }

    private fun render() {
        if (active) {
            target.set((radiusPx * fraction).toInt(), if (manageDim) blurDim * fraction else null)
        } else {
            target.set(0, if (manageDim) fallbackDim else null)
        }
    }

    fun release() {
        released = true
        animator?.cancel()
        animator = null
        target.reset()
    }
}

private fun readState(sp: SharedPreferences, surface: PetalBlurSurface, osAvailable: Boolean): PetalBlurState {
    val enabled = PetalBlurPrefs.surfaceEnabled(sp, surface)
    return PetalBlurState(
        enabled = enabled,
        active = enabled && osAvailable,
        strengthDp = PetalBlurPrefs.strengthDp(sp),
        dim = PetalBlurPrefs.dim(sp),
    )
}

/** Live blur settings for [surface], re-evaluated when a preference or the OS blur switch changes. */
@Composable
fun rememberPetalBlurState(surface: PetalBlurSurface): PetalBlurState {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var osAvailable by remember(context) { mutableStateOf(PetalBlurPrefs.isOsBlurAvailable(context)) }
    var state by remember(sp, surface) { mutableStateOf(readState(sp, surface, osAvailable)) }
    DisposableEffect(sp, surface, context) {
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(PetalBlurPrefs.PREFIX) || key == "sp_overflow_menu_blur_bg") {
                state = readState(sp, surface, osAvailable)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(prefListener)
        var wm: WindowManager? = null
        var osListener: Consumer<Boolean>? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                wm = context.getSystemService(WindowManager::class.java)
                osListener = Consumer { enabled ->
                    osAvailable = enabled
                    state = readState(sp, surface, enabled)
                }
                wm?.addCrossWindowBlurEnabledListener(ContextCompat.getMainExecutor(context), osListener)
            } catch (_: Throwable) {
                osListener = null
            }
        }
        // Re-read once now: the OS switch may have changed between composition and this effect.
        osAvailable = PetalBlurPrefs.isOsBlurAvailable(context)
        state = readState(sp, surface, osAvailable)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(prefListener)
            val l = osListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && l != null) {
                try {
                    wm?.removeCrossWindowBlurEnabledListener(l)
                } catch (_: Throwable) {
                }
            }
        }
    }
    return state
}

/**
 * Blurs behind the window that hosts this composition. Works inside a Compose `Dialog` (has a
 * [Window]) and inside Material3's `ModalBottomSheet` (a bare window-manager view).
 *
 * [fallbackDim] is the window dim used when blur is off or unavailable, so surfaces look exactly as
 * before in that case. Pass [manageDim] = false when the caller draws its own scrim (bottom sheets).
 * [visible] fades the blur out ahead of the surface's own exit animation.
 */
@Composable
fun PetalWindowBlurEffect(
    state: PetalBlurState,
    fallbackDim: Float,
    visible: Boolean = true,
    manageDim: Boolean = true,
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val controller = remember(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val target: PetalBlurTarget = if (window != null) WindowBlurTarget(window) else ViewBlurTarget(view)
        PetalBlurController(target, manageDim)
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    // Runs on every state change, including slider drags from Settings while a surface is open.
    DisposableEffect(controller, state, fallbackDim, visible, density) {
        controller.update(
            active = state.active,
            visible = visible,
            radiusPx = PetalBlurPrefs.radiusPx(state.strengthDp, density),
            blurDim = state.dim,
            fallbackDim = fallbackDim,
        )
        onDispose { }
    }
}

/**
 * For classic android.app.Dialog / BottomSheetDialog windows. Attach once the window exists
 * (onShowListener / onStart). Returns a cleanup to call on dismiss. Settings changes apply live.
 * [fallbackDim] null keeps the dialog's own dim when blur is off or unavailable.
 */
fun bindPetalWindowBlur(
    context: Context,
    window: Window,
    surface: PetalBlurSurface,
    fallbackDim: Float?,
): () -> Unit {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val controller = PetalBlurController(WindowBlurTarget(window))
    val density = context.resources.displayMetrics.density
    var osAvailable = PetalBlurPrefs.isOsBlurAvailable(context)

    fun refresh() {
        val state = readState(sp, surface, osAvailable)
        controller.update(
            active = state.active,
            visible = true,
            radiusPx = PetalBlurPrefs.radiusPx(state.strengthDp, density),
            blurDim = state.dim,
            fallbackDim = fallbackDim,
        )
    }

    val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith(PetalBlurPrefs.PREFIX) || key == "sp_overflow_menu_blur_bg") refresh()
    }
    sp.registerOnSharedPreferenceChangeListener(prefListener)
    var wm: WindowManager? = null
    var osListener: Consumer<Boolean>? = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            wm = context.getSystemService(WindowManager::class.java)
            osListener = Consumer { enabled ->
                osAvailable = enabled
                refresh()
            }
            wm?.addCrossWindowBlurEnabledListener(ContextCompat.getMainExecutor(context), osListener)
        } catch (_: Throwable) {
            osListener = null
        }
    }
    refresh()
    return {
        sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        val l = osListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && l != null) {
            try {
                wm?.removeCrossWindowBlurEnabledListener(l)
            } catch (_: Throwable) {
            }
        }
        controller.release()
    }
}

/**
 * Drop-in scrim for a raw Material3 `ModalBottomSheet`: the light dim over the blurred page while
 * blur runs, otherwise the stock Material scrim (so a sheet looks unchanged when blur is off).
 */
@Composable
fun rememberPetalSheetScrim(surface: PetalBlurSurface): androidx.compose.ui.graphics.Color {
    val state = rememberPetalBlurState(surface)
    return if (state.active) androidx.compose.ui.graphics.Color.Black.copy(alpha = state.dim)
    else androidx.compose.material3.BottomSheetDefaults.ScrimColor
}

/** Put this first inside a raw `ModalBottomSheet { }` to blur behind that sheet's window. */
@Composable
fun PetalSheetBlur(surface: PetalBlurSurface) {
    PetalWindowBlurEffect(
        state = rememberPetalBlurState(surface),
        fallbackDim = 0f,
        visible = true,
        manageDim = false,
    )
}
