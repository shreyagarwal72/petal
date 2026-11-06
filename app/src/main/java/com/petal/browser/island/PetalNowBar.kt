/*
 * PetalNowBar.kt
 * ──────────────────────────────────────────────────────────────────────────
 * In-app host for Petal NowBar (the expressive live-update bar & dynamic island).
 * The core live experience lives in PetalLiveIsland.kt and is shared with
 * the over-other-apps overlay.
 */

package com.petal.browser.island

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.PetalLiveActionBus

@Composable
fun PetalNowBar(
    modifier: Modifier = Modifier,
    actionListener: LiveActionActionListener? = null
) {
    val context = LocalContext.current
    val busState by PetalLiveActionBus.state.collectAsStateWithLifecycle()
    val settings by rememberIslandSettings()
    val show = remember(settings) { PetalIslandPermissions.shouldShowIsland(context, settings) }

    val view = LocalView.current
    val hardwareOffsetXDp = remember(view) { CutoutMetrics.cutoutHorizontalOffsetDp(view) }
    val effectiveOffsetXDp = when (settings.alignmentMode) {
        IslandSettings.ALIGN_LEFT -> -80f + settings.offsetXDp
        IslandSettings.ALIGN_RIGHT -> 80f + settings.offsetXDp
        IslandSettings.ALIGN_CENTER -> 0f + settings.offsetXDp
        else -> hardwareOffsetXDp + settings.offsetXDp // ALIGN_AUTO
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        PetalLiveIsland(
            state = if (show) busState else LiveActionBusState(),
            settings = settings,
            listener = actionListener ?: PetalLiveActionBus.getActionListener(),
            contentModifier = Modifier.statusBarsPadding(),
            cutoutOffsetXDp = effectiveOffsetXDp
        )
    }
}
