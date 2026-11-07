package com.petal.browser.widget.glance

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalCustomPalette
import com.petal.browser.ui.theme.applyAmoled
import com.petal.browser.ui.theme.applyStyle
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.paletteById
import kotlin.math.roundToInt

/** A background and the color its content is drawn in. */
internal class PetalTone(val bg: Int, val fg: Int)

/**
 * Every color a Petal widget draws, resolved from the app theme (or the widget's own Custom colors) and the
 * widget's settings. Plain ARGB ints so the Glance widgets and the Compose preview in the settings screen draw
 * from exactly the same numbers.
 */
internal class PetalWidgetPalette(
    val isDark: Boolean,
    /** The bar's fill, with the Transparency setting already applied. */
    val bar: Int,
    val ring: Int,
    /** The nested search field's fill; always visibly different from [bar]. */
    val field: Int,
    val onBar: Int,
    val onBarMuted: Int,
    val onField: Int,
    val onFieldMuted: Int,
    val primary: Int,
    val badge: PetalTone,
    val barTones: List<PetalTone>,
    val tileTones: List<PetalTone>
)

internal fun isWidgetDark(context: Context): Boolean {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    return when (sp.getString("sp_theme_config", "FOLLOW_SYSTEM")) {
        "DARK" -> true
        "LIGHT" -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * The Material color scheme a widget draws from. Never throws: a bad pref, palette id or dynamic-color failure
 * must not leave the launcher showing "Can't load widget".
 */
internal fun resolveWidgetScheme(context: Context, cfg: PetalWidgetConfig): ColorScheme =
    try {
        buildWidgetScheme(context, cfg)
    } catch (t: Throwable) {
        android.util.Log.e("PetalWidget", "Falling back to default widget colors", t)
        if (isWidgetDark(context)) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    }

private fun buildWidgetScheme(context: Context, cfg: PetalWidgetConfig): ColorScheme {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val isDark = isWidgetDark(context)
    val isAmoled = sp.getBoolean("sp_amoled", false)
    val colorStyle = ColorStyle.fromStorageKey(sp.getString("sp_color_style", "tonal_spot"))
    val expressiveColors = sp.getBoolean("sp_expressive_colors", false)

    var scheme: ColorScheme
    if (cfg.colorMode == PetalColorMode.CUSTOM) {
        // The widget's own seed, run through the same HCT algorithm the app's custom palette uses.
        scheme = PetalCustomPalette.scheme(PetalCustomPalette.Spec(cfg.seedColor), colorStyle, isDark)
    } else {
        val paletteId = sp.getString("sp_palette_id", "petal") ?: "petal"
        val useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
        scheme = if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            paletteById(paletteId).let { if (isDark) it.dark else it.light }
        }
        scheme = scheme.applyStyle(colorStyle)
        if (expressiveColors) {
            scheme = if (isDark) {
                if (isAmoled) {
                    scheme.copy(
                        surface = Color.Black,
                        surfaceContainer = Color(0xFF181818),
                        surfaceContainerHigh = Color(0xFF1E1E1E),
                        surfaceContainerHighest = Color(0xFF262626)
                    )
                } else {
                    scheme.copy(surfaceContainer = scheme.surfaceContainerHigh)
                }
            } else {
                scheme.copy(surfaceContainer = Color.White)
            }
        } else if (isDark && isAmoled) {
            scheme = scheme.applyAmoled()
        }
        return scheme
    }
    if (isDark && isAmoled) scheme = scheme.applyAmoled()
    return scheme
}

private fun withAlpha(color: Int, alpha: Float): Int =
    ColorUtils.setAlphaComponent(color, (alpha * 255f).roundToInt().coerceIn(0, 255))

private fun opaque(color: Int): Int = color or (0xFF shl 24)

/** [preferred] if it reads on [bg] (WCAG AA, 4.5:1), otherwise whichever of black or white does. */
private fun readable(preferred: Int, bg: Int): Int {
    val solid = opaque(bg)
    if (ColorUtils.calculateContrast(opaque(preferred), solid) >= 4.5) return opaque(preferred)
    val white = ColorUtils.calculateContrast(0xFFFFFFFF.toInt(), solid)
    val black = ColorUtils.calculateContrast(0xFF000000.toInt(), solid)
    return if (white >= black) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
}

private fun toneOf(bg: Int, preferredFg: Int): PetalTone {
    val solid = opaque(bg)
    return PetalTone(solid, readable(preferredFg, solid))
}

/** A user-picked color as a tone; the text on it is whichever of dark or light reads. */
private fun customTone(color: Int): PetalTone = toneOf(color, if (ColorUtils.calculateLuminance(opaque(color)) > 0.5) 0xFF1B1B1F.toInt() else 0xFFFFFFFF.toInt())

internal fun buildPalette(context: Context, cfg: PetalWidgetConfig): PetalWidgetPalette {
    val scheme = resolveWidgetScheme(context, cfg)
    val isDark = isWidgetDark(context)
    val glass = PetalGlassWidgets.isLiquidGlassEnabled(context)

    val barSolid = opaque(scheme.surfaceContainer.toArgb())
    val onSurface = scheme.onSurface.toArgb()
    val onSurfaceVariant = scheme.onSurfaceVariant.toArgb()

    // The bar itself: the Transparency slider, unless liquid glass is on (then the glass tile is the bar).
    val barAlpha = if (glass) 1f else 1f - cfg.transparency / 100f
    val bar = withAlpha(barSolid, barAlpha)

    // A field/pill tone derived from the bar rather than read from the scheme: with "expressive colors" every
    // surface container is the same white, which made the nested search field vanish into the bar.
    val fieldSolid = ColorUtils.blendARGB(barSolid, onSurface, if (isDark) 0.11f else 0.07f)
    val field = if (glass) (if (isDark) 0x3D000000 else 0x59FFFFFF) else withAlpha(fieldSolid, barAlpha.coerceAtLeast(0.55f))

    // The text must stay legible however see-through the bar is, so it is checked against the opaque tones and
    // pulled to the stronger on-surface color once the bar goes translucent.
    val translucent = barAlpha < 0.7f && !glass
    val onBar = readable(onSurface, barSolid)
    val onBarMuted = readable(if (translucent) onSurface else onSurfaceVariant, barSolid)
    val onField = readable(onSurface, fieldSolid)
    val onFieldMuted = readable(if (translucent) onSurface else onSurfaceVariant, fieldSolid)

    val defaultBar = listOf(
        toneOf(scheme.primaryContainer.toArgb(), scheme.onPrimaryContainer.toArgb()),
        toneOf(scheme.secondaryContainer.toArgb(), scheme.onSecondaryContainer.toArgb()),
        toneOf(scheme.tertiaryContainer.toArgb(), scheme.onTertiaryContainer.toArgb()),
        toneOf(ColorUtils.blendARGB(barSolid, onSurface, if (isDark) 0.18f else 0.12f), onSurface)
    )

    fun tones(colors: (Int) -> Int): List<PetalTone> =
        List(4) { i -> colors(i).takeIf { it != 0 }?.let { customTone(it) } ?: defaultBar[i] }

    val badge = cfg.badgeColor.takeIf { it != 0 }?.let { customTone(it) }
        ?: toneOf(scheme.primary.toArgb(), scheme.onPrimary.toArgb())

    return PetalWidgetPalette(
        isDark = isDark,
        bar = bar,
        ring = withAlpha(readable(scheme.outline.toArgb(), barSolid), 0.55f.coerceAtLeast(barAlpha)),
        field = field,
        onBar = onBar,
        onBarMuted = onBarMuted,
        onField = onField,
        onFieldMuted = onFieldMuted,
        primary = readable(scheme.primary.toArgb(), fieldSolid),
        badge = badge,
        barTones = tones { cfg.barColor(it) },
        tileTones = tones { cfg.tileColor(it) }
    )
}
