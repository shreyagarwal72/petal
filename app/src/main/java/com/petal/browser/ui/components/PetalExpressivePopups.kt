package com.petal.browser.ui.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Shared popup/dialog language for Petal.
 *
 * All transient surfaces use the same M3 Expressive containment rules:
 * - large 28–32dp shape
 * - tonal surface containers instead of flat white/black cards
 * - subtle outline for separation on AMOLED/dark themes
 * - generous 56dp menu rows and spring/motion supplied by MaterialExpressiveTheme
 */
object PetalExpressivePopupDefaults {
    val dialogShape: Shape = RoundedCornerShape(32.dp)
    val menuShape: Shape = RoundedCornerShape(24.dp)
    val menuContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val dialogContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
    val outline: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
}

@Composable
fun PetalExpressiveDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PetalExpressivePopupDefaults.dialogShape,
    containerColor: Color = PetalExpressivePopupDefaults.dialogContainerColor,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) {
    com.petal.browser.ui.containment.PetalDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.modalScaleIn(),
        shape = shape,
        containerColor = containerColor,
        properties = properties,
        content = content,
    )
}

@Composable
fun PetalExpressiveAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    confirmText: String = "OK",
    onConfirm: () -> Unit,
    dismissText: String? = "Cancel",
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    PetalExpressiveDialog(onDismissRequest = onDismissRequest) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (destructive) MaterialTheme.colorScheme.errorContainer else iconContainerColor,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else iconContentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dismissText != null) {
                TextButton(
                    onClick = { (onDismiss ?: onDismissRequest)() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(dismissText)
                }
                Spacer(Modifier.width(8.dp))
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else ButtonDefaults.buttonColors()
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PetalExpressiveMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (() -> Unit))? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    enabled: Boolean = true
) {
    com.petal.browser.ui.containment.PetalPopupMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
    )
}

/**
 * Material 3 Expressive Text Input Prompt Dialog (JavaScript prompt()).
 */
