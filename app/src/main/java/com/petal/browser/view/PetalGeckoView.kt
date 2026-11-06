package com.petal.browser.view

import android.util.Log
import com.petal.browser.view.PetalToast;
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
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
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.pwa.PetalPwaManager
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.TabThumbnailCache
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.SlowScriptResponse
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.PanZoomController
import kotlinx.coroutines.launch
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
    adoptedSession: GeckoSession? = null,
    initialIncognito: Boolean = false,
    val engineSession: mozilla.components.concept.engine.EngineSession? = null
) : FrameLayout(context, attrs, defStyleAttr), AlbumController {

    companion object {
        /** Max device-pixel scroll offset still considered "at top". */
        const val PAGE_TOP_TOLERANCE_PX = 36

        private const val TAG = "PetalGeckoView"

        /**
         * Returns the GeckoSession that backs a Mozilla GeckoEngineSession, or null if unavailable.
         *
         * GeckoEngineSession.geckoSession is `internal` to Mozilla's module, so Kotlin 2.4 no longer
         * lets us reference it directly. There is no public accessor, so it is read reflectively.
         * If Mozilla renames or removes the field this logs a warning and returns null, and the caller
         * falls back to creating its own GeckoSession.
         */
        private fun extractGeckoSession(
            engineSession: mozilla.components.concept.engine.EngineSession?
        ): GeckoSession? {
            if (engineSession !is mozilla.components.browser.engine.gecko.GeckoEngineSession) return null
            return try {
                // Kotlin compiles an `internal var` getter as `getGeckoSession$<module>`, so try both.
                val cls = engineSession.javaClass
                val getter = cls.methods.firstOrNull {
                    it.parameterCount == 0 && it.name.startsWith("getGeckoSession")
                }
                val fromGetter = getter?.let {
                    it.isAccessible = true
                    it.invoke(engineSession) as? GeckoSession
                }
                fromGetter ?: cls.getDeclaredField("geckoSession").let {
                    it.isAccessible = true
                    it.get(engineSession) as? GeckoSession
                }
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Could not read GeckoEngineSession.geckoSession: ${t.message}")
                null
            }
        }

        /**
         * Compatibility bridge for GeckoView APIs that Android Components does not expose
         * through EngineSession yet, including GeckoView WebExtension delegates.
         */
        @JvmStatic
        fun getEngineGeckoSession(
            engineSession: mozilla.components.concept.engine.EngineSession?
        ): GeckoSession? = extractGeckoSession(engineSession)

        @Volatile
        private var globalBrowserControllerRef: java.lang.ref.WeakReference<BrowserController>? = null

        @JvmStatic
        fun getBrowserController(): BrowserController? = globalBrowserControllerRef?.get()

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
        @JvmOverloads
        fun handleExternalResponse(
            activity: Activity,
            response: WebResponse,
            isPrivate: Boolean = false,
            referrerUrl: String? = null
        ) {
            val responseUrl = response.uri
            if (responseUrl.isNullOrBlank()) return
            val parsed = try { android.net.Uri.parse(responseUrl) } catch (_: Exception) { null }
            val isXpiUrl = parsed?.path?.endsWith(".xpi", ignoreCase = true) == true ||
                response.headers["content-type"]?.contains("application/x-xpinstall", ignoreCase = true) == true ||
                (parsed?.host?.contains("addons.mozilla.org", ignoreCase = true) == true && responseUrl.contains(".xpi", ignoreCase = true))
            if (isXpiUrl) {
                activity.runOnUiThread {
                    com.petal.browser.extensions.PetalExtensionManager.install(responseUrl) { success, message ->
                        com.petal.browser.view.PetalToast.show(activity, message ?: if (success) "Extension installed" else "Extension installation failed")
                    }
                }
                return
            }

            // Universal UserScript detection (.user.js) from GreasyFork, OpenUserJS, GitHub, etc.
            val isUserScript = parsed?.path?.endsWith(".user.js", ignoreCase = true) == true ||
                response.headers["content-type"]?.contains("application/x-javascript", ignoreCase = true) == true && responseUrl.contains(".user.js", ignoreCase = true)
            if (isUserScript) {
                Thread {
                    try {
                        val body = response.body?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        if (!body.isNullOrBlank()) {
                            val meta = com.petal.browser.compose.store.PetalUserScriptEngine.installScript(activity, body)
                            activity.runOnUiThread {
                                com.petal.browser.view.PetalToast.show(activity, "Installed UserScript: ${meta.name}")
                            }
                        }
                    } catch (e: Exception) {
                        activity.runOnUiThread {
                            com.petal.browser.view.PetalToast.show(activity, "Failed to install UserScript: ${e.message}")
                        }
                    }
                }.start()
                return
            }
            val headers = response.headers
            val contentDisposition = headers?.entries?.firstOrNull {
                it.key.equals("Content-Disposition", ignoreCase = true)
            }?.value
            val mimeType = headers?.entries?.firstOrNull {
                it.key.equals("Content-Type", ignoreCase = true)
            }?.value
            val fileName = PetalAndroidUtils.resolveFileName(responseUrl, contentDisposition, mimeType)
            val contentLength = headers?.entries?.firstOrNull {
                it.key.equals("Content-Length", ignoreCase = true)
            }?.value?.toLongOrNull() ?: 0L

            val isBlobOrData = responseUrl.startsWith("blob:", ignoreCase = true) || responseUrl.startsWith("data:", ignoreCase = true)
            if (isBlobOrData) {
                val bodyStream = response.body
                if (bodyStream != null) {
                    activity.runOnUiThread {
                        com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                            activity, responseUrl, contentDisposition, mimeType, contentLength
                        ) { confirmedName ->
                            val finalName = confirmedName.ifBlank { fileName }
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        val contentValues = android.content.ContentValues().apply {
                                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                                        }
                                        val resolver = activity.contentResolver
                                        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                        if (uri != null) {
                                            resolver.openOutputStream(uri)?.use { output ->
                                                bodyStream.use { input -> input.copyTo(output) }
                                            }
                                        }
                                    } else {
                                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                        val targetFile = java.io.File(downloadsDir, finalName)
                                        bodyStream.use { input ->
                                            java.io.FileOutputStream(targetFile).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        android.media.MediaScannerConnection.scanFile(
                                            activity,
                                            arrayOf(targetFile.absolutePath),
                                            arrayOf(mimeType ?: "application/octet-stream"),
                                            null
                                        )
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        com.petal.browser.view.PetalToast.show(activity, "Download completed: $finalName")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PetalDownload", "Error downloading blob/data stream", e)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        com.petal.browser.view.PetalToast.show(activity, "Download failed")
                                    }
                                }
                            }
                        }
                    }
                    return
                }
            }

            if (com.petal.browser.torrent.PetalTorrentEngineManager.handleTorrentOrMagnet(activity, responseUrl, fileName, mimeType)) {
                return
            }

            // If external download manager is preferred (1DM, ADM, AB Download Manager, Navi, or Auto),
            // skip the in-app confirmation popup and launch directly into the chosen manager.
            if (com.petal.browser.unit.ExternalDownloadManagerHelper.isExternalPreferred(activity)) {
                com.petal.browser.download.PetalDownloadRouter.download(activity, responseUrl, fileName, mimeType, referrerUrl = referrerUrl, isPrivate = isPrivate)
                return
            }

            activity.runOnUiThread {
                com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                    activity, responseUrl, contentDisposition, mimeType, contentLength
                ) { confirmedName ->
                    com.petal.browser.compose.downloads.PetalFetchDownloadBridge.enqueueGeckoDownload(
                        context = activity,
                        url = responseUrl,
                        fileName = confirmedName.ifBlank { fileName },
                        mimeType = mimeType,
                        responseHeaders = headers,
                        referrerUrl = referrerUrl,
                        isPrivate = isPrivate
                    )
                }
            }
        }

        @JvmStatic
        fun handleExternalScheme(activity: Activity, url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://") ||
                lower.startsWith("about:") || lower.startsWith("blob:") ||
                lower.startsWith("data:") || lower.startsWith("javascript:") ||
                lower.startsWith("petal:") || lower.startsWith("moz-extension:") ||
                lower.startsWith("resource:") || lower.startsWith("chrome:")
            ) {
                return false
            }
            if (lower.startsWith("intent://")) {
                try {
                    val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        val pm = activity.packageManager
                        if (pm != null && intent.resolveActivity(pm) != null) {
                            activity.startActivity(intent)
                            return true
                        }
                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                        if (!fallbackUrl.isNullOrBlank() && activity is com.petal.browser.activity.BrowserActivity) {
                            activity.runOnUiThread { activity.addAlbum(null, fallbackUrl, false, false) }
                            return true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error handling intent scheme: $url", e)
                }
                return true
            }
            try {
                val parsedUri = android.net.Uri.parse(url)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, parsedUri)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                val pm = activity.packageManager
                if (pm != null && intent.resolveActivity(pm) != null) {
                    activity.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
            return true
        }

        @JvmStatic
        fun getDerivedDesktopUserAgent(context: Context): String {
            return "Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0"
        }

        @JvmStatic
        @JvmOverloads
        fun createPromptDelegate(
            session: GeckoSession,
            context: Context,
            sp: SharedPreferences,
            incognitoProvider: () -> Boolean = { false },
            activityProvider: () -> Activity?
        ): GeckoSession.PromptDelegate {
            return object : GeckoSession.PromptDelegate {
                override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showAlert(
                            act,
                            prompt.title ?: act.getString(R.string.app_name),
                            prompt.message ?: ""
                        ) {
                            result.complete(prompt.dismiss())
                        }
                    }
                    return result
                }

                override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showConfirm(
                            act,
                            prompt.title ?: act.getString(R.string.app_name),
                            prompt.message ?: "",
                            { result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)) },
                            { result.complete(prompt.dismiss()) }
                        )
                    }
                    return result
                }

                override fun onAuthPrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.AuthPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    act.runOnUiThread {
                        val isPasswordOnly = (prompt.authOptions.flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
                        val dialogTitle = prompt.title ?: prompt.authOptions.uri ?: act.getString(R.string.app_name)
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showAuth(
                            act,
                            dialogTitle,
                            prompt.message ?: "Sign In",
                            isPasswordOnly,
                            prompt.authOptions.username,
                            { user, pass ->
                                if (isPasswordOnly) {
                                    result.complete(prompt.confirm(pass))
                                } else {
                                    result.complete(prompt.confirm(user, pass))
                                }
                            },
                            { result.complete(prompt.dismiss()) }
                        )
                    }
                    return result
                }

                override fun onTextPrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.TextPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showPrompt(
                            act,
                            prompt.title ?: act.getString(R.string.app_name),
                            prompt.message ?: "",
                            prompt.defaultValue,
                            { value -> result.complete(prompt.confirm(value)) },
                            { result.complete(prompt.dismiss()) }
                        )
                    }
                    return result
                }

                override fun onChoicePrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.ChoicePrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val rows = ArrayList<com.petal.browser.ui.components.PetalChoiceOption>()
                    fun flatten(list: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>?, nested: Boolean) {
                        list?.forEach { c ->
                            val kids = c.items
                            when {
                                c.separator -> rows.add(com.petal.browser.ui.components.PetalChoiceOption(id = c.id, label = "", isSeparator = true))
                                kids != null -> {
                                    rows.add(com.petal.browser.ui.components.PetalChoiceOption(id = c.id, label = c.label ?: "", isHeader = true))
                                    flatten(kids, true)
                                }
                                else -> rows.add(
                                    com.petal.browser.ui.components.PetalChoiceOption(
                                        id = c.id,
                                        label = c.label ?: "",
                                        selected = c.selected,
                                        disabled = c.disabled,
                                        indented = nested
                                    )
                                )
                            }
                        }
                    }
                    flatten(prompt.choices, false)
                    val multiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showChoice(
                            act,
                            prompt.title,
                            rows,
                            multiple,
                            { ids ->
                                if (multiple) {
                                    result.complete(prompt.confirm(ids.toTypedArray()))
                                } else {
                                    val firstId = ids.firstOrNull()
                                    if (firstId != null) {
                                        result.complete(prompt.confirm(firstId))
                                    } else {
                                        result.complete(prompt.dismiss())
                                    }
                                }
                            },
                            { result.complete(prompt.dismiss()) }
                        )
                    }
                    return result
                }

                override fun onColorPrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.ColorPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showColor(
                            act,
                            prompt.title,
                            prompt.defaultValue,
                            { hex -> result.complete(prompt.confirm(hex)) },
                            { result.complete(prompt.dismiss()) }
                        )
                    }
                    return result
                }

                override fun onDateTimePrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.DateTimePrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val mode = when (prompt.type) {
                        GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> "month"
                        GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> "week"
                        GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> "time"
                        GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> "datetime-local"
                        else -> "date"
                    }
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showDateTime(
                            act,
                            prompt.title,
                            mode,
                            prompt.defaultValue,
                            prompt.minValue,
                            prompt.maxValue,
                            { value -> result.complete(prompt.confirm(value)) },
                            { result.complete(prompt.dismiss()) }
                        )
                    }
                    return result
                }

                override fun onSharePrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.SharePrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val act = activityProvider()
                        ?: return GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.ABORT))
                    val body = listOf(prompt.text, prompt.uri).filter { !it.isNullOrBlank() }.joinToString("\n")
                    if (body.isBlank()) {
                        return GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.ABORT))
                    }
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    act.runOnUiThread {
                        try {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, body)
                                if (!prompt.title.isNullOrBlank()) putExtra(android.content.Intent.EXTRA_SUBJECT, prompt.title)
                            }
                            act.startActivity(android.content.Intent.createChooser(send, null))
                            result.complete(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.SUCCESS))
                        } catch (_: Throwable) {
                            result.complete(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.FAILURE))
                        }
                    }
                    return result
                }

                override fun onPopupPrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.PopupPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val blockPopups = sp.getBoolean("sp_block_popups", sp.getBoolean("profileStandard_javascriptPopUp", true))
                    val target = prompt.targetUri
                    return if (blockPopups) {
                        if (!target.isNullOrBlank()) {
                            val act = (context as? com.petal.browser.activity.BrowserActivity)
                                ?: (context as? android.content.ContextWrapper)?.baseContext as? com.petal.browser.activity.BrowserActivity
                            act?.runOnUiThread {
                                val domain = try { android.net.Uri.parse(target).host ?: target } catch (_: Throwable) { target }
                                com.petal.browser.view.PetalToast.show(
                                    act,
                                    act.getString(com.petal.browser.R.string.ui_block_popup_windows) + ": $domain",
                                    com.petal.browser.view.PetalToast.LENGTH_LONG,
                                    act.getString(com.petal.browser.R.string.app_ok),
                                    { act.addAlbum(null, target, false, incognitoProvider()) }
                                )
                            }
                        }
                        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
                    } else {
                        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
                    }
                }

                override fun onBeforeUnloadPrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    // Firefox always shows a "Leave page?" dialog when a site has unsaved form data.
                    // Silently allowing causes users to lose form data (shopping carts, emails, etc.)
                    // without any warning — match Firefox's confirmed-ALLOW-only pattern.
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider() ?: run {
                        result.complete(prompt.confirm(AllowOrDeny.ALLOW))
                        return result
                    }
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showConfirm(
                            act,
                            act.getString(com.petal.browser.R.string.app_name),
                            act.getString(com.petal.browser.R.string.hint_before_unload),
                            { result.complete(prompt.confirm(AllowOrDeny.ALLOW)) },
                            { result.complete(prompt.confirm(AllowOrDeny.DENY)) }
                        )
                    }
                    return result
                }

                override fun onRepostConfirmPrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    // Firefox shows a confirmation dialog before re-sending POST data (e.g. on
                    // page refresh after a form submit). Silently allowing risks duplicate
                    // order/payment submissions — match Firefox's confirmed-ALLOW-only pattern.
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider() ?: run {
                        result.complete(prompt.confirm(AllowOrDeny.ALLOW))
                        return result
                    }
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showConfirm(
                            act,
                            act.getString(com.petal.browser.R.string.app_name),
                            act.getString(com.petal.browser.R.string.hint_repost_confirm),
                            { result.complete(prompt.confirm(AllowOrDeny.ALLOW)) },
                            { result.complete(prompt.confirm(AllowOrDeny.DENY)) }
                        )
                    }
                    return result
                }

                override fun onLoginSave(
                    session: GeckoSession,
                    request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val geckoResult = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider()
                    val option = request.options.firstOrNull()
                    if (option == null || act !is androidx.activity.ComponentActivity) {
                        return GeckoResult.fromValue(request.dismiss())
                    }

                    val login = option.value
                    val username = login.username.orEmpty()
                    val password = login.password.orEmpty()
                    val origin = login.origin.orEmpty()
                    val domain = origin.ifBlank { login.formActionOrigin.orEmpty() }

                    if (password.isBlank() || domain.isBlank()) {
                        return GeckoResult.fromValue(request.dismiss())
                    }

                    com.petal.browser.passwords.PetalCredentialVault.init(context)
                    if (com.petal.browser.passwords.PetalCredentialVault.isNeverSave(context, domain)) {
                        return GeckoResult.fromValue(request.dismiss())
                    }

                    val existing = com.petal.browser.passwords.PetalCredentialVault.findByDomainAndUsername(domain, username)

                    act.runOnUiThread {
                        when {
                            existing == null -> {
                                // New login: show Material 3 Expressive Save Password Sheet
                                com.petal.browser.passwords.PetalSavePasswordSheet.show(
                                    activity = act,
                                    domain = domain,
                                    initialUsername = username,
                                    initialPassword = password,
                                    onSave = { finalUser, finalPass ->
                                        val guid = login.guid ?: java.util.UUID.randomUUID().toString()
                                        com.petal.browser.passwords.PetalCredentialVault.save(
                                            com.petal.browser.passwords.PetalCredential(
                                                id = guid,
                                                domain = domain,
                                                originUrl = origin.ifBlank { "https://$domain" },
                                                username = finalUser,
                                                password = finalPass
                                            )
                                        )
                                        val editedEntry = org.mozilla.geckoview.Autocomplete.LoginEntry.Builder()
                                            .origin(login.origin)
                                            .formActionOrigin(login.formActionOrigin)
                                            .httpRealm(login.httpRealm)
                                            .username(finalUser)
                                            .password(finalPass)
                                            .guid(guid)
                                            .build()
                                        val editedOption = org.mozilla.geckoview.Autocomplete.LoginSaveOption(editedEntry)
                                        geckoResult.complete(request.confirm(editedOption))
                                    },
                                    onNeverSave = {
                                        com.petal.browser.passwords.PetalCredentialVault.addNeverSaveDomain(context, domain)
                                        geckoResult.complete(request.dismiss())
                                    },
                                    onDismiss = {
                                        geckoResult.complete(request.dismiss())
                                    }
                                )
                            }
                            existing.password != password -> {
                                // Password changed: show Update Password Sheet
                                com.petal.browser.passwords.PetalUpdatePasswordSheet.show(
                                    activity = act,
                                    domain = domain,
                                    username = username,
                                    newPassword = password,
                                    onUpdate = { newPass ->
                                        com.petal.browser.passwords.PetalCredentialVault.updatePassword(existing.id, newPass)
                                        val editedEntry = org.mozilla.geckoview.Autocomplete.LoginEntry.Builder()
                                            .origin(login.origin)
                                            .formActionOrigin(login.formActionOrigin)
                                            .httpRealm(login.httpRealm)
                                            .username(username)
                                            .password(newPass)
                                            .guid(existing.id)
                                            .build()
                                        val editedOption = org.mozilla.geckoview.Autocomplete.LoginSaveOption(editedEntry)
                                        geckoResult.complete(request.confirm(editedOption))
                                    },
                                    onDismiss = {
                                        geckoResult.complete(request.dismiss())
                                    }
                                )
                            }
                            else -> {
                                // Already up to date: mark last used
                                com.petal.browser.passwords.PetalCredentialVault.markUsed(existing.id)
                                geckoResult.complete(request.dismiss())
                            }
                        }
                    }
                    return geckoResult
                }

                override fun onLoginSelect(
                    session: GeckoSession,
                    request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val geckoResult = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider()
                    val options = request.options

                    if (options.isEmpty()) {
                        return GeckoResult.fromValue(request.dismiss())
                    }

                    if (options.size == 1 || act !is androidx.activity.ComponentActivity) {
                        // Single match: auto-fill directly
                        val option = options.first()
                        val login = option.value
                        login.guid?.let { com.petal.browser.passwords.PetalCredentialVault.markUsed(it) }
                        return GeckoResult.fromValue(request.confirm(option))
                    }

                    // Multi-account selection: show Account Picker Sheet
                    val credentials = options.map { opt ->
                        val l = opt.value
                        com.petal.browser.passwords.PetalCredential(
                            id = l.guid ?: java.util.UUID.randomUUID().toString(),
                            domain = l.origin.orEmpty(),
                            originUrl = l.origin.orEmpty(),
                            username = l.username.orEmpty(),
                            password = l.password.orEmpty()
                        )
                    }

                    act.runOnUiThread {
                        com.petal.browser.passwords.PetalAccountPickerSheet.show(
                            activity = act,
                            domain = options.first().value.origin.orEmpty(),
                            accounts = credentials,
                            onAccountSelected = { selected ->
                                val matchOpt = options.firstOrNull { it.value.guid == selected.id || it.value.username == selected.username }
                                    ?: options.first()
                                selected.id.let { com.petal.browser.passwords.PetalCredentialVault.markUsed(it) }
                                geckoResult.complete(request.confirm(matchOpt))
                            },
                            onSuggestPassword = {
                                val generated = com.petal.browser.passwords.PetalPasswordGenerator.generate()
                                val clipboard = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Password", generated))
                                com.petal.browser.view.PetalToast.show(act, "Strong password copied to clipboard")
                                geckoResult.complete(request.dismiss())
                            },
                            onManagePasswords = {
                                if (act is com.petal.browser.activity.BrowserActivity) {
                                    act.openSettingsScreen()
                                }
                                geckoResult.complete(request.dismiss())
                            },
                            onDismiss = {
                                geckoResult.complete(request.dismiss())
                            }
                        )
                    }
                    return geckoResult
                }

                override fun onFilePrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.FilePrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val geckoResult = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val act = activityProvider()
                    if (act !is androidx.activity.ComponentActivity) {
                        return GeckoResult.fromValue(prompt.dismiss())
                    }
                    act.runOnUiThread {
                        val isMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                        val mimeTypes = prompt.mimeTypes?.filter { !it.isNullOrBlank() }?.toTypedArray() ?: emptyArray()

                        val valueCallback = object : android.webkit.ValueCallback<Array<android.net.Uri>> {
                            override fun onReceiveValue(value: Array<android.net.Uri>?) {
                                if (value == null || value.isEmpty()) {
                                    if (!prompt.isComplete) {
                                        geckoResult.complete(prompt.dismiss())
                                    }
                                } else if (isMultiple) {
                                    if (!prompt.isComplete) {
                                        geckoResult.complete(prompt.confirm(act, value))
                                    }
                                } else {
                                    if (!prompt.isComplete) {
                                        geckoResult.complete(prompt.confirm(act, value[0]))
                                    }
                                }
                            }
                        }

                        val fileChooserParams = object : android.webkit.WebChromeClient.FileChooserParams() {
                            override fun getMode(): Int = if (isMultiple) MODE_OPEN_MULTIPLE else MODE_OPEN
                            override fun getAcceptTypes(): Array<String> = mimeTypes
                            override fun isCaptureEnabled(): Boolean = prompt.capture != GeckoSession.PromptDelegate.FilePrompt.Capture.NONE
                            override fun getTitle(): CharSequence? = null
                            override fun getFilenameHint(): String? = null
                            override fun createIntent(): android.content.Intent {
                                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    if (isMultiple) putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    type = if (mimeTypes.isNotEmpty() && mimeTypes[0].isNotBlank()) mimeTypes[0] else "*/*"
                                    if (mimeTypes.size > 1) {
                                        putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes)
                                    }
                                }
                                return intent
                            }
                        }

                        if (act is com.petal.browser.activity.BrowserActivity) {
                            act.showFileChooser(valueCallback, fileChooserParams)
                        } else {
                            com.petal.browser.compose.file.PetalUploadChooser.show(
                                act,
                                valueCallback,
                                fileChooserParams
                            ) {
                                if (!prompt.isComplete) {
                                    geckoResult.complete(prompt.dismiss())
                                }
                            }
                        }
                    }
                    return geckoResult
                }

                // ─────────────────────────────────────────────────────────────────
                // Autofill Gaps: Credit Card, Address & FedCM (Identity-Credential)
                // Note: Credit card autofill, physical address autofill, and FedCM
                // (Federated Credential Management) prompts are currently not supported in Petal Browser.
                // Dismissing them immediately prevents UI hangs and unhandled Gecko promise rejections.
                //
                // TODO: Future Implementation Plan:
                // 1. Credit Card Storage & Selection:
                //    - Store cards encrypted in PetalCredentialVault (Tink AES-256-GCM / Android Keystore).
                //    - Material 3 Expressive bottom sheets for card selection and save confirmation.
                // 2. Address Autofill:
                //    - Encrypted address storage for billing/shipping fields.
                //    - Address picker bottom sheet conforming to Material 3 Design.
                // 3. FedCM (Identity Credential):
                //    - Material 3 Expressive provider and account chooser sheets.
                //    - Privacy policy sheet and consent flow.
                // ─────────────────────────────────────────────────────────────────

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
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    val providers = prompt.providers
                    if (providers.isNullOrEmpty()) {
                        return GeckoResult.fromValue(prompt.dismiss())
                    }
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val options = providers.mapIndexed { idx, provider ->
                        val label = provider.name.ifBlank { provider.domain }
                        com.petal.browser.ui.components.PetalChoiceOption(id = idx.toString(), label = label)
                    }
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showChoice(
                            act,
                            prompt.title ?: "Choose an Identity Provider",
                            options,
                            false,
                            { selectedIds ->
                                val selectedIdx = selectedIds.firstOrNull()?.toIntOrNull() ?: 0
                                result.complete(prompt.confirm(selectedIdx))
                            },
                            {
                                result.complete(prompt.dismiss())
                            }
                        )
                    }
                    return result
                }

                override fun onSelectIdentityCredentialAccount(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.IdentityCredential.AccountSelectorPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    val accounts = prompt.accounts
                    if (accounts.isNullOrEmpty()) {
                        return GeckoResult.fromValue(prompt.dismiss())
                    }
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    val options = accounts.mapIndexed { idx, acc ->
                        val label = when {
                            !acc.name.isNullOrBlank() && !acc.email.isNullOrBlank() -> "${acc.name} (${acc.email})"
                            !acc.name.isNullOrBlank() -> acc.name
                            !acc.email.isNullOrBlank() -> acc.email
                            else -> "Account ${idx + 1}"
                        }
                        com.petal.browser.ui.components.PetalChoiceOption(id = idx.toString(), label = label)
                    }
                    act.runOnUiThread {
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showChoice(
                            act,
                            prompt.title ?: "Select Account to Continue",
                            options,
                            false,
                            { selectedIds ->
                                val selectedIdx = selectedIds.firstOrNull()?.toIntOrNull() ?: 0
                                result.complete(prompt.confirm(selectedIdx))
                            },
                            {
                                result.complete(prompt.dismiss())
                            }
                        )
                    }
                    return result
                }

                override fun onShowPrivacyPolicyIdentityCredential(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.IdentityCredential.PrivacyPolicyPrompt
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                    val act = activityProvider() ?: return GeckoResult.fromValue(prompt.dismiss())
                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                    act.runOnUiThread {
                        val policyUrl = prompt.privacyPolicyUrl
                        val provider = prompt.providerDomain
                        val message = when {
                            policyUrl.isNotBlank() && provider.isNotBlank() ->
                                "Do you agree to the privacy policy for $provider?\n\n$policyUrl"
                            policyUrl.isNotBlank() ->
                                "Do you agree to the privacy policy?\n\n$policyUrl"
                            provider.isNotBlank() ->
                                "Do you agree to the privacy policy for $provider?"
                            else ->
                                "Do you agree to the privacy policy?"
                        }
                        com.petal.browser.ui.components.PetalExpressivePromptBridge.showConfirm(
                            act,
                            prompt.title ?: "Privacy Policy",
                            message,
                            { result.complete(prompt.confirm(true)) },
                            { result.complete(prompt.confirm(false)) }
                        )
                    }
                    return result
                }
            }
        }
    }

    interface OnScrollChangeListener {
        fun onScrollDown()
        fun onScrollUp()
        fun onScrollPositionChanged(scrollY: Int, contentHeight: Int) {}
    }

    val geckoView: GeckoView = SafeGeckoView(context).apply {
        com.petal.browser.ui.glass.GeckoGlassBackend.applyBackend(this, context)
    }
    // The session mode is immutable after GeckoSession construction. Creating a normal
    // session and switching to private mode later is too late and can leak normal-profile
    // state into an Incognito tab, especially during cold startup.
    // If an engineSession (GeckoEngineSession) is provided, adopt its underlying GeckoSession.
    var session: GeckoSession = adoptedSession
        ?: extractGeckoSession(engineSession)
        ?: GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(initialIncognito)
                .allowJavascript(
                    if (com.petal.browser.privacy.PetalPrivacyHardening.getSecurityLevel(PreferenceManager.getDefaultSharedPreferences(context)) == com.petal.browser.privacy.PetalPrivacyHardening.SecurityLevel.SAFEST) false
                    else PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_javascript", true)
                )
                .build()
        )

    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var isIncognito: Boolean = initialIncognito
    private var isForegroundTab: Boolean = false
    private var isStopped: Boolean = false
    @Volatile
    var isReaderEligible: Boolean = false
    private var isMediaPlaying: Boolean = false
    private var previewRevision: Long = 0L
    @Volatile
    private var capturedPreviewRevision: Long = -1L
    private var previewCaptureSequence: Long = 0L
    // Mirrors Firefox's BrowserThumbnails gate: only thumbnail once the compositor has painted
    // real content for the current document. Reset again when GeckoView drops its paint state.
    private var hasContentfulPaint: Boolean = false
    private var hasDocumentPainted: Boolean = false
    private var previewRetryCount: Int = 0

    private val surfaceCoverView: View = View(context).apply {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(getThemeBackgroundColor())
        visibility = View.VISIBLE
    }

    private fun getThemeBackgroundColor(): Int {
        val isAmoled = sp.getBoolean("sp_amoled", false)
        val configStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
        val isDark = when (configStr.uppercase()) {
            "LIGHT" -> false
            "DARK" -> true
            else -> {
                val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        return when {
            isDark && isAmoled -> android.graphics.Color.BLACK
            isDark -> android.graphics.Color.parseColor("#121212")
            else -> android.graphics.Color.WHITE
        }
    }

    private fun showSurfaceCover() {
        if (!hasDocumentPainted) {
            surfaceCoverView.setBackgroundColor(getThemeBackgroundColor())
            surfaceCoverView.alpha = 1f
            surfaceCoverView.visibility = View.VISIBLE
        }
    }

    private fun dismissSurfaceCover() {
        if (surfaceCoverView.visibility == View.VISIBLE) {
            post {
                surfaceCoverView.animate()
                    .alpha(0f)
                    .setDuration(120L)
                    .withEndAction {
                        surfaceCoverView.visibility = View.GONE
                        surfaceCoverView.alpha = 1f
                    }
                    .start()
            }
        }
    }

    private fun isInternalBootstrapUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.startsWith("moz-extension://") && (url.contains("bootstrap.html") || url.contains("token="))) return true
        return false
    }

    private var tabId: String = "tab_${System.currentTimeMillis()}_${Math.abs(hashCode())}"
    private var tabGroupId: String? = null
    private var tabGroupTitle: String? = null
    private var predecessor: AlbumController? = null

    enum class SessionState {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        DESTROYED
    }

    private var sessionState: SessionState = SessionState.UNINITIALIZED
    private var pendingLoadUrl: String? = null

    private var currentUrl: String = "about:blank"
    var isAdoptedPopup: Boolean = false
    /**
     * The last real (non-blank, non-home) URL that was explicitly requested via [loadUrl].
     * Unlike [currentUrl], this is never reset to "about:blank" — it preserves the original
     * intent even while the engine is still loading.
     */
    @JvmField var persistentUrl: String = ""
    private var currentTitle: String = "Petal Start"
    private var currentProgress: Int = 0
    private var canGoBackVal: Boolean = false
    private var canGoForwardVal: Boolean = false
    private var currentSelection: GeckoSession.SelectionActionDelegate.Selection? = null
    private var lastRecordedHistoryUrl: String? = null

    private var favicon: Bitmap? = null
    var currentSecurityInfo: GeckoSession.ProgressDelegate.SecurityInformation? = null
        private set

    private var mediaBridge: PetalMediaBridge? = null
    private var pwaManager: PetalPwaManager? = null
    private var onScrollChangeListener: OnScrollChangeListener? = null
    var onPageProgressChanged: ((Int) -> Unit)? = null
    var onPageTitleChanged: ((String) -> Unit)? = null
    var onPageSecurityChanged: ((Boolean) -> Unit)? = null
    private var lastScrollHapticY: Int = 0
    private var currentScrollY: Int = 0
    private var currentScrollX: Int = 0
    private var lastCrashRecoveryTime: Long = 0L
    private var crashRecoveryCount: Int = 0
    private var sessionInitializationStarted: Boolean = false
    @Volatile
    private var latestGeckoState: GeckoSession.SessionState? = null

    fun getSessionState(): GeckoSession.SessionState? = latestGeckoState

    fun captureEngineState(): String? {
        if (isIncognito) return null
        val state = latestGeckoState ?: return null
        return try {
            state.toString().takeIf { it.length > 2 }
        } catch (_: Throwable) {
            null
        }
    }

    init {
        addView(
            geckoView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addView(
            surfaceCoverView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        com.petal.browser.media.sniffer.PetalMediaSniffer.setActivePage(tabId, currentUrl)
        com.petal.browser.media.sniffer.PetalNativeSnifferEngine.setActiveTab(tabId, currentUrl)
        if (PetalGeckoRuntime.isGeckoAvailable(context)) {
            initGeckoSession()
        }
        this.pwaManager = PetalPwaManager(context, this, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (com.petal.browser.privacy.PetalPrivacyHardening.isLetterboxingEnabled(sp)) {
            val totalW = measuredWidth
            val totalH = measuredHeight
            if (totalW > 0 && totalH > 0) {
                val density = resources.displayMetrics.density
                val stepW = (200 * density).toInt().coerceAtLeast(1)
                val stepH = (100 * density).toInt().coerceAtLeast(1)

                val targetW = ((totalW / stepW) * stepW).coerceIn((240 * density).toInt(), totalW)
                val targetH = ((totalH / stepH) * stepH).coerceIn((320 * density).toInt(), totalH)

                val lp = geckoView.layoutParams as? LayoutParams
                if (lp != null && (lp.width != targetW || lp.height != targetH)) {
                    lp.width = targetW
                    lp.height = targetH
                    lp.gravity = android.view.Gravity.CENTER
                    geckoView.layoutParams = lp
                }
            }
        } else {
            val lp = geckoView.layoutParams as? LayoutParams
            if (lp != null && (lp.width != ViewGroup.LayoutParams.MATCH_PARENT || lp.height != ViewGroup.LayoutParams.MATCH_PARENT)) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                lp.gravity = android.view.Gravity.NO_GRAVITY
                geckoView.layoutParams = lp
            }
        }
    }

    @MainThread
    private fun initGeckoSession() {
        if (sessionInitializationStarted) {
            // A session may be re-initialized after popup adoption or process recovery.
            // Never start a second open/attachment sequence for the same GeckoSession.
            return
        }
        sessionInitializationStarted = true
        sessionState = SessionState.INITIALIZING

        val runtime = PetalGeckoRuntime.getOrCreate(context.applicationContext)
        // GeckoSession.open() must only be invoked for a newly-created, unopened session.
        // Keep the open/attach operation serialized on the main thread and make the
        // session's private-mode choice before open() (see constructor above).
        if (!session.isOpen) {
            session.open(runtime)
        }
        geckoView.setSession(session)
        session.setActive(isForegroundTab)
        applySettings()
        com.petal.browser.extensions.PetalExtensionManager.attachSession(session)
        com.petal.browser.extensions.PetalBuiltInExtensionManager.attachSession(session)
        // Do not mutate GeckoView's compositor child hierarchy during session attachment.
        // Edge-gesture handling is performed lazily from dispatchTouchEvent().

        // Progress & Loading Delegate
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (isInternalBootstrapUrl(url)) return
                if (url.equals("about:blank", ignoreCase = true) && persistentUrl.isNotBlank() && !PetalBrowserUtils.isHomePage(persistentUrl)) {
                    return
                }
                previewRevision++
                hasContentfulPaint = false
                hasDocumentPainted = false
                showSurfaceCover()
                previewRetryCount = 0
                isStopped = false
                isReaderEligible = false
                currentScrollY = 0
                currentScrollX = 0
                com.petal.browser.media.sniffer.PetalMediaSniffer.clear()
                currentUrl = url
                applySettings()

                com.petal.browser.media.sniffer.PetalMediaSniffer.setActivePage(tabId, url)
                com.petal.browser.media.sniffer.PetalNativeSnifferEngine.setActiveTab(tabId, url)
                updateProgress(10)

                // Waterfox-grade Early Cosmetic & Scriptlet Injection (document_start)
                if (!isInternalBootstrapUrl(url) && !url.startsWith("about:") && !url.startsWith("chrome:")) {
                    val adBlock = sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true))
                    if (adBlock) {
                        try {
                            val uboPayload = com.petal.browser.browser.PetalAdBlockEngine.getuBlockCosmeticAndScriptletPayload(url)
                            evaluateJavascript(uboPayload)
                        } catch (_: Throwable) {}
                    }
                    // Inject document-start UserScripts
                    try {
                        com.petal.browser.compose.store.PetalStoreScriptInjector.inject(session, context, url, "document-start")
                    } catch (_: Throwable) {}

                    // Inject video autoplay blocker if flag enabled
                    try {
                        if (com.petal.browser.flags.PetalFlagsRegistry.isEnabled(context, "enable-autoplay-blocker")) {
                            evaluateJavascript(com.petal.browser.engine.petal.VideoAutoplayBlockerScript.installScript)
                        }
                    } catch (_: Throwable) {}
                }

                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateLoadingState(context, tabId, true)
                    com.petal.browser.engine.gecko.PetalEngineStore.updateProgress(context, tabId, 10)
                }

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
                hideLoadingSkeleton()
                dismissSurfaceCover()
                isStopped = true
                updateProgress(PetalBrowserUtils.LOADING_STOPPED)
                if (hasContentfulPaint) schedulePreviewCapture(150L)
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateLoadingState(context, tabId, false)
                    com.petal.browser.engine.gecko.PetalEngineStore.updateProgress(context, tabId, 100)
                }

                // Native resource DOM scan (extract video, audio, image, and download links)
                if (success && !currentUrl.isNullOrBlank() && !com.petal.browser.media.sniffer.PetalNativeSnifferEngine.isSearchOrInternalUrl(currentUrl)) {
                    getMediaBridge()?.injectMediaHooks()
                    evaluateJavascript(com.petal.browser.media.sniffer.PetalNativeSnifferEngine.DOM_SCAN_JS) { result ->
                        com.petal.browser.media.sniffer.PetalNativeSnifferEngine.parseDomScanResult(tabId, currentUrl, result)
                    }

                    // Forced vertical scroll override if enabled
                    try {
                        val host = try { android.net.Uri.parse(currentUrl ?: "").host ?: "" } catch (_: Throwable) { "" }
                        if (host.isNotBlank() && sp.getBoolean("sp_force_vertical_scroll", false)) {
                            val scrollJs = com.petal.browser.engine.petal.blocking.ForcedVerticalScrollScript.create(listOf(host))
                            if (scrollJs.isNotBlank()) evaluateJavascript(scrollJs)
                        }
                    } catch (_: Throwable) {}

                    // Firefox-style Article Detection for dynamic Reader View indicator
                    val isCachedEligible = com.petal.browser.compose.reader.PetalReaderBridge.isUrlEligible(currentUrl)
                    if (isCachedEligible) {
                        this@PetalGeckoView.isReaderEligible = true
                        val hostAct = getHostActivity()
                        if (hostAct is com.petal.browser.activity.BrowserActivity) {
                            hostAct.runOnUiThread {
                                hostAct.setReaderModeEligible(this@PetalGeckoView, true)
                            }
                        }
                    }
                }

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.resetRefreshState()
                    act.runOnUiThread {
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                        act.updateBackCallbackState()
                    }
                }

                pwaManager?.detectPwaManifest()
                injectClientPrivacyProtections()
                if (success) {
                    com.petal.browser.compose.store.PetalStoreScriptInjector.inject(session, context, currentUrl, "document-idle")
                    injectTorraysAutoSearch(currentUrl)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                currentProgress = progress
                updateProgress(progress)
                onPageProgressChanged?.invoke(progress)
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateProgress(context, tabId, progress)
                }
                if (progress >= 30) {
                    hideLoadingSkeleton()
                }
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                currentSecurityInfo = securityInfo
                onPageSecurityChanged?.invoke(securityInfo.isSecure)
            }

            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                latestGeckoState = sessionState
            }
        }

        // Universal Live Liquid Glass & Material 3 Expressive Permission Delegate
        session.permissionDelegate = com.petal.browser.ui.components.PetalGeckoPermissionDelegate(context) {
            getHostActivity()
        }

        // Navigation Delegate
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackVal = canGoBack
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateNavigationState(context, tabId, canGoBackVal, canGoForwardVal)
                }
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread { act.updateBackCallbackState() }
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardVal = canGoForward
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateNavigationState(context, tabId, canGoBackVal, canGoForwardVal)
                }
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
                post { dismissCrashErrorState() }
                try {
                    val oldOrigin = currentUrl?.let { u ->
                        val p = android.net.Uri.parse(u)
                        val s = p.scheme ?: "https"
                        val a = p.authority
                        if (a != null) "$s://$a" else null
                    }
                    val newOrigin = url?.let { u ->
                        val p = android.net.Uri.parse(u)
                        val s = p.scheme ?: "https"
                        val a = p.authority
                        if (a != null) "$s://$a" else null
                    }
                    if (oldOrigin != null && oldOrigin != newOrigin) {
                        com.petal.browser.ui.components.PetalActiveMediaSensors.clearOrigin(oldOrigin)
                    }
                } catch (_: Throwable) {}

                if (url.isNullOrBlank() || url.equals(currentUrl, ignoreCase = true)) return
                if (isInternalBootstrapUrl(url)) return
                if (url.equals("about:blank", ignoreCase = true) && persistentUrl.isNotBlank() && !PetalBrowserUtils.isHomePage(persistentUrl)) {
                    return
                }
                // Brave Bounce Tracking Defense: skip intermediary tracking redirects directly to destination
                val unbouncedUrl = com.petal.browser.privacy.PetalBounceTrackingProtection.detectAndUnwrapBounceUrl(url)
                if (unbouncedUrl != null && !unbouncedUrl.equals(url, ignoreCase = true)) {
                    Log.i(TAG, "Bounce Tracking detected: bypassing $url -> $unbouncedUrl")
                    loadUrl(unbouncedUrl)
                    return
                }

                // Brave De-AMP: Automatically unwrap AMP URLs to publisher canonical URLs
                val deAmpUrl = com.petal.browser.privacy.PetalDeAmpEngine.unwrapAmpUrl(url)
                if (deAmpUrl != null && !deAmpUrl.equals(url, ignoreCase = true)) {
                    Log.i(TAG, "De-AMP triggered: redirecting $url -> $deAmpUrl")
                    loadUrl(deAmpUrl)
                    return
                }

                previewRevision++
                currentUrl = url
                // Update persistentUrl for navigations (redirects, SPA pushState) so the
                // save-on-pause always has the final visible URL, not the initial request URL.
                if (!url.equals("about:blank", ignoreCase = true) &&
                    !url.startsWith("about:") &&
                    !url.startsWith("moz-extension://")) {
                    persistentUrl = url
                }
                com.petal.browser.media.sniffer.PetalMediaSniffer.setActivePage(tabId, url)
                com.petal.browser.media.sniffer.PetalNativeSnifferEngine.setActiveTab(tabId, url)
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateUrlAndTitle(context, tabId, url, currentTitle)
                }

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        // Handle Firefox / Mozilla OAuth redirect callback completion
                        val fxManager = com.petal.browser.account.mozilla.FxAccountManager.getInstance()
                        if (fxManager.isRedirectUrl(url)) {
                            val authCode = fxManager.extractQueryParam(url, "code")
                            val state = fxManager.extractQueryParam(url, "state")
                            if (!authCode.isNullOrBlank() && fxManager.consumePendingAuth(state)) {
                                val emailParam = fxManager.extractQueryParam(url, "email") ?: ""
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    fxManager.exchangeCodeForTokens(authCode, emailParam)
                                    com.petal.browser.account.mozilla.PetalMozillaSyncManager.getInstance().syncNow(act)
                                    com.petal.browser.view.PetalToast.show(act, "Signed in with Firefox Account. Syncing data...")
                                    act.removeAlbum(this@PetalGeckoView)
                                }
                                return@runOnUiThread
                            }
                        }

                        // Location changes include login/OAuth redirects. Keep this callback
                        // UI-only; Gecko owns its compositor child lifecycle.
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                        act.updateBackCallbackState()

                        // Inject Brave Custom Cosmetic Element Hide Rules
                        val currentDomain = try { android.net.Uri.parse(url).host ?: "" } catch (_: Throwable) { "" }
                        if (currentDomain.isNotBlank()) {
                            val hideCss = com.petal.browser.privacy.PetalElementBlocker.getInjectionCss(act, currentDomain)
                            if (hideCss.isNotBlank()) {
                                val escaped = hideCss.replace("'", "\\'").replace("\n", " ")
                                evaluateJavascript("(function(){let s=document.getElementById('petal-custom-blocker');if(!s){s=document.createElement('style');s.id='petal-custom-blocker';document.head.appendChild(s);}s.textContent='$escaped';})();")
                            }
                        }

                        // Detect Google anti-bot CAPTCHA redirect page (google.com/sorry)
                        if (url.contains("google.") && url.contains("/sorry")) {
                            Log.w(TAG, "Encountered Google sorry CAPTCHA page: $url")
                        }
                    }
                }
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                // Direct native sniffer interception: classify media and document streams
                if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
                    com.petal.browser.media.sniffer.PetalNativeSnifferEngine.registerResource(
                        tabId = tabId,
                        url = uri,
                        pageUrl = currentUrl
                    )
                }

                if (PetalBrowserUtils.isHomePage(uri)) {
                    if (uri.equals("about:blank", ignoreCase = true)) {
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // Interactive Element Zapper handler (petal://zap-element?domain=...&selector=...)
                if (uri.startsWith("petal://zap-element", ignoreCase = true)) {
                    try {
                        val parsedUri = android.net.Uri.parse(uri)
                        val domain = parsedUri.getQueryParameter("domain") ?: ""
                        val selector = parsedUri.getQueryParameter("selector") ?: ""
                        if (domain.isNotBlank() && selector.isNotBlank()) {
                            com.petal.browser.privacy.PetalElementBlocker.addSelector(context, domain, selector)
                            post {
                                android.widget.Toast.makeText(context, "Element zapped & hidden on $domain", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Throwable) {}
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // Universal Adblock Filter List Subscription Link handler (abp:subscribe?location=...&title=...)
                if (uri.startsWith("abp:subscribe", ignoreCase = true)) {
                    try {
                        val parsedUri = android.net.Uri.parse(uri)
                        val location = parsedUri.getQueryParameter("location")
                        val title = parsedUri.getQueryParameter("title") ?: "Custom Filter List"
                        if (!location.isNullOrBlank()) {
                            val result = com.petal.browser.browser.PetalFilterSubscriptionManager.addCustomSubscription(context, title, location)
                            post {
                                if (result.isSuccess) {
                                    com.petal.browser.view.PetalToast.show(context, "Subscribed to filter list: $title")
                                } else {
                                    com.petal.browser.view.PetalToast.show(context, "Subscription failed: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        post {
                            com.petal.browser.view.PetalToast.show(context, "Failed to subscribe to filter list: ${e.message}")
                        }
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // Firefox Account OAuth Redirect Interception
                val fxaManager = com.petal.browser.account.mozilla.FxAccountManager.getInstance()
                if (fxaManager.isRedirectUrl(uri)) {
                    val code = fxaManager.extractQueryParam(uri, "code")
                    val state = fxaManager.extractQueryParam(uri, "state")
                    if (!code.isNullOrBlank() && fxaManager.consumePendingAuth(state)) {
                        val email = fxaManager.extractQueryParam(uri, "email") ?: ""
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val ok = fxaManager.exchangeCodeForTokens(code, email)
                            if (ok) {
                                com.petal.browser.account.mozilla.PetalMozillaSyncManager.getInstance().syncNow(context)
                                post {
                                    android.widget.Toast.makeText(context, "Firefox Account connected & sync active!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    // If no valid pending OAuth flow or missing code, do not intercept or fake auth; allow navigation
                }

                // Handle Android deep links and external app URI schemes (intent://, market://, tel:, etc.)
                if (isExternalScheme(uri)) {
                    if (!request.hasUserGesture) {
                        android.util.Log.d(TAG, "Blocked external app link without user gesture: $uri")
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    if (handleDeepLinkOrCustomScheme(uri)) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
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
                val parentSettings = this@PetalGeckoView.session.settings
                val secLevel = com.petal.browser.privacy.PetalPrivacyHardening.getSecurityLevel(sp)
                val allowJs = if (secLevel == com.petal.browser.privacy.PetalPrivacyHardening.SecurityLevel.SAFEST) {
                    false
                } else {
                    val prof = getProfile(context)
                    sp.getBoolean("sp_javascript", sp.getBoolean("${prof}_javascript", sp.getBoolean("profileStandard_javascript", true)))
                }
                val popupSession = GeckoSession(GeckoSessionSettings.Builder(parentSettings)
                    .usePrivateMode(this@PetalGeckoView.isIncognito)
                    .allowJavascript(allowJs)
                    .userAgentMode(parentSettings.userAgentMode)
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
                            act.adoptPopupGeckoSession(popupSession, uri, isIncognito)
                        } catch (t: Throwable) {
                            android.util.Log.e(TAG, "Failed to create login popup tab", t)
                            if (popupSession.isOpen) popupSession.close()
                        }
                    }
                }
                act.runOnUiThread(adoptWhenOpened)
                return result
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String>? {
                return this@PetalGeckoView.onLoadError(session, uri, error)
            }

            override fun onSubframeLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                return this@PetalGeckoView.onSubframeLoadRequest(session, request)
            }
        }

        // Let Gecko own visit detection and history state changes (matches official Firefox)
        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(
                session: GeckoSession,
                historyList: GeckoSession.HistoryDelegate.HistoryList
            ) {
                val currentIndex = historyList.currentIndex
                val size = historyList.size
                canGoBackVal = currentIndex > 0
                canGoForwardVal = currentIndex < size - 1
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateNavigationState(context, tabId, canGoBackVal, canGoForwardVal)
                }
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        act.updateBackCallbackState()
                        act.updateOmniBox()
                    }
                }
            }

            override fun onVisited(
                session: GeckoSession,
                url: String,
                lastVisitedURL: String?,
                flags: Int
            ): GeckoResult<Boolean> {
                // Official Firefox logic: filter out non-top-level visits, error pages, and redirect sources
                val isTopLevel = (flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) != 0
                val isError = (flags and GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR) != 0
                val isRedirect = (flags and GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE) != 0 ||
                                 (flags and GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT) != 0

                if (isTopLevel && !isError && !isRedirect) {
                    recordHistoryVisit(url, currentTitle)
                }
                return GeckoResult.fromValue(true)
            }

            override fun getVisited(
                session: GeckoSession,
                urls: Array<out String>
            ): GeckoResult<BooleanArray>? {
                val result = GeckoResult<BooleanArray>()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val action = HistoryBookmarkRepository(context)
                        action.open(false)
                        val visited = BooleanArray(urls.size) { i ->
                            val u = urls[i]
                            if (u.isBlank() || u.startsWith("about:") || u.startsWith("moz-extension://")) false
                            else action.checkUrl(u, HistoryBookmarkContract.TABLE_HISTORY)
                        }
                        action.close()
                        result.complete(visited)
                    } catch (_: Throwable) {
                        result.complete(BooleanArray(urls.size) { false })
                    }
                }
                return result
            }
        }

        // Content Delegate
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFirstContentfulPaint(session: GeckoSession) {
                hasContentfulPaint = true
                hasDocumentPainted = true
                dismissSurfaceCover()
                previewRetryCount = 0
                // Firefox captures when "not loading && first contentful paint". The page may
                // already have stopped loading by the time the first paint lands, so capture now,
                // after a short delay that lets the compositor present the painted frame.
                if (isStopped && isForegroundTab) schedulePreviewCapture(150L)
            }

            override fun onPaintStatusReset(session: GeckoSession) {
                // The session was paused/backgrounded and compositor resources were released.
                // Document paint history is retained so returning to the tab preserves presentation.
                hasContentfulPaint = false
            }

            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let {
                    currentTitle = it
                    onPageTitleChanged?.invoke(it)
                    if (engineSession != null) {
                        com.petal.browser.engine.gecko.PetalEngineStore.updateUrlAndTitle(context, tabId, currentUrl, it)
                    }
                    val act = getHostActivity()
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        act.runOnUiThread {
                            act.updateOmniBox()
                        }
                    }
                    if (it.isNotBlank() && it != "Petal Start" && it != "Petal Home" && currentUrl.isNotBlank()) {
                        recordHistoryVisit(currentUrl, it, force = true)
                    }
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        act.setCustomFullscreen(fullScreen)
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
                val act = getHostActivity() ?: return
                handleExternalResponse(act, response, this@PetalGeckoView.isIncognito, this@PetalGeckoView.currentUrl)
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

                val baseUri = runCatching { element.baseUri }.getOrNull()
                fun resolveUri(uri: String?): String? {
                    if (uri.isNullOrBlank()) return null
                    if (baseUri.isNullOrBlank() || uri.contains("://") || uri.startsWith("data:") || uri.startsWith("blob:") || uri.startsWith("javascript:")) {
                        return uri
                    }
                    return runCatching { java.net.URI(baseUri).resolve(uri).toString() }.getOrDefault(uri)
                }

                val linkUri = resolveUri(element.linkUri)
                val srcUri = resolveUri(element.srcUri)
                val elemType = element.type
                val linkText = runCatching { element.linkText }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: runCatching { element.title }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: runCatching { element.javaClass.getField("alt").get(element) as? String }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: runCatching { element.javaClass.getField("textContent").get(element) as? String }.getOrNull()
                val elementTitle = runCatching { element.title }.getOrNull()?.takeIf { it.isNotBlank() }
                val elementAlt = runCatching { element.javaClass.getField("alt").get(element) as? String }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: runCatching { element.javaClass.getField("altText").get(element) as? String }.getOrNull()?.takeIf { it.isNotBlank() }

                act.runOnUiThread {
                    com.petal.browser.compose.menu.BrowserContextMenuManager.handleContextMenu(
                        activity = act,
                        elemType = elemType,
                        linkUri = linkUri,
                        srcUri = srcUri,
                        linkText = linkText,
                        title = elementTitle,
                        alt = elementAlt,
                        pageUrl = this@PetalGeckoView.currentUrl,
                        pageTitle = this@PetalGeckoView.title
                    )
                }
            }

            override fun onSlowScript(session: GeckoSession, scriptFileName: String): GeckoResult<SlowScriptResponse>? {
                val act = getHostActivity() ?: return GeckoResult.fromValue(SlowScriptResponse.CONTINUE)
                val result = GeckoResult<SlowScriptResponse>()
                act.runOnUiThread {
                    com.petal.browser.ui.components.PetalExpressivePromptBridge.showSlowScriptDialog(
                        act,
                        { result.complete(SlowScriptResponse.STOP) },
                        { result.complete(SlowScriptResponse.CONTINUE) }
                    )
                }
                return result
            }
        }

        // Official Mozilla Firefox ContentBlocking Delegate (Enhanced Tracking Protection & AdBlock telemetry)
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                val atCat = try { event.antiTrackingCategory } catch (_: Throwable) { 0 }
                val cookieCat = try { event.cookieBehaviorCategory } catch (_: Throwable) { 0 }
                com.petal.browser.browser.PetalAdBlockEngine.recordBlockWithCategories(currentUrl, atCat, cookieCat)
            }
        }

        // Material 3 Expressive Prompt Delegate (Alerts, Confirms, Prompts, Auth, Choice, Text, Popups, File Chooser)
        session.promptDelegate = createPromptDelegate(session, context, sp, { isIncognito }) { getHostActivity() }


        // Scroll Delegate for Tactile Haptics and Address Bar Collapsing
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            private var lastAddressBarScrollY: Int = 0
            // Scroll gathered in the current direction. Hiding needs a longer push than showing,
            // so a small flick back always brings the bars back, but reading never hides them.
            private var scrollRun: Int = 0
            private var isFrameScheduled: Boolean = false
            private var pendingScrollY: Int = 0

            // One callback object for the whole session: no allocation per scroll event.
            private val frameCallback = android.view.Choreographer.FrameCallback {
                        isFrameScheduled = false
                        val latestY = pendingScrollY
                        val act = getHostActivity() ?: return@FrameCallback
                        val bAct = act as? com.petal.browser.activity.BrowserActivity
                        if (bAct != null && bAct.isBarAnimationRunning()) return@FrameCallback
                        val density = context.resources.displayMetrics.density
                        val hideAfterPx = 24f * density
                        val showAfterPx = 16f * density
                        val deltaY = latestY - lastAddressBarScrollY
                        lastAddressBarScrollY = latestY
                        onScrollChangeListener?.onScrollPositionChanged(latestY, height)
                        if (latestY <= 2) {
                            // At the top of the page: bars are always shown.
                            scrollRun = 0
                            onScrollChangeListener?.onScrollUp()
                        } else if (deltaY != 0) {
                            if ((deltaY > 0) != (scrollRun > 0)) scrollRun = 0
                            scrollRun += deltaY
                            if (scrollRun > hideAfterPx) {
                                scrollRun = 0
                                onScrollChangeListener?.onScrollDown()
                            } else if (scrollRun < -showAfterPx) {
                                scrollRun = 0
                                onScrollChangeListener?.onScrollUp()
                            }
                        }

                        // Tactile haptics: only check when enabled, with spaced interval
                        if (com.petal.browser.haptics.PetalHapticEngine.isScrollHapticsEnabled(context)) {
                            if (Math.abs(latestY - lastScrollHapticY) > 64) {
                                lastScrollHapticY = latestY
                                com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                                    .play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.40f, 50L)
                            }
                        }
            }

            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                currentScrollX = scrollX
                currentScrollY = scrollY
                pendingScrollY = scrollY
                if (isFrameScheduled) return
                isFrameScheduled = true
                // Gecko calls this on the UI thread, so go straight to the next frame.
                android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }

        // Selection & Context Menu Action Delegate (Long-click text selection)
        session.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
            override fun onShowActionRequest(
                session: GeckoSession,
                selection: GeckoSession.SelectionActionDelegate.Selection
            ) {
                currentSelection = selection
                val act = getHostActivity()
                if (act != null && com.petal.browser.ui.selection.PetalPageSelectionToolbar.show(act, this@PetalGeckoView, selection)) {
                    return
                }
                if (act is com.petal.browser.activity.BrowserActivity) {
                    val selectedText = selection.text
                    if (!selectedText.isNullOrBlank()) {
                        act.runOnUiThread {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showSelectionContextMenu(act, selectedText)
                        }
                    }
                }
            }

            override fun onHideAction(session: GeckoSession, reason: Int) {
                currentSelection = null
                getHostActivity()?.let { com.petal.browser.ui.selection.PetalPageSelectionToolbar.hide(it) }
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
                com.petal.browser.engine.gecko.PetalEngineStore.deactivateMediaSession(context, tabId)
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                isMediaPlaying = true
                mediaBridge?.setActiveGeckoMediaSession(mediaSession)
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        act.updatePipParams(true)
                    }
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(true)
                    l?.onMediaPlay(currentTitle, 0L, 0L)
                }
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                isMediaPlaying = false
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        act.updatePipParams(false)
                    }
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(false)
                    l?.onMediaPause(0L, 0L)
                }
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                isMediaPlaying = false
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
        sessionState = SessionState.READY

        val queuedUrl = pendingLoadUrl
        if (!queuedUrl.isNullOrBlank()) {
            pendingLoadUrl = null
            loadUrl(queuedUrl)
        }
    }

    /**
     * Records or updates a history entry for the currently visited page, ensuring
     * fallback titles when empty and capturing single-page application (SPA) navigations.
     */
    private fun recordHistoryVisit(targetUrl: String, titleToRecord: String? = null, force: Boolean = false) {
        if (isIncognito || targetUrl.isBlank() || targetUrl.equals("about:blank", ignoreCase = true) ||
            targetUrl.startsWith("about:", ignoreCase = true) || PetalBrowserUtils.isHomePage(targetUrl)) {
            return
        }
        if (!force && targetUrl == lastRecordedHistoryUrl) {
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
            val action = HistoryBookmarkRepository(context)
            action.open(true)
            if (action.checkUrl(targetUrl, HistoryBookmarkContract.TABLE_HISTORY)) {
                action.deleteURL(targetUrl, HistoryBookmarkContract.TABLE_HISTORY)
            }
            action.addHistory(com.petal.browser.database.Record(effectiveTitle, targetUrl, System.currentTimeMillis(), 0))
            action.close()
            com.petal.browser.unit.PetalSessionHistoryManager.recordSessionVisit(targetUrl)
            lastRecordedHistoryUrl = targetUrl
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
        // The initialization guard belongs to the old session, so reset it before
        // transferring ownership to the already-open popup session.
        sessionInitializationStarted = false
        initGeckoSession()
    }

    private fun updateProgress(progress: Int) {
        // GeckoView uses the same shared browser loading surface as WebView.
        // BrowserActivity renders it with the exact Material 3 Expressive
        // LinearWavyProgressIndicator used by Essentials' App Updater.
        if (isForegroundTab) {
            val bc = getBrowserController()
            if (bc != null) {
                val p = if (!isStopped) progress else PetalBrowserUtils.LOADING_STOPPED
                bc.updateProgress(p)
            }
        }
    }

    fun applySettings() {
        val profile = getProfile(context)
        // Check per-domain desktop site preference first, then fallback to global/profile desktop mode
        val host = try { android.net.Uri.parse(currentUrl ?: "").host } catch (_: Throwable) { null }
        val desktopEnabled = if (!host.isNullOrBlank() && sp.contains("sp_desktop_site_$host")) {
            sp.getBoolean("sp_desktop_site_$host", false)
        } else {
            sp.getBoolean("${profile}_desktop", sp.getBoolean("sp_desktop_site", false))
        }
        applyDesktopMode(desktopEnabled)
        applyGeckoBlockingPolicy(currentUrl)
        val secLevel = com.petal.browser.privacy.PetalPrivacyHardening.getSecurityLevel(sp)
        val enableJs = if (secLevel == com.petal.browser.privacy.PetalPrivacyHardening.SecurityLevel.SAFEST) {
            false
        } else {
            sp.getBoolean("sp_javascript", sp.getBoolean("${profile}_javascript", sp.getBoolean("profileStandard_javascript", true)))
        }
        session.settings.allowJavascript = enableJs
        com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(sp)
    }

    /**
     * Preferred color scheme is runtime-wide in GeckoView (preferredColorScheme).
     * It is configured on GeckoRuntime creation and updated via PetalGeckoRuntime.syncTheme(sp)
     * only when the theme setting changes, avoiding per-tab re-application.
     */
    private fun applyDarkModePolicy() {
        // No-op per-tab to preserve runtime-wide preferredColorScheme integrity.
    }

    fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError
    ): GeckoResult<String>? {
        Log.w(TAG, "Navigation onLoadError: uri=$uri, category=${error.category}, code=${error.code}")
        if (uri != null && uri.startsWith("https://", ignoreCase = true)) {
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val httpsOnly = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false))
            val host = try { android.net.Uri.parse(uri).host } catch (_: Throwable) { null }
            if (httpsOnly && !host.isNullOrBlank() && !com.petal.browser.privacy.PetalHttpsOnlyWhitelist.isBypassed(context, host)) {
                if (error.category == WebRequestError.ERROR_CATEGORY_SECURITY ||
                    error.category == WebRequestError.ERROR_CATEGORY_NETWORK) {
                    val act = getHostActivity() as? com.petal.browser.activity.BrowserActivity
                    act?.runOnUiThread {
                        com.petal.browser.view.PetalToast.show(
                            act,
                            "HTTPS connection failed for $host (HTTPS-Only Mode)",
                            com.petal.browser.view.PetalToast.LENGTH_LONG,
                            "Load via HTTP",
                            {
                                com.petal.browser.privacy.PetalHttpsOnlyWhitelist.addBypass(act, host)
                                val httpUrl = "http://" + uri.substring(8)
                                act.showAlbum(act.currentAlbumController, httpUrl)
                            }
                        )
                    }
                }
            }
        }
        hideLoadingSkeleton()
        dismissSurfaceCover()
        hasContentfulPaint = true
        isStopped = true
        updateProgress(PetalBrowserUtils.LOADING_STOPPED)
        val act = getHostActivity()
        if (act is com.petal.browser.activity.BrowserActivity) {
            act.runOnUiThread {
                act.updateOmniBox()
                act.updateAddressBar()
            }
        }

        val errorType = mapWebRequestErrorToErrorType(error)
        val errorUri = try {
            mozilla.components.browser.errorpages.ErrorPages.createUrlEncodedErrorPage(
                context.applicationContext,
                errorType,
                uri
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build error page URI", t)
            null
        }
        return if (errorUri != null) GeckoResult.fromValue(errorUri) else null
    }

    private fun mapWebRequestErrorToErrorType(error: WebRequestError): mozilla.components.concept.engine.request.ErrorType {
        val name = when (error.code) {
            WebRequestError.ERROR_UNKNOWN_HOST -> "ERROR_UNKNOWN_HOST"
            WebRequestError.ERROR_NET_TIMEOUT -> "ERROR_NET_TIMEOUT"
            WebRequestError.ERROR_CONNECTION_REFUSED -> "ERROR_CONNECTION_REFUSED"
            WebRequestError.ERROR_NET_RESET -> "ERROR_NET_RESET"
            WebRequestError.ERROR_NET_INTERRUPT -> "ERROR_NET_INTERRUPT"
            WebRequestError.ERROR_OFFLINE -> "ERROR_OFFLINE"
            WebRequestError.ERROR_PORT_BLOCKED -> "ERROR_PORT_BLOCKED"
            WebRequestError.ERROR_REDIRECT_LOOP -> "ERROR_REDIRECT_LOOP"
            WebRequestError.ERROR_UNKNOWN_PROTOCOL -> "ERROR_UNKNOWN_PROTOCOL"
            WebRequestError.ERROR_MALFORMED_URI -> "ERROR_MALFORMED_URI"
            WebRequestError.ERROR_SECURITY_SSL -> "ERROR_SECURITY_SSL"
            WebRequestError.ERROR_SECURITY_BAD_CERT -> "ERROR_SECURITY_BAD_CERT"
            WebRequestError.ERROR_BAD_HSTS_CERT -> "ERROR_BAD_HSTS_CERT"
            WebRequestError.ERROR_HTTPS_ONLY -> "ERROR_HTTPS_ONLY"
            WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI -> "ERROR_SAFEBROWSING_PHISHING_URI"
            WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI -> "ERROR_SAFEBROWSING_MALWARE_URI"
            WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI -> "ERROR_SAFEBROWSING_UNWANTED_URI"
            WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI -> "ERROR_SAFEBROWSING_HARMFUL_URI"
            else -> when (error.category) {
                WebRequestError.ERROR_CATEGORY_SECURITY -> "ERROR_SECURITY_SSL"
                WebRequestError.ERROR_CATEGORY_NETWORK -> "ERROR_OFFLINE"
                WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> "ERROR_SAFEBROWSING_MALWARE_URI"
                WebRequestError.ERROR_CATEGORY_URI -> "ERROR_MALFORMED_URI"
                else -> "UNKNOWN"
            }
        }
        return try {
            mozilla.components.concept.engine.request.ErrorType.valueOf(name)
        } catch (_: Throwable) {
            try {
                mozilla.components.concept.engine.request.ErrorType.UNKNOWN
            } catch (_: Throwable) {
                mozilla.components.concept.engine.request.ErrorType.values().first()
            }
        }
    }

    fun onSubframeLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny>? {
        val uri = request.uri
        if (isExternalScheme(uri)) {
            android.util.Log.d(TAG, "Blocked subframe external scheme request: $uri")
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
    }

    /**
     * Uses Gecko's native content-blocking pipeline for Gecko tabs. The legacy
     * PetalAdBlockEngine remains the WebView fallback, but must not duplicate or
     * inject rules into Gecko pages. Site whitelisting is applied at the session
     * boundary so the native tracker blocker is disabled for that origin only.
     */
    private fun applyGeckoBlockingPolicy(url: String?) {
        val enabled = com.petal.browser.browser.PetalAdBlockEngine.isAdBlockEnabled(context)
        val host = try { android.net.Uri.parse(url ?: "").host } catch (_: Throwable) { null }
        val whitelisted = !host.isNullOrBlank() &&
            com.petal.browser.browser.PetalAdBlockEngine.isDomainWhitelisted(host)
        session.settings.useTrackingProtection = enabled && !whitelisted
        com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(sp)
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
        dismissCrashErrorState()
        val trimmed = url.trim()
        if (trimmed.equals("about:config", ignoreCase = true) ||
            trimmed.equals("about:flags", ignoreCase = true) ||
            trimmed.startsWith("petal://flags", ignoreCase = true) ||
            trimmed.equals("chrome://flags", ignoreCase = true)) {
            val act = getHostActivity()
            if (act is com.petal.browser.activity.BrowserActivity) {
                act.runOnUiThread { act.openFlagsHubScreen() }
                return
            }
        }

        // "Petal Home" / "Petal Start" are the tab-switcher's human-readable display
        // placeholders (see AdapterTabs/setAlbumTitle), never real navigable targets.
        // If one leaks in here (e.g. from a session saved before this was fixed, or
        // any other display->navigation mixup), treat it as home instead of letting
        // queryWrapper() silently turn it into a live search-engine query.
        if (url.trim().equals("Petal Home", ignoreCase = true) || url.trim().equals("Petal Start", ignoreCase = true)) {
            hideLoadingSkeleton()
            session.loadUri("about:blank")
            currentUrl = "about:blank"
            currentTitle = "Petal Home"
            return
        }

        val redirected = PetalBrowserUtils.redirectURL(sp, url)
        val targetUrl = PetalBrowserUtils.queryWrapper(context, redirected)

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
                com.petal.browser.download.PetalDownloadRouter.download(
                    activity,
                    targetUrl,
                    PetalAndroidUtils.resolveFileName(targetUrl, null, "application/vnd.android.package-archive"),
                    "application/vnd.android.package-archive",
                    referrerUrl = currentUrl,
                    isPrivate = isIncognito
                )
                return
            }
        }

        if (PetalBrowserUtils.isHomePage(targetUrl) || PetalBrowserUtils.isHomePage(url) || targetUrl.equals("about:blank", ignoreCase = true)) {
            hideLoadingSkeleton()
            session.loadUri("about:blank")
            currentUrl = "about:blank"
            currentTitle = "Petal Home"
            return
        }

        if (handleDeepLinkOrCustomScheme(targetUrl)) {
            hideLoadingSkeleton()
            return
        }

        showLoadingSkeleton(targetUrl)
        currentUrl = targetUrl
        // Persist the intended URL separately from the live currentUrl so saveSession()
        // can recover it even if about:blank fires before the page finishes loading.
        persistentUrl = targetUrl
        if (engineSession != null) {
            engineSession.loadUrl(targetUrl)
        } else {
            if (sessionState != SessionState.READY || !session.isOpen) {
                // Queue the navigation request until GeckoSession initialization and attachment completes.
                // Calling session.loadUri before session.open() or during unattached state silently drops requests.
                pendingLoadUrl = targetUrl
                if (!sessionInitializationStarted && PetalGeckoRuntime.isGeckoAvailable(context)) {
                    initGeckoSession()
                }
                return
            }
            applySettings()
            session.loadUri(targetUrl)
        }
    }


    fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        try {
            val encodedData = android.util.Base64.encodeToString(data.toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
            val dataUri = "data:${mimeType ?: "text/html"};charset=${encoding ?: "utf-8"};base64,$encodedData"
            currentUrl = baseUrl ?: dataUri
            session.loadUri(dataUri)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in loadDataWithBaseURL", e)
        }
    }

    fun loadData(data: String, mimeType: String?, encoding: String?) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null)
    }

    /**
     * Recovers a GeckoSession after its content process has crashed or been killed.
     * Per GeckoView's documented contract, the session is permanently closed and
     * unusable at that point; re-opening it against the shared runtime and reloading
     * the last URL is the only way to restore a usable page. No page state (scroll
     * position, form data) survives this - that data is lost with the killed process.
     */
    private var crashErrorView: View? = null

    private fun showCrashErrorState() {
        crashErrorView = PetalCrashOverlayHelper.showCrashErrorState(this, crashErrorView) {
            crashRecoveryCount = 0
            recoverCrashedSession()
        }
    }

    private fun dismissCrashErrorState() {
        crashErrorView = PetalCrashOverlayHelper.dismissCrashErrorState(this, crashErrorView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissCrashErrorState()
    }

    private fun recoverCrashedSession() {
        try {
            dismissCrashErrorState()
            val now = System.currentTimeMillis()
            if (now - lastCrashRecoveryTime < 8000L) {
                crashRecoveryCount++
            } else {
                crashRecoveryCount = 1
            }
            lastCrashRecoveryTime = now

            if (crashRecoveryCount > 3) {
                android.util.Log.e(TAG, "Repeated GeckoSession crash detected ($crashRecoveryCount times in short window). Halting recovery loop.")
                showCrashErrorState()
                return
            }

            // A killed Gecko content process leaves its GeckoSession permanently unusable.
            // Recreate the session and all delegates instead of reopening the dead instance.
            try { geckoView.releaseSession() } catch (_: Throwable) {}
            try { if (session.isOpen) session.close() } catch (_: Throwable) {}
            val secLevel = com.petal.browser.privacy.PetalPrivacyHardening.getSecurityLevel(sp)
            val enableJs = if (secLevel == com.petal.browser.privacy.PetalPrivacyHardening.SecurityLevel.SAFEST) false
            else sp.getBoolean("sp_javascript", true)
            session = GeckoSession(
                GeckoSessionSettings.Builder()
                    .usePrivateMode(isIncognito)
                    .allowJavascript(enableJs)
                    .build()
            )
            sessionInitializationStarted = false
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
                        if (isForegroundTab) {
                            session.setActive(true)
                        }
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

    fun hasBackHistory(): Boolean = canGoBackVal

    fun canGoForward(): Boolean = canGoForwardVal

    /**
     * Let page-owned modal UI (such as HTML `<dialog>`, popups, or modern `CloseWatcher` elements)
     * consume Back before the browser traverses tab history. Matching Firefox & PetalTabViewController parity.
     */
    fun processBackPressed(onUnhandled: () -> Unit) {
        val engSession = engineSession
        if (engSession != null) {
            engSession.processBackPressed { handled ->
                if (handled) return@processBackPressed
                if (canGoBackVal) goBack() else onUnhandled()
            }
            return
        }

        try {
            session.processBackPressed().then(
                { handled ->
                    if (handled != null && handled) {
                        // Consumed by web modal/dialog
                        org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
                    } else {
                        post {
                            if (canGoBackVal) goBack() else onUnhandled()
                        }
                        org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
                    }
                },
                { _ ->
                    post {
                        if (canGoBackVal) goBack() else onUnhandled()
                    }
                    org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
                }
            )
        } catch (_: Throwable) {
            if (canGoBackVal) goBack() else onUnhandled()
        }
    }

    @JvmOverloads
    fun goBack(userInteraction: Boolean = true) {
        if (canGoBackVal) {
            try {
                session.goBack(userInteraction)
            } catch (t: Throwable) {
                engineSession?.goBack(userInteraction = userInteraction)
            }
        } else {
            // canGoBackVal can be transiently false during session restore before
            // Gecko's onCanGoBack() callback fires (the historyPending window).
            // Ask the engine session directly as a fallback so we don't silently
            // drop a back gesture when history exists but hasn't been reported yet.
            // This mirrors PetalTabViewController's isHistoryPending() path.
            try { engineSession?.goBack(userInteraction = userInteraction) } catch (_: Throwable) {}
        }
    }

    @JvmOverloads
    fun goForward(userInteraction: Boolean = true) {
        if (canGoForwardVal) {
            try {
                session.goForward(userInteraction)
            } catch (t: Throwable) {
                engineSession?.goForward(userInteraction = userInteraction)
            }
        }
    }

    fun reload() {
        reloadInternal(GeckoSession.LOAD_FLAGS_NONE)
    }

    fun reloadBypassCache() {
        reloadInternal(GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
    }

    private fun reloadInternal(flags: Int) {
        isStopped = false
        applySettings()
        val target = if (currentUrl.isNotBlank() && !PetalBrowserUtils.isHomePage(currentUrl) && !currentUrl.equals("about:blank", ignoreCase = true)) {
            showLoadingSkeleton(currentUrl)
            currentUrl
        } else if (persistentUrl.isNotBlank() && !PetalBrowserUtils.isHomePage(persistentUrl) && !persistentUrl.equals("about:blank", ignoreCase = true)) {
            showLoadingSkeleton(persistentUrl)
            persistentUrl
        } else {
            null
        }

        // Directly reload active GeckoSession so that navigating within a tab (e.g. from Google Search
        // to YouTube) reloads the active page instead of re-triggering Mozilla GeckoEngineSession's
        // stale initialLoadRequest.
        try {
            session.reload(flags)
        } catch (_: Throwable) {
            if (target != null) {
                session.loadUri(target)
            } else {
                try {
                    session.reload()
                } catch (_: Throwable) {
                    engineSession?.reload()
                }
            }
        }
    }

    fun stopLoading() {
        isStopped = true
        if (engineSession != null) engineSession.stopLoading() else session.stop()
        updateProgress(PetalBrowserUtils.LOADING_STOPPED)
    }

    fun getProgress(): Int = currentProgress

    fun isStopped(): Boolean = isStopped

    fun initPreferences(url: String?) {
        applySettings()
    }

    fun canClearSelection(): Boolean = currentSelection != null

    fun clearWebSelection(): Boolean {
        return try {
            val sel = currentSelection
            if (sel != null) {
                currentSelection = null
                sel.collapseToStart()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            currentSelection = null
            false
        }
    }

    fun clearMatches() {
        session.finder.clear()
        com.petal.browser.engine.gecko.PetalEngineStore.clearFindResults(context, tabId)
    }

    @JvmOverloads
    fun findAllAsync(query: String, callback: ((current: Int, total: Int) -> Unit)? = null) {
        // Match standard browser search behavior (case-insensitive by default)
        session.finder.find(query, 0).then({ result ->
            val cur = result?.current ?: 0
            val tot = result?.total ?: 0
            callback?.invoke(cur, tot)
            GeckoResult.fromValue(result)
        })
    }

    @JvmOverloads
    fun findNext(forward: Boolean, callback: ((current: Int, total: Int) -> Unit)? = null) {
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        session.finder.find(null, flags).then({ result ->
            val cur = result?.current ?: 0
            val tot = result?.total ?: 0
            callback?.invoke(cur, tot)
            GeckoResult.fromValue(result)
        })
    }

    fun setUserAgent(customUserAgent: String?) {
        try {
            if (customUserAgent != null && customUserAgent.isNotBlank()) {
                session.settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                session.settings.userAgentOverride = customUserAgent
            } else {
                session.settings.userAgentOverride = null
                val desktopEnabled = sp.getBoolean("sp_desktop", false)
                applyDesktopMode(desktopEnabled)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Toggles Firefox Reader Mode for the active page if available.
     */
    fun toggleReaderMode(active: Boolean) {
        try {
            com.petal.browser.engine.gecko.PetalEngineStore.toggleReaderMode(context, tabId, active)
            if (active) {
                session.loadUri("about:reader?url=${android.net.Uri.encode(currentUrl)}")
            } else if (currentUrl.startsWith("about:reader?url=")) {
                val origUrl = android.net.Uri.decode(currentUrl.substringAfter("about:reader?url="))
                loadUrl(origUrl)
            }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Failed to toggle reader mode: ${t.message}")
        }
    }

    /**
     * Updates Enhanced Tracking Protection policy level dynamically.
     */
    fun setTrackingProtectionLevel(level: Int) { // one of ContentBlocking.EtpLevel.NONE / DEFAULT / STRICT
        try {
            val runtime = com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(context)
            runtime.settings.contentBlocking.setEnhancedTrackingProtectionLevel(level)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Failed to update ETP level: ${t.message}")
        }
    }

    /**
     * Exports the current page to a PDF via GeckoSession.saveAsPdf(), which yields the PDF bytes
     * as an InputStream. The bytes are copied into [outputStream], and the streams are closed.
     */
    fun printToPdf(outputStream: java.io.OutputStream, callback: ((Boolean) -> Unit)? = null) {
        try {
            val result = session.saveAsPdf()
            if (result == null) {
                callback?.invoke(false)
                return
            }
            result.accept(
                { pdfStream ->
                    val ok = try {
                        if (pdfStream == null) {
                            false
                        } else {
                            pdfStream.use { input -> outputStream.use { out -> input.copyTo(out) } }
                            true
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e(TAG, "Failed writing PDF: ${t.message}")
                        false
                    }
                    callback?.invoke(ok)
                },
                { callback?.invoke(false) }
            )
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to print to PDF: ${t.message}")
            callback?.invoke(false)
        }
    }

    /**
     * Captures the full viewport visible page screenshot at native quality for user capture/export.
     */
    fun captureFullPageBitmap(callback: (Bitmap?) -> Unit) {
        try {
            geckoView.capturePixels().then({ bitmap ->
                callback(bitmap)
                GeckoResult.fromValue(null)
            }, {
                callback(null)
                GeckoResult.fromValue(null)
            })
        } catch (e: Exception) {
            callback(null)
        }
    }

    /**
     * Web Archive export is not available in GeckoView (GeckoSession has no such API; saveWebArchive
     * exists only on Android's system WebView). Always reports failure so callers can fall back.
     */
    fun saveAsWebArchive(outputStream: java.io.OutputStream, callback: ((Boolean) -> Unit)? = null) {
        android.util.Log.w(TAG, "saveAsWebArchive is not supported by GeckoView")
        callback?.invoke(false)
    }

    fun clearHistory() {
        canGoBackVal = false
        canGoForwardVal = false
        session.purgeHistory()
    }

    fun reloadWithoutInit() {
        isStopped = false
        session.reload()
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (direction < 0) {
            // Rely solely on the compositor-reported scroll position here.
            // GeckoView's own canScrollVertically(-1) always returns true
            // (it defers real scroll-boundary checks to the compositor rather
            // than the standard View scroll APIs).
            return !isPageAtTop()
        }
        return geckoView.canScrollVertically(direction)
    }

    /**
     * True when the page is at (or within a couple of device pixels of) the top.
     *
     * Gecko reports scroll in device pixels and can leave the last reported value at
     * 1-3px after a fling settles because of sub-pixel / DPR rounding. Treating any
     * value > 0 as "scrolled" made pull-to-refresh permanently unavailable on pages
     * that were visibly at the top. Negative values (overscroll) also count as top.
     * Also returns true if the page is currently about:blank or home.
     */
    fun isPageAtTop(): Boolean {
        if (currentUrl.isBlank() || currentUrl.equals("about:blank", ignoreCase = true) || PetalBrowserUtils.isHomePage(currentUrl)) {
            return true
        }
        return currentScrollY <= PAGE_TOP_TOLERANCE_PX
    }

    fun getPageScrollY(): Int = currentScrollY

    fun getPageScrollX(): Int = currentScrollX

    fun setProfileChanged() {
        applySettings()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AlbumController Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun getAlbumView(): View = this

    override fun getTabView(): View = this

    override fun activate() {
        // Give IME/editor actions to GeckoView itself. Focusing the wrapper FrameLayout
        // leaves webpage fields visually focused but can swallow the keyboard Enter key.
        geckoView.isFocusable = true
        geckoView.isFocusableInTouchMode = true
        geckoView.requestFocus()
        isForegroundTab = true
        session.setActive(true)
        if (hasDocumentPainted) {
            dismissSurfaceCover()
        }
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(null, session)
        geckoView.visibility = View.VISIBLE
        // Notify BrowserStore of tab selection
        try {
            com.petal.browser.engine.gecko.PetalEngineStore.selectTab(context, tabId)
        } catch (_: Throwable) {}
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
        // Prevent outer FrameLayout from stealing keyboard/IME focus from GeckoView
        return geckoView.requestFocus(direction, previouslyFocusedRect)
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (child != geckoView && focused != geckoView) {
            geckoView.requestFocus()
        }
    }

    override fun deactivate() {
        clearFocus()
        // Capture thumbnail preview while Gecko compositor surface is still active. This must
        // happen before isForegroundTab is cleared: capturePreviewBitmapAsync() refuses to
        // capture background tabs, so the previous order made this capture a guaranteed no-op.
        try {
            updatePreviewCache()
        } catch (_: Throwable) {}
        isForegroundTab = false
        try {
            if (!isPlayingAudio()) {
                session.setActive(false)
            }
        } catch (_: Throwable) {}
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(session, null)
    }

    override fun getTitle(): String {
        if (currentTitle.isNotEmpty() && !currentTitle.equals("Petal Start", ignoreCase = true)) {
            return currentTitle
        }
        return currentTitle
    }

    fun getAlbumTitle(): String {
        return currentTitle
    }

    override fun getUrl(): String {
        return currentUrl
    }

    fun getAlbumUrl(): String = currentUrl

    fun resetToHome() {
        hideLoadingSkeleton()
        currentUrl = "about:blank"
        currentTitle = "Petal Home"
        try {
            session.stop()
            session.loadUri("about:blank")
        } catch (_: Exception) {}
    }

    fun setAlbumTitle(title: String?, url: String?) {
        if (!title.isNullOrBlank()) {
            currentTitle = title
        }
        if (!url.isNullOrBlank()) {
            currentUrl = url
        }
    }

    /**
     * Records the URL of a tab whose navigation was already started by Gecko itself
     * (a link opened in a new tab / popup adopted via [adoptPopupSession]) WITHOUT
     * issuing another load.
     *
     * currentUrl starts out as "about:blank". BrowserActivity.showAlbum() decides
     * "is this the home screen?" from getAlbumUrl(), so an adopted tab that still
     * reports about:blank was treated as home: showAlbum() called resetToHome(),
     * which does session.stop() + loadUri("about:blank") - cancelling the link that
     * was loading and showing the home screen instead. Setting the real URL here
     * makes showAlbum() treat it as a website and leaves Gecko's load untouched.
     */
    fun markAdoptedNavigation(url: String?) {
        isAdoptedPopup = true
        val target = url?.trim().orEmpty()
        if (target.isEmpty() || PetalBrowserUtils.isHomePage(target)) return
        currentUrl = target
        currentTitle = target
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

    override fun isIncognito(): Boolean = isIncognito

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

    override fun onAttachedToWindow() {
        try {
            super.onAttachedToWindow()
        } catch (e: NullPointerException) {
            android.util.Log.w(TAG, "Handled PetalGeckoView onAttachedToWindow NPE: ${e.message}")
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Handled PetalGeckoView onAttachedToWindow error: ${t.message}")
        }
    }


    fun setBrowserController(controller: BrowserController?) {
        globalBrowserControllerRef = if (controller != null) java.lang.ref.WeakReference(controller) else null
    }

    fun setOnScrollChangeListener(listener: OnScrollChangeListener?) {
        this.onScrollChangeListener = listener
    }

    fun getMediaBridge(): PetalMediaBridge? {
        if (mediaBridge == null) {
            val act = getHostActivity()
            if (act != null) {
                mediaBridge = PetalMediaBridge(act, this, object : PetalMediaBridge.MediaStateListener {
                    override fun onMediaPlay(title: String?, positionMs: Long, durationMs: Long) {
                        isMediaPlaying = true
                        if (act is com.petal.browser.activity.BrowserActivity) {
                            act.isMediaPlaying = true
                            act.updatePipParams(true)
                            act.mediaService?.updateMediaState(title ?: currentTitle, currentTitle, true, positionMs, durationMs)
                        }
                    }

                    override fun onMediaPause(positionMs: Long, durationMs: Long) {
                        isMediaPlaying = false
                        if (act is com.petal.browser.activity.BrowserActivity) {
                            act.isMediaPlaying = false
                            act.updatePipParams(false)
                            act.mediaService?.updateMediaState(currentTitle, currentTitle, false, positionMs, durationMs)
                        }
                    }

                    override fun onMediaProgress(positionMs: Long, durationMs: Long) {}

                    override fun onMediaPlayingStateChanged(playing: Boolean) {
                        isMediaPlaying = playing
                        if (act is com.petal.browser.activity.BrowserActivity) {
                            act.isMediaPlaying = playing
                            act.updatePipParams(playing)
                        }
                    }

                    override fun onVideoDimensionsChanged(width: Int, height: Int) {
                        if (act is com.petal.browser.activity.BrowserActivity) {
                            act.updateVideoDimensions(width, height)
                        }
                    }
                })
            }
        }
        return mediaBridge
    }

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
        // No-op for modern tab architecture; favicon cache updated in setFavicon
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab Previews & Caching
    // ─────────────────────────────────────────────────────────────────────────

    fun getThumbnailKey(): String = getTabId()

    private var lastPreviewCaptureTime = 0L

    @JvmOverloads
    fun updatePreviewCache(force: Boolean = false) {
        val currentRev = previewRevision
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && capturedPreviewRevision == currentRev) {
            if (now - lastPreviewCaptureTime < 1500L) return
        }
        lastPreviewCaptureTime = now
        capturePreviewBitmapAsync { /* cache updated */ }
    }

    private fun schedulePreviewCapture(delayMs: Long) {
        postDelayed({
            try { updatePreviewCache() } catch (_: Throwable) {}
        }, delayMs)
    }

    /** Bounded retry for frames that came back blank because the compositor was not ready. */
    private fun schedulePreviewRetry() {
        if (previewRetryCount >= 3) return
        previewRetryCount++
        schedulePreviewCapture(350L * previewRetryCount)
    }

    fun getCachedPreviewBitmap(): Bitmap? {
        return TabThumbnailCache.getMemoryOnly(getThumbnailKey(), isIncognito)
    }

    fun capturePreviewBitmapAsync(callback: Consumer<Bitmap?>) = capturePreview(false, callback)

    /** Explicit capture right before the tab manager opens; see PetalTabViewController. */
    fun captureForSwitcher(callback: Consumer<Bitmap?>) = capturePreview(true, callback)

    private fun capturePreview(forceAccept: Boolean, callback: Consumer<Bitmap?>) {
        val key = getThumbnailKey()
        val revision = previewRevision
        val captureSequence = ++previewCaptureSequence
        val privateTab = isIncognito

        val cachingConsumer: (Bitmap?) -> Unit = { bmp ->
            val current = key == getThumbnailKey()
            var stored = false
            if (bmp != null && current) {
                stored = TabThumbnailCache.put(key, bmp, privateTab, forceAccept || hasContentfulPaint)
                if (stored) capturedPreviewRevision = revision else schedulePreviewRetry()
            }
            callback.accept(if (current && stored) bmp else null)
        }

        try {
            // Guard against unattached or 0-dimension views
            if (!isAttachedToWindow || geckoView.width <= 0 || geckoView.height <= 0) {
                cachingConsumer(null)
                return
            }
            geckoView.capturePixels().then({ bitmap ->
                if (bitmap != null) {
                    TabThumbnailCache.DISK.execute {
                        val w = bitmap.width
                        val h = bitmap.height
                        val targetWidth = Math.min(w, 480)
                        val targetHeight = Math.max(1, (h.toFloat() * targetWidth / w).toInt())
                        val scaled = try {
                            val s = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                            if (s !== bitmap && !bitmap.isRecycled) {
                                bitmap.recycle()
                            }
                            s
                        } catch (_: Throwable) {
                            bitmap
                        }
                        post {
                            cachingConsumer(scaled)
                        }
                    }
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
        return null
    }

    fun getBackPreviewBitmap(): Bitmap? {
        return null
    }

    fun onResume() {
        isForegroundTab = true
        try {
            session.setActive(true)
        } catch (_: Throwable) {}
        try {
            geckoView.visibility = View.VISIBLE
            geckoView.requestLayout()
            geckoView.invalidate()
        } catch (_: Throwable) {}
    }

    fun onPause() {
        val bgPlay = sp.getBoolean("sp_background_play", false)
        if (!isPlayingAudio() && (!bgPlay || !hasActiveMediaSession())) {
            session.setActive(false)
        }
    }

    /**
     * Returns true if audio/video is currently playing in this tab.
     */
    fun isPlayingAudio(): Boolean = isMediaPlaying

    /**
     * Returns true if this tab has an active or paused media session.
     */
    fun hasActiveMediaSession(): Boolean = mediaBridge?.activeGeckoMediaSession != null

    fun resumeTimers() {}

    fun pauseTimers() {}

    fun resetGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val emptyRects: List<android.graphics.Rect> = java.util.Collections.emptyList()
                systemGestureExclusionRects = emptyRects
                geckoView.systemGestureExclusionRects = emptyRects
                for (i in 0 until childCount) {
                    getChildAt(i)?.systemGestureExclusionRects = emptyRects
                }
            } catch (_: Throwable) {}
        }
    }

    fun setDynamicToolbarMaxHeight(toolbarHeightPx: Int) {
        try {
            geckoView.setDynamicToolbarMaxHeight(toolbarHeightPx)
        } catch (_: Throwable) {}
    }

    fun setVerticalClipping(clippingHeightPx: Int) {
        try {
            geckoView.setVerticalClipping(clippingHeightPx)
        } catch (_: Throwable) {}
    }

    override fun destroy() {
        try {
            val origin = currentUrl?.let { u ->
                val p = android.net.Uri.parse(u)
                val s = p.scheme ?: "https"
                val a = p.authority
                if (a != null) "$s://$a" else null
            }
            if (origin != null) {
                com.petal.browser.ui.components.PetalActiveMediaSensors.clearOrigin(origin)
                com.petal.browser.ui.components.PetalGeckoPermissionDelegate.clearSessionGrantsForOrigin(origin)
            }
        } catch (_: Throwable) {}
        sessionState = SessionState.DESTROYED
        pendingLoadUrl = null
        stopLoading()
        try { session.setActive(false) } catch (_: Throwable) {}
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(session, null)
        com.petal.browser.extensions.PetalExtensionManager.detachSession(session)
        // Detach GeckoView before closing its session. Closing an attached session can
        // race GeckoView's compositor teardown and is particularly fragile on cold start
        // and during Activity destruction.
        try { geckoView.releaseSession() } catch (_: Throwable) {}
        try { if (session.isOpen) session.close() } catch (_: Throwable) {}
        try { engineSession?.close() } catch (_: Throwable) {}
        try { com.petal.browser.engine.gecko.PetalEngineStore.removeTab(context, tabId) } catch (_: Throwable) {}
        try {
            if (isIncognito) {
                TabThumbnailCache.removePrivate(tabId)
            } else {
                TabThumbnailCache.removeAllIdentifiers(tabId, hashCode().toString())
            }
        } catch (_: Throwable) {}
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

    // ─────────────────────────────────────────────────────────────────
    // Predictive back edge gesture.
    //
    // GeckoView (like Chromium's WebView) claims system gesture exclusion
    // zones near the screen edges on behalf of page content that declares
    // its own horizontal touch handling (carousels, custom swipers, CSS
    // touch-action). Those rects are set internally by Gecko and are only
    // valid for the page that requested them - if they are left in place,
    // Android silently routes the next edge swipe to this view as a plain
    // touch instead of surfacing it as a predictive back gesture, which is
    // why the gesture would work on some pages/moments and not others.
    //
    // Official GeckoView guidance (matching how Fennec/Fenix keep the edge clear for the OS)
    // is to clear systemGestureExclusionRects on every ACTION_DOWN, before
    // the OS decides whether this touch belongs to the app or to a
    // predictive back/forward edge swipe - clearing it only after the
    // gesture has already started (as BrowserActivity's
    // handleOnBackStarted does) is too late, because by then Android has
    // already decided the touch was not a back gesture.
    // ─────────────────────────────────────────────────────────────────
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resetGestureExclusionRects()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun gatherTransparentRegion(region: Region?): Boolean {
        return try {
            super.gatherTransparentRegion(region)
        } catch (e: Exception) {
            false
        }
    }

    private fun showLoadingSkeleton(url: String) {
        // No-op: Full-screen skeleton overlay disabled to ensure GeckoView compositor paints directly without occlusion
    }

    private fun hideLoadingSkeleton() {
        // No-op: Full-screen skeleton overlay disabled
    }

    private fun isExternalScheme(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return !(lower.startsWith("http://") || lower.startsWith("https://") ||
                 lower.startsWith("about:") || lower.startsWith("blob:") ||
                 lower.startsWith("data:") || lower.startsWith("javascript:") ||
                 lower.startsWith("petal:") || lower.startsWith("moz-extension:") ||
                 lower.startsWith("resource:") || lower.startsWith("chrome:"))
    }

    private fun handleDeepLinkOrCustomScheme(url: String?): Boolean {
        if (url.isNullOrBlank() || !isExternalScheme(url)) return false

        val act = getHostActivity() ?: return false
        val lower = url.lowercase()
        if (lower.startsWith("intent://")) {
            try {
                val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                if (intent != null) {
                    intent.component = null
                    intent.selector = null
                    intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    val pm = act.packageManager
                    if (pm != null && intent.resolveActivity(pm) != null) {
                        act.startActivity(intent)
                        return true
                    }
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrBlank()) {
                        act.runOnUiThread { loadUrl(fallbackUrl) }
                        return true
                    }
                    val pkg = intent.`package`
                    if (!pkg.isNullOrBlank()) {
                        try {
                            val marketIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=$pkg")
                            )
                            marketIntent.component = null
                            marketIntent.selector = null
                            marketIntent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                            marketIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (pm != null && marketIntent.resolveActivity(pm) != null) {
                                act.startActivity(marketIntent)
                                return true
                            }
                        } catch (e: Exception) {
                            android.util.Log.d(TAG, "Blocked or failed market intent launch: $url", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Blocked or failed intent scheme: $url", e)
            }
            return true
        }

        try {
            val parsedUri = android.net.Uri.parse(url)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, parsedUri)
            intent.component = null
            intent.selector = null
            intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val pm = act.packageManager
            if (pm != null && intent.resolveActivity(pm) != null) {
                act.startActivity(intent)
                return true
            } else if (lower.startsWith("magnet:")) {
                // Friendly torrent/magnet handler when no dedicated torrent client is installed
                val clipboard = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Magnet Link", url))
                PetalToast.show(
                    act,
                    "Magnet link copied to clipboard (install a torrent client to open directly)",
                    android.widget.Toast.LENGTH_LONG
                )
                return true
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Blocked or failed custom scheme launch: $url", e)
        }

        android.util.Log.d(TAG, "Blocked unhandled external link or no app found: $url")
        // No app can handle this URI (e.g. content:// with no matching viewer) and it's
        // not http(s)/about/blob/data/javascript/petal — don't let Gecko fall through and
        // try to render it as a webpage (it will just fail and can leave a broken tab).
        // Block the load and tell the user instead.
        try {
            PetalToast.show(
                act,
                "No app found to open this link",
                android.widget.Toast.LENGTH_SHORT
            )
        } catch (_: Exception) {}

        return true
    }

    /**
     * Smoothly scrolls page to top
     */
    fun scrollToTop() {
        try {
            evaluateJavascript("window.scrollTo({ top: 0, behavior: 'smooth' })")
        } catch (_: Throwable) {}
    }

    /**
     * Smoothly scrolls page to bottom
     */
    fun scrollToBottom() {
        try {
            evaluateJavascript("window.scrollTo({ top: document.body.scrollHeight || document.documentElement.scrollHeight, behavior: 'smooth' })")
        } catch (_: Throwable) {}
    }

    /**
     * Evaluates JavaScript on the current GeckoSession safely with a real result path
     * via the built-in WebExtension content script native messaging port.
     */
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        try {
            com.petal.browser.extensions.PetalBuiltInExtensionManager.evaluateJavascript(session, script, callback)
        } catch (t: Throwable) {
            callback?.invoke("ERROR: ${t.message}")
        }
    }

    private fun injectTorraysAutoSearch(url: String?) {
        if (url.isNullOrBlank()) return
        if (!url.contains("torrays.vercel.app", ignoreCase = true) || !url.contains("q=")) return
        if (!com.petal.browser.extensions.PetalBuiltInExtensionManager.isTorraysEnabled(context)) return

        val script = """
            (function() {
                try {
                    var u = new URL(window.location.href);
                    var q = u.searchParams.get('q');
                    if (!q || !q.trim()) return;
                    var trimmed = q.trim();
                    var count = 0;
                    function run() {
                        count++;
                        var input = document.querySelector('input.search-input') || document.querySelector('input[type="text"]');
                        var form = document.querySelector('form.search-form') || document.querySelector('form');
                        if (input && form) {
                            if (input.dataset.petalSearched === trimmed) return;
                            var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                            if (setter && setter.set) { setter.set.call(input, trimmed); } else { input.value = trimmed; }
                            input.dispatchEvent(new Event('input', { bubbles: true }));
                            input.dispatchEvent(new Event('change', { bubbles: true }));
                            input.dataset.petalSearched = trimmed;
                            setTimeout(function() {
                                var btn = form.querySelector('button.search-button') || form.querySelector('button[type="submit"]');
                                if (btn && !btn.disabled) { btn.click(); }
                                else if (form.requestSubmit) { form.requestSubmit(); }
                                else { form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); }
                            }, 120);
                        } else if (count < 20) {
                            setTimeout(run, 200);
                        }
                    }
                    if (document.readyState === 'complete' || document.readyState === 'interactive') {
                        run();
                    } else {
                        document.addEventListener('DOMContentLoaded', run, { once: true });
                        window.addEventListener('load', run, { once: true });
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        evaluateJavascript(script)
    }

    /**
     * Client-side privacy and content protections.
     * Note: DNT/GPC, WebRTC IP protection, Canvas/WebGL noise, and visibility overrides
     * run at document_start via the built-in extension (defuser.js) to guarantee page scripts
     * cannot execute before protections are applied.
     */
    fun injectClientPrivacyProtections() {
        if (currentUrl.startsWith("about:") || currentUrl.startsWith("chrome:") || currentUrl.startsWith("moz-extension:")) return
        try {
            // Waterfox-grade cosmetic and anti-adblock scriptlet injection
            val adBlock = sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true))
            if (adBlock) {
                val uboPayload = com.petal.browser.browser.PetalAdBlockEngine.getuBlockCosmeticAndScriptletPayload(currentUrl)
                if (uboPayload.isNotBlank()) {
                    evaluateJavascript(uboPayload)
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Seamless Media Handoff: Captures the live playback state of the first or active
     * HTML5 <video> element on the page with real DOM evaluation.
     */
    fun captureVideoHandoffState(callback: (com.petal.browser.media.handoff.MediaHandoff?) -> Unit) {
        try {
            val js = """
                (function() {
                    try {
                        var vids = Array.from(document.querySelectorAll('video'));
                        if (vids.length === 0) return null;
                        var vid = vids.find(function(v) { return !v.paused; }) || vids[0];
                        var pos = Math.round((vid.currentTime || 0) * 1000);
                        var dur = Math.round((vid.duration || 0) * 1000);
                        var speed = vid.playbackRate || 1.0;
                        var isPaused = !!vid.paused;
                        var vol = vid.volume || 1.0;
                        var src = vid.currentSrc || vid.src || '';
                        return JSON.stringify({
                            pos: pos,
                            dur: dur,
                            speed: speed,
                            paused: isPaused,
                            vol: vol,
                            src: src
                        });
                    } catch(e) {
                        return null;
                    }
                })()
            """.trimIndent()
            evaluateJavascript(js) { rawResult ->
                if (rawResult.isNullOrBlank() || rawResult == "null" || rawResult.startsWith("ERROR:")) {
                    callback(null)
                    return@evaluateJavascript
                }
                try {
                    val json = org.json.JSONObject(rawResult)
                    val pos = json.optLong("pos", 0L)
                    val dur = json.optLong("dur", 0L)
                    val speed = json.optDouble("speed", 1.0).toFloat()
                    val paused = json.optBoolean("paused", false)
                    val vol = json.optDouble("vol", 1.0).toFloat()
                    val src = json.optString("src", "").takeIf { it.isNotBlank() } ?: currentUrl
                    callback(com.petal.browser.media.handoff.MediaHandoff(pos, dur, speed, paused, vol, src))
                } catch (_: Throwable) {
                    callback(null)
                }
            }
        } catch (_: Throwable) {
            callback(null)
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
    constructor(context: Context) : super(context) {
        try { setViewBackend(BACKEND_TEXTURE_VIEW) } catch (_: Throwable) {}
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        try { setViewBackend(BACKEND_TEXTURE_VIEW) } catch (_: Throwable) {}
    }

    private var initialDownY: Float = 0f
    private var gestureCanReachParent: Boolean = true
    private var inputResult: Int = PanZoomController.INPUT_RESULT_UNHANDLED
    private var scrollDirections: Int = 0
    private var overscrollDirections: Int = PanZoomController.OVERSCROLL_FLAG_VERTICAL

    fun canOverscrollTop(): Boolean {
        return inputResult != PanZoomController.INPUT_RESULT_HANDLED_CONTENT &&
                (scrollDirections and PanZoomController.SCROLLABLE_FLAG_TOP == 0) &&
                (overscrollDirections and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0)
    }

    fun isTouchHandledByWebsite(): Boolean {
        return inputResult == PanZoomController.INPUT_RESULT_HANDLED_CONTENT
    }

    fun isTouchUnhandled(): Boolean {
        return inputResult == PanZoomController.INPUT_RESULT_UNHANDLED
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return super.onTouchEvent(ev)
    }

    private fun updateInputResult(event: MotionEvent) {
        val eventAction = event.actionMasked
        val eventY = event.y
        try {
            onTouchEventForDetailResult(event).accept { detail ->
                if (!gestureCanReachParent || detail == null) {
                    return@accept
                }
                inputResult = detail.handledResult()
                scrollDirections = detail.scrollableDirections()
                overscrollDirections = detail.overscrollDirections()

                when (eventAction) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureCanReachParent = canOverscrollTop()
                        if (gestureCanReachParent && isTouchUnhandled()) {
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (initialDownY < eventY) {
                            // Downward pull: permit pull-to-refresh when at top
                            parent?.requestDisallowInterceptTouchEvent(false)
                        } else if (initialDownY > eventY) {
                            // Upward scroll into webpage content: lock parent for this gesture
                            // so pull-to-refresh can never intersect or hijack mid-scroll / fling.
                            parent?.requestDisallowInterceptTouchEvent(true)
                            gestureCanReachParent = false
                        } else {
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Safety guard if APZ detail call throws on transient state
        }
    }

    override fun onAttachedToWindow() {
        // Do not delay or suppress GeckoView's normal attach lifecycle. The parent
        // BrowserActivity now waits for WindowInsets before adding this view, while
        // this guard protects against OEM/Android 16 transient attach failures.
        try {
            super.onAttachedToWindow()
        } catch (e: NullPointerException) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView onAttachedToWindow NPE: ${e.message}")
        } catch (t: Throwable) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView onAttachedToWindow error: ${t.message}")
        }
    }

    override fun gatherTransparentRegion(region: Region?): Boolean {
        // Android 16/OEM WindowInsets can briefly be unavailable while ViewRootImpl
        // performs a traversal. GeckoView's compositor path may dereference that
        // missing insets object. Never let a decorative transparent-region pass
        // crash the UI thread or prevent the rest of the browser from rendering.
        return try {
            super.gatherTransparentRegion(region)
        } catch (e: NullPointerException) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView gatherTransparentRegion NPE: ${e.message}")
            false
        } catch (t: Throwable) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView gatherTransparentRegion error: ${t.message}")
            false
        }
    }

    override fun setSystemGestureExclusionRects(rects: MutableList<android.graphics.Rect>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Keep system gesture edges free for Android back gesture
                super.setSystemGestureExclusionRects(java.util.Collections.emptyList())
            } catch (_: Throwable) {}
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (direction < 0) {
            // GeckoView's internal View.canScrollVertically(-1) always returns true
            // by default because it delegates scrolling to its internal compositor surface.
            // Check parent PetalGeckoView's compositor scroll position if attached.
            val parentView = parent
            if (parentView is PetalGeckoView) {
                return !parentView.isPageAtTop()
            }
            return !canOverscrollTop()
        }
        return super.canScrollVertically(direction)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(ic, true) {
            override fun performEditorAction(editorAction: Int): Boolean {
                if (editorAction == EditorInfo.IME_ACTION_GO ||
                    editorAction == EditorInfo.IME_ACTION_SEARCH ||
                    editorAction == EditorInfo.IME_ACTION_SEND ||
                    editorAction == EditorInfo.IME_ACTION_DONE ||
                    editorAction == EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                    val handledDown = sendKeyEvent(down)
                    val handledUp = sendKeyEvent(up)
                    if (handledDown || handledUp) {
                        return true
                    }
                }
                return super.performEditorAction(editorAction)
            }
        }
    }
}
