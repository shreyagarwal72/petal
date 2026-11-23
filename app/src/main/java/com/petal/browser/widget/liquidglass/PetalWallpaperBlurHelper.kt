package com.petal.browser.widget.liquidglass

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Wallpaper extraction, slicing, and caching helper for Petal Liquid Glass widgets.
 * Mirrors the robust wallpaper slicing and auto-rebake architecture from Liquid Glass Widgets.
 */
object PetalWallpaperBlurHelper {

    private const val TAG = "PetalWallpaperBlurHelper"
    private const val PREFS_NAME = "com.petal.browser.widget.liquidglass.PositionPrefs"
    private const val KEY_RECT_PREFIX = "rect_"
    private const val WIDGET_DIR = "glass_widgets"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveWidgetRect(context: Context, widgetId: Int, rect: Rect) {
        getPrefs(context).edit()
            .putInt("${KEY_RECT_PREFIX}${widgetId}_left", rect.left)
            .putInt("${KEY_RECT_PREFIX}${widgetId}_top", rect.top)
            .putInt("${KEY_RECT_PREFIX}${widgetId}_right", rect.right)
            .putInt("${KEY_RECT_PREFIX}${widgetId}_bottom", rect.bottom)
            .apply()
    }

    fun getWidgetRect(context: Context, widgetId: Int): Rect? {
        val sp = getPrefs(context)
        val l = sp.getInt("${KEY_RECT_PREFIX}${widgetId}_left", -1)
        val t = sp.getInt("${KEY_RECT_PREFIX}${widgetId}_top", -1)
        val r = sp.getInt("${KEY_RECT_PREFIX}${widgetId}_right", -1)
        val b = sp.getInt("${KEY_RECT_PREFIX}${widgetId}_bottom", -1)
        if (l < 0 || t < 0 || r <= l || b <= t) return null
        return Rect(l, t, r, b)
    }

    fun removeWidgetRect(context: Context, widgetId: Int) {
        getPrefs(context).edit()
            .remove("${KEY_RECT_PREFIX}${widgetId}_left")
            .remove("${KEY_RECT_PREFIX}${widgetId}_top")
            .remove("${KEY_RECT_PREFIX}${widgetId}_right")
            .remove("${KEY_RECT_PREFIX}${widgetId}_bottom")
            .remove("clean_sig_$widgetId")
            .remove("lum_$widgetId")
            .apply()
        deleteBlurBitmap(context, widgetId)
        File(getStorageDir(context), "widget_clean_$widgetId.png").delete()
    }

    /** Returns [width, height] of the physical screen. */
    fun getScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.maximumWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = context.resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    /**
     * Extracts and center-crops wallpaper to screen size.
     * Checks custom picked photo first, then WallpaperManager, and falls back to an aesthetic
     * gradient canvas so widgets always have a beautiful background to refract.
     */
    fun renderWallpaperAtScreenSize(context: Context, screenW: Int, screenH: Int): Bitmap? {
        if (screenW <= 0 || screenH <= 0) return null

        // 1. Check custom picked wallpaper photo
        val customPhoto = File(context.filesDir, "glass_wallpaper_photo.jpg")
        if (customPhoto.exists() && customPhoto.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(customPhoto.absolutePath)
                if (bmp != null) {
                    val cropped = scaleCenterCrop(bmp, screenW, screenH)
                    if (cropped != bmp) bmp.recycle()
                    return cropped
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Custom wallpaper photo decode failed", t)
            }
        }

        // 2. System wallpaper. A live wallpaper (video/animated) cannot be read as an image:
        // WallpaperManager returns an unrelated static fallback, which produced the wrong
        // (tan/gold) glass colours. In that case return null and let the screenshot capture
        // (accessibility service) provide the real pixels instead of faking them.
        try {
            val wm = WallpaperManager.getInstance(context)
            if (wm.wallpaperInfo != null && !isWallpaperOwnsGlass(context)) return null
            val drawable = wm.drawable ?: wm.fastDrawable
            if (drawable != null) {
                val intrinsicW = drawable.intrinsicWidth
                val intrinsicH = drawable.intrinsicHeight
                if (intrinsicW > 0 && intrinsicH > 0) {
                    val scale: Float
                    var dx = 0f
                    var dy = 0f
                    if (intrinsicW * screenH > screenW * intrinsicH) {
                        scale = screenH.toFloat() / intrinsicH.toFloat()
                        dx = (screenW - intrinsicW * scale) * 0.5f
                    } else {
                        scale = screenW.toFloat() / intrinsicW.toFloat()
                        dy = (screenH - intrinsicH * scale) * 0.5f
                    }
                    val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(out)
                    canvas.translate(dx.roundToInt().toFloat(), dy.roundToInt().toFloat())
                    canvas.scale(scale, scale)
                    drawable.setBounds(0, 0, intrinsicW, intrinsicH)
                    drawable.draw(canvas)
                    return out
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "WallpaperManager extraction restricted or unavailable", t)
        }
        return null
    }

    private fun scaleCenterCrop(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = source.width
        val srcH = source.height
        if (srcW == targetW && srcH == targetH) return source

        val scale: Float
        var dx = 0f
        var dy = 0f
        if (srcW * targetH > targetW * srcH) {
            scale = targetH.toFloat() / srcH.toFloat()
            dx = (targetW - srcW * scale) * 0.5f
        } else {
            scale = targetW.toFloat() / srcW.toFloat()
            dy = (targetH - srcH * scale) * 0.5f
        }
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.translate(dx.roundToInt().toFloat(), dy.roundToInt().toFloat())
        canvas.scale(scale, scale)
        canvas.drawBitmap(source, 0f, 0f, null)
        return out
    }

    /**
     * Slices the exact wallpaper rectangle corresponding to the widget's bounds on screen.
     */
    fun getWallpaperCrop(context: Context, l: Int, t: Int, r: Int, b: Int): Bitmap? {
        val (screenW, screenH) = getScreenSize(context)
        val fullWallpaper = renderWallpaperAtScreenSize(context, screenW, screenH) ?: return null

        val cl = max(0, min(l, fullWallpaper.width - 1))
        val ct = max(0, min(t, fullWallpaper.height - 1))
        val cw = max(1, min(r - l, fullWallpaper.width - cl))
        val ch = max(1, min(b - t, fullWallpaper.height - ct))

        return try {
            val crop = Bitmap.createBitmap(fullWallpaper, cl, ct, cw, ch)
            fullWallpaper.recycle()
            crop
        } catch (e: Exception) {
            fullWallpaper.recycle()
            null
        }
    }

    private fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, WIDGET_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getBlurFile(context: Context, widgetId: Int): File {
        return File(getStorageDir(context), "widget_blur_$widgetId.png")
    }

    fun saveBlurBitmap(context: Context, widgetId: Int, bitmap: Bitmap) {
        val target = getBlurFile(context, widgetId)
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            temp.renameTo(target)
        } catch (e: IOException) {
            Log.e(TAG, "Failed saving blur bitmap for widget $widgetId", e)
            temp.delete()
        }
    }

