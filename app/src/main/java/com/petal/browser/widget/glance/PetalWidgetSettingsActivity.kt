package com.petal.browser.widget.glance

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.PetalSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.petal.browser.ui.theme.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.ui.theme.PetalExpressiveTheme

/**
 * The widget customization screen: the widget's configure activity (opens when a Petal widget is added on launchers
 * that ask, and from "Reconfigure" on Android 12+) and also opens from the app's settings for a chosen widget.
 *
 * A live preview, drawn from the very same layout plan and palette the real widget uses, stays pinned above four
 * tabs: Style (badge, shapes, hint), Colors (app theme or custom, transparency, per-element colors), Shortcuts
 * (which action each button and tile does) and Shape (corner radius, outline, Liquid Glass alignment).
 * Nothing is applied to the home screen until Save, as in the reference app's editor.
 */
class PetalWidgetSettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_KIND = "com.petal.browser.widget.EXTRA_KIND"

        fun intent(context: Context, kind: PetalWidgetKind): Intent =
            Intent(context, PetalWidgetSettingsActivity::class.java).putExtra(EXTRA_KIND, kind.key)
    }

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val configuring = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        val kind = if (configuring) kindOfWidget(appWidgetId) else PetalWidgetKind.fromKey(intent.getStringExtra(EXTRA_KIND))

        if (configuring) {
            // OK from the start: with CANCELED the launcher deletes the widget when the user backs out, and it
            // never draws a widget that hasn't had a first update.
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            PetalSearchGlanceWidgetUpdater.refresh(this)
        }

        setContent {
            PetalExpressiveTheme {
                PetalWidgetSettingsScreen(
                    kind = kind,
                    appWidgetId = appWidgetId,
                    onSaved = { cfg ->
                        PetalWidgetConfigStore.save(this, cfg)
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun kindOfWidget(id: Int): PetalWidgetKind {
        val className = try {
            AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className
        } catch (_: Throwable) {
            null
        }
        return PetalWidgetKind.entries.firstOrNull { it.receiverClass.name == className }
            ?: PetalWidgetKind.fromKey(intent.getStringExtra(EXTRA_KIND))
    }
}

private enum class SettingsTab(val labelRes: Int) {
    STYLE(R.string.pw_tab_style),
    COLORS(R.string.pw_tab_colors),
    SHORTCUTS(R.string.pw_tab_shortcuts),
    SHAPE(R.string.pw_tab_shape)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PetalWidgetSettingsScreen(
    kind: PetalWidgetKind,
    appWidgetId: Int,
    onSaved: (PetalWidgetConfig) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(PetalWidgetConfigStore.load(context, kind)) }
    var tab by remember { mutableStateOf(SettingsTab.STYLE) }
    var previewExpanded by remember { mutableStateOf(false) }
    val palette = remember(draft) { buildPalette(context, draft) }
    val configuring = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(kind.nameRes), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.pw_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { draft = PetalWidgetConfig.defaults(kind) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.pw_reset))
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!configuring) {
                        FilledTonalButton(onClick = { pinWidget(context, kind) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pw_add_home), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(onClick = { onSaved(draft) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pw_save))
                    }
                }
            }
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // ── Live preview, pinned ──
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val width = minOf(maxWidth.value, 360f)
                    val height = if (kind.tileSlots > 0 && previewExpanded) 132f else 64f
                    PreviewBackdrop(width, height + 24f) {
                        WidgetPreview(draft, palette, width - 24f, height)
                    }
                }
                if (kind.tileSlots > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !previewExpanded,
                            onClick = { previewExpanded = false },
                            label = { Text(stringResource(R.string.pw_preview_compact)) }
                        )
                        FilterChip(
                            selected = previewExpanded,
                            onClick = { previewExpanded = true },
                            label = { Text(stringResource(R.string.pw_preview_expanded)) }
                        )
                    }
                }
            }

            // ── Tabs ──
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsTab.entries.forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(stringResource(t.labelRes)) })
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    SettingsTab.STYLE -> StyleTab(draft) { draft = it }
                    SettingsTab.COLORS -> ColorsTab(draft) { draft = it }
                    SettingsTab.SHORTCUTS -> ShortcutsTab(draft) { draft = it }
                    SettingsTab.SHAPE -> ShapeTab(draft, appWidgetId) { draft = it }
                }
                Text(
                    stringResource(R.string.pw_visibility_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun pinWidget(context: Context, kind: PetalWidgetKind) {
    try {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, kind.receiverClass), null, null)
        }
    } catch (t: Throwable) {
        android.util.Log.w("PetalWidgetSettings", "Pinning the widget failed", t)
    }
}

