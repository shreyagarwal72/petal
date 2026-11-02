/*
 * PetalCutoutIsland.kt
 * ──────────────────────────────────────────────────────────────────────────
 * In-app host for the expressive live-update island. The island itself lives in
 * PetalLiveIsland.kt and is shared with the over-other-apps overlay.
 *
 * Fixes over the previous version:
 *   • Settings are reactive (they used to be read once and ignored until restart).
 *   • "Auto" mode now really hides the island when Android 16 shows its own Live Update chip.
 *   • The island animates in/out instead of popping.
 */

package com.petal.browser.island

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.PetalLiveActionBus

@Composable
fun PetalCutoutIsland(
    modifier: Modifier = Modifier,
    actionListener: LiveActionActionListener? = null
) {
    val context = LocalContext.current
    val busState by PetalLiveActionBus.state.collectAsState()
    val settings by rememberIslandSettings()
    val show = remember(settings) { PetalIslandPermissions.shouldShowIsland(context, settings) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        PetalLiveIsland(
            state = if (show) busState else LiveActionBusState(),
            settings = settings,
            listener = actionListener ?: PetalLiveActionBus.getActionListener(),
            contentModifier = Modifier.statusBarsPadding()
        )
    }
}
