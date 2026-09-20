package com.petal.browser.lens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.petal.browser.ui.components.expressivePress
import java.util.concurrent.Executors

@Composable
fun PetalQrScannerScreen(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var torchEnabled by remember { mutableStateOf(false) }
    val closeInteraction = remember { MutableInteractionSource() }
    val flashInteraction = remember { MutableInteractionSource() }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { image ->
                                try {
                                    val plane = image.planes.firstOrNull()
                                    if (plane != null) {
                                        val buffer = plane.buffer
                                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                        val pixels = IntArray(image.width * image.height)
                                        // CameraX Y-plane is greyscale; expand it for ZXing's luminance source.
                                        for (i in pixels.indices) {
                                            val y = bytes[(i.coerceAtMost(bytes.lastIndex))].toInt() and 0xff
                                            pixels[i] = -0x1000000 or (y shl 16) or (y shl 8) or y
                                        }
                                        val source = RGBLuminanceSource(image.width, image.height, pixels)
                                        val result = runCatching {
                                            MultiFormatReader().apply {
                                                setHints(mapOf(DecodeHintType.TRY_HARDER to true))
                                            }.decode(BinaryBitmap(HybridBinarizer(source))).text
                                        }.getOrNull()
                                        if (!result.isNullOrBlank()) {
                                            view.post { onResult(result) }
                                        }
                                    }
                                } finally { image.close() }
                            }
                            provider.unbindAll()
                            val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            cameraControl = camera.cameraControl
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Camera permission is required", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(onClick = onDismiss, interactionSource = closeInteraction, modifier = Modifier.expressivePress(closeInteraction)) {
                Icon(Icons.Rounded.Close, "Close")
            }
            FilledTonalIconButton(onClick = {
                torchEnabled = !torchEnabled
                cameraControl?.enableTorch(torchEnabled)
            }, interactionSource = flashInteraction, modifier = Modifier.expressivePress(flashInteraction)) {
                Icon(Icons.Rounded.FlashOn, "Flash", tint = if (torchEnabled) Color.Yellow else MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        ) {
            Text(
                "Scan a QR code or barcode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp)
            )
        }
    }
}
