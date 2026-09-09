package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * - [Neutral]: resting face, no mouth.
 * - [Happy]: small upward-curved mouth.
 * - [Thinking]: eyes narrowed and shifted sideways, no mouth.
 * - [Error]: flat/slightly wavy mouth, eyes slightly asymmetric.
 */
sealed class BudExpression {
    data object Neutral : BudExpression()
    data object Happy : BudExpression()
    data object Thinking : BudExpression()
    data object Error : BudExpression()
}

/**
 * User-facing preference controlling whether Bud ([PetalMascot]) appears anywhere in
 * the app. Screens that place Bud alongside a fallback icon/illustration should check
 * [rememberShowBudMascot] and fall back to their original visual when it's false.
 */
object PetalMascotPrefs {
    const val PREF_SHOW_MASCOT = "sp_show_bud_mascot"
    const val DEFAULT_SHOW_MASCOT = true
}

/**
 * Reads the "Show Bud mascot" preference (see [PetalMascotPrefs]), defaulting to on.
 * Read once per composition - same as other simple display preferences elsewhere in
 * Petal (dynamic color, AMOLED) - so flipping it in Settings takes effect the next
 * time the host screen (crash dialog, tab switcher empty state, etc.) is composed.
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
 * PetalMascot renders "Bud", Petal's abstract blob mascot, as a Material 3 Expressive
 * illustration built entirely from [MaterialTheme.colorScheme] tokens - no hardcoded
 * colors - so it automatically re-themes with Material You on Android 12+ and falls
 * back gracefully to Petal's static palette on older versions, exactly like
 * ZenithContainedLoadingIndicator does in ContainedLoadingIndicator.kt.
 *
 * The whole character (body, petal crown, face) is drawn on a single internal square
 * canvas so it drops cleanly into avatar-sized containers and stays legible as small
 * as 48dp.
 *
 * @param modifier Modifier applied to the mascot's bounding box.
 * @param expression Current [BudExpression] driving the face. Defaults to [BudExpression.Neutral].
 * @param size Side length of the square bounding box Bud is drawn in. Defaults to 96dp;
 *   tested down to 48dp for avatar-sized usage.
 * @param playEntrance Whether to play the bouncy pop-in entrance animation when this
 *   composable first enters composition. Set false to skip straight to resting scale
 *   (e.g. if the parent already animates visibility).
 * @param enableIdleBreathing Whether Bud gently scales up and down in a continuous
 *   idle "breathing" loop. Disable for static/snapshot contexts.
 * @param onClick Optional click handler. When provided, tapping Bud plays a quick
 *   squash-and-bounce-back press animation before invoking this callback.
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
    // --- Entrance: bouncy pop-in, same spring family as the pull-to-refresh
    // indicator's entrance in ContainedLoadingIndicator.kt (RefreshBarLoadingIndicator).
    val entranceScale = remember { Animatable(if (playEntrance) 0f else 1f) }
    LaunchedEffect(playEntrance) {
        if (playEntrance) {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // --- Idle breathing: slow, low-stiffness continuous loop. animateFloatAsState
    // re-triggers whenever its target changes; wrapping a low-stiffness spring in
    // infiniteRepeatable makes it drift between 1f and 1.06f forever, which reads as
    // a soft, organic breathing motion rather than a mechanical pulse.
    val breathingTarget = remember { mutableStateOf(1f) }
    LaunchedEffect(enableIdleBreathing) {
        breathingTarget.value = if (enableIdleBreathing) 1.06f else 1f
    }
    val breathingScale by animateFloatAsState(
        targetValue = breathingTarget.value,
        animationSpec = if (enableIdleBreathing) infiniteBreathingSpring() else spring(),
        label = "BudBreathingScale"
    )

    // --- Press interaction: quick squash-down then spring-back-up, same bouncy family
    // used for the entrance.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BudPressScale"
    )

    val combinedScale = entranceScale.value * breathingScale * pressScale

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

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
        BudFace(expression = expression, modifier = Modifier.size(size))
    }
}

/**
 * A gentle, continuously alternating low-stiffness spring for the idle breathing loop.
 * infiniteRepeatable + a bouncy-ish low-stiffness spring reads as a soft, organic
 * "breathing" motion rather than a mechanical linear pulse.
 */
private fun infiniteBreathingSpring() = infiniteRepeatable<Float>(
    animation = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    ),
    repeatMode = RepeatMode.Reverse
)

