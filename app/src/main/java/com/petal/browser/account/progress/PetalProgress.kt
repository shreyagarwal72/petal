package com.petal.browser.account.progress

import android.content.Context
import android.preference.PreferenceManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Local-only XP and levels. Nothing leaves the device, so it only unlocks cosmetics. */
object PetalXp {
    fun step(n: Int): Long = 50L + 10L * n + n.toLong() * n
    fun start(level: Int): Long = (1 until level).sumOf { step(it) }
    fun level(xp: Long): Int {
        var l = 1
        var left = xp
        while (left >= step(l)) { left -= step(l); l++ }
        return l
    }
    fun progress(xp: Long): Float =
        ((xp - start(level(xp))).toFloat() / step(level(xp)).toFloat()).coerceIn(0f, 1f)
    fun title(level: Int): String = when {
        level >= 50 -> "Petal Legend"
        level >= 30 -> "Grove Keeper"
        level >= 15 -> "Bloom Master"
        level >= 5 -> "Gardener"
        else -> "Seedling"
    }

    const val DAY_MS = 86_400_000L
    const val AD_XP = 40L
    const val AD_DAILY = 10
    const val AD_GAP_MS = 30_000L
}

enum class PetalCounter(val key: String) {
    TABS_OPENED("pp_c_tabs"),
    DOWNLOADS("pp_c_downloads"),
    BOOKMARKS("pp_c_bookmarks"),
    SITES_BLOCKED("pp_c_blocked"),
    DAYS_USED("pp_c_days"),
    ADS_WATCHED("pp_c_ads"),
}

data class PetalBadge(
    val id: String,
    val title: String,
    val hint: String,
    val counter: PetalCounter? = null,
    val target: Long = 0,
    val minLevel: Int = 0,
)

val PETAL_BADGES: List<PetalBadge> = listOf(
    PetalBadge("first_steps", "First Steps", "Open 10 tabs", PetalCounter.TABS_OPENED, 10),
    PetalBadge("tab_juggler", "Tab Juggler", "Open 250 tabs", PetalCounter.TABS_OPENED, 250),
    PetalBadge("tab_titan", "Tab Titan", "Open 2,000 tabs", PetalCounter.TABS_OPENED, 2000),
    PetalBadge("collector", "Collector", "Save 10 bookmarks", PetalCounter.BOOKMARKS, 10),
    PetalBadge("librarian", "Librarian", "Save 100 bookmarks", PetalCounter.BOOKMARKS, 100),
    PetalBadge("downloader", "Downloader", "Finish 10 downloads", PetalCounter.DOWNLOADS, 10),
    PetalBadge("hoarder", "Hoarder", "Finish 100 downloads", PetalCounter.DOWNLOADS, 100),
    PetalBadge("shield_up", "Shield Up", "Block 500 trackers or ads", PetalCounter.SITES_BLOCKED, 500),
    PetalBadge("fortress", "Fortress", "Block 10,000 trackers or ads", PetalCounter.SITES_BLOCKED, 10_000),
    PetalBadge("regular", "Regular", "Use Petal on 7 different days", PetalCounter.DAYS_USED, 7),
    PetalBadge("loyal", "Loyal", "Use Petal on 30 different days", PetalCounter.DAYS_USED, 30),
    PetalBadge("supporter", "Supporter", "Watch 3 supportive ads", PetalCounter.ADS_WATCHED, 3),
    PetalBadge("patron", "Patron", "Watch 25 supportive ads", PetalCounter.ADS_WATCHED, 25),
    PetalBadge("gardener", "Gardener", "Reach level 5", minLevel = 5),
    PetalBadge("bloom_master", "Bloom Master", "Reach level 15", minLevel = 15),
    PetalBadge("grove_keeper", "Grove Keeper", "Reach level 30", minLevel = 30),
)

/**
 * Features locked behind XP levels or supporter quest contributions.
 * A feature unlocks when ANY path is met: reaching the required level, OR watching enough supporter ads, OR explicit unlock.
 * Core browsing and privacy capabilities are NEVER locked.
 */
