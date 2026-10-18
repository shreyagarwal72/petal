package com.petal.browser.compose.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f)
}

@Composable
fun PetalImageCropDialog(
    imageUri: Uri?,
    imageUrl: String?,
    onDismissRequest: () -> Unit,
    onImageSaved: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    // Normalized crop rectangle: 0f..1f within display bounds
    var cropLeft by remember { mutableFloatStateOf(0.1f) }
    var cropTop by remember { mutableFloatStateOf(0.1f) }
    var cropRight by remember { mutableFloatStateOf(0.9f) }
    var cropBottom by remember { mutableFloatStateOf(0.9f) }

    // Load bitmap asynchronously
    LaunchedEffect(imageUri, imageUrl) {
        withContext(Dispatchers.IO) {
            val bmp = try {
                if (imageUri != null) {
                    context.contentResolver.openInputStream(imageUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } else if (!imageUrl.isNullOrBlank()) {
                    if (imageUrl.startsWith("file://")) {
                        BitmapFactory.decodeFile(imageUrl.removePrefix("file://"))
                    } else {
                        val url = java.net.URL(imageUrl)
                        val conn = url.openConnection()
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        BitmapFactory.decodeStream(conn.getInputStream())
                    }
                } else null
            } catch (e: Exception) {
                null
            }
            bitmap = bmp
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                    Text(
                        text = "Crop & Rotate",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            val bmp = bitmap ?: return@IconButton
                            isSaving = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // 1. Apply rotation if any
                                    val matrix = Matrix()
                                    if (rotationAngle != 0f) {
                                        matrix.postRotate(rotationAngle)
                                    }
                                    val rotatedBmp = if (rotationAngle != 0f) {
                                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                    } else {
                                        bmp
                                    }

                                    // 2. Crop bitmap according to normalized coordinates
                                    val startX = (cropLeft.coerceIn(0f, 1f) * rotatedBmp.width).toInt()
                                    val startY = (cropTop.coerceIn(0f, 1f) * rotatedBmp.height).toInt()
                                    val width = ((cropRight - cropLeft).coerceIn(0.05f, 1f) * rotatedBmp.width).toInt()
                                        .coerceAtMost(rotatedBmp.width - startX)
                                    val height = ((cropBottom - cropTop).coerceIn(0.05f, 1f) * rotatedBmp.height).toInt()
                                        .coerceAtMost(rotatedBmp.height - startY)

                                    val cropped = Bitmap.createBitmap(rotatedBmp, startX, startY, width, height)

                                    // 3. Save to Pictures/Petal
                                    val outputDir = File(context.getExternalFilesDir(null), "PetalEdits").apply { mkdirs() }
                                    val outputFile = File(outputDir, "PETAL_EDIT_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(outputFile).use { fos ->
                                        cropped.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                    }

                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        PetalHapticEngine.getInstance(context).playHeavyClick(context)
                                        Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
                                        onImageSaved(outputFile)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Aspect ratio chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CropAspectRatio.values().forEach { ratio ->
                            val isSelected = selectedRatio == ratio
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedRatio = ratio
                                    val r = ratio.ratio
                                    if (r != null) {
                                        val curW = cropRight - cropLeft
                                        val newH = (curW / r).coerceIn(0.1f, 0.8f)
                                        cropBottom = (cropTop + newH).coerceAtMost(1f)
                                    }
                                },
                                label = { Text(ratio.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    // Rotate button
                    OutlinedButton(
                        onClick = {
                            rotationAngle = (rotationAngle + 90f) % 360f
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rotate 90° (${rotationAngle.toInt()}°)")
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Display canvas with image & drag handles
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        val dx = dragAmount.x / w
                                        val dy = dragAmount.y / h

                                        // Adjust crop rectangle boundaries smoothly
                                        val touchX = change.position.x / w
                                        val touchY = change.position.y / h

                                        if (touchX < (cropLeft + cropRight) / 2f) {
                                            cropLeft = (cropLeft + dx).coerceIn(0.02f, cropRight - 0.1f)
                                        } else {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + 0.1f, 0.98f)
                                        }

                                        if (touchY < (cropTop + cropBottom) / 2f) {
                                            cropTop = (cropTop + dy).coerceIn(0.02f, cropBottom - 0.1f)
                                        } else {
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.1f, 0.98f)
                                        }
                                    }
                                }
                        ) {
                            val canvasW = size.width
                            val canvasH = size.height

                            val leftPx = cropLeft * canvasW
                            val topPx = cropTop * canvasH
                            val rightPx = cropRight * canvasW
                            val bottomPx = cropBottom * canvasH

                            // Dim outside area
                            drawRect(
                                color = Color.Black.copy(alpha = 0.55f),
                                size = size
                            )

                            // Clear inside active crop box
                            drawRect(
                                color = Color.Transparent,
                                topLeft = Offset(leftPx, topPx),
                                size = Size(rightPx - leftPx, bottomPx - topPx),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                            )

                            // White outline grid
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(leftPx, topPx),
                                size = Size(rightPx - leftPx, bottomPx - topPx),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // 1/3 guidelines
                            val stepX = (rightPx - leftPx) / 3f
                            val stepY = (bottomPx - topPx) / 3f
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(leftPx + stepX, topPx), Offset(leftPx + stepX, bottomPx), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(leftPx + stepX * 2, topPx), Offset(leftPx + stepX * 2, bottomPx), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(leftPx, topPx + stepY), Offset(rightPx, topPx + stepY), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(leftPx, topPx + stepY * 2), Offset(rightPx, topPx + stepY * 2), strokeWidth = 1.dp.toPx())
                        }
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
