package com.petal.browser.compose.ai

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.predictive.PetalContentSnapshot

object PetalAiHubBridge {

    @JvmStatic
    fun showAiHub(activity: ComponentActivity) {
        activity.runOnUiThread {
            try {
                val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
                PetalContentSnapshot.capture(rootView)
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

                    setContent {
                        val snapshotBitmap = remember { PetalContentSnapshot.current?.asImageBitmap() }
                        DisposableEffect(Unit) {
                            onDispose {
                                PetalContentSnapshot.clear()
                            }
                        }
                        PetalAiHubScreen(
                            backgroundSnapshot = snapshotBitmap,
                            context = activity,
                            onOpenUrl = { url ->
                                (activity as? com.petal.browser.activity.BrowserActivity)?.let { b ->
                                    (composeView?.parent as? ViewGroup)?.removeView(composeView)
                                    b.addAlbum(null, url, true)
                                }
                            },
                            onBack = {
                                (composeView?.parent as? ViewGroup)?.removeView(composeView)
                            }
                        )
                    }
                }

                val contentGroup = activity.findViewById<ViewGroup>(android.R.id.content)
                contentGroup?.addView(composeView, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

