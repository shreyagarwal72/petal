package com.petal.browser.ui.components;

/**
 * ElasticOverscrollHelper provides spring resistance physics calculations for overscroll rubber-banding.
 */
public class ElasticOverscrollHelper {

    /**
     * Calculates rubber-band offset with logarithmic resistance decay.
     */
    public static float calculateRubberBandOffset(float delta, float maxOffset) {
        if (delta == 0) return 0f;
        float sign = delta > 0 ? 1f : -1f;
        float absDelta = Math.abs(delta);
        float rubberBand = (1f - (1f / ((absDelta * 0.55f / maxOffset) + 1f))) * maxOffset;
        return sign * rubberBand;
    }
}
