package com.petal.browser.ui.components

import android.content.SharedPreferences

/**
 * The one place that decides how much of the screen the page gives up to the address bar and
 * the bottom nav.
 *
 * Rule: at rest the page never sits under a bar. It may flow under the bars only while they
 * are minimized or moving (that is what [Result.hiddenTop] / [Result.hiddenBottom] describe).
 *
 * The old "page runs under glass bars" look is kept behind the hidden pref
 * [PREF_GLASS_PAGE_UNDER_BARS] (default false, no UI).
 */
object BarInsets {

    const val PREF_GLASS_PAGE_UNDER_BARS = "sp_glass_page_under_bars"

    /** Pixel insets for the page area. [top]/[bottom] with the bars shown, hidden* minimized. */
    data class Result(val top: Int, val bottom: Int, val hiddenTop: Int, val hiddenBottom: Int)

    @JvmStatic
    fun legacyPageUnderBars(sp: SharedPreferences): Boolean =
        sp.getBoolean(PREF_GLASS_PAGE_UNDER_BARS, false)

    /**
     * Which view a glass bar samples. When the page is padded away from the bars, every glass
     * bar (top address bar, bottom address bar, nav) sits over the wallpaper backdrop. With the
     * legacy pref on, the old rule applies: only flat bars sample the backdrop.
     */
    @JvmStatic
    fun glassSamplesWallpaper(
        sp: SharedPreferences,
        isBottomAddressBar: Boolean,
        floatingNav: Boolean,
        forNav: Boolean
    ): Boolean {
        if (!sp.getBoolean("sp_liquid_glass_enabled", false)) return false
        if (!legacyPageUnderBars(sp)) return true
        return if (forNav) (isBottomAddressBar || !floatingNav) else isBottomAddressBar
    }

    /**
     * @param addressHeight measured height of the address bar; it already includes the status
     *   bar gap when the bar is at the top.
     * @param bottomNavHeight real nav height (0 when the nav is not shown). Callers keep the
     *   guard against the container measuring at full-screen height.
     * @param legacyUnderTop / [legacyUnderBottom] only true when the legacy pref is on AND the
     *   old code would have let the page run under that edge.
     * @param pillCollapse whether PILL collapse mode is active instead of HIDE.
     * @param pillHeight measured height of the minimized pill.
     * @param navBarBottomInset gesture navigation bar inset at the bottom of the screen.
     */
    @JvmStatic
    @JvmOverloads
    fun compute(
        isBottom: Boolean,
        reserveBarSpace: Boolean,
        addressHeight: Int,
        bottomNavHeight: Int,
        gap: Int,
        statusBarGap: Int,
        legacyUnderTop: Boolean,
        legacyUnderBottom: Boolean,
        pillCollapse: Boolean = false,
        pillHeight: Int = 0,
        navBarBottomInset: Int = 0
    ): Result {
        if (!reserveBarSpace) return Result(0, 0, 0, 0)

        val top = when {
            isBottom -> statusBarGap
            legacyUnderTop -> statusBarGap
            else -> addressHeight + gap
        }
        val bottom = when {
            legacyUnderBottom -> 0
            isBottom -> addressHeight + bottomNavHeight
            else -> bottomNavHeight
        }

        // Minimized:
        // In HIDE mode: the nav and bottom address bar slide away; top address bar leaves statusBarGap.
        // In PILL mode: the bar morphs into the compact floating pill over the web content.
        // The page flows edge-to-edge under the floating pill without creating dead window space.
        val hiddenTop: Int
        val hiddenBottom: Int
        if (pillCollapse && pillHeight > 0) {
            hiddenTop = statusBarGap
            hiddenBottom = 0
        } else {
            hiddenTop = if (isBottom) top else minOf(top, statusBarGap)
            hiddenBottom = maxOf(0, bottom - (if (isBottom) addressHeight else 0) - bottomNavHeight)
        }
        return Result(top, bottom, hiddenTop, hiddenBottom)
    }
}
