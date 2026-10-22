/*
 * PetalSplashScreen.kt
 * ──────────────────────────────────────────────────────────────────────────
 * High-performance, pure procedural code launch animation for Petal Browser.
 * Inspired by Dolphy's particle stream and Material You background theming:
 * - One-time launch per app process/cold-start.
 * - Dynamic Material You background gradient & radial glow that harmonizes
 *   with the system/app MaterialTheme colorScheme (Light / Dark / AMOLED).
 * - Procedural web, privacy & security glyph stream with glowing headline.
 * - Clean brand typography without any app icon.
 */
package com.petal.browser.compose.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

object PetalSplashState {
    /**
     * In-memory process flag ensuring the splash animation plays only once
     * per cold-start / app process life cycle.
     */
    @Volatile
    var hasShownThisSession: Boolean = false
}

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
    durationMs: Long = 1900L
) {
    LaunchedEffect(Unit) {
        delay(durationMs)
        PetalSplashState.hasShownThisSession = true
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
                alpha = 0.10f + random.nextFloat() * 0.35f
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
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.primary
    val backgroundColor = colorScheme.background
    val isDark = isSystemInDarkTheme() || backgroundColor == Color.Black || (colorScheme.surface.red + colorScheme.surface.green + colorScheme.surface.blue) < 1.5f

    // Subtle scale-up and fade-in entrance animation
    val contentScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "splash_scale"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "splash_title_alpha"
    )

    // Breathing pulse for ambient glow
    val infiniteTransition = rememberInfiniteTransition(label = "petal_ambient_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emblem_glow"
    )

    // Material You background gradient inspired by Dolphy & M3 Dynamic Color
    val backgroundTop = if (isDark) {
        if (backgroundColor == Color.Black) Color.Black else backgroundColor
    } else {
        colorScheme.surface
    }
    val backgroundMid = if (isDark) {
        accentColor.copy(alpha = 0.12f)
    } else {
        accentColor.copy(alpha = 0.08f)
    }
    val backgroundBottom = if (isDark) {
        if (backgroundColor == Color.Black) Color.Black else colorScheme.surfaceContainerLowest
    } else {
        colorScheme.surfaceContainerLow
    }

    val textColor = if (isDark) Color.White else colorScheme.onBackground
    val subtitleColor = if (isDark) Color.White.copy(alpha = 0.72f) else colorScheme.onSurfaceVariant

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

        // Radial ambient backdrop glow using Material You primary color
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isDark) 0.18f * pulseGlow else 0.10f * pulseGlow),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )

        // Center Brand Typography without any app icon
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    alpha = titleAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Brand Headline
            Text(
                text = "Petal",
                color = textColor,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = accentColor.copy(alpha = if (isDark) 0.85f * pulseGlow else 0.40f * pulseGlow),
                        offset = Offset(0f, 4f),
                        blurRadius = 32f
                    )
                )
            )

            Spacer(Modifier.height(8.dp))

            // Subtitle Tagline
            Text(
                text = "Personal Window to the Web",
                color = subtitleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