enum class PetalUnlockable(
    val id: String,
    val title: String,
    val description: String,
    val levelNeeded: Int,
    val adsNeeded: Int,
    /** Preference key that turns the feature on. It is forced off while the feature is locked. */
    val enabledKey: String,
    val iconName: String
) {
    LIQUID_GLASS(
        id = "liquid_glass",
        title = "Liquid Glass UI",
        description = "Fluid translucent frosted glass shaders across sheets and navbars",
        levelNeeded = 2,
        adsNeeded = 1,
        enabledKey = "sp_liquid_glass_enabled",
        iconName = "BlurOn"
    ),
    CUSTOM_FONT(
        id = "custom_font",
        title = "Custom Font Import",
        description = "Import and apply custom TTF/OTF typefaces across the browser",
        levelNeeded = 3,
        adsNeeded = 2,
        enabledKey = "sp_custom_font_unlocked",
        iconName = "FontDownload"
    ),
    FONT_VARIABLES(
        id = "font_variables",
        title = "Variable Font Axes Tuning",
        description = "Fine-tune font weight, optical size, grade, slant, and roundness axes",
        levelNeeded = 4,
        adsNeeded = 3,
        enabledKey = "sp_font_variables_unlocked",
        iconName = "Tune"
    ),
    LIVE_WALLPAPER(
        id = "live_wallpaper",
        title = "Homescreen Live Wallpaper",
        description = "Apply looping video and animated wallpapers on the browser home screen",
        levelNeeded = 5,
        adsNeeded = 4,
        enabledKey = "sp_live_wallpaper_unlocked",
        iconName = "Movie"
    ),
    APPLE_DUO_EFFECT(
        id = "apple_duo",
        title = "Apple Duo 3D Fold Effect",
        description = "Gyroscopic sensor-driven 3D screen folding and perspective physics",
        levelNeeded = 6,
        adsNeeded = 5,
        enabledKey = "sp_apple_duo_unlocked",
        iconName = "ScreenRotation"
    ),
    OMNIBOX_GLOW(
        id = "omnibox_glow",
        title = "Expressive Omnibox Glow",
        description = "Ambient iridescent neon particle aura while typing in the address bar",
        levelNeeded = 7,
        adsNeeded = 6,
        enabledKey = "sp_omnibox_glow_unlocked",
        iconName = "AutoAwesome"
    ),
    EXPRESSIVE_HAPTICS(
        id = "expressive_haptics",
        title = "Liquid Haptic Profiles",
        description = "Organic tactile feedback textures and fluid sensory gestures",
        levelNeeded = 8,
        adsNeeded = 7,
        enabledKey = "sp_expressive_haptics_unlocked",
        iconName = "Vibration"
    ),
    DYNAMIC_APP_ICONS(
        id = "dynamic_app_icons",
        title = "Prestige App Icons",
        description = "Exclusive Liquid Glass, Emerald, and Aurora launcher icon designs",
        levelNeeded = 10,
        adsNeeded = 8,
        enabledKey = "sp_dynamic_app_icons_unlocked",
        iconName = "AppShortcut"
    )
}

/**
 * Botanical & Dev Support Milestones inspired by Orion Store's 'Fuel the Code (Beta)' approach.
 * Transforms third-party ad viewing into an indie developer backing journey.
 */
data class PetalSupporterMilestone(
    val level: Int,
    val rank: String,
    val title: String,
    val story: String,
    val badgeId: String? = null,
    val unlockableId: String? = null
)

val PETAL_SUPPORTER_MILESTONES: List<PetalSupporterMilestone> = listOf(
    PetalSupporterMilestone(
        level = 1,
        rank = "Sprout Backer",
        title = "First Seed Planted",
        story = "You fueled the code for the first time! The developer's greenhouse has taken root."
    ),
    PetalSupporterMilestone(
        level = 3,
        rank = "Bloom Supporter",
        title = "Supporter of the Garden",
        story = "Three sparks of support provided. You've earned the Supporter badge!",
        badgeId = "supporter"
    ),
    PetalSupporterMilestone(
        level = 8,
        rank = "Garden Guardian",
        title = "Firewall Reinforced",
        story = "You helped protect the browser against invasive trackers, corporate telemetry, and paywalls."
    ),
    PetalSupporterMilestone(
        level = 15,
        rank = "Grove Keeper",
        title = "Gecko Engine Overclocked",
        story = "The open-source web engine runs swift and pure thanks to your continued backing."
    ),
    PetalSupporterMilestone(
        level = 25,
        rank = "Petal Legend",
        title = "Eternal Patron",
        story = "Maximum tier reached! You are now an eternal patron of independent open-source development.",
        badgeId = "patron"
    )
)

