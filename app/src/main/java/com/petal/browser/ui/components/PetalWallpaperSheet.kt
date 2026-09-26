/*
 * PetalWallpaperSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive wallpaper selection sheet.
 * Features:
 * - Built-in image cropper matching specific home screen scale & aspect ratio.
 * - Device storage photo & video picker.
 * - Universal Stride Slider (PetalSlider) for fine-grain Dim and Frosted Glass Blur.
 * - Live wallpaper frosted glass real-time blur.
 * - Rich catalog of curated static and dynamic live wallpapers.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.activity.ComponentActivity
import android.content.ContextWrapper
import com.petal.browser.compose.file.PetalFilePickerBridge
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.wallpaper.PetalWallpaperManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class WallpaperPreset(
    val id: String,
    val title: String,
    val category: String,
    val thumbUrl: String,
    val fullUrl: String,
    val isVideo: Boolean = false
)

val WALLPAPER_CATEGORIES = listOf(
    "All",
    "Live Video",
    "Nature",
    "Minimal & Dark",
    "Space & Neon",
    "Abstract"
)

val PRESET_WALLPAPERS = listOf(
    // ── Live Video Wallpapers ──
    WallpaperPreset(
        id = "w_live_ocean",
        title = "Ocean Waves",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400",
        fullUrl = "https://videos.pexels.com/video-files/5853147/5853147-hd_2048_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_sunset",
        title = "Sunset Horizon",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=400",
        fullUrl = "https://videos.pexels.com/video-files/11335978/11335978-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_aurora",
        title = "Northern Lights",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1531366936337-7c912a4589a7?w=400",
        fullUrl = "https://videos.pexels.com/video-files/856356/856356-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_clouds",
        title = "Sky Clouds Flow",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1534088568595-a066f410bcda?w=400",
        fullUrl = "https://videos.pexels.com/video-files/854999/854999-hd_1920_1080_25fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_particles",
        title = "Cosmic Fluid",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400",
        fullUrl = "https://videos.pexels.com/video-files/7191147/7191147-hd_1080_1920_25fps.mp4",
        isVideo = true
    ),

    // ── Nature Wallpapers ──
    WallpaperPreset(
        id = "w_ocean",
        title = "Turquoise Tide",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400",
        fullUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1600"
    ),
    WallpaperPreset(
        id = "w_sunset",
        title = "Golden Coast",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=400",
        fullUrl = "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=1600"
    ),
    WallpaperPreset(
        id = "w_forest",
        title = "Misty Forest",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=400",
        fullUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1600"
    ),
    WallpaperPreset(
        id = "w_mountains",
        title = "Alpine Peaks",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=400",
        fullUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1600"
    ),
    WallpaperPreset(
        id = "w_desert",
        title = "Dune Ripples",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=400",
        fullUrl = "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1600"
    ),

    // ── Minimal & Dark Wallpapers ──
    WallpaperPreset(
        id = "w_amoled_black",
        title = "Midnight Silk",
        category = "Minimal & Dark",
        thumbUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400",
        fullUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1600"
    ),
    WallpaperPreset(
        id = "w_monochrome_arch",
        title = "Minimal Shadow",
        category = "Minimal & Dark",
        thumbUrl = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=400",
        fullUrl = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1600"
    ),
    WallpaperPreset(
        id = "w_carbon",
        title = "Dark Geometry",
        category = "Minimal & Dark",
        thumbUrl = "https://images.unsplash.com/photo-1550684847-75bdda21cc95?w=400",
        fullUrl = "https://images.unsplash.com/photo-1550684847-75bdda21cc95?w=1600"
    ),

    // ── Space & Neon Wallpapers ──
    WallpaperPreset(
        id = "w_stars",
        title = "Cosmic Sky",
        category = "Space & Neon",
        thumbUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=400",
        fullUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1600"
    ),
    WallpaperPreset(
        id = "w_nebula",
        title = "Deep Nebula",
        category = "Space & Neon",
        thumbUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400",
        fullUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1600"
    ),
    WallpaperPreset(
        id = "w_cyber_city",
        title = "Neon Tokyo",
        category = "Space & Neon",
        thumbUrl = "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=400",
        fullUrl = "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=1600"
    ),

    // ── Abstract Wallpapers ──
    WallpaperPreset(
        id = "w_abstract",
        title = "Prism Gradient",
        category = "Abstract",
        thumbUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=400",
        fullUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=1600"
    ),
    WallpaperPreset(
        id = "w_liquid_flow",
        title = "Fluid Swirl",
        category = "Abstract",
        thumbUrl = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=400",
        fullUrl = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=1600"
    ),
    WallpaperPreset(
        id = "w_city",
        title = "City Lights",
        category = "Abstract",
        thumbUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=400",
        fullUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=1600"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalWallpaperSheet(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeUri = PetalWallpaperManager.wallpaperUri
    var currentDim by remember { mutableFloatStateOf(PetalWallpaperManager.wallpaperDim) }
    var currentBlur by remember { mutableFloatStateOf(PetalWallpaperManager.wallpaperBlur) }
    var selectedCategory by remember { mutableStateOf("All") }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    fun findActivity(): ComponentActivity? {
        var curr = context
        while (curr is ContextWrapper) {
            if (curr is ComponentActivity) return curr
            curr = curr.baseContext
        }
        return null
    }

    val handleSelectedFile: (File) -> Unit = { file ->
        val ext = file.extension.lowercase()
        val isVideoFile = ext in setOf("mp4", "webm", "mkv", "mov", "3gp", "avi", "ts", "flv", "m4v")
        if (isVideoFile) {
            // Copy video to app local wallpaper cache and apply directly
            scope.launch(Dispatchers.IO) {
                try {
                    val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                    val localFile = File(wallpaperDir, "live_wallpaper_${System.currentTimeMillis()}.$ext")
                    FileInputStream(file).use { input ->
                        FileOutputStream(localFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val localUri = Uri.fromFile(localFile).toString()
                    launch(Dispatchers.Main) {
                        PetalWallpaperManager.setWallpaper(context, localUri, currentDim, currentBlur)
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        PetalWallpaperManager.setWallpaper(context, Uri.fromFile(file).toString(), currentDim, currentBlur)
                    }
                }
            }
        } else {
            // Image file: open the homescreen-scale cropper
            pendingCropUri = Uri.fromFile(file)
        }
    }

    // Built-in device file picker fallback launcher (if activity is not ComponentActivity)
    val systemFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { inputUri ->
            val mime = context.contentResolver.getType(inputUri) ?: ""
            if (mime.contains("video", ignoreCase = true)) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                        val localFile = File(wallpaperDir, "live_wallpaper_${System.currentTimeMillis()}.mp4")
                        context.contentResolver.openInputStream(inputUri)?.use { input ->
                            FileOutputStream(localFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val localUri = Uri.fromFile(localFile).toString()
                        launch(Dispatchers.Main) {
                            PetalWallpaperManager.setWallpaper(context, localUri, currentDim, currentBlur)
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            PetalWallpaperManager.setWallpaper(context, inputUri.toString(), currentDim, currentBlur)
                        }
                    }
                }
            } else {
                pendingCropUri = inputUri
            }
        }
    }

    val openDevicePicker = {
        val act = findActivity()
        if (act != null) {
            PetalFilePickerBridge.showFilePicker(
                activity = act,
                mimeTypes = arrayOf("image/*", "video/*"),
                allowFolderSelection = false,
                allowMultiple = false,
                onFileSelected = handleSelectedFile,
                onBrowseSystemFallback = {
                    systemFallbackLauncher.launch("*/*")
                }
            )
        } else {
            systemFallbackLauncher.launch("*/*")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Home Wallpaper",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize your home background style",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (activeUri != null) {
                    TextButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            PetalWallpaperManager.clearWallpaper(context)
                        }
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons Row: Device Photo Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { openDevicePicker() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Device Storage (Crop & Apply)")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(WALLPAPER_CATEGORIES) { cat ->
                    val isCatSelected = selectedCategory == cat
                    FilterChip(
                        selected = isCatSelected,
                        onClick = {
                            PetalHapticEngine.getInstance(context).playTick(context)
                            selectedCategory = cat
                        },
                        label = { Text(cat, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Presets Horizontal Carousel
            val filteredPresets = remember(selectedCategory) {
                if (selectedCategory == "All") {
                    PRESET_WALLPAPERS
                } else {
                    PRESET_WALLPAPERS.filter { it.category == selectedCategory }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredPresets, key = { it.id }) { preset ->
                    val isSelected = activeUri == preset.fullUrl

                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                PetalWallpaperManager.setWallpaper(context, preset.fullUrl, currentDim, currentBlur)
                            }
                            .border(
                                width = if (isSelected) 2.5.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        AsyncImage(
                            model = preset.thumbUrl,
                            contentDescription = preset.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (preset.isVideo) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Sliders for Dim and Blur using Stride Slider (PetalSlider)
            if (activeUri != null) {
                Text(
                    text = "Dimming Overlay (${(currentDim * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                PetalSlider(
                    value = currentDim,
                    onValueChange = {
                        currentDim = it
                        PetalWallpaperManager.updateDim(context, it)
                    },
                    valueRange = 0f..0.85f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Frosted Glass Blur (${currentBlur.toInt()} dp)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                PetalSlider(
                    value = currentBlur,
                    onValueChange = {
                        currentBlur = it
                        PetalWallpaperManager.updateBlur(context, it)
                    },
                    valueRange = 0f..25f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Built-in Wallpaper Crop Sheet when an image is selected from device storage
    pendingCropUri?.let { uriToCrop ->
        PetalWallpaperCropSheet(
            imageUri = uriToCrop,
            onDismiss = { pendingCropUri = null },
            onWallpaperCropped = { croppedLocalUri ->
                pendingCropUri = null
                PetalWallpaperManager.setWallpaper(context, croppedLocalUri.toString(), currentDim, currentBlur)
            }
        )
    }
}
