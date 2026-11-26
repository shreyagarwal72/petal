package com.petal.browser.compose.engine

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.lerp
import com.petal.browser.ui.components.BarCollapseState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.PetalPermissionDelegateWrapper
import com.petal.browser.browser.PetalVerticalSwipeRefreshLayout
import com.petal.browser.compose.composable.PetalRefreshBarState
import com.petal.browser.ui.components.PetalPullRefreshIndicator
import com.petal.browser.ui.components.PetalPullRefreshNativeTheme
import com.petal.browser.compose.menu.BrowserContextMenuManager
import com.petal.browser.engine.gecko.PetalContentDelegateWrapper
import com.petal.browser.engine.gecko.PetalEngineBridge
import com.petal.browser.engine.gecko.PetalEngineStore
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
 * Modern Jetpack Compose wrapper hosting Mozilla's [EngineView] (GeckoView 161).
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
    // Padding the page keeps once the bars are minimized. The page grows into the freed space
    // as BarCollapseState.collapsedFraction goes 0 -> 1, so no empty band is left behind.
    collapsedTopPadding: Dp = topPadding,
    collapsedBottomPadding: Dp = bottomPadding,
    onEngineViewReady: ((View) -> Unit)? = null,
    onFirstFrameDrawn: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val act = (context as? MainActivity) ?: (context as? ContextWrapper)?.baseContext as? MainActivity

    // Scroll-driven bar collapse is decided in ONE place: the Gecko ScrollDelegate below, through
    // BarCollapseState.scrollDecider. A nested-scroll connection used to fire act.animateAddressBarCollapse
    // from single events, a second unguarded trigger that fought the delegate and made slow scrolls flip.
    DisposableEffect(Unit) {
        BarCollapseState.composeViewportOwnsScroll = true
        onDispose { BarCollapseState.composeViewportOwnsScroll = false }
    }
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
            isPageLoading = { currentIsLoading }
            onPullProgress = { refreshBarState.pullProgress = it }
            onRefreshStateChanged = { refreshing ->
                refreshBarState.isRefreshing = refreshing
                if (!refreshing) refreshBarState.pullProgress = 0f
            }
        }
    }

    // Hide the loader immediately when the page finishes loading, regardless of the 5s watchdog.
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            swipeLayout.forceStopRefresh()
            refreshBarState.isRefreshing = false
            refreshBarState.pullProgress = 0f
        }
    }

    // Safety net, same as PetalTabViewController: the feature only hides the spinner when the tab's
    // `loading` flag CHANGES. If a reload ends between two updates, drop the loader after a few seconds.
    LaunchedEffect(refreshBarState.isRefreshing) {
        if (refreshBarState.isRefreshing) {
            kotlinx.coroutines.delay(5_000L)
            if (!currentIsLoading && (swipeLayout.isRefreshing || refreshBarState.isRefreshing)) {
                swipeLayout.forceStopRefresh()
                refreshBarState.isRefreshing = false
                refreshBarState.pullProgress = 0f
            }
        }
    }

    // Pull is only available while a real web page is shown (matching SwipeRefreshFeature).
    SideEffect {
        swipeLayout.isEnabled = tab != null &&
            !PetalBrowserUtils.isHomePage(tab.content.url) &&
            !tab.content.fullScreen &&
            !tab.content.isPdf
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
            ev.asView().id = com.petal.browser.R.id.main_content
            ev.asView().background = null
            ev.asView().isNestedScrollingEnabled = true
            findGeckoView(ev.asView())?.let { gv ->
                gv.background = null
                gv.isNestedScrollingEnabled = true
                // OVER_SCROLL_ALWAYS lets Gecko report vertical overscroll (canOverscrollTop() == true)
                // on short non-scrollable pages too, matching official Firefox pull-to-refresh.
                gv.overScrollMode = View.OVER_SCROLL_ALWAYS
                try {
                    GeckoGlassBackend.applyBackend(gv, context)
                } catch (_: Throwable) {}
                // Finger state for BarScrollDecider. Returns false: GeckoView still handles every event.
                BarCollapseState.scrollDecider.touchTrackingAvailable = true
                gv.setOnTouchListener { _, ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            BarCollapseState.scrollDecider.onTouch(true)
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            BarCollapseState.scrollDecider.onTouch(false)
                            BarCollapseState.isPageScrolling = false
                            BarCollapseState.onGeckoScrollEnd()
                        }
                    }
                    false
                }
                // Tells the settle tracker when a real viewport resize actually laid out.
                gv.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if ((bottom - top) != (oldBottom - oldTop) || (right - left) != (oldRight - oldLeft)) {
                        BarCollapseState.onViewportLayoutApplied()
                    }
                }
            }
        }
    }

    LaunchedEffect(engineView) {
        onEngineViewReady?.invoke(engineView.asView())
    }

    // Resample page edge colors when scroll stops cleanly and track the legacy padding fallback pref.
    var legacyPaddingViewport by remember {
        mutableStateOf(
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(BarCollapseState.PREF_LEGACY_PADDING_VIEWPORT, false)
        )
    }
    DisposableEffect(context) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        BarCollapseState.legacyPaddingViewport = legacyPaddingViewport
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == BarCollapseState.PREF_LEGACY_PADDING_VIEWPORT) {
                val enabled = prefs.getBoolean(BarCollapseState.PREF_LEGACY_PADDING_VIEWPORT, false)
                BarCollapseState.legacyPaddingViewport = enabled
                legacyPaddingViewport = enabled
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        GeckoGlassBackend.onScrollIdleCallback = { PageEdgeColors.requestResample() }
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(listener)
            GeckoGlassBackend.onScrollIdleCallback = null
        }
    }

    // Re-apply the GeckoView backend only when glass preferences change, never mid-scroll or inside AndroidView update.
    com.petal.browser.ui.glass.LocalPetalGlassRevision.current
    val barGlassEnabled = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_address_bar") ||
        com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav")
    LaunchedEffect(engineView, barGlassEnabled) {
        findGeckoView(engineView.asView())?.let { gv ->
            try {
                GeckoGlassBackend.applyBackend(gv, context)
            } catch (_: Throwable) {}
        }
    }

    // Drive collapse using GeckoView's dynamic toolbar API (setDynamicToolbarMaxHeight + setVerticalClipping)
    // so collapsing the bars never resizes View padding or reflows page content.
    // The old padding-resize path remains available behind BarCollapseState.PREF_LEGACY_PADDING_VIEWPORT.
    val fullTopPx = with(density) { topPadding.roundToPx() }
    val fullBottomPx = with(density) { bottomPadding.roundToPx() }
    val minTopPx = with(density) { collapsedTopPadding.roundToPx() }
    val minBottomPx = with(density) { collapsedBottomPadding.roundToPx() }
    val dynamicTopPx = (fullTopPx - minTopPx).coerceAtLeast(0)
    val dynamicBottomPx = (fullBottomPx - minBottomPx).coerceAtLeast(0)
    val dynamicToolbarMaxPx = dynamicTopPx + dynamicBottomPx
    val lastDynamicMaxHeight = remember { intArrayOf(Int.MIN_VALUE) }
    val lastVerticalClipping = remember { intArrayOf(Int.MIN_VALUE) }

    // Keep the Firefox-style spinner below the bars.
    SideEffect {
        swipeLayout.extraTopInsetPx = if (legacyPaddingViewport) fullTopPx else minTopPx
    }

    LaunchedEffect(
        engineView,
        swipeLayout,
        fullTopPx,
        fullBottomPx,
        minTopPx,
        minBottomPx,
        legacyPaddingViewport
    ) {
        val v = engineView.asView()
        if (legacyPaddingViewport) {
            if (lastDynamicMaxHeight[0] != 0) {
                lastDynamicMaxHeight[0] = 0
                try { engineView.setDynamicToolbarMaxHeight(0) } catch (_: Throwable) {}
            }
            if (lastVerticalClipping[0] != 0) {
                lastVerticalClipping[0] = 0
                try { engineView.setVerticalClipping(0) } catch (_: Throwable) {}
            }
            if (v.translationY != 0f) v.translationY = 0f
            snapshotFlow { BarCollapseState.viewportCollapsed to BarCollapseState.collapsedFraction }
                .collect { (collapsedLayout, f) ->
                    val top = if (collapsedLayout) minTopPx else fullTopPx
                    val bottom = if (collapsedLayout) minBottomPx else fullBottomPx
                    if (v.paddingTop != top || v.paddingBottom != bottom) {
                        BarCollapseState.onViewportResizeRequested()
                        v.setPadding(0, top, 0, bottom)
                    } else {
                        BarCollapseState.onViewportLayoutApplied()
                    }
                    val shift = if (collapsedLayout) (fullTopPx - minTopPx).coerceAtLeast(0) * (1f - f) else 0f
                    if (swipeLayout.translationY != shift) swipeLayout.translationY = shift
                }
        } else {
            if (v.paddingTop != minTopPx || v.paddingBottom != minBottomPx) {
                BarCollapseState.onViewportResizeRequested()
                v.setPadding(0, minTopPx, 0, minBottomPx)
            }
            if (v.translationY != 0f) v.translationY = 0f
            if (lastDynamicMaxHeight[0] != dynamicToolbarMaxPx) {
                lastDynamicMaxHeight[0] = dynamicToolbarMaxPx
                try { engineView.setDynamicToolbarMaxHeight(dynamicToolbarMaxPx) } catch (_: Throwable) {}
            }
            snapshotFlow {
                com.petal.browser.ui.components.BarCollapseMath.pageFraction(
                    com.petal.browser.ui.components.BarSettings.collapseStyle,
                    BarCollapseState.collapsedFraction
                )
            }
                .collect { f ->
                    val remaining = (1f - f).coerceIn(0f, 1f)
                    val topShift = dynamicTopPx * remaining
                    if (swipeLayout.translationY != topShift) swipeLayout.translationY = topShift
                    val clipPx = (dynamicToolbarMaxPx * remaining).roundToInt().coerceIn(0, dynamicToolbarMaxPx)
                    if (lastVerticalClipping[0] != clipPx) {
                        lastVerticalClipping[0] = clipPx
                        try { engineView.setVerticalClipping(clipPx) } catch (_: Throwable) {}
                    }
                }
        }
    }

    // Bind current active tab session and attach delegates
    LaunchedEffect(tab?.id, tab?.engineState?.engineSession) {
        if (tab == null) return@LaunchedEffect
        BarCollapseState.resetScrollSession()
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
            if (onFirstFrameDrawn != null) {
                androidx.compose.runtime.withFrameNanos { }
                onFirstFrameDrawn(tab.id)
            }
        }
    }

    // Capture webpage thumbnail into TabThumbnailCache upon load completion.
    // On homepage navigation keep the existing thumbnail intact until the unified capture refreshes it.
    LaunchedEffect(tab?.id, isLoading, tab?.content?.url) {
        if (tab == null || isLoading) return@LaunchedEffect
        if (PetalBrowserUtils.isHomePage(tab.content.url)) {
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
            PetalViewportThumbnailer.capturePagePixels = { callback ->
                val gv = findGeckoView(engineView.asView())
                captureGeckoViewPixels(gv, callback)
            }
            PetalViewportThumbnailer.capture = {
                val current = PetalEngineStore.getStore(context).state.tabs.firstOrNull { it.id == id }
                if (current != null) {
                    if (!PetalBrowserUtils.isHomePage(current.content.url)) {
                        findGeckoView(engineView.asView())?.let { gv ->
                            captureGeckoThumbnail(context, gv, id, current.content.private)
                        }
                    } else {
                        val root = (context as? android.app.Activity)?.findViewById<View>(android.R.id.content)
                            ?: (context as? android.app.Activity)?.window?.decorView
                        com.petal.browser.unit.TabThumbnailCapture.capture(
                            rootView = root,
                            tabId = id,
                            isPrivate = current.content.private,
                            isHome = true
                        )
                    }
                }
            }
        }
        onDispose {
            if (PetalViewportThumbnailer.capture != null && id != null) PetalViewportThumbnailer.capture = null
            if (PetalViewportThumbnailer.capturePagePixels != null && id != null) PetalViewportThumbnailer.capturePagePixels = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // The bars reserve a strip above/below the page. It used to show the bare theme
        // background (pure black on AMOLED), which read as a black bar around the glass. It now
        // takes the page's own edge colour, sampled from Gecko, so bars and page look continuous.
        val fallback = MaterialTheme.colorScheme.background
        val topStripState = animateColorAsState(
            if (PageEdgeColors.top == Color.Unspecified) fallback else PageEdgeColors.top, label = "pageEdgeTop"
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topPadding)
                .drawBehind {
                    val h = lerp(topPadding.toPx(), collapsedTopPadding.toPx(), BarCollapseState.collapsedFraction)
                    if (h > 0f) drawRect(color = topStripState.value, size = Size(size.width, h))
                }
        )

        val darkThemeNow = isSystemInDarkTheme()
        val themeBgArgb = fallback.toArgb()
        LaunchedEffect(engineView, tab?.id, tab?.content?.url, isLoading) {
            if (!isLoading) { PageEdgeColors.requestResample() }
        }
        LaunchedEffect(engineView, darkThemeNow, themeBgArgb) {
            PageEdgeColors.requestResample()
        }
        LaunchedEffect(engineView) {
            PageEdgeColors.ticks.collectLatest {
                delay(280L)
                if (GeckoGlassBackend.isScrollingOrFlinging()) {
                    snapshotFlow { GeckoGlassBackend.isScrollingOrFlinging() }.first { !it }
                }
                findGeckoView(engineView.asView())?.let { PageEdgeColors.sample(it) }
            }
        }

        AndroidView(
            factory = {
                // Like Fenix, the EngineView is a direct child of the swipe-refresh layout:
                // SwipeRefreshFeature inspects that child to ask Gecko if the page can still scroll up.
                val view = engineView.asView()
                val collapsedLayout = BarCollapseState.viewportCollapsed
                view.setPadding(
                    0,
                    if (BarCollapseState.legacyPaddingViewport && !collapsedLayout) fullTopPx else minTopPx,
                    0,
                    if (BarCollapseState.legacyPaddingViewport && !collapsedLayout) fullBottomPx else minBottomPx
                )
                (view as? ViewGroup)?.clipToPadding = false
                (view as? ViewGroup)?.clipChildren = false
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
                swipeLayout.clipToPadding = false
                swipeLayout.clipChildren = false
                swipeLayout
            },
            update = {
                // Session binding and glass backend setup happen once in LaunchedEffect(engineView)
                // and when tab/session changes; avoid per-recomposition AndroidView work.
            },
            modifier = Modifier
                .fillMaxSize()
        )

        // Style comes from PetalLayoutState (live). FIREFOX draws nothing here: the layout's own
        // SwipeRefreshLayout circle is shown instead, themed from the live MaterialTheme.
        PetalPullRefreshNativeTheme(swipeLayout)
        PetalPullRefreshIndicator(
            isRefreshing = refreshBarState.isRefreshing,
            pullProgressProvider = { refreshBarState.pullProgress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding)
                .graphicsLayer {
                    translationY = -dynamicTopPx * com.petal.browser.ui.components.BarCollapseMath.pageFraction(
                        com.petal.browser.ui.components.BarSettings.collapseStyle,
                        BarCollapseState.collapsedFraction
                    )
                }
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
    val crashCallback: () -> Unit = {
        (context as? android.app.Activity)?.runOnUiThread {
            try {
                android.util.Log.i("ComposeEngineViewport", "Recovering terminated GeckoSession for tab ${tab.id}")
                try {
                    engineView.release()
                } catch (_: Throwable) {}
                try {
                    if (session.isOpen) session.close()
                } catch (_: Throwable) {}

                val store = PetalEngineStore.getStore(context)
                val eng = PetalEngineStore.getEngine(context)
                val newEngineSession = eng.createSession(private = tab.content.private)
                store.dispatch(
                    mozilla.components.browser.state.action.EngineAction.LinkEngineSessionAction(
                        tabId = tab.id,
                        engineSession = newEngineSession,
                        skipLoading = true
                    )
                )

                try {
                    engineView.render(newEngineSession)
                } catch (t: Throwable) {
                    android.util.Log.w("ComposeEngineViewport", "Error rendering recovered session: ${t.message}")
                }

                val newGs = PetalEngineBridge.extractGeckoSession(newEngineSession)
                if (newGs != null) {
                    attachSessionDelegates(
                        context = context,
                        session = newGs,
                        tab = tab,
                        engineView = engineView
                    )
                }

                val restoreUrl = tab.content.url
                if (restoreUrl.isNotBlank() && !PetalBrowserUtils.isHomePage(restoreUrl)) {
                    newEngineSession.loadUrl(restoreUrl)
                }
            } catch (t: Throwable) {
                android.util.Log.e("ComposeEngineViewport", "Failed to recover session after kill/crash", t)
            }
        }
    }
    val currentContent = session.contentDelegate
    if (currentContent is PetalContentDelegateWrapper) {
        currentContent.rebind(
            contextProvider = { context },
            isPrivateProvider = { tab.content.private },
            referrerProvider = { tab.content.url },
            onCrashCallback = crashCallback,
            pageUrlProvider = { tab.content.url },
            pageTitleProvider = { tab.content.title }
        )
    } else {
        session.contentDelegate = PetalContentDelegateWrapper(
            engine = currentContent,
            contextProvider = { context },
            isPrivateProvider = { tab.content.private },
            referrerProvider = { tab.content.url },
            onCrashCallback = crashCallback,
            pageUrlProvider = { tab.content.url },
            pageTitleProvider = { tab.content.title }
        )
    }

    // 3. Prompt Delegate:
    // Retained on Mozilla AC's GeckoPromptDelegate (browser-engine-gecko).
    // Web content prompt requests are dispatched via PromptMiddleware into BrowserStore
    // (tab.content.promptRequests) and presented through PetalBrowserPromptBridge
    // using Petal's Liquid Glass / Material 3 Expressive UI with zero reflection hacks.

    // 4. Permission Delegate
    val currentPerm = session.permissionDelegate
    if (currentPerm is PetalPermissionDelegateWrapper) {
        currentPerm.rebind(
            newActivitySupplier = actSupplier,
            newIsPrivateProvider = { tab.content.private }
        )
    } else {
        session.permissionDelegate = PetalPermissionDelegateWrapper(
            engine = currentPerm,
            context = context.applicationContext,
            activitySupplier = actSupplier,
            isPrivateProvider = { tab.content.private }
        )
    }

    // 5. Scroll Delegate for Address Bar collapse. Installed once per session: re-attaching only
    // refreshes the supplier, so repeated attachSessionDelegates calls cannot stack decision makers.
    val existingScroll = session.scrollDelegate
    if (existingScroll is PetalBarScrollDelegate) {
        existingScroll.actSupplier = actSupplier
    } else {
        session.scrollDelegate = PetalBarScrollDelegate(existingScroll, actSupplier, context)
    }

    // 6. Extensions: bound by WebExtensionSupport from the BrowserStore (the tab's engine session is
    //    linked and selected through the store), so nothing is attached here.
}

/**
 * Feeds Gecko scroll offsets to [BarCollapseState.scrollDecider], the only thing that decides bar
 * collapse. Mozilla's own delegate is kept in [chained] so the BrowserStore still gets scroll events.
 */
private class PetalBarScrollDelegate(
    private val chained: GeckoSession.ScrollDelegate?,
    var actSupplier: () -> MainActivity?,
    private val context: Context,
) : GeckoSession.ScrollDelegate {

    private val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    @Volatile
    private var hideBarsOnScroll: Boolean = sp.getBoolean("sp_hide_bars_on_scroll", true)

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "sp_hide_bars_on_scroll") {
            hideBarsOnScroll = prefs.getBoolean("sp_hide_bars_on_scroll", true)
        }
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
        try { chained?.onScrollChanged(session, scrollX, scrollY) } catch (_: Throwable) {}
        val decider = BarCollapseState.scrollDecider
        if (decider.isTouching()) {
            BarCollapseState.isPageScrolling = true
        }
        GeckoGlassBackend.notifyScrollActivity(flinging = decider.isFlingCandidate())
        val act = actSupplier() ?: return
        if (!hideBarsOnScroll) {
            // Preference off: bars stay open. animateAddressBarCollapse(false) re-opens them if needed.
            decider.reset()
            if (act.isAddressBarCollapsed()) act.runOnUiThread { act.animateAddressBarCollapse(false) }
            return
        }
        val decision = decider.onScroll(scrollY, context.resources.displayMetrics.density, act.isAddressBarCollapsed())
        if (decision != 0) act.runOnUiThread { act.animateAddressBarCollapse(decision > 0) }
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

/** Hook used by MainActivity to capture the Compose-hosted tab just before the tab manager opens. */
object PetalViewportThumbnailer {
    @Volatile
    @JvmField
    var capture: (() -> Unit)? = null

    @Volatile
    var capturePagePixels: (((Bitmap?) -> Unit) -> Unit)? = null

    @Volatile
    private var lastCaptureUptimeMs = 0L

    internal fun canCapturePixelsNow(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastCaptureUptimeMs < 1200L) return false
        lastCaptureUptimeMs = now
        return true
    }
}

