package com.petal.browser.passwords

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity

/**
 * PetalAutofillAuthActivity
 *
 * Opened by the system when the user taps a Petal suggestion (keyboard chip or dropdown).
 * Asks for fingerprint / screen lock. Only after that does the real username and password
 * go back to the system. Without this step, the login never leaves the vault.
 */
class PetalAutofillAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CREDENTIAL_ID = "petal_autofill_credential_id"
        const val EXTRA_USERNAME_ID = "petal_autofill_username_id"
        const val EXTRA_PASSWORD_ID = "petal_autofill_password_id"
    }

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            cancel()
            return
        }

        val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID)
        if (credentialId.isNullOrBlank()) {
            cancel()
            return
        }

        // No fingerprint and no screen lock on the phone = we refuse to hand out passwords.
        val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Set a screen lock to use Petal autofill", Toast.LENGTH_LONG).show()
            cancel()
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Petal")
            .setSubtitle("Confirm it's you to fill your password")
            .setAllowedAuthenticators(authenticators)
            .build()

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    deliver(credentialId)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    cancel()
                }

                override fun onAuthenticationFailed() {
                    // User can try again. The prompt stays open.
                }
            }
        )

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            cancel()
        }
    }

    private fun deliver(credentialId: String) {
        PetalCredentialVault.init(applicationContext)
        val cred = PetalCredentialVault.findById(credentialId)
        if (cred == null) {
            cancel()
            return
        }

        val usernameId = IntentCompat.getParcelableExtra(intent, EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordId = IntentCompat.getParcelableExtra(intent, EXTRA_PASSWORD_ID, AutofillId::class.java)

        val presentation = PetalAutofillService.buildPresentation(
            packageName,
            cred.username.ifBlank { cred.domain },
            "Petal • ${cred.domain}"
        )
        val dataset = Dataset.Builder(presentation)
        var fieldCount = 0

        if (usernameId != null && cred.username.isNotBlank()) {
            dataset.setValue(usernameId, AutofillValue.forText(cred.username))
            fieldCount++
        }
        if (passwordId != null && cred.password.isNotBlank()) {
            dataset.setValue(passwordId, AutofillValue.forText(cred.password))
            fieldCount++
        }

        if (fieldCount == 0) {
            cancel()
            return
        }

        val result = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset.build())
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
