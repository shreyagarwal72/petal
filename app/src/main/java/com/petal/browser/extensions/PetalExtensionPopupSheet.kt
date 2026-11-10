package com.petal.browser.extensions

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.ui.theme.PetalExpressiveTheme
import mozilla.components.concept.engine.EngineSession

/**
 * PetalExtensionPopupSheet
 * ─────────────────────────────────────────────────────────────────────────
 * Pure Compose ModalBottomSheet hosting an [EngineView] to display interactive WebExtension
 * popups (e.g., uBlock Origin dashboard, Bitwarden unlock & vault, Dark Reader toggles).
 */
object PetalExtensionPopupSheet {

    @OptIn(ExperimentalMaterial3Api::class)
    @JvmStatic
    fun show(
        activity: ComponentActivity,
        popupSession: EngineSession,
        onDismiss: () -> Unit = {}
    ) {
        val decorView = activity.window?.decorView as? ViewGroup ?: return
        lateinit var composeView: ComposeView

        val dismissAction: () -> Unit = {
            if (composeView.parent != null) {
                decorView.removeView(composeView)
            }
            onDismiss()
        }

        composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                PetalExpressiveTheme {
                    ModalBottomSheet(
                        onDismissRequest = dismissAction,
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        shape = BottomSheetDefaults.ExpandedShape,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val engineView = PetalEngineStore.createEngineView(ctx)
                                engineView.render(popupSession)
                                engineView.asView()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 500.dp)
                        )
                    }
                }
            }
        }

        decorView.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}
