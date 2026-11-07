/*
 * PetalLiveIsland.kt
 * ──────────────────────────────────────────────────────────────────────────
 * The Material 3 Expressive "live update" island. One composable, used in two places:
 *   • inside Petal (PetalNowBar)
 *   • over other apps (PetalLiveOverlayController) for devices without Android 16 Live Updates,
 *     or alongside them when the user picks "Always show island".
 *
 * Design rules
 *   • Only Material 3 Expressive: spatial springs, shape morphing, wavy progress, big pill buttons.
 *   • High contrast on ANY app behind it: inverse surface + inverse primary accent.
 *   • Everything is driven by IslandSettings, so setting changes apply instantly.
 *   • Expanded card collapses by itself after a few seconds of no touch.
 */

package com.petal.browser.island

import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.LiveActionItem
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import com.petal.browser.media.PetalAudioColorEngine
import com.petal.browser.media.PetalCookieShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long an expanded card stays open with no touch before it folds back into the pill. */
private const val AUTO_COLLAPSE_MS = 8_000L

private class IslandPalette(
    val surface: Color,
    val content: Color,
    val contentMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val track: Color
)

@Composable
private fun islandPalette(primaryItem: LiveActionItem?): IslandPalette {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isOled = remember(context) { PetalBurnProtection.isOledDisplay(context) }
    val c = MaterialTheme.colorScheme

    val mediaArt = (primaryItem as? LiveActionItem.Media)?.albumArt
    val audioPalette = remember(mediaArt) {
        if (mediaArt != null) PetalAudioColorEngine.extractPalette(mediaArt) else null
    }

    val surfaceColor = when {
        isOled && isDark -> Color(0xFF000000)
        isDark -> c.surfaceContainerLowest
        else -> c.surfaceContainerHighest
    }

    val accentColor = when {
        primaryItem is LiveActionItem.Achievement -> Color(0xFFFFB74D) // Celebratory Gold
        primaryItem is LiveActionItem.Credential -> Color(0xFF00E676) // Security Emerald
        audioPalette?.primary != null -> audioPalette.primary
        else -> c.primary
    }
    val contentColor = if (isDark) Color(0xFFE6E1E5) else c.onSurface
    val contentMuted = if (isDark) Color(0xFFCAC4D0) else c.onSurfaceVariant

    return IslandPalette(
        surface = surfaceColor,
        content = contentColor,
        contentMuted = contentMuted,
        accent = accentColor,
        onAccent = audioPalette?.onPrimary ?: c.onPrimary,
        accentSoft = accentColor.copy(alpha = 0.20f),
        track = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
    )
}

