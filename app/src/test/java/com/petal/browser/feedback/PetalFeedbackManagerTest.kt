package com.petal.browser.feedback

import android.content.SharedPreferences
import com.petal.browser.compose.settings.PetalSettingsSearchIndex
import com.petal.browser.compose.settings.SettingsCategory
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PetalFeedbackManager]:
 * - Auto-prompt cadence eligibility (launch count, install age, cooldown, max prompts, opt-out, already rated)
 * - Star rating routing (1..3 vs 4..5) and diagnostic feedback body formatting
 * - GitHub stargazer REST response parsing & cache TTL
 * - Settings search index entries for Play Store rating, GitHub stars, and feedback
 */
class PetalFeedbackManagerTest {

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

    @Test
    fun autoPromptRequiresLaunchCountAgeCooldownAndNoOptOut() {
        val sp = FakePreferences()
        val t0 = 1_700_000_000_000L

        // Fewer than MIN_LAUNCH_COUNT launches -> false
        repeat(PetalFeedbackManager.MIN_LAUNCH_COUNT - 1) {
            PetalFeedbackManager.recordLaunch(sp, t0)
        }
        val afterMinAge = t0 + PetalFeedbackManager.MIN_INSTALL_AGE_MS + 1000L
        assertFalse(PetalFeedbackManager.shouldAutoPrompt(sp, afterMinAge))

        // Hit MIN_LAUNCH_COUNT, still within MIN_INSTALL_AGE_MS -> false
        PetalFeedbackManager.recordLaunch(sp, t0 + 1000L)
        assertFalse(PetalFeedbackManager.shouldAutoPrompt(sp, t0 + 1000L))

        // Past MIN_INSTALL_AGE_MS with enough launches -> true
        assertTrue(PetalFeedbackManager.shouldAutoPrompt(sp, afterMinAge))

        // After marking shown, cooldown blocks immediate re-prompt
        PetalFeedbackManager.markPromptShown(sp, afterMinAge)
        assertFalse(PetalFeedbackManager.shouldAutoPrompt(sp, afterMinAge + 1000L))

        // Past 14d cooldown -> eligible again
        val afterCooldown = afterMinAge + PetalFeedbackManager.PROMPT_COOLDOWN_MS + 1000L
        assertTrue(PetalFeedbackManager.shouldAutoPrompt(sp, afterCooldown))

        // Opt-out blocks future prompts forever
        PetalFeedbackManager.markOptOut(sp)
        assertFalse(PetalFeedbackManager.shouldAutoPrompt(sp, afterCooldown + PetalFeedbackManager.PROMPT_COOLDOWN_MS))
    }

    @Test
    fun autoPromptStopsAfterPlayRatedOrLifetimePromptCap() {
        val sp = FakePreferences()
        val t0 = 1_700_000_000_000L
        repeat(PetalFeedbackManager.MIN_LAUNCH_COUNT) {
            PetalFeedbackManager.recordLaunch(sp, t0)
        }
        val eligibleAt = t0 + PetalFeedbackManager.MIN_INSTALL_AGE_MS + 1000L
        assertTrue(PetalFeedbackManager.shouldAutoPrompt(sp, eligibleAt))

        PetalFeedbackManager.markPlayRated(sp, 5)
        assertFalse(PetalFeedbackManager.shouldAutoPrompt(sp, eligibleAt))
        assertEquals(5, sp.getInt(PetalFeedbackManager.PREF_LAST_STAR_RATING, 0))

        // Reset rated flag and hit lifetime cap (MAX_LIFETIME_PROMPTS)
        sp.edit().putBoolean(PetalFeedbackManager.PREF_PLAY_RATED, false)
            .putInt(PetalFeedbackManager.PREF_PROMPT_COUNT, PetalFeedbackManager.MAX_LIFETIME_PROMPTS)
            .apply()
        assertFalse(
            PetalFeedbackManager.shouldAutoPrompt(
                sp,
                eligibleAt + PetalFeedbackManager.PROMPT_COOLDOWN_MS * 10
            )
        )
    }

    @Test
    fun starRoutingAndDiagnosticFeedbackFormatting() {
        assertFalse(PetalFeedbackManager.isPositiveRating(1))
        assertFalse(PetalFeedbackManager.isPositiveRating(2))
        assertFalse(PetalFeedbackManager.isPositiveRating(3))
        assertTrue(PetalFeedbackManager.isPositiveRating(4))
        assertTrue(PetalFeedbackManager.isPositiveRating(5))

        val body = PetalFeedbackManager.buildDiagnosticFeedbackBody(
            verName = "4.9",
            verCode = 490,
            stars = 2,
            userComment = "Tab strip flickers",
            buildType = "release",
            osRelease = "15",
            sdkInt = 35,
            manufacturer = "Google",
            model = "Pixel 9"
        )
        assertTrue(body.contains("Tab strip flickers"))
        assertTrue(body.contains("Rating: 2 / 5 stars"))
        assertTrue(body.contains("App Version: 4.9 (490)"))
    }

    @Test
    fun githubStarsParserAndCacheTtl() {
        val sampleJson = """{"id": 123, "name": "petal", "stargazers_count": 482, "forks_count": 19}"""
        assertEquals(482, PetalFeedbackManager.parseGitHubStarsCount(sampleJson))
        assertNull(PetalFeedbackManager.parseGitHubStarsCount("{}"))
        assertNull(PetalFeedbackManager.parseGitHubStarsCount(null))

        val sp = FakePreferences()
        val now = 1_700_000_000_000L
        PetalFeedbackManager.cacheGitHubStars(sp, 482, now)
        assertEquals(482, PetalFeedbackManager.cachedGitHubStars(sp))
        assertTrue(PetalFeedbackManager.isGitHubStarsCacheFresh(sp, now + 1000L))
        assertFalse(
            PetalFeedbackManager.isGitHubStarsCacheFresh(
                sp,
                now + PetalFeedbackManager.GITHUB_STARS_CACHE_TTL_MS + 1L
            )
        )
    }

    @Test
    fun searchIndexExposesPlayStoreGitHubStarsAndFeedbackUnderAbout() {
        val items = PetalSettingsSearchIndex.allItems
        assertTrue(items.any { it.id == "about_rate_play_store" && it.category == SettingsCategory.ABOUT })
        assertTrue(items.any { it.id == "about_star_github" && it.category == SettingsCategory.ABOUT })
        assertTrue(items.any { it.id == "about_send_feedback" && it.category == SettingsCategory.ABOUT })
    }
}
