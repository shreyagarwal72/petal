package com.petal.browser.account.sync

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.petal.browser.database.PetalPlacesProvider
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.unit.PetalBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SyncTab(val title: String, val url: String)
data class SyncDevice(val id: String, val name: String, val lastSeen: Long, val tabs: List<SyncTab>)
data class ReceivedTab(val id: String, val from: String, val title: String, val url: String)

/**
 * Sync through storage the user owns. The user picks any folder through Android's document picker
 * (Google Drive, Nextcloud, Syncthing, an SD card, ...) and Petal keeps one passphrase-encrypted file
 * in it ([FILE_NAME]). Every device pointed at that folder with the same passphrase merges what the
 * others have written and writes the combined result back. No Petal or third-party account is involved.
 *
 * Covers bookmarks (with reading list), history, passwords, home shortcuts, site rules, open tabs, a
 * device list and "send tab to device". Deletions travel too: each device notices what disappeared since
 * its last sync and records it, and the most recent change to an item wins (see [SyncCategoryState]).
 * Those timestamps come from each device's clock, so badly wrong clocks can make a stale change win.
 */
object PetalFolderSync {
    const val FILE_NAME = "petal-sync.psync"
    private const val PREV_NAME = "petal-sync.prev.psync"
    private const val PREFS = "petal_folder_sync"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_OUTBOX = "outbox"
    private const val KEY_RECEIVED = "received"
    private const val KEY_SEEN_IDS = "seen_ids"
    private const val KEY_DEVICES_CACHE = "devices_cache"
    private const val AUTO_INTERVAL_MS = 6L * 60 * 60 * 1000
    private const val FOREGROUND_MIN_GAP_MS = 10L * 60 * 1000
    private const val DEVICE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    private const val INBOX_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_TABS = 50
    private const val JOB_ID = 7301
    private const val MIME = PetalSyncImport.MIME

    const val KEY_BOOKMARKS = "sync_bookmarks"
    const val KEY_HISTORY = "sync_history"
    const val KEY_PASSWORDS = "sync_passwords"
    const val KEY_TABS = "sync_tabs"
    const val KEY_SHORTCUTS = "sync_shortcuts"
    const val KEY_RULES = "sync_site_rules"
    const val KEY_AUTO = "sync_auto"

    class SyncException(message: String) : Exception(message)

    /** Passphrase typed while opening a .psync file, offered once on the setup screen. Memory only. */
    @Volatile internal var importedPassphrase: String? = null

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
    private val _devices = MutableStateFlow<List<SyncDevice>>(emptyList())
    /** Other devices that sync to the same folder (this device is not listed). */
    val devices: StateFlow<List<SyncDevice>> = _devices.asStateFlow()
    private val _received = MutableStateFlow<List<ReceivedTab>>(emptyList())
    /** Tabs other devices sent to this one, waiting to be opened or dismissed. */
    val receivedTabs: StateFlow<List<ReceivedTab>> = _received.asStateFlow()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── settings ──────────────────────────────────────────────────────────────

    /** Passwords are the only category that stays off until the user turns it on. */
    fun isOn(c: Context, key: String): Boolean = prefs(c).getBoolean(key, key != KEY_PASSWORDS)
    fun setOn(c: Context, key: String, value: Boolean) = prefs(c).edit().putBoolean(key, value).apply()

    fun deviceId(c: Context): String {
        val p = prefs(c)
        return p.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { p.edit().putString(KEY_DEVICE_ID, it).apply() }
    }

