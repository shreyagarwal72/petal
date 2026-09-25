package com.petal.browser.extensions

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.concept.engine.EngineSession

/**
 * PetalExtensionPopupSheet
 * ─────────────────────────────────────────────────────────────────────────
 * BottomSheetDialog hosting an [EngineView] to display interactive WebExtension
 * popups (e.g., uBlock Origin dashboard, Bitwarden unlock & vault, Dark Reader toggles).
 */
class PetalExtensionPopupSheet(
    context: Context,
    popupSession: EngineSession
) : BottomSheetDialog(context) {

    init {
        val engineView = PetalEngineStore.createEngineView(context)
        engineView.render(popupSession)
        val view = engineView.asView()
        setContentView(view)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
