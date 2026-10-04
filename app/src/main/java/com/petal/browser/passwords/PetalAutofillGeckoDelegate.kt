package com.petal.browser.passwords

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * PetalAutofillGeckoDelegate
 *
 * Bridges Mozilla GeckoView's Autocomplete.StorageDelegate to Petal's local
 * AES-256 credential vault, enabling:
 *
 *  • onLoginFetch  – returns matching credentials when GeckoView loads a login form,
 *                    so Gecko can offer them in the browser's own suggestion UI.
 *  • onLoginSave   – called by Gecko after the user fills in and submits a form;
 *                    saves or updates the credential silently (Petal's own
 *                    "save?" snackbar is shown by the in-browser web extension
 *                    via the system autofill save prompt).
 *  • onLoginUsed   – updates the credential's lastUsed timestamp.
 *
 * Register with: GeckoRuntime.autocompleteStorageDelegate = PetalAutofillGeckoDelegate(ctx)
 */
class PetalAutofillGeckoDelegate(private val context: Context) : Autocomplete.StorageDelegate {

    companion object {
        private const val TAG = "PetalAutofillGeckoDelegate"
    }

    private fun ensureVault() = PetalCredentialVault.init(context)

    // ------------------------------------------------------------------
    // GeckoView → App: fetch matching logins for the current form domain
    // ------------------------------------------------------------------

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>>? {
        ensureVault()
        Log.d(TAG, "onLoginFetch: $domain")

        val norm = normalizeDomain(domain)
        val matches = PetalCredentialVault.getAll()
            .filter { domainMatches(it.domain, norm) }
            .map { cred ->
                Autocomplete.LoginEntry.Builder()
                    .origin("https://${normalizeDomain(cred.domain)}")
                    .username(cred.username)
                    .password(cred.password)
                    .guid(cred.id)
                    .build()
            }
            .toTypedArray()

        Log.d(TAG, "onLoginFetch: returning ${matches.size} credential(s) for $domain")
        return GeckoResult.fromValue(matches)
    }

    // ------------------------------------------------------------------
    // GeckoView → App: save a newly entered / updated login
    // ------------------------------------------------------------------

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        ensureVault()
        val username = login.username.orEmpty()
        val password = login.password.orEmpty()
        val origin   = login.origin.orEmpty()
        val saveDomain = normalizeDomain(origin.ifBlank { login.formActionOrigin.orEmpty() })

        if (password.isBlank() || saveDomain.isBlank()) {
            Log.d(TAG, "onLoginSave: blank password or domain – skip")
            return
        }

        val existing = PetalCredentialVault.getAll().firstOrNull {
            normalizeDomain(it.domain) == saveDomain &&
                    it.username.equals(username, ignoreCase = true)
        }

        when {
            existing == null -> {
                PetalCredentialVault.save(
                    PetalCredential(
                        id          = login.guid ?: java.util.UUID.randomUUID().toString(),
                        domain      = saveDomain,
                        originUrl   = origin.ifBlank { "https://$saveDomain" },
                        username    = username,
                        password    = password
                    )
                )
                Log.i(TAG, "onLoginSave: saved new login for $saveDomain")
            }
            existing.password != password -> {
                PetalCredentialVault.save(
                    existing.copy(password = password, updatedAt = System.currentTimeMillis())
                )
                Log.i(TAG, "onLoginSave: updated password for $saveDomain")
            }
            else -> Log.d(TAG, "onLoginSave: already up to date for $saveDomain")
        }
    }

    // ------------------------------------------------------------------
    // GeckoView → App: a credential was used (update last-used timestamp)
    // ------------------------------------------------------------------

    override fun onLoginUsed(login: Autocomplete.LoginEntry, useFields: Int) {
        ensureVault()
        val id = login.guid ?: return
        val existing = PetalCredentialVault.findById(id) ?: return
        PetalCredentialVault.save(existing.copy(updatedAt = System.currentTimeMillis()))
        Log.d(TAG, "onLoginUsed: updated last-used for ${existing.domain}")
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun normalizeDomain(raw: String): String =
        raw.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')

    private fun domainMatches(saved: String, page: String): Boolean {
        val s = normalizeDomain(saved)
        val p = normalizeDomain(page)
        if (s.isEmpty() || p.isEmpty()) return false
        return p == s || p.endsWith(".$s")
    }
}
