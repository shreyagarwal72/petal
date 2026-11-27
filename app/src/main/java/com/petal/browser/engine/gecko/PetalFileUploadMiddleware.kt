package com.petal.browser.engine.gecko

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import com.petal.browser.compose.file.PetalUploadChooser
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sends every website `<input type="file">` request to Petal's own pickers instead of the Android
 * system chooser (the Camera / Video / Files / More sheet).
 *
 * Mozilla's flow stays intact: Gecko asks for files, GeckoEngineSession turns that into a
 * `PromptRequest.File` and dispatches `ContentAction.UpdatePromptRequestAction`. Mozilla's
 * PromptFeature would normally open the system chooser for it. This middleware sits in front of the
 * reducer, takes the file request, opens [PetalUploadChooser] (Petal media picker / file picker /
 * two-choice sheet) and answers the request through Mozilla's own callbacks
 * (`onSingleFileSelected`, `onMultipleFilesSelected`, `onDismiss`), so Gecko receives the files
 * exactly as it would from the system picker.
 *
 * If no Petal activity is attached (for example in a background service) the request is passed on
 * unchanged, so Mozilla's PromptFeature still handles it.
 */
class PetalFileUploadMiddleware : Middleware<BrowserState, BrowserAction> {

    private data class PendingFilePrompt(
        val tabId: String,
        val request: PromptRequest.File
    )

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        when (action) {
            is ContentAction.UpdatePromptRequestAction -> {
                val request = action.promptRequest
                if (request is PromptRequest.File) {
                    val activity = activityRef?.get()
                    if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                        handle(activity, request)
                    } else {
                        pendingRequests.add(PendingFilePrompt(action.sessionId, request))
                    }
                    return
                }
            }
            is TabListAction.RemoveTabAction -> {
                dismissPendingForTabs(setOf(action.tabId))
            }
            is TabListAction.RemoveTabsAction -> {
                dismissPendingForTabs(action.tabIds.toSet())
            }
            is TabListAction.RemoveAllTabsAction,
            is TabListAction.RemoveAllNormalTabsAction -> {
                val it = pendingRequests.iterator()
                val toRemove = ArrayList<PendingFilePrompt>()
                while (it.hasNext()) {
                    val item = it.next()
                    if (action is TabListAction.RemoveAllTabsAction ||
                        store.state.tabs.firstOrNull { t -> t.id == item.tabId }?.content?.private != true
                    ) {
                        toRemove.add(item)
                    }
                }
                if (toRemove.isNotEmpty()) {
                    pendingRequests.removeAll(toRemove.toSet())
                    toRemove.forEach { runCatching { it.request.onDismiss() } }
                }
            }
            is TabListAction.RemoveAllPrivateTabsAction -> {
                val toRemove = pendingRequests.filter { item ->
                    store.state.tabs.firstOrNull { t -> t.id == item.tabId }?.content?.private == true
                }
                if (toRemove.isNotEmpty()) {
                    pendingRequests.removeAll(toRemove.toSet())
                    toRemove.forEach { runCatching { it.request.onDismiss() } }
                }
            }
            else -> Unit
        }
        next(action)
    }

    private fun dismissPendingForTabs(tabIds: Set<String>) {
        if (tabIds.isEmpty() || pendingRequests.isEmpty()) return
        val toRemove = pendingRequests.filter { it.tabId in tabIds }
        if (toRemove.isNotEmpty()) {
            pendingRequests.removeAll(toRemove.toSet())
            toRemove.forEach { runCatching { it.request.onDismiss() } }
        }
    }

    companion object {
        @Volatile
        private var activityRef: WeakReference<ComponentActivity>? = null
        private val pendingRequests = CopyOnWriteArrayList<PendingFilePrompt>()

        @JvmStatic
        fun attachActivity(activity: ComponentActivity) {
            activityRef = WeakReference(activity)
            if (pendingRequests.isNotEmpty() && !activity.isFinishing && !activity.isDestroyed) {
                val drained = pendingRequests.toList()
                pendingRequests.clear()
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) {
                        pendingRequests.addAll(drained)
                        return@runOnUiThread
                    }
                    drained.forEach { pending ->
                        handle(activity, pending.request)
                    }
                }
            }
        }

        @JvmStatic
        fun detachActivity(activity: ComponentActivity) {
            if (activityRef?.get() === activity) activityRef = null
        }

        private fun handle(activity: ComponentActivity, request: PromptRequest.File) {
            val multiple = request.isMultipleFilesSelection
            val mimeTypes = request.mimeTypes.filter { it.isNotBlank() }.toTypedArray()
            val capture = request.captureMode.name != "NONE"
            var answered = false

            val callback = ValueCallback<Array<Uri>> { uris ->
                if (answered) return@ValueCallback
                answered = true
                when {
                    uris == null || uris.isEmpty() -> request.onDismiss()
                    multiple -> request.onMultipleFilesSelected(activity, uris)
                    else -> request.onSingleFileSelected(activity, uris[0])
                }
            }

            val params = object : WebChromeClient.FileChooserParams() {
                override fun getMode(): Int = if (multiple) MODE_OPEN_MULTIPLE else MODE_OPEN
                override fun getAcceptTypes(): Array<String> = mimeTypes
                override fun isCaptureEnabled(): Boolean = capture
                override fun getTitle(): CharSequence? = null
                override fun getFilenameHint(): String? = null
                override fun createIntent(): android.content.Intent =
                    android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        if (multiple) putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                        type = mimeTypes.firstOrNull() ?: "*/*"
                        if (mimeTypes.size > 1) putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes)
                    }
            }

            try {
                PetalUploadChooser.show(activity, callback, params) { callback.onReceiveValue(null) }
            } catch (t: Throwable) {
                callback.onReceiveValue(null)
            }
        }
    }
}
