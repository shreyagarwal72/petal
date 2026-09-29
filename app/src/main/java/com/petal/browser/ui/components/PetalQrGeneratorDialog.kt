/*
 * PetalQrGeneratorDialog.kt
 * ─────────────────────────────────────────────────────────────────────────
 * On-device QR Code generator with signature Petal Material 3 Expressive styling:
 * - Rounded organic squircle / pebble card geometry with M3 elevation & tonal surface.
 * - Every data module is a soft rounded squircle rendered in Petal's teal
 *   brand gradient (rendered at true QR-module resolution, not upscaled
 *   pixels, so the rounding is actually visible).
 * - The three finder patterns are redrawn as heavily-rounded "petal eyes"
 *   instead of hard squares, while keeping the standard 7:5:3 module
 *   ratio so scanners still detect them reliably.
 * - No center logo/badge: nothing ever obstructs the data area, which
 *   keeps the code fast and reliable to scan on any camera.
 * - Quick copy link & share link actions with tactile Petal haptics.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.view.PetalToast

@Composable
fun PetalQrGeneratorDialog(
    url: String,
    title: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val primaryColorInt = MaterialTheme.colorScheme.primary.hashCode()
    val onSurfaceColorInt = MaterialTheme.colorScheme.onSurface.hashCode()

    val qrBitmap = remember(url, primaryColorInt) {
        generatePetalStyledQrBitmap(
            context = context,
            content = url,
            dimension = 640
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Petal Share QR",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scan to open immediately",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).playTick(context)
                            onDismissRequest()
                        }
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // The Designed QR Card with soft glow border & white backdrop for maximum scanner readability
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                )
                            ),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Petal Designed QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Could not generate QR code",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // URL & Title information pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title.ifEmpty { url },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: Copy Link, Share QR Image, Share Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                            PetalToast.show(context, "Link copied to clipboard")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            if (qrBitmap != null) {
                                shareQrImage(context, qrBitmap, title.ifEmpty { "Petal QR" })
                            } else {
                                PetalToast.show(context, "QR code not ready")
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share QR", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }

                    Button(
                        onClick = {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Saves generated QR bitmap to app cache and launches Android system share sheet with image URI.
 */
private fun shareQrImage(context: Context, bitmap: Bitmap, title: String) {
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val qrFile = java.io.File(cachePath, "petal_qr_${System.currentTimeMillis()}.png")
        java.io.FileOutputStream(qrFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            qrFile
        )

        if (contentUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share QR Code Image"))
        }
    } catch (e: Exception) {
        PetalToast.show(context, "Could not share QR image: ${e.message}")
    }
}

/**
 * Generates a Petal-branded, fully scannable QR code:
 * 1. High error correction (LEVEL H) for maximum real-world scan reliability.
 * 2. Every data module rendered as a soft rounded squircle, tinted with
 *    Petal's teal brand gradient — computed at true QR-module resolution
 *    (not the final upscaled pixel grid ZXing would otherwise hand back),
 *    so the rounding is actually visible instead of sub-pixel.
 * 3. The three finder patterns are redrawn as rounded "petal eyes" that
 *    keep the standard 7:5:3 module ratio scanners rely on to detect them.
 * 4. No center logo or badge of any kind — nothing ever sits on top of the
 *    data area, which is what actually determines scan reliability.
 */
