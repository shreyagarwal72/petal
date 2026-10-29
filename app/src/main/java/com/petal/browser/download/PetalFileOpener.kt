package com.petal.browser.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import com.petal.browser.activity.FileViewerActivity
import com.petal.browser.activity.ImageViewerActivity
import com.petal.browser.activity.MediaPlayerActivity
import com.petal.browser.activity.PdfViewerActivity
import com.petal.browser.apk.PetalApkInstallActivity
import java.util.Locale

/**
 * Universal dispatcher for opening files directly using Petal's built-in features:
 * - APK packages -> PetalApkInstallActivity ("Install with Petal")
 * - PDF documents -> PdfViewerActivity ("Petal PDF Viewer")
 * - Video & Audio -> MediaPlayerActivity ("Petal Media Player" with wavy controls)
 * - Images -> ImageViewerActivity ("Petal Image Viewer")
 * - Text, Code, Documents & Archives -> FileViewerActivity ("Petal File Viewer")
 * - Fallback -> Android system ACTION_VIEW
 */
object PetalFileOpener {

    fun createOpenIntent(context: Context, uri: Uri, fileName: String, rawMime: String? = null): Intent {
        val ext = MimeTypeMap.getFileExtensionFromUrl(fileName.ifEmpty { uri.toString() }).lowercase(Locale.ROOT)
        val detectedMime = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else null
        val mime = (rawMime?.takeIf { it.isNotBlank() && it != "*/*" } ?: detectedMime ?: "*/*").lowercase(Locale.ROOT)

        return when {
            // 1. APKs -> Install with Petal
            ext == "apk" || mime == "application/vnd.android.package-archive" -> {
                Intent(context, PetalApkInstallActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            // 2. PDF Documents -> Petal PDF Viewer
            ext == "pdf" || mime == "application/pdf" -> {
                Intent(context, PdfViewerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            // 3. Media (Audio & Video) -> Petal Media Player
            mime.startsWith("video/") || mime.startsWith("audio/") ||
            ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts", "m4v", "wmv",
                         "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "mid", "midi") -> {
                Intent(context, MediaPlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    val targetMime = if (mime != "*/*") mime else if (ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts", "m4v", "wmv")) "video/*" else "audio/*"
                    setDataAndType(uri, targetMime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            // 4. Images -> Petal Image Viewer
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "ico", "heic", "heif", "avif") -> {
                Intent(context, ImageViewerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    val targetMime = if (mime != "*/*") mime else "image/*"
                    setDataAndType(uri, targetMime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            // 5. Text, Code, Documents, Archives -> Petal File Viewer
            mime.startsWith("text/") || mime in setOf(
                "application/json", "application/xml", "application/javascript", "application/x-javascript",
                "application/zip", "application/x-zip-compressed", "application/x-tar", "application/x-7z-compressed",
                "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            ) || ext in setOf(
                "txt", "json", "xml", "html", "htm", "css", "js", "ts", "md", "markdown",
                "log", "csv", "tsv", "py", "java", "kt", "c", "cpp", "h", "hpp", "sh", "properties",
                "yaml", "yml", "toml", "ini", "conf", "zip", "tar", "gz", "7z", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
            ) -> {
                Intent(context, FileViewerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            // 6. Generic Fallback
            else -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }

    fun openFile(context: Context, uri: Uri, fileName: String, rawMime: String? = null) {
        val intent = createOpenIntent(context, uri, fileName, rawMime)
        runCatching { context.startActivity(intent) }
    }
}
