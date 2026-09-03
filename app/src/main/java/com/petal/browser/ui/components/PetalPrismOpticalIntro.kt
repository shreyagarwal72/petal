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
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Prism & Optical Beam Intro Overlay for Petal Browser.
 *
 * Timing: ~950ms total sequence, giving ample time to fully display after
 * the OS/Activity splash screen dismisses without being cut off.
 *
 * Visual Sequence:
 * 1. Initial 80ms buffer allowing system splash window to fully transition away.
 * 2. An isometric crystalline triangular prism rotates and forms in the center with 3D facet reflections (100ms..400ms).
 * 3. An intense optical coherent laser sweeps in from the left, striking the apex facet with an impact spark (250ms..550ms).
 * 4. Chromatic refraction splits the beam into multi-spectral petal waves (Cyan, Violet, Emerald, Amber, Rose) (450ms..750ms).
 * 5. Micro-haptic tactile feedback fires as the refracted beams bloom into the 3D Petal emblem (~550ms).
 * 6. Dynamic seamless spatial expansion blends the energy rays outward into the M3 Expressive home layout (750ms..950ms).
 */
@Composable
fun PetalPrismOpticalIntro(
    modifier: Modifier = Modifier,
    onIntroFinished: () -> Unit = {}
) {
    if (PetalIntroAnimationTracker.hasIntroCompleted) {
        return
    }

    val context = LocalContext.current
    val animProgress = remember { Animatable(0f) }
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Trigger haptic at laser-prism impact point (~360ms in)
        launch {
            delay(360L)
            try {
                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.85f)
            } catch (_: Throwable) {}
        }

        // Secondary subtle bloom settle haptic (~680ms)
        launch {
            delay(680L)
            try {
                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.5f)
            } catch (_: Throwable) {}
        }

        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 950,
                easing = CubicBezierEasing(0.18f, 0.0f, 0.12f, 1.0f)
            )
        )

        PetalIntroAnimationTracker.hasIntroCompleted = true
        isVisible = false
        onIntroFinished()
    }

    if (!isVisible) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = animProgress.value
            val width = size.width
            val height = size.height
            val cx = width / 2f
            val cy = height / 2f

            // Dissolve/fade out towards completion (from 0.75f to 1.0f)
            val overallAlpha = if (progress < 0.75f) {
                1f
            } else {
                (1f - ((progress - 0.75f) / 0.25f)).coerceIn(0f, 1f)
            }

            if (overallAlpha <= 0.001f) return@Canvas

            // ── 0. Backdrop Ambient Darkness & Radial Glow ──
            val bgDarkenAlpha = ((1f - progress * 1.05f).coerceIn(0f, 0.90f)) * overallAlpha
            if (bgDarkenAlpha > 0f) {
                drawRect(
                    color = surfaceColor.copy(alpha = bgDarkenAlpha)
                )
            }

            // Radial background flare matching active theme
            val flareRadius = (width.coerceAtLeast(height) * 0.75f) * (0.35f + progress * 0.65f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f * overallAlpha),
                        tertiaryColor.copy(alpha = 0.12f * overallAlpha),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = flareRadius
                ),
                radius = flareRadius,
                center = Offset(cx, cy)
            )

            // ── 1. 3D Isometric Prism Geometry ──
            val prismScale = when {
                progress < 0.28f -> (progress / 0.28f) * 1.05f
                progress < 0.48f -> 1.05f - ((progress - 0.28f) / 0.20f) * 0.05f
                else -> 1f + (progress - 0.48f) * 2.8f
            }

            val prismRotation = -15f + (progress * 35f) // 3D yaw/roll angle
            val prismBaseRadius = 46.dp.toPx() * prismScale

            // 3D Isometric Prism Vertices
            val apexAngle = -PI.toFloat() / 2f
            val leftAngle = apexAngle + (2f * PI.toFloat() / 3f)
            val rightAngle = apexAngle + (4f * PI.toFloat() / 3f)

            // 3D perspective depth offset
            val depthZ = 22.dp.toPx() * (1f - (progress - 0.42f).coerceAtLeast(0f) * 2f).coerceAtLeast(0f)

            val pApex = Offset(cx + prismBaseRadius * cos(apexAngle), cy + prismBaseRadius * sin(apexAngle) - depthZ)
            val pLeft = Offset(cx + prismBaseRadius * cos(leftAngle), cy + prismBaseRadius * sin(leftAngle))
            val pRight = Offset(cx + prismBaseRadius * cos(rightAngle), cy + prismBaseRadius * sin(rightAngle))
            val pCenter = Offset(cx, cy + depthZ * 0.4f)

            // Draw 3D Prism Faces with Glassmorphic Translucency & Specular Sheen
            rotate(degrees = prismRotation, pivot = Offset(cx, cy)) {
                // Face A: Left Facet (Light refraction side)
                val pathFacetLeft = Path().apply {
                    moveTo(pApex.x, pApex.y)
                    lineTo(pLeft.x, pLeft.y)
                    lineTo(pCenter.x, pCenter.y)
                    close()
                }
                drawPath(
                    path = pathFacetLeft,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f * overallAlpha),
                            primaryColor.copy(alpha = 0.35f * overallAlpha),
                            Color.Transparent
                        ),
                        start = pApex,
                        end = pLeft
                    )
                )

                // Face B: Right Facet (Specular reflection side)
                val pathFacetRight = Path().apply {
                    moveTo(pApex.x, pApex.y)
                    lineTo(pRight.x, pRight.y)
                    lineTo(pCenter.x, pCenter.y)
                    close()
                }
                drawPath(
                    path = pathFacetRight,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f * overallAlpha),
                            tertiaryColor.copy(alpha = 0.40f * overallAlpha),
                            Color.White.copy(alpha = 0.15f * overallAlpha)
                        ),
                        start = pApex,
                        end = pRight
                    )
                )

                // Face C: Bottom Base Facet
                val pathFacetBottom = Path().apply {
                    moveTo(pLeft.x, pLeft.y)
                    lineTo(pRight.x, pRight.y)
                    lineTo(pCenter.x, pCenter.y)
                    close()
                }
                drawPath(
                    path = pathFacetBottom,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.30f * overallAlpha),
                            tertiaryColor.copy(alpha = 0.20f * overallAlpha)
                        ),
                        start = pLeft,
                        end = pRight
                    )
                )

                // High-precision glass crystalline edges
                val prismStrokeWidth = 2.dp.toPx()
                val edgeColor = Color.White.copy(alpha = 0.85f * overallAlpha)
                drawLine(edgeColor, pApex, pLeft, strokeWidth = prismStrokeWidth)
                drawLine(edgeColor, pApex, pRight, strokeWidth = prismStrokeWidth)
                drawLine(edgeColor, pLeft, pRight, strokeWidth = prismStrokeWidth)
                drawLine(Color.White.copy(alpha = 0.5f * overallAlpha), pApex, pCenter, strokeWidth = 1.2.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.4f * overallAlpha), pLeft, pCenter, strokeWidth = 1.2.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.4f * overallAlpha), pRight, pCenter, strokeWidth = 1.2.dp.toPx())
            }

            // ── 2. Incoming Optical Coherent Beam (0.08f to 0.52f) ──
            if (progress in 0.08f..0.52f) {
                val beamProgress = ((progress - 0.08f) / 0.38f).coerceIn(0f, 1f)
                val beamStartX = -60.dp.toPx()
                val beamTargetX = cx - 12.dp.toPx()
                val currentBeamHeadX = beamStartX + (beamTargetX - beamStartX) * FastOutSlowInEasing.transform(beamProgress)
                val beamY = cy - 8.dp.toPx()

                // Core White High-Energy Laser
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.95f * overallAlpha),
                            Color.White.copy(alpha = 1.0f * overallAlpha)
                        ),
                        start = Offset(beamStartX, beamY),
                        end = Offset(currentBeamHeadX, beamY)
                    ),
                    start = Offset(beamStartX, beamY),
                    end = Offset(currentBeamHeadX, beamY),
                    strokeWidth = 3.5.dp.toPx()
                )

                // Laser Outer Luminescent Aura
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.6f * overallAlpha),
                            Color.White.copy(alpha = 0.8f * overallAlpha)
                        ),
                        start = Offset(beamStartX, beamY),
                        end = Offset(currentBeamHeadX, beamY)
                    ),
                    start = Offset(beamStartX, beamY),
                    end = Offset(currentBeamHeadX, beamY),
                    strokeWidth = 12.dp.toPx()
                )

                // Laser Strike Point Spark / Flare
                if (beamProgress > 0.65f) {
                    val flareIntensity = ((beamProgress - 0.65f) / 0.35f) * overallAlpha
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = flareIntensity),
                                primaryColor.copy(alpha = 0.8f * flareIntensity),
                                Color.Transparent
                            ),
                            center = Offset(currentBeamHeadX, beamY),
                            radius = 24.dp.toPx()
                        ),
                        radius = 24.dp.toPx(),
                        center = Offset(currentBeamHeadX, beamY)
                    )
                }
            }

            // ── 3. Chromatic Refracted Spectral Beams (0.24f to 0.78f) ──
            if (progress >= 0.24f) {
                val refractProgress = ((progress - 0.24f) / 0.48f).coerceIn(0f, 1f)
                val refractEase = FastOutSlowInEasing.transform(refractProgress)

                // Spectral ray wavelengths: Cyan, Violet, Emerald, Amber, Rose
                val spectralColors = listOf(
                    Color(0xFF00E5FF) to -38f, // Cyan / Speed
                    Color(0xFF7C4DFF) to -18f, // Violet / Privacy
                    Color(0xFF00E676) to 0f,   // Emerald / Performance
                    Color(0xFFFFB300) to 20f,  // Amber / Native Engine
                    Color(0xFFFF4081) to 40f   // Rose / Modern UI
                )

                val maxBeamLength = (width * 0.75f) * refractEase
                val strikePoint = Offset(cx, cy)

                spectralColors.forEachIndexed { _, (spectralColor, angleDeg) ->
                    val rad = Math.toRadians((angleDeg + prismRotation * 0.4f).toDouble()).toFloat()
                    val dirX = cos(rad)
                    val dirY = sin(rad)
                    val endPt = Offset(strikePoint.x + dirX * maxBeamLength, strikePoint.y + dirY * maxBeamLength)

                    // Refracted fan path
                    val rayPath = Path().apply {
                        moveTo(strikePoint.x, strikePoint.y)
                        val spread = 12.dp.toPx() * refractEase
                        val perpX = -dirY * spread
                        val perpY = dirX * spread
                        lineTo(endPt.x - perpX, endPt.y - perpY)
                        lineTo(endPt.x + perpX, endPt.y + perpY)
                        close()
                    }

                    drawPath(
                        path = rayPath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f * overallAlpha),
                                spectralColor.copy(alpha = 0.65f * overallAlpha),
                                spectralColor.copy(alpha = 0.25f * overallAlpha),
                                Color.Transparent
                            ),
                            start = strikePoint,
                            end = endPt
                        ),
                        blendMode = BlendMode.Plus
                    )
                }
            }

            // ── 4. 3D Petal Emblem Unfold & Bloom (0.38f to 1.0f) ──
            if (progress >= 0.38f) {
                val bloomProgress = ((progress - 0.38f) / 0.42f).coerceIn(0f, 1f)
                val bloomEase = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f).transform(bloomProgress)
                val petalRadius = 36.dp.toPx() * bloomEase
                val petalCount = 5

                rotate(degrees = progress * 72f, pivot = Offset(cx, cy)) {
                    for (i in 0 until petalCount) {
                        val petalAngle = (i * (360f / petalCount)) * (PI.toFloat() / 180f)
                        val petalDistance = 28.dp.toPx() * bloomEase
                        val petalCenterX = cx + petalDistance * cos(petalAngle)
                        val petalCenterY = cy + petalDistance * sin(petalAngle)

                        // Draw individual expressive petal leaf
                        scale(scale = bloomEase, pivot = Offset(petalCenterX, petalCenterY)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.85f * overallAlpha),
                                        primaryColor.copy(alpha = 0.65f * overallAlpha),
                                        tertiaryColor.copy(alpha = 0.20f * overallAlpha),
                                        Color.Transparent
                                    ),
                                    center = Offset(petalCenterX, petalCenterY),
                                    radius = petalRadius
                                ),
                                radius = petalRadius,
                                center = Offset(petalCenterX, petalCenterY)
                            )
                        }
                    }
                }

                // Core luminous jewel center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f * overallAlpha),
                            primaryColor.copy(alpha = 0.8f * overallAlpha),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = 20.dp.toPx() * bloomEase
                    ),
                    radius = 20.dp.toPx() * bloomEase,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}
