/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Predictive-back style engine. The AOSP / MIUIX / Scale / Classic geometry is ported from
 * InstallerX Revived's predictiveback engine (GPL-3.0-only, (C) InstallerX Revived
 * contributors). "Petal" is the animation Petal shipped with before styles existed.
 */

package com.petal.browser.predictive

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Selectable predictive back animation. [index] is the slider position. */
enum class PetalBackStyle(
    val key: String,
    val index: Int,
    /** Commit settle length. Ignored by [Petal], which keeps its original spring. */
    val commitDurationMs: Int,
    val commitEasing: Easing,
) {
    Petal("petal", 0, 0, FastOutSlowInEasing),
    Aosp("aosp", 1, 450, FastOutExtraSlowIn),
    Miuix("miuix", 2, 300, CubicBezierEasing(0.2f, 0f, 0f, 1f)),
    Scale("scale", 3, 200, FastOutSlowInEasing),
    Classic("classic", 4, 200, CubicBezierEasing(0.2f, 0f, 0f, 1f)),
    ;

    val usesSpringCommit: Boolean get() = this == Petal

    companion object {
        fun fromKey(key: String?): PetalBackStyle = entries.firstOrNull { it.key == key } ?: Petal
        fun fromIndex(index: Int): PetalBackStyle = entries.firstOrNull { it.index == index } ?: Petal
    }
}

/** Which way the page leaves in the [PetalBackStyle.Scale] style. */
enum class PetalBackExitDirection(val key: String) {
    FollowGesture("follow_gesture"),
    AlwaysRight("always_right"),
    AlwaysLeft("always_left"),
    ;

    companion object {
        fun fromKey(key: String?): PetalBackExitDirection =
            entries.firstOrNull { it.key == key } ?: AlwaysRight
    }
}

/** Everything a style needs to place both layers for one frame. */
data class PetalBackInput(
    /** Gesture progress while the finger is down; the settled progress during commit/cancel. */
    val progress: Float,
    val swipeEdge: Int,
    val touchY: Float,
    val initialTouchY: Float,
    val widthPx: Float,
    val heightPx: Float,
    val density: Float,
    val blurEnabled: Boolean,
    val committing: Boolean = false,
    /** Progress at the moment the finger lifted (commit only). */
    val releaseProgress: Float = 0f,
    /** Progress units per second at release (commit only). */
    val releaseVelocity: Float = 0f,
    /** Linear 0..1 position inside the commit settle (commit only). */
    val settleTime: Float = 0f,
    val exitDirection: PetalBackExitDirection = PetalBackExitDirection.AlwaysRight,
)

data class PetalBackLayer(
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val alpha: Float = 1f,
    val cornerDp: Float = 0f,
    val shadowDp: Float = 0f,
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
)

data class PetalBackFrame(
    /** The screen being dismissed. */
    val top: PetalBackLayer,
    /** The screen being revealed. */
    val behind: PetalBackLayer,
    /** Alpha of the black dim drawn over [behind]. */
    val behindDim: Float,
    /** Depth-blur radius on [behind], 0 when the blur effect is off. */
    val behindBlurDp: Float,
)

internal val FastOutExtraSlowIn: Easing = run {
    val knotX = 0.166666f
    val knotY = 0.4f
    val first = CubicBezierEasing(0.05f / knotX, 0f, 0.133333f / knotX, 0.06f / knotY)
    val second = CubicBezierEasing(
        (0.208333f - knotX) / (1f - knotX),
        (0.82f - knotY) / (1f - knotY),
        (0.25f - knotX) / (1f - knotX),
        (1f - knotY) / (1f - knotY),
    )
    Easing { fraction ->
        if (fraction < knotX) knotY * first.transform(fraction / knotX)
        else knotY + (1f - knotY) * second.transform((fraction - knotX) / (1f - knotX))
    }
}

private val BackGestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private const val DEPTH_BLUR_DP = 24f
private const val DIM_WITH_BLUR = 0.40f
private const val DIM_NO_BLUR = 0.75f

object PetalBackMotion {

    fun frame(style: PetalBackStyle, i: PetalBackInput): PetalBackFrame = when (style) {
        PetalBackStyle.Petal -> petal(i)
        PetalBackStyle.Aosp -> aosp(i)
        PetalBackStyle.Miuix -> miuix(i)
        PetalBackStyle.Scale -> scale(i)
        PetalBackStyle.Classic -> classic(i)
    }

    /** Java-friendly: behind-layer values for the live underlay (scale, tx, ty, dim, blurDp). */
    @JvmStatic
    fun behindValues(frame: PetalBackFrame): FloatArray = floatArrayOf(
        frame.behind.scale,
        frame.behind.translationX,
        frame.behind.translationY,
        frame.behindDim,
        frame.behindBlurDp,
    )

    private fun dimMax(i: PetalBackInput) = if (i.blurEnabled) DIM_WITH_BLUR else DIM_NO_BLUR

