package com.petal.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val isPrimaryColor: Boolean
)

/**
 * Generates a randomized set of M3 Expressive shapes on every launch/entry.
 */
object M3ExpressiveBackgroundProvider {

    fun generateRandomBlobs(seedKey: String = System.currentTimeMillis().toString()): List<ExpressiveBackgroundBlob> {
        val random = java.util.Random(seedKey.hashCode().toLong())
        val shapes = M3ExpressiveShapeType.entries

        return listOf(
            ExpressiveBackgroundBlob(
                type = shapes[random.nextInt(shapes.size)],
                centerRelX = 0.15f + random.nextFloat() * 0.2f,
                centerRelY = 0.12f + random.nextFloat() * 0.18f,
                radiusDp = 180f + random.nextFloat() * 100f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.05f + random.nextFloat() * 0.04f,
                isPrimaryColor = true
            ),
            ExpressiveBackgroundBlob(
                type = shapes[random.nextInt(shapes.size)],
                centerRelX = 0.75f + random.nextFloat() * 0.2f,
                centerRelY = 0.70f + random.nextFloat() * 0.25f,
                radiusDp = 220f + random.nextFloat() * 120f,
                rotation = random.nextFloat() * 360f,
                alpha = 0.04f + random.nextFloat() * 0.04f,
                isPrimaryColor = false
            )
        )
    }
}

/**
 * Seamless ambient background overlay rendering variable M3 Expressive shapes.
 */
@Composable
fun M3ExpressiveVariableBackground(
    modifier: Modifier = Modifier,
    pageSeed: String = "expressive_page"
) {
    val blobs = remember(pageSeed) {
        M3ExpressiveBackgroundProvider.generateRandomBlobs(pageSeed + System.currentTimeMillis().toString())
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        blobs.forEach { blob ->
            val color = if (blob.isPrimaryColor) primaryColor else tertiaryColor
            val cx = width * blob.centerRelX
            val cy = height * blob.centerRelY
            val radius = blob.radiusDp.dp.toPx()

            rotate(degrees = blob.rotation, pivot = Offset(cx, cy)) {
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
