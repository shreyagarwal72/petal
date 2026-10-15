/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.petal.browser.ui.glass

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Stride/Vyxel's shared wallpaper: frost once, then refract only each card's patch. */
class PetalGlassWallpaper(val sharp: GraphicsLayer, val frosted: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    var ready by mutableStateOf(false)
}

val LocalPetalGlassWallpaper = staticCompositionLocalOf<PetalGlassWallpaper?> { null }
val LocalPetalGlassRevision = compositionLocalOf { 0 }

@Composable
fun rememberPetalGlassWallpaper(): PetalGlassWallpaper {
    val sharp = rememberGraphicsLayer()
    val frosted = rememberGraphicsLayer()
    return remember(sharp, frosted) { PetalGlassWallpaper(sharp, frosted) }
}

/** Apply only to the background sibling, never to a container holding glass cards. */
fun Modifier.captureGlassWallpaper(wallpaper: PetalGlassWallpaper): Modifier =
    onGloballyPositioned {
        wallpaper.origin = it.positionInRoot()
        wallpaper.ready = it.size.width > 0 && it.size.height > 0
    }.drawWithCache {
        wallpaper.frosted.renderEffect = if (Build.VERSION.SDK_INT >= 31) {
            android.graphics.RenderEffect.createBlurEffect(
                7.dp.toPx(), 7.dp.toPx(), android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        } else null
        onDrawWithContent {
            wallpaper.sharp.record { this@onDrawWithContent.drawContent() }
            wallpaper.frosted.record { drawLayer(wallpaper.sharp) }
            drawLayer(wallpaper.sharp)
        }
    }

/** Fixed palette pools and contours, matching Stride's default glass wallpaper. */
@Composable
fun PetalGlassPattern(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    Canvas(modifier.fillMaxSize()) {
        drawRect(scheme.background)
        val colors = if (dark) listOf(scheme.primary.copy(alpha = .85f),
            scheme.tertiary.copy(alpha = .75f), scheme.secondary.copy(alpha = .65f))
        else listOf(scheme.primary.copy(alpha = .42f), scheme.tertiary.copy(alpha = .36f),
            scheme.secondaryContainer.copy(alpha = .9f))
        val centers = listOf(Offset(size.width * .15f, size.height * .12f),
            Offset(size.width * .95f, size.height * .48f),
            Offset(size.width * .2f, size.height * .88f))
        centers.forEachIndexed { i, center ->
            val radius = size.width * (.85f + i * .05f)
            drawCircle(Brush.radialGradient(listOf(colors[i],
                Color.Transparent), center, radius), radius, center)
        }
        repeat(12) { i ->
            val y = size.height * (.04f + i * .085f)
            val amp = size.height * .035f * if (i % 2 == 0) 1f else -1f
            val path = Path().apply {
                moveTo(-size.width * .1f, y)
                cubicTo(size.width * .25f, y - amp * 2, size.width * .55f,
                    y + amp * 2, size.width * 1.1f, y - amp)
            }
            drawPath(path, (if (dark) Color.White else scheme.primary).copy(alpha = if (dark) .26f else .32f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun Modifier.petalGlassPanel(shape: Shape, tint: Color, edge: Float): Modifier {
    val wallpaper = LocalPetalGlassWallpaper.current ?: return this
    if (!wallpaper.ready) return this
    val place = remember { GlassPanelPlace() }
    val glass = rememberGraphicsLayer()
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return onGloballyPositioned { place.coords = it; place.origin = it.positionInRoot() }
        .drawWithCache {
            val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val lensHeight = 12.dp.toPx()
            val lensAmount = 14.dp.toPx()
            val pad = lensAmount + 4.dp.toPx()
            val radii = cornerRadii(shape, size, layoutDirection, this)
            val effect = radii?.let { lensEffect(size, pad, it, lensHeight, lensAmount) }
            glass.renderEffect = effect?.asComposeRenderEffect()
            val full = IntSize(ceil(size.width + 2 * pad).toInt().coerceAtLeast(1),
                ceil(size.height + 2 * pad).toInt().coerceAtLeast(1))
            val rim = Brush.verticalGradient(listOf(
                Color.White.copy(alpha = ((if (dark) .3f else .75f) * edge).coerceIn(0f, 1f)),
                Color.White.copy(alpha = ((if (dark) .05f else .2f) * edge).coerceIn(0f, 1f))))
            val spec = Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f * edge.coerceIn(0f, 1f)),
                Color.Transparent), endY = 3.dp.toPx())
            onDrawBehind {
                val stored = place.origin
                val origin = place.coords?.takeIf { it.isAttached }?.positionInRoot() ?: stored
                val shift = wallpaper.origin - origin
                clipPath(outline) {
                    if (effect != null) {
                        glass.record(full) { translate(shift.x + pad, shift.y + pad) { drawLayer(wallpaper.frosted) } }
                        translate(-pad, -pad) { drawLayer(glass) }
                    } else {
                        translate(shift.x, shift.y) { drawLayer(wallpaper.frosted) }
                    }
                    drawRect(if (Build.VERSION.SDK_INT < 31) tint.copy(alpha = .85f) else tint)
                    drawRect(spec)
                }
                drawPath(outline, rim, style = Stroke(2.dp.toPx()))
            }
        }
}

private class GlassPanelPlace {
    var coords: LayoutCoordinates? = null
    var origin by mutableStateOf(Offset.Zero)
}
