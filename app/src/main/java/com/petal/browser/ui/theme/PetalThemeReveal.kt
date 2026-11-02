package com.petal.browser.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalView
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import kotlin.math.hypot
import kotlin.math.max

/**
 * Circular colour reveal for theme changes.
 *
 * Call [ThemeReveal.reveal] around anything that changes the look of the app (theme mode, palette,
 * harmony style, dynamic colour, custom colours). It takes a picture of the screen as it is now,
 * lays it over the window, applies the change underneath, then opens a circle from the point you
 * tapped so the new colours spread across the screen.
 *
 * Respects the system "Remove animations" / animator scale setting (a scale of 0 just applies the
 * change instantly).
 */
class ThemeReveal internal constructor(private val view: View) {

    /** Window coordinates of the latest finger-down, kept by [trackRevealOrigin]. */
    internal var origin: Offset = Offset.Unspecified
    internal var rootOffset: Offset = Offset.Zero

    fun reveal(change: () -> Unit) = PetalThemeRevealer.reveal(view, origin, change)
}

@Composable
fun rememberThemeReveal(): ThemeReveal {
    val view = LocalView.current
    return remember(view) { ThemeReveal(view) }
}

/**
 * Put this on the root of a screen. It only watches where the finger lands (without consuming
 * anything), so [ThemeReveal.reveal] can start the circle at the tapped swatch or switch.
 */
fun Modifier.trackRevealOrigin(reveal: ThemeReveal): Modifier = this
    .onGloballyPositioned { reveal.rootOffset = it.positionInWindow() }
    .pointerInput(reveal) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            reveal.origin = reveal.rootOffset + down.position
        }
    }

object PetalThemeRevealer {

    private const val DURATION_MS = 720L
    private var active: RevealOverlayView? = null

    fun reveal(view: View, origin: Offset, change: () -> Unit) {
        val activity = view.context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            change()
            return
        }
        val decor = activity.window.decorView as? ViewGroup
        if (decor == null || decor.width == 0 || decor.height == 0) {
            change()
            return
        }

        active?.finish()

        val cx = if (origin.isSpecified()) origin.x else decor.width / 2f
        val cy = if (origin.isSpecified()) origin.y else decor.height / 2f

        capture(activity, decor) { snapshot ->
            if (snapshot == null || activity.isFinishing || activity.isDestroyed) {
                change()
                return@capture
            }
            val overlay = RevealOverlayView(activity, snapshot, cx, cy)
            decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            active = overlay

            // The overlay shows the old frame, so the real UI can change underneath without flicker.
            change()

            // Let the new theme compose and lay out for two frames, then open the circle.
            overlay.postOnAnimation {
                overlay.postOnAnimation { overlay.start(DURATION_MS) { if (active === overlay) active = null } }
            }
        }
    }

    private fun Offset.isSpecified() = x.isFinite() && y.isFinite()

    private fun capture(activity: Activity, decor: ViewGroup, done: (Bitmap?) -> Unit) {
        val w = decor.width
        val h = decor.height
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            done(null)
            return
        }
        try {
            // PixelCopy also captures video/GL surfaces, which a plain view draw would miss.
            PixelCopy.request(activity.window, bmp, { result ->
                if (result == PixelCopy.SUCCESS) done(bmp) else done(fallbackCapture(decor, bmp))
            }, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            done(fallbackCapture(decor, bmp))
        }
    }

    private fun fallbackCapture(decor: View, target: Bitmap): Bitmap? = try {
        val c = Canvas(target)
        decor.draw(c)
        target
    } catch (t: Throwable) {
        null
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}

/** Draws the old frame everywhere except inside the growing circle. */
private class RevealOverlayView(
    context: Context,
    private var snapshot: Bitmap?,
    private val cx: Float,
    private val cy: Float
) : View(context) {

    private val hole = Path()
    private var radius = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start(durationMs: Long, onEnd: () -> Unit) {
        val maxR = max(
            max(hypot(cx, cy), hypot(width - cx, cy)),
            max(hypot(cx, height - cy), hypot(width - cx, height - cy))
        )
        animator = ValueAnimator.ofFloat(0f, maxR + 2f).apply {
            duration = durationMs
            // Emphasized decelerate: fast out of the finger, long soft landing at the edges.
            interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            addUpdateListener { radius = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    onEnd()
                }
            })
            start()
        }
    }

    fun finish() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        (parent as? ViewGroup)?.removeView(this)
        snapshot?.recycle()
        snapshot = null
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = snapshot ?: return
        hole.rewind()
        hole.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipOutPath(hole)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        canvas.restore()
    }
}
