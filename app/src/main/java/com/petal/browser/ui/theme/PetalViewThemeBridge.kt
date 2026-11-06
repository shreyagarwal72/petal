package com.petal.browser.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.preference.PreferenceManager
import com.google.android.material.color.ColorResourcesOverride

/**
 * Makes the legacy View system (MaterialAlertDialogBuilder dialogs, PopupMenu, Snackbar,
 * BottomSheetDialog, XML layouts, Settings activities) follow the SAME colour scheme that
 * [PetalExpressiveTheme] resolves for Compose: palette, dynamic colour, colour style,
 * palette mix, expressive colours and AMOLED.
 *
 * How it works: `AppTheme` maps every Material colour attribute to a `@color/md_theme_*`
 * resource. On API 30+ we override those resources at runtime with [ColorResourcesOverride]
 * using the live Compose scheme, so every `?attr/colorXxx` in XML, drawables and Material
 * widgets resolves to the Compose colours. Below API 30 the static Petal palette is used.
 *
 * The last scheme is persisted so activities created before Compose has composed
 * (Settings, viewers) start with the right colours instead of flashing the defaults.
 */
object PetalViewThemeBridge {

    private const val PREFS_KEY = "sp_view_theme_bridge_v1"

    @Volatile
    private var current: IntArray? = null

    @Volatile
    private var revision = 0

    private val tokenNames = arrayOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "error", "onError", "errorContainer", "onErrorContainer",
        "background", "onBackground", "surface", "onSurface",
        "surfaceVariant", "onSurfaceVariant", "outline", "outlineVariant",
        "inverseSurface", "inverseOnSurface", "inversePrimary",
        "primaryFixed", "onPrimaryFixed", "primaryFixedDim", "onPrimaryFixedVariant",
        "secondaryFixed", "onSecondaryFixed", "secondaryFixedDim", "onSecondaryFixedVariant",
        "tertiaryFixed", "onTertiaryFixed", "tertiaryFixedDim", "onTertiaryFixedVariant",
        "surfaceDim", "surfaceBright",
        "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
        "surfaceContainerHigh", "surfaceContainerHighest"
    )

    private fun ColorScheme.toColorList(): List<Color> = listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer,
        secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
        error, onError, errorContainer, onErrorContainer,
        background, onBackground, surface, onSurface,
        surfaceVariant, onSurfaceVariant, outline, outlineVariant,
        inverseSurface, inverseOnSurface, inversePrimary,
        primaryFixed, onPrimaryFixed, primaryFixedDim, onPrimaryFixedVariant,
        secondaryFixed, onSecondaryFixed, secondaryFixedDim, onSecondaryFixedVariant,
        tertiaryFixed, onTertiaryFixed, tertiaryFixedDim, onTertiaryFixedVariant,
        surfaceDim, surfaceBright,
        surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest
    )

    /**
     * Called from [PetalExpressiveTheme] every time the final scheme is resolved.
     * Cheap when nothing changed.
     */
    @JvmStatic
    fun publish(context: Context, scheme: ColorScheme) {
        val flat = scheme.toColorList().map { it.toArgb() }.toIntArray()
        val old = current
        if (old != null && old.contentEquals(flat)) return
        current = flat
        revision++
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREFS_KEY, flat.joinToString(","))
                .apply()
        } catch (_: Throwable) {
        }
        (context as? Activity)?.let { apply(it) } ?: run {
            var c: Context? = context
            while (c is android.content.ContextWrapper) {
                if (c is Activity) { apply(c); break }
                c = c.baseContext
            }
        }
    }

    private fun load(context: Context): IntArray? {
        current?.let { return it }
        return try {
            val raw = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREFS_KEY, null) ?: return null
            val parts = raw.split(",")
            if (parts.size != tokenNames.size) return null
            IntArray(parts.size) { parts[it].toInt() }.also { current = it }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Applies the live scheme to this activity's resources. Call from onCreate (after
     * setTheme) and it is re-run automatically whenever the Compose scheme changes.
     * Returns true when the override is active (API 30+), in which case callers must NOT
     * also apply Material's DynamicColors overlay (it would win over the override).
     */
    @JvmStatic
    fun apply(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val colors = load(activity) ?: return false
        return try {
            val map = HashMap<Int, Int>(tokenNames.size)
            for (i in tokenNames.indices) {
                val id = activity.resources.getIdentifier(
                    "md_theme_" + tokenNames[i], "color", activity.packageName
                )
                if (id != 0) map[id] = colors[i]
            }
            ColorResourcesOverride.getInstance().applyIfPossible(activity, map)
        } catch (_: Throwable) {
            false
        }
    }

    /** True once a scheme has been published or restored from disk. */
    @JvmStatic
    fun hasScheme(context: Context): Boolean = load(context) != null
}
