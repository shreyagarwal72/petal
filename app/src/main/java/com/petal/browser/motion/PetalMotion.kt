package com.petal.browser.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import com.petal.browser.ui.theme.layout.PetalLayoutState
import kotlin.math.roundToInt

/**
 * Central motion primitives for the Petal View system.
 *
 * Keep browser/web content lifecycle independent from these animations: this class only
 * changes visual View properties and never creates, opens, closes, or reloads sessions.
 */
object PetalMotion {
    const val MICRO = 140L
    const val COMPONENT = 220L
    const val TRANSITION = 350L
    const val EXPRESSIVE = 450L

    // Material 3 emphasized easing: quick start, gentle settle.
    @JvmField
    val EMPHASIZED: TimeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    @JvmField
    val EMPHASIZED_ACCELERATE: TimeInterpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    /** Scales duration based on user's Motion Duration Scale setting. */
    @JvmStatic
    fun scaleDuration(duration: Long): Long {
        return try {
            val s = PetalLayoutState.settings.motionDurationScale
            (duration * Math.max(0.25f, Math.min(3.0f, s))).toLong()
        } catch (_: Throwable) {
            duration
        }
    }

    /** Animate a newly presented surface without touching its lifecycle. */
    @JvmStatic
    @JvmOverloads
    fun enter(view: View?, duration: Long = TRANSITION) {
        if (view == null) return
        val effDuration = scaleDuration(duration)
        if (!animationsEnabled(view)) {
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.translationX = view.resources.displayMetrics.widthPixels * 0.08f
        view.scaleX = 0.985f
        view.scaleY = 0.985f
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(effDuration)
            .setInterpolator(EMPHASIZED)
            .withLayer()
            .start()
    }

    /** Receives container-transform progress: 0 = collapsed into the bar, 1 = full screen. */
    fun interface ProgressListener {
        fun onProgress(t: Float)
    }

    /**
     * Container transform (Gmail-style search open): the page grows out of [origin]
     * (a rect in screen coordinates, e.g. the tapped search/address bar) to full size, corners
     * going from the bar's radius to square.
     */
    @JvmStatic
    fun containerEnter(
        view: View?,
        origin: Rect?,
        radiusPx: Float,
        duration: Long,
        progress: ProgressListener?,
        onEnd: Runnable?
    ) {
        if (view == null) return
        if (origin == null || origin.isEmpty || !animationsEnabled(view) ||
            view.width == 0 || view.height == 0
        ) {
            enter(view, duration)
            progress?.onProgress(1f)
            onEnd?.run()
            return
        }
        runContainer(view, origin, radiusPx, duration, true, progress, onEnd)
    }

    /** Reverse of [containerEnter]: the page collapses back into [origin]. */
    @JvmStatic
    fun containerExit(
        view: View?,
        origin: Rect?,
        radiusPx: Float,
        duration: Long,
        progress: ProgressListener?,
        onEnd: Runnable?
    ) {
        if (view == null || origin == null || origin.isEmpty || !animationsEnabled(view) ||
            view.width == 0 || view.height == 0
        ) {
            onEnd?.run()
            return
        }
        runContainer(view, origin, radiusPx, duration, false, progress, onEnd)
    }

    private fun runContainer(
        view: View,
        originOnScreen: Rect,
        radiusPx: Float,
        duration: Long,
        opening: Boolean,
        progress: ProgressListener?,
        onEnd: Runnable?
    ) {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val oL = originOnScreen.left - loc[0]
        val oT = originOnScreen.top - loc[1]
        val oR = originOnScreen.right - loc[0]
        val oB = originOnScreen.bottom - loc[1]
        val startRadius = if (radiusPx >= 0f) radiusPx else originOnScreen.height() / 2f
        val t = floatArrayOf(if (opening) 0f else 1f)

        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                val p = t[0]
                val w = v.width
                val h = v.height
                val deviceRadius = 28f * v.resources.displayMetrics.density
                val currentRadius = startRadius + (deviceRadius - startRadius) * p
                outline.setRoundRect(
                    (oL + (0 - oL) * p).roundToInt(),
                    (oT + (0 - oT) * p).roundToInt(),
                    (oR + (w - oR) * p).roundToInt(),
                    (oB + (h - oB) * p).roundToInt(),
                    currentRadius
                )
            }
        }
        view.alpha = 1f
        progress?.onProgress(t[0])

