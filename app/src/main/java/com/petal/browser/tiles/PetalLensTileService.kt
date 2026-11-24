package com.petal.browser.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.widget.PetalSearchWidgetProvider

class PetalLensTileService : PetalTileBase() {

    override fun refreshTileState() {
        updateTileState(
            state = Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_lens_label),
            subtitle = getString(R.string.quick_tile_lens_subtitle),
            icon = Icon.createWithResource(this, R.drawable.ic_tile_lens)
        )
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, com.petal.browser.lens.PetalScannerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        launchIntent(intent)
    }
}
