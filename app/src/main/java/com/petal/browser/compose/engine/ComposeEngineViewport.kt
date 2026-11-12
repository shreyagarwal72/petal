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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.petal.browser.browser.PetalVerticalSwipeRefreshLayout
import com.petal.browser.compose.composable.PetalRefreshBarState
import com.petal.browser.compose.composable.RefreshBarLoadingIndicatorLazy
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.SwipeRefreshFeature
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
    // Pull-to-refresh uses Mozilla's own stack (same as Firefox for Android): a vertical-only
    // SwipeRefreshLayout + android-components' SwipeRefreshFeature, which asks Gecko
    // (InputResultDetail.canOverscrollTop) whether the page is at the top. The loader is drawn by
    // Petal's Zenith-style RefreshBarLoadingIndicatorLazy instead of the legacy Android spinner.
    val refreshBarState = remember { PetalRefreshBarState() }
    val currentIsLoading by rememberUpdatedState(isLoading)
    val swipeLayout = remember(context) {
        PetalVerticalSwipeRefreshLayout(context).apply {
            background = null
            isNestedScrollingEnabled = true
            onPullProgress = { refreshBarState.pullProgress = it }
            onRefreshStateChanged = { refreshing ->
                refreshBarState.isRefreshing = refreshing
                if (!refreshing) refreshBarState.pullProgress = 0f
            }
        }
    }

    // Hide the loader when the page finished loading (the Mozilla feature also does this on tab changes).
    LaunchedEffect(isLoading) {
        if (!isLoading) swipeLayout.isRefreshing = false
    }

    // Safety net, same as PetalTabViewController: the feature only hides the spinner when the tab's
    // `loading` flag CHANGES. If a reload ends between two updates, drop the loader after a few seconds.
    LaunchedEffect(refreshBarState.isRefreshing) {
        if (refreshBarState.isRefreshing) {
            kotlinx.coroutines.delay(5_000L)
            if (swipeLayout.isRefreshing && !currentIsLoading) swipeLayout.isRefreshing = false
        }
    }

    // Pull is only available while a real web page is shown.
    SideEffect {
        swipeLayout.isEnabled = tab != null && !PetalBrowserUtils.isHomePage(tab.content.url)
    }

    DisposableEffect(tab?.id, swipeLayout) {
        val tabId = tab?.id
        if (tabId == null) {
            onDispose { }
        } else {
            val store = PetalEngineStore.getStore(context)
            val feature = SwipeRefreshFeature(
                store = store,
                reloadUrlUseCase = SessionUseCases(store).reload,
                swipeRefreshLayout = swipeLayout,
                tabId = tabId
            )
            feature.start()
            onDispose {
                feature.stop()
                swipeLayout.isRefreshing = false
                refreshBarState.pullProgress = 0f
            }
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

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = {
                // Like Fenix, the EngineView is a direct child of the swipe-refresh layout:
                // SwipeRefreshFeature inspects that child to ask Gecko if the page can still scroll up.
                val view = engineView.asView()
                if (view.parent !== swipeLayout) {
                    (view.parent as? ViewGroup)?.removeView(view)
                    swipeLayout.addView(
                        view,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
                (swipeLayout.parent as? ViewGroup)?.removeView(swipeLayout)
                swipeLayout
            },
            update = {
                // Session binding is handled when the tab or engine session changes above.
                // Avoid rebinding GeckoView on every progress/title recomposition.
                findGeckoView(engineView.asView())?.let { gv ->
                    try {
                        GeckoGlassBackend.applyBackend(gv, context)
                    } catch (_: Throwable) {}
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        RefreshBarLoadingIndicatorLazy(
            isRefreshing = refreshBarState.isRefreshing,
            pullProgressProvider = { refreshBarState.pullProgress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop + 56.dp)
        )
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
