package com.petal.browser.compose.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.ui.theme.WidgetColors
import java.lang.reflect.Method

/**
 * LiveUpdateNotificationManager
 * Handles clean, standard live update notifications for active downloads:
 * 1. Follows active browser theming: Material You dynamic color when enabled, or Petal Pink when disabled.
 * 2. Clean, native Android progress presentation with download speed, percentage, ETA, and progress bar.
 * 3. Ensures silent, continuous, unsuppressed live alerts with Pause, Resume, and Cancel actions.
 */
object LiveUpdateNotificationManager {

    private const val TAG = "LiveUpdateNotifMgr"
    const val CHANNEL_ID = "petal_live_downloads"
    const val CHANNEL_NAME = "Live Downloader & Alerts"

    const val COLOR_PETAL_PINK_LIGHT = -0x27e4a0 // 0xFFD81B60
    const val COLOR_PETAL_PINK_DARK = -0x4f38 // 0xFFFFB0C8

    @JvmStatic
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            var method: Method? = null
            try {
                method = NotificationManager::class.java.getMethod("canPostPromotedNotifications")
            } catch (_: NoSuchMethodException) {
                try {
                    method = NotificationManager::class.java.getMethod("canUsePromotedNotifications")
                } catch (_: NoSuchMethodException) {}
            }
            if (method != null) {
                val result = method.invoke(nm)
                if (result is Boolean) return result
            }
            nm.areNotificationsEnabled()
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm != null && nm.areNotificationsEnabled()
    }

    @JvmStatic
    fun getLiveNotificationSettingsIntent(context: Context): Intent {
        val intent: Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    @JvmStatic
    fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live real-time alerts for active downloads with progress, velocity, and controls"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    @JvmStatic
    fun getLiveThemeAccentColor(context: Context): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val useDynamicColor = sp.getBoolean("useDynamicColor", isDynamicSupported)
        val isDark = isSystemInDarkTheme(context)

        if (useDynamicColor && isDynamicSupported) {
            try {
                val resId = if (isDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
                val dynamicColor = ContextCompat.getColor(context, resId)
                if (dynamicColor != 0) return dynamicColor
            } catch (_: Throwable) {}
        }

        val paletteId = sp.getString("sp_palette_id", "petal") ?: "petal"
        try {
            return WidgetColors.primaryArgb(paletteId, isDark)
        } catch (_: Throwable) {}

        return if (isDark) COLOR_PETAL_PINK_DARK else COLOR_PETAL_PINK_LIGHT
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val configStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM")
        if ("DARK".equals(configStr, ignoreCase = true)) return true
        if ("LIGHT".equals(configStr, ignoreCase = true)) return false
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    fun getRunningDollFrameResource(progressPercent: Int): Int = R.drawable.ic_download

    @JvmStatic
    fun buildLiveNotification(
        context: Context,
        id: Long,
        title: String,
        contentText: String,
        progressPercent: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
        chipText: String?,
        contentPendingIntent: PendingIntent?,
        cancelPendingIntent: PendingIntent?,
        togglePendingIntent: PendingIntent?
    ): Notification {
        ensureChannelCreated(context)

        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val liveUpdatesPref = sp.getBoolean("sp_live_updates", true)
        val themeAccentColor = getLiveThemeAccentColor(context)
        val iconRes = if (isPaused) R.drawable.icon_pause else R.drawable.ic_download
        val clampedProgress = Math.max(0, Math.min(100, progressPercent))

        if (liveUpdatesPref && canPostPromotedNotifications(context)) {
            val nativeNotif = buildAndroid16ProgressStyleNotification(
                context, title, contentText, clampedProgress, isIndeterminate, isPaused, chipText,
                contentPendingIntent, cancelPendingIntent, togglePendingIntent, liveUpdatesPref,
                themeAccentColor, iconRes
            )
            if (nativeNotif != null) return nativeNotif
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(chipText)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(themeAccentColor)
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)

        if (togglePendingIntent != null) {
            builder.addAction(
                if (isPaused) R.drawable.icon_play else R.drawable.icon_pause,
                if (isPaused) "Resume" else "Pause",
                togglePendingIntent
            )
        }

        if (cancelPendingIntent != null) {
            builder.addAction(R.drawable.icon_close, "Cancel", cancelPendingIntent)
        }
        if (contentPendingIntent != null) {
            builder.addAction(R.drawable.icon_download, "Downloads", contentPendingIntent)
        }

        if (isIndeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, clampedProgress, false)
        }

        // Custom heads-up / expanded RemoteViews featuring clean Material You progress
        try {
            val customView = RemoteViews(context.packageName, R.layout.layout_live_download_notification).apply {
                setTextViewText(R.id.notification_title, title)
                setTextViewText(R.id.notification_subtitle, contentText)
                setTextViewText(R.id.notification_speed_chip, chipText ?: "")
                setTextColor(R.id.notification_speed_chip, themeAccentColor)

                if (isIndeterminate) {
                    setProgressBar(R.id.notification_progress_bar, 0, 0, true)
                } else {
                    setProgressBar(R.id.notification_progress_bar, 100, clampedProgress, false)
                }
            }
            builder.setCustomContentView(customView)
            builder.setCustomBigContentView(customView)
        } catch (t: Throwable) {
            Log.d(TAG, "Custom RemoteViews creation ignored: " + t.message)
        }

        if (liveUpdatesPref) {
            val extras = Bundle().apply {
                putString("android.liveAlertText", chipText)
                putBoolean("android.isLiveAlert", true)
                putBoolean("android.promotedOngoing", !isPaused)
                putString("android.shortCriticalText", chipText)
                putInt("android.accentColor", themeAccentColor)
                putInt("android.progress", clampedProgress)
                putInt("android.progressMax", 100)
                putBoolean("android.progressIndeterminate", isIndeterminate)
            }
            builder.setExtras(extras)

            try {
                val setShortCriticalText = builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                setShortCriticalText.invoke(builder, chipText)
            } catch (_: Exception) {}
        }

        return builder.build()
    }

    private fun buildAndroid16ProgressStyleNotification(
        context: Context,
        title: String,
        contentText: String,
        clampedProgress: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
        chipText: String?,
        contentPendingIntent: PendingIntent?,
        cancelPendingIntent: PendingIntent?,
        togglePendingIntent: PendingIntent?,
        promoteLiveUpdate: Boolean,
        accentColor: Int,
        iconRes: Int
    ): Notification? {
        return try {
            val style = Notification.ProgressStyle()
                .setStyledByProgress(false)
                .setProgress(clampedProgress)
                .setProgressSegments(
                    listOf(Notification.ProgressStyle.Segment(100).setColor(accentColor))
                )
                .setProgressTrackerIcon(Icon.createWithResource(context, iconRes))

            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(style)
                .setShortCriticalText(if (!chipText.isNullOrEmpty()) chipText else "$clampedProgress%")
                .setProgress(100, clampedProgress, isIndeterminate)
                .setOngoing(!isPaused)
                .setOnlyAlertOnce(true)
                .setColor(accentColor)
                .setColorized(true)
                .setContentIntent(contentPendingIntent)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)

            if (promoteLiveUpdate && !isPaused) {
                try {
                    builder.setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                } catch (_: Throwable) {}
            }

            val extras = Bundle().apply {
                putString("android.liveAlertText", chipText)
                putBoolean("android.isLiveAlert", true)
                putBoolean("android.promotedOngoing", !isPaused && promoteLiveUpdate)
                putString("android.shortCriticalText", chipText)
                putInt("android.accentColor", accentColor)
                putInt("android.progress", clampedProgress)
                putInt("android.progressMax", 100)
                putBoolean("android.progressIndeterminate", isIndeterminate)
            }
            builder.addExtras(extras)

            if (togglePendingIntent != null) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, if (isPaused) R.drawable.icon_play else R.drawable.icon_pause),
                        if (isPaused) "Resume" else "Pause",
                        togglePendingIntent
                    ).build()
                )
            }

            if (cancelPendingIntent != null) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.icon_close),
                        "Cancel",
                        cancelPendingIntent
                    ).build()
                )
            }

            builder.build()
        } catch (_: Throwable) {
            null
        }
    }
}
