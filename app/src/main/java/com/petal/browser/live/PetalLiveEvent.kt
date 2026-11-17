package com.petal.browser.live

/**
 * Groups of NowBar events. Each group has its own on/off switch in Island settings
 * (pref key = [prefKey]) on top of the master switch ([PetalLiveEvents.KEY_MASTER]).
 */
enum class LiveEventCategory(val id: String, val label: String) {
    DOWNLOADS("downloads", "Downloads"),
    MEDIA("media", "Media playback"),
    MILESTONES("milestones", "Milestones & badges"),
    BACKUP("backup", "Backup & restore"),
    ADBLOCK("adblock", "Ad blocker"),
    SETTINGS("settings", "Settings changes"),
    TABS("tabs", "Tabs & private mode"),
    SYNC("sync", "Sync & Send Tab"),
    EXTENSIONS("extensions", "Extensions"),
    UPDATES("updates", "App updates & installs"),
    SECURITY("security", "Security & permissions"),
    SYSTEM("system", "Network & storage"),
    TOOLS("tools", "Reader, scan, files & tools"),
    INTRO("intro", "Feature introductions");

    val prefKey: String get() = "sp_island_ev_$id"
}

object LiveEventPriority {
    /** Background info. Can wait in the queue and never bumps anything. */
    const val INFO = 0
    /** Ongoing progress (downloads, backups, sync). */
    const val PROGRESS = 1
    /** Results the user should notice (finished, saved, changed). */
    const val IMPORTANT = 2
    /** Failures, security warnings and permission-in-use. Always wins a slot. */
    const val URGENT = 3
}

/**
 * Every kind of app event the NowBar can show. [ttlMs] is how long the card stays when nobody
 * touches it (0 = stays until its owner dismisses/replaces it, e.g. a running backup).
 * [accent] is an ARGB colour; 0 means "use the theme accent".
 */
enum class LiveEventKind(
    val category: LiveEventCategory,
    val priority: Int,
    val ttlMs: Long,
    val accent: Long = 0L
) {
    // Backup & restore
    BACKUP_PROGRESS(LiveEventCategory.BACKUP, LiveEventPriority.PROGRESS, 0L),
    BACKUP_DONE(LiveEventCategory.BACKUP, LiveEventPriority.IMPORTANT, 8_000L, 0xFF00E676),
    BACKUP_FAILED(LiveEventCategory.BACKUP, LiveEventPriority.URGENT, 10_000L, 0xFFFF5252),

    // Ad blocker
    ADBLOCK_STATE(LiveEventCategory.ADBLOCK, LiveEventPriority.IMPORTANT, 7_000L, 0xFF00BFA5),
    ADBLOCK_BLOCKED(LiveEventCategory.ADBLOCK, LiveEventPriority.INFO, 4_000L, 0xFF00BFA5),

    // Settings & intros
    SETTING_CHANGED(LiveEventCategory.SETTINGS, LiveEventPriority.INFO, 6_000L),
    LIQUID_GLASS_INTRO(LiveEventCategory.INTRO, LiveEventPriority.IMPORTANT, 12_000L, 0xFF7C4DFF),

    // Downloads (extra to the existing progress card)
    DOWNLOAD_FAILED(LiveEventCategory.DOWNLOADS, LiveEventPriority.URGENT, 10_000L, 0xFFFF5252),
    TORRENT_PROGRESS(LiveEventCategory.DOWNLOADS, LiveEventPriority.PROGRESS, 0L),
    SOCIAL_DOWNLOAD(LiveEventCategory.DOWNLOADS, LiveEventPriority.PROGRESS, 0L),

    // Tabs
    INCOGNITO_STATE(LiveEventCategory.TABS, LiveEventPriority.IMPORTANT, 4_000L, 0xFF9E9E9E),
    TAB_RESTORED(LiveEventCategory.TABS, LiveEventPriority.INFO, 4_000L),
    TAB_LIMIT(LiveEventCategory.TABS, LiveEventPriority.IMPORTANT, 5_000L, 0xFFFFB74D),

    // Sync
    SYNC_RUNNING(LiveEventCategory.SYNC, LiveEventPriority.PROGRESS, 0L),
    SYNC_DONE(LiveEventCategory.SYNC, LiveEventPriority.INFO, 4_000L),
    SYNC_FAILED(LiveEventCategory.SYNC, LiveEventPriority.URGENT, 8_000L, 0xFFFF5252),
    TAB_RECEIVED(LiveEventCategory.SYNC, LiveEventPriority.IMPORTANT, 10_000L),

    // Extensions
    EXTENSION_INSTALLED(LiveEventCategory.EXTENSIONS, LiveEventPriority.IMPORTANT, 6_000L),
    EXTENSION_UPDATED(LiveEventCategory.EXTENSIONS, LiveEventPriority.INFO, 5_000L),
    EXTENSION_REMOVED(LiveEventCategory.EXTENSIONS, LiveEventPriority.INFO, 5_000L),
    EXTENSION_ERROR(LiveEventCategory.EXTENSIONS, LiveEventPriority.URGENT, 8_000L, 0xFFFF5252),

    // Updates & installs
    UPDATE_AVAILABLE(LiveEventCategory.UPDATES, LiveEventPriority.IMPORTANT, 12_000L, 0xFF40C4FF),
    UPDATE_DOWNLOADED(LiveEventCategory.UPDATES, LiveEventPriority.IMPORTANT, 12_000L, 0xFF40C4FF),
    APK_INSTALL(LiveEventCategory.UPDATES, LiveEventPriority.IMPORTANT, 8_000L),

    // Security & permissions
    PERMISSION_IN_USE(LiveEventCategory.SECURITY, LiveEventPriority.URGENT, 6_000L, 0xFFFFB74D),
    PERMISSION_BLOCKED(LiveEventCategory.SECURITY, LiveEventPriority.INFO, 5_000L),
    POPUP_BLOCKED(LiveEventCategory.SECURITY, LiveEventPriority.INFO, 4_000L),
    INSECURE_PAGE(LiveEventCategory.SECURITY, LiveEventPriority.URGENT, 8_000L, 0xFFFF5252),
    PROFILE_LOCK(LiveEventCategory.SECURITY, LiveEventPriority.IMPORTANT, 4_000L),
    CRASH_RECOVERED(LiveEventCategory.SECURITY, LiveEventPriority.IMPORTANT, 8_000L, 0xFFFFB74D),

    // System
    NETWORK_LOST(LiveEventCategory.SYSTEM, LiveEventPriority.IMPORTANT, 0L, 0xFFFF5252),
    NETWORK_RESTORED(LiveEventCategory.SYSTEM, LiveEventPriority.INFO, 3_500L, 0xFF00E676),
    LOW_STORAGE(LiveEventCategory.SYSTEM, LiveEventPriority.URGENT, 10_000L, 0xFFFF5252),

    // Tools
    READER_MODE(LiveEventCategory.TOOLS, LiveEventPriority.INFO, 4_000L),
    READ_ALOUD(LiveEventCategory.TOOLS, LiveEventPriority.PROGRESS, 0L),
    TRANSLATED(LiveEventCategory.TOOLS, LiveEventPriority.INFO, 4_000L),
    SCREENSHOT_SAVED(LiveEventCategory.TOOLS, LiveEventPriority.IMPORTANT, 6_000L),
    PRINT_SAVED(LiveEventCategory.TOOLS, LiveEventPriority.INFO, 5_000L),
    QR_RESULT(LiveEventCategory.TOOLS, LiveEventPriority.IMPORTANT, 10_000L),
    CLIPBOARD_COPIED(LiveEventCategory.TOOLS, LiveEventPriority.INFO, 3_000L),
    SHARED_TO_PETAL(LiveEventCategory.TOOLS, LiveEventPriority.INFO, 5_000L),
    DEFAULT_BROWSER(LiveEventCategory.TOOLS, LiveEventPriority.INFO, 5_000L),

    // Generic
    INFO(LiveEventCategory.TOOLS, LiveEventPriority.INFO, 5_000L);
}

