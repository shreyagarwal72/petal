package com.petal.browser.now

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val SATELLITE_GAP_DP = 8
private const val DISMISS_THRESHOLD_DP = 76f
private const val VERTICAL_GESTURE_THRESHOLD_DP = 28f

@Composable
internal fun PetalNowCapsule(
    entry: PetalNowEntry,
    satellite: PetalNowEntry?,
    displayState: PetalNowDisplayState,
    motion: PetalNowMotion,
    onChangeDisplayState: (PetalNowDisplayState) -> Unit,
    onSwapSatelliteToPrimary: () -> Unit,
    onDismiss: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    val pressScale = remember { Animatable(1f) }
    val popScale = remember { Animatable(1f) }
    val badgePop = remember { Animatable(1f) }
    val dismissOffsetX = remember { Animatable(0f) }
    val pullStretch = remember { Animatable(0f) }

    LaunchedEffect(entry.familyKey, entry.slotKey) {
        dismissOffsetX.snapTo(0f)
        if (!entry.isHeroResting) {
            motion.popIn(badgePop)
        }
    }

    LaunchedEffect(entry.stateFlipActive) {
        if (entry.kind == PetalNowCardKind.ADBLOCK && entry.stateFlipActive != null) {
            motion.pop(popScale, peak = 1.06f)
        }
    }

    val cornerRadius by animateDpAsState(
        targetValue = when (displayState) {
            PetalNowDisplayState.COLLAPSED -> 20.dp
            PetalNowDisplayState.COMPACT -> 22.dp
            PetalNowDisplayState.EXPANDED -> 28.dp
        }.coerceAtLeast(0.dp),
        animationSpec = motion.dp(),
        label = "nowCorner",
    )

    val safeCorner = cornerRadius.coerceAtLeast(8.dp)
    val capsuleShape = RoundedCornerShape(safeCorner)

    val baseSurfaceColor = if (isGlass) {
        petalGroupSurfaceColor()
    } else if (displayState == PetalNowDisplayState.EXPANDED) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val surfaceColor by animateColorAsState(
        targetValue = baseSurfaceColor,
        animationSpec = tween(durationMillis = if (motion.reduceMotion) 1 else 200),
        label = "nowSurfaceColor",
    )

    val borderStroke = if (!isGlass) {
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = if (displayState == PetalNowDisplayState.EXPANDED) 0.55f else 0.42f
            ),
        )
    } else null

    val horizontalThresholdPx = with(density) { DISMISS_THRESHOLD_DP.dp.toPx() }
    val verticalThresholdPx = with(density) { VERTICAL_GESTURE_THRESHOLD_DP.dp.toPx() }
    val verticalResistancePx = with(density) { 120.dp.toPx() }

    val showSatellite = satellite != null && displayState != PetalNowDisplayState.EXPANDED

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (displayState == PetalNowDisplayState.EXPANDED) {
                        Modifier
                            .padding(horizontal = 14.dp)
                            .widthIn(min = 280.dp, max = 384.dp)
                            .fillMaxWidth()
                    } else if (displayState == PetalNowDisplayState.COMPACT) {
                        Modifier.widthIn(min = 148.dp, max = 288.dp)
                    } else {
                        Modifier.widthIn(min = 44.dp, max = 140.dp)
                    }
                )
                .onGloballyPositioned { coords ->
                    onBoundsChanged(coords.boundsInWindow())
                }
                .graphicsLayer {
                    val stretch = pullStretch.value.coerceIn(0f, 1f)
                    val s = pressScale.value * popScale.value * (1f + stretch * 0.03f)
                    scaleX = s
                    scaleY = s
                    translationX = dismissOffsetX.value
                    val dismissFrac = (abs(dismissOffsetX.value) / horizontalThresholdPx).coerceIn(0f, 1f)
                    alpha = (1f - dismissFrac * 0.45f).coerceIn(0f, 1f)
                }
                .liquidGlassChrome(shape = capsuleShape, enabled = isGlass, pressFx = false)
                .pointerInput(entry.slotKey, displayState, entry.canSwipeDismiss) {
                    detectTapGestures(
                        onPress = {
                            scope.launch { pressScale.animateTo(0.95f, motion.boop()) }
                            tryAwaitRelease()
                            scope.launch { pressScale.animateTo(1f, motion.boop()) }
                        },
                        onTap = {
                            haptic.play(PetalHapticEngine.Pattern.CLICK, 0.55f)
                            when (displayState) {
                                PetalNowDisplayState.COLLAPSED -> onChangeDisplayState(PetalNowDisplayState.EXPANDED)
                                PetalNowDisplayState.COMPACT -> onChangeDisplayState(PetalNowDisplayState.EXPANDED)
                                PetalNowDisplayState.EXPANDED -> onChangeDisplayState(entry.defaultState)
                            }
                        },
                        onLongPress = {
                            haptic.play(PetalHapticEngine.Pattern.SOFT_BUMP, 0.7f)
                            onChangeDisplayState(
                                if (displayState == PetalNowDisplayState.EXPANDED) entry.defaultState
                                else PetalNowDisplayState.EXPANDED
                            )
                        },
                    )
                }
                .pointerInput(entry.slotKey, displayState, entry.canSwipeDismiss) {
                    if (!entry.canSwipeDismiss) return@pointerInput
                    var armed = false
                    detectHorizontalDragGestures(
                        onDragStart = { armed = false },
                        onDragCancel = {
                            scope.launch {
                                dismissOffsetX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onDragEnd = {
                            val x = dismissOffsetX.value
                            if (abs(x) >= horizontalThresholdPx) {
                                onDismiss()
                            } else {
                                scope.launch {
                                    dismissOffsetX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val next = dismissOffsetX.value + dragAmount
                        if (!armed && abs(next) >= horizontalThresholdPx) {
                            armed = true
                            haptic.thresholdCrossed(context)
                        }
                        scope.launch { dismissOffsetX.snapTo(next) }
                    }
                }
                .pointerInput(entry.slotKey, displayState, entry.canSwipeDismiss) {
                    var dragTotal = 0f
                    val velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragTotal = 0f
                            velocityTracker.resetTracking()
                        },
                        onDragCancel = {
                            scope.launch { pullStretch.animateTo(0f, motion.float()) }
                        },
                        onDragEnd = {
                            val vy = velocityTracker.calculateVelocity().y
                            val initV = vy.coerceAtLeast(0f) * (1f - pullStretch.value) / verticalResistancePx
                            scope.launch { pullStretch.animateTo(0f, motion.float(), initialVelocity = initV) }
                            when {
                                dragTotal >= verticalThresholdPx && displayState != PetalNowDisplayState.EXPANDED -> {
                                    haptic.play(PetalHapticEngine.Pattern.TICK, 0.5f)
                                    onChangeDisplayState(PetalNowDisplayState.EXPANDED)
                                }
                                dragTotal <= -verticalThresholdPx && displayState == PetalNowDisplayState.EXPANDED -> {
                                    haptic.play(PetalHapticEngine.Pattern.TICK, 0.5f)
                                    onChangeDisplayState(entry.defaultState)
                                }
                                dragTotal <= -verticalThresholdPx && displayState == PetalNowDisplayState.COMPACT -> {
                                    onChangeDisplayState(PetalNowDisplayState.COLLAPSED)
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        dragTotal += dragAmount
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        val stretch = 1f - exp(-dragTotal.coerceAtLeast(0f) / verticalResistancePx)
                        scope.launch { pullStretch.snapTo(stretch) }
                    }
                },
            shape = capsuleShape,
            color = surfaceColor,
            tonalElevation = if (isGlass) 0.dp else if (displayState == PetalNowDisplayState.EXPANDED) 3.dp else 2.dp,
            shadowElevation = 0.dp,
            border = borderStroke,
        ) {
            AnimatedContent(
                targetState = displayState,
                transitionSpec = {
                    val inFade = fadeIn(motion.fade(170)) + scaleIn(
                        initialScale = if (motion.reduceMotion) 1f else 0.94f,
                        animationSpec = motion.fade(190),
                    )
                    val outFade = fadeOut(motion.fade(130)) + scaleOut(
                        targetScale = if (motion.reduceMotion) 1f else 0.95f,
                        animationSpec = motion.fade(130),
                    )
                    inFade.togetherWith(outFade)
                },
                label = "nowStateContent",
            ) { state ->
                when (state) {
                    PetalNowDisplayState.COLLAPSED -> CollapsedPillContent(
                        entry = entry,
                        motion = motion,
                        badgePop = badgePop.value,
                    )
                    PetalNowDisplayState.COMPACT -> CompactPillContent(
                        entry = entry,
                        motion = motion,
                        badgePop = badgePop.value,
                        onExpand = { onChangeDisplayState(PetalNowDisplayState.EXPANDED) },
                    )
                    PetalNowDisplayState.EXPANDED -> ExpandedCardContent(
                        entry = entry,
                        motion = motion,
                        onCollapse = { onChangeDisplayState(entry.defaultState) },
                        onDismiss = onDismiss,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSatellite,
            enter = fadeIn(motion.fade()) + scaleIn(initialScale = 0.7f, animationSpec = motion.float()),
            exit = fadeOut(motion.fade(140)) + scaleOut(targetScale = 0.7f, animationSpec = motion.fade(140)),
        ) {
            if (satellite != null) {
                Row {
                    Spacer(Modifier.width(SATELLITE_GAP_DP.dp))
                    PetalNowSatelliteBubble(
                        entry = satellite,
                        isGlass = isGlass,
                        motion = motion,
                        onClick = {
                            haptic.play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            onSwapSatelliteToPrimary()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedPillContent(
    entry: PetalNowEntry,
    motion: PetalNowMotion,
    badgePop: Float,
) {
    val accent = rememberPetalNowAccent(entry, motion)
    Row(
        modifier = Modifier
            .height(38.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PetalNowShapeBadge(
            entry = entry,
            size = 24.dp,
            iconSize = 14.dp,
            motion = motion,
            modifier = Modifier.graphicsLayer {
                scaleX = badgePop
                scaleY = badgePop
            },
        )
        val badgeText = entry.collapsedBadgeText
        if (!badgeText.isNullOrBlank()) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(end = 2.dp),
            )
        } else if (entry.progress != null && !entry.progress.isIndeterminate && entry.progress.fraction != null) {
            CircularProgressIndicator(
                progress = { entry.progress.fraction },
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = accent.onContainer,
                trackColor = accent.container.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun CompactPillContent(
    entry: PetalNowEntry,
    motion: PetalNowMotion,
    badgePop: Float,
    onExpand: () -> Unit,
) {
    val accent = rememberPetalNowAccent(entry, motion)
    val listener = PetalLiveActionBus.getActionListener()

    Column {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PetalNowShapeBadge(
                entry = entry,
                size = 28.dp,
                iconSize = 16.dp,
                motion = motion,
                modifier = Modifier.graphicsLayer {
                    scaleX = badgePop
                    scaleY = badgePop
                },
            )

            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.compactText.isNotBlank() && entry.compactText != entry.title) {
                    Text(
                        text = entry.compactText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Single ergonomic inline quick action depending on activity type
            when {
                entry.kind == PetalNowCardKind.MEDIA -> {
                    Surface(
                        onClick = { listener?.onMediaTogglePlayPause() },
                        shape = RoundedCornerShape(14.dp),
                        color = accent.container,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (entry.isAudioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (entry.isAudioPlaying) "Pause" else "Play",
                                tint = accent.onContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                entry.kind == PetalNowCardKind.DOWNLOAD && entry.progress != null && !entry.progress.isIndeterminate -> {
                    Text(
                        text = "${entry.progress.percent}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent.onContainer,
                    )
                }
                entry.kind == PetalNowCardKind.ADBLOCK && entry.toggle != null -> {
                    Surface(
                        onClick = { entry.toggle.onChange(!entry.toggle.checked) },
                        shape = RoundedCornerShape(12.dp),
                        color = accent.container,
                    ) {
                        Text(
                            text = if (entry.toggle.checked) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = accent.onContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) },
                    )
                }
            }
        }

        val frac = entry.progress?.fraction
        if (frac != null && entry.kind != PetalNowCardKind.ADBLOCK) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 3.dp)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac.coerceIn(0.03f, 1f))
                        .height(2.5.dp)
                        .background(accent.onContainer),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedCardContent(
    entry: PetalNowEntry,
    motion: PetalNowMotion,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = rememberPetalNowAccent(entry, motion)
    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header row: Badge + Category tag + Collapse / Close buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                PetalNowShapeBadge(
                    entry = entry,
                    size = 40.dp,
                    iconSize = 22.dp,
                    motion = motion,
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = entry.categoryTag.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        ),
                        color = accent.onContainer,
                        maxLines = 1,
                    )
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (entry.canSwipeDismiss) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (entry.subtitle.isNotBlank()) {
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Stat chips (Ad Blocker blocked breakdown or Download speed/ETA/size)
        if (entry.stats.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.stats.forEach { stat ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stat.value,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Progress bar (Wavy or standard)
        if (entry.progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PetalNowWavyProgressBar(
                    progress = entry.progress,
                    motion = motion,
                    waveColor = accent.onContainer,
                )
                val trailing = entry.progress.trailingLabel
                if (!trailing.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (entry.progress.isPaused) "Paused" else "Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = trailing,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Interactive Toggle (Hero Ad Blocker ON/OFF switch with animated flip feedback)
        if (entry.toggle != null) {
            Surface(
                onClick = {
                    haptic.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                    entry.toggle.onChange(!entry.toggle.checked)
                },
                shape = RoundedCornerShape(18.dp),
                color = if (entry.toggle.checked) accent.container.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = entry.toggle.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!entry.toggle.subtitle.isNullOrBlank()) {
                            Text(
                                text = entry.toggle.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    com.petal.browser.ui.components.IconSwitch(
                        checked = entry.toggle.checked,
                        icon = if (entry.kind == PetalNowCardKind.ADBLOCK) Icons.Rounded.GppGood else Icons.Rounded.Check,
                        onCheckedChange = {
                            haptic.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                            entry.toggle.onChange(it)
                        },
                    )
                }
            }
        }

        // Action row
        if (entry.actions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entry.actions.forEach { act ->
                    val modifierWeight = Modifier.weight(1f)
                    if (act.primary) {
                        Button(
                            onClick = {
                                haptic.play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                act.onClick()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent.onContainer,
                                contentColor = accent.container,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = modifierWeight.heightIn(min = 42.dp),
                        ) {
                            if (act.icon != null) {
                                Icon(act.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = act.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                haptic.play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                act.onClick()
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = modifierWeight.heightIn(min = 42.dp),
                        ) {
                            if (act.icon != null) {
                                Icon(act.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = act.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
