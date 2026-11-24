package com.petal.browser.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import java.util.concurrent.atomic.AtomicBoolean

data class RawBarcodeItem(
    val text: String,
    val formatName: String,
    val isSmallBarcode: Boolean = false
)

/**
 * CameraX analyzer using Y-plane [PlanarYUVLuminanceSource], a reused [MultiFormatReader]
 * with explicit format list, [GenericMultipleBarcodeReader] for multi-code detection,
 * average luminance calculation for low-light torch suggestions, and small-code auto-zoom detection.
 */
class PetalCameraAnalyzer(
    private val isLocked: AtomicBoolean,
    private val onBarcodesDetected: (List<RawBarcodeItem>) -> Unit,
    private val onLowLightDetected: (Boolean) -> Unit,
    private val onAutoZoomSuggested: () -> Unit
) : ImageAnalysis.Analyzer {

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

    private val singleReader = MultiFormatReader().apply {
        setHints(hints)
    }

    private val multipleReader = GenericMultipleBarcodeReader(singleReader)

    @Volatile
    private var lastScannedValue: String? = null
    @Volatile
    private var lastScannedTime: Long = 0L

    // Low-light measurement smoothing
    private var frameCounter = 0

    override fun analyze(image: ImageProxy) {
        // Atomic lock check: skip immediately if analysis is locked (e.g. sheet open or animating)
        if (isLocked.get()) {
            image.close()
            return
        }

        try {
            val plane = image.planes.firstOrNull()
            if (plane == null) {
                image.close()
                return
            }

            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val rowStride = plane.rowStride
            val cropRect = image.cropRect

            // Clamped coordinates to guarantee bounds safety
            val left = cropRect.left.coerceIn(0, (rowStride - 1).coerceAtLeast(0))
            val top = cropRect.top.coerceIn(0, (image.height - 1).coerceAtLeast(0))
            val width = cropRect.width().coerceIn(1, (rowStride - left).coerceAtLeast(1))
            val height = cropRect.height().coerceIn(1, (image.height - top).coerceAtLeast(1))

            // 1. Low light detection: sample every 16th pixel every 6 frames
            if (++frameCounter % 6 == 0) {
                var lumaSum = 0L
                var count = 0
                val step = 16
                for (y in top until (top + height) step step) {
                    val rowStart = y * rowStride
                    for (x in left until (left + width) step step) {
                        val idx = rowStart + x
                        if (idx in bytes.indices) {
                            lumaSum += (bytes[idx].toInt() and 0xFF)
                            count++
                        }
                    }
                }
                if (count > 0) {
                    val avgLuma = lumaSum / count
                    onLowLightDetected(avgLuma < 42)
                }
            }

            // 2. ZXing PlanarYUV Luminance Source
            val source = PlanarYUVLuminanceSource(
                bytes,
                rowStride,
                image.height,
                left,
                top,
                width,
                height,
                false
            )

            val bitmap = BinaryBitmap(HybridBinarizer(source))

            // 3. Multi-code detection with single-code fallback
            val results: Array<Result> = try {
                multipleReader.decodeMultiple(bitmap, hints)
            } catch (_: NotFoundException) {
                try {
                    arrayOf(singleReader.decodeWithState(bitmap))
                } catch (_: NotFoundException) {
                    // Try inverted if not already caught by ALSO_INVERTED
                    try {
                        arrayOf(singleReader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))))
                    } catch (_: NotFoundException) {
                        emptyArray()
                    }
                }
            } finally {
                singleReader.reset()
            }

            if (results.isNotEmpty() && !isLocked.get()) {
                val now = System.currentTimeMillis()
                val primaryValue = results.first().text

                // 1.5 second cooldown for the same value
                if (primaryValue == lastScannedValue && (now - lastScannedTime) < 1500L) {
                    return
                }

                lastScannedValue = primaryValue
                lastScannedTime = now

                // Check for small far barcode to hint auto-zoom
                var smallFarCodeFound = false
                val barcodeItems = results.mapNotNull { res ->
                    val text = res.text ?: return@mapNotNull null
                    val points = res.resultPoints
                    var isSmall = false
                    if (points != null && points.size >= 2) {
                        val minX = points.minOf { it.x }
                        val maxX = points.maxOf { it.x }
                        val codeWidth = maxX - minX
                        // If barcode takes less than 15% of viewfinder width, it's far / small
                        if (codeWidth > 0 && codeWidth < (width * 0.15f)) {
                            isSmall = true
                            smallFarCodeFound = true
                        }
                    }
                    RawBarcodeItem(
                        text = text,
                        formatName = res.barcodeFormat.name,
                        isSmallBarcode = isSmall
                    )
                }

                if (barcodeItems.isNotEmpty()) {
                    if (smallFarCodeFound) {
                        onAutoZoomSuggested()
                    }
                    onBarcodesDetected(barcodeItems)
                }
            }
        } catch (_: Exception) {
            // Ignore corrupted frame / decode exceptions
        } finally {
            image.close()
        }
    }
}
