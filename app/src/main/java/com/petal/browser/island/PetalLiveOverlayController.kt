package com.petal.browser.island

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.app.Application
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.getSystemService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Draws the expressive live-update island on top of other apps.
 *
 * It is meant for devices with no Android 16 Live Updates, but "Always show island" lets it run
 * next to the native chip too. It only appears when ALL of these are true:
 *   1. the "Show over other apps" toggle is on
 *   2. Android's "Display over other apps" permission is granted
 *   3. Petal is NOT on screen (inside Petal the in-app island is used, so you never see two)
 *   4. there is an active download / media / task
 *   5. the presentation mode allows an island
 *
 * The window is WRAP_CONTENT and sits just under the status bar. Because it is only as big as the
 * island, taps anywhere else go straight to the app underneath. Revoking the permission or
 * flipping the toggle removes it immediately.
 */
object PetalLiveOverlayController {

    private const val TAG = "PetalLiveOverlay"
    private const val EXIT_ANIMATION_MS = 700L

    private data class OverlayUi(val bus: LiveActionBusState, val settings: IslandSettings)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ui = MutableStateFlow(OverlayUi(LiveActionBusState(), IslandSettings()))

    private lateinit var app: Application
    private var started = false
    private var view: ComposeView? = null
    private var owner: PetalOverlayLifecycleOwner? = null
    private var removeJob: Job? = null

    /** Call once from Application.onCreate() on the main process. Idle and cheap until enabled. */
    fun init(application: Application) {
        if (started) return
        started = true
        app = application
        scope.launch {
            combine(
                IslandSettings.flow(app),
                PetalLiveActionBus.state,
                PetalForegroundTracker.inForeground
            ) { settings, bus, foreground -> Triple(settings, bus, foreground) }
                .collect { (settings, bus, foreground) -> evaluate(settings, bus, foreground) }
        }
    }

    private fun evaluate(settings: IslandSettings, bus: LiveActionBusState, foreground: Boolean) {
        val allowed = settings.overlayEnabled &&
            !foreground &&
            PetalIslandPermissions.canDrawOverlays(app) &&
            PetalIslandPermissions.shouldShowIsland(app, settings)

        if (allowed && bus.hasActiveAction) {
            removeJob?.cancel()
            removeJob = null
            if (ensureAttached()) ui.value = OverlayUi(bus, settings)
        } else if (view != null) {
            // Petal came to the front: the in-app island takes over, so drop the window at once.
            if (foreground) {
                removeJob?.cancel()
                detach()
                return
            }
            // Otherwise feed an empty state so the island animates out, then drop the window.
            ui.value = OverlayUi(LiveActionBusState(), settings)
            if (removeJob?.isActive != true) {
                removeJob = scope.launch {
                    delay(EXIT_ANIMATION_MS)
                    detach()
                }
            }
        }
    }

    private fun ensureAttached(): Boolean {
        if (view != null) return true
        val wm = app.getSystemService<WindowManager>() ?: return false

        val lifecycleOwner = PetalOverlayLifecycleOwner().also { it.onCreate() }
        val actions = PetalOverlayActions(app)

        val composeView = ComposeView(app).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PetalExpressiveTheme {
                    val current by ui.collectAsStateWithLifecycle()
                    PetalLiveIsland(
                        state = current.bus,
                        settings = current.settings,
                        listener = actions
                    )
                }
            }
        }

        return try {
            wm.addView(composeView, layoutParams())
            view = composeView
            owner = lifecycleOwner
            true
        } catch (t: Throwable) {
            // Permission revoked mid-flight, OEM restriction, etc. Fail quietly.
            Log.w(TAG, "Could not show live island overlay", t)
            lifecycleOwner.onDestroy()
            false
        }
    }

    private fun detach() {
        val v = view ?: return
        try {
            app.getSystemService<WindowManager>()?.removeViewImmediate(v)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed", t)
        }
        try { v.disposeComposition() } catch (_: Throwable) {}
        owner?.onDestroy()
        view = null
        owner = null
        removeJob = null
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeightPx()
            title = "PetalLiveIsland"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun statusBarHeightPx(): Int {
        val res = app.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else (24 * res.displayMetrics.density).toInt()
    }
}
