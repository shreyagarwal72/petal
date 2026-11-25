package com.petal.browser.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * The real, measured heights of the bars, in pixels. Bars report their own size with
 * `onSizeChanged`; the page padding is derived from these numbers instead of guessed constants,
 * so STACKED merge layout, the floating nav's 10dp margin, status bar / cutout insets, gesture vs
 * 3-button navigation and tablet sizes are all correct by construction.
 */
object BarMetrics {

    /** Top container: status bar / cutout gap + optional desktop tab strip + a top address bar. 0 until measured. */
    var topBarPx by mutableIntStateOf(0)
        private set

    /** The address bar when it sits at the bottom (includes its own gesture inset when no nav is shown). */
    var bottomAddressPx by mutableIntStateOf(0)
        private set

    /** The bottom nav including its gesture inset and floating margin. 0 while not shown. */
    var bottomNavPx by mutableIntStateOf(0)
        private set

    /**
     * Reports are ignored while the bars are minimized or sliding: a collapsed bar is not composed
     * (so it would report a size of 0) and must never shrink the page padding.
     */
    private fun settled(): Boolean =
        BarCollapseState.collapsedFraction <= 0f && !BarCollapseState.viewportCollapsed

    fun reportTop(px: Int) { if (px > 0 && settled()) topBarPx = px }
    fun reportBottomAddress(px: Int) { if (px > 0 && settled()) bottomAddressPx = px }
    fun reportBottomNav(px: Int) { if (px > 0 && settled()) bottomNavPx = px }

    /** The nav was removed (overlay open, merge style on a page): it reserves nothing. */
    fun clearBottomNav() { bottomNavPx = 0 }

    /** The bottom address bar is not used (it is at the top, or not on a page). */
    fun clearBottomAddress() { bottomAddressPx = 0 }

    /** Page insets in pixels. */
    data class PagePadding(val top: Int, val bottom: Int)

    /**
     * Pure function: how much the page gives up to the bars at rest.
     *
     * @param measuredTop  [topBarPx]; 0 means "not measured yet", [estimatedTop] is used instead.
     * @param estimatedTop estimate used only until the first measurement arrives.
     * @param topOverlayPx status bar / cutout + desktop tab strip, used when the address bar is at the bottom.
     * @param navVisible   whether the bottom nav is shown.
     * @param navBarInset  gesture / 3-button navigation bar inset, reserved when nothing else covers it.
     * @param isFloatingNav true when the bottom nav floats, so the page runs edge-to-edge underneath it.
     * @param isFloatingBottomAddress true when the bottom address bar floats, so the page runs edge-to-edge underneath it.
     */
    @JvmStatic
    @JvmOverloads
    fun pagePadding(
        isBottomAddressBar: Boolean,
        measuredTop: Int,
        estimatedTop: Int,
        measuredBottomAddress: Int,
        estimatedBottomAddress: Int,
        measuredBottomNav: Int,
        estimatedBottomNav: Int,
        navVisible: Boolean,
        topOverlayPx: Int,
        navBarInset: Int,
        isFloatingNav: Boolean = false,
        isFloatingBottomAddress: Boolean = false
    ): PagePadding {
        val nav = if (!navVisible) 0 else if (measuredBottomNav > 0) measuredBottomNav else estimatedBottomNav
        return if (isBottomAddressBar) {
            val address = if (measuredBottomAddress > 0) measuredBottomAddress else estimatedBottomAddress
            val bottom = if (isFloatingBottomAddress) 0 else address + nav
            PagePadding(top = topOverlayPx, bottom = bottom)
        } else {
            val top = if (measuredTop > 0) measuredTop else estimatedTop
            val bottom = if (isFloatingNav) 0 else if (navVisible) nav else navBarInset
            PagePadding(top = top, bottom = bottom)
        }
    }
}
