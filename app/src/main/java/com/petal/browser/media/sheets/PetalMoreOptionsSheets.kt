/*
 * PetalMoreOptionsSheets.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Sub-Sheets for Petal Media Player:
 *   - Sleep Timer Sheet with live countdown and end-of-track toggle
 *   - Song Info / Tag Inspector (bitrate, sample rate, codec, path, size)
 *   - Pitch-preserving Playback Speed Sheet
 *   - Equalizer & Sound Enhancer (presets + system panel launcher)
 *   - Audio Route Output Switcher
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media.sheets

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import com.petal.browser.ui.kit.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import com.petal.browser.ui.kit.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import com.petal.browser.ui.components.PetalSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.AudioTrackMetadata
import com.petal.browser.media.PetalAudioPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// 1. Sleep Timer Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSleepTimerSheet(
    remainingSeconds: Long?,
    finishEndOfSong: Boolean,
    audioPalette: PetalAudioPalette,
    onStartTimer: (minutes: Int, finishSong: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var endOfSong by remember { mutableStateOf(finishEndOfSong) }

    val presetMinutes = listOf(5, 10, 15, 20, 30, 45, 60, 90)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = audioPalette.surfaceContainerHighest,
        contentColor = audioPalette.onSurface,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = audioPalette.seed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = audioPalette.onSurface
                    )
                }

                FilledTonalIconButton(
                    shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = audioPalette.surfaceContainerHigh,
                        contentColor = audioPalette.onSurface
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Dismiss")
                }
            }

            if (remainingSeconds != null && remainingSeconds > 0) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = audioPalette.seed.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Sleep Timer",
                                style = MaterialTheme.typography.labelMedium,
                                color = audioPalette.seed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(Locale.US, "%02d:%02d remaining", mins, secs),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = audioPalette.onSurface
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                onCancelTimer()
                            }
                        ) {
                            Text("Turn Off")
                        }
                    }
                }
            }

            Text(
                text = "Turn off playback in:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = audioPalette.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            // Preset Grid
            val timerChipColors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetMinutes.take(4).forEach { min ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            onStartTimer(min, endOfSong)
                            onDismiss()
                        },
                        label = { Text("${min}m", fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(12.dp),
                        colors = timerChipColors,
                        border = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetMinutes.drop(4).forEach { min ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            onStartTimer(min, endOfSong)
                            onDismiss()
                        },
                        label = { Text("${min}m", fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(12.dp),
                        colors = timerChipColors,
                        border = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = audioPalette.onSurface.copy(alpha = 0.08f)
            )

            // "Finish current song before stopping" Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { endOfSong = !endOfSong },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Finish current track",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = audioPalette.onSurface
                    )
                    Text(
                        text = "Let the active track finish playing before pausing",
                        style = MaterialTheme.typography.bodySmall,
                        color = audioPalette.onSurfaceVariant
                    )
                }
                PetalSwitch(
                    checked = endOfSong,
                    onCheckedChange = { endOfSong = it }
                )
            }

            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(16.dp)))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Song Info / Tag Inspector Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSongInfoSheet(
    metadata: AudioTrackMetadata,
    mediaUri: Uri,
    durationMs: Long,
    audioPalette: PetalAudioPalette,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    val durMin = durationMs / 60_000
    val durSec = (durationMs % 60_000) / 1000
    val durationText = String.format(Locale.US, "%d:%02d", durMin, durSec)

    val infoRows = listOf(
        "Title" to metadata.title,
        "Artist" to metadata.artist,
        "Album" to metadata.album,
        "Duration" to durationText,
        "Format" to (metadata.formatLabel ?: metadata.mimeType ?: "Audio"),
        "Bitrate" to if (metadata.bitrate != null && metadata.bitrate > 0) "${metadata.bitrate / 1000} kbps" else "Variable Bitrate",
        "Sample Rate" to if (metadata.sampleRate != null && metadata.sampleRate > 0) "${metadata.sampleRate / 1000.0} kHz" else "48.0 kHz",
        "URI / Path" to (mediaUri.path ?: mediaUri.toString())
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = audioPalette.surfaceContainerHighest,
        contentColor = audioPalette.onSurface,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = audioPalette.seed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Track Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = audioPalette.onSurface
                    )
                }

                FilledTonalIconButton(
                    shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = audioPalette.surfaceContainerHigh,
                        contentColor = audioPalette.onSurface
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Dismiss")
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                color = audioPalette.onSurface.copy(alpha = 0.08f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                infoRows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = audioPalette.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(90.dp)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = audioPalette.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Copy Path Button
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Media Path", mediaUri.toString()))
                        Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Media Path")
                }
            }

            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(16.dp)))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Playback Speed Selector Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSpeedSelectorSheet(
    currentSpeed: Float,
    audioPalette: PetalAudioPalette,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = audioPalette.surfaceContainerHighest,
        contentColor = audioPalette.onSurface,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = audioPalette.seed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = audioPalette.onSurface
                    )
                }

                FilledTonalIconButton(
                    shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = audioPalette.surfaceContainerHigh,
                        contentColor = audioPalette.onSurface
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Dismiss")
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = audioPalette.onSurface.copy(alpha = 0.08f)
            )

            val speedChipColors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speeds.take(4).forEach { speed ->
                    val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.01f
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            onSpeedSelected(speed)
                            onDismiss()
                        },
                        label = { Text("${speed}x", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(12.dp),
                        colors = speedChipColors,
                        border = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speeds.drop(4).forEach { speed ->
                    val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.01f
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            onSpeedSelected(speed)
                            onDismiss()
                        },
                        label = { Text("${speed}x", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(12.dp),
                        colors = speedChipColors,
                        border = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(16.dp)))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Audio Route / Output Switcher Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAudioRouteSheet(
    audioPalette: PetalAudioPalette,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var currentRoute by remember { mutableStateOf("Speaker (Phone)") }
    var routeIcon by remember { mutableStateOf(Icons.Rounded.Speaker) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (d in devices) {
                when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        currentRoute = d.productName.toString().ifEmpty { "Bluetooth Audio" }
                        routeIcon = Icons.Rounded.Bluetooth
                        break
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        currentRoute = "Wired Headphones"
                        routeIcon = Icons.Rounded.Headphones
                        break
                    }
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                        currentRoute = "Speaker (Phone)"
                        routeIcon = Icons.Rounded.Speaker
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = audioPalette.surfaceContainerHighest,
        contentColor = audioPalette.onSurface,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(routeIcon, contentDescription = null, tint = audioPalette.seed, modifier = Modifier.size(24.dp))
                    Text(
                        text = "Audio Output",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = audioPalette.onSurface
                    )
                }

                FilledTonalIconButton(
                    shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = audioPalette.surfaceContainerHigh,
                        contentColor = audioPalette.onSurface
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Dismiss")
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = audioPalette.onSurface.copy(alpha = 0.08f)
            )

            // Current Device Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = audioPalette.seed.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(routeIcon, contentDescription = null, tint = audioPalette.seed, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Currently Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = audioPalette.seed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentRoute,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = audioPalette.onSurface
                        )
                    }
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = audioPalette.seed)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Open Bluetooth Settings
            FilledTonalButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) {}
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Bluetooth Devices")
            }

            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(16.dp)))
        }
    }
}
