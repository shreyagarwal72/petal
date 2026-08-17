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

        // 1. Clear Chromium WebView Cache & WebStorage
        try {
            BrowsingDataManager.clearCache(appContext, activeWebView);
            BrowsingDataManager.clearWebStorage();
            BrowsingDataManager.clearCookies();
            BrowsingDataManager.clearAutofillData(appContext);
            BrowsingDataManager.clearPermissions();
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

        // 3. Safely clean app_webview directory structures.
        // This is only safe when NO WebView instance is alive anywhere in the app:
        // these files (IndexedDB, Local Storage, Web Data, QuotaManager, ...) are the
        // live Chromium profile that every open/stored tab reads and writes through.
        // Deleting them while a tab's WebView still holds them open crashes the
        // native engine - and the more tabs are stored, the more likely that is.
        try {
            if (com.petal.browser.browser.BrowserContainer.size() == 0) {
                File appDataDir = new File(appContext.getApplicationInfo().dataDir);
                File appWebviewDir = new File(appDataDir, "app_webview");
                if (appWebviewDir.exists() && appWebviewDir.isDirectory()) {
                    cleanWebviewData(appWebviewDir);
                }
            } else {
                Log.w(TAG, "Skipping raw app_webview cleanup: " +
                        com.petal.browser.browser.BrowserContainer.size() +
                        " tab(s) still hold live WebView instances");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning app_webview directory", e);
        }
    }

    /**
     * Cleans specific subdirectories inside app_webview safely without corrupting core WebView files.
     */
    private static void cleanWebviewData(File appWebviewDir) {
        // Roots to clean in app_webview root or app_webview/Default
        String[] targets = new String[]{
                "Cache",
                "Code Cache",
                "GPUCache",
                "blob_storage",
                "databases",
                "IndexedDB",
                "Local Storage",
                "Service Worker",
                "Session Storage",
                "shared_proto_db",
                "VideoDecodeStats",
                "QuotaManager",
                "QuotaManager-journal",
                "Web Data",
                "Web Data-journal"
        };

        File defaultDir = new File(appWebviewDir, "Default");
        for (String targetName : targets) {
            // Check direct root
            File rootTarget = new File(appWebviewDir, targetName);
            if (rootTarget.exists()) {
                deleteDir(rootTarget);
            }
            // Check Default profile dir
            if (defaultDir.exists() && defaultDir.isDirectory()) {
                File defaultTarget = new File(defaultDir, targetName);
                if (defaultTarget.exists()) {
                    deleteDir(defaultTarget);
                }
            }
        }
    }

    /**
     * Recursively deletes a directory or file.
     */
    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir != null && dir.delete();
    }

    /**
     * Recursively deletes directory contents while keeping the top-level directory intact.
     */
    public static void deleteDirContents(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    deleteDir(new File(dir, child));
                }
            }
        }
    }
}
