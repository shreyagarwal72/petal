package com.petal.browser.ui.selection

import android.app.Activity
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.menu.BrowserContextMenuManager
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.BrowserUnit
import org.mozilla.geckoview.GeckoSession.SelectionActionDelegate as Sel

/**
 * The selection toolbar for text selected on a web page (GeckoView). GeckoView tells us the
 * selection's bounds and which actions are available; this draws the row over the page, in
 * the same Material 3 Expressive / liquid glass look as the Compose text-field toolbar.
 *
 * One transparent [ComposeView] per activity is added on top of the window. It consumes no
 * touches outside the toolbar itself, so selection handles and scrolling keep working.
 */
internal class PageSelection(val rectInWindow: RectF, val actions: List<PetalSelectionAction>)

object PetalPageSelectionToolbar {

    private class Holder(val view: ComposeView) {
        val state = mutableStateOf<PageSelection?>(null)
    }

    private val holders = java.util.WeakHashMap<Activity, Holder>()

    /**
     * Shows (or moves) the toolbar for [selection]. [anchor] is the view GeckoView draws in;
     * the selection's rect is relative to it. Returns false when the custom toolbar is off or
     * there is nothing to show, so the caller can fall back to the old selection sheet.
     */
    @JvmStatic
    fun show(activity: Activity, anchor: View, selection: Sel.Selection): Boolean {
        if (!PetalSelectionToolbarPrefs.isEnabled(activity)) return false
        val screenRect: RectF = (try { selection.screenRect } catch (_: Throwable) { null }) ?: return false
        val actions = buildActions(activity, selection)
        if (actions.isEmpty()) return false

        val decor = activity.window.decorView
        val locOnScreen = IntArray(2)
        decor.getLocationOnScreen(locOnScreen)
        val inWindow = RectF(
            screenRect.left - locOnScreen[0],
            screenRect.top - locOnScreen[1],
            screenRect.right - locOnScreen[0],
            screenRect.bottom - locOnScreen[1]
        )
        holderFor(activity).state.value = PageSelection(inWindow, actions)
        return true
    }

    @JvmStatic
    fun hide(activity: Activity) {
        holders[activity]?.state?.value = null
    }

    private fun holderFor(activity: Activity): Holder {
        holders[activity]?.let { return it }
        val state = Holder(ComposeView(activity))
        val view = state.view
        view.apply {
            (activity as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (activity as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PetalExpressiveTheme {
                    PageSelectionOverlay(state.state)
                }
            }
        }
        (activity.window.decorView as ViewGroup).addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        holders[activity] = state
        return state
    }

    private fun buildActions(activity: Activity, selection: Sel.Selection): List<PetalSelectionAction> {
        fun available(id: String): Boolean = try { selection.isActionAvailable(id) } catch (_: Throwable) { false }
        fun run(id: String) {
            try { selection.execute(id) } catch (_: Throwable) {}
            hide(activity)
        }

        val text: String = (try { selection.text } catch (_: Throwable) { null }) ?: ""
        val browser = activity as? BrowserActivity
        val out = ArrayList<PetalSelectionAction>()

        if (available(Sel.ACTION_CUT)) {
            out += PetalSelectionAction("cut", "Cut", Icons.Rounded.ContentCut) { run(Sel.ACTION_CUT) }
        }
        if (available(Sel.ACTION_COPY)) {
            out += PetalSelectionAction("copy", "Copy", Icons.Rounded.ContentCopy) { run(Sel.ACTION_COPY) }
        }
        if (available(Sel.ACTION_PASTE)) {
            out += PetalSelectionAction("paste", "Paste", Icons.Rounded.ContentPaste) { run(Sel.ACTION_PASTE) }
        }
        if (available(Sel.ACTION_SELECT_ALL)) {
            out += PetalSelectionAction("select_all", "Select all", Icons.Rounded.SelectAll) {
                try { selection.execute(Sel.ACTION_SELECT_ALL) } catch (_: Throwable) {}
            }
        }
        if (browser != null && text.isNotBlank()) {
            out += PetalSelectionAction("search", "Search", Icons.Rounded.Search) {
                hide(activity)
                val query = BrowserUnit.queryWrapper(browser, text)
                browser.addAlbum(text.take(24), query, false)
            }
            out += PetalSelectionAction("share", "Share", Icons.Rounded.Share) {
                hide(activity)
                browser.shareLink("Shared Text", text)
            }
            // Everything the old selection sheet offered stays one tap away.
            out += PetalSelectionAction("more", "More", Icons.Rounded.MoreHoriz) {
                hide(activity)
                BrowserContextMenuManager.showSelectionContextMenu(browser, text)
            }
        }
        return out
    }
}

@Composable
private fun PageSelectionOverlay(state: State<PageSelection?>) {
    val selection = state.value ?: return
    val density = LocalDensity.current
    val style = rememberSelectionToolbarStyle()
    val statusTop = WindowInsets.statusBars.getTop(density)
    val margin = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 10.dp.roundToPx() }
    val below = with(density) { 30.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(0, (constraints.maxWidth - 2 * margin).coerceAtLeast(0), 0, constraints.maxHeight)
                )
                val window = IntSize(constraints.maxWidth, constraints.maxHeight)
                layout(window.width, window.height) {
                    val at = placeSelectionToolbar(
                        left = selection.rectInWindow.left,
                        top = selection.rectInWindow.top,
                        right = selection.rectInWindow.right,
                        bottom = selection.rectInWindow.bottom,
                        content = IntSize(placeable.width, placeable.height),
                        window = window,
                        topSafe = statusTop,
                        margin = margin,
                        gap = gap,
                        below = below
                    )
                    placeable.place(at)
                }
            }
    ) {
        PetalSelectionToolbar(actions = selection.actions, style = style)
    }
}
