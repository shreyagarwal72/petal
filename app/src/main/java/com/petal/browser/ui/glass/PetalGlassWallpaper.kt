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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Stride/Vyxel's shared wallpaper: frost once, then refract only each card's patch. */
class PetalGlassWallpaper(val sharp: GraphicsLayer, val frosted: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    var ready by mutableStateOf(false)
    var hasRecorded by mutableStateOf(false)
}

val LocalPetalGlassWallpaper = staticCompositionLocalOf<PetalGlassWallpaper?> { null }
val LocalPetalGlassRevision = compositionLocalOf { 0 }

/**
 * The view that sits *behind* popups, menus and sheets of the current screen. Full-screen
 * overlays (PDF viewer, file viewer, ...) provide their own host view here so the glass of
 * the windows opened above them refracts that screen live, instead of the browser underneath.
 */
val LocalPetalGlassSource = staticCompositionLocalOf<android.view.View?> { null }

@Composable
fun rememberPetalGlassWallpaper(): PetalGlassWallpaper {
    val sharp = rememberGraphicsLayer()
    val frosted = rememberGraphicsLayer()
    return remember(sharp, frosted) { PetalGlassWallpaper(sharp, frosted) }
}

/** Apply only to the background sibling, never to a container holding glass cards. */
fun Modifier.captureGlassWallpaper(wallpaper: PetalGlassWallpaper): Modifier =
    onGloballyPositioned {
        try {
            wallpaper.origin = it.positionInRoot()
            val isNowReady = it.isAttached && it.size.width > 0 && it.size.height > 0
            if (!isNowReady) {
                wallpaper.hasRecorded = false
            }
            wallpaper.ready = isNowReady
        } catch (_: Throwable) {
            wallpaper.ready = false
            wallpaper.hasRecorded = false
        }
    }.drawWithCache {
        wallpaper.frosted.renderEffect = if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.graphics.RenderEffect.createBlurEffect(
                    20.dp.toPx(), 20.dp.toPx(), android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } catch (_: Throwable) { null }
        } else null
        onDrawWithContent {
            try {
                if (size.width > 0f && size.height > 0f) {
                    try {
                        wallpaper.sharp.record {
                            try {
                                this@onDrawWithContent.drawContent()
                            } catch (_: Throwable) {}
                        }
                        wallpaper.frosted.record {
                            try {
                                drawLayer(wallpaper.sharp)
                            } catch (_: Throwable) {}
                        }
                        wallpaper.hasRecorded = true
                    } catch (_: Throwable) {
                        wallpaper.hasRecorded = false
                    }
                    this@onDrawWithContent.drawContent()
                } else {
                    this@onDrawWithContent.drawContent()
                }
            } catch (_: Throwable) {
                try {
                    this@onDrawWithContent.drawContent()
                } catch (_: Throwable) {}
            }
        }
    }

/**
 * Same as [captureGlassWallpaper], but re-records every time [tick] changes. Use it on
 * scrolling/zooming content that glass bars float over (PDF pages): the caller bumps [tick]
 * once per frame while something moves, so the frosted wallpaper follows the content live.
 * Draws the content exactly like [captureGlassWallpaper]; only the re-record trigger differs.
 */
fun Modifier.captureGlassWallpaperLive(
    wallpaper: PetalGlassWallpaper,
    tick: () -> Long
): Modifier =
    onGloballyPositioned {
        try {
            wallpaper.origin = it.positionInRoot()
            val isNowReady = it.isAttached && it.size.width > 0 && it.size.height > 0
            if (!isNowReady) wallpaper.hasRecorded = false
            wallpaper.ready = isNowReady
        } catch (_: Throwable) {
            wallpaper.ready = false
            wallpaper.hasRecorded = false
        }
    }.drawWithCache {
        wallpaper.frosted.renderEffect = if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.graphics.RenderEffect.createBlurEffect(
                    20.dp.toPx(), 20.dp.toPx(), android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } catch (_: Throwable) { null }
        } else null
        onDrawWithContent {
            tick() // reading per-frame state makes this block run (and re-record) each frame
            try {
                if (size.width > 0f && size.height > 0f) {
                    try {
                        wallpaper.sharp.record { this@onDrawWithContent.drawContent() }
                        wallpaper.frosted.record { drawLayer(wallpaper.sharp) }
                        wallpaper.hasRecorded = true
                    } catch (_: Throwable) {
                        wallpaper.hasRecorded = false
                    }
                }
            } catch (_: Throwable) {}
            try {
                this@onDrawWithContent.drawContent()
            } catch (_: Throwable) {}
        }
    }

