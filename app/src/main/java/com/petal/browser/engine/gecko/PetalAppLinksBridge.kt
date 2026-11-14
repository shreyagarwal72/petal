package com.petal.browser.engine.gecko

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.activity.MainActivity
import com.petal.browser.ui.components.PetalAlertDialogBuilder
import mozilla.components.feature.app.links.AppLinksUseCases
import java.util.Locale

/**
 * PetalAppLinksBridge
 * ─────────────────────────────────────────────────────────────────────────────
 * Bridges Mozilla Android Components `feature-app-links` and `feature-intent`
 * into Petal Browser navigation and intent handling.
 *
 * Implements:
 * - Strict user-gesture requirement before launching external apps (Firefox semantics).
 * - Full `intent://` scheme parsing with `browser_fallback_url` fallback.
 * - Incognito departure confirmation dialog (preserving private mode boundaries).
 * - "Auto Open External Apps" (`sp_auto_open_apps`) for apps claiming HTTP(S) links.
 * - Petal internal scheme whitelist (`petal:`, `moz-extension:`, etc.).
 */
object PetalAppLinksBridge {
    private const val TAG = "PetalAppLinksBridge"

    /**
     * All URI schemes handled internally by Petal Browser. Web pages or links
     * matching these schemes must load inside the engine and never launch external apps.
     */
    val INTERNAL_SCHEMES: List<String> = listOf(
        "http:",
        "https:",
        "about:",
        "petal:",
        "data:",
        "blob:",
        "file:",
        "javascript:",
        "view-source:",
        "resource:",
        "moz-extension:",
        "content:",
        "ws:",
        "wss:",
        "ftp:"
    )

    @JvmStatic
    fun isInternalScheme(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmedLower = url.trim().lowercase(Locale.ROOT)
        return INTERNAL_SCHEMES.any { trimmedLower.startsWith(it) }
    }

    @JvmStatic
    fun isExternalScheme(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return !isInternalScheme(url)
    }

    /**
     * Handles external load requests according to Mozilla AC 159.0.1 and Firefox mobile rules.
     *
     * @return true if the navigation was consumed (either handled externally or blocked),
     *         false if the engine should proceed with its normal internal load.
     */
    @JvmStatic
    fun handleExternalLoadRequest(
        activity: Activity,
        uri: String,
        hasUserGesture: Boolean,
        isIncognito: Boolean,
        isRedirect: Boolean,
        isDirectNavigation: Boolean,
        onLoadFallbackUrl: ((String) -> Unit)? = null
    ): Boolean {
        if (uri.isBlank() || isInternalScheme(uri)) {
            return false
        }

        // Firefox security semantics: never launch an external app without a direct user gesture.
        // Scripted background redirects without user input are blocked from opening apps.
        if (!hasUserGesture && !isDirectNavigation && isRedirect) {
            Log.w(TAG, "Blocked unsolicited external link redirect without user gesture: $uri")
            return true
        }

        val appContext = activity.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val autoOpenApps = sp.getBoolean("sp_auto_open_apps", false)

        val useCases = AppLinksUseCases(
            context = appContext,
            launchInApp = { autoOpenApps }
        )

        val redirect = useCases.interceptedAppLinkRedirect(uri)
        val appIntent = redirect.appIntent

        if (appIntent != null) {
            if (isIncognito) {
                showIncognitoPrompt(activity, redirect.appName) {
                    useCases.openAppLink(
                        appIntent = appIntent,
                        launchInNewTask = true,
                        failedToLaunchAction = { fallbackUrl ->
                            handleFailedLaunch(activity, fallbackUrl, onLoadFallbackUrl)
                        }
                    )
                }
                return true
            }

            useCases.openAppLink(
                appIntent = appIntent,
                launchInNewTask = true,
                failedToLaunchAction = { fallbackUrl ->
                    handleFailedLaunch(activity, fallbackUrl, onLoadFallbackUrl)
                }
            )
            return true
        }

        // Fallback URL handling for intent:// links
        val fallback = redirect.fallbackUrl
        if (!fallback.isNullOrBlank()) {
            handleFailedLaunch(activity, fallback, onLoadFallbackUrl)
            return true
        }

        // Marketplace fallback for missing packages
        val marketIntent = redirect.marketplaceIntent
        if (marketIntent != null && hasUserGesture) {
            try {
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(marketIntent)
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "Failed launching marketplace intent: ${t.message}")
            }
        }

        // Generic fallback for custom schemes like tel:, mailto:, sms:, geo:
        try {
            val parsed = Uri.parse(uri.trim())
            val viewIntent = Intent(Intent.ACTION_VIEW, parsed).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (viewIntent.resolveActivity(activity.packageManager) != null) {
                if (isIncognito) {
                    showIncognitoPrompt(activity, parsed.scheme ?: "an external app") {
                        try {
                            activity.startActivity(viewIntent)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed launching scheme activity: ${t.message}")
                        }
                    }
                } else {
                    activity.startActivity(viewIntent)
                }
                return true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error resolving custom scheme $uri: ${t.message}")
        }

        return true
    }

    private fun handleFailedLaunch(
        activity: Activity,
        fallbackUrl: String?,
        onLoadFallbackUrl: ((String) -> Unit)?
    ) {
        if (!fallbackUrl.isNullOrBlank()) {
            if (onLoadFallbackUrl != null) {
                onLoadFallbackUrl(fallbackUrl)
            } else if (activity is MainActivity) {
                activity.viewModel.loadUrl(fallbackUrl)
            }
        }
    }

    private fun showIncognitoPrompt(
        activity: Activity,
        appName: String,
        onConfirmed: () -> Unit
    ) {
        if (activity.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return
        val targetName = appName.ifBlank { "an external application" }
        PetalAlertDialogBuilder(activity)
            .setTitle("Open External Application")
            .setMessage("Opening this link will launch $targetName outside of Private Browsing. Your activity in $targetName will not be protected by Private Browsing.\n\nDo you want to continue?")
            .setPositiveButton("Open") { dialog, _ ->
                dialog.dismiss()
                onConfirmed()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
