package com.petal.browser.island

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

    private const val HOST_TAG = "petal_nowbar_host"

    /**
     * Adds the in-app NowBar on top of [activity]'s content. Safe to call repeatedly: it does
     * nothing if the bar is already attached. Called from Application's lifecycle callbacks so
     * every Petal screen (browser, viewers, player, settings) shows the bar.
     *
     * The bar view is as wide as the screen but only as tall as the island; touches that miss the
     * island fall through to the screen underneath.
     */
    @JvmStatic
    fun attachToActivity(activity: ComponentActivity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<View>(HOST_TAG) != null) return
        val host = ComposeView(activity).apply { tag = HOST_TAG }
        // No explicit listener: the default one (PetalOverlayActions) is used so buttons work.
        attachNowBar(activity, host, PetalOverlayActions(activity.applicationContext))
        activity.addContentView(
            host,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )
    }

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
