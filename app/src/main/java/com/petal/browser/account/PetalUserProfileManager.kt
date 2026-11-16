package com.petal.browser.account

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

/** Where the profile picture comes from. */
enum class AvatarType { PRESET, GALLERY_URI }

/**
 * The single local identity shown across Petal (home, progress, account page). It lives only on this
 * device; sync between devices is done through the user's own storage, never through an account.
 */
data class PetalUserProfile(
    val displayName: String,
    val avatarType: AvatarType = AvatarType.PRESET,
    val avatarPresetId: String = "petal_flower",
    val customAvatarUri: String? = null,
    val bio: String = "",
    val accentColorHex: String? = null,
    val avatarTimestamp: Long = System.currentTimeMillis()
)

object PetalUserProfileManager {
    private const val KEY_DISPLAY_NAME = "sp_user_display_name"
    private const val LEGACY_KEY_DISPLAY_NAME = "sp_google_account_display_name"
    private const val KEY_MIGRATED = "sp_user_profile_migrated_v2"
    private const val KEY_AVATAR_TYPE = "sp_user_avatar_type"
    private const val KEY_AVATAR_PRESET = "sp_user_avatar_preset"
    private const val KEY_CUSTOM_AVATAR_URI = "sp_user_custom_avatar_uri"
    private const val KEY_BIO = "sp_user_bio"
    private const val KEY_ACCENT = "sp_user_accent"
    private const val DEFAULT_NAME = "Petal Explorer"
    private const val AVATAR_FILE = "petal_user_avatar.png"

    const val MAX_NAME = 30
    const val MAX_BIO = 80

    val builtinAvatarPresets = listOf(
        "petal_flower" to "Petal", "cosmic_star" to "Cosmic Star",
        "cyber_shield" to "Cyber Shield", "rocket_boost" to "Rocket",
        "ocean_wave" to "Ocean", "ninja_cat" to "Ninja",
        "sparkle" to "Sparkles", "bot_avatar" to "Cyber Bot"
    )

    val accentChoices = listOf(
        "#E91E63", "#9C27B0", "#3F51B5", "#009688",
        "#4CAF50", "#FF9800", "#FF5722", "#607D8B"
    )

    var currentProfile by mutableStateOf(PetalUserProfile(displayName = DEFAULT_NAME))
        private set

    private fun sp(context: Context) = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun init(context: Context) {
        val prefs = sp(context)
        migrateLegacy(prefs)
        rebuild(prefs)
    }

    /** One-time move of the name saved under the very first profile key. */
    private fun migrateLegacy(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val edit = prefs.edit()
        val legacy = prefs.getString(LEGACY_KEY_DISPLAY_NAME, null)
        if (!legacy.isNullOrBlank() && legacy != DEFAULT_NAME && !prefs.contains(KEY_DISPLAY_NAME)) {
            edit.putString(KEY_DISPLAY_NAME, legacy.take(MAX_NAME))
        }
        edit.remove(LEGACY_KEY_DISPLAY_NAME).remove("sp_google_account_email")
        edit.putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun rebuild(prefs: android.content.SharedPreferences) {
        val name = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        // Older builds could store other avatar types; anything unknown falls back to a built-in avatar.
        val type = runCatching { AvatarType.valueOf(prefs.getString(KEY_AVATAR_TYPE, "") ?: "") }
            .getOrDefault(AvatarType.PRESET)
        currentProfile = PetalUserProfile(
            displayName = name,
            avatarType = type,
            avatarPresetId = prefs.getString(KEY_AVATAR_PRESET, "petal_flower") ?: "petal_flower",
            customAvatarUri = prefs.getString(KEY_CUSTOM_AVATAR_URI, null),
            bio = prefs.getString(KEY_BIO, "") ?: "",
            accentColorHex = prefs.getString(KEY_ACCENT, null),
            avatarTimestamp = System.currentTimeMillis()
        )
    }

    fun updateDisplayName(context: Context, newName: String) {
        val name = newName.trim().take(MAX_NAME)
        val edit = sp(context).edit()
        if (name.isEmpty()) edit.remove(KEY_DISPLAY_NAME) else edit.putString(KEY_DISPLAY_NAME, name)
        edit.apply()
        rebuild(sp(context))
    }

    fun updateBio(context: Context, bio: String) {
        sp(context).edit().putString(KEY_BIO, bio.trim().take(MAX_BIO)).apply()
        rebuild(sp(context))
    }

    fun updateAccent(context: Context, hex: String?) {
        val edit = sp(context).edit()
        if (hex == null) edit.remove(KEY_ACCENT) else edit.putString(KEY_ACCENT, hex)
        edit.apply()
        rebuild(sp(context))
    }

    fun updateAvatarPreset(context: Context, presetId: String) {
        sp(context).edit()
            .putString(KEY_AVATAR_TYPE, AvatarType.PRESET.name).putString(KEY_AVATAR_PRESET, presetId).apply()
        rebuild(sp(context))
    }

    fun saveCroppedAvatar(context: Context, bitmap: android.graphics.Bitmap) {
        val file = java.io.File(context.filesDir, AVATAR_FILE)
        java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        updateAvatarGalleryUri(context, Uri.fromFile(file).toString())
    }

    fun updateAvatarGalleryUri(context: Context, uriString: String) {
        sp(context).edit()
            .putString(KEY_AVATAR_TYPE, AvatarType.GALLERY_URI.name)
            .putString(KEY_CUSTOM_AVATAR_URI, uriString).apply()
        rebuild(sp(context))
    }

    /** Deletes every local profile choice (name, bio, accent, photo). */
    fun resetLocalProfile(context: Context) {
        sp(context).edit()
            .remove(KEY_DISPLAY_NAME).remove(KEY_BIO).remove(KEY_ACCENT)
            .remove(KEY_AVATAR_TYPE).remove(KEY_AVATAR_PRESET).remove(KEY_CUSTOM_AVATAR_URI).apply()
        runCatching { java.io.File(context.filesDir, AVATAR_FILE).delete() }
        rebuild(sp(context))
    }
}
