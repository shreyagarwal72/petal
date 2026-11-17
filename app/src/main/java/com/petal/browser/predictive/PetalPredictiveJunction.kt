/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.predictive

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.SharedPreferences
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global settings & state junction for Predictive Back and Depth Blur effects across the Petal App.
 *
 * Ported 1:1 from RvSystem-Monitor & PixelPlayer's official architecture.
 */
object PetalPredictiveJunction {
    const val KEY_PREDICTIVE_BACK_ENABLED = "sp_predictive_back_junction_enabled"
    const val KEY_DEPTH_BLUR_ENABLED = "sp_depth_blur_junction_enabled"
    const val KEY_LIVE_UNDERLAY_ENABLED = "sp_live_underlay_junction_enabled"
    const val KEY_BACK_STYLE = "sp_predictive_back_style"
    const val KEY_BACK_EXIT_DIRECTION = "sp_predictive_back_exit_direction"

    private val _isPredictiveBackEnabled = MutableStateFlow(true)
    val isPredictiveBackEnabled: StateFlow<Boolean> = _isPredictiveBackEnabled.asStateFlow()

    private val _isDepthBlurEnabled = MutableStateFlow(true)
    val isDepthBlurEnabled: StateFlow<Boolean> = _isDepthBlurEnabled.asStateFlow()

    private val _isLiveUnderlayEnabled = MutableStateFlow(true)
    val isLiveUnderlayEnabled: StateFlow<Boolean> = _isLiveUnderlayEnabled.asStateFlow()

    private val _backStyle = MutableStateFlow(PetalBackStyle.Petal)
    val backStyle: StateFlow<PetalBackStyle> = _backStyle.asStateFlow()

    private val _backExitDirection = MutableStateFlow(PetalBackExitDirection.AlwaysRight)
    val backExitDirection: StateFlow<PetalBackExitDirection> = _backExitDirection.asStateFlow()

    @JvmStatic
    fun setBackStyle(prefs: SharedPreferences, style: PetalBackStyle) {
        _backStyle.value = style
        prefs.edit().putString(KEY_BACK_STYLE, style.key).apply()
    }

    @JvmStatic
    fun setBackExitDirection(prefs: SharedPreferences, direction: PetalBackExitDirection) {
        _backExitDirection.value = direction
        prefs.edit().putString(KEY_BACK_EXIT_DIRECTION, direction.key).apply()
    }

    @JvmStatic
    fun setLiveUnderlayEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isLiveUnderlayEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LIVE_UNDERLAY_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun init(prefs: SharedPreferences) {
        _isLiveUnderlayEnabled.value = prefs.getBoolean(KEY_LIVE_UNDERLAY_ENABLED, true)
        _isPredictiveBackEnabled.value = prefs.getBoolean(KEY_PREDICTIVE_BACK_ENABLED, true)
        _isDepthBlurEnabled.value = prefs.getBoolean(KEY_DEPTH_BLUR_ENABLED, true)
        val defaultBackStyle = PetalBackStyle.Petal
        _backStyle.value = PetalBackStyle.fromKey(prefs.getString(KEY_BACK_STYLE, defaultBackStyle.key))
        _backExitDirection.value = PetalBackExitDirection.fromKey(prefs.getString(KEY_BACK_EXIT_DIRECTION, null))
    }

