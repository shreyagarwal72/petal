package com.petal.browser.unit

import android.content.Context
import com.petal.browser.PetalApplication
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import org.mozilla.geckoview.StorageController
import java.io.File
import java.util.concurrent.Executors

/**
 * BrowsingDataManager: GeckoView-aligned browsing data cleaner.
 * Manages cache, cookies, DOM storages, permissions, and session cleanup
 * via GeckoView StorageController.
 */
object BrowsingDataManager {

    @Deprecated("No-op for GeckoView engine")
    @JvmStatic
    fun configureWebSettings(webView: Any?, isIncognito: Boolean) {
        // No-op for GeckoView engine
    }

    @JvmStatic
    fun clearWebStorage() {
        try {
            val ctx = PetalApplication.getInstance()
            if (ctx != null) {
                PetalGeckoRuntime.clearData(
                    ctx,
                    StorageController.ClearFlags.DOM_STORAGES
                )
            }
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun clearAutofillData(context: Context?) {
        if (context == null) return
        try {
            PetalGeckoRuntime.clearData(
                context,
                StorageController.ClearFlags.AUTH_SESSIONS
            )
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun clearCache(context: Context?) {
        if (context == null) return
        PetalCacheManager.clearCache(context, PetalCacheManager.Flags.ALL_CACHES, null)
    }

    @JvmStatic
    fun clearCookies(context: Context?) {
        if (context == null) return
        PetalCacheManager.clearCache(context, PetalCacheManager.Flags.COOKIES, null)
    }

    @JvmStatic
    fun clearPermissions(context: Context?) {
        try {
            val appCtx = context ?: PetalApplication.getInstance()
            if (appCtx != null) {
                PetalGeckoRuntime.clearData(
                    appCtx,
                    StorageController.ClearFlags.PERMISSIONS
                )
            }
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun clearBrowsingDataAsync(
        context: Context?,
        cache: Boolean,
        cookies: Boolean,
        webStorage: Boolean,
        autofill: Boolean,
        permissions: Boolean,
        onCompleted: Runnable?
    ) {
        if (context == null) {
            onCompleted?.run()
            return
        }
        Executors.newSingleThreadExecutor().execute {
            var flags = 0L
            if (cache) flags = flags or PetalCacheManager.Flags.ALL_CACHES
            if (cookies) flags = flags or PetalCacheManager.Flags.COOKIES
            if (webStorage) flags = flags or PetalCacheManager.Flags.DOM_WEB_STORAGE
            if (autofill) clearAutofillData(context)
            if (permissions) clearPermissions(context)

            PetalCacheManager.clearCache(context, flags, onCompleted)
        }
    }

    @Deprecated("Legacy WebView overload")
    @JvmStatic
    fun clearBrowsingDataAsync(
        context: Context?,
        webView: Any?,
        cache: Boolean,
        cookies: Boolean,
        webStorage: Boolean,
        autofill: Boolean,
        permissions: Boolean,
        onCompleted: Runnable?
    ) {
        clearBrowsingDataAsync(context, cache, cookies, webStorage, autofill, permissions, onCompleted)
    }

    @JvmStatic
    fun deleteDirContents(dir: File?) {
        PetalCacheManager.deleteDirContents(dir)
    }
}
