package com.petal.browser.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlin.math.roundToInt

/**
 * Shared state for one edge's predictive gesture (back on the left, forward on the right).
 * progress goes 0f (not started) -> 1f (fully committed).
 */
class PetalEdgeGestureState {
    var isActive by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
}

/**
 * Circular bubble matching RefreshBarLoadingIndicator's styling, peeking in from the
 * given edge as the gesture progresses, with a directional chevron instead of a spinner.
 */
@Composable
fun EdgeGestureIndicator(
    isLeftEdge: Boolean,
    isActive: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxHeight()
    ) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = if (isLeftEdge) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            val clamped = progress.coerceIn(0f, 1f)
            // Bubble starts mostly off-screen at the edge and slides in as the user drags,
            // the same way the refresh bubble scales/fades in rather than appearing at full size instantly.
            val travelPx = 56.dp
            val currentScale = 0.5f + (clamped * 0.5f)
            val currentAlpha = (clamped * 2.2f).coerceIn(0f, 1f)

            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 12.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .graphicsLayer {
                        val offsetPx = travelPx.toPx() * (1f - clamped)
                        translationX = if (isLeftEdge) -offsetPx else offsetPx
                        alpha = currentAlpha
                        scaleX = currentScale
                        scaleY = currentScale
                    }
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .requiredSize(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLeftEdge) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isLeftEdge) "Back" else "Forward",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.requiredSize(28.dp)
                    )
                }
            }
        }
    }
}

object PetalEdgeIndicatorBridge {
    @JvmStatic
    fun bind(
        composeView: ComposeView,
        activity: ComponentActivity,
        state: PetalEdgeGestureState,
        isLeftEdge: Boolean
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
                val colorStyle = try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    EdgeGestureIndicator(
                        isLeftEdge = isLeftEdge,
                        isActive = state.isActive,
                        progress = state.progress
                    )
                }
            }
        }
    }
}