@Composable
fun PetalExpressiveTextPromptDialog(
    title: String,
    message: String?,
    defaultValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textState = remember { mutableStateOf(defaultValue) }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = textState.value,
                onValueChange = { textState.value = it },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(textState.value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.ui_ok), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


/** One row of a web page <select> / menu list, flattened for the expressive picker. */
data class PetalChoiceOption(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val disabled: Boolean = false,
    val isHeader: Boolean = false,
    val isSeparator: Boolean = false,
    val indented: Boolean = false
)

/**
 * Material 3 Expressive choice popup for web <select> dropdowns (single / multiple).
 * Redesigned with Material 3 Expressive containment matching the context menu and settings:
 * tonal surfaceContainerHigh, rounded containment corners, connected rows, and responsive selection.
 */
@Composable
fun PetalExpressiveChoiceDialog(
    title: String?,
    options: List<PetalChoiceOption>,
    multiple: Boolean,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            addAll(options.filter { it.selected && !it.isHeader && !it.isSeparator }.map { it.id })
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val first = options.indexOfFirst { it.selected }
        if (first > 1) listState.scrollToItem(first - 1)
    }

    com.petal.browser.ui.containment.PetalHeroCard(
        shape = com.petal.browser.ui.containment.PetalContainmentShapes.Hero,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 380.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!title.isNullOrBlank()) title else if (multiple) "Select Options" else "Choose Option",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (multiple) "Select one or more items" else "Tap an option to select",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Selectable Rows List
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(options.size) { index ->
                    val opt = options[index]
                    when {
                        opt.isSeparator -> HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        opt.isHeader -> Text(
                            text = opt.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        else -> {
                            val isSel = opt.id in selected
                            val bg = if (isSel) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                     else MaterialTheme.colorScheme.surfaceContainerLow
                            val fg = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer
                                     else MaterialTheme.colorScheme.onSurface
                            val context = androidx.compose.ui.platform.LocalContext.current

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = bg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !opt.disabled) {
                                        try {
                                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                                                .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.7f)
                                        } catch (_: Exception) {}
                                        if (multiple) {
                                            if (isSel) selected.remove(opt.id) else selected.add(opt.id)
                                        } else {
                                            onConfirm(listOf(opt.id))
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .padding(
                                            start = if (opt.indented) 28.dp else 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 8.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = opt.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (opt.disabled) fg.copy(alpha = 0.38f) else fg,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (multiple) {
                                        Checkbox(
                                            checked = isSel,
                                            onCheckedChange = null,
                                            enabled = !opt.disabled
                                        )
                                    } else if (isSel) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Bottom action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(stringResource(R.string.ui_cancel), fontWeight = FontWeight.SemiBold)
                }
                if (multiple) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selected.toList()) },
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.ui_ok), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


private fun petalParseHex(hex: String?): Int {
    return try {
        val c = android.graphics.Color.parseColor(if (hex.isNullOrBlank()) "#000000" else hex)
        android.graphics.Color.rgb(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    } catch (_: Throwable) {
        android.graphics.Color.BLACK
    }
}

private fun petalToHex(argb: Int): String = String.format(
    java.util.Locale.ROOT, "#%02x%02x%02x",
    android.graphics.Color.red(argb), android.graphics.Color.green(argb), android.graphics.Color.blue(argb)
)

private val PetalColorPresets = listOf(
    0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
    0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF00ACC1.toInt(), 0xFF00897B.toInt(),
    0xFF43A047.toInt(), 0xFFFDD835.toInt(), 0xFFFB8C00.toInt(), 0xFF6D4C41.toInt(),
    0xFF757575.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()
)

/** Material 3 Expressive colour picker for <input type="color">. */
@Composable
fun PetalExpressiveColorDialog(
    title: String?,
    initialHex: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember { petalParseHex(initialHex) }
    var r by remember { mutableStateOf(android.graphics.Color.red(start).toFloat()) }
    var g by remember { mutableStateOf(android.graphics.Color.green(start).toFloat()) }
    var b by remember { mutableStateOf(android.graphics.Color.blue(start).toFloat()) }
    var hexText by remember { mutableStateOf(petalToHex(start)) }
    val current = android.graphics.Color.rgb(r.toInt(), g.toInt(), b.toInt())

    fun apply(argb: Int) {
        r = android.graphics.Color.red(argb).toFloat()
        g = android.graphics.Color.green(argb).toFloat()
        b = android.graphics.Color.blue(argb).toFloat()
        hexText = petalToHex(argb)
    }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = if (title.isNullOrBlank()) "Pick a colour" else title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(current))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
        )
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PetalColorPresets.size) { i ->
                val c = PetalColorPresets[i]
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(c))
                        .border(
                            if (c == current) 3.dp else 1.dp,
                            if (c == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            androidx.compose.foundation.shape.CircleShape
                        )
                        .clickable { apply(c) }
                )
            }
        }
        listOf(Triple("R", r, 0), Triple("G", g, 1), Triple("B", b, 2)).forEach { (label, value, idx) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Slider(
                    value = value,
                    onValueChange = {
                        when (idx) { 0 -> r = it; 1 -> g = it; else -> b = it }
                        hexText = petalToHex(android.graphics.Color.rgb(r.toInt(), g.toInt(), b.toInt()))
                    },
                    valueRange = 0f..255f,
                    modifier = Modifier.weight(1f)
                )
                Text(value.toInt().toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
            }
        }
        OutlinedTextField(
            value = hexText,
            onValueChange = { v ->
                hexText = v
                if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) {
                    val c = petalParseHex(v)
                    r = android.graphics.Color.red(c).toFloat()
                    g = android.graphics.Color.green(c).toFloat()
                    b = android.graphics.Color.blue(c).toFloat()
                }
            },
            label = { Text("Hex") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onConfirm(petalToHex(current)) },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(stringResource(R.string.ui_ok), fontWeight = FontWeight.Bold) }
        }
    }
}

private fun petalParseDate(mode: String, v: String?): java.time.LocalDate? {
    if (v.isNullOrBlank() || mode == "time") return null
    return try {
        when (mode) {
            "month" -> java.time.YearMonth.parse(v.take(7)).atDay(1)
            "week" -> {
                val m = Regex("(\\d{4})-W(\\d{2})").find(v)
                if (m == null) null else java.time.LocalDate.of(m.groupValues[1].toInt(), 1, 4)
                    .with(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR, m.groupValues[2].toLong())
            }
            else -> java.time.LocalDate.parse(v.take(10))
        }
    } catch (_: Throwable) { null }
}

