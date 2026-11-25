package com.petal.browser.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared state tracking the scroll minimization and morph of the Petal toolbar.
 *
 * [collapsedFraction] ranges from 0f (fully expanded bar) to 1f (compact minimized pill).
 * [MainActivity] drives the value during scroll animations; Compose bar composables observe it
 * to morph their shape, dimensions, and contents smoothly without jerky re-layouts.
 */
object BarCollapseState {

    /** 0f = fully open bar, 1f = fully collapsed into 36dp pill. */
    var collapsedFraction by mutableFloatStateOf(0f)

    /** Boolean flag tracking whether the bars are currently collapsed. */
    var isCollapsed by mutableStateOf(false)
        private set

    /**
     * Fallback preference: when true, restores the old padding-based viewport resize path instead of
     * GeckoView's dynamic toolbar + vertical clipping API. Kept behind a hidden fallback pref until
     * fixed-position bottom elements (e.g. YouTube bottom nav) are verified on device.
     */
    const val PREF_LEGACY_PADDING_VIEWPORT = "sp_legacy_padding_viewport"

    @Volatile
    @JvmField
    var legacyPaddingViewport: Boolean = false

    /**
     * True while the web page is laid out for MINIMIZED bars (edge to edge) in the legacy
     * padding-based viewport mode.
     */
    var viewportCollapsed by mutableStateOf(false)
        private set

    /** The one decision-maker for scroll-driven collapse / expand. See [BarScrollDecider]. */
    @JvmField
    val scrollDecider = BarScrollDecider()

    /**
     * True while the Compose viewport owns scroll-driven bar changes, so the legacy
     * PetalTabViewController scroll dispatcher must not make a second, competing decision.
     */
    @Volatile
    @JvmField
    var composeViewportOwnsScroll: Boolean = false

    /**
     * Uptime (ms) of the last time the page viewport was resized by a bar collapse/expand.
     * Kept for diagnostics only; suppression is driven by the settle state below, not by this.
     */
    @Volatile
    var lastViewportFlipUptimeMs: Long = 0L
        private set

    // ── Gecko viewport settle (legacy padding fallback only) ─────────────────
    // When legacyPaddingViewport is false, GeckoView's dynamic toolbar + vertical clipping never
    // reflows the page, so settle suppression is bypassed. When legacyPaddingViewport is true,
    // input stays suppressed until (1) the view reported its new layout and (2) Gecko has been
    // quiet for SETTLE_QUIET_MS after its last layout or scroll callback.
    private const val SETTLE_QUIET_MS = 220L
    private const val SETTLE_HARD_CAP_MS = 1500L
    @Volatile private var settleActive = false
    @Volatile private var settleStartMs = 0L
    @Volatile private var settleLastActivityMs = 0L
    @Volatile private var settleSawLayout = false

    @JvmField
    @Volatile
    var uptimeMillisProvider: () -> Long = { android.os.SystemClock.uptimeMillis() }

    private fun markViewportFlip(newValue: Boolean) {
        if (!legacyPaddingViewport) {
            settleActive = false
            return
        }
        if (newValue != viewportCollapsed) {
            val now = uptimeMillisProvider()
            lastViewportFlipUptimeMs = now
            settleStartMs = now
            settleLastActivityMs = now
            settleSawLayout = false
            settleActive = true
        }
    }

    /** The page view finished a layout pass (or no layout change was needed) after a viewport flip. */
    @JvmStatic
    fun onViewportLayoutApplied() {
        if (!legacyPaddingViewport || !settleActive) return
        settleSawLayout = true
        settleLastActivityMs = uptimeMillisProvider()
    }

    /** Gecko reported a scroll offset while settling: it is still reacting to the resize. */
    @JvmStatic
    fun onGeckoScrollWhileSettling() {
        if (legacyPaddingViewport && settleActive) {
            settleLastActivityMs = uptimeMillisProvider()
        }
    }

    @JvmStatic
    fun isViewportSettling(): Boolean {
        if (!legacyPaddingViewport || !settleActive) return false
        val now = uptimeMillisProvider()
        if (now - settleStartMs >= SETTLE_HARD_CAP_MS) { settleActive = false; return false }
        if (!settleSawLayout) return true
        if (now - settleLastActivityMs >= SETTLE_QUIET_MS) { settleActive = false; return false }
        return true
    }

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
        markViewportFlip(collapsed)
        viewportCollapsed = collapsed
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
        markViewportFlip(clamped > 0.001f)
        viewportCollapsed = clamped > 0.001f
    }

    /**
     * Grow the page into the space of the bars BEFORE they start to fade, so Gecko has painted the
     * new area by the time the bar stops covering it.
     */
    @JvmStatic
    fun beginViewportCollapse() {
        markViewportFlip(true)
        viewportCollapsed = true
    }

    @JvmStatic
    fun requestExpand() {
        onExpandRequested?.invoke()
    }
}
