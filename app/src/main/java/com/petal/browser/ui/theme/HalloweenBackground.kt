/*
 * Copyright 2026 Morphe & Petal Browser.
 * https://github.com/MorpheApp/morphe-manager
 */

package com.petal.browser.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.petal.browser.ui.glass.PetalGlassWallpaper
import com.petal.browser.ui.glass.captureGlassWallpaper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Every motion repeats a whole number of times per cycle, so the wrap of the clock is seamless
private const val HALLOWEEN_CYCLE_MS = 120000f
private const val ANIMATED_TIME_WRAP_MS = 3_600_000f
private const val TILT_TARGET_THRESHOLD = 0.02f

/**
 * Halloween night: bats flap across a moonlit sky while jack-o'-lanterns bob along the bottom.
 * Official Halloween background ported from MorpheApp with Petal Glass wallpaper capture integration.
 */
@Composable
fun HalloweenBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    glassWallpaper: PetalGlassWallpaper? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val batColor = if (isDark) Color(0xFF9C7FC4) else Color(0xFF3B2A4F)
    val moonColor = if (isDark) Color(0xFFFFCC80) else Color(0xFFFFA726)
    val moonAlpha = if (isDark) 0.5f else 0.4f
    val lanternAlpha = if (isDark) 0.35f else 0.3f
    val context = LocalContext.current

    val parallaxState = rememberHalloweenParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.2f,
        context = context
    )

    // Sorted far to close once, so nearer bats pass in front without a sort every frame
    val bats = remember {
        List(12) {
            val depth = Random.nextFloat()
            BatData(
                startProgress = Random.nextFloat(),
                // Far bats cross slower, which is most of what sells the depth
                crossings = 6 + (depth * 6).toInt(),
                leftward = Random.nextBoolean(),
                baseY = 0.08f + Random.nextFloat() * 0.6f,
                bobs = 20 + Random.nextInt(20),
                bobAmplitude = 0.01f + Random.nextFloat() * 0.02f,
                bobPhase = Random.nextFloat() * 2f * PI.toFloat(),
                flaps = 420 + Random.nextInt(180),
                flapPhase = Random.nextFloat() * 2f * PI.toFloat(),
                depth = depth
            )
        }.sortedBy { it.depth }
    }

    val lanterns = remember {
        listOf(0.12f, 0.38f, 0.64f, 0.88f).map { x ->
            LanternData(
                x = x + (Random.nextFloat() - 0.5f) * 0.08f,
                bobs = 24 + Random.nextInt(12),
                bobPhase = Random.nextFloat() * 2f * PI.toFloat(),
                flickers = 600 + Random.nextInt(300),
                depth = 0.4f + Random.nextFloat() * 0.6f
            )
        }
    }

    // One path rewound for every bat and one for every carved face, rather than a fresh allocation per frame
    val batPath = remember { Path() }
    val facePath = remember { Path() }

    val animatedTime = rememberHalloweenAnimatedTime(speedMultiplier)

    val canvasModifier = if (glassWallpaper != null) {
        modifier.captureGlassWallpaper(glassWallpaper).fillMaxSize()
    } else {
        modifier.fillMaxSize()
    }

    Canvas(modifier = canvasModifier) {
        val width = size.width
        val height = size.height
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val cycle = (animatedTime.value % HALLOWEEN_CYCLE_MS) / HALLOWEEN_CYCLE_MS

        // Moon, held almost still so it reads as far behind everything else
        val moonRadius = min(width, height) * 0.09f
        val moonCenter = Offset(width * 0.78f + tiltX * 8f, height * 0.14f + tiltY * 8f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(moonColor.copy(alpha = moonAlpha * 0.45f), Color.Transparent),
                center = moonCenter,
                radius = moonRadius * 3.5f
            ),
            radius = moonRadius * 3.5f,
            center = moonCenter
        )
        drawCircle(moonColor.copy(alpha = moonAlpha), moonRadius, moonCenter)
        drawCircle(
            color = Color.Black.copy(alpha = 0.06f),
            radius = moonRadius * 0.22f,
            center = moonCenter + Offset(-moonRadius * 0.3f, -moonRadius * 0.2f)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.05f),
            radius = moonRadius * 0.15f,
            center = moonCenter + Offset(moonRadius * 0.35f, moonRadius * 0.3f)
        )

        bats.forEach { bat ->
            val span = (24f + bat.depth * 36f) * density

            // Travels a wingspan past each edge, so a bat enters and leaves fully out of view
            val progress = (bat.startProgress + cycle * bat.crossings) % 1f
            val travel = progress * (width + span * 2f) - span
            val bob = sin(2f * PI.toFloat() * cycle * bat.bobs + bat.bobPhase) * bat.bobAmplitude * height
            val parallaxStrength = bat.depth * 40f

            val x = (if (bat.leftward) width - travel else travel) + tiltX * parallaxStrength
            val y = bat.baseY * height + bob + tiltY * parallaxStrength
            if (y < -span || y > height + span) return@forEach

            val flap = sin(2f * PI.toFloat() * cycle * bat.flaps + bat.flapPhase)
            val alpha = (0.3f + bat.depth * 0.5f)
            drawBat(batPath, Offset(x, y), span, flap, batColor.copy(alpha = alpha))
        }

        lanterns.forEach { lantern ->
            val radius = (20f + lantern.depth * 14f) * density
            val bob = sin(2f * PI.toFloat() * cycle * lantern.bobs + lantern.bobPhase) * radius * 0.25f
            val parallaxStrength = lantern.depth * 30f
            val center = Offset(
                lantern.x * width + tiltX * parallaxStrength,
                height - LanternGround.toPx() - radius * 1.4f + bob + tiltY * parallaxStrength
            )
            val flicker = 0.8f + 0.2f * sin(2f * PI.toFloat() * cycle * lantern.flickers)
            drawLantern(facePath, center, radius, lanternAlpha, flicker)
        }
    }
}

