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
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Controller singleton that tracks if cold-start intro animation has executed during
 * the current application process lifetime.
 */
object PetalIntroAnimationTracker {
    @Volatile
    var hasIntroCompleted: Boolean = false

    fun reset() {
        hasIntroCompleted = false
    }
}

/**
 * Available intro animation styles for cold start:
 * - NONE: Off by default. Instant entrance with zero animation overlay.
 * - PRISM_BEAM: 3D isometric crystal glass prism and optical coherent laser refraction.
 * - BLOOMING_PETAL: Organic 3D blooming flower unfolding from glowing seed with ripples.
 */
enum class PetalIntroStyle(val prefValue: String, val title: String, val description: String) {
    NONE("NONE", "Off (Default)", "Instant home screen display with zero startup animation"),
    PRISM_BEAM("PRISM_BEAM", "3D Prism & Optical Beam", "Refractive 3D glass prism struck by laser with chromatic spectral waves"),
    BLOOMING_PETAL("BLOOMING_PETAL", "The Blooming Petal", "Fluid organic blossom unfolding into 5 glassmorphic petals with haptic ripples");

    companion object {
        fun fromPref(value: String?): PetalIntroStyle {
            if (value == null) return NONE
            return values().firstOrNull { it.prefValue.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}

/**
 * Top-level Intro Animation Host.
 * Reads user preference ("sp_intro_animation_style", default "NONE") and executes
 * the selected 3D intro animation on cold start.
 */
@Composable
fun PetalIntroHost(
    modifier: Modifier = Modifier,
    onIntroFinished: () -> Unit = {}
) {
    if (PetalIntroAnimationTracker.hasIntroCompleted) {
        return
    }

    val context = LocalContext.current
    val sp = remember {
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
        } catch (_: Throwable) {
            null
        }
    }
    val styleKey = sp?.getString("sp_intro_animation_style", PetalIntroStyle.NONE.prefValue)
    val selectedStyle = PetalIntroStyle.fromPref(styleKey)

    if (selectedStyle == PetalIntroStyle.NONE) {
        PetalIntroAnimationTracker.hasIntroCompleted = true
        return
    }

    when (selectedStyle) {
        PetalIntroStyle.PRISM_BEAM -> {
            PetalPrismOpticalIntro(
                modifier = modifier,
                onIntroFinished = onIntroFinished
            )
        }
        PetalIntroStyle.BLOOMING_PETAL -> {
            PetalBloomingPetalIntro(
                modifier = modifier,
                onIntroFinished = onIntroFinished
            )
        }
        PetalIntroStyle.NONE -> Unit
    }
}

/**
 * 🌸 "The Blooming Petal" 3D Cold-Start Intro Animation.
 *
 * Sequence (~950ms total, giving full visibility after system splash screen drops):
 * 1. Initial grace period allowing system splash window to fully dismiss.
 * 2. Luminous core particle emerges and pulses at center (100ms..350ms).
 * 3. 5 organic glassmorphic 3D petals unfurl outward with spring rotation and expansion (300ms..750ms).
 * 4. Micro-haptic feedback clicks at full bloom expansion (~550ms).
 * 5. Concentric harmonic light ripples expand outward into the Material 3 Home UI (600ms..950ms).
 */
@Composable
fun PetalBloomingPetalIntro(
    modifier: Modifier = Modifier,
    onIntroFinished: () -> Unit = {}
) {
    if (PetalIntroAnimationTracker.hasIntroCompleted) return

    val context = LocalContext.current
    val animProgress = remember { Animatable(0f) }
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Subtle haptic as blooming begins (~350ms)
        launch {
            delay(350L)
            try {
                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.4f)
            } catch (_: Throwable) {}
        }

        // Full bloom click haptic (~550ms)
        launch {
            delay(550L)
            try {
                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.85f)
            } catch (_: Throwable) {}
        }

