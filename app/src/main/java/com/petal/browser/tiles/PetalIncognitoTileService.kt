package com.petal.browser.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.widget.PetalSearchWidgetProvider

class PetalIncognitoTileService : PetalTileBase() {

    override fun refreshTileState() {
        updateTileState(
            state = Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_incognito_label),
            subtitle = getString(R.string.quick_tile_incognito_subtitle),
            icon = Icon.createWithResource(this, R.drawable.ic_tile_incognito)
        )
    }

    override fun onClick() {
        super.onClick()
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val requireLock = sp.getBoolean("sp_biometric_incognito_tab_lock", false)
        val intent = Intent(this, MainActivity::class.java).apply {
            action = PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        launchIntent(intent, requireUnlock = requireLock)
    }
}
