package com.petal.browser.passwords

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.CharSequenceTransformation
import android.service.autofill.CustomDescription
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.petal.browser.R
import java.util.regex.Pattern

/**
 * PetalAutofillService
 *
 * Petal's system AutofillService.
 *  - Shows saved logins in the keyboard's top bar (inline suggestions, Android 11+),
 *    and as a Material 3 Expressive dropdown on older Android.
 *  - Never exposes passwords directly in the response. Every suggestion requires
 *    biometric / device credential unlock (see PetalAutofillAuthActivity).
 *  - Matches websites and apps via centralized eTLD+1 matcher.
 *  - Dynamic save dialog with CharSequenceTransformation to show the username.
 *  - Supports "Never for this site" via SaveInfo.NEGATIVE_BUTTON_STYLE_NEVER.
 *  - Fully handles compatibility mode requests.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PetalAutofillService : AutofillService() {

    companion object {
        private const val TAG = "PetalAutofillService"
        private const val MAX_SUGGESTIONS = 4

        private val KNOWN_APP_LOCK_PACKAGES = setOf(
            "com.domobile.applockwatcher",
            "com.domobile.applock",
            "com.sp.spapplock",
            "com.innox.applock",
            "com.alpha.applock",
            "com.koodun.lock",
            "com.morrison.applock",
            "com.gamemalt.applocker",
            "com.smartmobiletools.applock",
            "com.keepsafe.keepsafe",
            "com.calculator.vault.hidephoto",
            "com.thinkyeah.galleryvault"
        )

        /**
         * Material 3 Expressive suggestion chip.
         * Used for dropdown / autofill dropdown rows and reused by PetalAutofillAuthActivity.
         */
        fun buildPresentation(packageName: String, title: String, subtitle: String): RemoteViews {
            return try {
                RemoteViews(packageName, R.layout.petal_autofill_chip).apply {
                    setTextViewText(R.id.petal_autofill_username, title)
                    setTextViewText(R.id.petal_autofill_domain, subtitle)
                }
            } catch (_: Throwable) {
                // Fallback to AOSP default if layout is missing at runtime
                RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                    setTextViewText(android.R.id.text1, title)
                    setTextViewText(android.R.id.text2, subtitle)
                }
            }
        }

        /**
         * Converts an Android package name or raw app URI (e.g. "app://com.example")
         * to a user-friendly application display name if possible.
         * Web domains are cleaned of protocols and www, NEVER treated as package names.
         */
        fun cleanDisplayLabel(context: Context, rawTarget: String): String {
            if (rawTarget.isBlank()) return "Petal"
            if (rawTarget.startsWith("app://")) {
                val pkg = rawTarget.removePrefix("app://").trim()
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    if (label.isNotBlank()) return label
                } catch (_: Exception) {}
                if (pkg == context.packageName) return "Petal Browser"
                val readable = pkg.substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }
                if (readable.isNotBlank()) return readable
                return pkg
            }
            // For web domains: clean protocol, auth, www, port, paths
            return PetalCredentialVault.cleanDomain(rawTarget)
        }

        /**
         * Checks whether a package or activity structure corresponds to App Lock
         * or internal Petal security overlays, which must NEVER be offered autofill
         * or have passcodes saved.
         */
        fun isAppLockOrInternalSecurity(context: Context, pkg: String?, domain: String?, activityName: String? = null): Boolean {
            val ownPkg = context.packageName
            val p = pkg.orEmpty().lowercase()
            // Internal Petal activities that are NOT web browsing content or are auth screens
            if (p == ownPkg.lowercase()) {
                if (domain.isNullOrBlank()) return true
                val act = activityName.orEmpty().lowercase()
                if (act.contains("lock") || act.contains("security") || act.contains("auth") || act.contains("pin")) {
                    return true
                }
            }
            // Only check external packages against known App Lock packages (do not block innocent activities)
            if (KNOWN_APP_LOCK_PACKAGES.contains(p) || p.startsWith("com.domobile.applock")) {
                return true
            }
            return false
        }

        /**
         * Material 3 Expressive save-password dialog content for SaveInfo.CustomDescription.
         * Distinguishes "Save password?" vs "Update password?".
         * Uses CharSequenceTransformation to dynamically display the username from the form.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        fun buildSaveCustomDescription(
            packageName: String,
            usernameId: AutofillId?,
            domain: String,
            context: Context? = null,
            isUpdate: Boolean = false
        ): CustomDescription {
            val displayDomain = if (context != null) cleanDisplayLabel(context, domain) else domain
            val titleText = if (isUpdate) "Update password?" else "Save to Petal Vault?"
            val views = try {
                RemoteViews(packageName, R.layout.petal_autofill_save_dialog).apply {
                    setTextViewText(R.id.petal_save_title, titleText)
                    setTextViewText(R.id.petal_save_domain, displayDomain)
                    if (usernameId == null) {
                        setViewVisibility(R.id.petal_save_username, View.GONE)
                        setViewVisibility(R.id.petal_save_dot, View.GONE)
                    } else {
                        setTextViewText(R.id.petal_save_username, "Account")
                        setViewVisibility(R.id.petal_save_username, View.VISIBLE)
                        setViewVisibility(R.id.petal_save_dot, View.VISIBLE)
                    }
                }
            } catch (_: Throwable) {
                RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                    setTextViewText(android.R.id.text1, titleText)
                    setTextViewText(android.R.id.text2, displayDomain)
                }
            }

            val builder = CustomDescription.Builder(views)
            if (usernameId != null) {
                try {
                    val transformation = CharSequenceTransformation.Builder(
                        usernameId,
                        Pattern.compile("^(.+)$"),
                        "$1"
                    ).build()
                    builder.addChild(R.id.petal_save_username, transformation)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to attach CharSequenceTransformation", e)
                }
            }
            return builder.build()
        }
    }

    override fun onConnected() {
        super.onConnected()
        PetalCredentialVault.init(applicationContext)
    }

    // ---------------------------------------------------------------------------------
    // FILL
    // ---------------------------------------------------------------------------------

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        PetalCredentialVault.init(applicationContext)

        val isCompatMode = (request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST) != 0
        val parsed = ParsedForm(forSave = false)
        parsed.parse(structure)

        val usernameId = parsed.usernameId
        val passwordId = parsed.passwordId
        if (usernameId == null && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        val pkg = structure.activityComponent?.packageName
        val activityClass = structure.activityComponent?.className
        val domain = parsed.domain
        Log.d(TAG, "Fill request: domain=${domain ?: "none"} pkg=$pkg user=${usernameId != null} pass=${passwordId != null} compat=$isCompatMode")

        // Never offer autofill for App Lock or internal security screens
        if (isAppLockOrInternalSecurity(applicationContext, pkg, domain, activityClass)) {
            Log.d(TAG, "Fill request ignored: App Lock or internal security screen detected")
            callback.onSuccess(null)
            return
        }

        val matches = credentialsFor(domain, pkg)
        val response = FillResponse.Builder()

        matches.forEachIndexed { index, cred ->
            val appLabel = cleanDisplayLabel(applicationContext, cred.domain)
            val title = cred.username.ifBlank { appLabel }
            val subtitle = if (cred.username.isNotBlank()) appLabel else "Saved password"

            val authIntent = Intent(this, PetalAutofillAuthActivity::class.java).apply {
                putExtra(PetalAutofillAuthActivity.EXTRA_CREDENTIAL_ID, cred.id)
                if (usernameId != null) putExtra(PetalAutofillAuthActivity.EXTRA_USERNAME_ID, usernameId)
                if (passwordId != null) putExtra(PetalAutofillAuthActivity.EXTRA_PASSWORD_ID, passwordId)
            }
            val pending = PendingIntent.getActivity(
                this,
                cred.id.hashCode(),
                authIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val presBuilder = android.service.autofill.Presentations.Builder()
                    .setMenuPresentation(buildPresentation(packageName, title, subtitle))
                buildInlinePresentation(request, index, title, subtitle)?.let {
                    presBuilder.setInlinePresentation(it)
                }
                val presentations = presBuilder.build()
                val datasetBuilder = Dataset.Builder()
                if (usernameId != null) {
                    val field = android.service.autofill.Field.Builder()
                        .setPresentations(presentations)
                        .build()
                    datasetBuilder.setField(usernameId, field)
                }
                if (passwordId != null) {
                    val field = android.service.autofill.Field.Builder()
                        .setPresentations(presentations)
                        .build()
                    datasetBuilder.setField(passwordId, field)
                }
                datasetBuilder.setAuthentication(pending.intentSender)
                response.addDataset(datasetBuilder.build())
            } else {
                val dataset = Dataset.Builder(buildPresentation(packageName, title, subtitle))
                if (usernameId != null) dataset.setValue(usernameId, null)
                if (passwordId != null) dataset.setValue(passwordId, null)
                dataset.setAuthentication(pending.intentSender)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    buildInlinePresentation(request, index, title, subtitle)?.let {
                        dataset.setInlinePresentation(it)
                    }
                }
                response.addDataset(dataset.build())
            }
        }

        // Save prompt is set even when there are zero matches, so new sites can be saved.
        if (passwordId != null) {
            val type = if (usernameId != null) {
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
            } else {
                SaveInfo.SAVE_DATA_TYPE_PASSWORD
            }
            val saveInfoBuilder = SaveInfo.Builder(type, arrayOf(passwordId)).apply {
                if (usernameId != null) setOptionalIds(arrayOf(usernameId))
                setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            }

            val targetDomain = if (!domain.isNullOrBlank()) {
                PetalCredentialVault.cleanDomain(domain)
            } else if (!pkg.isNullOrBlank()) {
                "app://$pkg"
            } else ""

            if (targetDomain.isNotBlank()) {
                val neverIntent = Intent(this, PetalAutofillActionReceiver::class.java).apply {
                    action = PetalAutofillActionReceiver.ACTION_NEVER_SAVE
                    putExtra(PetalAutofillActionReceiver.EXTRA_DOMAIN, targetDomain)
                }
                val neverPending = PendingIntent.getBroadcast(
                    this,
                    targetDomain.hashCode(),
                    neverIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                saveInfoBuilder.setNegativeAction(SaveInfo.NEGATIVE_BUTTON_STYLE_NEVER, neverPending.intentSender)
            }

            try {
                val isUpdate = matches.isNotEmpty()
                val customDesc = buildSaveCustomDescription(packageName, usernameId, targetDomain, applicationContext, isUpdate)
                saveInfoBuilder.setCustomDescription(customDesc)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to attach custom description", e)
            }
            response.setSaveInfo(saveInfoBuilder.build())
        }

        if (matches.isEmpty() && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        callback.onSuccess(response.build())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildInlinePresentation(
        request: FillRequest,
        index: Int,
        title: String,
        subtitle: String
    ): InlinePresentation? {
        val inlineRequest = request.inlineSuggestionsRequest ?: return null
        val specs = inlineRequest.inlinePresentationSpecs
        if (specs.isEmpty() || index >= inlineRequest.maxSuggestionCount) return null

        val spec = specs[minOf(index, specs.size - 1)]
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null

        val attribution = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PetalPasswordsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()

        return InlinePresentation(content.slice, spec, false)
    }

    // ---------------------------------------------------------------------------------
    // MATCHING
    // ---------------------------------------------------------------------------------

    private fun credentialsFor(domain: String?, pkg: String?): List<PetalCredential> {
        val all = PetalCredentialVault.getAll()
        val matched = when {
            !domain.isNullOrBlank() -> all.filter { PetalCredentialVault.domainMatches(it.domain, domain) }
            !pkg.isNullOrBlank() -> all.filter { it.domain.equals("app://$pkg", ignoreCase = true) }
            else -> emptyList()
        }
        return matched
            .sortedWith(compareByDescending<PetalCredential> { it.isFavorite }.thenByDescending { it.updatedAt })
            .take(MAX_SUGGESTIONS)
    }

    // ---------------------------------------------------------------------------------
    // SAVE
    // ---------------------------------------------------------------------------------

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        PetalCredentialVault.init(applicationContext)

        val parsedSave = ParsedForm(forSave = true)
        for (context in request.fillContexts.asReversed()) {
            parsedSave.parse(context.structure)
        }

        val firstStructure = request.fillContexts.lastOrNull()?.structure
        val pkg = firstStructure?.activityComponent?.packageName
        val activityClass = firstStructure?.activityComponent?.className
        val rawDomain = parsedSave.domain

        val password = parsedSave.passwordValue.orEmpty()
        val username = parsedSave.usernameValue.orEmpty()
        if (password.isBlank()) {
            Log.d(TAG, "onSaveRequest: password blank for domain=$rawDomain pkg=$pkg – skip saving")
            callback.onSuccess()
            return
        }

        if (isAppLockOrInternalSecurity(applicationContext, pkg, rawDomain, activityClass)) {
            Log.d(TAG, "onSaveRequest: App Lock or internal security screen detected – skip saving")
            callback.onSuccess()
            return
        }

        if (rawDomain != null && PetalCredentialVault.isNeverSave(applicationContext, rawDomain)) {
            Log.d(TAG, "onSaveRequest: Domain $rawDomain is in Never-Save list – skip saving")
            callback.onSuccess()
            return
        }

        val saveDomain = if (!rawDomain.isNullOrBlank()) {
            PetalCredentialVault.cleanDomain(rawDomain)
        } else if (!pkg.isNullOrBlank()) {
            "app://$pkg"
        } else {
            Log.w(TAG, "onSaveRequest: cannot determine domain or package for domain=$rawDomain pkg=$pkg – skip")
            callback.onSuccess()
            return
        }

        val existing = PetalCredentialVault.getAll().firstOrNull {
            it.domain.equals(saveDomain, ignoreCase = true) &&
                    it.username.equals(username, ignoreCase = true)
        }

        when {
            existing == null -> {
                PetalCredentialVault.save(
                    PetalCredential(
                        id = java.util.UUID.randomUUID().toString(),
                        domain = saveDomain,
                        originUrl = if (saveDomain.startsWith("app://")) saveDomain else "https://$saveDomain",
                        username = username,
                        password = password
                    )
                )
                Log.i(TAG, "Saved new login for $saveDomain (user=${username.take(3)}…)")
            }
            existing.password != password -> {
                PetalCredentialVault.save(
                    existing.copy(password = password, updatedAt = System.currentTimeMillis())
                )
                Log.i(TAG, "Updated password for $saveDomain")
            }
            else -> Log.d(TAG, "Login already saved for $saveDomain")
        }

        callback.onSuccess()
    }

    // ---------------------------------------------------------------------------------
    // FORM PARSER
    // ---------------------------------------------------------------------------------

    /**
     * Walks AssistStructure and locates username, password (handling signup / change-password forms
     * with confirmation or current passwords), and domain.
     */
    private class ParsedForm(private val forSave: Boolean = false) {
        var domain: String? = null
        var scheme: String? = null
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue: String? = null
        var passwordValue: String? = null

        private var explicitUsernameId: AutofillId? = null
        private var explicitUsernameValue: String? = null
        private var plausibleTextBeforePasswordId: AutofillId? = null
        private var plausibleTextBeforePasswordValue: String? = null
        private var lastPlausibleTextId: AutofillId? = null
        private var lastPlausibleTextValue: String? = null

        // Track multiple password fields (e.g. signup: password + confirm; change: current + new + confirm)
        private var newPasswordId: AutofillId? = null
        private var newPasswordValue: String? = null
        private var primaryPasswordId: AutofillId? = null
        private var primaryPasswordValue: String? = null
        private var confirmPasswordId: AutofillId? = null
        private var confirmPasswordValue: String? = null

        fun parse(structure: AssistStructure) {
            for (i in 0 until structure.windowNodeCount) {
                traverse(structure.getWindowNodeAt(i).rootViewNode)
            }

            // Decide on primary password
            passwordId = newPasswordId ?: primaryPasswordId
            passwordValue = if (newPasswordId != null && !newPasswordValue.isNullOrBlank()) {
                newPasswordValue
            } else if (!primaryPasswordValue.isNullOrBlank()) {
                primaryPasswordValue
            } else if (!confirmPasswordValue.isNullOrBlank()) {
                confirmPasswordValue
            } else null

            if (passwordId != null) {
                usernameId = explicitUsernameId ?: plausibleTextBeforePasswordId
                usernameValue = if (explicitUsernameId != null) explicitUsernameValue else plausibleTextBeforePasswordValue
            } else {
                usernameId = explicitUsernameId
                usernameValue = explicitUsernameValue
            }

            if (!forSave && scheme.equals("http", ignoreCase = true) && domain != "localhost") {
                usernameId = null
                passwordId = null
            }
        }

        private fun traverse(node: AssistStructure.ViewNode?) {
            if (node == null) return

            if (domain.isNullOrBlank() && !node.webDomain.isNullOrBlank()) {
                domain = node.webDomain
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) scheme = node.webScheme
            }

            val id = node.autofillId
            val isTextField = id != null &&
                    node.autofillType == View.AUTOFILL_TYPE_TEXT &&
                    (forSave || node.visibility == View.VISIBLE)

            if (isTextField) {
                val value = node.autofillValue?.let { if (it.isText) it.textValue.toString() else null }
                    ?: node.text?.toString()

                if (isPasswordField(node)) {
                    val isNewPass = isNewPasswordField(node)
                    val isConfirm = isConfirmPasswordField(node)

                    if (isNewPass) {
                        newPasswordId = id
                        if (!value.isNullOrBlank()) newPasswordValue = value
                    } else if (isConfirm) {
                        confirmPasswordId = id
                        if (!value.isNullOrBlank()) confirmPasswordValue = value
                    } else if (primaryPasswordId == null) {
                        primaryPasswordId = id
                        if (!value.isNullOrBlank()) primaryPasswordValue = value
                        plausibleTextBeforePasswordId = lastPlausibleTextId
                        plausibleTextBeforePasswordValue = lastPlausibleTextValue
                    } else {
                        // Second password encountered (confirmation)
                        confirmPasswordId = id
                        if (!value.isNullOrBlank()) confirmPasswordValue = value
                    }
                } else if (isUsernameField(node)) {
                    if (explicitUsernameId == null) {
                        explicitUsernameId = id
                        if (!value.isNullOrBlank()) explicitUsernameValue = value
                    } else if (forSave && !value.isNullOrBlank()) {
                        explicitUsernameValue = value
                    }
                    lastPlausibleTextId = id
                    if (!value.isNullOrBlank()) lastPlausibleTextValue = value
                } else if (isPlausibleTextCandidate(node)) {
                    lastPlausibleTextId = id
                    if (!value.isNullOrBlank()) lastPlausibleTextValue = value
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }

        private fun htmlAttr(node: AssistStructure.ViewNode, name: String): String? {
            return node.htmlInfo?.attributes?.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
        }

        private fun isPasswordField(node: AssistStructure.ViewNode): Boolean {
            val hints = node.autofillHints ?: emptyArray()
            if (hints.any { it.equals(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true) }) return true

            val variation = node.inputType and InputType.TYPE_MASK_VARIATION
            val isTextClass = (node.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
            if (isTextClass && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) return true

            if (htmlAttr(node, "type").equals("password", ignoreCase = true)) return true
            val autocomplete = htmlAttr(node, "autocomplete")?.lowercase().orEmpty()
            if (autocomplete.contains("password")) return true

            val names = listOf(node.idEntry, node.hint, htmlAttr(node, "name"), htmlAttr(node, "id"))
                .joinToString(" ") { it?.lowercase().orEmpty() }
            return names.contains("password") || names.contains("passwd") || names.contains("pwd")
        }

        private fun isNewPasswordField(node: AssistStructure.ViewNode): Boolean {
            val autocomplete = htmlAttr(node, "autocomplete")?.lowercase().orEmpty()
            if (autocomplete.contains("new-password")) return true
            val names = listOf(node.idEntry, node.hint, htmlAttr(node, "name"), htmlAttr(node, "id"))
                .joinToString(" ") { it?.lowercase().orEmpty() }
            return names.contains("newpassword") || names.contains("new_password") || names.contains("new-password")
        }

        private fun isConfirmPasswordField(node: AssistStructure.ViewNode): Boolean {
            val names = listOf(node.idEntry, node.hint, htmlAttr(node, "name"), htmlAttr(node, "id"))
                .joinToString(" ") { it?.lowercase().orEmpty() }
            return names.contains("confirm") || names.contains("retype") || names.contains("repeat")
        }

        private fun isExcludedField(node: AssistStructure.ViewNode): Boolean {
            val htmlType = htmlAttr(node, "type")?.lowercase()
            if (htmlType in setOf("search", "submit", "button", "hidden", "checkbox", "radio", "file", "image")) {
                return true
            }
            val raw = listOf(node.idEntry, node.hint, htmlAttr(node, "name"), htmlAttr(node, "id"), htmlAttr(node, "placeholder"))
                .joinToString(" ") { it?.lowercase().orEmpty() }
            val excluded = listOf("search", "captcha", "recaptcha", "otp", "token", "verification", "pin", "query", "find", "cvv", "cvc", "coupon")
            return excluded.any { raw.contains(it) }
        }

        private fun isPlausibleTextCandidate(node: AssistStructure.ViewNode): Boolean {
            if (isExcludedField(node)) return false
            val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
            if (inputClass != InputType.TYPE_CLASS_TEXT && inputClass != InputType.TYPE_CLASS_PHONE) return false
            val variation = node.inputType and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS) return false
            return true
        }

        private fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
            if (isExcludedField(node) || !isPlausibleTextCandidate(node)) return false

            val hints = node.autofillHints ?: emptyArray()
            if (hints.any {
                    it.equals(View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                    it.equals(View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true) ||
                    it.equals(View.AUTOFILL_HINT_PHONE_NUMBER, ignoreCase = true)
                }) return true

            val htmlType = htmlAttr(node, "type")?.lowercase()
            if (htmlType == "email") return true

            val autocomplete = htmlAttr(node, "autocomplete")?.lowercase().orEmpty()
            if (autocomplete.contains("username") || autocomplete.contains("email")) return true

            val variation = node.inputType and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME) return true

            // Require distinct token match rather than arbitrary substring
            val identifiers = listOf(node.idEntry, node.hint, htmlAttr(node, "name"), htmlAttr(node, "id"))
            for (id in identifiers) {
                if (id.isNullOrBlank()) continue
                val tokens = id.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
                if (tokens.any { it in setOf("username", "user", "email", "account", "login", "identifier", "signin") }) {
                    return true
                }
            }
            return false
        }
    }
}
