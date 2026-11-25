package com.petal.browser.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.petal.browser.unit.PetalAndroidUtils
import org.json.JSONObject
import java.io.File

enum class FontAccentMode(val label: String, val subtitle: String) {
    OFF("Off", "Standard surface ink across all text"),
    HEADINGS("Headings", "Display, headline and title styles use primary accent"),
    HEADINGS_AND_LABELS("Headings & Labels", "Headings plus labels and captions use primary accent"),
    ALL("Everything", "All text styles use contrast-safe primary accent");

    companion object {
        fun fromName(name: String?): FontAccentMode {
            if (name.isNullOrBlank()) return OFF
            return when (name.trim().uppercase()) {
                "HEADINGS" -> HEADINGS
                "HEADINGS_AND_LABELS", "HEADINGS_LABELS", "LABELS" -> HEADINGS_AND_LABELS
                "ALL", "EVERYTHING" -> ALL
                else -> OFF
            }
        }
    }
}

@Immutable
data class CustomFontState(
    val path: String? = null,
    val lastValidPath: String? = null,
    val name: String = PetalFontPrefs.NO_CUSTOM_FONT_NAME,
    val settings: CustomFontSettings = CustomFontSettings(),
    val isVariable: Boolean = false
)

@Immutable
data class FontState(
    val appFont: AppFont = AppFont.PETAL,
    val gsFlexSettings: GSFlexSettings = GSFlexSettings(),
    val nunitoWeight: Int = FontAxes.DEFAULT_NUNITO_WEIGHT,
    val customFont: CustomFontState = CustomFontState(),
    val accentMode: FontAccentMode = FontAccentMode.OFF,
    val readerFollowsAppFont: Boolean = false
)

/**
 * Single source of truth for loading, migrating, and updating typography preferences across
 * every screen, sheet, dialog, activity, view bridge, and widget in Petal.
 */
object PetalFontPrefs {
    const val KEY_APP_FONT = "sp_app_font"
    const val KEY_GS_PRESET = "sp_gs_flex_preset"
    const val KEY_NUNITO_WEIGHT = "sp_nunito_weight"
    const val KEY_CUSTOM_FONT_PATH = "sp_custom_font_path"
    const val KEY_CUSTOM_FONT_LAST_VALID_PATH = "sp_custom_font_last_valid_path"
    const val KEY_CUSTOM_FONT_NAME = "sp_custom_font_name"
    const val KEY_CUSTOM_FONT_IS_VARIABLE = "sp_custom_font_is_variable"
    const val KEY_FONT_ACCENT = "sp_font_accent"
    const val KEY_READER_FOLLOWS_APP_FONT = "sp_reader_follows_app_font"
    const val NO_CUSTOM_FONT_NAME = "No font file selected"

    // Legacy dead keys cleaned up once during migration
    const val LEGACY_FONT_WIDTH = "sp_font_width"
    const val LEGACY_FONT_WEIGHT = "sp_font_weight"
    const val LEGACY_FONT_ROUNDNESS = "sp_font_roundness"
    private const val KEY_MIGRATED_V2 = "sp_font_prefs_migrated_v2"

    val ALL_FONT_KEYS: Set<String> = buildSet {
        add(KEY_APP_FONT)
        add(KEY_GS_PRESET)
        add(KEY_NUNITO_WEIGHT)
        add(KEY_CUSTOM_FONT_PATH)
        add(KEY_CUSTOM_FONT_LAST_VALID_PATH)
        add(KEY_CUSTOM_FONT_NAME)
        add(KEY_CUSTOM_FONT_IS_VARIABLE)
        add(KEY_FONT_ACCENT)
        add(KEY_READER_FOLLOWS_APP_FONT)
        for (tier in FontTier.entries) {
            for (axis in FontAxes.Axis.entries) {
                add(tier.gsPrefix + axis.keySuffix)
                add(tier.customPrefix + axis.keySuffix)
            }
        }
    }

    fun isFontKey(key: String?): Boolean =
        key == null || key in ALL_FONT_KEYS || key.startsWith("sp_gs_") || key.startsWith("sp_custom_")

    fun gsAxisKey(tier: FontTier, axis: FontAxes.Axis): String = tier.gsPrefix + axis.keySuffix
    fun customAxisKey(tier: FontTier, axis: FontAxes.Axis): String = tier.customPrefix + axis.keySuffix

