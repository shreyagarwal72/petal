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
import kotlinx.coroutines.flow.collectLatest
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
 * Always indeterminate so the Material 3 Expressive continuous shape-morphing animation runs.
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

/** Where the indicator waits (hidden behind the bar) and where it rests while refreshing. */
private val RefreshIndicatorStartY = (-40).dp
private val RefreshIndicatorRestY = 12.dp

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
    RefreshBarLoadingIndicatorLazy(
        isRefreshing = isRefreshing,
        pullProgressProvider = { pullProgress },
        modifier = modifier
    )
}

/**
 * Pull-to-refresh indicator combining interactive pulling physics with continuous Material 3 Expressive shape morphing:
 *  - While dragging: indicator tracks finger drag directly with decelerating slide from [RefreshIndicatorStartY] to [RefreshIndicatorRestY];
 *  - Tactile haptic feedback and release-to-refresh spring pop when commit threshold is reached;
 *  - Bouncy settle into resting position on refresh commit, smooth exit when complete;
 *  - Indeterminate M3 Expressive morph animation runs continuously across all phases.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RefreshBarLoadingIndicatorLazy(
    isRefreshing: Boolean,
    pullProgressProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val currentProvider by androidx.compose.runtime.rememberUpdatedState(pullProgressProvider)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    // One value drives everything: `shown` (0 = hidden, 1 = fully pulled / resting while refreshing).
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    var ready by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(isRefreshing) {
        androidx.compose.runtime.snapshotFlow { currentProvider() }
            .collectLatest { raw ->
                val p = raw.coerceIn(0f, 1f)
                if (p > 0f) {
                    shown.snapTo(p)
                    val nowReady = p >= 1f
                    if (nowReady && !ready) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }
                    if (nowReady != ready && (nowReady || p < 0.85f)) ready = nowReady
                } else {
                    ready = false
                    shown.animateTo(
                        targetValue = if (isRefreshing) 1f else 0f,
                        animationSpec = if (isRefreshing) {
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        } else {
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        }
                    )
                }
            }
    }

    // Small "release to refresh" pop when the pull reaches the commit distance.
    val pop by animateFloatAsState(
        targetValue = if (ready) 1.14f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "LoadingIndicatorPop"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        ContainedRefreshLoadingIndicator(
            modifier = Modifier.graphicsLayer {
                // All reads happen in draw phase: no recomposition per touch event.
                val s = shown.value.coerceAtLeast(0f)
                val t = if (s <= 1f) 1f - (1f - s) * (1f - s) else s
                val startY = RefreshIndicatorStartY.toPx()
                val restY = RefreshIndicatorRestY.toPx()
                translationY = startY + (restY - startY) * t
                val sc = (0.4f + 0.6f * t) * pop
                scaleX = sc
                scaleY = sc
                alpha = (s * 2f).coerceIn(0f, 1f)
            }
        )
    }
}

class PetalRefreshBarState {
    var isRefreshing by mutableStateOf(false)
    var pullProgress by mutableFloatStateOf(0f)
}

object PetalRefreshBarBridge {
    /**
     * For hosts that show a [com.petal.browser.browser.PetalVerticalSwipeRefreshLayout] (e.g. the tab
     * view) in plain Views: adds a pass-through Compose overlay on top of [parent] and feeds it from
     * the layout's pull/refresh callbacks. The gesture itself is still Mozilla's SwipeRefreshFeature.
     */
    @JvmStatic
    fun attachTo(
        parent: android.view.ViewGroup,
        activity: ComponentActivity,
        layout: com.petal.browser.browser.PetalVerticalSwipeRefreshLayout
    ) {
        val state = PetalRefreshBarState()
        layout.onPullProgress = { state.pullProgress = it }
        layout.onRefreshStateChanged = { refreshing ->
            state.isRefreshing = refreshing
            if (!refreshing) state.pullProgress = 0f
        }
        val overlay = ComposeView(activity).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            translationZ = 100f
        }
        bindRefreshBar(overlay, activity, state)
        val lp = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP
        )
        parent.addView(overlay, lp)
        // If the page runs under the status bar, keep the indicator below it.
        parent.post {
            val bars = androidx.core.view.ViewCompat.getRootWindowInsets(parent)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
            val loc = IntArray(2)
            parent.getLocationInWindow(loc)
            val extra = (bars - loc[1]).coerceAtLeast(0)
            if (extra != lp.topMargin) {
                lp.topMargin = extra
                overlay.layoutParams = lp
            }
        }
    }

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