/**
 * Like [captureGlassWallpaper] but draws nothing: it only records the content into the
 * wallpaper layers. Use it for dialogs and popups, where the live screen already shows
 * behind the window and the recorded copy exists only to be frosted and refracted.
 */
fun Modifier.recordGlassWallpaper(wallpaper: PetalGlassWallpaper): Modifier =
    onGloballyPositioned {
        try {
            wallpaper.origin = it.positionInRoot()
            val isNowReady = it.isAttached && it.size.width > 0 && it.size.height > 0
            if (!isNowReady) {
                wallpaper.hasRecorded = false
            }
            wallpaper.ready = isNowReady
        } catch (_: Throwable) {
            wallpaper.ready = false
            wallpaper.hasRecorded = false
        }
    }.drawWithCache {
        wallpaper.frosted.renderEffect = if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.graphics.RenderEffect.createBlurEffect(
                    20.dp.toPx(), 20.dp.toPx(), android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } catch (_: Throwable) { null }
        } else null
        onDrawWithContent {
            try {
                if (size.width > 0f && size.height > 0f) {
                    wallpaper.sharp.record { this@onDrawWithContent.drawContent() }
                    wallpaper.frosted.record { drawLayer(wallpaper.sharp) }
                    wallpaper.hasRecorded = true
                }
            } catch (_: Throwable) {}
        }
    }

/**
 * Live, dynamic variant for popups that live in their own window (the overflow menu).
 * Every frame it re-records [source] (a view of the app window behind the popup) into the
 * wallpaper layers, so the frosted/refracted glass follows animations, scrolling, video and
 * page changes behind the menu exactly like the in-window home screen containments do.
 * Draws nothing itself. [tick] must read a per-frame state so this redraws each frame.
 */
fun Modifier.liveGlassWallpaper(
    wallpaper: PetalGlassWallpaper,
    source: android.view.View,
    tick: () -> Long
): Modifier = liveGlassWallpaperImpl(wallpaper, source, tick, FloatArray(2))

private fun Modifier.liveGlassWallpaperImpl(
    wallpaper: PetalGlassWallpaper,
    source: android.view.View,
    tick: () -> Long,
    boxOnScreen: FloatArray
): Modifier =
    onGloballyPositioned {
        try {
            wallpaper.origin = it.positionInRoot()
            val isNowReady = it.size.width > 0 && it.size.height > 0
            if (!isNowReady) {
                wallpaper.hasRecorded = false
            }
            wallpaper.ready = isNowReady
            val pos = it.positionOnScreen()
            boxOnScreen[0] = pos.x
            boxOnScreen[1] = pos.y
        } catch (_: Throwable) {
            wallpaper.ready = false
            wallpaper.hasRecorded = false
        }
    }.drawWithCache {
        wallpaper.frosted.renderEffect = if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.graphics.RenderEffect.createBlurEffect(
                    20.dp.toPx(), 20.dp.toPx(), android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } catch (_: Throwable) { null }
        } else null
        val srcLoc = IntArray(2)
        onDrawWithContent {
            tick() // reading per-frame state redraws (and re-records) every frame
            if (source.isAttachedToWindow && source.width > 0 && source.height > 0 && size.width > 0f && size.height > 0f) {
                try {
                    source.getLocationOnScreen(srcLoc)
                    val dx = srcLoc[0] - boxOnScreen[0]
                    val dy = srcLoc[1] - boxOnScreen[1]
                    try {
                        wallpaper.sharp.record {
                            drawIntoCanvas { c ->
                                val nc = c.nativeCanvas
                                val save = nc.save()
                                nc.translate(dx, dy)
                                try { source.draw(nc) } catch (_: Throwable) {}
                                nc.restoreToCount(save)
                            }
                        }
                        wallpaper.frosted.record { drawLayer(wallpaper.sharp) }
                        wallpaper.hasRecorded = true
                    } catch (_: Throwable) {
                        wallpaper.hasRecorded = false
                    }
                } catch (_: Throwable) {}
            }
        }
    }

