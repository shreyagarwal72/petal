package com.petal.browser.ui.components

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar

/**
 * Chrome Android-style compact minimized toolbar pill shown on downward scroll.
 *
 * Displays `[Lock/Privacy] domain.com ›` in a 36dp rounded container. Tapping the pill
 * or swiping expands the full toolbar back into place.
 */
@Composable
fun PetalMinimizedPill(
    domain: String,
    isHttps: Boolean,
    isIncognito: Boolean,
    isBottom: Boolean,
    glassSource: View? = null,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pillShape = RoundedCornerShape(18.dp)
    val isGlass = glassSource != null
    val glassBar = rememberGlassBar(glassSource)
    val baseGlassColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val containerColor = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoSurfaceContainer
        isGlass -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = if (isBottom) 8.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = pillShape,
            color = containerColor,
            tonalElevation = if (isGlass) 0.dp else 3.dp,
            shadowElevation = if (isGlass) 0.dp else 4.dp,
            border = if (!isGlass) BorderStroke(0.8.dp, outlineColor) else null,
            modifier = Modifier
                .height(36.dp)
                .wrapContentWidth()
                .then(
                    if (isGlass) {
                        Modifier.liquidGlass(pillShape, baseGlassColor, glassBar)
                    } else Modifier
                )
                .clip(pillShape)
                .pointerInput(Unit) {
                    var dragAccum = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = {
                            // Any significant upward or downward drag triggers expansion
                            if (Math.abs(dragAccum) > 12.dp.toPx()) {
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
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true)
                ) {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                    onExpand()
                }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Security / Incognito Icon
                Icon(
                    imageVector = when {
                        isIncognito -> Icons.Rounded.VisibilityOff
                        isHttps -> Icons.Rounded.Lock
                        else -> Icons.Rounded.Lock
                    },
                    contentDescription = if (isHttps) "Secure connection" else "Not secure",
                    tint = if (isHttps && !isIncognito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )

                // Domain Label
                Text(
                    text = domain.ifBlank { "Search or type URL" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Chevron icon indicating tap-to-expand
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Expand toolbar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
