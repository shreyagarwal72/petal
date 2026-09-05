package com.petal.browser.view

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.R
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserController
import com.petal.browser.database.FaviconHelper
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.pwa.PetalPwaManager
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.TabThumbnailCache
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.util.function.Consumer

/**
 * PetalGeckoView
 * ─────────────────────────────────────────────────────────────────────────
 * Standalone, top-level GeckoView tab controller for Petal Browser.
 * Encapsulates [org.mozilla.geckoview.GeckoView] and [org.mozilla.geckoview.GeckoSession],
 * implementing [AlbumController] and [NestedScrollingChild3] to preserve all Material 3 browser
 * UI features, pull-to-refresh arbitration, predictive back edge gestures, and thumbnail caching.
 */
class PetalGeckoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), AlbumController, NestedScrollingChild3 {

    companion object {
        private const val TAG = "PetalGeckoView"
        @JvmStatic
        var browserController: BrowserController? = null

        @JvmStatic
        fun getDerivedDesktopUserAgent(context: Context): String {
            return "Mozilla/5.0 (X11; Linux x86_64; rv:154.0) Gecko/20100101 Firefox/154.0"
        }
    }

    interface OnScrollChangeListener {
        fun onScrollDown()
        fun onScrollUp()
    }

    private val childHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    val geckoView: GeckoView = GeckoView(context)
    val session: GeckoSession = GeckoSession()

    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var isIncognito: Boolean = false
    private var isForegroundTab: Boolean = false
    private var isStopped: Boolean = false
    private var tabId: String = "tab_${System.currentTimeMillis()}_${Math.abs(hashCode())}"
    private var tabGroupId: String? = null
    private var tabGroupTitle: String? = null
    private var predecessor: AlbumController? = null
    private val album: AdapterTabs = AdapterTabs(context, this, browserController)

    private var currentUrl: String = "about:blank"
    private var currentTitle: String = "Petal Start"
    private var currentProgress: Int = 0
    private var canGoBackVal: Boolean = false
    private var canGoForwardVal: Boolean = false
    private var favicon: Bitmap? = null

    private var mediaBridge: PetalMediaBridge? = null
    private var pwaManager: PetalPwaManager? = null
    private var onScrollChangeListener: OnScrollChangeListener? = null
    private var lastScrollHapticY: Int = 0

