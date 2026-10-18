package com.petal.browser.lens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Size
import com.petal.browser.view.PetalToast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import com.petal.browser.ui.glass.liveGlassWallpaper
import com.petal.browser.ui.glass.captureGlassWallpaper
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.bouncyClickable
import java.io.File
import java.util.concurrent.Executors
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.liquidGlassChrome

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
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var detectedValue by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    fun vibrateSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                    ?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                PetalHapticEngine.getInstance(context).playClick(context)
            }
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
                cameraControl = null
                executor.shutdownNow()
            } catch (_: Exception) {}
            (context as? com.petal.browser.activity.BrowserActivity)?.restoreBrowserInputFocus()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isLiquidGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val glassWallpaper = com.petal.browser.ui.glass.rememberPetalGlassWallpaper()
    var previewRef by remember { mutableStateOf<PreviewView?>(null) }
    var viewportRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val frameTick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isLiquidGlass, previewRef, cameraReady) {
        if (isLiquidGlass && previewRef != null && cameraReady) {
            while (true) { withFrameNanos { frameTick.longValue = it } }
        }
    }

    CompositionLocalProvider(
        com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides (if (isLiquidGlass) glassWallpaper else null)
    ) {
    // Fully opaque root: the old 95% alpha let the sheet/home underneath ghost through.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C10))
    ) {
        // ── Glass backdrop: the live camera feed (or a pattern before it is ready) ──
        if (isLiquidGlass) {
            val pv = previewRef
            if (pv != null && hasPermission && cameraReady) {
                Box(
                    Modifier
                        .matchParentSize()
                        .liveGlassWallpaper(glassWallpaper, pv) { frameTick.longValue }
                )
            } else {
                Box(Modifier.matchParentSize().captureGlassWallpaper(glassWallpaper)) {
                    com.petal.browser.ui.glass.PetalGlassPattern(Modifier.fillMaxSize())
                }
            }
        }

        // ── Full-bleed camera (TextureView so it can be recorded for glass) ──
        if (hasPermission) {
            key(lensFacing) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            val previewView = this
                            post { previewRef = previewView }
                                        val future = ProcessCameraProvider.getInstance(ctx)
                                        future.addListener({
                                            val provider = future.get()
                                            post { cameraProvider = provider }

                                            val preview = Preview.Builder()
                                                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                                .build()
                                                .also { it.surfaceProvider = surfaceProvider }

                                            val capture = ImageCapture.Builder()
                                                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                                .build()
                                            post {
                                                imageCapture = capture
                                                cameraReady = true
                                            }

                                            val analysis = ImageAnalysis.Builder()
                                                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()

                                            analysis.setAnalyzer(executor) { image ->
                                                try {
                                                    val plane = image.planes.firstOrNull()
                                                    if (plane != null) {
                                                        val buffer = plane.buffer
                                                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                                        val pixels = IntArray(image.width * image.height)
                                                        val rowStride = plane.rowStride.coerceAtLeast(image.width)
                                                        val pixelStride = plane.pixelStride.coerceAtLeast(1)
                                                        for (row in 0 until image.height) {
                                                            for (column in 0 until image.width) {
                                                                val offset = (row * rowStride + column * pixelStride).coerceIn(0, bytes.lastIndex)
                                                                val y = bytes[offset].toInt() and 0xff
                                                                pixels[row * image.width + column] = -0x1000000 or (y shl 16) or (y shl 8) or y
                                                            }
                                                        }
                                                        val result = runCatching {
                                                            MultiFormatReader().apply {
                                                                setHints(mapOf(DecodeHintType.TRY_HARDER to true))
                                                            }.decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))).text
                                                        }.getOrNull()

                                                        if (!result.isNullOrBlank() && !scanLocked) {
                                                            scanLocked = true
                                                            vibrateSuccess()
                                                            post {
                                                                detectedValue = result
                                                            }
                                                        }
                                                    }
                                                } finally {
                                                    image.close()
                                                }
                                            }

                                            try {
                                                provider.unbindAll()
                                                val camera = provider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                                                    preview,
                                                    analysis,
                                                    capture
                                                )
                                                cameraControl = camera.cameraControl
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Dim everything outside the scan frame ──
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            if (viewportRect.width > 0f) {
                val scrim = androidx.compose.ui.graphics.Path().apply {
                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(viewportRect, androidx.compose.ui.geometry.CornerRadius(32.dp.toPx())))
                }
                drawPath(scrim, Color.Black.copy(alpha = if (hasPermission) 0.52f else 0f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Bar with Status Bar spacing ──────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isLiquidGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(44.dp)
                        .then(if (isLiquidGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
                        .bouncyClickable {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            onDismiss()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_cancel_scanning),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_petal_qr_scanner),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = if (scanLocked) "Code identified" else "Point camera at any code",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isLiquidGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(44.dp)
                        .then(if (isLiquidGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.5f))

            // ── 3:4 scan frame (transparent window onto the full-bleed camera) ──
            val viewportShape = RoundedCornerShape(32.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .onGloballyPositioned { viewportRect = it.boundsInRoot() }
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isLiquidGlass) 0.75f else 1f),
                        viewportShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.ui_camera_permission_is_required_to),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.ui_grant_permission))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Subtitle Guidance (glass pill so it stays readable over the live camera)
            val hintShape = RoundedCornerShape(20.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .then(if (isLiquidGlass) Modifier.liquidGlassChrome(hintShape, enabled = true)
                          else Modifier.background(Color.Black.copy(alpha = 0.35f), hintShape))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_align_qr_code_or_barcode),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ui_auto_scan_detects_instantly_or),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Bottom Action Controls ───────────────────────────────────────
            val bottomBarShape = RoundedCornerShape(32.dp)
            Surface(
                shape = bottomBarShape,
                color = if (isLiquidGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                border = if (isLiquidGlass) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .then(if (isLiquidGlass) Modifier.liquidGlassChrome(bottomBarShape, enabled = true) else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch camera (front/back)
                    Surface(
                        shape = CircleShape,
                        color = if (isLiquidGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(54.dp)
                            .then(if (isLiquidGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
                            .bouncyClickable {
                                PetalHapticEngine.getInstance(context).playClick(context)
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = stringResource(R.string.ui_switch_camera),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Main Capture Click Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(68.dp)
                            .then(if (isLiquidGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
                            .bouncyClickable(enabled = !isCapturing) {
                                PetalHapticEngine.getInstance(context).playClick(context)
                                val capture = imageCapture
                                if (capture == null) {
                                    PetalToast.show(context, "Camera initializing...", PetalToast.LENGTH_SHORT)
                                    return@bouncyClickable
                                }
                                isCapturing = true
                                val output = File(context.cacheDir, "petal-scan-${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(output).build()

                                capture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onError(exc: ImageCaptureException) {
                                            isCapturing = false
                                            output.delete()
                                            PetalToast.show(context, "Capture failed: ${exc.message}", PetalToast.LENGTH_SHORT)
                                        }

                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            try {
                                                val bmp = BitmapFactory.decodeFile(output.absolutePath)
                                                if (bmp != null) {
                                                    val decoded = decodePetalBitmap(bmp)
                                                    if (!decoded.isNullOrBlank()) {
                                                        scanLocked = true
                                                        vibrateSuccess()
                                                        detectedValue = decoded
                                                    } else {
                                                        PetalToast.show(context, "No code found in photo", PetalToast.LENGTH_SHORT)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                PetalToast.show(context, "Unable to read captured image", PetalToast.LENGTH_SHORT)
                                            } finally {
                                                output.delete()
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCapturing) {
                                com.petal.browser.compose.composable.PetalContainedLoadingIndicator(
                                    size = 32.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = stringResource(R.string.ui_capture_photo_to_scan),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Torch Toggle Button
                    Surface(
                        shape = CircleShape,
                        color = if (torchEnabled) MaterialTheme.colorScheme.primaryContainer else if (isLiquidGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(54.dp)
                            .then(if (isLiquidGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
                            .bouncyClickable {
                                PetalHapticEngine.getInstance(context).playClick(context)
                                torchEnabled = !torchEnabled
                                cameraControl?.enableTorch(torchEnabled)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                contentDescription = stringResource(R.string.ui_toggle_torch),
                                tint = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Result Dialog ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = detectedValue != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val dialogShape = RoundedCornerShape(28.dp)
            Surface(
                shape = dialogShape,
                color = if (isLiquidGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (isLiquidGlass) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = if (isLiquidGlass) 0.dp else 12.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp)
                    .then(if (isLiquidGlass) Modifier.liquidGlassChrome(dialogShape, enabled = true) else Modifier)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.ui_code_detected),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (detectedValue?.startsWith("http://") == true || detectedValue?.startsWith("https://") == true) "Web link found" else "Text / Barcode content",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val textShape = RoundedCornerShape(16.dp)
                    Surface(
                        shape = textShape,
                        color = if (isLiquidGlass) Color.Black.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (isLiquidGlass) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isLiquidGlass) Modifier.liquidGlassChrome(textShape, enabled = true, sheenIntensity = 0.3f) else Modifier)
                    ) {
                        Text(
                            text = detectedValue.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                detectedValue = null
                                scanLocked = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.ui_scan_again))
                        }

                        Button(
                            onClick = {
                                val value = detectedValue
                                if (value != null) {
                                    detectedValue = null
                                    onResult(value)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text(
                                text = if (detectedValue?.startsWith("http://") == true || detectedValue?.startsWith("https://") == true) "Open Website" else "Use Result",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

private fun decodePetalBitmap(bitmap: Bitmap): String? {
    val scaled = Bitmap.createScaledBitmap(
        bitmap,
        bitmap.width.coerceAtMost(1600),
        (bitmap.height * 1600f / bitmap.width).toInt().coerceAtLeast(1),
        true
    )
    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    return runCatching {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.TRY_HARDER to true))
        }.decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(scaled.width, scaled.height, pixels)))).text
    }.getOrNull()
}

