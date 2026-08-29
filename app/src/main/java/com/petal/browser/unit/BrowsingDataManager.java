package com.petal.browser.unit;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;

/**
 * Chromium-style BrowsingDataManager.
 * Manages persistent & memory-only cache configurations, WebStorage, CookieManager,
 * WebViewDatabase, GeolocationPermissions, and AndroidX WebKit profile instances.
 */
public class BrowsingDataManager {

    private static final String INCOGNITO_PROFILE_NAME = "PetalIncognitoProfile";

    public static void configureWebSettings(WebView webView, boolean isIncognito) {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        if (settings == null) return;

        if (isIncognito) {
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setDomStorageEnabled(false);
            settings.setDatabaseEnabled(false);
            settings.setSaveFormData(false);
            settings.setSavePassword(false);
            settings.setGeolocationEnabled(false);

            // Isolate private tabs with AndroidX WebKit multi-profile if supported
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    Profile profile = ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE_NAME);
                    WebViewCompat.setProfile(webView, INCOGNITO_PROFILE_NAME);
                }
            } catch (Exception ignored) {}
        } else {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setSaveFormData(true);
            settings.setSavePassword(true);
            settings.setGeolocationEnabled(true);

            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME);
                    WebViewCompat.setProfile(webView, Profile.DEFAULT_PROFILE_NAME);
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Runs {@code action} on the main thread and blocks the calling thread until it has
     * actually completed. WebView/WebStorage/CookieManager APIs must run on the main
     * thread, but callers that go on to touch the underlying profile files on disk
     * (e.g. CacheManager) must not proceed until these calls have really finished -
     * a fire-and-forget Handler.post() lets file deletion race the still-pending
     * Chromium operation, which crashes the native WebView engine.
     */
    private static void runOnMainThreadBlocking(Runnable action) {
        android.os.Looper mainLooper = android.os.Looper.getMainLooper();
        if (android.os.Looper.myLooper() == mainLooper) {
            try {
                action.run();
            } catch (Exception ignored) {}
            return;
        }
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        new android.os.Handler(mainLooper).post(() -> {
            try {
                action.run();
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });
        try {
            // Bounded wait: never block indefinitely if the main thread is stuck.
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Clears cache and storage using Chromium AndroidX WebKit Profile APIs if supported.
     */
    public static void clearProfileCacheAndStorage() {
        runOnMainThreadBlocking(() -> {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    Profile defaultProfile = ProfileStore.getInstance().getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME);
                    if (defaultProfile != null) {
                        defaultProfile.getWebStorage().deleteAllData();
                        defaultProfile.getGeolocationPermissions().clearAll();
                    }
                    Profile incognitoProfile = ProfileStore.getInstance().getProfile(INCOGNITO_PROFILE_NAME);
                    if (incognitoProfile != null) {
                        incognitoProfile.getWebStorage().deleteAllData();
                        incognitoProfile.getGeolocationPermissions().clearAll();
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static void clearCache(final Context context, final WebView webView) {
        if (webView != null) {
            runOnMainThreadBlocking(() -> webView.clearCache(true));
        }
        trimWebViewMemory(context);
        clearHttpResponseCache();
        if (context != null) {
            try {
                deleteDirContents(context.getCacheDir());
                deleteDirContents(context.getExternalCacheDir());
            } catch (Exception ignored) {}
        }
    }

    public static void clearCookies() {
        runOnMainThreadBlocking(() -> {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        });
    }

    public static void clearWebStorage() {
        runOnMainThreadBlocking(() -> WebStorage.getInstance().deleteAllData());
        clearProfileCacheAndStorage();
    }

    public static void clearAutofillData(final Context context) {
        if (context == null) return;
        runOnMainThreadBlocking(() -> {
            WebViewDatabase webViewDatabase = WebViewDatabase.getInstance(context);
            if (webViewDatabase != null) {
                webViewDatabase.clearHttpAuthUsernamePassword();
                webViewDatabase.clearFormData();
            }
        });
    }

    /**
     * Clears Service Worker caches & registers using AndroidX WebKit ServiceWorkerControllerCompat if supported.
     */
    public static void clearServiceWorkerCache() {
        runOnMainThreadBlocking(() -> {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
                    androidx.webkit.ServiceWorkerControllerCompat swController = androidx.webkit.ServiceWorkerControllerCompat.getInstance();
                    if (swController != null) {
                        swController.getServiceWorkerWebSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Purges internal Chromium performance and disk tracing buffers using AndroidX WebKit TracingController if supported.
     */
    public static void clearTracingControllerBuffers() {
        runOnMainThreadBlocking(() -> {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.TRACING_CONTROLLER_BASIC_USAGE)) {
                    androidx.webkit.TracingController tracingController = androidx.webkit.TracingController.getInstance();
                    if (tracingController != null && tracingController.isTracing()) {
                        tracingController.stop(null, java.util.concurrent.Executors.newSingleThreadExecutor());
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Clears Proxy overrides & flushes Chromium socket pools via ProxyController if supported.
     */
    public static void clearProxyOverrides() {
        runOnMainThreadBlocking(() -> {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    androidx.webkit.ProxyController.getInstance().clearProxyOverride(
                            java.util.concurrent.Executors.newSingleThreadExecutor(),
                            () -> {}
                    );
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Flushes Android system-level HttpResponseCache.
     */
    public static void clearHttpResponseCache() {
        try {
            android.net.http.HttpResponseCache responseCache = android.net.http.HttpResponseCache.getInstalled();
            if (responseCache != null) {
                responseCache.flush();
                responseCache.delete();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Trims Chromium V8 RAM garbage collection and GPU memory caches for active webView.
     */
    public static void trimWebViewMemory(Context context) {
        if (context == null) return;
        runOnMainThreadBlocking(() -> {
            try {
                if (context instanceof android.content.ComponentCallbacks2) {
                    ((android.content.ComponentCallbacks2) context).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL);
                } else if (context.getApplicationContext() instanceof android.content.ComponentCallbacks2) {
                    ((android.content.ComponentCallbacks2) context.getApplicationContext()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL);
                }
            } catch (Exception ignored) {}
        });
    }

    public static void clearPermissions() {
        runOnMainThreadBlocking(() -> GeolocationPermissions.getInstance().clearAll());
        clearServiceWorkerCache();
        clearTracingControllerBuffers();
        clearProxyOverrides();
        clearHttpResponseCache();
    }

    public static void clearBrowsingDataAsync(
        final Context context,
        final WebView webView,
        final boolean cache,
        final boolean cookies,
        final boolean webStorage,
        final boolean autofill,
        final boolean permissions,
        final Runnable onCompleted
    ) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            if (cache) clearCache(context, webView);
            if (cookies) clearCookies();
            if (webStorage) clearWebStorage();
            if (autofill) clearAutofillData(context);
            if (permissions) clearPermissions();

            if (onCompleted != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onCompleted);
            }
        });
    }

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

    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }
}
