package com.petal.browser.download

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Post-download action pipeline: after a download completes, run the actions the user configured
 * for that file type (unzip, move to category folder, media scan, open).
 */
object PetalPostDownload {
    private const val TAG = "PetalPostDownload"

    enum class Action { UNZIP, MOVE_TO_CATEGORY, MEDIA_SCAN, OPEN }

    /** [category] is a [PetalSmartDownloadRouter.Category] name or "ANY". Actions run in the order given. */
    data class Rule(val category: String, val actions: List<Action>)

    @JvmStatic
    fun defaultRules(): List<Rule> = listOf(Rule("ANY", listOf(Action.MEDIA_SCAN)))

    @JvmStatic
    fun parseRules(json: String?): List<Rule> {
        if (json.isNullOrBlank()) return defaultRules()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val acts = o.optJSONArray("actions") ?: return@mapNotNull null
                val parsed = (0 until acts.length()).mapNotNull { j ->
                    runCatching { Action.valueOf(acts.getString(j)) }.getOrNull()
                }
                Rule(o.optString("category", "ANY").uppercase(), parsed)
            }
        } catch (_: Throwable) {
            defaultRules()
        }
    }

    @JvmStatic
    fun rulesToJson(rules: List<Rule>): String = JSONArray().also { arr ->
        rules.forEach { r ->
            arr.put(JSONObject().put("category", r.category).put("actions", JSONArray(r.actions.map { it.name })))
        }
    }.toString()

    /** Actions to run for a file of [category]: rules for ANY first, then the category's own, no duplicates. */
    @JvmStatic
    fun actionsFor(rules: List<Rule>, category: PetalSmartDownloadRouter.Category): List<Action> =
        (rules.filter { it.category == "ANY" } + rules.filter { it.category == category.name })
            .flatMap { it.actions }.distinct()

    /** Strips `..`, absolute paths and drive prefixes from a zip entry name; null if nothing safe is left. */
    @JvmStatic
    fun safeEntryPath(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." } || parts.isEmpty()) return null
        if (parts[0].contains(':')) return null
        return parts.joinToString("/")
    }

    /**
     * Runs the configured actions for a finished download. Call on a background thread.
     * [file] is the readable file (may be a copy in app storage); [name]/[mime] describe it.
     */
    @JvmStatic
    fun run(context: Context, file: File?, uri: Uri?, name: String, mime: String?, rules: List<Rule>, currentFilePath: String?) {
        val app = context.applicationContext
        val category = PetalSmartDownloadRouter.getCategory(name, mime)
        for (action in actionsFor(rules, category)) {
            try {
                when (action) {
                    Action.UNZIP -> if (file != null && name.endsWith(".zip", ignoreCase = true)) unzip(app, file, name)
                    Action.MOVE_TO_CATEGORY -> PetalDownloadStorage.relocateCompleted(app, name, mime, currentFilePath, force = true)
                    Action.MEDIA_SCAN -> if (file != null && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                        android.media.MediaScannerConnection.scanFile(app, arrayOf(file.absolutePath), arrayOf(mime), null)
                    }
                    Action.OPEN -> uri?.let { PetalFileOpener.openFile(app, it, name, mime) }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "$action failed for $name: ${t.message}")
            }
        }
    }

    /** Extracts into `Download/<zip name>/…` through the shared writer, with zip-slip and zip-bomb limits. */
    @Throws(IOException::class)
    private fun unzip(app: Context, zip: File, name: String) {
        val folder = name.substringBeforeLast('.').ifBlank { "extracted" }
        var entries = 0
        var written = 0L
        ZipFile(zip).use { zf ->
            val e = zf.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                if (entry.isDirectory) continue
                val safe = safeEntryPath(entry.name) ?: continue
                if (++entries > MAX_ENTRIES) throw IOException("Too many files in archive")
                val tmp = File.createTempFile("unzip_", ".tmp", app.cacheDir)
                try {
                    zf.getInputStream(entry).use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                written += n
                                if (written > MAX_TOTAL_BYTES) throw IOException("Archive too large to extract")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    val leaf = safe.substringAfterLast('/')
                    val sub = safe.substringBeforeLast('/', "")
                    val rel = "Download/$folder/" + if (sub.isEmpty()) "" else "$sub/"
                    PetalDownloadStorage.publishTo(app, tmp, leaf, null, rel)
                } finally {
                    tmp.delete()
                }
            }
        }
    }

    private const val MAX_ENTRIES = 10_000
    private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
}
