package com.petal.browser.account

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAvatarCropSheet(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onAvatarCropped: () -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                val maxDimension = 1280
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
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Crop Profile Picture",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(12.dp))

            if (sourceBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val bitmap = sourceBitmap!!

                // Interactive Crop Canvas Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val circleRadius = Math.min(canvasWidth, canvasHeight) * 0.4f

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

                        // Dim Mask with Circular Profile Cutout
                        val outerPath = Path().apply {
                            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                        }
                        val circlePath = Path().apply {
                            addOval(
                                Rect(
                                    center = Offset(canvasWidth / 2f, canvasHeight / 2f),
                                    radius = circleRadius
                                )
                            )
                        }
                        val maskPath = Path.combine(PathOperation.Difference, outerPath, circlePath)

                        drawPath(maskPath, color = Color.Black.copy(alpha = 0.65f))
                        drawPath(circlePath, color = Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Control Toolbar: Rotate, Reset, Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
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

                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.8f..4f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Crop & Save Button
                Button(
                    onClick = {
                        val bmp = sourceBitmap ?: return@Button
                        val cropped = cropBitmap(bmp, scale, offset, rotationAngle)
                        GoogleAccountManager.saveCroppedAvatar(context, cropped)
                        PetalHapticEngine.getInstance(context).playClick(context)
                        onAvatarCropped()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Save Profile Picture",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

private fun cropBitmap(source: Bitmap, scale: Float, offset: Offset, rotationAngle: Float): Bitmap {
    val matrix = Matrix().apply {
        postRotate(rotationAngle)
        postScale(scale, scale)
    }

    val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    val targetDim = Math.min(transformed.width, transformed.height)
    val startX = Math.max(0, (transformed.width - targetDim) / 2)
    val startY = Math.max(0, (transformed.height - targetDim) / 2)

    return Bitmap.createBitmap(transformed, startX, startY, targetDim, targetDim)
}
