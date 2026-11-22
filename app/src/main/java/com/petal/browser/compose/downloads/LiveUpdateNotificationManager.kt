package com.petal.browser.compose.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.ui.theme.WidgetColors

/**
 * Single place that builds Petal's "live" progress notifications (downloads and similar).
 *
 * Two paths, chosen per device:
 *  1. Android 16+ (API 36) -> a real [Notification.ProgressStyle] notification. When the user's
 *     "Live notifications" system switch is on it is also promoted (status-bar chip + lock screen).
 *     When it is off, the same notification is shown as a normal themed progress card.
 *  2. Older Android -> a clean standard progress notification with the same colours and actions.
 *
 * Rules that make Android accept a notification for promotion (breaking any of them silently
 * disables the chip): ongoing, has a title, a promotable style (ProgressStyle), NO custom
 * RemoteViews, NOT colorized, channel not IMPORTANCE_MIN. Nothing in here may break them.
 *
 * The notification theme follows Petal: Material You accent when dynamic colour is on,
 * otherwise the selected Petal palette (Petal pink by default).
 */
object LiveUpdateNotificationManager {

    private const val TAG = "LiveUpdateNotifMgr"
    const val CHANNEL_ID = "petal_live_downloads"
    const val CHANNEL_NAME = "Live Downloader & Alerts"

    /** Tag used for every Petal-posted download notification so ids can never collide with other features. */
    const val NOTIF_TAG = "petal_dl"

    const val COLOR_PETAL_PINK_LIGHT = -0x27e4a0 // 0xFFD81B60
    const val COLOR_PETAL_PINK_DARK = -0x4f38 // 0xFFFFB0C8

    const val PREF_LIVE_UPDATES = "sp_live_updates"

    // ───────────────────────────── Support checks ─────────────────────────────

