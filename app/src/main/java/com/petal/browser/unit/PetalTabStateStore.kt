package com.petal.browser.unit

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Per-tab persistence for the serialized GeckoView session state
 * (`GeckoSession.SessionState`: back/forward history stack, scroll position, zoom and form data).
 *
 * Why this exists
 * ───────────────
 * [PetalTabSessionManager] only persists each tab's URL. Restoring a tab from a URL alone starts a
 * brand-new single-entry history, so after the app is swiped away and reopened the Back gesture has
 * nothing to go back to and falls straight through to Home. Firefox (Fenix) avoids this by saving the
 * engine session state with every tab and restoring it into the new session; this store does the same.
 *
 * Design
 * ──────
 * - One file per tab (`<tabId>.json`) instead of one big blob in SharedPreferences/SQLite. The
 *   SharedPreferences file is fully loaded into memory at startup, so keeping per-tab history there
 *   would waste RAM on every launch.
 * - Stored in [Context.getNoBackupFilesDir]: browsing history must not be copied to cloud backups.
 * - Writes are atomic (temp file + rename) so a process kill mid-write can never leave a truncated
 *   state that would fail to parse on the next start.
 * - Each file is size-capped; an oversized state is skipped and the tab falls back to URL-only restore.
 * - Orphaned files (tabs that were closed) are removed by [retainOnly] on every session save.
 * - Incognito tabs are never passed to this store.
 */
object PetalTabStateStore {

    private const val TAG = "PetalTabState"
    private const val DIR_NAME = "petal_tab_state"
    private const val EXT = ".json"
    private const val TMP_EXT = ".tmp"

    /** Upper bound for one tab's serialized state. Larger states fall back to URL-only restore. */
    private const val MAX_STATE_BYTES = 1024 * 1024

    private val lock = Any()

    private fun dir(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, DIR_NAME)

    private fun fileName(tabId: String): String? {
        val safe = tabId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return if (safe.isBlank()) null else safe + EXT
    }

    @JvmStatic
    fun write(context: Context, tabId: String?, serializedState: String?) {
        if (tabId.isNullOrBlank() || serializedState.isNullOrBlank()) return
        // Cheap length check first (chars <= bytes for ASCII-dominant JSON; the exact cap is not critical).
        if (serializedState.length > MAX_STATE_BYTES) {
            Log.w(TAG, "Skipping oversized tab state for $tabId (${serializedState.length} chars)")
            return
        }
        val name = fileName(tabId) ?: return
        synchronized(lock) {
            try {
                val directory = dir(context)
                if (!directory.exists() && !directory.mkdirs()) return
                val target = File(directory, name)
                val tmp = File(directory, name + TMP_EXT)
                tmp.writeText(serializedState, Charsets.UTF_8)
                if (!tmp.renameTo(target)) {
                    // Some filesystems refuse to rename over an existing file.
                    target.delete()
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to persist tab state for $tabId: ${t.message}")
            }
        }
    }

    @JvmStatic
    fun read(context: Context, tabId: String?): String? {
        if (tabId.isNullOrBlank()) return null
        val name = fileName(tabId) ?: return null
        synchronized(lock) {
            return try {
                val file = File(dir(context), name)
                if (!file.isFile || file.length() <= 0L || file.length() > MAX_STATE_BYTES * 4L) {
                    null
                } else {
                    file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to read tab state for $tabId: ${t.message}")
                null
            }
        }
    }

    @JvmStatic
    fun delete(context: Context, tabId: String?) {
        if (tabId.isNullOrBlank()) return
        val name = fileName(tabId) ?: return
        synchronized(lock) {
            try {
                File(dir(context), name).delete()
            } catch (_: Throwable) {
            }
        }
    }

    /** Deletes every stored state whose tab id is not in [tabIds] (closed tabs, stale temp files). */
    @JvmStatic
    fun retainOnly(context: Context, tabIds: Collection<String>) {
        val keep = tabIds.mapNotNull { fileName(it) }.toHashSet()
        synchronized(lock) {
            try {
                val files = dir(context).listFiles() ?: return
                for (file in files) {
                    if (file.name !in keep) file.delete()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to prune tab states: ${t.message}")
            }
        }
    }

    @JvmStatic
    fun clearAll(context: Context?) {
        if (context == null) return
        synchronized(lock) {
            try {
                dir(context).listFiles()?.forEach { it.delete() }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to clear tab states: ${t.message}")
            }
        }
    }
}
