/*
 * PetalWallpaperManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Wallpaper management engine for Petal Browser (ported from OmniBrowser).
 * Supports profile-scoped home screen wallpapers, presets, live videos,
 * dim levels, and blur levels.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.petal.browser.profile.PetalProfileManager

object PetalWallpaperManager {
    private const val KEY_WALLPAPER_URI_PREFIX = "sp_home_wallpaper_uri"
    private const val KEY_WALLPAPER_DIM_PREFIX = "sp_home_wallpaper_dim"
    private const val KEY_WALLPAPER_BLUR_PREFIX = "sp_home_wallpaper_blur"

    var wallpaperUri by mutableStateOf<String?>(null)
        private set

    var wallpaperDim by mutableFloatStateOf(0.20f)
        private set

    var wallpaperBlur by mutableFloatStateOf(0f)
        private set

    /**
     * The wallpaper that is currently being fetched or buffered (null when nothing is).
     * The picker shows a loading indicator on that wallpaper's tile, and the background
     * clears it once the first frame or image is on screen.
     */
    var loadingUri by mutableStateOf<String?>(null)
        private set

    fun markLoading(uri: String?) {
        loadingUri = uri
    }

    /** Called by the wallpaper background when [uri] is showing, or has failed. */
    fun markReady(uri: String) {
        if (loadingUri == uri) loadingUri = null
    }

    fun init(context: Context) {
        loadForActiveProfile(context)
    }

    private fun safeGetFloat(sp: SharedPreferences, key: String, defaultVal: Float): Float {
        return try {
            sp.getFloat(key, defaultVal)
        } catch (_: ClassCastException) {
            try {
                // If stored as Integer/Long/Double/String during backup restore
                val all = sp.all
                val raw = all[key]
                when (raw) {
                    is Number -> raw.toFloat()
                    is String -> raw.toFloatOrNull() ?: defaultVal
                    else -> defaultVal
                }
            } catch (_: Throwable) {
                defaultVal
            }
        } catch (_: Throwable) {
            defaultVal
        }
    }

    fun loadForActiveProfile(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val profile = PetalProfileManager.activeProfile
        val profileId = profile.id
        val isDefault = profile.isDefault
        val uriKey = if (isDefault) KEY_WALLPAPER_URI_PREFIX else "${KEY_WALLPAPER_URI_PREFIX}_$profileId"
        val dimKey = if (isDefault) KEY_WALLPAPER_DIM_PREFIX else "${KEY_WALLPAPER_DIM_PREFIX}_$profileId"
        val blurKey = if (isDefault) KEY_WALLPAPER_BLUR_PREFIX else "${KEY_WALLPAPER_BLUR_PREFIX}_$profileId"

        val defaultUri = sp.getString(KEY_WALLPAPER_URI_PREFIX, null)
        wallpaperUri = if (isDefault) {
            sp.getString(uriKey, defaultUri)
        } else {
            sp.getString(uriKey, null)
        }
        val fallbackDim = safeGetFloat(sp, KEY_WALLPAPER_DIM_PREFIX, 0.20f)
        wallpaperDim = safeGetFloat(sp, dimKey, fallbackDim)
        val fallbackBlur = safeGetFloat(sp, KEY_WALLPAPER_BLUR_PREFIX, 0f)
        wallpaperBlur = safeGetFloat(sp, blurKey, fallbackBlur)
    }

    fun setWallpaper(context: Context, uri: String?, dim: Float = wallpaperDim, blur: Float = wallpaperBlur) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val profile = PetalProfileManager.activeProfile
        val profileId = profile.id
        val isDefault = profile.isDefault
        val uriKey = if (isDefault) KEY_WALLPAPER_URI_PREFIX else "${KEY_WALLPAPER_URI_PREFIX}_$profileId"
        val dimKey = if (isDefault) KEY_WALLPAPER_DIM_PREFIX else "${KEY_WALLPAPER_DIM_PREFIX}_$profileId"
        val blurKey = if (isDefault) KEY_WALLPAPER_BLUR_PREFIX else "${KEY_WALLPAPER_BLUR_PREFIX}_$profileId"

        wallpaperUri = uri
        wallpaperDim = dim.coerceIn(0f, 0.85f)
        wallpaperBlur = blur.coerceIn(0f, 25f)

        val editor = sp.edit()
        if (uri != null) {
            editor.putString(uriKey, uri)
            if (isDefault) {
                editor.putString("${KEY_WALLPAPER_URI_PREFIX}_$profileId", uri)
            }
        } else {
            editor.remove(uriKey)
            if (isDefault) {
                editor.remove("${KEY_WALLPAPER_URI_PREFIX}_$profileId")
            }
        }
        editor.putFloat(dimKey, wallpaperDim)
        if (isDefault) {
            editor.putFloat(KEY_WALLPAPER_DIM_PREFIX, wallpaperDim)
        }
        editor.putFloat(blurKey, wallpaperBlur)
        if (isDefault) {
            editor.putFloat(KEY_WALLPAPER_BLUR_PREFIX, wallpaperBlur)
        }
        editor.apply()
    }

    fun updateDim(context: Context, dim: Float) {
        setWallpaper(context, wallpaperUri, dim = dim, blur = wallpaperBlur)
    }

    fun updateBlur(context: Context, blur: Float) {
        setWallpaper(context, wallpaperUri, dim = wallpaperDim, blur = blur)
    }

    fun clearWallpaper(context: Context) {
        setWallpaper(context, null)
    }
}
