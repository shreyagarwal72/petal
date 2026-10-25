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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

import android.graphics.Bitmap
import coil.request.ImageRequest
import com.petal.browser.ui.containment.liquidGlassChrome

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
    "Abstract",
    "City",
    "Mountains",
    "Ocean",
    "Flowers",
    "Technology"
)

val PRESET_WALLPAPERS = listOf(
    // ── Live Video & Animated Wallpapers (from omni-browser & petal) ──
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
    WallpaperPreset(
        id = "w_live_sunlight",
        title = "Sunlight in Trees",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=400",
        fullUrl = "https://videos.pexels.com/video-files/11265968/11265968-hd_1920_1080_25fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_skyscrapers",
        title = "City Skyscrapers",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=400",
        fullUrl = "https://videos.pexels.com/video-files/12685044/12685044-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_rainy_city",
        title = "Rainy Night City",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=400",
        fullUrl = "https://videos.pexels.com/video-files/855432/855432-hd_1840_1034_25fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_abstract_colors",
        title = "Abstract Colors",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=400",
        fullUrl = "https://videos.pexels.com/video-files/10881637/10881637-hd_1920_1080_25fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_river_plants",
        title = "Plants by River",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=400",
        fullUrl = "https://videos.pexels.com/video-files/1208094/1208094-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_woods_walk",
        title = "Walking in Woods",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=400",
        fullUrl = "https://videos.pexels.com/video-files/8424070/8424070-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_sea_waves",
        title = "Blue Sea Waves",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=400",
        fullUrl = "https://videos.pexels.com/video-files/5668625/5668625-hd_2048_1080_30fps.mp4",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_pinterest_city",
        title = "Pinterest City Clip",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=400",
        fullUrl = "https://v1.pinimg.com/videos/iht/hls/d0/b6/93/d0b69328b1f41ad0271fe4374baa688b.m3u8",
        isVideo = true
    ),
    WallpaperPreset(
        id = "w_live_matrix",
        title = "Matrix Code",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400",
        fullUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExdW9uaXZ4OWYwNGd1bmM1c3Q4ZGFzeXJ4NnM2azE5enptcW5vdDVzdiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/L1R1tvI9svkIWwpVYr/giphy.gif",
        isVideo = false
    ),
    WallpaperPreset(
        id = "w_live_lofi",
        title = "Lo-Fi Cafe",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=400",
        fullUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHpucjNlOWRxeWFvZmVudDFndDFjcHZtZ3d1NHEycDdmcDduYmsyeCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/d480lR5b5h8k9X0M/giphy.gif",
        isVideo = false
    ),
    WallpaperPreset(
        id = "w_live_diy_gifts",
        title = "DIY Gift Ideas",
        category = "Live Video",
        thumbUrl = "https://images.unsplash.com/photo-1513519245088-0e12902e5a38?w=400",
        fullUrl = "android.resource://com.petal.browser/raw/wallpaper_diy_gifts",
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
    WallpaperPreset(
        id = "w_meadow",
        title = "Wild Grassland",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=400",
        fullUrl = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=1600"
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
    WallpaperPreset(
        id = "w_dark_texture",
        title = "Obsidian Mesh",
        category = "Minimal & Dark",
        thumbUrl = "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?w=400",
        fullUrl = "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?w=1600"
    ),
    WallpaperPreset(
        id = "w_dark_geometry",
        title = "Geometric Dark",
        category = "Minimal & Dark",
        thumbUrl = "https://images.unsplash.com/photo-1604871000636-074fa5117945?w=400",
        fullUrl = "https://images.unsplash.com/photo-1604871000636-074fa5117945?w=1600"
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
    WallpaperPreset(
        id = "w_neon_geometry",
        title = "Cyber Glow",
        category = "Space & Neon",
        thumbUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=400",
        fullUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=1600"
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
        id = "w_gradient_aura",
        title = "Pastel Aura",
        category = "Abstract",
        thumbUrl = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=400",
        fullUrl = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1600"
    ),
    WallpaperPreset(
        id = "w_paper_craft",
        title = "Silk Wave",
        category = "Abstract",
        thumbUrl = "https://images.unsplash.com/photo-1558591710-4b4a1ae0f04d?w=400",
        fullUrl = "https://images.unsplash.com/photo-1558591710-4b4a1ae0f04d?w=1600"
    ),

    // ── City Wallpapers ──
    WallpaperPreset(
        id = "w_city_lights",
        title = "Metropolis Pulse",
        category = "City",
        thumbUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=400",
        fullUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=1600"
    ),
    WallpaperPreset(
        id = "w_city_rain",
        title = "Rainy Boulevard",
        category = "City",
        thumbUrl = "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=400",
        fullUrl = "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=1600"
    ),
    WallpaperPreset(
        id = "w_city_skyline",
        title = "Urban Skyline",
        category = "City",
        thumbUrl = "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=400",
        fullUrl = "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=1600"
    ),
    WallpaperPreset(
        id = "w_city_night_traffic",
        title = "Night Traffic Trails",
        category = "City",
        thumbUrl = "https://images.unsplash.com/photo-1506521781263-d8422e82f27a?w=400",
        fullUrl = "https://images.unsplash.com/photo-1506521781263-d8422e82f27a?w=1600"
    ),

    // ── Mountains Wallpapers ──
    WallpaperPreset(
        id = "w_mountain_mist",
        title = "Misty Ridge",
        category = "Mountains",
        thumbUrl = "https://images.unsplash.com/photo-1486870591958-9b9d0d1dda99?w=400",
        fullUrl = "https://images.unsplash.com/photo-1486870591958-9b9d0d1dda99?w=1600"
    ),
    WallpaperPreset(
        id = "w_mountain_snow",
        title = "Summit Sunset",
        category = "Mountains",
        thumbUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=400",
        fullUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1600"
    ),
    WallpaperPreset(
        id = "w_mountain_lake",
        title = "Alpine Reflection",
        category = "Mountains",
        thumbUrl = "https://images.unsplash.com/photo-1483921020237-2ff51e8e4b22?w=400",
        fullUrl = "https://images.unsplash.com/photo-1483921020237-2ff51e8e4b22?w=1600"
    ),
    WallpaperPreset(
        id = "w_mountain_dolomites",
        title = "Dolomite Peaks",
        category = "Mountains",
        thumbUrl = "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=400",
        fullUrl = "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1600"
    ),

    // ── Ocean Wallpapers ──
    WallpaperPreset(
        id = "w_ocean_deep",
        title = "Pacific Waves",
        category = "Ocean",
        thumbUrl = "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=400",
        fullUrl = "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=1600"
    ),
    WallpaperPreset(
        id = "w_ocean_coast",
        title = "Seaside Horizons",
        category = "Ocean",
        thumbUrl = "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=400",
        fullUrl = "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=1600"
    ),
    WallpaperPreset(
        id = "w_ocean_aerial",
        title = "Aerial Coastline",
        category = "Ocean",
        thumbUrl = "https://images.unsplash.com/photo-1509233725247-49e657c54213?w=400",
        fullUrl = "https://images.unsplash.com/photo-1509233725247-49e657c54213?w=1600"
    ),

    // ── Flowers Wallpapers ──
    WallpaperPreset(
        id = "w_flower_bloom",
        title = "Spring Petals",
        category = "Flowers",
        thumbUrl = "https://images.unsplash.com/photo-1490750967868-88aa4486c946?w=400",
        fullUrl = "https://images.unsplash.com/photo-1490750967868-88aa4486c946?w=1600"
    ),
    WallpaperPreset(
        id = "w_flower_field",
        title = "Wild Flora",
        category = "Flowers",
        thumbUrl = "https://images.unsplash.com/photo-1465146344425-f00d5f5c8f07?w=400",
        fullUrl = "https://images.unsplash.com/photo-1465146344425-f00d5f5c8f07?w=1600"
    ),
    WallpaperPreset(
        id = "w_flower_cherry",
        title = "Cherry Blossom",
        category = "Flowers",
        thumbUrl = "https://images.unsplash.com/photo-1522383225653-ed111181a951?w=400",
        fullUrl = "https://images.unsplash.com/photo-1522383225653-ed111181a951?w=1600"
    ),

    // ── Technology Wallpapers ──
    WallpaperPreset(
        id = "w_tech_matrix",
        title = "Binary Flow",
        category = "Technology",
        thumbUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400",
        fullUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=1600"
    ),
    WallpaperPreset(
        id = "w_tech_circuits",
        title = "Circuit Grid",
        category = "Technology",
        thumbUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=400",
        fullUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=1600"
    ),
    WallpaperPreset(
        id = "w_tech_server",
        title = "Data Core",
        category = "Technology",
        thumbUrl = "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=400",
        fullUrl = "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=1600"
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
                        onDismissRequest()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        PetalWallpaperManager.setWallpaper(context, Uri.fromFile(file).toString(), currentDim, currentBlur)
                        onDismissRequest()
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
                                output.flush()
                                runCatching { output.fd.sync() }
                            }
                        }
                        val localUri = Uri.fromFile(localFile).toString()
                        launch(Dispatchers.Main) {
                            PetalWallpaperManager.setWallpaper(context, localUri, currentDim, currentBlur)
                            onDismissRequest()
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            PetalWallpaperManager.setWallpaper(context, inputUri.toString(), currentDim, currentBlur)
                            onDismissRequest()
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
                asModalDialog = true,
                onFileSelected = handleSelectedFile,
                onBrowseSystemFallback = {
                    systemFallbackLauncher.launch("*/*")
                }
            )
        } else {
            systemFallbackLauncher.launch("*/*")
        }
    }

    com.petal.browser.ui.containment.PetalSheet(
        onDismissRequest = onDismissRequest,
    ) {
        val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
        val sheetScrollState = androidx.compose.foundation.rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(sheetScrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_home_wallpaper),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ui_customize_your_home_background_style),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (activeUri != null) {
                    FilledTonalButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            PetalWallpaperManager.clearWallpaper(context)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(12.dp), enabled = true) else Modifier
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_reset), maxLines = 1, softWrap = false)
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
                    modifier = Modifier.weight(1f).then(
                        if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(14.dp), enabled = true) else Modifier
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_device_storage_crop_apply))
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
                        shape = RoundedCornerShape(12.dp),
                        border = if (isGlass) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = isCatSelected),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primaryContainer,
                            containerColor = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(12.dp), enabled = true) else Modifier
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
                        val thumbRequest = remember(preset.thumbUrl) {
                            ImageRequest.Builder(context)
                                .data(preset.thumbUrl)
                                .size(176, 260)
                                .crossfade(200)
                                .allowHardware(true)
                                .bitmapConfig(Bitmap.Config.RGB_565)
                                .build()
                        }
                        AsyncImage(
                            model = thumbRequest,
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
                                        contentDescription = stringResource(R.string.ui_video),
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
                                        contentDescription = stringResource(R.string.ui_selected),
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
                    text = stringResource(R.string.ui_dimming_overlay, (currentDim * 100).toInt()),
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
                    text = stringResource(R.string.ui_frosted_glass_blur_dp, currentBlur.toInt()),
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
                onDismissRequest()
            }
        )
    }
}
