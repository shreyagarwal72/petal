package com.petal.browser.media

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalPopupMenu
import com.petal.browser.ui.containment.PetalPopupMenuItem
import com.petal.browser.view.PetalToast

// ---------------------------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------------------------

private enum class KindFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }

private enum class MediaSort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    LARGEST("Largest first"),
    NAME("Name (A-Z)"),
}

private sealed interface GridEntry {
    val key: String

    object Actions : GridEntry { override val key: String = "__actions__" }
    data class Header(val label: String, val day: Long) : GridEntry { override val key: String get() = "h$day" }
    data class Item(val asset: PetalMediaAsset) : GridEntry { override val key: String get() = "m${asset.uri}" }
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

/**
 * Petal's built-in full-screen photo & video picker.
 *
 * Reads the device library directly through MediaStore (full access), with albums, filters, sorting,
 * date sections, multi-select with numbered order, camera capture and a one-tap switch to the
 * Petal file picker. It is a first-class page (not an overlay) hosted the same way as the file picker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetalMediaPickerScreen(
    spec: PetalUploadSpec,
    onDismissRequest: () -> Unit,
    onSelected: (List<Uri>) -> Unit,
    onBrowseFiles: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptics = remember { PetalHapticEngine.getInstance(context) }
    val multi = spec.allowMultiple

    // --- Access / permissions -------------------------------------------------------------
    var access by remember { mutableStateOf(PetalMediaLibrary.accessLevel(context, spec.allowsImages, spec.allowsVideos)) }
    var reloadTick by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = PetalMediaLibrary.accessLevel(context, spec.allowsImages, spec.allowsVideos)
        reloadTick++
    }

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (first) { first = false; return@LifecycleEventObserver }
                val now = PetalMediaLibrary.accessLevel(context, spec.allowsImages, spec.allowsVideos)
                if (now != access) access = now
                reloadTick++
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    fun requestAccess() {
        haptics.playClick(context)
        permissionLauncher.launch(PetalMediaLibrary.permissionsToRequest(spec.allowsImages, spec.allowsVideos))
    }

    // --- Library --------------------------------------------------------------------------
    var assets by remember { mutableStateOf<List<PetalMediaAsset>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(access, reloadTick) {
        if (access == PetalMediaAccess.NONE) {
            assets = emptyList()
            loading = false
        } else {
            if (assets.isEmpty()) loading = true
            assets = PetalMediaLibrary.load(context, spec)
            loading = false
        }
    }

    // --- View state -----------------------------------------------------------------------
    var kindFilter by remember { mutableStateOf(KindFilter.ALL) }
    var albumId by remember { mutableStateOf<Long?>(null) }
    var sort by remember { mutableStateOf(MediaSort.NEWEST) }
    var columns by remember { mutableIntStateOf(3) }
    var showSortMenu by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<PetalMediaAsset>() }

    val albums = remember(assets) { PetalMediaLibrary.albumsOf(assets) }
    // If the selected album vanished (deleted photos, reload) fall back to "all".
    LaunchedEffect(albums) { if (albumId != null && albums.none { it.id == albumId }) albumId = null }

    val visible = remember(assets, kindFilter, albumId, sort) {
        val filtered = assets.filter { a ->
            (albumId == null || a.bucketId == albumId) &&
                when (kindFilter) {
                    KindFilter.ALL -> true
                    KindFilter.PHOTOS -> !a.isVideo
                    KindFilter.VIDEOS -> a.isVideo
                }
        }
        when (sort) {
            MediaSort.NEWEST -> filtered.sortedByDescending { it.takenMs }
            MediaSort.OLDEST -> filtered.sortedBy { it.takenMs }
            MediaSort.LARGEST -> filtered.sortedByDescending { it.size }
            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    val cameraAllowed = spec.allowsImages || spec.allowsVideos
    val entries = remember(visible, sort, cameraAllowed) {
        buildList<GridEntry> {
            if (cameraAllowed) add(GridEntry.Actions)
            if (sort == MediaSort.NEWEST || sort == MediaSort.OLDEST) {
                var lastDay = Long.MIN_VALUE
                visible.forEach { a ->
                    val day = PetalMediaLibrary.dayKey(a.takenMs)
                    if (day != lastDay) {
                        lastDay = day
                        add(GridEntry.Header(PetalMediaLibrary.dayLabel(a.takenMs), day))
                    }
                    add(GridEntry.Item(a))
                }
            } else {
                visible.forEach { add(GridEntry.Item(it)) }
            }
        }
    }

    fun deliver(extra: Uri? = null) {
        val uris = selected.map { it.uri } + listOfNotNull(extra)
        if (uris.isNotEmpty()) onSelected(uris)
    }

    fun toggle(asset: PetalMediaAsset) {
        haptics.playClick(context)
        if (selected.contains(asset)) selected.remove(asset) else selected.add(asset)
    }

    // --- Camera ---------------------------------------------------------------------------
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    var afterCameraPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val uri = pendingCapture
        pendingCapture = null
        if (ok && uri != null) deliver(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterCameraPermission
        afterCameraPermission = null
        if (granted) action?.invoke() else PetalToast.show(context, "Camera permission is needed to capture")
    }

    // Petal's own camera handles photos; video still uses the system recorder.
    var showCamera by remember { mutableStateOf(spec.captureRequested && spec.allowsImages && !spec.allowsVideos) }

    fun launchCamera(video: Boolean) {
        haptics.playClick(context)
        if (!video) { showCamera = true; return }
        val start = {
            val uri = PetalMediaPickerManager.createTempCaptureUri(context, video)
            if (uri == null) {
                PetalToast.show(context, "Camera unavailable")
            } else {
                pendingCapture = uri
                try {
                    videoLauncher.launch(uri)
                } catch (_: Exception) {
                    pendingCapture = null
                    PetalToast.show(context, "No camera app available")
                }
            }
        }
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) start() else {
            afterCameraPermission = start
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // --- UI -------------------------------------------------------------------------------
    PetalPredictiveBackSurface(enabled = true, onBack = onDismissRequest) {
        PetalScreenWrapper {
          Box(Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                if (access == PetalMediaAccess.NONE) {
                    MediaPermissionState(
                        onGrant = ::requestAccess,
                        onBrowseFiles = onBrowseFiles,
                        onDismiss = onDismissRequest,
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            val albumName = albums.firstOrNull { it.id == albumId }?.name ?: "All albums"
                            ExpressiveHeader(
                                title = when {
                                    spec.allowsImages && spec.allowsVideos -> "Photos & videos"
                                    spec.allowsVideos -> "Videos"
                                    else -> "Photos"
                                },
                                subtitle = "${visible.size} items · $albumName",
                                onBack = { haptics.playClick(context); onDismissRequest() },
                                actions = {
                                    IconButton(onClick = {
                                        haptics.playClick(context)
                                        columns = if (columns == 3) 4 else 3
                                    }) {
                                        Icon(
                                            imageVector = if (columns == 3) Icons.Rounded.Apps else Icons.Rounded.GridView,
                                            contentDescription = "Change grid size",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { haptics.playClick(context); showSortMenu = true }) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.Sort,
                                                contentDescription = "Sort",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        PetalPopupMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                            MediaSort.entries.forEach { option ->
                                                PetalPopupMenuItem(
                                                    text = { Text(option.label) },
                                                    leadingIcon = { if (sort == option) Icon(Icons.Rounded.Check, null) },
                                                    onClick = { sort = option; showSortMenu = false },
                                                )
                                            }
                                        }
                                    }
                                    if (onBrowseFiles != null) {
                                        IconButton(onClick = { haptics.playClick(context); onBrowseFiles() }) {
                                            Icon(
                                                Icons.Rounded.FolderOpen,
                                                contentDescription = "Browse files",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                            )

                            // Kind + album filters
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                if (spec.allowsImages && spec.allowsVideos) {
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        KindFilter.entries.forEach { k ->
                                            FilterChip(
                                                selected = kindFilter == k,
                                                onClick = { haptics.playClick(context); kindFilter = k },
                                                label = { Text(k.label) },
                                                leadingIcon = {
                                                    Icon(
                                                        when (k) {
                                                            KindFilter.ALL -> Icons.Rounded.PhotoLibrary
                                                            KindFilter.PHOTOS -> Icons.Rounded.Image
                                                            KindFilter.VIDEOS -> Icons.Rounded.Videocam
                                                        },
                                                        null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                                if (albums.size > 1) {
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilterChip(
                                            selected = albumId == null,
                                            onClick = { haptics.playClick(context); albumId = null },
                                            label = { Text("All albums") },
                                            shape = RoundedCornerShape(14.dp),
                                        )
                                        albums.take(40).forEach { album ->
                                            FilterChip(
                                                selected = albumId == album.id,
                                                onClick = { haptics.playClick(context); albumId = album.id },
                                                label = { Text("${album.name} · ${album.count}") },
                                                shape = RoundedCornerShape(14.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            if (access == PetalMediaAccess.PARTIAL) {
                                PetalGroup(rowCount = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { _, position ->
                                    PetalGroupListRow(
                                        position = position,
                                        onClick = ::requestAccess,
                                        leading = {
                                            PetalGroupIconBadge(
                                                Icons.Rounded.Info,
                                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        },
                                        content = {
                                            Text(
                                                "Limited access",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                "Petal only sees the photos you shared. Tap to allow full access.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        trailing = {
                                            Icon(Icons.Rounded.LockOpen, null, tint = MaterialTheme.colorScheme.primary)
                                        },
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Grid
                            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when {
                                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                                    visible.isEmpty() -> EmptyLibraryState(cameraAllowed, onCamera = { launchCamera(false) })
                                    else -> LazyVerticalGrid(
                                        columns = GridCells.Fixed(columns),
                                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = navBottom + 104.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(
                                            items = entries,
                                            key = { it.key },
                                            span = { entry ->
                                                if (entry is GridEntry.Item) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                                            },
                                        ) { entry ->
                                            when (entry) {
                                                is GridEntry.Actions -> CaptureActions(
                                                    allowPhoto = spec.allowsImages,
                                                    allowVideo = spec.allowsVideos,
                                                    onPhoto = { launchCamera(false) },
                                                    onVideo = { launchCamera(true) },
                                                )
                                                is GridEntry.Header -> Text(
                                                    text = entry.label,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp),
                                                )
                                                is GridEntry.Item -> {
                                                    val index = selected.indexOf(entry.asset)
                                                    MediaCell(
                                                        asset = entry.asset,
                                                        thumbPx = if (columns == 3) 360 else 280,
                                                        showSelector = multi,
                                                        selectionNumber = index + 1,
                                                        onClick = {
                                                            if (multi) toggle(entry.asset) else {
                                                                haptics.playClick(context)
                                                                onSelected(listOf(entry.asset.uri))
                                                            }
                                                        },
                                                        onLongClick = if (multi) ({ toggle(entry.asset) }) else null,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating selection bar (multi-select)
                        AnimatedVisibility(
                            visible = multi && selected.isNotEmpty(),
                            enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            SelectionBar(
                                count = selected.size,
                                totalBytes = selected.sumOf { it.size },
                                onClear = { haptics.playClick(context); selected.clear() },
                                onConfirm = { haptics.playClick(context); deliver() },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showCamera,
                enter = fadeIn() + scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), initialScale = 0.92f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
                modifier = Modifier.fillMaxSize(),
            ) {
                PetalCameraScreen(
                    onCaptured = { uri -> showCamera = false; deliver(uri) },
                    onDismiss = { showCamera = false },
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
private fun CaptureActions(allowPhoto: Boolean, allowVideo: Boolean, onPhoto: () -> Unit, onVideo: () -> Unit) {
    val count = (if (allowPhoto) 1 else 0) + (if (allowVideo) 1 else 0)
    if (count == 0) return
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        var i = 0
        fun shapeFor(index: Int): RoundedCornerShape = when {
            count == 1 -> RoundedCornerShape(28.dp)
            index == 0 -> RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 6.dp, bottomEnd = 6.dp)
            else -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 28.dp, bottomEnd = 28.dp)
        }
        if (allowPhoto) {
            CaptureCard(Icons.Rounded.CameraAlt, "Take photo", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, shapeFor(i++), onPhoto, Modifier.weight(1f))
        }
        if (allowVideo) {
            CaptureCard(Icons.Rounded.Videocam, "Record video", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, shapeFor(i++), onVideo, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CaptureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    container: Color,
    content: Color,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val scale = com.petal.browser.ui.containment.rememberPetalGroupPressScale(source)
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier.scale(scale),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    asset: PetalMediaAsset,
    thumbPx: Int,
    showSelector: Boolean,
    selectionNumber: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val context = LocalContext.current
    val isSelected = selectionNumber > 0
    val scale by animateFloatAsState(
        if (isSelected) 0.86f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "mediaCellScale",
    )
    val radius by animateDpAsState(
        if (isSelected) 20.dp else 12.dp,
        spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "mediaCellRadius",
    )
    val shape = RoundedCornerShape(radius)

    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, asset.uri, thumbPx) {
        value = PetalMediaThumbs.load(context, asset, thumbPx)?.asImageBitmap()
    }
    val imageAlpha by animateFloatAsState(if (bitmap != null) 1f else 0f, label = "mediaCellFade")

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (bitmap == null) {
            Icon(
                imageVector = if (asset.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = asset.name,
                contentScale = ContentScale.Crop,
                alpha = imageAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (asset.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text(
                    PetalMediaLibrary.formatDuration(asset.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        if (showSelector) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
                    .border(
                        if (isSelected) 0.dp else 2.dp,
                        Color.White.copy(alpha = 0.9f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Text(
                        text = selectionNumber.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, totalBytes: Long, onClear: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "$count selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    Formatter.formatShortFileSize(context, totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onConfirm, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (count == 1) "Add 1" else "Add $count")
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(canCapture: Boolean, onCamera: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "No photos or videos match this filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canCapture) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onCamera, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Take a photo")
            }
        }
    }
}

@Composable
private fun MediaPermissionState(onGrant: () -> Unit, onBrowseFiles: (() -> Unit)?, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(80.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Photos & videos",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow Petal to read your photo and video library so you can pick what to upload. Nothing is sent anywhere until you choose it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(24.dp))

        val rows = if (onBrowseFiles != null) 2 else 1
        PetalGroup(rowCount = rows, modifier = Modifier.fillMaxWidth()) { index, position ->
            if (index == 0) {
                PetalGroupListRow(
                    position = position,
                    onClick = onGrant,
                    leading = { PetalGroupIconBadge(Icons.Rounded.LockOpen) },
                    content = {
                        Text("Allow access", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Browse your full gallery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            } else {
                PetalGroupListRow(
                    position = position,
                    onClick = { onBrowseFiles?.invoke() },
                    leading = {
                        PetalGroupIconBadge(
                            Icons.Rounded.FolderOpen,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                    content = {
                        Text("Browse files instead", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Use the Petal file picker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDismiss, shape = RoundedCornerShape(20.dp)) { Text("Cancel") }
    }
}