    @JvmStatic
    fun setPredictiveBackEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isPredictiveBackEnabled.value = enabled
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun setDepthBlurEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isDepthBlurEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DEPTH_BLUR_ENABLED, enabled).apply()
    }

    /**
     * Checks if the current Android OS level supports native predictive back gesture dispatch.
     * Android 14+ (API 34, UPSIDE_DOWN_CAKE) is the official platform requirement.
     */
    val isDeviceSupported: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * Set of page keys / sub-flows that do not support or participate in full-card predictive back gestures.
     * (E.g. in-screen category drilldowns, selection modes, dialog sub-steps).
     */
    private val NON_PREDICTIVE_PAGES = setOf(
        "CATEGORY_DRILLDOWN",
        "SELECTION_MODE",
        "DIALOG_STEP",
        "INACTIVE_SETTINGS"
    )

    /**
     * Checks whether a given page or screen supports full-card predictive back gesture navigation.
     */
    @JvmStatic
    fun isPagePredictiveSupported(pageKey: String?): Boolean {
        if (pageKey == null) return false
        return pageKey !in NON_PREDICTIVE_PAGES
    }

    /**
     * Determines whether Zenith-like header transition / animation should be active.
     * Triggers when:
     * 1) Predictive animations are toggled off by the user (_isPredictiveBackEnabled == false), OR
     * 2) Device is not supported (API < 34 / Android 13 or lower), OR
     * 3) Current page / transition is a non-supported page.
     */
    @JvmStatic
    fun shouldUseZenithHeaderTransition(
        isPredictiveSettingEnabled: Boolean = _isPredictiveBackEnabled.value,
        pageKey: String? = null
    ): Boolean {
        if (!isPredictiveSettingEnabled || !isDeviceSupported) return true
        if (pageKey != null && !isPagePredictiveSupported(pageKey)) return true
        return false
    }
}

val LocalPetalPredictiveJunctionState = compositionLocalOf { true }
val LocalPetalDepthBlurJunctionState = compositionLocalOf { true }
val LocalIsUnderlayPreview = compositionLocalOf { false }
val LocalZenithHeaderActive = compositionLocalOf { false }
val LocalZenithHeaderHoisted = compositionLocalOf { false }

/**
 * Live state of an in-flight predictive back gesture.
 */
data class PredictiveBackState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val swipeEdge: Int = BackEventCompat.EDGE_LEFT,
    /** True when the real surface behind the overlay is being animated instead of a snapshot. */
    val liveUnderlay: Boolean = false,
    val touchY: Float = 0f,
    val initialTouchY: Float = 0f,
    /** True once the finger lifted and the dismissal is settling to completion. */
    val committing: Boolean = false,
    val releaseProgress: Float = 0f,
    /** Progress units per second at the moment of release. */
    val releaseVelocity: Float = 0f,
    /** Linear 0..1 position inside the commit settle. */
    val settleTime: Float = 0f,
    /**
     * True for a short window after a committed back: the dismissed screen stays parked in its
     * final (invisible) pose instead of snapping back to full size while the host's own exit
     * animation (AnimatedVisibility fade/slide, NavHost pop) catches up. Without this the screen
     * popped back to 1.0 scale and then animated out a second time.
     */
    val dismissed: Boolean = false,
) {
    companion object {
        val Idle = PredictiveBackState()
    }
}

val LocalPredictiveBackState = compositionLocalOf { PredictiveBackState.Idle }

private const val HOLD_DISMISSED_MS = 500L

/**
 * Wraps [content] with a [PredictiveBackHandler] and republishes gesture progress
 * through [LocalPredictiveBackState] so any descendant can react to it live.
 *
 * Renders the real Home Screen surface in the background with 24.dp depth blur
 * during in-flight back gestures, matching RvSystem-Monitor & PixelPlayer 1:1.
 */
