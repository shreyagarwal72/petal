package com.petal.browser.passwords

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Result data class for batch credential imports.
 */
data class BatchImportResult(
    val imported: Int,
    val duplicates: Int,
    val skipped: Int,
    val errors: List<String> = emptyList()
)

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
    const val GCM_IV_LENGTH = 12 // 12 bytes recommended for GCM
    private const val VAULT_FILE_NAME = "petal_vault.enc"

    private val gson = Gson()
    private val credentials = mutableListOf<PetalCredential>()
    private val neverSaveDomains = mutableSetOf<String>()
    private const val NEVER_SAVE_PREF = "sp_autofill_never_save_domains"
    private var isInitialized = false
    private var vaultFile: File? = null
    private var appContext: Context? = null

    // True when the vault file exists but could not be read for a temporary reason.
    // We then refuse to overwrite it, so a good file is never replaced by an empty one.
    @Volatile
    private var writeBlocked = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val debouncedPersistScheduled = AtomicBoolean(false)

    fun isWriteBlocked(): Boolean = writeBlocked

    @Synchronized
    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        if (isInitialized) return
        vaultFile = File(appCtx.filesDir, VAULT_FILE_NAME)
        ensureKeyExists()
        // Ensure Logins storage is initialized and one-time migration executed
        PetalLoginsProvider.getLoginsStorage(appCtx)
        val loaded = PetalLoginsProvider.getAll(appCtx)
        credentials.clear()
        if (loaded.isNotEmpty()) {
            credentials.addAll(loaded)
        } else {
            loadVault()
            if (credentials.isNotEmpty()) {
                credentials.forEach { PetalLoginsProvider.save(appCtx, it) }
            }
        }
        isInitialized = true
        Log.i(TAG, "Vault initialized with ${credentials.size} credentials from SyncableLoginsStorage.")
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
            writeBlocked = true
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (key == null) {
            ensureKeyExists()
            return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
                ?: throw IllegalStateException("Keystore master key '$KEY_ALIAS' is unavailable.")
        }
        return key
    }

    @Synchronized
    private fun loadVault() {
        val file = vaultFile ?: return
        writeBlocked = false
        if (!file.exists() || file.length() == 0L) {
            credentials.clear()
            return
        }

        val atomicFile = androidx.core.util.AtomicFile(file)
        try {
            atomicFile.openRead().use { fis ->
                val iv = ByteArray(GCM_IV_LENGTH)
                var totalRead = 0
                while (totalRead < GCM_IV_LENGTH) {
                    val bytes = fis.read(iv, totalRead, GCM_IV_LENGTH - totalRead)
                    if (bytes == -1) break
                    totalRead += bytes
                }
                if (totalRead < GCM_IV_LENGTH) {
                    Log.w(TAG, "Corrupt vault file, IV too short ($totalRead bytes).")
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
                    if (!list.isNullOrEmpty()) {
                        val sanitized = list.map { sanitizeCredential(it) }
                        credentials.addAll(sanitized)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt and load credentials vault", e)
            credentials.clear()
            if (isKeyMismatch(e)) {
                try {
                    val aside = File(file.parentFile, VAULT_FILE_NAME + ".unreadable")
                    if (aside.exists()) aside.delete()
                    file.renameTo(aside)
                    Log.w(TAG, "Vault key mismatch. Old file moved to ${aside.name}; starting empty.")
                } catch (io: Exception) {
                    writeBlocked = true
                }
            } else {
                writeBlocked = true
            }
        }
    }

    private fun isKeyMismatch(e: Throwable?): Boolean {
        var t = e
        var depth = 0
        while (t != null && depth < 6) {
            if (t is javax.crypto.AEADBadTagException || t is javax.crypto.BadPaddingException) return true
            t = t.cause
            depth++
        }
        return false
    }

    @Synchronized
    private fun persistVault() {
        val file = vaultFile ?: return
        if (writeBlocked) {
            Log.w(TAG, "Vault could not be read this session; refusing to overwrite it.")
            return
        }

        val atomicFile = androidx.core.util.AtomicFile(file)
        var fos: FileOutputStream? = null
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv

            val json = gson.toJson(credentials)
            val encryptedBytes = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

            fos = atomicFile.startWrite()
            fos.write(iv)
            fos.write(encryptedBytes)
            fos.flush()
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and persist credentials vault", e)
            if (fos != null) {
                atomicFile.failWrite(fos)
            }
        }
    }

    /**
     * Debounces vault disk writes for frequent events like markUsed / onLoginUsed.
     */
    fun scheduleDebouncedPersist() {
        if (debouncedPersistScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed({
                debouncedPersistScheduled.set(false)
                synchronized(this) {
                    persistVault()
                }
            }, 800L)
        }
    }

    // ---------------------------------------------------------------------------------
    // CENTRALIZED eTLD+1 & DOMAIN MATCHER
    // ---------------------------------------------------------------------------------

    /**
     * Common two-part public suffixes (e.g. co.uk, com.au, co.jp) to prevent taking only the last part.
     */
    private val MULTI_PART_TLDS = setOf(
        "co.uk", "org.uk", "gov.uk", "ac.uk", "me.uk",
        "co.jp", "ne.jp", "ac.jp", "go.jp",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.nz", "net.nz", "org.nz",
        "co.za", "org.za",
        "com.br", "org.br", "net.br",
        "co.in", "net.in", "org.in", "gen.in", "firm.in",
        "com.sg", "edu.sg", "gov.sg",
        "com.mx", "org.mx",
        "com.tr", "org.tr",
        "com.tw", "org.tw",
        "github.io", "gitlab.io", "pages.dev"
    )

    fun cleanDomain(raw: String): String {
        val trimmed = raw.trim().lowercase()
        if (trimmed.startsWith("app://")) {
            return "app://" + trimmed.removePrefix("app://").trimEnd('/')
        }
        return trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
    }

    /**
     * Returns the effective TLD+1 base domain for grouping and matching.
     * E.g. "accounts.google.com" -> "google.com"
     *      "news.bbc.co.uk" -> "bbc.co.uk"
     *      "192.168.1.1" -> "192.168.1.1"
     *      "app://com.example.app" -> "app://com.example.app"
     */
    fun getEffectiveTldPlusOne(domain: String): String {
        val clean = cleanDomain(domain)
        if (clean.startsWith("app://")) return clean
        // If IP address, do not truncate
        if (clean.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) return clean

        val parts = clean.split(".")
        if (parts.size <= 2) return clean

        // Check if last two parts match multi-part TLD
        val lastTwo = parts.takeLast(2).joinToString(".")
        if (MULTI_PART_TLDS.contains(lastTwo) && parts.size >= 3) {
            return parts.takeLast(3).joinToString(".")
        }

        return parts.takeLast(2).joinToString(".")
    }

    /**
     * Unified domain matcher used across AutofillService, GeckoDelegate, and Vault lookups.
     * Saved "example.com" matches page "example.com" and "login.example.com".
     * Saved "login.example.com" matches page "login.example.com", and optionally other subdomains if base matches.
     */
    fun domainMatches(saved: String, page: String): Boolean {
        val s = cleanDomain(saved)
        val p = cleanDomain(page)
        if (s.isEmpty() || p.isEmpty()) return false
        if (s == p) return true
        if (s.startsWith("app://") || p.startsWith("app://")) return s.equals(p, ignoreCase = true)

        if (p.endsWith(".$s")) return true
        // Same eTLD+1 base check
        val baseS = getEffectiveTldPlusOne(s)
        val baseP = getEffectiveTldPlusOne(p)
        return baseS.isNotBlank() && baseS.equals(baseP, ignoreCase = true)
    }

    // ---------------------------------------------------------------------------------
    // CRUD & REPOSITORIES
    // ---------------------------------------------------------------------------------

    @Synchronized
    fun getNeverSaveDomains(context: Context): Set<String> {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val saved = sp.getStringSet(NEVER_SAVE_PREF, emptySet()) ?: emptySet()
        synchronized(neverSaveDomains) {
            neverSaveDomains.clear()
            neverSaveDomains.addAll(saved)
            return HashSet(neverSaveDomains)
        }
    }

    @Synchronized
    fun addNeverSaveDomain(context: Context, domain: String) {
        val clean = cleanDomain(domain)
        if (clean.isBlank()) return
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        synchronized(neverSaveDomains) {
            neverSaveDomains.add(clean)
            sp.edit().putStringSet(NEVER_SAVE_PREF, HashSet(neverSaveDomains)).apply()
        }
    }

    @Synchronized
    fun removeNeverSaveDomain(context: Context, domain: String) {
        val clean = cleanDomain(domain)
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        synchronized(neverSaveDomains) {
            neverSaveDomains.remove(clean)
            sp.edit().putStringSet(NEVER_SAVE_PREF, HashSet(neverSaveDomains)).apply()
        }
    }

    @Synchronized
    fun isNeverSave(context: Context, domain: String): Boolean {
        val clean = cleanDomain(domain)
        val set = getNeverSaveDomains(context)
        return set.contains(clean) || set.contains(getEffectiveTldPlusOne(clean))
    }

    @Synchronized
    fun getAll(): List<PetalCredential> {
        return ArrayList(credentials)
    }

    @Synchronized
    fun findByDomain(domain: String): List<PetalCredential> {
        val clean = cleanDomain(domain)
        return credentials.filter { domainMatches(it.domain, clean) }
    }

    @Synchronized
    fun findById(id: String): PetalCredential? {
        return credentials.firstOrNull { it.id == id }
    }

    @Synchronized
    fun save(credential: PetalCredential) {
        val sanitized = sanitizeCredential(credential)
        val index = credentials.indexOfFirst { it.id == sanitized.id }
        if (index >= 0) {
            credentials[index] = sanitized
        } else {
            // Check if exact same domain and username already exists, update without losing metadata
            val existingIndex = credentials.indexOfFirst {
                cleanDomain(it.domain).equals(cleanDomain(sanitized.domain), ignoreCase = true) &&
                it.username.equals(sanitized.username, ignoreCase = true)
            }
            if (existingIndex >= 0) {
                val existing = credentials[existingIndex]
                credentials[existingIndex] = sanitized.copy(
                    id = existing.id,
                    createdAt = existing.createdAt,
                    isFavorite = existing.isFavorite,
                    notes = if (sanitized.notes.isNotBlank()) sanitized.notes else existing.notes,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                credentials.add(0, sanitized)
            }
        }
        persistVault()
        appContext?.let { PetalLoginsProvider.save(it, sanitized) }
    }

    @Synchronized
    fun markUsed(id: String) {
        val index = credentials.indexOfFirst { it.id == id }
        if (index >= 0) {
            val curr = credentials[index]
            credentials[index] = curr.copy(lastUsedAt = System.currentTimeMillis())
            scheduleDebouncedPersist()
            appContext?.let { PetalLoginsProvider.markUsed(it, id) }
        }
    }

    @Synchronized
    fun updatePassword(id: String, newPassword: String) {
        val index = credentials.indexOfFirst { it.id == id }
        if (index >= 0) {
            val curr = credentials[index]
            credentials[index] = curr.copy(
                password = newPassword,
                updatedAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis()
            )
            persistVault()
            appContext?.let { PetalLoginsProvider.updatePassword(it, id, newPassword) }
        }
    }

    @Synchronized
    fun findByDomainAndUsername(domain: String, username: String): PetalCredential? {
        val clean = cleanDomain(domain)
        return credentials.firstOrNull {
            cleanDomain(it.domain).equals(clean, ignoreCase = true) &&
            it.username.equals(username, ignoreCase = true)
        }
    }

    @Synchronized
    fun delete(id: String) {
        val removed = credentials.removeAll { it.id == id }
        if (removed) {
            persistVault()
            appContext?.let { PetalLoginsProvider.delete(it, id) }
        }
    }

    @Synchronized
    fun toggleFavorite(id: String) {
        val index = credentials.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = credentials[index]
            val updated = current.copy(isFavorite = !current.isFavorite)
            credentials[index] = updated
            persistVault()
            appContext?.let { PetalLoginsProvider.save(it, updated) }
        }
    }

    @Synchronized
    fun setCompromised(id: String, isCompromised: Boolean) {
        val index = credentials.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = credentials[index]
            val updated = current.copy(isCompromised = isCompromised)
            credentials[index] = updated
            persistVault()
            appContext?.let { PetalLoginsProvider.save(it, updated) }
        }
    }

    @Synchronized
    fun clear() {
        credentials.clear()
        writeBlocked = false
        vaultFile?.delete()
        appContext?.let { PetalLoginsProvider.clear(it) }
    }

    @Synchronized
    fun exportToJson(): String {
        return gson.toJson(credentials)
    }

    // ---------------------------------------------------------------------------------
    // BATCH IMPORT WITH DEDUPLICATION & MERGING
    // ---------------------------------------------------------------------------------

    @Synchronized
    fun importBatch(list: List<PetalCredential>): BatchImportResult {
        if (list.isEmpty()) return BatchImportResult(0, 0, 0)
        var imported = 0
        var duplicates = 0
        var skipped = 0

        for (raw in list) {
            val cred = sanitizeCredential(raw)
            if (cred.domain.isBlank() || (cred.username.isBlank() && cred.password.isBlank())) {
                skipped++
                continue
            }

            val existingIndex = credentials.indexOfFirst {
                it.id == cred.id || (
                    cleanDomain(it.domain).equals(cleanDomain(cred.domain), ignoreCase = true) &&
                    it.username.equals(cred.username, ignoreCase = true)
                )
            }

            if (existingIndex >= 0) {
                val existing = credentials[existingIndex]
                if (existing.password == cred.password) {
                    duplicates++
                } else if (cred.updatedAt > existing.updatedAt) {
                    // Incoming credential is newer: update password and merge notes
                    credentials[existingIndex] = existing.copy(
                        password = cred.password,
                        updatedAt = cred.updatedAt,
                        notes = if (cred.notes.isNotBlank()) cred.notes else existing.notes
                    )
                    imported++
                } else {
                    duplicates++
                }
            } else {
                credentials.add(cred)
                imported++
            }
        }

        if (imported > 0) {
            persistVault()
            appContext?.let { ctx ->
                for (cred in credentials) {
                    PetalLoginsProvider.save(ctx, cred)
                }
            }
        }
        return BatchImportResult(imported, duplicates, skipped)
    }

    @Synchronized
    fun importFromJson(json: String): Int {
        return try {
            val type = object : TypeToken<List<PetalCredential>>() {}.type
            val list: List<PetalCredential>? = gson.fromJson(json, type)
            if (list.isNullOrEmpty()) return 0
            val result = importBatch(list)
            result.imported
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse and import backup JSON", e)
            0
        }
    }

    private fun sanitizeCredential(cred: PetalCredential): PetalCredential {
        val id = if (cred.id.isNullOrBlank()) UUID.randomUUID().toString() else cred.id
        val domain = cleanDomain(cred.domain.orEmpty())
        val originUrl = if (cred.originUrl.isNullOrBlank()) {
            if (domain.startsWith("app://")) domain else "https://$domain"
        } else cred.originUrl
        val username = cred.username.orEmpty()
        val password = cred.password.orEmpty()
        val notes = cred.notes.orEmpty()
        val createdAt = if (cred.createdAt <= 0L) System.currentTimeMillis() else cred.createdAt
        val updatedAt = if (cred.updatedAt <= 0L) createdAt else cred.updatedAt
        val lastUsedAt = if (cred.lastUsedAt <= 0L) createdAt else cred.lastUsedAt

        return cred.copy(
            id = id,
            domain = domain,
            originUrl = originUrl,
            username = username,
            password = password,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastUsedAt = lastUsedAt
        )
    }

    // ---------------------------------------------------------------------------------
    // PASSWORD-PROTECTED BACKUP (V2)
    // ---------------------------------------------------------------------------------

    private const val PASSWORD_BACKUP_MAGIC = "PETAL_ENC_VAULT_V2:"
    private const val PBKDF2_ITERATIONS = 210_000
    private const val SALT_LENGTH = 16
    const val MIN_BACKUP_PASSWORD_LENGTH = 8

    fun isPasswordBackup(content: String): Boolean {
        return content.trim().startsWith(PASSWORD_BACKUP_MAGIC)
    }

    private fun deriveBackupKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    @Synchronized
    fun exportWithPassword(password: String): String {
        require(password.length >= MIN_BACKUP_PASSWORD_LENGTH) {
            "Password must be at least $MIN_BACKUP_PASSWORD_LENGTH characters."
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveBackupKey(password, salt), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(gson.toJson(credentials).toByteArray(Charsets.UTF_8))

        val combined = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, combined, salt.size + iv.size, ciphertext.size)
        return PASSWORD_BACKUP_MAGIC + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    @Synchronized
    fun importWithPasswordOrThrow(content: String, password: String): Int {
        val trimmed = content.trim()
        require(trimmed.startsWith(PASSWORD_BACKUP_MAGIC)) { "This is not a password-protected Petal backup." }

        val combined = try {
            Base64.decode(trimmed.removePrefix(PASSWORD_BACKUP_MAGIC), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("The Petal backup is damaged.", e)
        }
        require(combined.size > SALT_LENGTH + GCM_IV_LENGTH) { "The Petal backup is incomplete." }

        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, combined.size)

        val decrypted = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveBackupKey(password, salt), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalStateException("Wrong password, or the backup is damaged.", e)
        }
        return importFromJson(String(decrypted, Charsets.UTF_8))
    }

    private const val ENCRYPTED_BACKUP_MAGIC = "PETAL_ENC_VAULT_V1:"

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

    fun isEncryptedBackup(content: String): Boolean {
        return content.trim().startsWith(ENCRYPTED_BACKUP_MAGIC) || isPasswordBackup(content)
    }

    @Synchronized
    fun importEncrypted(content: String): Int {
        return try {
            importEncryptedOrThrow(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt and import encrypted backup", e)
            0
        }
    }

    @Synchronized
    fun importEncryptedOrThrow(content: String): Int {
        val trimmed = content.trim()
        require(!isPasswordBackup(trimmed)) { "This backup is password protected. Enter its password to open it." }
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
                "This old-style backup is tied to the phone and Petal install that made it, so it cannot be opened here. Export new backups with a password.",
                e
            )
        }
        return importFromJson(String(decryptedBytes, Charsets.UTF_8))
    }
}
