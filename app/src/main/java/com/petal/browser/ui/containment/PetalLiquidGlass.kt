package com.petal.browser.ui.containment

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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

/**
 * Average brightness (0 dark .. 1 bright) of what the backdrop is showing, from a 5x5
 * thumbnail (Kyant's adaptive-luminance recipe). Sampled slowly and eased; any failure keeps
 * [fallback], so a device that cannot read the layer just gets the theme-based look.
 */
@Composable
private fun rememberBackdropLuminance(backdrop: PetalBackdrop, fallback: Float): Float {
    var sampled by remember(backdrop) { mutableFloatStateOf(fallback) }
    LaunchedEffect(backdrop) {
        while (isActive) {
            delay(1200)
            try {
                val full = backdrop.graphicsLayer.toImageBitmap().asAndroidBitmap()
                val thumb = Bitmap.createScaledBitmap(full, 5, 5, false)
                    .copy(Bitmap.Config.ARGB_8888, false)
                val px = IntArray(25)
                thumb.getPixels(px, 0, 5, 0, 0, 5, 5)
                var sum = 0.0
                for (argb in px) {
                    val r = (argb shr 16 and 0xFF) / 255.0
                    val g = (argb shr 8 and 0xFF) / 255.0
                    val b = (argb and 0xFF) / 255.0
                    sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                }
                sampled = (sum / px.size).toFloat()
            } catch (_: Throwable) {
                // Layer not recorded yet or not readable: keep the last value.
            }
        }
    }
    val eased by animateFloatAsState(sampled, label = "backdropLuminance")
    return eased
}

@Composable
internal fun Modifier.petalRealGlass(
    backdrop: PetalBackdrop?,
    shape: Shape,
    isDark: Boolean,
    refraction: com.petal.browser.ui.glass.RefractionTuning = com.petal.browser.ui.glass.RefractionTuning(),
    /** Translucency slider (0.10..0.95): higher = denser veil. */
    opacity: Float = 0.70f,
    /** Gaussian blur radius in dp; null = the default 7dp. */
    blurDp: Float? = null
): Modifier {
    if (backdrop == null || !petalRealGlassSupported) return this
    val luminance = rememberBackdropLuminance(backdrop, if (isDark) 0f else 1f)
    // Kyant's lens() throws for anything but a corner-based shape (Expressive*, GenericShape,
    // RectangleShape...). Those keep real blur/highlight, just without rim refraction.
    val lensSafe = shape is CornerBasedShape
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = { Highlight.Default },
        innerShadow = {
            InnerShadow(
                radius = 8.dp,
                offset = DpOffset(0.dp, 4.dp),
                color = Color.Black.copy(alpha = 0.10f)
            )
        },
        effects = {
            // vibrancy() already applies saturation 1.5. A second colorControls(saturation = 1.5)
            // stacked to 2.25x and made the glass look neon, so only one is applied.
            vibrancy()
            blur((blurDp ?: 7f).dp.toPx())
            // Stride's panel lens, clamped to fit short headers. Past 1.25x strength the rim
            // also splits colours (chromatic aberration), as in Kyant's toggle.
            if (lensSafe && refraction.enabled && petalLensSupported) {
                val lensHeight = (24.dp.toPx() * refraction.width).coerceAtMost(size.minDimension / 2f)
                lens(
                    lensHeight,
                    56.dp.toPx() * refraction.strength,
                    false,
                    refraction.strength > 1.25f
                )
            }
        },
        onDrawSurface = {
            // Light veil so text stays readable on any background. Text colour follows the
            // theme, so the veil grows when the picture behind fights it: a bright picture
            // under white (dark-theme) text, or a dark one under black (light-theme) text.
            val clash = if (isDark) luminance else 1f - luminance
            val base = if (isDark) 0.08f + opacity * 0.20f else 0.12f + opacity * 0.28f
            val veil = (base + 0.22f * clash.coerceIn(0f, 1f)).coerceIn(0f, 0.9f)
            drawRect((if (isDark) Color.Black else Color.White).copy(alpha = veil))
        }
    )
}

/**
 * LastWave's header glass: vibrant saturated blur, a light tint instead of a scrim, a soft top
 * sheen, a gentle lens so the curved bottom edge bends the content passing under it, and a thin
 * rim. Static (no pointer input, no per-frame state): one blur pass. Honours the Petal glass
 * sliders: blur (default 24dp) and translucency (scales the tint, 0.70 = LastWave's tint).
 * The element MUST be a sibling of the backdrop source, never inside it.
 */
@Composable
internal fun Modifier.petalHeaderGlass(
    backdrop: PetalBackdrop,
    shape: Shape,
    isDark: Boolean,
    opacity: Float = 0.70f,
    blurDp: Float? = null
): Modifier {
    if (!petalRealGlassSupported) return this
    // Kyant's lens() throws for a plain RectangleShape; a zero-radius rounded rect is identical.
    val safeShape = if (shape == RectangleShape) RoundedCornerShape(0.dp) else shape
    val lensSafe = safeShape is CornerBasedShape
    val tintScale = (opacity / 0.70f).coerceIn(0.2f, 1.35f)
    val tint = (if (isDark) 0.30f else 0.46f) * tintScale
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { safeShape },
        highlight = { Highlight(width = 1.dp) },
        effects = {
            colorControls(
                brightness = if (isDark) 0f else 0.04f,
                contrast = 1f,
                saturation = 1.6f
            )
            blur((blurDp ?: 24f).dp.toPx())
            if (lensSafe && petalLensSupported) {
                lens(
                    14.dp.toPx().coerceAtMost(size.minDimension / 3f),
                    28.dp.toPx().coerceAtMost(size.minDimension / 2f),
                    false
                )
            }
        },
        onDrawSurface = {
            drawRect((if (isDark) Color.Black else Color.White).copy(alpha = tint.coerceIn(0f, 0.85f)))
            // Sheen: light catching the top of the pane.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isDark) 0.07f else 0.16f),
                    0.55f to Color.Transparent
                )
            )
        }
    )
}
