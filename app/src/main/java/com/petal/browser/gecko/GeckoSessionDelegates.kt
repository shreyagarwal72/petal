/*
 * GeckoSessionDelegates.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Concrete implementations of GeckoSession delegate interfaces that pipe
 * engine callbacks into a [GeckoBrowserState] Compose state holder.
 *
 * All delegate callbacks arrive on the main thread so direct mutableState
 * assignments are safe without additional coroutine dispatching.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.util.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate.SecurityInformation

private const val TAG = "GeckoSessionDelegates"

// ── Progress Delegate ─────────────────────────────────────────────────────

/**
 * Wires [GeckoSession.ProgressDelegate] callbacks into [GeckoBrowserState].
 *
 * @param state the mutable state bag written to on every callback.
 */
class GeckoProgressDelegate(
    private val state: GeckoBrowserState
) : ProgressDelegate {

    override fun onPageStart(session: GeckoSession, url: String) {
        Log.d(TAG, "onPageStart: $url")
        state.currentUrl = url
        state.isLoading = true
        state.progress = 0.05f   // show minimal progress bar immediately
        state.isSecure = false
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        Log.d(TAG, "onPageStop success=$success")
        state.isLoading = false
        state.progress = 1f
    }

    override fun onProgressChange(session: GeckoSession, progress: Int) {
        // progress arrives as 0–100; normalise to 0f–1f
        state.progress = progress / 100f
    }

    override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
        // no-op; session state restoration handled elsewhere
    }

    override fun onSecurityChange(session: GeckoSession, securityInfo: SecurityInformation) {
        state.isSecure = securityInfo.isSecure
        Log.d(TAG, "Security: isSecure=${securityInfo.isSecure} host=${securityInfo.host}")
    }
}

// ── Navigation Delegate ───────────────────────────────────────────────────

/**
 * Wires [GeckoSession.NavigationDelegate] callbacks into [GeckoBrowserState].
 */
class GeckoNavigationDelegate(
    private val state: GeckoBrowserState
) : NavigationDelegate {

    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean
    ) {
        val resolved = url ?: "about:blank"
        Log.d(TAG, "onLocationChange: $resolved")
        state.currentUrl = resolved
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        state.canGoBack = canGoBack
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        state.canGoForward = canGoForward
    }

    override fun onLoadRequest(
        session: GeckoSession,
        request: NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny>? {
        // Allow all loads; custom URL interception can be layered here later.
        return GeckoResult.allow()
    }

    override fun onNewSession(
        session: GeckoSession,
        uri: String
    ): GeckoResult<GeckoSession>? {
        // Popup / new-window request — return null to block silently.
        // A future multi-tab implementation can intercept here instead.
        Log.d(TAG, "onNewSession requested for: $uri — blocked (single-tab mode)")
        return null
    }
}

// ── Content Delegate (title updates) ─────────────────────────────────────

/**
 * Minimal [GeckoSession.ContentDelegate] wiring that captures page titles.
 */
class GeckoContentDelegate(
    private val state: GeckoBrowserState
) : GeckoSession.ContentDelegate {

    override fun onTitleChange(session: GeckoSession, title: String?) {
        state.pageTitle = title ?: ""
    }
}
