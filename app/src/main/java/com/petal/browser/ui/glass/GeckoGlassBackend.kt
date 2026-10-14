package com.petal.browser.ui.glass

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import org.mozilla.geckoview.GeckoView

/**
 * Puts a GeckoView on its TextureView backend, so the page passes through the view
 * hierarchy and the nav bar's liquid glass can sample it. A SurfaceView's pixels never do;
 * under glass the page would be a hole.
 *
 * GeckoView's TextureView listener treats every new frame ([TextureView.SurfaceTextureListener.onSurfaceTextureUpdated])
 * as a brand-new surface: it wraps the SurfaceTexture in a fresh Surface and hands it to
 * Gecko, which composites again, which produces another frame. Left alone that redraws the
 * whole window at full frame rate on a still page. The TextureView takes in Gecko's frames
 * through its own internal listener, so that callback is simply not passed on.
 */
object GeckoGlassBackend {
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

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
}
