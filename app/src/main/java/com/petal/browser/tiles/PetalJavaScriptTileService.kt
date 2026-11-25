package com.petal.browser.tiles

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.engine.gecko.PetalGeckoRuntime

/**
 * Quick Settings Toggle Tile for JavaScript execution.
 */
class PetalJavaScriptTileService : PetalTileBase() {

    override val observedPrefKeys: Set<String> = setOf("sp_javascript")

    override fun refreshTileState() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val isJs = sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true))
        val subtitle = if (isJs) getString(R.string.quick_tile_javascript_on) else getString(R.string.quick_tile_javascript_off)
        updateTileState(
            state = if (isJs) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_javascript_label),
            subtitle = subtitle,
            icon = Icon.createWithResource(this, R.drawable.ic_tile_javascript)
        )
    }

    override fun onClick() {
        super.onClick()
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val current = sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true))
        val next = !current
        sp.edit().putBoolean("sp_javascript", next).apply()
        try {
            PetalGeckoRuntime.syncPreferences(sp)
        } catch (_: Throwable) {}
        refreshTileState()
    }
}
