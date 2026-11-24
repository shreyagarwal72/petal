package com.petal.browser.ui.components

import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
 * Chrome Android-style compact minimized toolbar pill shown on downward scroll.
 *
 * Displays `[Lock/Privacy] domain.com ⌄` in a 40dp rounded container with live Liquid Glass
 * blur and optical lens refraction over the actively scrolling webpage.
 * Tapping or swiping the pill smoothly springs the full toolbar back into place.
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = PetalSpring.quick(),
        label = "pill_press_scale"
    )

    Box(
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
        // 48dp touch target around the 40dp visual pill.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(48.dp)
                .wrapContentWidth()
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
                    currentOnExpand()
                }
        ) {
        Surface(
            shape = pillShape,
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = if (isGlass) 0.dp else 4.dp,
            modifier = Modifier
                .height(40.dp)
                .wrapContentWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(
                    if (isGlass) {
                        Modifier.liquidGlass(pillShape, baseGlassColor, resolvedGlassBar, pressFx = true)
                    } else Modifier
                )
                .clip(pillShape)
        ) {
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
                    overflow = TextOverflow.Ellipsis
                )

                // Expand indicator matching bar position
                Icon(
                    imageVector = if (isBottom) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.bar_expand_toolbar),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        }
    }
}
