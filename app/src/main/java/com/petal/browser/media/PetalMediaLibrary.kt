package com.petal.browser.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// What the website asked for
// ---------------------------------------------------------------------------------------------

/** Where an upload request should be served from. */
enum class PetalUploadRoute {
    /** Only images/videos are accepted: go straight to the Petal media picker. */
    MEDIA,

    /** Only documents/other files are accepted: go straight to the Petal file picker. */
    FILES,

    /** Could be either: let the person choose. */
    ASK,
}

/**
 * Parsed `<input type=file accept=... multiple capture>` request.
 * Also owns the auto-detection that decides which Petal picker to open.
 */
class PetalUploadSpec(
    accept: Array<String>?,
    val allowMultiple: Boolean,
    val captureRequested: Boolean,
) {
    /** Normalised MIME patterns. Extensions (".png") are converted to MIME types where known. */
    val patterns: List<String> = (accept ?: emptyArray())
        .flatMap { it.split(',') }
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .map { token ->
            if (token.startsWith(".")) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(token.removePrefix(".")) ?: token
            } else token
        }
        .distinct()

    /** No restriction at all (`*` or nothing declared). */
    val isOpen: Boolean = patterns.isEmpty() || patterns.any { it == "*/*" || it == "*" }

    val allowsImages: Boolean = isOpen || patterns.any { it.startsWith("image/") }
    val allowsVideos: Boolean = isOpen || patterns.any { it.startsWith("video/") }

    /** Accepts something that is not an image or video (pdf, docs, audio, archives, unknown extension). */
    val hasNonMedia: Boolean = patterns.any {
        it != "*/*" && it != "*" && !it.startsWith("image/") && !it.startsWith("video/")
    }

    fun allows(mime: String?): Boolean {
        if (isOpen) return true
        val m = (mime ?: return false).lowercase(Locale.ROOT)
        return patterns.any { p -> p == m || (p.endsWith("/*") && m.startsWith(p.dropLast(1))) }
    }

    /**
     * Built-in detection:
     *  - camera capture requested, or only image/video types accepted  -> MEDIA
     *  - only documents / audio / archives accepted                    -> FILES
     *  - anything open, or a mix of media and documents                -> ASK
     */
    fun route(): PetalUploadRoute = when {
        captureRequested && (allowsImages || allowsVideos) && !hasNonMedia -> PetalUploadRoute.MEDIA
        isOpen -> PetalUploadRoute.ASK
        hasNonMedia && (allowsImages || allowsVideos) -> PetalUploadRoute.ASK
        hasNonMedia -> PetalUploadRoute.FILES
        else -> PetalUploadRoute.MEDIA
    }

    /** Short human description of what the page accepts, shown in the chooser header. */
    fun describe(): String {
        if (isOpen) return if (allowMultiple) "Any files · multiple allowed" else "Any file"
        val parts = LinkedHashSet<String>()
        patterns.forEach { p ->
            parts += when {
                p == "image/*" -> "Images"
                p == "video/*" -> "Videos"
                p == "audio/*" -> "Audio"
                p.startsWith("image/") -> p.removePrefix("image/").uppercase(Locale.ROOT)
                p.startsWith("video/") -> p.removePrefix("video/").uppercase(Locale.ROOT)
                p == "application/pdf" -> "PDF"
                p.startsWith("text/") -> "Text"
                p.startsWith(".") -> p.removePrefix(".").uppercase(Locale.ROOT)
                else -> p.substringAfterLast('/').uppercase(Locale.ROOT)
            }
        }
        val shown = parts.take(4).joinToString(", ") + if (parts.size > 4) " +${parts.size - 4}" else ""
        return if (allowMultiple) "$shown · multiple allowed" else shown
    }
}

// ---------------------------------------------------------------------------------------------
// Library model
// ---------------------------------------------------------------------------------------------

data class PetalMediaAsset(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    /** Epoch millis of capture (falls back to date added). */
    val takenMs: Long,
    val isVideo: Boolean,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
)

data class PetalMediaAlbum(val id: Long, val name: String, val count: Int)

enum class PetalMediaAccess { NONE, PARTIAL, FULL }

object PetalMediaLibrary {

