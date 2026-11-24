package com.petal.browser.ui.components

/**
 * Pure collapse math shared by [PetalAddressBar] and [PetalMergedBar].
 *
 * Inputs are the collapse style and [BarCollapseState.collapsedFraction]; outputs are unit-less
 * multipliers or dp values so both bars animate identically and the math is unit testable.
 *
 * - [BarCollapseStyle.PILL]: the full bar fades/scales out while the minimized pill fades in.
 * - [BarCollapseStyle.HIDE]: the bar stays solid and slides off-screen by [Visuals.slideFraction]
 *   of its own measured height (up when at the top, down when at the bottom).
 */
object BarCollapseMath {

    /** Below this slide fraction a HIDE bar is still on screen; at or above it is fully gone. */
    const val HIDDEN_AT = 0.999f

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
        val barVisible: Boolean
    )

    @JvmStatic
    fun compute(style: BarCollapseStyle, collapsedFraction: Float, isBottom: Boolean): Visuals {
        val f = collapsedFraction.coerceIn(0f, 1f)
        return when (style) {
            BarCollapseStyle.HIDE -> Visuals(
                fullBarAlpha = 1f, fullBarScaleX = 1f, fullBarScaleY = 1f, fullBarTranslationDp = 0f,
                sideButtonAlpha = 1f, sideButtonScale = 1f, leftButtonOffsetDp = 0f, rightButtonOffsetDp = 0f,
                pillAlpha = 0f, pillScale = 1f, pillTranslationDp = 0f, outerBgAlpha = 1f,
                slideFraction = f, showPill = false, barVisible = f < HIDDEN_AT
            )
            BarCollapseStyle.PILL -> {
                val fullAlpha = ((0.45f - f) / 0.45f).coerceIn(0f, 1f)
                val sideAlpha = (1f - f * 2.2f).coerceIn(0f, 1f)
                val pillAlpha = ((f - 0.40f) / 0.60f).coerceIn(0f, 1f)
                val dir = if (isBottom) 1f else -1f
                Visuals(
                    fullBarAlpha = fullAlpha,
                    fullBarScaleX = 0.94f + 0.06f * (1f - f),
                    fullBarScaleY = 0.90f + 0.10f * (1f - f),
                    fullBarTranslationDp = 10f * dir * f,
                    sideButtonAlpha = sideAlpha,
                    sideButtonScale = 0.80f + 0.20f * sideAlpha,
                    leftButtonOffsetDp = -12f * f,
                    rightButtonOffsetDp = 12f * f,
                    pillAlpha = pillAlpha,
                    pillScale = 0.88f + 0.12f * pillAlpha,
                    pillTranslationDp = -10f * dir * (1f - pillAlpha),
                    outerBgAlpha = fullAlpha,
                    slideFraction = 0f,
                    showPill = pillAlpha > 0.005f,
                    barVisible = fullAlpha > 0.01f
                )
            }
        }
    }
}
