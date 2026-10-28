package com.petal.browser.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.DisplayMetrics
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hardware-accelerated and fallback software glass renderer for home screen widgets.
 *
 * Slices the home wallpaper according to the calibrated launcher coordinates, applies
 * vibrant frosted glass blur, a subtle material luminance tint, and a specular rim highlight stroke.
 */
object PetalGlassWidgetRenderer {

    data class Spec(
        val rect: Rect,
        val blurDp: Float = 24f,
        val cornerDp: Float = 24f,
        val dark: Boolean = false,
        val edge: Float = 1f,
        val refraction: Float = 0.5f,
        val tint: Int = 0,
        val tintAlpha: Float = 0f
    )

    private val cache = ConcurrentHashMap<Spec, Bitmap>()
    private var cachedWallpaper: Bitmap? = null
    private var lastWallpaperStamp: Long = 0L

    @Synchronized
    fun forget(appWidgetId: Int? = null) {
        cache.clear()
        cachedWallpaper = null
    }

    /**
     * Renders a glass card bitmap for the given [Spec], or returns the cached version.
     */
    fun render(context: Context, spec: Spec): Bitmap? {
        val rect = spec.rect
        if (rect.width() <= 0 || rect.height() <= 0) return null

        val cached = cache[spec]
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val wp = cachedWallpaper ?: PetalGlassWidgets.getWallpaper(context)?.also {
            cachedWallpaper = it
        } ?: return null

        val rendered = draw(context, wp, spec) ?: return null
        cache[spec] = rendered
        return rendered
    }

