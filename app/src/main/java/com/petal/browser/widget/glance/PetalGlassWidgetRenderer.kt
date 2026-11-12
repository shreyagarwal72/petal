package com.petal.browser.widget.glance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Liquid glass renderer for home screen widgets.
 *
 * A launcher widget is a RemoteViews image, so it cannot sample what is behind it every frame. What this
 * does instead is slice the wallpaper at the aligned position and turn that slice into glass:
 *  - a frosted blur for the body of the pane,
 *  - a real lens at the rim: pixels inside the bezel are fetched from further out than where they sit
 *    (circular-bezel height profile), with a little per-channel dispersion,
 *  - a vibrancy boost, a material tint and light-direction specular glints on the edge.
 * It re-renders whenever the wallpaper, the widget size/position or any glass setting changes.
 *
 * Tiles are written to filesDir and handed to the launcher through FileProvider (raw bitmaps would blow the
 * RemoteViews binder limit). Each tile's file name carries a content stamp, so the launcher never keeps
 * showing a stale image that happens to have the same URI.
 */
object PetalGlassWidgetRenderer {

    data class Spec(
        val rect: Rect,
        val blurDp: Float = 24f,
        val cornerDp: Float = 24f,
        val dark: Boolean = false,
        /** Sheen: strength of the specular rim and glints, 0..1.5. */
        val edge: Float = 1f,
        /** How hard the rim bends the image, 0 switches the lens off, 2 is the maximum. */
        val refraction: Float = 1f,
        /** How far in from the rim the glass bends, 0..2 (1 = as designed). */
        val refractionWidth: Float = 1f,
        val tint: Int = 0,
        val tintAlpha: Float = 0f
    )

    private data class Key(val spec: Spec, val stamp: Long, val density: Float)

