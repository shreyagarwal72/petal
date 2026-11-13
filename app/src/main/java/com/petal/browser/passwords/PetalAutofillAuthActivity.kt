package com.petal.browser.passwords

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
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
 * Authenticates with fingerprint, face, or device screen lock (PIN/pattern).
 * Only after verification does the credential get delivered to the system.
 */
class PetalAutofillAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CREDENTIAL_ID = "petal_autofill_credential_id"
        const val EXTRA_USERNAME_ID = "petal_autofill_username_id"
        const val EXTRA_PASSWORD_ID = "petal_autofill_password_id"
    }

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

        // Avoid showing duplicate biometric prompts on configuration changes or recreation
        if (savedInstanceState != null) {
            return
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify it's you")
            .setSubtitle("Petal needs your fingerprint or PIN to fill this password")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
            if (canAuth != BiometricManager.BIOMETRIC_SUCCESS && !isDeviceSecure) {
                Toast.makeText(this, "Add a screen lock to use Petal Autofill securely", Toast.LENGTH_LONG).show()
                cancel()
                return
            }
            promptInfoBuilder.setAllowedAuthenticators(authenticators)
        } else {
            // API 28-29: BIOMETRIC_WEAK or DEVICE_CREDENTIAL is not supported by setAllowedAuthenticators
            if (!isDeviceSecure) {
                val canBio = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                if (canBio != BiometricManager.BIOMETRIC_SUCCESS) {
                    Toast.makeText(this, "Add a screen lock to use Petal Autofill securely", Toast.LENGTH_LONG).show()
                    cancel()
                    return
                }
            }
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val promptInfo = promptInfoBuilder.build()

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
                    // Retry allowed
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

        val friendlyTarget = PetalAutofillService.cleanDisplayLabel(applicationContext, cred.domain)
        val presentation = PetalAutofillService.buildPresentation(
            packageName,
            cred.username.ifBlank { friendlyTarget },
            friendlyTarget
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

        // Record usage without resetting updatedAt
        PetalCredentialVault.markUsed(credentialId)

        val result = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset.build())
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
