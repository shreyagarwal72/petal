package com.petal.browser.download

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.Locale

/**
 * PetalSmartDownloadRouter
 * ─────────────────────────────────────────────────────────────────────────
 * Inspired by Floorp Browser's Smart Download Management.
 * Automatically organizes downloads into dedicated subdirectories based
 * on file extension and MIME type where supported by the operating system.
 */
object PetalSmartDownloadRouter {

    const val PREF_SMART_ROUTING = "sp_smart_download_routing"

    enum class Category(val subfolder: String) {
        IMAGES("Petal/Images"),
        VIDEO("Petal/Video"),
        AUDIO("Petal/Audio"),
        DOCUMENTS("Petal/Documents"),
        PACKAGES("Petal/Apps"),
        GENERAL("Petal")
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico", "avif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "m4v")
    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "ogg", "m4a", "opus", "aac")
    private val DOCUMENT_EXTENSIONS = setOf("pdf", "epub", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")
    private val PACKAGE_EXTENSIONS = setOf("apk", "xapk", "apks", "zip", "tar", "gz", "7z", "rar", "xpi")

    @JvmStatic
    fun getCategory(fileName: String, mimeType: String?): Category {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            IMAGE_EXTENSIONS.contains(ext) || mimeType?.startsWith("image/") == true -> Category.IMAGES
            VIDEO_EXTENSIONS.contains(ext) || mimeType?.startsWith("video/") == true -> Category.VIDEO
            AUDIO_EXTENSIONS.contains(ext) || mimeType?.startsWith("audio/") == true -> Category.AUDIO
            DOCUMENT_EXTENSIONS.contains(ext) || mimeType?.contains("pdf") == true || mimeType?.contains("document") == true -> Category.DOCUMENTS
            PACKAGE_EXTENSIONS.contains(ext) || mimeType?.contains("android.package-archive") == true -> Category.PACKAGES
            else -> Category.GENERAL
        }
    }

    @JvmStatic
    fun getRelativePath(fileName: String, mimeType: String?): String {
        return "Download/${getCategory(fileName, mimeType).subfolder}/"
    }

    /**
     * Directory handed to Mozilla's download writer.
     *
     * Android 10+: always the public Downloads directory. DefaultDownloadFileWriter only activates
     * its MediaStore path for that directory; category folders are applied after completion by
     * [PetalDownloadStorage.relocateCompleted] (and directly by [PetalDownloadStorage.publish] for
     * Petal's own engines). Android 9-: the category directory when smart routing is on.
     */
    @JvmStatic
    fun resolveDestinationDirectory(context: Context, fileName: String, mimeType: String?): File {
        val baseDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return baseDownloads
        if (!PetalDownloadStorage.smartRoutingEnabled(context)) return baseDownloads

        val targetDir = File(baseDownloads, getCategory(fileName, mimeType).subfolder)
        if (!targetDir.exists() && !targetDir.mkdirs()) return baseDownloads
        return if (targetDir.canWrite()) targetDir else baseDownloads
    }
}
