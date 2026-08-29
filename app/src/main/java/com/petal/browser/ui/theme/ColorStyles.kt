package com.petal.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ColorStyle(
    val storageKey: String,
    val label: String,
    val description: String
) {
    TONAL_SPOT("tonal_spot", "Tonal Spot", "Default Material 3 palette with balanced saturation and contrast"),
    VIBRANT("vibrant", "Vibrant", "Punches up colors with enhanced chroma and vivid accents"),
    EXPRESSIVE("expressive", "Expressive", "Creative color shifts across secondary and tertiary tones"),
    FRUIT_SALAD("fruit_salad", "Fruit Salad", "Playful, energetic complementary color variety"),
    NEUTRAL("neutral", "Neutral", "Subtle and relaxed tones with softened chroma"),
    MONOCHROME("monochrome", "Monochrome", "Clean grayscale palette for distraction-free browsing");

    companion object {
        val default: ColorStyle = TONAL_SPOT

        fun fromStorageKey(value: String?): ColorStyle {
            return entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) } ?: default
        }
    }
}

data class PetalPalette(
    val id: String,
    val label: String,
    val seed: Color,
    val light: ColorScheme,
    val dark: ColorScheme
)

private fun Color.mapHsv(block: (FloatArray) -> Unit): Color {
    return try {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        block(hsv)
        hsv[1] = hsv[1].coerceIn(0f, 1f)
        hsv[2] = hsv[2].coerceIn(0f, 1f)
        Color(android.graphics.Color.HSVToColor((alpha * 255).toInt().coerceIn(0, 255), hsv))
    } catch (e: Throwable) {
        this
    }
}

private fun Color.saturate(factor: Float) = mapHsv { it[1] = it[1] * factor }
private fun Color.hueShift(degrees: Float) = mapHsv { it[0] = (it[0] + degrees + 360f) % 360f }

private fun ColorScheme.mapAccents(transform: (Color) -> Color): ColorScheme = copy(
    primary = transform(primary),
    onPrimary = transform(onPrimary),
    primaryContainer = transform(primaryContainer),
    onPrimaryContainer = transform(onPrimaryContainer),
    secondary = transform(secondary),
    onSecondary = transform(onSecondary),
    secondaryContainer = transform(secondaryContainer),
    onSecondaryContainer = transform(onSecondaryContainer),
    tertiary = transform(tertiary),
    onTertiary = transform(onTertiary),
    tertiaryContainer = transform(tertiaryContainer),
    onTertiaryContainer = transform(onTertiaryContainer),
    inversePrimary = transform(inversePrimary)
)

private fun ColorScheme.mapTertiary(transform: (Color) -> Color): ColorScheme = copy(
    tertiary = transform(tertiary),
    tertiaryContainer = transform(tertiaryContainer)
)

private fun ColorScheme.mapAll(transform: (Color) -> Color): ColorScheme =
    mapAccents(transform).copy(
        background = transform(background),
        onBackground = transform(onBackground),
        surface = transform(surface),
        onSurface = transform(onSurface),
        surfaceVariant = transform(surfaceVariant),
        onSurfaceVariant = transform(onSurfaceVariant),
        surfaceContainerLowest = transform(surfaceContainerLowest),
        surfaceContainerLow = transform(surfaceContainerLow),
        surfaceContainer = transform(surfaceContainer),
        surfaceContainerHigh = transform(surfaceContainerHigh),
        surfaceContainerHighest = transform(surfaceContainerHighest),
        outline = transform(outline),
        outlineVariant = transform(outlineVariant),
        inverseSurface = transform(inverseSurface),
        inverseOnSurface = transform(inverseOnSurface)
    )

