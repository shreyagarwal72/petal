package com.petal.browser.unit

import android.content.ComponentCallbacks2
import android.content.Context
import android.net.http.HttpResponseCache
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.StorageController
import java.io.File
import java.util.concurrent.Executors

/**
 * PetalCacheManager: High-grade website & application cache manager implementing
 * the official Mozilla Firefox (GeckoView StorageController) caching architecture.
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
        const val PERMISSIONS = 1L shl 7
        const val AUTH_SESSIONS = 1L shl 8
        const val SITE_SETTINGS = 1L shl 9
        const val ALL_CACHES = NETWORK_CACHE or IMAGE_MEDIA_CACHE or APP_TEMP_CACHE or CODE_CACHE
        const val ALL_WEBSITE_DATA = ALL_CACHES or DOM_WEB_STORAGE or COOKIES or PERMISSIONS or AUTH_SESSIONS or SITE_SETTINGS
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
                val size = calculateDirSizeExcluding(cacheDir, "gecko", "mozilla", "profile", "cookies.sqlite", "databases")
                map["app_cache"] = size
                total += size
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed calculating app cache size: ${e.message}")
        }

        // 1b. Gecko Web Cache (HTTP disk cache / startup cache inside cacheDir/mozilla or gecko profile)
        try {
            var geckoWebCacheBytes = 0L
            for (parent in listOfNotNull(appContext.cacheDir, appContext.filesDir)) {
                val mozillaDir = File(parent, "mozilla")
                if (mozillaDir.isDirectory) {
                    mozillaDir.listFiles()?.forEach { profileDir ->
                        if (profileDir.isDirectory) {
                            geckoWebCacheBytes += calculateDirSize(File(profileDir, "cache2"))
                            geckoWebCacheBytes += calculateDirSize(File(profileDir, "startupCache"))
                            geckoWebCacheBytes += calculateDirSize(File(profileDir, "OfflineCache"))
                        }
                    }
                }
            }
            if (geckoWebCacheBytes > 0L) {
                map["gecko_web_cache"] = geckoWebCacheBytes
                total += geckoWebCacheBytes
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed calculating Gecko web cache size: ${e.message}")
        }

        // 1c. Website DOM / IndexedDB / ServiceWorker storage inside Gecko profile
        try {
            var siteStorageBytes = 0L
            val mozillaFilesDir = File(appContext.filesDir, "mozilla")
            if (mozillaFilesDir.isDirectory) {
                mozillaFilesDir.listFiles()?.forEach { profileDir ->
                    if (profileDir.isDirectory) {
                        siteStorageBytes += calculateDirSize(File(profileDir, "storage"))
                    }
                }
            }
            if (siteStorageBytes > 0L) {
                map["site_storage"] = siteStorageBytes
                total += siteStorageBytes
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed calculating site storage size: ${e.message}")
        }

        // 2. External Cache Dir
        try {
            val extCache = appContext.externalCacheDir
            if (extCache != null && extCache.isDirectory) {
                val size = calculateDirSize(extCache)
                map["external_cache"] = size
                total += size
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed calculating external cache size: ${e.message}")
        }

        // 3. Tab Thumbnails Dir
        try {
            val thumbDir = File(appContext.filesDir, "petal_tab_thumbnails")
            if (thumbDir.isDirectory) {
                val size = calculateDirSize(thumbDir)
                map["thumbnails"] = size
                total += size
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed calculating thumbnail cache size: ${e.message}")
        }

        // 4. Favicon database
        try {
            val faviconDb = appContext.getDatabasePath("favicon.db")
            if (faviconDb != null && faviconDb.isFile) {
                val size = faviconDb.length()
                map["favicon_db"] = size
                total += size
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed calculating favicon db size: ${e.message}")
        }

        // 5. Code Cache Dir
        try {
            val codeCache = appContext.codeCacheDir
            if (codeCache != null && codeCache.isDirectory) {
                val size = calculateDirSize(codeCache)
                map["code_cache"] = size
                total += size
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed calculating code cache size: ${e.message}")
        }

        CacheSizeInfo(total, formatBytes(total), map)
    }

    /**
     * Clears all cache sources (GeckoView network disk cache, memory cache,
     * Chromium webview fallback cache, tab thumbnails, and application temporary cache directories).
     */
    @JvmStatic
    @JvmOverloads
    fun clearAllCache(context: Context) {
        clearCache(context, Flags.ALL_CACHES, null)
    }

    @Deprecated("Legacy WebView parameter no longer used in GeckoView architecture")
    @JvmStatic
    fun clearAllCache(context: Context, activeWebView: Any?) {
        clearCache(context, Flags.ALL_CACHES, null)
    }

    /**
     * Clear all website data including caches, cookies, DOM storages, and IndexedDB
     * as per Firefox Fenix Clear Browsing Data specification.
     */
    @JvmStatic
    fun clearAllWebsiteData(context: Context) {
        clearCache(context, Flags.ALL_WEBSITE_DATA, null)
    }

    /**
     * Clears all cache sources matching the official Firefox Fenix + Chromium pipeline.
     */
    @JvmStatic
    @JvmOverloads
    fun clearCache(
        context: Context,
        flags: Long = Flags.ALL_CACHES,
        onCompleted: Runnable? = null
    ) {
        val appContext = context.applicationContext

        val pendingTasks = java.util.concurrent.atomic.AtomicInteger(1)
        val finishOne = {
            if (pendingTasks.decrementAndGet() == 0 && onCompleted != null) {
                mainHandler.post(onCompleted)
            }
        }

        // 1. Mozilla GeckoView StorageController pipeline (Firefox official engine)
        try {
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
            if ((flags and Flags.PERMISSIONS) != 0L) {
                geckoFlags = geckoFlags or StorageController.ClearFlags.PERMISSIONS
            }
            if ((flags and Flags.AUTH_SESSIONS) != 0L) {
                geckoFlags = geckoFlags or StorageController.ClearFlags.AUTH_SESSIONS
            }
            if ((flags and Flags.SITE_SETTINGS) != 0L) {
                geckoFlags = geckoFlags or StorageController.ClearFlags.SITE_SETTINGS
            }
            if (geckoFlags != 0L) {
                pendingTasks.incrementAndGet()
                PetalGeckoRuntime.clearDataAsync(appContext, geckoFlags) {
                    finishOne()
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

        // 3. Cookies cleanup
        if ((flags and Flags.COOKIES) != 0L) {
            com.petal.browser.download.PetalGeckoCookieBridge.clearAllCookies()
        }

        // 4. Legacy HTTP response cache: only when the network cache was requested
        if ((flags and Flags.NETWORK_CACHE) != 0L) {
            try {
                HttpResponseCache.getInstalled()?.delete()
            } catch (_: Exception) {}
        }

        // 5. File system temporary directories on background worker
        diskExecutor.execute {
            try {
                if ((flags and Flags.APP_TEMP_CACHE) != 0L) {
                    val cacheDir = appContext.cacheDir
                    if (cacheDir != null && cacheDir.isDirectory) {
                        deleteDirContentsExcluding(cacheDir, "gecko", "mozilla", "profile", "cookies.sqlite", "databases")
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
                finishOne()
            }
        }
    }

    @Deprecated("Legacy WebView parameter no longer used in GeckoView architecture")
    @JvmStatic
    fun clearCache(
        context: Context,
        flags: Long,
        activeWebView: Any?,
        onCompleted: Runnable?
    ) {
        clearCache(context, flags, onCompleted)
    }

    /**
     * Clears cached data for a specific host/domain (Per-site data clearing).
     */
    @JvmStatic
    fun clearSiteCache(context: Context, host: String) {
        clearSiteData(context, host, includeCookies = false, includePermissions = false)
    }

    /**
     * Official Firefox (GeckoView StorageController) per-site data clearing.
     * Clears caches, DOM storages, IndexedDB, cookies, and optionally permissions.
     */
    @JvmStatic
    @JvmOverloads
    fun clearSiteData(
        context: Context,
        host: String,
        includeCookies: Boolean = true,
        includePermissions: Boolean = false
    ) {
        if (host.isBlank()) return
        val appContext = context.applicationContext
        try {
            var cleanHost = host.trim().lowercase()
            if (cleanHost.startsWith("http://")) cleanHost = cleanHost.substring(7)
            if (cleanHost.startsWith("https://")) cleanHost = cleanHost.substring(8)
            val slash = cleanHost.indexOf('/')
            if (slash != -1) cleanHost = cleanHost.substring(0, slash)
            val colon = cleanHost.indexOf(':')
            if (colon != -1) cleanHost = cleanHost.substring(0, colon)

            var flags = StorageController.ClearFlags.ALL_CACHES or
                    StorageController.ClearFlags.DOM_STORAGES or
                    StorageController.ClearFlags.AUTH_SESSIONS
            if (includeCookies) {
                flags = flags or StorageController.ClearFlags.COOKIES
            }
            if (includePermissions) {
                flags = flags or StorageController.ClearFlags.PERMISSIONS
            }

            val storageController = PetalGeckoRuntime.getOrCreate(appContext).storageController
            storageController.clearDataFromHost(cleanHost, flags)

            // Also clear base domain if host is a subdomain (e.g., www.example.com -> example.com)
            // eTLD+1 via the public suffix list ("a.b.example.co.uk" -> "example.co.uk");
            // the old "last two labels" shortcut cleared "co.uk" instead.
            val baseDomain = try {
                com.petal.browser.engine.petal.blocking.PetalPublicSuffixRules.registrableDomain(cleanHost)
            } catch (_: Throwable) { null }
            if (!baseDomain.isNullOrBlank() && baseDomain != cleanHost) {
                storageController.clearDataFromBaseDomain(baseDomain, flags)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing site data for host: $host", e)
        }
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