/** Fixed palette pools and contours, matching Stride's default glass wallpaper. */
@Composable
fun PetalGlassPattern(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    Canvas(modifier.fillMaxSize()) {
        drawRect(if (dark) scheme.background else scheme.surface)
        val colors = if (dark) listOf(
            scheme.primary.copy(alpha = .85f),
            scheme.tertiary.copy(alpha = .75f),
            scheme.secondary.copy(alpha = .65f)
        ) else listOf(
            scheme.primary.copy(alpha = .22f),
            scheme.tertiary.copy(alpha = .18f),
            scheme.secondaryContainer.copy(alpha = .45f)
        )
        val centers = listOf(
            Offset(size.width * .15f, size.height * .12f),
            Offset(size.width * .95f, size.height * .48f),
            Offset(size.width * .2f, size.height * .88f)
        )
        centers.forEachIndexed { i, center ->
            val radius = size.width * (.85f + i * .05f)
            drawCircle(
                Brush.radialGradient(listOf(colors[i], Color.Transparent), center, radius),
                radius,
                center
            )
        }
        repeat(12) { i ->
            val y = size.height * (.04f + i * .085f)
            val amp = size.height * .035f * if (i % 2 == 0) 1f else -1f
            val path = Path().apply {
                moveTo(-size.width * .1f, y)
                cubicTo(
                    size.width * .25f, y - amp * 2,
                    size.width * .55f, y + amp * 2,
                    size.width * 1.1f, y - amp
                )
            }
            drawPath(
                path,
                (if (dark) Color.White else scheme.primary).copy(alpha = if (dark) .26f else .14f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            )
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(androidx.compose.ui.platform.LocalContext.current)
    val glowPref = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f)
    return onGloballyPositioned { place.coords = it; place.origin = it.positionInRoot() }
        .drawWithCache {
            val outline = Path().apply {
                try {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                } catch (_: Throwable) {
                    addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                }
            }
            val lensHeight = 14.dp.toPx()
            val lensAmount = 16.dp.toPx()
            val pad = lensAmount + 4.dp.toPx()
            val effect = try {
                val radii = cornerRadii(shape, size, layoutDirection, this)
                radii?.let { lensEffect(size, pad, it, lensHeight, lensAmount) }
            } catch (_: Throwable) { null }
            try {
                glass.renderEffect = effect?.asComposeRenderEffect()
            } catch (_: Throwable) {
                glass.renderEffect = null
            }
            val full = IntSize(
                ceil(size.width + 2 * pad).toInt().coerceAtLeast(1),
                ceil(size.height + 2 * pad).toInt().coerceAtLeast(1)
            )
            // Chromatic dual-angle glowing rim reflecting theme primary accent and specular highlight
            val rim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = ((if (dark) 0.50f else 0.85f) * edge).coerceIn(0f, 1f)),
                    primaryColor.copy(alpha = ((if (dark) 0.35f else 0.45f) * edge * glowPref).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = ((if (dark) 0.08f else 0.20f) * edge).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = ((if (dark) 0.25f else 0.45f) * edge).coerceIn(0f, 1f))
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val spec = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.28f * edge).coerceIn(0f, 1f)),
                    Color.Transparent
                ),
                endY = 4.dp.toPx()
            )
            onDrawBehind {
                try {
                    val stored = place.origin
                    val origin = place.coords?.takeIf { it.isAttached }?.positionInRoot() ?: stored
                    val shift = wallpaper.origin - origin
                    clipPath(outline) {
                        if (wallpaper.hasRecorded) {
                            try {
                                if (effect != null && full.width > 0 && full.height > 0) {
                                    glass.record(full) { translate(shift.x + pad, shift.y + pad) { drawLayer(wallpaper.frosted) } }
                                    translate(-pad, -pad) { drawLayer(glass) }
                                } else {
                                    translate(shift.x, shift.y) { drawLayer(wallpaper.frosted) }
                                }
                            } catch (_: Throwable) {
                                try {
                                    translate(shift.x, shift.y) { drawLayer(wallpaper.frosted) }
                                } catch (_: Throwable) {}
                            }
                        }
                        drawRect(if (Build.VERSION.SDK_INT < 31) tint.copy(alpha = .85f) else tint)
                        drawRect(spec)
                    }
                    drawPath(outline, rim, style = Stroke((1.25.dp * (0.8f + edge * 0.4f)).toPx()))
                } catch (_: Throwable) {
                    drawRect(tint)
                }
            }
        }
}

