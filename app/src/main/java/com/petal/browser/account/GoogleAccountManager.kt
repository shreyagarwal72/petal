package com.petal.browser.account

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    // Used only to fetch the signed-in user's real name/email/avatar from Google
    // once an auth cookie is detected - never used for the download engine etc.
    private val profileHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val profileFetchExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var isFetchingProfile = false

    @JvmStatic
    fun checkAndSyncGoogleAccount(context: Context) {
        val appContext = context.applicationContext ?: context
        try {
            val cookieManager = android.webkit.CookieManager.getInstance() ?: return
            val cookieHeader = try {
                cookieManager.getCookie("https://accounts.google.com") ?: ""
            } catch (e: Exception) {
                ""
            }

            // Check for Google login authentication cookies (SID, HSID, SSID, OSID, SAPISID)
            val hasAuthCookie = cookieHeader.contains("SID=") || cookieHeader.contains("OSID=") ||
                cookieHeader.contains("SAPISID=") || cookieHeader.contains("SSID=")
            if (!hasAuthCookie) return

            // Refresh profile if signed out OR if signed in with placeholder data / missing avatar
            if (currentProfile.isSignedIn && !currentProfile.avatarUrl.isNullOrEmpty() && !currentProfile.email.endsWith("user@gmail.com")) return
            if (isFetchingProfile) return

            fetchRealGoogleProfile(appContext, cookieHeader)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Fetches the *actual* signed-in Google account's name, email and avatar
     * using the same cookie-based account-status endpoint Chromium itself uses
     * for its account-consistency ("Gaia") checks, rather than fabricating
     * placeholder values. Runs entirely off the main thread; applies the
     * result (if any) back on the main thread via [signIn].
     */
    private fun fetchRealGoogleProfile(context: Context, cookieHeader: String) {
        isFetchingProfile = true
        profileFetchExecutor.execute {
            try {
                val request = Request.Builder()
                    .url("https://accounts.google.com/ListAccounts?gpsia=1&source=ChromiumBrowser&json=standard")
                    .header("Cookie", cookieHeader)
                    .build()

                profileHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful || body.isBlank()) return@use

                    // Response is prefixed with an XSSI-protection token, e.g. )]}'
                    val jsonText = body.substring(body.indexOf('[').coerceAtLeast(0))
                    val account = parseFirstAccount(jsonText)
                    if (account != null) {
                        mainHandler.post {
                            signIn(context, account.email, account.displayName, account.avatarUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isFetchingProfile = false
            }
        }
    }

    private data class ParsedGoogleAccount(
        val email: String,
        val displayName: String,
        val avatarUrl: String?
    )

    private fun parseFirstAccount(json: String): ParsedGoogleAccount? {
        return try {
            val root = JSONArray(json)
            val strings = mutableListOf<String>()
            collectStrings(root, strings)

            val email = strings.firstOrNull { it.contains("@") && it.contains(".") && !it.startsWith("http") }
                ?: return null

            val avatarUrl = strings.firstOrNull {
                it.startsWith("http") && (it.contains("googleusercontent") || it.contains("photo"))
            } ?: strings.firstOrNull { it.startsWith("http") }

            val displayName = strings.firstOrNull { candidate ->
                candidate != email && candidate != avatarUrl &&
                    !candidate.startsWith("http") && !candidate.startsWith("gaia.") &&
                    candidate.contains(" ") && candidate.any { it.isLetter() } &&
                    candidate.length in 2..60
            } ?: email.substringBefore("@")

            ParsedGoogleAccount(email = email, displayName = displayName, avatarUrl = avatarUrl)
        } catch (e: Exception) {
            null
        }
    }

    private fun collectStrings(value: Any?, out: MutableList<String>) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) collectStrings(value.opt(i), out)
            is String -> if (value.isNotBlank()) out.add(value)
        }
    }
}