@Composable
private fun BudFace(expression: BudExpression, modifier: Modifier = Modifier) {
    val bodyBase = MaterialTheme.colorScheme.primaryContainer
    val bodyEdge = MaterialTheme.colorScheme.primary
    val eyeColor = MaterialTheme.colorScheme.onPrimaryContainer
    val eyeHighlight = MaterialTheme.colorScheme.surface
    val glowColor = MaterialTheme.colorScheme.tertiaryContainer
    val mouthColor = MaterialTheme.colorScheme.onTertiaryContainer

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // Bud is ~1:1.1 width:height, wider at the bottom - built by an elliptical
        // radius function sampled around a full turn, then rendered as a smooth
        // closed Catmull-Rom spline so the outline (body + petals) stays one
        // continuous curve rather than a crown sitting on a separate silhouette.
        val bodyPath = budBodyPath(w, h)

        // Body fill: soft gradient from primaryContainer at the base toward
        // primary at the petal edges (top).
        val bodyBrush = Brush.verticalGradient(
            colors = listOf(bodyEdge, bodyBase),
            startY = 0f,
            endY = h
        )
        drawPath(path = bodyPath, brush = bodyBrush)

        // Face geometry, anchored to fractions of the canvas so it scales cleanly
        // down to 48dp.
        val eyeCy = h * 0.62f
        val eyeSpacing = w * 0.20f
        val leftEyeCx = cx - eyeSpacing
        val rightEyeCx = cx + eyeSpacing
        val glowRadius = w * 0.22f

        // Warm inner glow behind the eyes.
        drawCircle(
            color = glowColor.copy(alpha = 0.55f),
            radius = glowRadius,
            center = Offset(cx, eyeCy)
        )

        drawBudFaceFeatures(
            expression = expression,
            canvasWidth = w,
            canvasHeight = h,
            centerX = cx,
            eyeCy = eyeCy,
            leftEyeCx = leftEyeCx,
            rightEyeCx = rightEyeCx,
            eyeColor = eyeColor,
            eyeHighlight = eyeHighlight,
            mouthColor = mouthColor
        )
    }
}

/**
 * Builds Bud's single continuous body silhouette: an asymmetric rounded blob, wider
 * at the bottom, with 5 soft petal-shaped bumps forming the outer edge of the top
 * third of the shape. The petal bumps are blended into the base silhouette via an
 * angular window function so they read as part of the same edge rather than a crown
 * sitting on top, matching the soft scalloped curves of MaterialShapes-style shapes
 * (e.g. MaterialShapes.Cookie12Sided / Flower) rather than sharp points.
 */
private fun budBodyPath(width: Float, height: Float): Path {
    val cx = width / 2f
    val cy = height / 2f
    // 1 : 1.1 width-to-height silhouette.
    val radiusX = width / 2f * 0.94f
    val radiusY = height / 2f * 0.98f

    val petalCount = 5
    val petalAmplitude = 0.14f
    // Half-width (in turns, 0..1 = full circle) of the window around the top where
    // petals are allowed to appear - keeps them confined to roughly the top third
    // of the shape.
    val petalWindowHalfWidth = 0.20f
    val bottomBulge = 0.10f

    val sampleCount = 128
    val points = ArrayList<Offset>(sampleCount)

    for (i in 0 until sampleCount) {
        // t = 0 is straight up, increasing clockwise.
        val t = i.toFloat() / sampleCount
        val angle = (t * 2f * Math.PI - Math.PI / 2.0).toFloat()

        // Circular distance from the top (t = 0), in turns.
        val dist = min(t, 1f - t)
        val window = smoothstep(petalWindowHalfWidth, 0f, dist)

        // Gentle organic asymmetry plus extra width toward the bottom.
        val bottomDist = min(kotlin.math.abs(t - 0.5f), 1f - kotlin.math.abs(t - 0.5f))
        val bottomWindow = smoothstep(0.32f, 0f, bottomDist)
        val asymmetry = 0.045f * sin((t * 2f * Math.PI + Math.PI / 3.0).toFloat())

        val petalTerm = petalAmplitude * window * cos((petalCount * t * 2f * Math.PI).toFloat())
        val bulgeTerm = bottomBulge * bottomWindow

        val r = 1f + petalTerm + bulgeTerm + asymmetry

        val x = cx + radiusX * r * cos(angle)
        val y = cy + radiusY * r * sin(angle)
        points.add(Offset(x, y))
    }

    return catmullRomClosedPath(points)
}