    init {
        isNestedScrollingEnabled = true
        addView(
            geckoView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        initGeckoSession()
        album.setBrowserController(browserController)
    }

    private fun initGeckoSession() {
        val runtime = PetalGeckoRuntime.getOrCreate(context)
        session.open(runtime)
        geckoView.setSession(session)

        // Progress & Loading Delegate
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                isStopped = false
                currentUrl = url
                album.setAlbumTitle(currentTitle, url)
                updateProgress(10)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                isStopped = true
                updateProgress(BrowserUnit.LOADING_STOPPED)
                updatePreviewCache()

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.resetRefreshState()
                }

                if (!isIncognito && currentUrl.isNotEmpty() && !currentUrl.equals("about:blank", ignoreCase = true) && !currentUrl.startsWith("about:")) {
                    try {
                        val action = com.petal.browser.database.RecordAction(context)
                        action.open(true)
                        if (action.checkUrl(currentUrl, com.petal.browser.unit.RecordUnit.TABLE_HISTORY)) {
                            action.deleteURL(currentUrl, com.petal.browser.unit.RecordUnit.TABLE_HISTORY)
                        }
                        action.addHistory(com.petal.browser.database.Record(currentTitle, currentUrl, System.currentTimeMillis(), 0))
                        action.close()
                        com.petal.browser.unit.PetalSessionHistoryManager.recordSessionVisit(currentUrl)
                    } catch (ignored: Exception) {}

                    try {
                        com.petal.browser.unit.TabSessionManager.saveSession(context)
                    } catch (ignored: Exception) {}
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                currentProgress = progress
                updateProgress(progress)
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {}
        }

        // Navigation Delegate
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackVal = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardVal = canGoForward
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                if (BrowserUnit.isHomePage(uri)) {
                    session.loadUri("about:blank")
                    album.setAlbumTitle("Petal Home", "petal://home")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    val newView = PetalGeckoView(act)
                    newView.isIncognito = isIncognito
                    act.runOnUiThread {
                        act.addAlbum(newView.title, uri, true, isIncognito)
                    }
                    return GeckoResult.fromValue(newView.session)
                }
                return null
            }
        }

        // Content Delegate
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let {
                    currentTitle = it
                    album.setAlbumTitle(it, currentUrl)
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        // Fullscreen sync with Material 3 app bars
                    }
                }
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                val act = getHostActivity() ?: return
                if (act !is com.petal.browser.activity.BrowserActivity) return

                val linkUri = element.linkUri
                val srcUri = element.srcUri
                val elemType = element.type

                act.runOnUiThread {
                    when {
                        elemType == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE && !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showImageContextMenu(act, srcUri)
                        }
                        elemType == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO && !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showVideoContextMenu(act, srcUri)
                        }
                        elemType == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO && !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showAudioContextMenu(act, srcUri)
                        }
                        !linkUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showLinkContextMenu(act, linkUri)
                        }
                        !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showImageContextMenu(act, srcUri)
                        }
                    }
                }
            }
        }

        // Material 3 Prompt Delegate (Alerts, Confirms, Prompts)
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return result
                act.runOnUiThread {
                    MaterialAlertDialogBuilder(act)
                        .setTitle(prompt.title ?: act.getString(R.string.app_name))
                        .setMessage(prompt.message ?: "")
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            result.complete(prompt.dismiss())
                        }
                        .setOnCancelListener {
                            result.complete(prompt.dismiss())
                        }
                        .show()
                }
                return result
            }

            override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return result
                act.runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(act)
                        .setTitle(prompt.title ?: act.getString(R.string.app_name))
                        .setMessage(prompt.message ?: "")
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                            result.complete(prompt.dismiss())
                        }
                        .setOnCancelListener {
                            result.complete(prompt.dismiss())
                        }
                    builder.show()
                }
                return result
            }
        }

        // Scroll Delegate for Tactile Haptics and Address Bar Collapsing
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    if (Math.abs(scrollY - lastScrollHapticY) > 36) {
                        lastScrollHapticY = scrollY
                        com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                            .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.TICK, 0.45f, 60L)
                    }

                    onScrollChangeListener?.let { listener ->
                        val dy = scrollY - lastScrollHapticY
                        if (dy > 12) {
                            listener.onScrollDown()
                        } else if (dy < -12) {
                            listener.onScrollUp()
                        }
                    }
                }
            }
        }

        // Selection & Context Menu Action Delegate (Long-click text selection)
        session.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
            override fun onShowActionRequest(
                session: GeckoSession,
                selection: GeckoSession.SelectionActionDelegate.Selection
            ) {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    val selectedText = selection.text
                    if (!selectedText.isNullOrBlank()) {
                        act.runOnUiThread {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showSelectionContextMenu(act, selectedText)
                        }
                    }
                }
            }
        }

        applySettings()
    }

    private fun updateProgress(progress: Int) {
        if (isForegroundTab && browserController != null) {
            val p = if (!isStopped) progress else BrowserUnit.LOADING_STOPPED
            browserController?.updateProgress(p)
        }
    }

    fun applySettings() {
        val desktopEnabled = sp.getBoolean("sp_desktop_site", false)
        session.settings.userAgentMode = if (desktopEnabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.settings.useTrackingProtection = true
        val enableJs = sp.getBoolean("sp_javascript", true)
        session.settings.allowJavascript = enableJs
    }

    fun setDesktopMode(enabled: Boolean) {
        session.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.reload()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation Methods
    // ─────────────────────────────────────────────────────────────────────────

    fun loadUrl(url: String) {
        val redirected = BrowserUnit.redirectURL(null, sp, url)
        val targetUrl = BrowserUnit.queryWrapper(context, redirected)

        if (BrowserUnit.isHomePage(targetUrl) || BrowserUnit.isHomePage(url)) {
            session.loadUri("about:blank")
            currentUrl = "about:blank"
            currentTitle = "Petal Home"
            album.setAlbumTitle("Petal Home", "petal://home")
            return
        }

        currentUrl = targetUrl
        session.loadUri(targetUrl)
    }

    fun canGoBack(): Boolean = canGoBackVal

    fun canGoForward(): Boolean = canGoForwardVal

    fun goBack() {
        if (canGoBackVal) {
            session.goBack()
        }
    }

    fun goForward() {
        if (canGoForwardVal) {
            session.goForward()
        }
    }

    fun reload() {
        isStopped = false
        applySettings()
        session.reload()
    }

    fun stopLoading() {
        isStopped = true
        session.stop()
        updateProgress(BrowserUnit.LOADING_STOPPED)
    }

    fun getProgress(): Int = currentProgress

    fun isStopped(): Boolean = isStopped

    fun initPreferences(url: String?) {
        applySettings()
    }

    fun clearMatches() {
        session.finder.clear()
    }

    fun findAllAsync(query: String) {
        session.finder.find(query, GeckoSession.FINDER_FIND_MATCH_CASE)
    }

    fun findNext(forward: Boolean) {
        if (forward) {
            session.finder.find(null, GeckoSession.FINDER_FIND_MATCH_CASE)
        } else {
            session.finder.find(null, GeckoSession.FINDER_FIND_MATCH_CASE or GeckoSession.FINDER_FIND_BACKWARDS)
        }
    }

    fun clearHistory() {
        session.purgeHistory()
    }

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        // GeckoView executes scripts via WebExtensions or internal session delegates
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AlbumController Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun getAlbumView(): View = album.albumView

    override fun activate() {
        requestFocus()
        isForegroundTab = true
        album.activate()
        session.setActive(true)
        geckoView.visibility = View.VISIBLE
    }

    override fun deactivate() {
        clearFocus()
        isForegroundTab = false
        album.deactivate()
        updatePreviewCache()
        session.setActive(false)
    }

    override fun getTitle(): String = currentTitle

    override fun getUrl(): String = currentUrl

    fun getAlbumUrl(): String = album.url?.toString() ?: currentUrl

    fun setAlbumTitle(title: String?, url: String?) {
        album.setAlbumTitle(title, url)
    }

    fun getTabId(): String = tabId

    fun setTabId(id: String) {
        if (id.isNotEmpty()) this.tabId = id
    }

    fun getTabGroupId(): String? = tabGroupId

    fun setTabGroupId(id: String?) {
        this.tabGroupId = id
    }

    fun getTabGroupTitle(): String? = tabGroupTitle

    fun setTabGroupTitle(title: String?) {
        this.tabGroupTitle = title
    }

    fun isIncognito(): Boolean = isIncognito

    fun setIncognito(incognito: Boolean) {
        this.isIncognito = incognito
    }

    fun isForeground(): Boolean = isForegroundTab

    fun getPredecessor(): AlbumController? = predecessor

    fun setPredecessor(controller: AlbumController?) {
        this.predecessor = controller
    }

    fun setStopped(stopped: Boolean) {
        this.isStopped = stopped
    }

    fun setBrowserController(controller: BrowserController?) {
        browserController = controller
        album.setBrowserController(controller)
    }

    fun getBrowserController(): BrowserController? = browserController

    fun setOnScrollChangeListener(listener: OnScrollChangeListener?) {
        this.onScrollChangeListener = listener
    }

    fun getMediaBridge(): PetalMediaBridge? = mediaBridge

    fun setMediaBridge(bridge: PetalMediaBridge?) {
        this.mediaBridge = bridge
    }

    fun getPwaManager(): PetalPwaManager? = pwaManager

    fun setPwaManager(manager: PetalPwaManager?) {
        this.pwaManager = manager
    }

    fun getFavicon(): Bitmap? = favicon

    fun setFavicon(icon: Bitmap?) {
        this.favicon = icon
        if (!isIncognito && icon != null) {
            val helper = FaviconHelper(context)
            helper.addFavicon(context, currentUrl, icon)
        }
    }

    fun updateFavicon(url: String) {
        FaviconHelper.setFavicon(context, album.albumView, url, R.id.item_icon, R.drawable.icon_image_broken)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab Previews & Caching
    // ─────────────────────────────────────────────────────────────────────────

    fun getThumbnailKey(): String = getTabId()

    fun updatePreviewCache() {
        capturePreviewBitmapAsync { /* cache updated */ }
    }

    fun getCachedPreviewBitmap(): Bitmap? {
        var bitmap = TabThumbnailCache.get(getThumbnailKey())
        if (bitmap != null && !bitmap.isRecycled) return bitmap
        if (currentUrl.isNotEmpty() && !currentUrl.equals("about:blank", ignoreCase = true)) {
            bitmap = TabThumbnailCache.get(currentUrl)
            if (bitmap != null && !bitmap.isRecycled) return bitmap
        }
        return null
    }

    fun capturePreviewBitmapAsync(callback: Consumer<Bitmap?>) {
        val key = getThumbnailKey()
        val url = currentUrl

        val cachingConsumer: (Bitmap?) -> Unit = { bmp ->
            if (bmp != null) {
                TabThumbnailCache.put(key, bmp)
                if (url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true)) {
                    TabThumbnailCache.put(url, bmp)
                }
            }
            callback.accept(bmp)
        }

        try {
            geckoView.capturePixels().then({ bitmap ->
                if (bitmap != null) {
                    val w = bitmap.width
                    val h = bitmap.height
                    val targetWidth = Math.min(w, 480)
                    val targetHeight = Math.max(1, (h.toFloat() * targetWidth / w).toInt())
                    val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                    cachingConsumer(scaled)
                } else {
                    cachingConsumer(null)
                }
                GeckoResult.fromValue(null)
            }, {
                cachingConsumer(null)
                GeckoResult.fromValue(null)
            })
        } catch (e: Exception) {
            cachingConsumer(null)
        }
    }

    fun getBackHistoryUrl(): String? {
        return null
    }

    fun getBackPreviewBitmap(): Bitmap? {
        val url = getBackHistoryUrl() ?: return null
        val bitmap = TabThumbnailCache.get(url)
        if (bitmap != null && !bitmap.isRecycled) return bitmap
        return null
    }

    fun onResume() {
        session.setActive(true)
    }

    fun onPause() {
        session.setActive(false)
    }

    fun resumeTimers() {}

    fun pauseTimers() {}

    fun resetGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                systemGestureExclusionRects = java.util.Collections.emptyList()
            } catch (ignored: Exception) {}
        }
    }

    override fun setSystemGestureExclusionRects(rects: MutableList<android.graphics.Rect>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                super.setSystemGestureExclusionRects(java.util.Collections.emptyList())
            } catch (ignored: Exception) {}
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val action = ev.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                resetGestureExclusionRects()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun destroy() {
        stopLoading()
        session.setActive(false)
        session.close()
        geckoView.releaseSession()
        removeAllViews()
    }

    private fun getHostActivity(): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NestedScrollingChild3 Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = childHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int, type: Int): Boolean = childHelper.startNestedScroll(axes, type)

    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean = childHelper.hasNestedScrollingParent(type)

    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean = dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        childHelper.dispatchNestedPreFling(velocityX, velocityY)
}
