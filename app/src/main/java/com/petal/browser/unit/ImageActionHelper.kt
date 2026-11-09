package com.petal.browser.unit

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.petal.browser.download.PetalDownloadRouter
import com.petal.browser.download.PetalGeckoCookieBridge
import com.petal.browser.download.SafeDownloadValues
import com.petal.browser.view.PetalToast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

object ImageActionHelper {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @JvmStatic
    fun downloadImage(context: Context?, imageUrl: String?) {
        downloadImage(context, imageUrl, null, false)
    }

    @JvmStatic
    fun downloadImage(context: Context?, imageUrl: String?, referrerUrl: String?, isPrivate: Boolean) {
        if (context == null || imageUrl.isNullOrBlank()) return

        try {
            val scheme = Uri.parse(imageUrl).scheme
            if ("http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true)) {
                Thread {
                    val resolvedFileName: String = try {
                        val headBuilder = Request.Builder().url(imageUrl).head()
                        if (!referrerUrl.isNullOrBlank()) {
                            headBuilder.header("Referer", referrerUrl.trim())
                        }
                        val cookies = PetalGeckoCookieBridge.getCookies(imageUrl, isPrivate)
                        if (!cookies.isNullOrBlank()) {
                            headBuilder.header("Cookie", cookies.trim())
                        }
                        val headRequest = headBuilder.build()
                        val response = okHttpClient.newCall(headRequest).execute()
                        val contentDisposition = response.header("Content-Disposition")
                        val contentType = response.header("Content-Type")
                        response.close()
                        SafeDownloadValues.fileName(
                            imageUrl,
                            contentDisposition,
                            contentType ?: "image/jpeg"
                        )
                    } catch (_: Exception) {
                        var fallback = URLUtil.guessFileName(imageUrl, null, "image/jpeg")
                        if (!fallback.contains(".")) {
                            fallback += ".jpg"
                        }
                        fallback
                    }
                    val finalFileName = resolvedFileName
                    PetalDownloadRouter.download(
                        context,
                        imageUrl,
                        finalFileName,
                        "image/*",
                        referrerUrl,
                        isPrivate,
                        null,
                        null,
                        Collections.emptyMap(),
                        null,
                        null
                    )
                    if (context is Activity) {
                        context.runOnUiThread {
                            PetalToast.show(context, "Image download started")
                        }
                    }
                }.start()
            } else {
                // Handle Base64 or local URIs directly - no HTTP headers to fetch.
                var fileName = URLUtil.guessFileName(imageUrl, null, "image/jpeg")
                if (!fileName.contains(".")) {
                    fileName += ".jpg"
                }
                downloadImageDirectly(context, imageUrl, fileName)
            }
        } catch (e: Exception) {
            PetalToast.show(context, "Failed to download image: ${e.localizedMessage}")
        }
    }

    private fun downloadImageDirectly(context: Context, imageUrl: String, fileName: String) {
        Thread {
            try {
                val bytes = getImageBytes(context, imageUrl)
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, fileName)
                FileOutputStream(destFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }

                // Scan file to MediaStore
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(destFile)
                }
                context.sendBroadcast(intent)

                if (context is Activity) {
                    context.runOnUiThread {
                        PetalToast.show(context, "Image saved to Downloads")
                    }
                }
            } catch (_: Exception) {
                if (context is Activity) {
                    context.runOnUiThread {
                        PetalToast.show(context, "Failed to save image")
                    }
                }
            }
        }.start()
    }

    @JvmStatic
    fun shareImage(context: Context?, imageUrl: String?) {
        if (context == null || imageUrl.isNullOrBlank()) return

        PetalToast.show(context, "Preparing image for sharing...")

        Thread {
            try {
                val bytes = getImageBytes(context, imageUrl)
                val cacheDir = File(context.cacheDir, "shared_images")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val shareFile = File(cacheDir, "shared_image_${System.currentTimeMillis()}.jpg")
                FileOutputStream(shareFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    shareFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Image").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
                if (context is Activity) {
                    context.runOnUiThread {
                        PetalToast.show(context, "Failed to share image")
                    }
                }
            }
        }.start()
    }

    @JvmStatic
    fun copyImage(context: Context?, imageUrl: String?) {
        if (context == null || imageUrl.isNullOrBlank()) return

        Thread {
            try {
                val bytes = getImageBytes(context, imageUrl)

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val mime = bounds.outMimeType ?: "image/jpeg"
                val ext = when (mime) {
                    "image/png" -> ".png"
                    "image/webp" -> ".webp"
                    "image/gif" -> ".gif"
                    else -> ".jpg"
                }

                val cacheDir = File(context.cacheDir, "shared_images")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val old = cacheDir.listFiles { _, name -> name.startsWith("copied_image_") }
                old?.forEach { it.delete() }
                val file = File(cacheDir, "copied_image_${System.currentTimeMillis()}$ext")
                FileOutputStream(file).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file
                )

                Handler(Looper.getMainLooper()).post {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null) {
                            val clip = ClipData.newUri(context.contentResolver, "image", contentUri)
                            clipboard.setPrimaryClip(clip)
                            PetalToast.show(context, "Image copied")
                        }
                    } catch (_: Exception) {
                        PetalToast.show(context, "Couldn't copy image")
                    }
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    PetalToast.show(context, "Couldn't copy image")
                }
            }
        }.start()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getImageBytes(context: Context, imageUrl: String): ByteArray {
        return if (imageUrl.startsWith("data:image")) {
            val base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1)
            Base64.decode(base64Data, Base64.DEFAULT)
        } else if (imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
            val uri = Uri.parse(imageUrl)
            val isStream = context.contentResolver.openInputStream(uri) ?: throw RuntimeException("Null stream for $imageUrl")
            isStream.use { readAllBytesCompat(it) }
        } else {
            val request = Request.Builder().url(imageUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw RuntimeException("HTTP " + response.code)
            }
            val body = response.body ?: throw RuntimeException("Null response body")
            val bytes = body.bytes()
            response.close()
            bytes
        }
    }

    private fun readAllBytesCompat(isStream: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        var nRead: Int
        val data = ByteArray(8192)
        while (isStream.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        return buffer.toByteArray()
    }
}
