/*
 * PetalFloatingMediaIsland.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive persistent animated Floating Media Island card for Petal Browser.
 * Docked near the bottom of the viewport when HTML5 media/audio is playing.
 * Provides play/pause toggle, mute/unmute, 10s backward/forward jumps, and scrubbing progress.
 */

package com.petal.browser.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaPlaybackState(
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isMuted: Boolean = false
)

object PetalFloatingMediaBridge {
    private val _mediaState = MutableStateFlow(MediaPlaybackState())
    val mediaState = _mediaState.asStateFlow()

    @JvmStatic
    fun updateState(
        isPlaying: Boolean,
        title: String?,
        positionMs: Long,
        durationMs: Long,
        isMuted: Boolean
    ) {
        val cleanTitle = if (!title.isNullOrBlank()) title else "Media Playing"
        _mediaState.value = MediaPlaybackState(
            isVisible = true,
            isPlaying = isPlaying,
            title = cleanTitle,
            positionMs = positionMs,
            durationMs = durationMs,
            isMuted = isMuted
        )
    }

    @JvmStatic
    fun setPlaying(isPlaying: Boolean) {
        _mediaState.value = _mediaState.value.copy(
            isPlaying = isPlaying,
            isVisible = if (!isPlaying && _mediaState.value.title.isEmpty()) false else _mediaState.value.isVisible
        )
    }

    @JvmStatic
    fun updateProgress(positionMs: Long, durationMs: Long) {
        _mediaState.value = _mediaState.value.copy(
            positionMs = positionMs,
            durationMs = durationMs
        )
    }

    @JvmStatic
    fun hide() {
        _mediaState.value = _mediaState.value.copy(isVisible = false)
    }

    @JvmStatic
    fun bindMediaIsland(composeView: ComposeView, activity: ComponentActivity, getMediaBridge: () -> PetalMediaBridge?) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                val darkTheme = remember { sp.getString("theme", "0") != "1" }
                val dynamicColor = remember { sp.getBoolean("sp_dynamic_color", true) }
                val isAmoled = remember { sp.getBoolean("amoled_theme", false) }
                val isExpressive = remember { sp.getBoolean("sp_m3_expressive_colors", true) }
                val appFont = remember {
                    try {
                        AppFont.valueOf(sp.getString("sp_app_font", AppFont.GOOGLE_SANS_FLEX.name)!!)
                    } catch (_: Exception) {
                        AppFont.GOOGLE_SANS_FLEX
                    }
                }
                val colorStyle = remember {
                    try {
                        ColorStyle.valueOf(sp.getString("sp_color_style", ColorStyle.TONAL_SPOT.name)!!)
                    } catch (_: Exception) {
                        ColorStyle.TONAL_SPOT
                    }
                }

                PetalExpressiveTheme(
                    darkTheme = darkTheme,
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    expressiveColors = isExpressive,
                    appFont = appFont,
                    colorStyle = colorStyle
                ) {
                    val state by mediaState.collectAsState()
                    PetalFloatingMediaIslandContent(
                        state = state,
                        onPlayPauseToggle = {
                            val bridge = getMediaBridge() ?: return@PetalFloatingMediaIslandContent
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                            if (state.isPlaying) bridge.pauseMedia() else bridge.playMedia()
                        },
                        onMuteToggle = {
                            val bridge = getMediaBridge() ?: return@PetalFloatingMediaIslandContent
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                            bridge.toggleMute()
                        },
                        onSkipBackward = {
                            val bridge = getMediaBridge() ?: return@PetalFloatingMediaIslandContent
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            bridge.skip(-10)
                        },
                        onSkipForward = {
                            val bridge = getMediaBridge() ?: return@PetalFloatingMediaIslandContent
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            bridge.skip(10)
                        },
                        onDismiss = {
                            hide()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PetalFloatingMediaIslandContent(
    state: MediaPlaybackState,
    onPlayPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut()
    ) {
        val progress = if (state.durationMs > 0L) {
            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Leading media icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Title & progress time text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title.ifEmpty { "Background Playback" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.durationMs > 0L) {
                            val curSec = (state.positionMs / 1000L)
                            val durSec = (state.durationMs / 1000L)
                            val timeStr = String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, durSec / 60, durSec % 60)
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 10s Rewind
                    IconButton(onClick = onSkipBackward, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = "Skip back 10s",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play / Pause main button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onPlayPauseToggle() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 10s Forward
                    IconButton(onClick = onSkipForward, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "Skip forward 10s",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Mute / Unmute
                    IconButton(onClick = onMuteToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (state.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = "Mute toggle",
                            tint = if (state.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Dismiss island
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Flush progress bar along the bottom edge
                if (state.durationMs > 0L) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
