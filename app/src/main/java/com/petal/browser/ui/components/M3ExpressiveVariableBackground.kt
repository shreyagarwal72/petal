package com.petal.browser.ui.components

import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

enum class M3ExpressiveShapeType {
    SCALLOP, FLOWER, STARBURST, CLOVER, ARCH, POLYGON
}

data class ExpressiveBackgroundBlob(
    val type: M3ExpressiveShapeType,
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
 * Generates a randomized set of M3 Expressive shapes on every screen entry.
 * Raised to three blobs with a visible alpha range (previously 0.04-0.09,
 * which read as essentially invisible against light Petal palettes) — this
 * one change updates the background everywhere it's already used: History,
 * Settings, Downloads, Delete confirmation, Account Sync, Omnibox, and Home.
 */
object M3ExpressiveBackgroundProvider {

    fun generateRandomBlobs(seedKey: String): List<ExpressiveBackgroundBlob> {
        val random = java.util.Random(seedKey.hashCode().toLong())
        val shapes = M3ExpressiveShapeType.entries

        return listOf(
            ExpressiveBackgroundBlob(
                type = shapes[random.nextInt(shapes.size)],
                centerRelX = 0.10f + random.nextFloat() * 0.20f,
                centerRelY = 0.06f + random.nextFloat() * 0.16f,
                radiusDp = 220f + random.nextFloat() * 110f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.16f + random.nextFloat() * 0.08f,
                isPrimaryColor = true,
                driftSign = 1f
            ),
            ExpressiveBackgroundBlob(
                type = shapes[random.nextInt(shapes.size)],
                centerRelX = 0.78f + random.nextFloat() * 0.22f,
                centerRelY = 0.72f + random.nextFloat() * 0.24f,
                radiusDp = 260f + random.nextFloat() * 130f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.14f + random.nextFloat() * 0.07f,
                isPrimaryColor = false,
                driftSign = -1f
            ),
            ExpressiveBackgroundBlob(
                type = shapes[random.nextInt(shapes.size)],
                centerRelX = 0.72f + random.nextFloat() * 0.18f,
                centerRelY = 0.10f + random.nextFloat() * 0.20f,
                radiusDp = 130f + random.nextFloat() * 80f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.10f + random.nextFloat() * 0.06f,
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
 * Seamless ambient background overlay rendering variable, gently morphing M3
 * Expressive shapes. Used as the bottom-most layer of every full screen in
 * the app — pass a stable, screen-specific [pageSeed] (e.g. "home_page",
 * "history_page") so the layout is consistent within a session but differs
 * screen to screen. Purely decorative: never intercepts touch.
 */
@Composable
fun M3ExpressiveVariableBackground(
    modifier: Modifier = Modifier,
    pageSeed: String = "expressive_page"
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var isShapesEnabled by remember { mutableStateOf(sp.getBoolean("sp_expressive_bg_shapes", true)) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_expressive_bg_shapes") {
                isShapesEnabled = sp.getBoolean("sp_expressive_bg_shapes", true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    if (!isShapesEnabled) return
    // A fresh random layout each time this screen is entered (matches the
    // "variable" behavior every other screen already has), but stable for
    // the lifetime of this composition so it doesn't jump around mid-visit.
    val blobs = remember(pageSeed) {
        M3ExpressiveBackgroundProvider.generateRandomBlobs(pageSeed + System.currentTimeMillis().toString())
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val animate = !isReducedMotionEnabled() && !isBatterySaverEnabled()
    val transition = rememberInfiniteTransition(label = "m3_expressive_bg")
    val drift = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "m3_expressive_bg_drift"
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

            // Slow rotation drift (+/-14 deg) and a gentle radius "breathe" (+/-6%) --
            // the blob never spins or jumps, it just softly morphs in place.
            val animatedRotation = blob.rotation + blob.driftSign * drift * 14f
            val breathe = 1f + blob.driftSign * 0.06f * sin(drift * PI.toFloat())
            val radius = blob.radiusDp.dp.toPx() * breathe

            rotate(degrees = animatedRotation, pivot = Offset(cx, cy)) {
                val path = createM3ShapePath(blob.type, cx, cy, radius)
                drawPath(
                    path = path,
                    color = color.copy(alpha = blob.alpha)
                )
            }
        }
    }
}

private fun createM3ShapePath(type: M3ExpressiveShapeType, cx: Float, cy: Float, r: Float): Path {
    val path = Path()
    when (type) {
        M3ExpressiveShapeType.SCALLOP -> {
            val lobes = 8
            val depth = 0.18f
            val rMid = r * (1f - depth / 2f)
            val amp = r * depth / 2f
            val steps = 180
            for (i in 0..steps) {
                val angle = (i.toFloat() / steps * 2f * PI - PI / 2f).toFloat()
                val dist = rMid + amp * cos(lobes * angle)
                val x = cx + dist * cos(angle)
                val y = cy + dist * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        M3ExpressiveShapeType.FLOWER -> {
            val petals = 5
            val steps = 180
            for (i in 0..steps) {
                val angle = (i.toFloat() / steps * 2f * PI).toFloat()
                val dist = r * (0.7f + 0.3f * cos(petals * angle))
                val x = cx + dist * cos(angle)
                val y = cy + dist * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        M3ExpressiveShapeType.STARBURST -> {
            val points = 12
            val innerR = r * 0.6f
            val totalPoints = points * 2
            for (i in 0 until totalPoints) {
                val angle = (i.toDouble() * PI / points).toFloat()
                val dist = if (i % 2 == 0) r else innerR
                val x = cx + dist * cos(angle)
                val y = cy + dist * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        M3ExpressiveShapeType.CLOVER -> {
            val lobes = 4
            val steps = 180
            for (i in 0..steps) {
                val angle = (i.toFloat() / steps * 2f * PI).toFloat()
                val dist = r * (0.65f + 0.35f * sin(lobes * angle))
                val x = cx + dist * cos(angle)
                val y = cy + dist * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        M3ExpressiveShapeType.ARCH -> {
            path.moveTo(cx - r, cy + r)
            path.lineTo(cx - r, cy)
            path.arcTo(
                rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            path.lineTo(cx + r, cy + r)
            path.close()
        }
        M3ExpressiveShapeType.POLYGON -> {
            val sides = 6
            for (i in 0 until sides) {
                val angle = (i.toDouble() * 2.0 * PI / sides).toFloat()
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
    }
    return path
}
