package com.petal.browser.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.widget.PetalSearchWidgetProvider

class PetalBookmarksTileService : PetalTileBase() {

    override fun refreshTileState() {
        updateTileState(
            state = Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_bookmarks_label),
            subtitle = getString(R.string.quick_tile_bookmarks_subtitle),
            icon = Icon.createWithResource(this, R.drawable.ic_tile_bookmarks)
        )
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        launchIntent(intent)
    }
}
