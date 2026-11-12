package com.petal.browser.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.preference.PreferenceManager
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeRainbow
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.TonalPalette

/** Palette id stored in "sp_palette_id" when the user picks their own colors. */
const val CUSTOM_PALETTE_ID = "custom"

/**
 * User-picked palette, in the spirit of ColorBlendr: one seed colour (plus optional secondary and
 * tertiary overrides) is run through the real Material colour algorithm (HCT + tonal palettes),
 * and the harmony style decides how the other roles are derived.
 *
 * Unlike the preset palettes, the style is NOT an HSV tweak here. Tonal Spot, Vibrant, Expressive,
 * Fruit Salad, Neutral, Monochrome, Content, Fidelity and Rainbow each build their own palettes.
 */
object PetalCustomPalette {

    const val KEY_SEED = "sp_custom_seed"
    const val KEY_SECONDARY = "sp_custom_secondary"
    const val KEY_TERTIARY = "sp_custom_tertiary"

    val DEFAULT_SEED: Int = 0xFFD81B60.toInt()

    /** A secondary / tertiary value of 0 means "derive it from the seed". */
    data class Spec(val seed: Int, val secondary: Int = 0, val tertiary: Int = 0)

    @Volatile private var cachedSpec = Spec(DEFAULT_SEED)
    @Volatile private var cachedStyle = ColorStyle.TONAL_SPOT

    private val cache = object : LinkedHashMap<String, ColorScheme>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ColorScheme>?) = size > 24
    }

    fun read(sp: SharedPreferences): Spec = Spec(
        seed = sp.getInt(KEY_SEED, DEFAULT_SEED),
        secondary = sp.getInt(KEY_SECONDARY, 0),
        tertiary = sp.getInt(KEY_TERTIARY, 0)
    )

    fun spec(context: Context): Spec = read(PreferenceManager.getDefaultSharedPreferences(context))

    /** Keeps the cached spec fresh so non-Compose callers (widgets, paletteById) see new colours. */
    fun install(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        cachedSpec = read(sp)
        cachedStyle = ColorStyle.fromStorageKey(sp.getString("sp_color_style", null))
        sp.registerOnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                KEY_SEED, KEY_SECONDARY, KEY_TERTIARY -> cachedSpec = read(prefs)
                "sp_color_style" -> cachedStyle = ColorStyle.fromStorageKey(prefs.getString(key, null))
            }
        }
    }

    /** The palette handed out by paletteById(CUSTOM_PALETTE_ID). */
    fun currentPalette(): PetalPalette = palette(cachedSpec, cachedStyle)

    fun palette(spec: Spec, style: ColorStyle): PetalPalette = PetalPalette(
        id = CUSTOM_PALETTE_ID,
        label = "Custom",
        seed = Color(spec.seed),
        light = scheme(spec, style, dark = false),
        dark = scheme(spec, style, dark = true)
    )

    /** Builds (and memoises) a full Compose ColorScheme. Falls back to a plain scheme on any failure. */
    fun scheme(spec: Spec, style: ColorStyle, dark: Boolean, contrast: Double = 0.0): ColorScheme {
        val key = "${spec.seed}|${spec.secondary}|${spec.tertiary}|${style.name}|$dark|$contrast"
        synchronized(cache) { cache[key]?.let { return it } }
        val built = try {
            build(spec, style, dark, contrast)
        } catch (t: Throwable) {
            if (dark) darkColorScheme(primary = Color(spec.seed)) else lightColorScheme(primary = Color(spec.seed))
        }
        synchronized(cache) { cache[key] = built }
        return built
    }

    @SuppressLint("RestrictedApi")
    private fun generate(seed: Int, style: ColorStyle, dark: Boolean, contrast: Double): DynamicScheme {
        val hct = Hct.fromInt(seed)
        return when (style) {
            ColorStyle.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrast)
            ColorStyle.VIBRANT -> SchemeVibrant(hct, dark, contrast)
            ColorStyle.EXPRESSIVE -> SchemeExpressive(hct, dark, contrast)
            ColorStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, contrast)
            ColorStyle.NEUTRAL -> SchemeNeutral(hct, dark, contrast)
            ColorStyle.MONOCHROME -> SchemeMonochrome(hct, dark, contrast)
            ColorStyle.CONTENT -> SchemeContent(hct, dark, contrast)
            ColorStyle.FIDELITY -> SchemeFidelity(hct, dark, contrast)
            ColorStyle.RAINBOW -> SchemeRainbow(hct, dark, contrast)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun build(spec: Spec, style: ColorStyle, dark: Boolean, contrast: Double): ColorScheme {
        val base = generate(spec.seed, style, dark, contrast)
        // ColorBlendr-style overrides: a picked secondary/tertiary colour supplies its own accent palette.
        val secondary: TonalPalette = if (spec.secondary != 0) generate(spec.secondary, style, dark, contrast).primaryPalette else base.secondaryPalette
        val tertiary: TonalPalette = if (spec.tertiary != 0) generate(spec.tertiary, style, dark, contrast).primaryPalette else base.tertiaryPalette
        val primary: TonalPalette = base.primaryPalette
        val neutral: TonalPalette = base.neutralPalette
        val neutralVariant: TonalPalette = base.neutralVariantPalette
        val error: TonalPalette = base.errorPalette

        fun TonalPalette.c(light: Int, darkTone: Int) = Color(tone(if (dark) darkTone else light))

        // Standard Material 3 tone map for each role.
        val surface = neutral.c(98, 6)
        return if (dark) {
            darkColorScheme(
                primary = primary.c(40, 80), onPrimary = primary.c(100, 20),
                primaryContainer = primary.c(90, 30), onPrimaryContainer = primary.c(10, 90),
                inversePrimary = primary.c(80, 40),
                secondary = secondary.c(40, 80), onSecondary = secondary.c(100, 20),
                secondaryContainer = secondary.c(90, 30), onSecondaryContainer = secondary.c(10, 90),
                tertiary = tertiary.c(40, 80), onTertiary = tertiary.c(100, 20),
                tertiaryContainer = tertiary.c(90, 30), onTertiaryContainer = tertiary.c(10, 90),
                background = surface, onBackground = neutral.c(10, 90),
                surface = surface, onSurface = neutral.c(10, 90),
                surfaceVariant = neutralVariant.c(90, 30), onSurfaceVariant = neutralVariant.c(30, 80),
                surfaceTint = primary.c(40, 80),
                inverseSurface = neutral.c(20, 90), inverseOnSurface = neutral.c(95, 20),
                error = error.c(40, 80), onError = error.c(100, 20),
                errorContainer = error.c(90, 30), onErrorContainer = error.c(10, 90),
                outline = neutralVariant.c(50, 60), outlineVariant = neutralVariant.c(80, 30),
                scrim = Color.Black,
                surfaceBright = neutral.c(98, 24), surfaceDim = neutral.c(87, 6),
                surfaceContainerLowest = neutral.c(100, 4), surfaceContainerLow = neutral.c(96, 10),
                surfaceContainer = neutral.c(94, 12), surfaceContainerHigh = neutral.c(92, 17),
                surfaceContainerHighest = neutral.c(90, 22)
            )
        } else {
            lightColorScheme(
                primary = primary.c(40, 80), onPrimary = primary.c(100, 20),
                primaryContainer = primary.c(90, 30), onPrimaryContainer = primary.c(10, 90),
                inversePrimary = primary.c(80, 40),
                secondary = secondary.c(40, 80), onSecondary = secondary.c(100, 20),
                secondaryContainer = secondary.c(90, 30), onSecondaryContainer = secondary.c(10, 90),
                tertiary = tertiary.c(40, 80), onTertiary = tertiary.c(100, 20),
                tertiaryContainer = tertiary.c(90, 30), onTertiaryContainer = tertiary.c(10, 90),
                background = surface, onBackground = neutral.c(10, 90),
                surface = surface, onSurface = neutral.c(10, 90),
                surfaceVariant = neutralVariant.c(90, 30), onSurfaceVariant = neutralVariant.c(30, 80),
                surfaceTint = primary.c(40, 80),
                inverseSurface = neutral.c(20, 90), inverseOnSurface = neutral.c(95, 20),
                error = error.c(40, 80), onError = error.c(100, 20),
                errorContainer = error.c(90, 30), onErrorContainer = error.c(10, 90),
                outline = neutralVariant.c(50, 60), outlineVariant = neutralVariant.c(80, 30),
                scrim = Color.Black,
                surfaceBright = neutral.c(98, 24), surfaceDim = neutral.c(87, 6),
                surfaceContainerLowest = neutral.c(100, 4), surfaceContainerLow = neutral.c(96, 10),
                surfaceContainer = neutral.c(94, 12), surfaceContainerHigh = neutral.c(92, 17),
                surfaceContainerHighest = neutral.c(90, 22)
            )
        }
    }
}

