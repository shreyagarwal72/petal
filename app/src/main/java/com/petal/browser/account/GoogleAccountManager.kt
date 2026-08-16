package com.petal.browser.account

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.preference.PreferenceManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.json.JSONObject

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

sealed class GoogleSignInResult {
    data class Success(val profile: GoogleUserProfile) : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}

object GoogleAccountManager {

    // Web application OAuth client ID from Google Cloud Console.
    // Credential Manager uses this as the audience for the ID token it requests -
    // required even though this app has no backend of its own.
    private const val WEB_CLIENT_ID =
        "755813875491-tfaor37ei7a72lc5g0ghachduetf9fj6.apps.googleusercontent.com"

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
            val trimmed = newName.trim().take(15).ifEmpty { "Petal Explorer" }
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

    private fun persistSignedInProfile(context: Context, email: String, displayName: String, avatarUrl: String?) {
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
    }

    /**
     * Launches the real Google Sign-In flow via Credential Manager. This shows Google's own
     * account picker + consent screen - the user explicitly chooses an account and approves
     * sharing their basic profile. Reads the standard OpenID claims (email, name, picture) from
     * the returned ID token to populate the account section. There is no backend for this app,
     * so the token is only decoded for display, never treated as a verified auth credential.
     *
     * Must be called with an Activity context (required by Credential Manager / Play Services
     * Auth to show the account picker UI).
     */
    suspend fun signIn(context: Context): GoogleSignInResult {
        return try {
            val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val response = credentialManager.getCredential(context, request)

            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleSignInResult.Failure("Unexpected credential type returned")
            }

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val claims = decodeIdTokenClaims(googleIdTokenCredential.idToken)

            val email = claims?.optString("email")?.takeIf { it.isNotBlank() }
                ?: return GoogleSignInResult.Failure("Google did not return an email for this account")
            val displayName = googleIdTokenCredential.displayName
                ?: claims.optString("name").takeIf { it.isNotBlank() }
                ?: email.substringBefore("@")
            val avatarUrl = googleIdTokenCredential.profilePictureUri?.toString()
                ?: claims.optString("picture").takeIf { it.isNotBlank() }

            persistSignedInProfile(context, email, displayName, avatarUrl)
            GoogleSignInResult.Success(currentProfile)
        } catch (e: GetCredentialException) {
            e.printStackTrace()
            val cleanMsg = e.message ?: ""
            if (cleanMsg.contains("No credentials", ignoreCase = true) || e is androidx.credentials.exceptions.NoCredentialsException) {
                GoogleSignInResult.Failure("No Google account found or signed into Google Play Services on this device.")
            } else if (cleanMsg.contains("16") || cleanMsg.contains("Canceled", ignoreCase = true) || cleanMsg.contains("Cancelled", ignoreCase = true) || e is androidx.credentials.exceptions.GetCredentialCancellationException) {
                GoogleSignInResult.Failure("Sign-in was cancelled.")
            } else {
                GoogleSignInResult.Failure(cleanMsg.ifBlank { "Sign-in was unavailable or failed" })
            }
        } catch (e: GoogleIdTokenParsingException) {
            e.printStackTrace()
            GoogleSignInResult.Failure("Could not parse the credential returned by Google")
        } catch (e: Throwable) {
            e.printStackTrace()
            GoogleSignInResult.Failure(e.message ?: "Unknown sign-in error")
        }
    }

    /**
     * Decodes the (unverified) payload segment of the JWT ID token purely to read the standard
     * profile claims for display. This is safe only because there is no backend relying on this
     * value for authorization - it is used for UI display only. If this app ever adds a backend,
     * the ID token must be sent there and verified server-side instead of trusted client-side.
     */
    private fun decodeIdTokenClaims(idToken: String): JSONObject? {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JSONObject(String(payload, Charsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun signOut(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putBoolean(KEY_IS_SIGNED_IN, false)
                .apply()
            currentProfile = currentProfile.copy(isSignedIn = false)

            // Also clears the Credential Manager's cached sign-in state so the account
            // picker is shown again next time instead of silently re-signing in.
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
}
