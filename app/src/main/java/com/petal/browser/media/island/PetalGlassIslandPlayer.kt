/*
 * PetalGlassIslandPlayer.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Floating Liquid Glass Island Media Capsule (Dynamic Island style).
 * Shows interactive mini-player with real-time animated audio waveforms,
 * play/pause, media title, and expand controls.
 * Full compliance with Petal Liquid Glass & Material 3 Expressive guidelines.
 */
package com.petal.browser.media.island

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

data class GlassIslandState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isVisible: Boolean = false
)

object GlassIslandController {
    var state by mutableStateOf(GlassIslandState())
        private set

    fun updateMedia(title: String, artist: String, isPlaying: Boolean) {
        state = GlassIslandState(
            isPlaying = isPlaying,
            title = title.ifBlank { "Playing Media" },
            artist = artist,
            isVisible = true
        )
    }

    fun dismiss() {
        state = state.copy(isVisible = false)
    }

    fun togglePlayPause() {
        state = state.copy(isPlaying = !state.isPlaying)
    }
}

@Composable
fun PetalGlassIslandPlayer(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {}
) {
    val state = GlassIslandController.state
    if (!state.isVisible) return

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val shape = RoundedCornerShape(28.dp)
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .liquidGlassChrome(shape = shape, enabled = isGlass)
            .clickable { isExpanded = !isExpanded },
        shape = shape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                    .togetherWith(
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    )
            },
            label = "GlassIslandExpansion"
        ) { expanded ->
            if (!expanded) {
                // Compact pill mode
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WaveformVisualizer(isPlaying = state.isPlaying)

                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )

                    IconButton(
                        onClick = { GlassIslandController.togglePlayPause() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { GlassIslandController.dismiss() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                // Expanded card mode
                Column(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 340.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WaveformVisualizer(isPlaying = state.isPlaying)
                            Text(
                                text = "Petal Media Island",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { GlassIslandController.dismiss() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.artist.isNotBlank()) {
                            Text(
                                text = state.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { GlassIslandController.togglePlayPause() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = List(4) { i ->
        infiniteTransition.animateFloat(
            initialValue = 4f,
            targetValue = if (isPlaying) 18f else 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350 + i * 120, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$i"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(20.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.value.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
