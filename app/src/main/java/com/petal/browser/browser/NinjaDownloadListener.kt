package com.petal.browser.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebView
import com.google.android.material.snackbar.Snackbar
import com.petal.browser.R
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.NinjaToast
import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class NinjaDownloadListener(private val context: Context, private val webView: WebView) : DownloadListener {

    private fun getExtension(mimeType: String?): String {
        if (mimeType == null) return "bin"
        if (mimeType.contains("pdf")) return "pdf"
        if (mimeType.contains("image/png")) return "png"
        if (mimeType.contains("image/jpeg")) return "jpg"
        if (mimeType.contains("zip")) return "zip"
        return "bin"
    }

    private fun getExtensionFromBytes(bytes: ByteArray?): String {
        if (bytes == null || bytes.size < 4) return "bin"
        val hex = StringBuilder()
        for (i in 0 until 4) {
            hex.append(String.format("%02X", bytes[i]))
        }
        val magic = hex.toString()
        if (magic.startsWith("25504446")) return "pdf"
        if (magic.startsWith("89504E47")) return "png"
        if (magic.startsWith("FFD8FF")) return "jpg"
        if (magic.startsWith("47494638")) return "gif"
        if (magic.startsWith("504B0304")) return "zip"
        return "bin"
    }

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        if (url == null) return

        val now = System.currentTimeMillis()
        if (url == lastHandledUrl && (now - lastHandledTime) < 2000L) {
            return
        }
        lastHandledUrl = url
        lastHandledTime = now

        if (url.startsWith("blob:")) {
            // Blob URL handling via JavaScript extraction
            val js = "javascript:(function() {" +
                    "  var xhr = new XMLHttpRequest();" +
                    "  xhr.open('GET', '" + url + "', true);" +
                    "  xhr.responseType = 'blob';" +
                    "  xhr.onload = function(e) {" +
                    "    if (this.status == 200) {" +
                    "      var blob = this.response;" +
                    "      var reader = new FileReader();" +
                    "      reader.readAsDataURL(blob);" +
                    "      reader.onloadend = function() {" +
                    "        var base64data = reader.result;" +
                    "        window.PetalDownloadInterface.processBlob(base64data, '" + (mimeType ?: "") + "', '" + (URLUtil.guessFileName(url, contentDisposition, mimeType)) + "');" +
                    "      }" +
                    "    }" +
                    "  };" +
                    "  xhr.send();" +
                    "})()"
            webView.loadUrl(js)
            return
        }

        if (url.startsWith("data:")) {
            Executors.newSingleThreadExecutor().execute {
                try {
                    val parts = url.split(",")
                    if (parts.size > 1) {
                        val base64Data = parts[1]
                        val data = Base64.decode(base64Data, Base64.DEFAULT)
                        val ext = getExtensionFromBytes(data)
                        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                        val fileName = "download_" + LocalDateTime.now().format(formatter) + "." + ext
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadsDir, fileName)

                        BufferedOutputStream(Files.newOutputStream(file.toPath())).use { out ->
                            out.write(data)
                            out.flush()
                        }

                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType ?: "*/*"), null)
                        webView.post {
                            NinjaToast.show(context, R.string.toast_download_successful)
                        }
                    }
                } catch (e: Exception) {
                    webView.post {
                        NinjaToast.show(context, R.string.toast_download_failed)
                    }
                }
            }
            return
        }

        // Standard HTTP / HTTPS URL download delegate
        BrowserUnit.download(context, url, contentDisposition, mimeType)
    }

    companion object {
        private var lastHandledUrl: String? = null
        private var lastHandledTime: Long = 0L
    }
}
