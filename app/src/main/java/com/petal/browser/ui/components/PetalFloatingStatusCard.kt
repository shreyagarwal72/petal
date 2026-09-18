/*
 * PetalFloatingStatusCard.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Reusable persistent animated floating status card for Petal Browser.
 *
 * Design & Motion Specifications:
 * 1. Docked near the bottom of the screen with elevated surface styling (M3 Expressive).
 * 2. Spring-based entrance and exit transitions (Material 3 Expressive spring motion).
 * 3. Compact summary presentation:
 *    - Leading icon / thumbnail / status indicator
 *    - Title and subtitle
 *    - Optional primary action button (e.g., Open, Details, Resume)
 *    - Close / dismiss action button
 * 4. Swipe-to-dismiss gesture (horizontal dismiss via SwipeToDismissBox + optional swipe gestures).
 * 5. Slim animated progress indicator docked flush along its bottom edge when the underlying task
 *    has measurable progress (0.0f .. 1.0f) or indeterminate wavy/linear animation.
 */

package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Data specification for configuring a [PetalFloatingStatusCard].
 */
data class PetalFloatingStatusCardData(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val iconContent: (@Composable () -> Unit)? = null,
    val iconContainerColor: Color? = null,
    val iconContentColor: Color? = null,
    val primaryActionLabel: String? = null,
    val onPrimaryAction: (() -> Unit)? = null,
    val secondaryActionLabel: String? = null,
    val onSecondaryAction: (() -> Unit)? = null,
    /**
     * Measurable progress value between 0.0f and 1.0f.
     * When null, no progress indicator is shown unless [isIndeterminateProgress] is true.
     */
    val progress: Float? = null,
    val isIndeterminateProgress: Boolean = false,
    val useWavyProgress: Boolean = true,
    val progressColor: Color? = null,
    val containerColor: Color? = null,
    val contentColor: Color? = null
)

/**
 * Reusable persistent animated floating status card.
 *
 * @param visible Controls entrance/exit animation using spring physics.
 * @param data Card content, status, and actions.
 * @param onDismiss Invoked when the user swipes away or taps the close button.
 * @param modifier Outer modifier.
 * @param bottomPadding Additional bottom padding to clear navigation bars or floating docks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalFloatingStatusCard(
    visible: Boolean,
    data: PetalFloatingStatusCardData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp,
    onClick: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(visible) {
        if (visible) {
            dismissState.reset()
        }
    }

    // Spring animation specs matching Petal's motion system
    val enterSpringSpec = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val exitSpringSpec = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it * 2 },
            animationSpec = enterSpringSpec
        ) + fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it * 2 },
            animationSpec = exitSpringSpec
        ) + fadeOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + scaleOut(
            targetScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        modifier = modifier
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = bottomPadding)
        ) {
            val cardShape = RoundedCornerShape(24.dp)
            val effectiveContainerColor = data.containerColor
                ?: MaterialTheme.colorScheme.surfaceContainerHigh
            val effectiveContentColor = data.contentColor
                ?: MaterialTheme.colorScheme.onSurface

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 10.dp,
                        shape = cardShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                    .clip(cardShape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else Modifier
                    ),
                shape = cardShape,
                color = effectiveContainerColor,
                contentColor = effectiveContentColor,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Leading Icon / Thumbnail Container
                        if (data.iconContent != null) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                data.iconContent.invoke()
                            }
                            Spacer(Modifier.width(12.dp))
                        } else if (data.icon != null) {
                            val iconBg = data.iconContainerColor
                                ?: MaterialTheme.colorScheme.primaryContainer
                            val iconTint = data.iconContentColor
                                ?: MaterialTheme.colorScheme.onPrimaryContainer

                            Surface(
                                shape = CircleShape,
                                color = iconBg,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = data.icon,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                        }

                        // Title & Subtitle
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = data.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = effectiveContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!data.subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = data.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Secondary Action Button (if provided)
                        if (data.secondaryActionLabel != null && data.onSecondaryAction != null) {
                            Button(
                                onClick = data.onSecondaryAction,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(
                                    text = data.secondaryActionLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }

                        // Primary Action Button
                        if (data.primaryActionLabel != null && data.onPrimaryAction != null) {
                            Button(
                                onClick = data.onPrimaryAction,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = data.primaryActionLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }

                        // Close / Dismiss Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss status card",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Animated progress indicator docked flush along bottom edge
                    val barColor = data.progressColor ?: MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                    if (data.useWavyProgress) {
                        if (data.isIndeterminateProgress) {
                            LinearRipplingWavyProgressIndicator(
                                progress = null,
                                modifier = Modifier.fillMaxWidth(),
                                height = 4.5.dp,
                                strokeWidth = 3.5.dp,
                                waveAmplitude = 2.5.dp,
                                waveWavelength = 22.dp,
                                activeColor = barColor,
                                trackColor = trackColor
                            )
                        } else if (data.progress != null) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = data.progress.coerceIn(0f, 1f),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "floatingCardProgress"
                            )
                            LinearRipplingWavyProgressIndicator(
                                progress = animatedProgress,
                                modifier = Modifier.fillMaxWidth(),
                                height = 4.5.dp,
                                strokeWidth = 3.5.dp,
                                waveAmplitude = 2.5.dp,
                                waveWavelength = 22.dp,
                                activeColor = barColor,
                                trackColor = trackColor
                            )
                        }
                    } else {
                        if (data.isIndeterminateProgress) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp),
                                color = barColor,
                                trackColor = trackColor,
                                strokeCap = StrokeCap.Round
                            )
                        } else if (data.progress != null) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = data.progress.coerceIn(0f, 1f),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "floatingCardProgress"
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp),
                                color = barColor,
                                trackColor = trackColor,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}
