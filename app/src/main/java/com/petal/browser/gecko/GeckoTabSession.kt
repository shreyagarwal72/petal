/*
 * GeckoTabSession.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Represents an individual browser tab powered by Mozilla GeckoView.
 *
 * Encapsulates:
 *   • Unique tab identifier and incognito flag
 *   • Dedicated [GeckoSession] instance with complete delegate wiring
 *   • Observable [GeckoBrowserState] tracking URL, title, progress, security & navigation
 *   • In-memory preview Bitmap for the Petal Tab Switcher grid
 *   • Safe lifecycle management (open, load, close, crash handling)
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.UUID

private const val TAG = "GeckoTabSession"

/**
 * Encapsulates a single Gecko-backed tab with its session, state, and UI bindings.
 */
class GeckoTabSession(
    val id: String = UUID.randomUUID().toString(),
    val isIncognito: Boolean = false,
    initialUrl: String = "about:blank",
    private val onNewSessionRequested: ((String) -> Unit)? = null,
    private val onCloseRequested: (() -> Unit)? = null
) {
    val state = GeckoBrowserState().apply {
        currentUrl = initialUrl
    }

    var previewBitmap: Bitmap? by mutableStateOf(null)
    var isOpened: Boolean = false
        private set

    val session: GeckoSession by lazy {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognito)
            .useTrackingProtection(true)
            .build()

        GeckoSession(settings).apply {
            progressDelegate = GeckoProgressDelegate(state)
            navigationDelegate = GeckoNavigationDelegate(
                state = state,
                onNewTabUri = { newUri ->
                    onNewSessionRequested?.invoke(newUri)
                }
            )
            contentDelegate = GeckoContentDelegate(
                state = state,
                onCloseWindow = {
                    onCloseRequested?.invoke()
                }
            )
        }
    }

    /**
     * Safely opens the session with the shared GeckoRuntime.
     * Idempotent — will not re-open an already open session.
     */
    fun openSafely(runtime: org.mozilla.geckoview.GeckoRuntime) {
        if (isOpened) return
        try {
            session.open(runtime)
            isOpened = true
            if (state.currentUrl.isNotBlank() && state.currentUrl != "about:blank") {
                session.loadUri(state.currentUrl)
            } else {
                session.loadUri("about:blank")
            }
            Log.i(TAG, "GeckoTabSession $id opened successfully (incognito=$isIncognito)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open GeckoTabSession $id", e)
        }
    }

    /**
     * Loads a new URI in this tab.
     */
    fun loadUri(uri: String) {
        try {
            state.currentUrl = uri
            if (isOpened) {
                session.loadUri(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load URI '$uri' in tab $id", e)
        }
    }

    /**
     * Stops current loading.
     */
    fun stop() {
        try {
            if (isOpened) session.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping session $id", e)
        }
    }

    /**
     * Reloads the current page.
     */
    fun reload() {
        try {
            if (isOpened) session.reload()
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading session $id", e)
        }
    }

    /**
     * Navigates back if possible.
     */
    fun goBack() {
        try {
            if (isOpened && state.canGoBack) {
                session.stop()
                session.goBack()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating back in session $id", e)
        }
    }

    /**
     * Navigates forward if possible.
     */
    fun goForward() {
        try {
            if (isOpened && state.canGoForward) session.goForward()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating forward in session $id", e)
        }
    }

    /**
     * Safely closes and releases the underlying GeckoSession resources.
     */
    fun closeSafely() {
        try {
            if (isOpened) {
                session.close()
                isOpened = false
            }
            Log.i(TAG, "GeckoTabSession $id closed cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GeckoTabSession $id", e)
        }
    }
}
