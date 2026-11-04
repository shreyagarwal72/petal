package com.petal.browser.island

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.petal.browser.live.LiveActionActionListener

/**
 * Backward-compatibility wrapper. The primary implementation is now in [PetalNowBar].
 */
@Deprecated(
    message = "Renamed to PetalNowBar",
    replaceWith = ReplaceWith("PetalNowBar(modifier, actionListener)")
)
@Composable
fun PetalCutoutIsland(
    modifier: Modifier = Modifier,
    actionListener: LiveActionActionListener? = null
) {
    PetalNowBar(modifier = modifier, actionListener = actionListener)
}
