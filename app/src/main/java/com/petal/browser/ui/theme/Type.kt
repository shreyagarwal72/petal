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

    fun getAppTypeface(context: android.content.Context): android.graphics.Typeface {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val appFont = AppFont.fromName(sp.getString("sp_app_font", "PETAL"))
        if (appFont == AppFont.CUSTOM) {
            val path = sp.getString("sp_custom_font_path", null)
            val defaultCustom = context.filesDir.resolve("custom_font.ttf")
            val file = if (!path.isNullOrBlank() && java.io.File(path).exists()) java.io.File(path) else if (defaultCustom.exists()) defaultCustom else null
            if (file != null && file.canRead()) {
                try {
                    return android.graphics.Typeface.createFromFile(file)
                } catch (_: Exception) {}
            }
        }
        return petalSignatureTypeface(context)
    }

    @Volatile private var signatureCache: android.graphics.Typeface? = null
    @Volatile private var signatureKey: String? = null

    /**
     * Petal Signature for classic Views, notifications and widgets. ResourcesCompat.getFont returns
     * the variable font at its default axes (plain wght 400), which looks nothing like the
     * Compose UI, so build it with the current preset's body axes instead.
     */
    private fun petalSignatureTypeface(context: android.content.Context): android.graphics.Typeface {
        val fallback = try {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.google_sans_flex_variable)
                ?: android.graphics.Typeface.DEFAULT
        } catch (_: Exception) {
            android.graphics.Typeface.DEFAULT
        }
        return try {
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val gs = loadGSFlexSettings(sp)
            val b = if (gs.preset == GSFlexPreset.CUSTOM) gs.body else getPresetFontAxes(gs.preset).third
            val settings = "'wght' ${b.weight.toInt().coerceIn(1, 1000)}, 'wdth' ${b.width.coerceIn(25f, 150f)}, " +
                "'opsz' ${b.opsz.coerceIn(6f, 72f)}, 'GRAD' ${b.grade.toInt().coerceIn(-200, 200)}, " +
                "'slnt' ${b.slant.coerceIn(-10f, 0f)}, 'ROND' ${b.roundness.coerceIn(0f, 100f)}"
            if (settings == signatureKey) signatureCache?.let { return it }
            val file = java.io.File(context.cacheDir, "petal_signature.ttf")
            if (!file.exists() || file.length() == 0L) {
                context.resources.openRawResource(R.font.google_sans_flex_variable).use { i ->
                    file.outputStream().use { o -> i.copyTo(o) }
                }
            }
            val tf = android.graphics.Typeface.Builder(file).setFontVariationSettings(settings).build() ?: fallback
            signatureKey = settings
            signatureCache = tf
            tf
        } catch (_: Throwable) {
            fallback
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
            weight = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_display_weight", 950f),
            width = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_display_width", 100f),
            opsz = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_display_opsz", 30f),
            grade = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_display_grade", 0f),
            slant = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_display_slant", 0f),
            roundness = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_display_roundness", 100f)
        ),
        headline = FontAxes(
            weight = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_headline_weight", 700f),
            width = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_headline_width", 100f),
            opsz = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_headline_opsz", 32f),
            grade = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_headline_grade", 0f),
            slant = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_headline_slant", 0f),
            roundness = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_headline_roundness", 60f)
        ),
        body = FontAxes(
            weight = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_body_weight", 450f),
            width = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_body_width", 100f),
            opsz = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_body_opsz", 16f),
            grade = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_body_grade", 0f),
            slant = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_body_slant", 0f),
            roundness = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_custom_body_roundness", 0f)
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
    displayLarge = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = t.display, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = t.headline, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = t.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = t.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

@OptIn(ExperimentalTextApi::class)
private fun expressivePetalTypography(): Typography {
    fun flex(weight: Float, width: Float, rond: Float, opsz: Float): FontFamily = try {
        FontFamily(Font(
            resId = R.font.google_sans_flex_variable,
            weight = FontWeight(weight.toInt().coerceIn(1, 1000)),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.toInt().coerceIn(1, 1000)),
                FontVariation.width(width),
                FontVariation.Setting("ROND", rond),
                FontVariation.Setting("opsz", opsz),
                FontVariation.grade(0),
                FontVariation.slant(0f),
            ),
        ))
    } catch (_: Throwable) { FontFamily.Default }

    fun style(weight: Float, width: Float, rond: Float, size: Int, line: Int) = TextStyle(
        fontFamily = flex(weight, width, rond, size.toFloat()),
        fontWeight = FontWeight(weight.toInt().coerceIn(1, 1000)), fontSize = size.sp, lineHeight = line.sp,
    )

    return Typography(
        displayLarge = style(760f, 122f, 42f, 57, 64), displayMedium = style(740f, 120f, 40f, 45, 52),
        displaySmall = style(720f, 118f, 38f, 36, 44), headlineLarge = style(800f, 122f, 40f, 32, 40),
        headlineMedium = style(780f, 120f, 38f, 28, 36), headlineSmall = style(760f, 116f, 36f, 24, 32),
        titleLarge = style(640f, 110f, 30f, 22, 28), titleMedium = style(600f, 106f, 28f, 16, 24),
        titleSmall = style(580f, 105f, 26f, 14, 20), bodyLarge = style(440f, 102f, 18f, 16, 24),
        bodyMedium = style(430f, 102f, 18f, 15, 22), bodySmall = style(420f, 100f, 16f, 13, 18),
        labelLarge = style(680f, 108f, 46f, 14, 20), labelMedium = style(660f, 108f, 44f, 12, 16),
        labelSmall = style(660f, 108f, 44f, 11, 16),
    )
}

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
    PETAL_SIGNATURE("Petal Signature"),
    NEO("Neo"),
    COMPACT("Impact"),
    AIRY("Airy"),
    CUSTOM("Custom Variable");

    companion object {
        fun fromName(name: String?): GSFlexPreset {
            if (name.isNullOrBlank()) return PETAL_SIGNATURE
            return when (name.trim().uppercase()) {
                "PETAL", "PETAL_SIGNATURE", "ZENITH" -> PETAL_SIGNATURE
                "NEO" -> NEO
                "COMPACT", "IMPACT" -> COMPACT
                "AIRY" -> AIRY
                "CUSTOM" -> CUSTOM
                else -> PETAL_SIGNATURE
            }
        }
    }
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
    val preset: GSFlexPreset = GSFlexPreset.PETAL_SIGNATURE,
    val display: FontAxes = FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
    val headline: FontAxes = FontAxes(700f, 115f, 32f, 0f, 0f, 60f),
    val body: FontAxes = FontAxes(450f, 100f, 16f, 20f, 0f, 0f)
)

