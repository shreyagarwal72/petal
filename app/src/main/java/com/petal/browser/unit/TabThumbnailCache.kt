package com.petal.browser.unit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Bounded memory & disk cache for tab preview thumbnails.
 * Keyed by tab identifier (or URL/tab ID). Thumbnails persist across app restarts on disk
 * and are only deleted when explicit removal/tab closure occurs.
 */
object TabThumbnailCache {

    private const val TAG = "TabThumbnailCache"
    private const val MAX_ENTRIES = 24
    private var diskCacheDir: File? = null

    private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            if (oldValue != null && oldValue !== newValue && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    @JvmStatic
    fun initDiskCache(context: Context) {
        if (diskCacheDir != null) return
        try {
            val baseDir = context.applicationContext.filesDir
            val dir = File(baseDir, "petal_tab_thumbnails")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            diskCacheDir = dir
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init disk cache dir", e)
        }
    }

    @JvmStatic
    fun put(tabId: String?, bitmap: Bitmap?) {
        if (tabId.isNullOrEmpty() || bitmap == null || bitmap.isRecycled) return
        val safeKey = getSafeKey(tabId)

        // Proportional resize (contain/fit scale) instead of aggressive center-cropping
        var resized = bitmap
        val maxDimension = 640
        if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (aspect > 1.0f) {
                newWidth = maxDimension
                newHeight = (maxDimension / aspect).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * aspect).toInt()
            }
            if (newWidth > 0 && newHeight > 0) {
                try {
                    resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                } catch (ignored: Exception) {
                }
            }
        }

        cache.put(safeKey, resized)

        // Asynchronously persist to disk
        val bitmapToSave = resized
        Executors.newSingleThreadExecutor().execute {
            val dir = diskCacheDir
            if (dir != null && !bitmapToSave.isRecycled) {
                try {
                    val file = File(dir, "$safeKey.webp")
                    FileOutputStream(file).use { out ->
                        bitmapToSave.compress(Bitmap.CompressFormat.WEBP, 80, out)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write thumbnail to disk: $safeKey", e)
                }
            }
        }
    }

    @JvmStatic
    fun get(tabId: String?): Bitmap? {
        if (tabId.isNullOrEmpty()) return null
        val safeKey = getSafeKey(tabId)
        val mem = cache.get(safeKey)
        if (mem != null && !mem.isRecycled) {
            return mem
        }

        // Try load from disk
        val dir = diskCacheDir
        if (dir != null) {
            val file = File(dir, "$safeKey.webp")
            if (file.exists()) {
                try {
                    val diskBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (diskBitmap != null) {
                        cache.put(safeKey, diskBitmap)
                        return diskBitmap
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading thumbnail from disk: $safeKey", e)
                }
            }
        }
        return null
    }

    @JvmStatic
    fun remove(tabId: String?) {
        if (tabId.isNullOrEmpty()) return
        val safeKey = getSafeKey(tabId)
        cache.remove(safeKey)
        val dir = diskCacheDir
        if (dir != null) {
            val file = File(dir, "$safeKey.webp")
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @JvmStatic
    fun clear() {
        cache.evictAll()
        val dir = diskCacheDir
        if (dir != null && dir.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }

    private fun getSafeKey(key: String): String {
        return key.hashCode().toString()
    }
}
