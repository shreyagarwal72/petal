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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global settings & state junction for Predictive Back and Depth Blur effects across the Petal App.
 *
 * Implements 1:1 depth blur, reveal scaling, and back page underlay blur matching RvSystem-Monitor & PixelPlayer.
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
        _isPredictiveBackEnabled.value = prefs.getBoolean(KEY_PREDICTIVE_BACK_ENABLED, true)
        _isDepthBlurEnabled.value = prefs.getBoolean(KEY_DEPTH_BLUR_ENABLED, true)
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
}

val LocalPetalPredictiveJunctionState = compositionLocalOf { true }
val LocalPetalDepthBlurJunctionState = compositionLocalOf { true }
val LocalIsUnderlayPreview = compositionLocalOf { false }

/**
 * Live state of an in-flight predictive back gesture.
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

val LocalPredictiveBackState = compositionLocalOf { PredictiveBackState.Idle }

/**
 * Wraps [content] with a [PredictiveBackHandler] and republishes gesture progress
 * through [LocalPredictiveBackState] so any descendant can react to it live.
 *
 * Renders an underlay depth-blurred background behind the surface during gestures
 * matching RvSystem-Monitor & PixelPlayer.
 */
@Composable
fun PetalPredictiveBackSurface(
    enabled: Boolean = true,
    onBack: () -> Unit,
    underlayContent: (@Composable () -> Unit)? = { PetalDefaultUnderlayBlurPreview() },
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
            try {
                progressFlow.collect { backEvent ->
                    progressAnim.snapTo(backEvent.progress)
                    backState = PredictiveBackState(
                        isActive = true,
                        progress = backEvent.progress,
                        swipeEdge = backEvent.swipeEdge,
                    )
                }
                progressAnim.snapTo(backState.progress)
                progressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ) {
                    backState = backState.copy(isActive = true, progress = value)
                }
                backState = backState.copy(isActive = true, progress = 1f)
                onBack()
                backState = PredictiveBackState.Idle
            } catch (e: CancellationException) {
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
                backState = PredictiveBackState.Idle
            }
        }
    }

    CompositionLocalProvider(
        LocalPetalPredictiveJunctionState provides junctionPredictiveEnabled,
        LocalPetalDepthBlurJunctionState provides junctionBlurEnabled,
        LocalPredictiveBackState provides backState,
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

/**
 * Screen wrapper that applies predictive back visual effects and depth blur to full-screen Petal surfaces.
 * Ported 1:1 from RvSystem-Monitor & PixelPlayer:
 * - Depth blur: 24.dp on revealed back page underlay.
 * - Dim overlay: 0.4f (or 0.75f when blur disabled) clearing as gesture progresses.
 * - Scale & corner radius transformation on foreground surface.
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

    val predictiveBackState = LocalPredictiveBackState.current
    val underlayBlurRadius = if (isBehind && blurEnabled) 24.dp else 0.dp

    CompositionLocalProvider(LocalIsUnderlayPreview provides (isBehind || LocalIsUnderlayPreview.current)) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .blur(radius = underlayBlurRadius)
                .graphicsLayer {
                    val isActive = predictiveBackState.isActive
                    val progress = if (predictiveEnabled && isActive) predictiveBackState.progress else 0f

                    val slideEased = PetalPopExitSlideEasing.transform(progress)
                    val scaleEased = PetalM3EmphasizedEasing.transform(progress)

                    if (!isBehind) {
                        val swipeEdge = predictiveBackState.swipeEdge
                        val translationXFactor = if (isActive) {
                            if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                        } else {
                            0f
                        }

                        val scale = 1f - (PETAL_POP_EXIT_MAX_SCALE_DELTA * scaleEased)
                        val cornerRadius = 32f * scaleEased

                        scaleX = scale
                        scaleY = scale
                        translationX = size.width * translationXFactor * slideEased
                        compositingStrategy = if (isActive) CompositingStrategy.Offscreen else CompositingStrategy.Auto

                        if (cornerRadius > 0.5f) {
                            this.shape = RoundedCornerShape(cornerRadius.dp)
                            this.clip = true
                            this.shadowElevation = (16f * scaleEased).dp.toPx()
                        } else {
                            this.clip = false
                            this.shadowElevation = 0f
                        }
                    } else {
                        val revealScale = if (isActive) 0.94f + 0.06f * scaleEased else 1f
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
                val settledTargetDim = if (!blurEnabled) 0.75f else 0.40f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val isActive = predictiveBackState.isActive
                            val progress = if (predictiveEnabled && isActive) predictiveBackState.progress else 0f
                            val backProgressEased = if (isActive) PetalM3EmphasizedEasing.transform(progress) else 0f
                            alpha = if (isActive) settledTargetDim * (1f - backProgressEased) else settledTargetDim
                        }
                        .background(Color.Black)
                )
            }
        }
    }
}

/**
 * Default depth blur preview underlay surface rendered behind full-screen surfaces during predictive back gestures.
 * Ported 1:1 from RvSystem-Monitor & PixelPlayer's depth preview layout.
 */
@Composable
fun PetalDefaultUnderlayBlurPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Spacer(Modifier.height(48.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
