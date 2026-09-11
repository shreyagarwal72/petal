/*
 * PetalPdfViewerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Built-in native Material 3 Expressive PDF viewer for Petal Browser.
 * Inspired by ImageToolbox:
 *   • Pure native android.graphics.pdf.PdfRenderer (zero heavy external binary dependencies)
 *   • Smooth multi-page continuous vertical viewing with LazyColumn
 *   • High-performance asynchronous background rendering with coroutines
 *   • Pinch-to-zoom (0.8x - 5.0x) & pan with double-tap zoom toggle
 *   • Expressive floating M3 glassmorphism top and bottom app bars with auto-hide
 *   • Jump to Page dialog with slider + direct number input
 *   • Bottom sheet page thumbnail grid drawer for fast visual skimming
 *   • Native Android PrintManager integration for instant direct printing / PDF export
 *   • Share sheet, document info sheet (page count, dimensions, file size, path)
 *   • Supports content:// and file:// URIs seamlessly
 *   • PetalPredictiveBackSurface and PetalScreenWrapper integration
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.theme.*
import com.petal.browser.view.NinjaToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Java-Callable Bridge Entry Point ────────────────────────────────────────

object PetalPdfViewerBridge {

    @JvmStatic
    @JvmOverloads
    fun createPdfViewerView(
        activity: ComponentActivity,
        pdfUri: Uri,
        displayName: String? = null,
        onBackPress: () -> Unit,
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content)
            ?: activity.window.decorView
        PetalContentSnapshot.capture(rootView)

        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember {
                    PetalContentSnapshot.current?.asImageBitmap()
                }
                DisposableEffect(Unit) {
                    onDispose { PetalContentSnapshot.clear() }
                }

                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) { AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId,
                ) {
                    PetalPdfViewerScreen(
                        backgroundSnapshot = snapshotBitmap,
                        pdfUri = pdfUri,
                        displayName = displayName ?: pdfUri.lastPathSegment ?: "Document.pdf",
                        onBackPress = onBackPress,
                    )
                }
            }
        }
    }
}

// ─── Main PDF Viewer Screen Composable ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalPdfViewerScreen(
    backgroundSnapshot: ImageBitmap? = null,
    pdfUri: Uri,
    displayName: String,
    onBackPress: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // PDF Renderer state
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Navigation & UI state
    val listState = rememberLazyListState()
    var controlsVisible by remember { mutableStateOf(true) }
    var showThumbnailSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    // Zoom & Pan state
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    // Auto-hide controls timer
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun scheduleHideControls() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(4000)
            if (isActive) controlsVisible = false
        }
    }
    fun toggleControls() {
        controlsVisible = !controlsVisible
        if (controlsVisible) scheduleHideControls()
    }
    LaunchedEffect(Unit) { scheduleHideControls() }

    // Load PDF safely from URI
    DisposableEffect(pdfUri) {
        isLoading = true
        loadError = null
        try {
            val descriptor: ParcelFileDescriptor? = when (pdfUri.scheme) {
                "content" -> context.contentResolver.openFileDescriptor(pdfUri, "r")
                "file", null -> {
                    val filePath = pdfUri.path ?: pdfUri.toString().removePrefix("file://")
                    val file = File(filePath)
                    if (file.exists()) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        null
                    }
                }
                else -> {
                    runCatching { context.contentResolver.openFileDescriptor(pdfUri, "r") }.getOrNull()
                }
            }

            if (descriptor != null) {
                val renderer = PdfRenderer(descriptor)
                pfd = descriptor
                pdfRenderer = renderer
                pageCount = renderer.pageCount
                isLoading = false
            } else {
                loadError = "Unable to open PDF document descriptor"
                isLoading = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadError = "Failed to load PDF: " + (e.localizedMessage ?: "Unknown error")
            isLoading = false
        }

        onDispose {
            try {
                pdfRenderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Determine currently visible page index from LazyList
    val currentVisiblePage = remember {
        derivedStateOf {
            if (pageCount == 0) 0
            else (listState.firstVisibleItemIndex).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }

    PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBackPress,
    ) {
        PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    com.petal.browser.ui.components.PetalThemedSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(innerPadding)
                ) {
                    when {
                        isLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "Loading PDF...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        loadError != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "Cannot Display PDF",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = loadError ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                        Spacer(Modifier.height(18.dp))
                                        FilledTonalButton(
                                            onClick = onBackPress,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Go Back")
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // ── Multi-Page Lazy Column with Interactive Zoom & Pan ──
                            val gestureModifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pdfUri) {
                                    detectTapGestures(
                                        onTap = { toggleControls() },
                                        onDoubleTap = { tapOffset ->
                                            coroutineScope.launch {
                                                if (scaleAnim.value > 1.2f) {
                                                    scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                                    offsetXAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                                    offsetYAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                                } else {
                                                    val targetScale = 2.4f
                                                    val centreX = size.width / 2f
                                                    val centreY = size.height / 2f
                                                    scaleAnim.animateTo(targetScale, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                                                    offsetXAnim.animateTo(
                                                        ((centreX - tapOffset.x) * (targetScale - 1f)).coerceIn(-centreX, centreX),
                                                        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium)
                                                    )
                                                    offsetYAnim.animateTo(
                                                        ((centreY - tapOffset.y) * (targetScale - 1f)).coerceIn(-centreY, centreY),
                                                        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                                .pointerInput(pdfUri) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        coroutineScope.launch {
                                            val newScale = (scaleAnim.value * zoom).coerceIn(0.8f, 5.0f)
                                            scaleAnim.snapTo(newScale)
                                            val maxX = (size.width * (newScale - 1f)) / 2f
                                            val maxY = (size.height * (newScale - 1f)) / 2f
                                            offsetXAnim.snapTo(if (newScale > 1f) (offsetXAnim.value + pan.x).coerceIn(-maxX, maxX) else 0f)
                                            offsetYAnim.snapTo(if (newScale > 1f) (offsetYAnim.value + pan.y).coerceIn(-maxY, maxY) else 0f)
                                            if (newScale < 1f) {
                                                scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                                offsetXAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                                offsetYAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                            }
                                        }
                                    }
                                }

                            Box(
                                modifier = gestureModifier
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scaleAnim.value
                                            scaleY = scaleAnim.value
                                            translationX = offsetXAnim.value
                                            translationY = offsetYAnim.value
                                        },
                                    contentPadding = PaddingValues(top = 76.dp, bottom = 96.dp, start = 12.dp, end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(pageCount) { pageIndex ->
                                        PdfPageView(
                                            renderer = pdfRenderer,
                                            pageIndex = pageIndex,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                        )
                                    }
                                }
                            }

                            // ── Top App Bar (M3 Expressive Floating Surface) ──
                            AnimatedVisibility(
                                visible = controlsVisible,
                                modifier = Modifier.align(Alignment.TopCenter),
                                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it }
                            ) {
                                PdfViewerTopBar(
                                    title = displayName,
                                    currentPage = currentVisiblePage.value + 1,
                                    totalPages = pageCount,
                                    onBack = onBackPress,
                                    onJumpPage = { showJumpDialog = true },
                                    onShare = { sharePdfDocument(context, pdfUri, displayName) },
                                    onPrint = { printPdfDocument(context, pdfUri, displayName) },
                                    onInfo = { showInfoSheet = true }
                                )
                            }

                            // ── Bottom Floating Pill Action Bar ──
                            AnimatedVisibility(
                                visible = controlsVisible,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it },
                                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it }
                            ) {
                                PdfViewerBottomBar(
                                    currentPage = currentVisiblePage.value + 1,
                                    totalPages = pageCount,
                                    scale = scaleAnim.value,
                                    onResetZoom = {
                                        coroutineScope.launch {
                                            scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                            offsetXAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                            offsetYAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                        }
                                    },
                                    onShowThumbnails = { showThumbnailSheet = true },
                                    onJumpPage = { showJumpDialog = true }
                                )
                            }
                        }
                    }
                }
            }

            // ── Jump to Page Dialog ──
            if (showJumpDialog && pageCount > 0) {
                PdfJumpToPageDialog(
                    currentPage = currentVisiblePage.value + 1,
                    totalPages = pageCount,
                    onDismiss = { showJumpDialog = false },
                    onJump = { targetPage ->
                        showJumpDialog = false
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetPage - 1)
                        }
                    }
                )
            }

            // ── Thumbnail Grid Skimmer Bottom Sheet ──
            if (showThumbnailSheet && pdfRenderer != null && pageCount > 0) {
                PdfThumbnailSheet(
                    renderer = pdfRenderer!!,
                    pageCount = pageCount,
                    currentPage = currentVisiblePage.value,
                    onDismiss = { showThumbnailSheet = false },
                    onSelectPage = { selectedPage ->
                        showThumbnailSheet = false
                        coroutineScope.launch {
                            listState.animateScrollToItem(selectedPage)
                        }
                    }
                )
            }

            // ── Document Info Bottom Sheet ──
            if (showInfoSheet) {
                PdfInfoBottomSheet(
                    uri = pdfUri,
                    displayName = displayName,
                    pageCount = pageCount,
                    onDismiss = { showInfoSheet = false }
                )
            }
        }
    }
}

// ─── Single Page Renderer View ───────────────────────────────────────────────

@Composable
private fun PdfPageView(
    renderer: PdfRenderer?,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageAspectRatio by remember { mutableFloatStateOf(1.414f) }

    LaunchedEffect(renderer, pageIndex) {
        if (renderer == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val page = synchronized(renderer) {
                    if (pageIndex in 0 until renderer.pageCount) renderer.openPage(pageIndex) else null
                }
                if (page != null) {
                    val w = page.width
                    val h = page.height
                    pageAspectRatio = if (w > 0) h.toFloat() / w.toFloat() else 1.414f

                    val renderScale = 2.0f
                    val bitmapWidth = (w * renderScale).toInt().coerceAtLeast(300)
                    val bitmapHeight = (h * renderScale).toInt().coerceAtLeast(300)

                    val bmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)

                    synchronized(renderer) {
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                    }
                    pageBitmap = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(pageIndex) {
        onDispose {
            pageBitmap?.recycle()
            pageBitmap = null
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / pageAspectRatio),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page " + (pageIndex + 1),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}

// ─── Expressive Top Bar ──────────────────────────────────────────────────────

@Composable
private fun PdfViewerTopBar(
    title: String,
    currentPage: Int,
    totalPages: Int,
    onBack: () -> Unit,
    onJumpPage: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.width(6.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onJumpPage() }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (totalPages > 0) {
                    Text(
                        text = "Page $currentPage of $totalPages • Tap to jump",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onPrint) {
                Icon(
                    imageVector = Icons.Rounded.Print,
                    contentDescription = "Print document",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text("Jump to page") },
                        leadingIcon = { Icon(Icons.Rounded.FindInPage, null) },
                        onClick = { menuExpanded = false; onJumpPage() }
                    )
                    DropdownMenuItem(
                        text = { Text("Document info") },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        onClick = { menuExpanded = false; onInfo() }
                    )
                }
            }
        }
    }
}

// ─── Expressive Floating Bottom Bar ──────────────────────────────────────────

@Composable
private fun PdfViewerBottomBar(
    currentPage: Int,
    totalPages: Int,
    scale: Float,
    onResetZoom: () -> Unit,
    onShowThumbnails: () -> Unit,
    onJumpPage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
            modifier = Modifier.border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(28.dp)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable { onJumpPage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "$currentPage / $totalPages",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                IconButton(onClick = onShowThumbnails, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
                        contentDescription = "Thumbnails",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (kotlin.math.abs(scale - 1f) > 0.05f) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.clickable { onResetZoom() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomOutMap,
                                contentDescription = "Reset zoom",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Jump to Page Dialog ─────────────────────────────────────────────────────

@Composable
private fun PdfJumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var targetPage by remember { mutableIntStateOf(currentPage) }
    var textInput by remember { mutableStateOf(currentPage.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = Icons.Rounded.FindInPage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Jump to Page", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Page $targetPage of $totalPages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (totalPages > 1) {
                    Slider(
                        value = targetPage.toFloat(),
                        onValueChange = {
                            val p = it.toInt().coerceIn(1, totalPages)
                            targetPage = p
                            textInput = p.toString()
                        },
                        valueRange = 1f..totalPages.toFloat(),
                        steps = (totalPages - 2).coerceAtLeast(0)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }
                        textInput = filtered
                        val parsed = filtered.toIntOrNull()
                        if (parsed != null && parsed in 1..totalPages) {
                            targetPage = parsed
                        }
                    },
                    label = { Text("Page number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed = textInput.toIntOrNull() ?: targetPage
                        onJump(parsed.coerceIn(1, totalPages))
                    }),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textInput.toIntOrNull() ?: targetPage
                    onJump(parsed.coerceIn(1, totalPages))
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Jump")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
        }
    )
}

// ─── Thumbnail Skimmer Bottom Sheet ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfThumbnailSheet(
    renderer: PdfRenderer,
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onSelectPage: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Pages ($pageCount)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(pageCount) { index ->
                    PdfThumbnailItem(
                        renderer = renderer,
                        pageIndex = index,
                        isSelected = index == currentPage,
                        onClick = { onSelectPage(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfThumbnailItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(renderer, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val page = synchronized(renderer) {
                    if (pageIndex in 0 until renderer.pageCount) renderer.openPage(pageIndex) else null
                }
                if (page != null) {
                    val w = (page.width / 2).coerceAtLeast(120)
                    val h = (page.height / 2).coerceAtLeast(160)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    synchronized(renderer) {
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                    }
                    thumbBitmap = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(pageIndex) {
        onDispose {
            thumbBitmap?.recycle()
            thumbBitmap = null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = if (isSelected) 4.dp else 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .then(
                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                )
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap!!.asImageBitmap(),
                    contentDescription = "Page " + (pageIndex + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "" + (pageIndex + 1),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Document Info Bottom Sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfInfoBottomSheet(
    uri: Uri,
    displayName: String,
    pageCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fileInfo by produceState<Pair<String, String>?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val size: String
                val date: String
                if (uri.scheme == "content") {
                    val fd = context.contentResolver.openFileDescriptor(uri, "r")
                    size = if (fd != null) {
                        val s = formatFileSize(fd.statSize)
                        fd.close()
                        s
                    } else "—"
                    date = "—"
                } else {
                    val path = uri.path ?: uri.toString().removePrefix("file://")
                    val file = File(path)
                    size = if (file.exists()) formatFileSize(file.length()) else "—"
                    date = if (file.exists()) SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(file.lastModified())) else "—"
                }
                Pair(size, date)
            }.getOrNull()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Document Info",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PdfInfoRow("File Name", displayName)
            PdfInfoRow("Pages", "$pageCount pages")
            if (fileInfo != null) {
                PdfInfoRow("File Size", fileInfo!!.first)
                if (fileInfo!!.second != "—") {
                    PdfInfoRow("Modified", fileInfo!!.second)
                }
            }
            PdfInfoRow("Format", "PDF Document (application/pdf)")
            val displayPath = uri.path ?: uri.toString()
            if (displayPath.isNotBlank()) {
                PdfInfoRow("Location", displayPath)
            }
        }
    }
}

@Composable
private fun PdfInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

// ─── Helpers: Print, Share, File Formatting ──────────────────────────────────

private fun printPdfDocument(context: Context, uri: Uri, title: String) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            NinjaToast.show(context, "Print service unavailable on this device")
            return
        }

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(title)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("No output destination")
                    return
                }
                try {
                    val input = when (uri.scheme) {
                        "content" -> context.contentResolver.openInputStream(uri)
                        else -> FileInputStream(File(uri.path ?: uri.toString().removePrefix("file://")))
                    }
                    val output = FileOutputStream(destination.fileDescriptor)
                    input?.use { inStream ->
                        output.use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        printManager.print("Petal PDF - $title", adapter, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        e.printStackTrace()
        NinjaToast.show(context, "Unable to print: " + (e.localizedMessage ?: "Unknown error"))
    }
}

private fun sharePdfDocument(context: Context, uri: Uri, title: String) {
    try {
        val contentUri: Uri = if (uri.scheme == "file" || uri.scheme == null) {
            val filePath = uri.path ?: uri.toString().removePrefix("file://")
            val file = File(filePath)
            if (!file.exists()) {
                NinjaToast.show(context, "File does not exist")
                return
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
        } else {
            uri
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Share PDF Document").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        e.printStackTrace()
        NinjaToast.show(context, "Unable to share PDF: " + (e.localizedMessage ?: "Unknown error"))
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = 0
    var size = bytes.toDouble()
    while (size >= 1024 && digitGroups < units.size - 1) {
        size /= 1024
        digitGroups++
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, units[digitGroups])
}
