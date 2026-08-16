// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.animation.predictiveback

/**
 * A single frame of a predictive-back content transform: how far the page should be scaled,
 * shifted, faded and corner-rounded at a given gesture progress. Values are resolution
 * independent (dp / fraction), so both the Compose-based Settings screen transform and the
 * View-based [com.petal.browser.compose.composable.PredictiveContentTransformer] used for the
 * main browser content can apply them consistently.
 */
data class PredictiveBackFrame(
    val scale: Float = 1f,
    val translationXDp: Float = 0f,
    val alpha: Float = 1f,
    val cornerRadiusDp: Float = 0f,
)

/**
 * Computes the per-style predictive-back content transform for the animation options ported
 * from InstallerX-Revived (None / AOSP / MIUIX / Scale / Classic).
 *
 * InstallerX drives these as full two-screen `NavTransition`s over its Miuix nav-transition
 * stack. Petal Browser has a single WebView content surface rather than a Compose nav-graph
 * stack, so each case below re-expresses that option's signature scale/drift/fade/corner curve
 * as a transform on that single surface instead of choreographing two stacked screens.
 */
object PredictiveBackStyle {

    fun frameFor(
        animation: PredictiveBackAnimation,
        exitDirection: PredictiveBackExitDirection,
        progress: Float,
        isLeftEdge: Boolean,
    ): PredictiveBackFrame {
        val clamped = progress.coerceIn(0f, 1f)
        if (animation == PredictiveBackAnimation.NONE || clamped <= 0f) {
            return PredictiveBackFrame()
        }
        val eased = BackGestureEasing.transform(clamped)
        return when (animation) {
            PredictiveBackAnimation.NONE -> PredictiveBackFrame()
            PredictiveBackAnimation.AOSP -> aospFrame(eased, isLeftEdge)
            PredictiveBackAnimation.MIUIX -> miuixFrame(eased, isLeftEdge)
            PredictiveBackAnimation.SCALE -> scaleFrame(eased, isLeftEdge, exitDirection)
            PredictiveBackAnimation.CLASSIC -> classicFrame(eased, isLeftEdge)
        }
    }

    private fun driftSign(isLeftEdge: Boolean): Float = if (isLeftEdge) 1f else -1f

    private fun exitSign(exitDirection: PredictiveBackExitDirection, isLeftEdge: Boolean): Float =
        when (exitDirection) {
            PredictiveBackExitDirection.FOLLOW_GESTURE -> driftSign(isLeftEdge)
            PredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
            PredictiveBackExitDirection.ALWAYS_LEFT -> -1f
        }

    /** Matches Android's own system predictive-back "shrink and round" look. */
    private fun aospFrame(eased: Float, isLeftEdge: Boolean) = PredictiveBackFrame(
        scale = 1f - eased * 0.10f,
        translationXDp = driftSign(isLeftEdge) * eased * 48f,
        alpha = 1f - eased * 0.15f,
        cornerRadiusDp = eased * 28f,
    )

    /** MIUI's predictive back barely scales or fades - mostly a nudge with soft corners. */
    private fun miuixFrame(eased: Float, isLeftEdge: Boolean) = PredictiveBackFrame(
        scale = 1f - eased * 0.04f,
        translationXDp = driftSign(isLeftEdge) * eased * 24f,
        alpha = 1f,
        cornerRadiusDp = eased * 20f,
    )

    /** A more dramatic shrink-and-slide; direction is controlled by the Exit Direction setting. */
    private fun scaleFrame(
        eased: Float,
        isLeftEdge: Boolean,
        exitDirection: PredictiveBackExitDirection,
    ) = PredictiveBackFrame(
        scale = 1f - eased * 0.15f,
        translationXDp = exitSign(exitDirection, isLeftEdge) * eased * 96f,
        alpha = 1f - eased * 0.25f,
        cornerRadiusDp = eased * 32f,
    )

    /** The old "classic" pop: a longer drift, sharp (unrounded) corners, a steeper late fade. */
    private fun classicFrame(eased: Float, isLeftEdge: Boolean) = PredictiveBackFrame(
        scale = 1f - eased * 0.08f,
        translationXDp = driftSign(isLeftEdge) * eased * 96f,
        alpha = (1f - eased * eased * 0.6f).coerceAtLeast(0.4f),
        cornerRadiusDp = 0f,
    )
}
