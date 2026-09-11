/*
 * PetalImageViewerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Built-in native image viewer for Petal Browser.
 * Material 3 Expressive — offline-first, works on all image sources:
 *   • Local downloaded files (Downloads page preview strip + row items)
 *   • Web/remote URLs (long-press on website images → context menu)
 *   • External share intents (ACTION_SEND and ACTION_VIEW for image MIME types)
 *
 * Features:
 *  - HorizontalPager gallery (swipe left/right between images)
 *  - Pinch-to-zoom (0.8× – 5×), pan when zoomed
 *  - Double-tap toggle zoom (1× ↔ 2.5×) with spring animation
 *  - Single-tap auto-hide/show controls (top bar + bottom bar)
 *  - Rotate 90° CW (visual only)
 *  - Share, Delete (local only, with undo), Set as Wallpaper
 *  - Image Info bottom sheet (dimensions, size, path/URL, date)
 *  - PetalPredictiveBackSurface (swipe-to-dismiss)
 *  - Network images loaded with Coil (already in project) — offline BitmapFactory for local
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.downloads

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.RotateRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─── Unified image entry — covers local files AND remote/network URLs ─────────

/**
 * Represents a single image that the viewer can display.
 * Either backed by a [DownloadItem] (local file) or a raw remote URL.
 */
data class PetalViewerImageEntry(
    /** Human-readable label shown in the title bar */
    val label: String,
    /** Source URL — http/https for remote images, file:// or content:// for local */
    val sourceUrl: String,
    /** Non-null when this entry maps to a downloaded file (enables Delete, Wallpaper, disk Info) */
    val downloadItem: DownloadItem? = null,
    /** Whether this entry is a remote/network image that must be fetched */
    val isRemote: Boolean = sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://"),
)

// ─── Bridge (Java-callable entry point) ───────────────────────────────────────

object PetalImageViewerBridge {

    // ── Common internal factory ────────────────────────────────────────────────

    private fun buildComposeView(
        activity: ComponentActivity,
        initialIndex: Int,
        entries: List<PetalViewerImageEntry>,
        onBackPress: () -> Unit,
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content)
            ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)

        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember {
                    com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap()
                }
                DisposableEffect(Unit) {
                    onDispose { com.petal.browser.predictive.PetalContentSnapshot.clear() }
                }

                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName  = sp.getString("sp_app_font",    "GS_FLEX")       ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT")    ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id",  com.petal.browser.ui.theme.defaultPaletteId)
                    ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled     = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    com.petal.browser.ui.theme.AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) }
                    catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled    = isAmoled,
                    appFont      = appFont,
                    colorStyle   = colorStyle,
                    paletteId    = paletteId,
                ) {
                    PetalImageViewerScreen(
                        backgroundSnapshot = snapshotBitmap,
                        initialIndex       = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
                        entries            = entries,
                        onBackPress        = onBackPress,
                    )
                }
            }
        }
    }

    // ── 1. Local downloaded images (Downloads page) ────────────────────────────

    /**
     * Opens the viewer for images already downloaded to the device.
     * Used by the Downloads page preview strip and row items.
     */
    @JvmStatic
    fun createViewerView(
        activity: ComponentActivity,
        initialItem: DownloadItem,
        allImageItems: List<DownloadItem>,
        onBackPress: () -> Unit,
    ): ComposeView {
        val entries = allImageItems.map { item ->
            PetalViewerImageEntry(
                label        = item.fileName,
                sourceUrl    = item.localUri ?: item.fileUrl,
                downloadItem = item,
                isRemote     = false,
            )
        }
        val initialIndex = allImageItems.indexOfFirst { it.id == initialItem.id }.coerceAtLeast(0)
        return buildComposeView(activity, initialIndex, entries, onBackPress)
    }

    // ── 2. Remote / web images (website long-press) ────────────────────────────

    /**
     * Opens the viewer for a single remote image URL (e.g. from a website long-press).
     * Uses Coil for network loading — works with any HTTP/HTTPS image.
     *
     * @param imageUrl   The remote image URL to display.
     * @param pageTitle  Optional page title shown as subtitle.
     */
    @JvmStatic
    @JvmOverloads
    fun createWebViewerView(
        activity: ComponentActivity,
        imageUrl: String,
        pageTitle: String? = null,
        onBackPress: () -> Unit,
    ): ComposeView {
        val label = pageTitle ?: try {
            Uri.parse(imageUrl).lastPathSegment?.takeIf { it.isNotBlank() } ?: imageUrl
        } catch (_: Exception) { imageUrl }
        val entry = PetalViewerImageEntry(
            label     = label,
            sourceUrl = imageUrl,
            isRemote  = true,
        )
        return buildComposeView(activity, 0, listOf(entry), onBackPress)
    }

    // ── 3. External intent sources (ACTION_VIEW image/*, ACTION_SEND image/*) ──

    /**
     * Opens the viewer for an image delivered via an external Android intent
     * (e.g. "Open with" from Files, Share from another app).
     *
     * @param contentUri  The content:// or file:// URI from the intent.
     * @param displayName Optional display name from the resolver.
     */
    @JvmStatic
    @JvmOverloads
    fun createExternalViewerView(
        activity: ComponentActivity,
        contentUri: Uri,
        displayName: String? = null,
        onBackPress: () -> Unit,
    ): ComposeView {
        val label = displayName ?: contentUri.lastPathSegment ?: "Image"
        val entry = PetalViewerImageEntry(
            label     = label,
            sourceUrl = contentUri.toString(),
            isRemote  = false,
        )
        return buildComposeView(activity, 0, listOf(entry), onBackPress)
    }
}


