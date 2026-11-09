package com.petal.browser.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.BackEventCompat
import com.google.android.material.color.MaterialColors
import com.petal.browser.R
import com.petal.browser.browser.rules.PetalBackTarget
import com.petal.browser.haptics.PetalHapticEngine

/**
 * Material 3 Expressive contextual back pill indicator for Petal Browser.
 * Floats near the user's touch edge during in-flight predictive back gestures,
 * displaying context-aware glyphs (Web History Back, Return to Home, Close Tab).
 */
class PetalPredictiveBackPill(context: Context) : FrameLayout(context) {

    private val iconView: ImageView
    private val density = resources.displayMetrics.density
    private val pillSizePx = (48 * density).toInt()
    private val iconSizePx = (24 * density).toInt()
    private val edgePaddingPx = 16 * density
    private val maxTravelPx = 36 * density

    private var swipeEdge = BackEventCompat.EDGE_LEFT
    private var hasTriggeredHaptic = false

    init {
        visibility = GONE
        elevation = 24 * density

        val surfaceColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainerHighest,
            Color.DKGRAY
        )
        val outlineColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            Color.LTGRAY
        )
        val primaryColor = MaterialColors.getColor(
            this,
            android.R.attr.colorPrimary,
            Color.WHITE
        )

        val pillDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(surfaceColor)
            setStroke((1 * density).toInt(), outlineColor)
        }
        background = pillDrawable

        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            setColorFilter(primaryColor)
        }
        addView(iconView)
    }

    fun show(target: PetalBackTarget, touchY: Float, edge: Int) {
        animate().cancel()
        swipeEdge = edge
        hasTriggeredHaptic = false

        val iconRes = when (target) {
            PetalBackTarget.WEB_HISTORY -> R.drawable.arrow_back_ios_new
            PetalBackTarget.RETURN_TO_HOME -> R.drawable.home
            PetalBackTarget.CLOSE_TAB_AND_PREVIOUS,
            PetalBackTarget.CLOSE_AND_RETURN_TO_OPENER -> R.drawable.close_rounded
            else -> R.drawable.arrow_back_ios_new
        }
        iconView.setImageResource(iconRes)

        alpha = 0f
        scaleX = 0.6f
        scaleY = 0.6f
        visibility = VISIBLE

        updateProgress(0f, touchY)
    }

    fun updateProgress(progress: Float, touchY: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val parent = parent as? View ?: return

        // Vertically anchor around user's finger with status/nav padding bounds
        val minTop = 60 * density
        val maxBottom = parent.height - (100 * density)
        val targetY = (touchY - (pillSizePx / 2f)).coerceIn(minTop, maxBottom)
        translationY = targetY

        // Pull pill outward from swipe edge
        val travel = maxTravelPx * clampedProgress
        if (swipeEdge == BackEventCompat.EDGE_RIGHT) {
            translationX = parent.width - pillSizePx - edgePaddingPx - travel
            iconView.rotation = 180f
        } else {
            translationX = edgePaddingPx + travel
            iconView.rotation = 0f
        }

        scaleX = 0.6f + (0.4f * clampedProgress)
        scaleY = scaleX
        alpha = (clampedProgress * 4f).coerceIn(0f, 1f)

        // Haptic bump at commit threshold
        if (clampedProgress >= 0.35f && !hasTriggeredHaptic) {
            hasTriggeredHaptic = true
            try {
                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f, 100L)
            } catch (_: Throwable) {}
        }
    }

    fun playCommitHaptic() {
        try {
            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.9f, 50L)
        } catch (_: Throwable) {}
    }

    fun dismiss(animated: Boolean = true) {
        if (!animated || visibility != VISIBLE) {
            visibility = GONE
            return
        }
        animate()
            .alpha(0f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    animate().setListener(null)
                }
            })
            .start()
    }

    companion object {
        @JvmStatic
        fun attachToRoot(rootLayout: ViewGroup): PetalPredictiveBackPill {
            val existing = (0 until rootLayout.childCount)
                .map { rootLayout.getChildAt(it) }
                .filterIsInstance<PetalPredictiveBackPill>()
                .firstOrNull()
            if (existing != null) return existing

            val pill = PetalPredictiveBackPill(rootLayout.context)
            val density = rootLayout.resources.displayMetrics.density
            val size = (48 * density).toInt()
            val lp = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            rootLayout.addView(pill, lp)
            return pill
        }
    }
}
