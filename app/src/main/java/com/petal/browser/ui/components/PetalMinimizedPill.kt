package com.petal.browser.ui.components

import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.glass.GlassBar
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar
import androidx.compose.material3.Icon

/**
 * Shared host for [PetalMinimizedPill] used by both [PetalAddressBar] and [PetalMergedBar].
 * Reads [BarCollapseState.collapsedFraction] inside [derivedStateOf] for mount gating and inside
 * [graphicsLayer] for frame transforms so scroll frames never recompose the host bar.
 */
@Composable
internal fun BarMinimizedPillHost(
    chrome: BarChrome,
    isIncognito: Boolean,
    progress: Float,
    isLoading: Boolean,
    onExpand: () -> Unit,
    onPillClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (chrome.isPreview || !chrome.isPillCollapse) return
    val showPill by remember(chrome) {
        derivedStateOf { chrome.currentVisuals().showPill }
    }
    if (!showPill) return

    PetalMinimizedPill(
        domain = BarCollapseState.currentDomain,
        isHttps = BarCollapseState.isHttps,
        isIncognito = isIncognito,
        isBottom = chrome.isBottom,
        progress = progress,
        isLoading = isLoading,
        glassSource = if (chrome.isLiquidGlass) chrome.glassSource else null,
        isLiquidGlass = chrome.isLiquidGlass,
        glassBar = chrome.pillGlassBar,
        onExpand = onExpand,
        onPillClick = onPillClick,
        modifier = modifier.graphicsLayer {
            val v = chrome.currentVisuals()
            alpha = v.pillAlpha
            scaleX = v.pillScale
            scaleY = v.pillScale
            translationY = v.pillTranslationDp.dp.toPx()
        }
    )
}

/**
 * Chrome Android-style compact minimized toolbar pill shown on downward scroll.
 *
 * Displays `[Lock/Privacy] domain.com ⌄` in a 40dp rounded container with live Liquid Glass
 * blur and optical lens refraction over the actively scrolling webpage.
 * Tapping the pill opens the omnibox reliably; swiping expands the full toolbar.
 */
@Composable
fun PetalMinimizedPill(
    domain: String,
    isHttps: Boolean,
    isIncognito: Boolean,
    isBottom: Boolean,
    glassSource: View? = null,
    isLiquidGlass: Boolean = glassSource != null,
    glassBar: GlassBar? = null,
    progress: Float = 0f,
    isLoading: Boolean = false,
    onPillClick: (() -> Unit)? = null,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pillShape = RoundedCornerShape(20.dp)
    val isGlass = isLiquidGlass && !isIncognito
    val resolvedGlassBar = glassBar ?: rememberGlassBar(if (isGlass) glassSource else null)
    val baseGlassColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val statusBarTopInset = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()
    val navBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

    val containerColor = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoSurfaceContainer
        isGlass -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnPillClick by rememberUpdatedState(onPillClick ?: onExpand)
    val pillBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val pillRadiusPx = with(LocalDensity.current) { 20.dp.toPx() }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = PetalSpring.quick(),
        label = "pill_press_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (!isBottom) statusBarTopInset + 6.dp else 4.dp,
                bottom = if (isBottom) navBarBottomInset + 8.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val maxPillWidth = (maxWidth * 0.70f).coerceAtLeast(160.dp)
        // 48dp touch target around the 40dp visual pill.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(48.dp)
                .wrapContentWidth()
                .onGloballyPositioned { pillBounds[0] = it.boundsInWindow() }
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    var dragAccum = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = {
                            if (Math.abs(dragAccum) > 10.dp.toPx()) {
                                com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                                currentOnExpand()
                            }
                            dragAccum = 0f
                        },
                        onDragCancel = { dragAccum = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            dragAccum += dragAmount
                        }
                    )
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true)
                ) {
                    PetalOmniboxOrigin.mark(pillBounds[0], pillRadiusPx)
                    currentOnPillClick()
                }
        ) {
            Surface(
                shape = pillShape,
                color = containerColor,
                tonalElevation = if (isGlass) 0.dp else 2.dp,
                shadowElevation = if (isGlass) 0.dp else 2.dp,
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(max = maxPillWidth)
                    .wrapContentWidth()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(pillShape)
                    .then(
                        if (isGlass) {
                            Modifier.liquidGlass(pillShape, baseGlassColor, resolvedGlassBar, pressFx = true)
                        } else Modifier
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Security / Incognito Icon
                        // Lock only for HTTPS. HTTP gets an open lock in the error colour so it is never
                        // mistaken for a secure page.
                        val showSecurity = domain.isNotBlank()
                        if (isIncognito || showSecurity) {
                            Icon(
                                imageVector = when {
                                    isIncognito -> Icons.Rounded.VisibilityOff
                                    isHttps -> Icons.Rounded.Lock
                                    else -> Icons.Rounded.LockOpen
                                },
                                contentDescription = when {
                                    isIncognito -> null
                                    isHttps -> stringResource(R.string.bar_secure_connection)
                                    else -> stringResource(R.string.bar_not_secure)
                                },
                                tint = when {
                                    isIncognito -> MaterialTheme.colorScheme.onSurface
                                    isHttps -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // Domain Label
                        Text(
                            text = domain.ifBlank { stringResource(R.string.bar_search_or_type_url) },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                shadow = if (isGlass) {
                                    androidx.compose.ui.graphics.Shadow(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                        blurRadius = 6f
                                    )
                                } else null
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Expand indicator matching bar position
                        Icon(
                            imageVector = if (isBottom) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.bar_expand_toolbar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Thin progress line along the bottom of the minimized pill while loading
                    if (isLoading && progress > 0f && progress < 1f) {
                        val clamped = progress.coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(clamped)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}
