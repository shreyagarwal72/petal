package com.petal.browser.compose.find

import android.content.Context
import android.view.View
import androidx.preference.PreferenceManager
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.content.FindResultState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.findinpage.FindInPageFeature
import mozilla.components.feature.findinpage.view.FindInPageView

/**
 * PetalFindInPageBridge
 * ─────────────────────────────────────────────────────────────────────────
 * Adapts Mozilla Android Components `feature-findinpage` (FindInPageFeature,
 * FindInPageInteractor, FindInPagePresenter) to Petal's M3E Liquid Glass find bar.
 */
class PetalFindInPageBridge(
    private val context: Context,
    private val store: BrowserStore,
    private val engineViewSupplier: () -> EngineView?,
    private val onCloseCallback: () -> Unit
) : FindInPageView {

    override var listener: FindInPageView.Listener? = null
    override var private: Boolean = false

    var feature: FindInPageFeature? = null
        private set

    var currentResult: FindResultState? = null
        private set

    override fun displayResult(result: FindResultState) {
        currentResult = result
    }

    override fun focus() {
        // Compose BasicTextField handles focus via FocusRequester
    }

    override fun clear() {
        currentResult = null
    }

    override fun asView(): View = View(context)

    fun start() {
        val engineView = engineViewSupplier() ?: return
        val feat = FindInPageFeature(
            store = store,
            view = this,
            engineView = engineView,
            onClose = onCloseCallback
        )
        feature = feat
        feat.start()
        store.state.selectedTab?.let { tab ->
            feat.bind(tab)
        }
    }

    fun stop() {
        feature?.stop()
        feature = null
    }

    fun bind(session: SessionState) {
        feature?.bind(session)
    }

    fun unbind() {
        feature?.unbind()
    }

    companion object {
        @JvmStatic
        fun isMozillaFindInPageEnabled(context: Context): Boolean {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            return sp.getBoolean("sp_ac_phase1", false) || sp.getBoolean("sp_mozilla_find_in_page", false)
        }
    }
}
