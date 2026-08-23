@file:OptIn(ExperimentalTextApi::class)

package com.petal.browser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.petal.browser.R

enum class AppFont(val label: String) {
    PETAL("Petal's Signature"),
    GS_FLEX("GS FLEX"),
    NUNITO("Nunito")
}

@OptIn(ExperimentalTextApi::class)
private fun variableFont(
    resId: Int,
    weight: Int,
    width: Float = 100f,
    roundness: Float = 0f
): FontFamily {
    val clampedWeight = weight.coerceIn(100, 900)
    val clampedWidth = width.coerceIn(75f, 125f)
    val clampedRoundness = roundness.coerceIn(0f, 100f)

    return try {
        FontFamily(
            Font(
                resId = resId,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(clampedWeight),
                    FontVariation.width(clampedWidth),
                    FontVariation.Setting("ROND", clampedRoundness),
                    FontVariation.Setting("wght", clampedWeight.toFloat()),
                    FontVariation.Setting("wdth", clampedWidth)
                ),
                weight = FontWeight(clampedWeight)
            )
        )
    } catch (e: Throwable) {
        try {
            FontFamily(Font(resId = resId, weight = FontWeight(clampedWeight)))
        } catch (t: Throwable) {
            FontFamily.Default
        }
    }
}

private fun nunitoFont(weight: Int, width: Float, roundness: Float): FontFamily =
    variableFont(R.font.nunito_variable, weight, width, roundness)

private data class Tiers(
    val display: FontFamily,
    val headline: FontFamily,
    val title: FontFamily,
    val body: FontFamily,
    val label: FontFamily
)

private fun buildTypography(t: Tiers): Typography = Typography(
    displayLarge = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
)

private fun systemTypography(fontWeight: Int): Typography {
    val boldWeight = fontWeight.coerceAtLeast(750)
    val displayF = variableFont(R.font.google_sans_flex_variable, 950, 92f, 100f)
    val headlineF = variableFont(R.font.google_sans_flex_variable, 900, 92f, 100f)
    val titleF = variableFont(R.font.google_sans_flex_variable, 850, 92f, 100f)
    val bodyF = variableFont(R.font.google_sans_flex_variable, boldWeight, 92f, 100f)
    val labelF = variableFont(R.font.google_sans_flex_variable, (boldWeight + 50).coerceAtMost(900), 92f, 100f)
    val t = Tiers(displayF, headlineF, titleF, bodyF, labelF)
    return buildTypography(t)
}

enum class GSFlexPreset(val label: String) {
    ZENITH("Zenith (Default)"),
    NEO("Neo (Wide & Clean)"),
    COMPACT("Compact (High Density)"),
    AIRY("Airy (Spacious & Light)"),
    EXPRESSIVE("Expressive (Ultra Round)"),
    CUSTOM("Custom (Fine-Tuned)")
}

data class FontAxes(
    val weight: Float = 450f,
    val width: Float = 100f,
    val opsz: Float = 16f,
    val grade: Float = 0f,
    val slant: Float = 0f,
    val roundness: Float = 0f
) {
    fun toVariationSettings() = FontVariation.Settings(
        FontVariation.weight(weight.toInt().coerceIn(1, 1000)),
        FontVariation.width(width.coerceIn(25f, 150f)),
        FontVariation.Setting("opsz", opsz.coerceIn(6f, 72f)),
        FontVariation.grade(grade.toInt().coerceIn(-200, 200)),
        FontVariation.slant(slant.coerceIn(-10f, 0f)),
        FontVariation.Setting("ROND", roundness.coerceIn(0f, 100f))
    )
}

data class GSFlexSettings(
    val preset: GSFlexPreset = GSFlexPreset.ZENITH,
    val display: FontAxes = FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
    val headline: FontAxes = FontAxes(700f, 115f, 32f, 0f, 0f, 60f),
    val body: FontAxes = FontAxes(450f, 100f, 16f, 20f, 0f, 0f)
)

