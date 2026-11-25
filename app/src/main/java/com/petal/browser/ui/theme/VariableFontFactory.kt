@file:OptIn(ExperimentalTextApi::class)

package com.petal.browser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import java.io.File

enum class AppFont(val label: String) {
    PETAL("Google Sans Flex"),
    NUNITO("Nunito"),
    SYSTEM("System"),
    CUSTOM("Custom Font");

    companion object {
        fun fromName(name: String?): AppFont {
            if (name.isNullOrBlank()) return PETAL
            return when (name.trim().uppercase()) {
                "CUSTOM", "CUSTOM_STORAGE", "CUSTOM_FONT", "CUSTOM_FILE", "CUSTOM_TTF" -> CUSTOM
                "NUNITO" -> NUNITO
                "SYSTEM", "DEFAULT" -> SYSTEM
                "PETAL", "GS_FLEX", "GSFLEX", "GOOGLE_SANS_FLEX", "PETAL_SIGNATURE", "ZENITH" -> PETAL
                else -> PETAL
            }
        }
    }
}

object VariableFontFactory {

    /** OpenType features for Devanagari / Hinglish & Latin typography: kerning, ligatures, contextual alternates, marks. */
    const val FLEX_FEATURES = "kern, liga, calt, ccmp"

    private val SIGNATURE_DEFAULT = Triple(
        FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
        FontAxes(700f, 115f, 32f, 0f, 0f, 60f),
        FontAxes(450f, 100f, 16f, 20f, 0f, 0f)
    )

    fun getPresetFontAxes(preset: GSFlexPreset): Triple<FontAxes, FontAxes, FontAxes> = when (preset) {
        GSFlexPreset.PETAL_SIGNATURE -> SIGNATURE_DEFAULT
        GSFlexPreset.NEO -> Triple(
            FontAxes(800f, 125f, 72f, 0f, 0f, 0f),
            FontAxes(600f, 100f, 32f, 0f, 0f, 0f),
            FontAxes(400f, 95f, 16f, 10f, 0f, 0f)
        )
        GSFlexPreset.COMPACT -> Triple(
            FontAxes(900f, 75f, 30f, 0f, 0f, 30f),
            FontAxes(800f, 85f, 32f, 50f, 0f, 20f),
            FontAxes(500f, 90f, 16f, 30f, 0f, 10f)
        )
        GSFlexPreset.AIRY -> Triple(
            FontAxes(300f, 130f, 72f, 0f, 0f, 100f),
            FontAxes(500f, 120f, 32f, 0f, 0f, 100f),
            FontAxes(400f, 110f, 16f, 0f, 0f, 50f)
        )
        GSFlexPreset.CUSTOM -> SIGNATURE_DEFAULT
    }

    private fun flexFamily(axes: FontAxes, opsz: Float): FontFamily = try {
        FontFamily((100..1000 step 50).map { w ->
            Font(
                resId = R.font.google_sans_flex_variable,
                weight = FontWeight(w),
                style = FontStyle.Normal,
                variationSettings = axes.toVariationSettings(overrideWeight = w, overrideOpsz = opsz)
            )
        })
    } catch (_: Throwable) {
        FontFamily.Default
    }

    private fun customVariableFamily(file: File, axes: FontAxes, opsz: Float): FontFamily? = try {
        FontFamily((100..1000 step 50).map { w ->
            Font(
                file = file,
                weight = FontWeight(w),
                style = FontStyle.Normal,
                variationSettings = axes.toVariationSettings(overrideWeight = w, overrideOpsz = opsz)
            )
        })
    } catch (_: Throwable) {
        null
    }

