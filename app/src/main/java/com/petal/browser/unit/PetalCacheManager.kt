package com.petal.browser.unit

import android.content.ComponentCallbacks2
import android.content.Context
import android.net.http.HttpResponseCache
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.StorageController
import java.io.File
import java.util.concurrent.Executors

/**
 * PetalCacheManager: High-grade website & application cache manager implementing
 * the official Mozilla Firefox (GeckoView StorageController) and Chromium (WebView / WebStorage)
 * caching standards.
 */
object PetalCacheManager {

    private const val TAG = "PetalCacheManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val diskExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PetalCacheWorker").apply { isDaemon = true }
    }

    /** Cache categories corresponding to standard browser and app storage */
    object Flags {
        const val NETWORK_CACHE = 1L shl 0
        const val IMAGE_MEDIA_CACHE = 1L shl 1
        const val DOM_WEB_STORAGE = 1L shl 2
        const val APP_TEMP_CACHE = 1L shl 3
        const val THUMBNAILS_CACHE = 1L shl 4
        const val CODE_CACHE = 1L shl 5
        const val COOKIES = 1L shl 6
        const val ALL_CACHES = NETWORK_CACHE or IMAGE_MEDIA_CACHE or APP_TEMP_CACHE or CODE_CACHE
        const val ALL_WEBSITE_DATA = ALL_CACHES or DOM_WEB_STORAGE or COOKIES
    }

    data class CacheSizeInfo(
        val totalBytes: Long,
        val formattedSize: String,
        val details: Map<String, Long> = emptyMap()
    )

    /**
     * Calculates the cache size across all subsystems asynchronously.
     */
    @JvmStatic
    suspend fun calculateCacheSize(context: Context): CacheSizeInfo = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        var total: Long = 0
        val map = mutableMapOf<String, Long>()

        // 1. App Cache Dir (excluding protected database/profile entries)
        try {
            val cacheDir = appContext.cacheDir
            if (cacheDir != null && cacheDir.isDirectory) {
                val size = calculateDirSizeExcluding(cacheDir, "gecko", "profile", "cookies.sqlite", "databases")
                map["app_cache"] = size
                total += size
            }
        } catch (_: Exception) {}

        // 2. External Cache Dir
        try {
            val extCache = appContext.externalCacheDir
            if (extCache != null && extCache.isDirectory) {
                val size = calculateDirSize(extCache)
                map["external_cache"] = size
                total += size
            }
        } catch (_: Exception) {}

        // 3. Tab Thumbnails Dir
        try {
            val thumbDir = File(appContext.filesDir, "petal_tab_thumbnails")
            if (thumbDir.isDirectory) {
                val size = calculateDirSize(thumbDir)
                map["thumbnails"] = size
                total += size
            }
        } catch (_: Exception) {}

        // 4. Code Cache Dir
        try {
            val codeCache = appContext.codeCacheDir
            if (codeCache != null && codeCache.isDirectory) {
                val size = calculateDirSize(codeCache)
                map["code_cache"] = size
                total += size
            }
        } catch (_: Exception) {}

        CacheSizeInfo(total, formatBytes(total), map)
    }

    /**
     * Clears all cache sources (GeckoView network disk cache, memory cache,
     * Chromium webview fallback cache, tab thumbnails, and application temporary cache directories).
     */
    @JvmStatic
    @JvmOverloads
    fun clearAllCache(context: Context, activeWebView: WebView? = null) {
        clearCache(context, Flags.ALL_CACHES, activeWebView, null)
    }

    /**
     * Clear all website data including caches, cookies, DOM storages, and IndexedDB
     * as per Firefox Fenix Clear Browsing Data specification.
     */
    @JvmStatic
    fun clearAllWebsiteData(context: Context) {
        clearCache(context, Flags.ALL_WEBSITE_DATA, null, null)
    }

    /**
     * Clears all cache sources matching the official Firefox Fenix + Chromium pipeline.
     */
    @JvmStatic
    @JvmOverloads
    fun clearCache(
        context: Context,
        flags: Long = Flags.ALL_CACHES,
        activeWebView: WebView? = null,
        onCompleted: Runnable? = null
    ) {
        val appContext = context.applicationContext

        // 1. Mozilla GeckoView StorageController pipeline (Firefox official engine)
        try {
            if (PetalGeckoRuntime.isGeckoAvailable(appContext)) {
                var geckoFlags = 0L
                if ((flags and Flags.NETWORK_CACHE) != 0L) {
                    geckoFlags = geckoFlags or StorageController.ClearFlags.ALL_CACHES
                }
                if ((flags and Flags.IMAGE_MEDIA_CACHE) != 0L) {
                    geckoFlags = geckoFlags or StorageController.ClearFlags.IMAGE_CACHE
                }
                if ((flags and Flags.DOM_WEB_STORAGE) != 0L) {
                    geckoFlags = geckoFlags or StorageController.ClearFlags.DOM_STORAGES
                }
                if ((flags and Flags.COOKIES) != 0L) {
                    geckoFlags = geckoFlags or StorageController.ClearFlags.COOKIES
                    geckoFlags = geckoFlags or StorageController.ClearFlags.AUTH_SESSIONS
                }
                if (geckoFlags != 0L) {
                    PetalGeckoRuntime.clearData(appContext, geckoFlags)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing GeckoView storage", e)
        }

        // 2. Tab Thumbnail Cache (memory and disk) - only when THUMBNAILS_CACHE explicitly requested
        if ((flags and Flags.THUMBNAILS_CACHE) != 0L) {
            try {
                TabThumbnailCache.clearMemory()
                TabThumbnailCache.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing tab thumbnail cache", e)
            }
        }

        // 3. Chromium WebStorage & Multi-profile fallback (Chromium official engine)
        runOnMainThread {
            try {
                if ((flags and Flags.NETWORK_CACHE) != 0L) {
                    activeWebView?.clearCache(true)
                }
                if ((flags and Flags.DOM_WEB_STORAGE) != 0L) {
                    WebStorage.getInstance().deleteAllData()
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                        try {
                            ProfileStore.getInstance().getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME)?.webStorage?.deleteAllData()
                        } catch (_: Exception) {}
                    }
                }
                if ((flags and Flags.COOKIES) != 0L) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing Chromium storage", e)
            }
        }

        // 4. In-memory / HTTP caches
        try {
            HttpResponseCache.getInstalled()?.delete()
        } catch (_: Exception) {}

        // 5. File system temporary directories on background worker
        diskExecutor.execute {
            try {
                if ((flags and Flags.APP_TEMP_CACHE) != 0L || (flags and Flags.NETWORK_CACHE) != 0L) {
                    val cacheDir = appContext.cacheDir
                    if (cacheDir != null && cacheDir.isDirectory) {
                        deleteDirContentsExcluding(cacheDir, "gecko", "profile", "cookies.sqlite", "databases")
                    }
                    val extCache = appContext.externalCacheDir
                    if (extCache != null && extCache.isDirectory) {
                        deleteDirContents(extCache)
                    }
                }

                if ((flags and Flags.CODE_CACHE) != 0L) {
                    val codeCache = appContext.codeCacheDir
                    if (codeCache != null && codeCache.isDirectory) {
                        deleteDirContents(codeCache)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting cache directory contents", e)
            } finally {
                if (onCompleted != null) {
                    mainHandler.post(onCompleted)
                }
            }
        }
    }

    /**
     * Clears cached data for a specific host/domain (Per-site data clearing).
     */
    @JvmStatic
    fun clearSiteCache(context: Context, host: String) {
        if (host.isBlank()) return
        val appContext = context.applicationContext
        try {
            if (PetalGeckoRuntime.isGeckoAvailable(appContext)) {
                var cleanHost = host.trim().lowercase()
                if (cleanHost.startsWith("http://")) cleanHost = cleanHost.substring(7)
                if (cleanHost.startsWith("https://")) cleanHost = cleanHost.substring(8)
                val slash = cleanHost.indexOf('/')
                if (slash != -1) cleanHost = cleanHost.substring(0, slash)

                PetalGeckoRuntime.getOrCreate(appContext)
                    .storageController
                    .clearDataFromHost(cleanHost, StorageController.ClearFlags.ALL_CACHES or StorageController.ClearFlags.DOM_STORAGES)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing site cache for host: $host", e)
        }
    }

    /**
     * Official memory trim integration for low-memory events.
     */
    @JvmStatic
    fun onTrimMemory(context: Context, level: Int) {
        val appContext = context.applicationContext
        try {
            PetalGeckoRuntime.onTrimMemory(appContext, level)
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
                TabThumbnailCache.clearMemory()
            }
        } catch (_: Throwable) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    @JvmStatic
    fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var size = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            size += if (child.isDirectory) calculateDirSize(child) else child.length()
        }
        return size
    }

    @JvmStatic
    fun calculateDirSizeExcluding(dir: File?, vararg excludedNames: String): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var size = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            val name = child.name.lowercase()
            val skip = excludedNames.any { name.contains(it.lowercase()) }
            if (!skip) {
                size += if (child.isDirectory) calculateDirSize(child) else child.length()
            }
        }
        return size
    }

    @JvmStatic
    fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.exists()) {
            if (dir.isDirectory) {
                val children = dir.listFiles()
                if (children != null) {
                    for (child in children) {
                        deleteDir(child)
                    }
                }
            }
            return try { dir.delete() } catch (_: Exception) { false }
        }
        return false
    }

    @JvmStatic
    fun deleteDirContents(dir: File?) {
        if (dir != null && dir.isDirectory) {
            val children = dir.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteDir(child)
                }
            }
        }
    }

    @JvmStatic
    fun deleteDirContentsExcluding(dir: File?, vararg excludedNames: String) {
        if (dir != null && dir.isDirectory) {
            val children = dir.listFiles()
            if (children != null) {
                for (child in children) {
                    val name = child.name.lowercase()
                    val skip = excludedNames.any { name.contains(it.lowercase()) }
                    if (!skip) {
                        deleteDir(child)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
    }
}
