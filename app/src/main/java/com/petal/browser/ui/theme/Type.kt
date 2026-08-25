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
    PETAL("Petal Signature"),
    CUSTOM("Custom Font");

    companion object {
        fun fromName(name: String?): AppFont {
            if (name.isNullOrBlank()) return PETAL
            return when (name.trim().uppercase()) {
                "CUSTOM", "CUSTOM_STORAGE", "CUSTOM_FONT", "CUSTOM_FILE", "CUSTOM_TTF" -> CUSTOM
                else -> PETAL
            }
        }
    }
}

object PetalFontHelper {
    fun saveCustomFontUri(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val destFile = java.io.File(context.filesDir, "custom_font.ttf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val fileName = getFileName(context, uri) ?: "custom_font.ttf"
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putString("sp_app_font", "CUSTOM")
                .putString("sp_custom_font_path", destFile.absolutePath)
                .putString("sp_custom_font_name", fileName)
                .apply()
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = cursor.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return name
    }
}

data class CustomFontSettings(
    val display: FontAxes = FontAxes(950f, 100f, 30f, 0f, 0f, 100f),
    val headline: FontAxes = FontAxes(700f, 100f, 32f, 0f, 0f, 60f),
    val body: FontAxes = FontAxes(450f, 100f, 16f, 0f, 0f, 0f)
)

fun getCustomFontSettings(sp: android.content.SharedPreferences): CustomFontSettings {
    return CustomFontSettings(
        display = FontAxes(
            weight = sp.getFloat("sp_custom_display_weight", 950f),
            width = sp.getFloat("sp_custom_display_width", 100f),
            opsz = sp.getFloat("sp_custom_display_opsz", 30f),
            grade = sp.getFloat("sp_custom_display_grade", 0f),
            slant = sp.getFloat("sp_custom_display_slant", 0f),
            roundness = sp.getFloat("sp_custom_display_roundness", 100f)
        ),
        headline = FontAxes(
            weight = sp.getFloat("sp_custom_headline_weight", 700f),
            width = sp.getFloat("sp_custom_headline_width", 100f),
            opsz = sp.getFloat("sp_custom_headline_opsz", 32f),
            grade = sp.getFloat("sp_custom_headline_grade", 0f),
            slant = sp.getFloat("sp_custom_headline_slant", 0f),
            roundness = sp.getFloat("sp_custom_headline_roundness", 60f)
        ),
        body = FontAxes(
            weight = sp.getFloat("sp_custom_body_weight", 450f),
            width = sp.getFloat("sp_custom_body_width", 100f),
            opsz = sp.getFloat("sp_custom_body_opsz", 16f),
            grade = sp.getFloat("sp_custom_body_grade", 0f),
            slant = sp.getFloat("sp_custom_body_slant", 0f),
            roundness = sp.getFloat("sp_custom_body_roundness", 0f)
        )
    )
}

fun saveCustomFontSettings(sp: android.content.SharedPreferences, settings: CustomFontSettings) {
    sp.edit()
        .putFloat("sp_custom_display_weight", settings.display.weight)
        .putFloat("sp_custom_display_width", settings.display.width)
        .putFloat("sp_custom_display_opsz", settings.display.opsz)
        .putFloat("sp_custom_display_grade", settings.display.grade)
        .putFloat("sp_custom_display_slant", settings.display.slant)
        .putFloat("sp_custom_display_roundness", settings.display.roundness)
        .putFloat("sp_custom_headline_weight", settings.headline.weight)
        .putFloat("sp_custom_headline_width", settings.headline.width)
        .putFloat("sp_custom_headline_opsz", settings.headline.opsz)
        .putFloat("sp_custom_headline_grade", settings.headline.grade)
        .putFloat("sp_custom_headline_slant", settings.headline.slant)
        .putFloat("sp_custom_headline_roundness", settings.headline.roundness)
        .putFloat("sp_custom_body_weight", settings.body.weight)
        .putFloat("sp_custom_body_width", settings.body.width)
        .putFloat("sp_custom_body_opsz", settings.body.opsz)
        .putFloat("sp_custom_body_grade", settings.body.grade)
        .putFloat("sp_custom_body_slant", settings.body.slant)
        .putFloat("sp_custom_body_roundness", settings.body.roundness)
        .apply()
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
    PETAL("Petal (Default)"),
    NEO("Neo (Wide & Clean)"),
    COMPACT("Compact (High Density)"),
    AIRY("Airy (Spacious & Light)"),
    EXPRESSIVE("Expressive (Ultra Round)")
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
    val preset: GSFlexPreset = GSFlexPreset.PETAL,
    val display: FontAxes = FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
    val headline: FontAxes = FontAxes(700f, 115f, 32f, 0f, 0f, 60f),
    val body: FontAxes = FontAxes(450f, 100f, 16f, 20f, 0f, 0f)
)

fun getPresetFontAxes(preset: GSFlexPreset): Triple<FontAxes, FontAxes, FontAxes> {
    return when (preset) {
        GSFlexPreset.PETAL -> Triple(
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
    }
}

@OptIn(ExperimentalTextApi::class)
fun petalTypography(
    appFont: AppFont,
    fontWidth: Float = 92f,
    fontWeight: Int = 750,
    fontRoundness: Float = 100f,
    gsFlexSettings: GSFlexSettings = GSFlexSettings(),
    customFontPath: String? = null,
    customFontSettings: CustomFontSettings = CustomFontSettings()
): Typography = try {
    when (appFont) {
        AppFont.PETAL -> {
            val effectiveAxes = getPresetFontAxes(gsFlexSettings.preset)
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
        AppFont.CUSTOM -> {
            val validPath = customFontPath?.takeIf { it.isNotBlank() }
            val file = if (validPath != null) java.io.File(validPath) else null

            if (file != null && file.exists() && file.canRead()) {
                val customFontFamily = try {
                    val typeface = android.graphics.Typeface.createFromFile(file)
                    FontFamily(typeface)
                } catch (t: Throwable) {
                    try {
                        FontFamily(Font(file))
                    } catch (_: Throwable) {
                        null
                    }
                }

                if (customFontFamily != null) {
                    buildTypography(Tiers(customFontFamily, customFontFamily, customFontFamily, customFontFamily, customFontFamily))
                } else {
                    petalTypography(AppFont.PETAL, fontWidth, fontWeight, fontRoundness, gsFlexSettings)
                }
            } else {
                petalTypography(AppFont.PETAL, fontWidth, fontWeight, fontRoundness, gsFlexSettings)
            }
        }
    }
} catch (e: Throwable) {
    systemTypography(fontWeight)
}

val StrideTypography: Typography = Typography()

