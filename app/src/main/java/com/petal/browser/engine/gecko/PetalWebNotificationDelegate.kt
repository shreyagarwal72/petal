package com.petal.browser.engine.gecko

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalWebNotificationDelegate
 *
 * Implements Mozilla GeckoView's WebNotificationDelegate to display web notifications
 * triggered by websites (W3C Notifications API / Web Push) via Android's native NotificationManager.
 */
class PetalWebNotificationDelegate(private val context: Context) : WebNotificationDelegate {

    companion object {
        private const val TAG = "PetalWebNotification"
        const val CHANNEL_ID = "petal_web_notifications"
        const val ACTION_CLICK = "com.petal.browser.action.WEB_NOTIFICATION_CLICK"
        const val ACTION_DISMISS = "com.petal.browser.action.WEB_NOTIFICATION_DISMISS"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private val activeNotifications = ConcurrentHashMap<Int, WebNotification>()

        @JvmStatic
        fun handleClick(notificationId: Int) {
            val notification = activeNotifications.remove(notificationId)
            try {
                notification?.click()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed calling notification.click(): ${t.message}")
            }
        }

        @JvmStatic
        fun handleDismiss(notificationId: Int) {
            val notification = activeNotifications.remove(notificationId)
            try {
                notification?.dismiss()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed calling notification.dismiss(): ${t.message}")
            }
        }
    }

    init {
        ensureNotificationChannel(context)
    }

    private fun ensureNotificationChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Web Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications from websites and progressive web apps"
                    enableLights(true)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    override fun onShowNotification(notification: WebNotification) {
        try {
            val nm = NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) {
                Log.d(TAG, "System notifications disabled; skipping web notification")
                return
            }

            val tag = notification.tag
            val id = tag?.hashCode() ?: notification.hashCode()
            activeNotifications[id] = notification

            val clickIntent = Intent(context, WebNotificationActionReceiver::class.java).apply {
                action = ACTION_CLICK
                putExtra(EXTRA_NOTIFICATION_ID, id)
            }
            val clickPending = PendingIntent.getBroadcast(
                context,
                id,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, WebNotificationActionReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_NOTIFICATION_ID, id)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context,
                id + 100000,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val titleStr = notification.title
            val displayTitle = if (!titleStr.isNullOrBlank()) titleStr else "Web Notification"
            val textStr = notification.text
            val sourceStr = runCatching { notification.source }.getOrNull()
            val displayText = if (!textStr.isNullOrBlank()) textStr else (sourceStr ?: "")

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_live_notification)
                .setContentTitle(displayTitle)
                .setContentText(displayText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
                .setAutoCancel(true)
                .setContentIntent(clickPending)
                .setDeleteIntent(dismissPending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            // Notify GeckoView that notification is being displayed if show() exists
            runCatching {
                val showMethod = notification.javaClass.getMethod("show")
                showMethod.invoke(notification)
            }

            nm.notify(tag, id, builder.build())
            Log.i(TAG, "Displayed web notification: $displayTitle (id=$id)")
        } catch (t: Throwable) {
            Log.e(TAG, "Error displaying web notification: ${t.message}", t)
        }
    }

    override fun onCloseNotification(notification: WebNotification) {
        try {
            val tag = notification.tag
            val id = tag?.hashCode() ?: notification.hashCode()
            activeNotifications.remove(id)
            val nm = NotificationManagerCompat.from(context)
            if (tag != null) {
                nm.cancel(tag, id)
            } else {
                nm.cancel(id)
            }
            Log.i(TAG, "Closed web notification id=$id")
        } catch (t: Throwable) {
            Log.e(TAG, "Error closing web notification: ${t.message}", t)
        }
    }
}

class WebNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(PetalWebNotificationDelegate.EXTRA_NOTIFICATION_ID, 0)
        when (intent.action) {
            PetalWebNotificationDelegate.ACTION_CLICK -> {
                PetalWebNotificationDelegate.handleClick(id)
                // Bring browser to foreground
                val launchIntent = Intent(context, BrowserActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(launchIntent)
            }
            PetalWebNotificationDelegate.ACTION_DISMISS -> {
                PetalWebNotificationDelegate.handleDismiss(id)
            }
        }
    }
}
