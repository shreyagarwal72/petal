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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } else if (imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
                    val uri = Uri.parse(imageUrl)
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } else {
                    val request = Request.Builder().url(imageUrl).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}: Failed to load image")
                    val bytes = response.body?.bytes() ?: throw RuntimeException("Empty image body")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                if (bitmap == null) {
                    mainHandler.post { onResult(null, null, "Failed to decode image bitmap.") }
                    return@Thread
                }

                scanBitmap(bitmap) { text, barcodes, err ->
                    mainHandler.post { onResult(text, barcodes, err) }
                }
            } catch (e: Exception) {
                mainHandler.post { onResult(null, null, e.localizedMessage ?: "Failed to load or process image.") }
            }
        }.start()
    }

    private fun scanBitmap(
        bitmap: Bitmap,
        onResult: (detectedText: String?, detectedBarcodes: List<BarcodeResult>?, error: String?) -> Unit
    ) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )

        textRecognizer.process(inputImage)
            .addOnSuccessListener { textResult ->
                val detectedText = textResult.text.takeIf { it.isNotBlank() }

                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodeList ->
                        val barcodes = barcodeList.mapNotNull { b ->
                            val rawValue = b.rawValue ?: return@mapNotNull null
                            BarcodeResult(
                                rawValue = rawValue,
                                displayValue = b.displayValue ?: rawValue,
                                format = b.format
                            )
                        }.takeIf { it.isNotEmpty() }

                        onResult(detectedText, barcodes, null)
                    }
                    .addOnFailureListener {
                        onResult(detectedText, null, null)
                    }
            }
            .addOnFailureListener { e ->
                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodeList ->
                        val barcodes = barcodeList.mapNotNull { b ->
                            val rawValue = b.rawValue ?: return@mapNotNull null
                            BarcodeResult(
                                rawValue = rawValue,
                                displayValue = b.displayValue ?: rawValue,
                                format = b.format
                            )
                        }.takeIf { it.isNotEmpty() }

                        onResult(null, barcodes, null)
                    }
                    .addOnFailureListener {
                        onResult(null, null, e.localizedMessage ?: "ML Kit recognition error.")
                    }
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
        activity: ComponentActivity,
        imageUrl: String
    ) {
        activity.runOnUiThread {
            val dialog = BottomSheetDialog(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                    val appFont = remember(fontName) {
                        AppFont.fromName(fontName)
                    }
                    val colorStyle = remember(styleName) {
                        try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
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
                            onDismiss = { dialog.dismiss() }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
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
                        text = "Image Recognition",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Google ML Kit On-Device Recognition",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Scanning image for text & barcodes...",
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
                                "Recognition Error",
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
                            text = "Nothing recognized",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No text or QR/barcodes were detected in this image.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Barcode Results
                if (!detectedBarcodes.isNullOrEmpty()) {
                    Text(
                        text = "Barcodes & QR Codes",
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
                                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Open Link", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Barcode Result", barcode.rawValue))
                                    com.petal.browser.view.NinjaToast.show(context, "Barcode copied to clipboard")
                                }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Barcode", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Text Recognition Results
                if (!detectedText.isNullOrBlank()) {
                    Text(
                        text = "Detected Text",
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
                                        com.petal.browser.view.NinjaToast.show(context, "Text copied to clipboard")
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Text")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
