package com.petal.browser.compose.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Result of scaling and encoding an image for multimodal AI chat requests.
 */
data class EncodedAiImage(
    val bitmap: Bitmap,
    val base64: String,
    val mimeType: String = "image/jpeg"
)

/**
 * Shared image decode, EXIF-orientation correction, downscaling, and JPEG/Base64 encoding helper
 * used by both gallery image attachments and active-page screenshot attachments.
 */
object PetalAiImageEncoder {

    const val DEFAULT_MAX_DIMENSION = 1024
    const val DEFAULT_JPEG_QUALITY = 85

    /**
     * Decodes [uri] off the main thread by reading bounds and EXIF orientation first, sampling near
     * [maxDim] pixels on the longest side, applying EXIF rotation/flip, and JPEG/Base64 encoding.
     */
    suspend fun decodeAndEncodeUri(
        context: Context,
        uri: Uri,
        maxDim: Int = DEFAULT_MAX_DIMENSION,
        jpegQuality: Int = DEFAULT_JPEG_QUALITY
    ): EncodedAiImage? = withContext(Dispatchers.IO) {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            try {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } ?: ExifInterface.ORIENTATION_NORMAL

        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val origWidth = boundsOpts.outWidth
        val origHeight = boundsOpts.outHeight
        if (origWidth <= 0 || origHeight <= 0) {
            return@withContext null
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(origWidth, origHeight, maxDim)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@withContext null

        val oriented = applyExifOrientation(decoded, orientation)
        scaleAndEncodeBitmap(
            bitmap = oriented,
            maxDim = maxDim,
            jpegQuality = jpegQuality,
            recycleSource = true
        )
    }

    /**
     * Scales [bitmap] so its longest side does not exceed [maxDim] and encodes it as JPEG Base64
     * on [Dispatchers.IO].
     */
    suspend fun encodeBitmap(
        bitmap: Bitmap,
        maxDim: Int = DEFAULT_MAX_DIMENSION,
        jpegQuality: Int = DEFAULT_JPEG_QUALITY,
        recycleSource: Boolean = false
    ): EncodedAiImage? = withContext(Dispatchers.IO) {
        scaleAndEncodeBitmap(
            bitmap = bitmap,
            maxDim = maxDim,
            jpegQuality = jpegQuality,
            recycleSource = recycleSource
        )
    }

    internal fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = maxOf(width, height)
        while (longest / (inSampleSize * 2) >= maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        if (matrix.isIdentity) return bitmap

        val transformed = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        if (transformed !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun scaleAndEncodeBitmap(
        bitmap: Bitmap,
        maxDim: Int,
        jpegQuality: Int,
        recycleSource: Boolean
    ): EncodedAiImage? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        val width = bitmap.width
        val height = bitmap.height
        val scaled = if (width > maxDim || height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
            val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        if (recycleSource && scaled !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return EncodedAiImage(
            bitmap = scaled,
            base64 = base64,
            mimeType = "image/jpeg"
        )
    }
}
