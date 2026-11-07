/*
 * PetalImageViewerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Built-in native image viewer for Petal Browser.
 * Material 3 Expressive & Liquid Glass — offline-first, works on all image sources:
 *   • Local downloaded files (Downloads page preview strip + row items)
 *   • Web/remote URLs (long-press on website images → context menu)
 *   • External share intents (ACTION_SEND and ACTION_VIEW for image MIME types)
 *
 * Features:
 *  - HorizontalPager gallery (swipe left/right between images)
 *  - Pinch-to-zoom (0.8× – 6×), pan when zoomed with boundary clamping
 *  - Double-tap toggle zoom (1× ↔ 2.5×) with spring animation
 *  - Single-tap auto-hide/show controls (top bar + bottom bar)
 *  - Rotate 90° CW (visual only)
 *  - Share, Delete (local only, with undo), Set as Wallpaper
 *  - Material 3 Expressive Image Info bottom sheet (dimensions, size, path/URL, date)
 *  - OCR / MLKit text scanning integration
 *  - Comic / Webtoon Continuous Vertical Scroll Mode (Soul Browser Style)
 *  - Liquid Glass containment with isolated wallpaper capture (zero recursive crashes)
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.downloads

import com.petal.browser.ui.theme.ExpressiveButtonShape
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.petal.browser.R
import com.petal.browser.compose.image.PetalImageCropDialog
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.petalContainerEnter
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.captureGlassWallpaperLive
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
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
                    Box(Modifier.fillMaxSize().petalContainerEnter()) {
                        PetalImageViewerScreen(
                            initialIndex = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
                            entries      = entries,
                            onBackPress  = onBackPress,
                        )
                    }
                }
            }
        }
    }

    // ── 1. Local downloaded images (Downloads page) ────────────────────────────

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
    initialIndex: Int = 0,
    entries: List<PetalViewerImageEntry>,
    onBackPress: () -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) { entries.size }
    val currentEntry = entries.getOrNull(pagerState.currentPage)

    // Emit active file viewing to PetalLiveActionBus
    DisposableEffect(currentEntry) {
        if (currentEntry != null) {
            com.petal.browser.live.PetalLiveActionBus.updateFileViewer(
                fileName = currentEntry.label.ifBlank { "Image" },
                fileType = "IMAGE",
                subtitle = "${pagerState.currentPage + 1} of ${entries.size}"
            )
        }
        onDispose {
            com.petal.browser.live.PetalLiveActionBus.dismissFileViewer()
        }
    }

    // Controls visibility with auto-hide
    var controlsVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val hideJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val scheduleHide: () -> Unit = {
        hideJob.value?.cancel()
        hideJob.value = coroutineScope.launch {
            delay(3500)
            if (isActive) controlsVisible = false
        }
    }

    val toggleControls: () -> Unit = {
        controlsVisible = !controlsVisible
        if (controlsVisible) scheduleHide()
    }

    LaunchedEffect(Unit) { scheduleHide() }

    // Per-page rotation (keyed by sourceUrl)
    val rotationMap = remember { mutableStateMapOf<String, Float>() }
    val rotateCurrentEntry: () -> Unit = {
        val key = currentEntry?.sourceUrl
        if (key != null) {
            rotationMap[key] = ((rotationMap[key] ?: 0f) + 90f) % 360f
        }
    }

    // Info sheet & Crop Dialog
    var showInfoSheet by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }

    // Comic / Webtoon Continuous Vertical Scroll Mode (Soul Browser Style)
    var isComicMode by remember { mutableStateOf(false) }
    val comicListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))

    // Snackbar for delete undo
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val performDeleteCurrent: () -> Unit = {
        val item = currentEntry?.downloadItem
        if (item != null) {
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
    }

    val hostView = LocalView.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val imageWallpaper = rememberPetalGlassWallpaper()

    CompositionLocalProvider(
        LocalPetalGlassSource provides hostView,
        LocalPetalGlassWallpaper provides (if (isGlass) imageWallpaper else null)
    ) {
        val backgroundSnapshot = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }

        val hasSubOverlay = showInfoSheet || showCropDialog
        if (hasSubOverlay) {
            androidx.activity.compose.BackHandler(enabled = true) {
                if (showCropDialog) showCropDialog = false
                else if (showInfoSheet) showInfoSheet = false
            }
        }

        com.petal.browser.predictive.PetalPredictiveBackSurface(
            enabled = !hasSubOverlay,
            onBack = onBackPress
        ) {
            com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot, isolateGlassWallpaper = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // ── Sibling 1: Image Gallery Viewport (Wallpaper Producer) ──
                    // IMPORTANT: captureGlassWallpaperLive is placed ONLY on this viewport sibling,
                    // completely avoiding recursive GraphicsLayer recording cycles with the overlay bars!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isGlass) Modifier.captureGlassWallpaperLive(imageWallpaper) { pagerState.currentPage.toLong() }
                                else Modifier
                            )
                    ) {
                        if (isComicMode) {
                            LazyColumn(
                                state = comicListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(
                                    count = entries.size,
                                    key = { entries[it].sourceUrl }
                                ) { page ->
                                    val entry = entries[page]
                                    val rotDeg = rotationMap[entry.sourceUrl] ?: 0f
                                    ZoomableImagePage(
                                        entry       = entry,
                                        rotationDeg = rotDeg,
                                        onSingleTap = toggleControls,
                                    )
                                }
                            }
                        } else {
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
                                    onSingleTap = toggleControls,
                                )
                            }
                        }
                    }

                    // ── Sibling 2: Top Bar (Wallpaper Consumer - Floats over viewport) ──
                    AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it },
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it },
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
                                com.petal.browser.view.PetalToast.show(context, context.getString(R.string.ui_url_copied))
                            },
                            onScanOcr = {
                                val entry = currentEntry ?: return@ImageViewerTopBar
                                val url = entry.downloadItem?.localUri ?: entry.sourceUrl
                                com.petal.browser.compose.mlkit.PetalImageScannerBridge.show(context, url)
                            }
                        )
                    }

                    // ── Sibling 3: Page Indicator Dots (Gallery mode only) ──
                    if (entries.size > 1 && !isComicMode) {
                        AnimatedVisibility(
                            visible = controlsVisible,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 96.dp),
                            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit  = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        ) {
                            PageIndicatorDots(
                                count   = entries.size,
                                current = pagerState.currentPage,
                            )
                        }
                    }

                    // ── Sibling 4: Bottom Action Bar (Wallpaper Consumer - Floats over viewport) ──
                    AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
                    ) {
                        ImageViewerBottomBar(
                            entry         = currentEntry,
                            isComicMode   = isComicMode,
                            onToggleComic = {
                                isComicMode = !isComicMode
                                PetalHapticEngine.getInstance(context).playClick(context)
                            },
                            onCrop        = {
                                showCropDialog = true
                                PetalHapticEngine.getInstance(context).playClick(context)
                            },
                            onRotate      = { rotateCurrentEntry() },
                            onDelete      = { performDeleteCurrent() },
                            onWallpaper   = {
                                val entry = currentEntry ?: return@ImageViewerBottomBar
                                if (entry.downloadItem != null) {
                                    setAsWallpaper(context, entry.downloadItem)
                                } else {
                                    com.petal.browser.view.PetalToast.show(context, context.getString(R.string.ui_download_image_first_wallpaper))
                                }
                            },
                            onInfo        = { showInfoSheet = true },
                        )
                    }

                    // ── Sibling 5: Snackbar Host ──
                    com.petal.browser.ui.containment.PetalSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 90.dp)
                    )
                }
            }
        }

        // ── Image Crop Dialog ──
        if (showCropDialog && currentEntry != null) {
            PetalImageCropDialog(
                imageUri = currentEntry.downloadItem?.localUri?.let { Uri.parse(it) } ?: runCatching { Uri.parse(currentEntry.sourceUrl) }.getOrNull(),
                imageUrl = currentEntry.sourceUrl,
                onDismissRequest = { showCropDialog = false },
                onImageSaved = { savedFile ->
                    showCropDialog = false
                    com.petal.browser.view.PetalToast.show(context, context.getString(R.string.ui_image_saved))
                }
            )
        }

        // ── Image Info Bottom Sheet ──
        if (showInfoSheet && currentEntry != null) {
            ImageInfoBottomSheet(
                entry     = currentEntry,
                onDismiss = { showInfoSheet = false },
            )
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
        targetValue   = rotationDeg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rotation",
    )

    // Reset zoom and pan when entry changes
    LaunchedEffect(entry.sourceUrl) {
        scaleAnim.snapTo(1f)
        offsetXAnim.snapTo(0f)
        offsetYAnim.snapTo(0f)
    }

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
        // Pinch-to-zoom + pan with panZoomLock = false to smoothly pass swipe events to HorizontalPager when at 1x
        .pointerInput(entry.sourceUrl) {
            detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                scope.launch {
                    val currentScale = scaleAnim.value
                    val newScale = (currentScale * zoom).coerceIn(0.8f, 6f)
                    scaleAnim.snapTo(newScale)
                    val maxX = (size.width  * (newScale - 1f)) / 2f
                    val maxY = (size.height * (newScale - 1f)) / 2f
                    if (newScale > 1.05f) {
                        offsetXAnim.snapTo((offsetXAnim.value + pan.x).coerceIn(-maxX, maxX))
                        offsetYAnim.snapTo((offsetYAnim.value + pan.y).coerceIn(-maxY, maxY))
                    } else {
                        offsetXAnim.snapTo(0f)
                        offsetYAnim.snapTo(0f)
                    }
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
        val imageModel = remember(entry.sourceUrl) {
            val raw = entry.sourceUrl
            if (raw.startsWith("content://") || raw.startsWith("file://")) {
                Uri.parse(raw)
            } else {
                raw
            }
        }

        // Unified high-performance image loading via Coil (handles remote URLs, content://, file://)
        // Automatically manages downsampling, hardware bitmaps (Bitmap.Config.HARDWARE),
        // and memory caching to prevent out-of-memory ANRs on large resolution images.
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageModel)
                .crossfade(true)
                .build(),
            contentDescription = entry.label,
            contentScale = ContentScale.Fit,
            modifier = imageModifier,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.petal.browser.compose.composable.PetalContainedLoadingIndicator(size = 48.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text     = entry.label,
                            color    = Color.White.copy(alpha = 0.5f),
                            style    = MaterialTheme.typography.bodySmall,
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
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = ExpressiveButtonShape,
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White.copy(alpha = 0.7f),
                            size = 64.dp,
                            iconSize = 32.dp,
                        ) {
                            Icon(Icons.Rounded.BrokenImage, contentDescription = null)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ui_failed_to_load_image), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        )
    }
}

// ─── Material 3 Expressive Floating Top Bar ───────────────────────────────────

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
    onScanOcr: () -> Unit = {},
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = LocalContext.current
    val topShape = RoundedCornerShape(32.dp)

    val topBarBg = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
    val buttonBg = if (isGlass) MaterialTheme.colorScheme.surface.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .asPaddingValues().calculateTopPadding().coerceAtLeast(10.dp),
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = topShape,
            color = topBarBg,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, topShape, spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(topShape)
                .then(
                    if (isGlass) Modifier.liquidGlassChrome(topShape, enabled = true, sheenIntensity = 0.8f, pressFx = false)
                    else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Back Pill Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(ExpressiveButtonShape)
                        .background(buttonBg)
                        .clickable {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.ui_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Title and Page Badge Row
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = fileName,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee()
                    )

                    if (total > 1) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = "${currentIndex + 1} / $total",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Quick Actions: OCR + Share + More
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // OCR / Text Scanner
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(ExpressiveButtonShape)
                            .background(buttonBg)
                            .clickable {
                                PetalHapticEngine.getInstance(context).playClick(context)
                                onScanOcr()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DocumentScanner,
                            contentDescription = "Scan Text",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Share
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(ExpressiveButtonShape)
                            .background(buttonBg)
                            .clickable {
                                PetalHapticEngine.getInstance(context).playClick(context)
                                onShare()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.ui_share),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // More Menu
                    val menuRotation by animateFloatAsState(
                        targetValue = if (moreMenuExpanded) 180f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "img_menu_rot"
                    )
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(ExpressiveButtonShape)
                                .background(buttonBg)
                                .graphicsLayer { rotationZ = menuRotation }
                                .clickable {
                                    PetalHapticEngine.getInstance(context).playClick(context)
                                    moreMenuExpanded = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.ui_more),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        com.petal.browser.ui.containment.PetalPopupMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false },
                        ) {
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = "Extract Text (OCR)",
                                leadingIcon = { Icon(Icons.Rounded.DocumentScanner, null) },
                                onClick = { moreMenuExpanded = false; onScanOcr() },
                            )
                            com.petal.browser.ui.containment.PetalMenuDivider()
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = stringResource(R.string.ui_open_in_external_app),
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                                onClick = { moreMenuExpanded = false; onOpenExternal() },
                            )
                            com.petal.browser.ui.containment.PetalMenuDivider()
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = stringResource(R.string.ui_copy_url),
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = { moreMenuExpanded = false; onCopyUrl() },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Material 3 Expressive Floating Bottom Action Bar ─────────────────────────

@Composable
private fun ImageViewerBottomBar(
    entry: PetalViewerImageEntry?,
    isComicMode: Boolean,
    onToggleComic: () -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onWallpaper: () -> Unit,
    onInfo: () -> Unit,
) {
    val isLocal = entry?.downloadItem != null
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val bottomShape = RoundedCornerShape(36.dp)
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding().coerceAtLeast(16.dp)

    val bottomBarBg = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomClearance + 6.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = bottomShape,
            color = bottomBarBg,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .shadow(8.dp, bottomShape, spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(bottomShape)
                .then(
                    if (isGlass) Modifier.liquidGlassChrome(bottomShape, enabled = true, sheenIntensity = 0.8f, pressFx = false)
                    else Modifier
                ),
        ) {
            // Horizontal scroll ensures zero clipping or overflowing on any screen width or orientation
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Comic Mode Toggle (Soul Browser Style)
                ExpressivePillActionChip(
                    icon = if (isComicMode) Icons.Rounded.ViewCarousel else Icons.Rounded.AutoStories,
                    label = if (isComicMode) "Gallery" else "Comic",
                    isActive = isComicMode,
                    onClick = onToggleComic,
                )

                // 2. Crop & Edit
                ExpressivePillActionChip(
                    icon = Icons.Rounded.Crop,
                    label = "Crop",
                    onClick = onCrop,
                )

                // 3. Rotate 90°
                ExpressivePillActionChip(
                    icon = Icons.Rounded.RotateRight,
                    label = stringResource(R.string.ui_rotate),
                    onClick = onRotate,
                )

                // 4. Wallpaper
                ExpressivePillActionChip(
                    icon = Icons.Rounded.Wallpaper,
                    label = stringResource(R.string.ui_wallpaper),
                    onClick = onWallpaper,
                )

                // 5. Details / Info
                ExpressivePillActionChip(
                    icon = Icons.Rounded.Info,
                    label = stringResource(R.string.ui_info),
                    onClick = onInfo,
                )

                // 6. Delete (Local downloaded file only)
                if (isLocal) {
                    ExpressivePillActionChip(
                        icon = Icons.Rounded.Delete,
                        label = stringResource(R.string.ui_delete),
                        isDestructive = true,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressivePillActionChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(22.dp)

    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    }

    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(containerColor)
            .clickable {
                PetalHapticEngine.getInstance(context).playClick(context)
                onClick()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            )
        }
    }
}

// ─── Page Indicator Dots ─────────────────────────────────────────────────────

@Composable
private fun PageIndicatorDots(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        repeat(count.coerceAtMost(8)) { index ->
            val isActive = index == current
            val size by animateFloatAsState(
                targetValue   = if (isActive) 8f else 5f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dotSize",
            )
            val alpha by animateFloatAsState(
                targetValue   = if (isActive) 1f else 0.45f,
                animationSpec = tween(180),
                label = "dotAlpha",
            )
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(ExpressiveButtonShape)
                    .background(Color.White.copy(alpha = alpha)),
            )
        }
    }
}

// ─── Material 3 Expressive Image Info Bottom Sheet ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageInfoBottomSheet(
    entry: PetalViewerImageEntry,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    val fileInfo by produceState<Triple<String, String, String>?>(initialValue = null, entry.sourceUrl) {
        value = if (entry.isRemote) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val rawUri = Uri.parse(entry.sourceUrl)
                val path: String? = when (rawUri.scheme) {
                    "content" -> null
                    "file", null -> rawUri.path ?: entry.sourceUrl.removePrefix("file://")
                    else -> rawUri.path
                }
                if (path != null) {
                    val file = File(path)
                    val size = formatBytes(file.length())
                    val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(file.lastModified()))
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, opts)
                    val dims = if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth} × ${opts.outHeight} px" else "—"
                    Triple(size, date, dims)
                } else {
                    val fd = runCatching { context.contentResolver.openFileDescriptor(rawUri, "r") }.getOrNull()
                    val size = if (fd != null) { val s = formatBytes(fd.statSize); fd.close(); s } else "—"
                    val dims = runCatching {
                        context.contentResolver.openInputStream(rawUri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(stream, null, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth} × ${opts.outHeight} px" else null
                        }
                    }.getOrNull() ?: "—"
                    Triple(size, "—", dims)
                }
            }.getOrNull()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.then(
            if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), enabled = true)
            else Modifier
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.ui_image_info),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            InfoCardRow(label = stringResource(R.string.ui_name), value = entry.label)

            if (entry.isRemote) {
                InfoCardRow(label = stringResource(R.string.ui_type), value = "Remote Network Image")
                InfoCardRow(label = stringResource(R.string.ui_source_url), value = entry.sourceUrl)
            } else {
                if (fileInfo != null) {
                    if (fileInfo!!.third != "—") InfoCardRow(label = stringResource(R.string.ui_dimensions), value = fileInfo!!.third)
                    InfoCardRow(label = stringResource(R.string.ui_file_size), value = fileInfo!!.first)
                    if (fileInfo!!.second != "—") InfoCardRow(label = stringResource(R.string.ui_date), value = fileInfo!!.second)
                }
                val rawUri = Uri.parse(entry.sourceUrl)
                val path   = rawUri.path ?: entry.sourceUrl.removePrefix("file://")
                if (path.isNotBlank()) InfoCardRow(label = stringResource(R.string.ui_path), value = path)
                entry.downloadItem?.fileUrl?.takeIf { it.isNotBlank() }?.let {
                    InfoCardRow(label = stringResource(R.string.ui_source_url), value = it)
                }
            }
        }
    }
}

@Composable
private fun InfoCardRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Wallpaper helper ─────────────────────────────────────────────────────────

private fun setAsWallpaper(context: Context, item: DownloadItem) {
    try {
        val localUri = item.localUri ?: return
        val rawUri   = Uri.parse(localUri)
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
        com.petal.browser.view.PetalToast.show(context, context.getString(R.string.ui_unable_to_set_wallpaper))
    }
}
