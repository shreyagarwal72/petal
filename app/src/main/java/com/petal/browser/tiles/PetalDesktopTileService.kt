package com.petal.browser.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * Desktop Site Tile.
 *
 * In Petal Browser, desktop viewport mode is managed per-site/per-tab with domain overrides
 * (sp_desktop_site_). Toggling a global flag without a browser session in view is misleading.
 * As designed, this tile acts as a direct launcher into the active tab overflow menu so the user
 * can request Desktop Site directly in the active context.
 */
class PetalDesktopTileService : PetalTileBase() {

    override fun refreshTileState() {
        updateTileState(
            state = Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_desktop_site_label),
            subtitle = getString(R.string.quick_tile_desktop_site_subtitle),
            icon = Icon.createWithResource(this, R.drawable.ic_tile_desktop)
        )
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = PetalSearchWidgetProvider.ACTION_OPEN_MENU
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        launchIntent(intent)
    }
}