    fun deviceName(c: Context): String =
        prefs(c).getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() } ?: "Petal on ${Build.MODEL}"

    fun setDeviceName(c: Context, name: String) = prefs(c).edit().putString(KEY_DEVICE_NAME, name.trim().take(40)).apply()

    private fun treeUri(c: Context): Uri? = prefs(c).getString(KEY_TREE, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun clearError() { _error.value = null }

    /** Safe to call repeatedly. Loads saved state. */
    fun init(context: Context) {
        val app = context.applicationContext
        val tree = treeUri(app)
        _configured.value = tree != null && PetalSyncVault.has(app)
        _lastSync.value = prefs(app).getLong(KEY_LAST_SYNC, 0L)
        _devices.value = parseDevices(prefs(app).getString(KEY_DEVICES_CACHE, null))
        _received.value = parseReceived(prefs(app).getString(KEY_RECEIVED, null))
        if (tree != null && _folderName.value == null) {
            scope.launch { _folderName.value = folderLabel(app, tree) }
        }
        if (_configured.value) schedulePeriodic(app) else cancelPeriodic(app)
    }

    /** Called once per app start from MainActivity. */
    fun onAppStart(context: Context) {
        val app = context.applicationContext
        scope.launch { removeLegacyAccountData(app) }
        init(app)
        syncIfDue(app, AUTO_INTERVAL_MS)
    }

    /** Called when the app goes to the background, so changes reach the other devices soon after. */
    fun onAppBackground(context: Context) = syncIfDue(context.applicationContext, FOREGROUND_MIN_GAP_MS)

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
        if (_configured.value) schedulePeriodic(app)
    }

    fun setPassphrase(context: Context, passphrase: String) {
        val app = context.applicationContext
        PetalSyncVault.save(app, passphrase)
        importedPassphrase = null
        _configured.value = treeUri(app) != null
        if (_configured.value) schedulePeriodic(app)
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
        prefs(app).edit()
            .remove(KEY_TREE).remove(KEY_LAST_SYNC).remove(KEY_OUTBOX).remove(KEY_RECEIVED)
            .remove(KEY_SEEN_IDS).remove(KEY_DEVICES_CACHE).apply()
        PetalSyncVault.clear(app)
        importedPassphrase = null
        PetalSyncFiles.clearAll(app)
        cancelPeriodic(app)
        _configured.value = false
        _folderName.value = null
        _lastSync.value = 0L
        _error.value = null
        _message.value = null
        _devices.value = emptyList()
        _received.value = emptyList()
    }

    // ── tabs ──────────────────────────────────────────────────────────────────

    /** Queues a tab for another device; it is delivered on the next sync, which starts right away. */
    fun sendTab(context: Context, targetDeviceId: String, title: String, url: String) {
        val app = context.applicationContext
        val arr = JSONArray(prefs(app).getString(KEY_OUTBOX, "[]"))
        arr.put(
            JSONObject().put("id", UUID.randomUUID().toString()).put("to", targetDeviceId)
                .put("t", title).put("u", url).put("ts", System.currentTimeMillis())
        )
        prefs(app).edit().putString(KEY_OUTBOX, arr.toString()).apply()
        syncNow(app)
    }

    fun dismissReceived(context: Context, id: String) {
        val app = context.applicationContext
        _received.value = _received.value.filterNot { it.id == id }
        prefs(app).edit().putString(KEY_RECEIVED, receivedToJson(_received.value).toString()).apply()
    }

    // ── sync ──────────────────────────────────────────────────────────────────

    fun syncNow(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        scope.launch {
            runSync(app, publishTabs = true)
            onDone?.invoke()
        }
    }

    /** Used by the background job: there is no UI, so the live tab list is not published. */
    internal fun syncFromJob(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            if (_configured.value) runSync(app, publishTabs = false)
            onDone()
        }
    }

    private fun syncIfDue(context: Context, minGapMs: Long) {
        if (!_configured.value || !isOn(context, KEY_AUTO)) return
        if (System.currentTimeMillis() - _lastSync.value < minGapMs) return
        syncNow(context)
    }

    private suspend fun runSync(context: Context, publishTabs: Boolean) {
        if (!mutex.tryLock()) return // a sync is already running
        try {
            _syncing.value = true
            _error.value = null
            // NowBar: only user-started syncs (publishTabs) show a running card; the background job stays silent.
            if (publishTabs) com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.SYNC_RUNNING, "sync", "Syncing", "Talking to your sync folder…")
            _message.value = doSync(context, publishTabs)
            if (publishTabs) com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.SYNC_DONE, "sync", "Sync complete", _message.value.orEmpty().take(60))
            val now = System.currentTimeMillis()
            prefs(context).edit().putLong(KEY_LAST_SYNC, now).apply()
            _lastSync.value = now
        } catch (e: SyncException) {
            _error.value = e.message
            com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.SYNC_FAILED, "sync", "Sync failed", e.message.orEmpty().take(80))
        } catch (e: Exception) {
            _error.value = "Sync failed: ${e.message ?: e.javaClass.simpleName}"
            com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.SYNC_FAILED, "sync", "Sync failed", e.message.orEmpty().take(80))
        } finally {
            _syncing.value = false
            mutex.unlock()
        }
    }

    private fun doSync(context: Context, publishTabs: Boolean): String {
        val tree = treeUri(context) ?: throw SyncException("Choose a sync folder first.")
        val pass = PetalSyncVault.load(context) ?: throw SyncException("Set a sync passphrase first.")
        val wantBookmarks = isOn(context, KEY_BOOKMARKS)
        val wantHistory = isOn(context, KEY_HISTORY)
        val wantShortcuts = isOn(context, KEY_SHORTCUTS)
        val wantRules = isOn(context, KEY_RULES)
        val wantTabs = isOn(context, KEY_TABS)
        val wantPasswords = isOn(context, KEY_PASSWORDS)
        val now = System.currentTimeMillis()
        val myId = deviceId(context)

        // ── read the other devices' data ──
        val existing = findChild(context, tree, FILE_NAME)
        val remoteBytes = existing?.let { readBytes(context, it) }?.takeIf { it.isNotEmpty() }
        var remote: JSONObject? = null
        if (remoteBytes != null) {
            val plain = try {
                PetalSyncCrypto.decrypt(remoteBytes, pass)
            } catch (_: IllegalArgumentException) {
                throw SyncException("That folder already has a file named $FILE_NAME that isn't a Petal sync file. Nothing was changed.")
            } ?: throw SyncException("Wrong passphrase for the data in this folder. Nothing was changed.")
            remote = JSONObject(String(plain, Charsets.UTF_8))
        }

        // ── what this device holds now, and what it changed since the last sync ──
        val base = PetalSyncFiles.loadBase(context) // null on this device's first sync
        val state = PetalSyncFiles.loadState(context)
        val before = PetalBackupManager.buildBackupJson(context, wantBookmarks, wantHistory, wantShortcuts, false, wantRules, false)
        val bookmarksBefore = urls(before.optJSONArray("bookmarks"))
        val historyBefore = urls(before.optJSONArray("history"))
        val passwordsBefore = if (wantPasswords) PetalSyncPasswords.read(context) else emptyList()
        if (base != null) {
            if (wantBookmarks) noteLocalChanges(state.bookmarks, base.bookmarks, bookmarksBefore, now)
            if (wantHistory) noteLocalChanges(state.history, base.history, historyBefore, now)
            if (wantPasswords) noteLocalChanges(state.passwords, base.passwords, passwordsBefore.map { it.key }.toSet(), now)
        }

        // ── merge the other devices in ──
        var summary = "nothing new from other devices"
        if (remote != null) {
            val remoteState = SyncState.fromJson(remote.optJSONObject("state"))
            state.bookmarks.mergeFrom(remoteState.bookmarks)
            state.history.mergeFrom(remoteState.history)
            state.passwords.mergeFrom(remoteState.passwords)

            // A device's first sync only adds; it never deletes what it already had.
            if (base != null) {
                if (wantBookmarks) bookmarksBefore.filter { state.bookmarks.isDeleted(it) }.forEach { PetalPlacesProvider.deleteBookmark(context, it) }
                if (wantHistory) historyBefore.filter { state.history.isDeleted(it) }.forEach { PetalPlacesProvider.deleteHistory(context, it) }
                if (wantPasswords) PetalSyncPasswords.applyDeletes(context, passwordsBefore, state.passwords)
            }

            val additions = JSONObject()
            remote.optJSONArray("bookmarks")?.let { additions.put("bookmarks", dropDeleted(it, state.bookmarks)) }
            remote.optJSONArray("history")?.let { additions.put("history", dropDeleted(it, state.history)) }
            for (k in listOf("start_sites", "saved_sites", "trusted_sites", "protected_sites")) remote.opt(k)?.let { additions.put(k, it) }
            val merged = PetalBackupManager.mergeSyncSnapshot(context, additions, wantBookmarks, wantHistory, wantShortcuts, wantRules)
            summary = merged.removePrefix("Restored: ").removeSuffix(".").ifBlank { summary }

            if (wantPasswords) {
                val changed = PetalSyncPasswords.mergeRemote(context, remote.optJSONArray("passwords"), state.passwords, passwordsBefore)
                if (changed > 0) summary += ", $changed password${if (changed == 1) "" else "s"}"
                PetalSyncPasswords.refreshVault(context)
            }
        }

        // ── devices, open tabs and tabs sent between devices ──
        val devices = JSONObject(remote?.optJSONObject("devices")?.toString() ?: "{}")
        val myTabs = when {
            !wantTabs -> JSONArray()
            publishTabs -> currentTabs(context) ?: devices.optJSONObject(myId)?.optJSONArray("tabs") ?: JSONArray()
            else -> devices.optJSONObject(myId)?.optJSONArray("tabs") ?: JSONArray()
        }
        devices.put(myId, JSONObject().put("name", deviceName(context)).put("seen", now).put("tabs", myTabs))
        val staleIds = ArrayList<String>()
        for (id in devices.keys()) if (now - (devices.optJSONObject(id)?.optLong("seen", 0L) ?: 0L) > DEVICE_RETENTION_MS) staleIds.add(id)
        staleIds.forEach { devices.remove(it) }

        val inbox = JSONObject(remote?.optJSONObject("inbox")?.toString() ?: "{}")
        takeMyInbox(context, inbox, myId, devices)
        deliverOutbox(context, inbox, myId, devices, now)

        // ── write everything back ──
        val after = PetalBackupManager.buildBackupJson(context, wantBookmarks, wantHistory, wantShortcuts, false, wantRules, false)
        if (remote != null) {
            // A category this device doesn't sync must not wipe what other devices stored for it.
            val keep = ArrayList<String>()
            if (!wantBookmarks) keep += "bookmarks"
            if (!wantHistory) keep += "history"
            if (!wantShortcuts) keep += "start_sites"
            if (!wantRules) keep += listOf("saved_sites", "trusted_sites", "protected_sites")
            if (!wantPasswords) keep += "passwords"
            for (k in keep) remote.opt(k)?.let { after.put(k, it) }
        }
        val passwordsAfter = if (wantPasswords) PetalSyncPasswords.read(context) else emptyList()
        if (wantPasswords) after.put("passwords", PetalSyncPasswords.toJson(passwordsAfter))
        state.prune(now)
        after.put("v", 2)
        after.put("state", state.toJson())
        after.put("devices", devices)
        after.put("inbox", inbox)
        after.put("sync_time", now)

        val out = PetalSyncCrypto.encrypt(after.toString().toByteArray(Charsets.UTF_8), pass)
        // Keep the last good copy so a bad write can be recovered by hand.
        if (remoteBytes != null) writeNamed(context, tree, PREV_NAME, remoteBytes)
        writeNamed(context, tree, FILE_NAME, out)

        // Only after a successful write: remember what we now hold, so the next sync sees only new changes.
        PetalSyncFiles.saveState(context, state)
        PetalSyncFiles.saveBase(
            context,
            SyncBase(
                bookmarks = if (wantBookmarks) urls(after.optJSONArray("bookmarks")) else base?.bookmarks ?: emptySet(),
                history = if (wantHistory) urls(after.optJSONArray("history")) else base?.history ?: emptySet(),
                passwords = if (wantPasswords) passwordsAfter.map { it.key }.toSet() else base?.passwords ?: emptySet()
            )
        )
        publishDevices(context, devices, myId)
        prefs(context).edit().remove(KEY_OUTBOX).apply()
        return "Received: $summary"
    }

    private fun noteLocalChanges(st: SyncCategoryState, base: Set<String>, current: Set<String>, now: Long) {
        for (k in base) if (k !in current) st.put(k, now, false)
        for (k in current) if (k !in base && st.isDeleted(k)) st.put(k, now, true)
    }

    private fun urls(arr: JSONArray?): Set<String> {
        val out = HashSet<String>()
        if (arr != null) for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i)?.optString("url", "")?.trim().orEmpty()
            if (u.isNotEmpty()) out.add(u)
        }
        return out
    }

    private fun dropDeleted(arr: JSONArray, st: SyncCategoryState): JSONArray {
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!st.isDeleted(o.optString("url", "").trim())) out.put(o)
        }
        return out
    }

    private fun currentTabs(context: Context): JSONArray? = try {
        val arr = JSONArray()
        PetalEngineStore.getStore(context).state.tabs
            .filter { !it.content.private && it.content.url.startsWith("http") }
            .take(MAX_TABS)
            .forEach { arr.put(JSONObject().put("t", it.content.title).put("u", it.content.url)) }
        arr
    } catch (_: Throwable) { null }

    /** Picks up tabs other devices sent to this one and removes them from the shared inbox. */
    private fun takeMyInbox(context: Context, inbox: JSONObject, myId: String, devices: JSONObject) {
        val mine = inbox.optJSONArray(myId) ?: return
        val p = prefs(context)
        val seen = JSONArray(p.getString(KEY_SEEN_IDS, "[]"))
        val seenSet = HashSet<String>().also { s -> for (i in 0 until seen.length()) s.add(seen.optString(i)) }
        val list = _received.value.toMutableList()
        for (i in 0 until mine.length()) {
            val e = mine.optJSONObject(i) ?: continue
            val id = e.optString("id", "")
            if (id.isEmpty() || id in seenSet) continue
            seenSet.add(id); seen.put(id)
            val from = devices.optJSONObject(e.optString("from", ""))?.optString("name", "")?.ifBlank { null } ?: "Another device"
            list.add(ReceivedTab(id, from, e.optString("t", "").ifBlank { e.optString("u", "") }, e.optString("u", "")))
        }
        inbox.remove(myId)
        val trimmedSeen = JSONArray()
        val start = maxOf(0, seen.length() - 300)
        for (i in start until seen.length()) trimmedSeen.put(seen.optString(i))
        // NowBar: announce tabs that arrived from another device (new ids only).
        try {
            val known = _received.value.map { it.id }.toSet()
            list.filter { it.id !in known }.take(1).forEach { t ->
                com.petal.browser.live.PetalLiveActionBus.postSimple(
                    com.petal.browser.live.LiveEventKind.TAB_RECEIVED, "tab_received",
                    "Tab received", t.title.ifBlank { t.url }.take(60)
                )
            }
        } catch (_: Throwable) {}
        _received.value = list
        p.edit().putString(KEY_SEEN_IDS, trimmedSeen.toString()).putString(KEY_RECEIVED, receivedToJson(list).toString()).apply()
    }

    private fun deliverOutbox(context: Context, inbox: JSONObject, myId: String, devices: JSONObject, now: Long) {
        val pending = JSONArray(prefs(context).getString(KEY_OUTBOX, "[]"))
        for (i in 0 until pending.length()) {
            val e = pending.optJSONObject(i) ?: continue
            val to = e.optString("to", "")
            if (!devices.has(to)) continue
            val arr = inbox.optJSONArray(to) ?: JSONArray().also { inbox.put(to, it) }
            arr.put(JSONObject().put("id", e.optString("id")).put("t", e.optString("t")).put("u", e.optString("u")).put("from", myId).put("ts", e.optLong("ts", now)))
        }
        // Drop entries nobody picked up within a week.
        val targets = ArrayList<String>()
        for (k in inbox.keys()) targets.add(k)
        for (k in targets) {
            val src = inbox.optJSONArray(k) ?: continue
            val kept = JSONArray()
            for (i in 0 until src.length()) {
                val e = src.optJSONObject(i) ?: continue
                if (now - e.optLong("ts", now) < INBOX_RETENTION_MS) kept.put(e)
            }
            if (kept.length() == 0) inbox.remove(k) else inbox.put(k, kept)
        }
    }

    private fun publishDevices(context: Context, devices: JSONObject, myId: String) {
        val list = ArrayList<SyncDevice>()
        for (id in devices.keys()) {
            if (id == myId) continue
            val d = devices.optJSONObject(id) ?: continue
            val tabs = ArrayList<SyncTab>()
            val ta = d.optJSONArray("tabs")
            if (ta != null) for (i in 0 until ta.length()) {
                val t = ta.optJSONObject(i) ?: continue
                tabs.add(SyncTab(t.optString("t", "").ifBlank { t.optString("u", "") }, t.optString("u", "")))
            }
            list.add(SyncDevice(id, d.optString("name", "Unknown device"), d.optLong("seen", 0L), tabs))
        }
        list.sortByDescending { it.lastSeen }
        _devices.value = list
        prefs(context).edit().putString(KEY_DEVICES_CACHE, devicesToJson(list).toString()).apply()
    }

    private fun devicesToJson(list: List<SyncDevice>): JSONArray {
        val arr = JSONArray()
        for (d in list) {
            val tabs = JSONArray()
            d.tabs.forEach { tabs.put(JSONObject().put("t", it.title).put("u", it.url)) }
            arr.put(JSONObject().put("id", d.id).put("name", d.name).put("seen", d.lastSeen).put("tabs", tabs))
        }
        return arr
    }

    private fun parseDevices(json: String?): List<SyncDevice> = try {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            val ta = d.optJSONArray("tabs")
            val tabs = if (ta == null) emptyList() else (0 until ta.length()).mapNotNull { j ->
                val t = ta.optJSONObject(j) ?: return@mapNotNull null
                SyncTab(t.optString("t", ""), t.optString("u", ""))
            }
            SyncDevice(d.optString("id"), d.optString("name"), d.optLong("seen", 0L), tabs)
        }
    } catch (_: Exception) { emptyList() }

    private fun receivedToJson(list: List<ReceivedTab>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("from", it.from).put("t", it.title).put("u", it.url)) }
        return arr
    }

    private fun parseReceived(json: String?): List<ReceivedTab> = try {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            ReceivedTab(e.optString("id"), e.optString("from"), e.optString("t"), e.optString("u"))
        }
    } catch (_: Exception) { emptyList() }

    // ── background schedule ───────────────────────────────────────────────────

    private fun schedulePeriodic(context: Context) {
        if (!isOn(context, KEY_AUTO)) { cancelPeriodic(context); return }
        try {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, PetalSyncJobService::class.java))
                .setPeriodic(AUTO_INTERVAL_MS)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build()
            js.schedule(info)
        } catch (_: Exception) {}
    }

    private fun cancelPeriodic(context: Context) {
        try { context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID) } catch (_: Exception) {}
    }

    /** Re-evaluates the background job after the "sync automatically" switch changes. */
    fun onAutoChanged(context: Context) {
        val app = context.applicationContext
        if (_configured.value) schedulePeriodic(app) else cancelPeriodic(app)
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
