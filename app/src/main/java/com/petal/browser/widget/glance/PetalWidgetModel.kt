package com.petal.browser.widget.glance

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import com.petal.browser.R
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * The three Petal home-screen widgets. Each one keeps its own saved customization (see [PetalWidgetConfigStore]),
 * the same way the reference app keeps one saved design per widget design.
 */
enum class PetalWidgetKind(
    val key: String,
    @StringRes val nameRes: Int,
    /** How many action buttons the bar can hold. */
    val barSlots: Int,
    /** How many shortcut tiles the widget shows when it is tall enough (0 = no tiles). */
    val tileSlots: Int
) {
    PETAL1("petal1", R.string.widget_search_petal1_name, barSlots = 3, tileSlots = 4),
    PETAL2("petal2", R.string.widget_search_petal2_name, barSlots = 3, tileSlots = 0),
    PETAL3("petal3", R.string.widget_search_petal3_name, barSlots = 2, tileSlots = 0);

    /** The widget's receiver, for pinning it from the settings screen. */
    val receiverClass: Class<*>
        get() = when (this) {
            PETAL1 -> PetalSearchPetal1WidgetReceiver::class.java
            PETAL2 -> PetalSearchPetal2WidgetReceiver::class.java
            PETAL3 -> PetalSearchPetal3WidgetReceiver::class.java
        }

    companion object {
        fun fromKey(key: String?): PetalWidgetKind = entries.firstOrNull { it.key == key } ?: PETAL1
    }
}

/** Everything a widget button or tile can do. [NONE] leaves the slot empty. */
enum class PetalWidgetAction(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val intentAction: String?
) {
    SEARCH("search", R.string.ui_search, R.drawable.widget_ic_search, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH),
    AI("ai", R.string.ui_petal_ai_search, R.drawable.widget_ic_auto_awesome, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH),
    VOICE("voice", R.string.ui_voice_search_2, R.drawable.widget_ic_mic, PetalSearchWidgetProvider.ACTION_OPEN_VOICE),
    LENS("lens", R.string.ui_visual_camera_scanner, R.drawable.widget_ic_lens_camera, PetalSearchWidgetProvider.ACTION_OPEN_SNAP_CAMERA),
    NEW_TAB("new_tab", R.string.ui_new_tab, R.drawable.widget_ic_tab_plus, PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB),
    BOOKMARKS("bookmarks", R.string.ui_bookmarks, R.drawable.widget_ic_bookmark, PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS),
    DOWNLOADS("downloads", R.string.ui_downloads, R.drawable.ic_download, PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS),
    INCOGNITO("incognito", R.string.ui_private, R.drawable.widget_ic_incognito, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO),
    NONE("none", R.string.pw_action_none, 0, null);

    val isEmpty: Boolean get() = this == NONE

    companion object {
        fun fromId(id: String?): PetalWidgetAction = entries.firstOrNull { it.id == id } ?: NONE

        /** What the pickers offer for a slot (search is the bar itself, so it's not a slot action). */
        val pickable: List<PetalWidgetAction> = listOf(AI, VOICE, LENS, NEW_TAB, BOOKMARKS, DOWNLOADS, INCOGNITO, NONE)
    }
}

/**
 * Material 3 Expressive shapes the widgets can use for the badge, the buttons and the tiles. The cookies come first
 * because they are the widgets' signature; the polygons are the real [MaterialShapes] ones.
 */
enum class PetalWidgetShape(val id: String, val label: String) {
    COOKIE_9("cookie9", "Cookie 9"),
    COOKIE_12("cookie12", "Cookie 12"),
    COOKIE_7("cookie7", "Cookie 7"),
    COOKIE_6("cookie6", "Cookie 6"),
    COOKIE_4("cookie4", "Cookie 4"),
    CLOVER_4("clover4", "Clover 4"),
    CLOVER_8("clover8", "Clover 8"),
    SOFT_BURST("soft_burst", "Soft burst"),
    SUNNY("sunny", "Sunny"),
    VERY_SUNNY("very_sunny", "Very sunny"),
    FLOWER("flower", "Flower"),
    PUFFY("puffy", "Puffy"),
    CIRCLE("circle", "Circle"),
    SQUARE("square", "Square");

    fun polygon(): RoundedPolygon = when (this) {
        COOKIE_9 -> MaterialShapes.Cookie9Sided
        COOKIE_12 -> MaterialShapes.Cookie12Sided
        COOKIE_7 -> MaterialShapes.Cookie7Sided
        COOKIE_6 -> MaterialShapes.Cookie6Sided
        COOKIE_4 -> MaterialShapes.Cookie4Sided
        CLOVER_4 -> MaterialShapes.Clover4Leaf
        CLOVER_8 -> MaterialShapes.Clover8Leaf
        SOFT_BURST -> MaterialShapes.SoftBurst
        SUNNY -> MaterialShapes.Sunny
        VERY_SUNNY -> MaterialShapes.VerySunny
        FLOWER -> MaterialShapes.Flower
        PUFFY -> MaterialShapes.Puffy
        CIRCLE -> MaterialShapes.Circle
        SQUARE -> MaterialShapes.Square
    }

