package com.petal.browser.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.LaunchedEffect
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Petal Material 3 Expressive ContainedLoadingIndicator composable.
 * Displays an indeterminate loading indicator filling available screen bounds.
 *
 * When Liquid Glass is ON: Uses SimpMusic's dynamic polygon-morphing indicator
 * with glass refraction, specular highlight, and translucent container.
 * When Liquid Glass is OFF: Uses the standard Zenith primary contained indicator.
 *
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
        PetalContainedLoadingIndicator(
            modifier = Modifier
                .requiredSize(44.dp)
                .semantics { stateDescription = description }
        )
    }
}

/**
 * Universal Contained Loading Indicator for Petal Browser.
 *
 * If Liquid Glass is enabled:
 * - Dynamically morphs between Material 3 Expressive polygons (SimpMusic style).
 * - Applies glass surface color, specular edge reflection, and primary indicator glow.
 *
 * If Liquid Glass is disabled:
 * - Falls back to the classic Zenith-style primary contained indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    if (isGlass) {
        val polygons = remember {
            try {
                LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
            } catch (_: Throwable) {
                emptyList()
            }
        }

        if (polygons.isNotEmpty()) {
            var containerShape by remember {
                mutableStateOf(polygons.first())
            }

            LaunchedEffect(Unit) {
                while (isActive) {
                    try {
                        containerShape = polygons.random()
                    } catch (_: Throwable) {}
                    delay(650)
                }
            }

            // When Liquid Glass is ON: follow SimpMusic's container & indicator colors (surfaceContainerHighest & onSurfaceVariant)
            // with liquid glass chrome, instead of Material You / primary colors
            val glassContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
            val glassIndicator = MaterialTheme.colorScheme.onSurfaceVariant
            val currentShape = containerShape.toShape()

            Crossfade(
                targetState = currentShape,
                animationSpec = tween(400),
                modifier = modifier,
                label = "LiquidGlassMorphingCrossfade"
            ) { shape ->
                androidx.compose.material3.ContainedLoadingIndicator(
                    modifier = Modifier
                        .requiredSize(size)
                        .liquidGlassChrome(shape = shape, enabled = true),
                    polygons = polygons,
                    containerColor = glassContainer,
                    indicatorColor = glassIndicator,
                    containerShape = shape
                )
            }
        } else {
            ZenithContainedLoadingIndicator(
                modifier = modifier.requiredSize(size)
            )
        }
    } else {
        ZenithContainedLoadingIndicator(
            modifier = modifier.requiredSize(size)
        )
    }
}

/**
 * Zenith-style Material 3 Expressive contained loading indicator.
 *
 * This mirrors Zenith's implementation: the container uses the theme primary
 * color and the animated indicator uses onPrimary, so it automatically follows
 * Petal's light/dark and dynamic color schemes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ZenithContainedLoadingIndicator(
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ZenithContainedLoadingContainerColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onPrimary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ZenithContainedLoadingIndicatorColor"
    )

    androidx.compose.material3.ContainedLoadingIndicator(
        modifier = modifier,
        containerColor = containerColor,
        indicatorColor = indicatorColor
    )
}

/** Room for the whole indicator travel: 8 top pad + 40 max pull + 42 circle + bounce/shadow. */
private val RefreshBarContainerHeight = 120.dp

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
    val isVisible = isRefreshing || pullProgress > 0.01f

    // The indicator slides down with graphicsLayer (draw-only, not layout), so the host view
    // never grows to fit it. The old container was only 66dp tall, but a fast pull sends the
    // 42dp circle ~48dp down, so about half of it was drawn outside its host and got cut off.
    // Reserve the full travel (pull + bounce + shadow) up front, even while idle, so the host
    // is already the right size on the first frame of a quick flick.
    Box(modifier = modifier.fillMaxWidth().height(RefreshBarContainerHeight)) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .zIndex(500f)
                .padding(top = 8.dp, bottom = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val targetOffsetY = if (isRefreshing) {
                24.dp
            } else if (!isVisible) {
                0.dp
            } else {
                (4.dp.value + (pullProgress.coerceIn(0f, 1f) * 36.dp.value)).dp
            }

            val animatedOffsetY by animateFloatAsState(
                targetValue = targetOffsetY.value,
                animationSpec = if (isRefreshing) {
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                } else {
                    spring(stiffness = Spring.StiffnessHigh)
                },
                label = "RefreshBarIndicatorOffsetY"
            )

            val currentOpacity = if (isRefreshing) 1.0f else if (!isVisible) 0f else (pullProgress * 1.5f).coerceIn(0f, 1f)
            val animatedOpacity by animateFloatAsState(
                targetValue = currentOpacity,
                animationSpec = spring(stiffness = Spring.StiffnessHigh),
                label = "RefreshBarIndicatorOpacity"
            )

            val targetScale = if (isRefreshing) 1.0f else if (!isVisible) 0f else (0.5f + (pullProgress.coerceIn(0f, 1f) * 0.5f)).coerceIn(0.5f, 1.05f)
            // Bouncy settle once the indicator commits to refreshing (target snaps to 1.0),
            // and smooth fluid scaling during pull.
            val currentScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = if (isRefreshing) {
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                } else {
                    spring(stiffness = Spring.StiffnessHigh)
                },
                label = "RefreshBarIndicatorScale"
            )

            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .requiredSize(42.dp)
                    .graphicsLayer {
                        translationY = animatedOffsetY.dp.toPx()
                        alpha = animatedOpacity
                        scaleX = currentScale
                        scaleY = currentScale
                    }
            ) {
                PetalContainedLoadingIndicator(
                    modifier = Modifier.requiredSize(42.dp),
                    size = 42.dp
                )
            }
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
            setViewTreeViewModelStoreOwner(activity)
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
                    com.petal.browser.ui.theme.AppFont.fromName(fontName)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Petal Contained Loading Indicator Preview", showBackground = true)
@Composable
private fun ContainedLoadingIndicatorPreview() {
    PetalExpressiveTheme(darkTheme = true) {
        ContainedLoadingIndicator()
    }
}
