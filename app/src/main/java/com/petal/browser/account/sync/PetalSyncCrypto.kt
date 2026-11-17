package com.petal.browser.account.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for the sync file. The passphrase never leaves the user's devices: the file in
 * the user's own storage is AES-256-GCM encrypted with a key derived from it (PBKDF2-HMAC-SHA256), so
 * whoever hosts that storage (Drive, Nextcloud, an SD card, ...) only ever sees ciphertext.
 *
 * File layout: MAGIC | salt (16) | iv (12) | ciphertext+tag.
 */
object PetalSyncCrypto {
    private val MAGIC = "PSYNC1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val PBKDF2_ITERATIONS = 210_000
    private const val GCM_TAG_BITS = 128

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    /** Cheap check that [blob] looks like a Petal sync file (right header and a plausible size). */
    fun isPetalFile(blob: ByteArray): Boolean =
        blob.size > MAGIC.size + SALT_LEN + IV_LEN + 16 && blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /**
     * @return the plaintext, or null when the passphrase is wrong or the data was tampered with.
     * @throws IllegalArgumentException when the bytes are not a Petal sync file at all.
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray? {
        require(blob.size > MAGIC.size + SALT_LEN + IV_LEN + 16 && blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Not a Petal sync file"
        }
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val iv = blob.copyOfRange(MAGIC.size + SALT_LEN, MAGIC.size + SALT_LEN + IV_LEN)
        val body = blob.copyOfRange(MAGIC.size + SALT_LEN + IV_LEN, blob.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(body)
        } catch (_: AEADBadTagException) {
            null
        } catch (_: javax.crypto.BadPaddingException) {
            null
        }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * Keeps the sync passphrase on this device only, encrypted with a non-exportable Android Keystore key.
 * It is never written to backups or to the sync folder.
 */
object PetalSyncVault {
    private const val PREFS = "petal_sync_vault"
    private const val KEY_BLOB = "passphrase_blob"
    private const val ALIAS = "PetalSyncPassphraseKey_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun has(c: Context): Boolean = prefs(c).contains(KEY_BLOB)

    fun save(c: Context, passphrase: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ct
        prefs(c).edit().putString(KEY_BLOB, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(c: Context): CharArray? {
        val stored = prefs(c).getString(KEY_BLOB, null) ?: return null
        return try {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, 12)
            val ct = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8).toCharArray()
        } catch (_: Exception) {
            null
        }
    }

    fun clear(c: Context) {
        prefs(c).edit().clear().apply()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (_: Exception) {}
    }
}
