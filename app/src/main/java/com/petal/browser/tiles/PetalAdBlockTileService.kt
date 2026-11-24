package com.petal.browser.tiles

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalAdBlockMode
import com.petal.browser.live.LiveEventKind
import com.petal.browser.live.PetalLiveActionBus

/**
 * Quick Settings Toggle Tile for Petal Ad & Tracker Blocker.
 * Cycles through: Off -> Standard -> Aggressive -> Off.
 */
class PetalAdBlockTileService : PetalTileBase() {

    override val observedPrefKeys: Set<String> = setOf("sp_ad_block", "sp_adblock_protection_mode")

    override fun refreshTileState() {
        val mode = PetalAdBlockEngine.getAdBlockMode(this)
        val isActive = mode != PetalAdBlockMode.DISABLED
        val subtitle = when (mode) {
            PetalAdBlockMode.DISABLED -> getString(R.string.quick_tile_adblock_off)
            PetalAdBlockMode.STANDARD -> getString(R.string.quick_tile_adblock_standard)
            PetalAdBlockMode.AGGRESSIVE -> getString(R.string.quick_tile_adblock_aggressive)
        }
        updateTileState(
            state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            label = getString(R.string.quick_tile_adblock_label),
            subtitle = subtitle,
            icon = Icon.createWithResource(this, R.drawable.ic_tile_adblock)
        )
    }

    override fun onClick() {
        super.onClick()
        val currentMode = PetalAdBlockEngine.getAdBlockMode(this)
        val next = cycleNextMode(currentMode)
        PetalAdBlockEngine.setAdBlockMode(this, next)
        PetalLiveActionBus.postSimple(
            kind = LiveEventKind.ADBLOCK_STATE,
            key = "adblock_mode_change",
            title = getString(R.string.quick_tile_adblock_label),
            subtitle = next.title
        )
        refreshTileState()
    }

    companion object {
        fun cycleNextMode(current: PetalAdBlockMode): PetalAdBlockMode = when (current) {
            PetalAdBlockMode.DISABLED -> PetalAdBlockMode.STANDARD
            PetalAdBlockMode.STANDARD -> PetalAdBlockMode.AGGRESSIVE
            PetalAdBlockMode.AGGRESSIVE -> PetalAdBlockMode.DISABLED
        }
    }
}
