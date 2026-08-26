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

import android.content.SharedPreferences
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Global settings & state junction for Predictive Back and Depth Blur effects across the Petal App.
 * Changes here automatically propagate to every page and route without needing per-screen logic.
 */
object PetalPredictiveJunction {
    private const val KEY_PREDICTIVE_BACK_ENABLED = "sp_predictive_back_junction_enabled"
    private const val KEY_DEPTH_BLUR_ENABLED = "sp_depth_blur_junction_enabled"

    private val _isPredictiveBackEnabled = MutableStateFlow(true)
    val isPredictiveBackEnabled: StateFlow<Boolean> = _isPredictiveBackEnabled.asStateFlow()

    private val _isDepthBlurEnabled = MutableStateFlow(true)
    val isDepthBlurEnabled: StateFlow<Boolean> = _isDepthBlurEnabled.asStateFlow()

    @JvmStatic
    fun init(prefs: SharedPreferences) {
        _isPredictiveBackEnabled.value = true
        _isDepthBlurEnabled.value = prefs.getBoolean(KEY_DEPTH_BLUR_ENABLED, true)
    }

    @JvmStatic
    fun setPredictiveBackEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isPredictiveBackEnabled.value = true
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK_ENABLED, true).apply()
    }

    @JvmStatic
    fun setDepthBlurEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isDepthBlurEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DEPTH_BLUR_ENABLED, enabled).apply()
    }
}

val LocalPetalPredictiveJunctionState = compositionLocalOf { true }
val LocalPetalDepthBlurJunctionState = compositionLocalOf { true }
val LocalIsUnderlayPreview = compositionLocalOf { false }

// ---------------------------------------------------------------------------
// Predictive back gesture state — published downward via CompositionLocal so
// any descendant (ScreenWrapper, PetalScreenWrapper) can track the live finger
// position instead of waiting for the settled navigation event.
// ---------------------------------------------------------------------------

/**
 * Live state of an in-flight predictive back gesture.
 *
 * [progress] is 0f (finger at edge) → 1f (fully committed). It updates every frame
 * while the thumb is moving, which lets the revealed screen's blur/dim/scale track
 * the finger instead of snapping at commit time.
 */
data class PredictiveBackState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val swipeEdge: Int = BackEventCompat.EDGE_LEFT,
) {
    companion object {
        val Idle = PredictiveBackState()
    }
}

/** CompositionLocal that carries the current [PredictiveBackState] down the tree. */
val LocalPredictiveBackState = compositionLocalOf { PredictiveBackState.Idle }

// ---------------------------------------------------------------------------
// PetalPredictiveBackSurface — predictive back gesture wrapper
// ---------------------------------------------------------------------------

/**
 * Wraps [content] with a [PredictiveBackHandler] and republishes gesture progress
 * through [LocalPredictiveBackState] so any descendant can react to it live.
 *
 * - Only intercepts back when [enabled] is true AND the junction setting is on.
 * - On cancel: animates progress smoothly back to 0 (220 ms tween) so blur/scale
 *   relax instead of snapping — matches system back-cancel feel.
 * - On commit: resets state immediately and calls [onBack]. The system chrome
 *   handles the dismissal animation; do NOT spring-to-1f first.
 */
