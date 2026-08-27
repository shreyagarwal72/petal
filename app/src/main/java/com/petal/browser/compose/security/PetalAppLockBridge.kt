/*
 * PetalAppLockBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Java-interop bridge for launching App Lock Overlay and Configuration screens.
 */

package com.petal.browser.compose.security

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView

object PetalAppLockBridge {

    @JvmStatic
    fun showLockOverlay(activity: Activity, onUnlocked: Runnable, onCancel: Runnable) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        var composeView: ComposeView? = null
        composeView = ComposeView(activity).apply {
            setContent {
                PetalAppLockScreen(
                    onUnlocked = {
                        decor.removeView(composeView)
                        onUnlocked.run()
                    },
                    onBackPress = {
                        decor.removeView(composeView)
                        onCancel.run()
                    }
                )
            }
        }
        decor.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    @JvmStatic
    fun showConfig(activity: Activity, onBack: Runnable) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        var composeView: ComposeView? = null
        composeView = ComposeView(activity).apply {
            setContent {
                PetalAppLockConfigScreen(
                    onBack = {
                        decor.removeView(composeView)
                        onBack.run()
                    }
                )
            }
        }
        decor.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}
