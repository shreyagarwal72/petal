package com.petal.browser.island

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.petal.browser.live.LiveActionActionListener

/**
 * Backward-compatibility wrapper. The primary implementation is now in [PetalNowBarBridge].
 */
@Deprecated(
    message = "Renamed to PetalNowBarBridge",
    replaceWith = ReplaceWith("PetalNowBarBridge")
)
object PetalCutoutIslandBridge {

    @JvmStatic
    fun attachIsland(
        activity: ComponentActivity,
        composeView: ComposeView,
        listener: LiveActionActionListener? = null
    ) {
        PetalNowBarBridge.attachNowBar(activity, composeView, listener)
    }
}
