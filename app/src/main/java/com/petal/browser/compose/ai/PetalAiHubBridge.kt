package com.petal.browser.compose.ai

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

object PetalAiHubBridge {

    @JvmStatic
    fun showAiHub(activity: ComponentActivity) {
        activity.runOnUiThread {
            try {
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

                    setContent {
                        PetalAiHubScreen(
                            context = activity,
                            onOpenUrl = { url ->
                                (activity as? com.petal.browser.activity.BrowserActivity)?.let { b ->
                                    (composeView?.parent as? ViewGroup)?.removeView(composeView)
                                    b.updateUrlAndLoad(url)
                                }
                            },
                            onBack = {
                                (composeView?.parent as? ViewGroup)?.removeView(composeView)
                            }
                        )
                    }
                }

                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                rootView.addView(composeView, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
