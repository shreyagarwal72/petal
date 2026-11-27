package com.petal.browser.compose.incognito

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.petal.browser.R

object PetalIncognitoSessionManager {
    const val ACTION_CLOSE_INCOGNITO = "com.petal.browser.ACTION_CLOSE_INCOGNITO_TABS"
    private const val NOTIFICATION_ID = 4001
    private const val CHANNEL_ID = "petal_incognito_session_channel"

    private var activeIncognitoTabCount = 0
    private var hadIncognitoTabs = false

    @JvmStatic
    fun onIncognitoTabOpened(activity: Activity) {
        activeIncognitoTabCount++
        hadIncognitoTabs = true
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
        if (activeIncognitoTabCount > 0) {
            hadIncognitoTabs = true
        }
        updateIncognitoState(activity)
    }

    @JvmStatic
    fun getIncognitoCount(context: Context? = null): Int {
        val countFromStore = try {
            val ctx = context ?: com.petal.browser.PetalApplication.instance
            if (ctx != null) {
                com.petal.browser.engine.gecko.PetalEngineStore.getStore(ctx).state.tabs.count { it.content.private }
            } else 0
        } catch (_: Throwable) {
            0
        }
        return Math.max(countFromStore, activeIncognitoTabCount)
    }

    @JvmStatic
    fun isIncognitoActive(context: Context? = null): Boolean = getIncognitoCount(context) > 0

    @JvmStatic
    fun updateIncognitoState(activity: Activity) {
        val incognitoCount = getIncognitoCount(activity)
        if (incognitoCount > 0) {
            hadIncognitoTabs = true
            enableIncognitoSecurity(activity)
            showIncognitoNotification(activity)
        } else {
            disableIncognitoSecurity(activity)
            dismissIncognitoNotification(activity)
            if (hadIncognitoTabs) {
                flushSessionData(activity)
                hadIncognitoTabs = false
            }
        }
    }

    @JvmStatic
    fun syncIncognitoState(context: Context) {
        val incognitoCount = getIncognitoCount(context)
        val fgAct = (context as? Activity) ?: com.petal.browser.engine.gecko.PetalGeckoRuntime.getForegroundActivity()
        if (incognitoCount > 0) {
            hadIncognitoTabs = true
            if (fgAct != null) enableIncognitoSecurity(fgAct)
            showIncognitoNotification(context)
        } else {
            if (fgAct != null) {
                disableIncognitoSecurity(fgAct)
            }
            dismissIncognitoNotification(context)
            if (hadIncognitoTabs) {
                flushSessionData(context)
                hadIncognitoTabs = false
            }
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
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .remove("sp_incognito_search_history_queries")
                .remove("sp_incognito_search_history_list")
                .apply()

            // NOTE: never call StorageController.clearData() here. It targets the shared runtime
            // (and the normal profile), so it would wipe the user's regular cookies, logins and
            // site storage. Gecko discards private-session data itself when the private
            // sessions close; only Petal-owned state is cleaned below.

            // Purge private cookie cache in PetalGeckoCookieBridge
            com.petal.browser.download.PetalGeckoCookieBridge.clearPrivateCookies()

            // Immediately purge any in-memory private tab thumbnails (Firefox parity)
            com.petal.browser.unit.TabThumbnailCache.clearPrivateCache()

            // Clear session-scoped private adblock exceptions/stats, site permissions, and HTTPS-Only bypasses
            com.petal.browser.browser.PetalAdBlockEngine.clearPrivateSessionData()
            com.petal.browser.engine.gecko.PetalSitePermissionsBridge.clearSessionGrants(isPrivate = true)
            com.petal.browser.privacy.PetalHttpsOnlyWhitelist.clearPrivateSessionData()
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
                    description = "Status notification when Incognito tabs are active"
                }
                nm.createNotificationChannel(channel)
            }

            val closeIntent = Intent(context, com.petal.browser.activity.MainActivity::class.java).apply {
                action = ACTION_CLOSE_INCOGNITO
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(context, 0, closeIntent, pendingIntentFlags)

            val incognitoCount = getIncognitoCount(context)
            val contentText = if (incognitoCount > 1) {
                "$incognitoCount Incognito tabs open. Tap to close all."
            } else {
                "1 Incognito tab open. Tap to close."
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_incognito)
                .setContentTitle("Incognito Mode Active")
                .setContentText(contentText)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()

            notification.flags = notification.flags or android.app.Notification.FLAG_ONGOING_EVENT or android.app.Notification.FLAG_NO_CLEAR

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
