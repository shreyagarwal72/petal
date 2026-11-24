package com.petal.browser.lens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.petal.browser.R
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import com.petal.browser.compose.mlkit.PetalImageScannerBridge
import com.petal.browser.media.MediaFilterType
import com.petal.browser.media.MediaItem
import com.petal.browser.media.PetalMediaPickerManager
import com.petal.browser.scanner.ScannerHost
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Petal Lens Sheet: allows scanning with camera or selecting images from device gallery
 * to decode barcodes / QR codes directly using Petal's local scanner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalLensBottomSheet(
    autoSnapCamera: Boolean = false,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(PetalMediaPickerManager.hasMediaPermissions(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var showPetalScanner by rememberSaveable { mutableStateOf(false) }

    fun refreshGallery() {
        if (PetalMediaPickerManager.hasMediaPermissions(context)) {
            hasPermission = true
            coroutineScope.launch {
                isLoading = true
                val items = withContext(Dispatchers.IO) {
                    PetalMediaPickerManager.queryMedia(context, MediaFilterType.PHOTOS).distinctBy { it.uri.toString() }
                }
                mediaItems = items
                isLoading = false
            }
        } else {
            hasPermission = false
        }
    }

    LaunchedEffect(Unit) {
        refreshGallery()
    }

    fun openImageWithScanner(uri: Uri) {
        PetalImageScannerBridge.show(context, uri.toString())
        onDismissRequest()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            openImageWithScanner(uri)
        }
    }

    val galleryChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            openImageWithScanner(uri)
        }
    }

    fun launchSafeGalleryPicker() {
        try {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Throwable) {
            try {
                galleryChooserLauncher.launch("image/*")
            } catch (_: Throwable) {
                PetalToast.show(context, "Unable to launch photo picker", Toast.LENGTH_SHORT)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val isGranted = perms.values.any { it }
        if (isGranted) {
            refreshGallery()
        } else {
            PetalToast.show(context, "Media permission is required to browse gallery photos", Toast.LENGTH_SHORT)
        }
    }

    // Auto-trigger snap camera feature if requested from widget or shortcut
    var didTriggerAutoCamera by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoSnapCamera) {
        if (autoSnapCamera && !didTriggerAutoCamera) {
            didTriggerAutoCamera = true
            showPetalScanner = true
        }
    }

    if (showPetalScanner) {
        Dialog(
            onDismissRequest = {
                showPetalScanner = false
                onDismissRequest()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            ScannerHost(
                onDismiss = {
                    showPetalScanner = false
                    (context as? com.petal.browser.activity.MainActivity)?.restoreBrowserInputFocus()
                    onDismissRequest()
                }
            )
        }
    }

    val isLiquidGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheetShape = BottomSheetDefaults.ExpandedShape
    val glassWallpaper = rememberPetalGlassWallpaper()
    val sheetSheen = rememberGlassSheen(isLiquidGlass)
    val sheetDark = MaterialTheme.colorScheme.background.luminance() < .5f

    Surface(
        shape = sheetShape,
        color = if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(
            com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides
                (if (isLiquidGlass) glassWallpaper else com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current)
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (isLiquidGlass) {
                    com.petal.browser.ui.containment.PetalSheetGlassBackdrop(glassWallpaper, Modifier.matchParentSize())
                    Box(
                        Modifier
                            .matchParentSize()
                            .then(
                                if (!glassWallpaper.ready) Modifier.background(
                                    if (sheetDark) Color(0xE61A1C1E) else Color(0xE6F2F4F7), sheetShape
                                ) else Modifier
                            )
                            .liquidGlassChrome(sheetShape, enabled = true, sheenIntensity = sheetSheen)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = ExpressiveButtonShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.CenterFocusWeak,
                                        contentDescription = stringResource(R.string.ui_petal_qr_scanner),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.ui_petal_qr_scanner),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_select_a_photo_or_snap),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action Buttons Row: Snap Photo & System File Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Snap Photo Card
                        com.petal.browser.ui.containment.PetalActionCard(
                            onClick = { showPetalScanner = true },
                            shape = com.petal.browser.ui.containment.PetalContainmentShapes.HeroInner,
                            containerColor = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f).height(84.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ui_snap_photo),
                                        maxLines = 2,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_use_camera),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Gallery File Picker Card
                        com.petal.browser.ui.containment.PetalActionCard(
                            onClick = { launchSafeGalleryPicker() },
                            shape = com.petal.browser.ui.containment.PetalContainmentShapes.HeroInner,
                            containerColor = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f).height(84.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ui_android_system_picker),
                                        maxLines = 2,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_photos_picker),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            try {
                                galleryChooserLauncher.launch("image/*")
                            } catch (_: Exception) {
                                PetalToast.show(context, "Cannot open gallery", Toast.LENGTH_SHORT)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().then(
                            if (isLiquidGlass) Modifier.liquidGlassChrome(RoundedCornerShape(16.dp), enabled = true) else Modifier
                        ),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_gallery_chooser))
                    }

                    // Builtin Photos Gallery Grid
                    if (!hasPermission) {
                        val permShape = RoundedCornerShape(20.dp)
                        Surface(
                            shape = permShape,
                            color = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().then(
                                if (isLiquidGlass) Modifier.liquidGlassChrome(permShape, enabled = true) else Modifier
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ui_photo_gallery_access),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_allow_petal_browser_to_display),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(PetalMediaPickerManager.getRequiredMediaPermissions())
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.ui_grant_photo_access), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PetalContainedLoadingIndicator(size = 40.dp)
                        }
                    } else if (mediaItems.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ui_no_indexed_photos_found_in),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_you_can_browse_your_device),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { launchSafeGalleryPicker() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.ui_browse_photos_files), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.ui_recent_photos),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                        ) {
                            itemsIndexed(mediaItems, key = { index, item -> "${item.uri}_$index" }) { _, item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            openImageWithScanner(item.uri)
                                        }
                                ) {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
