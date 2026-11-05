package com.petal.browser.ui.glass

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
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
        val glassEnabled = sp.getBoolean("sp_liquid_glass_enabled", false)
        if (!glassEnabled) return false
        val addressGlass = sp.getBoolean("sp_liquid_glass_address_bar", true)
        val bottomGlass = sp.getBoolean("sp_liquid_glass_bottom_nav", true)
        return addressGlass || bottomGlass
    }

    @JvmStatic
    fun applyBackend(geckoView: GeckoView, context: Context) {
        if (shouldSamplePage(context)) {
            useTextureView(geckoView)
        } else {
            useSurfaceView(geckoView)
        }
    }

    @JvmStatic
    fun useSurfaceView(geckoView: GeckoView) {
        geckoView.setViewBackend(GeckoView.BACKEND_SURFACE_VIEW)
    }

    @JvmStatic
    fun useTextureView(geckoView: GeckoView) {
        geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
        val texture = findTextureView(geckoView) ?: return
        val inner = texture.surfaceTextureListener ?: return
        if (inner is FrameQuietListener) return
        texture.surfaceTextureListener = FrameQuietListener(inner)
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