object PetalSupporterQuest {
    fun currentMilestone(adsWatched: Long): PetalSupporterMilestone =
        PETAL_SUPPORTER_MILESTONES.lastOrNull { adsWatched >= it.level } ?: PETAL_SUPPORTER_MILESTONES.first()

    fun nextMilestone(adsWatched: Long): PetalSupporterMilestone? =
        PETAL_SUPPORTER_MILESTONES.firstOrNull { adsWatched < it.level }

    fun currentRank(adsWatched: Long): String =
        if (adsWatched <= 0L) "Seedling" else currentMilestone(adsWatched).rank
}

/** Backed by the default SharedPreferences so the existing "sp_liquid_glass_unlocked" flag keeps working. */
object PetalProgressStore {
    private const val KEY_XP = "pp_xp"
    private const val KEY_BADGES = "pp_badges"
    private const val KEY_SHOWCASE = "pp_showcase"
    private const val KEY_SINCE = "pp_since"
    private const val KEY_LAST_DAY = "pp_last_day"
    private const val KEY_AD_STAMPS = "pp_ad_stamps"

    var xp by mutableStateOf(0L); private set
    var unlockedBadges by mutableStateOf<Set<String>>(emptySet()); private set
    var showcase by mutableStateOf<List<String>>(emptyList()); private set
    var since by mutableStateOf(0L); private set
    var newlyUnlocked by mutableStateOf<List<String>>(emptyList()); private set
    private var counters by mutableStateOf<Map<String, Long>>(emptyMap())
    private var loaded = false

