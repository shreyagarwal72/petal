package com.petal.browser.ui.components

object ElasticOverscrollHelper {

    /**
     * Calculates rubber-band offset with logarithmic resistance decay.
     */
    @JvmStatic
    fun calculateRubberBandOffset(delta: Float, maxOffset: Float): Float {
        if (delta == 0f) return 0f
        val sign = if (delta > 0f) 1f else -1f
        val absDelta = Math.abs(delta)
        val rubberBand = (1f - 1f / (absDelta * 0.55f / maxOffset + 1f)) * maxOffset
        return sign * rubberBand
    }
}
