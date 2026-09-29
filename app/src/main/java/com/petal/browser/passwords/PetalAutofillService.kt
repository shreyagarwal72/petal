package com.petal.browser.passwords

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.petal.browser.R

/**
 * PetalAutofillService
 *
 * System AutofillService enabling Android to fill and save passwords across apps
 * and websites using Petal's local hardware-encrypted vault.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PetalAutofillService : AutofillService() {

    companion object {
        private const val TAG = "PetalAutofillService"
    }

    override fun onConnected() {
        super.onConnected()
        PetalCredentialVault.init(applicationContext)
    }

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

        val parser = AutofillStructureParser()
        parser.parse(structure)

        val domain = parser.detectedDomain
        val usernameId = parser.usernameFieldId
        val passwordId = parser.passwordFieldId

        if (passwordId == null && usernameId == null) {
            callback.onSuccess(null)
            return
        }

        val credentials = if (!domain.isNullOrBlank()) {
            PetalCredentialVault.findByDomain(domain)
        } else {
            PetalCredentialVault.getAll().take(5)
        }

        if (credentials.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()

        for (cred in credentials) {
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                setTextViewText(android.R.id.text1, cred.username.ifBlank { cred.domain })
                setTextViewText(android.R.id.text2, "Petal • ${cred.domain}")
            }

            val datasetBuilder = Dataset.Builder(presentation)

            if (usernameId != null && cred.username.isNotBlank()) {
                datasetBuilder.setValue(usernameId, AutofillValue.forText(cred.username))
            }
            if (passwordId != null && cred.password.isNotBlank()) {
                datasetBuilder.setValue(passwordId, AutofillValue.forText(cred.password))
            }

            responseBuilder.addDataset(datasetBuilder.build())
        }

        // Set save info if user enters new credentials
        val saveIds = listOfNotNull(usernameId, passwordId).toTypedArray()
        if (saveIds.isNotEmpty()) {
            val saveInfo = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                saveIds
            ).build()
            responseBuilder.setSaveInfo(saveInfo)
        }

        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess()
            return
        }

        PetalCredentialVault.init(applicationContext)

        val parser = AutofillStructureParser()
        parser.parse(structure)

        val domain = parser.detectedDomain ?: "app://${request.fillContexts.lastOrNull()?.structure?.activityComponent?.packageName ?: "unknown"}"
        val username = parser.detectedUsername.orEmpty()
        val password = parser.detectedPassword.orEmpty()

        if (password.isNotBlank() || username.isNotBlank()) {
            val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
            val cred = PetalCredential(
                id = java.util.UUID.randomUUID().toString(),
                domain = cleanDomain,
                originUrl = "https://$cleanDomain",
                username = username,
                password = password
            )
            PetalCredentialVault.save(cred)
            Log.i(TAG, "Saved new credential from autofill for $cleanDomain")
        }

        callback.onSuccess()
    }

    /**
     * Traverses AssistStructure to extract input field IDs, hints, and domain.
     */
    private class AutofillStructureParser {
        var usernameFieldId: AutofillId? = null
        var passwordFieldId: AutofillId? = null
        var detectedDomain: String? = null
        var detectedUsername: String? = null
        var detectedPassword: String? = null

        fun parse(structure: AssistStructure) {
            val nodeCount = structure.windowNodeCount
            for (i in 0 until nodeCount) {
                val windowNode = structure.getWindowNodeAt(i)
                traverseNode(windowNode.rootViewNode)
            }
        }

        private fun traverseNode(node: AssistStructure.ViewNode?) {
            if (node == null) return

            // Check domain from web domain if available
            if (detectedDomain == null && !node.webDomain.isNullOrBlank()) {
                detectedDomain = node.webDomain
            }

            val hints = node.autofillHints ?: emptyArray()
            val text = node.text?.toString() ?: ""
            val idEntry = node.idEntry?.lowercase() ?: ""
            val hintText = node.hint?.lowercase() ?: ""

            val isPasswordType = node.inputType and android.text.InputType.TYPE_MASK_VARIATION == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    node.inputType and android.text.InputType.TYPE_MASK_VARIATION == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    hints.any { it.equals(android.view.View.AUTOFILL_HINT_PASSWORD, ignoreCase = true) } ||
                    idEntry.contains("password") || idEntry.contains("pass") || hintText.contains("password")

            val isUsernameType = hints.any {
                it.equals(android.view.View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                        it.equals(android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true)
            } || idEntry.contains("username") || idEntry.contains("email") || idEntry.contains("login") ||
                    hintText.contains("username") || hintText.contains("email")

            if (isPasswordType && passwordFieldId == null) {
                passwordFieldId = node.autofillId
                if (text.isNotBlank()) detectedPassword = text
            } else if (isUsernameType && usernameFieldId == null) {
                usernameFieldId = node.autofillId
                if (text.isNotBlank()) detectedUsername = text
            }

            for (i in 0 until node.childCount) {
                traverseNode(node.getChildAt(i))
            }
        }
    }
}
