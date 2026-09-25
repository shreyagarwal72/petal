package com.petal.browser.browser

import android.content.Context
import android.view.View
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView

/**
 * PetalTabViewController
 * ─────────────────────────────────────────────────────────────────────────
 * Mozilla Android Components [EngineView] + [EngineSession] primary tab controller.
 *
 * Implements [AlbumController] to seamlessly bridge GeckoEngine's EngineView
 * into Petal's tab switching, lifecycle, and view hierarchy.
 */
class PetalTabViewController(
    private val context: Context,
    val tabId: String,
    val engineSession: EngineSession,
    private val initialTitle: String = "",
    private val initialUrl: String = "",
    private val incognito: Boolean = false
) : AlbumController {

    val engineView: EngineView = PetalEngineStore.createEngineView(context).apply {
        render(engineSession)
    }

    private var currentTitle: String = initialTitle
    private var currentUrl: String = initialUrl

    fun loadUrl(url: String) {
        currentUrl = url
        engineSession.loadUrl(url)
    }

    fun goBack(userInteraction: Boolean = true) {
        engineSession.goBack(userInteraction = userInteraction)
    }

    fun goForward(userInteraction: Boolean = true) {
        engineSession.goForward(userInteraction = userInteraction)
    }

    fun reload() {
        engineSession.reload()
    }

    fun stopLoading() {
        engineSession.stopLoading()
    }

    override fun getAlbumView(): View {
        return engineView.asView()
    }

    override fun activate() {
        // EngineSession activation is managed reactively via BrowserStore
    }

    override fun deactivate() {
        // EngineSession deactivation is managed reactively via BrowserStore
    }

    override fun getTitle(): String {
        return currentTitle
    }

    fun setTitle(title: String) {
        this.currentTitle = title
    }

    override fun getUrl(): String {
        return currentUrl
    }

    fun setUrl(url: String) {
        this.currentUrl = url
    }

    override fun isIncognito(): Boolean {
        return incognito
    }

    override fun destroy() {
        try {
            engineView.release()
        } catch (_: Throwable) {}
    }
}