fun getPresetFontAxes(preset: GSFlexPreset): Triple<FontAxes, FontAxes, FontAxes> {
    return when (preset) {
        GSFlexPreset.ZENITH -> Triple(
            FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
            FontAxes(700f, 115f, 32f, 0f, 0f, 60f),
            FontAxes(450f, 100f, 16f, 20f, 0f, 0f)
        )
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
        GSFlexPreset.EXPRESSIVE -> Triple(
            FontAxes(950f, 115f, 30f, 0f, 0f, 100f),
            FontAxes(850f, 105f, 32f, 0f, 0f, 100f),
            FontAxes(500f, 100f, 16f, 20f, 0f, 100f)
        )
        GSFlexPreset.CUSTOM -> Triple(
            FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
            FontAxes(700f, 115f, 32f, 0f, 0f, 60f),
            FontAxes(450f, 100f, 16f, 20f, 0f, 0f)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
fun petalTypography(
    appFont: AppFont,
    fontWidth: Float = 92f,
    fontWeight: Int = 750,
    fontRoundness: Float = 100f,
    gsFlexSettings: GSFlexSettings = GSFlexSettings()
): Typography = try {
    val effectiveAxes = if (gsFlexSettings.preset == GSFlexPreset.CUSTOM) {
        Triple(gsFlexSettings.display, gsFlexSettings.headline, gsFlexSettings.body)
    } else {
        getPresetFontAxes(gsFlexSettings.preset)
    }
    when (appFont) {
        AppFont.PETAL -> {
            val w = fontWeight.coerceIn(100, 900)
            val displayFont = variableFont(R.font.google_sans_flex_variable, weight = (w + 200).coerceAtMost(950), width = fontWidth, roundness = fontRoundness)
            val headlineFont = variableFont(R.font.google_sans_flex_variable, weight = (w + 150).coerceAtMost(900), width = fontWidth, roundness = fontRoundness)
            val titleFont = variableFont(R.font.google_sans_flex_variable, weight = (w + 100).coerceAtMost(850), width = fontWidth, roundness = fontRoundness)
            val bodyFont = variableFont(R.font.google_sans_flex_variable, weight = w, width = fontWidth, roundness = fontRoundness)
            val labelFont = variableFont(R.font.google_sans_flex_variable, weight = (w + 50).coerceAtMost(850), width = fontWidth, roundness = fontRoundness)
            buildTypography(Tiers(displayFont, headlineFont, titleFont, bodyFont, labelFont))
        }
        AppFont.GS_FLEX -> {
            // Use google_sans_flex_variable — Zenith's full 6-axis version (weight, width, opsz, GRAD, slant, ROND)
            val displayFont = FontFamily(Font(
                resId = R.font.google_sans_flex_variable,
                variationSettings = effectiveAxes.first.toVariationSettings(),
                weight = FontWeight(effectiveAxes.first.weight.toInt().coerceIn(1, 1000))
            ))
            val headlineFont = FontFamily(Font(
                resId = R.font.google_sans_flex_variable,
                variationSettings = effectiveAxes.second.toVariationSettings(),
                weight = FontWeight(effectiveAxes.second.weight.toInt().coerceIn(1, 1000))
            ))
            val bodyFont = FontFamily(Font(
                resId = R.font.google_sans_flex_variable,
                variationSettings = effectiveAxes.third.toVariationSettings(),
                weight = FontWeight(effectiveAxes.third.weight.toInt().coerceIn(1, 1000))
            ))
            buildTypography(Tiers(displayFont, headlineFont, headlineFont, bodyFont, bodyFont))
        }
        AppFont.NUNITO -> buildTypography(
            Tiers(
                nunitoFont(950, 92f, 100f),
                nunitoFont(900, 92f, 100f),
                nunitoFont(850, 92f, 100f),
                nunitoFont(750, 92f, 100f),
                nunitoFont(800, 92f, 100f)
            )
        )
    }
} catch (e: Throwable) {
    systemTypography(fontWeight)
}

val StrideTypography: Typography = Typography()

