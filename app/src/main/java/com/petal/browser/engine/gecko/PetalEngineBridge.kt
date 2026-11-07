package com.petal.browser.engine.gecko

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.R
import com.petal.browser.browser.BrowserController
import com.petal.browser.passwords.PetalCredential
import com.petal.browser.passwords.PetalCredentialVault
import com.petal.browser.ui.components.PetalChoiceOption
import com.petal.browser.ui.components.PetalExpressivePromptBridge
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.SlowScriptResponse
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * PetalEngineBridge
 * ─────────────────────────────────────────────────────────────────────────────
 * Centralizes browser engine delegates, download routing, intent dispatching,
 * profile resolution, session reflection extraction, and user agent helpers.
 */
object PetalEngineBridge {
    private const val TAG = "PetalEngineBridge"

    @Volatile
    private var globalBrowserControllerRef: WeakReference<BrowserController>? = null

    @JvmStatic
    fun getBrowserController(): BrowserController? = globalBrowserControllerRef?.get()

    @JvmStatic
    fun setBrowserController(controller: BrowserController?) {
        globalBrowserControllerRef = if (controller != null) WeakReference(controller) else null
    }

    /**
     * GeckoEngineSession.geckoSession is `internal` to Mozilla components, so Kotlin
     * does not expose it directly. Extract reflectively.
     */
    private fun extractGeckoSession(
        engineSession: mozilla.components.concept.engine.EngineSession?
    ): GeckoSession? {
        if (engineSession !is mozilla.components.browser.engine.gecko.GeckoEngineSession) return null
        return try {
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
            Log.w(TAG, "Could not read GeckoEngineSession.geckoSession: ${t.message}")
            null
        }
    }

    @JvmStatic
    fun getEngineGeckoSession(
        engineSession: mozilla.components.concept.engine.EngineSession?
    ): GeckoSession? = extractGeckoSession(engineSession)

    @JvmStatic
    @JvmOverloads
    fun getProfile(context: Context? = null): String {
        val ctx = context ?: com.petal.browser.PetalApplication.instance
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getString("sp_current_profile", "profileStandard") ?: "profileStandard"
    }

    @JvmStatic
    fun getDerivedDesktopUserAgent(context: Context): String {
        return "Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0"
    }

    @JvmStatic
    fun handleExternalScheme(activity: Activity, url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        if (url.startsWith("intent:", ignoreCase = true)) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent != null) {
                    val pm = activity.packageManager
                    if (pm != null && intent.resolveActivity(pm) != null) {
                        activity.startActivity(intent)
                        return true
                    }
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrBlank()) {
                        if (activity is com.petal.browser.activity.BrowserActivity) {
                            activity.showAlbum(activity.getCurrentAlbumController(), fallbackUrl)
                        }
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error handling intent scheme: $url", e)
            }
            return true
        }
        try {
            val parsedUri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, parsedUri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pm = activity.packageManager
            if (pm != null && intent.resolveActivity(pm) != null) {
                activity.startActivity(intent)
                return true
            }
        } catch (_: Exception) {}
        return true
    }

    @JvmStatic
    fun handleExternalResponse(
        activity: Activity,
        response: WebResponse,
        isPrivate: Boolean,
        referrer: String?
    ) {
        val responseUrl = response.uri
        val parsed = runCatching { Uri.parse(responseUrl) }.getOrNull()

        val isXpiUrl = parsed?.path?.endsWith(".xpi", ignoreCase = true) == true ||
            response.headers["content-type"]?.contains("application/x-xpinstall", ignoreCase = true) == true
        if (isXpiUrl) {
            activity.runOnUiThread {
                com.petal.browser.extensions.PetalExtensionManager.install(responseUrl) { success, message ->
                    PetalToast.show(activity, message ?: if (success) "Extension installed" else "Extension installation failed")
                }
            }
            return
        }

        val isUserScript = parsed?.path?.endsWith(".user.js", ignoreCase = true) == true ||
            response.headers["content-type"]?.contains("application/x-javascript", ignoreCase = true) == true && responseUrl.contains("userscript")
        if (isUserScript) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val body = response.body?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    if (body != null) {
                        val meta = com.petal.browser.compose.store.PetalUserScriptEngine.installScript(activity, body)
                        withContext(Dispatchers.Main) {
                            PetalToast.show(activity, "UserScript installed: ${meta?.name ?: "Unknown"}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error installing userscript", e)
                }
            }
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
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val contentValues = android.content.ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                    }
                                    val resolver = activity.contentResolver
                                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                    if (uri != null) {
                                        resolver.openOutputStream(uri)?.use { output ->
                                            bodyStream.use { input -> input.copyTo(output) }
                                        }
                                    }
                                } else {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val targetFile = File(downloadsDir, finalName)
                                    bodyStream.use { input ->
                                        FileOutputStream(targetFile).use { output ->
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
                                withContext(Dispatchers.Main) {
                                    PetalToast.show(activity, "Download completed: $finalName")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error downloading blob/data stream", e)
                                withContext(Dispatchers.Main) {
                                    PetalToast.show(activity, "Download failed")
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

        activity.runOnUiThread {
            com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                activity, responseUrl, contentDisposition, mimeType, contentLength
            ) { confirmedName ->
                val finalName = confirmedName.ifBlank { fileName }
                com.petal.browser.download.PetalDownloadBridge.enqueueDownload(
                    activity, responseUrl, finalName, mimeType, isPrivate
                )
            }
        }
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

    interface OnScrollChangeListener {
        fun onScrollDown()
        fun onScrollUp()
        fun onScrollPositionChanged(scrollY: Int, contentHeight: Int) {}
    }
}