    private fun blurFor(i: PetalBackInput, fraction: Float) =
        if (i.blurEnabled) DEPTH_BLUR_DP * fraction.coerceIn(0f, 1f) else 0f

    /** +1 when the page leaves to the right (left-edge swipe), -1 for a right-edge swipe. */
    private fun edgeSign(i: PetalBackInput) = if (i.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

    /** How far the commit settle has travelled, 0 while the finger is down. */
    private fun post(i: PetalBackInput): Float =
        if (!i.committing) 0f
        else ((i.progress - i.releaseProgress) / (1f - i.releaseProgress).coerceAtLeast(0.01f)).coerceIn(0f, 1f)

    /** Scrim that stays full while dragging and fades out as the commit settles. */
    private fun heldScrim(i: PetalBackInput) = if (i.committing) (1f - i.settleTime).coerceIn(0f, 1f) else 1f

    // ---------------------------------------------------------------------------------------
    // Petal: the animation Petal shipped with. Unchanged maths, now just one of the styles.
    // ---------------------------------------------------------------------------------------
    private fun petal(i: PetalBackInput): PetalBackFrame {
        val p = i.progress.coerceIn(0f, 1f)
        val eased = PetalM3EmphasizedEasing.transform(p)
        val slide = PetalPopExitSlideEasing.transform(p)
        val topScale = 1f - PETAL_POP_EXIT_MAX_SCALE_DELTA * eased
        val bgDir = if (i.swipeEdge == BackEventCompat.EDGE_RIGHT) 1f / 3f else -1f / 3f
        return PetalBackFrame(
            top = PetalBackLayer(
                scale = topScale,
                translationX = i.widthPx * edgeSign(i) * slide,
                cornerDp = 32f * eased,
                shadowDp = 16f * eased,
            ),
            behind = PetalBackLayer(
                scale = 0.94f + 0.06f * eased,
                translationX = i.widthPx * bgDir * (1f - eased),
                cornerDp = 32f * (1f - eased),
            ),
            behindDim = dimMax(i) * (1f - eased),
            behindBlurDp = blurFor(i, 1f - eased),
        )
    }

    // ---------------------------------------------------------------------------------------
    // AOSP cross-activity (InstallerX `AospNavTransition.CrossActivityPredictive`).
    // The dismissed page shrinks to 0.9, hugs the swipe edge and follows the finger vertically;
    // on commit it springs back toward 1.0 while drifting away and fading.
    // ---------------------------------------------------------------------------------------
    private fun aosp(i: PetalBackInput): PetalBackFrame {
        val minScale = 0.9f
        val drift = 96f * i.density
        val margin = 8f * i.density
        val hugMax = (i.widthPx * (1f - minScale) / 2f - margin).coerceAtLeast(0f)
        val sign = edgeSign(i)
        val p = i.progress.coerceIn(0f, 1f)
        val post = post(i)
        val bounce = aospBounce(i)

        val topScale: Float
        val topTx: Float
        val topAlpha: Float
        if (i.committing) {
            val releaseEased = 1f - BackGestureEasing.transform(i.releaseProgress.coerceIn(0f, 1f))
            val committed = minScale + (1f - minScale) * releaseEased
            val grown = committed + (1f - committed) * post
            topScale = snapScale(grown * bounce, i.widthPx)
            topTx = sign * ((1f - releaseEased) * hugMax + post * drift)
            topAlpha = (1f - 5f * i.settleTime).coerceAtLeast(0f)
        } else {
            val eased = 1f - BackGestureEasing.transform(p)
            topScale = snapScale(minScale + (1f - minScale) * eased, i.widthPx)
            topTx = sign * (1f - eased) * hugMax
            topAlpha = 1f
        }
        val ty = aospYShift(i, topScale)
        val shrink = ((1f - topScale) / (1f - minScale)).coerceIn(0f, 1f)

        // Revealed page: sits shifted away and shrunk while dragging, settles to rest on commit.
        val travel = if (i.committing) i.releaseProgress else p
        val liveScale = minScale + (1f - minScale) * (1f - BackGestureEasing.transform(travel.coerceIn(0f, 1f)))
        val behindScale = snapScale((liveScale + (1f - liveScale) * post) * bounce, i.widthPx)
        val behindTx = -sign * (1f - post) * drift

        val scrim = heldScrim(i)
        return PetalBackFrame(
            top = PetalBackLayer(
                scale = topScale,
                translationX = topTx,
                translationY = ty,
                alpha = topAlpha,
                cornerDp = 28f * shrink,
                shadowDp = 8f * shrink,
            ),
            behind = PetalBackLayer(
                scale = behindScale,
                translationX = behindTx,
                translationY = aospYShift(i, behindScale),
                cornerDp = 28f * (1f - post),
            ),
            behindDim = dimMax(i) * scrim,
            behindBlurDp = blurFor(i, scrim),
        )
    }

    private fun aospBounce(i: PetalBackInput): Float {
        if (!i.committing) return 1f
        val floorKick = if (i.releaseProgress < 0.1f) 120f else 0f
        val kick = (abs(i.releaseVelocity) * 100f * 0.1f * 2f).coerceIn(floorKick, 1000f)
        if (kick <= 0f) return 1f
        val omega = sqrt(200f)
        val omegaD = omega * sqrt(1f - 0.75f * 0.75f)
        val t = i.settleTime * PetalBackStyle.Aosp.commitDurationMs / 1000f
        val overlay = -(kick / omegaD) * exp(-0.75f * omega * t) * sin(omegaD * t)
        return ((100f + overlay) / 100f).coerceIn(0.5f, 1f)
    }

    private fun aospYShift(i: PetalBackInput, scale: Float): Float {
        if (i.heightPx <= 0f) return 0f
        val rawDelta = i.touchY - i.initialTouchY
        val half = i.heightPx / 2f
        val ratio = min(half, abs(rawDelta)) / half
        val damped = 1f - (1f - ratio) * (1f - ratio)
        val maxShift = ((i.heightPx - i.heightPx * scale) / 2f - 8f * i.density).coerceAtLeast(0f)
        return maxShift * damped * (if (rawDelta < 0f) -1f else 1f)
    }

    // ---------------------------------------------------------------------------------------
    // MIUIX default: the page slides out with the finger while the page below parallaxes in
    // from 25% to the side. (InstallerX delegates to miuix's own NavTransitions.MiuixDefault,
    // whose source is not part of the project, so this follows the same structure that
    // InstallerX's NoPredictiveBack lower/upper entry maths uses.)
    // ---------------------------------------------------------------------------------------
    private fun miuix(i: PetalBackInput): PetalBackFrame {
        val p = i.progress.coerceIn(0f, 1f)
        val sign = edgeSign(i)
        return PetalBackFrame(
            top = PetalBackLayer(
                translationX = i.widthPx * sign * p,
                shadowDp = 12f * (p * 4f).coerceAtMost(1f),
            ),
            behind = PetalBackLayer(
                translationX = -sign * (1f - p) * i.widthPx * 0.25f,
                alpha = 0.9f + 0.1f * p,
            ),
            behindDim = dimMax(i) * (1f - p),
            behindBlurDp = blurFor(i, 1f - p),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Scale (InstallerX `scaleNavTransition`): page shrinks to 0.85 around the touch point and,
    // on commit, flies off sideways in the chosen exit direction.
    // ---------------------------------------------------------------------------------------
    private fun scale(i: PetalBackInput): PetalBackFrame {
        val minScale = 0.85f
        val p = i.progress.coerceIn(0f, 1f)
        val post = post(i)
        val pageScale = if (i.committing) {
            val releaseEased = 1f - BackGestureEasing.transform(i.releaseProgress.coerceIn(0f, 1f))
            val committed = minScale + (1f - minScale) * releaseEased
            committed + (minScale - committed) * post
        } else {
            minScale + (1f - minScale) * (1f - BackGestureEasing.transform(p))
        }
        val exitSign = when (i.exitDirection) {
            PetalBackExitDirection.FollowGesture -> if (i.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            PetalBackExitDirection.AlwaysRight -> 1f
            PetalBackExitDirection.AlwaysLeft -> -1f
        }
        val originX = if (i.swipeEdge == BackEventCompat.EDGE_LEFT) 0.8f else 0.2f
        val originY = if (i.heightPx > 0f) (i.touchY / i.heightPx).coerceIn(0.1f, 0.9f) else 0.5f
        val shrink = ((1f - pageScale) / (1f - minScale)).coerceIn(0f, 1f)
        val scrim = heldScrim(i)
        return PetalBackFrame(
            top = PetalBackLayer(
                scale = snapScale(pageScale, i.widthPx),
                translationX = if (i.committing) exitSign * post * i.widthPx else 0f,
                cornerDp = 28f * shrink,
                shadowDp = 8f * shrink,
                originX = originX,
                originY = originY,
            ),
            behind = PetalBackLayer(),
            behindDim = dimMax(i) * scrim,
            behindBlurDp = blurFor(i, scrim),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Classic / KSU (InstallerX `ClassicScalePop`): page scales 1 -> 0.9 while fading out.
    // ---------------------------------------------------------------------------------------
    private fun classic(i: PetalBackInput): PetalBackFrame {
        val p = i.progress.coerceIn(0f, 1f)
        return PetalBackFrame(
            top = PetalBackLayer(
                scale = snapScale(1f - 0.1f * p, i.widthPx),
                alpha = 1f - p,
                cornerDp = 24f * p,
            ),
            behind = PetalBackLayer(),
            behindDim = dimMax(i) * (1f - p),
            behindBlurDp = blurFor(i, 1f - p),
        )
    }

    private fun snapScale(scale: Float, extent: Float): Float =
        if (extent > 0f) (scale * extent).roundToInt() / extent else scale
}
