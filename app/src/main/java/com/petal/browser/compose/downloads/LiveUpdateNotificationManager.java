package com.petal.browser.compose.downloads;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.petal.browser.R;
import com.petal.browser.ui.theme.WidgetColors;

import java.lang.reflect.Method;

/**
 * LiveUpdateNotificationManager
 * Handles live update notifications for active downloads:
 * 1. Follows active browser theming: Material You dynamic color when enabled, or Petal Pink when disabled.
 * 2. Replaces download icon above the live progress bar with an animated doll running in the direction of progress.
 * 3. Handles granular OS-specific permissions for live alerts/actions across Android versions.
 */
public class LiveUpdateNotificationManager {

    private static final String TAG = "LiveUpdateNotifMgr";
    public static final String CHANNEL_ID = "petal_live_downloads";
    public static final String CHANNEL_NAME = "Live Downloader & Alerts";

    public static final int COLOR_PETAL_PINK_LIGHT = 0xFFD81B60;
    public static final int COLOR_PETAL_PINK_DARK  = 0xFFFFB0C8;

    /**
     * Runtime capability check to determine if Android 16 promoted live notifications can be posted.
     */
    public static boolean canPostPromotedNotifications(Context context) {
        if (Build.VERSION.SDK_INT < 36) { // Android 16 / API 36
            return false;
        }
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return false;

            Method method = null;
            try {
                method = NotificationManager.class.getMethod("canPostPromotedNotifications");
            } catch (NoSuchMethodException e) {
                try {
                    method = NotificationManager.class.getMethod("canUsePromotedNotifications");
                } catch (NoSuchMethodException ignored) {}
            }

            if (method != null) {
                Object result = method.invoke(nm);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }

            return nm.areNotificationsEnabled();
        } catch (Exception e) {
            Log.d(TAG, "Error checking promoted notification capability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if notification permission is granted on Android 13+ (API 33+).
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    /**
     * Resolves an intent to open live alert / notification channel settings directly.
     */
    public static Intent getLiveNotificationSettingsIntent(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID);
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Ensures notification channel is created for live updates on Android 8.0+.
     */
    public static void ensureChannelCreated(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Live real-time alerts for active downloads with progress, velocity, and controls");
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Returns the active browser theme accent color:
     * - If Material You (Dynamic Color) is enabled (Android 12+), returns system dynamic primary accent.
     * - If disabled or on earlier Android versions, returns signature Petal Pink accent.
     */
    public static int getLiveThemeAccentColor(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        boolean useDynamicColor = sp.getBoolean("useDynamicColor", isDynamicSupported);
        boolean isDark = isSystemInDarkTheme(context);

        if (useDynamicColor && isDynamicSupported) {
            try {
                int resId = isDark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600;
                int dynamicColor = ContextCompat.getColor(context, resId);
                if (dynamicColor != 0) {
                    return dynamicColor;
                }
            } catch (Throwable ignored) {}
        }

        // Palette fallback: follow selected palette or Petal Pink
        String paletteId = sp.getString("sp_palette_id", "petal");
        try {
            return WidgetColors.primaryArgb(paletteId, isDark);
        } catch (Throwable ignored) {}

        return isDark ? COLOR_PETAL_PINK_DARK : COLOR_PETAL_PINK_LIGHT;
    }

    private static boolean isSystemInDarkTheme(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String configStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM");
        if ("DARK".equalsIgnoreCase(configStr)) return true;
        if ("LIGHT".equalsIgnoreCase(configStr)) return false;
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Resolves the animated doll running frame based on progress percentage.
     * Frames cycle sequentially: 1 -> 2 -> 3 -> 4 -> 1 facing forward in the direction of progress.
     */
    public static int getRunningDollFrameResource(int progressPercent) {
        int frame = Math.abs(progressPercent) % 4;
        switch (frame) {
            case 1:
                return R.drawable.ic_doll_run_2;
            case 2:
                return R.drawable.ic_doll_run_3;
            case 3:
                return R.drawable.ic_doll_run_4;
            case 0:
            default:
                return R.drawable.ic_doll_run_1;
        }
    }

    /**
     * Builds live update notification for download progress or media streaming,
     * utilizing Android 16 Notification.ProgressStyle when supported/enabled,
     * or standard ongoing NotificationCompat with dynamic theme and animated running doll.
     */
    public static Notification buildLiveNotification(
            Context context,
            long id,
            String title,
            String contentText,
            int progressPercent,
            boolean isIndeterminate,
            boolean isPaused,
            String chipText,
            PendingIntent contentPendingIntent,
            PendingIntent cancelPendingIntent,
            PendingIntent togglePendingIntent
    ) {
        ensureChannelCreated(context);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean liveUpdatesPref = sp.getBoolean("sp_live_updates", true);
        int themeAccentColor = getLiveThemeAccentColor(context);
        int dollFrameRes = isPaused ? R.drawable.ic_doll_run_1 : getRunningDollFrameResource(progressPercent);

        // Try building Android 16 native Notification.ProgressStyle if API >= 36 and capable
        if (canPostPromotedNotifications(context)) {
            Notification nativeNotif = buildAndroid16ProgressStyleNotification(
                    context, title, contentText, progressPercent, isIndeterminate, isPaused, chipText,
                    contentPendingIntent, cancelPendingIntent, togglePendingIntent, liveUpdatesPref,
                    themeAccentColor, dollFrameRes
            );
            if (nativeNotif != null) {
                return nativeNotif;
            }
        }

        // Build NotificationCompat with dynamic theming and animated running doll
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(dollFrameRes)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(chipText)
                .setOngoing(!isPaused)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setColor(themeAccentColor)
                .setColorized(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPendingIntent);

        if (togglePendingIntent != null) {
            if (isPaused) {
                builder.addAction(R.drawable.icon_play, "Resume", togglePendingIntent);
            } else {
                builder.addAction(R.drawable.icon_pause, "Pause", togglePendingIntent);
            }
        }

        if (cancelPendingIntent != null) {
            builder.addAction(R.drawable.icon_close, "Cancel", cancelPendingIntent);
        }
        if (contentPendingIntent != null) {
            builder.addAction(R.drawable.icon_download, "Downloads", contentPendingIntent);
        }

        int clampedProgress = Math.max(0, Math.min(100, progressPercent));
        if (isIndeterminate) {
            builder.setProgress(0, 0, true);
        } else {
            builder.setProgress(100, clampedProgress, false);
        }

        // Custom heads-up / expanded RemoteViews featuring the running doll above the progress bar
        try {
            RemoteViews customView = new RemoteViews(context.getPackageName(), R.layout.layout_live_download_notification);
            customView.setTextViewText(R.id.notification_title, title);
            customView.setTextViewText(R.id.notification_subtitle, contentText);
            customView.setTextViewText(R.id.notification_speed_chip, chipText != null ? chipText : "");
            customView.setTextColor(R.id.notification_speed_chip, themeAccentColor);
            customView.setImageViewResource(R.id.notification_doll_runner, dollFrameRes);

            if (isIndeterminate) {
                customView.setProgressBar(R.id.notification_progress_bar, 0, 0, true);
            } else {
                customView.setProgressBar(R.id.notification_progress_bar, 100, clampedProgress, false);
            }

            builder.setCustomContentView(customView);
        } catch (Throwable t) {
            Log.d(TAG, "Custom RemoteViews creation ignored: " + t.getMessage());
        }

        // Attach live alert metadata extras safely
        Bundle extras = new Bundle();
        extras.putString("android.liveAlertText", chipText);
        extras.putBoolean("android.isLiveAlert", true);
        extras.putBoolean("android.promotedOngoing", !isPaused && liveUpdatesPref);
        extras.putString("android.shortCriticalText", chipText);
        extras.putInt("android.accentColor", themeAccentColor);
        builder.setExtras(extras);

        try {
            Method setShortCriticalText = builder.getClass().getMethod("setShortCriticalText", CharSequence.class);
            setShortCriticalText.invoke(builder, chipText);
        } catch (Exception ignored) {}

        return builder.build();
    }

    /**
     * Builds API 36 (Android 16) Notification.ProgressStyle with segment tracking support,
     * applying the active browser theme color and the running doll icon directly on the tracker.
     */
    private static Notification buildAndroid16ProgressStyleNotification(
            Context context,
            String title,
            String contentText,
            int progressPercent,
            boolean isIndeterminate,
            boolean isPaused,
            String chipText,
            PendingIntent contentPendingIntent,
            PendingIntent cancelPendingIntent,
            PendingIntent togglePendingIntent,
            boolean promoteLiveUpdate,
            int accentColor,
            int dollFrameRes
    ) {
        try {
            int clampedProgress = Math.max(0, Math.min(100, progressPercent));

            Notification.ProgressStyle style = new Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .setProgress(clampedProgress)
                    .setProgressSegments(java.util.Collections.singletonList(
                            new Notification.ProgressStyle.Segment(100).setColor(accentColor)
                    ))
                    .setProgressTrackerIcon(Icon.createWithResource(context, dollFrameRes));

            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(dollFrameRes)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setStyle(style)
                    .setShortCriticalText(chipText != null && !chipText.isEmpty() ? chipText : clampedProgress + "%")
                    .setProgress(100, clampedProgress, isIndeterminate)
                    .setOngoing(!isPaused)
                    .setOnlyAlertOnce(true)
                    .setColor(accentColor)
                    .setColorized(true)
                    .setContentIntent(contentPendingIntent)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);

            if (promoteLiveUpdate && !isPaused) {
                builder.setFlag(Notification.FLAG_PROMOTED_ONGOING, true);
            }

            Bundle extras = new Bundle();
            extras.putString("android.liveAlertText", chipText);
            extras.putBoolean("android.isLiveAlert", true);
            extras.putBoolean("android.promotedOngoing", !isPaused && promoteLiveUpdate);
            extras.putString("android.shortCriticalText", chipText);
            extras.putInt("android.accentColor", accentColor);
            builder.addExtras(extras);

            if (togglePendingIntent != null) {
                Notification.Action toggleAction = new Notification.Action.Builder(
                        Icon.createWithResource(context, isPaused ? R.drawable.icon_play : R.drawable.icon_pause),
                        isPaused ? "Resume" : "Pause",
                        togglePendingIntent
                ).build();
                builder.addAction(toggleAction);
            }

            if (cancelPendingIntent != null) {
                Notification.Action cancelAction = new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.icon_close),
                        "Cancel",
                        cancelPendingIntent
                ).build();
                builder.addAction(cancelAction);
            }

            return builder.build();
        } catch (Throwable t) {
            Log.d(TAG, "Android 16 ProgressStyle creation failed, falling back to NotificationCompat: " + t.getMessage());
            return null;
        }
    }
}
