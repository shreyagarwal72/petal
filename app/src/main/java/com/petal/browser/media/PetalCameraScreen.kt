package com.petal.browser.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.rememberPetalGroupPressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

// ---------------------------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------------------------

private enum class FlashOption(val mode: Int, val icon: ImageVector, val label: String) {
    OFF(ImageCapture.FLASH_MODE_OFF, Icons.Rounded.FlashOff, "Flash off"),
    AUTO(ImageCapture.FLASH_MODE_AUTO, Icons.Rounded.FlashAuto, "Flash auto"),
    ON(ImageCapture.FLASH_MODE_ON, Icons.Rounded.FlashOn, "Flash on");

    fun next() = entries[(ordinal + 1) % entries.size]
}

private enum class TimerOption(val seconds: Int, val label: String) {
    OFF(0, "Timer off"),
    THREE(3, "3s"),
    TEN(10, "10s");

    fun next() = entries[(ordinal + 1) % entries.size]
}

private enum class RatioOption(val label: String, val ratio: Float, val strategy: AspectRatioStrategy) {
    R4_3("4:3", 3f / 4f, AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY),
    R16_9("16:9", 9f / 16f, AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY);

    fun next() = entries[(ordinal + 1) % entries.size]
}

private data class Shot(val file: File, val uri: Uri, val preview: ImageBitmap?)

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

/**
 * Petal's built-in camera: instant photo capture used inside the photo & video picker.
 *
 * Preview -> tap to focus, pinch to zoom, flash / timer / grid / aspect toggles, front-back switch.
 * After the shutter the photo is shown for review: Retake or Use photo. Photos are written to the app
 * cache (never to the gallery) and handed back as a content URI.
 */
