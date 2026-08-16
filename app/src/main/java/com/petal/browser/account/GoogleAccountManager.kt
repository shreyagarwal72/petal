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

enum class AvatarType { PRESET, GALLERY_URI, GOOGLE_URL }

data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarType: AvatarType = AvatarType.PRESET,
    val avatarPresetId: String = "app_icon",
    val customAvatarUri: String? = null,
    val isSignedIn: Boolean = false,
    val globalGoogleLogin: Boolean = true,
    val syncBookmarks: Boolean = true,
    val syncHistory: Boolean = true,
    val syncPasswords: Boolean = true,
    val syncOpenTabs: Boolean = true,
    val syncSearchEngines: Boolean = true
)

object GoogleAccountManager {

    private const val KEY_IS_SIGNED_IN = "sp_google_account_signed_in"
    private const val KEY_EMAIL = "sp_google_account_email"
    private const val KEY_DISPLAY_NAME = "sp_google_account_display_name"
    private const val KEY_AVATAR_URL = "sp_google_account_avatar_url"
    private const val KEY_AVATAR_TYPE = "sp_user_avatar_type"
    private const val KEY_AVATAR_PRESET = "sp_user_avatar_preset"
    private const val KEY_CUSTOM_AVATAR_URI = "sp_user_custom_avatar_uri"
    private const val KEY_GLOBAL_GOOGLE_LOGIN = "sp_global_google_login"
    private const val KEY_SYNC_BOOKMARKS = "sp_google_sync_bookmarks"
    private const val KEY_SYNC_HISTORY = "sp_google_sync_history"
    private const val KEY_SYNC_PASSWORDS = "sp_google_sync_passwords"
    private const val KEY_SYNC_TABS = "sp_google_sync_tabs"
    private const val KEY_SYNC_SEARCH_ENGINES = "sp_google_sync_search_engines"

    val builtinAvatarPresets = listOf(
        "app_icon" to "App Icon (Default)",
        "petal_flower" to "Petal",
        "cosmic_star" to "Cosmic Star",
        "cyber_shield" to "Cyber Shield",
        "rocket_boost" to "Rocket",
        "ocean_wave" to "Ocean",
        "ninja_cat" to "Ninja",
        "sparkle" to "Sparkles",
        "bot_avatar" to "Cyber Bot"
    )

    var currentProfile by mutableStateOf(
        GoogleUserProfile(
            email = "user@petalbrowser.org",
            displayName = "Petal Explorer",
            isSignedIn = false
        )
    )
        private set

    fun init(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val isSignedIn = sp.getBoolean(KEY_IS_SIGNED_IN, false)
            val email = sp.getString(KEY_EMAIL, "user@petalbrowser.org") ?: "user@petalbrowser.org"
            val displayName = sp.getString(KEY_DISPLAY_NAME, "Petal Explorer") ?: "Petal Explorer"
            val avatarUrl = sp.getString(KEY_AVATAR_URL, null)
            val avatarTypeStr = sp.getString(KEY_AVATAR_TYPE, AvatarType.PRESET.name) ?: AvatarType.PRESET.name
            val avatarType = try { AvatarType.valueOf(avatarTypeStr) } catch (_: Throwable) { AvatarType.PRESET }
            val avatarPresetId = sp.getString(KEY_AVATAR_PRESET, "petal_flower") ?: "petal_flower"
            val customAvatarUri = sp.getString(KEY_CUSTOM_AVATAR_URI, null)
            val globalGoogleLogin = sp.getBoolean(KEY_GLOBAL_GOOGLE_LOGIN, true)
            val syncBookmarks = sp.getBoolean(KEY_SYNC_BOOKMARKS, true)
            val syncHistory = sp.getBoolean(KEY_SYNC_HISTORY, true)
            val syncPasswords = sp.getBoolean(KEY_SYNC_PASSWORDS, true)
            val syncTabs = sp.getBoolean(KEY_SYNC_TABS, true)
            val syncSearchEngines = sp.getBoolean(KEY_SYNC_SEARCH_ENGINES, true)

            currentProfile = GoogleUserProfile(
                email = email,
                displayName = displayName,
                avatarUrl = avatarUrl,
                avatarType = avatarType,
                avatarPresetId = avatarPresetId,
                customAvatarUri = customAvatarUri,
                isSignedIn = isSignedIn,
                globalGoogleLogin = globalGoogleLogin,
                syncBookmarks = syncBookmarks,
                syncHistory = syncHistory,
                syncPasswords = syncPasswords,
                syncOpenTabs = syncTabs,
                syncSearchEngines = syncSearchEngines
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun updateDisplayName(context: Context, newName: String) {
        try {
            val trimmed = newName.trim().ifEmpty { "Petal Explorer" }
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
            currentProfile = currentProfile.copy(displayName = trimmed)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun updateAvatarPreset(context: Context, presetId: String) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putString(KEY_AVATAR_TYPE, AvatarType.PRESET.name)
                .putString(KEY_AVATAR_PRESET, presetId)
                .apply()
            currentProfile = currentProfile.copy(
                avatarType = AvatarType.PRESET,
                avatarPresetId = presetId
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun updateAvatarGalleryUri(context: Context, uriString: String) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putString(KEY_AVATAR_TYPE, AvatarType.GALLERY_URI.name)
                .putString(KEY_CUSTOM_AVATAR_URI, uriString)
                .apply()
            currentProfile = currentProfile.copy(
                avatarType = AvatarType.GALLERY_URI,
                customAvatarUri = uriString
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun setGlobalGoogleLogin(context: Context, enabled: Boolean) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().putBoolean(KEY_GLOBAL_GOOGLE_LOGIN, enabled).apply()
            currentProfile = currentProfile.copy(globalGoogleLogin = enabled)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun signIn(context: Context, email: String, displayName: String, avatarUrl: String? = null) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val effectiveAvatarType = if (!avatarUrl.isNullOrEmpty()) AvatarType.GOOGLE_URL else currentProfile.avatarType
            sp.edit()
                .putBoolean(KEY_IS_SIGNED_IN, true)
                .putString(KEY_EMAIL, email)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .putString(KEY_AVATAR_TYPE, effectiveAvatarType.name)
                .apply()

            currentProfile = currentProfile.copy(
                email = email,
                displayName = displayName,
                avatarUrl = avatarUrl,
                avatarType = effectiveAvatarType,
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
        syncPasswords: Boolean,
        syncTabs: Boolean,
        syncSearchEngines: Boolean
    ) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putBoolean(KEY_SYNC_BOOKMARKS, syncBookmarks)
                .putBoolean(KEY_SYNC_HISTORY, syncHistory)
                .putBoolean(KEY_SYNC_PASSWORDS, syncPasswords)
                .putBoolean(KEY_SYNC_TABS, syncTabs)
                .putBoolean(KEY_SYNC_SEARCH_ENGINES, syncSearchEngines)
                .apply()

            currentProfile = currentProfile.copy(
                syncBookmarks = syncBookmarks,
                syncHistory = syncHistory,
                syncPasswords = syncPasswords,
                syncOpenTabs = syncTabs,
                syncSearchEngines = syncSearchEngines
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