// ─── Root Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalImageViewerScreen(
    backgroundSnapshot: ImageBitmap? = null,
    initialIndex: Int = 0,
    entries: List<PetalViewerImageEntry>,
    onBackPress: () -> Unit = {},
) {
    val pagerState   = rememberPagerState(initialPage = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) { entries.size }
    val currentEntry = entries.getOrNull(pagerState.currentPage)

    // Controls visibility with auto-hide
    var controlsVisible by remember { mutableStateOf(true) }
    val coroutineScope  = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(3500)
            if (isActive) controlsVisible = false
        }
    }

    fun toggleControls() {
        controlsVisible = !controlsVisible
        if (controlsVisible) scheduleHide()
    }

    LaunchedEffect(Unit) { scheduleHide() }

    // Per-page rotation (keyed by sourceUrl)
    val rotationMap = remember { mutableStateMapOf<String, Float>() }
    fun rotateCurrentEntry() {
        val key = currentEntry?.sourceUrl ?: return
        rotationMap[key] = ((rotationMap[key] ?: 0f) + 90f) % 360f
    }

    // Info sheet
    var showInfoSheet by remember { mutableStateOf(false) }

    // Snackbar for delete undo
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun performDeleteCurrent() {
        val item = currentEntry?.downloadItem ?: return
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message     = "Deleted ${item.fileName}",
                actionLabel = "Undo",
                duration    = SnackbarDuration.Short,
            )
            if (result != SnackbarResult.ActionPerformed) {
                PetalFetchDownloadBridge.deleteDownload(context, item)
            }
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack  = onBackPress,
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = Color.Black,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    com.petal.browser.ui.components.PetalThemedSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.navigationBarsPadding()
                    )
                },
            ) { innerPadding ->

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(innerPadding)
                ) {
                    // ── Gallery Pager ────────────────────────────────
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { entries[it].sourceUrl },
                    ) { page ->
                        val entry   = entries[page]
                        val rotDeg  = rotationMap[entry.sourceUrl] ?: 0f
                        ZoomableImagePage(
                            entry       = entry,
                            rotationDeg = rotDeg,
                            onSingleTap = ::toggleControls,
                        )
                    }

                    // ── Top Bar ──────────────────────────────────────
                    AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
                        exit  = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it },
                    ) {
                        ImageViewerTopBar(
                            currentIndex = pagerState.currentPage,
                            total        = entries.size,
                            fileName     = currentEntry?.label ?: "",
                            isRemote     = currentEntry?.isRemote ?: false,
                            onBack       = onBackPress,
                            onShare      = {
                                val entry = currentEntry ?: return@ImageViewerTopBar
                                if (entry.downloadItem != null) {
                                    shareDownloadedFile(context, entry.downloadItem)
                                } else {
                                    // Share remote URL as text
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, entry.sourceUrl)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share image").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                }
                            },
                            onOpenExternal = {
                                val entry = currentEntry ?: return@ImageViewerTopBar
                                if (entry.downloadItem != null) {
                                    openDownloadedFile(context, entry.downloadItem)
                                } else {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.sourceUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            onCopyUrl = {
                                val url = currentEntry?.sourceUrl ?: return@ImageViewerTopBar
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Image URL", url))
                                com.petal.browser.view.NinjaToast.show(context, "URL copied")
                            },
                        )
                    }

                    // ── Bottom Bar ───────────────────────────────────
                    AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it },
                        exit  = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it },
                    ) {
                        ImageViewerBottomBar(
                            entry        = currentEntry,
                            onRotate     = ::rotateCurrentEntry,
                            onDelete     = ::performDeleteCurrent,
                            onWallpaper  = {
                                val entry = currentEntry ?: return@ImageViewerBottomBar
                                if (entry.downloadItem != null) {
                                    setAsWallpaper(context, entry.downloadItem)
                                } else {
                                    com.petal.browser.view.NinjaToast.show(context, "Download the image first to set as wallpaper")
                                }
                            },
                            onInfo       = { showInfoSheet = true },
                        )
                    }

                    // ── Page indicator dots ──────────────────────────
                    if (entries.size > 1) {
                        AnimatedVisibility(
                            visible = controlsVisible,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 100.dp),
                            enter = fadeIn(tween(220)),
                            exit  = fadeOut(tween(180)),
                        ) {
                            PageIndicatorDots(
                                count   = entries.size,
                                current = pagerState.currentPage,
                            )
                        }
                    }
                }
            }

            // ── Image Info Bottom Sheet ──────────────────────────
            if (showInfoSheet && currentEntry != null) {
                ImageInfoBottomSheet(
                    entry     = currentEntry,
                    onDismiss = { showInfoSheet = false },
                )
            }
        }
    }
}