    fun loadBlurBitmap(context: Context, widgetId: Int): Bitmap? {
        if (isWallpaperOwnsGlass(context)) {
            // Live wallpaper mode: return null or transparent bitmap
            return null
        }
        val file = getBlurFile(context, widgetId)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Throwable) {
            null
        }
    }

    fun deleteBlurBitmap(context: Context, widgetId: Int) {
        val file = getBlurFile(context, widgetId)
        if (file.exists()) file.delete()
    }

    /**
     * Checks if Petal's own Live Wallpaper service is the current system wallpaper.
     */
    fun isWallpaperOwnsGlass(context: Context): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val info = wm.wallpaperInfo
            info != null && info.packageName == context.packageName &&
                    info.serviceName == PetalGlassWallpaperService::class.java.name
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------
    // Clean captures: the real pixels behind a widget (taken by the accessibility service)
    // ---------------------------------------------------------------------------------------

    private fun getCleanFile(context: Context, widgetId: Int) =
        File(getStorageDir(context), "widget_clean_$widgetId.png")

    private fun rectSig(r: Rect) = "${r.left},${r.top},${r.right},${r.bottom}"

    fun saveCleanBitmap(context: Context, widgetId: Int, bitmap: Bitmap, rect: Rect) {
        val target = getCleanFile(context, widgetId)
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            temp.renameTo(target)
            getPrefs(context).edit().putString("clean_sig_$widgetId", rectSig(rect)).apply()
        } catch (e: IOException) {
            Log.e(TAG, "Failed saving clean bitmap for widget $widgetId", e)
            temp.delete()
        }
    }

    fun loadCleanBitmap(context: Context, widgetId: Int): Bitmap? {
        val f = getCleanFile(context, widgetId)
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Throwable) { null }
    }

    /** True when a clean capture exists for exactly this on-screen rectangle. */
    fun cleanMatches(context: Context, widgetId: Int, rect: Rect): Boolean {
        if (!getCleanFile(context, widgetId).exists()) return false
        return getPrefs(context).getString("clean_sig_$widgetId", null) == rectSig(rect)
    }

    /** Wallpaper changed etc.: forget all captures so they are retaken. */
    fun invalidateCaptures(context: Context) {
        try {
            getStorageDir(context).listFiles()?.forEach {
                if (it.name.startsWith("widget_clean_")) it.delete()
            }
            val ed = getPrefs(context).edit()
            getPrefs(context).all.keys.filter { it.startsWith("clean_sig_") }.forEach { ed.remove(it) }
            ed.apply()
        } catch (_: Throwable) {
        }
        PetalGlassAccessibilityService.onCapturesInvalidated()
    }

    // ---------------------------------------------------------------------------------------
    // Contrast: pick light or dark text/icons from the luminance behind the widget
    // ---------------------------------------------------------------------------------------

    /** Average brightness 0..1 of a bitmap (sampled). */
    fun computeLuma(bmp: Bitmap): Float {
        var sum = 0.0
        var n = 0
        val sx = max(1, bmp.width / 24)
        val sy = max(1, bmp.height / 6)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16 and 0xFF) / 255.0
                val g = (c shr 8 and 0xFF) / 255.0
                val b = (c and 0xFF) / 255.0
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                n++
                x += sx
            }
            y += sy
        }
        return if (n > 0) (sum / n).toFloat() else 0f
    }

    private fun saveLuma(context: Context, widgetId: Int, bmp: Bitmap) {
        getPrefs(context).edit().putFloat("lum_$widgetId", computeLuma(bmp)).apply()
    }

    private const val INK_DARK = 0xE6101418.toInt()
    private const val INK_LIGHT = 0xFFFFFFFF.toInt()

    /** Text/icon colour for a given tint mode and background brightness (also used by the settings preview). */
    fun inkFor(config: PetalBlurConfig, luma: Float): Int {
        if (config.darkLightMode == PetalBlurConfig.DARK_LIGHT_DARK) return INK_LIGHT
        if (config.darkLightMode == PetalBlurConfig.DARK_LIGHT_LIGHT) return INK_DARK
        return if (luma > 0.62f) INK_DARK else INK_LIGHT
    }

    fun getContentColor(context: Context, widgetId: Int, config: PetalBlurConfig): Int {
        if (isWallpaperOwnsGlass(context)) return INK_LIGHT
        return inkFor(config, getPrefs(context).getFloat("lum_$widgetId", 0f))
    }

    // ---------------------------------------------------------------------------------------
    // Baking
    // ---------------------------------------------------------------------------------------

    private fun isOnScreen(context: Context, r: Rect): Boolean {
        val (w, h) = getScreenSize(context)
        return r.left >= -2 && r.top >= -2 && r.right <= w + 2 && r.bottom <= h + 2
    }

    /**
     * Bakes one widget. Source priority: (1) real screenshot capture, (2) wallpaper slice at the
     * detected rect. With neither, returns false and the widget keeps its frosted placeholder
     * (never a guessed/wrong crop).
     */
    fun bakeWidget(context: Context, widgetId: Int, isPill: Boolean, config: PetalBlurConfig): Boolean {
        val rect = getWidgetRect(context, widgetId)
        var source: Bitmap? = loadCleanBitmap(context, widgetId)
        var w = rect?.width() ?: source?.width ?: 0
        var h = rect?.height() ?: source?.height ?: 0
        if (source == null && rect != null && isOnScreen(context, rect)) {
            source = getWallpaperCrop(context, rect.left, rect.top, rect.right, rect.bottom)
        }
        if (source == null || w <= 0 || h <= 0) return false
        return try {
            saveLuma(context, widgetId, source)
            val density = context.resources.displayMetrics.density
            val baked = PetalLiquidGlassRenderer.render(
                context = context,
                wallpaperCrop = source,
                widthPx = w,
                heightPx = h,
                config = config,
                density = density,
                cornerRadiusDp = if (isPill) 28f else -1f
            )
            if (baked != null) {
                saveBlurBitmap(context, widgetId, baked)
                baked.recycle()
                true
            } else false
        } catch (t: Throwable) {
            Log.e(TAG, "bakeWidget failed", t)
            false
        } finally {
            source.recycle()
        }
    }

    private val isRebaking = AtomicBoolean(false)
    private val rebakeAgain = AtomicBoolean(false)

    /**
     * Rebakes every placed Petal liquid glass widget from stored captures. Calls that arrive while a
     * bake is running are queued (previously they were silently dropped, so new positions were lost).
     */
    fun rebakeAllFromWallpaper(context: Context) {
        val app = context.applicationContext
        if (!isRebaking.compareAndSet(false, true)) {
            rebakeAgain.set(true)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                do {
                    rebakeAgain.set(false)
                    val mgr = AppWidgetManager.getInstance(app)
                    val searchIds = mgr.getAppWidgetIds(ComponentName(app, PetalLiquidGlassSearchWidgetReceiver::class.java)) ?: IntArray(0)
                    val pillIds = mgr.getAppWidgetIds(ComponentName(app, PetalLiquidGlassPillWidgetReceiver::class.java)) ?: IntArray(0)
                    if (searchIds.isEmpty() && pillIds.isEmpty()) break
                    if (!isWallpaperOwnsGlass(app)) {
                        val config = PetalBlurConfig.load(app)
                        for (id in searchIds) bakeWidget(app, id, false, config)
                        for (id in pillIds) bakeWidget(app, id, true, config)
                    }
                    PetalLiquidGlassWidgetBinder.updateAll(app)
                } while (rebakeAgain.get())
            } catch (t: Throwable) {
                Log.e(TAG, "Rebake all widgets failed", t)
            } finally {
                isRebaking.set(false)
            }
        }
    }
}
