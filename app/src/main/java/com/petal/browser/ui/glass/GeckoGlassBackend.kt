package com.petal.browser.ui.glass

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import org.mozilla.geckoview.GeckoView

/**
 * Manages the GeckoView rendering backend (SurfaceView vs TextureView).
 *
 * SurfaceView is the high-performance default (direct hardware overlay, zero compositing overhead,
 * no frame-listener latency, and no undrawn grey strips). TextureView is only enabled when Liquid Glass
 * is explicitly enabled by the user and requires sampling the live page behind translucent bars.
 */
object GeckoGlassBackend {

    const val SCROLL_IDLE_DELAY_MS = 300L
    private const val MIN_BAR_GLASS_RAM_BYTES = 5_200L * 1024L * 1024L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Compose-observable scroll active flag so bar glass falls back to solid M3 during scroll/fling. */
    var isScrolling by mutableStateOf(false)
        private set

    @Volatile
    var isFlinging: Boolean = false
        private set

    @Volatile
    private var isScrollingVolatile: Boolean = false

    @Volatile
    private var lastScrollUptimeMs: Long = 0L

    @Volatile
    private var idleCheckScheduled: Boolean = false

    @Volatile
    var onScrollIdleCallback: (() -> Unit)? = null

    @Volatile
    var onBackendSwitchedCallback: ((GeckoView) -> Unit)? = null