    private val cache = object : LinkedHashMap<Key, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>?): Boolean = size > 4
    }
    private var cachedWallpaper: Bitmap? = null
    private var cachedWallpaperStamp: Long = -1L

    /** Provider authority declared in AndroidManifest, matching the existing fileprovider entry. */
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val TILE_DIR = "glass_widget_tiles"

    /** Upper bound on rendered pixels; the tile is stretched to the widget, and glass is soft anyway. */
    private const val MAX_WORK_PIXELS = 520_000f

    @Synchronized
    fun forget(@Suppress("UNUSED_PARAMETER") appWidgetId: Int? = null) {
        cache.clear()
        cachedWallpaper = null
        cachedWallpaperStamp = -1L
    }

    /** Removes tiles nobody can still be showing (older than two minutes), for every widget. */
    fun deleteTiles(context: Context) {
        try {
            val cutoff = System.currentTimeMillis() - 120_000L
            File(context.filesDir, TILE_DIR).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        } catch (_: Throwable) {}
    }

    /** Deletes every tile of one widget (when the widget is removed). */
    fun deleteTilesFor(context: Context, widgetId: Int) {
        try {
            File(context.filesDir, TILE_DIR).listFiles()?.forEach {
                if (it.name.startsWith("widget_${widgetId}_")) it.delete()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Renders the glass card for [spec], saves it and returns a FileProvider [Uri] that Glance can use, or null
     * when there is no wallpaper to slice yet or rendering failed.
     */
    fun renderUri(context: Context, spec: Spec, widgetId: Int): Uri? {
        return try {
            val stamp = PetalGlassWidgets.copyFile(context).lastModified()
            val dir = File(context.filesDir, TILE_DIR).also { it.mkdirs() }
            val name = "widget_${widgetId}_${spec.hashCode().toUInt().toString(16)}_${stamp.toString(16)}.png"
            val file = File(dir, name)
            if (!file.exists() || file.length() == 0L) {
                val bitmap = render(context, spec) ?: return null
                val tmp = File(dir, "$name.tmp")
                FileOutputStream(tmp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                    return null
                }
            }
            // Superseded tiles of this widget go once the launcher has had time to switch to the new one.
            val cutoff = System.currentTimeMillis() - 60_000L
            dir.listFiles()?.forEach {
                if (it.name.startsWith("widget_${widgetId}_") && it.name != name && it.lastModified() < cutoff) it.delete()
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
            grantUriToLaunchers(context, uri)
            uri
        } catch (e: Throwable) {
            android.util.Log.e("PetalGlassRenderer", "Failed saving glass tile for widget $widgetId", e)
            null
        }
    }

    private fun grantUriToLaunchers(context: Context, uri: Uri) {
        try {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0
            for (info in pm.queryIntentActivities(launcherIntent, flags)) {
                try {
                    context.grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    /** Renders the glass card for [spec], or returns the cached one. */
    fun render(context: Context, spec: Spec): Bitmap? {
        val rect = spec.rect
        if (rect.width() <= 0 || rect.height() <= 0) return null

        val stamp = PetalGlassWidgets.copyFile(context).lastModified()
        val density = context.resources.displayMetrics.density
        val key = Key(spec.copy(rect = Rect(rect)), stamp, density)

        synchronized(this) {
            cache[key]?.let { if (!it.isRecycled) return it }
        }
        val wallpaper = loadWallpaper(context, stamp) ?: return null
        val rendered = draw(context, wallpaper, spec) ?: return null
        synchronized(this) { cache[key] = rendered }
        return rendered
    }

    @Synchronized
    private fun loadWallpaper(context: Context, stamp: Long): Bitmap? {
        val cached = cachedWallpaper
        if (cached != null && !cached.isRecycled && cachedWallpaperStamp == stamp) return cached
        val raw = PetalGlassWidgets.getWallpaper(context) ?: return null
        // A hardware bitmap can't be used as a shader on the software canvas the tile is drawn on.
        val fresh = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && raw.config == Bitmap.Config.HARDWARE) {
            raw.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else raw
        cachedWallpaper = fresh
        // getWallpaper() may have just rewritten the file, so read the stamp again.
        cachedWallpaperStamp = PetalGlassWidgets.copyFile(context).lastModified()
        return fresh
    }

    // ───────────────────────────── drawing ─────────────────────────────

    private fun draw(context: Context, wallpaper: Bitmap, spec: Spec): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val rect = spec.rect
            val fullW = rect.width()
            val fullH = rect.height()

            // Work resolution (the tile is stretched to the widget afterwards).
            val ws = min(1f, sqrt(MAX_WORK_PIXELS / (fullW.toFloat() * fullH.toFloat()))).coerceAtLeast(0.25f)
            val w = max(2, (fullW * ws).roundToInt())
            val h = max(2, (fullH * ws).roundToInt())
            val d = density * ws // work pixels per dp

            val cornerPx = min(spec.cornerDp * d, min(w, h) / 2f)
            val strength = spec.refraction.coerceIn(0f, 2f)
            val widthMul = spec.refractionWidth.coerceIn(0f, 2f)
            val lensOn = strength > 0.001f && widthMul > 0.001f
            val bezel = if (lensOn) min(min(w, h) / 2f, 18f * d * widthMul) else 0f
            val maxDisp = bezel * 0.85f * strength
            val pad = ceil(maxDisp).toInt() + 2
            val pw = w + 2 * pad
            val ph = h + 2 * pad

            // 1. The wallpaper under the widget, plus a margin the lens can pull from. CLAMP repeats the edge pixel
            //    where the margin runs off the wallpaper.
            val (dispW, dispH) = PetalGlassWidgets.displaySize(context)
            val scaleX = wallpaper.width.toFloat() / max(1, dispW)
            val scaleY = wallpaper.height.toFloat() / max(1, dispH)
            val bg = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            run {
                val shader = BitmapShader(wallpaper, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val m = Matrix()
                m.setScale(ws / scaleX, ws / scaleY)
                m.postTranslate(-rect.left * ws + pad, -rect.top * ws + pad)
                shader.setLocalMatrix(m)
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
                Canvas(bg).drawRect(0f, 0f, pw.toFloat(), ph.toFloat(), p)
            }

            // 2. Two blurs: a strong frost for the body and a light one the rim refracts.
            val strongR = max(4, (spec.blurDp * d * 0.5f).roundToInt())
            val strongBmp = frostedBlur(bg, strongR, 4)
            val lightBmp = frostedBlur(bg, max(2, strongR / 4), 2)
            bg.recycle()
            val strongPx = IntArray(pw * ph)
            val lightPx = IntArray(pw * ph)
            strongBmp.getPixels(strongPx, 0, pw, 0, 0, pw, ph)
            lightBmp.getPixels(lightPx, 0, pw, 0, 0, pw, ph)
            if (strongBmp != lightBmp) lightBmp.recycle()
            strongBmp.recycle()

            // 3. Per pixel: rounded-rect distance field, lens, vibrancy, tint, glints.
            val out = IntArray(w * h)
            val halfW = w / 2f
            val halfH = h / 2f
            val tintColor = if (spec.tintAlpha > 0f && spec.tint != 0) spec.tint else if (spec.dark) Color.argb(255, 12, 14, 20) else Color.WHITE
            val tintA = if (spec.tintAlpha > 0f && spec.tint != 0) spec.tintAlpha.coerceIn(0f, 1f) else if (spec.dark) 45f / 255f else 40f / 255f
            val tr = Color.red(tintColor)
            val tg = Color.green(tintColor)
            val tb = Color.blue(tintColor)
            val glint = spec.edge.coerceIn(0f, 1.5f)
            val nearBand = max(bezel, 4f * d)
            val lx = -0.55f
            val ly = -0.83f
            val dispersion = 0.07f * strength

            for (y in 0 until h) {
                val py = y + 0.5f - halfH
                for (x in 0 until w) {
                    val px = x + 0.5f - halfW
                    val qx = abs(px) - (halfW - cornerPx)
                    val qy = abs(py) - (halfH - cornerPx)
                    val outside = hypot(max(qx, 0f), max(qy, 0f))
                    val dist = -(outside + min(max(qx, qy), 0f) - cornerPx) // > 0 inside the glass
                    if (dist <= -0.5f) continue
                    val cover = (dist + 0.5f).coerceIn(0f, 1f)

                    val bx = x + pad
                    val by = y + pad
                    val base = strongPx[by * pw + bx]
                    var r = (base shr 16) and 0xFF
                    var g = (base shr 8) and 0xFF
                    var b = base and 0xFF

                    var spec1 = 0f
                    if (dist < nearBand) {
                        // Outward normal.
                        val sx = if (px < 0f) -1f else 1f
                        val sy = if (py < 0f) -1f else 1f
                        val nx: Float
                        val ny: Float
                        if (qx > 0f && qy > 0f) {
                            val len = max(1e-4f, hypot(qx, qy))
                            nx = qx / len * sx
                            ny = qy / len * sy
                        } else if (qx > qy) {
                            nx = sx
                            ny = 0f
                        } else {
                            nx = 0f
                            ny = sy
                        }

                        if (bezel > 0f && dist < bezel) {
                            val t = 1f - max(dist, 0f) / bezel // 0 inner edge of the bezel .. 1 at the rim
                            val f = 1f - sqrt(max(0f, 1f - t * t)) // circular bezel profile
                            val disp = maxDisp * f
                            val sr = sampleChannel(lightPx, pw, ph, bx + 0.5f + nx * disp * (1f + dispersion), by + 0.5f + ny * disp * (1f + dispersion), 16)
                            val sg = sampleChannel(lightPx, pw, ph, bx + 0.5f + nx * disp, by + 0.5f + ny * disp, 8)
                            val sb = sampleChannel(lightPx, pw, ph, bx + 0.5f + nx * disp * (1f - dispersion), by + 0.5f + ny * disp * (1f - dispersion), 0)
                            val m = smoothstep(0f, 0.55f, t)
                            r = (r + (sr - r) * m).roundToInt()
                            g = (g + (sg - g) * m).roundToInt()
                            b = (b + (sb - b) * m).roundToInt()
                        }

                        // Specular glints: strong on the edge facing the light, a weaker echo on the opposite one.
                        val facing = nx * lx + ny * ly
                        val s = max(0f, facing).pow(2f) + max(0f, -facing).pow(3f) * 0.45f
                        spec1 = s * exp(-max(dist, 0f) / (2.2f * d)) * glint * 0.55f
                    }

                    // Vibrancy and the glass wash.
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    r = (luma + (r - luma) * 1.18f).roundToInt().coerceIn(0, 255)
                    g = (luma + (g - luma) * 1.18f).roundToInt().coerceIn(0, 255)
                    b = (luma + (b - luma) * 1.18f).roundToInt().coerceIn(0, 255)
                    r = (r + (tr - r) * tintA).roundToInt()
                    g = (g + (tg - g) * tintA).roundToInt()
                    b = (b + (tb - b) * tintA).roundToInt()
                    if (spec1 > 0f) {
                        val a = spec1.coerceIn(0f, 0.9f)
                        r = (r + (255 - r) * a).roundToInt()
                        g = (g + (255 - g) * a).roundToInt()
                        b = (b + (255 - b) * a).roundToInt()
                    }

                    out[y * w + x] = ((cover * 255f).roundToInt() shl 24) or
                        (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
                }
            }

            val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            output.setPixels(out, 0, w, 0, 0, w, h)

            // 4. Thin specular rim stroke on top.
            if (spec.edge > 0f) {
                val canvas = Canvas(output)
                val strokePx = max(1f, 1.2f * d * spec.edge)
                val inset = strokePx / 2f
                val cr = max(0f, cornerPx - inset)
                val strokePath = Path().apply {
                    addRoundRect(RectF(inset, inset, w - inset, h - inset), cr, cr, Path.Direction.CW)
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx
                }
                val edgeScale = spec.edge.coerceIn(0.1f, 1.5f)
                val topAlpha = ((if (spec.dark) 75 else 160) * edgeScale).toInt().coerceIn(10, 255)
                val midAlpha = ((if (spec.dark) 20 else 40) * edgeScale).toInt().coerceIn(5, 150)
                val bottomAlpha = ((if (spec.dark) 45 else 85) * edgeScale).toInt().coerceIn(10, 200)
                strokePaint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
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

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Bilinear sample of one colour channel ([shift] 16, 8 or 0) at pixel-centre coordinates (x, y). */
    private fun sampleChannel(px: IntArray, w: Int, h: Int, x: Float, y: Float, shift: Int): Float {
        val fx = (x - 0.5f).coerceIn(0f, (w - 1).toFloat())
        val fy = (y - 0.5f).coerceIn(0f, (h - 1).toFloat())
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val x1 = min(w - 1, x0 + 1)
        val y1 = min(h - 1, y0 + 1)
        val tx = fx - x0
        val ty = fy - y0
        val c00 = ((px[y0 * w + x0] shr shift) and 0xFF).toFloat()
        val c10 = ((px[y0 * w + x1] shr shift) and 0xFF).toFloat()
        val c01 = ((px[y1 * w + x0] shr shift) and 0xFF).toFloat()
        val c11 = ((px[y1 * w + x1] shr shift) and 0xFF).toFloat()
        val top = c00 + (c10 - c00) * tx
        val bottom = c01 + (c11 - c01) * tx
        return top + (bottom - top) * ty
    }

    // ───────────────────────────── blur ─────────────────────────────

    /** Downsampled, two-pass box blur: a soft frost with no RenderScript or NDK. */
    private fun frostedBlur(src: Bitmap, radius: Int, downsample: Int): Bitmap {
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
                rsum += (p and 0xff0000) shr 16
                gsum += (p and 0x00ff00) shr 8
                bsum += (p and 0x0000ff)
            }
            for (x in 0 until w) {
                a[yi] = asum / div
                r[yi] = rsum / div
                g[yi] = gsum / div
                b[yi] = bsum / div

                val p1 = pix[yi + min(wm, max(x + radius + 1, 0))]
                val p2 = pix[yi + min(wm, max(x - radius, 0))]

                // The differences can be negative, so these shifts must be arithmetic (shr). The old logical
                // shift (ushr) turned every negative step into a huge positive one and corrupted the colours.
                asum += (p1 ushr 24) - (p2 ushr 24)
                rsum += ((p1 and 0xff0000) - (p2 and 0xff0000)) shr 16
                gsum += ((p1 and 0x00ff00) - (p2 and 0x00ff00)) shr 8
                bsum += (p1 and 0x0000ff) - (p2 and 0x0000ff)
                yi++
            }
        }

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
                pix[yi] = ((asum / div) shl 24) or ((rsum / div) shl 16) or ((gsum / div) shl 8) or (bsum / div)

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
}
