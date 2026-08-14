package com.petal.browser.account

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isSignedIn: Boolean = false,
    val syncBookmarks: Boolean = true,
    val syncHistory: Boolean = true,
    val syncPasswords: Boolean = true
)

object GoogleAccountManager {

    private const val KEY_IS_SIGNED_IN = "sp_google_account_signed_in"
    private const val KEY_EMAIL = "sp_google_account_email"
    private const val KEY_DISPLAY_NAME = "sp_google_account_display_name"
    private const val KEY_AVATAR_URL = "sp_google_account_avatar_url"
    private const val KEY_SYNC_BOOKMARKS = "sp_google_sync_bookmarks"
    private const val KEY_SYNC_HISTORY = "sp_google_sync_history"
    private const val KEY_SYNC_PASSWORDS = "sp_google_sync_passwords"

    var currentProfile by mutableStateOf(
        GoogleUserProfile(
            email = "user@gmail.com",
            displayName = "Google User",
            isSignedIn = false
        )
    )
        private set

    fun init(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val isSignedIn = sp.getBoolean(KEY_IS_SIGNED_IN, false)
            val email = sp.getString(KEY_EMAIL, "user@gmail.com") ?: "user@gmail.com"
            val displayName = sp.getString(KEY_DISPLAY_NAME, "Google User") ?: "Google User"
            val avatarUrl = sp.getString(KEY_AVATAR_URL, null)
            val syncBookmarks = sp.getBoolean(KEY_SYNC_BOOKMARKS, true)
            val syncHistory = sp.getBoolean(KEY_SYNC_HISTORY, true)
            val syncPasswords = sp.getBoolean(KEY_SYNC_PASSWORDS, true)

            currentProfile = GoogleUserProfile(
                email = email,
                displayName = displayName,
                avatarUrl = avatarUrl,
                isSignedIn = isSignedIn,
                syncBookmarks = syncBookmarks,
                syncHistory = syncHistory,
                syncPasswords = syncPasswords
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun signIn(context: Context, email: String, displayName: String, avatarUrl: String? = null) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putBoolean(KEY_IS_SIGNED_IN, true)
                .putString(KEY_EMAIL, email)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .apply()

            currentProfile = currentProfile.copy(
                email = email,
                displayName = displayName,
                avatarUrl = avatarUrl,
                isSignedIn = true
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun signOut(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putBoolean(KEY_IS_SIGNED_IN, false)
                .apply()

            currentProfile = currentProfile.copy(isSignedIn = false)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun updateSyncSettings(
        context: Context,
        syncBookmarks: Boolean,
        syncHistory: Boolean,
        syncPasswords: Boolean
    ) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putBoolean(KEY_SYNC_BOOKMARKS, syncBookmarks)
                .putBoolean(KEY_SYNC_HISTORY, syncHistory)
                .putBoolean(KEY_SYNC_PASSWORDS, syncPasswords)
                .apply()

            currentProfile = currentProfile.copy(
                syncBookmarks = syncBookmarks,
                syncHistory = syncHistory,
                syncPasswords = syncPasswords
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun checkAndSyncGoogleAccount(context: Context) {
        if (context == null) return
        try {
            val cookieManager = android.webkit.CookieManager.getInstance() ?: return
            val cookies = try {
                cookieManager.getCookie("https://accounts.google.com") ?: ""
            } catch (e: Exception) {
                ""
            }

            // Check for Google login authentication cookies (SID, HSID, SSID, OSID, SAPISID)
            val hasAuthCookie = cookies.contains("SID=") || cookies.contains("OSID=") || cookies.contains("SAPISID=") || cookies.contains("SSID=")

            if (hasAuthCookie) {
                val sp = PreferenceManager.getDefaultSharedPreferences(context)
                val existingEmail = sp.getString(KEY_EMAIL, null)
                val targetEmail = if (existingEmail.isNullOrEmpty() || existingEmail == "user@gmail.com") "google.user@gmail.com" else existingEmail
                val targetName = if (sp.getString(KEY_DISPLAY_NAME, null).isNullOrEmpty()) "Google Account User" else sp.getString(KEY_DISPLAY_NAME, "Google Account User")!!

                signIn(context, targetEmail, targetName)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
