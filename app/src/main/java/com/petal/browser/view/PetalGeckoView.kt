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
import android.graphics.Region
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
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.MediaSession
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
    defStyleAttr: Int = 0,
    adoptedSession: GeckoSession? = null
) : FrameLayout(context, attrs, defStyleAttr), AlbumController, NestedScrollingChild3 {

    companion object {
        private const val TAG = "PetalGeckoView"
        @JvmField
        var globalBrowserController: BrowserController? = null

        @JvmStatic
        fun getBrowserController(): BrowserController? = globalBrowserController

        @JvmStatic
        fun getProfile(context: Context? = null): String {
            val ctx = context ?: com.petal.browser.PetalApplication.instance
            if (ctx != null) {
                val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
                return sp.getString("profile", "profileStandard") ?: "profileStandard"
            }
            return "profileStandard"
        }

        @JvmStatic
        fun getDerivedDesktopUserAgent(context: Context): String {
            return "Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0"
        }
    }

    interface OnScrollChangeListener {
        fun onScrollDown()
        fun onScrollUp()
    }

    private val childHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    val geckoView: GeckoView = SafeGeckoView(context)
    var session: GeckoSession = adoptedSession ?: GeckoSession()

    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var isIncognito: Boolean = false
    private var isForegroundTab: Boolean = false
    private var isStopped: Boolean = false

    private var tabId: String = "tab_${System.currentTimeMillis()}_${Math.abs(hashCode())}"
    private var tabGroupId: String? = null
    private var tabGroupTitle: String? = null
    private var predecessor: AlbumController? = null
    private val album: AdapterTabs = AdapterTabs(context, this, globalBrowserController)

    private var currentUrl: String = "about:blank"
    private var currentTitle: String = "Petal Start"
    private var currentProgress: Int = 0
    private var canGoBackVal: Boolean = false
    private var canGoForwardVal: Boolean = false
    private val backHistoryUrls: java.util.ArrayList<String> = java.util.ArrayList()
    private var isNavigatingHistory: Boolean = false
    private var lastRecordedHistoryUrl: String? = null
    private var favicon: Bitmap? = null
    var currentSecurityInfo: GeckoSession.ProgressDelegate.SecurityInformation? = null
        private set

    private var mediaBridge: PetalMediaBridge? = null
    private var pwaManager: PetalPwaManager? = null
    private var onScrollChangeListener: OnScrollChangeListener? = null
    private var lastScrollHapticY: Int = 0
    private var currentScrollY: Int = 0
    private var currentScrollX: Int = 0
    private var lastCrashRecoveryTime: Long = 0L
    private var crashRecoveryCount: Int = 0

    init {
        isNestedScrollingEnabled = true
        addView(
            geckoView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        initGeckoSession()
        album.setBrowserController(globalBrowserController)
    }

    private fun initGeckoSession() {
        val runtime = PetalGeckoRuntime.getOrCreate(context.applicationContext)
        // GeckoView requires open() to receive a brand-new, unopened session.
        // Recovery and view reattachment can race with the initial setup, so do
        // not call open again when this session is already attached/open.
        if (!session.isOpen) {
            session.open(runtime)
        }
        geckoView.setSession(session)
        // Do not mutate GeckoView's compositor child hierarchy during session attachment.
        // Edge-gesture handling is performed lazily from dispatchTouchEvent().

        // Progress & Loading Delegate
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                isStopped = false
                if (!isNavigatingHistory && currentUrl.isNotEmpty() && !currentUrl.equals("about:blank", ignoreCase = true) && !currentUrl.startsWith("about:")) {
                    if (backHistoryUrls.isEmpty() || backHistoryUrls.last() != currentUrl) {
                        backHistoryUrls.add(currentUrl)
                        if (backHistoryUrls.size > 50) {
                            backHistoryUrls.removeAt(0)
                        }
                    }
                }
                isNavigatingHistory = false
                currentUrl = url
                album.setAlbumTitle(currentTitle, url)
                updateProgress(10)

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        // Do not touch GeckoView's compositor child hierarchy during
                        // navigation. Login/OAuth redirects can recreate that hierarchy
                        // concurrently and walking it here can trigger a native crash.
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                    }
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                isStopped = true
                updateProgress(BrowserUnit.LOADING_STOPPED)
                updatePreviewCache()

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.resetRefreshState()
                    act.runOnUiThread {
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                    }
                }

                recordHistoryVisit(currentUrl, currentTitle)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                currentProgress = progress
                updateProgress(progress)
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                currentSecurityInfo = securityInfo
            }
        }

        // Navigation Delegate
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackVal = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardVal = canGoForward
            }

            // onPageStart only fires with the URL that was originally requested. If the
            // server responds with a redirect, or the page later calls history.pushState/
            // replaceState (every SPA route change - Google included), the actual displayed
            // location moves on but onPageStart never fires again, so currentUrl (and the
            // address bar bound to it) was left showing the stale, pre-redirect/pre-navigation
            // URL indefinitely. onLocationChange is GeckoView's dedicated callback for exactly
            // this - it fires whenever the visible top-level location changes, redirect or not.
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url.isNullOrBlank() || url.equals(currentUrl, ignoreCase = true)) return
                currentUrl = url
                album.setAlbumTitle(currentTitle, url)
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        // Location changes include login/OAuth redirects. Keep this callback
                        // UI-only; Gecko owns its compositor child lifecycle.
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                    }
                }
                recordHistoryVisit(url, currentTitle)
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                if (BrowserUnit.isHomePage(uri)) {
                    album.setAlbumTitle("Petal Home", "petal://home")
                    if (uri.equals("about:blank", ignoreCase = true)) {
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // If "Open Redirect Links in Background" is enabled, spawn cross-origin redirects/links in background tabs
                val openRedirectsInBackground = sp.getBoolean("sp_open_redirects_in_background", false)
                if (openRedirectsInBackground && request.target != GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT) {
                    val act = getHostActivity()
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        val currentHost = try { android.net.Uri.parse(currentUrl).host } catch (e: Exception) { null }
                        val targetHost = try { android.net.Uri.parse(uri).host } catch (e: Exception) { null }
                        if (!currentHost.isNullOrEmpty() && !targetHost.isNullOrEmpty() && !currentHost.equals(targetHost, ignoreCase = true)) {
                            act.runOnUiThread {
                                act.addAlbum(null, uri, false, isIncognito)
                            }
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                    }
                }

                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val act = getHostActivity() as? com.petal.browser.activity.BrowserActivity ?: return null
                if (act.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed)) return null

                // GeckoView's onNewSession contract: return a brand-new, UNOPENED GeckoSession.
                // GeckoView itself will call open() on it. Once open, we adopt it into a new
                // popup tab via adoptPopupSession() — which reuses the already-open session
                // without calling open() a second time (which would crash).
                val popupSession = GeckoSession(GeckoSessionSettings.Builder()
                    .usePrivateMode(this@PetalGeckoView.isIncognito)
                    .build())
                val result = GeckoResult<GeckoSession>()
                result.complete(popupSession)

                // Gecko opens the returned session asynchronously. Never adopt it until
                // that open has completed: initGeckoSession() would otherwise open it first,
                // and Gecko would then fail its own pending open with "Must use an unopened
                // GeckoSession instance". This is most visible on OAuth/login popups.
                val adoptWhenOpened = object : Runnable {
                    private var attempts = 0

                    override fun run() {
                        try {
                            if (act.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed)) {
                                if (popupSession.isOpen) popupSession.close()
                                return
                            }
                            if (!popupSession.isOpen) {
                                if (++attempts <= 120) {
                                    postDelayed(this, 16L)
                                } else {
                                    android.util.Log.w(TAG, "Popup GeckoSession did not open in time; skipping adoption")
                                }
                                return
                            }
                            act.adoptPopupGeckoSession(popupSession, isIncognito)
                        } catch (t: Throwable) {
                            android.util.Log.e(TAG, "Failed to create login popup tab", t)
                            if (popupSession.isOpen) popupSession.close()
                        }
                    }
                }
                act.runOnUiThread(adoptWhenOpened)
                return result
            }
        }

        // Content Delegate
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let {
                    currentTitle = it
                    album.setAlbumTitle(it, currentUrl)
                    val act = getHostActivity()
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        act.runOnUiThread {
                            act.updateOmniBox()
                        }
                    }
                    if (it.isNotBlank() && it != "Petal Start" && it != "Petal Home" && currentUrl.isNotBlank()) {
                        recordHistoryVisit(currentUrl, it)
                    }
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

            override fun onCloseRequest(session: GeckoSession) {
                // When an OAuth or web login window/tab invokes window.close() after completion,
                // safely close this popup tab and return focus to the predecessor tab.
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        try {
                            act.removeAlbum(this@PetalGeckoView)
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Failed to close tab on window.close() request", e)
                        }
                    }
                }
            }

            override fun onFocusRequest(session: GeckoSession) {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        try {
                            act.showAlbum(this@PetalGeckoView)
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Failed to focus tab on onFocusRequest", e)
                        }
                    }
                }
            }

            override fun onCrash(session: GeckoSession) {
                // Per GeckoView docs: once the content process crashes, the session is
                // permanently closed/unusable. Without re-opening and reloading here, the
                // tab is left showing the last URL with a blank page indefinitely - this
                // is the root cause of "page loads but shows blank" that a manual reload
                // happens to fix (reload re-triggers loadUrl, masking the real problem).
                android.util.Log.w(TAG, "GeckoSession content process crashed for $currentUrl - reopening session")
                com.petal.browser.logger.PetalAppLogger.e(TAG, "GeckoSession content process crashed for $currentUrl")
                com.petal.browser.logger.PetalAppLogger.recordProcessCrash(
                    context,
                    TAG,
                    "GeckoView Content Process Crash (SIGSEGV / Native Termination)",
                    "Active URL: $currentUrl | Title: $currentTitle"
                )
                recoverCrashedSession()
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val responseUrl = response.uri
                if (responseUrl.isNullOrBlank()) return
                val act = getHostActivity() ?: return
                val parsed = try { android.net.Uri.parse(responseUrl) } catch (_: Exception) { null }
                val isMozillaXpi = parsed?.host?.equals("addons.mozilla.org", ignoreCase = true) == true &&
                    parsed.path?.contains("/downloads/", ignoreCase = true) == true &&
                    parsed.path?.endsWith(".xpi", ignoreCase = true) == true
                if (isMozillaXpi) {
                    act.runOnUiThread {
                        com.petal.browser.extensions.PetalExtensionManager.install(responseUrl) { success, message ->
                            com.petal.browser.view.NinjaToast.show(act, message ?: if (success) "Extension installed" else "Extension installation failed")
                        }
                    }
                    return
                }
                // GeckoView's WebResponse carries the server's real Content-Disposition and
                // Content-Type headers - previously these were discarded (passed as null),
                // so every download fell back to guessing a name purely from the URL path.
                // For signed CDN links, dynamic endpoints, or any URL without a clean
                // "name.ext" tail, that guess had nothing to go on and produced a wrong
                // name and/or wrong extension. Reading the real headers here fixes that
                // for every download that goes through the GeckoView engine.
                val headers = response.headers
                val contentDisposition = headers?.entries?.firstOrNull {
                    it.key.equals("Content-Disposition", ignoreCase = true)
                }?.value
                val mimeType = headers?.entries?.firstOrNull {
                    it.key.equals("Content-Type", ignoreCase = true)
                }?.value
                val fileName = HelperUnit.resolveFileName(responseUrl, contentDisposition, mimeType)
                val contentLength = headers?.entries?.firstOrNull {
                    it.key.equals("Content-Length", ignoreCase = true)
                }?.value?.toLongOrNull() ?: 0L
                act.runOnUiThread {
                    com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                        act, responseUrl, contentDisposition, mimeType, contentLength
                    ) { confirmedName ->
                        BrowserUnit.download(act, responseUrl, confirmedName.ifBlank { fileName }, mimeType)
                    }
                }
            }
            override fun onKill(session: GeckoSession) {
                // The OS/Gecko killed the content process (almost always a routine low-memory
                // kill under system memory pressure, not an actual app fault). The session is
                // reopened transparently below, so this is intentionally NOT reported as a
                // crash - recordProcessCrash() is deliberately not called here so it can no
                // longer trigger the crash-reporting dialog on next launch. A plain debug log
                // line is kept for local troubleshooting only.
                android.util.Log.w(TAG, "GeckoSession content process killed for $currentUrl (low memory) - reopening session")
                recoverCrashedSession()
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

        // Material 3 Prompt Delegate (Alerts, Confirms, Prompts, Auth, Choice, Text, Popups)
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

            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return GeckoResult.fromValue(prompt.dismiss())
                act.runOnUiThread {
                    val layout = android.widget.LinearLayout(act).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        val pad = (16 * resources.displayMetrics.density).toInt()
                        setPadding(pad, pad / 2, pad, pad / 2)
                    }
                    val userEdit = android.widget.EditText(act).apply {
                        hint = "Username"
                        prompt.authOptions.username?.let { setText(it) }
                    }
                    val passEdit = android.widget.EditText(act).apply {
                        hint = "Password"
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    val isPasswordOnly = (prompt.authOptions.flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
                    if (!isPasswordOnly) layout.addView(userEdit)
                    layout.addView(passEdit)

                    val dialogTitle = prompt.title ?: prompt.authOptions.uri ?: act.getString(R.string.app_name)
                    MaterialAlertDialogBuilder(act)
                        .setTitle(dialogTitle)
                        .setMessage(prompt.message ?: "Sign In")
                        .setView(layout)
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            val enteredPassword = passEdit.text.toString()
                            if (isPasswordOnly) {
                                result.complete(prompt.confirm(enteredPassword))
                            } else {
                                val enteredUser = userEdit.text.toString()
                                result.complete(prompt.confirm(enteredUser, enteredPassword))
                            }
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
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

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return GeckoResult.fromValue(prompt.dismiss())
                act.runOnUiThread {
                    val input = android.widget.EditText(act).apply {
                        prompt.defaultValue?.let { setText(it) }
                        selectAll()
                    }
                    val container = android.widget.FrameLayout(act).apply {
                        val pad = (20 * resources.displayMetrics.density).toInt()
                        setPadding(pad, pad / 2, pad, pad / 2)
                        addView(input)
                    }
                    MaterialAlertDialogBuilder(act)
                        .setTitle(prompt.title ?: act.getString(R.string.app_name))
                        .setMessage(prompt.message ?: "")
                        .setView(container)
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            result.complete(prompt.confirm(input.text.toString()))
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
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

            override fun onChoicePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ChoicePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                // Safe dismissal avoids unhandled choice/menu prompt exceptions during form submissions
                return GeckoResult.fromValue(prompt.dismiss())
            }

            override fun onPopupPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.PopupPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                // Allow popup window requests (such as OAuth sign-in windows)
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
            }

            override fun onBeforeUnloadPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                // Automatically allow unload during navigation/redirects
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
            }

            override fun onRepostConfirmPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
            }

            // Autofill is disabled at the runtime level (loginAutofillEnabled = false),
            // but these are overridden defensively so Gecko never falls through to the
            // default interface behavior if autofill is re-enabled later without this
            // being revisited. Dismissing immediately is safe and crash-free.
            override fun onLoginSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onLoginSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onCreditCardSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSaveOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onCreditCardSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSelectOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onAddressSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSaveOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onAddressSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSelectOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onSelectIdentityCredentialProvider(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.ProviderSelectorPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.dismiss())
            }

            override fun onSelectIdentityCredentialAccount(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.AccountSelectorPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.dismiss())
            }

            override fun onShowPrivacyPolicyIdentityCredential(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.PrivacyPolicyPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.dismiss())
            }
        }

        // Scroll Delegate for Tactile Haptics and Address Bar Collapsing
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                currentScrollX = scrollX
                currentScrollY = scrollY
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    if (Math.abs(scrollY - lastScrollHapticY) > 36) {
                        lastScrollHapticY = scrollY
                        if (com.petal.browser.haptics.PetalHapticEngine.isScrollHapticsEnabled(context)) {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                                .play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.45f, 60L)
                        }
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

        // Native GeckoView MediaSession Delegate for HTML5 Media & PiP Tracking
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                mediaBridge?.setActiveGeckoMediaSession(mediaSession)
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (mediaBridge?.activeGeckoMediaSession == mediaSession) {
                    mediaBridge?.setActiveGeckoMediaSession(null)
                }
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                mediaBridge?.setActiveGeckoMediaSession(mediaSession)
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(true)
                    l?.onMediaPlay(currentTitle, 0L, 0L)
                }
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(false)
                    l?.onMediaPause(0L, 0L)
                }
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(false)
                    l?.onMediaPause(0L, 0L)
                }
            }

            override fun onPositionState(session: GeckoSession, mediaSession: MediaSession, state: MediaSession.PositionState) {
                mediaBridge?.updatePositionState(state.position, state.duration)
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    val pos = (state.position * 1000).toLong()
                    val dur = (state.duration * 1000).toLong()
                    l?.onMediaProgress(pos, dur)
                }
            }

            override fun onFullscreen(session: GeckoSession, mediaSession: MediaSession, enabled: Boolean, meta: MediaSession.ElementMetadata?) {
                if (meta != null && meta.width > 0 && meta.height > 0) {
                    val act = getHostActivity() ?: return
                    act.runOnUiThread {
                        mediaBridge?.listener?.onVideoDimensionsChanged(meta.width.toInt(), meta.height.toInt())
                    }
                }
            }

            override fun onMetadata(session: GeckoSession, mediaSession: MediaSession, meta: MediaSession.Metadata) {
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    val title = meta.title ?: currentTitle
                    if (mediaSession.isActive) {
                        l?.onMediaPlay(title, 0L, 0L)
                    }
                }
            }
        }

        applySettings()
    }

    /**
     * Records or updates a history entry for the currently visited page, ensuring
     * fallback titles when empty and capturing single-page application (SPA) navigations.
     */
    private fun recordHistoryVisit(targetUrl: String, titleToRecord: String? = null) {
        if (isIncognito || targetUrl.isBlank() || targetUrl.equals("about:blank", ignoreCase = true) || targetUrl.startsWith("about:", ignoreCase = true)) {
            return
        }
        val rawTitle = titleToRecord ?: currentTitle
        val effectiveTitle = if (rawTitle.isBlank() || rawTitle == "Petal Start" || rawTitle == "Petal Home") {
            try {
                val host = android.net.Uri.parse(targetUrl).host
                if (!host.isNullOrBlank()) host else targetUrl
            } catch (_: Exception) {
                targetUrl
            }
        } else {
            rawTitle
        }

        try {
            val action = com.petal.browser.database.RecordAction(context)
            action.open(true)
            if (action.checkUrl(targetUrl, com.petal.browser.unit.RecordUnit.TABLE_HISTORY)) {
                action.deleteURL(targetUrl, com.petal.browser.unit.RecordUnit.TABLE_HISTORY)
            }
            action.addHistory(com.petal.browser.database.Record(effectiveTitle, targetUrl, System.currentTimeMillis(), 0))
            action.close()
            com.petal.browser.unit.PetalSessionHistoryManager.recordSessionVisit(targetUrl)
            lastRecordedHistoryUrl = targetUrl
        } catch (_: Exception) {}

        try {
            com.petal.browser.unit.TabSessionManager.saveSession(context)
        } catch (_: Exception) {}
    }

    /**
     * Adopts a GeckoSession that was already opened by GeckoView's [onNewSession] callback.
     * Closes the auto-created session from [initGeckoSession], swaps in the popup session,
     * re-registers all delegates, and attaches it to the GeckoView surface.
     *
     * MUST be called on the main thread after GeckoView has called session.open() on popupSession.
     */
    @MainThread
    fun adoptPopupSession(popupSession: GeckoSession) {
        // Detach the automatically-created session from GeckoView before closing it.
        // Closing an attached session while an OAuth/login popup is being adopted can
        // race Gecko's compositor teardown and crash the native content process.
        if (session !== popupSession) {
            try {
                geckoView.releaseSession()
            } catch (_: Throwable) {
                // Older GeckoView builds may already have released the view.
            }
            try {
                if (session.isOpen) session.close()
            } catch (_: Throwable) {
                // The session may have completed teardown between the checks.
            }
        }
        session = popupSession
        // Re-init all delegates on the adopted session without calling open() again.
        initGeckoSession()
    }

    private fun updateProgress(progress: Int) {
        // GeckoView uses the same shared browser loading surface as WebView.
        // BrowserActivity renders it with the exact Material 3 Expressive
        // LinearWavyProgressIndicator used by Essentials' App Updater.
        if (isForegroundTab && globalBrowserController != null) {
            val p = if (!isStopped) progress else BrowserUnit.LOADING_STOPPED
            globalBrowserController?.updateProgress(p)
        }
    }

    fun applySettings() {
        val profile = getProfile(context)
        // BrowserNavigationDelegate's "Desktop site" toggle saves under "${profile}_desktop"
        // (e.g. "profileStandard_desktop") - this used to read the unrelated, never-written
        // "sp_desktop_site" key instead, so the toggle reverted to mobile on the very next
        // navigation, new tab, or session restore. "sp_desktop_site" is kept as a fallback
        // only for anyone who had it set from an older build.
        val desktopEnabled = sp.getBoolean("${profile}_desktop", sp.getBoolean("sp_desktop_site", false))
        applyDesktopMode(desktopEnabled)
        session.settings.useTrackingProtection = true
        val enableJs = sp.getBoolean("sp_javascript", true)
        session.settings.allowJavascript = enableJs
    }

    fun setDesktopMode(enabled: Boolean) {
        applyDesktopMode(enabled)
        session.reload()
    }

    /**
     * GeckoView decoupled the desktop viewport from userAgentMode: setting USER_AGENT_MODE_DESKTOP
     * alone no longer gives the page a desktop-width layout, it only changes the UA string. Without
     * also setting viewportMode, sites saw a desktop User-Agent but Gecko still laid the page out
     * in the narrow mobile viewport - pages reported themselves as "desktop" yet still rendered
     * zoomed-in/broken as if on a phone. Both must be set together for "Desktop site" to work.
     */
    private fun applyDesktopMode(enabled: Boolean) {
        session.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.settings.viewportMode = if (enabled) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation Methods
    // ─────────────────────────────────────────────────────────────────────────

    fun loadUrl(url: String) {
        val redirected = BrowserUnit.redirectURL(sp, url)
        val targetUrl = BrowserUnit.queryWrapper(context, redirected)

        // APKMirror download endpoints are file responses, not pages. Rendering the
        // download.php response in Gecko can create a large transient document and
        // trigger an Android low-memory kill before the download starts.
        val targetUri = try { android.net.Uri.parse(targetUrl) } catch (_: Exception) { null }
        val isApkMirrorDownload = targetUri?.host?.endsWith("apkmirror.com", ignoreCase = true) == true &&
            (targetUri.path?.contains("/download.php", ignoreCase = true) == true ||
             targetUri.path?.endsWith(".apk", ignoreCase = true) == true)
        if (isApkMirrorDownload) {
            val activity = getHostActivity()
            if (activity != null) {
                BrowserUnit.download(activity, targetUrl, HelperUnit.resolveFileName(targetUrl, null, "application/vnd.android.package-archive"), "application/vnd.android.package-archive")
                return
            }
        }

        if (BrowserUnit.isHomePage(targetUrl) || BrowserUnit.isHomePage(url)) {
            session.loadUri("about:blank")
            currentUrl = "about:blank"
            currentTitle = "Petal Home"
            album.setAlbumTitle("Petal Home", "petal://home")
            return
        }

        currentUrl = targetUrl
        album.setAlbumTitle(targetUrl, targetUrl)
        session.loadUri(targetUrl)
    }

    /**
     * Recovers a GeckoSession after its content process has crashed or been killed.
     * Per GeckoView's documented contract, the session is permanently closed and
     * unusable at that point; re-opening it against the shared runtime and reloading
     * the last URL is the only way to restore a usable page. No page state (scroll
     * position, form data) survives this - that data is lost with the killed process.
     */
    private fun recoverCrashedSession() {
        try {
            val now = System.currentTimeMillis()
            if (now - lastCrashRecoveryTime < 8000L) {
                crashRecoveryCount++
            } else {
                crashRecoveryCount = 1
            }
            lastCrashRecoveryTime = now

            if (crashRecoveryCount > 3) {
                android.util.Log.e(TAG, "Repeated GeckoSession crash detected ($crashRecoveryCount times in short window). Halting recovery loop.")
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        com.petal.browser.view.NinjaToast.show(context, "Page repeatedly crashed. Stopped auto-reloading.")
                    }
                }
                return
            }

            // A killed Gecko content process leaves its GeckoSession permanently unusable.
            // Recreate the session and all delegates instead of reopening the dead instance.
            try { geckoView.releaseSession() } catch (_: Throwable) {}
            try { if (session.isOpen) session.close() } catch (_: Throwable) {}
            session = GeckoSession()
            initGeckoSession()

            // Rebinding a fresh session also gives GeckoView a new compositor surface.
            // Reopening the session alone isn't enough: GeckoView's compositor can stay
            // bound to the dead content process's Surface, so the reloaded page finishes
            // "loading" (title/progress/URL bar all update via the delegates above) but no
            // pixels ever get composited - exactly the "page loads but shows blank" symptom.
            // Detaching and reattaching the underlying GeckoView forces it through
            // onDetachedFromWindow/onAttachedToWindow, which makes GeckoView bind a fresh
            // GeckoDisplay/Surface to the new content process instead of the stale one.
            //
            // FIX: Defer the addView() inside post() so that onAttachedToWindow fires only
            // after the window has been fully laid out and has valid WindowInsets.
            // Without this deferral, GeckoView$Display.onGlobalLayout() calls
            // windowInsets.getInsets() on a null WindowInsets reference → NPE crash.
            val urlToRestore = currentUrl
            val parent = geckoView.parent as? ViewGroup
            if (parent != null) {
                val index = parent.indexOfChild(geckoView)
                parent.removeView(geckoView)
                // Post the re-attach to the next layout pass so the window's insets are ready.
                post {
                    try {
                        parent.addView(geckoView, index)
                        if (urlToRestore.isNotEmpty() && !urlToRestore.equals("about:blank", ignoreCase = true)) {
                            session.loadUri(urlToRestore)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to re-attach GeckoView after session recovery: ${e.message}", e)
                    }
                }
            } else {
                if (urlToRestore.isNotEmpty() && !urlToRestore.equals("about:blank", ignoreCase = true)) {
                    session.loadUri(urlToRestore)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to recover crashed GeckoSession: ${e.message}", e)
        }
    }

    fun canGoBack(): Boolean = canGoBackVal

    fun hasBackHistory(): Boolean = canGoBackVal || backHistoryUrls.isNotEmpty()

    fun canGoForward(): Boolean = canGoForwardVal

    fun goBack() {
        if (canGoBackVal) {
            isNavigatingHistory = true
            if (backHistoryUrls.isNotEmpty()) {
                backHistoryUrls.removeAt(backHistoryUrls.size - 1)
            }
            session.goBack()
        } else {
            val previousUrl = backHistoryUrls.removeLastOrNull() ?: return
            isNavigatingHistory = true
            currentUrl = previousUrl
            session.loadUri(previousUrl)
        }
    }

    fun goForward() {
        if (canGoForwardVal) {
            isNavigatingHistory = true
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

    fun reloadWithoutInit() {
        isStopped = false
        session.reload()
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (direction < 0) {
            // Check if we can scroll up: return true if scrolled down (currentScrollY > 0)
            return currentScrollY > 0 || geckoView.canScrollVertically(direction)
        }
        return geckoView.canScrollVertically(direction)
    }

    fun getPageScrollY(): Int = currentScrollY

    fun getPageScrollX(): Int = currentScrollX

    fun setProfileChanged() {
        applySettings()
    }

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        try {
            if (script.startsWith("javascript:")) {
                session.loadUri(script)
            } else {
                session.loadUri("javascript:(function(){try{" + script + "}catch(e){}})();")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "evaluateJavascript error: " + e.message)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AlbumController Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun getAlbumView(): View = this

    fun getTabView(): View = album.albumView

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
        globalBrowserController = controller
        album.setBrowserController(controller)
    }

    fun setOnScrollChangeListener(listener: OnScrollChangeListener?) {
        this.onScrollChangeListener = listener
    }

    fun getMediaBridge(): PetalMediaBridge? = mediaBridge

    fun setMediaBridge(bridge: PetalMediaBridge?) {
        this.mediaBridge = bridge
        bridge?.attachGeckoView(this)
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
        if (!canGoBackVal) return null
        return backHistoryUrls.lastOrNull()
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

    /**
     * Keep both the wrapper and GeckoView's actual rendering child out of Android's
     * system-gesture exclusion regions. GeckoView can recreate/update its child view
     * during navigation, so clearing only the wrapper is not sufficient for Android 13+
     * predictive-back gestures.
     */
    fun resetGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Call the framework implementation directly. Assigning the
                // property here dispatches to our override and recursively
                // re-enters this method until the process stack overflows.
                super.setSystemGestureExclusionRects(java.util.Collections.emptyList())
            } catch (_: Throwable) {}
            try {
                geckoView.systemGestureExclusionRects = java.util.Collections.emptyList()
            } catch (_: Throwable) {}
        }
    }

    override fun setSystemGestureExclusionRects(rects: MutableList<android.graphics.Rect>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                super.setSystemGestureExclusionRects(java.util.Collections.emptyList())
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Only ACTION_DOWN near the left/right screen edge can be the start of a predictive-back
     * swipe, so that's the only case that needs exclusion rects cleared. Previously this was
     * running on every touch or completely omitted. Restricting this to edge touches (e.g. 48dp
     * edge zone) ensures Android's system back gesture isn't blocked by GeckoView's internal
     * exclusion rects, while completely keeping login taps and page taps away from iterating
     * compositor child views.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val edgeZone = (48f * resources.displayMetrics.density).toInt()
            val screenWidth = resources.displayMetrics.widthPixels
            val rawX = ev.rawX
            if (rawX < edgeZone || rawX > (screenWidth - edgeZone)) {
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

    override fun gatherTransparentRegion(region: Region?): Boolean {
        return try {
            super.gatherTransparentRegion(region)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * SafeGeckoView
 * ─────────────────────────────────────────────────────────────────────────
 * GeckoView subclass that safely wraps gatherTransparentRegion().
 * On Android 16 (SDK 36) and modern OEM devices (Realme/OPPO/OnePlus ColorOS),
 * triggering a login/autofill popup or IME transition triggers ViewRootImpl
 * performTraversals() -> gatherTransparentRegion(). GeckoView.gatherTransparentRegion()
 * invokes its private Display.onGlobalLayout(), which unconditionally attempts to call
 * windowInsets.getInsets(...) on mSurfaceWrapper.getView().getRootWindowInsets().
 * When the window or decor view has not yet attached its window insets or during
 * transient window focus/IME changes, getRootWindowInsets() returns null, triggering a
 * fatal NullPointerException on the main thread:
 * "Attempt to invoke virtual method 'android.graphics.Insets android.view.WindowInsets.getInsets(int)' on a null object reference".
 *
 * Intercepting gatherTransparentRegion() with a try-catch guarantees that transient
 * null root WindowInsets will never crash the browser process, allowing layout traversal
 * to complete normally.
 */
class SafeGeckoView : GeckoView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun gatherTransparentRegion(region: Region?): Boolean {
        return try {
            super.gatherTransparentRegion(region)
        } catch (e: NullPointerException) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView gatherTransparentRegion NPE: ${e.message}")
            false
        }
    }
}

