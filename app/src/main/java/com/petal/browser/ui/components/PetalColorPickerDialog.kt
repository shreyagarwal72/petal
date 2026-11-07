package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private enum class PickerTab(val label: String) { WHEEL("Wheel"), RGB("RGB"), HSV("HSV"), IMAGE("Image") }

/**
 * Colour picker with a live hex preview and four ways to choose: colour wheel, RGB sliders,
 * HSV sliders, or tapping a colour in one of your photos. Hue is kept separately from the colour
 * so dragging saturation to zero never loses your place on the wheel.
 */
@Composable
fun PetalColorPickerDialog(
    initialColor: Color,
    title: String = "Pick a colour",
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(1f) }
    var tab by remember { mutableIntStateOf(0) }

    remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        0
    }

    val color = Color.hsv(hue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    fun setColor(c: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        // Keep the old hue when the new colour is grey (hue is meaningless there).
        if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0]
        sat = hsv[1]; value = hsv[2]
    }

    val animatedColor by animateColorAsState(color, label = "pickerPreview")
    val onPreview = if (animatedColor.luminance() > 0.5f) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = null,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth().height(132.dp).background(animatedColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = onPreview.copy(alpha = 0.75f))
                        Text(
                            "#%06X".format(animatedColor.toArgb() and 0xFFFFFF),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
                            color = onPreview
                        )
                    }
                }

                TabPills(selected = tab, onSelect = { tab = it }, modifier = Modifier.padding(16.dp))

                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(300.dp), contentAlignment = Alignment.Center) {
                    when (PickerTab.entries[tab]) {
                        PickerTab.WHEEL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                HueSatWheel(hue, sat, value) { h, s -> hue = h; sat = s }
                            }
                            LabeledColorSlider("V", value, 0f..1f) { value = it }
                        }
                        PickerTab.RGB -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val r = color.red; val g = color.green; val b = color.blue
                            LabeledColorSlider("R", r, 0f..1f) { setColor(Color(it, g, b)) }
                            LabeledColorSlider("G", g, 0f..1f) { setColor(Color(r, it, b)) }
                            LabeledColorSlider("B", b, 0f..1f) { setColor(Color(r, g, it)) }
                            HexField(color) { setColor(it) }
                        }
                        PickerTab.HSV -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledColorSlider("H", hue, 0f..360f, valueText = "${hue.toInt()}°") { hue = it }
                            LabeledColorSlider("S", sat, 0f..1f) { sat = it }
                            LabeledColorSlider("V", value, 0f..1f) { value = it }
                        }
                        PickerTab.IMAGE -> ImagePickPage { setColor(it) }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onConfirm(color) },
                        shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("OK", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun TabPills(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(ExpressiveButtonShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PickerTab.entries.forEachIndexed { i, t ->
            val bg by animateColorAsState(
                if (i == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "tabBg"
            )
            val fg by animateColorAsState(
                if (i == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabFg"
            )
            Box(
                Modifier.weight(1f).height(44.dp).clip(ExpressiveButtonShape).background(bg).clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Text(t.label, color = fg, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) }
        }
    }
}

@Composable
private fun HueSatWheel(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val hues = remember { (0..360 step 30).map { Color.hsv(it.toFloat() % 360f, 1f, 1f) } }
    BoxWithConstraints(Modifier.aspectRatio(1f)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun pick(p: Offset, size: IntSize) {
                        val r = min(size.width, size.height) / 2f
                        val dx = p.x - size.width / 2f
                        val dy = p.y - size.height / 2f
                        val h = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                        onChange(h, (sqrt(dx * dx + dy * dy) / r).coerceIn(0f, 1f))
                    }
                    detectTapGestures { pick(it, size) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { }, onDrag = { change, _ ->
                        change.consume()
                        val r = min(size.width, size.height) / 2f
                        val dx = change.position.x - size.width / 2f
                        val dy = change.position.y - size.height / 2f
                        val h = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                        onChange(h, (sqrt(dx * dx + dy * dy) / r).coerceIn(0f, 1f))
                    })
                }
        ) {
            val r = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Brush.sweepGradient(hues, c), radius = r, center = c)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), c, r), radius = r, center = c)
            // Dim the whole wheel with the V value, like a real HSV disc.
            drawCircle(Color.Black.copy(alpha = 1f - value.coerceIn(0f, 1f)), radius = r, center = c)
            val rad = Math.toRadians(hue.toDouble())
            val t = Offset(c.x + cos(rad).toFloat() * sat * r, c.y + sin(rad).toFloat() * sat * r)
            drawCircle(Color.White, radius = 14.dp.toPx(), center = t)
            drawCircle(Color.hsv(hue, sat, value), radius = 11.dp.toPx(), center = t)
        }
    }
}

@Composable
private fun LabeledColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String = "%.2f".format(value / range.endInclusive),
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.size(28.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        PetalSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            modifier = Modifier.padding(start = 8.dp).size(width = 44.dp, height = 24.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HexField(color: Color, onColor: (Color) -> Unit) {
    var text by remember(color) { mutableStateOf("%06X".format(color.toArgb() and 0xFFFFFF)) }
    TextField(
        value = text,
        onValueChange = { raw ->
            val clean = raw.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
            text = clean
            if (clean.length == 6) clean.toLongOrNull(16)?.let { onColor(Color(0xFF000000 or it)) }
        },
        label = { Text("Hex") },
        prefix = { Text("#") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ImagePickPage(onPicked: (Color) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var marker by remember { mutableStateOf<Offset?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        bitmap = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (t: Throwable) {
            null
        }
        marker = null
    }

    val bmp = bitmap
    if (bmp == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pick a photo, then tap any spot to use its colour.", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = ExpressiveButtonShape
            ) { Text("Choose photo") }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .pointerInput(bmp) {
                        fun sample(p: Offset) {
                            val scale = min(size.width / bmp.width.toFloat(), size.height / bmp.height.toFloat())
                            val left = (size.width - bmp.width * scale) / 2f
                            val top = (size.height - bmp.height * scale) / 2f
                            val x = ((p.x - left) / scale).toInt()
                            val y = ((p.y - top) / scale).toInt()
                            if (x in 0 until bmp.width && y in 0 until bmp.height) {
                                marker = p
                                onPicked(Color(bmp.getPixel(x, y) or (0xFF shl 24)))
                            }
                        }
                        detectTapGestures { sample(it) }
                    }
                    .pointerInput(bmp) {
                        detectDragGestures(onDrag = { change, _ ->
                            change.consume()
                            val p = change.position
                            val scale = min(size.width / bmp.width.toFloat(), size.height / bmp.height.toFloat())
                            val left = (size.width - bmp.width * scale) / 2f
                            val top = (size.height - bmp.height * scale) / 2f
                            val x = ((p.x - left) / scale).toInt()
                            val y = ((p.y - top) / scale).toInt()
                            if (x in 0 until bmp.width && y in 0 until bmp.height) {
                                marker = p
                                onPicked(Color(bmp.getPixel(x, y) or (0xFF shl 24)))
                            }
                        })
                    }
            ) {
                Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                marker?.let { m ->
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.White, 12.dp.toPx(), m, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        drawCircle(Color.Black.copy(alpha = 0.35f), 14.dp.toPx(), m, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                }
            }
            FilledTonalButton(
                onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Another photo") }
        }
    }
}