/**
 * A bat silhouette centered on [center], [s] wide from wingtip to wingtip. [flap] runs from -1
 * to 1 and swings the wingtips down and up around the body.
 */
private fun DrawScope.drawBat(path: Path, center: Offset, s: Float, flap: Float, color: Color) {
    val tipY = -flap * s * 0.3f
    path.rewind()
    path.moveTo(0f, -s * 0.08f)
    // Right wing: leading edge out to the tip, then a scalloped trailing edge back to the body
    path.lineTo(s * 0.1f, -s * 0.05f)
    path.quadraticTo(s * 0.3f, -s * 0.12f + tipY * 0.5f, s * 0.5f, tipY)
    path.quadraticTo(s * 0.42f, tipY * 0.4f + s * 0.02f, s * 0.34f, s * 0.06f + tipY * 0.3f)
    path.quadraticTo(s * 0.26f, tipY * 0.2f, s * 0.18f, s * 0.09f)
    path.quadraticTo(s * 0.1f, s * 0.04f, 0f, s * 0.12f)
    // Left wing, mirrored
    path.quadraticTo(-s * 0.1f, s * 0.04f, -s * 0.18f, s * 0.09f)
    path.quadraticTo(-s * 0.26f, tipY * 0.2f, -s * 0.34f, s * 0.06f + tipY * 0.3f)
    path.quadraticTo(-s * 0.42f, tipY * 0.4f + s * 0.02f, -s * 0.5f, tipY)
    path.quadraticTo(-s * 0.3f, -s * 0.12f + tipY * 0.5f, -s * 0.1f, -s * 0.05f)
    path.close()
    // Ears
    path.moveTo(-s * 0.06f, -s * 0.06f)
    path.lineTo(-s * 0.05f, -s * 0.15f)
    path.lineTo(-s * 0.01f, -s * 0.08f)
    path.close()
    path.moveTo(s * 0.06f, -s * 0.06f)
    path.lineTo(s * 0.05f, -s * 0.15f)
    path.lineTo(s * 0.01f, -s * 0.08f)
    path.close()
    path.translate(center)
    drawPath(path, color)
}

/**
 * A jack-o'-lantern of radius [r] centered on [center]. [glow] scales the light behind the carved
 * face, which flickers around 1 and flares above it.
 */
