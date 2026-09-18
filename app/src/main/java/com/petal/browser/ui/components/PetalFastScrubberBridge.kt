/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Bridge binding for PetalFastScrubber Compose overlay
 */

package com.petal.browser.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme

class PetalFastScrubberBridge(
    private val activity: ComponentActivity,
    private val onJumpToTopAction: () -> Unit,
    private val onJumpToBottomAction: () -> Unit
) {
    private var isVisibleState = mutableStateOf(false)
    private var progressPercentState = mutableIntStateOf(0)
    private var hideRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun bind(composeView: ComposeView) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.setViewTreeLifecycleOwner(activity)
        composeView.setViewTreeViewModelStoreOwner(activity)
        composeView.setViewTreeSavedStateRegistryOwner(activity)

        composeView.setContent {
            val visible by remember { isVisibleState }
            val percent by remember { progressPercentState }

            PetalExpressiveTheme {
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    PetalFastScrubber(
                        visible = visible,
                        progressPercent = percent,
                        onJumpToTop = onJumpToTopAction,
                        onJumpToBottom = onJumpToBottomAction
                    )
                }
            }
        }
    }

    fun onScrollUpdate(scrollY: Int, contentHeight: Int) {
        val percent = if (contentHeight > 0) {
            ((scrollY.toFloat() / contentHeight.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else 0

        progressPercentState.intValue = percent

        if (scrollY > 150) {
            isVisibleState.value = true
            hideRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                isVisibleState.value = false
            }
            hideRunnable = runnable
            handler.postDelayed(runnable, 2400)
        } else {
            isVisibleState.value = false
        }
    }

    fun hide() {
        isVisibleState.value = false
        hideRunnable?.let { handler.removeCallbacks(it) }
    }
}
