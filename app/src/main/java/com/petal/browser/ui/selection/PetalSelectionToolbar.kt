package com.petal.browser.ui.selection

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.preference.PreferenceManager
import com.petal.browser.ui.components.ExpressiveButtonGroup
import com.petal.browser.ui.components.ExpressiveSegmentItem
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.PetalGlassContainer
import com.petal.browser.ui.glass.petalGlassPanel
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Petal's own text-selection toolbar, used by every Compose text field in the app (through
 * [LocalTextToolbar]) and by text selected on web pages ([PetalPageSelectionToolbar]).
 *
 * Two looks, picked in Settings → Appearance → Text selection toolbar:
 *  - Material 3 Expressive: a tonal pill with springy, shape-morphing buttons.
 *  - Liquid glass: the same row, but frosted + refracted live from whatever is behind it.
 */
object PetalSelectionToolbarPrefs {
    const val KEY_ENABLED = "sp_selection_toolbar_enabled"
    const val KEY_STYLE = "sp_selection_toolbar_style"

    const val STYLE_AUTO = "AUTO"
    const val STYLE_EXPRESSIVE = "EXPRESSIVE"
    const val STYLE_GLASS = "GLASS"

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_ENABLED, true)
}

enum class PetalSelectionToolbarStyle { EXPRESSIVE, LIQUID_GLASS }

class PetalSelectionAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun <T> rememberPref(vararg keys: String, read: (SharedPreferences) -> T): T {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var value by remember(sp) { mutableStateOf(read(sp)) }
    DisposableEffect(sp) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || keys.contains(key)) value = read(sp)
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

@Composable
internal fun rememberSelectionToolbarEnabled(): Boolean =
    rememberPref(PetalSelectionToolbarPrefs.KEY_ENABLED) {
        it.getBoolean(PetalSelectionToolbarPrefs.KEY_ENABLED, true)
    }

/** AUTO follows the app: liquid glass when Liquid Glass UI is on, Material 3 Expressive otherwise. */
@Composable
internal fun rememberSelectionToolbarStyle(): PetalSelectionToolbarStyle {
    val pref = rememberPref(PetalSelectionToolbarPrefs.KEY_STYLE) {
        it.getString(PetalSelectionToolbarPrefs.KEY_STYLE, PetalSelectionToolbarPrefs.STYLE_AUTO)
            ?: PetalSelectionToolbarPrefs.STYLE_AUTO
    }
    val appGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    return when (pref) {
        PetalSelectionToolbarPrefs.STYLE_GLASS -> PetalSelectionToolbarStyle.LIQUID_GLASS
        PetalSelectionToolbarPrefs.STYLE_EXPRESSIVE -> PetalSelectionToolbarStyle.EXPRESSIVE
        else -> if (appGlass) PetalSelectionToolbarStyle.LIQUID_GLASS else PetalSelectionToolbarStyle.EXPRESSIVE
    }
}

// ---------------------------------------------------------------------------------------------
// Compose TextToolbar (text fields + SelectionContainer, everywhere in the app)
// ---------------------------------------------------------------------------------------------

internal class PetalTextToolbarRequest(
    val rect: Rect,
    val onCopy: (() -> Unit)?,
    val onPaste: (() -> Unit)?,
    val onCut: (() -> Unit)?,
    val onSelectAll: (() -> Unit)?
)

class PetalTextToolbar : TextToolbar {
    internal var request by mutableStateOf<PetalTextToolbarRequest?>(null)

    override val status: TextToolbarStatus
        get() = if (request == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        if (onCopyRequested == null && onPasteRequested == null &&
            onCutRequested == null && onSelectAllRequested == null
        ) {
            request = null
            return
        }
        request = PetalTextToolbarRequest(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() {
        request = null
    }
}

/**
 * Installs [PetalTextToolbar] for everything inside [content]. Called once from
 * `PetalExpressiveTheme`, so every screen, sheet and dialog gets it.
 */
@Composable
fun PetalTextToolbarProvider(content: @Composable () -> Unit) {
    val enabled = rememberSelectionToolbarEnabled()
    if (!enabled) {
        content()
        return
    }
    val toolbar = remember { PetalTextToolbar() }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
        PetalTextToolbarHost(toolbar)
    }
}

@Composable
private fun PetalTextToolbarHost(toolbar: PetalTextToolbar) {
    val req = toolbar.request ?: return
    val view = LocalView.current
    val density = LocalDensity.current
    val style = rememberSelectionToolbarStyle()
    val statusTop = WindowInsets.statusBars.getTop(density)
    val margin = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 10.dp.roundToPx() }
    val below = with(density) { 30.dp.roundToPx() }
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp - 16.dp

