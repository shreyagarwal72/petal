package com.petal.browser.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation
import java.util.Locale

enum class FontTier(val label: String, val gsPrefix: String, val customPrefix: String) {
    DISPLAY("Display", "sp_gs_d_", "sp_custom_display_"),
    HEADLINE("Headline", "sp_gs_h_", "sp_custom_headline_"),
    BODY("Body", "sp_gs_b_", "sp_custom_body_")
}

@OptIn(ExperimentalTextApi::class)
data class FontAxes(
    val weight: Float = DEFAULT_WEIGHT,
    val width: Float = DEFAULT_WIDTH,
    val opsz: Float = DEFAULT_OPSZ,
    val grade: Float = DEFAULT_GRADE,
    val slant: Float = DEFAULT_SLANT,
    val roundness: Float = DEFAULT_ROUNDNESS
) {
    enum class Axis(
        val keySuffix: String,
        val tag: String,
        val label: String,
        val range: ClosedFloatingPointRange<Float>,
        val defaultValue: Float
    ) {
        WEIGHT("weight", "wght", "Weight", 100f..1000f, DEFAULT_WEIGHT),
        WIDTH("width", "wdth", "Width", 25f..150f, DEFAULT_WIDTH),
        OPSZ("opsz", "opsz", "Optical Size", 6f..72f, DEFAULT_OPSZ),
        GRADE("grade", "GRAD", "Grade", -200f..200f, DEFAULT_GRADE),
        SLANT("slant", "slnt", "Slant", -10f..0f, DEFAULT_SLANT),
        ROUNDNESS("roundness", "ROND", "Roundness", 0f..100f, DEFAULT_ROUNDNESS);

        fun clamp(value: Float): Float = value.coerceIn(range.start, range.endInclusive)

        fun format(value: Float): String {
            val v = clamp(value)
            return when (this) {
                OPSZ -> String.format(Locale.US, "%.0f pt", v)
                GRADE -> String.format(Locale.US, "%+.0f", v)
                SLANT -> String.format(Locale.US, "%.0f\u00B0", v)
                ROUNDNESS -> String.format(Locale.US, "%.0f%%", v)
                else -> String.format(Locale.US, "%.0f", v)
            }
        }

        fun get(axes: FontAxes): Float = when (this) {
            WEIGHT -> axes.weight
            WIDTH -> axes.width
            OPSZ -> axes.opsz
            GRADE -> axes.grade
            SLANT -> axes.slant
            ROUNDNESS -> axes.roundness
        }

        fun withValue(axes: FontAxes, value: Float): FontAxes {
            val clamped = clamp(value)
            return when (this) {
                WEIGHT -> axes.copy(weight = clamped)
                WIDTH -> axes.copy(width = clamped)
                OPSZ -> axes.copy(opsz = clamped)
                GRADE -> axes.copy(grade = clamped)
                SLANT -> axes.copy(slant = clamped)
                ROUNDNESS -> axes.copy(roundness = clamped)
            }
        }
    }

    fun clamped(): FontAxes = FontAxes(
        weight = Axis.WEIGHT.clamp(weight),
        width = Axis.WIDTH.clamp(width),
        opsz = Axis.OPSZ.clamp(opsz),
        grade = Axis.GRADE.clamp(grade),
        slant = Axis.SLANT.clamp(slant),
        roundness = Axis.ROUNDNESS.clamp(roundness)
    )

    fun toVariationSettings(overrideWeight: Int? = null, overrideOpsz: Float? = null): FontVariation.Settings {
        val c = clamped()
        val w = Axis.WEIGHT.clamp((overrideWeight ?: c.weight.toInt()).toFloat()).toInt()
        val op = Axis.OPSZ.clamp(overrideOpsz ?: c.opsz)
        return FontVariation.Settings(
            FontVariation.weight(w),
            FontVariation.width(c.width),
            FontVariation.Setting(Axis.OPSZ.tag, op),
            FontVariation.grade(c.grade.toInt()),
            FontVariation.slant(c.slant),
            FontVariation.Setting(Axis.ROUNDNESS.tag, c.roundness)
        )
    }

    fun toVariationString(overrideWeight: Int? = null, overrideOpsz: Float? = null): String {
        val c = clamped()
        val w = Axis.WEIGHT.clamp((overrideWeight ?: c.weight.toInt()).toFloat()).toInt()
        val op = Axis.OPSZ.clamp(overrideOpsz ?: c.opsz)
        return "'wght' $w, 'wdth' ${c.width}, 'opsz' $op, 'GRAD' ${c.grade.toInt()}, 'slnt' ${c.slant}, 'ROND' ${c.roundness}"
    }

    companion object {
        const val DEFAULT_WEIGHT = 450f
        const val DEFAULT_WIDTH = 100f
        const val DEFAULT_OPSZ = 16f
        const val DEFAULT_GRADE = 0f
        const val DEFAULT_SLANT = 0f
        const val DEFAULT_ROUNDNESS = 0f
        val NUNITO_WEIGHT_RANGE = 100f..1000f
        const val DEFAULT_NUNITO_WEIGHT = 400
    }
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

data class GSFlexSettings(
    val preset: GSFlexPreset = GSFlexPreset.PETAL_SIGNATURE,
    val display: FontAxes = FontAxes(950f, 85f, 30f, 0f, 0f, 100f),
    val headline: FontAxes = FontAxes(700f, 115f, 32f, 0f, 0f, 60f),
    val body: FontAxes = FontAxes(450f, 100f, 16f, 20f, 0f, 0f)
) {
    fun axesFor(tier: FontTier): FontAxes = when (tier) {
        FontTier.DISPLAY -> display
        FontTier.HEADLINE -> headline
        FontTier.BODY -> body
    }

    fun withTierAxes(tier: FontTier, axes: FontAxes): GSFlexSettings = when (tier) {
        FontTier.DISPLAY -> copy(display = axes.clamped())
        FontTier.HEADLINE -> copy(headline = axes.clamped())
        FontTier.BODY -> copy(body = axes.clamped())
    }
}

data class CustomFontSettings(
    val display: FontAxes = FontAxes(950f, 100f, 30f, 0f, 0f, 100f),
    val headline: FontAxes = FontAxes(700f, 100f, 32f, 0f, 0f, 60f),
    val body: FontAxes = FontAxes(450f, 100f, 16f, 0f, 0f, 0f)
) {
    fun axesFor(tier: FontTier): FontAxes = when (tier) {
        FontTier.DISPLAY -> display
        FontTier.HEADLINE -> headline
        FontTier.BODY -> body
    }

    fun withTierAxes(tier: FontTier, axes: FontAxes): CustomFontSettings = when (tier) {
        FontTier.DISPLAY -> copy(display = axes.clamped())
        FontTier.HEADLINE -> copy(headline = axes.clamped())
        FontTier.BODY -> copy(body = axes.clamped())
    }
}
