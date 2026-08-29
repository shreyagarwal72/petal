package com.petal.browser.unit;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import java.io.File;
import java.util.Objects;

/**
 * CacheManager: Handles robust clearing of HTTP cache, WebStorage, cookies,
 * and app_webview directory structures while maintaining safety across all Android versions.
 */
public class CacheManager {

    private static final String TAG = "CacheManager";

    /**
     * Clears all cache sources including HTTP cache, WebStorage, Cookies, and app_webview directories.
     *
     * @param context Application or Activity context.
     * @param activeWebView Optional active WebView instance to clear cache on; can be null.
     */
    public static void clearAllCache(Context context, WebView activeWebView) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        // 1. Clear Chromium WebView Cache (http/disk cache ONLY)
        try {
            BrowsingDataManager.clearCache(appContext, activeWebView);
        } catch (Exception e) {
            Log.w(TAG, "Error clearing Chromium cache sources", e);
        }

        // 2. Delete Application Cache Directory
        try {
            File cacheDir = appContext.getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteDirContents(cacheDir);
            }
            File extCacheDir = appContext.getExternalCacheDir();
            if (extCacheDir != null && extCacheDir.isDirectory()) {
                deleteDirContents(extCacheDir);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error clearing app cache directory", e);
        }

        // NOTE: We intentionally do NOT manually delete files inside app_webview
        // (Cache, Code Cache, GPUCache, blob_storage) here.
        //
        // Even after every NinjaWebView instance has been destroyed, Chromium's
        // WebView engine keeps process-wide singleton caches (HTTP disk cache,
        // V8 code cache, GPU shader cache) backed by memory-mapped index files.
        // Those singletons are not guaranteed to be torn down just because the
        // Java-level WebView objects are gone, since this cleanup can run from
        // onDestroy() while the app process (and the in-process WebView engine)
        // is still alive.
        //
        // Deleting those files out from under a live Chromium instance can
        // corrupt the on-disk cache index. That corruption doesn't crash the
        // app immediately - it crashes the *next* time the app launches and
        // Chromium tries to reinitialize against the now-corrupted cache files.
        //
        // Step 1 above (webView.clearCache(true), clearHttpResponseCache(),
        // clearProfileCacheAndStorage()) already clears these caches through
        // WebView's own safe, coordinated APIs, so this raw filesystem cleanup
        // is both redundant and dangerous. Do not reintroduce it.
    }

    /**
     * Recursively deletes a directory or file.
     */
    public static boolean deleteDir(File dir) {
        if (dir != null && dir.exists()) {
            if (dir.isDirectory()) {
                File[] children = dir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteDir(child);
                    }
                }
            }
            try {
                return dir.delete();
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    /**
     * Recursively deletes directory contents while keeping the top-level directory intact.
     */
    public static void deleteDirContents(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
    }
}
