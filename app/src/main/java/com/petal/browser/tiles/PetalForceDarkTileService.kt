package com.petal.browser.tiles

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.engine.gecko.PetalGeckoRuntime

/**
 * Quick Settings Toggle Tile for Web Page Force Dark Mode.
 */
class PetalForceDarkTileService : PetalTileBase() {

    override val observedPrefKeys: Set<String> = setOf("sp_force_dark_mode")

    override fun refreshTileState() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val isDark = sp.getBoolean("sp_force_dark_mode", false)
        val subtitle = if (isDark) getString(R.string.quick_tile_force_dark_on) else getString(R.string.quick_tile_force_dark_off)
        updateTileState(
            state = if (isDark) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_force_dark_label),
            subtitle = subtitle,
            icon = Icon.createWithResource(this, R.drawable.ic_tile_dark_mode)
        )
    }

    override fun onClick() {
        super.onClick()
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val current = sp.getBoolean("sp_force_dark_mode", false)
        val next = !current
        sp.edit().putBoolean("sp_force_dark_mode", next).apply()
        try {
            PetalGeckoRuntime.syncPreferences(sp)
        } catch (_: Throwable) {}
        refreshTileState()
    }
}
