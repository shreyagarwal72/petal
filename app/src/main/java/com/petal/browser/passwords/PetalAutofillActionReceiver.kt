package com.petal.browser.passwords

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered by Autofill Save dialog negative action
 * ("Never for this site"). Adds domain to Petal's never-save list.
 */
class PetalAutofillActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NEVER_SAVE = "com.petal.browser.passwords.ACTION_NEVER_SAVE"
        const val EXTRA_DOMAIN = "extra_domain"
        private const val TAG = "PetalAutofillReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action == ACTION_NEVER_SAVE) {
            val domain = intent.getStringExtra(EXTRA_DOMAIN)
            if (!domain.isNullOrBlank()) {
                Log.i(TAG, "Never-save requested via system autofill prompt: $domain")
                PetalCredentialVault.addNeverSaveDomain(context, domain)
            }
        }
    }
}
