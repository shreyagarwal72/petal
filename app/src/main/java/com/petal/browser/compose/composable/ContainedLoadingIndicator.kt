package com.petal.browser.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

/**
 * Reusable Material 3 Expressive "Contained Loading Indicator".
 * Features a circular background container housing an inner shape that continuously
 * morphs between rounded geometric shapes (pentagon and triangle) using
 * [RoundedPolygon] and [Morph] from androidx.graphics.shapes driven by an infiniteTransition loop.
 *
 * @param modifier Standard composable modifier.
 * @param containerSize Diameter of the outer circular container (default: 64.dp).
 * @param containerColor Background color of the container (default: MaterialTheme.colorScheme.primary).
 * @param indicatorColor Foreground color of the morphing indicator shape (default: MaterialTheme.colorScheme.onPrimary).
 * @param animationDurationMillis Duration in milliseconds for one full morph cycle (default: 1800ms).
 */
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerSize: Dp = 64.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    animationDurationMillis: Int = 1800
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(containerSize),
            shadowElevation = 6.dp,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "containedLoadingIndicatorTransition")

                val morphProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(animationDurationMillis, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "morphProgress"
                )

                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(animationDurationMillis * 2, easing = LinearEasing)
                    ),
                    label = "rotationAngle"
                )

                Canvas(modifier = Modifier.fillMaxSize(0.52f)) {
                    val radius = size.minDimension / 2f
                    val center = size.width / 2f

                    val pentagon = RoundedPolygon(
                        numVertices = 5,
                        radius = radius,
                        centerX = center,
                        centerY = center,
                        rounding = CornerRounding(radius * 0.25f)
                    )

                    val triangle = RoundedPolygon(
                        numVertices = 3,
                        radius = radius,
                        centerX = center,
                        centerY = center,
                        rounding = CornerRounding(radius * 0.35f)
                    )

                    val morph = Morph(pentagon, triangle)
                    val composePath = morph.toPath(morphProgress).asComposePath()

                    rotate(degrees = rotationAngle) {
                        drawPath(
                            path = composePath,
                            color = indicatorColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * RefreshBar pull-to-refresh loading indicator utilizing [ContainedLoadingIndicator].
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
        enter = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialOffsetY = { -it }) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        exit = slideOutVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), targetOffsetY = { -it }) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            ContainedLoadingIndicator(
                containerSize = 48.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.GS_FLEX }
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

@Preview(name = "Contained Loading Indicator - Dark Theme", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ContainedLoadingIndicatorPreview() {
    PetalExpressiveTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }
    }
}