/**
 * Bumps whenever a theme-related preference changes. PetalExpressiveTheme reads it while it
 * evaluates its pref-backed defaults, so every theme in the app recomposes live, with no restart.
 */
object PetalThemePrefs {
    private var installed = false
    var revision by mutableIntStateOf(0)
        private set

    private val keys = setOf(
        "sp_theme_config", "useDynamicColor", "sp_amoled", "sp_expressive_colors",
        "sp_color_style", "sp_palette_id", "sp_palette_manually_selected",
        PetalCustomPalette.KEY_SEED, PetalCustomPalette.KEY_SECONDARY, PetalCustomPalette.KEY_TERTIARY,
        PetalPaletteMix.KEY_ENABLED, PetalPaletteMix.KEY_SECONDARY, PetalPaletteMix.KEY_TERTIARY,
        "sp_app_font", "sp_font_width", "sp_font_weight", "sp_font_roundness", "sp_custom_font_path",
        "sp_blur_effect_enabled", "sp_expressive_shapes_35_enabled", "sp_corner_radius",
        "sp_high_refresh_rate", "sp_touch_haptics"
    )

    fun install(context: Context) {
        if (installed) return
        installed = true
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .registerOnSharedPreferenceChangeListener { _, key -> if (key == null || key in keys) revision++ }
    }

    /** Synchronously bumps theme revision to trigger instantaneous recomposition in Compose. */
    fun bump() {
        revision++
    }
}
