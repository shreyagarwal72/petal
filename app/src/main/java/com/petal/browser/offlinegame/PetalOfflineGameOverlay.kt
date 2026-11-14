package com.petal.browser.offlinegame

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.motion.MotionGate
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.toShape
import org.mozilla.geckoview.WebRequestError

/** Mutable details of the failed load, shared between the tab view and the Compose overlay. */
@Stable
class PetalOfflineGameModel(url: String?, hostError: Boolean) {
    var url by mutableStateOf(url)
    var hostError by mutableStateOf(hostError)

    fun update(newUrl: String?, newHostError: Boolean) {
        url = newUrl
        hostError = newHostError
    }
}

/**
 * Hosts "Petal Hop" over a tab whose main-frame load failed because of connectivity. It mirrors the
 * crash-overlay helper: the tab view owns the returned [View] and removes it again with [dismiss].
 */
object PetalOfflineGameOverlay {

    const val PREF_ENABLED = "sp_offline_game"
    const val PREF_BEST = "sp_offline_game_best"

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ENABLED, true)

    private val CONNECTIVITY_ERRORS = setOf(
        "ERROR_UNKNOWN_HOST",
        "ERROR_NET_TIMEOUT",
        "ERROR_NET_RESET",
        "ERROR_NET_INTERRUPT",
        "ERROR_CONNECTION_REFUSED",
        "ERROR_NO_INTERNET",
        "ERROR_OFFLINE",
        "ERROR_UNKNOWN_PROXY_HOST",
        "ERROR_PROXY_CONNECTION_REFUSED"
    )

    /** Errors where "you are offline / the network is flaky" is a plausible explanation. */
    @JvmStatic
    fun isConnectivityErrorName(name: String): Boolean = name in CONNECTIVITY_ERRORS

    @JvmStatic
    fun isHostErrorName(name: String): Boolean = name == "ERROR_UNKNOWN_HOST"

    /** Maps a raw GeckoView error code to the same names Mozilla's ErrorType enum uses. */
    @JvmStatic
    fun nameForWebRequestCode(code: Int): String? = when (code) {
        WebRequestError.ERROR_UNKNOWN_HOST -> "ERROR_UNKNOWN_HOST"
        WebRequestError.ERROR_NET_TIMEOUT -> "ERROR_NET_TIMEOUT"
        WebRequestError.ERROR_NET_RESET -> "ERROR_NET_RESET"
        WebRequestError.ERROR_NET_INTERRUPT -> "ERROR_NET_INTERRUPT"
        WebRequestError.ERROR_CONNECTION_REFUSED -> "ERROR_CONNECTION_REFUSED"
        else -> null
    }

    @JvmStatic
    fun show(
        parent: ViewGroup,
        existing: View?,
        failedUrl: String?,
        hostError: Boolean,
        onTryAgain: (String?) -> Unit
    ): View? {
        if (existing != null) {
            (existing.tag as? PetalOfflineGameModel)?.update(failedUrl, hostError)
            return existing
        }
        val activity = findActivity(parent.context)
        if (activity == null) {
            android.util.Log.w("PetalHop", "No ComponentActivity found for ${parent.context}; overlay skipped")
            return null
        }
        android.util.Log.d("PetalHop", "Showing offline game overlay for $failedUrl (hostError=$hostError)")
        val model = PetalOfflineGameModel(failedUrl, hostError)
        val view = ComposeView(activity).apply {
            tag = model
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                PetalExpressiveTheme {
                    PetalOfflineGameScreen(model = model, onTryAgain = onTryAgain)
                }
            }
        }
        parent.addView(view)
        return view
    }

    @JvmStatic
    fun dismiss(parent: ViewGroup, view: View?): View? {
        if (view != null) parent.removeView(view)
        return null
    }

    private fun findActivity(context: Context): ComponentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) return c
            c = c.baseContext
        }
        return null
    }
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun PetalOfflineGameScreen(
    model: PetalOfflineGameModel,
    onTryAgain: (String?) -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val state = rememberPetalHopState()
    val reduceMotion = remember(context) { MotionGate.isReduceMotion(context) }

    // A mistyped address is the likelier cause for "unknown host", so the game waits for a tap there.
    val hostError = model.hostError
    var started by remember { mutableStateOf(!hostError) }

    // Connectivity: only offer "back online" if we actually saw the device offline first.
    var sawOffline by remember { mutableStateOf(!isOnline(context)) }
    var backOnline by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (sawOffline && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    backOnline = true
                }
            }

            override fun onLost(network: Network) {
                sawOffline = true
                backOnline = false
            }
        }
        try {
            cm?.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
        }
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Throwable) {
            }
        }
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Surface(modifier = Modifier.fillMaxSize(), color = cs.surface) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.94f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ErrorHeader(hostError = hostError, url = model.url, reduceMotion = reduceMotion)

                GameCard(
                    state = state,
                    started = started,
                    onStart = { started = true },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                AnimatedVisibility(
                    visible = backOnline,
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 } +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 } +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                ) {
                    BackOnlinePill(onReload = { onTryAgain(model.url) })
                }

                HopButton(
                    text = stringResource(R.string.offline_game_try_again),
                    icon = Icons.Rounded.Refresh,
                    container = cs.primary,
                    content = cs.onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTryAgain(model.url) }
                )
            }
        }
    }
}