fun ColorScheme.applyStyle(style: ColorStyle): ColorScheme = try {
    when (style) {
        ColorStyle.TONAL_SPOT -> this
        ColorStyle.VIBRANT -> mapAccents { it.saturate(1.5f) }
            .copy(
                primaryContainer = primaryContainer.saturate(1.35f),
                secondaryContainer = secondaryContainer.saturate(1.3f),
                tertiaryContainer = tertiaryContainer.saturate(1.3f)
            )
        ColorStyle.EXPRESSIVE -> mapAccents { it.saturate(1.25f) }
            .mapTertiary { it.hueShift(-45f) }
            .copy(
                secondary = secondary.hueShift(35f).saturate(1.3f),
                secondaryContainer = secondaryContainer.hueShift(35f).saturate(1.25f)
            )
        ColorStyle.FRUIT_SALAD -> mapAccents { it.saturate(1.35f) }
            .mapTertiary { it.hueShift(60f) }
            .copy(
                primary = primary.hueShift(-15f),
                secondary = secondary.hueShift(45f).saturate(1.4f),
                secondaryContainer = secondaryContainer.hueShift(45f).saturate(1.3f)
            )
        ColorStyle.NEUTRAL -> mapAccents { it.saturate(0.35f) }
        ColorStyle.MONOCHROME -> mapAll { it.saturate(0f) }
    }
} catch (e: Throwable) {
    this
}

// --- Stride & Petal Color Palettes ---
private val TideLight = lightColorScheme(
    primary = Color(0xFF006960),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF051F1C),
    tertiary = Color(0xFF9A4600),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC6),
    onTertiaryContainer = Color(0xFF321300),
    background = Color(0xFFEBF3F0),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFEBF3F0),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFDFFFD),
    surfaceContainerHigh = Color(0xFFF3F9F6),
    surfaceContainerHighest = Color(0xFFE7EEEB),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C5)
)

private val TideDark = darkColorScheme(
    primary = Color(0xFF82D5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9EF2E4),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFFFB877),
    onTertiary = Color(0xFF522300),
    tertiaryContainer = Color(0xFF743500),
    onTertiaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFF0D1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0D1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF081110),
    surfaceContainerLow = Color(0xFF151E1C),
    surfaceContainer = Color(0xFF1A2422),
    surfaceContainerHigh = Color(0xFF242F2C),
    surfaceContainerHighest = Color(0xFF2F3A37),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946)
)

private val ZenLight = lightColorScheme(
    primary = Color(0xFF445E91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF715573),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBD7FC),
    onTertiaryContainer = Color(0xFF29132D),
    background = Color(0xFFE9EDFB),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFE9EDFB),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFDFE2F0),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFCFDFF),
    surfaceContainerHigh = Color(0xFFF2F5FF),
    surfaceContainerHighest = Color(0xFFE4E8F5),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0)
)

private val ZenDark = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF102F60),
    primaryContainer = Color(0xFF2B4678),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDEBCDF),
    onTertiary = Color(0xFF402843),
    tertiaryContainer = Color(0xFF583E5B),
    onTertiaryContainer = Color(0xFFFBD7FC),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C21),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F)
)

private val EmberLight = lightColorScheme(
    primary = Color(0xFFA63D00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF77574A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF2C160C),
    tertiary = Color(0xFF66558F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF211047),
    background = Color(0xFFFBEFE9),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFBEFE9),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF4DED4),
    onSurfaceVariant = Color(0xFF52443D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFF8F5),
    surfaceContainerHigh = Color(0xFFFCEAE2),
    surfaceContainerHighest = Color(0xFFF6E4DC),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD7C2B8)
)

private val EmberDark = darkColorScheme(
    primary = Color(0xFFFFB598),
    onPrimary = Color(0xFF591D00),
    primaryContainer = Color(0xFF7F2C00),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFE7BEAD),
    onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF5D4034),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFFD0BCFE),
    onTertiary = Color(0xFF37265D),
    tertiaryContainer = Color(0xFF4E3D75),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF19110D),
    onBackground = Color(0xFFF0DFD8),
    surface = Color(0xFF19110D),
    onSurface = Color(0xFFF0DFD8),
    surfaceVariant = Color(0xFF52443D),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color(0xFF130C08),
    surfaceContainerLow = Color(0xFF211915),
    surfaceContainer = Color(0xFF261E19),
    surfaceContainerHigh = Color(0xFF312823),
    surfaceContainerHighest = Color(0xFF3C332D),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF52443D)
)

private val ForestLight = lightColorScheme(
    primary = Color(0xFF3B6939),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCF0B4),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    background = Color(0xFFEDF4E7),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFEDF4E7),
    onSurface = Color(0xFF181D17),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFCFDF6),
    surfaceContainerHigh = Color(0xFFF1F7EB),
    surfaceContainerHighest = Color(0xFFE5EBDF),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD)
)

