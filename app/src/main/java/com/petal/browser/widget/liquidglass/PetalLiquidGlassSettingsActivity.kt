package com.petal.browser.widget.liquidglass

import android.app.WallpaperManager
import com.petal.browser.ui.theme.PetalText
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.petal.browser.ui.components.ExpressiveButtonGroup
import com.petal.browser.ui.components.ExpressiveSegmentItem
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.rememberPetalGroupShape
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.FilterChip
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Petal Liquid Glass widget settings.
 *
 * Built only from Petal's own containment kit, so it follows the app's UI choice by itself:
 * Liquid Glass UI on -> every card, row and button is live glass; off -> Material 3 Expressive.
 * Sliders are [PetalSlider] (the user's chosen slider style). The preview is the REAL glass renderer
 * running on your wallpaper, not a mock-up.
 */
class PetalLiquidGlassSettingsActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // OK from the start: with CANCELED the launcher deletes the widget if the user backs out.
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            PetalLiquidGlassWidgetBinder.updateAll(this)
        } else {
            setResult(RESULT_CANCELED)
        }

        val isPillWidget = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            PetalLiquidGlassWidgetBinder.isPill(this, appWidgetId)

        setContent {
            PetalExpressiveTheme {
                LiquidGlassSettingsScreen(
                    initialConfig = PetalBlurConfig.load(this),
                    configuringWidgetId = appWidgetId,
                    initialIsPill = isPillWidget,
                    onSave = { updated ->
                        PetalBlurConfig.save(this, updated)
                        PetalWallpaperBlurHelper.rebakeAllFromWallpaper(this)
                        finish()
                    },
                    onClose = { finish() },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidGlassSettingsScreen(
    initialConfig: PetalBlurConfig,
    configuringWidgetId: Int,
    initialIsPill: Boolean,
    onSave: (PetalBlurConfig) -> Unit,
    onClose: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val defaults = remember { PetalBlurConfig() }

    var blurRadius by remember { mutableFloatStateOf(initialConfig.blurRadius.coerceIn(0f, 50f)) }
    var refractionHeight by remember { mutableFloatStateOf(initialConfig.refractionHeight.coerceIn(12f, 50f)) }
    var refractionOffset by remember { mutableFloatStateOf(initialConfig.refractionOffset.coerceIn(20f, 120f)) }
    var cornerRadius by remember { mutableFloatStateOf(initialConfig.cornerRadius.coerceIn(12f, 50f)) }
    var edgeAlpha by remember { mutableIntStateOf(initialConfig.edgeAlpha.coerceIn(0, 100)) }
    var darkLightMode by remember { mutableIntStateOf(initialConfig.darkLightMode) }
    var darkLightIntensity by remember { mutableIntStateOf(initialConfig.darkLightIntensity.coerceIn(10, 100)) }
    var wallpaperRevision by remember { mutableIntStateOf(0) }
    var previewIsPill by remember { mutableStateOf(initialIsPill) }

    var a11yOn by remember { mutableStateOf(PetalGlassAccessibilityService.isRunning) }
    var liveWallpaperActive by remember { mutableStateOf(PetalWallpaperBlurHelper.isWallpaperOwnsGlass(context)) }
    var hasCustomPhoto by remember { mutableStateOf(PetalWallpaperBlurHelper.hasCustomWallpaperPhoto(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = PetalGlassAccessibilityService.isRunning
                liveWallpaperActive = PetalWallpaperBlurHelper.isWallpaperOwnsGlass(context)
                hasCustomPhoto = PetalWallpaperBlurHelper.hasCustomWallpaperPhoto(context)
                wallpaperRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val draft = initialConfig.copy(
        blurRadius = blurRadius,
        refractionHeight = refractionHeight,
        refractionOffset = refractionOffset,
        cornerRadius = cornerRadius,
        edgeAlpha = edgeAlpha,
        darkLightMode = darkLightMode,
        darkLightIntensity = darkLightIntensity
    )

    val pickWallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val target = PetalWallpaperBlurHelper.getCustomWallpaperFile(context)
                        val tmp = File(target.parentFile, "${target.name}.tmp")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tmp).use { input.copyTo(it) }
                        } ?: return@withContext false
                        tmp.renameTo(target)
                        true
                    } catch (_: Throwable) {
                        false
                    }
                }
                if (ok) {
                    PetalWallpaperBlurHelper.invalidateCaptures(context)
                    PetalWallpaperBlurHelper.rebakeAllFromWallpaper(context)
                    hasCustomPhoto = PetalWallpaperBlurHelper.hasCustomWallpaperPhoto(context)
                    wallpaperRevision++
                    Toast.makeText(context, "Wallpaper photo saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed saving wallpaper photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun requestPinGlassWidget(receiver: Class<*>) {
        try {
            val mgr = AppWidgetManager.getInstance(context)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(context, receiver), null, null)
            } else {
                Toast.makeText(context, "Launcher does not support pinning widgets", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(context, "Could not pin widget", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Liquid Glass widgets", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        blurRadius = defaults.blurRadius
                        refractionHeight = defaults.refractionHeight
                        refractionOffset = defaults.refractionOffset
                        cornerRadius = defaults.cornerRadius
                        edgeAlpha = defaults.edgeAlpha
                        darkLightMode = defaults.darkLightMode
                        darkLightIntensity = defaults.darkLightIntensity
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset to defaults")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (configuringWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        FilledTonalButton(
                            onClick = {
                                requestPinGlassWidget(
                                    if (previewIsPill) PetalLiquidGlassPillWidgetReceiver::class.java
                                    else PetalLiquidGlassSearchWidgetReceiver::class.java
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (previewIsPill) "Add Pill" else "Add Bar",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply to widgets", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Live preview (real renderer) ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GlassPreview(draft, wallpaperRevision, previewIsPill)
                if (configuringWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !previewIsPill,
                            onClick = { previewIsPill = false },
                            label = { Text("Search bar") }
                        )
                        FilterChip(
                            selected = previewIsPill,
                            onClick = { previewIsPill = true },
                            label = { Text("Compact pill") }
                        )
                    }
                }
            }

            // ── Setup ──
            PetalSettingsSection(title = "Setup") {
                val rows = if (hasCustomPhoto) 5 else 4
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    var rowIdx = 0
                    PetalGroupRow(
                        icon = Icons.Rounded.Accessibility,
                        title = "Position detection",
                        subtitle = if (a11yOn) "On. Petal photographs the real wallpaper behind each widget"
                        else "Off. Turn on Petal's accessibility service (then switch it off and on once after an update)",
                        position = petalGroupPositionFor(rowIdx++, rows),
                        onClick = onOpenAccessibility
                    )
                    PetalGroupRow(
                        icon = Icons.Rounded.Refresh,
                        title = "Refresh glass",
                        subtitle = "Retake the background of every widget, then open your home screen",
                        position = petalGroupPositionFor(rowIdx++, rows),
                        onClick = {
                            PetalWallpaperBlurHelper.invalidateCaptures(context)
                            PetalWallpaperBlurHelper.rebakeAllFromWallpaper(context)
                            Toast.makeText(context, "Refreshing glass. Go to your home screen", Toast.LENGTH_SHORT).show()
                        },
                        trailing = {}
                    )
                    PetalGroupRow(
                        icon = Icons.Rounded.Image,
                        title = "Pick wallpaper photo",
                        subtitle = if (hasCustomPhoto) "Custom photo active. Tap to choose another image"
                        else "Fallback if screenshots are unavailable on your device",
                        position = petalGroupPositionFor(rowIdx++, rows),
                        onClick = {
                            pickWallpaperLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                    if (hasCustomPhoto) {
                        PetalGroupRow(
                            icon = Icons.Rounded.DeleteOutline,
                            title = "Clear custom photo",
                            subtitle = "Revert back to system wallpaper and screenshot capture",
                            position = petalGroupPositionFor(rowIdx++, rows),
                            onClick = {
                                PetalWallpaperBlurHelper.clearCustomWallpaperPhoto(context)
                                PetalWallpaperBlurHelper.rebakeAllFromWallpaper(context)
                                hasCustomPhoto = false
                                wallpaperRevision++
                                Toast.makeText(context, "Custom wallpaper photo cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    PetalGroupRow(
                        icon = Icons.Rounded.Wallpaper,
                        title = "Petal live wallpaper",
                        subtitle = if (liveWallpaperActive) "Active (60/120 Hz real-time glass)"
                        else "Real-time glass at 60/120 Hz",
                        position = petalGroupPositionFor(rowIdx, rows),
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, PetalGlassWallpaperService::class.java)
                                    )
                                )
                            } catch (_: Throwable) {
                                Toast.makeText(context, "Could not open the live wallpaper chooser", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // ── Glass optics ──
            PetalSettingsSection(title = "Glass optics") {
                val rows = 5
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    GlassSliderRow("Bevel height", "${refractionHeight.toInt()} dp", refractionHeight, 12f..50f,
                        petalGroupPositionFor(0, rows)) { refractionHeight = it }
                    GlassSliderRow("Refraction strength", "${refractionOffset.toInt()} dp", refractionOffset, 20f..120f,
                        petalGroupPositionFor(1, rows)) { refractionOffset = it }
                    GlassSliderRow("Frosted blur", "${"%.1f".format(blurRadius)} px", blurRadius, 0f..50f,
                        petalGroupPositionFor(2, rows)) { blurRadius = it }
                    GlassSliderRow("Corner radius", "${cornerRadius.toInt()} dp", cornerRadius, 12f..50f,
                        petalGroupPositionFor(3, rows)) { cornerRadius = it }
                    GlassSliderRow("Rim highlight", "$edgeAlpha%", edgeAlpha.toFloat(), 0f..100f,
                        petalGroupPositionFor(4, rows)) { edgeAlpha = it.toInt() }
                }
            }

            // ── Tone ──
            PetalSettingsSection(title = "Tint and tone") {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    GlassContainer(petalGroupPositionFor(0, if (darkLightMode != PetalBlurConfig.DARK_LIGHT_OFF) 2 else 1)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Glass tint", style = PetalText.rowTitle)
                            ExpressiveButtonGroup(
                                items = listOf(
                                    ExpressiveSegmentItem("off", "Auto", Icons.Rounded.AutoAwesome),
                                    ExpressiveSegmentItem("dark", "Dark", Icons.Rounded.DarkMode),
                                    ExpressiveSegmentItem("light", "Light", Icons.Rounded.LightMode)
                                ),
                                selectedId = when (darkLightMode) {
                                    PetalBlurConfig.DARK_LIGHT_DARK -> "dark"
                                    PetalBlurConfig.DARK_LIGHT_LIGHT -> "light"
                                    else -> "off"
                                },
                                onItemSelected = {
                                    darkLightMode = when (it) {
                                        "dark" -> PetalBlurConfig.DARK_LIGHT_DARK
                                        "light" -> PetalBlurConfig.DARK_LIGHT_LIGHT
                                        else -> PetalBlurConfig.DARK_LIGHT_OFF
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Auto leaves the glass clear and picks light or dark text from your wallpaper.",
                                style = PetalText.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (darkLightMode != PetalBlurConfig.DARK_LIGHT_OFF) {
                        GlassSliderRow("Tint intensity", "$darkLightIntensity%", darkLightIntensity.toFloat(), 10f..100f,
                            PetalGroupPosition.BOTTOM) { darkLightIntensity = it.toInt() }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One glass/M3E containment card in a Petal group: same surface, corners and glass chrome as every settings row. */
@Composable
private fun GlassContainer(position: PetalGroupPosition, content: @Composable () -> Unit) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen)
    ) {
        content()
    }
}

@Composable
private fun GlassSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    position: PetalGroupPosition,
    onChange: (Float) -> Unit
) {
    GlassContainer(position) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = PetalText.rowTitle,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    valueText,
                    style = PetalText.value,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            PetalSlider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Live preview: the real [PetalLiquidGlassRenderer] drawing the widget over a crop of your wallpaper.
 * Live wallpapers cannot be read as an image, so a soft gradient stands in for them.
 */
@Composable
private fun GlassPreview(config: PetalBlurConfig, wallpaperRevision: Int, isPill: Boolean) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val padDp = 28
        val glassHeightDp = if (isPill) 48 else 60
        val wPx = (maxWidth.value * density).toInt().coerceAtLeast(2)
        val padPx = (padDp * density).toInt()
        val glassWpx = (wPx - padPx * 2).coerceAtLeast(2)
        val glassHpx = (glassHeightDp * density).toInt()
        val totalHpx = glassHpx + padPx * 2

        var cachedInner by remember { mutableStateOf<Bitmap?>(null) }
        var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
        var glass by remember { mutableStateOf<ImageBitmap?>(null) }
        var luma by remember { mutableFloatStateOf(0f) }

        // Extract and cache wallpaper backdrop only when geometry or wallpaper changes.
        LaunchedEffect(wPx, totalHpx, glassHpx, wallpaperRevision) {
            val pair = withContext(Dispatchers.Default) {
                try {
                    val (sw, sh) = PetalWallpaperBlurHelper.getScreenSize(context)
                    val l = ((sw - wPx) / 2).coerceAtLeast(0)
                    val t = (sh * 0.42f).toInt().coerceAtMost((sh - totalHpx).coerceAtLeast(0))
                    val crop = PetalWallpaperBlurHelper.getWallpaperCrop(
                        context, l, t, l + min(wPx, sw), t + totalHpx
                    ) ?: fallbackBackdrop(wPx, totalHpx)
                    val back = if (crop.width == wPx && crop.height == totalHpx) crop
                    else Bitmap.createScaledBitmap(crop, wPx, totalHpx, true).also { crop.recycle() }
                    val inner = Bitmap.createBitmap(back, padPx, padPx, glassWpx, glassHpx)
                    val l1 = PetalWallpaperBlurHelper.computeLuma(inner)
                    Triple(back, inner, l1)
                } catch (_: Throwable) {
                    null
                }
            }
            if (pair != null) {
                cachedInner = pair.second
                luma = pair.third
                backdrop = pair.first.asImageBitmap()
            }
        }

        // Re-render only the glass optics shader/blur pass during slider drags.
        LaunchedEffect(config, cachedInner, glassHpx) {
            val inner = cachedInner ?: return@LaunchedEffect
            delay(45)
            val rendered = withContext(Dispatchers.Default) {
                try {
                    PetalLiquidGlassRenderer.render(
                        context = context,
                        wallpaperCrop = inner,
                        widthPx = glassWpx,
                        heightPx = glassHpx,
                        config = config,
                        density = density
                    )
                } catch (_: Throwable) {
                    null
                }
            }
            if (rendered != null) {
                glass = rendered.asImageBitmap()
            }
        }

        val ink = Color(PetalWallpaperBlurHelper.inkFor(config, luma))
        val shapeRadius = min(config.cornerRadius, glassHeightDp / 2f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((glassHeightDp + padDp * 2).dp)
                .clip(RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            backdrop?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = if (isPill) (padDp + 24).dp else padDp.dp)
                    .fillMaxWidth()
                    .height(glassHeightDp.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                glass?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(shapeRadius.dp))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = if (isPill) 12.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isPill) 10.dp else 12.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = ink, modifier = Modifier.size(if (isPill) 20.dp else 22.dp))
                    Text(
                        "Search or type URL",
                        style = PetalText.rowTitle,
                        fontSize = if (isPill) 13.sp else 14.sp,
                        color = ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = ink, modifier = Modifier.size(if (isPill) 20.dp else 22.dp))
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = ink, modifier = Modifier.size(if (isPill) 20.dp else 22.dp))
                }
            }
        }
    }
}

private fun fallbackBackdrop(w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF8FA8D8.toInt(), 0xFFE7B8C8.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    // A few shapes so the refraction at the glass edge is visible.
    p.shader = null
    p.color = 0x66FFFFFF
    c.drawCircle(w * 0.22f, h * 0.5f, h * 0.45f, p)
    p.color = 0x553A4F8F
    c.drawCircle(w * 0.72f, h * 0.45f, h * 0.55f, p)
    return bmp
}
