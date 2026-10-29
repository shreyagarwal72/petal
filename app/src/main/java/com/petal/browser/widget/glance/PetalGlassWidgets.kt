package com.petal.browser.widget.glance

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.WindowManager
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Singleton repository and configuration state manager for Petal's liquid glass widget system.
 *
 * Implements the wallpaper caching, coordinate alignment mapping, and real-time invalidation
 * mechanism modeled after the high-performance Stride liquid glass widget architecture.
 */
object PetalGlassWidgets {

    private const val FILE_NAME = "glass_home_wallpaper.jpg"
    private const val PREFS_NAME = "petal_glass_widgets"
    private const val KEY_RECT_PREFIX = "rect_"
    private const val KEY_SIZE_PREFIX = "size_"
    private const val KEY_WALLPAPER_ID = "wallpaper_id"

    val changes = MutableStateFlow(0L)

    fun store(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun copyFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun displaySize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.currentWindowMetrics.bounds
            return Pair(bounds.width(), bounds.height())
        }
        val dm = context.resources.displayMetrics
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    /**
     * Retrieves the cached home wallpaper bitmap.
     * Attempts automatic extraction from [WallpaperManager] if no cache exists yet.
     */
    @Synchronized
    fun getWallpaper(context: Context): Bitmap? {
        val file = copyFile(context)
        if (file.exists() && file.length() > 0) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) return bmp
        }
        val sysBmp = tryExtractSystemWallpaper(context)
        if (sysBmp != null) return sysBmp

        // Fallback: check if the user selected a custom image for Liquid Glass in settings
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val imageUriStr = sp.getString("sp_liquid_glass_bg_image_uri", null)
        if (!imageUriStr.isNullOrEmpty()) {
            try {
                val uri = android.net.Uri.parse(imageUriStr)
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    ) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                if (bmp != null) {
                    saveWallpaperBitmap(context, bmp)
                    return bmp
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun tryExtractSystemWallpaper(context: Context): Bitmap? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = wm.drawable
            if (drawable != null) {
                val (dispWidth, dispHeight) = displaySize(context)
                val targetW = max(dispWidth, drawable.intrinsicWidth)
                val targetH = max(dispHeight, drawable.intrinsicHeight)
                val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, targetW, targetH)
                    drawable.draw(canvas)
                    bmp
                }
                saveWallpaperBitmap(context, bitmap)
                bitmap
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun saveWallpaperBitmap(context: Context, bitmap: Bitmap) {
        val file = copyFile(context)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
            try {
                val wm = WallpaperManager.getInstance(context)
                val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                } else 0
                store(context).edit().putInt(KEY_WALLPAPER_ID, id).apply()
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    /**
     * Persists a newly provided or picked wallpaper bitmap scaled to screen dimensions.
     */
    suspend fun setHomeWallpaper(context: Context, sourceBitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val (dispWidth, dispHeight) = displaySize(context)
            val srcW = sourceBitmap.width
            val srcH = sourceBitmap.height

            val scale = max(dispWidth.toFloat() / srcW, dispHeight.toFloat() / srcH)
            val scaledW = (srcW * scale).roundToInt()
            val scaledH = (srcH * scale).roundToInt()

            val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, scaledW, scaledH, true)
            val left = ((scaledW - dispWidth) / 2).coerceIn(0, max(0, scaledW - dispWidth))
            val top = ((scaledH - dispHeight) / 2).coerceIn(0, max(0, scaledH - dispHeight))

            val croppedBitmap = Bitmap.createBitmap(scaledBitmap, left, top, dispWidth, dispHeight)
            if (scaledBitmap != sourceBitmap && scaledBitmap != croppedBitmap) {
                scaledBitmap.recycle()
            }

            saveWallpaperBitmap(context, croppedBitmap)
            PetalGlassWidgetRenderer.forget()
            PetalGlassWidgetRenderer.deleteTiles(context)
            changes.value = System.currentTimeMillis()
            PetalSearchGlanceWidgetUpdater.refresh(context)
            true
        } catch (e: Throwable) {
            android.util.Log.e("PetalGlassWidgets", "Failed saving wallpaper", e)
            false
        }
    }

    fun getRect(context: Context, appWidgetId: Int): Rect? {
        val raw = store(context).getString("$KEY_RECT_PREFIX$appWidgetId", null) ?: return null
        val parts = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size == 4) {
            val rect = Rect(parts[0], parts[1], parts[2], parts[3])
            if (rect.width() > 0 && rect.height() > 0) {
                return rect
            }
        }
        return null
    }

    fun setRect(context: Context, appWidgetId: Int, rect: Rect) {
        val value = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
        store(context).edit().putString("$KEY_RECT_PREFIX$appWidgetId", value).apply()
        PetalGlassWidgetRenderer.forget(appWidgetId)
        PetalGlassWidgetRenderer.deleteTiles(context)
        changes.value = System.currentTimeMillis()
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }

    fun forget(context: Context, appWidgetId: Int) {
        store(context).edit()
            .remove("$KEY_RECT_PREFIX$appWidgetId")
            .remove("$KEY_SIZE_PREFIX$appWidgetId")
            .apply()
        PetalGlassWidgetRenderer.forget(appWidgetId)
        changes.value = System.currentTimeMillis()
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }

    fun forgetAllRects(context: Context) {
        val editor = store(context).edit()
        store(context).all.keys.forEach { key ->
            if (key.startsWith(KEY_RECT_PREFIX) || key.startsWith(KEY_SIZE_PREFIX)) {
                editor.remove(key)
            }
        }
        editor.apply()
        PetalGlassWidgetRenderer.forget()
        changes.value = System.currentTimeMillis()
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }

    fun noteSize(context: Context, appWidgetId: Int, widthPx: Int, heightPx: Int) {
        store(context).edit().putString("$KEY_SIZE_PREFIX$appWidgetId", "$widthPx,$heightPx").apply()
    }

    fun getSize(context: Context, appWidgetId: Int): Pair<Int, Int>? {
        val raw = store(context).getString("$KEY_SIZE_PREFIX$appWidgetId", null) ?: return null
        val parts = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }

    fun isLiquidGlassEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val master = sp.getBoolean("sp_liquid_glass_enabled", false)
        val widgets = sp.getBoolean("sp_liquid_glass_widgets", true)
        return master && widgets
    }

    fun active(context: Context): Boolean {
        return isLiquidGlassEnabled(context) && copyFile(context).exists()
    }
}