fun getPresetFontAxes(preset: GSFlexPreset): Triple<FontAxes, FontAxes, FontAxes> {
    return when (preset) {
        GSFlexPreset.PETAL_SIGNATURE -> Triple(
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
        else -> getPresetFontAxes(GSFlexPreset.PETAL_SIGNATURE)
    }
}

fun loadGSFlexSettings(sp: android.content.SharedPreferences): GSFlexSettings {
    val presetName = sp.getString("sp_gs_flex_preset", GSFlexPreset.PETAL_SIGNATURE.name)
    val preset = GSFlexPreset.fromName(presetName)
    val defaultAxes = getPresetFontAxes(GSFlexPreset.PETAL_SIGNATURE)
    val display = FontAxes(
        weight = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_d_weight", defaultAxes.first.weight),
        width = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_d_width", defaultAxes.first.width),
        opsz = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_d_opsz", defaultAxes.first.opsz),
        grade = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_d_grade", defaultAxes.first.grade),
        slant = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_d_slant", defaultAxes.first.slant),
        roundness = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_d_roundness", defaultAxes.first.roundness)
    )
    val headline = FontAxes(
        weight = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_h_weight", defaultAxes.second.weight),
        width = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_h_width", defaultAxes.second.width),
        opsz = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_h_opsz", defaultAxes.second.opsz),
        grade = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_h_grade", defaultAxes.second.grade),
        slant = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_h_slant", defaultAxes.second.slant),
        roundness = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_h_roundness", defaultAxes.second.roundness)
    )
    val body = FontAxes(
        weight = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_b_weight", defaultAxes.third.weight),
        width = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_b_width", defaultAxes.third.width),
        opsz = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_b_opsz", defaultAxes.third.opsz),
        grade = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_b_grade", defaultAxes.third.grade),
        slant = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_b_slant", defaultAxes.third.slant),
        roundness = com.petal.browser.unit.HelperUnit.getSafeFloat(sp, "sp_gs_b_roundness", defaultAxes.third.roundness)
    )
    return GSFlexSettings(preset = preset, display = display, headline = headline, body = body)
}

fun saveGSFlexSettings(sp: android.content.SharedPreferences, settings: GSFlexSettings) {
    sp.edit()
        .putString("sp_gs_flex_preset", settings.preset.name)
        .putFloat("sp_gs_d_weight", settings.display.weight)
        .putFloat("sp_gs_d_width", settings.display.width)
        .putFloat("sp_gs_d_opsz", settings.display.opsz)
        .putFloat("sp_gs_d_grade", settings.display.grade)
        .putFloat("sp_gs_d_slant", settings.display.slant)
        .putFloat("sp_gs_d_roundness", settings.display.roundness)
        .putFloat("sp_gs_h_weight", settings.headline.weight)
        .putFloat("sp_gs_h_width", settings.headline.width)
        .putFloat("sp_gs_h_opsz", settings.headline.opsz)
        .putFloat("sp_gs_h_grade", settings.headline.grade)
        .putFloat("sp_gs_h_slant", settings.headline.slant)
        .putFloat("sp_gs_h_roundness", settings.headline.roundness)
        .putFloat("sp_gs_b_weight", settings.body.weight)
        .putFloat("sp_gs_b_width", settings.body.width)
        .putFloat("sp_gs_b_opsz", settings.body.opsz)
        .putFloat("sp_gs_b_grade", settings.body.grade)
        .putFloat("sp_gs_b_slant", settings.body.slant)
        .putFloat("sp_gs_b_roundness", settings.body.roundness)
        .apply()
}

/**
 * Default OpenType features for every Google Sans Flex text style: kerning, standard ligatures,
 * contextual alternates and mark composition (needed for Devanagari / Hinglish text).
 */
private const val FLEX_FEATURES = "kern, liga, calt, ccmp"

/**
 * One FontFamily per tier. It holds an entry for every weight (100..1000, step 50), each pinned to
 * the tier's width / grade / slant / roundness and a given optical size. A request for any
 * FontWeight therefore gets the real wght instance of the variable font. Previously one entry was
 * pinned to a single wght and Compose faked bold on top of it whenever a style asked for >= 600.
 */
@OptIn(ExperimentalTextApi::class)
private fun flexFamily(axes: FontAxes, opsz: Float): FontFamily {
    return try {
        FontFamily((100..1000 step 50).map { w ->
            Font(
                resId = R.font.google_sans_flex_variable,
                weight = FontWeight(w),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w),
                    FontVariation.width(axes.width.coerceIn(25f, 150f)),
                    FontVariation.Setting("opsz", opsz.coerceIn(6f, 72f)),
                    FontVariation.grade(axes.grade.toInt().coerceIn(-200, 200)),
                    FontVariation.slant(axes.slant.coerceIn(-10f, 0f)),
                    FontVariation.Setting("ROND", axes.roundness.coerceIn(0f, 100f))
                )
            )
        })
    } catch (_: Throwable) {
        FontFamily.Default
    }
}

