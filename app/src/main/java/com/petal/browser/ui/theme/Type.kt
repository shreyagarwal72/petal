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
    GS_FLEX("Google Sans Flex"),
    NUNITO("Nunito"),
    INTER("Inter"),
    OUTFIT("Outfit"),
    LEXEND("Lexend"),
    MANROPE("Manrope"),
    GROTESK("Space Grotesk")
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

    return FontFamily(
        Font(
            resId = resId,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(clampedWeight),
                FontVariation.width(clampedWidth),
                FontVariation.Setting("RNDS", clampedRoundness)
            ),
            weight = FontWeight(clampedWeight)
        )
    )
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
    displayLarge = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)

private fun weightedTiers(
    resId: Int,
    top: Int = 900,
    width: Float = 100f,
    roundness: Float = 0f
): Tiers = Tiers(
    display = variableFont(resId, top, width, roundness),
    headline = variableFont(resId, (top - 100).coerceAtLeast(100), width, roundness),
    title = variableFont(resId, (top - 200).coerceAtLeast(100), width, roundness),
    body = variableFont(resId, 450, width, roundness),
    label = variableFont(resId, 600, width, roundness)
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

fun petalTypography(
    appFont: AppFont,
    fontWidth: Float = 100f,
    fontWeight: Int = 400,
    fontRoundness: Float = 0f
): Typography = when (appFont) {
    AppFont.SYSTEM -> systemTypography(fontWeight)
    AppFont.GS_FLEX -> buildTypography(weightedTiers(R.font.google_sans_flex, top = fontWeight, width = fontWidth, roundness = fontRoundness))
    AppFont.NUNITO -> buildTypography(
        Tiers(
            nunitoFont(fontWeight + 500, fontWidth, fontRoundness),
            nunitoFont(fontWeight + 350, fontWidth, fontRoundness),
            nunitoFont(fontWeight + 300, fontWidth, fontRoundness),
            nunitoFont(fontWeight + 100, fontWidth, fontRoundness),
            nunitoFont(fontWeight + 250, fontWidth, fontRoundness)
        )
    )
    AppFont.INTER -> buildTypography(weightedTiers(R.font.inter_variable, top = fontWeight, width = fontWidth, roundness = fontRoundness))
    AppFont.OUTFIT -> buildTypography(weightedTiers(R.font.outfit_variable, top = fontWeight, width = fontWidth, roundness = fontRoundness))
    AppFont.LEXEND -> buildTypography(weightedTiers(R.font.lexend_variable, top = fontWeight, width = fontWidth, roundness = fontRoundness))
    AppFont.MANROPE -> buildTypography(weightedTiers(R.font.manrope_variable, top = fontWeight, width = fontWidth, roundness = fontRoundness))
    AppFont.GROTESK -> buildTypography(weightedTiers(R.font.spacegrotesk_variable, top = fontWeight, width = fontWidth, roundness = fontRoundness))
}
