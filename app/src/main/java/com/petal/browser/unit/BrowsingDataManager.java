package com.petal.browser.unit;

import android.content.Context;
import com.petal.browser.logger.PetalAppLogger;
import java.io.File;

/**
 * BrowsingDataManager: GeckoView-aligned browsing data cleaner.
 * Manages cache, cookies, DOM storages, permissions, and session cleanup
 * via GeckoView StorageController.
 */
public class BrowsingDataManager {

    private static final String TAG = "BrowsingDataManager";

    @Deprecated
    public static void configureWebSettings(Object webView, boolean isIncognito) {
        // No-op for GeckoView engine
    }

    public static void clearWebStorage() {
        try {
            Context ctx = com.petal.browser.PetalApplication.getInstance();
            if (ctx != null) {
                com.petal.browser.engine.gecko.PetalGeckoRuntime.clearData(
                    ctx,
                    org.mozilla.geckoview.StorageController.ClearFlags.DOM_STORAGES
                );
            }
        } catch (Throwable ignored) {}
    }

    public static void clearAutofillData(final Context context) {
        if (context == null) return;
        try {
            com.petal.browser.engine.gecko.PetalGeckoRuntime.clearData(
                context,
                org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS
            );
        } catch (Throwable ignored) {}
    }

    public static void clearCache(final Context context) {
        if (context == null) return;
        PetalCacheManager.clearCache(context, PetalCacheManager.Flags.ALL_CACHES, null);
    }

    public static void clearCookies(final Context context) {
        if (context == null) return;
        PetalCacheManager.clearCache(context, PetalCacheManager.Flags.COOKIES, null);
    }

    public static void clearPermissions(final Context context) {
        try {
            Context appCtx = context != null ? context : com.petal.browser.PetalApplication.getInstance();
            if (appCtx != null) {
                com.petal.browser.engine.gecko.PetalGeckoRuntime.clearData(
                    appCtx,
                    org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS
                );
            }
        } catch (Throwable ignored) {}
    }

    public static void clearBrowsingDataAsync(
        final Context context,
        final boolean cache,
        final boolean cookies,
        final boolean webStorage,
        final boolean autofill,
        final boolean permissions,
        final Runnable onCompleted
    ) {
        if (context == null) {
            if (onCompleted != null) onCompleted.run();
            return;
        }
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            long flags = 0L;
            if (cache) flags |= PetalCacheManager.Flags.ALL_CACHES;
            if (cookies) flags |= PetalCacheManager.Flags.COOKIES;
            if (webStorage) flags |= PetalCacheManager.Flags.DOM_WEB_STORAGE;
            if (autofill) clearAutofillData(context);
            if (permissions) clearPermissions(context);

            PetalCacheManager.clearCache(context, flags, onCompleted);
        });
    }

    @Deprecated
    public static void clearBrowsingDataAsync(
        final Context context,
        final Object webView,
        final boolean cache,
        final boolean cookies,
        final boolean webStorage,
        final boolean autofill,
        final boolean permissions,
        final Runnable onCompleted
    ) {
        clearBrowsingDataAsync(context, cache, cookies, webStorage, autofill, permissions, onCompleted);
    }

    public static void deleteDirContents(File dir) {
        PetalCacheManager.deleteDirContents(dir);
    }

    private static boolean deleteDir(File dir) {
        return PetalCacheManager.deleteDir(dir);
    }
}