    val level: Int get() = PetalXp.level(xp)
    fun counter(c: PetalCounter): Long = counters[c.key] ?: 0L
    private fun sp(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = sp(ctx)
        xp = p.getLong(KEY_XP, 0L)
        unlockedBadges = p.getString(KEY_BADGES, "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        showcase = p.getString(KEY_SHOWCASE, "").orEmpty().split(',').filter { it.isNotBlank() }
        var s = p.getLong(KEY_SINCE, 0L)
        if (s == 0L) { s = System.currentTimeMillis(); p.edit().putLong(KEY_SINCE, s).apply() }
        since = s
        counters = PetalCounter.values().associate { it.key to p.getLong(it.key, 0L) }
        migrateFeatureLocks(ctx)
        touchDay(ctx)

        // F-Droid build: permanently unlock all features for free (no ads required)
        if (com.petal.browser.BuildConfig.IS_FDROID_BUILD) {
            val ed = p.edit()
            for (u in PetalUnlockable.values()) {
                ed.putBoolean("pp_unlocked_" + u.id, true)
            }
            ed.apply()
        }
    }

    /** Call from tab, bookmark, download and blocker code. Cheap to call often. */
    fun bump(ctx: Context, c: PetalCounter, by: Long = 1L, xpGain: Long = 0L) {
        init(ctx)
        val next = counter(c) + by
        counters = counters + (c.key to next)
        sp(ctx).edit().putLong(c.key, next).apply()
        if (xpGain > 0) addXp(ctx, xpGain) else evaluateBadges(ctx)
    }

    fun addXp(ctx: Context, amount: Long) {
        init(ctx)
        val oldLevel = level
        xp = (xp + amount).coerceAtLeast(0L)
        sp(ctx).edit().putLong(KEY_XP, xp).apply()
        val newLevel = level
        if (newLevel > oldLevel) {
            com.petal.browser.live.PetalLiveActionBus.postAchievement(
                title = "Level Up! You reached Level $newLevel",
                subtitle = "Earned ${PetalXp.title(newLevel)} status. Tap to view newly unlocked rewards!",
                isMilestone = true,
                level = newLevel
            )
        }
        evaluateBadges(ctx)
    }

    private fun touchDay(ctx: Context) {
        val today = System.currentTimeMillis() / PetalXp.DAY_MS
        val p = sp(ctx)
        if (p.getLong(KEY_LAST_DAY, -1L) != today) {
            p.edit().putLong(KEY_LAST_DAY, today).apply()
            bump(ctx, PetalCounter.DAYS_USED, 1L, xpGain = 5L)
        }
    }

    fun isBadgeUnlocked(id: String) = id in unlockedBadges

    fun badgeProgress(b: PetalBadge): Pair<Long, Long> =
        if (b.counter != null) counter(b.counter) to b.target else level.toLong() to b.minLevel.toLong()

    private fun evaluateBadges(ctx: Context) {
        val fresh = PETAL_BADGES.filter { it.id !in unlockedBadges }
            .filter { val (cur, goal) = badgeProgress(it); cur >= goal }.map { it.id }
        refreshUnlocks(ctx)
        if (fresh.isEmpty()) return
        unlockedBadges = unlockedBadges + fresh
        sp(ctx).edit().putString(KEY_BADGES, unlockedBadges.joinToString(",")).apply()
        newlyUnlocked = newlyUnlocked + fresh

        // Broadcast to Petal NowBar for interactive celebratory pill and tap navigation
        fresh.forEach { badgeId ->
            val b = PETAL_BADGES.firstOrNull { it.id == badgeId }
            if (b != null) {
                com.petal.browser.live.PetalLiveActionBus.postAchievement(
                    title = "Badge Unlocked: ${b.title}",
                    subtitle = b.hint,
                    badgeId = b.id
                )
            }
        }
    }

    /** Runs after every XP or counter change, so a level-up unlocks features at once. */
    private fun refreshUnlocks(ctx: Context) {
        PetalUnlockable.values().forEach { u ->
            val unlocked = isUnlocked(ctx, u)
            if (unlocked) {
                persistUnlock(ctx, u)
            }
        }
    }

    private fun migrateFeatureLocks(ctx: Context) {
        // Evaluate unlocked state for all features
        refreshUnlocks(ctx)
    }

    private fun persistUnlock(ctx: Context, u: PetalUnlockable) {
        val e = sp(ctx).edit().putBoolean("pp_unlocked_" + u.id, true)
        if (u == PetalUnlockable.LIQUID_GLASS) e.putBoolean("sp_liquid_glass_unlocked", true)
        e.putBoolean(u.enabledKey + "_unlocked", true)
        e.apply()
    }

    fun consumeNewBadges() { newlyUnlocked = emptyList() }

    fun toggleShowcase(ctx: Context, id: String) {
        if (id !in unlockedBadges) return
        showcase = when {
            id in showcase -> showcase - id
            showcase.size >= 3 -> showcase
            else -> showcase + id
        }
        sp(ctx).edit().putString(KEY_SHOWCASE, showcase.joinToString(",")).apply()
    }

    // ---- Ad rewards ----

    fun adBlockReason(ctx: Context, now: Long = System.currentTimeMillis()): String? {
        val stamps = sp(ctx).getString(KEY_AD_STAMPS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it < PetalXp.DAY_MS }
        return when {
            stamps.size >= PetalXp.AD_DAILY -> "Daily limit reached. Come back tomorrow."
            stamps.any { now - it < PetalXp.AD_GAP_MS } -> "Please wait a few seconds before the next one."
            else -> null
        }
    }

    /** Ads watched toward this feature, capped at what it needs. */
    fun unlockProgress(ctx: Context, u: PetalUnlockable): Int {
        init(ctx)
        val ads = counter(PetalCounter.ADS_WATCHED).toInt()
        return ads.coerceAtMost(u.adsNeeded)
    }

    /**
     * Checks if a cosmetic feature is unlocked:
     * - Reached target XP level
     * - OR watched required supporter ads
     * - OR previously unlocked / restored via encrypted backup
     */
    fun isUnlocked(ctx: Context, u: PetalUnlockable): Boolean {
        if (com.petal.browser.BuildConfig.IS_FDROID_BUILD) return true
        init(ctx)
        val p = sp(ctx)
        if (p.getBoolean("pp_unlocked_" + u.id, false)) return true
        if (level >= u.levelNeeded) {
            persistUnlock(ctx, u)
            return true
        }
        val ads = counter(PetalCounter.ADS_WATCHED)
        if (ads >= u.adsNeeded) {
            persistUnlock(ctx, u)
            return true
        }
        return false
    }

    fun getRemainingRequirement(ctx: Context, u: PetalUnlockable): String {
        init(ctx)
        val curLevel = level
        val adsWatched = counter(PetalCounter.ADS_WATCHED)
        val lvDiff = (u.levelNeeded - curLevel).coerceAtLeast(0)
        val adDiff = (u.adsNeeded - adsWatched).coerceAtLeast(0)
        return "Unlock at Level ${u.levelNeeded} ($lvDiff levels to go) or with $adDiff more Fuel the Code support"
    }

    /** Restores progress, XP, stats, badges, and unlocks from decrypted backup JSON */
    fun restoreFromBackupJson(ctx: Context, json: org.json.JSONObject) {
        init(ctx)
        val p = sp(ctx)
        val editor = p.edit()

        if (json.has("xp")) {
            val restoredXp = json.optLong("xp", 0L)
            xp = restoredXp.coerceAtLeast(0L)
            editor.putLong(KEY_XP, xp)
        }

        if (json.has("since")) {
            since = json.optLong("since", System.currentTimeMillis())
            editor.putLong(KEY_SINCE, since)
        }

        // Restore Counters
        val countersObj = json.optJSONObject("counters")
        if (countersObj != null) {
            val updatedCounters = counters.toMutableMap()
            PetalCounter.values().forEach { counter ->
                if (countersObj.has(counter.name)) {
                    val countVal = countersObj.optLong(counter.name, 0L)
                    updatedCounters[counter.key] = countVal
                    editor.putLong(counter.key, countVal)
                }
            }
            counters = updatedCounters
        }

        // Restore Badges
        val badgesArray = json.optJSONArray("unlocked_badges")
        if (badgesArray != null) {
            val badgeList = mutableSetOf<String>()
            for (i in 0 until badgesArray.length()) {
                badgeList.add(badgesArray.getString(i))
            }
            unlockedBadges = badgeList
            editor.putString(KEY_BADGES, badgeList.joinToString(","))
        }

        // Restore Showcase
        val showcaseArray = json.optJSONArray("showcase")
        if (showcaseArray != null) {
            val scList = mutableListOf<String>()
            for (i in 0 until showcaseArray.length()) {
                scList.add(showcaseArray.getString(i))
            }
            showcase = scList
            editor.putString(KEY_SHOWCASE, scList.joinToString(","))
        }

        // Restore Unlocked Features
        val unlocksArray = json.optJSONArray("unlocked_features")
        if (unlocksArray != null) {
            for (i in 0 until unlocksArray.length()) {
                val unlockId = unlocksArray.getString(i)
                editor.putBoolean("pp_unlocked_$unlockId", true)
                if (unlockId == PetalUnlockable.LIQUID_GLASS.id) {
                    editor.putBoolean("sp_liquid_glass_unlocked", true)
                }
            }
        }

        editor.apply()
        refreshUnlocks(ctx)
    }

    /** Call only after the SDK confirms the ad was fully watched. */
    fun onAdCompleted(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        init(ctx)
        if (adBlockReason(ctx, now) != null) return false
        val p = sp(ctx)
        val stamps = p.getString(KEY_AD_STAMPS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it < PetalXp.DAY_MS } + now
        p.edit().putString(KEY_AD_STAMPS, stamps.joinToString(",")).apply()
        val oldAds = counter(PetalCounter.ADS_WATCHED)
        bump(ctx, PetalCounter.ADS_WATCHED, 1L, xpGain = PetalXp.AD_XP)
        val newAds = oldAds + 1L

        // Check if a supporter quest milestone was unlocked
        val hitMilestone = PETAL_SUPPORTER_MILESTONES.firstOrNull { it.level.toLong() == newAds }
        if (hitMilestone != null) {
            com.petal.browser.live.PetalLiveActionBus.postAchievement(
                title = "Milestone: ${hitMilestone.title}",
                subtitle = hitMilestone.story,
                isMilestone = true,
                level = hitMilestone.level
            )
        }
        return true
    }
}

/** Plug-in point for the ad SDK. The FOSS build keeps the no-op default. */
interface PetalRewardedAds {
    val isAvailable: Boolean
    val isLoaded: Boolean get() = false
    val isLoading: Boolean get() = false
    /** Why the last load/show attempt failed (already shown to the user via onMessage), or null. */
    val lastError: String? get() = null
    /** Shows one rewarded ad. Calls onResult(true) only if it was fully watched. */
    fun show(activity: android.app.Activity, onResult: (Boolean) -> Unit)
    fun show(activity: android.app.Activity, onMessage: (String) -> Unit, onResult: (Boolean) -> Unit) {
        show(activity, onResult)
    }
    fun preload(context: android.content.Context) {}
}

object PetalNoAds : PetalRewardedAds {
    override val isAvailable = false
    override fun show(activity: android.app.Activity, onResult: (Boolean) -> Unit) = onResult(false)
}

object PetalAds {
    /** The Play variant sets this at startup to its AdMob implementation. */
    @Volatile var provider: PetalRewardedAds = PetalNoAds
}
