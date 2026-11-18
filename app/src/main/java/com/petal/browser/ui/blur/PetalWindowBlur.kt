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
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Universal live background blur: blurs whatever is visible BEHIND a window (overflow menu, bottom
 * sheet, dialog). It never touches the surface that sits on top, so it works the same in Material 3
 * Expressive and in Liquid Glass, and it never reads any Liquid Glass preference.
 *
 * Two engines, picked per device (see [PetalBlurMethod]):
 *  - SYSTEM: the OS compositor blur (`blurBehindRadius`, Android 12+). Truly live and free, but many
 *    OEM builds switch it off (`isCrossWindowBlurEnabled == false`), and then it cannot work at all.
 *  - IN_APP: no OS support needed. The app window is captured with PixelCopy at low resolution
 *    (which includes Gecko's SurfaceView and video), blurred on a background thread, and drawn as the
 *    background of the overlay window. Live at about 20 fps, works on Android 8+.
 */
enum class PetalBlurSurface(val key: String) {
    OVERFLOW_MENU("sp_blur_overflow_menu"),
    SHEETS("sp_blur_sheets"),
    DIALOGS("sp_blur_dialogs"),
    TAB_MANAGER("sp_blur_tab_manager"),
    MEDIA("sp_blur_media"),
}

/** What the user picked in settings. */
enum class PetalBlurMethod(val id: String) {
    AUTO("auto"),
    SYSTEM("system"),
    IN_APP("inapp");

    companion object {
        fun fromId(id: String?): PetalBlurMethod = values().firstOrNull { it.id == id } ?: AUTO
    }
}

/** What is actually running for a surface right now. */
enum class PetalBlurMode { SYSTEM, IN_APP, NONE }

object PetalBlurPrefs {
    const val KEY_ENABLED = "sp_blur_enabled"
    const val KEY_STRENGTH = "sp_blur_strength"
    const val KEY_DIM = "sp_blur_dim"
    const val KEY_METHOD = "sp_blur_method"
    const val PREFIX = "sp_blur_"

    /** Pre-rework per-surface key for the overflow menu. Read once as a fallback, never written. */
    private const val LEGACY_OVERFLOW_KEY = "sp_overflow_menu_blur_bg"

    const val DEFAULT_STRENGTH_DP = 24f
    const val DEFAULT_DIM = 0.12f
    const val MAX_STRENGTH_DP = 60f
    const val MAX_DIM = 0.5f

    /** Hard cap on the radius handed to the compositor, in px. */
    private const val MAX_RADIUS_PX = 200

    private fun surfaceDefault(sp: SharedPreferences, surface: PetalBlurSurface): Boolean =
        if (surface == PetalBlurSurface.OVERFLOW_MENU && sp.contains(LEGACY_OVERFLOW_KEY)) {
            sp.getBoolean(LEGACY_OVERFLOW_KEY, true)
        } else true

    @JvmStatic
    fun surfaceEnabled(sp: SharedPreferences, surface: PetalBlurSurface): Boolean {
        if (!sp.getBoolean(KEY_ENABLED, true)) return false
        return sp.getBoolean(surface.key, surfaceDefault(sp, surface))
    }

    /** Value shown by the settings toggle for [surface] (ignores the master switch). */
    @JvmStatic
    fun surfaceToggle(sp: SharedPreferences, surface: PetalBlurSurface): Boolean =
        sp.getBoolean(surface.key, surfaceDefault(sp, surface))

    @JvmStatic
    fun strengthDp(sp: SharedPreferences): Float =
        sp.getFloat(KEY_STRENGTH, DEFAULT_STRENGTH_DP).coerceIn(0f, MAX_STRENGTH_DP)

    @JvmStatic
    fun dim(sp: SharedPreferences): Float =
        sp.getFloat(KEY_DIM, DEFAULT_DIM).coerceIn(0f, MAX_DIM)

    @JvmStatic
    fun method(sp: SharedPreferences): PetalBlurMethod =
        PetalBlurMethod.fromId(sp.getString(KEY_METHOD, PetalBlurMethod.AUTO.id))

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
    fun resolveMode(method: PetalBlurMethod, osAvailable: Boolean): PetalBlurMode = when (method) {
        PetalBlurMethod.AUTO -> if (osAvailable) PetalBlurMode.SYSTEM else PetalBlurMode.IN_APP
        PetalBlurMethod.SYSTEM -> if (osAvailable) PetalBlurMode.SYSTEM else PetalBlurMode.NONE
        PetalBlurMethod.IN_APP -> PetalBlurMode.IN_APP
    }

    @JvmStatic
    fun radiusPx(strengthDp: Float, density: Float): Int =
        (strengthDp * density).toInt().coerceIn(0, MAX_RADIUS_PX)
}

/**
 * Resolved blur settings for one surface. [enabled] is what the user asked for; [mode] is the engine
 * that will run (NONE when disabled, or when System was forced but the OS cannot blur). When blur is
 * not [active] callers use a plain dim.
 */
@Immutable
data class PetalBlurState(
    val enabled: Boolean,
    val mode: PetalBlurMode,
    val strengthDp: Float,
    val dim: Float,
) {
    val active: Boolean get() = enabled && mode != PetalBlurMode.NONE
}

/**
 * True when the host activity is translucent (e.g. the APK installer floating over another app). Its
 * own window is empty, so the in-app capture would only ever blur black: only the OS blur can work.
 */
internal fun isTranslucentHost(context: Context): Boolean {
    return try {
        val a = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
        val v = a.getBoolean(0, false)
        a.recycle()
        v && findActivity(context) !is com.petal.browser.activity.MainActivity
    } catch (_: Throwable) {
        false
    }
}

private fun readState(
    sp: SharedPreferences,
    surface: PetalBlurSurface,
    osAvailable: Boolean,
    translucentHost: Boolean = false,
): PetalBlurState {
    val enabled = PetalBlurPrefs.surfaceEnabled(sp, surface)
    var resolved = if (enabled) PetalBlurPrefs.resolveMode(PetalBlurPrefs.method(sp), osAvailable) else PetalBlurMode.NONE
    if (translucentHost && resolved == PetalBlurMode.IN_APP) resolved = PetalBlurMode.NONE
    return PetalBlurState(
        enabled = enabled,
        mode = resolved,
        strengthDp = PetalBlurPrefs.strengthDp(sp),
        dim = PetalBlurPrefs.dim(sp),
    )
}

private fun findActivity(context: Context): Activity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Compose Dialog wrappers reset the window background to transparent; never clobber a blur backdrop. */
fun keepTransparentWindowBackground(window: Window) {
    if (window.decorView.background !is PetalBackdropDrawable) {
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    }
}

// ---------------------------------------------------------------------------------------------
// SYSTEM engine targets
// ---------------------------------------------------------------------------------------------

/** Where the OS blur is written: a real [Window] (Dialog) or a view added straight to the window manager. */
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

// ---------------------------------------------------------------------------------------------
// IN_APP engine: PixelCopy -> low-res box blur -> window background
// ---------------------------------------------------------------------------------------------

/** Background drawable that shows the latest blurred frame stretched over the whole window. */
class PetalBackdropDrawable : Drawable() {
    @Volatile
    var bitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var fade = 0

    override fun draw(canvas: Canvas) {
        val b = bitmap ?: return
        if (b.isRecycled || fade <= 0) return
        paint.alpha = fade
        canvas.drawBitmap(b, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        if (fade != alpha) {
            fade = alpha
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = fade
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private object PetalBackdropThread {
    val handler: Handler by lazy {
        val t = HandlerThread("PetalBackdropBlur").apply { start() }
        Handler(t.looper)
    }
}

/** Captures the host activity window at low resolution, blurs it, and feeds [drawable]. Main thread API. */
internal class PetalBackdropCapturer(
    private val hostWindow: () -> Window?,
    private val drawable: PetalBackdropDrawable,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var radiusPx = 0
    private var running = false
    @Volatile private var inFlight = false
    private var copy: Bitmap? = null
    private var svCopy: Bitmap? = null
    private var composed: Bitmap? = null
    private val outs = arrayOfNulls<Bitmap>(2)
    private var outIndex = 0
    private var pixelsA = IntArray(0)
    private var pixelsB = IntArray(0)

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            capture()
            main.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    fun setRadius(px: Int) {
        radiusPx = px
    }

    fun start() {
        if (running) return
        running = true
        main.post(tick)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
    }

    private fun capture() {
        if (inFlight) return
        val window = hostWindow() ?: return
        val decor = window.decorView
        if (!decor.isAttachedToWindow || decor.width <= 0 || decor.height <= 0) return
        val w = max(MIN_SIDE, decor.width / DOWNSCALE)
        val h = max(MIN_SIDE, decor.height / DOWNSCALE)
        var bmp = copy
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            copy = bmp
        }
        // PixelCopy of a Window only returns the window's own buffer. A SurfaceView (GeckoView's page)
        // is a separate layer that leaves a transparent hole there, which used to be blurred as black.
        // Find it now (main thread) and copy it separately, then lay the window capture on top.
        val sv = findVisibleSurfaceView(decor)
        var svRect: Rect? = null
        if (sv != null) {
            val loc = IntArray(2)
            sv.getLocationInWindow(loc)
            val dl = IntArray(2)
            decor.getLocationInWindow(dl)
            val sx = w.toFloat() / decor.width
            val sy = h.toFloat() / decor.height
            val l = ((loc[0] - dl[0]) * sx).roundToInt()
            val t = ((loc[1] - dl[1]) * sy).roundToInt()
            svRect = Rect(l, t, l + max(1, (sv.width * sx).roundToInt()), t + max(1, (sv.height * sy).roundToInt()))
        }
        inFlight = true
        try {
            val target = bmp
            PixelCopy.request(window, target, { result ->
                if (result != PixelCopy.SUCCESS) {
                    inFlight = false
                } else if (sv != null && svRect != null) {
                    copySurfaceView(sv, svRect, target)
                } else {
                    process(target)
                }
            }, PetalBackdropThread.handler)
        } catch (_: Throwable) {
            inFlight = false
        }
    }

    /** Runs on the blur thread. Falls back to the window capture alone when the surface has no frame. */
    private fun copySurfaceView(sv: SurfaceView, rect: Rect, windowCopy: Bitmap) {
        try {
            val rw = max(1, rect.width())
            val rh = max(1, rect.height())
            var s = svCopy
            if (s == null || s.width != rw || s.height != rh) {
                s = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                svCopy = s
            }
            val dst = s
            PixelCopy.request(sv, dst, { result ->
                try {
                    if (result == PixelCopy.SUCCESS) {
                        var c = composed
                        if (c == null || c.width != windowCopy.width || c.height != windowCopy.height) {
                            c = Bitmap.createBitmap(windowCopy.width, windowCopy.height, Bitmap.Config.ARGB_8888)
                            composed = c
                        }
                        val canvas = Canvas(c)
                        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                        canvas.drawBitmap(dst, null, rect, null)
                        canvas.drawBitmap(windowCopy, 0f, 0f, null)
                        process(c)
                    } else {
                        process(windowCopy)
                    }
                } catch (_: Throwable) {
                    inFlight = false
                }
            }, PetalBackdropThread.handler)
        } catch (_: Throwable) {
            process(windowCopy)
        }
    }

    private fun findVisibleSurfaceView(root: View): SurfaceView? {
        if (root.visibility != View.VISIBLE) return null
        if (root is SurfaceView) {
            return if (root.width > 0 && root.height > 0 && root.holder.surface?.isValid == true) root else null
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findVisibleSurfaceView(root.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** Runs on the blur thread. */
    private fun process(src: Bitmap) {
        try {
            val w = src.width
            val h = src.height
            val n = w * h
            if (pixelsA.size != n) {
                pixelsA = IntArray(n)
                pixelsB = IntArray(n)
            }
            src.getPixels(pixelsA, 0, w, 0, 0, w, h)
            // radiusPx is the full-resolution blur radius; the capture is DOWNSCALE times smaller.
            val r = (radiusPx * SIGMA_PER_RADIUS / DOWNSCALE).roundToInt()
            val blurred = if (r >= 1) boxBlur3(pixelsA, pixelsB, w, h, r) else pixelsA
            var out = outs[outIndex]
            if (out == null || out.width != w || out.height != h) {
                out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                outs[outIndex] = out
            }
            out.setPixels(blurred, 0, w, 0, 0, w, h)
            val ready = out
            outIndex = 1 - outIndex
            main.post {
                drawable.bitmap = ready
                drawable.invalidateSelf()
                inFlight = false
            }
        } catch (_: Throwable) {
            inFlight = false
        }
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 50L // ~20 fps
        const val DOWNSCALE = 6
        const val MIN_SIDE = 48
        /** Window blur radius to Gaussian sigma, in full-resolution px. Tuned by eye, adjust on device. */
        const val SIGMA_PER_RADIUS = 0.6f
    }
}

/** Three box-blur passes (about a Gaussian) on both axes. Returns the array holding the result. */
private fun boxBlur3(a: IntArray, b: IntArray, w: Int, h: Int, r: Int): IntArray {
    var src = a
    var dst = b
    repeat(3) {
        for (y in 0 until h) blurLine(src, dst, y * w, 1, w, r)
        var t = src; src = dst; dst = t
        for (x in 0 until w) blurLine(src, dst, x, w, h, r)
        t = src; src = dst; dst = t
    }
    return src
}

private fun blurLine(src: IntArray, dst: IntArray, start: Int, stride: Int, len: Int, r: Int) {
    val div = 2 * r + 1
    var rs = 0
    var gs = 0
    var bs = 0
    for (i in -r..r) {
        val p = src[start + min(len - 1, max(0, i)) * stride]
        rs += (p shr 16) and 0xFF
        gs += (p shr 8) and 0xFF
        bs += p and 0xFF
    }
    for (i in 0 until len) {
        dst[start + i * stride] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
        val add = src[start + min(len - 1, i + r + 1) * stride]
        val sub = src[start + max(0, i - r) * stride]
        rs += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
        gs += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
        bs += (add and 0xFF) - (sub and 0xFF)
    }
}

// ---------------------------------------------------------------------------------------------
// Controller: runs one overlay window's blur with either engine and fades it in and out
// ---------------------------------------------------------------------------------------------

/**
 * Main thread only. [update] is cheap and idempotent: call it whenever any input changes.
 * [setBackdrop] installs (or, with null, removes) the in-app blur drawable as the window background.
 */
internal class PetalBlurController(
    private val target: PetalBlurTarget,
    private val setBackdrop: (Drawable?) -> Unit,
    private val hostWindow: () -> Window?,
    private val manageDim: Boolean = true,
) {
    private var animator: ValueAnimator? = null
    private var animGoal = -1f
    private var fraction = 0f
    private var mode = PetalBlurMode.NONE
    private var radiusPx = 0
    private var blurDim = 0f
    private var fallbackDim: Float? = 0f
    private var released = false
    private var drawable: PetalBackdropDrawable? = null
    private var capturer: PetalBackdropCapturer? = null

    /** [fallbackDim] null leaves the window's own dim alone while blur is not running. */
    fun update(mode: PetalBlurMode, visible: Boolean, radiusPx: Int, blurDim: Float, fallbackDim: Float?) {
        if (released) return
        switchMode(mode)
        this.radiusPx = radiusPx
        this.blurDim = blurDim
        this.fallbackDim = fallbackDim
        capturer?.setRadius(radiusPx)
        if (mode == PetalBlurMode.NONE) {
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

    private fun switchMode(newMode: PetalBlurMode) {
        if (newMode == mode) return
        when (mode) {
            PetalBlurMode.SYSTEM -> target.reset()
            PetalBlurMode.IN_APP -> stopBackdrop()
            PetalBlurMode.NONE -> {}
        }
        mode = newMode
        if (newMode == PetalBlurMode.IN_APP) startBackdrop()
    }

    private fun startBackdrop() {
        val d = PetalBackdropDrawable()
        drawable = d
        setBackdrop(d)
        capturer = PetalBackdropCapturer(hostWindow, d).also {
            it.setRadius(radiusPx)
            it.start()
        }
    }

    private fun stopBackdrop() {
        capturer?.stop()
        capturer = null
        drawable = null
        setBackdrop(null)
    }

    private fun render() {
        val dim: Float? = if (!manageDim) null
        else if (mode != PetalBlurMode.NONE) blurDim * fraction
        else fallbackDim
        when (mode) {
            PetalBlurMode.SYSTEM -> target.set((radiusPx * fraction).toInt(), dim)
            PetalBlurMode.IN_APP -> {
                drawable?.alpha = (255 * fraction).roundToInt().coerceIn(0, 255)
                target.set(0, dim)
            }
            PetalBlurMode.NONE -> target.set(0, dim)
        }
    }

    fun release() {
        released = true
        animator?.cancel()
        animator = null
        if (mode == PetalBlurMode.IN_APP) stopBackdrop()
        target.reset()
    }
}

// ---------------------------------------------------------------------------------------------
// Compose and View entry points
// ---------------------------------------------------------------------------------------------

/** Live blur settings for [surface], re-evaluated when a preference or the OS blur switch changes. */
@Composable
fun rememberPetalBlurState(surface: PetalBlurSurface): PetalBlurState {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var osAvailable by remember(context) { mutableStateOf(PetalBlurPrefs.isOsBlurAvailable(context)) }
    val translucent = remember(context) { isTranslucentHost(context) }
    var state by remember(sp, surface) { mutableStateOf(readState(sp, surface, osAvailable, translucent)) }
    DisposableEffect(sp, surface, context) {
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(PetalBlurPrefs.PREFIX) || key == "sp_overflow_menu_blur_bg") {
                state = readState(sp, surface, osAvailable, translucent)
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
                    state = readState(sp, surface, enabled, translucent)
                }
                wm?.addCrossWindowBlurEnabledListener(ContextCompat.getMainExecutor(context), osListener)
            } catch (_: Throwable) {
                osListener = null
            }
        }
        // Re-read once now: the OS switch may have changed between composition and this effect.
        osAvailable = PetalBlurPrefs.isOsBlurAvailable(context)
        state = readState(sp, surface, osAvailable, translucent)
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
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val controller = remember(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val host = findActivity(context)?.window
        if (window != null) {
            PetalBlurController(
                target = WindowBlurTarget(window),
                setBackdrop = { d -> window.setBackgroundDrawable(d ?: ColorDrawable(android.graphics.Color.TRANSPARENT)) },
                hostWindow = { host },
                manageDim = manageDim,
            )
        } else {
            PetalBlurController(
                target = ViewBlurTarget(view),
                setBackdrop = { d -> view.rootView.background = d },
                hostWindow = { host },
                manageDim = manageDim,
            )
        }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    // Runs on every state change, including slider drags from Settings while a surface is open.
    DisposableEffect(controller, state, fallbackDim, visible, density) {
        controller.update(
            mode = if (state.enabled) state.mode else PetalBlurMode.NONE,
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
    val host = findActivity(context)?.window
    val controller = PetalBlurController(
        target = WindowBlurTarget(window),
        setBackdrop = { d -> window.setBackgroundDrawable(d ?: ColorDrawable(android.graphics.Color.TRANSPARENT)) },
        hostWindow = { host },
    )
    val density = context.resources.displayMetrics.density
    var osAvailable = PetalBlurPrefs.isOsBlurAvailable(context)
    val translucent = isTranslucentHost(context)

    fun refresh() {
        val state = readState(sp, surface, osAvailable, translucent)
        controller.update(
            mode = state.mode,
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
