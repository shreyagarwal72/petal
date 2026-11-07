/*
 * PetalLiveActionBus.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Unified State & Event Hub for Petal Live Actions.
 * Drives both:
 * 1. Android 16 Native Live Actions (Rich Ongoing Notifications / Status Bar Chips)
 * 2. Expressive Petal NowBar (In-App and Overlay for non-supported devices)
 */

package com.petal.browser.live

import android.graphics.Bitmap
import com.petal.browser.compose.downloads.DownloadItem
import com.petal.browser.compose.downloads.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        val isIndeterminate: Boolean,
        val isCompleted: Boolean = false,
        val localUri: String = ""
    ) : LiveActionItem("dl_$downloadId")

    data class Media(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val albumArt: Bitmap? = null
    ) : LiveActionItem("media_playback")

    data class FileViewer(
        val fileName: String,
        val fileType: String,
        val subtitle: String,
        val active: Boolean = true
    ) : LiveActionItem("file_viewer")

    data class Task(
        val taskId: String,
        val taskName: String,
        val progressPercent: Int,
        val statusText: String
    ) : LiveActionItem("task_$taskId")

    data class Achievement(
        val achievementId: String,
        val title: String,
        val subtitle: String,
        val badgeId: String? = null,
        val isMilestone: Boolean = false,
        val level: Int = 0,
        /** Where the Open button goes: "badges", "rewards" or "profile". */
        val destination: String? = null
    ) : LiveActionItem("ach_${achievementId}")

    data class Credential(
        val domain: String,
        val username: String,
        val password: String,
        val isUpdate: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : LiveActionItem("pwd_${domain}_$timestamp")
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
    fun onOpenFileViewer() {}
    fun onOpenProfile() {}
    fun onOpenPasswords() {}
    fun onCopyText(text: String, label: String = "Text") {}
    /** Opens a finished download with the system/Petal file opener. */
    fun onOpenDownloadedFile(localUri: String, fileName: String) { onOpenDownloads() }
    fun onDismiss(id: String) {
        PetalLiveActionBus.dismiss(id)
    }
}

object PetalLiveActionBus {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

        if (isFailed) {
            dismiss("dl_${item.id}")
            return
        }

        if (isCompleted) {
            val completedAction = LiveActionItem.Download(
                downloadId = item.id,
                fileName = item.fileName,
                progressPercent = 100,
                speedBytesPerSec = 0L,
                speedFormatted = "Complete",
                bytesDownloaded = if (item.totalSize > 0) item.totalSize else item.bytesDownloaded,
                totalBytes = item.totalSize,
                etaSeconds = 0L,
                isPaused = false,
                isIndeterminate = false,
                isCompleted = true,
                localUri = item.localUri ?: ""
            )
            promoteOrUpdate(completedAction)
            // Auto dismiss after 5 seconds
            scope.launch {
                delay(8_000L)
                dismiss("dl_${item.id}")
            }
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
            isIndeterminate = isIndeterminate,
            isCompleted = false,
            localUri = item.localUri ?: ""
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
    fun updateFileViewer(
        fileName: String,
        fileType: String,
        subtitle: String
    ) {
        val fileAction = LiveActionItem.FileViewer(
            fileName = fileName,
            fileType = fileType,
            subtitle = subtitle,
            active = true
        )
        promoteOrUpdate(fileAction)
    }

    @JvmStatic
    fun dismissFileViewer() {
        dismiss("file_viewer")
    }

    @JvmStatic
    fun postAchievement(
        title: String,
        subtitle: String,
        badgeId: String? = null,
        isMilestone: Boolean = false,
        level: Int = 0,
        destination: String? = null
    ) {
        val uniqueId = "${System.currentTimeMillis()}"
        val achievement = LiveActionItem.Achievement(
            achievementId = uniqueId,
            title = title,
            subtitle = subtitle,
            badgeId = badgeId,
            isMilestone = isMilestone,
            level = level,
            destination = destination
        )
        promoteOrUpdate(achievement)
        // Auto-dismiss transient achievement from Petal NowBar after 8 seconds
        scope.launch {
            delay(8_000L)
            dismiss(achievement.id)
        }
    }

    @JvmStatic
    fun postCredentialSaved(
        domain: String,
        username: String,
        password: String,
        isUpdate: Boolean = false
    ) {
        val cred = LiveActionItem.Credential(
            domain = domain,
            username = username,
            password = password,
            isUpdate = isUpdate
        )
        promoteOrUpdate(cred)
        // Auto-dismiss credential alert from Petal NowBar after 6 seconds
        scope.launch {
            delay(6_000L)
            dismiss(cred.id)
        }
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

    /** Removes unfinished download cards whose id is no longer active (cancelled / removed). */
    @JvmStatic
    fun pruneDownloads(activeIds: Set<Long>) {
        _state.update { cur ->
            fun stale(i: LiveActionItem?) =
                i is LiveActionItem.Download && !i.isCompleted && i.downloadId !in activeIds
            val p = cur.primary.takeUnless { stale(it) }
            val s = cur.secondary.takeUnless { stale(it) }
            if (p === cur.primary && s === cur.secondary) cur
            else LiveActionBusState(primary = p ?: s, secondary = if (p == null) null else s)
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
