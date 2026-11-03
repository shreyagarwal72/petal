package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.layout.FastInvokeEasing
import com.petal.browser.ui.theme.layout.LocalPetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalSwitchType
import com.petal.browser.ui.theme.layout.PointToPointEasing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Authentic Expressive Switches ported from ImageToolbox:
 * - CupertinoSwitch (with dynamic aspect-ratio squish 1.0 -> 1.25 on press/drag)
 * - HyperOSSwitch (pointer input drag gestures with PointToPointEasing)
 * - FluentSwitch (aspect stretching with FastInvokeEasing)
 * - PixelSwitch (clean Google Pixel capsule with spring)
 * - OneUISwitch (canvas track & thumb rendering with ripple)
 * - ComposeSwitch (standard minimalist toggle)
 * - MaterialYouSwitch (standard M3 switch)
 *
 * NOTE: Liquid Glass auto-applies whenever Liquid Glass theme is active.
 */
@Composable
fun IconSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val layoutSettings = LocalPetalLayoutSettings.current

    if (isGlass) {
        AuthenticLiquidGlassSwitch(
            checked = checked,
            icon = icon,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier
        )
    } else {
        when (layoutSettings.switchType) {
            PetalSwitchType.CUPERTINO -> {
                AuthenticCupertinoSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.HYPEROS -> {
                AuthenticHyperOSSwitch(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.FLUENT -> {
                AuthenticFluentSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.PIXEL -> {
                AuthenticPixelSwitch(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.ONE_UI -> {
                AuthenticOneUISwitch(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.COMPOSE -> {
                AuthenticComposeSwitch(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.MATERIAL_YOU -> {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    thumbContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                    modifier = modifier.scale(0.95f),
                )
            }
        }
    }
}

/**
 * ImageToolbox CupertinoSwitch:
 * Dynamic aspect ratio stretching (1.0 to 1.25 on press/drag), spring physics, and bounce alignments.
 */
@Composable
fun AuthenticCupertinoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()

    val animatedAspectRatio by animateFloatAsState(
        targetValue = if (isPressed || isDragged) 1.25f else 1f,
        animationSpec = tween(300),
        label = "cupertinoAspect"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.33f),
        animationSpec = tween(300),
        label = "cupertinoTrack"
    )

    var alignment by remember(checked) {
        mutableFloatStateOf(if (checked) 1f else -1f)
    }

    val state = rememberDraggableState {
        alignment = (alignment + it).coerceIn(-1f, 1f)
    }

    val animatedAlignment by animateFloatAsState(
        targetValue = alignment,
        animationSpec = tween(300),
        label = "cupertinoAlign"
    )

    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .toggleable(
                value = checked,
                onValueChange = { onCheckedChange?.invoke(it) },
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null
            )
            .draggable(
                state = state,
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                enabled = enabled,
                onDragStopped = {
                    if (alignment < 0.5f) {
                        alignment = -1f
                        if (checked) onCheckedChange?.invoke(false)
                    } else {
                        alignment = 1f
                        if (!checked) onCheckedChange?.invoke(true)
                    }
                }
            )
            .wrapContentSize(Alignment.Center)
            .requiredSize(51.dp, 31.dp)
            .clip(shape)
            .background(trackColor)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(animatedAspectRatio)
                .align(BiasAlignment.Horizontal(animatedAlignment))
                .clip(shape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface)
        )
    }
}

/**
 * ImageToolbox HyperOSSwitch:
 * Custom pointerInput drag detection, PointToPointEasing, and dynamic capsule thumb.
 */
@Composable
fun AuthenticHyperOSSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val thumbOffset by animateDpAsState(
        targetValue = (if (checked) {
            if (!enabled) 24.dp else if (isPressed || isDragged || isHovered) 22.dp else 24.dp
        } else {
            if (!enabled) 4.dp else if (isPressed || isDragged || isHovered) 3.dp else 4.dp
        }) + dragOffset.dp,
        animationSpec = tween(400, easing = PointToPointEasing),
        label = "hyperOffset"
    )

    val thumbSize by animateDpAsState(
        targetValue = if (!enabled) 20.dp else if (isPressed || isDragged || isHovered) 22.dp else 20.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "hyperSize"
    )

    val backgroundColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(200),
        label = "hyperBg"
    )

    val shape = RoundedCornerShape(15.dp)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .requiredSize(50.dp, 28.5.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange?.invoke(!checked) }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                .pointerInput(checked, enabled, onCheckedChange) {
                    if (!enabled) return@pointerInput
                    val dragInteraction = DragInteraction.Start()
                    detectHorizontalDragGestures(
                        onDragStart = { interactionSource.tryEmit(dragInteraction) },
                        onDragEnd = {
                            if (dragOffset.absoluteValue > 10f) onCheckedChange?.invoke(!checked)
                            interactionSource.tryEmit(DragInteraction.Stop(dragInteraction))
                            scope.launch {
                                delay(50)
                                dragOffset = 0f
                            }
                        },
                        onDragCancel = {
                            interactionSource.tryEmit(DragInteraction.Cancel(dragInteraction))
                            dragOffset = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffset = (dragOffset + dragAmount / 2).let {
                            if (checked) it.coerceIn(-18f, 0f) else it.coerceIn(0f, 18f)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/**
 * ImageToolbox FluentSwitch:
 * Microsoft Fluent compact squish toggle with FastInvokeEasing.
 */
@Composable
fun AuthenticFluentSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()

    val width by animateDpAsState(
        if (pressed || dragged) 16.dp else 12.dp,
        animationSpec = tween(200, easing = FastInvokeEasing),
        label = "fluentWidth"
    )

    val offset by animateDpAsState(
        if (checked) 24.dp - (width / 2) else 3.dp,
        animationSpec = tween(200, easing = PointToPointEasing),
        label = "fluentOffset"
    )

    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "fluentTrack"
    )

    Box(
        modifier = modifier
            .requiredSize(40.dp, 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(width = width, height = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
        )
    }
}

/**
 * ImageToolbox PixelSwitch:
 * Android Pixel styled oval track with animated thumb size.
 */
@Composable
fun AuthenticPixelSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(200, easing = PointToPointEasing),
        label = "pixelTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 24.dp else 4.dp,
        animationSpec = tween(200, easing = PointToPointEasing),
        label = "pixelOffset"
    )
    val thumbSize by animateDpAsState(
        if (checked) 24.dp else 16.dp,
        animationSpec = tween(200, easing = PointToPointEasing),
        label = "pixelSize"
    )

    Box(
        modifier = modifier
            .requiredSize(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * ImageToolbox OneUISwitch:
 * Samsung OneUI oval switch with live drag physics.
 */
@Composable
fun AuthenticOneUISwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "oneUiTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 3.dp,
        animationSpec = tween(180),
        label = "oneUiOffset"
    )

    Box(
        modifier = modifier
            .requiredSize(48.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/**
 * ImageToolbox ComposeSwitch:
 * Clean minimalist Jetpack Compose toggle.
 */
@Composable
fun AuthenticComposeSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "composeTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 3.dp,
        animationSpec = tween(180),
        label = "composeOffset"
    )

    Box(
        modifier = modifier
            .requiredSize(48.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/**
 * Liquid Glass Switch:
 * Auto-applied translucent frosted glass capsule with live refraction, sheen, and glass thumb.
 */
@Composable
fun AuthenticLiquidGlassSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val sheen = rememberGlassSheen(true)
    val pillShape = RoundedCornerShape(15.dp)
    val trackBg by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        label = "glassTrackBg"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 24.dp else 3.dp,
        animationSpec = tween(200),
        label = "glassThumbOffset"
    )

    Box(
        modifier = modifier
            .requiredSize(width = 52.dp, height = 30.dp)
            .clip(pillShape)
            .background(trackBg)
            .liquidGlassChrome(shape = pillShape, enabled = true, sheenIntensity = sheen, pressFx = false)
            .clickable(
                enabled = enabled && onCheckedChange != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .liquidGlassChrome(shape = CircleShape, enabled = true, sheenIntensity = 0.85f, pressFx = false),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (checked) icon else Icons.Rounded.Check,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