    private fun draw(context: Context, wallpaper: Bitmap, spec: Spec): Bitmap? {
        return try {
            val dm: DisplayMetrics = context.resources.displayMetrics
            val density = dm.density

            val rect = spec.rect
            val targetW = max(1, rect.width())
            val targetH = max(1, rect.height())

            // Safeguard bounds within wallpaper bitmap
            val wpW = wallpaper.width
            val wpH = wallpaper.height
            val (dispW, dispH) = PetalGlassWidgets.displaySize(context)

            val scaleX = wpW.toFloat() / max(1, dispW)
            val scaleY = wpH.toFloat() / max(1, dispH)

            val cropLeft = (rect.left * scaleX).roundToInt().coerceIn(0, wpW - 1)
            val cropTop = (rect.top * scaleY).roundToInt().coerceIn(0, wpH - 1)
            val cropRight = (rect.right * scaleX).roundToInt().coerceIn(cropLeft + 1, wpW)
            val cropBottom = (rect.bottom * scaleY).roundToInt().coerceIn(cropTop + 1, wpH)

            val cropW = max(1, cropRight - cropLeft)
            val cropH = max(1, cropBottom - cropTop)

            val subBmp = Bitmap.createBitmap(wallpaper, cropLeft, cropTop, cropW, cropH)
            val scaledSub = if (cropW != targetW || cropH != targetH) {
                val sc = Bitmap.createScaledBitmap(subBmp, targetW, targetH, true)
                if (sc != subBmp) subBmp.recycle()
                sc
            } else {
                subBmp
            }

            // Apply smooth frosted glass blur
            val blurRadiusPx = max(4, (spec.blurDp * density * 0.5f).roundToInt())
            val blurred = fastFrostedBlur(scaledSub, blurRadiusPx)
            if (blurred != scaledSub) scaledSub.recycle()

            // Composite output with rounded corners, tint, and specular rim stroke
            val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val cornerPx = spec.cornerDp * density

            val roundRectPath = Path().apply {
                addRoundRect(
                    RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()),
                    cornerPx,
                    cornerPx,
                    Path.Direction.CW
                )
            }

            // Draw blurred wallpaper slice clipped to rounded bounds
            canvas.save()
            canvas.clipPath(roundRectPath)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(blurred, 0f, 0f, paint)

            // Dynamic translucent glass wash / tint
            val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (spec.tintAlpha > 0f && spec.tint != 0) {
                tintPaint.color = withAlpha(spec.tint, spec.tintAlpha)
                canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), tintPaint)
            } else {
                if (spec.dark) {
                    // Deep acrylic glass tint
                    tintPaint.color = Color.argb(45, 12, 14, 20)
                } else {
                    // Vibrant crystal glass tint
                    tintPaint.color = Color.argb(40, 255, 255, 255)
                }
                canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), tintPaint)
            }
            canvas.restore()
            blurred.recycle()

            // Draw specular rim highlight stroke
            if (spec.edge > 0f) {
                val strokePx = max(1f, 1.2f * density * spec.edge)
                val inset = strokePx / 2f
                val strokePath = Path().apply {
                    addRoundRect(
                        RectF(inset, inset, targetW.toFloat() - inset, targetH.toFloat() - inset),
                        max(0f, cornerPx - inset),
                        max(0f, cornerPx - inset),
                        Path.Direction.CW
                    )
                }

                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx
                }

                val topAlpha = if (spec.dark) 75 else 160
                val midAlpha = if (spec.dark) 20 else 40
                val bottomAlpha = if (spec.dark) 45 else 85

                strokePaint.shader = LinearGradient(
                    0f, 0f, 0f, targetH.toFloat(),
                    intArrayOf(
                        Color.argb(topAlpha, 255, 255, 255),
                        Color.argb(midAlpha, 255, 255, 255),
                        Color.argb(bottomAlpha, 255, 255, 255)
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )

                canvas.drawPath(strokePath, strokePaint)
            }

            output
        } catch (e: Throwable) {
            android.util.Log.e("PetalGlassRenderer", "Error rendering glass slice", e)
            null
        }
    }

    /**
     * High-speed, multi-pass downsampled box blur creating rich frosted glass
     * without NDK overhead or RenderScript deprecation warnings.
     */
    private fun fastFrostedBlur(src: Bitmap, radius: Int): Bitmap {
        val downsample = 4
        val w = max(1, src.width / downsample)
        val h = max(1, src.height / downsample)

        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val r = max(1, radius / downsample)

        val blurredSmall = multiPassBoxBlur(small, r, 2)
        val output = Bitmap.createScaledBitmap(blurredSmall, src.width, src.height, true)
        if (blurredSmall != small) small.recycle()
        if (blurredSmall != output) blurredSmall.recycle()
        return output
    }

    private fun multiPassBoxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        var current = src
        for (i in 0 until passes) {
            val next = boxBlurIteration(current, radius)
            if (current != src && current != next) current.recycle()
            current = next
        }
        return current
    }

    private fun boxBlurIteration(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val div = radius + radius + 1

        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        val a = IntArray(w * h)

        var yi = 0
        for (y in 0 until h) {
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var asum = 0

            for (i in -radius..radius) {
                val p = pix[yi + min(wm, max(i, 0))]
                asum += (p ushr 24)
                rsum += (p and 0xff0000) ushr 16
                gsum += (p and 0x00ff00) ushr 8
                bsum += (p and 0x0000ff)
            }

            for (x in 0 until w) {
                a[yi] = asum / div
                r[yi] = rsum / div
                g[yi] = gsum / div
                b[yi] = bsum / div

                val p1 = pix[yi + min(wm, max(x + radius + 1, 0))]
                val p2 = pix[yi + min(wm, max(x - radius, 0))]

                asum += (p1 ushr 24) - (p2 ushr 24)
                rsum += ((p1 and 0xff0000) - (p2 and 0xff0000)) ushr 16
                gsum += ((p1 and 0x00ff00) - (p2 and 0x00ff00)) ushr 8
                bsum += (p1 and 0x0000ff) - (p2 and 0x0000ff)

                yi++
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var asum = 0
            var yp = -radius * w

            for (i in -radius..radius) {
                yi = max(0, yp) + x
                asum += a[yi]
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }

            yi = x
            for (y in 0 until h) {
                pix[yi] = (asum / div shl 24) or (rsum / div shl 16) or (gsum / div shl 8) or (bsum / div)

                val y1 = min(hm, max(y + radius + 1, 0)) * w + x
                val y2 = min(hm, max(y - radius, 0)) * w + x

                asum += a[y1] - a[y2]
                rsum += r[y1] - r[y2]
                gsum += g[y1] - g[y2]
                bsum += b[y1] - b[y2]

                yi += w
            }
        }

        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(pix, 0, w, 0, 0, w, h)
        return outBmp
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