// ───────────────────────────── tabs ─────────────────────────────

@Composable
private fun StyleTab(cfg: PetalWidgetConfig, onChange: (PetalWidgetConfig) -> Unit) {
    val hasBadge = cfg.kind != PetalWidgetKind.PETAL2
    if (hasBadge) {
        SectionCard(stringResource(R.string.pw_badge)) {
            SwitchRow(stringResource(R.string.pw_show_badge), stringResource(R.string.pw_show_badge_desc), cfg.showBadge) {
                onChange(cfg.copy(showBadge = it))
            }
            if (cfg.showBadge) {
                TextField(
                    value = cfg.badgeText,
                    onValueChange = { onChange(cfg.copy(badgeText = it.take(2))) },
                    label = { Text(stringResource(R.string.pw_badge_text)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = com.petal.browser.ui.containment.petalFilledTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Label(stringResource(R.string.pw_badge_shape))
                ShapePicker(cfg.badgeShape) { onChange(cfg.copy(badgeShape = it)) }
            }
        }
    }
    SectionCard(stringResource(R.string.pw_button_shape)) {
        ShapePicker(cfg.buttonShape) { onChange(cfg.copy(buttonShape = it)) }
    }
    if (cfg.kind.tileSlots > 0) {
        SectionCard(stringResource(R.string.pw_tile_shape)) {
            ShapePicker(cfg.tileShape) { onChange(cfg.copy(tileShape = it)) }
        }
    }
    SectionCard(stringResource(R.string.pw_hint_text)) {
        TextField(
            value = cfg.hint,
            onValueChange = { onChange(cfg.copy(hint = it.take(40))) },
            label = { Text(stringResource(R.string.pw_hint_default)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = com.petal.browser.ui.containment.petalFilledTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColorsTab(cfg: PetalWidgetConfig, onChange: (PetalWidgetConfig) -> Unit) {
    val glass = PetalGlassWidgets.isLiquidGlassEnabled(LocalContext.current)
    var picking by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var pickingInitial by remember { mutableStateOf(0) }

    SectionCard(stringResource(R.string.pw_color_mode)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = cfg.colorMode == PetalColorMode.APP_THEME,
                onClick = { onChange(cfg.copy(colorMode = PetalColorMode.APP_THEME)) },
                label = { Text(stringResource(R.string.pw_color_app)) }
            )
            FilterChip(
                selected = cfg.colorMode == PetalColorMode.CUSTOM,
                onClick = { onChange(cfg.copy(colorMode = PetalColorMode.CUSTOM)) },
                label = { Text(stringResource(R.string.pw_color_custom)) }
            )
        }
        if (cfg.colorMode == PetalColorMode.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            val hueColors = remember { List(7) { Color.hsv(it * 60f, 0.85f, 1f) } }
            GradientSliderRow(stringResource(R.string.pw_hue), "${cfg.hue.toInt()}°", cfg.hue, 0f..360f, hueColors) {
                onChange(cfg.copy(hue = it))
            }
            GradientSliderRow(
                stringResource(R.string.pw_saturation), "${cfg.saturation.toInt()}%", cfg.saturation, 0f..100f,
                listOf(Color.hsv(cfg.hue, 0f, 1f), Color.hsv(cfg.hue, 1f, 1f))
            ) { onChange(cfg.copy(saturation = it)) }
            GradientSliderRow(
                stringResource(R.string.pw_brightness), "${cfg.brightness.toInt()}%", cfg.brightness, 25f..100f,
                listOf(Color.hsv(cfg.hue, cfg.saturation / 100f, 0.25f), Color.hsv(cfg.hue, cfg.saturation / 100f, 1f))
            ) { onChange(cfg.copy(brightness = it)) }
        }
    }

    SectionCard(stringResource(R.string.pw_transparency)) {
        LabeledSlider(
            title = stringResource(R.string.pw_transparency),
            valueText = "${cfg.transparency}%",
            value = cfg.transparency.toFloat(),
            range = 0f..PETAL_MAX_TRANSPARENCY.toFloat(),
            steps = PETAL_MAX_TRANSPARENCY / 5 - 1,
            enabled = !glass
        ) { onChange(cfg.copy(transparency = ((it / 5f).toInt() * 5).coerceIn(0, PETAL_MAX_TRANSPARENCY))) }
        if (glass) {
            Text(
                stringResource(R.string.pw_transparency_glass),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    SectionCard(stringResource(R.string.pw_element_colors)) {
        SwitchRow(stringResource(R.string.pw_sync_colors), null, cfg.syncColors) { onChange(cfg.copy(syncColors = it)) }

        if (cfg.kind != PetalWidgetKind.PETAL2) {
            ColorRow(stringResource(R.string.pw_color_badge), cfg.badgeColor, onPick = { initial, apply ->
                pickingInitial = initial; picking = apply
            }) { onChange(cfg.copy(badgeColor = it)) }
        }
        if (cfg.syncColors) {
            ColorRow(stringResource(R.string.pw_color_all), cfg.syncColor, onPick = { initial, apply ->
                pickingInitial = initial; picking = apply
            }) { onChange(cfg.copy(syncColor = it)) }
        } else {
            for (i in 0 until cfg.kind.barSlots) {
                if (cfg.barAction(i).isEmpty) continue
                ColorRow(stringResource(R.string.pw_color_button, i + 1), cfg.barColors.getOrElse(i) { 0 }, onPick = { initial, apply ->
                    pickingInitial = initial; picking = apply
                }) { c -> onChange(cfg.copy(barColors = cfg.barColors.toMutableList().also { it[i] = c })) }
            }
            for (i in 0 until cfg.kind.tileSlots) {
                if (cfg.tile(i).isEmpty) continue
                ColorRow(stringResource(R.string.pw_color_tile, i + 1), cfg.tileColors.getOrElse(i) { 0 }, onPick = { initial, apply ->
                    pickingInitial = initial; picking = apply
                }) { c -> onChange(cfg.copy(tileColors = cfg.tileColors.toMutableList().also { it[i] = c })) }
            }
        }
    }

    picking?.let { apply ->
        ColorPickerDialog(
            initial = pickingInitial,
            onPick = { apply(it); picking = null },
            onDismiss = { picking = null }
        )
    }
}

@Composable
private fun ShortcutsTab(cfg: PetalWidgetConfig, onChange: (PetalWidgetConfig) -> Unit) {
    var picking by remember { mutableStateOf<((PetalWidgetAction) -> Unit)?>(null) }
    var pickingCurrent by remember { mutableStateOf(PetalWidgetAction.NONE) }

    SectionCard(stringResource(R.string.pw_bar_actions)) {
        for (i in 0 until cfg.kind.barSlots) {
            ActionSlotRow(stringResource(R.string.pw_slot, i + 1), cfg.barAction(i)) {
                pickingCurrent = cfg.barAction(i)
                picking = { action ->
                    val list = List(cfg.kind.barSlots) { idx -> if (idx == i) action else cfg.barAction(idx) }
                    onChange(cfg.copy(barActions = list))
                }
            }
        }
    }
    if (cfg.kind.tileSlots > 0) {
        SectionCard(stringResource(R.string.pw_tiles), stringResource(R.string.pw_tiles_hint)) {
            for (i in 0 until cfg.kind.tileSlots) {
                ActionSlotRow(stringResource(R.string.pw_slot, i + 1), cfg.tile(i)) {
                    pickingCurrent = cfg.tile(i)
                    picking = { action ->
                        val list = List(cfg.kind.tileSlots) { idx -> if (idx == i) action else cfg.tile(idx) }
                        onChange(cfg.copy(tiles = list))
                    }
                }
            }
        }
    }

    picking?.let { apply ->
        ActionPickerDialog(
            current = pickingCurrent,
            onPick = { apply(it); picking = null },
            onDismiss = { picking = null }
        )
    }
}

@Composable
private fun ShapeTab(cfg: PetalWidgetConfig, appWidgetId: Int, onChange: (PetalWidgetConfig) -> Unit) {
    val context = LocalContext.current
    SectionCard(stringResource(R.string.pw_corner_radius)) {
        val index = PETAL_RADIUS_STEPS.indexOf(cfg.cornerRadius).coerceAtLeast(0)
        LabeledSlider(
            title = stringResource(R.string.pw_corner_radius),
            valueText = if (cfg.cornerRadius >= PETAL_RADIUS_FULL) stringResource(R.string.pw_radius_full) else "${cfg.cornerRadius} dp",
            value = index.toFloat(),
            range = 0f..(PETAL_RADIUS_STEPS.size - 1).toFloat(),
            steps = PETAL_RADIUS_STEPS.size - 2
        ) { onChange(cfg.copy(cornerRadius = PETAL_RADIUS_STEPS[it.toInt().coerceIn(0, PETAL_RADIUS_STEPS.size - 1)])) }
    }
    SectionCard(stringResource(R.string.pw_outline)) {
        LabeledSlider(
            title = stringResource(R.string.pw_outline),
            valueText = if (cfg.outline == 0) stringResource(R.string.pw_outline_none) else "${cfg.outline} dp",
            value = cfg.outline.toFloat(),
            range = 0f..PETAL_MAX_OUTLINE.toFloat(),
            steps = PETAL_MAX_OUTLINE - 1
        ) { onChange(cfg.copy(outline = it.toInt().coerceIn(0, PETAL_MAX_OUTLINE))) }
    }
    if (PetalGlassWidgets.isLiquidGlassEnabled(context) && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        SectionCard(stringResource(R.string.pw_glass_align), stringResource(R.string.pw_glass_hint)) {
            FilledTonalButton(onClick = { context.startActivity(PetalWidgetGlassActivity.intent(context, appWidgetId)) }) {
                Text(stringResource(R.string.pw_glass_align))
            }
        }
    }
}

// ───────────────────────────── preview ─────────────────────────────

@Composable
private fun PreviewBackdrop(width: Float, height: Float, content: @Composable () -> Unit) {
    // A wallpaper-like gradient behind the widget, so Transparency is visible in the preview.
    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3B6EA8), Color(0xFF8E5BA6), Color(0xFFE08A6A))
                )
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun rememberPolygonShape(shape: PetalWidgetShape): Shape = remember(shape) { shape.polygon().toShape() }

/** A cookie (or any expressive shape) with an icon, as the widget bitmaps draw them. */
@Composable
private fun CookieIcon(shape: PetalWidgetShape, size: Int, tone: PetalTone, iconRes: Int, rotation: Float = 0f, iconScale: Float = 0.56f) {
    val composeShape = rememberPolygonShape(shape)
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().rotate(rotation).clip(composeShape).background(Color(tone.bg)))
        if (iconRes != 0) {
            Icon(painterResource(iconRes), contentDescription = null, tint = Color(tone.fg), modifier = Modifier.size((size * iconScale).dp))
        }
    }
}

@Composable
private fun PreviewBadge(cfg: PetalWidgetConfig, pal: PetalWidgetPalette, size: Int) {
    val composeShape = rememberPolygonShape(cfg.badgeShape)
    Box(modifier = Modifier.size(size.dp).clip(composeShape).background(Color(pal.badge.bg)), contentAlignment = Alignment.Center) {
        Text(
            cfg.badgeText.ifBlank { "P" },
            color = Color(pal.badge.fg),
            fontWeight = FontWeight.Bold,
            fontSize = (size * (if (cfg.badgeText.length > 1) 0.38f else 0.5f)).sp
        )
    }
}

@Composable
private fun PreviewHint(cfg: PetalWidgetConfig, color: Int) {
    Text(
        cfg.hint.trim().ifEmpty { stringResource(cfg.hintRes()) },
        color = Color(color),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun WidgetPreview(cfg: PetalWidgetConfig, pal: PetalWidgetPalette, widthDp: Float, heightDp: Float) {
    val plan = planLayout(cfg, widthDp, heightDp)
    val radius = if (plan.radius >= PETAL_RADIUS_FULL) minOf(widthDp, heightDp) / 2f else plan.radius.toFloat()
    val barShape = RoundedCornerShape(radius.dp)
    val fieldRadius = if (plan.fieldRadius >= PETAL_RADIUS_FULL) 999f else plan.fieldRadius.toFloat()
    val fieldShape = RoundedCornerShape(fieldRadius.dp)

    Box(
        modifier = Modifier
            .size(widthDp.dp, heightDp.dp)
            .clip(barShape)
            .background(Color(pal.bar))
            .then(if (cfg.outline > 0) Modifier.border(cfg.outline.dp, Color(pal.ring), barShape) else Modifier)
    ) {
        when (cfg.kind) {
            PetalWidgetKind.PETAL1 -> if (plan.expanded) {
                Column(Modifier.fillMaxSize().padding(plan.padding.dp), verticalArrangement = Arrangement.Center) {
                    Box(
                        Modifier.fillMaxWidth().height((plan.barHeight - 2 * plan.padding).dp).clip(fieldShape).background(Color(pal.field))
                    ) { PreviewSearchRow(cfg, pal, plan, pal.onFieldMuted, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        plan.tiles.forEach { slot ->
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                CookieIcon(cfg.tileShape, plan.tileCookie, pal.tileTones[slot.index.coerceIn(0, 3)], slot.action.iconRes, (slot.index * 20).toFloat(), 0.46f)
                                if (plan.showTileLabels) {
                                    Text(stringResource(slot.action.labelRes), color = Color(pal.onBar), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            } else {
                PreviewSearchRow(cfg, pal, plan, pal.onBarMuted, Modifier.fillMaxSize().padding(plan.padding.dp))
            }

            PetalWidgetKind.PETAL2 -> Row(Modifier.fillMaxSize().padding(plan.padding.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(fieldShape).background(Color(pal.field)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.widget_ic_search), null, tint = Color(pal.primary), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    PreviewHint(cfg, pal.onFieldMuted)
                }
                plan.buttons.forEach { slot ->
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(plan.buttonTouch.dp), contentAlignment = Alignment.Center) {
                        CookieIcon(cfg.buttonShape, plan.buttonSize, pal.barTones[slot.index.coerceIn(0, 3)], slot.action.iconRes, (slot.index * 15).toFloat())
                    }
                }
            }

            PetalWidgetKind.PETAL3 -> Row(
                Modifier.fillMaxSize().padding(start = plan.padding.dp, end = (plan.padding + 4).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plan.showBadge) PreviewBadge(cfg, pal, plan.badgeSize)
                Box(Modifier.weight(1f).padding(start = if (plan.showBadge) 14.dp else 16.dp)) { PreviewHint(cfg, pal.onBarMuted) }
                plan.buttons.forEach { slot ->
                    Box(Modifier.size(plan.buttonTouch.dp), contentAlignment = Alignment.Center) {
                        CookieIcon(cfg.buttonShape, plan.buttonSize, pal.barTones[slot.index.coerceIn(0, 3)], slot.action.iconRes, (slot.index * 15).toFloat())
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSearchRow(cfg: PetalWidgetConfig, pal: PetalWidgetPalette, plan: PetalLayoutPlan, hintColor: Int, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (plan.showBadge) PreviewBadge(cfg, pal, plan.badgeSize)
        Box(Modifier.weight(1f).padding(start = if (plan.showBadge) 10.dp else 14.dp)) { PreviewHint(cfg, hintColor) }
        plan.buttons.forEach { slot ->
            Box(Modifier.size(plan.buttonTouch.dp), contentAlignment = Alignment.Center) {
                CookieIcon(cfg.buttonShape, plan.buttonSize, pal.barTones[slot.index.coerceIn(0, 3)], slot.action.iconRes, (slot.index * 15).toFloat())
            }
        }
    }
}

// ───────────────────────────── controls ─────────────────────────────

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        PetalSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        PetalSlider(value = value, onValueChange = onChange, valueRange = range, steps = steps.coerceAtLeast(0), enabled = enabled)
    }
}

/** A slider whose track shows the colors it picks between (hue spectrum, saturation ramp...). */
@Composable
private fun GradientSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: List<Color>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Brush.horizontalGradient(colors))
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ShapePicker(selected: PetalWidgetShape, onSelect: (PetalWidgetShape) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PetalWidgetShape.entries.forEach { shape ->
            val isSelected = shape == selected
            val composeShape = rememberPolygonShape(shape)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .then(if (isSelected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(18.dp)) else Modifier)
                    .clickable { onSelect(shape) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(38.dp).clip(composeShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
    Text(selected.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val SWATCHES = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00897B,
    0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF6D4C41, 0xFF546E7A, 0xFFFFFFFF, 0xFF1B1B1F
).map { it.toInt() }

/** A row of color swatches: Theme (0), presets, and a "+" that opens the full picker. */
@Composable
private fun ColorRow(
    title: String,
    selected: Int,
    onPick: (initial: Int, apply: (Int) -> Unit) -> Unit,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(title)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Swatch(color = null, selected = selected == 0, label = stringResource(R.string.pw_color_theme)) { onSelect(0) }
            SWATCHES.forEach { c -> Swatch(color = c, selected = selected == c, label = null) { onSelect(c) } }
            val isCustom = selected != 0 && selected !in SWATCHES
            Swatch(color = if (isCustom) selected else null, selected = isCustom, label = null, plus = !isCustom) {
                onPick(if (selected != 0) selected else SWATCHES[5], onSelect)
            }
        }
    }
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, label: String?, plus: Boolean = false, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected && color != null -> Icon(
                Icons.Rounded.Check, null,
                tint = if (androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
            label != null -> Text(label.take(1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            plus -> Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActionSlotRow(label: String, action: PetalWidgetAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (action.iconRes != 0) {
                Icon(painterResource(action.iconRes), null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(action.labelRes), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ActionPickerDialog(current: PetalWidgetAction, onPick: (PetalWidgetAction) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pw_cancel)) } },
        title = { Text(stringResource(R.string.pw_pick_action)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PetalWidgetAction.pickable.forEach { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(action) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = action == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        if (action.iconRes != 0) {
                            Icon(painterResource(action.iconRes), null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(stringResource(action.labelRes))
                    }
                }
            }
        }
    )
}

@Composable
private fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember(initial) { mutableStateOf(hsv[0]) }
    var sat by remember(initial) { mutableStateOf(hsv[1] * 100f) }
    var value by remember(initial) { mutableStateOf(hsv[2] * 100f) }
    val color = Color.hsv(hue, sat / 100f, value / 100f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pw_pick_color)) },
        confirmButton = {
            TextButton(onClick = { onPick(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat / 100f, value / 100f))) }) {
                Text(stringResource(R.string.pw_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pw_cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(color))
                GradientSliderRow(
                    stringResource(R.string.pw_hue), "${hue.toInt()}°", hue, 0f..360f,
                    List(7) { Color.hsv(it * 60f, 0.85f, 1f) }
                ) { hue = it }
                GradientSliderRow(
                    stringResource(R.string.pw_saturation), "${sat.toInt()}%", sat, 0f..100f,
                    listOf(Color.hsv(hue, 0f, value / 100f), Color.hsv(hue, 1f, value / 100f))
                ) { sat = it }
                GradientSliderRow(
                    stringResource(R.string.pw_brightness), "${value.toInt()}%", value, 0f..100f,
                    listOf(Color.hsv(hue, sat / 100f, 0f), Color.hsv(hue, sat / 100f, 1f))
                ) { value = it }
            }
        }
    )
}
