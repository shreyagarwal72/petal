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
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
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
import com.petal.browser.ui.components.PetalLiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        // Default result is CANCELED unless user saves
        setResult(Activity.RESULT_CANCELED)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    WidgetGlassCalibrationScreen(
                        appWidgetId = appWidgetId,
                        onClose = { finish() },
                        onSaved = { rect ->
                            PetalGlassWidgets.setRect(this@PetalWidgetGlassActivity, appWidgetId, rect)
                            val result = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(Activity.RESULT_OK, result)
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
                .background(Color(0xFF0F111A))
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2230)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.widget_glass_not_ready_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isProcessing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

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

        // Stored rect or default placement
        val initialRect = remember(appWidgetId) {
            PetalGlassWidgets.getRect(context, appWidgetId)
        }
        val initialSize = remember(appWidgetId) {
            PetalGlassWidgets.getSize(context, appWidgetId)
        }

        // Widget frame coordinates in pixels
        var frameWidthPx by remember {
            mutableStateOf(
                initialRect?.width()?.toFloat()
                    ?: initialSize?.first?.toFloat()
                    ?: with(density) { 320.dp.toPx() }
            )
        }
        var frameHeightPx by remember {
            mutableStateOf(
                initialRect?.height()?.toFloat()
                    ?: initialSize?.second?.toFloat()
                    ?: with(density) { 64.dp.toPx() }
            )
        }

        var offsetX by remember {
            mutableStateOf(initialRect?.left?.toFloat() ?: 0f)
        }
        var offsetY by remember {
            mutableStateOf(initialRect?.top?.toFloat() ?: 0f)
        }

        // Center initially if no previous rect existed
        var isInitialized by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    screenSize = size
                    if (!isInitialized && size.width > 0 && size.height > 0) {
                        if (initialRect == null) {
                            offsetX = ((size.width - frameWidthPx) / 2f).coerceAtLeast(0f)
                            offsetY = (size.height * 0.28f).coerceAtLeast(0f)
                        }
                        isInitialized = true
                    }
                }
        ) {
            // Fullscreen home wallpaper
            Image(
                bitmap = wallpaper.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // High-contrast darkened scrim to clearly highlight the widget frame
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )

            // Grid lines overlay to help launcher cell snapping
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeColor = Color.White.copy(alpha = 0.08f)
                val cols = 4
                val rows = 6
                val stepX = size.width / cols
                val stepY = size.height / rows
                for (i in 1 until cols) {
                    drawLine(
                        color = strokeColor,
                        start = androidx.compose.ui.geometry.Offset(i * stepX, 0f),
                        end = androidx.compose.ui.geometry.Offset(i * stepX, size.height),
                        strokeWidth = 1f
                    )
                }
                for (j in 1 until rows) {
                    drawLine(
                        color = strokeColor,
                        start = androidx.compose.ui.geometry.Offset(0f, j * stepY),
                        end = androidx.compose.ui.geometry.Offset(size.width, j * stepY),
                        strokeWidth = 1f
                    )
                }
            }

            // Draggable widget alignment frame
            val frameLeft = offsetX.roundToInt().coerceIn(0, max(0, screenSize.width - frameWidthPx.roundToInt()))
            val frameTop = offsetY.roundToInt().coerceIn(0, max(0, screenSize.height - frameHeightPx.roundToInt()))
            val frameRight = frameLeft + frameWidthPx.roundToInt()
            val frameBottom = frameTop + frameHeightPx.roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(frameLeft, frameTop) }
                    .size(
                        width = with(density) { frameWidthPx.toDp() },
                        height = with(density) { frameHeightPx.toDp() }
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val maxW = screenSize.width - frameWidthPx
                            val maxH = screenSize.height - frameHeightPx
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, max(0f, maxW))
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, max(0f, maxH))
                        }
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Petal Widget Frame",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${frameWidthPx.roundToInt()} × ${frameHeightPx.roundToInt()} px",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            // Top control header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        tint = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.widget_glass_align_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                IconButton(
                    onClick = onPickNewWallpaper,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ImageIcon,
                        contentDescription = stringResource(R.string.widget_glass_pick_wallpaper),
                        tint = Color.White
                    )
                }
            }

            // Bottom control pill
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.widget_glass_align_hint),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PetalLiquidButton(
                        onClick = {
                            if (screenSize.width > 0 && screenSize.height > 0) {
                                offsetX = ((screenSize.width - frameWidthPx) / 2f).coerceAtLeast(0f)
                                offsetY = (screenSize.height * 0.28f).coerceAtLeast(0f)
                            }
                        },
                        variant = PetalButtonVariant.Tonal,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.widget_glass_reset), fontSize = 13.sp)
                    }

                    PetalLiquidButton(
                        onClick = {
                            val targetRect = Rect(frameLeft, frameTop, frameRight, frameBottom)
                            onSave(targetRect)
                        },
                        variant = PetalButtonVariant.Filled,
                        modifier = Modifier.weight(1.3f).height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.widget_glass_save), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
