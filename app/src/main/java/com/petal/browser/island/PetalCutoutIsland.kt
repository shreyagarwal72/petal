/*
 * PetalCutoutIsland.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Dynamic Cutout Island.
 * Ported & adapted from expressive-cutout for Petal Browser.
 *
 * Full animations & transitions parity with expressive-cutout:
 * - Spatial springs with configurable speed & bounce
 * - Authentic tap "boop" physics (scale dip and spring recovery)
 * - Wavy progress bar animation
 * - Multi-activity satellite bubble split
 * - Expand/collapse spatial transforms
 */

package com.petal.browser.island

import android.os.Build
import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.LiveActionItem
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PetalCutoutIsland(
    modifier: Modifier = Modifier,
    actionListener: LiveActionActionListener? = null
) {
    val busState by PetalLiveActionBus.state.collectAsState()
    val primaryItem = busState.primary
    val secondaryItem = busState.secondary

    if (!busState.hasActiveAction || primaryItem == null) {
        return
    }

    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }

    // Read user settings
    val liveUpdatesEnabled = remember(sp) { sp.getBoolean("sp_live_updates", true) }
    if (!liveUpdatesEnabled) return

    val islandMode = remember(sp) { sp.getString("sp_island_mode", "AUTO") ?: "AUTO" }
    if ("NOTIFICATION_ONLY".equals(islandMode, ignoreCase = true)) return

    // If AUTO mode and native Android 16 live actions are supported, let status bar chip lead
    val isNativeA16Promoted = remember(context) {
        Build.VERSION.SDK_INT >= 36 && LiveUpdateNotificationManager.canPostPromotedNotifications(context)
    }

    // Build configuration-aware IslandMotion
    val animStyleStr = remember(sp) { sp.getString("sp_island_anim_style", "EXPRESSIVE") ?: "EXPRESSIVE" }
    val animSpeedStr = remember(sp) { sp.getString("sp_island_anim_speed", "DEFAULT") ?: "DEFAULT" }
    val animBounceStr = remember(sp) { sp.getString("sp_island_anim_bounce", "NORMAL") ?: "NORMAL" }
    val wavyProgressEnabled = remember(sp) { sp.getBoolean("sp_island_wavy_progress", true) }

    val motion = remember(animStyleStr, animSpeedStr, animBounceStr) {
        val style = if ("EASE_IN_OUT".equals(animStyleStr, ignoreCase = true)) IslandAnimationStyle.EASE_IN_OUT else IslandAnimationStyle.EXPRESSIVE
        val speed = when (animSpeedStr.uppercase(Locale.US)) {
            "SLOW" -> IslandAnimationSpeed.SLOW
            "FAST" -> IslandAnimationSpeed.FAST
            else -> IslandAnimationSpeed.DEFAULT
        }
        val bounce = when (animBounceStr.uppercase(Locale.US)) {
            "SMALL" -> IslandAnimationBounce.SMALL
            "BIG" -> IslandAnimationBounce.BIG
            else -> IslandAnimationBounce.NORMAL
        }
        IslandMotion(style = style, speed = speed, bounce = bounce)
    }

    var isExpanded by remember { mutableStateOf(false) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val coroutineScope = rememberCoroutineScope()

    // Tap Boop Scale Physics (from expressive-cutout)
    val scale = remember { Animatable(1f) }

    // Smooth Dimension transitions driven by IslandMotion.dp()
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 20.dp,
        animationSpec = motion.dp(),
        label = "islandCornerRadius"
    )
    val shape = RoundedCornerShape(cornerRadius)

    val surfaceColor = if (isGlass) {
        petalGroupSurfaceColor()
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Main Cutout Island Container with Boop Spring Scale
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .animateContentSize(animationSpec = motion.dp())
                    .clip(shape)
                    .liquidGlassChrome(shape = shape, enabled = isGlass)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Trigger authentic reference-app tap boop
                        coroutineScope.launch {
                            scale.animateTo(0.95f, motion.boop())
                            scale.animateTo(1f, motion.boop())
                        }
                        isExpanded = !isExpanded
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -15f) isExpanded = false
                            else if (dragAmount > 15f) isExpanded = true
                        }
                    },
                shape = shape,
                color = surfaceColor,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn(animationSpec = motion.float()) +
                                scaleIn(initialScale = 0.92f, animationSpec = motion.float()))
                            .togetherWith(
                                fadeOut(animationSpec = motion.float()) +
                                        scaleOut(targetScale = 0.92f, animationSpec = motion.float())
                            )
                    },
                    label = "PetalIslandExpandCollapse"
                ) { expanded ->
                    if (!expanded) {
                        CompactIslandPill(
                            item = primaryItem,
                            onTogglePlayPause = {
                                actionListener?.onMediaTogglePlayPause()
                                    ?: PetalLiveActionBus.getActionListener()?.onMediaTogglePlayPause()
                            }
                        )
                    } else {
                        ExpandedIslandCard(
                            item = primaryItem,
                            actionListener = actionListener ?: PetalLiveActionBus.getActionListener(),
                            wavyEnabled = wavyProgressEnabled,
                            onCollapse = { isExpanded = false }
                        )
                    }
                }
            }

            // Satellite Pill for Multi-Activity Split Pill Mode
            if (secondaryItem != null && !isExpanded) {
                Spacer(modifier = Modifier.width(8.dp))
                SatelliteBubble(
                    item = secondaryItem,
                    motion = motion,
                    onClick = {
                        coroutineScope.launch {
                            scale.animateTo(0.95f, motion.boop())
                            scale.animateTo(1f, motion.boop())
                        }
                        isExpanded = true
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactIslandPill(
    item: LiveActionItem,
    onTogglePlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (item) {
            is LiveActionItem.Download -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isPaused) Icons.Rounded.Pause else Icons.Rounded.ArrowDownward,
                        contentDescription = "Download",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    text = "${item.progressPercent}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = item.speedFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                if (!item.isPaused) {
                    PulsingIndicatorDot()
                }
            }

            is LiveActionItem.Media -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Media",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.widthIn(max = 130.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                SoundwaveEqualizer(isPlaying = item.isPlaying)
            }

            is LiveActionItem.Task -> {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = item.taskName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${item.progressPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun SatelliteBubble(
    item: LiveActionItem,
    motion: IslandMotion,
    onClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val satScale = remember { Animatable(1f) }

    Surface(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = satScale.value
                scaleY = satScale.value
            }
            .clip(CircleShape)
            .clickable {
                coroutineScope.launch {
                    satScale.animateTo(0.92f, motion.boop())
                    satScale.animateTo(1f, motion.boop())
                }
                onClick()
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (item) {
                is LiveActionItem.Download -> {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Download satellite",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                is LiveActionItem.Media -> {
                    SoundwaveEqualizer(isPlaying = item.isPlaying)
                }
                is LiveActionItem.Task -> {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedIslandCard(
    item: LiveActionItem,
    actionListener: LiveActionActionListener?,
    wavyEnabled: Boolean,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 360.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (item) {
            is LiveActionItem.Download -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.isPaused) "Paused" else "${item.speedFormatted} • ${item.progressPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Wavy progress bar adhering to user settings
                PetalWavyProgressBar(
                    progress = item.progressPercent / 100f,
                    waveColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    isWavy = wavyEnabled && !item.isPaused,
                    animated = wavyEnabled && !item.isPaused
                )

                val downloadedStr = Formatter.formatFileSize(context, item.bytesDownloaded)
                val totalStr = if (item.totalBytes > 0) Formatter.formatFileSize(context, item.totalBytes) else "--"
                val etaStr = if (item.etaSeconds > 0) "${item.etaSeconds}s remaining" else ""

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$downloadedStr / $totalStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (etaStr.isNotEmpty()) {
                        Text(
                            text = etaStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { actionListener?.onDownloadTogglePause(item.downloadId) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (item.isPaused) "Resume" else "Pause", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            actionListener?.onDownloadCancel(item.downloadId)
                            onCollapse()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel", fontSize = 13.sp)
                    }

                    IconButton(
                        onClick = {
                            actionListener?.onOpenDownloads()
                            onCollapse()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = "Downloads folder"
                        )
                    }
                }
            }

            is LiveActionItem.Media -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs) else 0f
                PetalWavyProgressBar(
                    progress = progress,
                    waveColor = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    isWavy = wavyEnabled && item.isPlaying,
                    animated = wavyEnabled && item.isPlaying
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { actionListener?.onMediaSkip(-10) }) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "Skip back 10s"
                        )
                    }

                    FilledIconButton(
                        onClick = { actionListener?.onMediaTogglePlayPause() },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (item.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = { actionListener?.onMediaSkip(10) }) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "Skip forward 10s"
                        )
                    }
                }
            }

            is LiveActionItem.Task -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.taskName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                    }
                }
                PetalWavyProgressBar(
                    progress = item.progressPercent / 100f,
                    waveColor = MaterialTheme.colorScheme.tertiary,
                    isWavy = wavyEnabled,
                    animated = wavyEnabled
                )
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PulsingIndicatorDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}

@Composable
private fun SoundwaveEqualizer(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "soundwave")
    val bar1 by transition.animateFloat(
        initialValue = 4f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 12f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 6f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        modifier = Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val h1 = if (isPlaying) bar1.dp else 4.dp
        val h2 = if (isPlaying) bar2.dp else 8.dp
        val h3 = if (isPlaying) bar3.dp else 5.dp
        val color = MaterialTheme.colorScheme.primary

        Box(modifier = Modifier.width(2.5.dp).height(h1).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.width(2.5.dp).height(h2).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.width(2.5.dp).height(h3).clip(RoundedCornerShape(1.dp)).background(color))
    }
}
