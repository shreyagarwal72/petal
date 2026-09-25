package com.petal.browser.browser

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView

/**
 * PetalTabViewController
 * ─────────────────────────────────────────────────────────────────────────
 * High-performance tab view controller hosting Mozilla Android Components [EngineView].
 *
 * Bridges the UI layout hierarchy with Mozilla's [EngineSession] and [BrowserStore]
 * state machine, allowing tabs to render content and track loading/navigation events.
 */
class PetalTabViewController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val engineView: EngineView = PetalEngineStore.createEngineView(context)
    private var currentSession: EngineSession? = null
    private var currentTabId: String? = null

    init {
        val view = engineView.asView()
        addView(
            view,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * Binds a [TabSessionState] to this controller's [EngineView].
     */
    fun bindTab(tab: TabSessionState, store: BrowserStore) {
        val engineSession = tab.engineState.engineSession ?: return
        if (currentSession === engineSession) return

        currentSession = engineSession
        currentTabId = tab.id

        engineView.render(engineSession)

        engineSession.register(object : EngineSession.Observer {
            override fun onLocationChange(url: String) {
                store.dispatch(ContentAction.UpdateUrlAction(tab.id, url))
            }

            override fun onTitleChange(title: String) {
                store.dispatch(ContentAction.UpdateTitleAction(tab.id, title))
            }

            override fun onProgress(progress: Int) {
                store.dispatch(ContentAction.UpdateProgressAction(tab.id, progress))
            }

            override fun onLoadingStateChange(loading: Boolean) {
                store.dispatch(ContentAction.UpdateLoadingStateAction(tab.id, loading))
            }
        })
    }

    /**
     * Detaches the currently attached session from the engine view.
     */
    fun unbind() {
        currentSession = null
        currentTabId = null
    }

    /**
     * Returns the underlying Android [View] for this controller.
     */
    fun getView(): View = this
}
