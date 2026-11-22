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

    private val isRebaking = AtomicBoolean(false)

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
            .apply()
        deleteBlurBitmap(context, widgetId)
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

        // 2. Try WallpaperManager system wallpaper
        try {
            val wm = WallpaperManager.getInstance(context)
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

        // 3. Fallback: Generate an elegant frosted dark glass aesthetic backdrop
        return try {
            val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, screenW.toFloat(), screenH.toFloat(),
                intArrayOf(0xFF1E2838.toInt(), 0xFF141A24.toInt(), 0xFF0D1218.toInt()),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), paint)
            out
        } catch (t: Throwable) {
            Log.e(TAG, "Synthetic wallpaper fallback failed", t)
            null
        }
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

    /**
     * Asynchronously rebakes all placed Petal liquid glass widgets from the current wallpaper.
     */
    fun rebakeAllFromWallpaper(context: Context) {
        if (!isRebaking.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val searchComponent = ComponentName(context, PetalLiquidGlassSearchWidgetReceiver::class.java)
                val pillComponent = ComponentName(context, PetalLiquidGlassPillWidgetReceiver::class.java)

                val searchIds = appWidgetManager.getAppWidgetIds(searchComponent) ?: IntArray(0)
                val pillIds = appWidgetManager.getAppWidgetIds(pillComponent) ?: IntArray(0)
                val allIds = searchIds + pillIds

                if (allIds.isEmpty()) return@launch

                val config = PetalBlurConfig.load(context)
                val density = context.resources.displayMetrics.density
                val (screenW, screenH) = getScreenSize(context)

                for (id in allIds) {
                    val isPill = pillIds.contains(id)
                    val rect = getWidgetRect(context, id) ?: run {
                        // Default fallback rect centered on screen before accessibility detects exact bounds
                        val width = if (isPill) (screenW * 0.72f).toInt() else (screenW * 0.88f).toInt()
                        val height = if (isPill) (48 * density).toInt() else (56 * density).toInt()
                        val left = (screenW - width) / 2
                        val top = (screenH * 0.22f).toInt()
                        Rect(left, top, left + width, top + height)
                    }

                    val crop = getWallpaperCrop(context, rect.left, rect.top, rect.right, rect.bottom)
                    if (crop != null) {
                        val baked = PetalLiquidGlassRenderer.render(
                            context = context,
                            wallpaperCrop = crop,
                            widthPx = rect.width(),
                            heightPx = rect.height(),
                            config = config,
                            density = density
                        )
                        if (baked != null) {
                            saveBlurBitmap(context, id, baked)
                            baked.recycle()
                        }
                        crop.recycle()
                    }
                }

                // Notify widgets to refresh their RemoteViews
                val searchIntent = Intent(context, PetalLiquidGlassSearchWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, searchIds)
                }
                val pillIntent = Intent(context, PetalLiquidGlassPillWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, pillIds)
                }
                context.sendBroadcast(searchIntent)
                context.sendBroadcast(pillIntent)
            } catch (t: Throwable) {
                Log.e(TAG, "Rebake all widgets failed", t)
            } finally {
                isRebaking.set(false)
            }
        }
    }
}
