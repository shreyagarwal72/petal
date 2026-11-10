package com.petal.browser.compose.engine

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.petal.browser.compose.composable.RefreshBarLoadingIndicatorLazy
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.ui.glass.GeckoGlassBackend
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.TabThumbnailCache
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineView
import org.mozilla.geckoview.GeckoResult
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
    var isPullRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
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
        if (tab == null) return@LaunchedEffect
        var session = tab.engineState.engineSession
        if (session == null) {
            try {
                val pair = PetalEngineStore.materializeTabSession(
                    context = context,
                    tabId = tab.id,
                    fallbackUrl = tab.content.url,
                    fallbackTitle = tab.content.title,
                    isIncognito = tab.content.private,
                    select = true
                )
                session = pair.second
            } catch (t: Throwable) {
                android.util.Log.e("ComposeEngineViewport", "Failed to materialize tab session", t)
            }
        }
        if (session != null) {
            try {
                engineView.render(session)
            } catch (t: Throwable) {
                android.util.Log.w("ComposeEngineViewport", "Failed to render engine session: ${t.message}")
            }
        }
    }

    // Capture webpage thumbnail into TabThumbnailCache upon load completion
    LaunchedEffect(tab?.id, isLoading) {
        if (!isLoading && tab != null && tab.content.url.isNotBlank() && !PetalBrowserUtils.isHomePage(tab.content.url)) {
            kotlinx.coroutines.delay(250L)
            findGeckoView(engineView.asView())?.let { gv ->
                captureGeckoThumbnail(gv, tab.id, tab.content.private)
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
        modifier = modifier.fillMaxSize(),
        indicator = {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            RefreshBarLoadingIndicatorLazy(
                isRefreshing = isPullRefreshing,
                pullProgressProvider = { pullRefreshState.distanceFraction },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarTop + 56.dp)
            )
        }
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
                    // Session binding is handled when the tab or engine session changes above.
                    // Avoid rebinding GeckoView on every progress/title recomposition.
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

private fun captureGeckoThumbnail(gv: GeckoView, tabId: String, isIncognito: Boolean) {
    if (gv.width <= 0 || gv.height <= 0) return
    try {
        gv.capturePixels().then({ bitmap: Bitmap? ->
            if (bitmap != null) {
                TabThumbnailCache.DISK.execute {
                    val w = bitmap.width
                    val h = bitmap.height
                    val targetWidth = Math.min(w, 480)
                    val targetHeight = Math.max(1, (h.toFloat() * targetWidth / w).toInt())
                    val scaled = try {
                        val s = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                        if (s !== bitmap && !bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                        s
                    } catch (_: Throwable) {
                        bitmap
                    }
                    TabThumbnailCache.put(tabId, scaled, isIncognito, true)
                }
            }
            GeckoResult.fromValue<Void?>(null)
        }, {
            GeckoResult.fromValue<Void?>(null)
        })
    } catch (_: Throwable) {}
}
