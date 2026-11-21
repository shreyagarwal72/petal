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
     * True while the web page is laid out for MINIMIZED bars (edge to edge). The page viewport is
     * resized only when this flips, never per animation frame: a Gecko resize is expensive and
     * leaves an unpainted block where the bar was while it catches up. The remaining distance is
     * covered by a translation that [ComposeEngineViewport] animates from [collapsedFraction].
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

    // ── Gecko viewport settle ────────────────────────────────────────────────
    // Resizing the viewport makes Gecko reflow and shift its scroll offset, which comes back as
    // scroll callbacks that look like user input. Instead of a fixed clock, input stays suppressed
    // until (1) the view reported its new layout and (2) Gecko has been quiet for SETTLE_QUIET_MS
    // after its last layout or scroll callback. SETTLE_HARD_CAP_MS only stops a lost callback from
    // suppressing scrolling forever.
    private const val SETTLE_QUIET_MS = 220L
    private const val SETTLE_HARD_CAP_MS = 1500L
    @Volatile private var settleActive = false
    @Volatile private var settleStartMs = 0L
    @Volatile private var settleLastActivityMs = 0L
    @Volatile private var settleSawLayout = false

    private fun markViewportFlip(newValue: Boolean) {
        if (newValue != viewportCollapsed) {
            val now = android.os.SystemClock.uptimeMillis()
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
        if (!settleActive) return
        settleSawLayout = true
        settleLastActivityMs = android.os.SystemClock.uptimeMillis()
    }

    /** Gecko reported a scroll offset while settling: it is still reacting to the resize. */
    @JvmStatic
    fun onGeckoScrollWhileSettling() {
        if (settleActive) settleLastActivityMs = android.os.SystemClock.uptimeMillis()
    }

    @JvmStatic
    fun isViewportSettling(): Boolean {
        if (!settleActive) return false
        val now = android.os.SystemClock.uptimeMillis()
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
