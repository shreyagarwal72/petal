package com.petal.browser.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.graphics.shapes.toPath
import com.petal.browser.ui.theme.PetalFontHelper
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the Material 3 Expressive shapes (cookies, clovers, bursts...) the widgets use for their badge, buttons
 * and tiles. RemoteViews can't clip to an arbitrary shape, so each shape is rasterised once from the real
 * [androidx.compose.material3.MaterialShapes] polygon and handed to Glance as a small bitmap.
 *
 * Keeping these small matters: bitmaps count against the RemoteViews memory budget, and a blown budget is what
 * turns a widget into "Can't load widget". Sizes are capped at 3x density and 256px, and identical requests share
 * one cached bitmap.
 */
internal object PetalWidgetShapes {

    private val cache = object : LinkedHashMap<String, Bitmap>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 24
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    /**
     * A [shape] filled with [color], [sizeDp] square. [glyph] (a letter such as the "P" badge) is drawn in
     * [glyphColor] on top; [rim] (0..1) adds the soft specular rim the liquid-glass look uses.
     */
    fun bitmap(
        context: Context,
        shape: PetalWidgetShape,
        sizeDp: Int,
        color: Int,
        glyph: String? = null,
        glyphColor: Int = 0,
        rim: Float = 0f,
        rotationDeg: Float = 0f
    ): Bitmap {
        val density = context.resources.displayMetrics.density.coerceIn(1f, 3f)
        val px = (sizeDp * density).roundToInt().coerceIn(16, 256)
        val key = "${shape.id}|$px|$color|$glyph|$glyphColor|${(rim * 100).roundToInt()}|${rotationDeg.roundToInt()}"
        synchronized(cache) { cache[key]?.let { if (!it.isRecycled) return it } }

        val bmp = draw(context, shape, px, color, glyph, glyphColor, rim, rotationDeg)
        synchronized(cache) { cache[key] = bmp }
        return bmp
    }

    private fun draw(
        context: Context,
        shape: PetalWidgetShape,
        px: Int,
        color: Int,
        glyph: String?,
        glyphColor: Int,
        rim: Float,
        rotationDeg: Float
    ): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val path = shape.polygon().toPath()
        // Fit whatever coordinate space the polygon uses into the bitmap, centred, so a shape that isn't
        // normalised to a unit box still lands inside the canvas.
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val side = max(bounds.width(), bounds.height()).coerceAtLeast(0.0001f)
        val inset = max(1f, px * 0.02f)
        val scale = (px - 2f * inset) / side
        val matrix = Matrix().apply {
            postTranslate(-bounds.centerX(), -bounds.centerY())
            if (rotationDeg != 0f) postRotate(rotationDeg)
            postScale(scale, scale)
            postTranslate(px / 2f, px / 2f)
        }
        path.transform(matrix)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawPath(path, fill)

        if (rim > 0f) {
            val strokePx = max(1f, 1.5f * context.resources.displayMetrics.density.coerceAtMost(3f))
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                shader = LinearGradient(
                    0f, 0f, px.toFloat(), px.toFloat(),
                    android.graphics.Color.argb((180 * rim).roundToInt().coerceIn(30, 220), 255, 255, 255),
                    android.graphics.Color.argb((40 * rim).roundToInt().coerceIn(8, 90), 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(path, rimPaint)
        }

        if (!glyph.isNullOrEmpty()) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = glyphColor
                textSize = px * (if (glyph.length > 1) 0.40f else 0.52f)
                textAlign = Paint.Align.CENTER
                typeface = try {
                    PetalFontHelper.getAppTypeface(context)
                } catch (_: Throwable) {
                    android.graphics.Typeface.DEFAULT_BOLD
                }
            }
            val fm = text.fontMetrics
            canvas.drawText(glyph, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, text)
        }
        return bmp
    }
}
