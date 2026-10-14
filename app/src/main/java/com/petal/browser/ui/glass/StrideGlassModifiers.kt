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
    if (bar == null) {
        if (wallpaper != null && wallpaper.ready) {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(androidx.compose.ui.platform.LocalContext.current)
            val edge = prefs.getFloat("sp_liquid_glass_sheen", .60f).coerceIn(0f, 1f) / .60f
            return this.petalGlassPanel(shape, tint.copy(alpha = 0.45f), edge)
        }
        return this.clip(shape).background(tint.copy(alpha = 0.94f))
    }
    val density = LocalDensity.current
    val dark = tint.luminance() < 0.5f
    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(androidx.compose.ui.platform.LocalContext.current)
    val themeGlass = prefs.getBoolean("sp_liquid_glass_enabled", false)
    val opacity = if (themeGlass) prefs.getFloat("sp_liquid_glass_alpha", .70f).coerceIn(.20f, .95f) / .70f else 1f
    val edge = if (themeGlass) prefs.getFloat("sp_liquid_glass_sheen", .60f).coerceIn(0f, 1f) / .60f else 1f
    // Stride's bar only ever floats over its own themed screens, so a breath of tint
    // (14-20 %) is enough there. A browser's floats over any page — a white article in the
    // dark theme washed its icons out — so the tint is heavier here; the blur, the bent rim
    // and the lit edge still carry the glass.
    val look = with(density) {
        val blur = 6.dp.toPx()
        GlassLook(
            fill = tint.copy(alpha = ((if (dark) 0.5f else 0.55f) * opacity).coerceIn(0f, 1f)).argb(),
            specular = Color.White.copy(alpha = ((if (dark) 0.22f else 0.55f) * edge).coerceIn(0f, 1f)).argb(),
            rimTop = Color.White.copy(alpha = ((if (dark) 0.35f else 0.8f) * edge).coerceIn(0f, 1f)).argb(),
            rimBottom = Color.White.copy(alpha = ((if (dark) 0.06f else 0.25f) * edge).coerceIn(0f, 1f)).argb(),
            blur = blur,
            lensHeight = 22.dp.toPx(),
            lensAmount = 36.dp.toPx(),
            specHeight = 3.dp.toPx(),
            rimWidth = 1.dp.toPx(),
            // Margin for the blur only; the lens samples inward and needs none.
            pad = blur * 1.5f + 2.dp.toPx()
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
                        // First frame, before the backdrop exists, or a software canvas.
                        drawOutline(shape.createOutline(size, layoutDirection, this), tint.copy(alpha = 0.94f))
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
        SlabLook(
            fill = tint.copy(alpha = 0.08f).argb(),
            rimTop = Color.White.copy(alpha = 0.9f).argb(),
            rimBottom = Color.White.copy(alpha = 0.3f).argb(),
            lensHeight = 14.dp.toPx(),
            lensAmount = 22.dp.toPx(),
            rimWidth = 1.dp.toPx(),
            pad = 2.dp.toPx()
        )
    }
    if (bar == null) {
        val wallpaper = LocalPetalGlassWallpaper.current
        val isGlass = wallpaper != null && wallpaper.ready
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
