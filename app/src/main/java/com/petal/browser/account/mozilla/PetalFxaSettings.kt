package com.petal.browser.account.mozilla

import android.content.Context
import android.os.Build

/**
 * Non-secret Firefox Sync preferences. They live in "petal_sync_settings", which the backup manager
 * includes in backups. Credentials never live here: the official FxaAccountManager keeps account state in
 * its own storage, which is deliberately excluded from backups (a restored sign-in would be invalid anyway).
 */
object PetalFxaSettings {
    private const val PREFS = "petal_sync_settings"
    const val KEY_BOOKMARKS = "engine_bookmarks"
    const val KEY_HISTORY = "engine_history"
    const val KEY_TABS = "engine_tabs"
    const val KEY_PASSWORDS = "engine_passwords"
    const val KEY_DEVICE_NAME = "device_name"
    const val KEY_LAST_SYNC = "last_sync"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bookmarks(c: Context) = p(c).getBoolean(KEY_BOOKMARKS, true)
    fun history(c: Context) = p(c).getBoolean(KEY_HISTORY, true)
    fun setBookmarks(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_BOOKMARKS, v).apply()
    fun setHistory(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_HISTORY, v).apply()
    fun tabs(c: Context) = p(c).getBoolean(KEY_TABS, true)
    fun setTabs(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_TABS, v).apply()
    /** Off until the user turns it on: passwords only leave the device when explicitly chosen. */
    fun passwords(c: Context) = p(c).getBoolean(KEY_PASSWORDS, false)
    fun setPasswords(c: Context, v: Boolean) = p(c).edit().putBoolean(KEY_PASSWORDS, v).apply()
    fun deviceName(c: Context): String =
        p(c).getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() } ?: "Petal on ${Build.MODEL}"
    fun setDeviceName(c: Context, v: String) = p(c).edit().putString(KEY_DEVICE_NAME, v.trim()).apply()
    fun lastSync(c: Context) = p(c).getLong(KEY_LAST_SYNC, 0L)
    fun setLastSync(c: Context, t: Long) = p(c).edit().putLong(KEY_LAST_SYNC, t).apply()
}
