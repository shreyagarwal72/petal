package com.petal.browser.compose.composable

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * Shared state for one edge's predictive gesture (back on the left, forward on the right).
 * progress goes 0f (not started) -> 1f (fully committed).
 */
class PetalEdgeGestureState {
    var isActive by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
}

/**
 * Circular bubble - the exact same Surface (CircleShape, same colors/elevation) and the
 * exact same ContainedLoadingIndicator spinner used by RefreshBarLoadingIndicator in
 * ContainedLoadingIndicator.kt, just peeking in from an edge instead of the top.
 */
@OptIn(com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi::class)
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
                    // Same call, same size, as the refresh spinner in ContainedLoadingIndicator.kt.
                    ContainedLoadingIndicator(
                        modifier = Modifier.requiredSize(38.dp)
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
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.GS_FLEX }
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

    @JvmStatic
    fun bindContentTransform(
        activity: ComponentActivity,
        contentView: android.view.View,
        backState: PetalEdgeGestureState,
        forwardState: PetalEdgeGestureState
    ) {
        val overlayView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PredictiveContentTransformer(
                    contentView = contentView,
                    backState = backState,
                    forwardState = forwardState
                )
            }
        }
        activity.addContentView(
            overlayView,
            android.widget.FrameLayout.LayoutParams(1, 1)
        )
    }
}

@Composable
fun PredictiveContentTransformer(
    contentView: android.view.View,
    backState: PetalEdgeGestureState,
    forwardState: PetalEdgeGestureState
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backActive = backState.isActive
    val backProgress = backState.progress
    val forwardActive = forwardState.isActive
    val forwardProgress = forwardState.progress

    androidx.compose.runtime.LaunchedEffect(backActive, backProgress, forwardActive, forwardProgress) {
        val isLeft = backActive || (!forwardActive && backProgress > 0f)
        val active = backActive || forwardActive
        val rawProgress = if (isLeft) backProgress else forwardProgress
        val progress = rawProgress.coerceIn(0f, 1f)

        if (!active && progress == 0f) {
            contentView.animate().cancel()
            contentView.scaleX = 1.0f
            contentView.scaleY = 1.0f
            contentView.translationX = 0.0f
            contentView.alpha = 1.0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                contentView.setRenderEffect(null)
            }
            contentView.clipToOutline = false
        } else {
            contentView.animate().cancel()

            // Read the user's chosen animation style/direction fresh on every gesture tick so a
            // change made in Settings while the browser is running takes effect immediately.
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val animation = com.petal.browser.animation.predictiveback.PredictiveBackAnimation.fromValueOrDefault(
                sp.getString(
                    "sp_predictive_back_anim",
                    com.petal.browser.animation.predictiveback.PredictiveBackAnimation.AOSP.value
                ) ?: com.petal.browser.animation.predictiveback.PredictiveBackAnimation.AOSP.value
            )
            val exitDirection = com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.fromValueOrDefault(
                sp.getString(
                    "sp_predictive_back_exit_dir",
                    com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.ALWAYS_RIGHT.value
                ) ?: com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.ALWAYS_RIGHT.value
            )

            val frame = com.petal.browser.animation.predictiveback.PredictiveBackStyle.frameFor(
                animation = animation,
                exitDirection = exitDirection,
                progress = progress,
                isLeftEdge = isLeft,
            )
            val density = contentView.resources.displayMetrics.density

            contentView.scaleX = frame.scale
            contentView.scaleY = frame.scale
            contentView.translationX = frame.translationXDp * density
            contentView.alpha = frame.alpha

            val cornerRadiusPx = frame.cornerRadiusDp * density
            if (cornerRadiusPx > 0f) {
                contentView.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
                contentView.clipToOutline = true
            } else {
                contentView.clipToOutline = false
            }
        }
    }
}
