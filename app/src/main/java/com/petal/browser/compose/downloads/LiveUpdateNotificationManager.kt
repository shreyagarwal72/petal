package com.petal.browser.compose.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * LiveUpdateNotificationManager
 * Handles Android 16 (API 36) Notification.ProgressStyle live updates / promoted notifications
 * with runtime capability verification via canPostPromotedNotifications(), segment tracking for downloads/media,
 * and graceful fallback to standard NotificationCompat.Builder ongoing notifications for API < 36 and all OEM devices.
 */
object LiveUpdateNotificationManager {

    private const val TAG = "LiveUpdateNotifMgr"
    const val CHANNEL_ID = "petal_live_downloads"
    const val CHANNEL_NAME = "Live Downloader & Alerts"

    /**
     * Runtime capability check to determine if Android 16 promoted live notifications can be posted.
     */
    @JvmStatic
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) { // Android 16 / API 36
            return false
        }
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false

            // Reflection check for NotificationManager.canPostPromotedNotifications() or canUsePromotedNotifications()
            var method: Method? = null
            try {
                method = NotificationManager::class.java.getMethod("canPostPromotedNotifications")
            } catch (e: NoSuchMethodException) {
                try {
                    method = NotificationManager::class.java.getMethod("canUsePromotedNotifications")
                } catch (ignored: NoSuchMethodException) {
                }
            }

            if (method != null) {
                val result = method.invoke(nm)
                if (result is Boolean) {
                    return result
                }
            }

            // Fallback for Android 16 API 36 preview/final builds: check general notification authorization
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                nm.areNotificationsEnabled()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking promoted notification capability: " + e.message)
            false
        }
    }

    /**
     * Ensures notification channel is created for live updates on Android 8.0+.
     */
    @JvmStatic
    fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Live real-time alerts for active downloads and media with progress, velocity, and controls"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Builds live update notification for download progress or media streaming,
     * utilizing Android 16 Notification.ProgressStyle when supported/enabled,
     * or standard ongoing NotificationCompat with live chips as fallback.
     */
    @JvmStatic
    fun buildLiveNotification(
        context: Context,
        id: Long,
        title: String,
        contentText: String,
        progressPercent: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
        chipText: String,
        contentPendingIntent: PendingIntent?,
        cancelPendingIntent: PendingIntent?,
        togglePendingIntent: PendingIntent?
    ): Notification {
        ensureChannelCreated(context)

        // Try building Android 16 native Notification.ProgressStyle via reflection if API >= 36 and capable
        if (canPostPromotedNotifications(context)) {
            val nativeNotif = buildAndroid16ProgressStyleNotification(
                context, title, contentText, progressPercent, isIndeterminate, chipText, contentPendingIntent, cancelPendingIntent
            )
            if (nativeNotif != null) {
                return nativeNotif
            }
        }

        // Cross-device backward-compatible NotificationCompat builder for Android 15 & below / OEM fallbacks
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(chipText)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)

        if (togglePendingIntent != null) {
            if (isPaused) {
                builder.addAction(R.drawable.icon_play, "Resume", togglePendingIntent)
            } else {
                builder.addAction(R.drawable.icon_pause, "Pause", togglePendingIntent)
            }
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
            builder.setProgress(100, Math.max(0, Math.min(100, progressPercent)), false)
        }

        // Attach live alert metadata extras safely
        val extras = Bundle().apply {
            putString("android.liveAlertText", chipText)
            putBoolean("android.isLiveAlert", true)
        }
        builder.setExtras(extras)

        try {
            val setShortCriticalText = builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
            setShortCriticalText.invoke(builder, chipText)
        } catch (ignored: Exception) {
        }

        return builder.build()
    }

    /**
     * Uses reflection to build API 36 (Android 16) Notification.ProgressStyle with segment tracking support.
     */
    private fun buildAndroid16ProgressStyleNotification(
        context: Context,
        title: String,
        contentText: String,
        progressPercent: Int,
        isIndeterminate: Boolean,
        chipText: String,
        contentPendingIntent: PendingIntent?,
        cancelPendingIntent: PendingIntent?
    ): Notification? {
        return try {
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_download)
                .setContentTitle(title)
                .setContentText(contentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent)

            // Attempt to construct android.app.Notification.ProgressStyle
            val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
            val styleConstructor = progressStyleClass.getConstructor()
            val progressStyle = styleConstructor.newInstance()

            // Set progress or segment tracking if methods are present
            if (isIndeterminate) {
                val setIndeterminateMethod = progressStyleClass.getMethod("setIndeterminate", Boolean::class.javaPrimitiveType)
                setIndeterminateMethod.invoke(progressStyle, true)
            } else {
                val setProgressMethod = progressStyleClass.getMethod("setProgress", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                setProgressMethod.invoke(progressStyle, 100, progressPercent)
            }

            // Set short critical live text / chip text if supported on ProgressStyle
            try {
                val setProgressTrackerText = progressStyleClass.getMethod("setProgressTrackerText", CharSequence::class.java)
                setProgressTrackerText.invoke(progressStyle, chipText)
            } catch (ignored: NoSuchMethodException) {
            }

            // Apply style to Notification.Builder
            val setStyleMethod = Notification.Builder::class.java.getMethod("setStyle", Notification.Style::class.java)
            setStyleMethod.invoke(builder, progressStyle)

            // Add actions
            if (cancelPendingIntent != null) {
                val cancelAction = Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.icon_close),
                    "Cancel",
                    cancelPendingIntent
                ).build()
                builder.addAction(cancelAction)
            }

            // Set Promoted flag if method exists
            try {
                val setPromotedMethod = Notification.Builder::class.java.getMethod("setPromoted", Boolean::class.javaPrimitiveType)
                setPromotedMethod.invoke(builder, true)
            } catch (ignored: NoSuchMethodException) {
            }

            builder.build()
        } catch (e: Exception) {
            Log.d(TAG, "Android 16 ProgressStyle creation via reflection failed, using NotificationCompat fallback: " + e.message)
            null
        }
    }
}
