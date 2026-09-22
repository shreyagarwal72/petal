package com.petal.browser.lens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.petal.browser.ui.components.expressivePress
import android.os.VibrationEffect
import android.os.Vibrator
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
    var scanLocked by remember { mutableStateOf(false) }
    val closeInteraction = remember { MutableInteractionSource() }
    val flashInteraction = remember { MutableInteractionSource() }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var detectedValue by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraControl = null
            executor.shutdownNow()
            (context as? com.petal.browser.activity.BrowserActivity)?.restoreBrowserInputFocus()
        }
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cancel scanning") }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Petal QR Scanner", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Auto-scan is ready", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .72f))
                }
                Icon(Icons.Rounded.QrCodeScanner, "Scanner", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(32.dp)).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(32.dp))) {
                Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = .7f), RoundedCornerShape(28.dp)))
            }
            AnimatedVisibility(visible = detectedValue == null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                "Align the code inside the frame",
                Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            }
            Spacer(Modifier.weight(1f))
            Text("Point at a code or capture a photo", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodyMedium)
        }
        if (hasPermission) {
            key(lensFacing) { AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            view.post { cameraProvider = provider }
                            val preview = Preview.Builder().setTargetResolution(Size(1280, 960)).build().also { it.surfaceProvider = view.surfaceProvider }
                            val capture = ImageCapture.Builder().setTargetResolution(Size(1280, 960)).build()
                            view.post { imageCapture = capture }
                            val analysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 960))
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
                                        val rowStride = plane.rowStride.coerceAtLeast(image.width)
                                        val pixelStride = plane.pixelStride.coerceAtLeast(1)
                                        for (row in 0 until image.height) {
                                            for (column in 0 until image.width) {
                                                val offset = (row * rowStride + column * pixelStride).coerceIn(0, bytes.lastIndex)
                                                val y = bytes[offset].toInt() and 0xff
                                                pixels[row * image.width + column] = -0x1000000 or (y shl 16) or (y shl 8) or y
                                            }
                                        }
                                        val source = RGBLuminanceSource(image.width, image.height, pixels)
                                        val result = runCatching {
                                            MultiFormatReader().apply {
                                                setHints(mapOf(DecodeHintType.TRY_HARDER to true))
                                            }.decode(BinaryBitmap(HybridBinarizer(source))).text
                                        }.getOrNull()
                                        if (!result.isNullOrBlank() && !scanLocked) {
                                            scanLocked = true
                                            view.post {
                                                detectedValue = result
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator)
                                                        ?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                                                }
                                            }
                                        }
                                    }
                                } finally { image.close() }
                            }
                            provider.unbindAll()
                            val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, analysis, capture)
                            cameraControl = camera.cameraControl
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).align(Alignment.Center)
            ) }
        } else {
            Text("Camera permission is required", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        AnimatedVisibility(
            visible = detectedValue != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary)
                    Text("QR code detected", style = MaterialTheme.typography.headlineSmall)
                    Text(detectedValue.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { detectedValue = null; scanLocked = false }) { Text("Scan again") }
                        Button(onClick = { detectedValue?.let(onResult) }) {
                            Text(if (detectedValue?.startsWith("http://") == true || detectedValue?.startsWith("https://") == true) "Open website" else "Use result")
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 24.dp, bottom = 76.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) { Icon(Icons.Rounded.Cameraswitch, "Switch camera") }
            FilledIconButton(onClick = {
                val output = File(context.cacheDir, "petal-scan-${System.nanoTime()}.jpg")
                imageCapture?.takePicture(ImageCapture.OutputFileOptions.Builder(output).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) { }
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        decodePetalBitmap(BitmapFactory.decodeFile(output.absolutePath))?.let { value -> scanLocked = true; detectedValue = value }
                        output.delete()
                    }
                })
            }) { Icon(Icons.Rounded.CameraAlt, "Capture and scan") }
            FilledTonalIconButton(onClick = {
                torchEnabled = !torchEnabled
                cameraControl?.enableTorch(torchEnabled)
            }, interactionSource = flashInteraction, modifier = Modifier.expressivePress(flashInteraction)) {
                Icon(Icons.Rounded.FlashOn, "Flashlight", tint = if (torchEnabled) Color.Yellow else MaterialTheme.colorScheme.onSecondaryContainer)
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

private fun decodePetalBitmap(bitmap: Bitmap): String? {
    val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width.coerceAtMost(1600), (bitmap.height * 1600f / bitmap.width).toInt().coerceAtLeast(1), true)
    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    return runCatching {
        MultiFormatReader().apply { setHints(mapOf(DecodeHintType.TRY_HARDER to true)) }
            .decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(scaled.width, scaled.height, pixels)))).text
    }.getOrNull()
}
