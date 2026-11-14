package com.petal.browser.ui.glass

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
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

    @JvmStatic
    fun shouldSamplePage(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val exp = sp.getString("sp_ui_experience", null)
        val isMiuix = exp == com.petal.browser.ui.theme.layout.UiExperience.MIUIX.key
        val master = !isMiuix && (sp.getBoolean("sp_liquid_glass_enabled", false) || (exp == com.petal.browser.ui.theme.layout.UiExperience.LIQUID_GLASS.key))
        if (!master) return false
        val addressGlass = sp.getBoolean("sp_liquid_glass_address_bar", true)
        val bottomGlass = sp.getBoolean("sp_liquid_glass_bottom_nav", true)
        return addressGlass || bottomGlass
    }

    @JvmStatic
    fun applyBackend(geckoView: GeckoView, context: Context) {
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