@Composable
private fun ErrorHeader(hostError: Boolean, url: String?, reduceMotion: Boolean) {
    val cs = MaterialTheme.colorScheme
    val spin by rememberInfiniteTransition(label = "badgeSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "badgeSpinValue"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = if (reduceMotion) 0f else spin }
                    .background(cs.primaryContainer, PetalMaterialShapes.Cookie9Sided.toShape())
            )
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = cs.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    if (hostError) R.string.offline_game_error_host_title else R.string.offline_game_error_no_internet_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                text = stringResource(
                    if (hostError) R.string.offline_game_error_host_desc else R.string.offline_game_error_no_internet_desc
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            if (!url.isNullOrBlank()) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    state: PetalHopState,
    started: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val over = state.phase == PetalHopEngine.Phase.OVER
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.surfaceContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PetalHopGame(
                state = state,
                interactive = started && !over,
                modifier = Modifier.fillMaxSize()
            )

            // Score + best chips.
            AnimatedVisibility(
                visible = started,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it }
            ) {
                ScoreRow(state)
            }

            // First-tap hint.
            AnimatedVisibility(
                visible = started && state.phase == PetalHopEngine.Phase.READY,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.9f)
            ) {
                Surface(shape = CircleShape, color = cs.secondaryContainer) {
                    Text(
                        text = stringResource(R.string.offline_game_tap_to_hop),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // "Play while you wait" (unknown-host errors only).
            AnimatedVisibility(
                visible = !started,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f)
            ) {
                HopButton(
                    text = stringResource(R.string.offline_game_play),
                    icon = Icons.Rounded.PlayArrow,
                    container = cs.tertiaryContainer,
                    content = cs.onTertiaryContainer,
                    onClick = onStart
                )
            }

            // Game over card.
            AnimatedVisibility(
                visible = over,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.7f),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.9f)
            ) {
                GameOverCard(state)
            }
        }
    }
}

@Composable
private fun ScoreRow(state: PetalHopState) {
    val cs = MaterialTheme.colorScheme
    val bump = remember { Animatable(1f) }
    LaunchedEffect(state.milestoneToken) {
        if (state.milestoneToken > 0) {
            bump.snapTo(1.28f)
            bump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = cs.primaryContainer,
            modifier = Modifier
                .scale(bump.value)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(
                text = stringResource(R.string.offline_game_score, state.score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Surface(shape = CircleShape, color = cs.surfaceContainerHighest) {
            Text(
                text = stringResource(R.string.offline_game_best, maxOf(state.best, state.engine.best)),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun GameOverCard(state: PetalHopState) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = PetalMaterialShapes.Cookie9Sided.toShape(),
        color = cs.surfaceContainerHighest,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(
                    if (state.newBest) R.string.offline_game_new_best else R.string.offline_game_oops
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (state.newBest) cs.tertiary else cs.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.offline_game_score, state.score),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface
            )
            Text(
                text = stringResource(R.string.offline_game_best, maxOf(state.best, state.engine.best)),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            HopButton(
                text = stringResource(R.string.offline_game_play_again),
                icon = Icons.Rounded.Refresh,
                container = cs.primary,
                content = cs.onPrimary,
                onClick = { state.restart() }
            )
        }
    }
}

@Composable
private fun BackOnlinePill(onReload: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = cs.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.offline_game_back_online),
                style = MaterialTheme.typography.titleSmall,
                color = cs.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            HopButton(
                text = stringResource(R.string.offline_game_reload),
                icon = null,
                container = cs.tertiary,
                content = cs.onTertiary,
                height = 40.dp,
                onClick = onReload
            )
        }
    }
}

/**
 * Pill button that morphs its corners and squishes on press (M3 Expressive press-state shape
 * morph), driven by springs so it feels like the rest of the app's expressive controls.
 */
@Composable
private fun HopButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 56.dp
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else height / 2,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "hopButtonCorner"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "hopButtonScale"
    )
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        modifier = modifier
            .heightIn(min = height)
            .scale(pressScale)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = height)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
