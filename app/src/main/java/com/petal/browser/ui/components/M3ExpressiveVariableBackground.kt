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

import android.graphics.Matrix
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.PetalMaterialShapes
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class M3ExpressiveShapeType {
    SCALLOP, FLOWER, STARBURST, CLOVER, ARCH, POLYGON
}

data class ExpressiveBackgroundBlob(
    val startPolygon: RoundedPolygon,
    val endPolygon: RoundedPolygon,
    val morph: Morph,
    val centerRelX: Float,
    val centerRelY: Float,
    val radiusDp: Float,
    val rotation: Float,
    val alpha: Float,
    val isPrimaryColor: Boolean,
    /** -1f or 1f — which direction this blob's slow rotation drift and breathing run in, so layers don't move in lockstep. */
    val driftSign: Float
)

/**
 * Generates an organic, vector-morphing set of Material 3 Expressive shapes from
 * all 35 official M3 shapes catalog.
 */
object M3ExpressiveBackgroundProvider {

    fun generateRandomBlobs(seedKey: String): List<ExpressiveBackgroundBlob> {
        val random = java.util.Random(seedKey.hashCode().toLong())
        val allHolders = PetalMaterialShapes.allShapes

        fun getRandomMorph(): Pair<RoundedPolygon, RoundedPolygon> {
            val start = allHolders[random.nextInt(allHolders.size)].polygon
            var end = allHolders[random.nextInt(allHolders.size)].polygon
            if (end == start && allHolders.size > 1) {
                end = allHolders[(random.nextInt(allHolders.size) + 1) % allHolders.size].polygon
            }
            return Pair(start, end)
        }

        val (s1, e1) = getRandomMorph()
        val (s2, e2) = getRandomMorph()
        val (s3, e3) = getRandomMorph()

        return listOf(
            ExpressiveBackgroundBlob(
                startPolygon = s1,
                endPolygon = e1,
                morph = Morph(s1, e1),
                centerRelX = 0.10f + random.nextFloat() * 0.20f,
                centerRelY = 0.06f + random.nextFloat() * 0.16f,
                radiusDp = 220f + random.nextFloat() * 110f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.18f + random.nextFloat() * 0.08f,
                isPrimaryColor = true,
                driftSign = 1f
            ),
            ExpressiveBackgroundBlob(
                startPolygon = s2,
                endPolygon = e2,
                morph = Morph(s2, e2),
                centerRelX = 0.78f + random.nextFloat() * 0.22f,
                centerRelY = 0.72f + random.nextFloat() * 0.24f,
                radiusDp = 270f + random.nextFloat() * 130f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.16f + random.nextFloat() * 0.07f,
                isPrimaryColor = false,
                driftSign = -1f
            ),
            ExpressiveBackgroundBlob(
                startPolygon = s3,
                endPolygon = e3,
                morph = Morph(s3, e3),
                centerRelX = 0.72f + random.nextFloat() * 0.18f,
                centerRelY = 0.10f + random.nextFloat() * 0.20f,
                radiusDp = 140f + random.nextFloat() * 80f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.12f + random.nextFloat() * 0.06f,
                isPrimaryColor = true,
                driftSign = 1f
            )
        )
    }
}

/** True when the system animator scale is 0 (Settings > Accessibility > Remove animations). */
@Composable
private fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Throwable) {
            1f
        }
        scale == 0f
    }
}

/** True when the device is in battery saver — background motion is skipped to save power. */
@Composable
private fun isBatterySaverEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Seamless ambient background overlay rendering real-time morphing androidx.graphics.shapes.Morph
 * Expressive shapes with luminous multi-stop radial gradients and gentle kinetic breathing.
 */
