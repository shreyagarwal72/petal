package com.petal.browser.compose.mlkit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.petal.browser.R
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.BrowserUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

object PetalImageScannerManager {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) return null

            var inSampleSize = 1
            var w = origW
            var h = origH
            while (w > maxDim * 2 || h > maxDim * 2) {
                inSampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun scanImageFromUrl(
        context: Context,
        imageUrl: String,
        onResult: (detectedText: String?, detectedBarcodes: List<BarcodeResult>?, error: String?) -> Unit
    ) {
        Thread {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                val bitmap: Bitmap? = if (imageUrl.startsWith("data:image")) {
                    val base64Data = imageUrl.substringAfter(",")
                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, boundsOptions)
                    var inSampleSize = 1
                    var w = boundsOptions.outWidth
                    var h = boundsOptions.outHeight
                    while (w > 1600 * 2 || h > 1600 * 2) {
                        inSampleSize *= 2
                        w /= 2
                        h /= 2
                    }
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, decodeOptions)
                } else if (imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
                    val uri = Uri.parse(imageUrl)
                    decodeSampledBitmap(context, uri, 1600)
                } else {
                    val request = Request.Builder().url(imageUrl).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}: Failed to load image")
                    val bytes = response.body?.bytes() ?: throw RuntimeException("Empty image body")
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                    var inSampleSize = 1
                    var w = boundsOptions.outWidth
                    var h = boundsOptions.outHeight
                    while (w > 1600 * 2 || h > 1600 * 2) {
                        inSampleSize *= 2
                        w /= 2
                        h /= 2
                    }
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                }

                if (bitmap == null) {
                    mainHandler.post { onResult(null, null, "Failed to decode image bitmap.") }
                    return@Thread
                }

                scanBitmap(bitmap) { text, barcodes, err ->
                    mainHandler.post { onResult(text, barcodes, err) }
                }
            } catch (e: Throwable) {
                mainHandler.post { onResult(null, null, e.localizedMessage ?: "Failed to load or process image.") }
            }
        }.start()
    }

    private fun scanBitmap(
        bitmap: Bitmap,
        onResult: (detectedText: String?, detectedBarcodes: List<BarcodeResult>?, error: String?) -> Unit
    ) {
        try {
            val maxDim = 1600
            val maxSide = maxOf(bitmap.width, bitmap.height)
            val scaledBitmap = if (maxSide > maxDim) {
                val scale = maxDim.toFloat() / maxSide
                val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val width = scaledBitmap.width
            val height = scaledBitmap.height
            val pixels = IntArray(width * height)
            scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            val source = RGBLuminanceSource(width, height, pixels)
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    com.google.zxing.BarcodeFormat.DATA_MATRIX,
                    com.google.zxing.BarcodeFormat.AZTEC,
                    com.google.zxing.BarcodeFormat.PDF_417,
                    com.google.zxing.BarcodeFormat.EAN_13,
                    com.google.zxing.BarcodeFormat.EAN_8,
                    com.google.zxing.BarcodeFormat.UPC_A,
                    com.google.zxing.BarcodeFormat.UPC_E,
                    com.google.zxing.BarcodeFormat.CODE_39,
                    com.google.zxing.BarcodeFormat.CODE_93,
                    com.google.zxing.BarcodeFormat.CODE_128,
                    com.google.zxing.BarcodeFormat.ITF,
                    com.google.zxing.BarcodeFormat.CODABAR
                )
            )
            val result: Result = MultiFormatReader().apply { setHints(hints) }.decode(BinaryBitmap(HybridBinarizer(source)))
            onResult(
                result.text,
                listOf(BarcodeResult(result.text, result.text, result.barcodeFormat.ordinal)),
                null
            )
        } catch (_: Throwable) {
            onResult(null, null, null)
        }
    }
}

data class BarcodeResult(
    val rawValue: String,
    val displayValue: String,
    val format: Int
)

object PetalImageScannerBridge {
    @JvmStatic
    fun show(
        context: Context,
        imageUrl: String
    ) {
        var current: Context? = context
        while (current is android.content.ContextWrapper && current !is ComponentActivity) {
            current = current.baseContext
        }
        val activity = current as? ComponentActivity ?: return

        activity.runOnUiThread {
            val dialog = BottomSheetDialog(activity)
            dialog.setOnShowListener {
                dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                dialog.behavior.skipCollapsed = true
                dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                    val appFont = remember(fontName) {
                        AppFont.fromName(fontName)
                    }
                    val colorStyle = remember(styleName) {
                        try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                    }

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        PetalImageScanResultSheet(
                            imageUrl = imageUrl,
                            onDismiss = {
                                try {
                                    if (dialog.isShowing) dialog.dismiss()
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.setOnDismissListener {
                composeView.clearFocus()
                dialog.window?.decorView?.clearFocus()
                (activity as? com.petal.browser.activity.BrowserActivity)
                    ?.restoreBrowserInputFocus()
            }
            dialog.show()
        }
    }
}

@Composable
fun PetalImageScanResultSheet(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var detectedText by remember { mutableStateOf<String?>(null) }
    var detectedBarcodes by remember { mutableStateOf<List<BarcodeResult>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imageUrl) {
        isLoading = true
        errorMessage = null
        PetalImageScannerManager.scanImageFromUrl(context, imageUrl) { text, barcodes, err ->
            isLoading = false
            detectedText = text
            detectedBarcodes = barcodes
            errorMessage = err
        }
    }

    val isLiquidGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Surface(
        shape = sheetShape,
        color = if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (isLiquidGlass) {
                val glassWallpaper = com.petal.browser.ui.glass.rememberPetalGlassWallpaper()
                val sheetDark = MaterialTheme.colorScheme.background.luminance() < .5f
                val sheetSheen = com.petal.browser.ui.glass.rememberGlassSheen(isLiquidGlass)
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
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )

                Spacer(Modifier.height(14.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_image_recognition),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_google_ml_kit_on_device),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close))
                    }
                }

                Spacer(Modifier.height(18.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        com.petal.browser.compose.composable.PetalContainedLoadingIndicator(
                            size = 40.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.ui_scanning_image_for_text_barcodes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.ui_recognition_error),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = errorMessage ?: "Failed to process image.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else if (detectedText.isNullOrBlank() && detectedBarcodes.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.ui_nothing_recognized),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.ui_no_text_or_qr_barcodes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Barcode Results
                    if (!detectedBarcodes.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.ui_barcodes_qr_codes),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        detectedBarcodes!!.forEach { barcode ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.QrCode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = barcode.displayValue,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (barcode.rawValue.startsWith("http://") || barcode.rawValue.startsWith("https://")) {
                                        IconButton(onClick = {
                                            onDismiss()
                                            try {
                                                BrowserUnit.intentURL(context, Uri.parse(barcode.rawValue))
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = stringResource(R.string.ui_open_link), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Barcode Result", barcode.rawValue))
                                        com.petal.browser.view.PetalToast.show(context, "Barcode copied to clipboard")
                                    }) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.ui_copy_barcode), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Text Recognition Results
                    if (!detectedText.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.ui_detected_text),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = detectedText!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Detected Text", detectedText))
                                            com.petal.browser.view.PetalToast.show(context, "Text copied to clipboard")
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.ui_copy_text))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
