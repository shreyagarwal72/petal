/*
 * BookmarkHtmlImporterExporter.kt
 * ─────────────────────────────────────────────────────────────────────────
 * JSON Bookmark Importer and Exporter for Petal Browser.
 *
 * Implements:
 *   • JSON Export/Import for Petal's bookmark format
 *   • Scoped Storage (SAF / MediaStore / InputStream / OutputStream) integration
 *   • Duplicate URL detection and database transaction batching
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.unit

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.view.NinjaToast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

object BookmarkHtmlImporterExporter {
    private const val TAG = "BookmarkImportExport"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Internal data structure for JSON backup/export
     */
    data class BookmarkJsonItem(
        val title: String,
        val url: String,
        val time: Long = 0L,
        val iconColor: Long = 1L
    )

    data class BookmarkJsonBackup(
        val version: Int = 1,
        val app: String = "Petal Browser",
        val exportedAt: Long = System.currentTimeMillis(),
        val count: Int = 0,
        val bookmarks: List<BookmarkJsonItem> = emptyList()
    )

    /**
     * Generates modern JSON backup string from bookmarks.
     * Uses explicit getURL() to avoid Kotlin JVM property synthesis issues with getURL() vs getUrl().
     */
    fun exportToJsonString(bookmarks: List<Record>): String {
        val items = bookmarks.mapNotNull { record ->
            val u = record.getURL()?.trim() ?: return@mapNotNull null
            if (u.isEmpty() || u.equals("about:blank", ignoreCase = true)) return@mapNotNull null
            val t = record.title?.trim()?.ifEmpty { u } ?: u
            val bookmarkTime = when {
                record.time > 0 -> record.time
                record.iconColor > 0 -> record.iconColor
                else -> System.currentTimeMillis()
            }
            BookmarkJsonItem(
                title = t,
                url = u,
                time = bookmarkTime,
                iconColor = record.iconColor
            )
        }
        val backup = BookmarkJsonBackup(
            version = 1,
            app = "Petal Browser",
            exportedAt = System.currentTimeMillis(),
            count = items.size,
            bookmarks = items
        )
        return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(backup)
    }

    /**
     * Exports all bookmarks to a target Storage Access Framework (SAF) Uri as JSON.
     */
    @JvmOverloads
    fun exportToUri(context: Context, destinationUri: Uri, format: String = "json", onComplete: ((Boolean, Int) -> Unit)? = null) {
        executor.execute {
            try {
                val action = RecordAction(context)
                action.open(false)
                val bookmarks = action.listBookmark(context, false, 0)
                action.close()

                val validBookmarks = bookmarks.filter {
                    val u = it.getURL()?.trim() ?: ""
                    u.isNotEmpty() && !u.equals("about:blank", ignoreCase = true)
                }

                if (validBookmarks.isEmpty()) {
                    mainHandler.post {
                        NinjaToast.show(context, "No bookmarks found to export")
                        onComplete?.invoke(false, 0)
                    }
                    return@execute
                }

                val content = exportToJsonString(validBookmarks)

                // Open output stream with write fallback ("rwt" -> "wt" -> "w")
                val outputStream = try {
                    // SAF providers commonly create a zero-byte document for rwt and
                    // do not expose a seekable FileDescriptor. Plain write mode is
                    // portable across DocumentsProvider implementations.
                    context.contentResolver.openOutputStream(destinationUri, "w")
                } catch (e: Exception) {
                    null
                } ?: try {
                    context.contentResolver.openOutputStream(destinationUri, "wt")
                } catch (e: Exception) {
                    null
                } ?: try {
                    context.contentResolver.openOutputStream(destinationUri, "w")
                } catch (e: Exception) {
                    null
                } ?: throw IllegalStateException("Could not open destination storage stream")

                val bytes = content.toByteArray(Charsets.UTF_8)
                outputStream.use { os ->
                    os.write(bytes)
                    os.flush()
                    try {
                        (os as? java.io.FileOutputStream)?.fd?.sync()
                    } catch (_: Exception) {}
                }

                Log.i(TAG, "Exported ${validBookmarks.size} bookmarks to JSON: $destinationUri (${bytes.size} bytes)")
                mainHandler.post {
                    NinjaToast.show(context, "Exported ${validBookmarks.size} bookmarks successfully")
                    onComplete?.invoke(true, validBookmarks.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export bookmarks", e)
                mainHandler.post {
                    NinjaToast.show(context, "Export failed: ${e.message}")
                    onComplete?.invoke(false, 0)
                }
            }
        }
    }

    /**
     * Parses JSON bookmark file and imports records into the bookmarks database.
     */
    @JvmOverloads
    fun importFromUri(context: Context, sourceUri: Uri, onComplete: ((Boolean, Int) -> Unit)? = null) {
        executor.execute {
            try {
                val rawContent = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
                } ?: throw IllegalStateException("Could not open file stream")

                val trimmed = rawContent.trim()
                val parsedRecords = parseJsonBookmarks(trimmed)

                if (parsedRecords.isEmpty()) {
                    mainHandler.post {
                        NinjaToast.show(context, "No valid bookmarks found in file")
                        onComplete?.invoke(false, 0)
                    }
                    return@execute
                }

                val action = RecordAction(context)
                action.open(true)
                var importedCount = 0

                for (record in parsedRecords) {
                    val url = record.getURL() ?: continue
                    if (!action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) {
                        action.addBookmark(record)
                        importedCount++
                    }
                }
                action.close()

                Log.i(TAG, "Imported $importedCount / ${parsedRecords.size} bookmarks from: $sourceUri")
                mainHandler.post {
                    NinjaToast.show(context, "Imported $importedCount bookmarks (Total: ${parsedRecords.size})")
                    onComplete?.invoke(true, importedCount)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import bookmarks", e)
                mainHandler.post {
                    NinjaToast.show(context, "Import failed: ${e.message}")
                    onComplete?.invoke(false, 0)
                }
            }
        }
    }

    private fun parseJsonBookmarks(content: String): List<Record> {
        val list = ArrayList<Record>()
        try {
            if (content.startsWith("{")) {
                val jsonObject = com.google.gson.JsonParser.parseString(content).asJsonObject
                if (jsonObject.has("bookmarks")) {
                    val array = jsonObject.getAsJsonArray("bookmarks")
                    for (element in array) {
                        if (element.isJsonObject) {
                            val obj = element.asJsonObject
                            val url = obj.get("url")?.asString?.trim() ?: continue
                            if (url.isEmpty() || url.equals("about:blank", ignoreCase = true)) continue
                            val title = obj.get("title")?.asString?.trim()?.ifEmpty { url } ?: url
                            val time = obj.get("time")?.asLong ?: System.currentTimeMillis()
                            val iconColor = obj.get("iconColor")?.asLong ?: 1L
                            val record = Record()
                            record.setURL(url)
                            record.title = title
                            record.setTime(time)
                            record.setIconColor(iconColor)
                            list.add(record)
                        }
                    }
                }
            } else if (content.startsWith("[")) {
                val array = com.google.gson.JsonParser.parseString(content).asJsonArray
                for (element in array) {
                    if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        val url = obj.get("url")?.asString?.trim() ?: continue
                        if (url.isEmpty() || url.equals("about:blank", ignoreCase = true)) continue
                        val title = obj.get("title")?.asString?.trim()?.ifEmpty { url } ?: url
                        val time = obj.get("time")?.asLong ?: System.currentTimeMillis()
                        val iconColor = obj.get("iconColor")?.asLong ?: 1L
                        val record = Record()
                        record.setURL(url)
                        record.title = title
                        record.setTime(time)
                        record.setIconColor(iconColor)
                        list.add(record)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parsing failed", e)
        }
        return list
    }
}
