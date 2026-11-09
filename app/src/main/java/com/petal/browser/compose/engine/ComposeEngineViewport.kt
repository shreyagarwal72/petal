package com.petal.browser.compose.engine

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.ui.glass.GeckoGlassBackend
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineView
import org.mozilla.geckoview.GeckoView

/**
 * ComposeEngineViewport
 * ─────────────────────────────────────────────────────────────────────────────
 * Modern Jetpack Compose wrapper hosting Mozilla's [EngineView] (GeckoView 159).
 *
 * Provides direct GPU compositing for Petal's Liquid Glass and Material 3
 * Expressive layers with zero legacy ViewTree hacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEngineViewport(
    tab: TabSessionState?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pullRefreshState = rememberPullToRefreshState()
    var isPullRefreshing by remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(isLoading) {
        if (!isLoading) {
            isPullRefreshing = false
        }
    }

    // Create and retain an EngineView instance across recompositions
    val engineView = remember(context) {
        PetalEngineStore.createEngineView(context).also { ev ->
            ev.asView().background = null
            ev.asView().isNestedScrollingEnabled = true
            findGeckoView(ev.asView())?.let { gv ->
                gv.background = null
                try {
                    GeckoGlassBackend.applyBackend(gv, context)
                } catch (_: Throwable) {}
            }
        }
    }

    // Bind current active tab session
    LaunchedEffect(tab?.id, tab?.engineState?.engineSession) {
        val session = tab?.engineState?.engineSession
        if (session != null) {
            try {
                engineView.render(session)
            } catch (t: Throwable) {
                android.util.Log.w("ComposeEngineViewport", "Failed to render engine session: ${t.message}")
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            isPullRefreshing = true
            onRefresh()
        },
        state = pullRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AndroidView(
                factory = {
                    val frameLayout = FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        background = null
                    }
                    val view = engineView.asView()
                    (view.parent as? ViewGroup)?.removeView(view)
                    frameLayout.addView(
                        view,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    frameLayout
                },
                update = {
                    // Update layout and backend if required
                    findGeckoView(engineView.asView())?.let { gv ->
                        try {
                            GeckoGlassBackend.applyBackend(gv, context)
                        } catch (_: Throwable) {}
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun findGeckoView(root: View?): GeckoView? {
    if (root is GeckoView) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            val child = findGeckoView(root.getChildAt(i))
            if (child != null) return child
        }
    }
    return null
}
