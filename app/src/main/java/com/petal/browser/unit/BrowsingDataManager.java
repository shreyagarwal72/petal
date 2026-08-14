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
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.LaunchKt;

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
        }
    }

    public static void clearCache(Context context, WebView webView) {
        if (webView != null) {
            webView.clearCache(true);
        }
        if (context != null) {
            try {
                deleteDir(context.getCacheDir());
                deleteDir(context.getExternalCacheDir());
            } catch (Exception ignored) {}
        }
    }

    public static void clearCookies() {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } catch (Exception ignored) {}
    }

    public static void clearWebStorage() {
        try {
            WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {}
    }

    public static void clearAutofillData(Context context) {
        if (context == null) return;
        try {
            WebViewDatabase webViewDatabase = WebViewDatabase.getInstance(context);
            if (webViewDatabase != null) {
                webViewDatabase.clearHttpAuthUsernamePassword();
                webViewDatabase.clearFormData();
            }
        } catch (Exception ignored) {}
    }

    public static void clearPermissions() {
        try {
            GeolocationPermissions.getInstance().clearAll();
        } catch (Exception ignored) {}
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
        LaunchKt.launch$default(
            androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycleScope(),
            Dispatchers.getIO(),
            null,
            (scope, continuation) -> {
                if (cache) clearCache(context, webView);
                if (cookies) clearCookies();
                if (webStorage) clearWebStorage();
                if (autofill) clearAutofillData(context);
                if (permissions) clearPermissions();

                if (onCompleted != null) {
                    LaunchKt.launch$default(
                        androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycleScope(),
                        Dispatchers.getMain(),
                        null,
                        (s, c) -> {
                            onCompleted.run();
                            return kotlin.Unit.INSTANCE;
                        },
                        2,
                        null
                    );
                }
                return kotlin.Unit.INSTANCE;
            },
            2,
            null
        );
    }

    private static boolean deleteDir(File dir) {
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
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }
}
