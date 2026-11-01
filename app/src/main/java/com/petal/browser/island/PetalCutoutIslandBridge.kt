package com.petal.browser.island

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalCutoutIslandBridge {

    @JvmStatic
    fun attachIsland(
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
                PetalCutoutIsland(actionListener = listener)
            }
        }
    }
}
