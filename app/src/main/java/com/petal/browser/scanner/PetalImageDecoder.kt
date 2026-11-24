package com.petal.browser.scanner

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * High-performance image decoding helper for gallery photos, shared images, and clipboard.
 * Features:
 * - Subsampled background decoding with memory bounds
 * - EXIF orientation rotation
 * - Inverted luminance retry
 * - Multi-code detection
 * - Clear empty result (no external fallback)
 */
object PetalImageDecoder {

    private val supportedFormats = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417,
        BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.CODE_128,
        BarcodeFormat.ITF,
        BarcodeFormat.CODABAR
    )

    private val hints = mapOf<DecodeHintType, Any>(
        DecodeHintType.POSSIBLE_FORMATS to supportedFormats,
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.ALSO_INVERTED to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    /**
     * Decodes QR / Barcodes from an image URI (e.g. Photo Picker, MediaStore, or Shared Intent).
     */
    suspend fun decodeUri(context: Context, uri: Uri, maxDimension: Int = 1600): List<RawBarcodeItem> {
        return withContext(Dispatchers.IO) {
            val bitmap = decodeSampledAndOrientedBitmap(context, uri, maxDimension) ?: return@withContext emptyList()
            decodeBitmap(bitmap)
        }
    }

    /**
     * Decodes barcodes directly from an in-memory [Bitmap].
     */
    suspend fun decodeBitmap(bitmap: Bitmap): List<RawBarcodeItem> {
        return withContext(Dispatchers.Default) {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = MultiFormatReader().apply { setHints(hints) }
            val multiReader = GenericMultipleBarcodeReader(reader)

            val results: Array<Result> = try {
                multiReader.decodeMultiple(binaryBitmap, hints)
            } catch (_: NotFoundException) {
                try {
                    arrayOf(reader.decodeWithState(binaryBitmap))
                } catch (_: NotFoundException) {
                    // Try inverted source if standard decode failed
                    try {
                        val inverted = BinaryBitmap(HybridBinarizer(source.invert()))
                        arrayOf(reader.decodeWithState(inverted))
                    } catch (_: NotFoundException) {
                        emptyArray()
                    }
                }
            } finally {
                reader.reset()
            }

            results.mapNotNull { res ->
                val text = res.text ?: return@mapNotNull null
                RawBarcodeItem(
                    text = text,
                    formatName = res.barcodeFormat.name,
                    isSmallBarcode = false
                )
            }.distinctBy { it.text }
        }
    }

    /**
     * Inspects clipboard for available image URI or image clip.
     */
    fun getClipboardImageUri(context: Context): Uri? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        val description = cm.primaryClipDescription ?: return null
        if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) ||
            description.hasMimeType("image/*")
        ) {
            val item = clip.getItemAt(0)
            return item.uri
        }
        return null
    }

    /**
     * Inspects clipboard for text content that might be a code or link.
     */
    fun getClipboardText(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text?.toString()?.trim()
        return if (!text.isNullOrBlank()) text else null
    }

    private fun decodeSampledAndOrientedBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            // 1. Measure dimensions without full decode
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            var inSampleSize = 1
            if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // 2. Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // 3. Apply EXIF orientation
            val orientation = getExifOrientation(context, uri)
            if (orientation != 0) {
                val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
                Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true)
            } else {
                sampledBitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return 0
            val exif = ExifInterface(inputStream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }
}
