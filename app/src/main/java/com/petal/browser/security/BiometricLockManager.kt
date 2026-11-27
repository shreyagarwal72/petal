package com.petal.browser.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.concurrent.Executor

object BiometricLockManager {

    const val KEY_BIOMETRIC_LOCK = "sp_biometric_lock"
    const val PASSCODE_HASH_PREFIX = "sha256$"
    private const val PASSCODE_HASH_SALT = "petal.app_lock.v1:"

    /** True while an AndroidX BiometricPrompt / credential activity is in flight. */
    @Volatile
    @JvmField
    var isPromptActive: Boolean = false

    @JvmStatic
    fun isHashedPasscode(stored: String?): Boolean =
        !stored.isNullOrBlank() && stored.startsWith(PASSCODE_HASH_PREFIX)

    @JvmStatic
    fun hashPasscode(passcode: String): String {
        val trimmed = passcode.trim()
        if (trimmed.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((PASSCODE_HASH_SALT + trimmed).toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            hex.append(String.format("%02x", b))
        }
        return PASSCODE_HASH_PREFIX + hex.toString()
    }

    @JvmStatic
    fun matchesPasscode(input: String?, stored: String?): Boolean {
        val candidate = input?.trim().orEmpty()
        val expected = stored?.trim().orEmpty()
        if (candidate.isEmpty() || expected.isEmpty()) return false
        return if (expected.startsWith(PASSCODE_HASH_PREFIX)) {
            MessageDigest.isEqual(
                hashPasscode(candidate).toByteArray(Charsets.UTF_8),
                expected.toByteArray(Charsets.UTF_8)
            )
        } else {
            MessageDigest.isEqual(
                candidate.toByteArray(Charsets.UTF_8),
                expected.toByteArray(Charsets.UTF_8)
            )
        }
    }

    @JvmStatic
    fun upgradeLegacyPasscodeIfNeeded(sp: SharedPreferences, plainInput: String) {
        val current = sp.getString("sp_app_lock_passcode", "") ?: ""
        if (current.isNotBlank() && !isHashedPasscode(current) && matchesPasscode(plainInput, current)) {
            sp.edit().putString("sp_app_lock_passcode", hashPasscode(plainInput)).apply()
        }
    }

    /**
     * Checks if biometric or device credential authentication is available on the device.
     */
     @JvmStatic
     fun canAuthenticate(context: Context): Boolean {
         val biometricManager = BiometricManager.from(context)
         val pureBiometrics = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
         val canPure = biometricManager.canAuthenticate(pureBiometrics) == BiometricManager.BIOMETRIC_SUCCESS
         if (canPure) return true

         val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
             BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
         } else {
             BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
         }
         return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
     }

     /**
      * Checks specifically if hardware biometric sensor (fingerprint/face) is enrolled and available.
      */
     @JvmStatic
     fun canAuthenticatePureBiometric(context: Context): Boolean {
         val biometricManager = BiometricManager.from(context)
         val pureBiometrics = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
         return biometricManager.canAuthenticate(pureBiometrics) == BiometricManager.BIOMETRIC_SUCCESS
     }

     /**
      * Triggers the AndroidX BiometricPrompt for app lock authentication.
      * Prioritizes pure biometric (fingerprint/face) so the biometric prompt dialog is actively presented.
      */
     @JvmStatic
     @JvmOverloads
     fun authenticate(
         activity: androidx.fragment.app.FragmentActivity,
         title: String = "Petal Browser App Lock",
         subtitle: String = "Confirm fingerprint to continue",
         onSuccess: Runnable,
         onError: java.util.function.Consumer<String>
     ) {
         val executor: Executor = ContextCompat.getMainExecutor(activity)

         val callback = object : BiometricPrompt.AuthenticationCallback() {
             override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                 super.onAuthenticationSucceeded(result)
                 isPromptActive = false
                 onSuccess.run()
             }

             override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                 super.onAuthenticationError(errorCode, errString)
                 isPromptActive = false
                 if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                     errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                 ) {
                     onError.accept(errString.toString())
                 } else {
                     onError.accept("Authentication cancelled")
                 }
             }

             override fun onAuthenticationFailed() {
                 super.onAuthenticationFailed()
             }
         }

         val biometricPrompt = BiometricPrompt(activity, executor, callback)
         val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
             .setTitle(title)
             .setSubtitle(subtitle)

         val biometricManager = BiometricManager.from(activity)
         val pureBiometrics = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
         val hasPureBiometrics = biometricManager.canAuthenticate(pureBiometrics) == BiometricManager.BIOMETRIC_SUCCESS

         if (hasPureBiometrics) {
             // Explicit pure biometric prompt with negative cancel button ensures fingerprint dialog pops up
             promptInfoBuilder.setAllowedAuthenticators(pureBiometrics)
             promptInfoBuilder.setNegativeButtonText("Use Passcode")
         } else {
             // Fallback to device PIN/pattern/password
             val credentialAuthenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                 BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
             } else {
                 BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
             }
             promptInfoBuilder.setAllowedAuthenticators(credentialAuthenticators)
         }

         try {
             isPromptActive = true
             biometricPrompt.authenticate(promptInfoBuilder.build())
         } catch (e: Exception) {
             isPromptActive = false
             e.printStackTrace()
             onError.accept(e.message ?: "Failed to launch biometric authentication")
         }
     }
}
