package com.petal.browser.unit

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File

/**
 * Chromium-style BrowsingDataManager.
 * Manages persistent & memory-only cache configurations, WebStorage, CookieManager,
 * WebViewDatabase, GeolocationPermissions, and AndroidX WebKit profile instances.
 */
object BrowsingDataManager {

    private const val INCOGNITO_PROFILE_NAME = "PetalIncognitoProfile"

    @JvmStatic
    fun configureWebSettings(webView: WebView?, isIncognito: Boolean) {
        if (webView == null) return
        val settings: WebSettings = webView.settings ?: return

        if (isIncognito) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.domStorageEnabled = false
            settings.databaseEnabled = false
            settings.saveFormData = false
            settings.savePassword = false
            settings.setGeolocationEnabled(false)

            // Isolate private tabs with AndroidX WebKit multi-profile if supported
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE_NAME)
                    WebViewCompat.setProfile(webView, INCOGNITO_PROFILE_NAME)
                }
            } catch (ignored: Exception) {
            }
        } else {
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.saveFormData = true
            settings.savePassword = true
            settings.setGeolocationEnabled(true)

            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME)
                    WebViewCompat.setProfile(webView, Profile.DEFAULT_PROFILE_NAME)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    @JvmStatic
    fun clearCache(context: Context?, activeWebView: WebView?) {
        if (context == null) return
        try {
            activeWebView?.clearCache(true)
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun clearCookies(context: Context?) {
        if (context == null) return
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun clearWebStorage() {
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun clearFormData(context: Context?) {
        if (context == null) return
        try {
            val db = WebViewDatabase.getInstance(context)
            db.clearFormData()
            db.clearHttpAuthUsernamePassword()
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun clearGeolocationPermissions() {
        try {
            GeolocationPermissions.getInstance().clearAll()
        } catch (ignored: Exception) {
        }
    }
}