private fun petalParseTime(mode: String, v: String?): java.time.LocalTime? {
    if (v.isNullOrBlank()) return null
    return try {
        when (mode) {
            "time" -> java.time.LocalTime.parse(v.take(5))
            "datetime-local" -> java.time.LocalTime.parse(v.substringAfter('T', "").take(5))
            else -> null
        }
    } catch (_: Throwable) { null }
}

/**
 * Material 3 Expressive date / time / month / week / datetime-local picker.
 * mode: "date", "month", "week", "time", "datetime-local".
 */
@Composable
fun PetalExpressiveDateTimeDialog(
    title: String?,
    mode: String,
    defaultValue: String?,
    minValue: String?,
    maxValue: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val utc = java.time.ZoneOffset.UTC
    val hasDate = mode != "time"
    val hasTime = mode == "time" || mode == "datetime-local"
    val initDate = remember { petalParseDate(mode, defaultValue) ?: java.time.LocalDate.now() }
    val initTime = remember { petalParseTime(mode, defaultValue) ?: java.time.LocalTime.now().withSecond(0).withNano(0) }
    val minDate = remember { petalParseDate(mode, minValue) }
    val maxDate = remember { petalParseDate(mode, maxValue) }
    val selectable = remember {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate()
                return (minDate == null || !d.isBefore(minDate)) && (maxDate == null || !d.isAfter(maxDate))
            }
            override fun isSelectableYear(year: Int): Boolean =
                (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
        }
    }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initDate.atStartOfDay(utc).toInstant().toEpochMilli(),
        selectableDates = selectable
    )
    val timeState = rememberTimePickerState(
        initialHour = initTime.hour,
        initialMinute = initTime.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(ctx)
    )
    var step by remember { mutableStateOf(if (hasDate) 0 else 1) }

    fun result(): String {
        val date = java.time.Instant.ofEpochMilli(dateState.selectedDateMillis ?: initDate.atStartOfDay(utc).toInstant().toEpochMilli())
            .atZone(utc).toLocalDate()
        val t = String.format(java.util.Locale.ROOT, "%02d:%02d", timeState.hour, timeState.minute)
        return when (mode) {
            "month" -> String.format(java.util.Locale.ROOT, "%04d-%02d", date.year, date.monthValue)
            "week" -> String.format(
                java.util.Locale.ROOT, "%04d-W%02d",
                date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR),
                date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            )
            "time" -> t
            "datetime-local" -> date.toString() + "T" + t
            else -> date.toString()
        }
    }

    if (step == 0) {
        val goesToTime = hasTime
        DatePickerDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(28.dp),
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            confirmButton = {
                Button(
                    onClick = { if (goesToTime) step = 1 else onConfirm(result()) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(if (goesToTime) "Next" else stringResource(R.string.ui_ok), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_cancel)) }
            }
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
    } else {
        PetalExpressiveDialog(onDismissRequest = onDismiss) {
            Text(
                text = if (title.isNullOrBlank()) "Select time" else title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (hasDate) {
                    TextButton(onClick = { step = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(result()) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.ui_ok), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * Material 3 Expressive Authentication Prompt Dialog (HTTP Basic / Digest Auth).
 */
@Composable
fun PetalExpressiveAuthPromptDialog(
    title: String,
    message: String?,
    isPasswordOnly: Boolean,
    initialUsername: String = "",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val usernameState = remember { mutableStateOf(initialUsername) }
    val passwordState = remember { mutableStateOf("") }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isPasswordOnly) {
                OutlinedTextField(
                    value = usernameState.value,
                    onValueChange = { usernameState.value = it },
                    label = { Text(stringResource(R.string.ui_username)) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = passwordState.value,
                onValueChange = { passwordState.value = it },
                label = { Text(stringResource(R.string.ui_password)) },
                shape = RoundedCornerShape(16.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(usernameState.value, passwordState.value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.ui_sign_in), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Java Interop Bridge to render pure Material 3 Expressive Prompts directly from GeckoView.
 */
object PetalExpressivePromptBridge {

    @JvmStatic
    fun showAlert(
        context: android.content.Context,
        title: String,
        message: String?,
        onConfirm: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveAlertDialog(
                        onDismissRequest = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.run()
                        },
                        title = title,
                        message = message,
                        confirmText = stringResource(R.string.ui_ok),
                        onConfirm = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.run()
                        },
                        dismissText = null
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onConfirm.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    @JvmStatic
    fun showConfirm(
        context: android.content.Context,
        title: String,
        message: String?,
        onConfirm: Runnable,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveAlertDialog(
                        onDismissRequest = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        },
                        title = title,
                        message = message,
                        confirmText = stringResource(R.string.ui_ok),
                        onConfirm = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.run()
                        },
                        dismissText = stringResource(R.string.ui_cancel),
                        onDismiss = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        }
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onCancel.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    @JvmStatic
    fun showPrompt(
        context: android.content.Context,
        title: String,
        message: String?,
        defaultValue: String?,
        onConfirm: java.util.function.Consumer<String>,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveTextPromptDialog(
                        title = title,
                        message = message,
                        defaultValue = defaultValue ?: "",
                        onConfirm = { value ->
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.accept(value)
                        },
                        onDismiss = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        }
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onCancel.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    @JvmStatic
    fun showAuth(
        context: android.content.Context,
        title: String,
        message: String?,
        isPasswordOnly: Boolean,
        initialUsername: String?,
        onConfirm: java.util.function.BiConsumer<String, String>,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveAuthPromptDialog(
                        title = title,
                        message = message,
                        isPasswordOnly = isPasswordOnly,
                        initialUsername = initialUsername ?: "",
                        onConfirm = { user, pass ->
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.accept(user, pass)
                        },
                        onDismiss = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        }
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onCancel.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }


    @JvmStatic
    fun showChoice(
        context: android.content.Context,
        title: String?,
        options: List<PetalChoiceOption>,
        multiple: Boolean,
        onConfirm: java.util.function.Consumer<List<String>>,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: run { onCancel.run(); return }
        var dialog: androidx.appcompat.app.AlertDialog? = null
        var finished = false
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveChoiceDialog(
                        title = title,
                        options = options,
                        multiple = multiple,
                        onConfirm = { ids ->
                            if (!finished) { finished = true; try { dialog?.dismiss() } catch (_: Exception) {}; onConfirm.accept(ids) }
                        },
                        onDismiss = {
                            if (!finished) { finished = true; try { dialog?.dismiss() } catch (_: Exception) {}; onCancel.run() }
                        }
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { if (!finished) { finished = true; onCancel.run() } }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }


    private fun hostCompose(
        activity: androidx.activity.ComponentActivity,
        onCancel: Runnable,
        content: @Composable (complete: (() -> Unit) -> Unit) -> Unit
    ) {
        var dialog: androidx.appcompat.app.AlertDialog? = null
        var finished = false
        val complete: (() -> Unit) -> Unit = { action ->
            if (!finished) {
                finished = true
                try { dialog?.dismiss() } catch (_: Exception) {}
                action()
            }
        }
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { com.petal.browser.ui.theme.PetalExpressiveTheme { content(complete) } }
        }
        dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { complete { onCancel.run() } }
            .create().apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                show()
            }
    }

    @JvmStatic
    fun showColor(
        context: android.content.Context,
        title: String?,
        initialHex: String?,
        onConfirm: java.util.function.Consumer<String>,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: run { onCancel.run(); return }
        hostCompose(activity, onCancel) { complete ->
            PetalExpressiveColorDialog(
                title = title,
                initialHex = initialHex,
                onConfirm = { hex -> complete { onConfirm.accept(hex) } },
                onDismiss = { complete { onCancel.run() } }
            )
        }
    }

    @JvmStatic
    fun showDateTime(
        context: android.content.Context,
        title: String?,
        mode: String,
        defaultValue: String?,
        minValue: String?,
        maxValue: String?,
        onConfirm: java.util.function.Consumer<String>,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: run { onCancel.run(); return }
        hostCompose(activity, onCancel) { complete ->
            PetalExpressiveDateTimeDialog(
                title = title,
                mode = mode,
                defaultValue = defaultValue,
                minValue = minValue,
                maxValue = maxValue,
                onConfirm = { v -> complete { onConfirm.accept(v) } },
                onDismiss = { complete { onCancel.run() } }
            )
        }
    }

    private fun findActivity(context: android.content.Context): androidx.activity.ComponentActivity? {
        var curr = context
        while (curr is android.content.ContextWrapper) {
            if (curr is androidx.activity.ComponentActivity) return curr
            curr = curr.baseContext
        }
        return null
    }
}
