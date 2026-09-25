/*
 * PetalWallpaperSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive wallpaper selection sheet (ported from OmniBrowser).
 * Provides online preset galleries, live video wallpapers, custom file picker,
 * and real-time dimming / blur sliders.
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.wallpaper.PetalWallpaperManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class WallpaperPreset(
    val id: String,
    val title: String,
    val thumbUrl: String,
    val fullUrl: String,
    val isVideo: Boolean = false
)

val WALLPAPER_CATEGORIES = listOf(
    "Featured",
    "Live Video",
    "Nature",
    "Space",
    "Abstract",
    "Minimal",
    "Dark",
    "Ocean"
)

val PRESET_WALLPAPERS = listOf(
    WallpaperPreset(
        id = "w_ocean",
        title = "Ocean Waves",
        thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400",
        fullUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1600"
    ),
    WallpaperPreset(
        id = "w_sunset",
        title = "Sunset Coast",
        thumbUrl = "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=400",
        fullUrl = "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=1600"
    ),
    WallpaperPreset(
        id = "w_forest",
        title = "Misty Forest",
        thumbUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=400",
        fullUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1600"
    ),
    WallpaperPreset(
        id = "w_city",
        title = "City Lights",
        thumbUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=400",
        fullUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=1600"
    ),
    WallpaperPreset(
        id = "w_abstract",
        title = "Abstract Gradient",
        thumbUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=400",
        fullUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=1600"
    ),
    WallpaperPreset(
        id = "w_stars",
        title = "Cosmic Sky",
        thumbUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=400",
        fullUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1600"
    ),
    // Live videos from OmniBrowser catalog
    WallpaperPreset(
        id = "w_live_ocean",
        title = "Live Waves",
        thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400",
        fullUrl = "https://videos.pexels.com/video-files/5853147/5853147-hd_2048_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_sunset",
        title = "Live Horizon",
        thumbUrl = "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=400",
        fullUrl = "https://videos.pexels.com/video-files/11335978/11335978-hd_1920_1080_30fps.mp4",
        isVideo = true
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
    var selectedCategory by remember { mutableStateOf("Featured") }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { inputUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    val mime = context.contentResolver.getType(inputUri) ?: ""
                    val ext = when {
                        mime.contains("video") -> "mp4"
                        mime.contains("gif") -> "gif"
                        else -> "jpg"
                    }
                    val localFile = File(context.filesDir, "wallpaper_${System.currentTimeMillis()}.$ext")
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CONFIRM, 0.4f)
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

            // Action Buttons Row: Device Photo Picker + Seeded Gallery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { pickerLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Device Storage")
                }
            }

            Spacer(Modifier.height(18.dp))

            // Presets Header & Category Row
            Text(
                text = "Preset Wallpapers",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(PRESET_WALLPAPERS, key = { it.id }) { preset ->
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

            Spacer(Modifier.height(18.dp))

            // Sliders for Dim and Blur
            if (activeUri != null) {
                Text(
                    text = "Dimming Overlay (${(currentDim * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = currentDim,
                    onValueChange = {
                        currentDim = it
                        PetalWallpaperManager.updateDim(context, it)
                    },
                    valueRange = 0f..0.80f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Frosted Glass Blur (${currentBlur.toInt()} dp)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = currentBlur,
                    onValueChange = {
                        currentBlur = it
                        PetalWallpaperManager.updateBlur(context, it)
                    },
                    valueRange = 0f..20f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
