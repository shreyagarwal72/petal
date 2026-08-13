package com.petal.browser.compose.incognito

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.app.NotificationCompat
import com.petal.browser.R

object PetalIncognitoSessionManager {
    const val ACTION_CLOSE_INCOGNITO = "com.petal.browser.ACTION_CLOSE_INCOGNITO_TABS"
    private const val NOTIFICATION_ID = 4001
    private const val CHANNEL_ID = "petal_incognito_session_channel"

    private var activeIncognitoTabCount = 0

    @JvmStatic
    fun onIncognitoTabOpened(activity: Activity) {
        activeIncognitoTabCount++
        updateIncognitoState(activity)
    }

    @JvmStatic
    fun onIncognitoTabClosed(activity: Activity) {
        if (activeIncognitoTabCount > 0) {
            activeIncognitoTabCount--
        }
        updateIncognitoState(activity)
    }

    @JvmStatic
    fun setIncognitoTabCount(activity: Activity, count: Int) {
        activeIncognitoTabCount = count.coerceAtLeast(0)
        updateIncognitoState(activity)
    }

    @JvmStatic
    fun isIncognitoActive(): Boolean = activeIncognitoTabCount > 0

    @JvmStatic
    fun updateIncognitoState(activity: Activity) {
        if (activeIncognitoTabCount > 0) {
            enableIncognitoSecurity(activity)
            showIncognitoNotification(activity)
        } else {
            disableIncognitoSecurity(activity)
            dismissIncognitoNotification(activity)
            flushSessionData(activity)
        }
    }

    @JvmStatic
    fun enableIncognitoSecurity(activity: Activity) {
        try {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun disableIncognitoSecurity(activity: Activity) {
        try {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun flushSessionData(context: Context) {
        try {
            WebStorage.getInstance().deleteAllData()
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showIncognitoNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Incognito Session",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent status notification when Incognito tabs are open"
                }
                nm.createNotificationChannel(channel)
            }

            val closeIntent = Intent(context, com.petal.browser.activity.BrowserActivity::class.java).apply {
                action = ACTION_CLOSE_INCOGNITO
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(context, 0, closeIntent, pendingIntentFlags)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_incognito)
                .setContentTitle("Close all Incognito tabs")
                .setContentText("$activeIncognitoTabCount private tab${if (activeIncognitoTabCount > 1) "s" else ""} active. Tap to close all.")
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun dismissIncognitoNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
