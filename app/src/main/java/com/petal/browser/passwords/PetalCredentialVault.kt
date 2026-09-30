package com.petal.browser.passwords

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * PetalCredentialVault
 *
 * Local-only, encrypted password manager storage utilizing Android Keystore
 * (AES-256 GCM) with no external server sync or telemetry.
 */
object PetalCredentialVault {

    private const val TAG = "PetalCredentialVault"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "PetalVaultKey_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12 // 12 bytes recommended for GCM
    private const val VAULT_FILE_NAME = "petal_vault.enc"

    private val gson = Gson()
    private val credentials = mutableListOf<PetalCredential>()
    private var isInitialized = false
    private var vaultFile: File? = null

    @Synchronized
    fun init(context: Context) {
        if (isInitialized && vaultFile != null) return
        val appContext = context.applicationContext
        vaultFile = File(appContext.filesDir, VAULT_FILE_NAME)
        ensureKeyExists()
        loadVault()
        isInitialized = true
        Log.i(TAG, "Vault initialized with ${credentials.size} credentials.")
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.i(TAG, "Generated new hardware-backed AES-256 GCM master key.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure Keystore key", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    @Synchronized
    private fun loadVault() {
        val file = vaultFile ?: return
        if (!file.exists() || file.length() == 0L) {
            credentials.clear()
            return
        }

        try {
            FileInputStream(file).use { fis ->
                val iv = ByteArray(GCM_IV_LENGTH)
                val bytesRead = fis.read(iv)
                if (bytesRead < GCM_IV_LENGTH) {
                    Log.w(TAG, "Corrupt vault file, IV too short.")
                    credentials.clear()
                    return
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

                CipherInputStream(fis, cipher).use { cis ->
                    val json = cis.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val type = object : TypeToken<List<PetalCredential>>() {}.type
                    val list: List<PetalCredential>? = gson.fromJson(json, type)
                    credentials.clear()
                    if (list != null) {
                        credentials.addAll(list)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt and load credentials vault", e)
        }
    }

    @Synchronized
    private fun persistVault() {
        val file = vaultFile ?: return
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv

            FileOutputStream(file).use { fos ->
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    val json = gson.toJson(credentials)
                    cos.write(json.toByteArray(Charsets.UTF_8))
                    cos.flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and persist credentials vault", e)
        }
    }

    @Synchronized
    fun getAll(): List<PetalCredential> {
        return ArrayList(credentials)
    }

    @Synchronized
    fun findByDomain(domain: String): List<PetalCredential> {
        val target = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
        return credentials.filter {
            val d = it.domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
            d.equals(target, ignoreCase = true) || d.endsWith(".$target") || target.endsWith(".$d")
        }
    }

    @Synchronized
    fun findById(id: String): PetalCredential? {
        return credentials.firstOrNull { it.id == id }
    }

    @Synchronized
    fun save(credential: PetalCredential) {
        val index = credentials.indexOfFirst { it.id == credential.id }
        if (index >= 0) {
            credentials[index] = credential
        } else {
            // Check if exact same domain and username already exists, update if so
            val existingIndex = credentials.indexOfFirst {
                it.domain.equals(credential.domain, ignoreCase = true) &&
                it.username.equals(credential.username, ignoreCase = true)
            }
            if (existingIndex >= 0) {
                credentials[existingIndex] = credential
            } else {
                credentials.add(0, credential)
            }
        }
        persistVault()
    }

    @Synchronized
    fun delete(id: String) {
        val removed = credentials.removeAll { it.id == id }
        if (removed) {
            persistVault()
        }
    }

    @Synchronized
    fun toggleFavorite(id: String) {
        val index = credentials.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = credentials[index]
            credentials[index] = current.copy(isFavorite = !current.isFavorite)
            persistVault()
        }
    }

    @Synchronized
    fun clear() {
        credentials.clear()
        vaultFile?.delete()
    }

    @Synchronized
    fun exportToJson(): String {
        return gson.toJson(credentials)
    }

    private const val ENCRYPTED_BACKUP_MAGIC = "PETAL_ENC_VAULT_V1:"

    /**
     * Exports credentials encrypted with hardware-backed AES-256 GCM key so it can ONLY be restored by Petal.
     * Returns a string prefixed with PETAL_ENC_VAULT_V1: followed by Base64(IV + Ciphertext).
     */
    @Synchronized
    fun exportEncrypted(): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val jsonBytes = gson.toJson(credentials).toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(jsonBytes)
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return ENCRYPTED_BACKUP_MAGIC + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Checks if a content string is an encrypted Petal backup.
     */
    fun isEncryptedBackup(content: String): Boolean {
        return content.trim().startsWith(ENCRYPTED_BACKUP_MAGIC)
    }

    /**
     * Imports credentials from an encrypted Petal backup.
     */
    @Synchronized
    fun importEncrypted(content: String): Int {
        return try {
            importEncryptedOrThrow(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt and import encrypted backup", e)
            0
        }
    }

    /** Imports an encrypted backup and throws when the file cannot be decrypted or parsed. */
    @Synchronized
    fun importEncryptedOrThrow(content: String): Int {
        val trimmed = content.trim()
        require(trimmed.startsWith(ENCRYPTED_BACKUP_MAGIC)) {
            "This is not a valid encrypted Petal backup."
        }

        val combined = try {
            Base64.decode(trimmed.removePrefix(ENCRYPTED_BACKUP_MAGIC), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("The encrypted Petal backup is damaged.", e)
        }
        require(combined.size > GCM_IV_LENGTH) { "The encrypted Petal backup is incomplete." }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decryptedBytes = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalStateException(
                "This .petal backup was encrypted by a different Petal installation or is damaged.",
                e
            )
        }
        return importFromJson(String(decryptedBytes, Charsets.UTF_8))
    }

    @Synchronized
    fun importFromJson(json: String): Int {
        return try {
            val type = object : TypeToken<List<PetalCredential>>() {}.type
            val list: List<PetalCredential>? = gson.fromJson(json, type)
            if (list.isNullOrEmpty()) return 0

            var count = 0
            for (cred in list) {
                val exists = credentials.any {
                    it.domain.equals(cred.domain, ignoreCase = true) &&
                    it.username.equals(cred.username, ignoreCase = true)
                }
                if (!exists) {
                    credentials.add(cred)
                    count++
                }
            }
            if (count > 0) {
                persistVault()
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse and import backup JSON", e)
            0
        }
    }
}
