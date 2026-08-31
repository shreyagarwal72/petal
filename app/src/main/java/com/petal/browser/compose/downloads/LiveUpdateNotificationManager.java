package com.petal.browser.compose.downloads;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * LiveUpdateNotificationManager
 * Handles Android 16 (API 36) Notification.ProgressStyle live updates / promoted notifications
 * with runtime capability verification via canPostPromotedNotifications(), segment tracking for downloads/media,
 * and graceful fallback to standard NotificationCompat.Builder ongoing notifications for API < 36 and all OEM devices.
 */
public class LiveUpdateNotificationManager {

    private static final String TAG = "LiveUpdateNotifMgr";
    public static final String CHANNEL_ID = "petal_live_downloads";
    public static final String CHANNEL_NAME = "Live Downloader & Alerts";

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

            // Reflection check for NotificationManager.canPostPromotedNotifications() or canUsePromotedNotifications()
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

            // Fallback for Android 16 API 36 preview/final builds: check general notification authorization
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return nm.areNotificationsEnabled();
            }
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Error checking promoted notification capability: " + e.getMessage());
            return false;
        }
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
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Live real-time alerts for active downloads and media with progress, velocity, and controls");
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Builds live update notification for download progress or media streaming,
     * utilizing Android 16 Notification.ProgressStyle when supported/enabled,
     * or standard ongoing NotificationCompat with live chips as fallback.
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

        // Try building Android 16 native Notification.ProgressStyle via reflection if API >= 36 and capable
        if (canPostPromotedNotifications(context)) {
            Notification nativeNotif = buildAndroid16ProgressStyleNotification(
                    context, title, contentText, progressPercent, isIndeterminate, chipText, contentPendingIntent, cancelPendingIntent
            );
            if (nativeNotif != null) {
                return nativeNotif;
            }
        }

        // Cross-device backward-compatible NotificationCompat builder for Android 15 & below / OEM fallbacks
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_download)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(chipText)
                .setOngoing(!isPaused)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
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

        if (isIndeterminate) {
            builder.setProgress(0, 0, true);
        } else {
            builder.setProgress(100, Math.max(0, Math.min(100, progressPercent)), false);
        }

        // Attach live alert metadata extras safely
        Bundle extras = new Bundle();
        extras.putString("android.liveAlertText", chipText);
        extras.putBoolean("android.isLiveAlert", true);
        extras.putBoolean("android.promotedOngoing", !isPaused);
        extras.putString("android.shortCriticalText", chipText);
        builder.setExtras(extras);

        try {
            Method setShortCriticalText = builder.getClass().getMethod("setShortCriticalText", CharSequence.class);
            setShortCriticalText.invoke(builder, chipText);
        } catch (Exception ignored) {}

        return builder.build();
    }

    /**
     * Uses reflection to build API 36 (Android 16) Notification.ProgressStyle with segment tracking support,
     * setting setOngoing(true), setShortCriticalText(), and promoted ongoing status bar chip flags.
     */
    private static Notification buildAndroid16ProgressStyleNotification(
            Context context,
            String title,
            String contentText,
            int progressPercent,
            boolean isIndeterminate,
            String chipText,
            PendingIntent contentPendingIntent,
            PendingIntent cancelPendingIntent
    ) {
        try {
            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.icon_download)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(contentPendingIntent);

            // Attempt to construct android.app.Notification.ProgressStyle
            Class<?> progressStyleClass = Class.forName("android.app.Notification$ProgressStyle");
            Constructor<?> styleConstructor = progressStyleClass.getConstructor();
            Object progressStyle = styleConstructor.newInstance();

            // Set progress or segment tracking if methods are present
            if (isIndeterminate) {
                Method setIndeterminateMethod = progressStyleClass.getMethod("setIndeterminate", boolean.class);
                setIndeterminateMethod.invoke(progressStyle, true);
            } else {
                Method setProgressMethod = progressStyleClass.getMethod("setProgress", int.class, int.class);
                setProgressMethod.invoke(progressStyle, 100, progressPercent);
            }

            // Set short critical live text / chip text if supported on ProgressStyle
            try {
                Method setProgressTrackerText = progressStyleClass.getMethod("setProgressTrackerText", CharSequence.class);
                setProgressTrackerText.invoke(progressStyle, chipText);
            } catch (NoSuchMethodException ignored) {}

            try {
                Method setShortCriticalTextMethod = progressStyleClass.getMethod("setShortCriticalText", CharSequence.class);
                setShortCriticalTextMethod.invoke(progressStyle, chipText);
            } catch (NoSuchMethodException ignored) {}

            // Apply style to Notification.Builder
            Method setStyleMethod = Notification.Builder.class.getMethod("setStyle", Notification.Style.class);
            setStyleMethod.invoke(builder, progressStyle);

            // Set short critical text directly on Notification.Builder if supported
            try {
                Method setShortCriticalText = Notification.Builder.class.getMethod("setShortCriticalText", CharSequence.class);
                setShortCriticalText.invoke(builder, chipText);
            } catch (NoSuchMethodException ignored) {}

            // Set Promoted flag if method exists
            try {
                Method setPromotedMethod = Notification.Builder.class.getMethod("setPromoted", boolean.class);
                setPromotedMethod.invoke(builder, true);
            } catch (NoSuchMethodException ignored) {}

            try {
                Method setPromotedOngoing = Notification.Builder.class.getMethod("setPromotedOngoing", boolean.class);
                setPromotedOngoing.invoke(builder, true);
            } catch (NoSuchMethodException ignored) {}

            // Add extras for Promoted Ongoing & Live Alert chips
            Bundle extras = new Bundle();
            extras.putString("android.liveAlertText", chipText);
            extras.putBoolean("android.isLiveAlert", true);
            extras.putBoolean("android.promotedOngoing", true);
            extras.putString("android.shortCriticalText", chipText);
            builder.addExtras(extras);

            // Add actions
            if (cancelPendingIntent != null) {
                Notification.Action cancelAction = new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.icon_close),
                        "Cancel",
                        cancelPendingIntent
                ).build();
                builder.addAction(cancelAction);
            }

            return builder.build();
        } catch (Exception e) {
            Log.d(TAG, "Android 16 ProgressStyle creation via reflection failed, using NotificationCompat fallback: " + e.getMessage());
            return null;
        }
    }
}
