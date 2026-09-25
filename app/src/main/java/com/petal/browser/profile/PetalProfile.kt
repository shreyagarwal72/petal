/*
 * PetalProfile.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Multi-profile architecture for Petal Browser (Issue #23).
 * Represents discrete user browsing profiles with isolated preferences,
 * home shortcuts, avatars, and appearance tokens.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.profile

import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import org.json.JSONObject

data class PetalProfile(
    val id: String,
    val name: String,
    val colorHex: String = "#4285F4",
    val avatarPresetId: String = "petal_flower",
    val customAvatarUri: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getComposeColor(): ComposeColor {
        return try {
            ComposeColor(Color.parseColor(colorHex))
        } catch (_: Throwable) {
            ComposeColor(0xFF4285F4)
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("colorHex", colorHex)
        obj.put("avatarPresetId", avatarPresetId)
        if (customAvatarUri != null) obj.put("customAvatarUri", customAvatarUri)
        obj.put("isDefault", isDefault)
        obj.put("createdAt", createdAt)
        return obj
    }

    companion object {
        val DEFAULT_PROFILE = PetalProfile(
            id = "profile_default",
            name = "Personal",
            colorHex = "#4285F4",
            avatarPresetId = "petal_flower",
            isDefault = true
        )

        fun fromJson(obj: JSONObject): PetalProfile {
            return PetalProfile(
                id = obj.optString("id", "profile_default"),
                name = obj.optString("name", "Personal"),
                colorHex = obj.optString("colorHex", "#4285F4"),
                avatarPresetId = obj.optString("avatarPresetId", "petal_flower"),
                customAvatarUri = if (obj.has("customAvatarUri")) obj.optString("customAvatarUri") else null,
                isDefault = obj.optBoolean("isDefault", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
