package com.petal.browser.passwords

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
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
import com.petal.browser.activity.BrowserActivity

/**
 * PetalAutofillService
 *
 * Petal's own system AutofillService.
 *  - Shows saved logins in the keyboard's top bar (inline suggestions, Android 11+),
 *    and as a normal dropdown on older Android.
 *  - Never puts a password in the response directly. Every suggestion needs
 *    fingerprint / screen lock first (see PetalAutofillAuthActivity).
 *  - Only offers a login when the website (or app) matches. No domain = no suggestions.
 *  - Offers "Save password?" even when nothing is saved yet for the site.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PetalAutofillService : AutofillService() {

    companion object {
        private const val TAG = "PetalAutofillService"
        private const val MAX_SUGGESTIONS = 4

        /** Same shape the system dropdown uses. Also reused by the auth activity. */
        fun buildPresentation(packageName: String, title: String, subtitle: String): RemoteViews {
            return RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                setTextViewText(android.R.id.text1, title)
                setTextViewText(android.R.id.text2, subtitle)
            }
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

        val parsed = ParsedForm()
        parsed.parse(structure)

        val usernameId = parsed.usernameId
        val passwordId = parsed.passwordId
        if (usernameId == null && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        val pkg = structure.activityComponent?.packageName
        val domain = parsed.domain
        Log.d(TAG, "Fill request: domain=${domain ?: "none"} pkg=$pkg user=${usernameId != null} pass=${passwordId != null}")

        val matches = credentialsFor(domain, pkg)
        val response = FillResponse.Builder()

        matches.forEachIndexed { index, cred ->
            val title = cred.username.ifBlank { cred.domain }
            val subtitle = "Petal • ${cred.domain}"

            val authIntent = Intent(this, PetalAutofillAuthActivity::class.java).apply {
                putExtra(PetalAutofillAuthActivity.EXTRA_CREDENTIAL_ID, cred.id)
                if (usernameId != null) putExtra(PetalAutofillAuthActivity.EXTRA_USERNAME_ID, usernameId)
                if (passwordId != null) putExtra(PetalAutofillAuthActivity.EXTRA_PASSWORD_ID, passwordId)
            }
            // The system adds its own extras to this intent, so it has to be mutable.
            val pending = PendingIntent.getActivity(
                this,
                cred.id.hashCode(),
                authIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val dataset = Dataset.Builder(buildPresentation(packageName, title, subtitle))
            // null value = "locked". The real value only comes after the user unlocks.
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

        // Save prompt is set even when there are zero matches. Otherwise new sites
        // can never be saved.
        if (passwordId != null) {
            val type = if (usernameId != null) {
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
            } else {
                SaveInfo.SAVE_DATA_TYPE_PASSWORD
            }
            val saveInfo = SaveInfo.Builder(type, arrayOf(passwordId)).apply {
                if (usernameId != null) setOptionalIds(arrayOf(usernameId))
                setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            }.build()
            response.setSaveInfo(saveInfo)
        }

        // A response with no datasets and no save info is invalid.
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
        // Keyboard must support the v1 inline style, otherwise skip inline for this chip.
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null

        val attribution = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BrowserActivity::class.java),
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
            !domain.isNullOrBlank() -> all.filter { domainMatches(it.domain, domain) }
            !pkg.isNullOrBlank() -> all.filter { it.domain.equals("app://$pkg", ignoreCase = true) }
            // No idea where we are. Offer nothing instead of guessing.
            else -> emptyList()
        }
        return matched
            .sortedWith(compareByDescending<PetalCredential> { it.isFavorite }.thenByDescending { it.updatedAt })
            .take(MAX_SUGGESTIONS)
    }

    /**
     * saved "example.com" matches page "example.com" and "login.example.com".
     * saved "login.example.com" does NOT match page "example.com".
     */
    private fun domainMatches(saved: String, page: String): Boolean {
        val s = normalizeDomain(saved)
        val p = normalizeDomain(page)
        if (s.isEmpty() || p.isEmpty()) return false
        return p == s || p.endsWith(".$s")
    }

    private fun normalizeDomain(raw: String): String {
        return raw.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
    }

    // ---------------------------------------------------------------------------------
    // SAVE
    // ---------------------------------------------------------------------------------

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess()
            return
        }

        PetalCredentialVault.init(applicationContext)

        val parsed = ParsedForm()
        parsed.parse(structure)

        val password = parsed.passwordValue.orEmpty()
        val username = parsed.usernameValue.orEmpty()
        if (password.isBlank()) {
            callback.onSuccess()
            return
        }

        val pkg = structure.activityComponent?.packageName
        val saveDomain = if (!parsed.domain.isNullOrBlank()) {
            normalizeDomain(parsed.domain!!)
        } else if (!pkg.isNullOrBlank()) {
            "app://$pkg"
        } else {
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
                Log.i(TAG, "Saved new login for $saveDomain")
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

    /** Walks the screen structure and finds the username field, password field and website. */
    private class ParsedForm {
        var domain: String? = null
        var scheme: String? = null
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue: String? = null
        var passwordValue: String? = null

        private var explicitUsernameId: AutofillId? = null
        private var explicitUsernameValue: String? = null
        private var lastTextId: AutofillId? = null
        private var lastTextValue: String? = null
        private var textBeforePasswordId: AutofillId? = null
        private var textBeforePasswordValue: String? = null

        fun parse(structure: AssistStructure) {
            for (i in 0 until structure.windowNodeCount) {
                traverse(structure.getWindowNodeAt(i).rootViewNode)
            }

            if (passwordId != null) {
                // Prefer a field clearly marked as username/email. Otherwise use the
                // text field right before the password field.
                usernameId = explicitUsernameId ?: textBeforePasswordId
                usernameValue = if (explicitUsernameId != null) explicitUsernameValue else textBeforePasswordValue
            } else {
                // Two-step login: only the username/email page is on screen.
                usernameId = explicitUsernameId
                usernameValue = explicitUsernameValue
            }

            // Do not fill into plain http pages (except local testing).
            if (scheme.equals("http", ignoreCase = true) && domain != "localhost") {
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
                    node.visibility == View.VISIBLE

            if (isTextField) {
                val value = node.autofillValue?.let { if (it.isText) it.textValue.toString() else null }
                    ?: node.text?.toString()

                if (isPasswordField(node)) {
                    if (passwordId == null) {
                        passwordId = id
                        passwordValue = value
                        textBeforePasswordId = lastTextId
                        textBeforePasswordValue = lastTextValue
                    }
                } else {
                    if (explicitUsernameId == null && isUsernameField(node)) {
                        explicitUsernameId = id
                        explicitUsernameValue = value
                    }
                    lastTextId = id
                    lastTextValue = value
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

        private fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
            val hints = node.autofillHints ?: emptyArray()
            if (hints.any {
                    it.equals(View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                            it.equals(View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true)
                }) return true

            val type = htmlAttr(node, "type")?.lowercase()
            if (type == "email") return true
            val autocomplete = htmlAttr(node, "autocomplete")?.lowercase().orEmpty()
            if (autocomplete.contains("username") || autocomplete.contains("email")) return true

            val names = listOf(node.idEntry, node.hint, htmlAttr(node, "name"), htmlAttr(node, "id"))
                .joinToString(" ") { it?.lowercase().orEmpty() }
            return names.contains("user") || names.contains("email") ||
                    names.contains("login") || names.contains("identifier")
        }
    }
}
