package com.petal.browser.compose.composable

import com.petal.browser.ui.theme.ExpressiveButtonShape
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
import androidx.compose.animation.core.snap
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
import androidx.compose.material3.LoadingIndicator
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
import com.petal.browser.ui.components.PetalCircularWavyProgressIndicator
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

/**
 * Petal Material 3 Expressive LoadingIndicator composable (from komi-store).
 * Displays an uncontained indeterminate shape-morphing loading indicator.
 *
 * @param modifier The modifier to be applied to the composable
 * @param color The tint color for the animated indicator
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val description = "Loading..."
        PetalLoadingIndicator(
            modifier = Modifier
                .requiredSize(44.dp)
                .semantics { stateDescription = description },
            size = 44.dp,
            color = color
        )
    }
}

/**
 * Material 3 Expressive Circular Wavy Loading Indicator (from komi-store).
 *
 * Displays the Material 3 Expressive circular wavy progress indicator (a ring with
 * a darker track and a wavy moving arc) across Petal.
 * Compact sizes (< 24.dp) use a thinner stroke (~2.dp) and shorter wavelength via
 * [PetalCircularWavyProgressIndicator], while sizes >= 24.dp use komi-store's defaults.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalLoadingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val indicatorColor by animateColorAsState(
        targetValue = color,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "PetalLoadingIndicatorColor"
    )

    PetalCircularWavyProgressIndicator(
        modifier = modifier.requiredSize(size),
        size = size,
        color = indicatorColor
    )
}

/**
 * Universal Loading Indicator for Petal Browser.
 *
 * Uses the uncontained Material 3 Expressive wavy loader from komi-store with proper
 * UI animations everywhere across the app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    PetalLoadingIndicator(
        modifier = modifier,
        size = size,
        color = color
    )
}

/**
 * Contained loading indicator used specifically for pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContainedRefreshLoadingIndicator(
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "RefreshBarContainerColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onPrimary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "RefreshBarIndicatorColor"
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
 *
 * Fast-flick fix: the indicator used to be mounted lazily inside AnimatedVisibility and its offset,
 * scale and alpha were chased by springs that started from zero. A quick pull moves the progress from
 * 0 to ~1 in one or two frames, so the first frames showed a half-composed, half-scaled, clipped circle
 * until the springs caught up. Now:
 *  - the indicator is always composed (warm), so the first frame of a flick already has real content;
 *  - while the finger is down, offset / scale / alpha follow the pull progress directly (no spring lag),
 *    read inside graphicsLayer so no recomposition or layout happens per touch event;
 *  - springs are only used for the settle into the refreshing state and for fading out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RefreshBarLoadingIndicator(
    isRefreshing: Boolean,
    onRefresh: () -> Unit = {},
    pullProgress: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    RefreshBarLoadingIndicatorLazy(
        isRefreshing = isRefreshing,
        pullProgressProvider = { pullProgress },
        modifier = modifier
    )
}

/**
 * Same indicator, but the pull progress is read through [pullProgressProvider] only inside
 * graphicsLayer / derivedStateOf. The touch handler pushes a new progress on every MOVE event; reading
 * a plain Float here made the whole host composition (theme + preference reads) re-run per event,
 * which starved the main thread during a pull.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RefreshBarLoadingIndicatorLazy(
    isRefreshing: Boolean,
    pullProgressProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val currentProvider by androidx.compose.runtime.rememberUpdatedState(pullProgressProvider)
    val isVisible by remember(isRefreshing) {
        androidx.compose.runtime.derivedStateOf { isRefreshing || currentProvider() > 0.01f }
    }

    // 0 -> 1 while pulling, 1 once refreshing. Bouncy settle only when it commits to refreshing.
    val refreshFactor by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "RefreshBarRefreshFactor"
    )
    // Appears instantly (no fade-in lag on a fast flick), fades out smoothly.
    val visibleTarget = if (isVisible) 1f else 0f
    val visibleFactor by animateFloatAsState(
        targetValue = visibleTarget,
        animationSpec = if (visibleTarget == 1f) snap<Float>() else spring<Float>(stiffness = Spring.StiffnessMediumLow),
        label = "RefreshBarVisibleFactor"
    )

    // The indicator slides down with graphicsLayer (draw-only, not layout), so the host view
    // never grows to fit it. Reserve the full travel (pull + bounce + shadow) up front, even while
    // idle, so the host is already the right size on the first frame of a quick flick.
    Box(modifier = modifier.fillMaxWidth().height(RefreshBarContainerHeight)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .zIndex(500f)
                .padding(top = 8.dp, bottom = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = ExpressiveButtonShape,
                color = Color.Transparent,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .requiredSize(42.dp)
                    .graphicsLayer {
                        val p = currentProvider().coerceIn(0f, 1f)
                        val r = refreshFactor
                        val dragOffsetDp = 4f + p * 36f
                        val offsetDp = dragOffsetDp + (24f - dragOffsetDp) * r
                        val dragScale = (0.5f + p * 0.5f).coerceIn(0.5f, 1.05f)
                        val dragAlpha = (p * 1.5f).coerceIn(0f, 1f)
                        translationY = offsetDp.dp.toPx()
                        scaleX = dragScale + (1f - dragScale) * r
                        scaleY = scaleX
                        alpha = (dragAlpha + (1f - dragAlpha) * r) * visibleFactor
                    }
            ) {
                ContainedRefreshLoadingIndicator(
                    modifier = Modifier.requiredSize(42.dp)
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
                    RefreshBarLoadingIndicatorLazy(
                        isRefreshing = state.isRefreshing,
                        pullProgressProvider = { state.pullProgress }
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
