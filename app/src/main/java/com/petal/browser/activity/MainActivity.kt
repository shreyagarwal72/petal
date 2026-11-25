package com.petal.browser.activity

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.VideoView
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.petal.browser.R
import com.petal.browser.account.sync.PetalFolderSync
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserController
import com.petal.browser.browser.PetalBrowserFeatures
import com.petal.browser.browser.PetalSessionRestoreManager
import com.petal.browser.browser.PetalTabSurface
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.PetalRootApp
import com.petal.browser.compose.composable.PetalRefreshBarState
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.database.Record
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.media.BrowserMediaDelegate
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.media.PetalMediaSessionService
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.view.RecordListAdapter
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity
 * ─────────────────────────────────────────────────────────────────────────────
 * The modern, lightweight 100% Jetpack Compose Entry Point for Petal Browser.
 *
 * Fully replaces the legacy 8,560-line BrowserActivity. All UI rendering,
 * window insets, Material 3 Expressive theme tokens, and Liquid Glass composition
 * run entirely in Jetpack Compose, while Mozilla's [BrowserStore], [GeckoView],
 * and [PetalBrowserFeatures] power engine features, prompts, and site permissions.
 *
 * Implements [BrowserController] and provides legacy delegation stubs to ensure
 * seamless compatibility across all remaining delegates without needing BrowserActivity.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity(), BrowserController {

    val viewModel: PetalBrowserViewModel by viewModels()
    var browserFeatures: PetalBrowserFeatures? = null

    // Compatibility fields accessed by subsystem delegates
    val activity: Activity get() = this
    val context: Context get() = this
    val sp: android.content.SharedPreferences by lazy {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
    }
    var currentAlbumController: AlbumController? = null
    var currentVideoWidth: Int = 0
    var currentVideoHeight: Int = 0
    var isMediaPlaying: Boolean = false
    var isOverlayScreenShowing: Boolean = false
    var isCustomFullscreenState: Boolean = false
    var isDecorOverlayShowing: Boolean = false
    var isPetalHomeSurfaceShowing: Boolean = false
    private var pillMorphAnimator: android.animation.ValueAnimator? = null
    private var isAddressBarCollapsedState = false

    fun isBarAnimationRunning(): Boolean = pillMorphAnimator?.isStarted == true
    fun isAddressBarCollapsed(): Boolean = isAddressBarCollapsedState
    var pendingOverlayBackAction: Runnable? = null
    var mediaService: PetalMediaSessionService? = null
    val activeMediaBridge: PetalMediaBridge? = null
    val refreshState: PetalRefreshBarState = PetalRefreshBarState()
    var tab_container: LinearLayout? = null
    var fullscreenHolder: FrameLayout? = null
    var customView: View? = null
    var videoView: VideoView? = null
    var bottom_navigation: BottomNavigationView? = null
    var fab_menu: FloatingActionButton? = null
    var contentOffsetApplied: Float = 0f
    var contentPaddingAnimator: androidx.dynamicanimation.animation.SpringAnimation? = null
    var overViewTab: String? = null
    var filter: Boolean = false
    var filterBy: Long = 0L
    var newIcon: Long = 0L
    var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var dialogOverview: androidx.appcompat.app.AlertDialog? = null
    var search_input: TextInputEditText? = null
    private var singleTabViewController: PetalTabViewController? = null

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sp_immersive_fullscreen" -> {
                runOnUiThread { applyImmersiveFullscreen() }
            }
            com.petal.browser.unit.PetalHighRefreshRateManager.PREF_HIGH_REFRESH_RATE -> {
                runOnUiThread { com.petal.browser.unit.PetalHighRefreshRateManager.applyHighRefreshRate(this) }
            }
        }
    }

    fun applyImmersiveFullscreen() {
        val immersive = isCustomFullscreenState || sp.getBoolean("sp_immersive_fullscreen", false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }



    companion object {
        const val INPUT_FILE_REQUEST_CODE = 1

        @Deprecated("Use PetalApplication.getAppContext() directly")
        @JvmStatic
        fun getAppContext(): Context? = com.petal.browser.PetalApplication.getAppContext()

        @JvmStatic
        fun isExternalOrWidgetLaunch(intent: Intent?): Boolean = false

        @JvmStatic
        fun shouldSkipLaunchSplash(intent: Intent?): Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { provider ->
            try {
                provider.view.isClickable = false
                provider.view.isFocusable = false
                // Let the animated icon finish its run (the system may report "ready" earlier), then
                // fade the splash out instead of cutting it off mid-animation.
                val remaining = (provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis -
                    System.currentTimeMillis()).coerceIn(0L, 1000L)
                provider.view.postDelayed({
                    try {
                        provider.view.animate().alpha(0f).setDuration(180L)
                            .withEndAction { runCatching { provider.remove() } }
                            .start()
                    } catch (_: Throwable) {
                        runCatching { provider.remove() }
                    }
                }, remaining)
            } catch (_: Throwable) {
                runCatching { provider.remove() }
            }
        }
        super.onCreate(savedInstanceState)
        com.petal.browser.browser.PetalTabMetadataStore.preload(this)
        // Keep the next cold-start splash in sync with the selected app icon (Android 13+; no-op elsewhere).
        com.petal.browser.appicon.PetalAppIcons.syncSplashTheme(this)
        com.petal.browser.unit.PetalHighRefreshRateManager.applyHighRefreshRate(this)
        com.petal.browser.engine.gecko.PetalFileUploadMiddleware.attachActivity(this)
        enableEdgeToEdge()
        applyImmersiveFullscreen()
        sp.registerOnSharedPreferenceChangeListener(prefChangeListener)

        com.petal.browser.compose.splash.PetalSplashState.hasShownThisSession = true
        com.petal.browser.compose.splash.PetalSplashState.splashActive.value = false

        // Restore tabs from the saved session. This must NOT be gated on savedInstanceState == null:
        // after Android kills the process the activity is recreated WITH a saved state but with an
        // empty BrowserStore, and skipping the restore there opened a blank tab (and let AutoSave
        // overwrite the stored session). restoreSession() is a cheap no-op when the process is
        // alive and the store already holds tabs (rotation, theme change, etc.).
        run {
            val restored = com.petal.browser.browser.PetalSessionRestoreManager.restoreSession(this)
            val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(this)
            if (!restored && store.state.tabs.isEmpty()) {
                val action = intent?.action
                val willOpenTab = action == com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB ||
                    action == com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO
                if (!willOpenTab) {
                    viewModel.openNewTab("about:home", selectTab = true)
                }
            }
        }

        viewModel.onBeforeTabSwitcherOpen = { captureVisibleTabThumbnail() }

        setContent {
            PetalExpressiveTheme {
                PetalRootApp(viewModel = viewModel)
            }
        }

        // Allow the first browser frame to render before attaching optional engine features.
        // These initializers can trigger Gecko extension scans and service/store setup.
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            PetalExtensionManager.attach(applicationContext)
            PetalBuiltInExtensionManager.installAll(applicationContext)
            PetalFolderSync.onAppStart(this)
            browserFeatures = PetalBrowserFeatures(this).also { it.start() }
            handleIncomingIntent(intent)
            com.petal.browser.unit.PetalUpdateUtils.checkForUpdates(this, isLaunchCheck = true)
        }, 250L)
    }

    override fun onStop() {
        super.onStop()
        window.decorView.removeCallbacks(suspendSweep)
        try { com.petal.browser.engine.gecko.PetalTabSuspender.sweepIdleTabs(applicationContext) } catch (_: Throwable) {}
        try {
            // Drop sidecar metadata of tabs that are gone (keeping recently-closed ids for undo).
            val st = com.petal.browser.engine.gecko.PetalEngineStore.getStore(this).state
            if (st.restoreComplete) {
                com.petal.browser.browser.PetalTabMetadataStore.pruneKeeping(
                    applicationContext, st.tabs.map { it.id },
                    com.petal.browser.unit.PetalRecentlyClosedManager.getRecentlyClosedTabs().map { it.id }
                )
            }
        } catch (_: Throwable) {}
        com.petal.browser.account.sync.PetalFolderSync.onAppBackground(this)
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val clearQuit = sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)
        if (isFinishing && clearQuit) {
            com.petal.browser.unit.PetalBrowserUtils.clearOnExit(this, waitForCompletion = true)
        } else {
            com.petal.browser.browser.PetalSessionRestoreManager.saveSession(this, isStopping = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sp.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        com.petal.browser.engine.gecko.PetalFileUploadMiddleware.detachActivity(this)
        browserFeatures?.stop()
        browserFeatures = null
    }

    // Memory Saver: periodic store-driven sweep of idle background tabs while the app is visible.
    private val suspendSweep = object : Runnable {
        override fun run() {
            try { com.petal.browser.engine.gecko.PetalTabSuspender.sweepIdleTabs(applicationContext) } catch (_: Throwable) {}
            window?.decorView?.postDelayed(this, 60_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.removeCallbacks(suspendSweep)
        window.decorView.postDelayed(suspendSweep, 60_000L)
        // Scheduled auto-archive (setting, off by default): stale tabs go to Recently Closed.
        window.decorView.postDelayed({ try { viewModel.archiveStaleTabs() } catch (_: Throwable) {} }, 3_000L)
        com.petal.browser.unit.PetalHighRefreshRateManager.applyHighRefreshRate(this)
        applyImmersiveFullscreen()
        com.petal.browser.engine.gecko.PetalEngineStore.pendingTabSelectionOnResume?.let { pendingId ->
            com.petal.browser.engine.gecko.PetalEngineStore.pendingTabSelectionOnResume = null
            viewModel.selectTab(pendingId)
        }
        try {
            window.decorView.isFocusableInTouchMode = true
            window.decorView.requestFocus()
        } catch (_: Throwable) { }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        com.petal.browser.ui.components.PetalRuntimePermissionBridge.handlePermissionsResult(
            requestCode,
            permissions,
            grantResults
        )
        browserFeatures?.onPermissionsResult(requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (com.petal.browser.engine.gecko.PetalGeckoRuntime.handleActivityResult(requestCode, resultCode, data)) {
            return
        }
        if (browserFeatures?.onActivityResult(requestCode, resultCode, data) == true) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        browserFeatures?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subsystem Delegation & Navigation Methods
    // ─────────────────────────────────────────────────────────────────────────

    fun getOrCreateTabViewController(): PetalTabViewController {
        if (singleTabViewController == null) {
            singleTabViewController = PetalTabViewController(this)
        }
        return singleTabViewController!!
    }



    fun getCurrentTabSurface(): PetalTabSurface {
        return getOrCreateTabViewController()
    }

    @JvmOverloads
    fun addAlbum(title: String?, url: String?, foreground: Boolean = true, incognito: Boolean = false) {
        val effectiveUrl = if (url.isNullOrBlank()) "about:home" else url
        viewModel.openNewTab(effectiveUrl, selectTab = foreground, private = incognito)
        if (incognito) com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.INCOGNITO_STATE, "incognito", "Private tab", "History and cookies won't be saved")
    }

    fun addAlbumInGroup(title: String?, url: String?, foreground: Boolean, groupId: String?, groupTitle: String?) {
        val effectiveUrl = if (url.isNullOrBlank()) "about:home" else url
        viewModel.openNewTab(effectiveUrl, selectTab = foreground, private = false, groupId = groupId, groupTitle = groupTitle)
    }

    fun selectTab(controller: AlbumController?) {
        showAlbum(controller)
    }

    fun closeTab(controller: AlbumController?) {
        removeAlbum(controller)
    }

    fun switchAdjacentTab(direction: Int) {
        if (direction > 0) {
            viewModel.switchToNextTab()
        } else {
            viewModel.switchToPreviousTab()
        }
    }

    fun nextAlbumController(next: Boolean): AlbumController? {
        switchAdjacentTab(if (next) 1 else -1)
        return currentAlbumController
    }

    fun restoreTab(
        title: String?,
        url: String?,
        index: Int,
        isIncognito: Boolean,
        groupId: String?,
        groupTitle: String?,
        groupColorHex: String?,
        keepOverviewOpen: Boolean
    ): AlbumController? {
        val effectiveUrl = if (url.isNullOrBlank()) "about:home" else url
        viewModel.openNewTab(
            url = effectiveUrl,
            selectTab = false,
            private = isIncognito,
            groupId = groupId,
            groupTitle = groupTitle,
            keepSwitcherOpen = keepOverviewOpen
        )
        return null
    }

    fun saveOpenedTabs() {
        PetalSessionRestoreManager.saveSession(this, isStopping = false)
    }

    @JvmOverloads
    fun openSettingsScreen(category: SettingsCategory? = null) {
        viewModel.openSettings(category ?: SettingsCategory.OVERVIEW)
    }

    fun openApiIntegrationsHub() {
        viewModel.openSettings(SettingsCategory.API_INTEGRATIONS)
    }

    fun showDownloads() {
        viewModel.openDownloads()
    }

    fun showBookmarksPage() { viewModel.openBookmarks() }
    fun showBookmarks() { viewModel.openBookmarks() }
    fun showBookmarksSheet() { viewModel.openBookmarks() }
    fun showHistoryScreen() { viewModel.openHistory() }
    fun showOmniboxPage(initialQuery: String? = null) {
        viewModel.openOmnibox(initialQuery)
    }
    fun showExtensionsScreen() { viewModel.openExtensions() }
    fun showQuickTools() {
        com.petal.browser.ui.components.PetalQuickToolsBridge.showQuickTools(this)
    }
    fun showReaderMode() {
        val tab = viewModel.activeTab.value
        if (tab != null) {
            val curUrl = tab.content.url
            val isReader = curUrl.startsWith("about:reader?url=")
            com.petal.browser.engine.gecko.PetalEngineStore.toggleReaderMode(this, tab.id, !isReader)
            try {
                if (!isReader) {
                    viewModel.loadUrl("about:reader?url=${android.net.Uri.encode(curUrl)}")
                } else {
                    val origUrl = android.net.Uri.decode(curUrl.substringAfter("about:reader?url="))
                    viewModel.loadUrl(origUrl)
                }
            } catch (_: Throwable) {}
            com.petal.browser.live.PetalLiveActionBus.postSimple(
                com.petal.browser.live.LiveEventKind.READER_MODE,
                "reader",
                if (isReader) "Reader mode off" else "Reader mode on"
            )
            return
        }
        val controller = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
        if (controller != null) {
            val isReader = controller.url?.startsWith("about:reader?url=") == true
            controller.toggleReaderMode(!isReader)
            com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.READER_MODE, "reader", if (isReader) "Reader mode off" else "Reader mode on")
        }
    }
    fun showDeleteScreen() {
        viewModel.openDelete()
    }
    fun showSafeLocker() {
        viewModel.openSafeLocker()
    }
    /** Sends a link to another device that syncs to the same folder. */
    fun showSendToDevice(title: String, url: String) {
        if (!com.petal.browser.account.sync.PetalFolderSync.configured.value) {
            android.widget.Toast.makeText(this, "Set up sync in Account & Sync first", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val devices = com.petal.browser.account.sync.PetalFolderSync.devices.value
        if (devices.isEmpty()) {
            android.widget.Toast.makeText(this, "No other devices yet. Sync on your other device first.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Send to device")
            .setItems(devices.map { it.name }.toTypedArray()) { _, i ->
                com.petal.browser.account.sync.PetalFolderSync.sendTab(this, devices[i].id, title, url)
                android.widget.Toast.makeText(this, "Sending to ${devices[i].name}…", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    /** Refreshes the thumbnail of the tab on screen so the tab manager never shows a stale frame. */
    fun captureVisibleTabThumbnail() {
        try {
            val curTab = viewModel.activeTab.value
            if (curTab != null && com.petal.browser.unit.PetalBrowserUtils.isHomePage(curTab.content.url)) {
                val root = findViewById<View>(android.R.id.content) ?: window.decorView
                com.petal.browser.unit.TabThumbnailCapture.capture(
                    rootView = root,
                    tabId = curTab.id,
                    isPrivate = curTab.content.private,
                    isHome = true
                )
            } else {
                (currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.updatePreviewCache(true)
                com.petal.browser.compose.engine.PetalViewportThumbnailer.capture?.invoke()
            }
        } catch (_: Throwable) {}
    }
    fun captureBrowserMainPreview() {
        val root = findViewById<View>(android.R.id.content) ?: window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(root)
    }
    fun searchOnSite() {
        viewModel.openFindInPage()
    }
    fun setSelectedTab() {
        when (overViewTab) {
            getString(R.string.album_title_bookmarks) -> viewModel.openBookmarks()
            getString(R.string.album_title_history) -> viewModel.openHistory()
            else -> viewModel.setTabSwitcherOpen(true)
        }
    }
    fun showDialogFilter() {
        com.petal.browser.ui.components.BrowserDialogManager.showDialogFilter(this)
    }
    fun showDialogCustomSearches(url: String) {
        com.petal.browser.ui.components.BrowserDialogManager.showDialogCustomSearches(this, url)
    }
    fun doubleTapsQuit() { finish() }
    fun maybePromptDefaultBrowser() {
        com.petal.browser.ui.components.PetalBrowserPermissionDialog.maybePromptDefaultBrowser(this, false)
    }
    fun savePageOffline() {
        val controller = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
        if (controller != null) {
            val curUrl = controller.url.orEmpty()
            if (curUrl.isNotBlank() && !curUrl.startsWith("about:")) {
                try {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val titleSafe = (controller.title.ifBlank { "Page" }).replace(Regex("[^a-zA-Z0-9.-]"), "_")
                    val targetPdf = java.io.File(downloadsDir, "$titleSafe.pdf")
                    java.io.FileOutputStream(targetPdf).use { fos ->
                        controller.printToPdf(fos) { success ->
                            if (success) {
                                com.petal.browser.view.PetalToast.show(this, "Saved to Downloads: ${targetPdf.name}")
                            }
                        }
                    }
                } catch (_: Throwable) {
                    com.petal.browser.view.PetalToast.show(this, "Failed to save offline page")
                }
            }
        }
    }
    fun createWebPrintJob(webView: Any?) {
        val controller = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
        val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager == null || controller == null) {
            savePageOffline()
            return
        }
        val jobName = "${controller.title.ifBlank { "Petal Web Document" }} Document"
        val adapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(jobName)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("No output destination")
                    return
                }
                val fos = java.io.FileOutputStream(destination.fileDescriptor)
                controller.printToPdf(fos) { success ->
                    if (success) {
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } else {
                        callback?.onWriteFailed("Print to PDF failed")
                    }
                }
            }
        }
        printManager.print(jobName, adapter, android.print.PrintAttributes.Builder().build())
    }
    fun updateBackCallbackState() { }
    fun updateOmniBox() { }
    fun settlePillFraction(target: Float) {
        val current = com.petal.browser.ui.components.BarCollapseState.collapsedFraction
        pillMorphAnimator?.cancel()
        if (kotlin.math.abs(current - target) < 0.001f) {
            com.petal.browser.ui.components.BarCollapseState.setFraction(target)
            return
        }
        // Collapsing from fully open: resize the page first (bars still fully visible on top),
        // then fade the bars once Gecko has painted the freed area. No black block, no jump.
        val startsCollapse = target > current && current <= 0.001f && com.petal.browser.ui.components.BarCollapseState.legacyPaddingViewport
        if (startsCollapse) com.petal.browser.ui.components.BarCollapseState.beginViewportCollapse()
        pillMorphAnimator = android.animation.ValueAnimator.ofFloat(current, target).apply {
            duration = 320L
            startDelay = if (startsCollapse) 110L else 0L
            interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                com.petal.browser.ui.components.BarCollapseState.setFraction(f)
            }
            start()
        }
    }

    fun onScrollOffsetDelta(deltaY: Float) {
        if (!sp.getBoolean("sp_hide_bars_on_scroll", true)) return
        val density = resources.displayMetrics.density
        val step = deltaY / (120f * density)
        val current = com.petal.browser.ui.components.BarCollapseState.collapsedFraction
        val next = (current + step).coerceIn(0f, 1f)
        com.petal.browser.ui.components.BarCollapseState.setFraction(next)
    }

    fun animateAddressBarCollapse(collapse: Boolean) {
        if (!sp.getBoolean("sp_hide_bars_on_scroll", true)) {
            if (isAddressBarCollapsedState) {
                isAddressBarCollapsedState = false
                settlePillFraction(0f)
            }
            return
        }
        if (isAddressBarCollapsedState == collapse) return
        isAddressBarCollapsedState = collapse
        settlePillFraction(if (collapse) 1f else 0f)
    }

    fun updateVideoDimensions(width: Int, height: Int) {
        currentVideoWidth = width
        currentVideoHeight = height
    }

    fun setCustomFullscreen(fullscreen: Boolean) {
        isCustomFullscreenState = fullscreen
        runOnUiThread { applyImmersiveFullscreen() }
    }

    fun resolveCurrentTabUrl(): String = viewModel.activeUrl.value

    fun copyLink(url: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("URL", url)
        clipboard?.setPrimaryClip(clip)
    }

    fun shareLink(title: String, url: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_TITLE, title)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, null))
    }

    fun saveBookmark(title: String, url: String) {
        if (url.isNotBlank() && !url.startsWith("about:")) {
            viewModel.saveBookmark(title, url)
        }
    }
    fun toggleBookmark(title: String, url: String): Boolean {
        if (url.isBlank() || url.startsWith("about:")) return false
        val repo = com.petal.browser.database.HistoryBookmarkRepository(this)
        val exists = try { repo.checkBookmark(url) } catch (_: Throwable) { false }
        viewModel.toggleBookmark(title, url)
        return !exists
    }

    fun evaluateJavascriptInActiveTab(script: String, callback: ValueCallback<String>?) {
        val tab = viewModel.activeTab.value
        val gs = tab?.engineState?.engineSession?.let { com.petal.browser.engine.gecko.PetalEngineBridge.extractGeckoSession(it) }
            ?: (currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.getGeckoSession()
        if (gs != null) {
            com.petal.browser.extensions.PetalBuiltInExtensionManager.evaluateJavascript(gs, script) { result ->
                callback?.onReceiveValue(result)
            }
        } else {
            val ctrl = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
            ctrl?.evaluateJavascript(script) { result ->
                callback?.onReceiveValue(result)
            }
        }
    }

    fun evaluateJavascriptInActiveTab(script: String, callback: ((String?) -> Unit)? = null) {
        val tab = viewModel.activeTab.value
        val gs = tab?.engineState?.engineSession?.let { com.petal.browser.engine.gecko.PetalEngineBridge.extractGeckoSession(it) }
            ?: (currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.getGeckoSession()
        if (gs != null) {
            com.petal.browser.extensions.PetalBuiltInExtensionManager.evaluateJavascript(gs, script, callback)
        } else {
            val ctrl = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
            ctrl?.evaluateJavascript(script, callback)
        }
    }

    /** Captures the visible GeckoView pixels of the active web page on the UI thread. */
    fun captureActivePagePixels(callback: (android.graphics.Bitmap?) -> Unit) {
        runOnUiThread {
            val viewportCapture = com.petal.browser.compose.engine.PetalViewportThumbnailer.capturePagePixels
            if (viewportCapture != null) {
                viewportCapture(callback)
                return@runOnUiThread
            }
            val controller = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
            if (controller != null) {
                controller.captureFullPageBitmap(callback)
                return@runOnUiThread
            }
            callback(null)
        }
    }

    val presentedScreens = java.util.Stack<View>()
    val hasPresentedScreens: Boolean get() = presentedScreens.isNotEmpty()

    /** Observable by Compose (the Stack is not): lets the root decide when the system may handle Back. */
    var presentedScreenCount by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private fun syncPresentedCount() { presentedScreenCount = presentedScreens.size }

    fun dismissTopPresentedScreen(): Boolean {
        if (presentedScreens.isNotEmpty()) {
            val screen = presentedScreens.pop()
            syncPresentedCount()
            val content = findViewById<android.view.ViewGroup>(android.R.id.content)
            screen.animate()
                .alpha(0f)
                .translationY(80f)
                .setDuration(180L)
                .withEndAction { content?.removeView(screen) }
                .start()
            return true
        }
        return false
    }

    fun performBackNavigation() {
        if (dismissTopPresentedScreen()) {
            return
        }
        if (canBrowserGoBack()) {
            if (!viewModel.goBack()) {
                (currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.goBack()
            }
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    fun resetRefreshState() { }
    fun hideRefreshAndProgressOverlays() { }
    fun onActivePageLoadFinished() { }
    fun reloadCurrentBrowserTab() {
        viewModel.reload()
    }
    fun canBrowserGoBack(): Boolean =
        viewModel.canGoBack.value || ((currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.canGoBack() == true)
    fun restoreBrowserInputFocus() { }

    fun triggerRebirth(ctx: Context) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ctx.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    fun updatePipParams(enableAutoEnter: Boolean) {
        BrowserMediaDelegate.updatePipParams(this, enableAutoEnter)
    }
    fun triggerSystemPipMode() {
        BrowserMediaDelegate.triggerSystemPipMode(this)
    }

    fun installPwaShortcut() {
        com.petal.browser.pwa.PetalPwaManager(this, currentAlbumController, null).installCurrentPwa(this)
    }
    val isCurrentTabHomeOrBlank: Boolean
        get() = viewModel.activeUrl.value.isBlank() || viewModel.activeUrl.value == "about:home" 
    fun isActualWebPage(url: String): Boolean =
        url.isNotBlank() && !url.startsWith("about:")

    fun adoptPreparedWindow(source: PetalTabViewController?, request: mozilla.components.concept.engine.window.WindowRequest?) {
        if (request == null) return
        val tabId = java.util.UUID.randomUUID().toString()
        val isIncognito = source?.isIncognito ?: false
        com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
            context = this,
            tabId = tabId,
            url = request.url ?: "about:blank",
            isIncognito = isIncognito,
            select = true
        )
        viewModel.selectTab(tabId)
    }
    fun closeContentWindow(source: PetalTabViewController?) {
        source?.let { removeAlbum(it) }
    }
    fun adoptPopupGeckoSession(popupSession: org.mozilla.geckoview.GeckoSession?, targetUrl: String?, isIncognito: Boolean) {
        adoptPopupGeckoSession(popupSession, targetUrl, isIncognito, true)
    }
    fun adoptPopupGeckoSession(popupSession: org.mozilla.geckoview.GeckoSession?, targetUrl: String?, isIncognito: Boolean, activate: Boolean) {
        val tabId = java.util.UUID.randomUUID().toString()
        val url = targetUrl?.takeIf { it.isNotBlank() } ?: "about:blank"
        val pair = com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
            context = this,
            tabId = tabId,
            url = url,
            isIncognito = isIncognito,
            select = activate
        )
        if (popupSession != null) {
            com.petal.browser.engine.gecko.PetalEngineBridge.attachGeckoSessionToEngineSession(pair.second, popupSession)
        }
        if (activate) {
            viewModel.selectTab(tabId)
        }
    }

    fun isActionDispatchable(intent: Intent): Boolean = true
    fun dispatchIntent(intent: Intent) { handleIncomingIntent(intent) }

    fun presentComposeScreen(screen: View?) {
        presentComposeScreen(screen, true)
    }

    fun presentComposeScreen(screen: View?, animate: Boolean) {
        if (screen == null) return
        val content = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        if (screen.parent != null) {
            (screen.parent as? android.view.ViewGroup)?.removeView(screen)
        }
        screen.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        if (animate) {
            screen.alpha = 0f
            screen.translationY = 80f
            content.addView(screen)
            screen.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            content.addView(screen)
        }
        presentedScreens.push(screen)
        syncPresentedCount()
    }

    fun clearContentFrameKeepingTabs() {
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        while (presentedScreens.isNotEmpty()) {
            val screen = presentedScreens.pop()
            content?.removeView(screen)
        }
        syncPresentedCount()
    }

    fun onTabUrlStarted(webView: AlbumController?, url: String?) { }

    // ─────────────────────────────────────────────────────────────────────────
    // BrowserController Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun updateProgress(progress: Int) { }

    override fun showAlbum(albumController: AlbumController?) {
        showAlbum(albumController, null)
    }

    fun showAlbum(albumController: AlbumController?, targetUrl: String?) {
        captureVisibleTabThumbnail()
        currentAlbumController = albumController
        val id = albumController?.tabId?.takeIf { it.isNotBlank() }
        if (id != null) {
            viewModel.selectTab(id)
        }
        if (!targetUrl.isNullOrBlank()) {
            viewModel.loadUrl(targetUrl)
        }
    }

    override fun removeAlbum(albumController: AlbumController?) {
        val id = albumController?.tabId?.takeIf { it.isNotBlank() }
            ?: viewModel.browserState.value.selectedTabId
        id?.let { viewModel.closeTab(it) }
    }

    override fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?) {
        this.filePathCallback = filePathCallback
        this.mFilePathCallback = filePathCallback
        // Always Petal's own picker, never the Android system chooser.
        com.petal.browser.compose.file.PetalUploadChooser.show(this, filePathCallback, fileChooserParams) {
            filePathCallback?.onReceiveValue(null)
        }
    }

    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) { }

    override fun onHideCustomView() { }

    override fun showOverview() {
        viewModel.setTabSwitcherOpen(true)
    }

    override fun hideOverview() {
        viewModel.setTabSwitcherOpen(false)
    }

    override fun hideOverflow() {
        viewModel.setMenuOpen(false)
    }

    override fun hideSearch() {
        viewModel.setOmniboxOpen(false)
    }

    override fun showDialogFastToggle(title: String?, url: String?, floatingActionButton: FloatingActionButton?) {
        com.petal.browser.ui.components.BrowserDialogManager.showDialogFilter(this)
    }

    override fun setProfileIcon(floatingActionButton: FloatingActionButton?, url: String?) { }

    override fun showOverflow(
        dialog: Dialog?,
        view: View?,
        hideMenu: Int,
        title: String?,
        url: String?,
        adapterRecord: RecordListAdapter?,
        recordList: List<Record>?,
        location: Int
    ) {
        viewModel.setMenuOpen(true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intent Handling
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.hasExtra(com.petal.browser.engine.gecko.PetalWebNotificationDelegate.EXTRA_WEB_NOTIFICATION_CLICK_ID)) {
            val notificationId = intent.getIntExtra(com.petal.browser.engine.gecko.PetalWebNotificationDelegate.EXTRA_WEB_NOTIFICATION_CLICK_ID, 0)
            intent.removeExtra(com.petal.browser.engine.gecko.PetalWebNotificationDelegate.EXTRA_WEB_NOTIFICATION_CLICK_ID)
            com.petal.browser.engine.gecko.PetalWebNotificationDelegate.handleClick(notificationId)
            return
        }

        // Download notifications (live progress, completion, failure) and the island open the
        // Downloads screen with this extra. Consume it so a rotation/recreate does not reopen it.
        if (intent.getBooleanExtra("open_downloads", false)) {
            intent.removeExtra("open_downloads")
            viewModel.openDownloads()
            return
        }

        val action = intent.action
        // A widget button's action is one-shot: clear it so a rotation or recreate doesn't run it again.
        if (action != null && action.startsWith("com.petal.browser.action.OPEN_")) {
            intent.action = Intent.ACTION_MAIN
        }
        val data = intent.dataString
        val dataUri = intent.data

        // Handle WebExtension .xpi installation
        if (dataUri != null && (intent.type == "application/x-xpinstall" || dataUri.path?.endsWith(".xpi") == true)) {
            PetalExtensionManager.installFromContentUri(this, dataUri) { success, _ ->
                if (success) {
                    viewModel.openExtensions()
                }
            }
            return
        }

        // Handle Petal sync files (.psync): decrypt with the passphrase and add their contents here
        if (dataUri != null && com.petal.browser.account.sync.PetalSyncImport.isSyncFile(this, dataUri, intent.type)) {
            // One-shot: clear it so a rotation or recreate doesn't open the dialog again.
            intent.data = null
            intent.action = Intent.ACTION_MAIN
            com.petal.browser.account.sync.PetalSyncImport.handle(this, dataUri)
            return
        }

        if (Intent.ACTION_PROCESS_TEXT == action) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!text.isNullOrBlank()) {
                viewModel.openAiSheet(query = "", selectedQuote = text)
            }
            return
        }

        when (action) {
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB -> {
                viewModel.openNewTab("about:home", selectTab = true, private = false)
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO -> {
                viewModel.openNewTab("about:home", selectTab = true, private = true)
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS -> {
                viewModel.openBookmarks()
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS -> {
                viewModel.openDownloads()
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH -> {
                val query = intent.getStringExtra("query")
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: ""
                viewModel.openAiSheet(query)
            }
            // The search bar opens the search page on the current tab (it used to just open a blank new tab).
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_SEARCH -> {
                viewModel.openOmnibox()
            }
            // These two widget buttons had no handler at all, so tapping them only brought the app forward.
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_VOICE -> {
                com.petal.browser.ui.components.PetalVoiceSearchBridge.showVoiceSearchSheet(this) { result ->
                    if (result.isNotBlank()) viewModel.loadUrl(result.trim())
                }
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_LENS,
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_SNAP_CAMERA -> {
                val scannerIntent = Intent(this, com.petal.browser.lens.PetalScannerActivity::class.java)
                startActivity(scannerIntent)
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_MENU -> {
                viewModel.setMenuOpen(true)
            }
            com.petal.browser.compose.incognito.PetalIncognitoSessionManager.ACTION_CLOSE_INCOGNITO -> {
                viewModel.closeAllTabs(isIncognitoOnly = true)
            }
            Intent.ACTION_VIEW -> {
                if (!data.isNullOrBlank()) {
                    viewModel.loadUrl(data)
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.SHARED_TO_PETAL, "shared", "Shared to Petal", text.take(60))
                        viewModel.loadUrl(text)
                    }
                }
            }
        }
    }
}
