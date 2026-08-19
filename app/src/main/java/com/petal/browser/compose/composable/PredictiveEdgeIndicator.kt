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

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Shared state for one edge's predictive gesture (back on the left, forward on the right).
 * progress goes 0f (not started) -> 1f (fully committed).
 */
class PetalEdgeGestureState {
    var isActive by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)

    /**
     * Cache key (see [com.petal.browser.animation.predictiveback.PagePreviewCache]) for the
     * "last page" preview that should be revealed underneath this gesture - the page/screen
     * the user is swiping back (or forward) *to*. Set right as the gesture starts so the
     * preview layer always reflects the correct destination, InstallerX-Revived style.
     */
    var previewKey by mutableStateOf<String?>(null)
}

/**
 * Circular bubble - the exact same Surface (CircleShape, same colors/elevation) peeking in
 * from an edge. Displays a thumbnail of the target destination page from PagePreviewCache,
 * falling back to ContainedLoadingIndicator if no preview is cached yet.
 */
@OptIn(com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EdgeGestureIndicator(
    isLeftEdge: Boolean,
    isActive: Boolean,
    progress: Float,
    previewKey: String? = null,
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
            val springProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = clamped,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                ),
                label = "M3EEdgeSpring"
            )
            val travelPx = 64.dp
            val currentScale = 0.5f + (springProgress * 0.55f)
            val currentAlpha = (springProgress * 2.2f).coerceIn(0f, 1f)
            val cornerPercent = (50 - (springProgress * 20).toInt()).coerceIn(30, 50)

            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerPercent),
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
                    val bitmap = com.petal.browser.animation.predictiveback.PagePreviewCache.get(previewKey)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .requiredSize(38.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        // Fallback spinner if no bitmap is cached yet for that URL
                        ContainedLoadingIndicator(
                            modifier = Modifier.requiredSize(38.dp)
                        )
                    }
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
                        progress = state.progress,
                        previewKey = state.previewKey
                    )
                }
            }
        }
    }

    /**
     * Wires up the predictive-back content transform for the *whole page* - address bar
     * header, page content, and bottom nav all animate together in [pageView] - plus a
     * "last page" preview layer that peeks in from behind it, matching InstallerX-Revived's
     * two-screen choreography instead of a flat shrink-on-scrim.
     *
     * [pageView] should be the top-level page container (activity_main's root layout) so the
     * header and bottom nav move in lockstep with the content instead of staying static while
     * only the WebView container animates.
     */
    @JvmStatic
    fun bindContentTransform(
        activity: ComponentActivity,
        pageView: android.view.View,
        backState: PetalEdgeGestureState,
        forwardState: PetalEdgeGestureState
    ) {
        // A plain ImageView inserted behind pageView in the same parent (not via
        // addContentView, which always appends on top) - this is what actually shows through
        // as pageView shrinks/rounds/fades away during the gesture.
        val previewImage = android.widget.ImageView(activity).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            visibility = android.view.View.INVISIBLE
        }
        val parent = pageView.parent as? android.view.ViewGroup
        parent?.addView(
            previewImage,
            parent.indexOfChild(pageView).coerceAtLeast(0),
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Headless 1x1 ComposeView purely to host a LaunchedEffect that drives pageView's and
        // previewImage's real (imperative) View properties every gesture tick.
        val overlayView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PredictiveContentTransformer(
                    pageView = pageView,
                    previewImage = previewImage,
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
    pageView: android.view.View,
    previewImage: android.widget.ImageView,
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
            pageView.animate().cancel()
            pageView.scaleX = 1.0f
            pageView.scaleY = 1.0f
            pageView.translationX = 0.0f
            pageView.alpha = 1.0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pageView.setRenderEffect(null)
            }
            pageView.clipToOutline = false

            previewImage.visibility = android.view.View.INVISIBLE
            previewImage.alpha = 0f
            previewImage.scaleX = 1f
            previewImage.scaleY = 1f
            previewImage.translationX = 0f
            previewImage.clipToOutline = false
        } else {
            pageView.animate().cancel()

            // Read the user's chosen animation style/direction fresh on every gesture tick so a
            // change made in Settings while the browser is running takes effect immediately.
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val animation = com.petal.browser.animation.predictiveback.PredictiveBackAnimation.fromValueOrDefault(
                sp.getString(
                    "sp_predictive_back_anim",
                    com.petal.browser.animation.predictiveback.PredictiveBackAnimation.CLASSIC.value
                ) ?: com.petal.browser.animation.predictiveback.PredictiveBackAnimation.CLASSIC.value
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
            val density = pageView.resources.displayMetrics.density

            pageView.scaleX = frame.scale
            pageView.scaleY = frame.scale
            pageView.translationX = frame.translationXDp * density
            pageView.alpha = frame.alpha

            val cornerRadiusPx = frame.cornerRadiusDp * density
            if (cornerRadiusPx > 0f) {
                pageView.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
                pageView.clipToOutline = true
            } else {
                pageView.clipToOutline = false
            }

            // "Last page" preview layer: whichever page/screen this gesture is heading
            // towards, peeking in from behind pageView as it shrinks out of the way -
            // the InstallerX-Revived two-screen look instead of a flat shrink-on-scrim.
            val activeState = if (isLeft) backState else forwardState
            val previewBitmap = com.petal.browser.animation.predictiveback.PagePreviewCache.get(activeState.previewKey)
            if (previewBitmap != null) {
                if (previewImage.drawable == null || (previewImage.tag as? String) != activeState.previewKey) {
                    previewImage.setImageBitmap(previewBitmap)
                    previewImage.tag = activeState.previewKey
                }
                previewImage.visibility = android.view.View.VISIBLE

                val underlay = com.petal.browser.animation.predictiveback.PredictiveBackStyle.underlayFrameFor(
                    animation = animation,
                    exitDirection = exitDirection,
                    progress = progress,
                    isLeftEdge = isLeft,
                )
                previewImage.scaleX = underlay.scale
                previewImage.scaleY = underlay.scale
                previewImage.translationX = underlay.translationXDp * density
                previewImage.alpha = underlay.alpha

                val underlayCornerPx = underlay.cornerRadiusDp * density
                if (underlayCornerPx > 0f) {
                    previewImage.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, underlayCornerPx)
                        }
                    }
                    previewImage.clipToOutline = true
                } else {
                    previewImage.clipToOutline = false
                }
            } else {
                previewImage.visibility = android.view.View.INVISIBLE
            }
        }
    }
}