@Composable
fun PetalLiveIsland(
    state: LiveActionBusState,
    settings: IslandSettings,
    listener: LiveActionActionListener?,
    modifier: Modifier = Modifier,
    /** Applied to the island's own row (e.g. status-bar padding), so a hidden island takes no space. */
    contentModifier: Modifier = Modifier,
    /** Dynamic horizontal offset in dp for physical cutout alignment. */
    cutoutOffsetXDp: Float = 0f,
    /** Lets a host window grow/shrink with the island (used by the overlay). */
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val motion = remember(settings.animStyle, settings.animSpeed, settings.animBounce) { settings.motion }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Keep the last real content alive so the exit animation has something to draw.
    val lastState = remember { mutableStateOf(state) }
    if (state.hasActiveAction) lastState.value = state
    val shown = lastState.value
    val primary = shown.primary
    val secondary = shown.secondary

    val palette = islandPalette(primary)

    // Built-in automatic AMOLED burn-in protection: cyclic micro-shift & luminance clamping
    val burnShift by PetalBurnProtection.rememberOrbiterShift(state.hasActiveAction)
    val oledLuminance = PetalBurnProtection.rememberOledLuminanceAlpha()

    var expanded by remember { mutableStateOf(false) }
    var focusSecondary by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.hasActiveAction) {
        if (!state.hasActiveAction) {
            expanded = false
            focusSecondary = false
        }
    }
    LaunchedEffect(expanded) { onExpandedChange(expanded) }
    LaunchedEffect(expanded, touches) {
        if (expanded) {
            delay(AUTO_COLLAPSE_MS)
            expanded = false
            focusSecondary = false
        }
    }

    val boop = remember { Animatable(1f) }
    fun playBoop() {
        scope.launch {
            boop.animateTo(0.94f, motion.boop())
            boop.animateTo(1f, motion.boop())
        }
    }

    // Smooth placement animation for cutout alignment and vertical offset
    val targetOffsetX = if (expanded) 0f else cutoutOffsetXDp
    val animatedOffsetX by animateFloatAsState(targetOffsetX, animationSpec = motion.float(), label = "islandOffsetX")
    val targetOffsetY = if (expanded) 0f else settings.offsetYDp.toFloat()
    val animatedOffsetY by animateFloatAsState(targetOffsetY, animationSpec = motion.float(), label = "islandOffsetY")

    // Shape morph: pill -> card, or notch with concave flare ears.
    val corner by animateDpAsState(
        targetValue = if (expanded) 32.dp else 22.dp,
        animationSpec = motion.dp(),
        label = "islandCorner"
    )
    val shape: androidx.compose.ui.graphics.Shape = if (settings.isNotch && !expanded) {
        NotchShape(
            flare = settings.notchFlareDp.dp,
            bottomLeft = corner,
            bottomRight = corner
        )
    } else {
        RoundedCornerShape(corner)
    }

    // Wrap the listener so every button press counts as "still using it".
    val touchListener = remember(listener) {
        object : LiveActionActionListener {
            override fun onDownloadTogglePause(downloadId: Long) { touches++; listener?.onDownloadTogglePause(downloadId) }
            override fun onDownloadCancel(downloadId: Long) { touches++; listener?.onDownloadCancel(downloadId) }
            override fun onMediaTogglePlayPause() { touches++; listener?.onMediaTogglePlayPause() }
            override fun onMediaSkip(seconds: Int) { touches++; listener?.onMediaSkip(seconds) }
            override fun onOpenDownloads() { listener?.onOpenDownloads() }
            override fun onOpenBrowser() { listener?.onOpenBrowser() }
            override fun onOpenFileViewer() { listener?.onOpenFileViewer() }
            override fun onOpenProfile() { listener?.onOpenProfile() }
            override fun onOpenPasswords() { listener?.onOpenPasswords() }
            override fun onCopyText(text: String, label: String) { touches++; listener?.onCopyText(text, label) }
            override fun onOpenDownloadedFile(localUri: String, fileName: String) { listener?.onOpenDownloadedFile(localUri, fileName) }
            override fun onDismiss(id: String) { listener?.onDismiss(id) ?: PetalLiveActionBus.dismiss(id) }
        }
    }

    AnimatedVisibility(
        visible = state.hasActiveAction && primary != null,
        modifier = modifier,
        enter = slideInVertically(animationSpec = motion.intOffset()) { -it } +
            scaleIn(initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 0f), animationSpec = motion.float()) +
            fadeIn(animationSpec = motion.swapFade()),
        exit = slideOutVertically(animationSpec = motion.intOffset()) { -it } +
            scaleOut(targetScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 0f), animationSpec = motion.swapScale()) +
            fadeOut(animationSpec = motion.swapFade())
    ) {
        if (primary == null) return@AnimatedVisibility
        val expandedItem = if (focusSecondary && secondary != null) secondary else primary

        Row(
            modifier = contentModifier
                .graphicsLayer {
                    translationX = animatedOffsetX + burnShift.x
                    translationY = animatedOffsetY + burnShift.y
                    alpha = oledLuminance
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .graphicsLayer { scaleX = boop.value; scaleY = boop.value }
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = if (expanded) "Collapse live update" else "Expand live update",
                        role = Role.Button
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playBoop()
                        touches++
                        if (expanded) { expanded = false; focusSecondary = false } else expanded = true
                    }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total > 24f) expanded = true else if (total < -24f) expanded = false
                            }
                        ) { _, dragAmount -> total += dragAmount }
                    },
                shape = shape,
                color = palette.surface,
                contentColor = palette.content,
                tonalElevation = 0.dp,
                shadowElevation = 10.dp
            ) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        (fadeIn(motion.swapFade()) + scaleIn(initialScale = 0.9f, animationSpec = motion.float()))
                            .togetherWith(fadeOut(motion.swapFade()) + scaleOut(targetScale = 0.9f, animationSpec = motion.swapScale()))
                            .using(SizeTransform(clip = false) { _, _ -> motion.intSize() })
                    },
                    label = "islandExpandCollapse"
                ) { isExpanded ->
                    if (isExpanded) {
                        ExpandedCard(
                            item = expandedItem,
                            palette = palette,
                            listener = touchListener,
                            wavy = settings.wavyProgress,
                            onCollapse = { expanded = false; focusSecondary = false }
                        )
                    } else {
                        CompactPill(item = primary, palette = palette)
                    }
                }
            }

            AnimatedVisibility(
                visible = secondary != null && !expanded,
                enter = scaleIn(animationSpec = motion.float()) + fadeIn(motion.swapFade()),
                exit = scaleOut(animationSpec = motion.swapScale()) + fadeOut(motion.swapFade())
            ) {
                if (secondary != null) {
                    SatelliteBubble(
                        item = secondary,
                        palette = palette,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            focusSecondary = true
                            expanded = true
                        }
                    )
                }
            }
        }
    }
}

