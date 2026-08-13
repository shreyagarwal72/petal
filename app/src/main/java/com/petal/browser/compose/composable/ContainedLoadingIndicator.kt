package com.petal.browser.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlin.math.cos
import kotlin.math.sin

/**
 * Composable to display an indeterminate loading indicator that fills all available screen
 * @param modifier The modifier to be applied to the composable
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainedLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val description = "Loading..."
        CircularProgressIndicator(
            modifier = Modifier
                .requiredSize(48.dp)
                .semantics { stateDescription = description }
        )
    }
}

/**
 * M3 Expressive-style morphing "blob" shape, drawn with plain Compose Canvas
 * primitives - no dependency on androidx.compose.material3.LoadingIndicator,
 * which this project's pinned Compose Material3 version does not ship
 * (that's what "unresolved reference" was pointing at). It continuously
 * rotates and breathes its corner rounding between a soft pentagon and a
 * near-circle, echoing the shape ObtainX's ExpressiveRefreshIndicator uses.
 */
@Composable
private fun ExpressiveBlobShape(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val transition = rememberInfiniteTransition(label = "expressiveBlob")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "blobRotation",
    )
    val morph by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blobMorph",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val cornerRounding = 0.55f + 0.25f * morph
        val path = roundedPolygonPath(sides = 5, radius = radius, center = center, cornerRounding = cornerRounding)
        rotate(degrees = rotation, pivot = center) {
            drawPath(path = path, color = color)
        }
    }
}

/** Builds a closed [Path] for a regular polygon with rounded corners. */
private fun roundedPolygonPath(sides: Int, radius: Float, center: Offset, cornerRounding: Float): Path {
    val angleStep = 2 * Math.PI / sides
    val vertices = (0 until sides).map { i ->
        val angle = -Math.PI / 2 + i * angleStep
        Offset(
            x = center.x + radius * cos(angle).toFloat(),
            y = center.y + radius * sin(angle).toFloat(),
        )
    }
    val path = Path()
    val n = vertices.size
    for (i in 0 until n) {
        val prev = vertices[(i - 1 + n) % n]
        val curr = vertices[i]
        val next = vertices[(i + 1) % n]
        val toPrev = Offset(prev.x - curr.x, prev.y - curr.y)
        val toNext = Offset(next.x - curr.x, next.y - curr.y)
        val startPoint = Offset(curr.x + toPrev.x * cornerRounding * 0.5f, curr.y + toPrev.y * cornerRounding * 0.5f)
        val endPoint = Offset(curr.x + toNext.x * cornerRounding * 0.5f, curr.y + toNext.y * cornerRounding * 0.5f)
        if (i == 0) {
            path.moveTo(startPoint.x, startPoint.y)
        } else {
            path.lineTo(startPoint.x, startPoint.y)
        }
        path.quadraticBezierTo(curr.x, curr.y, endPoint.x, endPoint.y)
    }
    path.close()
    return path
}

/**
 * M3 Expressive pull-to-refresh indicator.
 *
 * This is the native Android/Compose equivalent of ObtainX's
 * [ExpressiveRefreshIndicator] (Flutter package: expressive_refresh): it swaps
 * the legacy circular spinner for a morphing-polygon shape ([ExpressiveBlobShape])
 * inside a colored container, instead of a plain spinner. Gesture tracking is
 * done manually in BrowserActivity (see initPullToRefresh), which drives
 * [pullProgress] and [isRefreshing] into this composable via
 * [PetalRefreshBarState] - so instead of delegating to PullToRefreshBox's own
 * drag handling, the indicator itself scales in with the pull and starts its
 * indeterminate morph/rotate animation once a refresh is triggered.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RefreshBarLoadingIndicator(
    isRefreshing: Boolean,
    onRefresh: () -> Unit = {},
    pullProgress: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isRefreshing || pullProgress > 0.15f,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // While pulling (not yet refreshing) the badge scales in with the
            // drag distance; once released/refreshing it plays its normal
            // indeterminate expressive animation at full size.
            val scale = if (isRefreshing) 1f else pullProgress.coerceIn(0f, 1f)
            val description = if (isRefreshing) "Refreshing..." else "Pull to refresh"
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .semantics { stateDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp
                ) {}
                ExpressiveBlobShape(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

class PetalRefreshBarState {
    var isRefreshing by mutableStateOf(false)
    var pullProgress by mutableFloatStateOf(0f)
}

object PetalRefreshBarBridge {
    @JvmStatic
    fun bindRefreshBar(
        composeView: ComposeView,
        activity: ComponentActivity,
        state: PetalRefreshBarState
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    RefreshBarLoadingIndicator(
                        isRefreshing = state.isRefreshing,
                        pullProgress = state.pullProgress
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContainedLoadingIndicatorPreview() {
    ContainedLoadingIndicator()
}