@Composable
fun PetalPredictiveBackSurface(
    enabled: Boolean = true,
    /** Park the dismissed screen in its final pose after commit. False when [onBack] only pops an inner page. */
    holdDismissedPose: Boolean = true,
    /** Let the host activity animate the real surface behind (parallax/dim/blur) during this gesture. */
    useLiveUnderlay: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsStateWithLifecycle()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsStateWithLifecycle()
    val isUnderlayPreview = LocalIsUnderlayPreview.current
    val progressAnim = remember { Animatable(0f) }
    var backState by remember { mutableStateOf(PredictiveBackState.Idle) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val screenWidthPx = configuration.screenWidthDp * density
    val screenHeightPx = configuration.screenHeightDp * density

    if (!isUnderlayPreview && enabled) {
        if (junctionPredictiveEnabled) {
            PredictiveBackHandler(enabled = true) { progressFlow ->
                // The style is latched per gesture so changing it mid-swipe can't glitch the frame.
                val style = PetalPredictiveJunction.backStyle.value
                val exitDirection = PetalPredictiveJunction.backExitDirection.value
                val holdPose = holdDismissedPose
                val wantsLive = useLiveUnderlay
                var committedDone = false
                var gestureStarted = false
                var liveUnderlay = false
                var initialTouchY = 0f
                var lastProgress = 0f
                var lastNanos = 0L
                var velocity = 0f

                fun publishUnderlay(state: PredictiveBackState) {
                    if (!liveUnderlay) return
                    val frame = PetalBackMotion.frame(
                        style,
                        state.toInput(style, exitDirection, junctionBlurEnabled, screenWidthPx, screenHeightPx, density),
                    )
                    PetalLiveUnderlay.publish(PetalBackMotion.behindValues(frame))
                    PetalLiveUnderlay.progress(state.progress, state.swipeEdge)
                }

                try {
                    progressFlow.collect { backEvent ->
                        val now = System.nanoTime()
                        if (!gestureStarted) {
                            gestureStarted = true
                            initialTouchY = backEvent.touchY
                            lastProgress = backEvent.progress
                            lastNanos = now
                            liveUnderlay = wantsLive && PetalLiveUnderlay.begin(backEvent.swipeEdge)
                        } else {
                            val dt = (now - lastNanos) / 1_000_000_000f
                            if (dt > 0.001f) {
                                val instant = (backEvent.progress - lastProgress) / dt
                                velocity = velocity * 0.5f + instant * 0.5f
                                lastProgress = backEvent.progress
                                lastNanos = now
                            }
                        }
                        progressAnim.snapTo(backEvent.progress)
                        backState = PredictiveBackState(
                            isActive = true,
                            progress = backEvent.progress,
                            swipeEdge = backEvent.swipeEdge,
                            liveUnderlay = liveUnderlay,
                            touchY = backEvent.touchY,
                            initialTouchY = initialTouchY,
                            releaseVelocity = velocity,
                        )
                        publishUnderlay(backState)
                    }

                    // Finger lifted and the system committed the back: settle the dismissal.
                    val release = backState.progress
                    backState = backState.copy(committing = true, releaseProgress = release, settleTime = 0f)
                    if (style.usesSpringCommit) {
                        progressAnim.snapTo(release)
                        progressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ) {
                            val settle = ((value - release) / (1f - release).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                            backState = backState.copy(progress = value, settleTime = settle)
                            publishUnderlay(backState)
                        }
                    } else {
                        val settleAnim = Animatable(0f)
                        settleAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(style.commitDurationMs, easing = LinearEasing)
                        ) {
                            val eased = style.commitEasing.transform(value)
                            backState = backState.copy(
                                progress = release + (1f - release) * eased,
                                settleTime = value,
                            )
                            publishUnderlay(backState)
                        }
                    }
                    backState = backState.copy(progress = 1f, settleTime = 1f)
                    publishUnderlay(backState)
                    try {
                        onBack()
                        committedDone = true
                        if (holdPose) {
                            backState = PredictiveBackState.Idle.copy(dismissed = true)
                            kotlinx.coroutines.delay(HOLD_DISMISSED_MS)
                        }
                    } finally {
                        if (liveUnderlay) PetalLiveUnderlay.end(true)
                        progressAnim.snapTo(0f)
                        backState = PredictiveBackState.Idle
                    }
                } catch (e: CancellationException) {
                    val from = backState.progress
                    backState = backState.copy(committing = false)
                    progressAnim.snapTo(from)
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = if (style.usesSpringCommit) {
                            spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        } else {
                            spring(stiffness = 1500f, dampingRatio = Spring.DampingRatioNoBouncy)
                        }
                    ) {
                        backState = backState.copy(isActive = true, progress = value)
                        publishUnderlay(backState)
                    }
                    if (liveUnderlay) PetalLiveUnderlay.end(false)
                    backState = PredictiveBackState.Idle
                }
            }
        } else {
            // Instant, standard back navigation when predictive animations are toggled off
            androidx.activity.compose.BackHandler(enabled = true, onBack = onBack)
        }
    }

    val isZenithHeaderActive = remember(junctionPredictiveEnabled) {
        PetalPredictiveJunction.shouldUseZenithHeaderTransition(junctionPredictiveEnabled)
    }

    CompositionLocalProvider(
        LocalPetalPredictiveJunctionState provides junctionPredictiveEnabled,
        LocalPetalDepthBlurJunctionState provides junctionBlurEnabled,
        LocalPredictiveBackState provides backState,
        LocalZenithHeaderActive provides isZenithHeaderActive,
    ) {
        content()
    }
}