/* ───────────────────────────── Compact pill ───────────────────────────── */

@Composable
private fun CompactPill(item: LiveActionItem, palette: IslandPalette) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (item) {
            is LiveActionItem.Download -> {
                ProgressBadge(
                    progress = item.progressPercent / 100f,
                    indeterminate = item.isIndeterminate && !item.isPaused && !item.isCompleted,
                    icon = when {
                        item.isCompleted -> Icons.Rounded.Check
                        item.isPaused -> Icons.Rounded.Pause
                        else -> Icons.Rounded.ArrowDownward
                    },
                    palette = palette,
                    size = 32
                )
                Text(
                    text = when {
                        item.isCompleted -> "Downloaded"
                        item.isIndeterminate -> "Downloading"
                        else -> "${item.progressPercent}%"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = palette.content
                )
                if (!item.isPaused && !item.isCompleted && item.speedBytesPerSec > 0) {
                    Text(
                        text = item.speedFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent
                    )
                }
                if (item.isCompleted) {
                    // nothing: the check icon already says it
                } else if (item.isPaused) {
                    Text("Paused", style = MaterialTheme.typography.labelMedium, color = palette.contentMuted)
                } else {
                    PulsingDot(palette.accent)
                }
            }

            is LiveActionItem.Media -> {
                MediaArt(item = item, palette = palette, size = 32, corner = 16)
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
                    modifier = Modifier.widthIn(max = 130.dp),
                    color = palette.content
                )
                Equalizer(isPlaying = item.isPlaying, color = palette.accent)
            }

            is LiveActionItem.Task -> {
                ProgressBadge(
                    progress = item.progressPercent / 100f,
                    indeterminate = false,
                    icon = Icons.Rounded.AutoAwesome,
                    palette = palette,
                    size = 32
                )
                Text(
                    text = item.taskName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
                    modifier = Modifier.widthIn(max = 120.dp),
                    color = palette.content
                )
                Text(
                    text = "${item.progressPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
            }

            is LiveActionItem.FileViewer -> {
                val icon = when (item.fileType) {
                    "PDF" -> Icons.Rounded.PictureAsPdf
                    "IMAGE" -> Icons.Rounded.Image
                    "CODE", "KT", "JS", "PY", "HTML" -> Icons.Rounded.Code
                    else -> Icons.Rounded.Description
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveButtonShape)
                        .background(palette.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = item.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
                    modifier = Modifier.widthIn(max = 130.dp),
                    color = palette.content
                )
                Text(
                    text = item.fileType,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = palette.accent
                )
            }

            is LiveActionItem.Achievement -> {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveButtonShape)
                        .background(palette.accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        achievementIcon(item),
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    modifier = Modifier.widthIn(max = 140.dp),
                    color = palette.content
                )
                Text(
                    text = if (item.isMilestone) "Milestone" else "Unlocked",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = palette.accent
                )
            }

            is LiveActionItem.Credential -> {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveButtonShape)
                        .background(palette.accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = item.domain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    modifier = Modifier.widthIn(max = 140.dp),
                    color = palette.content
                )
                Text(
                    text = if (item.isUpdate) "Updated ✓" else "Saved ✓",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = palette.accent
                )
            }
        }
    }
}

