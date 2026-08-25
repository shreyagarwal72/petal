package com.petal.browser.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Surface
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

/**
 * Petal Material 3 Expressive ContainedLoadingIndicator composable.
 * Displays an indeterminate loading indicator filling available screen bounds.
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
        ContainedLoadingIndicator(
            modifier = Modifier
                .requiredSize(64.dp)
                .semantics { stateDescription = description }
        )
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
    val isVisible = isRefreshing || pullProgress > 0.01f

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        exit = slideOutVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Fixed height tall enough to contain the circle's full travel range
                // (its own ~58dp size plus the largest translationY offset used below,
                // 64dp, plus margin). translationY is a paint-time transform - it never
                // changes this Box's measured size, so without reserving space for the
                // worst case up front, the ComposeView hosting this clips the circle
                // wherever its un-translated resting bounds happened to end.
                .height(140.dp)
                .zIndex(500f)
                .padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val offsetY = if (isRefreshing) 24.dp else if (!isVisible) 0.dp else (pullProgress.coerceIn(0f, 1f) * 64.dp.value).dp
            val currentOpacity = if (isRefreshing) 1.0f else if (!isVisible) 0f else (pullProgress * 1.8f).coerceIn(0f, 1f)
            val currentScale = if (isRefreshing) 1.0f else if (!isVisible) 0f else (0.3f + (pullProgress * 0.7f)).coerceIn(0.3f, 1.0f)

            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 12.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = if (isVisible) offsetY.toPx() else 0f
                        alpha = if (isVisible) currentOpacity else 0f
                        scaleX = if (isVisible) currentScale else 0f
                        scaleY = if (isVisible) currentScale else 0f
                    }
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .requiredSize(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator(
                        modifier = Modifier.requiredSize(38.dp)
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
