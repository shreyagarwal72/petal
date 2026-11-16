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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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

    /**
     * Incremented whenever a glass tuning preference (blur, sheen, refraction) is saved. Widgets collect this
     * alongside [changes] so they recompose and re-render whenever the user adjusts glass settings.
     */
    val tuningChanges = MutableStateFlow(0L)

    /** Call this after saving any glass tuning preference so all placed widgets re-render with the new settings. */
    fun onTuningChanged(context: Context) {
        PetalGlassWidgetRenderer.forget()
        tuningChanges.value = System.currentTimeMillis()
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }

    fun store(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun copyFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    /** The full display in pixels, portrait: what the wallpaper copy covers one-to-one. Same from any context. */
    fun displaySize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(dm)
            if (dm.widthPixels > 0) dm.widthPixels to dm.heightPixels
            else context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        return min(w, h) to max(w, h)
    }

    /** True while the home wallpaper is still the image Petal's glass copy was taken from. */
    fun isWallpaperCurrent(context: Context): Boolean {
        if (!copyFile(context).exists()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        return try {
            val stored = store(context).getInt(KEY_WALLPAPER_ID, Int.MIN_VALUE)
            val current = WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            // No id on record (copy taken from the system wallpaper) or the system gave none: trust the copy.
            stored == Int.MIN_VALUE || current <= 0 || stored == current
        } catch (_: Throwable) { true }
    }

    /**
     * Retrieves the cached home wallpaper bitmap.
     * Attempts automatic extraction from [WallpaperManager] if no cache exists yet.
     */
    @Synchronized
    fun getWallpaper(context: Context): Bitmap? {
        val file = copyFile(context)
        // The system wallpaper may have changed without us hearing about it (the broadcast is unreliable on
        // newer Android), so compare its id with the one the cached copy was taken from.
        if (isSystemWallpaperStale(context)) {
            tryExtractSystemWallpaper(context)?.let { return it }
        }
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
                    val norm = normalizeToDisplay(context, bmp)
                    saveWallpaperBitmap(context, norm)
                    return norm
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun isSystemWallpaperStale(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val stored = store(context).getInt(KEY_WALLPAPER_ID, Int.MIN_VALUE)
            val current = WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            stored != Int.MIN_VALUE && current > 0 && current != stored
        } catch (_: Throwable) {
            false
        }
    }

    /** The system wallpaper changed: drop every cached slice, re-read the wallpaper and redraw all widgets. */
    fun onSystemWallpaperChanged(context: Context) {
        PetalGlassWidgetRenderer.forget()
        try { tryExtractSystemWallpaper(context) } catch (_: Throwable) {}
        PetalGlassWidgetRenderer.deleteTiles(context)
        changes.value = System.currentTimeMillis()
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }

    /** A widget was removed from the home screen: forget its alignment and delete its tiles (no redraw needed). */
    fun purge(context: Context, appWidgetId: Int) {
        store(context).edit()
            .remove("$KEY_RECT_PREFIX$appWidgetId")
            .remove("$KEY_SIZE_PREFIX$appWidgetId")
            .apply()
        PetalGlassWidgetRenderer.deleteTilesFor(context, appWidgetId)
    }

    /**
     * Scales [src] to cover the display and centre-crops it, which is how a launcher lays out a non-scrolling
     * wallpaper. Everything (aligner, renderer, slices) works in this one coordinate space, so a slice taken at
     * a screen position always matches what is behind the widget.
     */
    fun normalizeToDisplay(context: Context, src: Bitmap): Bitmap {
        val (dw, dh) = displaySize(context)
        if (dw <= 0 || dh <= 0) return src
        if (src.width == dw && src.height == dh) return src
        val scale = max(dw.toFloat() / src.width, dh.toFloat() / src.height)
        val sw = max(dw, (src.width * scale).roundToInt())
        val sh = max(dh, (src.height * scale).roundToInt())
        val soft = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE)
            src.copy(Bitmap.Config.ARGB_8888, false) ?: return src else src
        val scaled = Bitmap.createScaledBitmap(soft, sw, sh, true)
        val left = ((sw - dw) / 2).coerceAtLeast(0)
        val top = ((sh - dh) / 2).coerceAtLeast(0)
        val out = Bitmap.createBitmap(scaled, left, top, dw, dh)
        if (scaled !== out && scaled !== src) scaled.recycle()
        return out
    }

    @SuppressLint("MissingPermission")
    fun tryExtractSystemWallpaper(context: Context): Bitmap? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = wm.drawable
            if (drawable != null) {
                val raw = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val tw = max(1, drawable.intrinsicWidth.takeIf { it > 0 } ?: displaySize(context).first)
                    val th = max(1, drawable.intrinsicHeight.takeIf { it > 0 } ?: displaySize(context).second)
                    val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, tw, th)
                    drawable.draw(Canvas(bmp))
                    bmp
                }
                val bitmap = normalizeToDisplay(context, raw)
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

            // The copy the glass is cut from must be what the home screen really shows, so set it as the home
            // wallpaper (lock screen left alone), cropped to exactly the display. Without this the aligner and the
            // widgets used an image the launcher never drew, and the glass never matched what was behind it.
            val wallpaperId = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    WallpaperManager.getInstance(context).setBitmap(croppedBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                else { @Suppress("DEPRECATION") WallpaperManager.getInstance(context).setBitmap(croppedBitmap); 0 }
            } catch (_: Throwable) { -1 }
            saveWallpaperBitmap(context, croppedBitmap)
            if (wallpaperId > 0) store(context).edit().putInt(KEY_WALLPAPER_ID, wallpaperId).apply()
            forgetAllRects(context)
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
        val value = "$widthPx,$heightPx"
        if (store(context).getString("$KEY_SIZE_PREFIX$appWidgetId", null) != value)
            store(context).edit().putString("$KEY_SIZE_PREFIX$appWidgetId", value).apply()
    }

    /**
     * A widget that was resized by more than a few percent since it was lined up no longer sits where its
     * alignment says, so the alignment is dropped and the widget asks to be lined up again. Silent (no redraw
     * broadcast): the caller is already composing the widget.
     */
    fun dropIfResized(context: Context, appWidgetId: Int, widthPx: Int, heightPx: Int): Boolean {
        val r = getRect(context, appWidgetId) ?: return false
        val moved = abs(r.width() - widthPx) > r.width() * 0.08f || abs(r.height() - heightPx) > r.height() * 0.08f
        if (moved) {
            store(context).edit().remove("$KEY_RECT_PREFIX$appWidgetId").apply()
            PetalGlassWidgetRenderer.forget(appWidgetId)
        }
        return moved
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
        return isLiquidGlassEnabled(context) && isWallpaperCurrent(context)
    }
}
