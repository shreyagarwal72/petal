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

    /**
     * How much of the freed space the web page should have claimed at [collapsedFraction].
     * PILL bars are fully gone at [PILL_HANDOFF], so the page must finish moving by then; a linear
     * 0..1 page shift leaves an empty band under the already-invisible bar. HIDE bars slide
     * linearly, so the page follows linearly too.
     */
    @JvmStatic
    fun pageFraction(style: BarCollapseStyle, collapsedFraction: Float): Float {
        val f = collapsedFraction.coerceIn(0f, 1f)
        return if (style == BarCollapseStyle.PILL) (f / PILL_HANDOFF).coerceIn(0f, 1f) else f
    }

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
                val barVis = fullAlpha >= 0.05f
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

    @JvmStatic fun isBarVisible(style: BarCollapseStyle, collapsedFraction: Float): Boolean {
        val f = collapsedFraction.coerceIn(0f, 1f)
        return when (style) {
            BarCollapseStyle.HIDE -> f < HIDDEN_AT
            BarCollapseStyle.PILL -> ((0.45f - f) / 0.45f).coerceIn(0f, 1f) > 0.01f
        }
    }

    @JvmStatic fun isPillShown(style: BarCollapseStyle, collapsedFraction: Float): Boolean {
        if (style != BarCollapseStyle.PILL) return false
        val f = collapsedFraction.coerceIn(0f, 1f)
        return ((f - 0.40f) / 0.60f).coerceIn(0f, 1f) > 0.005f
    }

    @JvmStatic fun slideFraction(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) collapsedFraction.coerceIn(0f, 1f) else 0f

    @JvmStatic fun fullBarAlpha(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 1f else ((0.45f - collapsedFraction.coerceIn(0f, 1f)) / 0.45f).coerceIn(0f, 1f)

    @JvmStatic fun fullBarScaleX(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 1f else 0.94f + 0.06f * (1f - collapsedFraction.coerceIn(0f, 1f))

    @JvmStatic fun fullBarScaleY(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 1f else 0.90f + 0.10f * (1f - collapsedFraction.coerceIn(0f, 1f))

    @JvmStatic fun fullBarTranslationDp(style: BarCollapseStyle, collapsedFraction: Float, isBottom: Boolean): Float =
        if (style == BarCollapseStyle.HIDE) 0f else 10f * (if (isBottom) 1f else -1f) * collapsedFraction.coerceIn(0f, 1f)

    @JvmStatic fun sideButtonAlpha(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 1f else (1f - collapsedFraction.coerceIn(0f, 1f) * 2.2f).coerceIn(0f, 1f)

    @JvmStatic fun sideButtonScale(style: BarCollapseStyle, collapsedFraction: Float): Float {
        val a = sideButtonAlpha(style, collapsedFraction)
        return if (style == BarCollapseStyle.HIDE) 1f else 0.80f + 0.20f * a
    }

    @JvmStatic fun leftButtonOffsetDp(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 0f else -12f * collapsedFraction.coerceIn(0f, 1f)

    @JvmStatic fun rightButtonOffsetDp(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 0f else 12f * collapsedFraction.coerceIn(0f, 1f)

    @JvmStatic fun pillAlpha(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 0f else ((collapsedFraction.coerceIn(0f, 1f) - 0.40f) / 0.60f).coerceIn(0f, 1f)

    @JvmStatic fun pillScale(style: BarCollapseStyle, collapsedFraction: Float): Float =
        if (style == BarCollapseStyle.HIDE) 1f else 0.88f + 0.12f * pillAlpha(style, collapsedFraction)

    @JvmStatic fun pillTranslationDp(style: BarCollapseStyle, collapsedFraction: Float, isBottom: Boolean): Float =
        if (style == BarCollapseStyle.HIDE) 0f else -10f * (if (isBottom) 1f else -1f) * (1f - pillAlpha(style, collapsedFraction))
}
