// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.animation.predictiveback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/**
 * A small, capped cache of downsampled screenshots used to render the "last page" preview
 * layer underneath a predictive-back gesture - the same idea as InstallerX-Revived's
 * two-screen NavTransition, where the destination screen is visibly there behind the one
 * being swiped away instead of a flat scrim.
 *
 * Petal Browser doesn't have a Compose nav-graph stack backing every screen (WebView pages,
 * Home, Downloads, History, Settings, Account all just swap views in and out of the same
 * container), so there's no "previous screen" composable to render live. Instead, every
 * relevant surface is asked to leave a lightweight bitmap of itself behind before it's
 * replaced or navigated away from, and predictive-back gestures look that bitmap up by key
 * to use as the underlay.
 */
object PagePreviewCache {

    /** Key used for the browser's home / new-tab surface. */
    const val KEY_HOME: String = "petal_preview_home"

    /**
     * Key used for "whatever the main browser content (address bar + page + bottom nav)
     * looked like" right before a full-screen surface (Settings, Downloads, History,
     * Account) was opened on top of it.
     */
    const val KEY_BROWSER_MAIN: String = "petal_preview_browser_main"

    private const val MAX_ENTRIES = 8
    private const val MAX_DIMENSION_PX = 720

    // Insertion-ordered so eviction can drop the oldest entry once we're over capacity.
    private val cache = LinkedHashMap<String, Bitmap>()

    @JvmStatic
    @Synchronized
    fun get(key: String?): Bitmap? {
        if (key == null) return null
        return cache[key]
    }

    @JvmStatic
    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache.remove(key)
        cache[key] = bitmap
        while (cache.size > MAX_ENTRIES) {
            val oldestKey = cache.keys.firstOrNull() ?: break
            cache.remove(oldestKey)?.let { old ->
                if (!old.isRecycled) old.recycle()
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun remove(key: String) {
        cache.remove(key)?.let { if (!it.isRecycled) it.recycle() }
    }

    /**
     * Renders [view] into a downsampled bitmap and stores it under [key]. Safe to call
     * often (e.g. after every page load) - the bitmap is capped to [MAX_DIMENSION_PX] on
     * its longest side, so memory stays bounded regardless of device resolution.
     */
    @JvmStatic
    fun capture(key: String, view: View) {
        try {
            val width = view.width
            val height = view.height
            if (width <= 0 || height <= 0) return

            val longest = maxOf(width, height)
            val scale = if (longest > MAX_DIMENSION_PX) MAX_DIMENSION_PX.toFloat() / longest else 1f
            val outWidth = (width * scale).toInt().coerceAtLeast(1)
            val outHeight = (height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            view.draw(canvas)

            put(key, bitmap)
        } catch (e: Exception) {
            // Snapshotting is a nice-to-have for the preview layer; never let it crash
            // navigation if a view can't be captured (e.g. mid-teardown).
            e.printStackTrace()
        }
    }

    /** Keys a webview page snapshot by URL so back/forward gestures can look up neighbors. */
    @JvmStatic
    fun keyForUrl(url: String?): String? = if (url.isNullOrBlank()) null else "petal_preview_url::$url"
}
