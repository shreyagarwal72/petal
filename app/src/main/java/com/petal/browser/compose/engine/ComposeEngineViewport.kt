package com.petal.browser.compose.engine

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.PetalPermissionDelegateWrapper
import com.petal.browser.browser.PetalPromptDelegateWrapper
import com.petal.browser.browser.PetalVerticalSwipeRefreshLayout
import com.petal.browser.compose.composable.PetalRefreshBarState
import com.petal.browser.compose.composable.RefreshBarLoadingIndicatorLazy
import com.petal.browser.compose.menu.BrowserContextMenuManager
import com.petal.browser.engine.gecko.PetalContentDelegateWrapper
import com.petal.browser.engine.gecko.PetalEngineBridge
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.ui.glass.GeckoGlassBackend
import com.petal.browser.ui.selection.PetalPageSelectionToolbar
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.TabThumbnailCache
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.SwipeRefreshFeature
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * ComposeEngineViewport
 * ─────────────────────────────────────────────────────────────────────────────
 * Modern Jetpack Compose wrapper hosting Mozilla's [EngineView] (GeckoView 159).
 *
 * Provides direct GPU compositing for Petal's Liquid Glass and Material 3
 * Expressive layers with zero legacy ViewTree hacks. Fully wires Mozilla HitResult
 * context menus, text selection toolbars, prompt/permission handlers, and dynamic
 * toolbar heights for smooth scrolling and clean status bar padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEngineViewport(
    tab: TabSessionState?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
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

    // ── Context Menu Observer ──────────────────────────────────────────────────
    // Listens to BrowserStore HitResult emissions from GeckoEngineSession for the active tab,
    // consumes the result atomically, and surfaces Petal's Material 3 Expressive / Liquid Glass context menu.
    DisposableEffect(tab?.id) {
        val tabId = tab?.id
        if (tabId == null) {
            onDispose { }
        } else {
            val store = PetalEngineStore.getStore(context)
            val sub = store.observeManually { state ->
                val currentTab = state.tabs.firstOrNull { it.id == tabId } ?: return@observeManually
                val hit = currentTab.content.hitResult ?: return@observeManually
                store.dispatch(ContentAction.ConsumeHitResultAction(tabId))
                val act = (context as? MainActivity)
                    ?: (context as? ContextWrapper)?.baseContext as? MainActivity
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    act.runOnUiThread {
                        BrowserContextMenuManager.handleContextMenu(
                            activity = act,
                            hitResult = hit,
                            pageUrl = currentTab.content.url,
                            pageTitle = currentTab.content.title
                        )
                    }
                }
            }.also { it.resume() }
            onDispose {
                sub.unsubscribe()
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
                gv.isNestedScrollingEnabled = true
                gv.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                try {
                    GeckoGlassBackend.applyBackend(gv, context)
                } catch (_: Throwable) {}
            }
        }
    }

    // Inform GeckoView of dynamic toolbar height for smooth scrolling and proper viewport calculations
    val totalToolbarHeightPx = with(density) { (topPadding + bottomPadding).roundToPx() }
    LaunchedEffect(totalToolbarHeightPx, engineView) {
        try { engineView.setDynamicToolbarMaxHeight(totalToolbarHeightPx) } catch (_: Throwable) {}
        findGeckoView(engineView.asView())?.let { gv ->
            try { gv.setDynamicToolbarMaxHeight(totalToolbarHeightPx) } catch (_: Throwable) {}
        }
    }

    // Bind current active tab session and attach delegates
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
            val gs = PetalEngineBridge.extractGeckoSession(session)
            if (gs != null) {
                attachSessionDelegates(
                    context = context,
                    session = gs,
                    tab = tab,
                    engineView = engineView
                )
            }
        }
    }

    // Capture webpage thumbnail into TabThumbnailCache upon load completion.
    LaunchedEffect(tab?.id, isLoading, tab?.content?.url) {
        if (tab == null || isLoading) return@LaunchedEffect
        if (PetalBrowserUtils.isHomePage(tab.content.url)) {
            // The tab went back to the home page: its old website thumbnail is now wrong.
            TabThumbnailCache.remove(tab.id)
            return@LaunchedEffect
        }
        if (tab.content.url.isNotBlank()) {
            kotlinx.coroutines.delay(250L)
            findGeckoView(engineView.asView())?.let { gv ->
                captureGeckoThumbnail(context, gv, tab.id, tab.content.private)
            }
        }
    }

    // Lets the activity refresh this thumbnail right before the tab manager opens.
    DisposableEffect(tab?.id) {
        val id = tab?.id
        if (id != null) {
            PetalViewportThumbnailer.capture = {
                val current = PetalEngineStore.getStore(context).state.tabs.firstOrNull { it.id == id }
                if (current != null && !PetalBrowserUtils.isHomePage(current.content.url)) {
                    findGeckoView(engineView.asView())?.let { gv ->
                        captureGeckoThumbnail(context, gv, id, current.content.private)
                    }
                }
            }
        }
        onDispose {
            if (PetalViewportThumbnailer.capture != null && id != null) PetalViewportThumbnailer.capture = null
        }
    }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
        )

        RefreshBarLoadingIndicatorLazy(
            isRefreshing = refreshBarState.isRefreshing,
            pullProgressProvider = { refreshBarState.pullProgress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding + 8.dp)
        )
    }
}

