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
    NUNITO("Nunito"),
    INTER("Inter"),
    OUTFIT("Outfit"),
    LEXEND("Lexend"),
    MANROPE("Manrope"),
    GROTESK("Space Grotesk")
}

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: Int): FontFamily = FontFamily(
    Font(
        resId = resId,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        weight = FontWeight(weight.coerceIn(100, 900))
    )
)

private fun weightedTiers(resId: Int, top: Int = 900): Typography {
    val display = variableFont(resId, top)
    val headline = variableFont(resId, (top - 100).coerceAtLeast(100))
    val title = variableFont(resId, (top - 200).coerceAtLeast(100))
    val body = variableFont(resId, 450)
    val label = variableFont(resId, 600)
    return Typography(
        displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontFamily = headline, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = headline, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = headline, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = title, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
        labelLarge = TextStyle(fontFamily = label, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelSmall = TextStyle(fontFamily = label, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
    )
}

fun petalTypography(appFont: AppFont): Typography = when (appFont) {
    AppFont.SYSTEM -> Typography()
    AppFont.NUNITO -> weightedTiers(R.font.nunito_variable, top = 900)
    AppFont.INTER -> weightedTiers(R.font.inter_variable, top = 900)
    AppFont.OUTFIT -> weightedTiers(R.font.outfit_variable, top = 900)
    AppFont.LEXEND -> weightedTiers(R.font.lexend_variable, top = 900)
    AppFont.MANROPE -> weightedTiers(R.font.manrope_variable, top = 800)
    AppFont.GROTESK -> weightedTiers(R.font.spacegrotesk_variable, top = 700)
}
