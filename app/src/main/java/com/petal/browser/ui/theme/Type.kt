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
    GS_ROUND("Google Sans Round"),
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

/**
 * Ported from RV System Monitor's `googleSansFlexFontFamily`: Google Sans Flex pinned to a
 * fixed ROND=100 "always rounded" look, instead of Petal's tunable width/roundness sliders.
 * Simpler and more consistent than the GS_FLEX preset system - just weight varies per tier.
 */
private val MonitorRoundVariationSetting = FontVariation.Setting("ROND", 100.0f)

@OptIn(ExperimentalTextApi::class)
private fun googleSansRoundFontFamily(weight: Int, width: Float = 92f): FontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.coerceIn(1, 1000)),
            FontVariation.width(width.coerceIn(75f, 125f)),
            MonitorRoundVariationSetting
        ),
        weight = FontWeight(weight.coerceIn(100, 900))
    ),
)

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

private fun weightedTiers(
    resId: Int,
    top: Int = 750,
    width: Float = 92f,
    roundness: Float = 100f
): Tiers = Tiers(
    display = variableFont(resId, 950, width, roundness),
    headline = variableFont(resId, 900, width, roundness),
    title = variableFont(resId, 850, width, roundness),
    body = variableFont(resId, 750, width, roundness),
    label = variableFont(resId, 800, width, roundness)
)

private fun systemTypography(fontWeight: Int): Typography {
    val boldWeight = fontWeight.coerceAtLeast(750)
    val displayF = googleSansRoundFontFamily(950, 92f)
    val headlineF = googleSansRoundFontFamily(900, 92f)
    val titleF = googleSansRoundFontFamily(850, 92f)
    val bodyF = googleSansRoundFontFamily(boldWeight, 92f)
    val labelF = googleSansRoundFontFamily((boldWeight + 50).coerceAtMost(900), 92f)
    val t = Tiers(displayF, headlineF, titleF, bodyF, labelF)
    return buildTypography(t)
}

enum class GSFlexPreset(val label: String) {
    ZENITH("Zenith (Default)"),
    NEO("Neo (Wide & Clean)"),
    COMPACT("Compact (High Density)"),
    AIRY("Airy (Spacious & Light)"),
    EXPRESSIVE("Expressive (Ultra Round)")
}

data class FontAxes(
    val weight: Float = 800f,
    val width: Float = 92f,
    val opsz: Float = 16f,
    val grade: Float = 0f,
    val slant: Float = 0f,
    val roundness: Float = 100f
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
    val display: FontAxes = FontAxes(950f, 90f, 72f, 0f, 0f, 100f),
    val headline: FontAxes = FontAxes(850f, 92f, 32f, 0f, 0f, 100f),
    val body: FontAxes = FontAxes(750f, 94f, 16f, 0f, 0f, 100f)
)

fun getPresetFontAxes(preset: GSFlexPreset): Triple<FontAxes, FontAxes, FontAxes> {
    return when (preset) {
        GSFlexPreset.ZENITH -> Triple(
            FontAxes(950f, 90f, 30f, 0f, 0f, 100f),
            FontAxes(850f, 92f, 32f, 0f, 0f, 100f),
            FontAxes(750f, 94f, 16f, 20f, 0f, 100f)
        )
        GSFlexPreset.EXPRESSIVE -> Triple(
            FontAxes(950f, 90f, 30f, 0f, 0f, 100f),
            FontAxes(900f, 92f, 32f, 0f, 0f, 100f),
            FontAxes(780f, 94f, 16f, 20f, 0f, 100f)
        )
        GSFlexPreset.NEO -> Triple(
            FontAxes(900f, 95f, 72f, 0f, 0f, 100f),
            FontAxes(800f, 92f, 32f, 0f, 0f, 100f),
            FontAxes(720f, 94f, 16f, 10f, 0f, 100f)
        )
        GSFlexPreset.COMPACT -> Triple(
            FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
            FontAxes(880f, 88f, 32f, 50f, 0f, 100f),
            FontAxes(760f, 90f, 16f, 30f, 0f, 100f)
        )
        GSFlexPreset.AIRY -> Triple(
            FontAxes(900f, 95f, 72f, 0f, 0f, 100f),
            FontAxes(820f, 92f, 32f, 0f, 0f, 100f),
            FontAxes(730f, 94f, 16f, 0f, 0f, 100f)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
fun petalTypography(
    appFont: AppFont,
    fontWidth: Float = 92f,
    fontWeight: Int = 750,
    fontRoundness: Float = 100f,
    preset: GSFlexPreset = GSFlexPreset.ZENITH
): Typography = try {
    val presetAxes = getPresetFontAxes(preset)
    when (appFont) {
        AppFont.PETAL -> {
            val w = fontWeight.coerceIn(100, 900)
            val displayFont = variableFont(R.font.google_sans_flex, weight = (w + 200).coerceAtMost(950), width = fontWidth, roundness = fontRoundness)
            val headlineFont = variableFont(R.font.google_sans_flex, weight = (w + 150).coerceAtMost(900), width = fontWidth, roundness = fontRoundness)
            val titleFont = variableFont(R.font.google_sans_flex, weight = (w + 100).coerceAtMost(850), width = fontWidth, roundness = fontRoundness)
            val bodyFont = variableFont(R.font.google_sans_flex, weight = w, width = fontWidth, roundness = fontRoundness)
            val labelFont = variableFont(R.font.google_sans_flex, weight = (w + 50).coerceAtMost(850), width = fontWidth, roundness = fontRoundness)
            buildTypography(Tiers(displayFont, headlineFont, titleFont, bodyFont, labelFont))
        }
        AppFont.GS_FLEX -> {
            val displayFont = FontFamily(Font(resId = R.font.google_sans_flex, variationSettings = presetAxes.first.toVariationSettings(), weight = FontWeight(950)))
            val headlineFont = FontFamily(Font(resId = R.font.google_sans_flex, variationSettings = presetAxes.second.toVariationSettings(), weight = FontWeight(850)))
            val bodyFont = FontFamily(Font(resId = R.font.google_sans_flex, variationSettings = presetAxes.third.toVariationSettings(), weight = FontWeight(750)))
            buildTypography(Tiers(displayFont, headlineFont, headlineFont, bodyFont, bodyFont))
        }
        AppFont.GS_ROUND -> buildTypography(
            Tiers(
                display = googleSansRoundFontFamily(950, 92f),
                headline = googleSansRoundFontFamily(900, 92f),
                title = googleSansRoundFontFamily(850, 92f),
                body = googleSansRoundFontFamily(750, 92f),
                label = googleSansRoundFontFamily(800, 92f)
            )
        )
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

