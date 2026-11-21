package com.petal.browser.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException

/**
 * The one place that writes finished files into the public Downloads collection.
 *
 * Used by every engine that produces a file itself (HLS/DASH stream remux, yt-dlp). The engines
 * build their output in app-private cache first, then call [publish]:
 *  - Android 10+ : a `MediaStore.Downloads` row is inserted with IS_PENDING=1 and the category
 *    RELATIVE_PATH (when smart routing is on), the bytes are streamed in, and IS_PENDING is
 *    cleared only on success (atomic finish: other apps never see a half-written file; a failure
 *    deletes the row). This needs no storage permission and works in the Play flavor, which
 *    strips MANAGE_EXTERNAL_STORAGE.
 *  - Android 9-  : a plain file in the (category) Downloads directory, written to a `.part` name
 *    and renamed on success.
 */
object PetalDownloadStorage {
    private const val TAG = "PetalDownloadStorage"

    class Published(val uri: Uri, val displayName: String, val sizeBytes: Long, val relativePath: String?)

    @JvmStatic
    fun smartRoutingEnabled(context: Context): Boolean = try {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(PetalSmartDownloadRouter.PREF_SMART_ROUTING, false)
    } catch (_: Throwable) {
        false
    }

    /**
     * MediaStore RELATIVE_PATH for a file: `Download/` normally, `Download/Petal/<Category>/`
     * when smart routing is on. Pure, so it is unit-testable.
     */
    @JvmStatic
    fun relativePathFor(smartEnabled: Boolean, fileName: String, mimeType: String?): String =
        if (smartEnabled) PetalSmartDownloadRouter.getRelativePath(fileName, mimeType) else "Download/"

    /** Copies [source] into public Downloads and deletes it. Throws on failure (nothing is left behind). */
    @JvmStatic
    @Throws(IOException::class)
    fun publish(context: Context, source: File, fileName: String, mimeType: String?): Published {
        val app = context.applicationContext
        val mime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val smart = smartRoutingEnabled(app)
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(app, source, fileName, mime, relativePathFor(smart, fileName, mime))
        } else {
            publishLegacy(app, source, fileName, mime, smart)
        }
        runCatching { source.delete() }
        return result
    }

    /** Like [publish] but into an explicit RELATIVE_PATH (e.g. extracted archive folders). */
    @JvmStatic
    @Throws(IOException::class)
    fun publishTo(context: Context, source: File, fileName: String, mimeType: String?, relativePath: String): Published {
        val app = context.applicationContext
        val mime = mimeType?.takeIf { it.isNotBlank() }
            ?: android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(app, source, fileName, mime, relativePath)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativePath.removePrefix("Download/"))
            dir.mkdirs()
            val target = uniqueFile(dir, fileName)
            source.copyTo(target, overwrite = false)
            Published(Uri.fromFile(target), target.name, target.length(), null)
        }
        return result
    }

    private fun publishViaMediaStore(app: Context, source: File, name: String, mime: String, relativePath: String): Published {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create a Downloads entry")
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                source.inputStream().use { it.copyTo(out, 256 * 1024) }
            } ?: throw IOException("Could not open the Downloads entry for writing")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw if (t is IOException) t else IOException(t.message, t)
        }
        // The system may have de-duplicated the name ("file (1).mp4"); report the real one.
        var finalName = name
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) finalName = it.getString(0) ?: name
            }
        }
        return Published(uri, finalName, source.length(), relativePath)
    }

    private fun publishLegacy(app: Context, source: File, name: String, mime: String, smart: Boolean): Published {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = if (smart) File(base, PetalSmartDownloadRouter.getCategory(name, mime).subfolder) else base
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) throw IOException("Cannot create ${dir.path}")
        val target = uniqueFile(dir, name)
        val part = File(dir, target.name + ".part")
        try {
            source.inputStream().use { input -> part.outputStream().use { input.copyTo(it, 256 * 1024) } }
            if (!part.renameTo(target)) throw IOException("Cannot finish ${target.name}")
        } catch (t: Throwable) {
            runCatching { part.delete() }
            throw if (t is IOException) t else IOException(t.message, t)
        }
        runCatching {
            android.media.MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), arrayOf(mime), null)
        }
        return Published(Uri.fromFile(target), target.name, target.length(), null)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    /**
     * Smart routing for Mozilla's own writer on Android 10+: AbstractFetchDownloadService writes into
     * `Download/` through MediaStore and cannot be pointed at a sub-folder, so after COMPLETED we
     * ask MediaStore to move the app's own row by updating RELATIVE_PATH. Best effort: any failure
     * leaves the file where it is.
     *
     * @return the new directory path on success, or null when nothing was moved.
     */
    @JvmStatic
    fun relocateCompleted(context: Context, fileName: String, mimeType: String?, currentFilePath: String?, force: Boolean = false): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val app = context.applicationContext
        if (!force && !smartRoutingEnabled(app)) return null
        val target = relativePathFor(true, fileName, mimeType)
        return try {
            val resolver = app.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = if (currentFilePath != null) "${MediaStore.MediaColumns.DATA} = ?" else
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val args = if (currentFilePath != null) arrayOf(currentFilePath) else arrayOf(fileName, "Download/")
            var id: Long? = null
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH), selection, args, null)?.use {
                if (it.moveToFirst()) {
                    if (it.getString(1) == target) return null // already in place
                    id = it.getLong(0)
                }
            }
            val rowId = id ?: return null
            val uri = android.content.ContentUris.withAppendedId(collection, rowId)
            val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, target) }
            val updated = resolver.update(uri, values, null, null)
            if (updated > 0) {
                File(Environment.getExternalStorageDirectory(), target.trimEnd('/')).absolutePath
            } else null
        } catch (t: Throwable) {
            Log.w(TAG, "Smart-routing relocation failed: ${t.message}")
            null
        }
    }
}
