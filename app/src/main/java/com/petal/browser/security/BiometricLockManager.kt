package com.petal.browser.security

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

object BiometricLockManager {

    const val KEY_BIOMETRIC_LOCK = "sp_biometric_lock"

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
         activity: AppCompatActivity,
         title: String = "Petal Browser App Lock",
         subtitle: String = "Confirm fingerprint to continue",
         onSuccess: Runnable,
         onError: java.util.function.Consumer<String>
     ) {
         val executor: Executor = ContextCompat.getMainExecutor(activity)

         val callback = object : BiometricPrompt.AuthenticationCallback() {
             override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                 super.onAuthenticationSucceeded(result)
                 onSuccess.run()
             }

             override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                 super.onAuthenticationError(errorCode, errString)
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
             biometricPrompt.authenticate(promptInfoBuilder.build())
         } catch (e: Exception) {
             e.printStackTrace()
             onError.accept(e.message ?: "Failed to launch biometric authentication")
         }
     }
}
