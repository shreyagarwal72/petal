/*
 * File: ProgressiveBlurModifier.kt
 * Description: Real-time, edge-graduated (progressive) blur modifier backed
 * by an AGSL RuntimeShader. Ported from sameerasw/essentials (MIT License)
 * and used as the Header Blur / Navigation Bar Blur implementation in
 * Petal Browser, replacing the previous flat-alpha translucency approach.
 */

package com.petal.browser.ui.modifiers

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.petal.browser.utils.DeviceUtils

enum class BlurDirection {
    TOP, BOTTOM
}

// language=AGSL
private val PROGRESSIVE_BLUR_SKSL = """
    uniform shader content;
    uniform float blurRadius;
    uniform float height;
    uniform float contentHeight;
    uniform int isTop;

    half4 main(float2 fragCoord) {
        float progress;
        if (isTop == 1) {
            progress = 1.0 - clamp(fragCoord.y / height, 0.0, 1.0);
        } else {
            progress = 1.0 - clamp((contentHeight - fragCoord.y) / height, 0.0, 1.0);
        }

        // Easing curve for smoother transition (power curve)
        progress = pow(progress, 1.5);

        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;

        // Random value for dithering based on pixel coordinates
        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 4;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                // Apply jittered sampling with dither
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;

                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;

                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
""".trimIndent()

/**
 * Applies a progressive (edge-graduated) blur to the specified edge of the
 * element it's attached to. Only works on Android 13+ (API 33) — on older
 * devices, power-save mode, or known-problematic devices, the shader is
 * skipped and only the [showGradientOverlay] scrim (if any) is drawn.
 */
fun Modifier.progressiveBlur(
    blurRadius: Float,
    height: Float,
    direction: BlurDirection = BlurDirection.TOP,
    showGradientOverlay: Boolean = true
): Modifier = composed {
    val overlayColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)

    val context = LocalContext.current
    val isPowerSave =
        remember(context) { DeviceUtils.isPowerSaveMode(context) }
    val isBlurProblematicDevice =
        remember { DeviceUtils.isBlurProblematicDevice() }

    val blurModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blurRadius > 0f && !isPowerSave && !isBlurProblematicDevice) {
            Modifier.graphicsLayer {
                val shader = RuntimeShader(PROGRESSIVE_BLUR_SKSL)
                shader.setFloatUniform("blurRadius", blurRadius)
                shader.setFloatUniform("height", height)
                shader.setFloatUniform("contentHeight", size.height)
                shader.setIntUniform("isTop", if (direction == BlurDirection.TOP) 1 else 0)

                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
            }
        } else Modifier

    val gradientModifier = if (showGradientOverlay) {
        Modifier.drawWithContent {
            drawContent()
            val (brush, _) = when (direction) {
                BlurDirection.TOP -> {
                    Brush.verticalGradient(
                        colors = listOf(overlayColor, Color.Transparent),
                        endY = height
                    ) to height
                }

                BlurDirection.BOTTOM -> {
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, overlayColor),
                        startY = size.height - height
                    ) to height
                }
            }
            drawRect(brush = brush)
        }
    } else Modifier

    this
        .then(blurModifier)
        .then(gradientModifier)
}
