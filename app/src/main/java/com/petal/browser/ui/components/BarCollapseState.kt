package com.petal.browser.ui.components

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Signal-driven settle tracker for real Gecko viewport size changes (e.g. orientation, desktop tab
 * bar toggle, or fallback resize). Never extends its window on continuous user scrolling: once a
 * layout pass has been observed, it finishes on Gecko scroll-end or after [POST_LAYOUT_FALLBACK_MS]
 * from the layout pass, with [HARD_CAP_MS] strictly as a safety net against lost callbacks.
 */
class ViewportSettleTracker(private val nowMs: () -> Long = { SystemClock.uptimeMillis() }) {

    companion object {
        const val POST_LAYOUT_FALLBACK_MS = 80L
        const val HARD_CAP_MS = 800L
    }

    @Volatile private var active = false
    @Volatile private var startMs = 0L
    @Volatile private var layoutAppliedMs = 0L
    @Volatile private var sawLayout = false
    @Volatile private var sawScrollEnd = false

    fun beginSettle() {
        val now = nowMs()
        startMs = now
        layoutAppliedMs = 0L
        sawLayout = false
        sawScrollEnd = false
        active = true
    }

    fun onLayoutApplied() {
        if (!active) return
        sawLayout = true
        if (sawScrollEnd) {
            active = false
            return
        }
        layoutAppliedMs = nowMs()
    }

    fun onGeckoScrollEnd() {
        if (!active) return
        sawScrollEnd = true
        if (sawLayout) {
            active = false
        }
    }

    fun onGeckoScroll() {
        if (!active) return
        if (sawLayout && nowMs() - layoutAppliedMs >= POST_LAYOUT_FALLBACK_MS) {
            active = false
        }
    }

    fun isSettling(): Boolean {
        if (!active) return false
        val now = nowMs()
        if (now - startMs >= HARD_CAP_MS) {
            active = false
            return false
        }
        if (sawLayout && (sawScrollEnd || now - layoutAppliedMs >= POST_LAYOUT_FALLBACK_MS)) {
            active = false
            return false
        }
        return true
    }

    fun reset() {
        active = false
        startMs = 0L
        layoutAppliedMs = 0L
        sawLayout = false
        sawScrollEnd = false
    }
}

/**
 * Shared state tracking the scroll minimization and morph of the Petal toolbar.
 *
 * [collapsedFraction] ranges from 0f (fully expanded bar) to 1f (compact minimized pill).
 * [MainActivity] drives the value during scroll animations; Compose bar composables read it
 * inside draw/graphicsLayer phases so animations never recompose the bar tree per frame.
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
     * padding-based viewport mode. Under dynamic-toolbar mode, Gecko's static padding does not
     * change on collapse/expand flips; only translation and vertical clipping animate.
     */
    var viewportCollapsed by mutableStateOf(false)
        private set

    /**
     * True only while the web page is actively scrolling/flinging. Flips once at scroll start
     * and once when scroll settles, allowing background/glass effects to stay static mid-scroll.
     */
    var isPageScrolling by mutableStateOf(false)

    /** The one decision-maker for scroll-driven collapse / expand. See [BarScrollDecider]. */
    @JvmField
    val scrollDecider = BarScrollDecider()

    @JvmField
    val settleTracker = ViewportSettleTracker()

    @Volatile
    @JvmField
    var composeViewportOwnsScroll: Boolean = false

    @Volatile
    var lastViewportFlipUptimeMs: Long = 0L
        private set

    @JvmStatic
    fun onViewportResizeRequested() {
        lastViewportFlipUptimeMs = SystemClock.uptimeMillis()
        settleTracker.beginSettle()
    }

    @JvmStatic
    fun onViewportLayoutApplied() {
        settleTracker.onLayoutApplied()
    }

    @JvmStatic
    fun onGeckoScrollEnd() {
        settleTracker.onGeckoScrollEnd()
    }

    @JvmStatic
    fun onGeckoScrollWhileSettling() {
        settleTracker.onGeckoScroll()
    }

    @JvmStatic
    fun isViewportSettling(): Boolean = settleTracker.isSettling()

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
        viewportCollapsed = collapsed
        collapsedFraction = if (collapsed) 1f else 0f
    }

    @JvmStatic
    fun setFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        collapsedFraction = clamped
        isCollapsed = clamped >= 0.5f
        viewportCollapsed = clamped > 0.001f
    }

    @JvmStatic
    fun beginViewportCollapse() {
        viewportCollapsed = true
    }

    @JvmStatic
    fun resetScrollSession() {
        isPageScrolling = false
        settleTracker.reset()
        scrollDecider.reset()
    }

    @JvmStatic
    fun requestExpand() {
        onExpandRequested?.invoke()
    }
}
