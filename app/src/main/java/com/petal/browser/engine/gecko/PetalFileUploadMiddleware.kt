package com.petal.browser.engine.gecko

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import com.petal.browser.compose.file.PetalUploadChooser
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import java.lang.ref.WeakReference

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

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        if (action is ContentAction.UpdatePromptRequestAction) {
            val request = action.promptRequest
            if (request is PromptRequest.File) {
                val activity = activityRef?.get()
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    handle(activity, request)
                    return
                }
            }
        }
        next(action)
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

    companion object {
        @Volatile
        private var activityRef: WeakReference<ComponentActivity>? = null

        @JvmStatic
        fun attachActivity(activity: ComponentActivity) {
            activityRef = WeakReference(activity)
        }

        @JvmStatic
        fun detachActivity(activity: ComponentActivity) {
            if (activityRef?.get() === activity) activityRef = null
        }
    }
}
