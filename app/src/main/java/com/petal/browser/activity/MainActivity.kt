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
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.petal.browser.account.mozilla.FxAccountManager
import com.petal.browser.account.mozilla.PetalMozillaSyncManager
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserController
import com.petal.browser.browser.PetalBrowserFeatures
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
    fun isBarAnimationRunning(): Boolean = false
    fun isAddressBarCollapsed(): Boolean = false
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
                provider.remove()
            } catch (_: Throwable) { }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.petal.browser.compose.splash.PetalSplashState.hasShownThisSession = true
        com.petal.browser.compose.splash.PetalSplashState.splashActive.value = false

        // Restore tabs from previous session if cold start
        if (savedInstanceState == null) {
            val restored = com.petal.browser.browser.PetalSessionRestoreManager.restoreSession(this)
            val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(this)
            if (!restored && store.state.tabs.isEmpty()) {
                viewModel.openNewTab("about:home", selectTab = true)
            }
        }

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
            FxAccountManager.getInstance().initialize(this)
            PetalMozillaSyncManager.getInstance()
            browserFeatures = PetalBrowserFeatures(this).also { it.start() }
            handleIncomingIntent(intent)
        }, 250L)
    }

    override fun onStop() {
        super.onStop()
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
        browserFeatures?.stop()
        browserFeatures = null
    }

    override fun onResume() {
        super.onResume()
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
        browserFeatures?.onPermissionsResult(requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
        if (!url.isNullOrBlank()) {
            addAlbum(title, url, false, isIncognito)
        }
        return null
    }

    fun saveOpenedTabs() {
        PetalSessionRestoreManager.saveSession(this, isStopping = false)
    }

    @JvmOverloads
    fun openSettingsScreen(category: SettingsCategory? = null) {
        viewModel.openSettings(category ?: SettingsCategory.OVERVIEW)
    }

    fun openFlagsHubScreen() {
        viewModel.openSettings(SettingsCategory.MISCELLANEOUS)
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
        viewModel.openOmnibox()
    }
    fun showExtensionsScreen() { viewModel.openExtensions() }
    fun showQuickTools() {
        com.petal.browser.ui.components.PetalQuickToolsBridge.showQuickTools(this)
    }
    fun showReaderMode() {
        val controller = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
        if (controller != null) {
            val isReader = controller.url?.startsWith("about:reader?url=") == true
            controller.toggleReaderMode(!isReader)
        }
    }
    fun showDeleteScreen() {
        viewModel.openDelete()
    }
    fun showSafeLocker() {
        viewModel.openSafeLocker()
    }
    fun showSendToDevice() {
        val url = currentAlbumController?.url.orEmpty()
        val title = currentAlbumController?.title.orEmpty()
        com.petal.browser.account.mozilla.PetalSendToDeviceBridge.showSendToDevice(this, url, title)
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
    fun updatePersistentBottomNav() { }
    fun updateBackCallbackState() { }
    fun updateOmniBox() { }
    fun settlePillFraction(target: Float) { }
    fun onScrollOffsetDelta(deltaY: Float) { }

    fun updateVideoDimensions(width: Int, height: Int) {
        currentVideoWidth = width
        currentVideoHeight = height
    }

    fun setCustomFullscreen(fullscreen: Boolean) {
        isCustomFullscreenState = fullscreen
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
        val ctrl = currentAlbumController as? com.petal.browser.browser.PetalTabViewController
        ctrl?.evaluateJavascript(script, callback)
    }

    val presentedScreens = java.util.Stack<View>()
    val hasPresentedScreens: Boolean get() = presentedScreens.isNotEmpty()

    fun dismissTopPresentedScreen(): Boolean {
        if (presentedScreens.isNotEmpty()) {
            val screen = presentedScreens.pop()
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

    fun animateAddressBarCollapse(collapse: Boolean) { }
    fun applyAddressBarPosition() { }
    fun performBackNavigation() {
        if (dismissTopPresentedScreen()) {
            return
        }
        if (canBrowserGoBack()) {
            (currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.goBack() ?: viewModel.goBack()
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    fun resetRefreshState() { }
    fun hideRefreshAndProgressOverlays() { }
    fun onActivePageLoadFinished() { }
    fun reloadCurrentBrowserTab() {
        (currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.reload() ?: viewModel.reload()
    }
    fun canBrowserGoBack(): Boolean =
        (currentAlbumController as? com.petal.browser.browser.PetalTabViewController)?.canGoBack() ?: viewModel.canGoBack.value
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
    }

    fun clearContentFrameKeepingTabs() {
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        while (presentedScreens.isNotEmpty()) {
            val screen = presentedScreens.pop()
            content?.removeView(screen)
        }
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
        currentAlbumController = albumController
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

        val action = intent.action
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

        if (Intent.ACTION_PROCESS_TEXT == action) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!text.isNullOrBlank()) {
                viewModel.loadUrl(text)
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
                viewModel.openAiSheet()
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_SEARCH -> {
                viewModel.openNewTab("about:home", selectTab = true, private = false)
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
                        viewModel.loadUrl(text)
                    }
                }
            }
        }
    }
}
