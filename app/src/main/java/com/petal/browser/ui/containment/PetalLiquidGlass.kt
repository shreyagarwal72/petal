package com.petal.browser.ui.containment

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.petal.browser.ui.glass.PetalLiquidGlassTuning
import com.petal.browser.ui.glass.RefractionTuning
import com.petal.browser.ui.glass.createPetalLiquidRenderEffect

/**
 * Explicit sibling backdrops for headers. Repeated cards use the shared frosted wallpaper
 * in PetalGlassWallpaper; browser bars use StrideGlass's View-backed capture.
 *
 * How it works
 *  - One composable (the "source") is marked with [petalBackdropSource]. Its pixels are recorded.
 *  - Glass surfaces call `liquidGlassChrome(..., backdrop = thatBackdrop)`; they blur and refract
 *    what the source recorded.
 *  - The glass MUST be a SIBLING of the source, never inside it (feedback loop = crash).
 *  - A WebView cannot be recorded, so surfaces drawn over web pages stay on the fallback look.
 */
typealias PetalBackdrop = LayerBackdrop

@Composable
fun rememberPetalBackdrop(): PetalBackdrop = rememberLayerBackdrop()

/** Marks this element as the picture that sibling glass surfaces refract. */
fun Modifier.petalBackdropSource(backdrop: PetalBackdrop): Modifier = this.layerBackdrop(backdrop)

/**
 * Real (backdrop) glass needs RenderEffect, so API 31+. The edge-bending lens is an AGSL
 * RuntimeShader and needs API 33; on Android 12/12L the glass is still real, blurred and
 * vibrant, just without the rim refraction. Below API 31 the frosted fallback look is used.
 */
val petalRealGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The lens refraction shader (API 33+). */
val petalLensSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal fun Modifier.petalRealGlass(
    backdrop: PetalBackdrop?,
    shape: Shape,
    isDark: Boolean,
    refraction: RefractionTuning = RefractionTuning(),
    /** Translucency slider (0.10..0.95): higher = denser veil. */
    opacity: Float = 0.70f,
    liquidTuning: PetalLiquidGlassTuning? = null
): Modifier {
    if (backdrop == null || !petalRealGlassSupported) return this
    val tuning = liquidTuning ?: PetalLiquidGlassTuning(
        refractionStrength = 0.085f * refraction.strength,
        edgeWidth = 0.040f * refraction.width
    )
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = { Highlight.Default },
        effects = {
            vibrancy()
            colorControls(
                brightness = 0f,
                contrast = tuning.contrastBoost,
                saturation = 1.35f
            )
            blur(tuning.blurRadiusDp.dp.toPx())

            // High-fidelity AGSL Liquid Glass Shader (API 33+)
            if (tuning.refractionEnabled && petalLensSupported) {
                val veilAlpha = (if (isDark) 0.08f + opacity * 0.20f else 0.12f + opacity * 0.28f).coerceIn(0f, 0.9f)
                val liquidEffect = createPetalLiquidRenderEffect(
                    size = size,
                    offset = Offset.Zero,
                    shape = shape,
                    tuning = tuning,
                    tintColor = if (isDark) Color.Black else Color.White,
                    tintAlpha = veilAlpha,
                    density = this,
                    scope = this
                )
                if (liquidEffect != null) {
                    effect(liquidEffect.asComposeRenderEffect())
                } else {
                    val lensHeight = (24.dp.toPx() * (tuning.edgeWidth * 10f)).coerceAtMost(size.minDimension / 2f)
                    lens(lensHeight, 56.dp.toPx() * (tuning.refractionStrength * 10f), false)
                }
            } else if (refraction.enabled && petalLensSupported) {
                val lensHeight = (24.dp.toPx() * refraction.width).coerceAtMost(size.minDimension / 2f)
                lens(lensHeight, 56.dp.toPx() * refraction.strength, false)
            }
        },
        onDrawSurface = {
            // Light veil fallback so text stays readable on any background
            val veil = (if (isDark) 0.08f + opacity * 0.20f else 0.12f + opacity * 0.28f).coerceIn(0f, 0.9f)
            drawRect((if (isDark) Color.Black else Color.White).copy(alpha = veil))
        }
    )
}
