/*
 * PetalSplashScreen.kt
 * ──────────────────────────────────────────────────────────────────────────
 * High-performance, pure procedural code launch animation for Petal Browser.
 * Inspired by Dolphy's particle stream, adapted into a Liquid Glass &
 * Material 3 Expressive "Quantum Web Stream" with falling web & privacy glyphs,
 * 12-sided cookie rotation, radial bloom, and dynamic theme palette sync.
 */
package com.petal.browser.compose.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class PetalWebGlyph(
    val iconIndex: Int,
    val xFraction: Float,
    val startFraction: Float,
    val speed: Float,
    val sizeDp: Int,
    val alpha: Float
)

// Curated web, browsing & privacy icon set for Petal
private val webGlyphIcons = listOf(
    Icons.Rounded.Language,
    Icons.Rounded.Shield,
    Icons.Rounded.Lock,
    Icons.Rounded.AutoAwesome,
    Icons.Rounded.Bolt,
    Icons.Rounded.Tab,
    Icons.Rounded.Spa,
    Icons.Rounded.Public,
    Icons.Rounded.TravelExplore,
    Icons.Rounded.VpnLock,
    Icons.Rounded.Security,
    Icons.Rounded.Cookie,
    Icons.Rounded.Terminal,
    Icons.Rounded.Bookmarks
)

@Composable
fun PetalSplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 2000L
) {
    LaunchedEffect(Unit) {
        delay(durationMs)
        onSplashComplete()
    }

    PetalSplashContent(modifier = modifier)
}

@Composable
fun PetalWebGlyphField(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    glyphCount: Int = 110,
    columns: Int = 14
) {
    val painters = webGlyphIcons.map { rememberVectorPainter(it) }

    val infiniteTransition = rememberInfiniteTransition(label = "petal_glyphs_loop")
    val progressState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glyphs_progress"
    )

    val glyphs = remember(glyphCount, columns) {
        val random = Random(1337)
        List(glyphCount) { index ->
            val column = index % columns
            PetalWebGlyph(
                iconIndex = random.nextInt(webGlyphIcons.size),
                xFraction = ((column + 0.5f) / columns.toFloat()) + random.nextFloat() * 0.04f - 0.02f,
                startFraction = random.nextFloat() * 1.35f,
                speed = 0.28f + random.nextFloat() * 0.55f,
                sizeDp = 12 + random.nextInt(12),
                alpha = 0.08f + random.nextFloat() * 0.32f
            )
        }
    }

    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val progress = progressState.value
        val widthPx = size.width
        val heightPx = size.height
        if (widthPx <= 0f || heightPx <= 0f) return@Canvas

        for (i in 0 until glyphs.size) {
            val glyph = glyphs[i]
            val x = widthPx * glyph.xFraction
            val yProgress = (glyph.startFraction + progress * glyph.speed) % 1.35f
            val y = heightPx * yProgress - heightPx * 0.15f
            val sizePx = with(density) { glyph.sizeDp.dp.toPx() }
            val painter = painters[glyph.iconIndex]

            translate(left = x, top = y) {
                with(painter) {
                    draw(
                        size = Size(sizePx, sizePx),
                        alpha = glyph.alpha,
                        colorFilter = ColorFilter.tint(accentColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun PetalSplashContent(
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Subtle scale-up entrance animation
    val contentScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "splash_scale"
    )

    // Ambient 360 degree rotation for the cookie emblem
    val infiniteTransition = rememberInfiniteTransition(label = "petal_emblem_rotation")
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "emblem_rotation"
    )

    // Breathing pulse for ambient glow
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emblem_glow"
    )

    val backgroundTop = Color(0xFF040609)
    val backgroundMid = accentColor.copy(alpha = 0.08f)
    val backgroundBottom = Color(0xFF020304)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(backgroundTop, backgroundMid, backgroundBottom)
                )
            )
    ) {
        // Procedural Web Glyph Particle Stream
        PetalWebGlyphField(
            modifier = Modifier.fillMaxSize(),
            accentColor = accentColor
        )

        // Radial ambient backdrop glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.18f * pulseGlow),
                            Color.Transparent
                        ),
                        radius = 700f
                    )
                )
        )

        // Center Brand Emblem & Typography
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Rotating 12-sided scalloped cookie container with central lotus / spa icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer { rotationZ = rotationDegrees }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            ) {
                // Counter-rotate the inner icon so it stays upright
                Box(
                    modifier = Modifier.graphicsLayer { rotationZ = -rotationDegrees }
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.Spa,
                        contentDescription = "Petal",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Glowing Brand Headline
            Text(
                text = "Petal",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = accentColor.copy(alpha = 0.85f * pulseGlow),
                        offset = Offset(0f, 4f),
                        blurRadius = 32f
                    )
                )
            )

            Spacer(Modifier.height(6.dp))

            // Subtitle Tagline
            Text(
                text = "Personal Window to the Web",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
