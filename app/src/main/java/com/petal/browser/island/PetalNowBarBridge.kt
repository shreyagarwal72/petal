package com.petal.browser.island

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalNowBarBridge {

    @JvmStatic
    fun attachNowBar(
        activity: ComponentActivity,
        composeView: ComposeView,
        listener: LiveActionActionListener? = null
    ) {
        if (listener != null) {
            PetalLiveActionBus.setActionListener(listener)
        }

        composeView.setViewTreeLifecycleOwner(activity)
        composeView.setViewTreeViewModelStoreOwner(activity)
        composeView.setViewTreeSavedStateRegistryOwner(activity)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        composeView.setContent {
            PetalExpressiveTheme {
                PetalNowBar(actionListener = listener)
            }
        }
    }

    /**
     * Backward compatibility alias.
     */
    @Deprecated(
        message = "Renamed to attachNowBar",
        replaceWith = ReplaceWith("attachNowBar(activity, composeView, listener)")
    )
    @JvmStatic
    fun attachIsland(
        activity: ComponentActivity,
        composeView: ComposeView,
        listener: LiveActionActionListener? = null
    ) {
        attachNowBar(activity, composeView, listener)
    }
}