/** Maps live gesture state onto the engine's input for a surface of the given size. */
fun PredictiveBackState.toInput(
    style: PetalBackStyle,
    exitDirection: PetalBackExitDirection,
    blurEnabled: Boolean,
    widthPx: Float,
    heightPx: Float,
    density: Float,
): PetalBackInput = PetalBackInput(
    progress = progress,
    swipeEdge = swipeEdge,
    touchY = if (touchY == 0f && initialTouchY == 0f) heightPx / 2f else touchY,
    initialTouchY = if (touchY == 0f && initialTouchY == 0f) heightPx / 2f else initialTouchY,
    widthPx = widthPx,
    heightPx = heightPx,
    density = density,
    blurEnabled = blurEnabled,
    committing = committing,
    releaseProgress = releaseProgress,
    releaseVelocity = releaseVelocity,
    settleTime = settleTime,
    exitDirection = exitDirection,
)

/**
 * Screen wrapper that applies predictive back visual effects and depth blur to full-screen Petal surfaces.
 * Ported 1:1 from RvSystem-Monitor & PixelPlayer:
 * - Background (behind) surface: 24.dp depth blur + black dim overlay (0.40f/0.75f clearing as gesture completes)
 *   with aospSharedAxisPopEnter parallax slide in (-33% -> 0).
 * - Foreground (top) surface: crisp scale down (1.0 -> 0.85) + 32.dp corner clipping + 16.dp drop shadow + aospSharedAxisPopExit slide offset (+50% right / -50% left).
 * - AnimatedVisibilityScope support: live 32dp corner radius, 24dp depth blur, and dim alpha transition specs during NavHost transitions.
 */
