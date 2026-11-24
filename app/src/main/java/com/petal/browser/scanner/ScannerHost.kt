package com.petal.browser.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.petal.browser.R
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.captureGlassWallpaper
import com.petal.browser.ui.glass.liveGlassWallpaper
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ScannerMode {
    SCAN,
    GALLERY,
    HISTORY
}

enum class CameraErrorState {
    NONE,
    PERMISSION_DENIED,
    PERMISSION_PERMANENTLY_DENIED,
    NO_CAMERA,
    CAMERA_IN_USE,
    BIND_FAILED
}

/**
 * Universal Scanner Host Composable for Petal Browser.
 * Powers PetalScannerActivity, PetalLensBottomSheet, Quick Settings scanner tile, and Quick Tools.
 */
@Composable
fun ScannerHost(
    onDismiss: () -> Unit,
    onResultDispatched: ((String) -> Unit)? = null,
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ── Concurrency Guard ──────────────────────────────────────────────────
    val acquired = remember { ScannerGuard.tryAcquire() }
    if (!acquired) {
        LaunchedEffect(Unit) {
            PetalToast.show(context, "Scanner is already running", Toast.LENGTH_SHORT)
            onDismiss()
        }
        return
    }

    DisposableEffect(Unit) {
        onDispose {
            ScannerGuard.release()
        }
    }

    // ── Permissions & Error State ──────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf(CameraErrorState.NONE) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            val act = context as? android.app.Activity
            val permanentlyDenied = act != null && !act.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            cameraError = if (permanentlyDenied) {
                CameraErrorState.PERMISSION_PERMANENTLY_DENIED
            } else {
                CameraErrorState.PERMISSION_DENIED
            }
        } else {
            cameraError = CameraErrorState.NONE
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── CameraX Architecture ───────────────────────────────────────────────
    val executor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var previewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraReady by remember { mutableStateOf(false) }

    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var hasFlashUnit by remember { mutableStateOf(false) }

    // Analysis lock & state
    val analysisLocked = remember { AtomicBoolean(false) }
    var scanLocked by remember { mutableStateOf(false) }
    var isLowLight by remember { mutableStateOf(false) }
    var suggestAutoZoom by remember { mutableStateOf(false) }

    // Detected results
    var detectedResults by remember { mutableStateOf<List<PetalScanResult>>(emptyList()) }
    var selectedResultIndex by remember { mutableIntStateOf(0) }
    var isDecodingGallery by remember { mutableStateOf(false) }

    // Navigation & Sub-views
    var activeMode by rememberSaveable { mutableStateOf(ScannerMode.SCAN) }
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }

    // Tap-to-focus ring
    var focusRingPoint by remember { mutableStateOf<Offset?>(null) }
    val focusRingAlpha = remember { Animatable(0f) }
    val focusRingScale = remember { Animatable(1.4f) }

    // Accessibility talkback announcement
    var talkBackText by remember { mutableStateOf("") }

    // Camera bind retry trigger
    var bindTrigger by remember { mutableIntStateOf(0) }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
                cameraControl = null
                executor.shutdownNow()
            } catch (_: Exception) {}
        }
    }

    // ── Result Handling ────────────────────────────────────────────────────
    fun handleDispatchedBarcodes(barcodes: List<RawBarcodeItem>) {
        if (barcodes.isEmpty() || scanLocked) return

        val scanResults = barcodes.map { item ->
            val payload = ScanParser.parse(item.text, item.formatName)
            val safetyReport = if (payload is ScanPayload.Url) {
                LinkInspector.inspect(payload.url, payload.host)
            } else null

            PetalScanResult(
                rawValue = item.text,
                formatName = item.formatName,
                payload = payload,
                safetyReport = safetyReport
            )
        }

        val primary = scanResults.first()

        // 1. Check auto-open preference
        if (PetalScannerSettings.isAutoOpenEnabled(context) && primary.payload is ScanPayload.Url) {
            PetalScanActionHandler.performDetectionFeedback(context)
            PetalScanHistoryStore.addHistory(context, primary)
            if (onResultDispatched != null) {
                onResultDispatched(primary.rawValue)
            } else {
                PetalScanActionHandler.openUrl(context, (primary.payload as ScanPayload.Url).url)
            }
            onDismiss()
            return
        }

        // 2. Lock and present sheet
        analysisLocked.set(true)
        scanLocked = true
        PetalScanActionHandler.performDetectionFeedback(context)
        PetalScanHistoryStore.addHistory(context, primary)
        talkBackText = "Detected ${primary.formatName} code"

        detectedResults = scanResults
        selectedResultIndex = 0
    }

    // ── Gallery Decoding ───────────────────────────────────────────────────
    fun decodeGalleryUri(uri: Uri) {
        isDecodingGallery = true
        coroutineScope.launch {
            val items = PetalImageDecoder.decodeUri(context, uri)
            isDecodingGallery = false
            if (items.isNotEmpty()) {
                handleDispatchedBarcodes(items)
            } else {
                PetalToast.show(context, "No QR code or barcode found in this photo", Toast.LENGTH_SHORT)
            }
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) decodeGalleryUri(uri)
    }

    val galleryFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) decodeGalleryUri(uri)
    }

    fun launchSafePhotoPicker() {
        try {
            galleryPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            try {
                galleryFallbackLauncher.launch("image/*")
            } catch (_: Exception) {
                PetalToast.show(context, "Cannot open photo gallery", Toast.LENGTH_SHORT)
            }
        }
    }

    // ── Liquid Glass Configuration ─────────────────────────────────────────
    val isLiquidGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val glassWallpaper = rememberPetalGlassWallpaper()
    val frameTick = remember { mutableLongStateOf(0L) }

    LaunchedEffect(isLiquidGlass, previewRef, cameraReady) {
        if (isLiquidGlass && previewRef != null && cameraReady) {
            while (true) {
                withFrameNanos { frameTick.longValue = it }
            }
        }
    }

    // ── Sub-screens (History / Settings) ───────────────────────────────────
    if (showSettingsScreen) {
        PetalScannerSettingsScreen(onBack = { showSettingsScreen = false })
        return
    }

    if (activeMode == ScannerMode.HISTORY) {
        PetalScannerHistoryScreen(
            onBack = { activeMode = ScannerMode.SCAN },
            onSelectEntry = { entry ->
                val payload = ScanParser.parse(entry.rawValue, entry.formatName)
                val safety = if (payload is ScanPayload.Url) LinkInspector.inspect(payload.url, payload.host) else null
                detectedResults = listOf(
                    PetalScanResult(
                        rawValue = entry.rawValue,
                        formatName = entry.formatName,
                        payload = payload,
                        safetyReport = safety,
                        timestamp = entry.timestamp
                    )
                )
                selectedResultIndex = 0
                scanLocked = true
            }
        )
        return
    }

    // ── Predictive Back ────────────────────────────────────────────────────
    val hasSubOverlay = detectedResults.isNotEmpty() || isDecodingGallery
    if (hasSubOverlay) {
        androidx.activity.compose.BackHandler(enabled = true) {
            detectedResults = emptyList()
            scanLocked = false
            analysisLocked.set(false)
        }
    }

    PetalPredictiveBackSurface(
        enabled = !hasSubOverlay,
        onBack = onDismiss
    ) {
        CompositionLocalProvider(
            LocalPetalGlassWallpaper provides (if (isLiquidGlass) glassWallpaper else null)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F))
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        if (talkBackText.isNotEmpty()) {
                            contentDescription = talkBackText
                        }
                    }
            ) {
                // ── Liquid Glass Live Camera Reflection ────────────────────────
                if (isLiquidGlass) {
                    val pv = previewRef
                    if (pv != null && hasCameraPermission && cameraReady) {
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

                // ── Full-Bleed Camera Surface ──────────────────────────────────
                if (hasCameraPermission && cameraError == CameraErrorState.NONE) {
                    key(lensFacing, bindTrigger) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    post { previewRef = this }

                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val provider = cameraProviderFuture.get()
                                            cameraProvider = provider

                                            val cameraSelector = CameraSelector.Builder()
                                                .requireLensFacing(lensFacing)
                                                .build()

                                            // Resolution Selector: 1280x960 fallback closest higher then lower
                                            val resolutionSelector = ResolutionSelector.Builder()
                                                .setResolutionStrategy(
                                                    ResolutionStrategy(
                                                        Size(1280, 960),
                                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                                    )
                                                )
                                                .build()

                                            // Preview UseCase
                                            val previewUseCase = Preview.Builder()
                                                .setResolutionSelector(resolutionSelector)
                                                .build()
                                                .also { it.surfaceProvider = this.surfaceProvider }

                                            // Analyzer UseCase
                                            val analyzerUseCase = ImageAnalysis.Builder()
                                                .setResolutionSelector(resolutionSelector)
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()

                                            val analyzer = PetalCameraAnalyzer(
                                                isLocked = analysisLocked,
                                                onBarcodesDetected = { items ->
                                                    post { handleDispatchedBarcodes(items) }
                                                },
                                                onLowLightDetected = { lowLight ->
                                                    post { isLowLight = lowLight }
                                                },
                                                onAutoZoomSuggested = {
                                                    post { suggestAutoZoom = true }
                                                }
                                            )
                                            analyzerUseCase.setAnalyzer(executor, analyzer)

                                            // ViewPort (1:1 square viewfinder)
                                            val viewPort = ViewPort.Builder(
                                                Rational(1, 1),
                                                this.display?.rotation ?: Surface.ROTATION_0
                                            ).setScaleType(ViewPort.FILL_CENTER).build()

                                            val useCaseGroup = UseCaseGroup.Builder()
                                                .addUseCase(previewUseCase)
                                                .addUseCase(analyzerUseCase)
                                                .setViewPort(viewPort)
                                                .build()

                                            provider.unbindAll()
                                            val camera = provider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                useCaseGroup
                                            )

                                            cameraControl = camera.cameraControl
                                            cameraInfo = camera.cameraInfo
                                            hasFlashUnit = camera.cameraInfo.hasFlashUnit()

                                            // Restore torch state across rebinds
                                            if (hasFlashUnit && torchEnabled) {
                                                camera.cameraControl.enableTorch(true)
                                            }

                                            cameraReady = true
                                            cameraError = CameraErrorState.NONE
                                        } catch (e: Exception) {
                                            cameraError = CameraErrorState.BIND_FAILED
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            val newZoom = if (zoomRatio < 1.8f) 2.0f else 1.0f
                                            zoomRatio = newZoom
                                            cameraControl?.setZoomRatio(newZoom)
                                        },
                                        onTap = { tapOffset ->
                                            // Trigger tap-to-focus
                                            val pv = previewRef ?: return@detectTapGestures
                                            focusRingPoint = tapOffset
                                            coroutineScope.launch {
                                                focusRingAlpha.snapTo(1f)
                                                focusRingScale.snapTo(1.4f)
                                                focusRingScale.animateTo(1.0f, tween(200, easing = FastOutSlowInEasing))
                                                focusRingAlpha.animateTo(0f, tween(600, delayMillis = 300))
                                                focusRingPoint = null
                                            }
                                            try {
                                                val factory = pv.meteringPointFactory
                                                val point = factory.createPoint(tapOffset.x, tapOffset.y)
                                                val action = FocusMeteringAction.Builder(
                                                    point,
                                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                                cameraControl?.startFocusAndMetering(action)
                                            } catch (_: Exception) {}
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom != 1f) {
                                            val current = zoomRatio
                                            val newZoom = (current * zoom).coerceIn(1.0f, 8.0f)
                                            zoomRatio = newZoom
                                            cameraControl?.setZoomRatio(newZoom)
                                        }
                                    }
                                }
                        )
                    }
                }

                // ── Animated Viewfinder Geometry ───────────────────────────────
                val config = LocalConfiguration.current
                val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
                val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
                val boxSizePx = minOf(screenWidthPx, screenHeightPx) * 0.72f
                val cornerRadiusPx = with(density) { 28.dp.toPx() }

                val boxLeft = (screenWidthPx - boxSizePx) / 2f
                val boxTop = (screenHeightPx - boxSizePx) / 2f - with(density) { 36.dp.toPx() }
                val viewfinderRect = Rect(boxLeft, boxTop, boxLeft + boxSizePx, boxTop + boxSizePx)

                // Dimmed Canvas Scrim
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission && cameraError == CameraErrorState.NONE) {
                        val scrimPath = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addRoundRect(RoundRect(viewfinderRect, CornerRadius(cornerRadiusPx, cornerRadiusPx)))
                        }
                        drawPath(scrimPath, Color.Black.copy(alpha = 0.55f))
                    }
                }

                // Animated Corner Brackets & Scan Line
                if (hasCameraPermission && cameraError == CameraErrorState.NONE) {
                    val bracketTighten by animateFloatAsState(
                        targetValue = if (scanLocked) 1.0f else 0.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "bracketTighten"
                    )

                    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
                    val scanLineProgress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scanProgress"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bracketColor = if (scanLocked) Color(0xFF34D399) else Color.White
                        val bracketStroke = 4.5.dp.toPx()
                        val cornerLen = 32.dp.toPx() - (bracketTighten * 6.dp.toPx())
                        val inset = bracketTighten * 10.dp.toPx()

                        val r = Rect(
                            viewfinderRect.left + inset,
                            viewfinderRect.top + inset,
                            viewfinderRect.right - inset,
                            viewfinderRect.bottom - inset
                        )

                        // Top-left
                        drawLine(bracketColor, Offset(r.left, r.top + cornerLen), Offset(r.left, r.top), bracketStroke)
                        drawLine(bracketColor, Offset(r.left, r.top), Offset(r.left + cornerLen, r.top), bracketStroke)

                        // Top-right
                        drawLine(bracketColor, Offset(r.right - cornerLen, r.top), Offset(r.right, r.top), bracketStroke)
                        drawLine(bracketColor, Offset(r.right, r.top), Offset(r.right, r.top + cornerLen), bracketStroke)

                        // Bottom-left
                        drawLine(bracketColor, Offset(r.left, r.bottom - cornerLen), Offset(r.left, r.bottom), bracketStroke)
                        drawLine(bracketColor, Offset(r.left, r.bottom), Offset(r.left + cornerLen, r.bottom), bracketStroke)

                        // Bottom-right
                        drawLine(bracketColor, Offset(r.right - cornerLen, r.bottom), Offset(r.right, r.bottom), bracketStroke)
                        drawLine(bracketColor, Offset(r.right, r.bottom), Offset(r.right, r.bottom - cornerLen), bracketStroke)

                        // Scan sweep line (hidden when locked)
                        if (!scanLocked) {
                            val scanY = r.top + (r.height * scanLineProgress)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF38BDF8).copy(alpha = 0.75f), Color.Transparent),
                                    startY = scanY - 14.dp.toPx(),
                                    endY = scanY + 14.dp.toPx()
                                ),
                                topLeft = Offset(r.left + 8.dp.toPx(), scanY - 10.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(r.width - 16.dp.toPx(), 20.dp.toPx())
                            )
                        }
                    }
                }

                // Tap-to-focus ring drawing
                focusRingPoint?.let { pt ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = focusRingAlpha.value),
                            radius = 32.dp.toPx() * focusRingScale.value,
                            center = pt,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                // ── Error States Overlays ──────────────────────────────────────
                if (!hasCameraPermission || cameraError != CameraErrorState.NONE) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isLiquidGlass) Modifier.liquidGlassChrome(RoundedCornerShape(24.dp), enabled = true) else Modifier)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val isPermanentlyDenied = cameraError == CameraErrorState.PERMISSION_PERMANENTLY_DENIED
                                Icon(
                                    imageVector = if (!hasCameraPermission) Icons.Rounded.CameraAlt else Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = when {
                                        !hasCameraPermission -> "Camera Permission Required"
                                        cameraError == CameraErrorState.CAMERA_IN_USE -> "Camera In Use"
                                        cameraError == CameraErrorState.NO_CAMERA -> "No Camera Available"
                                        else -> "Camera Unavailable"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = when {
                                        isPermanentlyDenied -> "Camera access was permanently disabled. Please allow camera permissions in App Settings to scan codes."
                                        !hasCameraPermission -> "Petal Browser requires camera access to scan QR codes and barcodes."
                                        cameraError == CameraErrorState.CAMERA_IN_USE -> "The camera is currently in use by another application. Please close other camera apps and retry."
                                        else -> "Failed to initialize the camera hardware."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                if (isPermanentlyDenied) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        },
                                        shape = ExpressiveButtonShape
                                    ) {
                                        Text("Open Settings")
                                    }
                                } else if (!hasCameraPermission) {
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                        shape = ExpressiveButtonShape
                                    ) {
                                        Text("Grant Permission")
                                    }
                                } else {
                                    Button(
                                        onClick = { bindTrigger++ },
                                        shape = ExpressiveButtonShape
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Top Bar & Mode Selectors ───────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Close Button
                        ControlIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Close Scanner",
                            isLiquidGlass = isLiquidGlass,
                            onClick = onDismiss
                        )

                        // Mode Chips: Scan, Gallery, History
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModeChip(
                                label = "Scan",
                                icon = Icons.Rounded.QrCodeScanner,
                                isSelected = activeMode == ScannerMode.SCAN,
                                isLiquidGlass = isLiquidGlass,
                                onClick = { activeMode = ScannerMode.SCAN }
                            )

                            ModeChip(
                                label = "Gallery",
                                icon = Icons.Rounded.PhotoLibrary,
                                isSelected = false,
                                isLiquidGlass = isLiquidGlass,
                                onClick = { launchSafePhotoPicker() }
                            )

                            ModeChip(
                                label = "History",
                                icon = Icons.Rounded.History,
                                isSelected = activeMode == ScannerMode.HISTORY,
                                isLiquidGlass = isLiquidGlass,
                                onClick = { activeMode = ScannerMode.HISTORY }
                            )
                        }

                        // Settings Button
                        ControlIconButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Scanner Settings",
                            isLiquidGlass = isLiquidGlass,
                            onClick = { showSettingsScreen = true }
                        )
                    }

                    // Low-Light and Auto-Zoom Smart Hints
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AnimatedVisibility(
                            visible = isLowLight && !torchEnabled && hasFlashUnit,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .bouncyClickable {
                                        torchEnabled = true
                                        cameraControl?.enableTorch(true)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.FlashlightOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Low light — tap for torch",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = suggestAutoZoom && zoomRatio <= 1.2f,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .bouncyClickable {
                                        zoomRatio = 2.0f
                                        cameraControl?.setZoomRatio(2.0f)
                                        suggestAutoZoom = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Small code — tap to zoom 2x",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Bottom Control Rail ────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zoom Presets Pill Row: 1x, 2x, 5x
                    Surface(
                        shape = CircleShape,
                        color = if (isLiquidGlass) petalGroupSurfaceColor() else Color(0x66000000),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .then(if (isLiquidGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1.0f, 2.0f, 5.0f).forEach { preset ->
                                val isSelected = Math.abs(zoomRatio - preset) < 0.2f
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .bouncyClickable {
                                            zoomRatio = preset
                                            cameraControl?.setZoomRatio(preset)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${preset.toInt()}x",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Rail Actions: Torch, Gallery Photo, Front/Back Camera Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Torch Button (disabled if no flash unit)
                        ControlIconButton(
                            icon = if (torchEnabled) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn,
                            contentDescription = "Toggle Torch",
                            isLiquidGlass = isLiquidGlass,
                            enabled = hasFlashUnit,
                            tint = if (torchEnabled) Color(0xFFFACC15) else Color.White,
                            onClick = {
                                val next = !torchEnabled
                                torchEnabled = next
                                cameraControl?.enableTorch(next)
                            }
                        )

                        // Photo Picker / Gallery Button
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(56.dp)
                                .bouncyClickable { launchSafePhotoPicker() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isDecodingGallery) {
                                    PetalContainedLoadingIndicator(size = 24.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = "Scan from Gallery",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        // Lens Facing Toggle (guarded by hasCamera)
                        val canSwitchCamera = remember(cameraProvider) {
                            val provider = cameraProvider ?: return@remember false
                            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) &&
                                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                        }

                        ControlIconButton(
                            icon = Icons.Rounded.Cameraswitch,
                            contentDescription = "Switch Camera",
                            isLiquidGlass = isLiquidGlass,
                            enabled = canSwitchCamera,
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            }
                        )
                    }
                }

                // ── Result Bottom Sheet ────────────────────────────────────────
                if (detectedResults.isNotEmpty()) {
                    PetalScanResultSheet(
                        results = detectedResults,
                        selectedIndex = selectedResultIndex,
                        onSelectIndex = { selectedResultIndex = it },
                        onDismissRequest = {
                            detectedResults = emptyList()
                            scanLocked = false
                            analysisLocked.set(false)
                        },
                        onOpenInBrowser = { url, inNewTab ->
                            if (onResultDispatched != null) {
                                onResultDispatched(url)
                            } else {
                                PetalScanActionHandler.openUrl(context, url, inNewTab)
                            }
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isLiquidGlass: Boolean,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isLiquidGlass) petalGroupSurfaceColor() else Color(0x66000000),
        modifier = Modifier
            .size(48.dp)
            .then(if (isLiquidGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
            .bouncyClickable(enabled = enabled) { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isLiquidGlass: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isLiquidGlass -> petalGroupSurfaceColor()
            else -> Color(0x66000000)
        },
        modifier = Modifier
            .then(if (isLiquidGlass && !isSelected) Modifier.liquidGlassChrome(CircleShape, enabled = true) else Modifier)
            .bouncyClickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
            )
        }
    }
}
