package com.petal.browser.account

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

/** Local profile/avatar state for the FOSS build. No Google or Play Services code is used. */
enum class AvatarType { PRESET, GALLERY_URI, GOOGLE_URL }

data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarType: AvatarType = AvatarType.PRESET,
    val avatarPresetId: String = "petal_flower",
    val customAvatarUri: String? = null,
    val isSignedIn: Boolean = false,
    val globalGoogleLogin: Boolean = false,
    val avatarTimestamp: Long = System.currentTimeMillis()
)

sealed class GoogleSignInResult {
    data class Success(val profile: GoogleUserProfile) : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}

object GoogleAccountManager {
    private const val KEY_EMAIL = "sp_google_account_email"
    private const val KEY_DISPLAY_NAME = "sp_google_account_display_name"
    private const val KEY_AVATAR_TYPE = "sp_user_avatar_type"
    private const val KEY_AVATAR_PRESET = "sp_user_avatar_preset"
    private const val KEY_CUSTOM_AVATAR_URI = "sp_user_custom_avatar_uri"

    val builtinAvatarPresets = listOf(
        "petal_flower" to "Petal", "cosmic_star" to "Cosmic Star",
        "cyber_shield" to "Cyber Shield", "rocket_boost" to "Rocket",
        "ocean_wave" to "Ocean", "ninja_cat" to "Ninja",
        "sparkle" to "Sparkles", "bot_avatar" to "Cyber Bot"
    )

    var currentProfile by mutableStateOf(GoogleUserProfile("user@petalbrowser.org", "Petal Explorer"))
        private set

    fun init(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val type = runCatching {
            AvatarType.valueOf(sp.getString(KEY_AVATAR_TYPE, AvatarType.PRESET.name)!!)
        }.getOrDefault(AvatarType.PRESET)
        currentProfile = currentProfile.copy(
            email = sp.getString(KEY_EMAIL, currentProfile.email) ?: currentProfile.email,
            displayName = sp.getString(KEY_DISPLAY_NAME, currentProfile.displayName) ?: currentProfile.displayName,
            avatarType = type,
            avatarPresetId = sp.getString(KEY_AVATAR_PRESET, "petal_flower") ?: "petal_flower",
            customAvatarUri = sp.getString(KEY_CUSTOM_AVATAR_URI, null),
            isSignedIn = false,
            globalGoogleLogin = false
        )
    }

    fun updateDisplayName(context: Context, newName: String) {
        val name = newName.trim().take(15).ifEmpty { "Petal Explorer" }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY_DISPLAY_NAME, name).apply()
        currentProfile = currentProfile.copy(displayName = name, avatarTimestamp = System.currentTimeMillis())
    }

    fun updateAvatarPreset(context: Context, presetId: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_AVATAR_TYPE, AvatarType.PRESET.name).putString(KEY_AVATAR_PRESET, presetId).apply()
        currentProfile = currentProfile.copy(avatarType = AvatarType.PRESET, avatarPresetId = presetId,
            avatarTimestamp = System.currentTimeMillis())
    }

    fun saveCroppedAvatar(context: Context, bitmap: android.graphics.Bitmap) {
        val file = java.io.File(context.filesDir, "petal_user_avatar.png")
        java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        updateAvatarGalleryUri(context, Uri.fromFile(file).toString())
    }

    fun updateAvatarGalleryUri(context: Context, uriString: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_AVATAR_TYPE, AvatarType.GALLERY_URI.name)
            .putString(KEY_CUSTOM_AVATAR_URI, uriString).apply()
        currentProfile = currentProfile.copy(avatarType = AvatarType.GALLERY_URI,
            customAvatarUri = uriString, avatarTimestamp = System.currentTimeMillis())
    }
}
