package com.petal.browser.tiles

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.petal.browser.R
import com.petal.browser.widget.PetalSearchWidgetProvider
import com.petal.browser.widget.PetalWidgetIntentRouter

class PetalSearchTileService : PetalTileBase() {

    override fun refreshTileState() {
        updateTileState(
            state = Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_search_label),
            subtitle = getString(R.string.quick_tile_search_subtitle),
            icon = Icon.createWithResource(this, R.drawable.ic_tile_search)
        )
    }

    override fun onClick() {
        super.onClick()
        launchIntent(PetalWidgetIntentRouter.intentForAction(this, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    }
}