private fun attachSessionDelegates(
    context: Context,
    session: GeckoSession,
    tab: TabSessionState,
    engineView: EngineView,
) {
    val actSupplier: () -> MainActivity? = {
        (context as? MainActivity) ?: (context as? ContextWrapper)?.baseContext as? MainActivity
    }

    // 1. Text Selection Action Delegate
    session.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
        override fun onShowActionRequest(
            session: GeckoSession,
            selection: GeckoSession.SelectionActionDelegate.Selection
        ) {
            val act = actSupplier() ?: return
            val anchorView: View = findGeckoView(engineView.asView()) ?: engineView.asView()
            if (PetalPageSelectionToolbar.show(act, anchorView, selection)) {
                return
            }
            val text = selection.text
            if (!text.isNullOrBlank()) {
                act.runOnUiThread {
                    BrowserContextMenuManager.showSelectionContextMenu(act, text)
                }
            }
        }

        override fun onHideAction(session: GeckoSession, reason: Int) {
            val act = actSupplier()
            if (act != null) PetalPageSelectionToolbar.hide(act)
        }
    }

    // 2. Content Delegate (wraps Mozilla's delegate for onContextMenu, fullScreen, crashes)
    val currentContent = session.contentDelegate
    if (currentContent is PetalContentDelegateWrapper) {
        currentContent.rebind(
            contextProvider = { context },
            isPrivateProvider = { tab.content.private },
            referrerProvider = { tab.content.url },
            onCrashCallback = { },
            pageUrlProvider = { tab.content.url },
            pageTitleProvider = { tab.content.title }
        )
    } else {
        session.contentDelegate = PetalContentDelegateWrapper(
            engine = currentContent,
            contextProvider = { context },
            isPrivateProvider = { tab.content.private },
            referrerProvider = { tab.content.url },
            onCrashCallback = { },
            pageUrlProvider = { tab.content.url },
            pageTitleProvider = { tab.content.title }
        )
    }

    // 3. Prompt Delegate (prompts, alerts, auth, popups)
    val currentPrompt = session.promptDelegate
    if (currentPrompt is PetalPromptDelegateWrapper) {
        currentPrompt.rebind(
            newIncognitoProvider = { tab.content.private },
            newActivityProvider = actSupplier
        )
    } else {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        session.promptDelegate = PetalPromptDelegateWrapper(
            engine = currentPrompt,
            session = session,
            appContext = context.applicationContext,
            sp = sp,
            incognitoProvider = { tab.content.private },
            activityProvider = actSupplier
        )
    }

    // 4. Permission Delegate
    val currentPerm = session.permissionDelegate
    if (currentPerm is PetalPermissionDelegateWrapper) {
        currentPerm.rebind(actSupplier)
    } else {
        session.permissionDelegate = PetalPermissionDelegateWrapper(
            engine = currentPerm,
            context = context.applicationContext,
            activitySupplier = actSupplier
        )
    }

    // 5. Extensions
    PetalExtensionManager.attachSession(session)
    PetalExtensionManager.setActiveBrowserSession(null, session)
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

/** Hook used by MainActivity to capture the Compose-hosted tab just before the tab manager opens. */
object PetalViewportThumbnailer {
    @Volatile
    @JvmField
    var capture: (() -> Unit)? = null
}

private fun captureGeckoThumbnail(context: android.content.Context, gv: GeckoView, tabId: String, isIncognito: Boolean) {
    if (gv.width <= 0 || gv.height <= 0) return
    // Same gate as Firefox's BrowserThumbnails: only the SELECTED tab, and only after first
    // contentful paint. Without it a tab switched away during the delay got the NEW tab's pixels
    // stored under its own id (wrong thumbnail) and half-painted white frames were kept.
    val store = PetalEngineStore.getStore(context)
    if (store.state.selectedTabId != tabId) return
    if (store.state.tabs.firstOrNull { it.id == tabId }?.content?.firstContentfulPaint != true) return
    try {
        gv.capturePixels().then({ bitmap: Bitmap? ->
            if (bitmap != null) {
                TabThumbnailCache.DISK.execute {
                    // The tab may have changed while the pixels were in flight.
                    if (store.state.selectedTabId != tabId) {
                        if (!bitmap.isRecycled) bitmap.recycle()
                        return@execute
                    }
                    val w = bitmap.width
                    val h = bitmap.height
                    if (w <= 0 || h <= 0) return@execute
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
