package com.petal.browser.appicon

import android.content.SharedPreferences
import com.petal.browser.compose.settings.PetalSettingsSearchIndex
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.compose.splash.PetalSplashState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for launch style migration (`sp_app_launch_animation` -> `sp_launch_style`),
 * `PetalSplashState.shouldPlayLaunchSplash`, and deferred pending-icon application on stop.
 */
class LaunchAndAppIconTest {

    private class FakePreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = REMOVE_SENTINEL
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                for ((k, v) in pending) {
                    if (v === REMOVE_SENTINEL) map.remove(k) else map[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private companion object {
            val REMOVE_SENTINEL = Any()
        }
    }

    @Before
    fun resetSplashState() {
        PetalSplashState.hasShownThisSession = false
        PetalSplashState.hasTriggeredLaunchRippleThisSession = false
        PetalSplashState.splashActive.value = false
    }

    @Test
    fun legacyLaunchAnimationTrueMigratesOnceToQuantum() {
        val sp = FakePreferences().apply {
            edit().putBoolean(PetalSplashState.PREF_LEGACY_APP_LAUNCH_ANIMATION, true).apply()
        }

        val resolved = PetalSplashState.resolveLaunchStyle(sp)
        assertEquals(PetalSplashState.STYLE_QUANTUM, resolved)
        assertEquals(PetalSplashState.STYLE_QUANTUM, sp.getString(PetalSplashState.PREF_LAUNCH_STYLE, null))

        // Flipping legacy key afterwards must not re-trigger migration once sp_launch_style exists
        sp.edit().putBoolean(PetalSplashState.PREF_LEGACY_APP_LAUNCH_ANIMATION, false).apply()
        assertEquals(PetalSplashState.STYLE_QUANTUM, PetalSplashState.resolveLaunchStyle(sp))
    }

    @Test
    fun legacyLaunchAnimationFalseMigratesOnceToIntro() {
        val sp = FakePreferences().apply {
            edit().putBoolean(PetalSplashState.PREF_LEGACY_APP_LAUNCH_ANIMATION, false).apply()
        }

        val resolved = PetalSplashState.resolveLaunchStyle(sp)
        assertEquals(PetalSplashState.STYLE_INTRO, resolved)
        assertEquals(PetalSplashState.STYLE_INTRO, sp.getString(PetalSplashState.PREF_LAUNCH_STYLE, null))
    }

    @Test
    fun shouldPlayLaunchSplashOnlyWhenQuantumAndNotShownThisSession() {
        val quantumSp = FakePreferences().apply {
            edit().putString(PetalSplashState.PREF_LAUNCH_STYLE, PetalSplashState.STYLE_QUANTUM).apply()
        }
        PetalSplashState.hasShownThisSession = false
        assertTrue(PetalSplashState.shouldPlayLaunchSplash(quantumSp))

        PetalSplashState.hasShownThisSession = true
        assertFalse(PetalSplashState.shouldPlayLaunchSplash(quantumSp))

        val introSp = FakePreferences().apply {
            edit().putString(PetalSplashState.PREF_LAUNCH_STYLE, PetalSplashState.STYLE_INTRO).apply()
        }
        PetalSplashState.hasShownThisSession = false
        assertFalse(PetalSplashState.shouldPlayLaunchSplash(introSp))

        PetalSplashState.hasShownThisSession = true
        assertFalse(PetalSplashState.shouldPlayLaunchSplash(introSp))
    }

    @Test
    fun pendingIconQueuesReselectClearsAndAppliesOnStop() {
        val sp = FakePreferences()
        val active = PetalAppIcon.BLOOM

        // Queue Shield instead of applying immediately
        assertTrue(PetalAppIcons.queueIconChange(sp, active, PetalAppIcon.SHIELD))
        assertEquals(PetalAppIcon.SHIELD.id, sp.getString(PetalAppIcons.PREF_PENDING_APP_ICON, null))
        assertEquals(PetalAppIcon.SHIELD, PetalAppIcons.pending(sp))

        // Re-selecting the active icon clears sp_pending_app_icon
        assertFalse(PetalAppIcons.queueIconChange(sp, active, PetalAppIcon.BLOOM))
        assertNull(sp.getString(PetalAppIcons.PREF_PENDING_APP_ICON, null))
        assertNull(PetalAppIcons.pending(sp))

        // Queue Lens and simulate ON_STOP / stop apply
        PetalAppIcons.queueIconChange(sp, active, PetalAppIcon.LENS)
        var appliedIcon: PetalAppIcon? = null
        var syncedIcon: PetalAppIcon? = null

        val result = PetalAppIcons.applyPending(
            sp = sp,
            activeIcon = active,
            applyAlias = { icon ->
                appliedIcon = icon
                true
            },
            onApplied = { icon ->
                syncedIcon = icon
            }
        )

        assertEquals(PetalAppIcon.LENS, result)
        assertEquals(PetalAppIcon.LENS, appliedIcon)
        assertEquals(PetalAppIcon.LENS, syncedIcon)
        assertNull(sp.getString(PetalAppIcons.PREF_PENDING_APP_ICON, null))
    }

    @Test
    fun failedPendingIconApplyRetainsPendingPref() {
        val sp = FakePreferences()
        PetalAppIcons.queueIconChange(sp, PetalAppIcon.BLOOM, PetalAppIcon.GLOBE)

        val result = PetalAppIcons.applyPending(
            sp = sp,
            activeIcon = PetalAppIcon.BLOOM,
            applyAlias = { false }
        )

        assertNull(result)
        assertEquals(PetalAppIcon.GLOBE.id, sp.getString(PetalAppIcons.PREF_PENDING_APP_ICON, null))
    }

    @Test
    fun searchIndexRoutesLaunchStyleAndRippleToAppIconCategory() {
        val items = PetalSettingsSearchIndex.allItems
        assertTrue(items.any { it.id == "app_icon_intro" && it.category == SettingsCategory.APP_ICON })
        assertTrue(items.any { it.id == "app_icon_ripple" && it.category == SettingsCategory.APP_ICON })
    }
}
