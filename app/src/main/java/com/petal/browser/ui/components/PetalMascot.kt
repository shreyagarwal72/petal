package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Bud's expression state. Callers switch this to drive the mascot's face.
 *
 * - [Neutral]   : resting face, no mouth.
 * - [Happy]     : small upward-curved mouth.
 * - [Thinking]  : eyes narrowed and sweep left-right slowly, no mouth.
 * - [Error]     : slightly wavy mouth, eyes slightly asymmetric.
 * - [Sleeping]  : eyes closed (soft arcs), slow deep breathing.
 * - [Excited]   : wide open eyes, round "O" mouth.
 * - [Searching] : eyes scan side-to-side in a rapid loop.
 */
sealed class BudExpression {
    data object Neutral   : BudExpression()
    data object Happy     : BudExpression()
    data object Thinking  : BudExpression()
    data object Error     : BudExpression()
    data object Sleeping  : BudExpression()
    data object Excited   : BudExpression()
    data object Searching : BudExpression()
}

/**
 * User-facing preference controlling whether Bud ([PetalMascot]) appears anywhere in
 * the app. Screens that place Bud alongside a fallback icon/illustration should check
 * [rememberShowBudMascot] and fall back to their original visual when it's false.
 */
object PetalMascotPrefs {
    const val PREF_SHOW_MASCOT    = "sp_show_bud_mascot"
    const val DEFAULT_SHOW_MASCOT = true
}

/**
 * Reads the "Show Bud mascot" preference (see [PetalMascotPrefs]), defaulting to on.
 */
@Composable
fun rememberShowBudMascot(): Boolean {
    val context = LocalContext.current
    return remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PetalMascotPrefs.PREF_SHOW_MASCOT, PetalMascotPrefs.DEFAULT_SHOW_MASCOT)
    }
}

/**
 * PetalMascot renders "Bud", Petal's abstract blob mascot, built entirely from
 * [MaterialTheme.colorScheme] tokens — no hardcoded colors — so it automatically
 * re-themes with Material You on Android 12+ and falls back to Petal's static palette
 * on older versions.
 *
 * @param modifier            Modifier applied to the mascot's bounding box.
 * @param expression          Current [BudExpression] driving the face.
 * @param size                Side length of the square bounding box. Defaults to 96dp.
 * @param playEntrance        Whether to play the bouncy pop-in entrance animation.
 * @param enableIdleBreathing Whether Bud gently scales in a continuous idle loop.
 * @param onClick             Optional click handler — plays a squash-bounce press animation.
 */