private fun generatePetalStyledQrBitmap(
    context: Context,
    content: String,
    dimension: Int
): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val quietZoneModules = 2
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            put(EncodeHintType.MARGIN, quietZoneModules)
        }

        val writer = QRCodeWriter()
        // Requesting a 1x1 target forces ZXing's internal `multiple` scale
        // factor to 1, so the returned matrix is in true QR *modules*
        // (e.g. 25x25) rather than pre-upscaled to `dimension` pixels.
        // We do our own scaling below so every module can be styled.
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 1, 1, hints)
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height

        val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Crisp white background — best contrast for scanning
        canvas.drawRect(
            0f, 0f, dimension.toFloat(), dimension.toFloat(),
            Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        )

        val cellWidth = dimension.toFloat() / matrixWidth.toFloat()
        val cellHeight = dimension.toFloat() / matrixHeight.toFloat()

        // Petal brand gradient: Deep Teal -> brighter leaf-teal, diagonal across the whole code
        val deepTeal = android.graphics.Color.rgb(2, 96, 101)
        val brightTeal = android.graphics.Color.rgb(44, 168, 150)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = android.graphics.LinearGradient(
                0f, 0f, dimension.toFloat(), dimension.toFloat(),
                deepTeal, brightTeal, android.graphics.Shader.TileMode.CLAMP
            )
        }

        // Finder pattern bounding boxes (module coords): top-left, top-right, bottom-left.
        // Every QR version reserves an untouched 7x7 module square in these corners.
        val finderSize = 7
        data class FinderOrigin(val mx: Int, val my: Int)
        // The 1x1 render request bakes the quiet zone directly into the matrix
        // (ZXing centers the real code with `quietZoneModules` of blank margin
        // on every side), so the actual finder patterns start there, not at
        // matrix edge (0,0).
        val finderOrigins = listOf(
            FinderOrigin(quietZoneModules, quietZoneModules),
            FinderOrigin(matrixWidth - finderSize - quietZoneModules, quietZoneModules),
            FinderOrigin(quietZoneModules, matrixHeight - finderSize - quietZoneModules)
        )
        fun isInsideFinder(x: Int, y: Int): Boolean = finderOrigins.any { origin ->
            x >= origin.mx && x < origin.mx + finderSize && y >= origin.my && y < origin.my + finderSize
        }

        // 1) Data modules — rounded squircles in the brand gradient, skipping finder zones
        val rectF = RectF()
        val cornerRadius = cellWidth * 0.35f
        for (x in 0 until matrixWidth) {
            for (y in 0 until matrixHeight) {
                if (isInsideFinder(x, y)) continue
                if (bitMatrix.get(x, y)) {
                    val left = x * cellWidth + cellWidth * 0.08f
                    val top = y * cellHeight + cellHeight * 0.08f
                    val right = (x + 1) * cellWidth - cellWidth * 0.08f
                    val bottom = (y + 1) * cellHeight - cellHeight * 0.08f
                    rectF.set(left, top, right, bottom)
                    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, gradientPaint)
                }
            }
        }

        // 2) Petal-eye finder markers — same 7:5:3 ring structure as a standard
        // finder pattern, but with heavily rounded (squircle) corners for the
        // "petal" look. The ring proportions are what scanners key off, so
        // rounding the corners doesn't affect detection.
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        finderOrigins.forEach { origin ->
            val left = origin.mx * cellWidth
            val top = origin.my * cellHeight
            val outerSize = finderSize * cellWidth

            // Outer 7x7 ring (brand gradient)
            rectF.set(left, top, left + outerSize, top + outerSize)
            canvas.drawRoundRect(rectF, outerSize * 0.32f, outerSize * 0.32f, gradientPaint)

            // Middle 5x5 white gap
            val midInset = cellWidth
            rectF.set(left + midInset, top + midInset, left + outerSize - midInset, top + outerSize - midInset)
            canvas.drawRoundRect(rectF, (outerSize - 2 * midInset) * 0.32f, (outerSize - 2 * midInset) * 0.32f, whitePaint)

            // Inner 3x3 solid "pupil" (brand gradient)
            val innerInset = cellWidth * 2f
            rectF.set(left + innerInset, top + innerInset, left + outerSize - innerInset, top + outerSize - innerInset)
            canvas.drawRoundRect(rectF, (outerSize - 2 * innerInset) * 0.32f, (outerSize - 2 * innerInset) * 0.32f, gradientPaint)
        }

        bitmap
    } catch (_: Exception) {
        null
    }
}