    val cut = stringResource(android.R.string.cut)
    val copy = stringResource(android.R.string.copy)
    val paste = stringResource(android.R.string.paste)
    val selectAll = stringResource(android.R.string.selectAll)

    val actions = remember(req, cut, copy, paste, selectAll) {
        buildList {
            req.onCut?.let { add(PetalSelectionAction("cut", cut, Icons.Rounded.ContentCut, it)) }
            req.onCopy?.let { add(PetalSelectionAction("copy", copy, Icons.Rounded.ContentCopy, it)) }
            req.onPaste?.let { add(PetalSelectionAction("paste", paste, Icons.Rounded.ContentPaste, it)) }
            req.onSelectAll?.let { add(PetalSelectionAction("select_all", selectAll, Icons.Rounded.SelectAll, it)) }
        }
    }

    val provider = remember(req.rect, statusTop, margin, gap, below, view) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // The rect is in this view's coordinates; popups are placed in window coordinates.
                val loc = IntArray(2)
                view.getLocationInWindow(loc)
                return placeSelectionToolbar(
                    left = loc[0] + req.rect.left,
                    top = loc[1] + req.rect.top,
                    right = loc[0] + req.rect.right,
                    bottom = loc[1] + req.rect.bottom,
                    content = popupContentSize,
                    window = windowSize,
                    topSafe = statusTop,
                    margin = margin,
                    gap = gap,
                    below = below
                )
            }
        }
    }

    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        PetalSelectionToolbar(
            actions = actions,
            style = style,
            modifier = Modifier.widthIn(max = maxWidth)
        )
    }
}

/** Above the selection when there is room, otherwise below it (clear of the selection handles). */
internal fun placeSelectionToolbar(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    content: IntSize,
    window: IntSize,
    topSafe: Int,
    margin: Int,
    gap: Int,
    below: Int
): IntOffset {
    val centerX = (left + right) / 2f
    val x = (centerX - content.width / 2f).roundToInt()
        .coerceIn(margin, max(margin, window.width - content.width - margin))
    val minY = topSafe + margin
    var y = (top - content.height - gap).roundToInt()
    if (y < minY) y = (bottom + below).roundToInt()
    y = y.coerceIn(minY, max(minY, window.height - content.height - margin))
    return IntOffset(x, y)
}

// ---------------------------------------------------------------------------------------------
// The toolbar itself
// ---------------------------------------------------------------------------------------------

@Composable
fun PetalSelectionToolbar(
    actions: List<PetalSelectionAction>,
    style: PetalSelectionToolbarStyle,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
    }
    val shape = RoundedCornerShape(28.dp)
    val animated = modifier.graphicsLayer {
        val s = 0.80f + 0.20f * enter.value
        scaleX = s
        scaleY = s
        alpha = enter.value.coerceIn(0f, 1f)
    }
    when (style) {
        PetalSelectionToolbarStyle.EXPRESSIVE -> ExpressiveToolbar(actions, shape, animated)
        PetalSelectionToolbarStyle.LIQUID_GLASS -> GlassToolbar(actions, shape, animated)
    }
}

@Composable
private fun ExpressiveToolbar(actions: List<PetalSelectionAction>, shape: Shape, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = shape,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        ActionsRow(
            actions = actions,
            contentColor = scheme.onSurface,
            pressedContainer = scheme.primaryContainer,
            pressedContent = scheme.onPrimaryContainer
        )
    }
}

