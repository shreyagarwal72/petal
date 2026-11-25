package com.petal.browser.ui.components

/**
 * Pure collapse math shared by [PetalAddressBar], [PetalMergedBar], [PetalBottomNavBar], and [PetalMinimizedPill].
 *
 * Inputs are the collapse style and [BarCollapseState.collapsedFraction]; outputs are unit-less
 * multipliers or dp values so every bar animates on one synchronized curve and the math is unit testable.
 *
 * - [BarCollapseStyle.PILL]: the full bar, merged side buttons, and bottom nav fade/scale out on
 *   `0..PILL_HANDOFF`, and the minimized pill fades in cleanly on `PILL_HANDOFF..1` with zero overlap.
 * - [BarCollapseStyle.HIDE]: the bars stay solid and slide off-screen by [Visuals.slideFraction]
 *   of their own measured height (up when at the top, down when at the bottom).
 */
object BarCollapseMath {

    /** Below this slide fraction a HIDE bar is still on screen; at or above it is fully gone. */
    const val HIDDEN_AT = 0.999f

    /** Disjoint handoff point in PILL mode between full bar exit and minimized pill entrance. */
    const val PILL_HANDOFF = 0.42f

    data class Visuals(
        val fullBarAlpha: Float,
        val fullBarScaleX: Float,
        val fullBarScaleY: Float,
        val fullBarTranslationDp: Float,
        val sideButtonAlpha: Float,
        val sideButtonScale: Float,
        val leftButtonOffsetDp: Float,
        val rightButtonOffsetDp: Float,
        val pillAlpha: Float,
        val pillScale: Float,
        val pillTranslationDp: Float,
        val outerBgAlpha: Float,
        /** 0..1 share of its own height the bar is slid away. Only non-zero in HIDE mode. */
        val slideFraction: Float,
        val showPill: Boolean,
        /** False once the bar is invisible, so it stops swallowing touches meant for the page. */
        val barVisible: Boolean,
        val navAlpha: Float = fullBarAlpha,
        val navScale: Float = 0.92f + 0.08f * fullBarAlpha,
        val navTranslationDp: Float = 16f * (1f - fullBarAlpha),
        val navVisible: Boolean = barVisible
    )

    @JvmStatic
    fun compute(style: BarCollapseStyle, collapsedFraction: Float, isBottom: Boolean): Visuals {
        val f = collapsedFraction.coerceIn(0f, 1f)
        return when (style) {
            BarCollapseStyle.HIDE -> {
                val visible = f < HIDDEN_AT
                Visuals(
                    fullBarAlpha = 1f, fullBarScaleX = 1f, fullBarScaleY = 1f, fullBarTranslationDp = 0f,
                    sideButtonAlpha = 1f, sideButtonScale = 1f, leftButtonOffsetDp = 0f, rightButtonOffsetDp = 0f,
                    pillAlpha = 0f, pillScale = 1f, pillTranslationDp = 0f, outerBgAlpha = 1f,
                    slideFraction = f, showPill = false, barVisible = visible,
                    navAlpha = 1f, navScale = 1f, navTranslationDp = 0f, navVisible = visible
                )
            }
            BarCollapseStyle.PILL -> {
                val fullAlpha = ((PILL_HANDOFF - f) / PILL_HANDOFF).coerceIn(0f, 1f)
                val pillAlpha = ((f - PILL_HANDOFF) / (1f - PILL_HANDOFF)).coerceIn(0f, 1f)
                val dir = if (isBottom) 1f else -1f
                val barVis = fullAlpha > 0.005f
                Visuals(
                    fullBarAlpha = fullAlpha,
                    fullBarScaleX = 0.94f + 0.06f * fullAlpha,
                    fullBarScaleY = 0.90f + 0.10f * fullAlpha,
                    fullBarTranslationDp = 10f * dir * (1f - fullAlpha),
                    sideButtonAlpha = fullAlpha,
                    sideButtonScale = 0.80f + 0.20f * fullAlpha,
                    leftButtonOffsetDp = -12f * (1f - fullAlpha),
                    rightButtonOffsetDp = 12f * (1f - fullAlpha),
                    pillAlpha = pillAlpha,
                    pillScale = 0.88f + 0.12f * pillAlpha,
                    pillTranslationDp = -10f * dir * (1f - pillAlpha),
                    outerBgAlpha = fullAlpha,
                    slideFraction = 0f,
                    showPill = pillAlpha > 0.005f,
                    barVisible = barVis,
                    navAlpha = fullAlpha,
                    navScale = 0.92f + 0.08f * fullAlpha,
                    navTranslationDp = 16f * (1f - fullAlpha),
                    navVisible = barVis
                )
            }
        }
    }
}