    private val scrollIdleRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastScrollUptimeMs
            if (elapsed >= SCROLL_IDLE_DELAY_MS) {
                idleCheckScheduled = false
                isScrollingVolatile = false
                isFlinging = false
                isScrolling = false
                onScrollIdleCallback?.invoke()
            } else {
                mainHandler.postDelayed(this, SCROLL_IDLE_DELAY_MS - elapsed)
            }
        }
    }

    /** Marks page scroll/fling activity and schedules glass/edge restore ~300 ms after idle. */
    @JvmStatic
    fun notifyScrollActivity(flinging: Boolean = false) {
        lastScrollUptimeMs = SystemClock.uptimeMillis()
        if (flinging) isFlinging = true
        isScrollingVolatile = true
        val activateOnMain = Runnable {
            isScrolling = true
            if (!idleCheckScheduled) {
                idleCheckScheduled = true
                mainHandler.postDelayed(scrollIdleRunnable, SCROLL_IDLE_DELAY_MS)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            activateOnMain.run()
        } else {
            mainHandler.post(activateOnMain)
        }
    }

    @JvmStatic
    fun isScrollingOrFlinging(): Boolean = isScrollingVolatile || isFlinging

    /**
     * Live page-sampling glass for the address bar and bottom nav defaults to OFF on devices
     * below a capable performance class (low-RAM devices, <=4 CPU cores, <6 GB RAM, or API <31
     * media performance class), while still honoring explicit user toggles.
     */
    @JvmStatic
    fun defaultBarGlassEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (com.petal.browser.motion.MotionGate.isLowEndDevice(context)) return false
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                if (memInfo.totalMem in 1L until MIN_BAR_GLASS_RAM_BYTES) return false
            }
            if (Build.VERSION.MEDIA_PERFORMANCE_CLASS in 1 until Build.VERSION_CODES.S) {
                return false
            }
        } catch (_: Throwable) {}
        return true
    }

    @JvmStatic
    fun isAddressBarGlassEnabled(sp: SharedPreferences, context: Context): Boolean =
        sp.getBoolean("sp_liquid_glass_address_bar", defaultBarGlassEnabled(context))

    @JvmStatic
    fun isBottomNavGlassEnabled(sp: SharedPreferences, context: Context): Boolean =
        sp.getBoolean("sp_liquid_glass_bottom_nav", defaultBarGlassEnabled(context))

    @JvmStatic
    fun shouldSamplePage(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (isScrollingOrFlinging() || com.petal.browser.ui.components.BarCollapseState.isCollapsed) return false
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (!com.petal.browser.ui.containment.isLiquidGlassMaster(sp)) return false
        return isAddressBarGlassEnabled(sp, context) || isBottomNavGlassEnabled(sp, context)
    }

    @JvmStatic
    fun applyBackend(geckoView: GeckoView, context: Context) {
        // Never rebuild the SurfaceView/TextureView while a scroll or fling is in flight.
        if (isScrollingOrFlinging()) return
        // Match official Mozilla GeckoView standard: default to BACKEND_SURFACE_VIEW
        // for optimal hardware composition and reliable web page rendering.
        // Switch to BACKEND_TEXTURE_VIEW only when Liquid Glass requires sampling the page.
        val wantTexture = shouldSamplePage(context)
        val hasTexture = findTextureView(geckoView) != null
        // Already correct: never rebuild the surface for nothing (every rebuild drops the frame).
        if (wantTexture == hasTexture) return
        switchBackend(geckoView, wantTexture)
    }

    /**
     * Switches the surface while a session may be attached.
     *
     * Why this is needed (read from Mozilla's own source, GeckoView.java + SurfaceViewWrapper.java):
     *  - SurfaceViewWrapper.useSurfaceView() builds a NEW SurfaceView but never registers the
     *    SurfaceHolder.Callback on it (only setListener(), called once from the GeckoView
     *    constructor, does that). After TextureView -> SurfaceView, Gecko therefore never receives
     *    a surface and the page stays black. That is the "black screen when Liquid Glass is
     *    toggled" bug. We re-register the same callback ourselves.
     *  - The session is released first and set again afterwards so the compositor never draws
     *    into a surface that is being torn down. GeckoView re-acquires the display when the new
     *    surface reports in.
     */
    private fun switchBackend(geckoView: GeckoView, toTexture: Boolean) {
        val released = try { geckoView.releaseSession() } catch (_: Throwable) { null }
        try {
            if (toTexture) {
                useTextureView(geckoView)
            } else {
                // The TextureView's listener is Mozilla's ListenerWrapper, which also implements
                // SurfaceHolder.Callback. Grab it before the TextureView is replaced.
                val wrapper = findTextureView(geckoView)?.surfaceTextureListener
                useSurfaceView(geckoView)
                val surfaceView = findSurfaceView(geckoView)
                if (wrapper is SurfaceHolder.Callback && surfaceView != null) {
                    surfaceView.holder.addCallback(wrapper)
                } else if (!reattachHolderCallbackByReflection(geckoView)) {
                    android.util.Log.w("GeckoGlassBackend", "Could not re-attach the SurfaceHolder callback")
                }
            }
        } finally {
            if (released != null) {
                try { geckoView.setSession(released) } catch (_: Throwable) {}
            }
            onBackendSwitchedCallback?.invoke(geckoView)
        }
    }

    /** Fallback if Mozilla renames its internals: look the callback up by type instead of by name. */
    private fun reattachHolderCallbackByReflection(geckoView: GeckoView): Boolean {
        return try {
            val wrapperField = GeckoView::class.java.getDeclaredField("mSurfaceWrapper")
            wrapperField.isAccessible = true
            val wrapper = wrapperField.get(geckoView) ?: return false
            val cbField = wrapper.javaClass.declaredFields.firstOrNull {
                SurfaceHolder.Callback::class.java.isAssignableFrom(it.type)
            } ?: return false
            cbField.isAccessible = true
            val callback = cbField.get(wrapper) as? SurfaceHolder.Callback ?: return false
            val surfaceView = findSurfaceView(geckoView) ?: return false
            surfaceView.holder.addCallback(callback)
            true
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun useSurfaceView(geckoView: GeckoView) {
        try {
            geckoView.setViewBackend(GeckoView.BACKEND_SURFACE_VIEW)
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun useTextureView(geckoView: GeckoView) {
        try {
            geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
        } catch (_: Throwable) {}
    }

    private fun findSurfaceView(root: View): SurfaceView? {
        if (root is SurfaceView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findSurfaceView(root.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun findTextureView(root: View): TextureView? {
        if (root is TextureView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findTextureView(root.getChildAt(i))?.let { return it }
        }
        return null
    }

    private class FrameQuietListener(private val inner: TextureView.SurfaceTextureListener) :
        TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) =
            inner.onSurfaceTextureAvailable(surface, width, height)

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) =
            inner.onSurfaceTextureSizeChanged(surface, width, height)

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean =
            inner.onSurfaceTextureDestroyed(surface)

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            try {
                inner.onSurfaceTextureUpdated(surface)
            } catch (_: Throwable) {}
        }
    }
}
