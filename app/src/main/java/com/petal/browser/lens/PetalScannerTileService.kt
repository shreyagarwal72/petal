package com.petal.browser.lens

import android.content.Intent
import android.service.quicksettings.TileService

class PetalScannerTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, PetalScannerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivityAndCollapse(intent)
    }
}