@OptIn(ExperimentalTextApi::class)
fun createGsFlexTypography(settings: GSFlexSettings): Typography {
    val custom = settings.preset == GSFlexPreset.CUSTOM
    val axes = if (!custom) getPresetFontAxes(settings.preset)
    else Triple(settings.display, settings.headline, settings.body)

    // Optical size: presets follow each style's own point size (what opsz is designed for);
    // Custom mode uses the value from the Optical Size slider.
    fun style(a: FontAxes, weight: Float, size: Int, line: Int, spacing: Float): TextStyle {
        val w = weight.toInt().coerceIn(1, 1000)
        return TextStyle(
            fontFamily = flexFamily(a, if (custom) a.opsz else size.toFloat()),
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
            createGsFlexTypography(gsFlexSettings)
        }
        AppFont.CUSTOM -> {
            val validPath = customFontPath?.takeIf { it.isNotBlank() }
            val file = if (validPath != null) java.io.File(validPath) else null

            if (file != null && file.exists() && file.canRead()) {
                val displayFont = try {
                    FontFamily(Font(file, variationSettings = customFontSettings.display.toVariationSettings()))
                } catch (_: Throwable) {
                    try { FontFamily(Font(file)) } catch (_: Throwable) {
                        try { FontFamily(android.graphics.Typeface.createFromFile(file)) } catch (_: Throwable) { null }
                    }
                }
                val headlineFont = try {
                    FontFamily(Font(file, variationSettings = customFontSettings.headline.toVariationSettings()))
                } catch (_: Throwable) {
                    displayFont
                }
                val bodyFont = try {
                    FontFamily(Font(file, variationSettings = customFontSettings.body.toVariationSettings()))
                } catch (_: Throwable) {
                    displayFont
                }

                if (displayFont != null && headlineFont != null && bodyFont != null) {
                    buildTypography(Tiers(displayFont, headlineFont, headlineFont, bodyFont, bodyFont))
                } else {
                    createGsFlexTypography(gsFlexSettings)
                }
            } else {
                createGsFlexTypography(gsFlexSettings)
            }
        }
    }
} catch (e: Throwable) {
    systemTypography(fontWeight)
}

val StrideTypography: Typography = Typography()
