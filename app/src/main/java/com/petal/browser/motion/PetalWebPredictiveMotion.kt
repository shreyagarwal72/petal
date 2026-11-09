package com.petal.browser.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat

/**
 * Manages real-time predictive back gesture card transformations for Petal Browser's
 * web and homepage surfaces (contentFrame).
 *
 * Implements Material 3 Expressive and Android 14/15 Predictive Back motion specs,
 * adapted from Candy Browser's PredictiveBackMotion physics (0.90 scale, 5% translation, 28dp rounded corners).
 */
class PetalWebPredictiveMotion(private val hostView: View) {

    private var isGestureActive = false
    private var currentProgress = 0f
    private var swipeEdge = BackEventCompat.EDGE_LEFT
    private var activeAnimator: ValueAnimator? = null

    // Cache density for fast per-frame corner calculations
    private val density = hostView.resources.displayMetrics.density
    private val maxCornerRadiusPx = 28f * density

    fun beginGesture(edge: Int) {
        cancelActiveAnimation()
        isGestureActive = true
        swipeEdge = edge
        currentProgress = 0f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hostView.clipToOutline = true
        }
    }

    fun applyProgress(progress: Float) {
        if (!isGestureActive) return
        currentProgress = progress.coerceIn(0f, 1f)
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

        // 1.0 down to 0.90 scale
        val scale = 1f - (0.10f * currentProgress)
        // 5% edge translation in swipe direction
        val translationX = hostView.width * 0.05f * currentProgress * direction

        hostView.scaleX = scale
        hostView.scaleY = scale
        hostView.translationX = translationX

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val currentRadius = maxCornerRadiusPx * currentProgress
            hostView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, currentRadius)
                }
            }
            hostView.invalidateOutline()
        }
    }

    fun settleCancel(onComplete: Runnable? = null) {
        cancelActiveAnimation()
        val startProgress = currentProgress
        if (startProgress <= 0.01f) {
            resetVisuals()
            onComplete?.run()
            return
        }

        val anim = ValueAnimator.ofFloat(startProgress, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                applyProgress(va.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    resetVisuals()
                    onComplete?.run()
                }
            })
        }
        activeAnimator = anim
        anim.start()
    }

    fun settleCommit(onComplete: Runnable) {
        cancelActiveAnimation()
        val startProgress = currentProgress
        val anim = ValueAnimator.ofFloat(startProgress, 1f).apply {
            duration = 160
            interpolator = AccelerateInterpolator()
            addUpdateListener { va ->
                applyProgress(va.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    resetVisuals()
                    onComplete.run()
                }
            })
        }
        activeAnimator = anim
        anim.start()
    }

    fun resetVisuals() {
        cancelActiveAnimation()
        isGestureActive = false
        currentProgress = 0f

        hostView.scaleX = 1f
        hostView.scaleY = 1f
        hostView.translationX = 0f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hostView.clipToOutline = false
            hostView.outlineProvider = ViewOutlineProvider.BACKGROUND
            hostView.invalidateOutline()
        }
    }

    private fun cancelActiveAnimation() {
        activeAnimator?.cancel()
        activeAnimator = null
    }
}