    companion object {
        fun fromId(id: String?, default: PetalWidgetShape): PetalWidgetShape =
            entries.firstOrNull { it.id == id } ?: default
    }
}

enum class PetalColorMode { APP_THEME, CUSTOM }

/** Corner radius steps for the bar, in dp; [RADIUS_FULL] is a fully round pill at any height. */
const val PETAL_RADIUS_FULL = 100
val PETAL_RADIUS_STEPS: List<Int> = (0..40 step 4).toList() + PETAL_RADIUS_FULL
const val PETAL_MAX_OUTLINE = 4
const val PETAL_MAX_TRANSPARENCY = 60

/**
 * Everything the user customizes for one widget. Color ints use 0 for "follow the theme".
 * Hue is 0..360, saturation and brightness are 0..100, transparency is 0..[PETAL_MAX_TRANSPARENCY] (percent).
 */
data class PetalWidgetConfig(
    val kind: PetalWidgetKind,
    val colorMode: PetalColorMode = PetalColorMode.APP_THEME,
    val hue: Float = 335f,
    val saturation: Float = 70f,
    val brightness: Float = 85f,
    val transparency: Int = 0,
    val cornerRadius: Int = PETAL_RADIUS_FULL,
    val outline: Int = 0,
    val showBadge: Boolean = true,
    val badgeText: String = "P",
    val badgeShape: PetalWidgetShape = PetalWidgetShape.COOKIE_9,
    val buttonShape: PetalWidgetShape = PetalWidgetShape.COOKIE_12,
    val tileShape: PetalWidgetShape = PetalWidgetShape.COOKIE_9,
    /** Empty means the widget's built-in hint. */
    val hint: String = "",
    val barActions: List<PetalWidgetAction> = emptyList(),
    val tiles: List<PetalWidgetAction> = emptyList(),
    val syncColors: Boolean = false,
    val syncColor: Int = 0,
    val badgeColor: Int = 0,
    val barColors: List<Int> = List(4) { 0 },
    val tileColors: List<Int> = List(4) { 0 }
) {
    /** The seed the Custom color mode feeds the Material color algorithm. */
    val seedColor: Int
        get() = AndroidColor.HSVToColor(
            floatArrayOf(
                hue.coerceIn(0f, 360f),
                (saturation / 100f).coerceIn(0f, 1f),
                (brightness / 100f).coerceIn(0.25f, 1f)
            )
        )

    @StringRes
    fun hintRes(): Int = if (kind == PetalWidgetKind.PETAL3) R.string.ui_search_or_type_url_2 else R.string.ui_search

    /** The action for bar slot [index], honouring the widget's slot count. */
    fun barAction(index: Int): PetalWidgetAction = barActions.getOrNull(index) ?: PetalWidgetAction.NONE
    fun tile(index: Int): PetalWidgetAction = tiles.getOrNull(index) ?: PetalWidgetAction.NONE

    fun barColor(index: Int): Int = if (syncColors) syncColor else barColors.getOrElse(index) { 0 }
    fun tileColor(index: Int): Int = if (syncColors) syncColor else tileColors.getOrElse(index) { 0 }

    companion object {
        fun defaults(kind: PetalWidgetKind): PetalWidgetConfig = when (kind) {
            PetalWidgetKind.PETAL1 -> PetalWidgetConfig(
                kind = kind,
                badgeShape = PetalWidgetShape.COOKIE_9,
                buttonShape = PetalWidgetShape.COOKIE_12,
                tileShape = PetalWidgetShape.COOKIE_9,
                barActions = listOf(PetalWidgetAction.AI, PetalWidgetAction.VOICE, PetalWidgetAction.LENS),
                tiles = listOf(
                    PetalWidgetAction.NEW_TAB, PetalWidgetAction.BOOKMARKS,
                    PetalWidgetAction.DOWNLOADS, PetalWidgetAction.INCOGNITO
                )
            )
            PetalWidgetKind.PETAL2 -> PetalWidgetConfig(
                kind = kind,
                showBadge = false,
                buttonShape = PetalWidgetShape.COOKIE_9,
                barActions = listOf(PetalWidgetAction.AI, PetalWidgetAction.INCOGNITO, PetalWidgetAction.LENS)
            )
            PetalWidgetKind.PETAL3 -> PetalWidgetConfig(
                kind = kind,
                badgeShape = PetalWidgetShape.COOKIE_12,
                buttonShape = PetalWidgetShape.COOKIE_9,
                barActions = listOf(PetalWidgetAction.AI, PetalWidgetAction.NONE)
            )
        }
    }
}

/** Reads and writes [PetalWidgetConfig]s. The only place that knows the key names. */
object PetalWidgetConfigStore {
    private const val PREFS = "petal_widget_config"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSaved(context: Context, kind: PetalWidgetKind): Boolean =
        prefs(context).getBoolean("${kind.key}_saved", false)