    private fun customStaticFamily(file: File): FontFamily? = try {
        FontFamily((100..1000 step 100).map { w ->
            Font(
                file = file,
                weight = FontWeight(w),
                style = FontStyle.Normal
            )
        })
    } catch (_: Throwable) {
        try {
            FontFamily(Font(file))
        } catch (_: Throwable) {
            try {
                FontFamily(android.graphics.Typeface.createFromFile(file))
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun createGsFlexTypography(settings: GSFlexSettings): Typography {
        val custom = settings.preset == GSFlexPreset.CUSTOM
        val axes = if (custom) {
            Triple(settings.display.clamped(), settings.headline.clamped(), settings.body.clamped())
        } else {
            getPresetFontAxes(settings.preset)
        }

        fun style(a: FontAxes, weight: Float, size: Int, line: Int, spacing: Float): TextStyle {
            val w = FontAxes.Axis.WEIGHT.clamp(weight).toInt()
            val opsz = if (custom) a.opsz else size.toFloat()
            return TextStyle(
                fontFamily = flexFamily(a, opsz),
                fontWeight = FontWeight(w),
                fontSynthesis = FontSynthesis.None,
                fontFeatureSettings = FLEX_FEATURES,
                fontSize = size.sp,
                lineHeight = line.sp,
                letterSpacing = spacing.sp
            )
        }

        val d = axes.first
        val h = axes.second
        val b = axes.third
        val label = b.weight + 150f
        return Typography(
            displayLarge = style(d, d.weight, 57, 64, -0.25f),
            displayMedium = style(d, d.weight, 45, 52, 0f),
            displaySmall = style(d, d.weight, 36, 44, 0f),
            headlineLarge = style(h, h.weight, 32, 40, 0f),
            headlineMedium = style(h, h.weight, 28, 36, 0f),
            headlineSmall = style(h, h.weight, 24, 32, 0f),
            titleLarge = style(h, h.weight, 22, 28, 0f),
            titleMedium = style(h, h.weight, 16, 24, 0.15f),
            titleSmall = style(h, h.weight, 14, 20, 0.1f),
            bodyLarge = style(b, b.weight, 16, 24, 0.5f),
            bodyMedium = style(b, b.weight, 14, 20, 0.25f),
            bodySmall = style(b, b.weight, 12, 16, 0.4f),
            labelLarge = style(b, label, 14, 20, 0.1f),
            labelMedium = style(b, label, 12, 16, 0.5f),
            labelSmall = style(b, label, 11, 16, 0.5f)
        )
    }

    val NunitoFontFamily: FontFamily = try {
        FontFamily((100..1000 step 50).map { w ->
            Font(
                resId = R.font.nunito_variable,
                weight = FontWeight(w),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(w))
            )
        })
    } catch (_: Throwable) {
        FontFamily.Default
    }

    fun createNunitoTypography(baseWeight: Int = FontAxes.DEFAULT_NUNITO_WEIGHT): Typography {
        val base = FontAxes.Axis.WEIGHT.clamp(baseWeight.toFloat()).toInt()
        val displayW = base.coerceIn(100, 1000)
        val headlineW = (base + 200).coerceIn(100, 1000)
        val titleW = (base + 300).coerceIn(100, 1000)
        val bodyW = (base + 50).coerceIn(100, 1000)
        val labelW = (base + 300).coerceIn(100, 1000)
        return Typography(
            displayLarge = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(displayW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
            displayMedium = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(displayW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
            displaySmall = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(displayW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
            headlineLarge = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(headlineW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
            headlineMedium = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(headlineW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
            headlineSmall = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(headlineW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
            titleLarge = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(titleW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
            titleMedium = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(titleW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
            titleSmall = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(titleW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            bodyLarge = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(bodyW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
            bodyMedium = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(bodyW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
            bodySmall = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(bodyW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
            labelLarge = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(labelW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            labelMedium = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(labelW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
            labelSmall = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight(labelW), fontSynthesis = FontSynthesis.None, fontFeatureSettings = FLEX_FEATURES, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
        )
    }

    fun createBasicTypography(fontFamily: FontFamily): Typography = Typography(
        displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
        displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        headlineMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
    )

    fun createCustomTypography(
        custom: CustomFontState,
        fallbackGsFlex: GSFlexSettings
    ): Typography {
        val file = PetalFontHelper.resolveReadableCustomFontFile(custom)
            ?: return createGsFlexTypography(fallbackGsFlex)
        val isVar = PetalFontHelper.isVariableFontFile(file)
        val cfg = custom.settings
        val d = cfg.display.clamped()
        val h = cfg.headline.clamped()
        val b = cfg.body.clamped()

        return if (isVar) {
            fun varStyle(a: FontAxes, w: Float, size: Int, line: Int, spacing: Float): TextStyle? {
                val fam = customVariableFamily(file, a, a.opsz) ?: return null
                return TextStyle(
                    fontFamily = fam,
                    fontWeight = FontWeight(FontAxes.Axis.WEIGHT.clamp(w).toInt()),
                    fontSynthesis = FontSynthesis.None,
                    fontFeatureSettings = FLEX_FEATURES,
                    fontSize = size.sp,
                    lineHeight = line.sp,
                    letterSpacing = spacing.sp
                )
            }
            val labelW = FontAxes.Axis.WEIGHT.clamp(b.weight + 150f)
            val dLarge = varStyle(d, d.weight, 57, 64, -0.25f) ?: return createGsFlexTypography(fallbackGsFlex)
            Typography(
                displayLarge = dLarge,
                displayMedium = varStyle(d, d.weight, 45, 52, 0f) ?: dLarge,
                displaySmall = varStyle(d, d.weight, 36, 44, 0f) ?: dLarge,
                headlineLarge = varStyle(h, h.weight, 32, 40, 0f) ?: dLarge,
                headlineMedium = varStyle(h, h.weight, 28, 36, 0f) ?: dLarge,
                headlineSmall = varStyle(h, h.weight, 24, 32, 0f) ?: dLarge,
                titleLarge = varStyle(h, h.weight, 22, 28, 0f) ?: dLarge,
                titleMedium = varStyle(h, h.weight, 16, 24, 0.15f) ?: dLarge,
                titleSmall = varStyle(h, h.weight, 14, 20, 0.1f) ?: dLarge,
                bodyLarge = varStyle(b, b.weight, 16, 24, 0.5f) ?: dLarge,
                bodyMedium = varStyle(b, b.weight, 14, 20, 0.25f) ?: dLarge,
                bodySmall = varStyle(b, b.weight, 12, 16, 0.4f) ?: dLarge,
                labelLarge = varStyle(b, labelW, 14, 20, 0.1f) ?: dLarge,
                labelMedium = varStyle(b, labelW, 12, 16, 0.5f) ?: dLarge,
                labelSmall = varStyle(b, labelW, 11, 16, 0.5f) ?: dLarge
            )
        } else {
            val staticFamily = customStaticFamily(file) ?: return createGsFlexTypography(fallbackGsFlex)
            fun staticStyle(weight: FontWeight, size: Int, line: Int, spacing: Float): TextStyle = TextStyle(
                fontFamily = staticFamily,
                fontWeight = weight,
                fontSynthesis = FontSynthesis.Weight,
                fontFeatureSettings = FLEX_FEATURES,
                fontSize = size.sp,
                lineHeight = line.sp,
                letterSpacing = spacing.sp
            )
            Typography(
                displayLarge = staticStyle(FontWeight.Normal, 57, 64, -0.25f),
                displayMedium = staticStyle(FontWeight.Normal, 45, 52, 0f),
                displaySmall = staticStyle(FontWeight.Normal, 36, 44, 0f),
                headlineLarge = staticStyle(FontWeight.SemiBold, 32, 40, 0f),
                headlineMedium = staticStyle(FontWeight.SemiBold, 28, 36, 0f),
                headlineSmall = staticStyle(FontWeight.SemiBold, 24, 32, 0f),
                titleLarge = staticStyle(FontWeight.Bold, 22, 28, 0f),
                titleMedium = staticStyle(FontWeight.Bold, 16, 24, 0.15f),
                titleSmall = staticStyle(FontWeight.Bold, 14, 20, 0.1f),
                bodyLarge = staticStyle(FontWeight.Medium, 16, 24, 0.5f),
                bodyMedium = staticStyle(FontWeight.Medium, 14, 20, 0.25f),
                bodySmall = staticStyle(FontWeight.Medium, 12, 16, 0.4f),
                labelLarge = staticStyle(FontWeight.Bold, 14, 20, 0.1f),
                labelMedium = staticStyle(FontWeight.Bold, 12, 16, 0.5f),
                labelSmall = staticStyle(FontWeight.Bold, 11, 16, 0.5f)
            )
        }
    }

    fun createTypography(state: FontState): Typography = try {
        when (state.appFont) {
            AppFont.PETAL -> createGsFlexTypography(state.gsFlexSettings)
            AppFont.NUNITO -> createNunitoTypography(state.nunitoWeight)
            AppFont.SYSTEM -> SystemTypography
            AppFont.CUSTOM -> createCustomTypography(state.customFont, state.gsFlexSettings)
        }
    } catch (_: Throwable) {
        SystemTypography
    }
}

val NunitoFontFamily: FontFamily = VariableFontFactory.NunitoFontFamily
val NunitoTypography: Typography = VariableFontFactory.createNunitoTypography()
val SystemTypography: Typography = VariableFontFactory.createBasicTypography(FontFamily.Default)
val StrideTypography: Typography = Typography()

fun getPresetFontAxes(preset: GSFlexPreset): Triple<FontAxes, FontAxes, FontAxes> =
    VariableFontFactory.getPresetFontAxes(preset)

fun createGsFlexTypography(settings: GSFlexSettings): Typography =
    VariableFontFactory.createGsFlexTypography(settings)

fun petalTypography(state: FontState): Typography =
    VariableFontFactory.createTypography(state)
