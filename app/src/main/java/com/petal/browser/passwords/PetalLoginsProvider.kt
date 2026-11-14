package com.petal.browser.passwords

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * PetalLoginsProvider
 * ─────────────────────────────────────────────────────────────────────────
 * Single source of truth for browser credentials and passwords, backed by official
 * Mozilla Android Components [SyncableLoginsStorage] encrypted at rest via [SecureAbove22Preferences]
 * (lib-dataprotect).
 *
 * Implements a one-time, in-memory migration from the legacy encrypted vault ("petal_vault.enc"),
 * with strictly ZERO plaintext written to disk or logs.
 */
object PetalLoginsProvider {
    private const val TAG = "PetalLoginsProvider"
    private const val PREF_MIGRATED = "sp_credentials_migrated_to_syncable_v1"
    private const val LEGACY_VAULT_FILE = "petal_vault.enc"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val LEGACY_KEY_ALIAS = "PetalVaultKey_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    @Volatile
    private var loginsStorage: SyncableLoginsStorage? = null

    @Synchronized
    fun getLoginsStorage(context: Context): SyncableLoginsStorage {
        loginsStorage?.let { return it }
        val appContext = context.applicationContext
        val storage = SyncableLoginsStorage(
            appContext,
            lazy { SecureAbove22Preferences(appContext, "petal_fxa_logins", false) }
        )
        loginsStorage = storage
        migrateLegacyVaultIfNeeded(appContext, storage)
        return storage
    }

    private fun migrateLegacyVaultIfNeeded(appContext: Context, storage: SyncableLoginsStorage) {
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (sp.getBoolean(PREF_MIGRATED, false)) return

        val file = File(appContext.filesDir, LEGACY_VAULT_FILE)
        if (file.exists() && file.length() > 0) {
            try {
                val legacyCreds = decryptLegacyVaultInMemory(file)
                if (legacyCreds.isNotEmpty()) {
                    runBlocking(Dispatchers.IO) {
                        for (c in legacyCreds) {
                            if (c.password.isBlank()) continue
                            val originUrl = c.originUrl.ifBlank { "https://" + c.domain }
                            val entry = LoginEntry(
                                origin = originUrl,
                                formActionOrigin = c.formActionOrigin ?: originUrl,
                                username = c.username,
                                password = c.password
                            )
                            try {
                                storage.addOrUpdate(entry)
                            } catch (_: Throwable) {}
                        }
                    }
                    Log.i(TAG, "Migrated ${legacyCreds.size} legacy credentials into SyncableLoginsStorage.")
                }
                file.renameTo(File(appContext.filesDir, "$LEGACY_VAULT_FILE.migrated"))
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to migrate legacy credentials", t)
            }
        }
        sp.edit().putBoolean(PREF_MIGRATED, true).apply()
    }

    private fun decryptLegacyVaultInMemory(file: File): List<PetalCredential> {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(LEGACY_KEY_ALIAS, null) as? javax.crypto.SecretKey ?: return emptyList()

        FileInputStream(file).use { fis ->
            val iv = ByteArray(GCM_IV_LENGTH)
            val ivRead = fis.read(iv)
            if (ivRead != GCM_IV_LENGTH) return emptyList()

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val encryptedBytes = fis.readBytes()
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val json = String(decryptedBytes, Charsets.UTF_8)
            val type = object : TypeToken<List<PetalCredential>>() {}.type
            return Gson().fromJson(json, type) ?: emptyList()
        }
    }

    private fun normalizeOrigin(url: String, domain: String): String {
        val raw = url.ifBlank { "https://$domain" }
        val u = runCatching { Uri.parse(raw) }.getOrNull()
        return if (u != null && u.scheme != null && u.host != null) "${u.scheme}://${u.host}" else raw
    }

    private fun cleanDomain(origin: String): String {
        val host = runCatching { Uri.parse(origin).host }.getOrNull() ?: origin
        return host.lowercase().removePrefix("www.")
    }

    fun getAll(context: Context): List<PetalCredential> {
        val storage = getLoginsStorage(context)
        return runBlocking(Dispatchers.IO) {
            try {
                val list = storage.list()
                list.map { entry ->
                    PetalCredential(
                        id = entry.guid,
                        domain = cleanDomain(entry.origin),
                        originUrl = entry.origin,
                        username = entry.username,
                        password = entry.password,
                        createdAt = entry.timeCreated,
                        updatedAt = entry.timePasswordChanged,
                        lastUsedAt = entry.timeLastUsed,
                        formActionOrigin = entry.formActionOrigin
                    )
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun save(context: Context, credential: PetalCredential) {
        val storage = getLoginsStorage(context)
        val origin = normalizeOrigin(credential.originUrl, credential.domain)
        val entry = LoginEntry(
            origin = origin,
            formActionOrigin = credential.formActionOrigin ?: origin,
            username = credential.username,
            password = credential.password
        )
        runBlocking(Dispatchers.IO) {
            try {
                val existing = storage.list().firstOrNull {
                    it.guid == credential.id ||
                    (it.origin.equals(entry.origin, ignoreCase = true) && it.username.equals(entry.username, ignoreCase = true))
                }
                if (existing != null) {
                    storage.update(existing.guid, entry)
                } else {
                    storage.add(entry)
                }
            } catch (_: Throwable) {}
        }
    }

    fun delete(context: Context, id: String) {
        val storage = getLoginsStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                storage.delete(id)
            } catch (_: Throwable) {}
        }
    }

    fun updatePassword(context: Context, id: String, newPassword: String) {
        val storage = getLoginsStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                val existing = storage.get(id)
                if (existing != null) {
                    val updated = existing.toEntry().copy(password = newPassword)
                    storage.update(id, updated)
                }
            } catch (_: Throwable) {}
        }
    }

    fun markUsed(context: Context, id: String) {
        val storage = getLoginsStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                storage.touch(id)
            } catch (_: Throwable) {}
        }
    }

    fun clear(context: Context) {
        val storage = getLoginsStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                storage.wipeLocal()
            } catch (_: Throwable) {}
        }
    }

    fun findByDomainAndUsername(context: Context, domain: String, username: String): PetalCredential? {
        val clean = cleanDomain(domain)
        val all = getAll(context)
        return all.firstOrNull {
            it.domain.equals(clean, ignoreCase = true) && it.username.equals(username, ignoreCase = true)
        }
    }
}
