package com.petal.browser.engine.gecko

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.PetalOwnedDelegate
import com.petal.browser.compose.menu.BrowserContextMenuManager
import com.petal.browser.logger.PetalAppLogger
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

/**
 * PetalContentDelegateWrapper
 * ─────────────────────────────────────────────────────────────────────────────
 * Preserves Mozilla engine's own ContentDelegate (for title, fullScreen, crash,
 * and firstContentfulPaint callbacks) while handling Petal's onExternalResponse and onContextMenu.
 */
class PetalContentDelegateWrapper(
    val engine: GeckoSession.ContentDelegate?,
    private var contextProvider: () -> Context,
    private var isPrivateProvider: () -> Boolean = { false },
    private var referrerProvider: () -> String? = { null },
    private var onCrashCallback: (() -> Unit)? = null,
    private var pageUrlProvider: () -> String? = { null },
    private var pageTitleProvider: () -> String? = { null }
) : GeckoSession.ContentDelegate, PetalOwnedDelegate {

    /** Re-points every provider at a newly bound view (the session can outlive its view). */
    fun rebind(
        contextProvider: () -> Context,
        isPrivateProvider: () -> Boolean,
        referrerProvider: () -> String?,
        onCrashCallback: (() -> Unit)?,
        pageUrlProvider: () -> String?,
        pageTitleProvider: () -> String?
    ) {
        this.contextProvider = contextProvider
        this.isPrivateProvider = isPrivateProvider
        this.referrerProvider = referrerProvider
        this.onCrashCallback = onCrashCallback
        this.pageUrlProvider = pageUrlProvider
        this.pageTitleProvider = pageTitleProvider
    }

    private fun resolveActivity(): Activity? {
        var ctx: Context? = contextProvider()
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    override fun onTitleChange(session: GeckoSession, title: String?) {
        engine?.onTitleChange(session, title)
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        val act = resolveActivity()
        if (act is MainActivity) {
            act.runOnUiThread {
                act.setCustomFullscreen(fullScreen)
            }
        }
        engine?.onFullScreen(session, fullScreen)
    }

    override fun onCloseRequest(session: GeckoSession) {
        engine?.onCloseRequest(session)
    }

    override fun onFocusRequest(session: GeckoSession) {
        engine?.onFocusRequest(session)
    }

    override fun onCrash(session: GeckoSession) {
        val targetPage = pageUrlProvider() ?: "unknown URL"
        val ctx = contextProvider().applicationContext
        PetalAppLogger.recordProcessCrash(
            ctx,
            "GeckoEngine",
            "GeckoSession content process crashed on $targetPage",
            "URL: $targetPage"
        )
        engine?.onCrash(session)
        onCrashCallback?.invoke()
    }

    override fun onKill(session: GeckoSession) {
        val targetPage = pageUrlProvider() ?: "unknown URL"
        val ctx = contextProvider().applicationContext
        PetalAppLogger.recordProcessCrash(
            ctx,
            "GeckoEngine",
            "GeckoSession content process was killed by OS on $targetPage",
            "URL: $targetPage"
        )
        engine?.onKill(session)
        onCrashCallback?.invoke()
    }

    override fun onFirstComposite(session: GeckoSession) {
        engine?.onFirstComposite(session)
    }

    override fun onFirstContentfulPaint(session: GeckoSession) {
        engine?.onFirstContentfulPaint(session)
    }

    // Mozilla's GeckoEngineSession turns this into onPreviewImageChange (page preview / og:image
    // for the tab). Without forwarding it the callback was silently dropped.
    override fun onPreviewImage(session: GeckoSession, previewImageUrl: String) {
        engine?.onPreviewImage(session, previewImageUrl)
    }

    override fun onPaintStatusReset(session: GeckoSession) {
        engine?.onPaintStatusReset(session)
    }

    override fun onContextMenu(
        session: GeckoSession,
        screenX: Int,
        screenY: Int,
        element: GeckoSession.ContentDelegate.ContextElement
    ) {
        // Long-press parsing is Mozilla's: forwarding to the engine session lets GeckoEngineSession
        // turn the element into a HitResult in the BrowserStore, and observeContextMenu()
        // shows Petal's own menu for it.
        //
        // Mozilla produces no HitResult for a press on empty page area (no link, no media), so
        // that single case keeps Petal's page menu.
        val act = resolveActivity()
        if (act is MainActivity &&
            element.linkUri.isNullOrBlank() && element.srcUri.isNullOrBlank() &&
            element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_NONE
        ) {
            act.runOnUiThread {
                BrowserContextMenuManager.handleContextMenu(
                    activity = act,
                    hitResult = null,
                    pageUrl = pageUrlProvider(),
                    pageTitle = pageTitleProvider()
                )
            }
            return
        }
        engine?.onContextMenu(session, screenX, screenY, element)
    }

    override fun onExternalResponse(
        session: GeckoSession,
        response: WebResponse
    ) {
        val act = resolveActivity()
        if (act != null) {
            PetalEngineBridge.handleExternalResponse(
                act, response, isPrivateProvider(), referrerProvider()
            )
            // Handled here. Do NOT also forward to Mozilla's delegate: it would publish the same
            // download as ContentState.download and PetalDownloadPromptObserver would prompt again.
            return
        }
        // No activity to show UI on: let the engine publish it to the store; the observer
        // (PetalDownloadPromptObserver) picks it up when an activity is available.
        engine?.onExternalResponse(session, response)
    }

    override fun onShowDynamicToolbar(session: GeckoSession) {
        engine?.onShowDynamicToolbar(session)
    }

    override fun onWebAppManifest(session: GeckoSession, manifest: JSONObject) {
        engine?.onWebAppManifest(session, manifest)
    }

    override fun onMetaViewportFitChange(session: GeckoSession, viewportFit: String) {
        engine?.onMetaViewportFitChange(session, viewportFit)
    }

    override fun onSlowScript(
        session: GeckoSession,
        scriptFileName: String
    ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.SlowScriptResponse>? {
        val act = resolveActivity()
        if (act == null || act.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed)) {
            return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.SlowScriptResponse.CONTINUE)
        }
        val result = org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.SlowScriptResponse>()
        act.runOnUiThread {
            try {
                com.petal.browser.ui.dialog.PetalSlowScriptDialog.show(
                    activity = act,
                    scriptFileName = scriptFileName,
                    onWait = { result.complete(org.mozilla.geckoview.SlowScriptResponse.CONTINUE) },
                    onStop = { result.complete(org.mozilla.geckoview.SlowScriptResponse.STOP) }
                )
            } catch (t: Throwable) {
                result.complete(org.mozilla.geckoview.SlowScriptResponse.CONTINUE)
            }
        }
        return result
    }
}