private val ForestDark = darkColorScheme(
    primary = Color(0xFFA1D399),
    onPrimary = Color(0xFF0A390F),
    primaryContainer = Color(0xFF235024),
    onPrimaryContainer = Color(0xFFBCF0B4),
    secondary = Color(0xFFBACCB3),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1E4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE0E4DA),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE0E4DA),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFF0B0F0A),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF272B25),
    surfaceContainerHighest = Color(0xFF313630),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940)
)

private val PetalLight = lightColorScheme(
    primary = Color(0xFFD81B60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF201A1C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF201A1C),
    surfaceVariant = Color(0xFFF2DDE1),
    onSurfaceVariant = Color(0xFF514347),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFFCEAEF),
    surfaceContainerHigh = Color(0xFFF6E4E9),
    surfaceContainerHighest = Color(0xFFF0DEE3),
    outline = Color(0xFF847377),
    outlineVariant = Color(0xFFD6C2C5)
)

private val PetalDark = darkColorScheme(
    primary = Color(0xFFFFB0C8),
    onPrimary = Color(0xFF65002F),
    primaryContainer = Color(0xFF8E0045),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE3BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290B),
    tertiaryContainer = Color(0xFF623F1F),
    onTertiaryContainer = Color(0xFFFFDCC1),
    background = Color(0xFF181113),
    onBackground = Color(0xFFECDFE1),
    surface = Color(0xFF181113),
    onSurface = Color(0xFFECDFE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD6C2C5),
    surfaceContainerLowest = Color(0xFF130C0E),
    surfaceContainerLow = Color(0xFF20191B),
    surfaceContainer = Color(0xFF251D20),
    surfaceContainerHigh = Color(0xFF30282A),
    surfaceContainerHighest = Color(0xFF3B3235),
    outline = Color(0xFF9F8C90),
    outlineVariant = Color(0xFF514347)
)

private val DefaultLight = lightColorScheme(
    primary = Color(0xFF4D568D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF06114E),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F)
)

private val DefaultDark = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF1D285C),
    primaryContainer = Color(0xFF353D74),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF46263C),
    tertiaryContainer = Color(0xFF5F3C53),
    onTertiaryContainer = Color(0xFFFFD8EE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF0D0E11),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF16171B),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF424450),
    onSurfaceVariant = Color(0xFFC4C6D0)
)

val PetalPalettes: List<PetalPalette> = listOf(
    PetalPalette("default", "Indigo", Color(0xFF4D568D), DefaultLight, DefaultDark),
    PetalPalette("petal", "Petal Pink", Color(0xFFD81B60), PetalLight, PetalDark),
    PetalPalette("tide", "Tide", Color(0xFF00A88E), TideLight, TideDark),
    PetalPalette("zen", "Zen", Color(0xFF445E91), ZenLight, ZenDark),
    PetalPalette("ember", "Ember", Color(0xFFB4552D), EmberLight, EmberDark),
    PetalPalette("forest", "Forest", Color(0xFF3B6939), ForestLight, ForestDark)
)

fun paletteById(id: String): PetalPalette = PetalPalettes.firstOrNull { it.id == id } ?: PetalPalettes.first()

/**
 * Plain-Int ARGB accessors for non-Compose Java callers (e.g. AppWidgetProvider).
 * ColorScheme's properties and Color.toArgb() are Kotlin-only APIs that aren't safe
 * to call directly from Java, so this does the conversion here and hands back an Int.
 */
object WidgetColors {
    @JvmStatic
    fun primaryArgb(paletteId: String, isDark: Boolean): Int {
        val scheme = paletteById(paletteId).let { if (isDark) it.dark else it.light }
        return scheme.primary.toArgb()
    }

    @JvmStatic
    fun secondaryArgb(paletteId: String, isDark: Boolean): Int {
        val scheme = paletteById(paletteId).let { if (isDark) it.dark else it.light }
        return scheme.secondary.toArgb()
    }

    @JvmStatic
    fun tertiaryArgb(paletteId: String, isDark: Boolean): Int {
        val scheme = paletteById(paletteId).let { if (isDark) it.dark else it.light }
        return scheme.tertiary.toArgb()
    }

    @JvmStatic
    fun onSurfaceVariantArgb(paletteId: String, isDark: Boolean): Int {
        val scheme = paletteById(paletteId).let { if (isDark) it.dark else it.light }
        return scheme.onSurfaceVariant.toArgb()
    }
}