@Composable
fun PetalPredictiveBackSurface(
    enabled: Boolean = true,
    onBack: () -> Unit,
    underlayContent: (@Composable () -> Unit)? = { PetalDynamicUnderlayPreview() },
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()
    val isUnderlayPreview = LocalIsUnderlayPreview.current

    val isFullyEnabled = enabled && junctionPredictiveEnabled && !isUnderlayPreview

    var backState by remember { mutableStateOf(PredictiveBackState.Idle) }
    val progressAnim = remember { Animatable(0f) }

    if (isFullyEnabled) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            val canWebGoBack = try {
                com.petal.browser.activity.BrowserActivity.canNinjaGoBack()
            } catch (_: Exception) {
                false
            }
            if (canWebGoBack) {
                try {
                    (context as? com.petal.browser.activity.BrowserActivity)?.handleBackPress()
                } catch (_: Exception) {
                    (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                }
                return@PredictiveBackHandler
            }

            try {
                progressFlow.collect { backEvent ->
                    progressAnim.snapTo(backEvent.progress)
                    backState = PredictiveBackState(
                        isActive = true,
                        progress = backEvent.progress,
                        swipeEdge = backEvent.swipeEdge,
                    )
                }
                // Gesture committed — smoothly animate remaining progress to 1f with spring physics
                progressAnim.snapTo(backState.progress)
                progressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ) {
                    backState = backState.copy(isActive = true, progress = value)
                }
                backState = backState.copy(isActive = true, progress = 1f)
                onBack()
                backState = PredictiveBackState.Idle
            } catch (e: CancellationException) {
                // Gesture cancelled — smoothly relax progress back to 0f with spring physics
                progressAnim.snapTo(backState.progress)
                progressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy
                    )
                ) {
                    backState = backState.copy(isActive = true, progress = value)
                }
                backState = backState.copy(isActive = true, progress = 0f)
                backState = PredictiveBackState.Idle
                throw e
            }
        }
    }

    CompositionLocalProvider(
        LocalPetalPredictiveJunctionState provides junctionPredictiveEnabled,
        LocalPetalDepthBlurJunctionState provides junctionBlurEnabled,
        LocalPredictiveBackState provides backState
    ) {
        if (underlayContent != null && !isUnderlayPreview) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (backState.isActive) {
                    PetalScreenWrapper(isBehind = true) {
                        underlayContent()
                    }
                }
                content()
            }
        } else {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// PetalScreenWrapper — visual layer driven by LocalPredictiveBackState
// ---------------------------------------------------------------------------

/**
 * Screen wrapper that applies predictive back visual effects to full-screen Petal surfaces.
 *
 * Reads [LocalPredictiveBackState] directly instead of requiring a manually threaded
 * `progress` float parameter. Wrap with [PetalPredictiveBackSurface] at the call site
 * to publish gesture state into the composition tree.
 *
 * Visual effects:
 * - Scale: 1.0 at rest → 0.88 at full gesture (foreground screen shrinking).
 * - Corner radius: 0 dp → 32 dp as gesture progresses (foreground only).
 * - Dim + blur on [isBehind] screens — clears live with the finger so the reveal
 *   feels attached to the swipe, not triggered by the commit.
 * - Revealed-screen scale: 0.94 → 1.0 as gesture progresses (subtle grow-to-meet).
 * - Ease: FastOutSlowInEasing applied to the raw progress so blur/dim clear
 *   noticeably ahead of the finger — matches PixelPlayer & RvSystem-Monitor.
 */
@Composable
fun PetalScreenWrapper(
    isBehind: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()

    val predictiveEnabled = junctionPredictiveEnabled
    val blurEnabled = junctionBlurEnabled
    val disableBlurAllOver = !blurEnabled

    val predictiveBackState = LocalPredictiveBackState.current
    val underlayBlurRadius = if (isBehind && !disableBlurAllOver) 24.dp else 0.dp

    CompositionLocalProvider(LocalIsUnderlayPreview provides (isBehind || LocalIsUnderlayPreview.current)) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .blur(radius = underlayBlurRadius)
                .graphicsLayer {
                    val isActive = predictiveBackState.isActive
                    val progress = if (predictiveEnabled && isActive) predictiveBackState.progress else 0f
                    val backProgressEased = if (isActive) FastOutSlowInEasing.transform(progress) else 0f

                    if (!isBehind) {
                        // Foreground card transformations — executed purely in Draw phase
                        val scale = 1f - (0.12f * progress)
                        val cornerRadius = 32f * progress
                        val alphaVal = if (isActive) 1f - (0.15f * progress) else 1f
                        val swipeEdge = predictiveBackState.swipeEdge
                        val translationXFactor = if (isActive) {
                            if (swipeEdge == BackEventCompat.EDGE_LEFT) 0.35f
                            else if (swipeEdge == BackEventCompat.EDGE_RIGHT) -0.35f
                            else 0f
                        } else 0f

                        scaleX = scale
                        scaleY = scale
                        translationX = size.width * translationXFactor * progress
                        translationY = size.height * 0.015f * progress
                        alpha = alphaVal
                        compositingStrategy = if (isActive) CompositingStrategy.Offscreen else CompositingStrategy.Auto

                        if (cornerRadius > 0.5f) {
                            this.shape = RoundedCornerShape(cornerRadius.dp)
                            this.clip = true
                            this.shadowElevation = (16f * progress).dp.toPx()
                        } else {
                            this.clip = false
                            this.shadowElevation = 0f
                        }
                    } else {
                        // Revealed underlay screen transformations — executed purely in Draw phase
                        val revealScale = if (isActive) 0.94f + 0.06f * backProgressEased else 1f
                        scaleX = revealScale
                        scaleY = revealScale
                        alpha = 1f
                        compositingStrategy = if (isActive) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                        this.clip = false
                        this.shadowElevation = 0f
                    }
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()

            if (isBehind) {
                val settledTargetDim = if (disableBlurAllOver) 0.75f else 0.40f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val isActive = predictiveBackState.isActive
                            val progress = if (predictiveEnabled && isActive) predictiveBackState.progress else 0f
                            val backProgressEased = if (isActive) FastOutSlowInEasing.transform(progress) else 0f
                            alpha = if (isActive) settledTargetDim * (1f - backProgressEased) else settledTargetDim
                        }
                        .background(Color.Black),
                )
            }
        }
    }
}

/**
 * Renders the dynamic underlay preview behind screens (Settings, Account Sync, etc.) during predictive back.
 * If a website is active in the WebView, displays a high-fidelity web page preview card with title, URL, and brand emblem.
 * If no website is open, renders the standard Petal Home Screen.
 */
@Composable
fun PetalDynamicUnderlayPreview() {
    val isHomeOrBlank = remember {
        try {
            com.petal.browser.activity.BrowserActivity.isCurrentTabHomeOrBlank()
        } catch (_: Exception) {
            true
        }
    }

    if (isHomeOrBlank) {
        com.petal.browser.compose.home.PetalHomeScreen()
    } else {
        PetalWebPageUnderlayPreviewCard()
    }
}

@Composable
private fun PetalWebPageUnderlayPreviewCard() {
    val webView = remember {
        try {
            com.petal.browser.activity.BrowserActivity.getNinjaWebView()
        } catch (_: Exception) {
            null
        }
    }

    val pageUrl = webView?.url ?: "https://petal.browser"
    val pageTitle = webView?.title?.takeIf { it.isNotBlank() } ?: "Web Page"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Address Bar Pill with Title & URL
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = pageUrl,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Center: Website Content Emblem Placeholder
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pageUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Spacer / Dock
            Spacer(Modifier.height(32.dp))
        }
    }
}