    fun migrateLegacyIfNeeded(sp: SharedPreferences) {
        val hasDead = sp.contains(LEGACY_FONT_WIDTH) || sp.contains(LEGACY_FONT_WEIGHT) || sp.contains(LEGACY_FONT_ROUNDNESS)
        if (!hasDead && sp.getBoolean(KEY_MIGRATED_V2, false)) return

        val editor = sp.edit()
        if (hasDead && !sp.contains(KEY_GS_PRESET)) {
            val w = readLegacyNumeric(sp, LEGACY_FONT_WEIGHT, 750f)
            val wd = readLegacyNumeric(sp, LEGACY_FONT_WIDTH, 92f)
            val r = readLegacyNumeric(sp, LEGACY_FONT_ROUNDNESS, 100f)
            val nonDefault = w != 750f || wd != 92f || r != 100f
            if (nonDefault) {
                val base = VariableFontFactory.getPresetFontAxes(GSFlexPreset.PETAL_SIGNATURE)
                val display = base.first.copy(weight = w, width = wd, roundness = r).clamped()
                val headline = base.second.copy(width = wd, roundness = r).clamped()
                val body = base.third.copy(width = wd).clamped()
                editor.putString(KEY_GS_PRESET, GSFlexPreset.CUSTOM.name)
                writeAxes(editor, FontTier.DISPLAY, display, custom = false)
                writeAxes(editor, FontTier.HEADLINE, headline, custom = false)
                writeAxes(editor, FontTier.BODY, body, custom = false)
            }
        }
        val rawFont = sp.getString(KEY_APP_FONT, null)
        if (rawFont != null && rawFont.equals("GS_FLEX", ignoreCase = true)) {
            editor.putString(KEY_APP_FONT, AppFont.PETAL.name)
        }
        editor.remove(LEGACY_FONT_WIDTH)
            .remove(LEGACY_FONT_WEIGHT)
            .remove(LEGACY_FONT_ROUNDNESS)
            .putBoolean(KEY_MIGRATED_V2, true)
            .apply()
    }

    private fun readLegacyNumeric(sp: SharedPreferences, key: String, fallback: Float): Float {
        return try {
            sp.getFloat(key, fallback)
        } catch (_: ClassCastException) {
            try {
                sp.getInt(key, fallback.toInt()).toFloat()
            } catch (_: Throwable) {
                fallback
            }
        }
    }

    fun load(sp: SharedPreferences, context: Context? = null): FontState {
        migrateLegacyIfNeeded(sp)
        val appFont = AppFont.fromName(sp.getString(KEY_APP_FONT, AppFont.PETAL.name))
        val gsFlex = loadGSFlexSettings(sp)
        val nunitoW = readLegacyNumeric(sp, KEY_NUNITO_WEIGHT, FontAxes.DEFAULT_NUNITO_WEIGHT.toFloat())
            .let { FontAxes.Axis.WEIGHT.clamp(it).toInt() }
        val customState = loadCustomFontState(sp, context)
        val accentMode = FontAccentMode.fromName(sp.getString(KEY_FONT_ACCENT, FontAccentMode.OFF.name))
        val readerFollows = sp.getBoolean(KEY_READER_FOLLOWS_APP_FONT, false)
        return FontState(
            appFont = appFont,
            gsFlexSettings = gsFlex,
            nunitoWeight = nunitoW,
            customFont = customState,
            accentMode = accentMode,
            readerFollowsAppFont = readerFollows
        )
    }

    fun loadGSFlexSettings(sp: SharedPreferences): GSFlexSettings {
        val preset = GSFlexPreset.fromName(sp.getString(KEY_GS_PRESET, GSFlexPreset.PETAL_SIGNATURE.name))
        val dAxes = VariableFontFactory.getPresetFontAxes(GSFlexPreset.PETAL_SIGNATURE)
        return GSFlexSettings(
            preset = preset,
            display = readAxes(sp, FontTier.DISPLAY, dAxes.first, custom = false),
            headline = readAxes(sp, FontTier.HEADLINE, dAxes.second, custom = false),
            body = readAxes(sp, FontTier.BODY, dAxes.third, custom = false)
        )
    }

