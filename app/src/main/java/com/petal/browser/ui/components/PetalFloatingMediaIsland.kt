/*
 * PetalFloatingMediaIsland.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive persistent animated Floating Media Island card for Petal Browser.
 * Docked above the bottom bar when HTML5 media/audio is playing.
 * Only shown on Home surface and Website surfaces (hidden during overlays, settings, dialogs).
 * Seamless handoff to Petal Media Player (MediaPlayerActivity) on click.
 * Expanded controls include:
 *   - Play/Pause toggle
 *   - Interactive Scrubbing Slider
 *   - Mute/Unmute
 *   - 10s backward/forward jumps
 *   - Playback Speed Selector (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
 *   - Picture-in-Picture (PiP) trigger
 *   - Animated Soundwave Equalizer Capsule
 */

package com.petal.browser.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.activity.MediaPlayerActivity
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.media.handoff.MediaHandoff
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
    val isMuted: Boolean = false,
    val sourceUrl: String = "",
    val mimeType: String? = null,
    val playbackSpeed: Float = 1.0f,
    val isVideo: Boolean = true,
    val isSurfaceAllowed: Boolean = true
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
        updateState(
            isPlaying = isPlaying,
            title = title,
            positionMs = positionMs,
            durationMs = durationMs,
            isMuted = isMuted,
            sourceUrl = _mediaState.value.sourceUrl,
            mimeType = _mediaState.value.mimeType
        )
    }

    @JvmStatic
    fun updateState(
        isPlaying: Boolean,
        title: String?,
        positionMs: Long,
        durationMs: Long,
        isMuted: Boolean,
        sourceUrl: String?,
        mimeType: String?
    ) {
        val cleanTitle = if (!title.isNullOrBlank()) title else "Media Playing"
        val resolvedSource = if (!sourceUrl.isNullOrBlank()) sourceUrl else _mediaState.value.sourceUrl
        val isVideo = if (mimeType != null && mimeType.startsWith("audio/")) false else true

        _mediaState.value = _mediaState.value.copy(
            isVisible = true,
            isPlaying = isPlaying,
            title = cleanTitle,
            positionMs = positionMs,
            durationMs = durationMs,
            isMuted = isMuted,
            sourceUrl = resolvedSource,
            mimeType = mimeType ?: _mediaState.value.mimeType,
            isVideo = isVideo
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
    fun updateSpeed(speed: Float) {
        _mediaState.value = _mediaState.value.copy(
            playbackSpeed = speed
        )
    }

    @JvmStatic
    fun updateSourceUrl(url: String?, mimeType: String?) {
        if (url.isNullOrBlank()) return
        val isVideo = if (mimeType != null && mimeType.startsWith("audio/")) false else true
        _mediaState.value = _mediaState.value.copy(
            sourceUrl = url,
            mimeType = mimeType ?: _mediaState.value.mimeType,
            isVideo = isVideo
        )
    }

    @JvmStatic
    fun setSurfaceAllowed(allowed: Boolean) {
        if (_mediaState.value.isSurfaceAllowed != allowed) {
            _mediaState.value = _mediaState.value.copy(isSurfaceAllowed = allowed)
        }
    }

    @JvmStatic
    fun hide() {
        _mediaState.value = _mediaState.value.copy(isVisible = false)
    }

    @JvmStatic
    fun bindMediaIsland(
        composeView: ComposeView,
        activity: ComponentActivity,
        getMediaBridge: () -> PetalMediaBridge?
    ) {
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
                    AppFont.fromName(sp.getString("sp_app_font", AppFont.PETAL.name))
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
                        onSeekTo = { posMs ->
                            val bridge = getMediaBridge() ?: return@PetalFloatingMediaIslandContent
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            bridge.seekMediaTo(posMs)
                        },
                        onSpeedSelect = { speed ->
                            val bridge = getMediaBridge() ?: return@PetalFloatingMediaIslandContent
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            bridge.changeSpeed(speed)
                            updateSpeed(speed)
                        },
                        onTriggerPip = {
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                            PetalMediaBridge.enterPipIfSupported(activity, null)
                        },
                        onOpenInMediaPlayer = {
                            PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.8f)
                            // Pause web playback to prevent audio collision
                            val bridge = getMediaBridge()
                            bridge?.pauseMedia()

                            val rawUrl = state.sourceUrl.ifBlank {
                                if (activity is com.petal.browser.activity.BrowserActivity) {
                                    activity.currentUrl ?: ""
                                } else ""
                            }
                            if (rawUrl.isNotBlank()) {
                                val targetUri = Uri.parse(rawUrl)
                                val intent = Intent(activity, MediaPlayerActivity::class.java).apply {
                                    data = targetUri
                                    if (!state.mimeType.isNullOrBlank()) {
                                        type = state.mimeType
                                    }
                                    putExtra(MediaHandoff.EXTRA_HANDOFF_POSITION_MS, state.positionMs)
                                    putExtra(MediaHandoff.EXTRA_HANDOFF_SPEED, state.playbackSpeed)
                                    putExtra(MediaHandoff.EXTRA_HANDOFF_IS_PAUSED, !state.isPlaying)
                                }
                                activity.startActivity(intent)
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalFloatingMediaIslandContent(
    state: MediaPlaybackState,
    onPlayPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedSelect: (Float) -> Unit,
    onTriggerPip: () -> Unit,
    onOpenInMediaPlayer: () -> Unit,
    onDismiss: () -> Unit
) {
    // Only show if visible AND surface allows (Home or Website surface, no overlay/pip)
    val shouldShow = state.isVisible && state.isSurfaceAllowed

    AnimatedVisibility(
        visible = shouldShow,
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
        var isExpanded by remember { mutableStateOf(false) }
        var showSpeedMenu by remember { mutableStateOf(false) }

        val progress = if (state.durationMs > 0L) {
            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                0.75.dp,
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Main Capsule Row (Always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Leading media icon with active soundwave animation
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { onOpenInMediaPlayer() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isPlaying) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "wave")
                                    val h1: Float by infiniteTransition.animateFloat(
                                        initialValue = 6f, targetValue = 20f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(380, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ), label = "h1"
                                    )
                                    val h2: Float by infiniteTransition.animateFloat(
                                        initialValue = 18f, targetValue = 8f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(520, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ), label = "h2"
                                    )
                                    val h3: Float by infiniteTransition.animateFloat(
                                        initialValue = 10f, targetValue = 22f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(440, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ), label = "h3"
                                    )
                                    Box(modifier = Modifier.width(3.dp).height(h1.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    Box(modifier = Modifier.width(3.dp).height(h2.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    Box(modifier = Modifier.width(3.dp).height(h3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            } else {
                                Icon(
                                    imageVector = if (state.isVideo) Icons.Rounded.Videocam else Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Title & progress time text -> click opens Petal Media Player
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenInMediaPlayer() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.title.ifEmpty { "Playing Media" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Open in Petal Media Player",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (state.durationMs > 0L) {
                            val curSec = (state.positionMs / 1000L).coerceAtLeast(0L)
                            val durSec = (state.durationMs / 1000L).coerceAtLeast(0L)
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
                            contentDescription = stringResource(R.string.ui_skip_back_10s),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Play / Pause pill button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onPlayPauseToggle() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 10s Forward
                    IconButton(onClick = onSkipForward, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = stringResource(R.string.ui_skip_forward_10s),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Expand / Collapse Extra Controls
                    IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Dismiss island
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Expanded Section: Interactive Scrubber Slider + Speed + PiP + Mute
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        // Interactive Slider
                        if (state.durationMs > 0L) {
                            var sliderValue by remember(state.positionMs) {
                                mutableFloatStateOf(progress)
                            }
                            var isDragging by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Slider(
                                    value = if (isDragging) sliderValue else progress,
                                    onValueChange = {
                                        isDragging = true
                                        sliderValue = it
                                    },
                                    onValueChangeFinished = {
                                        isDragging = false
                                        val targetMs = (sliderValue * state.durationMs).toLong()
                                        onSeekTo(targetMs)
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Extended Actions Bar: Speed, PiP, Mute, Open in Player
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed Selector
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showSpeedMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = "Speed",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "${state.playbackSpeed}x",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x",
                                                    fontWeight = if (state.playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (state.playbackSpeed == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                showSpeedMenu = false
                                                onSpeedSelect(speed)
                                            }
                                        )
                                    }
                                }
                            }

                            // PiP Mode Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTriggerPip() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureInPictureAlt,
                                        contentDescription = "PiP",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "PiP",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Mute / Unmute Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onMuteToggle() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                        contentDescription = stringResource(R.string.ui_mute_toggle),
                                        tint = if (state.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (state.isMuted) "Unmute" else "Mute",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = if (state.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Open in Native Petal Player Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenInMediaPlayer() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Open Player",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Player",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Flush progress bar along the bottom edge when collapsed
                if (!isExpanded && state.durationMs > 0L) {
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