@Composable
fun PetalMascot(
    modifier: Modifier = Modifier,
    expression: BudExpression = BudExpression.Neutral,
    size: Dp = 96.dp,
    playEntrance: Boolean = true,
    enableIdleBreathing: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    // --- Entrance: bouncy pop-in. Same spring family as pull-to-refresh indicator. ---
    val entranceScale = remember { Animatable(if (playEntrance) 0f else 1f) }
    LaunchedEffect(playEntrance) {
        if (playEntrance) {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            )
        }
    }

    // --- Idle breathing via InfiniteTransition (correctly loops forever). ---
    // Sleeping gets a longer, deeper breath cycle to feel calm rather than alert.
    val breathDurationMs = if (expression is BudExpression.Sleeping) 3200 else 1800
    val breathAmplitude  = if (expression is BudExpression.Sleeping) 1.09f else 1.06f

    val infiniteTransition = rememberInfiniteTransition(label = "BudBreathing")
    val breathingScale by if (enableIdleBreathing) {
        infiniteTransition.animateFloat(
            initialValue  = 1f,
            targetValue   = breathAmplitude,
            animationSpec = infiniteRepeatable(
                animation  = tween(breathDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "BudBreathingScale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // --- Press interaction: squash-down then spring-back-up. ---
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "BudPressScale"
    )

    val combinedScale = entranceScale.value * breathingScale * pressScale

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication        = null,
            onClick           = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Bud" }
            .then(clickableModifier)
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
            }
    ) {
        BudFace(
            expression         = expression,
            modifier           = Modifier.size(size),
            infiniteTransition = infiniteTransition
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal face composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BudFace(
    expression: BudExpression,
    modifier: Modifier = Modifier,
    infiniteTransition: InfiniteTransition
) {
    val bodyBase     = MaterialTheme.colorScheme.primaryContainer
    val bodyEdge     = MaterialTheme.colorScheme.primary
    val eyeColor     = MaterialTheme.colorScheme.onPrimaryContainer
    val eyeHighlight = MaterialTheme.colorScheme.surface
    val glowColor    = MaterialTheme.colorScheme.tertiaryContainer
    val mouthColor   = MaterialTheme.colorScheme.onTertiaryContainer
    val outerGlow    = MaterialTheme.colorScheme.tertiaryContainer

    // Animated eye-shift for Thinking / Searching expressions.
    val eyeShiftFraction by when (expression) {
        is BudExpression.Thinking -> infiniteTransition.animateFloat(
            initialValue  = -1f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ThinkingEyeShift"
        )
        is BudExpression.Searching -> infiniteTransition.animateFloat(
            initialValue  = -1f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "SearchingEyeShift"
        )
        else -> remember { mutableStateOf(0f) }
    }

    // Cache the body path — recompute only when the canvas size changes.
    val cachedBodyPath = remember { mutableStateOf<Pair<Size, Path>?>(null) }

    Canvas(modifier = modifier) {
        val w  = size.width
        val h  = size.height
        val cx = w / 2f

        val currentSize = size
        val bodyPath = if (cachedBodyPath.value?.first == currentSize) {
            cachedBodyPath.value!!.second
        } else {
            budBodyPath(w, h).also { cachedBodyPath.value = currentSize to it }
        }

        // Outer edge glow — subtle tertiaryContainer halo for depth.
        drawPath(
            path  = bodyPath,
            brush = Brush.radialGradient(
                colors  = listOf(outerGlow.copy(alpha = 0.0f), outerGlow.copy(alpha = 0.28f)),
                center  = Offset(cx, h * 0.48f),
                radius  = w * 0.62f
            )
        )

        // Body fill: primary at petal tips → primaryContainer at base.
        drawPath(
            path  = bodyPath,
            brush = Brush.verticalGradient(
                colors = listOf(bodyEdge, bodyBase),
                startY = 0f,
                endY   = h
            )
        )

        val eyeCy      = h * 0.62f
        val eyeSpacing = w * 0.20f
        val leftEyeCx  = cx - eyeSpacing
        val rightEyeCx = cx + eyeSpacing
        val glowRadius = w * 0.22f

        // Warm inner glow behind the eyes.
        drawCircle(
            color  = glowColor.copy(alpha = 0.55f),
            radius = glowRadius,
            center = Offset(cx, eyeCy)
        )

        drawBudFaceFeatures(
            expression       = expression,
            canvasWidth      = w,
            canvasHeight     = h,
            centerX          = cx,
            eyeCy            = eyeCy,
            leftEyeCx        = leftEyeCx,
            rightEyeCx       = rightEyeCx,
            eyeShiftFraction = eyeShiftFraction,
            eyeColor         = eyeColor,
            eyeHighlight     = eyeHighlight,
            mouthColor       = mouthColor
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Body silhouette path
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds Bud's single continuous body silhouette: an asymmetric rounded blob, wider
 * at the bottom, with 5 soft petal-shaped bumps forming the outer edge of the top
 * third of the shape.
 */
private fun budBodyPath(width: Float, height: Float): Path {
    val cx = width  / 2f
    val cy = height / 2f
    val radiusX = width  / 2f * 0.94f
    val radiusY = height / 2f * 0.98f

    val petalCount           = 5
    val petalAmplitude       = 0.14f
    val petalWindowHalfWidth = 0.20f
    val bottomBulge          = 0.10f
    val sampleCount          = 128
    val points               = ArrayList<Offset>(sampleCount)

    for (i in 0 until sampleCount) {
        val t     = i.toFloat() / sampleCount
        val angle = (t * 2f * Math.PI - Math.PI / 2.0).toFloat()

        val dist         = min(t, 1f - t)
        val window       = smoothstep(petalWindowHalfWidth, 0f, dist)
        val bottomDist   = min(kotlin.math.abs(t - 0.5f), 1f - kotlin.math.abs(t - 0.5f))
        val bottomWindow = smoothstep(0.32f, 0f, bottomDist)
        val asymmetry    = 0.045f * sin((t * 2f * Math.PI + Math.PI / 3.0).toFloat())

        val r = 1f + petalAmplitude * window * cos((petalCount * t * 2f * Math.PI).toFloat()) +
                bottomBulge * bottomWindow + asymmetry

        points.add(Offset(cx + radiusX * r * cos(angle), cy + radiusY * r * sin(angle)))
    }

    return catmullRomClosedPath(points)
}

private fun smoothstep(edge: Float, x0: Float, x: Float): Float {
    val t = ((edge - x) / (edge - x0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun catmullRomClosedPath(points: List<Offset>): Path {
    val path = Path()
    val n    = points.size
    if (n < 3) return path

    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]

        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    path.close()
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
// Face features
// ─────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBudFaceFeatures(
    expression: BudExpression,
    canvasWidth: Float,
    canvasHeight: Float,
    centerX: Float,
    eyeCy: Float,
    leftEyeCx: Float,
    rightEyeCx: Float,
    eyeShiftFraction: Float,
    eyeColor: Color,
    eyeHighlight: Color,
    mouthColor: Color
) {
    val baseEyeRadius  = canvasWidth * 0.052f
    val highlightRadius = baseEyeRadius * 0.34f

    when (expression) {
        is BudExpression.Neutral -> {
            drawEye(leftEyeCx,  eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius, highlightRight = false)
            drawEye(rightEyeCx, eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius, highlightRight = false)
        }

        is BudExpression.Happy -> {
            drawEye(leftEyeCx,  eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius, highlightRight = false)
            drawEye(rightEyeCx, eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius, highlightRight = false)

            val mouthY         = eyeCy + canvasHeight * 0.13f
            val mouthHalfWidth = canvasWidth * 0.11f
            drawPath(
                path  = Path().apply {
                    moveTo(centerX - mouthHalfWidth, mouthY)
                    quadraticTo(centerX, mouthY + canvasHeight * 0.07f, centerX + mouthHalfWidth, mouthY)
                },
                color = mouthColor,
                style = Stroke(width = canvasWidth * 0.028f, cap = StrokeCap.Round)
            )
        }

        is BudExpression.Thinking, is BudExpression.Searching -> {
            val shift  = canvasWidth * 0.042f * eyeShiftFraction
            val vScale = if (expression is BudExpression.Thinking) 0.42f else 0.72f
            val hlRight = eyeShiftFraction > 0f
            drawEye(leftEyeCx  + shift, eyeCy, baseEyeRadius, vScale, eyeColor, eyeHighlight, highlightRadius, hlRight)
            drawEye(rightEyeCx + shift, eyeCy, baseEyeRadius, vScale, eyeColor, eyeHighlight, highlightRadius, hlRight)
        }

        is BudExpression.Error -> {
            drawEye(leftEyeCx,  eyeCy - canvasHeight * 0.01f, baseEyeRadius * 0.9f,
                1f, eyeColor, eyeHighlight, highlightRadius * 0.9f, highlightRight = false)
            drawEye(rightEyeCx, eyeCy + canvasHeight * 0.008f, baseEyeRadius * 1.05f,
                1f, eyeColor, eyeHighlight, highlightRadius, highlightRight = false)

            val mouthY         = eyeCy + canvasHeight * 0.15f
            val mouthHalfWidth = canvasWidth * 0.10f
            val wobble         = canvasHeight * 0.018f
            drawPath(
                path  = Path().apply {
                    moveTo(centerX - mouthHalfWidth, mouthY)
                    quadraticTo(centerX - mouthHalfWidth * 0.33f, mouthY + wobble, centerX, mouthY)
                    quadraticTo(centerX + mouthHalfWidth * 0.33f, mouthY - wobble, centerX + mouthHalfWidth, mouthY)
                },
                color = mouthColor,
                style = Stroke(width = canvasWidth * 0.026f, cap = StrokeCap.Round)
            )
        }

        is BudExpression.Sleeping -> {
            // Eyes closed: soft upward arc (like "^").
            val arcW   = baseEyeRadius * 1.6f
            val arcH   = baseEyeRadius * 0.9f
            val stroke = Stroke(width = canvasWidth * 0.028f, cap = StrokeCap.Round)
            listOf(leftEyeCx, rightEyeCx).forEach { ex ->
                drawPath(
                    path  = Path().apply {
                        moveTo(ex - arcW, eyeCy)
                        quadraticTo(ex, eyeCy - arcH, ex + arcW, eyeCy)
                    },
                    color = eyeColor,
                    style = stroke
                )
            }
        }

        is BudExpression.Excited -> {
            val excitedRadius = baseEyeRadius * 1.3f
            drawEye(leftEyeCx,  eyeCy, excitedRadius, 1f, eyeColor, eyeHighlight, highlightRadius * 1.3f, highlightRight = false)
            drawEye(rightEyeCx, eyeCy, excitedRadius, 1f, eyeColor, eyeHighlight, highlightRadius * 1.3f, highlightRight = false)

            // Round "O" open mouth.
            val mouthCy = eyeCy + canvasHeight * 0.13f
            drawCircle(
                color  = mouthColor,
                radius = canvasWidth * 0.058f,
                center = Offset(centerX, mouthCy),
                style  = Stroke(width = canvasWidth * 0.026f)
            )
        }
    }
}

/**
 * Draws a single eye circle with a specular highlight.
 *
 * @param highlightRight When true the highlight shifts to top-right instead of top-left,
 *                       matching Bud's gaze direction in Thinking/Searching modes.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEye(
    cx: Float,
    cy: Float,
    radius: Float,
    verticalScale: Float,
    eyeColor: Color,
    highlightColor: Color,
    highlightRadius: Float,
    highlightRight: Boolean = false
) {
    if (verticalScale == 1f) {
        drawCircle(color = eyeColor, radius = radius, center = Offset(cx, cy))
    } else {
        scale(scaleX = 1f, scaleY = verticalScale, pivot = Offset(cx, cy)) {
            drawCircle(color = eyeColor, radius = radius, center = Offset(cx, cy))
        }
    }
    val hlOffsetX = if (highlightRight) radius * 0.32f else -radius * 0.32f
    drawCircle(
        color  = highlightColor,
        radius = highlightRadius,
        center = Offset(cx + hlOffsetX, cy - radius * 0.32f * verticalScale)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Bud - Neutral",   showBackground = true)
@Composable
private fun PetalMascotNeutralPreview() {
    PetalExpressiveTheme { PetalMascot(expression = BudExpression.Neutral) }
}

@Preview(name = "Bud - Happy",     showBackground = true)
@Composable
private fun PetalMascotHappyPreview() {
    PetalExpressiveTheme { PetalMascot(expression = BudExpression.Happy) }
}

@Preview(name = "Bud - Thinking",  showBackground = true)
@Composable
private fun PetalMascotThinkingPreview() {
    PetalExpressiveTheme { PetalMascot(expression = BudExpression.Thinking) }
}

@Preview(name = "Bud - Error",     showBackground = true)
@Composable
private fun PetalMascotErrorPreview() {
    PetalExpressiveTheme { PetalMascot(expression = BudExpression.Error) }
}

@Preview(name = "Bud - Sleeping",  showBackground = true)
@Composable
private fun PetalMascotSleepingPreview() {
    PetalExpressiveTheme { PetalMascot(expression = BudExpression.Sleeping) }
}

@Preview(name = "Bud - Excited",   showBackground = true)
@Composable
private fun PetalMascotExcitedPreview() {
    PetalExpressiveTheme { PetalMascot(expression = BudExpression.Excited) }
}

@Preview(name = "Bud - Searching", showBackground = true)
@Composable
private fun PetalMascotSearchingPreview() {
    PetalExpressiveTheme { PetalMascot(expression = BudExpression.Searching) }
}

@Preview(name = "Bud - Small (48dp)", showBackground = true)
@Composable
private fun PetalMascotSmallPreview() {
    PetalExpressiveTheme { PetalMascot(size = 48.dp, expression = BudExpression.Happy) }
}