    fun load(context: Context, kind: PetalWidgetKind): PetalWidgetConfig {
        val d = PetalWidgetConfig.defaults(kind)
        val p = prefs(context)
        if (!p.getBoolean("${kind.key}_saved", false)) return d
        val k = kind.key

        fun actions(key: String, size: Int, fallback: List<PetalWidgetAction>): List<PetalWidgetAction> {
            val raw = p.getString("${k}_$key", null) ?: return fallback
            val parsed = raw.split(",").map { PetalWidgetAction.fromId(it.trim()) }
            return List(size) { parsed.getOrNull(it) ?: PetalWidgetAction.NONE }
        }
        fun colors(key: String): List<Int> {
            val raw = p.getString("${k}_$key", null) ?: return List(4) { 0 }
            val parsed = raw.split(",").map { it.trim().toIntOrNull() ?: 0 }
            return List(4) { parsed.getOrNull(it) ?: 0 }
        }

        return d.copy(
            colorMode = if (p.getString("${k}_color_mode", null) == "custom") PetalColorMode.CUSTOM else PetalColorMode.APP_THEME,
            hue = p.getFloat("${k}_hue", d.hue).coerceIn(0f, 360f),
            saturation = p.getFloat("${k}_saturation", d.saturation).coerceIn(0f, 100f),
            brightness = p.getFloat("${k}_brightness", d.brightness).coerceIn(0f, 100f),
            transparency = p.getInt("${k}_transparency", d.transparency).coerceIn(0, PETAL_MAX_TRANSPARENCY),
            cornerRadius = p.getInt("${k}_corner", d.cornerRadius).let { v ->
                PETAL_RADIUS_STEPS.minByOrNull { kotlin.math.abs(it - v) } ?: d.cornerRadius
            },
            outline = p.getInt("${k}_outline", d.outline).coerceIn(0, PETAL_MAX_OUTLINE),
            showBadge = p.getBoolean("${k}_show_badge", d.showBadge),
            badgeText = (p.getString("${k}_badge_text", d.badgeText) ?: d.badgeText).take(2),
            badgeShape = PetalWidgetShape.fromId(p.getString("${k}_badge_shape", null), d.badgeShape),
            buttonShape = PetalWidgetShape.fromId(p.getString("${k}_button_shape", null), d.buttonShape),
            tileShape = PetalWidgetShape.fromId(p.getString("${k}_tile_shape", null), d.tileShape),
            hint = p.getString("${k}_hint", d.hint) ?: d.hint,
            barActions = actions("bar_actions", kind.barSlots, d.barActions),
            tiles = actions("tiles", kind.tileSlots, d.tiles),
            syncColors = p.getBoolean("${k}_sync_colors", false),
            syncColor = p.getInt("${k}_sync_color", 0),
            badgeColor = p.getInt("${k}_badge_color", 0),
            barColors = colors("bar_colors"),
            tileColors = colors("tile_colors")
        )
    }

    fun save(context: Context, cfg: PetalWidgetConfig) {
        val k = cfg.kind.key
        prefs(context).edit()
            .putBoolean("${k}_saved", true)
            .putString("${k}_color_mode", if (cfg.colorMode == PetalColorMode.CUSTOM) "custom" else "app")
            .putFloat("${k}_hue", cfg.hue)
            .putFloat("${k}_saturation", cfg.saturation)
            .putFloat("${k}_brightness", cfg.brightness)
            .putInt("${k}_transparency", cfg.transparency)
            .putInt("${k}_corner", cfg.cornerRadius)
            .putInt("${k}_outline", cfg.outline)
            .putBoolean("${k}_show_badge", cfg.showBadge)
            .putString("${k}_badge_text", cfg.badgeText.take(2))
            .putString("${k}_badge_shape", cfg.badgeShape.id)
            .putString("${k}_button_shape", cfg.buttonShape.id)
            .putString("${k}_tile_shape", cfg.tileShape.id)
            .putString("${k}_hint", cfg.hint)
            .putString("${k}_bar_actions", cfg.barActions.joinToString(",") { it.id })
            .putString("${k}_tiles", cfg.tiles.joinToString(",") { it.id })
            .putBoolean("${k}_sync_colors", cfg.syncColors)
            .putInt("${k}_sync_color", cfg.syncColor)
            .putInt("${k}_badge_color", cfg.badgeColor)
            .putString("${k}_bar_colors", cfg.barColors.joinToString(","))
            .putString("${k}_tile_colors", cfg.tileColors.joinToString(","))
            .apply()
        PetalWidgetShapes.clear()
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }

    fun reset(context: Context, kind: PetalWidgetKind) {
        val k = kind.key
        val p = prefs(context)
        val editor = p.edit()
        p.all.keys.filter { it.startsWith("${k}_") }.forEach { editor.remove(it) }
        editor.apply()
        PetalWidgetShapes.clear()
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }
}