@Composable
fun M3ExpressiveVariableBackground(
    modifier: Modifier = Modifier,
    pageSeed: String = "expressive_page"
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var isShapesEnabled by remember { mutableStateOf(sp.getBoolean("sp_expressive_bg_shapes", true)) }
    var shapeChangeMode by remember { mutableStateOf(sp.getString("sp_bg_shape_change_mode", "ALWAYS") ?: "ALWAYS") } // "ALWAYS" or "PERIODIC"
    var rotationMinutes by remember { mutableIntStateOf(sp.getInt("sp_bg_shape_rotation_min", 5)) } // 1..60 min
    var seedEpoch by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_expressive_bg_shapes" -> {
                    isShapesEnabled = sp.getBoolean("sp_expressive_bg_shapes", true)
                }
                "sp_bg_shape_change_mode" -> {
                    shapeChangeMode = sp.getString("sp_bg_shape_change_mode", "ALWAYS") ?: "ALWAYS"
                }
                "sp_bg_shape_rotation_min" -> {
                    rotationMinutes = sp.getInt("sp_bg_shape_rotation_min", 5)
                }
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    if (!isShapesEnabled) return

    // Static seed epoch for PERIODIC mode so shapes remain stable until timer ticks
    var periodicSeedEpoch by rememberSaveable { mutableLongStateOf(sp.getLong("sp_periodic_seed_epoch", 1000L)) }

    // Periodic Shape Rotation Timer (only active in PERIODIC mode)
    LaunchedEffect(shapeChangeMode, rotationMinutes) {
        if (shapeChangeMode == "PERIODIC" && rotationMinutes > 0) {
            val intervalMs = rotationMinutes * 60 * 1000L
            while (this.isActive) {
                kotlinx.coroutines.delay(intervalMs)
                val nextEpoch = System.currentTimeMillis()
                periodicSeedEpoch = nextEpoch
                sp.edit().putLong("sp_periodic_seed_epoch", nextEpoch).apply()
            }
        }
    }

    val effectiveSeed = if (shapeChangeMode == "ALWAYS") {
        pageSeed + "_" + seedEpoch.toString()
    } else {
        pageSeed + "_periodic_" + periodicSeedEpoch.toString()
    }

    val blobs = remember(pageSeed, shapeChangeMode, seedEpoch, periodicSeedEpoch) {
        M3ExpressiveBackgroundProvider.generateRandomBlobs(effectiveSeed)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val animate = !isReducedMotionEnabled() && !isBatterySaverEnabled()
    val transition = rememberInfiniteTransition(label = "m3_expressive_bg")

    // Slow continuous drift
    val drift = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 28000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "m3_expressive_bg_drift"
        ).value
    } else {
        0f
    }

    // Real-time vector shape morph progress (Forward & Counter-phase)
    val morphProgressA = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)),
                repeatMode = RepeatMode.Reverse
            ),
            label = "m3_morph_progress_a"
        ).value
    } else {
        0f
    }

    val morphProgressB = if (animate) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18000, easing = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)),
                repeatMode = RepeatMode.Reverse
            ),
            label = "m3_morph_progress_b"
        ).value
    } else {
        0f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        blobs.forEach { blob ->
            val color = if (blob.isPrimaryColor) primaryColor else tertiaryColor
            val cx = width * blob.centerRelX
            val cy = height * blob.centerRelY

            val morphProgress = if (blob.driftSign > 0) morphProgressA else morphProgressB
            val animatedRotation = blob.rotation + blob.driftSign * drift * 16f
            val breathe = 1f + blob.driftSign * 0.05f * sin(drift * PI.toFloat())
            val radius = blob.radiusDp.dp.toPx() * breathe

            rotate(degrees = animatedRotation, pivot = Offset(cx, cy)) {
                val androidPath = blob.morph.toPath(progress = morphProgress)
                val matrix = Matrix()
                matrix.postScale(radius, radius)
                matrix.postTranslate(cx, cy)
                androidPath.transform(matrix)
                val path = androidPath.asComposePath()

                val radialBrush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = blob.alpha),
                        color.copy(alpha = blob.alpha * 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius * 1.2f
                )

                drawPath(
                    path = path,
                    brush = radialBrush
                )
            }
        }
    }
}