// ─── Zoomable Image Page ─────────────────────────────────────────────────────

@Composable
private fun ZoomableImagePage(
    entry: PetalViewerImageEntry,
    rotationDeg: Float,
    onSingleTap: () -> Unit,
) {
    val context = LocalContext.current

    // Zoom / pan state
    val scaleAnim   = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val scope       = rememberCoroutineScope()

    // Rotation animation
    val animatedRotation by animateFloatAsState(
        targetValue  = rotationDeg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rotation",
    )

    val gestureModifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        // Double-tap zoom toggle
        .pointerInput(entry.sourceUrl) {
            detectTapGestures(
                onTap = { onSingleTap() },
                onDoubleTap = { tapOffset ->
                    scope.launch {
                        if (scaleAnim.value > 1.2f) {
                            scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                            offsetXAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                            offsetYAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                        } else {
                            val targetScale = 2.5f
                            val centreX = size.width / 2f
                            val centreY = size.height / 2f
                            scaleAnim.animateTo(targetScale, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                            offsetXAnim.animateTo(((centreX - tapOffset.x) * (targetScale - 1f)).coerceIn(-centreX, centreX), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                            offsetYAnim.animateTo(((centreY - tapOffset.y) * (targetScale - 1f)).coerceIn(-centreY, centreY), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                        }
                    }
                },
            )
        }
        // Pinch-to-zoom + pan
        .pointerInput(entry.sourceUrl) {
            detectTransformGestures { _, pan, zoom, _ ->
                scope.launch {
                    val newScale = (scaleAnim.value * zoom).coerceIn(0.8f, 5f)
                    scaleAnim.snapTo(newScale)
                    val maxX = (size.width  * (newScale - 1f)) / 2f
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

    val imageModifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            scaleX       = scaleAnim.value
            scaleY       = scaleAnim.value
            translationX = offsetXAnim.value
            translationY = offsetYAnim.value
            rotationZ    = animatedRotation
        }

    Box(modifier = gestureModifier, contentAlignment = Alignment.Center) {
        if (entry.isRemote) {
            // ── Remote / network image via Coil ──────────────────────────────
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(entry.sourceUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = entry.label,
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text  = entry.label,
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Failed to load image", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
            )
        } else {
            // ── Local file via BitmapFactory (offline-capable) ────────────────
            val bitmap by produceState<Bitmap?>(initialValue = null, entry.sourceUrl) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val rawUri = Uri.parse(entry.sourceUrl)
                        when (rawUri.scheme) {
                            "content" -> {
                                context.contentResolver.openInputStream(rawUri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)
                                }
                            }
                            "file", null -> {
                                val path = rawUri.path ?: entry.sourceUrl.removePrefix("file://")
                                BitmapFactory.decodeFile(path)
                            }
                            else -> BitmapFactory.decodeFile(rawUri.path)
                        }
                    }.getOrNull()
                }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = entry.label,
                    contentScale = ContentScale.Fit,
                    modifier = imageModifier,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text  = entry.label,
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}


// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ImageViewerTopBar(
    currentIndex: Int,
    total: Int,
    fileName: String,
    isRemote: Boolean = false,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onCopyUrl: () -> Unit = {},
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }

            Spacer(Modifier.width(4.dp))

            // Title + counter
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text      = fileName,
                    color     = Color.White,
                    style     = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                )
                if (total > 1) {
                    Text(
                        text  = "${currentIndex + 1} of $total",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Share
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White)
            }

            // More options
            Box {
                IconButton(onClick = { moreMenuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White)
                }
                DropdownMenu(
                    expanded    = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in external app") },
                        leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                        onClick = { moreMenuExpanded = false; onOpenExternal() },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy URL") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { moreMenuExpanded = false; onCopyUrl() },
                    )
                }
            }
        }
    }
}

// ─── Bottom Bar ──────────────────────────────────────────────────────────────

