package com.petal.browser.ui.glass

import android.content.Context
import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** Stride-style glass on Petal's nav bar. On by default; needs Android 12 for the blur. */
object StrideGlassPrefs {
    const val KEY = "sp_stride_glass_nav"

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, true)
}

/**
 * The glass for a bar floating over [source], or null when glass is off or unsupported —
 * the bar is then a solid fill, as Stride's bar is outside its shell.
 */
@Composable
fun rememberGlassBar(source: View?): GlassBar? {
    if (source == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val host = LocalView.current
    val bar = remember(source) { GlassBackdrop.of(source)?.let { GlassBar(it) } } ?: return null
    bar.host = host
    DisposableEffect(bar) {
        bar.attach()
        onDispose { bar.detach() }
    }
    return bar
}

/**
 * Liquid glass for the nav bar: what's under it, blurred, refracted at the rim and tinted
 * with [tint] — Stride's recipe. Without [bar] the bar is filled with [tint], nearly opaque,
 * rather than leaving its labels over sharp, busy content.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape, tint: Color, bar: GlassBar?): Modifier {
    LocalPetalGlassRevision.current
    val wallpaper = LocalPetalGlassWallpaper.current
    val density = LocalDensity.current
    val dark = tint.luminance() < 0.5f
    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(androidx.compose.ui.platform.LocalContext.current)
    val themeGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav")
    val alphaPref = prefs.getFloat("sp_liquid_glass_alpha", .70f).coerceIn(.10f, .95f)
    val opacity = if (themeGlass) (0.25f + alphaPref * 1.05f) else 1f
    val baseEdge = if (themeGlass) (prefs.getFloat("sp_liquid_glass_sheen", .60f).coerceIn(0f, 1f) / .60f) else 1f
    // Frost level matched to the settings containments (~0.4 alpha), so the bar reads as
    // frosted glass even where the live page cannot be sampled.
    val baseTintAlpha = ((if (dark) 0.30f else 0.38f) * opacity).coerceIn(0f, 0.9f)
    if (bar == null) {
        if (wallpaper != null && wallpaper.ready) {
            return this.petalGlassPanel(shape, tint.copy(alpha = baseTintAlpha), baseEdge)
        }
        if (themeGlass) {
            val fallbackAlpha = if (dark) 0.38f else 0.45f
            val rimBrush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = ((if (dark) 0.55f else 0.85f) * baseEdge).coerceIn(0f, 1f)),
                    tint.copy(alpha = ((if (dark) 0.35f else 0.50f) * baseEdge).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = ((if (dark) 0.10f else 0.20f) * baseEdge).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = ((if (dark) 0.25f else 0.40f) * baseEdge).coerceIn(0f, 1f))
                ),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
            )
            return this
                .clip(shape)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val specBrush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = (0.28f * baseEdge).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        endY = 4.dp.toPx()
                    )
                    onDrawBehind {
                        drawOutline(outline, tint.copy(alpha = (fallbackAlpha * opacity).coerceIn(0.15f, 0.85f)))
                        drawRect(specBrush)
                        drawOutline(outline, rimBrush, style = Stroke((1.25.dp * (0.8f + baseEdge * 0.4f)).toPx()))
                    }
                }
        }
        return this.clip(shape).background(tint.copy(alpha = 0.94f))
    }
    // Liquid glass tint: authentic physical glass with pure light bending refraction,
    // beveled specular sheen, and clear optical depth.
    val look = with(density) {
        val blur = (2.5f + alphaPref * 5.0f).dp.toPx()
        val lensHeight = 16.dp.toPx()
        val lensAmount = 18.dp.toPx()
        // Ensure pad completely accommodates both blur and refraction displacement
        val pad = blur * 1.5f + lensAmount + 6.dp.toPx()
        GlassLook(
            fill = tint.copy(alpha = baseTintAlpha).argb(),
            specular = Color.White.copy(alpha = ((if (dark) 0.35f else 0.65f) * baseEdge).coerceIn(0f, 1f)).argb(),
            rimTop = Color.White.copy(alpha = ((if (dark) 0.55f else 0.85f) * baseEdge).coerceIn(0f, 1f)).argb(),
            rimBottom = Color.White.copy(alpha = ((if (dark) 0.12f else 0.30f) * baseEdge).coerceIn(0f, 1f)).argb(),
            blur = blur,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            specHeight = 3.dp.toPx(),
            rimWidth = 1.dp.toPx(),
            pad = pad
        )
    }
    return this
        .onGloballyPositioned { bar.coords = it }
        .drawWithCache {
            bar.setShape(size, shape, layoutDirection, this, look)
            onDrawBehind {
                bar.record()
                drawIntoCanvas { canvas ->
                    if (!bar.drawBar(canvas.nativeCanvas)) {
                        drawOutline(shape.createOutline(size, layoutDirection, this), tint.copy(alpha = baseTintAlpha))
                        val fallbackOutline = shape.createOutline(size, layoutDirection, this)
                        val rimBrush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = ((if (dark) 0.55f else 0.85f) * baseEdge).coerceIn(0f, 1f)),
                                tint.copy(alpha = ((if (dark) 0.35f else 0.50f) * baseEdge).coerceIn(0f, 1f)),
                                Color.White.copy(alpha = ((if (dark) 0.10f else 0.20f) * baseEdge).coerceIn(0f, 1f)),
                                Color.White.copy(alpha = ((if (dark) 0.25f else 0.40f) * baseEdge).coerceIn(0f, 1f))
                            ),
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                        )
                        drawOutline(fallbackOutline, rimBrush, style = androidx.compose.ui.graphics.drawscope.Stroke((1.25.dp * (0.8f + baseEdge * 0.4f)).toPx()))
                    }
                }
            }
        }
}

/**
 * The selection slab that slides between nav items: clear glass lying on the frosted bar.
 * It refracts the bar — never the screen behind it, and never with a blur of its own.
 *
 * [target] gives the selected item's bounds in root coordinates and is read at draw time,
 * every frame, so an animated target is followed exactly with nothing recomposed.
 */
@Composable
fun Modifier.glassTile(target: () -> Rect?, shape: Shape, tint: Color, bar: GlassBar?): Modifier {
    val density = LocalDensity.current
    val look = with(density) {
        val lensHeight = 12.dp.toPx()
        val lensAmount = 14.dp.toPx()
        SlabLook(
            fill = tint.copy(alpha = 0.12f).argb(),
            rimTop = Color.White.copy(alpha = 0.95f).argb(),
            rimBottom = Color.White.copy(alpha = 0.35f).argb(),
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            rimWidth = 1.dp.toPx(),
            pad = lensAmount + 4.dp.toPx()
        )
    }
    if (bar == null) {
        val wallpaper = LocalPetalGlassWallpaper.current
        val themeGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav")
        val isGlass = (wallpaper != null && wallpaper.ready) || themeGlass
        val solid = if (isGlass) tint.copy(alpha = 0.28f) else tint.copy(alpha = 0.16f)
        val place = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
        return this
            .onGloballyPositioned { place[0] = it }
            .drawWithCache {
                onDrawBehind {
                    val own = place[0]?.takeIf { it.isAttached }?.positionInRoot() ?: return@onDrawBehind
                    val r = target()?.translate(-own) ?: return@onDrawBehind
                    translate(r.left, r.top) {
                        drawOutline(shape.createOutline(r.size, layoutDirection, this), solid)
                        if (isGlass) {
                            val rim = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.15f)),
                                startY = 0f,
                                endY = r.size.height
                            )
                            drawOutline(shape.createOutline(r.size, layoutDirection, this), rim, style = Stroke(1.5f.dp.toPx()))
                        }
                    }
                }
            }
    }
    val place = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { place[0] = it }
        .drawWithCache {
            onDrawBehind {
                val barCoords = bar.coords?.takeIf { it.isAttached } ?: return@onDrawBehind
                val own = place[0]?.takeIf { it.isAttached } ?: return@onDrawBehind
                val barInRoot = barCoords.positionInRoot()
                val r = target()?.translate(-barInRoot)
                bar.setSlab(r, shape, layoutDirection, this, look)
                bar.recordSlab()
                drawIntoCanvas { canvas ->
                    bar.drawSlab(canvas.nativeCanvas, own.positionInRoot() - barInRoot)
                }
            }
        }
}