@Composable
fun PetalCameraScreen(
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val haptics = remember { PetalHapticEngine.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        permissionAsked = true
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    // Camera state
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by remember { mutableStateOf(FlashOption.OFF) }
    var timer by remember { mutableStateOf(TimerOption.OFF) }
    var ratio by remember { mutableStateOf(RatioOption.R4_3) }
    var showGrid by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Interaction state
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    val flashOverlay = remember { Animatable(0f) }

    fun discard(s: Shot?) { runCatching { s?.file?.delete() } }

    val latestShot = rememberUpdatedState(shot)
    var handedOff by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { executor.shutdownNow() }
            // A photo that was captured but never used does not stay in the cache.
            if (!handedOff) discard(latestShot.value)
        }
    }

    // Bind / rebind the camera when lens or aspect ratio changes.
    LaunchedEffect(hasPermission, lensFacing, ratio, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        if (!hasPermission) return@LaunchedEffect
        cameraError = false
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ runCatching { future.get() }.getOrNull()?.let { if (cont.isActive) cont.resume(it) } }, ContextCompat.getMainExecutor(context))
        }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val resolution = ResolutionSelector.Builder().setAspectRatioStrategy(ratio.strategy).build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build().also { it.surfaceProvider = pv.surfaceProvider }
        val capture = ImageCapture.Builder()
            .setResolutionSelector(resolution)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
            .setFlashMode(flash.mode)
            .build()
        try {
            provider.unbindAll()
            val bound = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            camera = bound
            imageCapture = capture
            hasFlashUnit = bound.cameraInfo.hasFlashUnit()
            if (!hasFlashUnit) flash = FlashOption.OFF
            bound.cameraInfo.zoomState.value?.let {
                minZoom = it.minZoomRatio
                maxZoom = it.maxZoomRatio
                zoom = it.zoomRatio
            }
        } catch (_: Exception) {
            cameraError = true
        }
    }
    LaunchedEffect(flash) { imageCapture?.flashMode = flash.mode }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (capturing) return
        capturing = true
        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "PETAL_IMG_${stamp}.jpg")
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT }
        val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
        haptics.playClick(context)
        scope.launch {
            flashOverlay.snapTo(0.85f)
            flashOverlay.animateTo(0f, tween(260))
        }
        capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                scope.launch {
                    val bmp = withContext(Dispatchers.IO) { decodePreview(file) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    shot = Shot(file, uri, bmp?.asImageBitmap())
                    capturing = false
                }
            }

            override fun onError(exception: ImageCaptureException) {
                capturing = false
                runCatching { file.delete() }
                com.petal.browser.view.PetalToast.show(context, "Couldn't capture photo")
            }
        })
    }

    fun startCapture() {
        if (capturing || countdown > 0) return
        val seconds = timer.seconds
        if (seconds == 0) { takePhoto(); return }
        scope.launch {
            for (s in seconds downTo 1) {
                countdown = s
                haptics.playTick(context)
                delay(1000)
            }
            countdown = 0
            takePhoto()
        }
    }

    // Back: cancel countdown -> close review -> leave the camera.
    BackHandler {
        when {
            countdown > 0 -> countdown = 0
            shot != null -> { discard(shot); shot = null }
            else -> onDismiss()
        }
    }

    // --- UI -------------------------------------------------------------------------------
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopControls(
                reviewing = shot != null,
                flash = flash,
                flashAvailable = hasFlashUnit && shot == null,
                timer = timer,
                ratio = ratio,
                showGrid = showGrid,
                onClose = { haptics.playClick(context); if (shot != null) { discard(shot); shot = null } else onDismiss() },
                onFlash = { haptics.playClick(context); flash = flash.next() },
                onTimer = { haptics.playClick(context); timer = timer.next() },
                onRatio = { haptics.playClick(context); ratio = ratio.next() },
                onGrid = { haptics.playClick(context); showGrid = !showGrid },
            )

            Spacer(Modifier.weight(0.4f))

            // Viewport
            val viewportShape = RoundedCornerShape(32.dp)
            val borderColor by animateColorAsStateCompat(
                if (shot != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Surface(
                shape = viewportShape,
                color = Color.Black,
                border = BorderStroke(2.5.dp, borderColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio.ratio)
                    .clip(viewportShape),
            ) {
                Box(Modifier.fillMaxSize().onSizeChanged { viewportSize = it }) {
                    if (hasPermission) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    previewView = this
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Tap to focus + pinch to zoom
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(camera, previewView) {
                                    detectTapGestures { offset ->
                                        val pv = previewView ?: return@detectTapGestures
                                        val cam = camera ?: return@detectTapGestures
                                        val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                        runCatching { cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build()) }
                                        focusPoint = offset
                                        focusTick++
                                        haptics.playTick(context)
                                    }
                                }
                                .pointerInput(camera, minZoom, maxZoom) {
                                    detectTransformGestures { _, _, gestureZoom, _ ->
                                        val cam = camera ?: return@detectTransformGestures
                                        zoom = (zoom * gestureZoom).coerceIn(minZoom, maxZoom)
                                        cam.cameraControl.setZoomRatio(zoom)
                                    }
                                },
                        )

                        if (showGrid && shot == null) RuleOfThirdsGrid()
                        FocusRing(point = focusPoint, tick = focusTick)

                        // Zoom pill
                        AnimatedVisibility(
                            visible = shot == null && maxZoom > minZoom && zoom > minZoom + 0.05f,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                        ) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                                Text(
                                    String.format(Locale.US, "%.1f×", zoom),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    } else {
                        PermissionPrompt(
                            denied = permissionAsked,
                            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        )
                    }

                    if (cameraError) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Rounded.NoPhotography, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Camera unavailable", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Another app may be using it.", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Review image
                    androidx.compose.animation.AnimatedVisibility(
                        visible = shot != null,
                        enter = fadeIn(tween(160)) + scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), initialScale = 0.92f),
                        exit = fadeOut(tween(120)),
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            shot?.preview?.let {
                                Image(it, contentDescription = "Captured photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }

                    // Shutter flash + countdown
                    Box(Modifier.fillMaxSize().alpha(flashOverlay.value).background(Color.White))
                    AnimatedContent(
                        targetState = countdown,
                        transitionSpec = {
                            (scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.4f) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 1.6f) + fadeOut())
                        },
                        modifier = Modifier.align(Alignment.Center),
                        label = "countdown",
                    ) { value ->
                        if (value > 0) {
                            Text(
                                value.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    shot != null -> "Looks good?"
                    countdown > 0 -> "Hold still…"
                    else -> "Tap to focus · Pinch to zoom"
                },
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            // Bottom controls
            AnimatedContent(
                targetState = shot != null,
                transitionSpec = {
                    (slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { it / 2 } + fadeOut())
                },
                label = "cameraControls",
            ) { reviewing ->
                if (reviewing) {
                    ReviewControls(
                        onRetake = { haptics.playClick(context); discard(shot); shot = null },
                        onUse = { haptics.playClick(context); shot?.let { handedOff = true; onCaptured(it.uri) } },
                    )
                } else {
                    CaptureControls(
                        enabled = hasPermission && imageCapture != null && !capturing && !cameraError,
                        capturing = capturing || countdown > 0,
                        onShutter = ::startCapture,
                        onFlip = {
                            haptics.playClick(context)
                            zoom = 1f
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        },
                        onCancelCountdown = { countdown = 0 },
                        counting = countdown > 0,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun animateColorAsStateCompat(target: Color) =
    androidx.compose.animation.animateColorAsState(target, tween(220), label = "cameraBorder")

@Composable
private fun TopControls(
    reviewing: Boolean,
    flash: FlashOption,
    flashAvailable: Boolean,
    timer: TimerOption,
    ratio: RatioOption,
    showGrid: Boolean,
    onClose: () -> Unit,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onRatio: () -> Unit,
    onGrid: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundControl(Icons.Rounded.Close, "Close camera", onClick = onClose)

        Column(Modifier.weight(1f)) {
            Text("Petal Camera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (reviewing) "Review photo" else "Take a photo to upload",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }

        AnimatedVisibility(visible = !reviewing, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (flashAvailable) {
                    RoundControl(flash.icon, flash.label, active = flash != FlashOption.OFF, onClick = onFlash)
                }
                RoundControl(
                    icon = if (timer == TimerOption.OFF) Icons.Rounded.TimerOff else Icons.Rounded.Timer,
                    description = timer.label,
                    active = timer != TimerOption.OFF,
                    badge = timer.label.takeIf { timer != TimerOption.OFF },
                    onClick = onTimer,
                )
                RoundControl(Icons.Rounded.GridOn, "Grid", active = showGrid, onClick = onGrid)
                Surface(
                    onClick = onRatio,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    modifier = Modifier.height(44.dp),
                ) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(ratio.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    badge: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressScale = rememberPetalGroupPressScale(source)
    val container by androidx.compose.animation.animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        tween(160),
        label = "roundControlBg",
    )
    val content by androidx.compose.animation.animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        tween(160),
        label = "roundControlFg",
    )
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = CircleShape,
        color = container,
        modifier = Modifier.size(44.dp).scale(pressScale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = content, modifier = Modifier.size(22.dp))
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CaptureControls(
    enabled: Boolean,
    capturing: Boolean,
    counting: Boolean,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    onCancelCountdown: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(54.dp)) // balances the flip button so the shutter stays centred
            ShutterButton(enabled = enabled || counting, busy = capturing, counting = counting, onClick = if (counting) onCancelCountdown else onShutter)
            Surface(
                onClick = onFlip,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Cameraswitch, contentDescription = "Switch camera", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, counting: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressScale = rememberPetalGroupPressScale(source)
    // Inner disc morphs: circle -> rounded square while a timer is counting (tap to cancel).
    val innerRadius by animateDpAsState(if (counting) 12.dp else 32.dp, spring(Spring.DampingRatioMediumBouncy), label = "shutterRadius")
    val innerSize by animateDpAsState(if (counting) 30.dp else if (busy) 50.dp else 58.dp, spring(Spring.DampingRatioMediumBouncy), label = "shutterSize")
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(4.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(78.dp).scale(pressScale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(innerSize)
                    .clip(RoundedCornerShape(innerRadius))
                    .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            )
        }
    }
}

@Composable
private fun ReviewControls(onRetake: () -> Unit, onUse: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            FilledTonalButton(
                onClick = onRetake,
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(Icons.Rounded.Replay, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retake", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onUse,
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use photo", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RuleOfThirdsGrid() {
    val line = Color.White.copy(alpha = 0.35f)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (i in 1..2) {
            drawLine(line, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 1.5f)
            drawLine(line, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 1.5f)
        }
    }
}

@Composable
private fun FocusRing(point: Offset?, tick: Int) {
    if (point == null) return
    val scale = remember(tick) { Animatable(1.5f) }
    val alpha = remember(tick) { Animatable(1f) }
    LaunchedEffect(tick) {
        launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
        delay(700)
        alpha.animateTo(0f, tween(300))
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val r = 34.dp.toPx() * scale.value
        drawCircle(color.copy(alpha = alpha.value), radius = r, center = point, style = Stroke(width = 2.5.dp.toPx()))
        drawCircle(color.copy(alpha = alpha.value), radius = 3.dp.toPx(), center = point)
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CameraAlt, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (denied) "Camera access was denied. Allow it to take photos." else "Camera permission is required to take photos.",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onGrant, shape = RoundedCornerShape(16.dp)) { Text("Allow camera") }
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

/** Decodes a downsampled, EXIF-rotated bitmap for the review screen. */
private fun decodePreview(file: File, maxEdge: Int = 1600): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null

    val orientation = ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
    }
    if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}.getOrNull()