@Composable
private fun SatelliteBubble(item: LiveActionItem, palette: IslandPalette, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clip(ExpressiveButtonShape)
            .clickable(role = Role.Button, onClickLabel = "Show live update", onClick = onClick),
        shape = ExpressiveButtonShape,
        color = palette.surface,
        shadowElevation = 10.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (item) {
                is LiveActionItem.Download -> ProgressBadge(
                    progress = item.progressPercent / 100f,
                    indeterminate = item.isIndeterminate && !item.isPaused,
                    icon = if (item.isPaused) Icons.Rounded.Pause else Icons.Rounded.ArrowDownward,
                    palette = palette,
                    size = 32
                )
                is LiveActionItem.Media -> MediaArt(item, palette, size = 32, corner = 16)
                is LiveActionItem.Task -> ProgressBadge(
                    progress = item.progressPercent / 100f,
                    indeterminate = false,
                    icon = Icons.Rounded.AutoAwesome,
                    palette = palette,
                    size = 32
                )
                is LiveActionItem.FileViewer -> {
                    val icon = when (item.fileType) {
                        "PDF" -> Icons.Rounded.PictureAsPdf
                        "IMAGE" -> Icons.Rounded.Image
                        else -> Icons.Rounded.Description
                    }
                    Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                }
                is LiveActionItem.Achievement -> {
                    Icon(achievementIcon(item), contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                }
                is LiveActionItem.Credential -> {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Badge notifications show the badge's own icon; level-ups and milestones keep the trophy. */
private fun achievementIcon(item: LiveActionItem.Achievement): androidx.compose.ui.graphics.vector.ImageVector = when (item.badgeId) {
    "downloader", "hoarder" -> Icons.Rounded.Download
    "first_steps", "tab_juggler", "tab_titan" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.EmojiEvents
}

/* ───────────────────────────── Expanded card ───────────────────────────── */

@Composable
private fun ExpandedCard(
    item: LiveActionItem,
    palette: IslandPalette,
    listener: LiveActionActionListener,
    wavy: Boolean,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .widthIn(min = 300.dp, max = 360.dp)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (item) {
            is LiveActionItem.Download -> {
                CardHeader(
                    leading = {
                        ProgressBadge(
                            progress = item.progressPercent / 100f,
                            indeterminate = item.isIndeterminate && !item.isPaused && !item.isCompleted,
                            icon = if (item.isCompleted) Icons.Rounded.Check else if (item.isPaused) Icons.Rounded.Pause else Icons.Rounded.Download,
                            palette = palette,
                            size = 44
                        )
                    },
                    title = item.fileName,
                    subtitle = when {
                        item.isCompleted -> "Download Complete"
                        item.isPaused -> "Paused"
                        item.isIndeterminate -> "Downloading…"
                        else -> "${item.speedFormatted} • ${item.progressPercent}%"
                    },
                    palette = palette,
                    onCollapse = onCollapse,
                    onDismiss = { listener.onDismiss(item.id); onCollapse() }
                )

                PetalWavyProgressBar(
                    progress = if (item.isIndeterminate) 0f else item.progressPercent / 100f,
                    waveColor = palette.accent,
                    trackColor = palette.track,
                    isWavy = wavy && !item.isPaused,
                    animated = wavy && !item.isPaused
                )

                val done = Formatter.formatFileSize(context, item.bytesDownloaded)
                val total = if (item.totalBytes > 0) Formatter.formatFileSize(context, item.totalBytes) else "unknown size"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$done of $total", style = MaterialTheme.typography.labelMedium, color = palette.contentMuted)
                    val eta = formatEta(item.etaSeconds)
                    if (!item.isPaused && eta.isNotEmpty()) {
                        Text(eta, style = MaterialTheme.typography.labelMedium, color = palette.accent)
                    }
                }

                if (item.isCompleted) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { listener.onOpenDownloadedFile(item.localUri, item.fileName); onCollapse() },
                            modifier = Modifier.weight(1f),
                            shape = ExpressiveButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open file", fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = { listener.onOpenDownloads(); onCollapse() },
                            modifier = Modifier.weight(1f),
                            shape = ExpressiveButtonShape,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accentSoft, contentColor = palette.content)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Downloads", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { listener.onDownloadTogglePause(item.downloadId) },
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                    ) {
                        Icon(
                            if (item.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.isPaused) "Resume" else "Pause", fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = { listener.onDownloadCancel(item.downloadId); onCollapse() },
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveButtonShape,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accentSoft, contentColor = palette.content)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { listener.onOpenDownloads(); onCollapse() },
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.accent)
                    ) { Text("Open downloads") }
                }
                }
            }

            is LiveActionItem.Media -> {
                CardHeader(
                    leading = { MediaArt(item, palette, size = 48, corner = 14) },
                    title = item.title,
                    subtitle = item.artist,
                    palette = palette,
                    onCollapse = onCollapse
                )

                val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
                PetalWavyProgressBar(
                    progress = progress,
                    waveColor = palette.accent,
                    trackColor = palette.track,
                    isWavy = wavy && item.isPlaying,
                    animated = wavy && item.isPlaying
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { listener.onMediaSkip(-10) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = palette.content)
                    ) { Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds") }

                    // Shape morph: round while playing, squircle while paused.
                    val playCorner by animateDpAsState(
                        targetValue = if (item.isPlaying) 28.dp else 16.dp,
                        label = "playCorner"
                    )
                    FilledIconButton(
                        onClick = { listener.onMediaTogglePlayPause() },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(playCorner),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                    ) {
                        Icon(
                            if (item.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (item.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = { listener.onMediaSkip(10) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = palette.content)
                    ) { Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { listener.onOpenBrowser(); onCollapse() },
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.accent)
                    ) { Text("Open Petal") }
                }
            }

            is LiveActionItem.Task -> {
                CardHeader(
                    leading = {
                        ProgressBadge(
                            progress = item.progressPercent / 100f,
                            indeterminate = false,
                            icon = Icons.Rounded.AutoAwesome,
                            palette = palette,
                            size = 44
                        )
                    },
                    title = item.taskName,
                    subtitle = "${item.progressPercent}%",
                    palette = palette,
                    onCollapse = onCollapse
                )
                PetalWavyProgressBar(
                    progress = item.progressPercent / 100f,
                    waveColor = palette.accent,
                    trackColor = palette.track,
                    isWavy = wavy,
                    animated = wavy
                )
                Text(item.statusText, style = MaterialTheme.typography.bodySmall, color = palette.contentMuted)
            }

            is LiveActionItem.FileViewer -> {
                val icon = when (item.fileType) {
                    "PDF" -> Icons.Rounded.PictureAsPdf
                    "IMAGE" -> Icons.Rounded.Image
                    "CODE", "KT", "JS", "PY", "HTML" -> Icons.Rounded.Code
                    else -> Icons.Rounded.Description
                }
                CardHeader(
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(26.dp))
                        }
                    },
                    title = item.fileName,
                    subtitle = "${item.fileType} • ${item.subtitle}",
                    palette = palette,
                    onCollapse = onCollapse
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { listener.onOpenFileViewer(); onCollapse() },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Return to File")
                    }
                }
            }

            is LiveActionItem.Achievement -> {
                CardHeader(
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(ExpressiveButtonShape)
                                .background(palette.accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(achievementIcon(item), contentDescription = null, tint = palette.accent, modifier = Modifier.size(26.dp))
                        }
                    },
                    title = item.title,
                    subtitle = if (item.isMilestone) "Quest Milestone Reached" else "Achievement Unlocked",
                    palette = palette,
                    onCollapse = onCollapse
                )

                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.contentMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            listener.onOpenProfile()
                            onCollapse()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                    ) {
                        Icon(achievementIcon(item), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when (item.destination) {
                                "badges" -> "View badge"
                                "rewards" -> "View rewards"
                                else -> "View in Profile"
                            }
                        )
                    }
                }
            }

            is LiveActionItem.Credential -> {
                var passwordVisible by remember { mutableStateOf(false) }

                CardHeader(
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(ExpressiveButtonShape)
                                .background(palette.accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp))
                        }
                    },
                    title = if (item.isUpdate) "Password Updated" else "Password Saved",
                    subtitle = "${item.domain} • Keystore AES-256",
                    palette = palette,
                    onCollapse = onCollapse
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.content.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                            Text(
                                text = item.username.ifBlank { "User" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = palette.content
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Key, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (passwordVisible) item.password else "••••••••••••",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.content
                                )
                            }
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = palette.contentMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            listener.onCopyText(item.password, "Password")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.content.copy(alpha = 0.14f), contentColor = palette.content)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = {
                            listener.onOpenPasswords()
                            onCollapse()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent
                        )
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vault", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    palette: IslandPalette,
    onCollapse: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = palette.content
            )
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = palette.contentMuted
            )
        }
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = palette.contentMuted)
            ) { Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp)) }
        }
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = palette.contentMuted)
        ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Collapse", modifier = Modifier.size(20.dp)) }
    }
}

