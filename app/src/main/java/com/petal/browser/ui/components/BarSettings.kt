package com.petal.browser.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

/** Address bar height. Stored as the raw [pref] string so existing preferences and backups keep working. */
enum class AddressBarHeight(val pref: String, val barDp: Int, val cornerDp: Int, val verticalMarginDp: Int) {
    COMPACT("COMPACT", 50, 24, 3),
    STANDARD("STANDARD", 56, 28, 5);

    companion object {
        /** The single default for every consumer (bars, settings, root layout). */
        val DEFAULT = COMPACT

        @JvmStatic
        fun fromPref(raw: String?): AddressBarHeight =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

enum class AddressBarPosition(val pref: String) {
    TOP("TOP"),
    BOTTOM("BOTTOM");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): AddressBarPosition =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: TOP
    }
}

enum class BarCollapseStyle(val pref: String) {
    PILL("PILL"),
    HIDE("HIDE");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): BarCollapseStyle =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: PILL
    }
}

enum class AddressBarStyle(val pref: String) {
    CLASSIC("CLASSIC"),
    MERGE("MERGE");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): AddressBarStyle =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: CLASSIC
    }
}

enum class MergeLayout(val pref: String) {
    SINGLE("SINGLE"),
    STACKED("STACKED");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): MergeLayout =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: SINGLE
    }
}

enum class UrlDisplayMode(val pref: String) {
    FULL("FULL"),
    COMPACT("COMPACT"),
    DOMAIN_ONLY("DOMAIN_ONLY");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): UrlDisplayMode =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: FULL
    }
}

enum class BarProgressStyle(val pref: String) {
    LINE("LINE"),
    HIDDEN("HIDDEN");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): BarProgressStyle =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: LINE
    }
}

enum class ClassicOuterAction(val pref: String) {
    SHARE("SHARE"),
    NEW_TAB("NEW_TAB"),
    BOOKMARKS("BOOKMARKS"),
    FIND_IN_PAGE("FIND_IN_PAGE"),
    DOWNLOADS("DOWNLOADS");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): ClassicOuterAction =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: SHARE
    }
}

enum class NavSearchButtonStyle(val pref: String) {
    SEPARATE("SEPARATE"),
    INTEGRATED("INTEGRATED"),
    HIDDEN("HIDDEN");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): NavSearchButtonStyle =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: SEPARATE
    }
}

enum class NavMidSlot(val pref: String) {
    NEW_TAB("NEW_TAB"),
    BOOKMARKS("BOOKMARKS"),
    HISTORY("HISTORY"),
    DOWNLOADS("DOWNLOADS");

    companion object {
        @JvmStatic
        fun fromPref(raw: String?): NavMidSlot =
            entries.firstOrNull { it.pref.equals(raw, ignoreCase = true) } ?: NEW_TAB
    }
}

/**
 * One shared, observable source for every bar preference. Exactly one
 * [SharedPreferences.OnSharedPreferenceChangeListener] is registered for the whole app; bars read
 * these Compose state values instead of each registering their own listener.
 */
object BarSettings {
    const val KEY_HEIGHT = "sp_address_bar_height"
    const val KEY_FLOATING = "sp_floating_tab_bar"
    const val KEY_SWIPE_TABS = "sp_address_bar_swipe_tabs"
    const val KEY_QUICK_ACTIONS = "sp_address_bar_quick_actions"
    const val KEY_ACTION = "sp_address_bar_action"
    const val KEY_AI_LEGACY = "sp_ai_search_address_bar"
    const val KEY_MEDIA_BUTTON = "sp_media_button_address_bar"
    const val KEY_SHOW_NAV_LABELS = "sp_show_nav_labels"

    var height by mutableStateOf(AddressBarHeight.DEFAULT); private set
    var position by mutableStateOf(AddressBarPosition.TOP); private set
    var collapseStyle by mutableStateOf(BarCollapseStyle.PILL); private set
    var style by mutableStateOf(AddressBarStyle.CLASSIC); private set
    var mergeLayout by mutableStateOf(MergeLayout.SINGLE); private set
    var floatingNav by mutableStateOf(true); private set
    var showNavLabels by mutableStateOf(true); private set
    var swipeTabs by mutableStateOf(true); private set
    var quickActions by mutableStateOf(true); private set
    var mediaButton by mutableStateOf(true); private set
    var hideBarsOnScroll by mutableStateOf(true); private set
    var extensionChips by mutableStateOf(true); private set
    var urlDisplayMode by mutableStateOf(UrlDisplayMode.FULL); private set
    var progressStyle by mutableStateOf(BarProgressStyle.LINE); private set
    var classicOuterAction by mutableStateOf(ClassicOuterAction.SHARE); private set
    var classicShowReload by mutableStateOf(true); private set
    var navSearchStyle by mutableStateOf(NavSearchButtonStyle.SEPARATE); private set
    var navSwipeTabs by mutableStateOf(true); private set
    var navShowOnPages by mutableStateOf(true); private set
    var navMidSlot by mutableStateOf(NavMidSlot.NEW_TAB); private set
    var contextAction by mutableStateOf("AI"); private set
    var mergeActions by mutableStateOf<Pair<List<BarAction>, List<BarAction>>>(emptyList<BarAction>() to emptyList()); private set

