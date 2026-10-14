package com.petal.browser.ui.containment

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

/** Blur needs API 31 and the lens shader needs API 33, so older phones keep the fallback look. */
val petalRealGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal fun Modifier.petalRealGlass(
    backdrop: PetalBackdrop?,
    shape: Shape,
    isDark: Boolean
): Modifier {
    if (backdrop == null || !petalRealGlassSupported) return this
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = { Highlight.Default },
        effects = {
            vibrancy()
            colorControls(
                brightness = 0f,
                contrast = 1f,
                saturation = 1.5f
            )
            blur(7f.dp.toPx())
            // Stride's panel lens, clamped to fit short headers.
            lens(24.dp.toPx().coerceAtMost(size.minDimension / 2f), 56.dp.toPx(), false)
        },
        onDrawSurface = {
            // Light veil so text stays readable on any background.
            drawRect((if (isDark) Color.Black else Color.White).copy(alpha = if (isDark) .22f else .32f))
        }
    )
}