    fun loadCustomFontSettings(sp: SharedPreferences): CustomFontSettings {
        val defaults = CustomFontSettings()
        return CustomFontSettings(
            display = readAxes(sp, FontTier.DISPLAY, defaults.display, custom = true),
            headline = readAxes(sp, FontTier.HEADLINE, defaults.headline, custom = true),
            body = readAxes(sp, FontTier.BODY, defaults.body, custom = true)
        )
    }

    private fun loadCustomFontState(sp: SharedPreferences, context: Context?): CustomFontState {
        var path = sp.getString(KEY_CUSTOM_FONT_PATH, null)?.takeIf { it.isNotBlank() }
        if (path != null && !File(path).exists()) path = null
        if (path == null && context != null) {
            val legacyFile = context.filesDir.resolve("custom_font.ttf")
            if (legacyFile.exists() && legacyFile.canRead()) {
                path = legacyFile.absolutePath
            }
        }
        val lastValid = sp.getString(KEY_CUSTOM_FONT_LAST_VALID_PATH, null)?.takeIf { it.isNotBlank() && File(it).exists() }
        val name = sp.getString(KEY_CUSTOM_FONT_NAME, NO_CUSTOM_FONT_NAME) ?: NO_CUSTOM_FONT_NAME
        val resolvedFile = (path ?: lastValid)?.let(::File)
        val isVar = if (sp.contains(KEY_CUSTOM_FONT_IS_VARIABLE)) {
            sp.getBoolean(KEY_CUSTOM_FONT_IS_VARIABLE, false)
        } else {
            resolvedFile != null && PetalFontHelper.isVariableFontFile(resolvedFile)
        }
        return CustomFontState(
            path = path,
            lastValidPath = lastValid,
            name = name,
            settings = loadCustomFontSettings(sp),
            isVariable = isVar
        )
    }

    private fun readAxes(sp: SharedPreferences, tier: FontTier, defaultAxes: FontAxes, custom: Boolean): FontAxes {
        val prefix = if (custom) tier.customPrefix else tier.gsPrefix
        return FontAxes(
            weight = PetalAndroidUtils.getSafeFloat(sp, prefix + FontAxes.Axis.WEIGHT.keySuffix, defaultAxes.weight),
            width = PetalAndroidUtils.getSafeFloat(sp, prefix + FontAxes.Axis.WIDTH.keySuffix, defaultAxes.width),
            opsz = PetalAndroidUtils.getSafeFloat(sp, prefix + FontAxes.Axis.OPSZ.keySuffix, defaultAxes.opsz),
            grade = PetalAndroidUtils.getSafeFloat(sp, prefix + FontAxes.Axis.GRADE.keySuffix, defaultAxes.grade),
            slant = PetalAndroidUtils.getSafeFloat(sp, prefix + FontAxes.Axis.SLANT.keySuffix, defaultAxes.slant),
            roundness = PetalAndroidUtils.getSafeFloat(sp, prefix + FontAxes.Axis.ROUNDNESS.keySuffix, defaultAxes.roundness)
        ).clamped()
    }

    private fun writeAxes(editor: SharedPreferences.Editor, tier: FontTier, axes: FontAxes, custom: Boolean) {
        val c = axes.clamped()
        val prefix = if (custom) tier.customPrefix else tier.gsPrefix
        editor
            .putFloat(prefix + FontAxes.Axis.WEIGHT.keySuffix, c.weight)
            .putFloat(prefix + FontAxes.Axis.WIDTH.keySuffix, c.width)
            .putFloat(prefix + FontAxes.Axis.OPSZ.keySuffix, c.opsz)
            .putFloat(prefix + FontAxes.Axis.GRADE.keySuffix, c.grade)
            .putFloat(prefix + FontAxes.Axis.SLANT.keySuffix, c.slant)
            .putFloat(prefix + FontAxes.Axis.ROUNDNESS.keySuffix, c.roundness)
    }

    fun setAppFont(sp: SharedPreferences, font: AppFont) {
        sp.edit().putString(KEY_APP_FONT, font.name).apply()
        PetalThemePrefs.bump()
    }

    fun setGsFlexPreset(sp: SharedPreferences, preset: GSFlexPreset) {
        sp.edit().putString(KEY_GS_PRESET, preset.name).apply()
        PetalThemePrefs.bump()
    }