@Composable
fun PetalScreenWrapper(
    navController: NavController? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isBehind: Boolean = false,
    backgroundSnapshot: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    /**
     * Optional 0..1 "how far is the screen above me pushed in" read in the DRAW phase. When set, a
     * behind screen eases its resting pose (parallax, dim, corners, depth blur) in with the push
     * instead of snapping to it, and the resting pose is the exact frame-0 pose of the selected
     * back style so the first frame of a back gesture continues from it without a jump.
     */
    behindFraction: (() -> Float)? = null,
    /**
     * Gesture state to pose this wrapper with. Defaults to [LocalPredictiveBackState]; a sub-page host
     * passes its own gesture here so the parent page behind it is posed by the sub-page's gesture
     * while the parent's content still sees the outer page's state.
     */
    gestureState: PredictiveBackState? = null,
    /**
     * Give this page its own liquid-glass wallpaper. The app theme provides ONE shared wallpaper;
     * while two pages exist at once (open, close, predictive back) both recorded into it and fought
     * over its position, so the glass showed the wrong patch. Pass false only for a screen that
     * draws no background of its own and still wants to refract the shared one.
     */
    isolateGlassWallpaper: Boolean = true,
    customGlassWallpaper: com.petal.browser.ui.glass.PetalGlassWallpaper? = null,
    content: @Composable () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialCurrentState = lifecycleOwner.lifecycle.currentStateAsState().value
    var isResumed by remember { mutableStateOf(initialCurrentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsStateWithLifecycle()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsStateWithLifecycle()

    val predictiveEnabled = junctionPredictiveEnabled
    val blurEnabled = junctionBlurEnabled
    val predictiveBackState = gestureState ?: LocalPredictiveBackState.current

    val outerGlassWallpaper = com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current
    val ownGlassWallpaper =
        if (customGlassWallpaper != null) null
        else if (isolateGlassWallpaper && outerGlassWallpaper != null) com.petal.browser.ui.glass.rememberPetalGlassWallpaper()
        else null
    val pageGlassWallpaper = customGlassWallpaper ?: ownGlassWallpaper ?: outerGlassWallpaper

    val myEntry = lifecycleOwner as? NavBackStackEntry
    val previousEntryId = navController?.previousBackStackEntry?.id
    val isBehindTopScreen = isBehind || (navController != null && myEntry != null && previousEntryId == myEntry.id)

    val transition = animatedVisibilityScope?.transition

    val transitionCornerRadius = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
            label = "cornerRadius"
        ) { state ->
            if (state == EnterExitState.PostExit || state == EnterExitState.PreEnter) 32f else 0f
        }
        animatedValue
    } else 0f

    val transitionDimAlpha = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)) },
            label = "dimAlpha"
        ) { state ->
            if (isBehindTopScreen && (state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
                if (!blurEnabled) 0.75f else 0.40f
            } else 0f
        }
        animatedValue
    } else 0f

    val transitionBlurRadiusDp = if (transition != null) {
        val animatedValue by transition.animateDp(
            transitionSpec = { tween(durationMillis = 350, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)) },
            label = "blurRadius"
        ) { state ->
            if (isBehindTopScreen && blurEnabled && (state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
                24.dp
            } else 0.dp
        }
        animatedValue
    } else 0.dp

    val isActive = predictiveBackState.isActive
    val style by PetalPredictiveJunction.backStyle.collectAsStateWithLifecycle()
    val exitDirection by PetalPredictiveJunction.backExitDirection.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val screenDensity = LocalDensity.current.density
    val gestureLive = predictiveEnabled && isActive

    fun gestureFrame(widthPx: Float, heightPx: Float, density: Float): PetalBackFrame? =
        if (gestureLive) {
            PetalBackMotion.frame(
                style,
                predictiveBackState.toInput(style, exitDirection, blurEnabled, widthPx, heightPx, density),
            )
        } else null

    val frame = gestureFrame(
        configuration.screenWidthDp * screenDensity,
        configuration.screenHeightDp * screenDensity,
        screenDensity,
    )

    val currentBlurRadiusDp = if ((isBehindTopScreen || transitionBlurRadiusDp > 0.dp) && blurEnabled) {
        if (frame != null) {
            frame.behindBlurDp.dp
        } else if (transitionBlurRadiusDp > 0.dp) {
            transitionBlurRadiusDp
        } else if (behindFraction != null) {
            // Item 4: Don't blur behind state during push/pop transition; prefer scrim/dim.
            0.dp
        } else {
            24.dp
        }
    } else 0.dp

    CompositionLocalProvider(LocalIsUnderlayPreview provides (isBehindTopScreen || LocalIsUnderlayPreview.current)) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            // Layer 0: Background snapshot underlay (rendered when gesture is active or behind top screen)
            if (backgroundSnapshot != null && (isActive || isBehindTopScreen) && !(isActive && predictiveBackState.liveUnderlay) && !predictiveBackState.dismissed) {
                // At rest (not mid-gesture) the snapshot keeps the original Petal pose.
                val snapshotFrame = frame ?: PetalBackMotion.frame(
                    PetalBackStyle.Petal,
                    PetalBackInput(
                        progress = 0f,
                        swipeEdge = predictiveBackState.swipeEdge,
                        touchY = 0f,
                        initialTouchY = 0f,
                        widthPx = configuration.screenWidthDp * screenDensity,
                        heightPx = configuration.screenHeightDp * screenDensity,
                        density = screenDensity,
                        blurEnabled = blurEnabled,
                    ),
                )
                val snapshotBlurRadius = snapshotFrame.behindBlurDp.dp
                val snapshotDimAlpha = snapshotFrame.behindDim
                val behindLayer = snapshotFrame.behind

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (snapshotBlurRadius > 0.dp) Modifier.blur(radius = snapshotBlurRadius)
                            else Modifier
                        )
                        .graphicsLayer {
                            scaleX = behindLayer.scale
                            scaleY = behindLayer.scale
                            translationX = behindLayer.translationX
                            translationY = behindLayer.translationY
                            alpha = behindLayer.alpha
                        }
                ) {
                    Image(
                        bitmap = backgroundSnapshot,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = snapshotDimAlpha.coerceIn(0f, 1f)))
                    )
                }
            }

            // Layer 1: Foreground interactive screen content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (behindFraction == null && isBehindTopScreen && blurEnabled && currentBlurRadiusDp > 0.dp) Modifier.blur(radius = currentBlurRadiusDp)
                        else Modifier
                    )
                    .graphicsLayer {
                        val f = gestureFrame(size.width, size.height, density)

                        if (!isBehindTopScreen) {
                            val layer = f?.top
                            if (layer != null) {
                                scaleX = layer.scale
                                scaleY = layer.scale
                                transformOrigin = TransformOrigin(layer.originX, layer.originY)
                                translationX = layer.translationX
                                translationY = layer.translationY
                                alpha = layer.alpha
                            }
                            // Dismissed by a committed gesture: stay parked (invisible) until the host removes us.
                            if (predictiveEnabled && predictiveBackState.dismissed) alpha = 0f
                            val cornerRadius = maxOf(layer?.cornerDp ?: 0f, transitionCornerRadius)
                            compositingStrategy = if (layer != null || transitionCornerRadius > 0.5f) CompositingStrategy.Offscreen else CompositingStrategy.Auto

                            if (cornerRadius > 0.5f) {
                                this.shape = RoundedCornerShape(cornerRadius.dp)
                                this.clip = true
                                this.shadowElevation = (layer?.shadowDp ?: 0f).dp.toPx()
                            } else {
                                this.clip = false
                                this.shadowElevation = 0f
                            }
                        } else {
                            val layer = f?.behind
                            var restCorner = 32f
                            var blurDp = 0f
                            var fractionActive = false
                            if (layer != null) {
                                scaleX = layer.scale
                                scaleY = layer.scale
                                transformOrigin = TransformOrigin(layer.originX, layer.originY)
                                translationX = layer.translationX
                                translationY = layer.translationY
                                alpha = layer.alpha
                                if (behindFraction != null) blurDp = f?.behindBlurDp ?: 0f
                            } else if (behindFraction != null) {
                                val fr = behindFraction.invoke().coerceIn(0f, 1f)
                                val rest = PetalBackMotion.frame(
                                    style,
                                    PetalBackInput(
                                        progress = 0f,
                                        swipeEdge = BackEventCompat.EDGE_LEFT,
                                        touchY = size.height / 2f,
                                        initialTouchY = size.height / 2f,
                                        widthPx = size.width,
                                        heightPx = size.height,
                                        density = density,
                                        blurEnabled = blurEnabled,
                                    ),
                                )
                                val pose = rest.behind
                                scaleX = 1f + (pose.scale - 1f) * fr
                                scaleY = 1f + (pose.scale - 1f) * fr
                                transformOrigin = TransformOrigin(pose.originX, pose.originY)
                                translationX = pose.translationX * fr
                                translationY = pose.translationY * fr
                                alpha = 1f + (pose.alpha - 1f) * fr
                                restCorner = pose.cornerDp * fr
                                // Item 4: Scrim/dim only for behind state during transition, no blur over glass panels
                                blurDp = 0f
                                fractionActive = fr > 0.001f
                            } else {
                                alpha = 1f
                            }

                            // At rest a behind screen keeps its 32.dp rounded pose; mid-gesture the style decides.
                            val cornerRadius = maxOf(layer?.cornerDp ?: restCorner, transitionCornerRadius)
                            val blurring = behindFraction != null && blurEnabled && blurDp > 0.5f
                            compositingStrategy = if (layer != null || currentBlurRadiusDp > 0.dp || cornerRadius > 0.5f || fractionActive || blurring) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                            renderEffect = if (blurring) {
                                val px = blurDp.dp.toPx()
                                BlurEffect(px, px, TileMode.Clamp)
                            } else null
                            if (cornerRadius > 0.5f) {
                                this.shape = RoundedCornerShape(cornerRadius.dp)
                                this.clip = true
                            } else {
                                this.clip = false
                            }
                            this.shadowElevation = 0f
                        }
                    }
                    .then(
                        if (!isBehindTopScreen) Modifier.background(MaterialTheme.colorScheme.background)
                        else Modifier
                    )
            ) {
                CompositionLocalProvider(com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides pageGlassWallpaper) {
                    content()
                }

                if (isBehindTopScreen || transitionDimAlpha > 0f) {
                    val settledTargetDim = if (!blurEnabled) 0.75f else 0.40f
                    val effectiveDimAlpha = frame?.behindDim ?: maxOf(settledTargetDim, transitionDimAlpha)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val dim = when {
                                    frame != null -> frame.behindDim
                                    behindFraction != null ->
                                        maxOf(settledTargetDim * behindFraction.invoke().coerceIn(0f, 1f), transitionDimAlpha)
                                    else -> effectiveDimAlpha
                                }
                                alpha = dim.coerceIn(0f, 1f)
                            }
                            .background(Color.Black)
                    )
                }
            }
        }
    }
}


