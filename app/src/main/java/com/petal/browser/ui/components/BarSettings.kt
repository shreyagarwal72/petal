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

    var height by mutableStateOf(AddressBarHeight.DEFAULT); private set
    var position by mutableStateOf(AddressBarPosition.TOP); private set
    var collapseStyle by mutableStateOf(BarCollapseStyle.PILL); private set
    var style by mutableStateOf(AddressBarStyle.CLASSIC); private set
    var mergeLayout by mutableStateOf(MergeLayout.SINGLE); private set
    var floatingNav by mutableStateOf(true); private set
    var swipeTabs by mutableStateOf(true); private set
    var quickActions by mutableStateOf(true); private set
    var mediaButton by mutableStateOf(true); private set
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
            BarPrefs.KEY_ADDRESS_BAR_STYLE, BarPrefs.KEY_MERGE_LAYOUT, KEY_FLOATING, KEY_SWIPE_TABS,
            KEY_QUICK_ACTIONS, KEY_MEDIA_BUTTON, KEY_ACTION, BarPrefs.KEY_MERGE_LEADING
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
            KEY_SWIPE_TABS -> swipeTabs = p.getBoolean(key, true)
            KEY_QUICK_ACTIONS -> quickActions = p.getBoolean(key, true)
            KEY_MEDIA_BUTTON -> mediaButton = p.getBoolean(key, true)
            KEY_ACTION, KEY_AI_LEGACY ->
                contextAction = p.getString(KEY_ACTION, if (p.getBoolean(KEY_AI_LEGACY, true)) "AI" else "NONE") ?: "AI"
            BarPrefs.KEY_MERGE_LEADING, BarPrefs.KEY_MERGE_TRAILING ->
                mergeActions = BarPrefs.getSanitizedMergeActions(p)
        }
        if (key == BarPrefs.KEY_MERGE_LEADING) mergeActions = BarPrefs.getSanitizedMergeActions(p)
    }
}

/** Attaches the shared listener once and returns the observable settings. */
@Composable
fun rememberBarSettings(): BarSettings {
    val context = LocalContext.current
    remember(context) { BarSettings.attach(context) }
    return BarSettings
}
