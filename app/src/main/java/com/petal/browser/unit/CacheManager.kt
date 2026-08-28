package com.petal.browser.unit

import android.content.Context
import android.util.Log
import android.webkit.WebView
import java.io.File

/**
 * CacheManager: Handles robust clearing of HTTP cache, WebStorage, cookies,
 * and app_webview directory structures while maintaining safety across all Android versions.
 */
object CacheManager {

    private const val TAG = "CacheManager"

    @JvmStatic
    fun clearAllCache(context: Context?, activeWebView: WebView?) {
        if (context == null) return
        val appContext = context.applicationContext

        try {
            BrowsingDataManager.clearCache(appContext, activeWebView)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing Chromium cache sources", e)
        }

        try {
            val cacheDir = appContext.cacheDir
            if (cacheDir != null && cacheDir.isDirectory) {
                deleteDirContents(cacheDir)
            }
            val extCacheDir = appContext.externalCacheDir
            if (extCacheDir != null && extCacheDir.isDirectory) {
                deleteDirContents(extCacheDir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing app cache directory", e)
        }
    }

    private fun deleteDirContents(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        var success = true
        val children = dir.listFiles()
        if (children != null) {
            for (child in children) {
                if (child.isDirectory) {
                    success = deleteDirContents(child) && child.delete() && success
                } else {
                    success = child.delete() && success
                }
            }
        }
        return success
    }
}
