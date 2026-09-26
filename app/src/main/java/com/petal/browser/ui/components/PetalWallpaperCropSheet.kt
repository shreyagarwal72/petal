package com.petal.browser.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * PetalWallpaperCropSheet:
 * Interactive image cropping sheet matching the exact portrait aspect ratio
 * of the user's device screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalWallpaperCropSheet(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onWallpaperCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenAspect = remember(configuration) {
        val w = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
        val h = configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
        w / h
    }

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                val maxDimension = 2048
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val freshStream = context.contentResolver.openInputStream(imageUri)
                sourceBitmap = BitmapFactory.decodeStream(freshStream, null, decodeOptions)
                freshStream?.close()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Crop Wallpaper",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scale and position for your home screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(14.dp))

            if (sourceBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val bitmap = sourceBitmap!!

                // Interactive Crop Canvas Area matching home screen aspect ratio
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 6f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        canvasSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())

                        // Calculate crop viewport rectangle preserving screen aspect ratio
                        val cropHeight = canvasHeight * 0.90f
                        val cropWidth = cropHeight * screenAspect
                        val cropRect = Rect(
                            offset = Offset((canvasWidth - cropWidth) / 2f, (canvasHeight - cropHeight) / 2f),
                            size = Size(cropWidth, cropHeight)
                        )

                        // Draw Rotated & Transformed Image
                        drawContext.canvas.save()
                        drawContext.canvas.translate(canvasWidth / 2f + offset.x, canvasHeight / 2f + offset.y)
                        drawContext.canvas.rotate(rotationAngle)
                        drawContext.canvas.scale(scale, scale)

                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = IntOffset(-bitmap.width / 2, -bitmap.height / 2),
                            dstSize = IntSize(bitmap.width, bitmap.height)
                        )
                        drawContext.canvas.restore()

                        // Dim Outer Mask with Cutout for Wallpaper Target Area
                        val outerPath = Path().apply {
                            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                        }
                        val cropPath = Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    cropRect,
                                    androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx())
                                )
                            )
                        }
                        val maskPath = Path.combine(PathOperation.Difference, outerPath, cropPath)

                        drawPath(maskPath, color = Color.Black.copy(alpha = 0.65f))
                        drawPath(
                            cropPath,
                            color = Color.White.copy(alpha = 0.90f),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Control Toolbar: Rotate, Reset, Zoom via Stride Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        rotationAngle = (rotationAngle + 90f) % 360f
                        PetalHapticEngine.getInstance(context).playTick(context)
                    }) {
                        Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate")
                    }

                    IconButton(onClick = {
                        scale = 1f
                        offset = Offset.Zero
                        rotationAngle = 0f
                        PetalHapticEngine.getInstance(context).playTick(context)
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset")
                    }

                    PetalSlider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.7f..4.5f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Apply & Save Cropped Wallpaper Button
                Button(
                    onClick = {
                        val bmp = sourceBitmap ?: return@Button
                        val cw = if (canvasSize.width > 0) canvasSize.width.toFloat() else 1080f
                        val ch = if (canvasSize.height > 0) canvasSize.height.toFloat() else 1920f
                        val cropped = cropWallpaperBitmap(bmp, scale, offset, rotationAngle, cw, ch, screenAspect)

                        // Save directly into app internal storage directory
                        val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                        val targetFile = File(wallpaperDir, "home_wallpaper_${System.currentTimeMillis()}.jpg")
                        try {
                            FileOutputStream(targetFile).use { out ->
                                cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            PetalHapticEngine.getInstance(context).playClick(context)
                            onWallpaperCropped(Uri.fromFile(targetFile))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Set as Home Wallpaper",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * Accurately crops bitmap based on screen aspect ratio, transform offsets, rotation, and scale.
 */
private fun cropWallpaperBitmap(
    source: Bitmap,
    scale: Float,
    offset: Offset,
    rotationAngle: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    aspectRatio: Float
): Bitmap {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return source

    try {
        val rotatedBitmap = if (rotationAngle % 360f != 0f) {
            val matrix = Matrix().apply { postRotate(rotationAngle) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val rw = rotatedBitmap.width.toFloat()
        val rh = rotatedBitmap.height.toFloat()

        val cw = if (canvasWidth > 0f) canvasWidth else 1080f
        val ch = if (canvasHeight > 0f) canvasHeight else 1920f
        val cropHeight = ch * 0.90f
        val cropWidth = cropHeight * aspectRatio
        val currentScale = scale.coerceAtLeast(0.1f)

        val imgCenterX = cw / 2f + offset.x
        val imgCenterY = ch / 2f + offset.y

        val imgLeftCanvas = imgCenterX - (rw * currentScale) / 2f
        val imgTopCanvas = imgCenterY - (rh * currentScale) / 2f

        val cropLeftCanvas = (cw - cropWidth) / 2f
        val cropTopCanvas = (ch - cropHeight) / 2f

        val dxCanvas = cropLeftCanvas - imgLeftCanvas
        val dyCanvas = cropTopCanvas - imgTopCanvas

        val cropX = (dxCanvas / currentScale).coerceIn(0f, (rw - 1f).coerceAtLeast(0f))
        val cropY = (dyCanvas / currentScale).coerceIn(0f, (rh - 1f).coerceAtLeast(0f))

        val maxAllowedW = rw - cropX
        val maxAllowedH = rh - cropY

        val targetBitmapWidth = (cropWidth / currentScale).coerceIn(1f, maxAllowedW.coerceAtLeast(1f))
        val targetBitmapHeight = (cropHeight / currentScale).coerceIn(1f, maxAllowedH.coerceAtLeast(1f))

        val cropped = Bitmap.createBitmap(
            rotatedBitmap,
            cropX.toInt(),
            cropY.toInt(),
            targetBitmapWidth.toInt().coerceAtLeast(1),
            targetBitmapHeight.toInt().coerceAtLeast(1)
        )

        // Scale to crisp high-res portrait bounds (e.g. 1080 x 1080/aspect)
        val finalWidth = 1080
        val finalHeight = (1080f / aspectRatio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(cropped, finalWidth, finalHeight, true)
    } catch (e: Throwable) {
        e.printStackTrace()
        return source
    }
}
