package com.petal.browser.island

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager

/**
 * Everything the live island needs from the user, in one place.
 *
 *  - Notifications           (Android 13+)  : download / media notifications, and Android 16 chips
 *  - Display over other apps (all versions) : only for the over-other-apps island toggle
 *
 * No accessibility service, notification-listener or other broad access is needed.
 */
object PetalIslandPermissions {

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Opens the exact "Display over other apps" screen for Petal. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when this device shows Android 16 promoted Live Updates (status bar chip). */
    fun nativeLiveUpdatesAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 36 && LiveUpdateNotificationManager.canPostPromotedNotifications(context)

    /**
     * Whether the island should be drawn at all.
     *  AUTO            -> only where the system has no native Live Update chip
     *  ALWAYS_ISLAND   -> everywhere, alongside the native chip when there is one
     *  NOTIFICATION_ONLY -> never
     */
    fun shouldShowIsland(context: Context, settings: IslandSettings): Boolean {
        if (!settings.liveUpdates) return false
        return when (settings.mode) {
            IslandSettings.MODE_NOTIFICATION_ONLY -> false
            IslandSettings.MODE_ALWAYS -> true
            else -> !nativeLiveUpdatesAvailable(context)
        }
    }
}