@Composable
private fun GlassToolbar(actions: List<PetalSelectionAction>, shape: Shape, modifier: Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val opacity = sp.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.10f, 0.95f)
    val sheen = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f)
    val baseAlpha = if (dark) 0.14f + opacity * 0.40f else 0.18f + opacity * 0.50f
    val tint = scheme.surfaceContainerHigh.copy(alpha = baseAlpha.coerceIn(0f, 0.96f))
    val edge = (sheen / 0.60f).coerceIn(0f, 2.5f)

    // Live: the real window behind the toolbar is re-recorded every frame, then frosted and
    // refracted at the rim, so the glass follows scrolling, video and page changes.
    PetalGlassContainer(modifier = modifier, live = true) {
        val wallpaper = LocalPetalGlassWallpaper.current
        val sampled = wallpaper != null && wallpaper.ready && wallpaper.hasRecorded
        Row(
            modifier = Modifier
                .then(
                    // Until the first frame is sampled (and on Android < 12) keep it legible.
                    if (sampled) Modifier else Modifier.clip(shape).background(tint.copy(alpha = 0.72f))
                )
                .petalGlassPanel(shape, tint, edge)
        ) {
            ActionsRow(
                actions = actions,
                contentColor = scheme.onSurface,
                pressedContainer = Color.White.copy(alpha = if (dark) 0.16f else 0.34f),
                pressedContent = scheme.onSurface
            )
        }
    }
}

@Composable
private fun ActionsRow(
    actions: List<PetalSelectionAction>,
    contentColor: Color,
    pressedContainer: Color,
    pressedContent: Color
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            ActionButton(action, contentColor, pressedContainer, pressedContent)
        }
    }
}

@Composable
private fun RowScope.ActionButton(
    action: PetalSelectionAction,
    contentColor: Color,
    pressedContainer: Color,
    pressedContent: Color
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    // Expressive shape morph: a pressed button squares off, then springs back to a pill.
    val corner: Dp by animateDpAsState(
        targetValue = if (pressed) 10.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "selActionCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "selActionScale"
    )
    val tint = if (pressed) pressedContent else contentColor
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(if (pressed) pressedContainer else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                action.onClick()
            }
            .defaultMinSize(minWidth = 60.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = action.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

/** Appearance → "Text selection toolbar". Reads and writes SharedPreferences directly. */
@Composable
fun PetalSelectionToolbarSettingsSection(targetHighlightId: String? = null) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var enabled by remember { mutableStateOf(sp.getBoolean(PetalSelectionToolbarPrefs.KEY_ENABLED, true)) }
    var style by remember {
        mutableStateOf(
            sp.getString(PetalSelectionToolbarPrefs.KEY_STYLE, PetalSelectionToolbarPrefs.STYLE_AUTO)
                ?: PetalSelectionToolbarPrefs.STYLE_AUTO
        )
    }
    PetalSettingsSection(
        title = "Text selection toolbar",
        icon = Icons.Rounded.TextFields,
        cardId = "appearance_selection_toolbar",
        targetHighlightId = targetHighlightId
    ) {
        Text(
            text = "The Cut / Copy / Paste row that appears when you select text, in address bar, settings, dialogs and on web pages.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PetalSettingsToggleRow(
            title = "Custom selection toolbar",
            subtitle = "Replace the system toolbar with Petal's own",
            icon = Icons.Rounded.AutoAwesome,
            checked = enabled,
            position = PetalGroupPosition.SINGLE,
            onCheckedChange = {
                enabled = it
                sp.edit().putBoolean(PetalSelectionToolbarPrefs.KEY_ENABLED, it).apply()
            }
        )
        if (enabled) {
            Text(
                text = "Style",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            ExpressiveButtonGroup(
                items = listOf(
                    ExpressiveSegmentItem(id = PetalSelectionToolbarPrefs.STYLE_AUTO, label = "Auto", icon = Icons.Rounded.AutoAwesome),
                    ExpressiveSegmentItem(id = PetalSelectionToolbarPrefs.STYLE_EXPRESSIVE, label = "Material 3", icon = Icons.Rounded.Palette),
                    ExpressiveSegmentItem(id = PetalSelectionToolbarPrefs.STYLE_GLASS, label = "Liquid glass", icon = Icons.Rounded.BlurOn)
                ),
                selectedId = style,
                onItemSelected = {
                    style = it
                    sp.edit().putString(PetalSelectionToolbarPrefs.KEY_STYLE, it).apply()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Auto uses liquid glass when Liquid Glass UI is on, Material 3 Expressive otherwise.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
