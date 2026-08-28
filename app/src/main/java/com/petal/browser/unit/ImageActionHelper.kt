package com.petal.browser.unit

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.petal.browser.view.NinjaToast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object ImageActionHelper {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @JvmStatic
    fun downloadImage(context: Context?, imageUrl: String?) {
        if (context == null || imageUrl.isNullOrBlank()) {
            return
        }

        try {
            var fileName = URLUtil.guessFileName(imageUrl, null, "image/jpeg")
            if (!fileName.contains(".")) {
                fileName += ".jpg"
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                val request = DownloadManager.Request(Uri.parse(imageUrl)).apply {
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    allowScanningByMediaScanner()
                }
                downloadManager.enqueue(request)
                NinjaToast.show(context, "Image download started")
            } else {
                downloadImageDirectly(context, imageUrl, fileName)
            }
        } catch (e: Exception) {
            NinjaToast.show(context, "Failed to download image: ${e.localizedMessage}")
        }
    }

    private fun downloadImageDirectly(context: Context, imageUrl: String, fileName: String) {
        Thread {
            try {
                val bytes = getImageBytes(context, imageUrl)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
                (context as? android.app.Activity)?.runOnUiThread {
                    NinjaToast.show(context, "Image saved to Downloads: $fileName")
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    NinjaToast.show(context, "Failed to save image: ${e.localizedMessage}")
                }
            }
        }.start()
    }

    @JvmStatic
    fun shareImage(context: Context?, imageUrl: String?) {
        if (context == null || imageUrl.isNullOrBlank()) return

        Thread {
            try {
                val bytes = getImageBytes(context, imageUrl)
                val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
                val file = File(cacheDir, "shared_image_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }

                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Image").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    NinjaToast.show(context, "Failed to share image: ${e.localizedMessage}")
                }
            }
        }.start()
    }

    @Throws(Exception::class)
    private fun getImageBytes(context: Context, imageUrl: String): ByteArray {
        if (imageUrl.startsWith("data:image/")) {
            val base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1)
            return Base64.decode(base64Data, Base64.DEFAULT)
        }

        val request = Request.Builder().url(imageUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw Exception("HTTP ${response.code}")
            }
            return body.bytes()
        }
    }
}