        try {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 950,
                    easing = FastOutSlowInEasing
                )
            )
        } catch (_: Throwable) {}

        PetalIntroAnimationTracker.hasIntroCompleted = true
        isVisible = false
        try {
            onIntroFinished()
        } catch (_: Throwable) {}
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
            if (width <= 0f || height <= 0f) return@Canvas

            val cx = width / 2f
            val cy = height / 2f

            // Dissolve smoothly towards end (0.75f .. 1.0f)
            val overallAlpha = if (progress < 0.75f) {
                1f
            } else {
                (1f - ((progress - 0.75f) / 0.25f)).coerceIn(0f, 1f)
            }

            if (overallAlpha <= 0.001f) return@Canvas

            // Ambient background curtain matching theme
            val bgDarken = (1f - progress * 1.05f).coerceIn(0f, 0.92f) * overallAlpha
            if (bgDarken > 0f) {
                drawRect(color = surfaceColor.copy(alpha = bgDarken))
            }

            // ── Phase 1: Concentric Harmonic Ripples (0.40f .. 1.0f) ──
            if (progress >= 0.40f) {
                val rippleProgress = ((progress - 0.40f) / 0.60f).coerceIn(0f, 1f)
                val maxRippleRadius = width.coerceAtLeast(height) * 0.85f

                for (r in 0..2) {
                    val phaseOffset = r * 0.18f
                    val ringProgress = (rippleProgress - phaseOffset).coerceIn(0f, 1f)
                    if (ringProgress > 0f) {
                        val ringRadius = (maxRippleRadius * ringProgress).coerceAtLeast(1f)
                        val ringAlpha = ((1f - ringProgress) * 0.35f * overallAlpha).coerceIn(0f, 1f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = ringAlpha),
                                    tertiaryColor.copy(alpha = ringAlpha * 0.4f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = ringRadius
                            ),
                            radius = ringRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }

            // ── Phase 2: 5 Organic 3D Petals Blooming (0.12f .. 0.85f) ──
            if (progress >= 0.12f) {
                val bloomProgress = ((progress - 0.12f) / 0.68f).coerceIn(0f, 1f)
                val bloomEase = FastOutSlowInEasing.transform(bloomProgress)

                val petalCount = 5
                val maxPetalDist = (42.dp.toPx() * bloomEase).coerceAtLeast(0f)
                val petalRadiusX = (26.dp.toPx() * bloomEase).coerceAtLeast(0.1f)
                val petalRadiusY = (46.dp.toPx() * bloomEase).coerceAtLeast(0.1f)
                val spinAngle = progress * 65f

                rotate(degrees = spinAngle, pivot = Offset(cx, cy)) {
                    for (i in 0 until petalCount) {
                        val angle = (i * (360f / petalCount)) * (PI.toFloat() / 180f)
                        val petalCx = cx + maxPetalDist * cos(angle)
                        val petalCy = cy + maxPetalDist * sin(angle)

                        val petalRotationDeg = (i * (360f / petalCount)) + 90f

                        rotate(degrees = petalRotationDeg, pivot = Offset(petalCx, petalCy)) {
                            // 3D Glassmorphic Petal Path
                            val petalPath = Path().apply {
                                moveTo(petalCx, petalCy - petalRadiusY)
                                cubicTo(
                                    petalCx + petalRadiusX, petalCy - petalRadiusY * 0.4f,
                                    petalCx + petalRadiusX, petalCy + petalRadiusY * 0.6f,
                                    petalCx, petalCy + petalRadiusY
                                )
                                cubicTo(
                                    petalCx - petalRadiusX, petalCy + petalRadiusY * 0.6f,
                                    petalCx - petalRadiusX, petalCy - petalRadiusY * 0.4f,
                                    petalCx, petalCy - petalRadiusY
                                )
                                close()
                            }

                            val gradRadius = (petalRadiusY * 1.1f).coerceAtLeast(1f)
                            // Gradient fill with specular light
                            val petalGrad = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.90f * overallAlpha),
                                    primaryColor.copy(alpha = 0.70f * overallAlpha),
                                    tertiaryColor.copy(alpha = 0.35f * overallAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(petalCx, petalCy),
                                radius = gradRadius
                            )
                            drawPath(path = petalPath, brush = petalGrad)

                            // Outer crystalline edge highlight
                            drawPath(
                                path = petalPath,
                                color = Color.White.copy(alpha = 0.65f * overallAlpha),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }

            // ── Phase 3: Luminous Golden / Jewel Core Seed (0.05f .. 0.85f) ──
            val coreScale = when {
                progress < 0.25f -> (progress / 0.25f) * 1.25f
                progress < 0.60f -> 1.25f - ((progress - 0.25f) / 0.35f) * 0.25f
                else -> 1f + (progress - 0.60f) * 1.5f
            }.coerceAtLeast(0f)
            val coreRadius = (24.dp.toPx() * coreScale).coerceAtLeast(1f)

            // Core radial sunburst
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f * overallAlpha),
                        Color(0xFFFFD54F).copy(alpha = 0.85f * overallAlpha),
                        primaryColor.copy(alpha = 0.5f * overallAlpha),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = (coreRadius * 1.4f).coerceAtLeast(1f)
                ),
                radius = (coreRadius * 1.4f).coerceAtLeast(1f),
                center = Offset(cx, cy)
            )

            // Inner sparkling nucleus
            drawCircle(
                color = Color.White.copy(alpha = 0.95f * overallAlpha),
                radius = (8.dp.toPx() * coreScale).coerceAtLeast(1f),
                center = Offset(cx, cy)
            )
        }
    }
}
