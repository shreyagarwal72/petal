/*
 * PetalLiveActionBus.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Unified State & Event Hub for Petal Live Actions.
 * Drives both:
 * 1. Android 16 Native Live Actions (Rich Ongoing Notifications / Status Bar Chips)
 * 2. Expressive Custom Cutout Island (In-App and Overlay for non-supported devices)
 */

package com.petal.browser.live

import android.graphics.Bitmap
import com.petal.browser.compose.downloads.DownloadItem
import com.petal.browser.compose.downloads.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

sealed class LiveActionItem(open val id: String) {
    data class Download(
        val downloadId: Long,
        val fileName: String,
        val progressPercent: Int,
        val speedBytesPerSec: Long,
        val speedFormatted: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val etaSeconds: Long,
        val isPaused: Boolean,
        val isIndeterminate: Boolean
    ) : LiveActionItem("dl_$downloadId")

    data class Media(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val albumArt: Bitmap? = null
    ) : LiveActionItem("media_playback")

    data class Task(
        val taskId: String,
        val taskName: String,
        val progressPercent: Int,
        val statusText: String
    ) : LiveActionItem("task_$taskId")
}

data class LiveActionBusState(
    val primary: LiveActionItem? = null,
    val secondary: LiveActionItem? = null
) {
    val hasActiveAction: Boolean
        get() = primary != null || secondary != null
}

interface LiveActionActionListener {
    fun onDownloadTogglePause(downloadId: Long)
    fun onDownloadCancel(downloadId: Long)
    fun onMediaTogglePlayPause()
    fun onMediaSkip(seconds: Int)
    fun onOpenDownloads()
    fun onOpenBrowser()
}

object PetalLiveActionBus {

    private val _state = MutableStateFlow(LiveActionBusState())
    @get:JvmStatic
    val state: StateFlow<LiveActionBusState> = _state.asStateFlow()

    @JvmStatic
    fun getCurrentState(): LiveActionBusState = _state.value

    @Volatile
    private var actionListener: LiveActionActionListener? = null

    @JvmStatic
    fun setActionListener(listener: LiveActionActionListener?) {
        actionListener = listener
    }

    @JvmStatic
    fun getActionListener(): LiveActionActionListener? = actionListener

    @JvmStatic
    fun updateDownload(item: DownloadItem) {
        val isCompleted = item.status == DownloadStatus.STATUS_SUCCESSFUL
        val isFailed = item.status == DownloadStatus.STATUS_FAILED

        if (isCompleted || isFailed) {
            dismiss("dl_${item.id}")
            return
        }

        val progressPercent = ((item.progress ?: 0f) * 100f).toInt().coerceIn(0, 100)
        val speedStr = formatSpeed(item.speedBytesPerSec)
        val isIndeterminate = item.totalSize <= 0L
        val isPaused = item.status == DownloadStatus.STATUS_PAUSED

        val downloadAction = LiveActionItem.Download(
            downloadId = item.id,
            fileName = item.fileName,
            progressPercent = progressPercent,
            speedBytesPerSec = item.speedBytesPerSec,
            speedFormatted = speedStr,
            bytesDownloaded = item.bytesDownloaded,
            totalBytes = item.totalSize,
            etaSeconds = item.etaSeconds,
            isPaused = isPaused,
            isIndeterminate = isIndeterminate
        )

        promoteOrUpdate(downloadAction)
    }

    @JvmStatic
    fun updateMedia(
        title: String?,
        artist: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        albumArt: Bitmap?
    ) {
        val cleanTitle = if (!title.isNullOrBlank()) title else "Web Media"
        val cleanArtist = if (!artist.isNullOrBlank()) artist else "Petal Browser"

        val mediaAction = LiveActionItem.Media(
            title = cleanTitle,
            artist = cleanArtist,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            albumArt = albumArt
        )

        promoteOrUpdate(mediaAction)
    }

    @JvmStatic
    fun dismissMedia() {
        dismiss("media_playback")
    }

    @JvmStatic
    fun dismiss(id: String) {
        _state.update { current ->
            when {
                current.primary?.id == id -> current.copy(primary = current.secondary, secondary = null)
                current.secondary?.id == id -> current.copy(secondary = null)
                else -> current
            }
        }
    }

    @JvmStatic
    fun clearAll() {
        _state.value = LiveActionBusState()
    }

    private fun promoteOrUpdate(item: LiveActionItem) {
        _state.update { current ->
            when {
                current.primary?.id == item.id -> current.copy(primary = item)
                current.secondary?.id == item.id -> current.copy(secondary = item)
                current.primary == null -> current.copy(primary = item)
                // Secondary slot for multi-activity satellite pills
                else -> current.copy(secondary = item)
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mb)
        } else {
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }
}
