package com.petal.browser.tiles

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.engine.gecko.PetalGeckoRuntime

/**
 * Quick Settings Toggle Tile for HTTPS-Only Mode.
 */
class PetalHttpsOnlyTileService : PetalTileBase() {

    override val observedPrefKeys: Set<String> = setOf("sp_https_only")

    override fun refreshTileState() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val isHttps = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false))
        val subtitle = if (isHttps) getString(R.string.quick_tile_https_only_on) else getString(R.string.quick_tile_https_only_off)
        updateTileState(
            state = if (isHttps) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_https_only_label),
            subtitle = subtitle,
            icon = Icon.createWithResource(this, R.drawable.ic_tile_https)
        )
    }

    override fun onClick() {
        super.onClick()
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val current = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false))
        val next = !current
        sp.edit().putBoolean("sp_https_only", next).apply()
        try {
            PetalGeckoRuntime.syncPreferences(sp)
        } catch (_: Throwable) {}
        refreshTileState()
    }
}
