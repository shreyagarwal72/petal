/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Jetpack Compose modifier applying the Apple Duo fold frosted glass effect.
 */

package com.petal.browser.appleduo

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

private const val FALLBACK_PX_PER_MM = 6f

/**
 * Applies the Apple Duo (BETA) frosted-glass fold effect to this layout subtree.
 * Gracefully degrades on Android versions below API 33 (Tiramisu).
 */
fun Modifier.appleDuoFoldEffect(
    parameters: FoldParameters? = null
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val isEnabled by AppleDuoManager.isEnabled.collectAsState()
    if (!isEnabled) return@composed this

    val tiltDegrees by AppleDuoManager.currentTilt.collectAsState()
    val hingeSide by AppleDuoManager.currentHingeSide.collectAsState()
    val eyeDistance by AppleDuoManager.eyeDistance.collectAsState()
    val blurSpread by AppleDuoManager.blurSpread.collectAsState()
    val darkening by AppleDuoManager.darkening.collectAsState()

    if (Math.abs(tiltDegrees) < 1e-4f) return@composed this

    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics: DisplayMetrics = context.resources.displayMetrics

    val shaderSrc = remember {
        AppleDuoManager.getShaderSource(context)
    }

    val autoPxPerMm = remember(displayMetrics) {
        val xdpi = displayMetrics.xdpi
        if (xdpi.isFinite() && xdpi > 0f) xdpi / 25.4f else FALLBACK_PX_PER_MM
    }

    val finalEyeDistance = parameters?.eyeDistanceMillimeters ?: eyeDistance
    val finalBlurSpread = parameters?.blurSpread ?: blurSpread
    val finalDarkening = parameters?.darkening ?: darkening
    val finalPxPerMm = if (parameters != null && parameters.pixelsPerMillimeter > 0f) {
        parameters.pixelsPerMillimeter
    } else {
        autoPxPerMm
    }

    this.graphicsLayer {
        if (shaderSrc.isEmpty() || size.width <= 1f || size.height <= 1f) {
            renderEffect = null
            return@graphicsLayer
        }
        val shader = try {
            RuntimeShader(shaderSrc).apply {
                setFloatUniform("resolution", size.width, size.height)
                setFloatUniform("tiltDegrees", tiltDegrees)
                setFloatUniform("eyeDistancePx", finalEyeDistance * finalPxPerMm)
                setFloatUniform("hingeSide", hingeSide)
                setFloatUniform("blurSpread", finalBlurSpread)
                setFloatUniform("darkening", finalDarkening * 6f / finalPxPerMm)
            }
        } catch (e: Exception) {
            Log.e("AppleDuoFold", "RuntimeShader failed: ${e.message}", e)
            renderEffect = null
            return@graphicsLayer
        }
        renderEffect = try {
            RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        } catch (e: Exception) {
            Log.e("AppleDuoFold", "createRuntimeShaderEffect failed: ${e.message}", e)
            null
        }
        clip = true
        @Suppress("UNUSED_EXPRESSION")
        density
    }
}
