package com.petal.browser.engine.gecko

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.StorageController
import java.io.File

/**
 * PetalStorageManager
 * ─────────────────────────────────────────────────────────────────────────
 * Official Firefox-style unified storage controller and maintenance engine.
 *
 * Responsibilities:
 * 1. Tiered cache eviction through GeckoView [StorageController.clearData]:
 *    - ALL_CACHES: Clears HTTP web and memory cache
 *    - IMAGE_CACHE: Frees decoded bitmap caches
 *    - DOM_STORAGES: IndexedDB and LocalStorage eviction
 *    - COOKIES: Granular cookie jar purging
 * 2. Ephemeral file purging: sweeps download staging chunks, media temp files,
 *    and file upload buffers.
 * 3. Database health & compaction: executes non-blocking `VACUUM` and `WAL_CHECKPOINT`
 *    on SQLite databases to reclaim disk space and constrain memory cache pages.
 */
object PetalStorageManager {
    private const val TAG = "PetalStorageManager"

    private val bgScope = CoroutineScope(Dispatchers.IO)

    /**
     * Purges temporary upload chunks and ephemeral cache files.
     */
    @JvmStatic
    fun cleanEphemeralStorage(context: Context) {
        bgScope.launch {
            try {
                val cacheDir = context.applicationContext.cacheDir
                cleanDirectory(cacheDir, maxAgeMillis = 24 * 60 * 60 * 1000L) // > 24 hours old

                val externalCache = context.applicationContext.externalCacheDir
                if (externalCache != null && externalCache.exists()) {
                    cleanDirectory(externalCache, maxAgeMillis = 24 * 60 * 60 * 1000L)
                }
                Log.d(TAG, "Completed ephemeral storage sweep")
            } catch (t: Throwable) {
                Log.w(TAG, "Error cleaning ephemeral storage: ${t.message}")
            }
        }
    }

    private fun cleanDirectory(dir: File, maxAgeMillis: Long) {
        val now = System.currentTimeMillis()
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                cleanDirectory(f, maxAgeMillis)
            } else {
                if (f.name.startsWith("upload_") || f.name.startsWith("tmp_") || f.name.endsWith(".tmp") ||
                    (now - f.lastModified()) > maxAgeMillis) {
                    try { f.delete() } catch (_: Throwable) {}
                }
            }
        }
    }

    /**
     * Performs SQLite database compaction (`VACUUM` and WAL checkpoint) in the background.
     */
    @JvmStatic
    fun optimizeDatabases(context: Context) {
        bgScope.launch {
            try {
                val action = com.petal.browser.database.RecordAction(context.applicationContext)
                action.open(true)
                action.optimizeDatabase()
                action.close()
                Log.d(TAG, "Successfully compacted Petal SQLite databases")
            } catch (t: Throwable) {
                Log.w(TAG, "Database optimization skipped or failed: ${t.message}")
            }
        }
    }

    /**
     * Clear fine-grained Gecko storage flags.
     */
    @JvmStatic
    fun clearGeckoStorage(context: Context, flags: Long) {
        try {
            if (PetalGeckoRuntime.isGeckoAvailable(context)) {
                PetalGeckoRuntime.clearData(context, flags)
                Log.i(TAG, "Cleared Gecko storage with flags: $flags")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed clearing Gecko storage flags: ${t.message}")
        }
    }
}
