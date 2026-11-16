package com.petal.browser.account.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.petal.browser.unit.PetalBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/**
 * Sync through storage the user owns. The user picks any folder through Android's document picker
 * (Google Drive, Nextcloud, Syncthing, an SD card, ...) and Petal keeps one passphrase-encrypted file
 * in it ([FILE_NAME]). Every device pointed at that folder with the same passphrase merges what the
 * others have written and writes back the combined result. No Petal or third-party account is involved.
 *
 * Merge is additive: items missing on a device are added; deletions are not propagated, so removing a
 * bookmark on one device will not remove it on the others.
 */
object PetalFolderSync {
    const val FILE_NAME = "petal-sync.psync"
    private const val PREV_NAME = "petal-sync.prev.psync"
    private const val PREFS = "petal_folder_sync"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val AUTO_INTERVAL_MS = 6L * 60 * 60 * 1000
    private const val MIME = "application/octet-stream"

    const val KEY_BOOKMARKS = "sync_bookmarks"
    const val KEY_HISTORY = "sync_history"
    const val KEY_SHORTCUTS = "sync_shortcuts"
    const val KEY_RULES = "sync_site_rules"
    const val KEY_AUTO = "sync_auto"

    class SyncException(message: String) : Exception(message)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _configured = MutableStateFlow(false)
    val configured: StateFlow<Boolean> = _configured.asStateFlow()
    private val _folderName = MutableStateFlow<String?>(null)
    val folderName: StateFlow<String?> = _folderName.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── settings ──────────────────────────────────────────────────────────────

    fun isOn(c: Context, key: String): Boolean = prefs(c).getBoolean(key, true)
    fun setOn(c: Context, key: String, value: Boolean) = prefs(c).edit().putBoolean(key, value).apply()

