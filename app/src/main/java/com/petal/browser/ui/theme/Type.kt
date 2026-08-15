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
    SYSTEM("System Default"),
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
                    FontVariation.Setting("RNDS", clampedRoundness),
                    FontVariation.Setting("SOFT", clampedRoundness),
                    FontVariation.Setting("ROUND", clampedRoundness),
                    FontVariation.Setting("rnd ", clampedRoundness),
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
    displayLarge = TextStyle(fontFamily = t.display, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = t.display, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = t.display, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = t.headline, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = t.headline, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = t.headline, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = t.title, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = t.title, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = t.title, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = t.body, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = t.body, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = t.body, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = t.label, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = t.label, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = t.label, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)

private fun weightedTiers(
    resId: Int,
    top: Int = 400,
    width: Float = 100f,
    roundness: Float = 0f
): Tiers = Tiers(
    display = variableFont(resId, (top + 400).coerceAtMost(900), width, roundness),
    headline = variableFont(resId, (top + 300).coerceAtMost(900), width, roundness),
    title = variableFont(resId, (top + 200).coerceAtMost(900), width, roundness),
    body = variableFont(resId, top, width, roundness),
    label = variableFont(resId, (top + 100).coerceAtMost(900), width, roundness)
)

private fun systemTypography(fontWeight: Int): Typography {
    val weight = FontWeight(fontWeight.coerceIn(100, 900))
    val t = Tiers(FontFamily.Default, FontFamily.Default, FontFamily.Default, FontFamily.Default, FontFamily.Default)
    return Typography(
        displayLarge = TextStyle(fontFamily = t.display, fontWeight = weight, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1).sp),
        displayMedium = TextStyle(fontFamily = t.display, fontWeight = weight, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-0.5).sp),
        displaySmall = TextStyle(fontFamily = t.display, fontWeight = weight, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = t.headline, fontWeight = weight, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = t.headline, fontWeight = weight, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = t.headline, fontWeight = weight, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = t.title, fontWeight = weight, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = t.title, fontWeight = weight, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = t.title, fontWeight = weight, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = t.body, fontWeight = weight, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
        bodyMedium = TextStyle(fontFamily = t.body, fontWeight = weight, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        bodySmall = TextStyle(fontFamily = t.body, fontWeight = weight, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
        labelLarge = TextStyle(fontFamily = t.label, fontWeight = weight, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = t.label, fontWeight = weight, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelSmall = TextStyle(fontFamily = t.label, fontWeight = weight, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
    )
}

enum class GSFlexPreset(val label: String) {
    DEFAULT("Custom Sliders"),
    EXPRESSIVE("Expressive (Ultra Round)"),
    NEO("Neo (Wide & Clean)"),
    COMPACT("Compact (High Density)"),
    AIRY("Airy (Spacious & Light)")
}

data class FontAxes(
    val weight: Float = 400f,
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
        FontVariation.Setting("RNDS", roundness.coerceIn(0f, 100f)),
        FontVariation.Setting("ROND", roundness.coerceIn(0f, 100f)),
        FontVariation.Setting("SOFT", roundness.coerceIn(0f, 100f)),
        FontVariation.Setting("ROUND", roundness.coerceIn(0f, 100f))
    )
}

data class GSFlexSettings(
    val preset: GSFlexPreset = GSFlexPreset.DEFAULT,
    val display: FontAxes = FontAxes(400f, 100f, 72f, 0f, 0f, 0f),
    val headline: FontAxes = FontAxes(400f, 100f, 32f, 0f, 0f, 0f),
    val body: FontAxes = FontAxes(400f, 100f, 16f, 0f, 0f, 0f)
)

fun getPresetFontAxes(preset: GSFlexPreset): Triple<FontAxes, FontAxes, FontAxes>? {
    return when (preset) {
        GSFlexPreset.EXPRESSIVE -> Triple(
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
        GSFlexPreset.DEFAULT -> null
    }
}

@OptIn(ExperimentalTextApi::class)
fun petalTypography(
    appFont: AppFont,
    fontWidth: Float = 100f,
    fontWeight: Int = 400,
    fontRoundness: Float = 0f,
    preset: GSFlexPreset = GSFlexPreset.DEFAULT
): Typography = try {
    val presetAxes = getPresetFontAxes(preset)
    if (appFont == AppFont.GS_FLEX && presetAxes != null) {
        val displayFont = FontFamily(Font(resId = R.font.google_sans_flex, variationSettings = presetAxes.first.toVariationSettings()))
        val headlineFont = FontFamily(Font(resId = R.font.google_sans_flex, variationSettings = presetAxes.second.toVariationSettings()))
        val bodyFont = FontFamily(Font(resId = R.font.google_sans_flex, variationSettings = presetAxes.third.toVariationSettings()))
        buildTypography(Tiers(displayFont, headlineFont, headlineFont, bodyFont, bodyFont))
    } else {
        when (appFont) {
            AppFont.SYSTEM -> systemTypography(fontWeight)
            AppFont.GS_FLEX -> buildTypography(weightedTiers(R.font.google_sans_flex, top = fontWeight, width = fontWidth, roundness = fontRoundness))
            AppFont.NUNITO -> buildTypography(
                Tiers(
                    nunitoFont(fontWeight + 500, fontWidth, fontRoundness),
                    nunitoFont(fontWeight + 350, fontWidth, fontRoundness),
                    nunitoFont(fontWeight + 300, fontWidth, fontRoundness),
                    nunitoFont(fontWeight, fontWidth, fontRoundness),
                    nunitoFont(fontWeight + 250, fontWidth, fontRoundness)
                )
            )
        }
    }
} catch (e: Throwable) {
    systemTypography(fontWeight)
}

val StrideTypography: Typography = Typography()