private fun captureGeckoViewPixels(gv: GeckoView?, callback: (Bitmap?) -> Unit) {
    val doCapture = {
        if (gv == null || !gv.isAttachedToWindow || gv.width <= 0 || gv.height <= 0 || GeckoGlassBackend.isFlinging) {
            callback(null)
        } else if (!PetalViewportThumbnailer.canCapturePixelsNow()) {
            callback(null)
        } else {
            PageEdgeColors.markCaptured()
            try {
                gv.capturePixels().then({ bitmap: Bitmap? ->
                    callback(bitmap)
                    GeckoResult.fromValue<Void?>(null)
                }, {
                    callback(null)
                    GeckoResult.fromValue<Void?>(null)
                })
            } catch (_: Throwable) {
                callback(null)
            }
        }
    }
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        doCapture()
    } else {
        android.os.Handler(android.os.Looper.getMainLooper()).post { doCapture() }
    }
}

private fun captureGeckoThumbnail(context: android.content.Context, gv: GeckoView, tabId: String, isIncognito: Boolean) {
    if (gv.width <= 0 || gv.height <= 0 || GeckoGlassBackend.isFlinging) return
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


/**
 * Colour of the page right under the top and bottom bars, sampled from a Gecko frame. Used to
 * fill the strips the bars reserve, so no black band shows between the page and the glass.
 */
object PageEdgeColors {
    var top by mutableStateOf(Color.Unspecified)
    var bottom by mutableStateOf(Color.Unspecified)
    internal val ticks = MutableStateFlow(0L)

    fun requestResample() { ticks.value = ticks.value + 1 }

    @Volatile private var lastSampleUptimeMs = 0L

    internal fun markCaptured() {
        lastSampleUptimeMs = android.os.SystemClock.uptimeMillis()
    }

    internal fun sample(gv: GeckoView) {
        if (BarCollapseState.isPageScrolling) return
        if (gv.width <= 0 || gv.height <= 0) return
        if (GeckoGlassBackend.isScrollingOrFlinging()) return
        // capturePixels is not free: never more than once per 1.2 s.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSampleUptimeMs < 1200L) return
        lastSampleUptimeMs = now
        try {
            val padTop = gv.paddingTop
            val padBottom = gv.paddingBottom
            gv.capturePixels().then({ bmp: Bitmap? ->
                if (bmp != null && bmp.width > 4 && bmp.height > 4) {
                    // The frame may or may not include the padding; pick rows just inside the
                    // page either way by clamping to the bitmap.
                    val scale = bmp.height.toFloat() / gv.height.coerceAtLeast(1)
                    val yTop = ((padTop * scale).toInt() + 3).coerceIn(0, bmp.height - 1)
                    val yBottom = (bmp.height - 1 - (padBottom * scale).toInt() - 3).coerceIn(0, bmp.height - 1)
                    val xs = intArrayOf(bmp.width / 8, bmp.width / 2, bmp.width * 7 / 8)
                    fun avg(y: Int): Color {
                        var r = 0; var g = 0; var b = 0
                        for (x in xs) {
                            val c = bmp.getPixel(x, y)
                            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                        }
                        return Color(0xFF shl 24 or ((r / 3) shl 16) or ((g / 3) shl 8) or (b / 3))
                    }
                    val t = avg(yTop)
                    val b = avg(yBottom)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        top = t
                        bottom = b
                    }
                }
                GeckoResult.fromValue<Void?>(null)
            }, {
                GeckoResult.fromValue<Void?>(null)
            })
        } catch (_: Throwable) {}
    }
}
