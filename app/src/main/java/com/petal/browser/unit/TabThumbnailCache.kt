package com.petal.browser.unit

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Bounded, asynchronous tab thumbnail storage. Tab IDs are the only cache identity;
 * URLs are deliberately never aliases because multiple tabs can display the same URL.
 * Private thumbnails use a separate RAM-only cache and never read or write disk.
 */
object TabThumbnailCache {

    private const val TAG = "TabThumbnailCache"
    private const val MAX_PREVIEW_WIDTH = 480
    private const val MAX_PREVIEW_ASPECT = 1.7f
    private val MAX_PREVIEW_HEIGHT = (MAX_PREVIEW_WIDTH * MAX_PREVIEW_ASPECT).roundToInt()
    private const val MAX_DISK_CACHE_BYTES = 64L * 1024L * 1024L
    private const val TRIM_EVERY_N_WRITES = 12
    private val WRITES_SINCE_TRIM = AtomicInteger()
    private val MAIN = Handler(Looper.getMainLooper())

    @JvmField
    val DISK: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PetalThumbnailDisk").apply { isDaemon = true }
    }

    private val TOKENS = AtomicLong()
    private val GENERATION = AtomicLong()
    private val regularVersions = ConcurrentHashMap<String, Long>()
    private val pendingLoads = ConcurrentHashMap.newKeySet<String>()
    private val loadCallbacks = ConcurrentHashMap<String, CopyOnWriteArrayList<ThumbnailCallback>>()
    private val listeners = CopyOnWriteArrayList<ThumbnailListener>()

    /** Notified on the main thread whenever a tab's thumbnail is stored or removed. */
    fun interface ThumbnailListener {
        fun onThumbnailChanged(tabId: String)
    }

    @JvmStatic
    fun addListener(listener: ThumbnailListener) {
        listeners.addIfAbsent(listener)
    }

    @JvmStatic
    fun removeListener(listener: ThumbnailListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged(tabId: String) {
        if (listeners.isEmpty()) return
        MAIN.post { for (l in listeners) try { l.onThumbnailChanged(tabId) } catch (_: Throwable) {} }
    }

    private val regularMemoryCache: LruCache<String, Bitmap>
    private val privateMemoryCache: LruCache<String, Bitmap>
    @Volatile
    private var diskCacheDir: File? = null
    @Volatile
    private var initialized = false

    init {
        val maxMemoryKb = Math.max(1024L, Runtime.getRuntime().maxMemory() / 1024L)
        val regularKb = Math.max(2048L, Math.min(maxMemoryKb / 8L, 40L * 1024L)).toInt()
        val privateKb = Math.max(1024L, Math.min(maxMemoryKb / 16L, 8L * 1024L)).toInt()
        regularMemoryCache = createMemoryCache(regularKb)
        privateMemoryCache = createMemoryCache(privateKb)
    }

    private fun createMemoryCache(maxKb: Int): LruCache<String, Bitmap> {
        return object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                if (bitmap.isRecycled) return 0
                return Math.max(1, bitmap.byteCount / 1024)
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun initDiskCache(context: Context) {
        if (initialized && diskCacheDir != null && diskCacheDir!!.isDirectory) return
        val appContext = context.applicationContext
        val dir = File(appContext.filesDir, "petal_tab_thumbnails")
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            Log.e(TAG, "Cannot create thumbnail cache directory: $dir")
            diskCacheDir = null
            initialized = false
            return
        }
        diskCacheDir = dir
        initialized = true
        try {
            appContext.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    trimMemory(level)
                }
                override fun onConfigurationChanged(newConfig: Configuration) {}
                override fun onLowMemory() {
                    clearMemory()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register thumbnail memory callbacks", e)
        }
        val initializedDir = dir
        DISK.execute { trimDiskCache(initializedDir) }
    }

    @JvmStatic
    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            clearMemory()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            regularMemoryCache.evictAll()
            privateMemoryCache.trimToSize(privateMemoryCache.size() / 2)
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            regularMemoryCache.trimToSize(regularMemoryCache.size() / 2)
        }
    }

    @JvmStatic
    fun retainOnly(liveTabIds: Collection<String>?) {
        if (liveTabIds == null) return
        val keep = HashSet<String>()
        for (id in liveTabIds) {
            if (!isBlank(id)) keep.add(diskKey(id) + ".thumb")
        }
        val generation = GENERATION.get()
        DISK.execute {
            val dir = getDiskCacheDir() ?: return@execute
            if (generation != GENERATION.get()) return@execute
            val files = dir.listFiles() ?: return@execute
            for (file in files) {
                if (!file.isFile || keep.contains(file.name)) continue
                if (file.name.endsWith(".tmp")) continue
                deleteQuietly(file)
            }
        }
    }

    private fun getDiskCacheDir(): File? {
        val dir = diskCacheDir
        if (dir != null && dir.isDirectory) return dir
        try {
            val app = com.petal.browser.PetalApplication.getInstance()
            if (app != null) initDiskCache(app)
        } catch (_: Exception) {}
        return diskCacheDir
    }

    @JvmStatic
    @Synchronized
    fun put(tabId: String?, bitmap: Bitmap?): Boolean = put(tabId, bitmap, false, false)

    @JvmStatic
    @Synchronized
    fun put(tabId: String?, bitmap: Bitmap?, isPrivate: Boolean): Boolean = put(tabId, bitmap, isPrivate, false)

    @JvmStatic
    @Synchronized
    fun put(tabId: String?, bitmap: Bitmap?, isPrivate: Boolean, allowUniform: Boolean): Boolean {
        if (isBlank(tabId) || bitmap == null || bitmap.isRecycled) return false
        if (if (allowUniform) isTransparentBitmap(bitmap) else isBlankBitmap(bitmap)) return false
        val snapshot = makeSnapshot(bitmap) ?: return false
        if (snapshot.isRecycled) return false
        val cache = if (isPrivate) privateMemoryCache else regularMemoryCache
        cache.put(tabId!!, snapshot)
        notifyChanged(tabId)
        if (isPrivate) return true

        val token = TOKENS.incrementAndGet()
        val generation = GENERATION.get()
        regularVersions[tabId] = token
        DISK.execute { saveToDisk(tabId, snapshot, token, generation) }
        return true
    }

    private fun isTransparentBitmap(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true
        try {
            for (yi in 0 until 24) {
                val y = Math.min(height - 1, ((yi + 0.5f) * height / 24).toInt())
                for (xi in 0 until 24) {
                    val x = Math.min(width - 1, ((xi + 0.5f) * width / 24).toInt())
                    if ((bitmap.getPixel(x, y) ushr 24) != 0) return false
                }
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    @JvmStatic
    fun isBlankBitmap(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.isRecycled) return true
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true
        val stepsX = Math.min(width, 48)
        val stepsY = Math.min(height, 48)
        try {
            var first = 0
            var initialized = false
            for (yi in 0 until stepsY) {
                val y = Math.min(height - 1, ((yi + 0.5f) * height / stepsY).toInt())
                for (xi in 0 until stepsX) {
                    val x = Math.min(width - 1, ((xi + 0.5f) * width / stepsX).toInt())
                    val pixel = bitmap.getPixel(x, y)
                    if (!initialized) {
                        first = pixel
                        initialized = true
                    } else if (pixel != first) {
                        return false
                    }
                }
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    @JvmStatic
    fun get(tabId: String?): Bitmap? = getMemoryOnly(tabId, false)

    @JvmStatic
    fun getMemoryOnly(tabId: String?): Bitmap? = getMemoryOnly(tabId, false)

    @JvmStatic
    fun getMemoryOnly(tabId: String?, isPrivate: Boolean): Bitmap? {
        if (isBlank(tabId)) return null
        val bitmap = (if (isPrivate) privateMemoryCache else regularMemoryCache).get(tabId!!)
        return if (bitmap != null && !bitmap.isRecycled) bitmap else null
    }

    @JvmStatic
    fun loadAsync(tabId: String?, callback: ThumbnailCallback) {
        loadFirstAsync(arrayOf(tabId ?: ""), false, callback)
    }

    @JvmStatic
    fun loadFirstAsync(identifiers: Array<String>, callback: ThumbnailCallback) {
        loadFirstAsync(identifiers, false, callback)
    }

    @JvmStatic
    fun loadFirstAsync(identifiers: Array<String>, isPrivate: Boolean, callback: ThumbnailCallback) {
        if (isPrivate) {
            for (id in identifiers) {
                val memory = getMemoryOnly(id, true)
                if (memory != null) {
                    dispatch(callback, memory)
                    return
                }
            }
            dispatch(callback, null)
            return
        }
        val uniqueIds = ArrayList<String>()
        val seenIds = HashSet<String>()
        for (id in identifiers) {
            if (!isBlank(id) && seenIds.add(id)) uniqueIds.add(id)
        }
        val ids = uniqueIds.toTypedArray()
        if (ids.isEmpty()) {
            dispatch(callback, null)
            return
        }
        val cache = if (isPrivate) privateMemoryCache else regularMemoryCache
        for (id in ids) {
            val memory = cache.get(id)
            if (memory != null && !memory.isRecycled) {
                dispatch(callback, memory)
                return
            }
        }
        val generation = GENERATION.get()
        val versions = LongArray(ids.size) { i -> versionOf(ids[i]) }
        val requestKey = ids.joinToString("\u0000") + "\u0002"
        if (!pendingLoads.add(requestKey)) {
            loadCallbacks.computeIfAbsent(requestKey) { CopyOnWriteArrayList() }.add(callback)
            return
        }
        val callbacks = CopyOnWriteArrayList<ThumbnailCallback>()
        callbacks.add(callback)
        loadCallbacks[requestKey] = callbacks
        DISK.execute {
            var loaded: Bitmap? = null
            try {
                var loadedId: String? = null
                for (id in ids) {
                    loaded = loadFromDisk(id)
                    if (loaded != null && !loaded.isRecycled) {
                        loadedId = id
                        break
                    }
                    loaded = null
                }
                val valid = generation == GENERATION.get() && !versionsChanged(ids, versions)
                if (!valid) {
                    if (loaded != null && !loaded.isRecycled) loaded.recycle()
                    loaded = null
                    // A newer thumbnail was stored while the disk read was running: hand that one
                    // over instead of reporting "no thumbnail" (which made previews flash empty).
                    for (id in ids) {
                        val fresh = regularMemoryCache.get(id)
                        if (fresh != null && !fresh.isRecycled) { loaded = fresh; break }
                    }
                } else if (loaded != null && loadedId != null) {
                    regularMemoryCache.put(loadedId, loaded)
                    val dir = getDiskCacheDir()
                    val canonical = if (dir == null) null else File(dir, diskKey(loadedId) + ".thumb")
                    if (canonical != null && !canonical.isFile) put(loadedId, loaded, false)
                }
            } finally {
                val completed = loadCallbacks.remove(requestKey)
                pendingLoads.remove(requestKey)
                if (completed != null) {
                    for (waiter in completed) dispatch(waiter, loaded)
                }
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun remove(tabId: String?) {
        if (isBlank(tabId)) return
        regularMemoryCache.remove(tabId!!)
        privateMemoryCache.remove(tabId)
        notifyChanged(tabId)
        val token = TOKENS.incrementAndGet()
        val generation = GENERATION.get()
        regularVersions[tabId] = token
        DISK.execute {
            if (generation != GENERATION.get() || versionOf(tabId) != token) return@execute
            val dir = getDiskCacheDir() ?: return@execute
            deleteQuietly(File(dir, diskKey(tabId) + ".thumb"))
            deleteQuietly(File(dir, legacyKey(tabId) + ".webp"))
            deleteQuietly(File(dir, legacyKey(tabId) + ".png"))
            regularVersions.remove(tabId, token)
        }
    }

    @JvmStatic
    fun removeAllIdentifiers(vararg ids: String?) {
        for (id in ids) if (!isBlank(id)) remove(id)
    }

    @JvmStatic
    fun clearPrivateCache() {
        privateMemoryCache.evictAll()
    }

    @JvmStatic
    @Synchronized
    fun removePrivate(tabId: String?) {
        if (!isBlank(tabId)) privateMemoryCache.remove(tabId!!)
    }

    @JvmStatic
    fun clearMemory() {
        regularMemoryCache.evictAll()
        privateMemoryCache.evictAll()
    }

    @JvmStatic
    fun clear() {
        evictAll()
    }

    @JvmStatic
    @Synchronized
    fun evictAll() {
        regularMemoryCache.evictAll()
        privateMemoryCache.evictAll()
        regularVersions.clear()
        pendingLoads.clear()
        val orphaned = ArrayList<ThumbnailCallback>()
        for (list in loadCallbacks.values) orphaned.addAll(list)
        loadCallbacks.clear()
        for (waiter in orphaned) dispatch(waiter, null)
        val generation = GENERATION.incrementAndGet()
        DISK.execute {
            if (generation != GENERATION.get()) return@execute
            val dir = getDiskCacheDir() ?: return@execute
            val files = dir.listFiles() ?: return@execute
            for (file in files) {
                if (generation != GENERATION.get()) return@execute
                if (file.isFile) deleteQuietly(file)
            }
        }
    }

    private fun makeSnapshot(source: Bitmap): Bitmap? {
        var software: Bitmap? = null
        return try {
            var from = source
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
                software = source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
                from = software
            }
            val width = from.width
            val height = from.height
            if (width <= 0 || height <= 0) return null
            val cropHeight = Math.min(height, (width * MAX_PREVIEW_ASPECT).roundToInt())
            val scale = Math.min(1f, MAX_PREVIEW_WIDTH / width.toFloat())
            val config = from.config ?: Bitmap.Config.ARGB_8888
            if (scale >= 1f && cropHeight == height) {
                return from.copy(config, false)
            }
            val outWidth = Math.max(1, (width * scale).roundToInt())
            val outHeight = Math.max(1, (cropHeight * scale).roundToInt())
            val out = Bitmap.createBitmap(outWidth, outHeight, config)
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(from, Rect(0, 0, width, cropHeight), Rect(0, 0, outWidth, outHeight), paint)
            out
        } catch (error: Throwable) {
            Log.w(TAG, "Could not snapshot tab thumbnail", error)
            null
        } finally {
            if (software != null && !software.isRecycled) software.recycle()
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_PREVIEW_WIDTH && bitmap.height <= MAX_PREVIEW_HEIGHT) return bitmap
        val fitted = makeSnapshot(bitmap)
        return fitted ?: bitmap
    }

    private fun saveToDisk(tabId: String, bitmap: Bitmap, token: Long, generation: Long) {
        val dir = getDiskCacheDir()
        if (dir == null || !isCurrent(tabId, token, generation) || bitmap.isRecycled) return
        val target = File(dir, diskKey(tabId) + ".thumb")
        val temp = File(dir, diskKey(tabId) + "." + token + ".tmp")
        try {
            val compressed: Boolean
            FileOutputStream(temp).use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                compressed = bitmap.compress(format, 86, out)
                out.flush()
                out.fd.sync()
            }
            if (!compressed || !isCurrent(tabId, token, generation)) {
                deleteQuietly(temp)
                return
            }
            if (!temp.renameTo(target)) {
                deleteQuietly(target)
                if (!temp.renameTo(target)) throw IOException("Could not publish thumbnail file")
            }
            if (!isCurrent(tabId, token, generation)) deleteQuietly(target)
            if (WRITES_SINCE_TRIM.incrementAndGet() >= TRIM_EVERY_N_WRITES) {
                WRITES_SINCE_TRIM.set(0)
                trimDiskCache(dir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving thumbnail for tab", e)
            deleteQuietly(temp)
        }
    }

    private fun loadFromDisk(tabId: String): Bitmap? {
        val dir = getDiskCacheDir() ?: return null
        var result = decodeThumbnail(File(dir, diskKey(tabId) + ".thumb"))
        if (result == null) {
            result = decodeThumbnail(File(dir, legacyKey(tabId) + ".webp"))
            if (result == null) result = decodeThumbnail(File(dir, legacyKey(tabId) + ".png"))
        }
        return result
    }

    private fun decodeThumbnail(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                deleteQuietly(file)
                return null
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > MAX_PREVIEW_WIDTH * 2) inSampleSize *= 2
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap == null || bitmap.isRecycled) {
                deleteQuietly(file)
                return null
            }
            file.setLastModified(System.currentTimeMillis())
            val thumbnail = downscaleIfNeeded(bitmap)
            if (thumbnail != bitmap && !bitmap.isRecycled) bitmap.recycle()
            thumbnail
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Not enough memory to decode tab thumbnail", error)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode tab thumbnail", e)
            deleteQuietly(file)
            null
        }
    }

    private fun trimDiskCache(dir: File) {
        val files = dir.listFiles { _, name ->
            name.endsWith(".thumb") || name.endsWith(".webp") || name.endsWith(".png") || name.endsWith(".tmp")
        } ?: return
        if (files.isEmpty()) return
        Arrays.sort(files, Comparator.comparingLong(File::lastModified))
        val now = System.currentTimeMillis()
        var total = 0L
        val retained = ArrayList<File>()
        for (file in files) {
            val staleTemp = file.name.endsWith(".tmp") && now - file.lastModified() > 60000L
            if (staleTemp || (!file.name.endsWith(".tmp") && !file.name.startsWith("v2_"))) {
                deleteQuietly(file)
            } else if (!file.name.endsWith(".tmp")) {
                total += file.length()
                retained.add(file)
            }
        }
        for (file in retained) {
            if (total <= MAX_DISK_CACHE_BYTES) break
            val size = file.length()
            if (deleteQuietly(file)) total -= size
        }
    }

    private fun diskKey(tabId: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(tabId.toByteArray(StandardCharsets.UTF_8))
            val key = StringBuilder("v2_")
            for (value in digest) key.append(String.format(Locale.ROOT, "%02x", value.toInt() and 0xff))
            key.toString()
        } catch (e: Exception) {
            throw IllegalStateException("SHA-256 is unavailable", e)
        }
    }

    private fun legacyKey(key: String): String {
        var sanitized = key.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        if (sanitized.length > 48) {
            val hash = Math.abs(key.hashCode()).toString()
            sanitized = sanitized.substring(0, 36) + "_" + hash
        }
        return sanitized
    }

    private fun isCurrent(tabId: String, token: Long, generation: Long): Boolean {
        return generation == GENERATION.get() && versionOf(tabId) == token
    }

    private fun versionOf(tabId: String): Long {
        return regularVersions[tabId] ?: 0L
    }

    private fun versionsChanged(ids: Array<String>, versions: LongArray): Boolean {
        for (i in ids.indices) {
            if (versionOf(ids[i]) != versions[i]) return true
        }
        return false
    }

    private fun isBlank(value: String?): Boolean = value.isNullOrBlank()

    private fun deleteQuietly(file: File): Boolean = !file.exists() || file.delete()

    private fun dispatch(callback: ThumbnailCallback, bitmap: Bitmap?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onThumbnailLoaded(bitmap)
        } else {
            MAIN.post { callback.onThumbnailLoaded(bitmap) }
        }
    }

    fun interface ThumbnailCallback {
        fun onThumbnailLoaded(bitmap: Bitmap?)
    }
}
