/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Hardware-accelerated liquid displacement ripple effect using AGSL
 * (RuntimeShader and RenderEffect) for action confirmations and unlock feedback.
 */

package com.petal.browser.ui.layout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.petal.browser.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiquidRippleEffect(view: View) {

    private val viewRef = WeakReference(view)
    private val shader: RuntimeShader
    private var animator: ValueAnimator? = null

    init {
        val shaderCode = loadShaderSource(view.context)
        shader = RuntimeShader(shaderCode)
    }

    private fun loadShaderSource(context: Context): String {
        return context.resources.openRawResource(R.raw.nfc_ripple).use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
    }

    fun animate(cx: Float, cy: Float, durationSec: Float = 3.2f) {
        val view = viewRef.get() ?: return
        val displayMetrics = view.resources.displayMetrics
        val density = displayMetrics.density

        val width = view.width.toFloat().takeIf { it > 0 } ?: displayMetrics.widthPixels.toFloat()
        val height = view.height.toFloat().takeIf { it > 0 } ?: displayMetrics.heightPixels.toFloat()

        val amplitude = 32f * density
        val frequency = 12f
        val decay = 4.5f
        val speed = 1400f * density

        shader.setFloatUniform("uResolution", width, height)
        shader.setFloatUniform("uOrigin", cx, cy)
        shader.setFloatUniform("uAmplitude", amplitude)
        shader.setFloatUniform("uFrequency", frequency)
        shader.setFloatUniform("uDecay", decay)
        shader.setFloatUniform("uSpeed", speed)

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, durationSec).apply {
            duration = (durationSec * 1000f).toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val time = anim.animatedValue as Float
                shader.setFloatUniform("uTime", time)
                try {
                    val effect = RenderEffect.createRuntimeShaderEffect(shader, "inputShader")
                    view.setRenderEffect(effect)
                    view.invalidate()
                } catch (ignored: Exception) {}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        view.setRenderEffect(null)
                        view.invalidate()
                    } catch (ignored: Exception) {}
                }
            })
            start()
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun trigger(view: View?, cx: Float? = null, cy: Float? = null, durationSec: Float = 3.2f) {
            if (view == null) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val targetView = view.rootView ?: view
                val originX = cx ?: (targetView.width.toFloat().takeIf { it > 0 } ?: targetView.resources.displayMetrics.widthPixels.toFloat()) / 2f
                val originY = cy ?: (targetView.height.toFloat().takeIf { it > 0 } ?: targetView.resources.displayMetrics.heightPixels.toFloat()) / 2f

                var effect = targetView.getTag(R.id.ripple_effect_tag) as? LiquidRippleEffect
                if (effect == null) {
                    effect = LiquidRippleEffect(targetView)
                    targetView.setTag(R.id.ripple_effect_tag, effect)
                }
                effect.animate(originX, originY, durationSec)
            }
        }
    }
}

/**
 * Jetpack Compose wrapper for Liquid Displacement Ripple effect.
 */
@Composable
fun LiquidRippleContainer(
    modifier: Modifier = Modifier,
    rippleTrigger: Int = 0,
    rippleOrigin: Offset = Offset.Zero,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val animTime = remember { Animatable(0f) }

    LaunchedEffect(rippleTrigger) {
        if (rippleTrigger > 0) {
            animTime.snapTo(0f)
            animTime.animateTo(
                targetValue = 3.2f,
                animationSpec = tween(durationMillis = 3200, easing = LinearEasing)
            )
            animTime.snapTo(0f)
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val code = context.resources.openRawResource(R.raw.nfc_ripple).use {
                    BufferedReader(InputStreamReader(it)).readText()
                }
                RuntimeShader(code)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    var containerWindowPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                containerWindowPos = coords.positionInWindow()
            }
            .graphicsLayer {
                val currentTime = animTime.value
                if (currentTime > 0f && currentTime < 3.2f && shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val densityVal = density.density
                    val amplitude = 32f * densityVal
                    val frequency = 12f
                    val decay = 4.5f
                    val speed = 1400f * densityVal

                    val localOriginX = if (rippleOrigin != Offset.Zero) {
                        rippleOrigin.x - containerWindowPos.x
                    } else {
                        size.width / 2f
                    }
                    val localOriginY = if (rippleOrigin != Offset.Zero) {
                        (rippleOrigin.y - containerWindowPos.y).coerceAtLeast(0f)
                    } else {
                        size.height / 2f
                    }

                    shader.setFloatUniform("uResolution", size.width, size.height)
                    shader.setFloatUniform("uOrigin", localOriginX, localOriginY)
                    shader.setFloatUniform("uTime", currentTime)
                    shader.setFloatUniform("uAmplitude", amplitude)
                    shader.setFloatUniform("uFrequency", frequency)
                    shader.setFloatUniform("uDecay", decay)
                    shader.setFloatUniform("uSpeed", speed)

                    renderEffect = android.graphics.RenderEffect
                        .createRuntimeShaderEffect(shader, "inputShader")
                        .asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
            }
    ) {
        content()
    }
}
