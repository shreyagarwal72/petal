package com.petal.browser.unit;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.petal.browser.engine.gecko.PetalGeckoRuntime;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.StorageController;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * CacheManager: Official Firefox (GeckoView / Fenix) grade website cache & storage manager.
 * ─────────────────────────────────────────────────────────────────────────────
 * Manages full and per-host clearing of HTTP/network cache, image decode cache,
 * DOM storage (localStorage, sessionStorage), IndexedDB, ServiceWorkers, and app cache.
 * Uses GeckoView's native StorageController while maintaining safe Chromium fallbacks.
 */
public class CacheManager {

    private static final String TAG = "CacheManager";

    /**
     * Clears all website cache sources (GeckoView network disk cache, memory cache,
     * Chromium webview fallback cache, and application temporary cache directories).
     *
     * @param context Application or Activity context.
     * @param activeWebView Optional active WebView instance to clear cache on; can be null.
     */
    public static void clearAllCache(@NonNull Context context, @Nullable WebView activeWebView) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        // 1. Clear Mozilla GeckoView Engine Cache (Official Firefox StorageController pipeline)
        try {
            if (PetalGeckoRuntime.isGeckoAvailable(appContext)) {
                // ALL_CACHES clears: Network disk cache, memory cache, image decodes, and shader caches
                PetalGeckoRuntime.clearData(appContext, StorageController.ClearFlags.ALL_CACHES);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error clearing GeckoView website cache", e);
        }

        // 2. Clear In-Memory Decoded Tab Thumbnails & Favicons
        try {
            TabThumbnailCache.clearMemory();
        } catch (Exception e) {
            Log.w(TAG, "Error trimming thumbnail memory cache", e);
        }

        // 3. Clear Chromium WebView Cache Fallback (for any embedded WebViews)
        try {
            BrowsingDataManager.clearCache(appContext, activeWebView);
        } catch (Exception e) {
            Log.w(TAG, "Error clearing Chromium cache sources", e);
        }

        // 4. Safely Delete Application Cache Directory without touching Gecko profiles
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File cacheDir = appContext.getCacheDir();
                if (cacheDir != null && cacheDir.isDirectory()) {
                    deleteDirContentsExcluding(cacheDir, "gecko", "profile", "cookies.sqlite");
                }
                File extCacheDir = appContext.getExternalCacheDir();
                if (extCacheDir != null && extCacheDir.isDirectory()) {
                    deleteDirContents(extCacheDir);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error clearing app cache directory", e);
            }
        });
    }

    /**
     * Clear all website data including caches, cookies, DOM storages, and IndexedDB
     * as per Firefox Fenix Clear Browsing Data specification.
     */
    public static void clearAllWebsiteData(@NonNull Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        try {
            if (PetalGeckoRuntime.isGeckoAvailable(appContext)) {
                long flags = StorageController.ClearFlags.ALL_CACHES
                        | StorageController.ClearFlags.COOKIES
                        | StorageController.ClearFlags.DOM_STORAGES
                        | StorageController.ClearFlags.AUTH_SESSIONS;
                PetalGeckoRuntime.clearData(appContext, flags);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error clearing all GeckoView website data", e);
        }

        clearAllCache(appContext, null);
    }

    /**
     * Clears cached data for a specific website host (Firefox per-site data clearing).
     *
     * @param context Application context.
     * @param host Domain or host name (e.g., "example.com").
     */
    public static void clearSiteCache(@NonNull Context context, @NonNull String host) {
        if (context == null || host == null || host.trim().isEmpty()) return;
        Context appContext = context.getApplicationContext();

        try {
            if (PetalGeckoRuntime.isGeckoAvailable(appContext)) {
                String cleanHost = host.trim().toLowerCase();
                if (cleanHost.startsWith("http://")) cleanHost = cleanHost.substring(7);
                if (cleanHost.startsWith("https://")) cleanHost = cleanHost.substring(8);
                int slashIndex = cleanHost.indexOf('/');
                if (slashIndex != -1) cleanHost = cleanHost.substring(0, slashIndex);

                PetalGeckoRuntime.getOrCreate(appContext)
                        .getStorageController()
                        .clearDataFromHost(cleanHost, StorageController.ClearFlags.ALL_CACHES | StorageController.ClearFlags.DOM_STORAGES);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error clearing site cache for host: " + host, e);
        }
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

    /**
     * Recursively deletes directory contents while protecting specific protected folder names.
     */
    public static void deleteDirContentsExcluding(File dir, String... excludedNames) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    boolean skip = false;
                    for (String ex : excludedNames) {
                        if (child.getName().equalsIgnoreCase(ex) || child.getName().toLowerCase().contains(ex.toLowerCase())) {
                            skip = true;
                            break;
                        }
                    }
                    if (!skip) {
                        deleteDir(child);
                    }
                }
            }
        }
    }
}
