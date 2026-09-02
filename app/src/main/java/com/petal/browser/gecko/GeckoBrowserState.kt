/*
 * GeckoBrowserState.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Reactive Compose state holder for a single GeckoView browser tab.
 * All fields are observable via Jetpack Compose State so the UI recomposes
 * automatically when navigation, progress, or security state changes.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mutable Compose state bag describing the current browser tab.
 *
 * Properties are backed by [androidx.compose.runtime.State] so any Composable
 * that reads them will automatically recompose on change.
 */
class GeckoBrowserState {

    /** The URL currently loaded or being loaded. */
    var currentUrl: String by mutableStateOf("about:blank")
        internal set

    /** Page <title> text received from the engine. */
    var pageTitle: String by mutableStateOf("")
        internal set

    /**
     * Normalised load progress in the range [0f, 1f].
     * 0f = not started / complete, growing to 1f = fully loaded.
     */
    var progress: Float by mutableFloatStateOf(0f)
        internal set

    /** True while the page is actively loading. */
    var isLoading: Boolean by mutableStateOf(false)
        internal set

    /** True if the session history has a previous entry. */
    var canGoBack: Boolean by mutableStateOf(false)
        internal set

    /** True if the session history has a next entry (after navigating back). */
    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    /**
     * True when the current page was served over a valid, unrevoked TLS/SSL
     * connection (i.e. the padlock should be shown green/closed).
     */
    var isSecure: Boolean by mutableStateOf(false)
        internal set
}