    private fun treeUri(c: Context): Uri? = prefs(c).getString(KEY_TREE, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun clearError() { _error.value = null }

    /** Safe to call repeatedly. Loads saved state and runs the one-time cleanup of the old account data. */
    fun init(context: Context) {
        val app = context.applicationContext
        val tree = treeUri(app)
        _configured.value = tree != null && PetalSyncVault.has(app)
        _lastSync.value = prefs(app).getLong(KEY_LAST_SYNC, 0L)
        if (tree != null && _folderName.value == null) {
            scope.launch { _folderName.value = folderLabel(app, tree) }
        }
    }

    /** Called once per app start from MainActivity. */
    fun onAppStart(context: Context) {
        val app = context.applicationContext
        scope.launch { removeLegacyAccountData(app) }
        init(app)
        syncIfDue(app)
    }

    fun setFolder(context: Context, uri: Uri) {
        val app = context.applicationContext
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
        val old = treeUri(app)
        if (old != null && old != uri) runCatching { app.contentResolver.releasePersistableUriPermission(old, flags) }
        prefs(app).edit().putString(KEY_TREE, uri.toString()).apply()
        _folderName.value = null
        _configured.value = PetalSyncVault.has(app)
        scope.launch { _folderName.value = folderLabel(app, uri) }
    }

    fun setPassphrase(context: Context, passphrase: String) {
        val app = context.applicationContext
        PetalSyncVault.save(app, passphrase)
        _configured.value = treeUri(app) != null
    }

    /** Forgets the folder and the passphrase on this device. The file in the folder is left untouched. */
    fun disconnect(context: Context) {
        val app = context.applicationContext
        treeUri(app)?.let {
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs(app).edit().remove(KEY_TREE).remove(KEY_LAST_SYNC).apply()
        PetalSyncVault.clear(app)
        _configured.value = false
        _folderName.value = null
        _lastSync.value = 0L
        _error.value = null
        _message.value = null
    }

    // ── sync ──────────────────────────────────────────────────────────────────

    fun syncNow(context: Context) {
        val app = context.applicationContext
        scope.launch { runSync(app) }
    }

    fun syncIfDue(context: Context) {
        val app = context.applicationContext
        if (!_configured.value || !isOn(app, KEY_AUTO)) return
        if (System.currentTimeMillis() - _lastSync.value < AUTO_INTERVAL_MS) return
        syncNow(app)
    }

    private suspend fun runSync(context: Context) {
        if (!mutex.tryLock()) return // a sync is already running
        try {
            _syncing.value = true
            _error.value = null
            _message.value = doSync(context)
            val now = System.currentTimeMillis()
            prefs(context).edit().putLong(KEY_LAST_SYNC, now).apply()
            _lastSync.value = now
        } catch (e: SyncException) {
            _error.value = e.message
        } catch (e: Exception) {
            _error.value = "Sync failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            _syncing.value = false
            mutex.unlock()
        }
    }

    private fun doSync(context: Context): String {
        val tree = treeUri(context) ?: throw SyncException("Choose a sync folder first.")
        val pass = PetalSyncVault.load(context) ?: throw SyncException("Set a sync passphrase first.")
        val wantBookmarks = isOn(context, KEY_BOOKMARKS)
        val wantHistory = isOn(context, KEY_HISTORY)
        val wantShortcuts = isOn(context, KEY_SHORTCUTS)
        val wantRules = isOn(context, KEY_RULES)

        val existing = findChild(context, tree, FILE_NAME)
        val remoteBytes = existing?.let { readBytes(context, it) }?.takeIf { it.isNotEmpty() }

        var remote: JSONObject? = null
        var received = "nothing new from other devices"
        if (remoteBytes != null) {
            val plain = try {
                PetalSyncCrypto.decrypt(remoteBytes, pass)
            } catch (_: IllegalArgumentException) {
                throw SyncException("That folder already has a file named $FILE_NAME that isn't a Petal sync file. Nothing was changed.")
            } ?: throw SyncException("Wrong passphrase for the data in this folder. Nothing was changed.")
            remote = JSONObject(String(plain, Charsets.UTF_8))
            val summary = PetalBackupManager.mergeSyncSnapshot(context, remote, wantBookmarks, wantHistory, wantShortcuts, wantRules)
            received = summary.removePrefix("Restored: ").removeSuffix(".").ifBlank { received }
        }

        val local = PetalBackupManager.buildBackupJson(
            context, wantBookmarks, wantHistory, wantShortcuts, false, wantRules, false
        )
        // A category this device doesn't sync must not wipe what other devices stored for it.
        if (remote != null) {
            val keep = ArrayList<String>()
            if (!wantBookmarks) keep += "bookmarks"
            if (!wantHistory) keep += "history"
            if (!wantShortcuts) keep += "start_sites"
            if (!wantRules) keep += listOf("saved_sites", "trusted_sites", "protected_sites")
            for (k in keep) remote.opt(k)?.let { local.put(k, it) }
        }
        local.put("sync_device", Build.MODEL)
        local.put("sync_time", System.currentTimeMillis())

        val out = PetalSyncCrypto.encrypt(local.toString().toByteArray(Charsets.UTF_8), pass)
        // Keep the last good copy so a bad write can be recovered by hand.
        if (remoteBytes != null) writeNamed(context, tree, PREV_NAME, remoteBytes)
        writeNamed(context, tree, FILE_NAME, out)
        return "Received: $received"
    }

    // ── storage access (Storage Access Framework, no extra dependency) ────────

    private fun folderLabel(context: Context, tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun findChild(context: Context, tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        try {
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val display = c.getString(1) ?: continue
                    // Some providers append an extension to generic files, so accept "<name>.<ext>" too.
                    if (display == name || display.startsWith("$name.")) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    }
                }
            }
        } catch (e: SecurityException) {
            throw SyncException("Petal lost access to the sync folder. Choose the folder again.")
        } catch (e: IllegalArgumentException) {
            throw SyncException("The sync folder is no longer available. Choose the folder again.")
        }
        return null
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun writeNamed(context: Context, tree: Uri, name: String, bytes: ByteArray) {
        val cr = context.contentResolver
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var target = findChild(context, tree, name)
            ?: DocumentsContract.createDocument(cr, dir, MIME, name)
            ?: throw SyncException("Couldn't create the sync file in that folder. Is it writable?")
        var stream = try { cr.openOutputStream(target, "wt") } catch (_: Exception) { null }
        if (stream == null) {
            // Provider can't truncate: recreate so no stale bytes remain after a shorter write.
            runCatching { DocumentsContract.deleteDocument(cr, target) }
            target = DocumentsContract.createDocument(cr, dir, MIME, name)
                ?: throw SyncException("Couldn't write to the sync folder.")
            stream = cr.openOutputStream(target, "w") ?: throw SyncException("Couldn't write to the sync folder.")
        }
        stream.use { it.write(bytes); it.flush() }
    }

    // ── one-time cleanup of the removed Mozilla account integration ───────────

    /**
     * Earlier builds could sign in with a Mozilla account. That feature is gone, so remove the account
     * state it left behind. The password manager's own storage ("petal_fxa_logins") is deliberately
     * NOT touched: it only keeps its historical name and holds the key to the user's saved logins.
     */
    private fun removeLegacyAccountData(context: Context) {
        val p = prefs(context)
        if (p.getBoolean("legacy_account_cleaned", false)) return
        try {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            dir.listFiles()?.forEach { f ->
                val name = f.name.removeSuffix(".xml")
                val legacy = name == "petal_sync_settings" || name.startsWith("fxa") ||
                    name.startsWith("mozac_service_fxa") || name.startsWith("mozac_feature_syncedtabs")
                if (legacy && name != "petal_fxa_logins") {
                    runCatching { context.deleteSharedPreferences(name) }
                }
            }
        } catch (_: Exception) {}
        p.edit().putBoolean("legacy_account_cleaned", true).apply()
    }
}
