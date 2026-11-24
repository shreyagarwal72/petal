package com.petal.browser.lens

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.petal.browser.R
import com.petal.browser.tiles.PetalTileBase

class PetalScannerTileService : PetalTileBase() {

    override fun refreshTileState() {
        updateTileState(
            state = Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_scanner_label),
            subtitle = getString(R.string.quick_tile_scanner_subtitle),
            icon = Icon.createWithResource(this, R.drawable.ic_tile_scanner)
        )
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, PetalScannerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        launchIntent(intent)
    }
}
