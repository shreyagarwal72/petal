package com.petal.browser.now

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalAdBlockMode
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.LiveActionItem
import com.petal.browser.live.LiveEventCategory
import com.petal.browser.live.LiveEventItem
import com.petal.browser.live.LiveEventKind
import com.petal.browser.live.LiveEventPriority
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.live.liveCategoryOf
import com.petal.browser.live.livePriorityOf
import java.util.Locale
import kotlin.math.roundToInt

@Immutable
internal data class PetalNowPageContext(
    val activeUrl: String,
    val isHome: Boolean,
    val globalAdBlockEnabled: Boolean,
    val adBlockRevision: Long,
) {
    val host: String? = if (isHome || activeUrl.isBlank() || activeUrl.startsWith("about:")) {
        null
    } else {
        try {
            Uri.parse(activeUrl).host?.lowercase(Locale.US)?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}

internal object PetalNowBusMapper {

    fun map(
        context: Context,
        busState: LiveActionBusState,
        prefs: PetalNowPrefs,
        pageContext: PetalNowPageContext,
        onToggleAdBlock: (Boolean) -> Unit,
        onToggleDomainWhitelist: (String, Boolean) -> Unit,
        onOpenAdBlockSettings: () -> Unit,
    ): PetalNowSlots {
        if (prefs.mode == PetalNowMode.OFF || !prefs.eventsMaster) {
            return PetalNowSlots()
        }

        val suppressActiveDownloads = LiveUpdateNotificationManager.shouldPromote(context)

        val candidates = ArrayList<PetalNowEntry>(2)
        busState.primary?.let { item ->
            mapBusItem(
                context = context,
                item = item,
                prefs = prefs,
                pageContext = pageContext,
                suppressActiveDownloads = suppressActiveDownloads,
                onToggleAdBlock = onToggleAdBlock,
                onToggleDomainWhitelist = onToggleDomainWhitelist,
                onOpenAdBlockSettings = onOpenAdBlockSettings,
            )?.let { candidates.add(it) }
        }
        busState.secondary?.let { item ->
            mapBusItem(
                context = context,
                item = item,
                prefs = prefs,
                pageContext = pageContext,
                suppressActiveDownloads = suppressActiveDownloads,
                onToggleAdBlock = onToggleAdBlock,
                onToggleDomainWhitelist = onToggleDomainWhitelist,
                onOpenAdBlockSettings = onOpenAdBlockSettings,
            )?.let { candidates.add(it) }
        }

        if (candidates.isEmpty() && prefs.mode == PetalNowMode.ALWAYS) {
            val resting = buildRestingEntry(
                context = context,
                prefs = prefs,
                pageContext = pageContext,
                onToggleAdBlock = onToggleAdBlock,
                onToggleDomainWhitelist = onToggleDomainWhitelist,
                onOpenAdBlockSettings = onOpenAdBlockSettings,
            )
            if (resting != null) return PetalNowSlots(primary = resting, satellite = null)
        }

        return PetalNowSlots(
            primary = candidates.getOrNull(0),
            satellite = candidates.getOrNull(1),
        )
    }

    private fun mapBusItem(
        context: Context,
        item: LiveActionItem,
        prefs: PetalNowPrefs,
        pageContext: PetalNowPageContext,
        suppressActiveDownloads: Boolean,
        onToggleAdBlock: (Boolean) -> Unit,
        onToggleDomainWhitelist: (String, Boolean) -> Unit,
        onOpenAdBlockSettings: () -> Unit,
    ): PetalNowEntry? {
        val category = liveCategoryOf(item)
        if (!prefs.isCategoryOn(category)) return null

        return when (item) {
            is LiveActionItem.Download -> {
                // Keep Android 16 promoted status-bar chip as the single live indicator while active.
                if (suppressActiveDownloads && !item.isCompleted) return null
                mapDownload(item, prefs)
            }
            is LiveActionItem.Media -> mapMedia(item, prefs)
            is LiveActionItem.FileViewer -> mapFileViewer(item)
            is LiveActionItem.Task -> mapTask(item, prefs)
            is LiveActionItem.Achievement -> mapAchievement(item)
            is LiveActionItem.Credential -> mapCredential(item)
            is LiveEventItem -> {
                if (item.kind == LiveEventKind.ADBLOCK_STATE || item.kind == LiveEventKind.ADBLOCK_BLOCKED) {
                    buildAdBlockHeroEntry(
                        context = context,
                        pageContext = pageContext,
                        onToggleAdBlock = onToggleAdBlock,
                        onToggleDomainWhitelist = onToggleDomainWhitelist,
                        onOpenAdBlockSettings = onOpenAdBlockSettings,
                        rawBusId = item.id,
                        priority = item.kind.priority,
                        isHeroResting = false,
                    )
                } else {
                    mapGenericEvent(item, prefs)
                }
            }
        }
    }

    private fun mapDownload(item: LiveActionItem.Download, prefs: PetalNowPrefs): PetalNowEntry {
        val listener = PetalLiveActionBus.getActionListener()
        val actions = if (item.isCompleted) {
            listOf(
                PetalNowAction("dl_open_${item.downloadId}", "Open file", primary = true, icon = Icons.Rounded.OpenInBrowser) {
                    listener?.onOpenDownloadedFile(item.localUri, item.fileName) ?: listener?.onOpenDownloads()
                    PetalLiveActionBus.dismiss(item.id)
                },
                PetalNowAction("dl_all_${item.downloadId}", "Downloads", primary = false) {
                    listener?.onOpenDownloads()
                },
            )
        } else {
            listOf(
                PetalNowAction(
                    id = "dl_toggle_${item.downloadId}",
                    label = if (item.isPaused) "Resume" else "Pause",
                    primary = true,
                    icon = if (item.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                ) {
                    listener?.onDownloadTogglePause(item.downloadId)
                },
                PetalNowAction("dl_cancel_${item.downloadId}", "Cancel", primary = false, icon = Icons.Rounded.Close) {
                    listener?.onDownloadCancel(item.downloadId)
                },
                PetalNowAction("dl_list_${item.downloadId}", "All", primary = false) {
                    listener?.onOpenDownloads()
                },
            )
        }

        val compact = when {
            item.isCompleted -> "Downloaded"
            item.isPaused -> "Paused · ${item.progressPercent}%"
            item.isIndeterminate -> "Downloading…"
            else -> "${item.progressPercent}% · ${item.speedFormatted}"
        }

        val subtitle = when {
            item.isCompleted -> "Download complete — ready to open"
            item.isPaused -> "${formatBytes(item.bytesDownloaded)} paused"
            item.totalBytes > 0L ->
                "${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)} · ${item.speedFormatted}" +
                    if (item.etaSeconds > 0L) " · ${formatEta(item.etaSeconds)} left" else ""
            else -> "${formatBytes(item.bytesDownloaded)} · ${item.speedFormatted}"
        }

        val stats = buildList {
            if (!item.isCompleted && item.speedFormatted.isNotBlank() && !item.isPaused) {
                add(PetalNowStatChip("Speed", item.speedFormatted))
            }
            if (item.totalBytes > 0L) {
                add(PetalNowStatChip("Size", formatBytes(item.totalBytes)))
            }
            if (!item.isCompleted && item.etaSeconds > 0L) {
                add(PetalNowStatChip("ETA", formatEta(item.etaSeconds)))
            }
        }

        return PetalNowEntry(
            slotKey = item.id,
            familyKey = item.id,
            kind = PetalNowCardKind.DOWNLOAD,
            category = LiveEventCategory.DOWNLOADS,
            categoryTag = if (item.isCompleted) "Download ready" else "Downloading",
            priority = livePriorityOf(item),
            title = item.fileName.ifBlank { "Download" },
            compactText = compact,
            collapsedBadgeText = if (item.isCompleted) null else if (item.isIndeterminate) "…" else "${item.progressPercent}%",
            subtitle = subtitle,
            icon = if (item.isCompleted) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
            badgeShape = PetalNowBadgeShapeKind.BUN,
            accentRole = if (item.isCompleted) PetalNowAccentRole.SUCCESS else PetalNowAccentRole.SECONDARY,
            progress = if (item.isCompleted) null else PetalNowProgress(
                fraction = if (item.isIndeterminate) null else (item.progressPercent / 100f).coerceIn(0f, 1f),
                percent = item.progressPercent,
                isWavy = prefs.wavyProgress,
                isPaused = item.isPaused,
                isIndeterminate = item.isIndeterminate,
                trailingLabel = if (item.isIndeterminate) null else "${item.progressPercent}%",
            ),
            stats = stats,
            actions = actions,
            canSwipeDismiss = item.isCompleted,
            rawBusId = item.id,
            defaultState = PetalNowDisplayState.COMPACT,
        )
    }

    private fun mapMedia(item: LiveActionItem.Media, prefs: PetalNowPrefs): PetalNowEntry {
        val listener = PetalLiveActionBus.getActionListener()
        val frac = if (item.durationMs > 0L) (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f) else null
        val pct = frac?.let { (it * 100f).roundToInt().coerceIn(0, 100) } ?: -1

        val actions = listOf(
            PetalNowAction("media_rew", "-10s", primary = false) { listener?.onMediaSkip(-10) },
            PetalNowAction(
                id = "media_play",
                label = if (item.isPlaying) "Pause" else "Play",
                primary = true,
                icon = if (item.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            ) {
                listener?.onMediaTogglePlayPause()
            },
            PetalNowAction("media_fwd", "+10s", primary = false) { listener?.onMediaSkip(10) },
        )

        val timeText = if (item.durationMs > 0L) {
            "${formatDurationMs(item.positionMs)} / ${formatDurationMs(item.durationMs)}"
        } else if (item.positionMs > 0L) {
            formatDurationMs(item.positionMs)
        } else {
            item.artist
        }

        return PetalNowEntry(
            slotKey = item.id,
            familyKey = "media",
            kind = PetalNowCardKind.MEDIA,
            category = LiveEventCategory.MEDIA,
            categoryTag = if (item.isPlaying) "Now playing" else "Media paused",
            priority = livePriorityOf(item),
            title = item.title,
            compactText = item.title,
            subtitle = if (item.artist.isNotBlank() && timeText != item.artist) "${item.artist} · $timeText" else timeText,
            icon = Icons.Rounded.MusicNote,
            badgeShape = PetalNowBadgeShapeKind.FLOWER,
            accentRole = PetalNowAccentRole.PRIMARY,
            albumArt = item.albumArt,
            isAudioPlaying = item.isPlaying,
            progress = if (frac != null) {
                PetalNowProgress(
                    fraction = frac,
                    percent = pct,
                    isWavy = prefs.wavyProgress && item.isPlaying,
                    isPaused = !item.isPlaying,
                    trailingLabel = formatDurationMs(item.durationMs),
                )
            } else null,
            actions = actions,
            canSwipeDismiss = true,
            rawBusId = item.id,
            defaultState = PetalNowDisplayState.COMPACT,
        )
    }

    private fun mapFileViewer(item: LiveActionItem.FileViewer): PetalNowEntry {
        val listener = PetalLiveActionBus.getActionListener()
        return PetalNowEntry(
            slotKey = item.id,
            familyKey = "file_viewer",
            kind = PetalNowCardKind.FILE_VIEWER,
            category = LiveEventCategory.TOOLS,
            categoryTag = item.fileType.uppercase(Locale.US).ifBlank { "File" },
            priority = livePriorityOf(item),
            title = item.fileName,
            compactText = "${item.fileType}: ${item.fileName}",
            subtitle = item.subtitle,
            icon = Icons.Rounded.Description,
            badgeShape = PetalNowBadgeShapeKind.PUFFY,
            accentRole = PetalNowAccentRole.SECONDARY,
            actions = listOf(
                PetalNowAction("fv_open", "Return to file", primary = true) { listener?.onOpenFileViewer() }
            ),
            canSwipeDismiss = true,
            rawBusId = item.id,
        )
    }

    private fun mapTask(item: LiveActionItem.Task, prefs: PetalNowPrefs): PetalNowEntry {
        val pct = item.progressPercent.coerceIn(-1, 100)
        return PetalNowEntry(
            slotKey = item.id,
            familyKey = item.id,
            kind = PetalNowCardKind.GENERIC,
            category = LiveEventCategory.TOOLS,
            categoryTag = "Task running",
            priority = livePriorityOf(item),
            title = item.taskName,
            compactText = if (pct >= 0) "$pct% · ${item.taskName}" else item.taskName,
            collapsedBadgeText = if (pct >= 0) "$pct%" else null,
            subtitle = item.statusText,
            icon = Icons.Rounded.Tune,
            badgeShape = PetalNowBadgeShapeKind.SUNNY,
            accentRole = PetalNowAccentRole.PRIMARY,
            progress = PetalNowProgress(
                fraction = if (pct >= 0) pct / 100f else null,
                percent = pct,
                isWavy = prefs.wavyProgress,
                isIndeterminate = pct < 0,
            ),
            canSwipeDismiss = true,
            rawBusId = item.id,
        )
    }

    private fun mapAchievement(item: LiveActionItem.Achievement): PetalNowEntry {
        val listener = PetalLiveActionBus.getActionListener()
        return PetalNowEntry(
            slotKey = item.id,
            familyKey = "achievement",
            kind = PetalNowCardKind.ACHIEVEMENT,
            category = LiveEventCategory.MILESTONES,
            categoryTag = if (item.level > 0) "Level ${item.level}" else "Milestone",
            priority = livePriorityOf(item),
            title = item.title,
            compactText = item.title,
            subtitle = item.subtitle,
            icon = Icons.Rounded.EmojiEvents,
            badgeShape = PetalNowBadgeShapeKind.SUNNY,
            accentRole = PetalNowAccentRole.TERTIARY,
            actions = listOf(
                PetalNowAction("ach_profile", "View rewards", primary = true) {
                    listener?.onOpenProfile()
                    PetalLiveActionBus.dismiss(item.id)
                }
            ),
            canSwipeDismiss = true,
            rawBusId = item.id,
        )
    }

    private fun mapCredential(item: LiveActionItem.Credential): PetalNowEntry {
        val listener = PetalLiveActionBus.getActionListener()
        val actionVerb = if (item.isUpdate) "Password updated" else "Password saved"
        return PetalNowEntry(
            slotKey = item.id,
            familyKey = "credential",
            kind = PetalNowCardKind.CREDENTIAL,
            category = LiveEventCategory.SECURITY,
            categoryTag = "Vault",
            priority = livePriorityOf(item),
            title = actionVerb,
            compactText = "$actionVerb · ${item.domain}",
            subtitle = buildString {
                append(item.domain)
                if (item.username.isNotBlank()) append(" (${item.username})")
            },
            icon = Icons.Rounded.Key,
            badgeShape = PetalNowBadgeShapeKind.GEM,
            accentRole = PetalNowAccentRole.TERTIARY,
            actions = listOf(
                PetalNowAction("cred_vault", "Manage passwords", primary = true) {
                    listener?.onOpenPasswords()
                    PetalLiveActionBus.dismiss(item.id)
                }
            ),
            canSwipeDismiss = true,
            rawBusId = item.id,
        )
    }

    private fun mapGenericEvent(item: LiveEventItem, prefs: PetalNowPrefs): PetalNowEntry {
        val kind = item.kind
        val pct = item.progressPercent.coerceIn(-1, 100)
        val hasProgress = pct >= 0 || kind in setOf(
            LiveEventKind.BACKUP_PROGRESS,
            LiveEventKind.SYNC_RUNNING,
            LiveEventKind.TORRENT_PROGRESS,
            LiveEventKind.SOCIAL_DOWNLOAD,
            LiveEventKind.READ_ALOUD,
        )

        val cardKind = when (kind.category) {
            LiveEventCategory.BACKUP -> PetalNowCardKind.BACKUP
            LiveEventCategory.SYNC -> PetalNowCardKind.SYNC
            LiveEventCategory.EXTENSIONS -> PetalNowCardKind.EXTENSION
            LiveEventCategory.UPDATES -> PetalNowCardKind.UPDATE
            LiveEventCategory.SECURITY -> PetalNowCardKind.PERMISSION
            LiveEventCategory.TABS -> PetalNowCardKind.TAB_STATE
            LiveEventCategory.SYSTEM -> PetalNowCardKind.NETWORK
            LiveEventCategory.DOWNLOADS -> PetalNowCardKind.DOWNLOAD
            LiveEventCategory.ADBLOCK -> PetalNowCardKind.ADBLOCK
            else -> PetalNowCardKind.TOOL
        }

        val icon = when (kind) {
            LiveEventKind.BACKUP_PROGRESS -> Icons.Rounded.Backup
            LiveEventKind.BACKUP_DONE -> Icons.Rounded.CloudDone
            LiveEventKind.BACKUP_FAILED -> Icons.Rounded.ErrorOutline
            LiveEventKind.ADBLOCK_STATE, LiveEventKind.ADBLOCK_BLOCKED -> Icons.Rounded.Shield
            LiveEventKind.SETTING_CHANGED -> Icons.Rounded.Tune
            LiveEventKind.LIQUID_GLASS_INTRO -> Icons.Rounded.AutoAwesome
            LiveEventKind.DOWNLOAD_FAILED -> Icons.Rounded.ErrorOutline
            LiveEventKind.TORRENT_PROGRESS, LiveEventKind.SOCIAL_DOWNLOAD -> Icons.Rounded.Download
            LiveEventKind.INCOGNITO_STATE -> Icons.Rounded.VisibilityOff
            LiveEventKind.TAB_RESTORED -> Icons.Rounded.Tab
            LiveEventKind.TAB_LIMIT -> Icons.Rounded.WarningAmber
            LiveEventKind.SYNC_RUNNING -> Icons.Rounded.CloudSync
            LiveEventKind.SYNC_DONE -> Icons.Rounded.CloudDone
            LiveEventKind.SYNC_FAILED -> Icons.Rounded.ErrorOutline
            LiveEventKind.TAB_RECEIVED -> Icons.Rounded.Sync
            LiveEventKind.EXTENSION_INSTALLED, LiveEventKind.EXTENSION_UPDATED, LiveEventKind.EXTENSION_REMOVED -> Icons.Rounded.Extension
            LiveEventKind.EXTENSION_ERROR -> Icons.Rounded.ErrorOutline
            LiveEventKind.UPDATE_AVAILABLE, LiveEventKind.UPDATE_DOWNLOADED, LiveEventKind.APK_INSTALL -> Icons.Rounded.SystemUpdate
            LiveEventKind.PERMISSION_IN_USE -> Icons.Rounded.NotificationsActive
            LiveEventKind.PERMISSION_BLOCKED -> Icons.Rounded.Block
            LiveEventKind.POPUP_BLOCKED -> Icons.Rounded.GppMaybe
            LiveEventKind.INSECURE_PAGE -> Icons.Rounded.WarningAmber
            LiveEventKind.PROFILE_LOCK -> Icons.Rounded.Lock
            LiveEventKind.CRASH_RECOVERED -> Icons.Rounded.Security
            LiveEventKind.NETWORK_LOST -> Icons.Rounded.WifiOff
            LiveEventKind.NETWORK_RESTORED -> Icons.Rounded.Wifi
            LiveEventKind.LOW_STORAGE -> Icons.Rounded.Storage
            LiveEventKind.READER_MODE -> Icons.Rounded.MenuBook
            LiveEventKind.READ_ALOUD -> Icons.Rounded.VolumeUp
            LiveEventKind.TRANSLATED -> Icons.Rounded.Translate
            LiveEventKind.SCREENSHOT_SAVED -> Icons.Rounded.CameraAlt
            LiveEventKind.PRINT_SAVED -> Icons.Rounded.Print
            LiveEventKind.QR_RESULT -> Icons.Rounded.QrCode
            LiveEventKind.CLIPBOARD_COPIED -> Icons.Rounded.ContentCopy
            LiveEventKind.SHARED_TO_PETAL -> Icons.Rounded.Share
            LiveEventKind.DEFAULT_BROWSER -> Icons.Rounded.OpenInBrowser
            LiveEventKind.INFO -> Icons.Rounded.Info
        }

        val shape = when (kind.category) {
            LiveEventCategory.BACKUP, LiveEventCategory.SYNC -> PetalNowBadgeShapeKind.CLOVER_4
            LiveEventCategory.EXTENSIONS, LiveEventCategory.UPDATES -> PetalNowBadgeShapeKind.SUNNY
            LiveEventCategory.SECURITY -> PetalNowBadgeShapeKind.GEM
            LiveEventCategory.DOWNLOADS -> PetalNowBadgeShapeKind.BUN
            LiveEventCategory.TABS, LiveEventCategory.SYSTEM -> PetalNowBadgeShapeKind.GHOSTISH
            LiveEventCategory.INTRO, LiveEventCategory.MILESTONES -> PetalNowBadgeShapeKind.FLOWER
            else -> PetalNowBadgeShapeKind.PUFFY
        }

        val accentRole = when {
            kind.priority == LiveEventPriority.URGENT -> PetalNowAccentRole.ERROR
            kind == LiveEventKind.BACKUP_DONE || kind == LiveEventKind.NETWORK_RESTORED -> PetalNowAccentRole.SUCCESS
            kind.category == LiveEventCategory.SECURITY -> PetalNowAccentRole.WARNING
            kind.category == LiveEventCategory.INTRO || kind.category == LiveEventCategory.EXTENSIONS -> PetalNowAccentRole.TERTIARY
            else -> PetalNowAccentRole.PRIMARY
        }

        val accentColor = if (kind.accent != 0L) Color(kind.accent) else null

        val mappedActions = item.actions.mapIndexed { idx, act ->
            PetalNowAction(
                id = "${item.key}_act_$idx",
                label = act.label,
                primary = act.primary,
            ) {
                act.run()
                if (kind.ttlMs > 0L) {
                    PetalLiveActionBus.dismiss(item.id)
                }
            }
        }

        val mappedToggle = item.toggle?.let { t ->
            PetalNowToggle(
                label = t.label,
                checked = t.checked,
                onChange = t.onChange,
            )
        }

        return PetalNowEntry(
            slotKey = item.id,
            familyKey = "ev_${item.key}",
            kind = cardKind,
            category = kind.category,
            categoryTag = kind.category.label,
            priority = kind.priority,
            title = item.title,
            compactText = if (pct >= 0) "$pct% · ${item.title}" else item.title,
            collapsedBadgeText = if (pct >= 0) "$pct%" else null,
            subtitle = item.subtitle,
            icon = icon,
            badgeShape = shape,
            accentRole = accentRole,
            accentOverrideColor = accentColor,
            progress = if (hasProgress) {
                PetalNowProgress(
                    fraction = if (pct >= 0) pct / 100f else null,
                    percent = pct,
                    isWavy = prefs.wavyProgress,
                    isIndeterminate = pct < 0,
                )
            } else null,
            toggle = mappedToggle,
            actions = mappedActions,
            canSwipeDismiss = true,
            rawBusId = item.id,
            defaultState = PetalNowDisplayState.COMPACT,
        )
    }

    private fun buildRestingEntry(
        context: Context,
        prefs: PetalNowPrefs,
        pageContext: PetalNowPageContext,
        onToggleAdBlock: (Boolean) -> Unit,
        onToggleDomainWhitelist: (String, Boolean) -> Unit,
        onOpenAdBlockSettings: () -> Unit,
    ): PetalNowEntry? {
        if (pageContext.host != null && prefs.isCategoryOn(LiveEventCategory.ADBLOCK)) {
            return buildAdBlockHeroEntry(
                context = context,
                pageContext = pageContext,
                onToggleAdBlock = onToggleAdBlock,
                onToggleDomainWhitelist = onToggleDomainWhitelist,
                onOpenAdBlockSettings = onOpenAdBlockSettings,
                rawBusId = null,
                priority = LiveEventPriority.INFO,
                isHeroResting = true,
            )
        }

        if (!pageContext.isHome) return null

        return PetalNowEntry(
            slotKey = "resting_home",
            familyKey = "resting_home",
            kind = PetalNowCardKind.RESTING_HOME,
            category = LiveEventCategory.TOOLS,
            categoryTag = "Petal Now",
            priority = LiveEventPriority.INFO,
            title = "Petal Now active",
            compactText = if (pageContext.globalAdBlockEnabled) "Shield ready" else "Shield paused",
            subtitle = "Live downloads, media, sync & ad shield status appear here automatically.",
            icon = Icons.Rounded.AutoAwesome,
            badgeShape = PetalNowBadgeShapeKind.FLOWER,
            accentRole = PetalNowAccentRole.PRIMARY,
            toggle = PetalNowToggle(
                label = "Ad & tracker blocker",
                subtitle = if (pageContext.globalAdBlockEnabled) "Enabled globally" else "Paused globally",
                checked = pageContext.globalAdBlockEnabled,
                onChange = onToggleAdBlock,
            ),
            actions = listOf(
                PetalNowAction("home_ad_settings", "Ad blocker settings", primary = false) {
                    onOpenAdBlockSettings()
                }
            ),
            canSwipeDismiss = false,
            rawBusId = null,
            defaultState = PetalNowDisplayState.COLLAPSED,
            isHeroResting = true,
        )
    }

    fun buildAdBlockHeroEntry(
        context: Context,
        pageContext: PetalNowPageContext,
        onToggleAdBlock: (Boolean) -> Unit,
        onToggleDomainWhitelist: (String, Boolean) -> Unit,
        onOpenAdBlockSettings: () -> Unit,
        rawBusId: String?,
        priority: Int,
        isHeroResting: Boolean,
    ): PetalNowEntry {
        val host = pageContext.host
        val globalMode = PetalAdBlockEngine.getAdBlockMode(context)
        val effectiveMode = PetalAdBlockEngine.getEffectiveMode(context, host)
        val isSiteWhitelisted = host != null && PetalAdBlockEngine.isDomainWhitelisted(host)
        val isEffectiveOn = effectiveMode != PetalAdBlockMode.DISABLED && pageContext.globalAdBlockEnabled
        val blockedOnSite = if (host != null) PetalAdBlockEngine.getBlockedCountForDomain(host) else 0
        val categories = if (host != null) PetalAdBlockEngine.getCategoryCountsForDomain(host) else PetalAdBlockEngine.BlockedCategoryCounts()
        val totalBlocked = PetalAdBlockEngine.getTotalBlockedCount()

        val compactText = when {
            !pageContext.globalAdBlockEnabled -> "Shield off"
            isSiteWhitelisted && host != null -> "Paused on $host"
            blockedOnSite > 0 -> "$blockedOnSite blocked"
            globalMode == PetalAdBlockMode.AGGRESSIVE -> "Strict shield"
            else -> "Shield on"
        }

        val subtitle = when {
            !pageContext.globalAdBlockEnabled -> "Ads & trackers are currently allowed across Petal."
            isSiteWhitelisted && host != null -> "Paused only for $host. Global protection stays on."
            host != null && blockedOnSite > 0 -> "$blockedOnSite requests blocked on $host"
            host != null -> "Actively protecting $host (${globalMode.title})"
            else -> "Total blocked this session: $totalBlocked"
        }

        val stats = buildList {
            if (host != null) {
                add(PetalNowStatChip("On site", blockedOnSite.toString()))
                val trackerTotal = categories.analytics + categories.content + categories.social
                add(PetalNowStatChip("Ads", categories.ads.toString()))
                add(PetalNowStatChip("Trackers", trackerTotal.toString()))
                if (categories.cookies > 0) {
                    add(PetalNowStatChip("Cookies", categories.cookies.toString()))
                }
            }
            add(PetalNowStatChip("Session", totalBlocked.toString()))
        }

        val actions = buildList {
            if (host != null && pageContext.globalAdBlockEnabled) {
                add(
                    PetalNowAction(
                        id = "ad_site_whitelist",
                        label = if (isSiteWhitelisted) "Resume on site" else "Allow on site",
                        primary = true,
                        icon = if (isSiteWhitelisted) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe,
                    ) {
                        onToggleDomainWhitelist(host, !isSiteWhitelisted)
                    }
                )
            }
            add(
                PetalNowAction(
                    id = "ad_settings",
                    label = "Filters & settings",
                    primary = host == null || !pageContext.globalAdBlockEnabled,
                    icon = Icons.Rounded.Tune,
                ) {
                    onOpenAdBlockSettings()
                }
            )
        }

        return PetalNowEntry(
            slotKey = if (rawBusId != null) rawBusId else "hero_adblock",
            familyKey = "adblock",
            kind = PetalNowCardKind.ADBLOCK,
            category = LiveEventCategory.ADBLOCK,
            categoryTag = if (isEffectiveOn) "Shield active" else "Shield off",
            priority = priority,
            title = if (isEffectiveOn) "Ad blocker on" else "Ad blocker off",
            compactText = compactText,
            collapsedBadgeText = if (isEffectiveOn && blockedOnSite > 0) blockedOnSite.toString() else null,
            subtitle = subtitle,
            icon = if (isEffectiveOn) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe,
            badgeShape = if (isEffectiveOn) PetalNowBadgeShapeKind.COOKIE_7 else PetalNowBadgeShapeKind.CIRCLE,
            secondaryBadgeShape = PetalNowBadgeShapeKind.CIRCLE,
            stateFlipActive = isEffectiveOn,
            accentRole = if (isEffectiveOn) PetalNowAccentRole.PRIMARY else PetalNowAccentRole.WARNING,
            toggle = PetalNowToggle(
                label = "Block ads & trackers",
                subtitle = if (pageContext.globalAdBlockEnabled) "${globalMode.title} filtering" else "Protection disabled",
                checked = pageContext.globalAdBlockEnabled,
                onChange = onToggleAdBlock,
            ),
            stats = stats,
            actions = actions,
            canSwipeDismiss = !isHeroResting,
            rawBusId = rawBusId,
            defaultState = if (isHeroResting) PetalNowDisplayState.COLLAPSED else PetalNowDisplayState.COMPACT,
            isHeroResting = isHeroResting,
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "${bytes.coerceAtLeast(0L)} B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return if (mb < 1024.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }
    }

    private fun formatEta(seconds: Long): String = when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds / 3600L}h ${(seconds % 3600L) / 60L}m"
    }

    private fun formatDurationMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return if (m >= 60L) {
            String.format(Locale.US, "%d:%02d:%02d", m / 60L, m % 60L, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
