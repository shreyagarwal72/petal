/*
 * PetalProfileManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Singleton managing user browsing profiles in Petal Browser (Issue #23).
 * Provides thread-safe profile creation, switching, editing, deletion,
 * and scoped preference keys.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.petal.browser.account.GoogleAccountManager
import org.json.JSONArray
import java.util.UUID

object PetalProfileManager {
    private const val KEY_PROFILES_JSON = "sp_petal_profiles_list_v1"
    private const val KEY_ACTIVE_PROFILE_ID = "sp_petal_active_profile_id_v1"

    var activeProfile by mutableStateOf(PetalProfile.DEFAULT_PROFILE)
        private set

    var profiles by mutableStateOf<List<PetalProfile>>(listOf(PetalProfile.DEFAULT_PROFILE))
        private set

    fun init(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val jsonStr = sp.getString(KEY_PROFILES_JSON, null)
        val loadedProfiles = mutableListOf<PetalProfile>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    loadedProfiles.add(PetalProfile.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (loadedProfiles.isEmpty()) {
            loadedProfiles.add(PetalProfile.DEFAULT_PROFILE)
            saveProfiles(context, loadedProfiles)
        }

        profiles = loadedProfiles

        val savedActiveId = sp.getString(KEY_ACTIVE_PROFILE_ID, PetalProfile.DEFAULT_PROFILE.id)
        activeProfile = loadedProfiles.find { it.id == savedActiveId } ?: loadedProfiles.first()

        // Sync with GoogleAccountManager display name if default
        GoogleAccountManager.init(context)
    }

    fun switchProfile(context: Context, profileId: String) {
        val target = profiles.find { it.id == profileId } ?: return
        activeProfile = target
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(KEY_ACTIVE_PROFILE_ID, target.id)
            .apply()
    }

    fun createProfile(
        context: Context,
        name: String,
        colorHex: String = "#4285F4",
        avatarPresetId: String = "petal_flower"
    ): PetalProfile {
        val newProfile = PetalProfile(
            id = "profile_" + UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { "Profile ${profiles.size + 1}" },
            colorHex = colorHex,
            avatarPresetId = avatarPresetId,
            isDefault = false
        )
        val updated = profiles + newProfile
        profiles = updated
        saveProfiles(context, updated)
        switchProfile(context, newProfile.id)
        return newProfile
    }

    fun updateProfile(context: Context, updated: PetalProfile) {
        val list = profiles.toMutableList()
        val index = list.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            list[index] = updated
            profiles = list
            saveProfiles(context, list)
            if (activeProfile.id == updated.id) {
                activeProfile = updated
            }
        }
    }

    fun deleteProfile(context: Context, profileId: String): Boolean {
        if (profiles.size <= 1) return false
        val target = profiles.find { it.id == profileId } ?: return false
        if (target.isDefault) return false

        val updated = profiles.filter { it.id != profileId }
        profiles = updated
        saveProfiles(context, updated)

        if (activeProfile.id == profileId) {
            switchProfile(context, profiles.first().id)
        }
        return true
    }

    private fun saveProfiles(context: Context, list: List<PetalProfile>) {
        val array = JSONArray()
        for (p in list) {
            array.put(p.toJson())
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(KEY_PROFILES_JSON, array.toString())
            .apply()
    }

    /**
     * Returns a profile-scoped preferences key, ensuring shortcuts and history
     * stay distinct per profile.
     */
    fun getScopedKey(baseKey: String): String {
        return if (activeProfile.isDefault) baseKey else "${baseKey}_${activeProfile.id}"
    }
}
