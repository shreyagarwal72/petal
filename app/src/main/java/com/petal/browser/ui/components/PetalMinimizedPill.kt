package com.petal.browser.ui.components

import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    val rawStatusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isImmersive = rawStatusTop == 0.dp
    val statusBarTopInset = if (isImmersive) 0.dp else WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()
    val navBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

    val containerColor = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoSurfaceContainer
        isGlass -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pill_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (!isBottom) (if (isImmersive) 10.dp else statusBarTopInset + 6.dp) else 4.dp,
                bottom = if (isBottom) navBarBottomInset + 8.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = pillShape,
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = if (isGlass) 0.dp else 4.dp,
            border = if (isGlass) null else BorderStroke(1.dp, outlineColor),
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
                .pointerInput(Unit) {
                    var dragAccum = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = {
                            if (Math.abs(dragAccum) > 10.dp.toPx()) {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                onExpand()
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
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                    onExpand()
                }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Security / Incognito Icon
                Icon(
                    imageVector = when {
                        isIncognito -> Icons.Rounded.VisibilityOff
                        isHttps -> Icons.Rounded.Lock
                        else -> Icons.Rounded.Lock
                    },
                    contentDescription = if (isHttps) "Secure connection" else "Not secure",
                    tint = if (isHttps && !isIncognito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(15.dp)
                )

                // Domain Label
                Text(
                    text = domain.ifBlank { "Search or type URL" },
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
                    contentDescription = "Expand toolbar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