/** Smoothstep-style falloff: 1 at x=0, 0 at x=edge, smooth in between. */
private fun smoothstep(edge: Float, x0: Float, x: Float): Float {
    val t = ((edge - x) / (edge - x0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Builds a smooth closed curve through [points] using a Catmull-Rom to cubic-Bezier conversion. */
private fun catmullRomClosedPath(points: List<Offset>): Path {
    val path = Path()
    val n = points.size
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

/** Draws Bud's eyes (with highlight) and, when present, mouth for the given [expression]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBudFaceFeatures(
    expression: BudExpression,
    canvasWidth: Float,
    canvasHeight: Float,
    centerX: Float,
    eyeCy: Float,
    leftEyeCx: Float,
    rightEyeCx: Float,
    eyeColor: Color,
    eyeHighlight: Color,
    mouthColor: Color
) {
    val baseEyeRadius = canvasWidth * 0.052f
    val highlightRadius = baseEyeRadius * 0.34f

    when (expression) {
        is BudExpression.Neutral -> {
            drawEye(leftEyeCx, eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius)
            drawEye(rightEyeCx, eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius)
        }

        is BudExpression.Happy -> {
            drawEye(leftEyeCx, eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius)
            drawEye(rightEyeCx, eyeCy, baseEyeRadius, 1f, eyeColor, eyeHighlight, highlightRadius)

            val mouthY = eyeCy + canvasHeight * 0.13f
            val mouthHalfWidth = canvasWidth * 0.11f
            val mouthPath = Path().apply {
                moveTo(centerX - mouthHalfWidth, mouthY)
                quadraticTo(
                    centerX, mouthY + canvasHeight * 0.07f,
                    centerX + mouthHalfWidth, mouthY
                )
            }
            drawPath(
                path = mouthPath,
                color = mouthColor,
                style = Stroke(width = canvasWidth * 0.028f, cap = StrokeCap.Round)
            )
        }

        is BudExpression.Thinking -> {
            // Eyes narrowed (squashed vertically) and shifted sideways, as if
            // glancing off to one side while pondering.
            val shift = canvasWidth * 0.035f
            drawEye(
                leftEyeCx + shift, eyeCy, baseEyeRadius,
                verticalScale = 0.42f, eyeColor = eyeColor,
                highlightColor = eyeHighlight, highlightRadius = highlightRadius
            )
            drawEye(
                rightEyeCx + shift, eyeCy, baseEyeRadius,
                verticalScale = 0.42f, eyeColor = eyeColor,
                highlightColor = eyeHighlight, highlightRadius = highlightRadius
            )
        }

        is BudExpression.Error -> {
            // Eyes slightly asymmetric: one a touch smaller/higher than the other.
            drawEye(
                leftEyeCx, eyeCy - canvasHeight * 0.01f, baseEyeRadius * 0.9f,
                verticalScale = 1f, eyeColor = eyeColor,
                highlightColor = eyeHighlight, highlightRadius = highlightRadius * 0.9f
            )
            drawEye(
                rightEyeCx, eyeCy + canvasHeight * 0.008f, baseEyeRadius * 1.05f,
                verticalScale = 1f, eyeColor = eyeColor,
                highlightColor = eyeHighlight, highlightRadius = highlightRadius
            )

            val mouthY = eyeCy + canvasHeight * 0.15f
            val mouthHalfWidth = canvasWidth * 0.10f
            val wobble = canvasHeight * 0.018f
            val mouthPath = Path().apply {
                moveTo(centerX - mouthHalfWidth, mouthY)
                quadraticTo(
                    centerX - mouthHalfWidth * 0.33f, mouthY + wobble,
                    centerX, mouthY
                )
                quadraticTo(
                    centerX + mouthHalfWidth * 0.33f, mouthY - wobble,
                    centerX + mouthHalfWidth, mouthY
                )
            }
            drawPath(
                path = mouthPath,
                color = mouthColor,
                style = Stroke(width = canvasWidth * 0.026f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEye(
    cx: Float,
    cy: Float,
    radius: Float,
    verticalScale: Float,
    eyeColor: Color,
    highlightColor: Color,
    highlightRadius: Float
) {
    if (verticalScale == 1f) {
        drawCircle(color = eyeColor, radius = radius, center = Offset(cx, cy))
    } else {
        scale(scaleX = 1f, scaleY = verticalScale, pivot = Offset(cx, cy)) {
            drawCircle(color = eyeColor, radius = radius, center = Offset(cx, cy))
        }
    }
    drawCircle(
        color = highlightColor,
        radius = highlightRadius,
        center = Offset(cx - radius * 0.32f, cy - radius * 0.32f * verticalScale)
    )
}

@Preview(name = "Bud - Neutral", showBackground = true)
@Composable
private fun PetalMascotNeutralPreview() {
    PetalExpressiveTheme {
        PetalMascot(expression = BudExpression.Neutral)
    }
}

@Preview(name = "Bud - Happy", showBackground = true)
@Composable
private fun PetalMascotHappyPreview() {
    PetalExpressiveTheme {
        PetalMascot(expression = BudExpression.Happy)
    }
}

@Preview(name = "Bud - Thinking", showBackground = true)
@Composable
private fun PetalMascotThinkingPreview() {
    PetalExpressiveTheme {
        PetalMascot(expression = BudExpression.Thinking)
    }
}

@Preview(name = "Bud - Error", showBackground = true)
@Composable
private fun PetalMascotErrorPreview() {
    PetalExpressiveTheme {
        PetalMascot(expression = BudExpression.Error)
    }
}

@Preview(name = "Bud - Small (48dp)", showBackground = true)
@Composable
private fun PetalMascotSmallPreview() {
    PetalExpressiveTheme {
        PetalMascot(size = 48.dp, expression = BudExpression.Happy)
    }
}
