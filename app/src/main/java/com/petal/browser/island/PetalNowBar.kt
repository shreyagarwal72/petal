/*
 * PetalNowBar.kt
 * ──────────────────────────────────────────────────────────────────────────
 * In-app host for Petal NowBar. Used whenever the bar is NOT drawn by an overlay window, i.e.
 * IN_APP placement, or the app-overlay placement while Petal is open. The core experience lives
 * in PetalLiveIsland.kt and is shared with the overlay window.
 *
 * It is attached to the activity by [PetalNowBarBridge.attachToActivity].
 */

package com.petal.browser.island

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.PetalLiveActionBus
import kotlinx.coroutines.delay

@Composable
fun PetalNowBar(
    modifier: Modifier = Modifier,
    actionListener: LiveActionActionListener? = null
) {
    val context = LocalContext.current
    val busState by PetalLiveActionBus.state.collectAsStateWithLifecycle()
    val settings by rememberIslandSettings()
    val a11y by PetalIslandAccessibilityService.connected.collectAsStateWithLifecycle()

    // When the accessibility window is drawing the bar (inside the status bar, over every app),
    // this in-app copy must stay hidden or you would see two.
    val placement = remember(settings, a11y) {
        PetalLiveOverlayController.resolvePlacement(context, settings, a11y)
    }
    val show = placement != IslandPlacement.ACCESSIBILITY &&
        PetalIslandPermissions.shouldShowIsland(context, settings)

    val view = LocalView.current
    var hardwareOffsetXDp by remember { mutableFloatStateOf(0f) }
    // Cutout insets only exist once the view is attached, so sample a few times after it appears.
    LaunchedEffect(view, settings.alignmentMode) {
        repeat(3) {
            hardwareOffsetXDp = CutoutMetrics.cutoutHorizontalOffsetDp(view)
            delay(400)
        }
    }
    val effectiveOffsetXDp = PetalIslandPlacementMath.effectiveOffsetXDp(
        settings.alignmentMode, hardwareOffsetXDp, settings.offsetXDp
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        PetalLiveIsland(
            state = if (show) busState else LiveActionBusState(),
            settings = settings,
            listener = actionListener ?: PetalLiveActionBus.getActionListener(),
            // Anchored: draw from the very top, inside the status bar. Otherwise sit below it.
            contentModifier = if (settings.anchorStatusBar) Modifier else Modifier.statusBarsPadding(),
            cutoutOffsetXDp = effectiveOffsetXDp
        )
    }
}