private fun DrawScope.drawLantern(face: Path, center: Offset, r: Float, alpha: Float, glow: Float) {
    val faceAlpha = (alpha * glow).coerceIn(0f, 1f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(LanternGlow.copy(alpha = (0.22f * glow).coerceIn(0f, 1f)), Color.Transparent),
            center = center,
            radius = r * 2.6f
        ),
        radius = r * 2.6f,
        center = center
    )

    // Side ribs first, the front one over them
    val ribSize = Size(r * 1.3f, r * 1.6f)
    drawOval(
        PumpkinShade.copy(alpha = alpha),
        topLeft = Offset(center.x - r * 0.45f - ribSize.width / 2f, center.y - ribSize.height / 2f),
        size = ribSize
    )
    drawOval(
        PumpkinShade.copy(alpha = alpha),
        topLeft = Offset(center.x + r * 0.45f - ribSize.width / 2f, center.y - ribSize.height / 2f),
        size = ribSize
    )
    drawOval(
        Pumpkin.copy(alpha = alpha),
        topLeft = Offset(center.x - r * 0.6f, center.y - r * 0.8f),
        size = Size(r * 1.2f, r * 1.6f)
    )
    drawRect(
        PumpkinStem.copy(alpha = alpha),
        topLeft = Offset(center.x - r * 0.08f, center.y - r * 1.0f),
        size = Size(r * 0.16f, r * 0.3f)
    )

    face.rewind()
    face.apply {
        // Eyes
        moveTo(center.x - r * 0.42f, center.y - r * 0.05f)
        lineTo(center.x - r * 0.26f, center.y - r * 0.38f)
        lineTo(center.x - r * 0.1f, center.y - r * 0.05f)
        close()
        moveTo(center.x + r * 0.1f, center.y - r * 0.05f)
        lineTo(center.x + r * 0.26f, center.y - r * 0.38f)
        lineTo(center.x + r * 0.42f, center.y - r * 0.05f)
        close()
        // Toothy grin
        moveTo(center.x - r * 0.5f, center.y + r * 0.15f)
        lineTo(center.x - r * 0.25f, center.y + r * 0.28f)
        lineTo(center.x - r * 0.12f, center.y + r * 0.18f)
        lineTo(center.x, center.y + r * 0.3f)
        lineTo(center.x + r * 0.12f, center.y + r * 0.18f)
        lineTo(center.x + r * 0.25f, center.y + r * 0.28f)
        lineTo(center.x + r * 0.5f, center.y + r * 0.15f)
        lineTo(center.x + r * 0.3f, center.y + r * 0.5f)
        lineTo(center.x - r * 0.3f, center.y + r * 0.5f)
        close()
    }
    drawPath(face, LanternGlow.copy(alpha = faceAlpha))
}

/** How far above the screen's bottom the lanterns stand, clear of the navigation floating there. */
private val LanternGround = 110.dp

private val Pumpkin = Color(0xFFF57C00)
private val PumpkinShade = Color(0xFFE65100)
private val PumpkinStem = Color(0xFF5D4037)
private val LanternGlow = Color(0xFFFFD54F)

private data class BatData(
    val startProgress: Float,
    val crossings: Int,
    val leftward: Boolean,
    val baseY: Float,
    val bobs: Int,
    val bobAmplitude: Float,
    val bobPhase: Float,
    val flaps: Int,
    val flapPhase: Float,
    val depth: Float
)

private data class LanternData(
    val x: Float,
    val bobs: Int,
    val bobPhase: Float,
    val flickers: Int,
    val depth: Float
)

data class HalloweenParallaxState(
    val tiltX: State<Float>,
    val tiltY: State<Float>
)

@Composable
fun rememberHalloweenParallaxState(
    enableParallax: Boolean,
    sensitivity: Float = 0.3f,
    context: Context
): HalloweenParallaxState {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }
    val tiltTarget = remember { MutableStateFlow(Offset.Zero) }

    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            tiltTarget.value = Offset.Zero
            smoothTiltX.snapTo(0f)
            smoothTiltY.snapTo(0f)
            return@LaunchedEffect
        }

        tiltTarget.collectLatest { target ->
            coroutineScope {
                launch {
                    smoothTiltX.animateTo(
                        targetValue = target.x,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                launch {
                    smoothTiltY.animateTo(
                        targetValue = target.y,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(enableParallax, lifecycleOwner) {
        if (!enableParallax) return@LaunchedEffect

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@LaunchedEffect
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var baselineX = 0f
            var baselineY = 0f
            var isCalibrated = false

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!isCalibrated) {
                        baselineX = event.values[0]
                        baselineY = event.values[1]
                        isCalibrated = true
                    }

                    val rawTiltX = event.values[0] - baselineX
                    val rawTiltY = -(event.values[1] - baselineY)
                    val target = Offset(rawTiltX * sensitivity, rawTiltY * sensitivity)

                    if ((target - tiltTarget.value).getDistance() > TILT_TARGET_THRESHOLD) {
                        tiltTarget.value = target
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
            try {
                awaitCancellation()
            } finally {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return HalloweenParallaxState(
        tiltX = smoothTiltX.asState(),
        tiltY = smoothTiltY.asState()
    )
}

@Composable
fun rememberHalloweenAnimatedTime(speedMultiplier: Float = 1f): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, speedMultiplier) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastFrameMs = withInfiniteAnimationFrameMillis { it }
            while (isActive) {
                withInfiniteAnimationFrameMillis { frameMs ->
                    val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                    lastFrameMs = frameMs
                    time.floatValue = (time.floatValue + delta * speedMultiplier) % ANIMATED_TIME_WRAP_MS
                }
            }
        }
    }
    return time
}
