package com.petal.browser.island

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.RoundedCorner
import android.view.View

/**
 * Reads physical screen geometry — the front-camera cutout and rounded corners —
 * from a live View or DisplayManager. Adapted from expressive-cutout for Petal.
 */
object CutoutMetrics {

    /**
     * The centre of the device's camera cutout in [view]'s pixel coordinates, or null when the
     * device has no cutout.
     */
    fun cutoutCenterPx(view: View): PointF? {
        val insets = view.rootWindowInsets ?: return null
        val bounds = boundsOf(insets.displayCutout) ?: return null
        return PointF(bounds.centerX(), bounds.centerY())
    }

    /**
     * Calculates the horizontal offset from the display center in dp.
     * Positive = camera is right of center; Negative = camera is left of center; 0 = dead center.
     */
    fun cutoutHorizontalOffsetDp(view: View): Float {
        val center = cutoutCenterPx(view) ?: return 0f
        val screenWidthPx = view.resources.displayMetrics.widthPixels
        val density = view.resources.displayMetrics.density.coerceAtLeast(1f)
        return (center.x - screenWidthPx / 2f) / density
    }

    /**
     * The camera cutout's bounding box in display pixel coordinates.
     */
    fun displayCutoutBoundsPx(context: Context): RectF? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return null
        return boundsOf(display.cutout)
    }

    /**
     * The bounding box of [cutout], preferring exact outline path on API 31+.
     */
    fun boundsOf(cutout: android.view.DisplayCutout?): RectF? {
        if (cutout == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val path = cutout.cutoutPath
            if (path != null) {
                val bounds = RectF()
                path.computeBounds(bounds, true)
                if (!bounds.isEmpty) return bounds
            }
        }
        val rect = cutout.boundingRects.maxByOrNull { it.width() * it.height() } ?: return null
        return RectF(rect)
    }

    /**
     * The display's rounded-corner radius in pixels.
     */
    fun screenCornerRadiusPx(view: View): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val insets = view.rootWindowInsets ?: return null
        return listOf(
            RoundedCorner.POSITION_TOP_LEFT,
            RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT,
            RoundedCorner.POSITION_BOTTOM_RIGHT,
        ).mapNotNull { insets.getRoundedCorner(it)?.radius }
            .maxOrNull()
            ?.takeIf { it > 0 }
    }
}
