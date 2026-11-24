package com.petal.browser.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.compose.incognito.PetalIncognitoSessionManager

/**
 * Quick Settings Action Tile to close all open Incognito/Private tabs.
 * Subtitle shows the count of currently open private tabs (never URLs or titles).
 */
class PetalCloseIncognitoTileService : PetalTileBase() {

    override fun refreshTileState() {
        val count = PetalIncognitoSessionManager.getIncognitoCount(this)
        val subtitle = if (count > 0) {
            getString(R.string.quick_tile_close_incognito_count, count)
        } else {
            getString(R.string.quick_tile_close_incognito_none)
        }
        updateTileState(
            state = if (count > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_close_incognito_label),
            subtitle = subtitle,
            icon = Icon.createWithResource(this, R.drawable.ic_tile_close_incognito)
        )
    }

    override fun onClick() {
        super.onClick()
        val count = PetalIncognitoSessionManager.getIncognitoCount(this)
        if (count > 0) {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = PetalIncognitoSessionManager.ACTION_CLOSE_INCOGNITO
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            launchIntent(intent)
        }
        refreshTileState()
    }
}
