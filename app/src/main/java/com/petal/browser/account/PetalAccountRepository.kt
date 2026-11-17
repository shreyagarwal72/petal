package com.petal.browser.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color

/** What the account page shows: the local profile plus whether folder sync is set up on this device. */
data class PetalAccount(
    val profile: PetalUserProfile,
    val title: String,
    val subtitle: String,
    val syncConfigured: Boolean,
    val accent: Color?
)

object PetalAccountRepository {

    /** Pure merge of the local profile and the sync state; no side effects. */
    fun resolve(profile: PetalUserProfile, syncConfigured: Boolean, folderName: String?): PetalAccount {
        val subtitle = when {
            syncConfigured && !folderName.isNullOrBlank() -> "Syncing with $folderName"
            syncConfigured -> "Sync is set up"
            else -> "Local profile · sync is off"
        }
        return PetalAccount(
            profile = profile,
            title = profile.displayName,
            subtitle = subtitle,
            syncConfigured = syncConfigured,
            accent = parseAccent(profile.accentColorHex)
        )
    }

    fun parseAccent(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /** Opens a picked .psync file in the import/restore dialog. */
    fun openSyncFile(context: Context, uri: android.net.Uri): Boolean {
        val activity = context.findActivity() as? com.petal.browser.activity.MainActivity ?: return false
        com.petal.browser.account.sync.PetalSyncImport.handle(activity, uri)
        return true
    }

    /**
     * Opens the App Lock settings page as its own decor overlay with its own predictive-back surface
     * (the same path the Java side uses), so it never nests inside another screen's back handling.
     */
    fun openAppLock(context: Context): Boolean {
        val activity = context.findActivity() ?: return false
        com.petal.browser.compose.security.PetalAppLockBridge.showConfig(activity, Runnable { })
        return true
    }
}
