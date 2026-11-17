/*
 * PetalLiveActionBus.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Unified State & Event Hub for Petal Live Actions.
 * Drives both:
 * 1. Android 16 Native Live Actions (Rich Ongoing Notifications / Status Bar Chips)
 * 2. Expressive Petal NowBar (In-App and Overlay for non-supported devices)
 */

package com.petal.browser.live

import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
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
            postEvent(
                LiveEventItem(
                    key = "dl_failed_${item.id}",
                    kind = LiveEventKind.DOWNLOAD_FAILED,
                    title = "Download failed",
                    subtitle = item.fileName,
                    actions = listOf(LiveEventAction("Downloads", true) { actionListener?.onOpenDownloads() })
                )
            )
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
        synchronized(lock) {
            expiryJobs.remove(id)?.cancel()
            pending.removeAll { it.id == id }
            val cur = _state.value
            _state.value = when {
                cur.primary?.id == id -> cur.copy(primary = cur.secondary, secondary = null)
                cur.secondary?.id == id -> cur.copy(secondary = null)
                else -> cur
            }
            refillFromPending()
        }
    }

    /** Removes unfinished download cards whose id is no longer active (cancelled / removed). */
    @JvmStatic
    fun pruneDownloads(activeIds: Set<Long>) {
        synchronized(lock) {
            fun stale(i: LiveActionItem?) =
                i is LiveActionItem.Download && !i.isCompleted && i.downloadId !in activeIds
            pending.removeAll { stale(it) }
            val cur = _state.value
            val p = cur.primary.takeUnless { stale(it) }
            val s = cur.secondary.takeUnless { stale(it) }
            if (p !== cur.primary || s !== cur.secondary) {
                _state.value = LiveActionBusState(primary = p ?: s, secondary = if (p == null) null else s)
            }
            refillFromPending()
        }
    }

    @JvmStatic
    fun clearAll() {
        synchronized(lock) {
            pending.clear()
            expiryJobs.values.forEach { it.cancel() }
            expiryJobs.clear()
            _state.value = LiveActionBusState()
        }
    }

    // ───────────────────────────── Slots, queue, priority ─────────────────────────────

    /** Items waiting for a free slot. Guarded by [lock]. */
    private val pending = ArrayList<LiveActionItem>()
    private val expiryJobs = HashMap<String, Job>()
    private val lastPosted = HashMap<String, Long>()
    private val lock = Any()
    private const val MAX_PENDING = 8
    private const val DUPLICATE_WINDOW_MS = 600L

    @Volatile private var appContext: Context? = null
    @Volatile private var quietUntilMs = 0L

    /**
     * Call once from Application.onCreate(). Lets the bus read the per-category switches.
     * Also starts a short quiet period so startup noise never flashes on the bar.
     */
    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
        suppressFor(STARTUP_QUIET_MS)
    }

    /**
     * Drops low-importance events (settings changes, info) for [ms]. Use while restoring a
     * backup or applying many settings at once.
     */
    @JvmStatic
    fun suppressFor(ms: Long) {
        quietUntilMs = maxOf(quietUntilMs, System.currentTimeMillis() + ms)
    }

    private const val STARTUP_QUIET_MS = 5_000L

    /** True during the startup / restore quiet period. */
    @JvmStatic
    fun isQuiet(): Boolean = System.currentTimeMillis() < quietUntilMs

    /** True when the master switch and this item's category switch are both on. */
    @JvmStatic
    fun isCategoryEnabled(category: LiveEventCategory): Boolean {
        val ctx = appContext ?: return true
        return try {
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            sp.getBoolean(PetalLiveEvents.KEY_MASTER, true) && sp.getBoolean(category.prefKey, true)
        } catch (_: Throwable) {
            true
        }
    }

    private fun promoteOrUpdate(item: LiveActionItem) {
        if (!isCategoryEnabled(liveCategoryOf(item))) return
        synchronized(lock) {
            val cur = _state.value
            when {
                cur.primary?.id == item.id -> _state.value = cur.copy(primary = item)
                cur.secondary?.id == item.id -> _state.value = cur.copy(secondary = item)
                pending.any { it.id == item.id } -> {
                    val i = pending.indexOfFirst { it.id == item.id }
                    pending[i] = item
                }
                cur.primary == null -> _state.value = LiveActionBusState(primary = item)
                cur.secondary == null -> _state.value =
                    // A more important newcomer takes the main pill; the old one becomes the bubble.
                    if (livePriorityOf(item) > livePriorityOf(cur.primary))
                        LiveActionBusState(primary = item, secondary = cur.primary)
                    else cur.copy(secondary = item)
                else -> {
                    val victim = if (livePriorityOf(cur.secondary) <= livePriorityOf(cur.primary)) cur.secondary else cur.primary
                    if (livePriorityOf(item) > livePriorityOf(victim)) {
                        // Bump the least important card back into the queue; it returns when a slot frees up.
                        addPending(victim!!)
                        _state.value =
                            if (victim === cur.secondary) cur.copy(secondary = item)
                            else LiveActionBusState(primary = item, secondary = cur.secondary)
                    } else {
                        addPending(item)
                    }
                }
            }
        }
    }

    private fun addPending(item: LiveActionItem) {
        pending.removeAll { it.id == item.id }
        pending.add(item)
        while (pending.size > MAX_PENDING) {
            // Drop the least important, oldest entry first.
            val drop = pending.withIndex().minWithOrNull(
                compareBy<IndexedValue<LiveActionItem>>({ livePriorityOf(it.value) }, { it.index })
            )?.index ?: 0
            pending.removeAt(drop)
        }
    }

    /** Moves the most important waiting card into any free slot. Call with [lock] held. */
    private fun refillFromPending() {
        while (pending.isNotEmpty()) {
            val cur = _state.value
            if (cur.primary != null && cur.secondary != null) return
            val best = pending.withIndex().maxWithOrNull(
                compareBy<IndexedValue<LiveActionItem>>({ livePriorityOf(it.value) }, { -it.index })
            )?.index ?: return
            val item = pending.removeAt(best)
            _state.value = if (cur.primary == null) LiveActionBusState(primary = item, secondary = cur.secondary)
            else cur.copy(secondary = item)
        }
    }

    // ───────────────────────────── Generic events ─────────────────────────────

    /**
     * Shows (or updates) a generic event card. Repeats of the same [LiveEventItem.key] replace the
     * card and restart its timer. Noisy repeats within 0.6 s are ignored. Low-importance events are
     * dropped during the startup / restore quiet period.
     */
    @JvmStatic
    fun postEvent(item: LiveEventItem) {
        val kind = item.kind
        if (!isCategoryEnabled(kind.category)) return
        val now = System.currentTimeMillis()
        val quiet = now < quietUntilMs
        if (quiet && (kind.priority <= LiveEventPriority.INFO || kind.category == LiveEventCategory.SETTINGS)) return
        synchronized(lock) {
            val last = lastPosted[item.key]
            val same = (_state.value.primary as? LiveEventItem ?: _state.value.secondary as? LiveEventItem)
                ?.takeIf { it.key == item.key } == item
            if (kind.priority < LiveEventPriority.URGENT && last != null && now - last < DUPLICATE_WINDOW_MS && same) return
            lastPosted[item.key] = now
        }
        promoteOrUpdate(item)
        scheduleExpiry(item)
    }

    private fun scheduleExpiry(item: LiveEventItem) {
        val ttl = item.kind.ttlMs
        synchronized(lock) {
            expiryJobs.remove(item.id)?.cancel()
            if (ttl <= 0L) return
            expiryJobs[item.id] = scope.launch {
                delay(ttl)
                dismiss(item.id)
            }
        }
    }

    // Convenience posters. Each one is a single line at the call site.

    @JvmStatic
    fun postBackupProgress(title: String, subtitle: String, percent: Int) = postEvent(
        LiveEventItem("backup", LiveEventKind.BACKUP_PROGRESS, title, subtitle, percent.coerceIn(-1, 100))
    )

    @JvmStatic
    @JvmOverloads
    fun postBackupResult(
        success: Boolean,
        message: String,
        openAction: LiveEventAction? = null,
        title: String? = null
    ) = postEvent(
        LiveEventItem(
            key = "backup",
            kind = if (success) LiveEventKind.BACKUP_DONE else LiveEventKind.BACKUP_FAILED,
            title = title ?: if (success) "Backup complete" else "Backup failed",
            subtitle = message,
            actions = listOfNotNull(openAction)
        )
    )

    @JvmStatic
    fun postAdBlockState(
        enabled: Boolean,
        blockedOnPage: Int,
        domain: String?,
        onToggle: (Boolean) -> Unit,
        openSettings: (() -> Unit)? = null
    ) = postEvent(
        LiveEventItem(
            key = "adblock",
            kind = LiveEventKind.ADBLOCK_STATE,
            title = if (enabled) "Ad blocker on" else "Ad blocker off",
            subtitle = when {
                !enabled -> "Ads and trackers are allowed"
                blockedOnPage > 0 && !domain.isNullOrBlank() -> "$blockedOnPage blocked on $domain"
                else -> "Blocking ads and trackers"
            },
            toggle = LiveEventToggle("Block ads & trackers", enabled, onToggle),
            actions = if (openSettings != null) listOf(LiveEventAction("Ad blocker settings", true, openSettings)) else emptyList()
        )
    )

    @JvmStatic
    fun postSettingChanged(key: String, label: String, oldValue: String, newValue: String, undo: (() -> Unit)?) = postEvent(
        LiveEventItem(
            key = "setting_$key",
            kind = LiveEventKind.SETTING_CHANGED,
            title = label,
            subtitle = "$oldValue  →  $newValue",
            actions = if (undo != null) listOf(LiveEventAction("Undo", true, undo)) else emptyList()
        )
    )

    @JvmStatic
    fun postLiquidGlassIntro(openAppearance: (() -> Unit)?) = postEvent(
        LiveEventItem(
            key = "liquid_glass_intro",
            kind = LiveEventKind.LIQUID_GLASS_INTRO,
            title = "Liquid Glass is on",
            subtitle = "Bars and sheets now refract what's behind them",
            actions = if (openAppearance != null) listOf(LiveEventAction("Appearance settings", true, openAppearance)) else emptyList()
        )
    )

    /** Simple one-line event: icon from [kind], [title], optional [subtitle] and one optional button. */
    @JvmStatic
    @JvmOverloads
    fun postSimple(
        kind: LiveEventKind,
        key: String,
        title: String,
        subtitle: String = "",
        progressPercent: Int = -1,
        action: LiveEventAction? = null
    ) = postEvent(
        LiveEventItem(key, kind, title, subtitle, progressPercent, listOfNotNull(action))
    )

    /** Removes a generic event card by key. */
    @JvmStatic
    fun dismissEvent(key: String) = dismiss("ev_$key")

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