    /** Runtime permissions to ask for so the picker gets full library access. */
    fun permissionsToRequest(images: Boolean, videos: Boolean): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> buildList {
            if (images) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (videos) add(Manifest.permission.READ_MEDIA_VIDEO)
        }.toTypedArray()
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun accessLevel(context: Context, images: Boolean, videos: Boolean): PetalMediaAccess {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return PetalMediaAccess.FULL
        }
        return when {
            Build.VERSION.SDK_INT >= 33 -> {
                val imagesOk = !images || granted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val videosOk = !videos || granted(context, Manifest.permission.READ_MEDIA_VIDEO)
                val anyFull = (images && granted(context, Manifest.permission.READ_MEDIA_IMAGES)) ||
                    (videos && granted(context, Manifest.permission.READ_MEDIA_VIDEO))
                when {
                    imagesOk && videosOk && (images || videos) -> PetalMediaAccess.FULL
                    Build.VERSION.SDK_INT >= 34 &&
                        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PetalMediaAccess.PARTIAL
                    anyFull -> PetalMediaAccess.PARTIAL
                    else -> PetalMediaAccess.NONE
                }
            }
            granted(context, Manifest.permission.READ_EXTERNAL_STORAGE) -> PetalMediaAccess.FULL
            else -> PetalMediaAccess.NONE
        }
    }

    /** Reads every photo / video the app is allowed to see, newest first. */
    suspend fun load(context: Context, spec: PetalUploadSpec): List<PetalMediaAsset> = withContext(Dispatchers.IO) {
        val result = ArrayList<PetalMediaAsset>()
        if (spec.allowsImages) result += query(context, isVideo = false, spec = spec)
        if (spec.allowsVideos) result += query(context, isVideo = true, spec = spec)
        result.sortByDescending { it.takenMs }
        result
    }

    @Suppress("InlinedApi")
    private fun query(context: Context, isVideo: Boolean, spec: PetalUploadSpec): List<PetalMediaAsset> {
        val base: Uri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN)
            add(if (isVideo) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID)
            add(if (isVideo) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        val out = ArrayList<PetalMediaAsset>()
        try {
            context.contentResolver.query(base, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iTaken = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN)
                val iBucket = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID)
                val iBucketName = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val iDuration = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1

                while (c.moveToNext()) {
                    val mime = c.getString(iMime) ?: (if (isVideo) "video/*" else "image/*")
                    if (!spec.allows(mime)) continue
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000L)
                    out += PetalMediaAsset(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "media_$id",
                        mime = mime,
                        size = c.getLong(iSize),
                        takenMs = taken,
                        isVideo = isVideo,
                        durationMs = if (iDuration >= 0) c.getLong(iDuration) else 0L,
                        bucketId = c.getLong(iBucket),
                        bucketName = c.getString(iBucketName) ?: "Other",
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked mid-query: return what we have.
        } catch (_: Exception) {
        }
        return out
    }

    fun albumsOf(items: List<PetalMediaAsset>): List<PetalMediaAlbum> =
        items.groupBy { it.bucketId }
            .map { (id, list) -> PetalMediaAlbum(id, list.first().bucketName, list.size) }
            .sortedByDescending { it.count }

    // ---- Formatting helpers ------------------------------------------------------------------

    fun formatDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val s = total % 60
        val m = (total / 60) % 60
        val h = total / 3600
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** "Today", "Yesterday", "Mar 4", "Mar 4, 2024". */
    fun dayLabel(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        fun dayKey(c: Calendar) = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        if (dayKey(now) == dayKey(then)) return "Today"
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (dayKey(yesterday) == dayKey(then)) return "Yesterday"
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val base = "${months[then.get(Calendar.MONTH)]} ${then.get(Calendar.DAY_OF_MONTH)}"
        return if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) base else "$base, ${then.get(Calendar.YEAR)}"
    }

    fun dayKey(ts: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR)
    }
}

// ---------------------------------------------------------------------------------------------
// Thumbnails
// ---------------------------------------------------------------------------------------------

object PetalMediaThumbs {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @Suppress("DEPRECATION")
    suspend fun load(context: Context, asset: PetalMediaAsset, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${asset.uri}#$px"
        cache.get(key)?.let { return@withContext it }
        val cr = context.contentResolver
        val bmp: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= 29) {
                cr.loadThumbnail(asset.uri, Size(px, px), null)
            } else if (asset.isVideo) {
                MediaStore.Video.Thumbnails.getThumbnail(cr, asset.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(cr, asset.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
            }
        } catch (_: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        bmp
    }
}
