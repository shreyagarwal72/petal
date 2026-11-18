package com.petal.browser.appicon

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.preference.PreferenceManager
import com.petal.browser.R

/**
 * The launcher icons a user can pick in Settings > App Icon.
 *
 * Each entry maps to one `<activity-alias>` in AndroidManifest.xml (exactly one is enabled at a time and
 * owns the LAUNCHER entry), an adaptive icon in `mipmap-anydpi-v26`, and its own animated launch theme.
 * All of them take their colours from `@color/petal_icon_*`, so Material You recolours the whole icon.
 */
enum class PetalAppIcon(
    val id: String,
    val aliasName: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    /** Adaptive launcher icon (also used for the in-app picker preview). */
    val launcherRes: Int,
    @StyleRes val splashTheme: Int,
    /** Animated launch mark; the same drawable the system splash plays. */
    @DrawableRes val introRes: Int,
) {
    BLOOM(
        "bloom", "com.petal.browser.appicon.Bloom",
        R.string.app_icon_bloom, R.string.app_icon_bloom_desc,
        R.mipmap.ic_launcher, R.style.Theme_Petal_Splash_Bloom, R.drawable.ic_splash_bloom_animated
    ),
    SWIRL(
        "swirl", "com.petal.browser.appicon.Swirl",
        R.string.app_icon_swirl, R.string.app_icon_swirl_desc,
        R.mipmap.ic_launcher_swirl, R.style.Theme_Petal_Splash_Swirl, R.drawable.ic_splash_petal_animated
    ),
    TABS(
        "tabs", "com.petal.browser.appicon.Tabs",
        R.string.app_icon_tabs, R.string.app_icon_tabs_desc,
        R.mipmap.ic_launcher_tabs, R.style.Theme_Petal_Splash_Tabs, R.drawable.ic_splash_tabs_animated
    ),
    PETAL_P(
        "p", "com.petal.browser.appicon.P",
        R.string.app_icon_p, R.string.app_icon_p_desc,
        R.mipmap.ic_launcher_p, R.style.Theme_Petal_Splash_P, R.drawable.ic_splash_p_animated
    ),
    LENS(
        "lens", "com.petal.browser.appicon.Lens",
        R.string.app_icon_lens, R.string.app_icon_lens_desc,
        R.mipmap.ic_launcher_lens, R.style.Theme_Petal_Splash_Lens, R.drawable.ic_splash_lens_animated
    ),
    SHIELD(
        "shield", "com.petal.browser.appicon.Shield",
        R.string.app_icon_shield, R.string.app_icon_shield_desc,
        R.mipmap.ic_launcher_shield, R.style.Theme_Petal_Splash_Shield, R.drawable.ic_splash_shield_animated
    );

    companion object {
        /** Flat Bloom is the default and the only alias that is enabled in the manifest. */
        val DEFAULT = BLOOM

        fun fromId(id: String?): PetalAppIcon = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Reads and switches the launcher icon, and keeps the launch (splash) animation in step with it. */
object PetalAppIcons {

    private const val PREF_SPLASH_SYNCED = "sp_app_icon_splash_synced"
    private const val PREF_SPLASH_VERSION = "sp_app_icon_splash_version"
    /** Bump to force every install to re-apply the splash theme once after an update. */
    private const val SPLASH_VERSION = 2

    private fun component(context: Context, icon: PetalAppIcon) =
        ComponentName(context.packageName, icon.aliasName)

    /**
     * The icon that is currently enabled. PackageManager is the source of truth (not a preference), so
     * this stays correct after backups, restores and reinstalls.
     */
    @JvmStatic
    fun current(context: Context): PetalAppIcon {
        val pm = context.packageManager
        val states = PetalAppIcon.values().associateWith { icon ->
            runCatching { pm.getComponentEnabledSetting(component(context, icon)) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        }
        states.entries.firstOrNull { it.value == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
            ?.let { return it.key }
        // Nothing explicitly enabled: the manifest default (Bloom) is the live one.
        return PetalAppIcon.DEFAULT
    }

    /**
     * Enables [icon] and disables the others. The new alias is enabled first so there is never a moment
     * without a launcher entry. DONT_KILL_APP keeps Petal running (and the current tabs alive).
     */
    @JvmStatic
    fun apply(context: Context, icon: PetalAppIcon): Boolean {
        val pm = context.packageManager
        return try {
            pm.setComponentEnabledSetting(
                component(context, icon),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            PetalAppIcon.values().filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    component(context, it),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Android 13+ lets an app choose the theme (and so the animated icon) of its NEXT cold-start splash.
     * The manifest theme cannot differ per activity-alias, so this is how each icon gets its own intro.
     * On Android 12 the default Bloom animation is always used; before 12 the compat splash shows the
     * static Bloom mark.
     */
    @JvmStatic
    @JvmOverloads
    fun syncSplashTheme(activity: Activity, icon: PetalAppIcon = current(activity), force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        // The pref alone is not trusted: a restore/reinstall resets the system-side theme while the pref
        // survives, so the theme is re-applied when forced or after an app update (SPLASH_VERSION).
        if (!force && sp.getString(PREF_SPLASH_SYNCED, null) == icon.id &&
            sp.getInt(PREF_SPLASH_VERSION, 0) == SPLASH_VERSION) return
        runCatching {
            activity.splashScreen.setSplashScreenTheme(icon.splashTheme)
            sp.edit().putString(PREF_SPLASH_SYNCED, icon.id).putInt(PREF_SPLASH_VERSION, SPLASH_VERSION).apply()
        }
    }

    @JvmStatic
    fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