    /**
     * True when the device is Android 16+ AND the user allows Petal to post promoted
     * (Live) notifications in system settings.
     */
    @JvmStatic
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            nm.canPostPromotedNotifications()
        } catch (_: Throwable) {
            false
        }
    }

    /** The in-app "Live updates" switch (default on). */
    @JvmStatic
    fun isLiveUpdatesEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_LIVE_UPDATES, true)

    /** Live updates wanted by the user AND supported by the device. */
    @JvmStatic
    fun shouldPromote(context: Context): Boolean =
        isLiveUpdatesEnabled(context) && canPostPromotedNotifications(context)

    @JvmStatic
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm != null && nm.areNotificationsEnabled()
    }

    /** Opens the system page where the user turns "Live notifications" on for Petal. */
    @JvmStatic
    fun getLiveNotificationSettingsIntent(context: Context): Intent {
        val intent: Intent = if (Build.VERSION.SDK_INT >= 36) {
            Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:" + context.packageName)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    // ───────────────────────────── Channel ─────────────────────────────

    @JvmStatic
    fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_LOW: silent, but still eligible for promotion (IMPORTANCE_MIN is not).
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            description = "Live real-time alerts for active downloads with progress, speed and controls"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    // ───────────────────────────── Theme ─────────────────────────────

    @JvmStatic
    fun getLiveThemeAccentColor(context: Context): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val useDynamicColor = sp.getBoolean("useDynamicColor", isDynamicSupported)
        val isDark = isDarkTheme(context)

        if (useDynamicColor && isDynamicSupported) {
            try {
                val resId = if (isDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
                val dynamicColor = ContextCompat.getColor(context, resId)
                if (dynamicColor != 0) return dynamicColor
            } catch (_: Throwable) {
            }
        }

        val paletteId = sp.getString("sp_palette_id", "petal") ?: "petal"
        try {
            return WidgetColors.primaryArgb(paletteId, isDark)
        } catch (_: Throwable) {
        }
        return if (isDark) COLOR_PETAL_PINK_DARK else COLOR_PETAL_PINK_LIGHT
    }

    private fun isDarkTheme(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val configStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM")
        if ("DARK".equals(configStr, ignoreCase = true)) return true
        if ("LIGHT".equals(configStr, ignoreCase = true)) return false
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    // ───────────────────────────── Builder ─────────────────────────────

    /**
     * Builds the live progress notification.
     *
     * @param detailText longer status line for the standard notification ("2.1 MB/s • 45%").
     *        The short status-bar chip is derived here ("45%", "Paused"), because the system
     *        truncates chips to a few characters.
     * @param togglePendingIntent Pause/Resume action, or null when the row cannot be paused.
     */
    @JvmStatic
    fun buildLiveNotification(
        context: Context,
        title: String,
        contentText: String,
        progressPercent: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
        detailText: String?,
        contentPendingIntent: PendingIntent?,
        cancelPendingIntent: PendingIntent?,
        togglePendingIntent: PendingIntent?,
        groupKey: String? = null
    ): Notification {
        ensureChannelCreated(context)

        val accent = getLiveThemeAccentColor(context)
        val percent = progressPercent.coerceIn(0, 100)
        val chip = when {
            isPaused -> "Paused"
            isIndeterminate -> "…"
            else -> "$percent%"
        }

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                return buildProgressStyle(
                    context, title, contentText, percent, isIndeterminate, isPaused, chip,
                    contentPendingIntent, cancelPendingIntent, togglePendingIntent,
                    accent, promote = shouldPromote(context), groupKey = groupKey
                )
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "ProgressStyle notification failed, using standard one", t)
            }
        }
        return buildStandard(
            context, title, contentText, percent, isIndeterminate, isPaused, detailText ?: chip,
            contentPendingIntent, cancelPendingIntent, togglePendingIntent, accent, groupKey
        )
    }

    private fun buildProgressStyle(
        context: Context,
        title: String,
        contentText: String,
        percent: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
        chip: String,
        contentPendingIntent: PendingIntent?,
        cancelPendingIntent: PendingIntent?,
        togglePendingIntent: PendingIntent?,
        accent: Int,
        promote: Boolean,
        groupKey: String?
    ): Notification {
        // One accent segment; styledByProgress lets the system draw the filled part in the accent
        // and the rest as a faded tonal track, which is the Material 3 Expressive look.
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(accent)))
            .setProgressTrackerIcon(
                Icon.createWithResource(context, if (isPaused) R.drawable.icon_pause else R.drawable.ic_petal_filled)
            )
        if (isIndeterminate) style.setProgressIndeterminate(true) else style.setProgress(percent)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isPaused) R.drawable.icon_pause else R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(style)
            .setShortCriticalText(chip)
            // Stay ongoing + promoted while paused: dropping either makes the notification leave
            // the live chip and jump around the shade.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(accent)
            .setColorized(false) // colorized notifications are NOT promotable
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(promote)
            .setContentIntent(contentPendingIntent)
        // Keep Mozilla's group so its foreground summary still folds this card in.
        if (groupKey != null) builder.setGroup(groupKey)

        togglePendingIntent?.let {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, if (isPaused) R.drawable.icon_play else R.drawable.icon_pause),
                    if (isPaused) "Resume" else "Pause",
                    it
                ).build()
            )
        }
        cancelPendingIntent?.let {
            builder.addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.icon_close), "Cancel", it).build()
            )
        }

        val notification = builder.build()
        if (promote && !notification.hasPromotableCharacteristics()) {
            android.util.Log.w(TAG, "Live notification built but Android says it is not promotable")
        }
        return notification
    }

    private fun buildStandard(
        context: Context,
        title: String,
        contentText: String,
        percent: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
        detail: String,
        contentPendingIntent: PendingIntent?,
        cancelPendingIntent: PendingIntent?,
        togglePendingIntent: PendingIntent?,
        accent: Int,
        groupKey: String?
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isPaused) R.drawable.icon_pause else R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setColor(accent)
            .setColorized(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setProgress(100, percent, isIndeterminate)
        if (groupKey != null) builder.setGroup(groupKey)

        togglePendingIntent?.let {
            builder.addAction(
                if (isPaused) R.drawable.icon_play else R.drawable.icon_pause,
                if (isPaused) "Resume" else "Pause",
                it
            )
        }
        cancelPendingIntent?.let { builder.addAction(R.drawable.icon_close, "Cancel", it) }
        return builder.build()
    }
}