@Composable
private fun ImageViewerBottomBar(
    entry: PetalViewerImageEntry?,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onWallpaper: () -> Unit,
    onInfo: () -> Unit,
) {
    val isLocal = entry?.downloadItem != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.clip(RoundedCornerShape(50)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Rotate
                ViewerActionButton(
                    icon        = Icons.Rounded.RotateRight,
                    label       = "Rotate",
                    onClick     = onRotate,
                )

                // Delete — only shown for local downloaded files
                if (isLocal) {
                    ViewerActionButton(
                        icon    = Icons.Rounded.Delete,
                        label   = "Delete",
                        tint    = Color(0xFFFF7878),
                        onClick = onDelete,
                    )
                }

                // Set as wallpaper
                ViewerActionButton(
                    icon        = Icons.Rounded.Wallpaper,
                    label       = "Wallpaper",
                    onClick     = onWallpaper,
                )

                // Info
                ViewerActionButton(
                    icon        = Icons.Rounded.Info,
                    label       = "Info",
                    onClick     = onInfo,
                )
            }
        }
    }
}


@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(
            text  = label,
            color = tint.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
    }
}

// ─── Page Indicator Dots ─────────────────────────────────────────────────────

@Composable
private fun PageIndicatorDots(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        repeat(count.coerceAtMost(8)) { index ->
            val isActive = index == current
            val size by animateFloatAsState(
                targetValue  = if (isActive) 8f else 5f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dotSize",
            )
            val alpha by animateFloatAsState(
                targetValue  = if (isActive) 1f else 0.45f,
                animationSpec = tween(180),
                label = "dotAlpha",
            )
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha)),
            )
        }
    }
}

// ─── Image Info Bottom Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageInfoBottomSheet(
    entry: PetalViewerImageEntry,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Resolve file info for local entries only
    val fileInfo by produceState<Triple<String, String, String>?>(initialValue = null, entry.sourceUrl) {
        value = if (entry.isRemote) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val rawUri = Uri.parse(entry.sourceUrl)
                val path: String? = when (rawUri.scheme) {
                    "content" -> null // dimensions not available without decode
                    "file", null -> rawUri.path ?: entry.sourceUrl.removePrefix("file://")
                    else -> rawUri.path
                }
                if (path != null) {
                    val file = File(path)
                    val size = formatBytes(file.length())
                    val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(file.lastModified()))
                    val bmp  = BitmapFactory.decodeFile(path)
                    val dims = if (bmp != null) "${bmp.width} × ${bmp.height} px" else "—"
                    bmp?.recycle()
                    Triple(size, date, dims)
                } else {
                    // content:// URI — get size only
                    val fd = runCatching { context.contentResolver.openFileDescriptor(rawUri, "r") }.getOrNull()
                    val size = if (fd != null) { val s = formatBytes(fd.statSize); fd.close(); s } else "—"
                    Triple(size, "—", "—")
                }
            }.getOrNull()
        }
    }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape             = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle        = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text      = "Image Info",
                style     = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier  = Modifier.padding(bottom = 16.dp),
            )

            InfoRow(label = "Name", value = entry.label)

            if (entry.isRemote) {
                InfoRow(label = "Source URL", value = entry.sourceUrl)
                InfoRow(label = "Type", value = "Remote image")
            } else {
                // Local file details
                if (fileInfo != null) {
                    if (fileInfo!!.third != "—") InfoRow(label = "Dimensions", value = fileInfo!!.third)
                    InfoRow(label = "File size", value = fileInfo!!.first)
                    if (fileInfo!!.second != "—") InfoRow(label = "Date", value = fileInfo!!.second)
                }
                val rawUri = Uri.parse(entry.sourceUrl)
                val path   = rawUri.path ?: entry.sourceUrl.removePrefix("file://")
                if (path.isNotBlank()) InfoRow(label = "Path", value = path)
                // Source URL (original download link) if present via downloadItem
                entry.downloadItem?.fileUrl?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(label = "Source URL", value = it)
                }
            }
        }
    }
}


@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(
            modifier  = Modifier.padding(top = 10.dp),
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

// ─── Wallpaper helper ─────────────────────────────────────────────────────────

private fun setAsWallpaper(context: Context, item: DownloadItem) {
    try {
        val localUri    = item.localUri ?: return
        val rawUri      = Uri.parse(localUri)
        val contentUri: Uri = if (rawUri.scheme == "file" || rawUri.scheme == null) {
            val path = rawUri.path ?: localUri.removePrefix("file://")
            val file = File(path)
            if (!file.exists()) return
            androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
        } else rawUri

        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(contentUri, "image/*")
            putExtra("mimeType", "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Set as wallpaper").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        e.printStackTrace()
        com.petal.browser.view.NinjaToast.show(context, "Unable to set wallpaper")
    }
}