/* ───────────────────────────── Small parts ───────────────────────────── */

/** Icon inside a circular progress ring. The ring eases to each new value instead of jumping. */
@Composable
private fun ProgressBadge(
    progress: Float,
    indeterminate: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: IslandPalette,
    size: Int
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = "badgeProgress"
    )
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        if (indeterminate) {
            CircularProgressIndicator(
                modifier = Modifier.size(size.dp),
                color = palette.accent,
                strokeWidth = 3.dp,
                trackColor = palette.track
            )
        } else {
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.size(size.dp),
                color = palette.accent,
                strokeWidth = 3.dp,
                trackColor = palette.track
            )
        }
        androidx.compose.animation.Crossfade(targetState = icon, animationSpec = tween(220), label = "badgeIcon") { ic ->
            Icon(
                imageVector = ic,
                contentDescription = null,
                modifier = Modifier.size((size * 0.46f).dp),
                tint = palette.content
            )
        }
    }
}

@Composable
private fun MediaArt(item: LiveActionItem.Media, palette: IslandPalette, size: Int, corner: Int) {
    val art = item.albumArt
    val artShape: androidx.compose.ui.graphics.Shape = if (item.isPlaying) {
        ExpressiveButtonShape
    } else {
        RoundedCornerShape(corner.dp)
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(artShape)
            .background(palette.accentSoft),
        contentAlignment = Alignment.Center
    ) {
        if (art != null && !art.isRecycled) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp)
            )
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = palette.accent, modifier = Modifier.size((size * 0.55f).dp))
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val animActive = com.petal.browser.ui.components.rememberAnimationActive()
    val t = rememberInfiniteTransition(label = "dot")
    val dotAlpha by t.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    val alpha = if (animActive) dotAlpha else 0.7f
    Box(Modifier.size(6.dp).clip(ExpressiveButtonShape).background(color.copy(alpha = alpha)))
}

@Composable
private fun Equalizer(isPlaying: Boolean, color: Color) {
    val animActive = com.petal.browser.ui.components.rememberAnimationActive()
    val isLive = isPlaying && animActive
    val t = rememberInfiniteTransition(label = "eq")
    val b1 by t.animateFloat(4f, 14f, infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "b1")
    val b2 by t.animateFloat(12f, 5f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b2")
    val b3 by t.animateFloat(6f, 16f, infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse), label = "b3")
    Row(
        modifier = Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            if (isLive) b1 else 4f,
            if (isLive) b2 else 8f,
            if (isLive) b3 else 5f
        ).forEach { h ->
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

private fun formatEta(seconds: Long): String = when {
    seconds <= 0L -> ""
    seconds < 60L -> "${seconds}s left"
    seconds < 3600L -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
}
