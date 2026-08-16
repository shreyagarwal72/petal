// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.animation.predictiveback

import kotlin.math.roundToInt

internal fun snapScaleToPixelExtent(scale: Float, extent: Float): Float =
    if (extent > 0f) (scale * extent).roundToInt() / extent else scale

internal fun snapTranslationToPixelEdge(
    translation: Float,
    scale: Float,
    extent: Float,
    pivotFraction: Float = 0.5f,
): Float {
    if (extent <= 0f) return translation
    val scaledEdgeOffset = extent * pivotFraction * (1f - scale)
    return (translation + scaledEdgeOffset).roundToInt() - scaledEdgeOffset
}