    fun setGsFlexAxis(sp: SharedPreferences, tier: FontTier, axis: FontAxes.Axis, value: Float) {
        sp.edit()
            .putString(KEY_GS_PRESET, GSFlexPreset.CUSTOM.name)
            .putFloat(gsAxisKey(tier, axis), axis.clamp(value))
            .apply()
        PetalThemePrefs.bump()
    }

    fun setAllGsFlexAxes(sp: SharedPreferences, axis: FontAxes.Axis, value: Float) {
        val v = axis.clamp(value)
        val editor = sp.edit().putString(KEY_GS_PRESET, GSFlexPreset.CUSTOM.name)
        for (tier in FontTier.entries) {
            editor.putFloat(gsAxisKey(tier, axis), v)
        }
        editor.apply()
        PetalThemePrefs.bump()
    }

    fun setGsFlexSettings(sp: SharedPreferences, settings: GSFlexSettings) {
        val editor = sp.edit().putString(KEY_GS_PRESET, settings.preset.name)
        writeAxes(editor, FontTier.DISPLAY, settings.display, custom = false)
        writeAxes(editor, FontTier.HEADLINE, settings.headline, custom = false)
        writeAxes(editor, FontTier.BODY, settings.body, custom = false)
        editor.apply()
        PetalThemePrefs.bump()
    }

    fun resetGsFlexToPreset(sp: SharedPreferences, preset: GSFlexPreset) {
        val target = if (preset == GSFlexPreset.CUSTOM) GSFlexPreset.PETAL_SIGNATURE else preset
        val axes = VariableFontFactory.getPresetFontAxes(target)
        setGsFlexSettings(
            sp,
            GSFlexSettings(preset = target, display = axes.first, headline = axes.second, body = axes.third)
        )
    }

    fun setNunitoWeight(sp: SharedPreferences, weight: Int) {
        sp.edit().putInt(KEY_NUNITO_WEIGHT, FontAxes.Axis.WEIGHT.clamp(weight.toFloat()).toInt()).apply()
        PetalThemePrefs.bump()
    }

    fun setCustomFontAxis(sp: SharedPreferences, tier: FontTier, axis: FontAxes.Axis, value: Float) {
        sp.edit().putFloat(customAxisKey(tier, axis), axis.clamp(value)).apply()
        PetalThemePrefs.bump()
    }

    fun setAllCustomFontAxes(sp: SharedPreferences, axis: FontAxes.Axis, value: Float) {
        val v = axis.clamp(value)
        val editor = sp.edit()
        for (tier in FontTier.entries) {
            editor.putFloat(customAxisKey(tier, axis), v)
        }
        editor.apply()
        PetalThemePrefs.bump()
    }

    fun setCustomFontSettings(sp: SharedPreferences, settings: CustomFontSettings) {
        val editor = sp.edit()
        writeAxes(editor, FontTier.DISPLAY, settings.display, custom = true)
        writeAxes(editor, FontTier.HEADLINE, settings.headline, custom = true)
        writeAxes(editor, FontTier.BODY, settings.body, custom = true)
        editor.apply()
        PetalThemePrefs.bump()
    }

    fun setFontAccentMode(sp: SharedPreferences, mode: FontAccentMode) {
        sp.edit().putString(KEY_FONT_ACCENT, mode.name).apply()
        PetalThemePrefs.bump()
    }

    fun setReaderFollowsAppFont(sp: SharedPreferences, enabled: Boolean) {
        sp.edit().putBoolean(KEY_READER_FOLLOWS_APP_FONT, enabled).apply()
        PetalThemePrefs.bump()
    }