private class GlassPanelPlace {
    var coords: LayoutCoordinates? = null
    var origin by mutableStateOf(Offset.Zero)
}

/**
 * Universal container for floating dialogs, bottom sheets, and popups that provides
 * an authentic physical liquid glass backdrop. Captures the screen behind the dialog
 * (via PetalContentSnapshot or procedural pattern) and supplies LocalPetalGlassWallpaper
 * so petalGlassPanel activates its genuine AGSL lens refraction and dynamic glow rims.
 */
@Composable
fun PetalGlassContainer(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    live: Boolean = false,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val wallpaper = rememberPetalGlassWallpaper()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Root view of the app window behind this popup/card
    val hostSource = LocalPetalGlassSource.current
    val rootView = remember(context, hostSource) {
        if (hostSource != null) return@remember hostSource
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.let {
            it.findViewById<android.view.View>(com.petal.browser.R.id.main)
                ?: it.findViewById<android.view.View>(android.R.id.content)
        }
    }
    val frameTick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled, live, rootView) {
        if (enabled && live && rootView != null) {
            while (true) {
                withFrameNanos { frameTick.longValue = it }
            }
        }
    }
    val snapshot = remember {
        val fresh = try {
            if (rootView != null && rootView.width > 0 && rootView.height > 0)
                com.petal.browser.predictive.PetalContentSnapshot.capture(rootView) else null
        } catch (_: Throwable) { null }
        fresh ?: com.petal.browser.predictive.PetalContentSnapshot.current
    }
    var screenPos by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }

    CompositionLocalProvider(LocalPetalGlassWallpaper provides wallpaper) {
        Box(
            modifier.onGloballyPositioned {
                val pos = it.positionOnScreen()
                screenPos = androidx.compose.ui.unit.IntOffset(
                    kotlin.math.round(pos.x).toInt(), kotlin.math.round(pos.y).toInt()
                )
            }
        ) {
            if (live && rootView != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .liveGlassWallpaper(wallpaper, rootView) { frameTick.longValue }
                )
            } else if (snapshot != null && !snapshot.isRecycled) {
                // Full-screen-sized backdrop placed so it lines up with the real screen
                // behind the popup; recorded only, never drawn.
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .layout { measurable, _ ->
                                val placeable = measurable.measure(
                                    androidx.compose.ui.unit.Constraints.fixed(snapshot.width, snapshot.height)
                                )
                                val rootLoc = IntArray(2)
                                rootView?.getLocationOnScreen(rootLoc)
                                layout(0, 0) {
                                    placeable.place(-(screenPos.x - rootLoc[0]), -(screenPos.y - rootLoc[1]))
                                }
                            }
                            .recordGlassWallpaper(wallpaper)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = snapshot.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.matchParentSize().recordGlassWallpaper(wallpaper)) {
                    PetalGlassPattern(Modifier.fillMaxSize())
                }
            }
            content()
        }
    }
}