    private var prefs: SharedPreferences? = null

    // Held strongly: SharedPreferences only keeps weak references to its listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == null) readAll(p) else read(p, key)
    }

    @Synchronized
    fun attach(context: Context) {
        if (prefs != null) return
        val p = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs = p
        readAll(p)
        p.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readAll(p: SharedPreferences) {
        listOf(
            KEY_HEIGHT, BarPrefs.KEY_ADDRESS_BAR_POSITION, BarPrefs.KEY_BAR_COLLAPSE_STYLE,
            BarPrefs.KEY_ADDRESS_BAR_STYLE, BarPrefs.KEY_MERGE_LAYOUT, KEY_FLOATING,
            KEY_SHOW_NAV_LABELS, KEY_SWIPE_TABS, KEY_QUICK_ACTIONS, KEY_MEDIA_BUTTON,
            BarPrefs.KEY_HIDE_BARS_ON_SCROLL, BarPrefs.KEY_EXTENSION_CHIPS,
            BarPrefs.KEY_URL_DISPLAY_MODE, BarPrefs.KEY_PROGRESS_STYLE,
            BarPrefs.KEY_CLASSIC_OUTER_ACTION, BarPrefs.KEY_CLASSIC_SHOW_RELOAD,
            BarPrefs.KEY_NAV_SEARCH_STYLE, BarPrefs.KEY_NAV_SWIPE_TABS,
            BarPrefs.KEY_NAV_SHOW_ON_PAGES, BarPrefs.KEY_NAV_MID_SLOT,
            KEY_ACTION, BarPrefs.KEY_MERGE_LEADING, BarPrefs.KEY_MERGE_TRAILING
        ).forEach { read(p, it) }
    }

    private fun read(p: SharedPreferences, key: String) {
        when (key) {
            KEY_HEIGHT -> height = AddressBarHeight.fromPref(p.getString(KEY_HEIGHT, null))
            BarPrefs.KEY_ADDRESS_BAR_POSITION ->
                position = AddressBarPosition.fromPref(p.getString(key, null))
            BarPrefs.KEY_BAR_COLLAPSE_STYLE ->
                collapseStyle = BarCollapseStyle.fromPref(p.getString(key, null))
            BarPrefs.KEY_ADDRESS_BAR_STYLE -> style = AddressBarStyle.fromPref(p.getString(key, null))
            BarPrefs.KEY_MERGE_LAYOUT -> mergeLayout = MergeLayout.fromPref(p.getString(key, null))
            KEY_FLOATING -> floatingNav = p.getBoolean(key, true)
            KEY_SHOW_NAV_LABELS -> showNavLabels = p.getBoolean(key, true)
            KEY_SWIPE_TABS -> swipeTabs = p.getBoolean(key, true)
            KEY_QUICK_ACTIONS -> quickActions = p.getBoolean(key, true)
            KEY_MEDIA_BUTTON -> mediaButton = p.getBoolean(key, true)
            BarPrefs.KEY_HIDE_BARS_ON_SCROLL -> hideBarsOnScroll = p.getBoolean(key, true)
            BarPrefs.KEY_EXTENSION_CHIPS -> extensionChips = p.getBoolean(key, true)
            BarPrefs.KEY_URL_DISPLAY_MODE -> urlDisplayMode = UrlDisplayMode.fromPref(p.getString(key, null))
            BarPrefs.KEY_PROGRESS_STYLE -> progressStyle = BarProgressStyle.fromPref(p.getString(key, null))
            BarPrefs.KEY_CLASSIC_OUTER_ACTION -> classicOuterAction = ClassicOuterAction.fromPref(p.getString(key, null))
            BarPrefs.KEY_CLASSIC_SHOW_RELOAD -> classicShowReload = p.getBoolean(key, true)
            BarPrefs.KEY_NAV_SEARCH_STYLE -> navSearchStyle = NavSearchButtonStyle.fromPref(p.getString(key, null))
            BarPrefs.KEY_NAV_SWIPE_TABS -> navSwipeTabs = p.getBoolean(key, true)
            BarPrefs.KEY_NAV_SHOW_ON_PAGES -> navShowOnPages = p.getBoolean(key, true)
            BarPrefs.KEY_NAV_MID_SLOT -> navMidSlot = NavMidSlot.fromPref(p.getString(key, null))
            KEY_ACTION, KEY_AI_LEGACY ->
                contextAction = p.getString(KEY_ACTION, if (p.getBoolean(KEY_AI_LEGACY, true)) "AI" else "NONE") ?: "AI"
            BarPrefs.KEY_MERGE_LEADING, BarPrefs.KEY_MERGE_TRAILING ->
                mergeActions = BarPrefs.getSanitizedMergeActions(p)
        }
    }
}

/** Attaches the shared listener once and returns the observable settings. */
@Composable
fun rememberBarSettings(): BarSettings {
    val context = LocalContext.current
    remember(context) { BarSettings.attach(context) }
    return BarSettings
}
