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

    fun init(context: Context) {
        loadForActiveProfile(context)
    }

    fun loadForActiveProfile(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val profileId = PetalProfileManager.activeProfile.id
        val uriKey = "${KEY_WALLPAPER_URI_PREFIX}_$profileId"
        val dimKey = "${KEY_WALLPAPER_DIM_PREFIX}_$profileId"
        val blurKey = "${KEY_WALLPAPER_BLUR_PREFIX}_$profileId"

        val defaultUri = sp.getString(KEY_WALLPAPER_URI_PREFIX, null)
        wallpaperUri = sp.getString(uriKey, defaultUri)
        wallpaperDim = sp.getFloat(dimKey, sp.getFloat(KEY_WALLPAPER_DIM_PREFIX, 0.20f))
        wallpaperBlur = sp.getFloat(blurKey, sp.getFloat(KEY_WALLPAPER_BLUR_PREFIX, 0f))
    }

    fun setWallpaper(context: Context, uri: String?, dim: Float = wallpaperDim, blur: Float = wallpaperBlur) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val profileId = PetalProfileManager.activeProfile.id
        val uriKey = "${KEY_WALLPAPER_URI_PREFIX}_$profileId"
        val dimKey = "${KEY_WALLPAPER_DIM_PREFIX}_$profileId"
        val blurKey = "${KEY_WALLPAPER_BLUR_PREFIX}_$profileId"

        wallpaperUri = uri
        wallpaperDim = dim.coerceIn(0f, 0.85f)
        wallpaperBlur = blur.coerceIn(0f, 25f)

        val editor = sp.edit()
        if (uri != null) {
            editor.putString(uriKey, uri)
            if (PetalProfileManager.activeProfile.isDefault) {
                editor.putString(KEY_WALLPAPER_URI_PREFIX, uri)
            }
        } else {
            editor.remove(uriKey)
            if (PetalProfileManager.activeProfile.isDefault) {
                editor.remove(KEY_WALLPAPER_URI_PREFIX)
            }
        }
        editor.putFloat(dimKey, wallpaperDim)
        editor.putFloat(blurKey, wallpaperBlur)
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