    fun resetAll(sp: SharedPreferences) {
        val sig = VariableFontFactory.getPresetFontAxes(GSFlexPreset.PETAL_SIGNATURE)
        val customDefaults = CustomFontSettings()
        val editor = sp.edit()
            .putString(KEY_APP_FONT, AppFont.PETAL.name)
            .putInt(KEY_NUNITO_WEIGHT, FontAxes.DEFAULT_NUNITO_WEIGHT)
            .putString(KEY_FONT_ACCENT, FontAccentMode.OFF.name)
            .putBoolean(KEY_READER_FOLLOWS_APP_FONT, false)
            .putString(KEY_GS_PRESET, GSFlexPreset.PETAL_SIGNATURE.name)
        writeAxes(editor, FontTier.DISPLAY, sig.first, custom = false)
        writeAxes(editor, FontTier.HEADLINE, sig.second, custom = false)
        writeAxes(editor, FontTier.BODY, sig.third, custom = false)
        writeAxes(editor, FontTier.DISPLAY, customDefaults.display, custom = true)
        writeAxes(editor, FontTier.HEADLINE, customDefaults.headline, custom = true)
        writeAxes(editor, FontTier.BODY, customDefaults.body, custom = true)
        editor.apply()
        PetalFontHelper.clearCache()
        PetalThemePrefs.bump()
    }

    fun exportJson(sp: SharedPreferences): JSONObject {
        val state = load(sp)
        return JSONObject().apply {
            put("appFont", state.appFont.name)
            put("gsPreset", state.gsFlexSettings.preset.name)
            put("nunitoWeight", state.nunitoWeight)
            put("accentMode", state.accentMode.name)
            put("readerFollowsAppFont", state.readerFollowsAppFont)
            for (tier in FontTier.entries) {
                val gs = state.gsFlexSettings.axesFor(tier)
                put("gs_${tier.name.lowercase()}", JSONObject().apply {
                    for (axis in FontAxes.Axis.entries) put(axis.keySuffix, axis.get(gs).toDouble())
                })
                val cu = state.customFont.settings.axesFor(tier)
                put("custom_${tier.name.lowercase()}", JSONObject().apply {
                    for (axis in FontAxes.Axis.entries) put(axis.keySuffix, axis.get(cu).toDouble())
                })
            }
        }
    }

    fun importJson(sp: SharedPreferences, json: JSONObject): Boolean {
        return try {
            val font = AppFont.fromName(json.optString("appFont", AppFont.PETAL.name))
            val preset = GSFlexPreset.fromName(json.optString("gsPreset", GSFlexPreset.PETAL_SIGNATURE.name))
            val nunitoW = json.optInt("nunitoWeight", FontAxes.DEFAULT_NUNITO_WEIGHT)
            val accent = FontAccentMode.fromName(json.optString("accentMode", FontAccentMode.OFF.name))
            val readerFollow = json.optBoolean("readerFollowsAppFont", false)

            fun readTier(prefix: String, fallback: FontAxes): FontAxes {
                val obj = json.optJSONObject(prefix) ?: return fallback
                var axes = fallback
                for (axis in FontAxes.Axis.entries) {
                    if (obj.has(axis.keySuffix)) {
                        axes = axis.withValue(axes, obj.optDouble(axis.keySuffix, axis.get(axes).toDouble()).toFloat())
                    }
                }
                return axes.clamped()
            }

            val defSig = VariableFontFactory.getPresetFontAxes(GSFlexPreset.PETAL_SIGNATURE)
            val gs = GSFlexSettings(
                preset = preset,
                display = readTier("gs_display", defSig.first),
                headline = readTier("gs_headline", defSig.second),
                body = readTier("gs_body", defSig.third)
            )
            val cuDef = CustomFontSettings()
            val cu = CustomFontSettings(
                display = readTier("custom_display", cuDef.display),
                headline = readTier("custom_headline", cuDef.headline),
                body = readTier("custom_body", cuDef.body)
            )

            setGsFlexSettings(sp, gs)
            setCustomFontSettings(sp, cu)
            setNunitoWeight(sp, nunitoW)
            setFontAccentMode(sp, accent)
            setReaderFollowsAppFont(sp, readerFollow)
            setAppFont(sp, font)
            true
        } catch (_: Throwable) {
            false
        }
    }
}

@Composable
fun rememberFontState(): FontState {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    @Suppress("UNUSED_VARIABLE") val revision = PetalThemePrefs.revision
    var state by remember(sp) { mutableStateOf(PetalFontPrefs.load(sp, context)) }

    DisposableEffect(sp) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (PetalFontPrefs.isFontKey(key)) {
                state = PetalFontPrefs.load(sp, context)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Re-sync cleanly when PetalThemePrefs.bump() triggers recomposition.
    return remember(state, revision) { PetalFontPrefs.load(sp, context) }
}
