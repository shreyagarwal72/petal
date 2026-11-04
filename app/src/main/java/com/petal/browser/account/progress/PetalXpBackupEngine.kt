package com.petal.browser.account.progress

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted Backup & Recovery Engine for Petal XP, Levels, Badges, and Stats.
 * Outputs and parses `.pt` files.
 *
 * File Structure (.pt format v1):
 * - Magic Header: 4 bytes "PTXP" (0x50, 0x54, 0x58, 0x50)
 * - Version: 1 byte (0x01)
 * - Salt: 16 bytes
 * - IV: 12 bytes
 * - Ciphertext: AES-256-GCM encrypted UTF-8 JSON payload with 128-bit authentication tag
 */
object PetalXpBackupEngine {
    private const val TAG = "PetalXpBackup"
    private val MAGIC = byteArrayOf(0x50, 0x54, 0x58, 0x50) // "PTXP"
    private const val VERSION: Byte = 0x01
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_LENGTH_BITS = 128

    fun createBackupJson(context: Context): JSONObject {
        val root = JSONObject()
        root.put("app", "com.petal.browser")
        root.put("type", "petal_xp_backup")
        root.put("timestamp", System.currentTimeMillis())
        root.put("xp", PetalProgressStore.xp)
        root.put("since", PetalProgressStore.since)

        // Counters
        val countersObj = JSONObject()
        PetalCounter.values().forEach { counter ->
            countersObj.put(counter.name, PetalProgressStore.counter(counter))
        }
        root.put("counters", countersObj)

        // Badges
        val badgesArray = JSONArray()
        PetalProgressStore.unlockedBadges.forEach { badgesArray.put(it) }
        root.put("unlocked_badges", badgesArray)

        // Showcase
        val showcaseArray = JSONArray()
        PetalProgressStore.showcase.forEach { showcaseArray.put(it) }
        root.put("showcase", showcaseArray)

        // Unlocked Features (persistent IDs)
        val unlocksArray = JSONArray()
        PetalUnlockable.values().forEach { unlockable ->
            if (PetalProgressStore.isUnlocked(context, unlockable)) {
                unlocksArray.put(unlockable.id)
            }
        }
        root.put("unlocked_features", unlocksArray)

        return root
    }

    fun exportEncryptedBackup(context: Context, uri: Uri, password: String):Result<Unit> {
        return runCatching {
            require(password.isNotBlank()) { "Password cannot be empty" }
            val json = createBackupJson(context)
            val plaintext = json.toString(2).toByteArray(StandardCharsets.UTF_8)

            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
            val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }

            val secretKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)

            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(MAGIC)
                output.write(byteArrayOf(VERSION))
                output.write(salt)
                output.write(iv)
                output.write(ciphertext)
                output.flush()
            } ?: throw IllegalStateException("Cannot open destination output stream")
            Log.d(TAG, "Successfully exported encrypted .pt backup (${ciphertext.size} bytes)")
        }
    }

    fun restoreEncryptedBackup(context: Context, uri: Uri, password: String): Result<JSONObject> {
        return runCatching {
            require(password.isNotBlank()) { "Password cannot be empty" }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot open backup file for reading")

            val minSize = MAGIC.size + 1 + SALT_SIZE + IV_SIZE + 16
            if (bytes.size < minSize) {
                throw IllegalArgumentException("Invalid backup file: file too short")
            }

            // Verify Magic
            for (i in MAGIC.indices) {
                if (bytes[i] != MAGIC[i]) {
                    throw IllegalArgumentException("Not a valid Petal XP backup file (.pt)")
                }
            }

            val version = bytes[4]
            if (version != VERSION) {
                throw IllegalArgumentException("Unsupported backup version: $version")
            }

            var offset = 5
            val salt = bytes.copyOfRange(offset, offset + SALT_SIZE)
            offset += SALT_SIZE
            val iv = bytes.copyOfRange(offset, offset + IV_SIZE)
            offset += IV_SIZE
            val ciphertext = bytes.copyOfRange(offset, bytes.size)

            val secretKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))

            val plaintext = try {
                cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                throw IllegalArgumentException("Incorrect password or corrupted .pt backup file", e)
            }

            val jsonStr = String(plaintext, StandardCharsets.UTF_8)
            val json = JSONObject(jsonStr)

            // Apply restored state into PetalProgressStore
            PetalProgressStore.restoreFromBackupJson(context, json)
            Log.d(TAG, "Successfully restored Petal XP backup from ${uri.path}")
            json
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
