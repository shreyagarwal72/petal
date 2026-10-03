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
        PetalCacheManager.clearAllCache(context, activeWebView);
    }

    /**
     * Clear all website data including caches, cookies, DOM storages, and IndexedDB
     * as per Firefox Fenix Clear Browsing Data specification.
     */
    public static void clearAllWebsiteData(@NonNull Context context) {
        PetalCacheManager.clearAllWebsiteData(context);
    }

    /**
     * Clears cached data for a specific website host (Firefox per-site data clearing).
     *
     * @param context Application context.
     * @param host Domain or host name (e.g., "example.com").
     */
    public static void clearSiteCache(@NonNull Context context, @NonNull String host) {
        PetalCacheManager.clearSiteCache(context, host);
    }

    /**
     * Recursively deletes a directory or file.
     */
    public static boolean deleteDir(File dir) {
        return PetalCacheManager.deleteDir(dir);
    }

    /**
     * Recursively deletes directory contents while keeping the top-level directory intact.
     */
    public static void deleteDirContents(File dir) {
        PetalCacheManager.deleteDirContents(dir);
    }

    /**
     * Recursively deletes directory contents while protecting specific protected folder names.
     */
    public static void deleteDirContentsExcluding(File dir, String... excludedNames) {
        PetalCacheManager.deleteDirContentsExcluding(dir, excludedNames);
    }
}
