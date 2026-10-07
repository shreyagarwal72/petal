package com.petal.browser.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.view.View
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PetalWebpageCaptureEngine
 * ─────────────────────────────────────────────────────────────────────────
 * High-performance full-page screenshot capture engine.
 * Renders scrolling webviews into full-resolution PNG or PDF documents
 * directly saved to the user's Downloads or external documents directory.
 */
object PetalWebpageCaptureEngine {

    data class CaptureResult(
        val success: Boolean,
        val filePath: String? = null,
        val fileUri: String? = null,
        val errorMessage: String? = null
    )

    fun captureViewToBitmap(view: View): Bitmap? {
        return try {
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmapToPng(
        context: Context,
        bitmap: Bitmap,
        siteTitle: String = "webpage"
    ): CaptureResult {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cleanTitle = siteTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val fileName = "Petal_${cleanTitle}_$timestamp.png"

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            CaptureResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = file.toURI().toString()
            )
        } catch (e: Exception) {
            CaptureResult(
                success = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    fun saveBitmapToPdf(
        context: Context,
        bitmap: Bitmap,
        siteTitle: String = "webpage"
    ): CaptureResult {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cleanTitle = siteTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val fileName = "Petal_${cleanTitle}_$timestamp.pdf"

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = document.startPage(pageInfo)

            val canvas = page.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
                out.flush()
            }
            document.close()

            CaptureResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = file.toURI().toString()
            )
        } catch (e: Exception) {
            CaptureResult(
                success = false,
                errorMessage = e.localizedMessage
            )
        }
    }
}
