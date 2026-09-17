/*
 * SafeLockerManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Biometric Safe Locker Manager for Petal Browser.
 * Securely stores private files in isolated app storage protected by
 * hardware-backed Biometric authentication (Fingerprint, Face, Device PIN).
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.privacy

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class LockedFileItem(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val mimeType: String
)

class SafeLockerManager(private val context: Context) {

    companion object {
        private const val TAG = "SafeLockerManager"
        private const val LOCKER_DIR = "safe_locker"
    }

    private val lockerDir = File(context.filesDir, LOCKER_DIR).apply {
        if (!exists()) {
            mkdirs()
            try {
                // Drop .nomedia so system media scanners never index private locker assets
                File(this, ".nomedia").createNewFile()
            } catch (_: Exception) {}
        }
    }

    private val _lockedFiles = MutableStateFlow<List<LockedFileItem>>(emptyList())
    val lockedFiles: StateFlow<List<LockedFileItem>> = _lockedFiles.asStateFlow()

    init {
        refreshFiles()
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Safe Locker")
            .setSubtitle("Confirm your biometric identity or device credential")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                // User can retry
            }
        })

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "Authentication failed")
        }
    }

    fun refreshFiles() {
        val files = lockerDir.listFiles { f -> f.isFile && f.name != ".nomedia" } ?: emptyArray()
        val items = files.map { file ->
            val ext = file.extension.lowercase()
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            LockedFileItem(
                id = file.name,
                name = file.name,
                file = file,
                sizeBytes = file.length(),
                modifiedTime = file.lastModified(),
                mimeType = mime
            )
        }.sortedByDescending { it.modifiedTime }
        _lockedFiles.value = items
    }

    suspend fun importFile(uri: Uri, displayName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(lockerDir, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            refreshFiles()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file into Safe Locker: ${e.message}")
            false
        }
    }

    suspend fun deleteFile(item: LockedFileItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = item.file.delete()
            refreshFiles()
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file from Safe Locker: ${e.message}")
            false
        }
    }
}
