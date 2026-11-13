package com.petal.browser.widget.glance

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Wallpaper as ImageIcon
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.petal.browser.R
import com.petal.browser.ui.components.PetalButtonVariant
import com.petal.browser.ui.components.PetalCircularWavyProgressIndicator
import com.petal.browser.ui.components.PetalLiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fullscreen calibration activity for Petal's liquid glass home screen widgets.
 *
 * Allows users to visually align the widget bounding rectangle over their home screen wallpaper,
 * matching launcher icon grids and providing instant hardware-blur liquid glass preview.
 */
class PetalWidgetGlassActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent {
            return Intent(context, PetalWidgetGlassActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        // Result is OK from the start: with CANCELED the launcher deletes the widget when the
        // user backs out, and even after OK it never draws the widget until it gets a first update.
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        PetalSearchGlanceWidgetUpdater.refresh(this)

        setContent {
            com.petal.browser.ui.theme.PetalExpressiveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    WidgetGlassCalibrationScreen(
                        appWidgetId = appWidgetId,
                        onClose = { PetalSearchGlanceWidgetUpdater.refresh(this@PetalWidgetGlassActivity); finish() },
                        onSaved = { rect ->
                            PetalGlassWidgets.setRect(this@PetalWidgetGlassActivity, appWidgetId, rect)
                            val result = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(Activity.RESULT_OK, result)
                            PetalSearchGlanceWidgetUpdater.refresh(this@PetalWidgetGlassActivity)
                            finish()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun WidgetGlassCalibrationScreen(
        appWidgetId: Int,
        onClose: () -> Unit,
        onSaved: (Rect) -> Unit
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current

        var wallpaper by remember { mutableStateOf<Bitmap?>(null) }
        var isProcessing by remember { mutableStateOf(false) }

        // Load cached wallpaper
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                wallpaper = PetalGlassWidgets.getWallpaper(context)
            }
        }

        // Image picker launcher
        val pickImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                isProcessing = true
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                decoder.isMutableRequired = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                        if (bmp != null) {
                            PetalGlassWidgets.setHomeWallpaper(context, bmp)
                            wallpaper = PetalGlassWidgets.getWallpaper(context)
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("PetalWidgetGlass", "Failed loading selected image", e)
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }

        val currentWallpaper = wallpaper
        if (currentWallpaper == null) {
            // Not ready view: Prompt user to pick or auto-detect wallpaper
            WallpaperNotReadyView(
                isProcessing = isProcessing,
                onAutoDetect = {
                    isProcessing = true
                    lifecycleScope.launch(Dispatchers.IO) {
                        val detected = PetalGlassWidgets.tryExtractSystemWallpaper(context)
                        wallpaper = detected
                        isProcessing = false
                    }
                },
                onPickImage = {
                    pickImageLauncher.launch("image/*")
                },
                onClose = onClose
            )
        } else {
            // Alignment overlay view
            WallpaperAlignerView(
                wallpaper = currentWallpaper,
                appWidgetId = appWidgetId,
                onPickNewWallpaper = { pickImageLauncher.launch("image/*") },
                onSave = onSaved,
                onClose = onClose
            )
        }
    }

    @Composable
    private fun WallpaperNotReadyView(
        isProcessing: Boolean,
        onAutoDetect: () -> Unit,
        onPickImage: () -> Unit,
        onClose: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Crop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.widget_glass_not_ready_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.widget_glass_not_ready_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isProcessing) {
                        PetalCircularWavyProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        PetalLiquidButton(
                            onClick = onAutoDetect,
                            variant = PetalButtonVariant.Filled,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(stringResource(R.string.widget_glass_detect_wallpaper))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        PetalLiquidButton(
                            onClick = onPickImage,
                            variant = PetalButtonVariant.Tonal,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(stringResource(R.string.widget_glass_pick_wallpaper))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(android.R.string.cancel),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    /** Which Petal widget this id belongs to, so the preview uses the same corner radius as the real widget. */
    private fun kindOf(context: Context, appWidgetId: Int): PetalWidgetKind {
        val cls = try {
            AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
        } catch (_: Throwable) { "" }
        return when {
            cls.contains("Petal2") -> PetalWidgetKind.PETAL2
            cls.contains("Petal3") -> PetalWidgetKind.PETAL3
            else -> PetalWidgetKind.PETAL1
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun WallpaperAlignerView(
        wallpaper: Bitmap,
        appWidgetId: Int,
        onPickNewWallpaper: () -> Unit,
        onSave: (Rect) -> Unit,
        onClose: () -> Unit
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        var screenSize by remember { mutableStateOf(IntSize.Zero) }

        val initialRect = remember(appWidgetId) { PetalGlassWidgets.getRect(context, appWidgetId) }
        val liveSize = remember(appWidgetId) { PetalGlassWidgets.getSize(context, appWidgetId) }
        val cfg = remember(appWidgetId) { PetalWidgetConfigStore.load(context, kindOf(context, appWidgetId)) }
        val dark = remember { isWidgetDark(context) }

        // The frame always has the widget's real pixel size, so the slice is never stretched.
        val frameW = remember { (liveSize?.first ?: initialRect?.width() ?: with(density) { 320.dp.roundToPx() }).toFloat() }
        val frameH = remember { (liveSize?.second ?: initialRect?.height() ?: with(density) { 64.dp.roundToPx() }).toFloat() }
        val cornerDp = remember(cfg, frameW, frameH) {
            val shortDp = with(density) { minOf(frameW, frameH).toDp().value }
            if (cfg.cornerRadius >= PETAL_RADIUS_FULL) shortDp / 2f else cfg.cornerRadius.toFloat()
        }

        var offsetX by remember { mutableStateOf(initialRect?.left?.toFloat() ?: 0f) }
        var offsetY by remember { mutableStateOf(initialRect?.top?.toFloat() ?: 0f) }
        var ready by remember { mutableStateOf(initialRect != null) }
        var preview by remember { mutableStateOf<Bitmap?>(null) }

        fun clampX(v: Float) = v.coerceIn(0f, max(0f, screenSize.width - frameW))
        fun clampY(v: Float) = v.coerceIn(0f, max(0f, screenSize.height - frameH))
        fun center() {
            offsetX = clampX((screenSize.width - frameW) / 2f)
            offsetY = clampY(screenSize.height * 0.28f)
        }

        // Live glass: re-slice whenever the frame moves, keeping only the newest request so dragging stays smooth.
        LaunchedEffect(ready, screenSize, wallpaper) {
            if (!ready) return@LaunchedEffect
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            snapshotFlow { Pair(offsetX.roundToInt(), offsetY.roundToInt()) }
                .debounce(16)
                .collectLatest { (x, y) ->
                    val left = x.coerceIn(0, max(0, screenSize.width - frameW.roundToInt()))
                    val top = y.coerceIn(0, max(0, screenSize.height - frameH.roundToInt()))
                    val r = Rect(left, top, left + frameW.roundToInt(), top + frameH.roundToInt())
                    val bmp = withContext(Dispatchers.Default) {
                        PetalGlassWidgetRenderer.render(
                            context,
                            PetalGlassWidgetRenderer.Spec(
                                rect = r,
                                blurDp = 24f,
                                cornerDp = cornerDp,
                                dark = dark,
                                edge = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f),
                                refraction = sp.getFloat("sp_liquid_glass_refraction", 1.0f).coerceIn(0f, 2f),
                                refractionWidth = sp.getFloat("sp_liquid_glass_refraction_width", 1.0f).coerceIn(0f, 2f)
                            )
                        )
                    }
                    if (bmp != null) preview = bmp
                }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    screenSize = size
                    if (size.width > 0 && size.height > 0) {
                        if (!ready) { center(); ready = true } else { offsetX = clampX(offsetX); offsetY = clampY(offsetY) }
                    }
                }
        ) {
            // Wallpaper is already display-sized, so FillBounds maps 1:1 to the real home screen.
            Image(
                bitmap = wallpaper.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

            if (ready) {
                val left = offsetX.roundToInt()
                val top = offsetY.roundToInt()
                val shape = RoundedCornerShape(cornerDp.dp)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(left, top) }
                        .size(with(density) { frameW.toDp() }, with(density) { frameH.toDp() })
                        .pointerInput(screenSize) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                offsetX = clampX(offsetX + drag.x)
                                offsetY = clampY(offsetY + drag.y)
                            }
                        }
                        .clip(shape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), shape),
                    contentAlignment = Alignment.Center
                ) {
                    preview?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "${with(density) { frameW.toDp().value.roundToInt() }} × ${with(density) { frameH.toDp().value.roundToInt() }} dp",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Header: close | title | wallpaper picker, never overlapping.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel), tint = Color.White)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(22.dp), color = Color.Black.copy(alpha = 0.6f)) {
                        Text(
                            text = stringResource(R.string.widget_glass_align_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onPickNewWallpaper,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(ImageIcon, stringResource(R.string.widget_glass_pick_wallpaper), tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.widget_glass_align_hint),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { center() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.widget_glass_reset), fontSize = 14.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            val l = offsetX.roundToInt(); val t = offsetY.roundToInt()
                            onSave(Rect(l, t, l + frameW.roundToInt(), t + frameH.roundToInt()))
                        },
                        modifier = Modifier.weight(1.3f).height(52.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.widget_glass_save), fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
