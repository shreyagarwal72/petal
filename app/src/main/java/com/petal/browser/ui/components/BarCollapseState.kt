package com.petal.browser.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared state tracking the scroll minimization and morph of the Petal toolbar.
 *
 * [collapsedFraction] ranges from 0f (fully expanded bar) to 1f (compact minimized pill).
 * [BrowserActivity] drives the value during scroll animations; Compose bar composables observe it
 * to morph their shape, dimensions, and contents smoothly without jerky re-layouts.
 */
object BarCollapseState {

    /** 0f = fully open bar, 1f = fully collapsed into 36dp pill. */
    var collapsedFraction by mutableFloatStateOf(0f)

    /** Boolean flag tracking whether the bars are currently collapsed. */
    var isCollapsed by mutableStateOf(false)
        private set

    /** Extracted domain of the active web page (e.g. "google.com", "wikipedia.org"). */
    var currentDomain by mutableStateOf("")

    /** True if active page is loaded over HTTPS. */
    var isHttps by mutableStateOf(true)

    /** True if active tab is in Incognito mode. */
    var isIncognito by mutableStateOf(false)

    /** Callback invoked when the user taps or swipes on the minimized pill to expand the bar. */
    var onExpandRequested: (() -> Unit)? = null

    @JvmStatic
    fun updatePage(url: String?, incognito: Boolean) {
        isIncognito = incognito
        if (url.isNullOrBlank() || url == "about:blank" || url.startsWith("file:///android_asset/")) {
            currentDomain = ""
            isHttps = true
            return
        }
        isHttps = url.startsWith("https://")
        val clean = when {
            url.startsWith("https://") -> url.substring(8)
            url.startsWith("http://") -> url.substring(7)
            else -> url
        }
        val slashIdx = clean.indexOf('/')
        currentDomain = if (slashIdx != -1) clean.substring(0, slashIdx) else clean
    }

    @JvmStatic
    fun updateCollapsed(collapsed: Boolean) {
        isCollapsed = collapsed
        if (!collapsed) {
            collapsedFraction = 0f
        } else {
            collapsedFraction = 1f
        }
    }

    @JvmStatic
    fun setFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        collapsedFraction = clamped
        isCollapsed = clamped >= 0.5f
    }

    @JvmStatic
    fun requestExpand() {
        onExpandRequested?.invoke()
    }
}