/** A button on an expanded event card. [run] is called on the main thread. */
class LiveEventAction(
    val label: String,
    /** True for the filled, highlighted button. Only one action per card should be primary. */
    val primary: Boolean = false,
    val run: () -> Unit
)

/** An on/off switch on an expanded event card (used by the ad blocker card). */
class LiveEventToggle(
    val label: String,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit
)

/**
 * One generic NowBar card. [key] is the merge key: posting the same key again replaces the card
 * (and restarts its timer) instead of stacking a second one.
 */
data class LiveEventItem(
    val key: String,
    val kind: LiveEventKind,
    val title: String,
    val subtitle: String = "",
    /** 0..100 for a progress ring, or -1 for none. */
    val progressPercent: Int = -1,
    val actions: List<LiveEventAction> = emptyList(),
    val toggle: LiveEventToggle? = null
) : LiveActionItem("ev_$key")

/** The settings category a card belongs to, so each group can be switched off. */
fun liveCategoryOf(item: LiveActionItem): LiveEventCategory = when (item) {
    is LiveActionItem.Download -> LiveEventCategory.DOWNLOADS
    is LiveActionItem.Media -> LiveEventCategory.MEDIA
    is LiveActionItem.FileViewer -> LiveEventCategory.TOOLS
    is LiveActionItem.Task -> LiveEventCategory.TOOLS
    is LiveActionItem.Achievement -> LiveEventCategory.MILESTONES
    is LiveActionItem.Credential -> LiveEventCategory.SECURITY
    is LiveEventItem -> item.kind.category
}

/** Higher number = more important. Decides who gets a slot when the bar is full. */
fun livePriorityOf(item: LiveActionItem?): Int = when (item) {
    null -> -1
    is LiveActionItem.Download ->
        if (item.isCompleted) LiveEventPriority.IMPORTANT else LiveEventPriority.PROGRESS
    is LiveActionItem.Media -> LiveEventPriority.INFO
    is LiveActionItem.FileViewer -> LiveEventPriority.INFO
    is LiveActionItem.Task -> LiveEventPriority.PROGRESS
    is LiveActionItem.Achievement -> LiveEventPriority.IMPORTANT
    is LiveActionItem.Credential -> LiveEventPriority.IMPORTANT
    is LiveEventItem -> item.kind.priority
}

object PetalLiveEvents {
    const val KEY_MASTER = "sp_island_events_master"
}