        val transName = if (opening) "container_enter" else "container_exit"
        MotionGate.startTransition(transName)
        val a = ValueAnimator.ofFloat(if (opening) 0f else 1f, if (opening) 1f else 0f)
        a.duration = scaleDuration(duration)
        a.interpolator = EMPHASIZED
        a.addUpdateListener { anim ->
            t[0] = anim.animatedValue as Float
            view.invalidateOutline()
            progress?.onProgress(t[0])
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                try {
                    view.clipToOutline = false
                    view.outlineProvider = ViewOutlineProvider.BACKGROUND
                    view.alpha = 1f
                    onEnd?.run()
                } finally {
                    MotionGate.endTransition(transName)
                }
            }
        })
        a.start()
    }

    /**
     * The tapped bar's own pixels (cropped from a screenshot), drawn over the growing page and
     * faded out as the page takes over, so the bar visibly turns into the search page (and back).
     */
    class BarGhost(
        private val bitmap: Bitmap?,
        private val radius: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val path = Path()
        private val rectF = RectF()
        private var ghostAlpha = 255

        /** Ghost is fully visible while collapsed and gone by 30% of the way open. */
        fun setProgress(t: Float) {
            val a = (255f * Math.max(0f, Math.min(1f, 1f - t / 0.3f))).roundToInt()
            if (a != ghostAlpha) {
                ghostAlpha = a
                invalidateSelf()
            }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (bitmap == null || bitmap.isRecycled || b.isEmpty || ghostAlpha <= 0) return
            paint.alpha = ghostAlpha
            rectF.set(b)
            path.reset()
            path.addRoundRect(rectF, radius, radius, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bitmap, null, b, paint)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(a: Int) {
            ghostAlpha = a
            invalidateSelf()
        }

        override fun setColorFilter(cf: ColorFilter?) {
            paint.colorFilter = cf
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** Animate a tab/card into a container with a smaller, faster motion. */
    @JvmStatic
    fun tabEnter(view: View?) {
        if (view == null) return
        if (!animationsEnabled(view)) {
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.translationY = 18f
        view.scaleX = 0.96f
        view.scaleY = 0.96f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(scaleDuration(COMPONENT))
            .setInterpolator(EMPHASIZED)
            .withLayer()
            .start()
    }

    /** Small press feedback for any interactive View. */
    @JvmStatic
    fun press(view: View?, pressed: Boolean) {
        if (view == null || !animationsEnabled(view)) return
        view.animate()
            .scaleX(if (pressed) 0.95f else 1f)
            .scaleY(if (pressed) 0.95f else 1f)
            .setDuration(scaleDuration(MICRO))
            .setInterpolator(EMPHASIZED)
            .start()
    }

    /** Smooth bottom sheet slide-up entrance with subtle scale. */
    @JvmStatic
    fun sheetEnter(view: View?) {
        if (view == null) return
        if (!animationsEnabled(view)) {
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.translationY = 120f
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(scaleDuration(TRANSITION))
            .setInterpolator(EMPHASIZED)
            .withLayer()
            .start()
    }

    /** Smooth bottom sheet slide-down exit. */
    @JvmStatic
    fun sheetExit(view: View?, onComplete: Runnable?) {
        if (view == null) return
        if (!animationsEnabled(view)) {
            onComplete?.run()
            return
        }
        view.animate()
            .alpha(0f)
            .translationY(160f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(scaleDuration(COMPONENT))
            .setInterpolator(EMPHASIZED_ACCELERATE)
            .withLayer()
            .withEndAction { onComplete?.run() }
            .start()
    }

    /** Premium modal dialog pop-in entrance. */
    @JvmStatic
    fun dialogEnter(view: View?) {
        if (view == null) return
        if (!animationsEnabled(view)) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.scaleX = 0.90f
        view.scaleY = 0.90f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(scaleDuration(COMPONENT))
            .setInterpolator(EMPHASIZED)
            .withLayer()
            .start()
    }

    /** Smooth cross-fade for seamless surface switching without blank frames. */
    @JvmStatic
    fun crossfade(outgoing: View?, incoming: View?, duration: Long) {
        val effDuration = scaleDuration(duration)
        if (incoming != null) {
            incoming.visibility = View.VISIBLE
            incoming.alpha = 0f
            incoming.animate()
                .alpha(1f)
                .setDuration(effDuration)
                .setInterpolator(EMPHASIZED)
                .start()
        }
        if (outgoing != null) {
            outgoing.animate()
                .alpha(0f)
                .setDuration(effDuration)
                .setInterpolator(EMPHASIZED_ACCELERATE)
                .withEndAction { outgoing.visibility = View.GONE }
                .start()
        }
    }

    private fun animationsEnabled(view: View?): Boolean {
        return try {
            if (view != null && MotionGate.isReduceMotion(view.context)) return false
            if (!ValueAnimator.areAnimatorsEnabled()) return false
            val scale = Settings.Global.getFloat(
                view?.context?.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale > 0f
        } catch (_: Exception) {
            true
        }
    }
}
