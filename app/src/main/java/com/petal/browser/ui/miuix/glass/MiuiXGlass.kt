package com.petal.browser.ui.miuix.glass

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.containment.petalRealGlassSupported
import com.petal.browser.ui.miuix.theme.MiuiXShapes
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors

/**
 * Xiaomi HyperOS Flagship Acrylic Blur & Translucent Glass Engine.
 *
 * Implements the flagship progressive multi-pass blur and vibrancy material seen on
 * high-end Xiaomi devices running HyperOS/MIUIX.
 *
 * Capabilities:
 * - Real-time progressive backdrop blur via Kyant's backdrop engine (`io.github.kyant0:backdrop`)
 * - Color vibrancy and saturation boost (1.35x) to maintain vibrant contrast under heavy blur
 * - Specular 0.5dp inner highlight rim stroke (`Color(0x22FFFFFF)` dark / `Color(0x0F000000)` light)
 * - Automatic graceful fallback to solid elevated squircle surfaces on Android < 12 (API < 31)
 */
object MiuiXGlassTuning {
    val BlurRadius: Dp = 24.dp
    const val SaturationBoost: Float = 1.35f
    const val DarkVeilAlpha: Float = 0.72f
    const val LightVeilAlpha: Float = 0.78f
}

/**
 * Applies the flagship acrylic glass material to this composable.
 *
 * When a [backdrop] is supplied on supported hardware (API 31+), it renders authentic
 * real-time refractive acrylic blur with vibrancy. Otherwise, it renders a sleek,
 * semi-translucent elevated squircle surface with specular hairline border.
 */
fun Modifier.miuixAcrylicGlass(
    shape: Shape = MiuiXShapes.Card,
    isDark: Boolean,
    backdrop: PetalBackdrop? = null,
    enabled: Boolean = true,
    drawBorder: Boolean = true,
): Modifier {
    if (!enabled) return this

    val borderStroke = if (drawBorder) {
        val borderColor = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
        BorderStroke(0.5.dp, borderColor)
    } else null

    val baseModifier = this.clip(shape)

    // Flagship real-time backdrop blur on Android 12+
    if (backdrop != null && petalRealGlassSupported) {
        val blurredBackdrop = baseModifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { Highlight.Default },
            effects = {
                vibrancy()
                colorControls(
                    brightness = 0f,
                    contrast = 1f,
                    saturation = MiuiXGlassTuning.SaturationBoost
                )
                blur(MiuiXGlassTuning.BlurRadius.toPx())
            },
            onDrawSurface = {
                // HyperOS acrylic tint layer
                val veil = if (isDark) {
                    Color(0xFF1C1C1E).copy(alpha = MiuiXGlassTuning.DarkVeilAlpha)
                } else {
                    Color(0xFFFFFFFF).copy(alpha = MiuiXGlassTuning.LightVeilAlpha)
                }
                drawRect(veil)
            }
        )
        return if (borderStroke != null) blurredBackdrop.border(borderStroke, shape) else blurredBackdrop
    }

    // High-performance translucent/solid acrylic surface fallback
    val surfaceColor = if (isDark) {
        Color(0xFF1C1C1E).copy(alpha = if (petalRealGlassSupported) 0.88f else 1f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = if (petalRealGlassSupported) 0.92f else 1f)
    }

    val surfaceModifier = baseModifier.background(surfaceColor, shape)
    return if (borderStroke != null) surfaceModifier.border(borderStroke, shape) else surfaceModifier
}

/**
 * Composable container that hosts the flagship MiuiX acrylic surface.
 */
@Composable
fun MiuiXGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MiuiXShapes.Card,
    backdrop: PetalBackdrop? = null,
    enabled: Boolean = true,
    drawBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalMiuiXColors.current
    Box(
        modifier = modifier.miuixAcrylicGlass(
            shape = shape,
            isDark = colors.isDark,
            backdrop = backdrop,
            enabled = enabled,
            drawBorder = drawBorder,
        ),
        content = content
    )
}
