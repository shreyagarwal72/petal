package com.petal.browser.island

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.live.LiveEventItem
import com.petal.browser.live.LiveEventKind
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.theme.ExpressiveButtonShape

/** One icon per kind of event. */
internal fun eventIcon(kind: LiveEventKind): ImageVector = when (kind) {
    LiveEventKind.BACKUP_PROGRESS, LiveEventKind.BACKUP_DONE -> Icons.Rounded.Backup
    LiveEventKind.BACKUP_FAILED, LiveEventKind.DOWNLOAD_FAILED, LiveEventKind.EXTENSION_ERROR,
    LiveEventKind.SYNC_FAILED -> Icons.Rounded.Error
    LiveEventKind.ADBLOCK_STATE, LiveEventKind.ADBLOCK_BLOCKED -> Icons.Rounded.Shield
    LiveEventKind.SETTING_CHANGED -> Icons.Rounded.Tune
    LiveEventKind.LIQUID_GLASS_INTRO -> Icons.Rounded.AutoAwesome
    LiveEventKind.TORRENT_PROGRESS, LiveEventKind.SOCIAL_DOWNLOAD -> Icons.Rounded.Download
    LiveEventKind.INCOGNITO_STATE -> Icons.Rounded.VisibilityOff
    LiveEventKind.TAB_RESTORED, LiveEventKind.TAB_LIMIT, LiveEventKind.TAB_RECEIVED -> Icons.Rounded.Tab
    LiveEventKind.SYNC_RUNNING, LiveEventKind.SYNC_DONE -> Icons.Rounded.Sync
    LiveEventKind.EXTENSION_INSTALLED, LiveEventKind.EXTENSION_UPDATED,
    LiveEventKind.EXTENSION_REMOVED -> Icons.Rounded.Extension
    LiveEventKind.UPDATE_AVAILABLE, LiveEventKind.UPDATE_DOWNLOADED,
    LiveEventKind.APK_INSTALL -> Icons.Rounded.SystemUpdate
    LiveEventKind.PERMISSION_IN_USE -> Icons.Rounded.Videocam
    LiveEventKind.PERMISSION_BLOCKED, LiveEventKind.POPUP_BLOCKED -> Icons.Rounded.Block
    LiveEventKind.INSECURE_PAGE, LiveEventKind.CRASH_RECOVERED -> Icons.Rounded.Warning
    LiveEventKind.PROFILE_LOCK -> Icons.Rounded.Lock
    LiveEventKind.NETWORK_LOST -> Icons.Rounded.WifiOff
    LiveEventKind.NETWORK_RESTORED -> Icons.Rounded.Wifi
    LiveEventKind.LOW_STORAGE -> Icons.Rounded.Storage
    LiveEventKind.READER_MODE -> Icons.Rounded.MenuBook
    LiveEventKind.READ_ALOUD -> Icons.Rounded.VolumeUp
    LiveEventKind.TRANSLATED -> Icons.Rounded.Translate
    LiveEventKind.SCREENSHOT_SAVED -> Icons.Rounded.Image
    LiveEventKind.PRINT_SAVED -> Icons.Rounded.Print
    LiveEventKind.QR_RESULT -> Icons.Rounded.QrCodeScanner
    LiveEventKind.CLIPBOARD_COPIED -> Icons.Rounded.ContentCopy
    LiveEventKind.SHARED_TO_PETAL -> Icons.Rounded.Share
    LiveEventKind.DEFAULT_BROWSER -> Icons.Rounded.OpenInNew
    LiveEventKind.INFO -> Icons.Rounded.Info
}

private fun LiveEventItem.hasProgress() = progressPercent in 0..100

/** The round icon badge: a progress ring when the event has progress, a soft tile otherwise. */
@Composable
internal fun EventBadge(item: LiveEventItem, palette: IslandPalette, size: Int) {
    if (item.hasProgress()) {
        ProgressBadge(
            progress = item.progressPercent / 100f,
            indeterminate = false,
            icon = eventIcon(item.kind),
            palette = palette,
            size = size
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(ExpressiveButtonShape)
                .background(palette.accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                eventIcon(item.kind),
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size((size * 0.56f).dp)
            )
        }
    }
}

/** Content of the collapsed pill (goes inside the pill's Row). */
@Composable
internal fun EventPillContent(item: LiveEventItem, palette: IslandPalette) {
    EventBadge(item, palette, size = 32)
    Text(
        text = item.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
        modifier = Modifier.widthIn(max = 150.dp),
        color = palette.content
    )
    if (item.hasProgress()) {
        Text(
            text = "${item.progressPercent}%",
            style = MaterialTheme.typography.labelMedium,
            color = palette.accent
        )
    } else if (item.toggle != null) {
        Text(
            text = if (item.toggle.checked) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = palette.accent
        )
    }
}

/** Content of the secondary bubble. */
@Composable
internal fun EventBubbleContent(item: LiveEventItem, palette: IslandPalette) {
    EventBadge(item, palette, size = 32)
}

/** Content of the expanded card (goes inside the card's Column). */
@Composable
internal fun EventExpandedContent(
    item: LiveEventItem,
    palette: IslandPalette,
    wavy: Boolean,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit
) {
    CardHeader(
        leading = { EventBadge(item, palette, size = 44) },
        title = item.title,
        subtitle = item.subtitle,
        palette = palette,
        onCollapse = onCollapse,
        onDismiss = onDismiss
    )

    if (item.hasProgress()) {
        PetalWavyProgressBar(
            progress = item.progressPercent / 100f,
            waveColor = palette.accent,
            trackColor = palette.track,
            isWavy = wavy,
            animated = wavy
        )
    }

    val toggle = item.toggle
    if (toggle != null) {
        // The switch keeps its own state so it flips instantly, then reports the change.
        var checked by remember(item.id, toggle.checked) { mutableStateOf(toggle.checked) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = toggle.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = palette.content,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    toggle.onChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = palette.accent,
                    checkedThumbColor = palette.onAccent,
                    uncheckedTrackColor = palette.track,
                    uncheckedThumbColor = palette.contentMuted,
                    uncheckedBorderColor = palette.track
                )
            )
        }
    }

    if (item.actions.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.actions.take(2).forEach { action ->
                if (action.primary) {
                    Button(
                        onClick = { action.run(); onCollapse() },
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent
                        )
                    ) { Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) }
                } else {
                    FilledTonalButton(
                        onClick = { action.run(); onCollapse() },
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveButtonShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = palette.accentSoft,
                            contentColor = palette.content
                        )
                    ) { Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