/**
 * Predictive-back chrome for a page that may be hosted inside another page's surface. When
 * [wrap] is false the host (e.g. the Settings container) already owns the single back handler and
 * wrapper, so wrapping again would register a second handler and apply the dismiss transform twice.
 */
@Composable
fun PetalPredictiveShell(
    wrap: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    backgroundSnapshot: ImageBitmap? = null,
    content: @Composable () -> Unit,
) {
    if (wrap) {
        PetalPredictiveBackSurface(enabled = enabled, onBack = onBack) {
            PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) { content() }
        }
    } else {
        content()
    }
}

/**
 * A page with an in-page sub-page (a vault's settings page, a details/edit/import overlay...). The
 * sub-page is a real foreground layer: it is posed by the selected [PetalBackStyle] through the same
 * engine as full-screen pages, while [parent] stays composed (state preserved) and LIVE behind it,
 * receiving the behind pose, dim and depth blur from the sub-page's own gesture. The sub-page has its
 * own header, so there is no shared-header title crossfade to overlap or ghost.
 *
 * The sub-page layer leaves with [ExitTransition.None]: a committed back has already parked it in its
 * final pose, so a host exit animation would only replay it (ghosting / double animation). With
 * predictive back off or reduce-motion enabled this degrades to an instant swap. This surface never
 * asks the root for a live underlay (the parent fully covers what is behind) and holds no dismissed
 * pose (the layer is removed immediately).
 */
@Composable
fun PetalSubPageHost(
    visible: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    parent: @Composable () -> Unit,
    subPage: @Composable () -> Unit,
) {
    val reduceMotion = com.petal.browser.ui.components.rememberReduceMotion()
    val outerState = LocalPredictiveBackState.current
    val fraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 300, easing = FastOutSlowInEasing),
        label = "subPageBehindFraction",
    )
    PetalPredictiveBackSurface(
        enabled = visible,
        onBack = onBack,
        holdDismissedPose = false,
        useLiveUnderlay = false,
    ) {
        val innerState = LocalPredictiveBackState.current
        Box(modifier.fillMaxSize()) {
            // One stable call site for the parent so its state survives the sub-page opening.
            CompositionLocalProvider(LocalPredictiveBackState provides outerState) {
                PetalScreenWrapper(
                    isBehind = visible,
                    behindFraction = { fraction },
                    gestureState = innerState,
                ) { parent() }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None
                else androidx.compose.animation.fadeIn(tween(200)) +
                    androidx.compose.animation.slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 5 },
                exit = androidx.compose.animation.ExitTransition.None,
                modifier = Modifier.fillMaxSize(),
            ) {
                PetalScreenWrapper(gestureState = innerState) { subPage() }
            }
        }
    }
}
