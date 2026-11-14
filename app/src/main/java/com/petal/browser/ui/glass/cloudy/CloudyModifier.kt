package com.petal.browser.ui.glass.cloudy

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.containment.petalRealGlass
import com.petal.browser.ui.containment.petalRealGlassSupported
import com.petal.browser.ui.glass.RefractionTuning

/**
 * Self-blur and scrim modifier inspired by skydoves/Cloudy.
 *
 * @param radius Radius of the blur in Dp.
 * @param enabled Whether the blur effect is active.
 * @param clipShape Optional clipping shape.
 */
fun Modifier.cloudy(
    radius: Dp = 16.dp,
    enabled: Boolean = true,
    clipShape: Shape? = null,
): Modifier {
    if (!enabled || radius <= 0.dp) return this

    val clipped = if (clipShape != null) this.clip(clipShape) else this

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return clipped.graphicsLayer {
            val rPx = radius.toPx().coerceAtLeast(0.1f)
            val effect = RenderEffect.createBlurEffect(rPx, rPx, Shader.TileMode.CLAMP)
            renderEffect = effect.asComposeRenderEffect()
        }
    }

    return clipped
}

/**
 * Enhanced Liquid Glass modifier incorporating Cloudy optical chromatic dispersion
 * and SDF normal-based refraction (when enabled).
 */
@Composable
fun Modifier.cloudyLiquidGlass(
    backdrop: PetalBackdrop?,
    shape: Shape,
    cornerRadius: Dp = 20.dp,
    isDark: Boolean = true,
    cloudyTuning: CloudyGlassTuning = rememberCloudyTuning(),
    refraction: RefractionTuning = RefractionTuning(),
    opacity: Float = 0.70f,
): Modifier {
    val base = this.petalRealGlass(
        backdrop = backdrop,
        shape = shape,
        isDark = isDark,
        refraction = refraction,
        opacity = opacity,
    )

    if (cloudyTuning.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return base.cloudyOpticalRefraction(
            cornerRadius = cornerRadius,
            rimWidth = 24.dp * refraction.width,
            refractionStrength = 16f * refraction.strength,
            tuning = cloudyTuning,
            isDark = isDark,
        )
    }

    return base
}
