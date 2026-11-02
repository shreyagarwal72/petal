package com.petal.browser.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.preference.PreferenceManager

/**
 * Palette mixing: primary, secondary and tertiary can each come from a different source at the
 * same time.
 *
 *   primary   -> whatever palette is selected today (preset, Material You, or custom)
 *   secondary -> Auto | Material You | a preset palette | your own colour
 *   tertiary  -> Auto | Material You | a preset palette | your own colour
 *
 * A source supplies its PRIMARY colours (colour, on-colour, container, on-container) to the role it
 * is assigned to, so "secondary = Forest" really looks like Forest's green rather than its muted
 * secondary. Applied after the harmony style and before AMOLED, so those still work on top.
 */
object PetalPaletteMix {

    const val KEY_ENABLED = "sp_palette_mix"
    const val KEY_SECONDARY = "sp_mix_secondary"
    const val KEY_TERTIARY = "sp_mix_tertiary"

    /** Roles, also used by the widget accessor. */
    const val ROLE_SECONDARY = 1
    const val ROLE_TERTIARY = 2

    sealed interface Source {
        data object Auto : Source
        data object Dynamic : Source
        data class Preset(val id: String) : Source
        /** The colour stored in PetalCustomPalette.KEY_SECONDARY / KEY_TERTIARY. */
        data object Custom : Source
    }

    data class Recipe(
        val id: String,
        val name: String,
        val subtitle: String,
        val secondary: Source,
        val tertiary: Source
    )

    val RECIPES = listOf(
        Recipe("sunset", "Sunset Glow", "Peach & Amber", Source.Preset("peach"), Source.Preset("amber")),
        Recipe("ocean_forest", "Ocean Forest", "Forest & Mint", Source.Preset("forest"), Source.Preset("mint")),
        Recipe("cyberpunk", "Cyberpunk", "Cyan & Pink", Source.Preset("cyan"), Source.Preset("pink")),
        Recipe("sakura_sky", "Cherry Blossom", "Sakura & Sky", Source.Preset("sakura"), Source.Preset("sky")),
        Recipe("autumn_clay", "Autumn Amber", "Amber & Wine", Source.Preset("amber"), Source.Preset("wine"))
    )

    fun defaultVibrantSources(): Pair<Source, Source> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Pair(Source.Dynamic, Source.Preset("forest"))
        } else {
            Pair(Source.Preset("ocean"), Source.Preset("peach"))
        }
    }

    data class Config(
        val enabled: Boolean = false,
        val secondary: Source = Source.Auto,
        val tertiary: Source = Source.Auto
    ) {
        val active: Boolean get() = enabled && (secondary != Source.Auto || tertiary != Source.Auto)
    }

    fun encode(s: Source): String = when (s) {
        Source.Auto -> "auto"
        Source.Dynamic -> "dynamic"
        Source.Custom -> "color"
        is Source.Preset -> "preset:${s.id}"
    }

    fun parse(raw: String?): Source = when {
        raw == null || raw == "auto" -> Source.Auto
        raw == "dynamic" -> Source.Dynamic
        raw == "color" -> Source.Custom
        raw.startsWith("preset:") -> Source.Preset(raw.removePrefix("preset:"))
        else -> Source.Auto
    }

    fun read(sp: SharedPreferences) = Config(
        enabled = sp.getBoolean(KEY_ENABLED, false),
        secondary = parse(sp.getString(KEY_SECONDARY, null)),
        tertiary = parse(sp.getString(KEY_TERTIARY, null))
    )

    fun config(context: Context): Config = read(PreferenceManager.getDefaultSharedPreferences(context))

    @Volatile private var cached = Config()
    @Volatile private var cachedStyle = ColorStyle.TONAL_SPOT
    @Volatile private var cachedCustom = PetalCustomPalette.Spec(PetalCustomPalette.DEFAULT_SEED)

    /** Keeps a copy for non-Compose callers (home-screen widgets). */
    fun install(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        cached = read(sp)
        cachedStyle = ColorStyle.fromStorageKey(sp.getString("sp_color_style", null))
        cachedCustom = PetalCustomPalette.read(sp)
        sp.registerOnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                KEY_ENABLED, KEY_SECONDARY, KEY_TERTIARY -> cached = read(prefs)
                "sp_color_style" -> cachedStyle = ColorStyle.fromStorageKey(prefs.getString(key, null))
                PetalCustomPalette.KEY_SECONDARY, PetalCustomPalette.KEY_TERTIARY, PetalCustomPalette.KEY_SEED ->
                    cachedCustom = PetalCustomPalette.read(prefs)
            }
        }
    }

    /** The scheme whose PRIMARY colours feed a role, or null when the role stays automatic. */
    private fun sourceScheme(
        source: Source, role: Int, custom: PetalCustomPalette.Spec,
        dark: Boolean, style: ColorStyle, context: Context?
    ): ColorScheme? = when (source) {
        Source.Auto -> null
        Source.Dynamic ->
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).applyStyle(style)
            } else null
        is Source.Preset -> paletteById(source.id).let { if (dark) it.dark else it.light }.applyStyle(style)
        Source.Custom -> {
            val argb = if (role == ROLE_SECONDARY) custom.secondary else custom.tertiary
            if (argb == 0) null else PetalCustomPalette.scheme(PetalCustomPalette.Spec(seed = argb), style, dark)
        }
    }

    private fun ColorScheme.withSecondaryFrom(s: ColorScheme) = copy(
        secondary = s.primary, onSecondary = s.onPrimary,
        secondaryContainer = s.primaryContainer, onSecondaryContainer = s.onPrimaryContainer
    )

    private fun ColorScheme.withTertiaryFrom(s: ColorScheme) = copy(
        tertiary = s.primary, onTertiary = s.onPrimary,
        tertiaryContainer = s.primaryContainer, onTertiaryContainer = s.onPrimaryContainer
    )

    /** Returns [base] with secondary and/or tertiary replaced by their chosen sources. */
    fun apply(
        base: ColorScheme, config: Config, custom: PetalCustomPalette.Spec,
        dark: Boolean, style: ColorStyle, context: Context
    ): ColorScheme {
        if (!config.active) return base
        return try {
            var out = base
            sourceScheme(config.secondary, ROLE_SECONDARY, custom, dark, style, context)?.let { out = out.withSecondaryFrom(it) }
            sourceScheme(config.tertiary, ROLE_TERTIARY, custom, dark, style, context)?.let { out = out.withTertiaryFrom(it) }
            out
        } catch (t: Throwable) {
            base
        }
    }

    /** For widgets: the mixed accent for a role, or null to keep the palette's own (also null for Material You). */
    @JvmStatic
    fun widgetOverride(role: Int, isDark: Boolean): Int? {
        val cfg = cached
        if (!cfg.active) return null
        val source = if (role == ROLE_SECONDARY) cfg.secondary else cfg.tertiary
        return try {
            sourceScheme(source, role, cachedCustom, isDark, cachedStyle, null)?.primary?.toArgb()
        } catch (t: Throwable) {
            null
        }
    }
}
