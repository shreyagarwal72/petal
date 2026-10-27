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
 * Features locked behind a task. Add a new entry here to lock another cosmetic feature.
 * A feature unlocks when ANY path is met: enough ads watched in total, OR a high enough level.
 * Keep core browsing features OUT of this list.
 */
enum class PetalUnlockable(
    val id: String,
    val title: String,
    val adsNeeded: Int,
    val levelNeeded: Int,
    /** Preference key that turns the feature on. It is forced off while the feature is locked. */
    val enabledKey: String,
) {
    LIQUID_GLASS("liquid_glass", "Liquid Glass", adsNeeded = 3, levelNeeded = 10, enabledKey = "sp_liquid_glass_enabled"),
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
        xp = (xp + amount).coerceAtLeast(0L)
        sp(ctx).edit().putLong(KEY_XP, xp).apply()
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
    }

    /** Runs after every XP or counter change, so a level-up unlocks features at once. */
    private fun refreshUnlocks(ctx: Context) { PetalUnlockable.values().forEach { isUnlocked(ctx, it) } }

    /**
     * One time: people who already had a locked feature turned on keep it (it was free before).
     * After that, any locked feature that is still switched on is forced off.
     */
    private fun migrateFeatureLocks(ctx: Context) {
        val p = sp(ctx)
        val first = !p.getBoolean("pp_feat_migrated", false)
        PetalUnlockable.values().forEach { u ->
            if (first && p.getBoolean(u.enabledKey, false)) persistUnlock(ctx, u)
            if (!isUnlocked(ctx, u) && p.getBoolean(u.enabledKey, false)) p.edit().putBoolean(u.enabledKey, false).apply()
        }
        if (first) p.edit().putBoolean("pp_feat_migrated", true).apply()
    }

    private fun persistUnlock(ctx: Context, u: PetalUnlockable) {
        val e = sp(ctx).edit().putBoolean("pp_unlocked_" + u.id, true)
        // Older code and the settings screen read this flag for Liquid Glass.
        if (u == PetalUnlockable.LIQUID_GLASS) e.putBoolean("sp_liquid_glass_unlocked", true)
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

    /** Ads watched toward this feature, capped at what it needs (for "1 / 3" labels). */
    fun unlockProgress(ctx: Context, u: PetalUnlockable): Int = counter(PetalCounter.ADS_WATCHED).toInt().coerceAtMost(u.adsNeeded)

    /** The ONE check the rest of the app must use. Persists the unlock the first time a path is met. */
    fun isUnlocked(ctx: Context, u: PetalUnlockable): Boolean {
        val p = sp(ctx)
        if (p.getBoolean("pp_unlocked_" + u.id, false)) return true
        val ads = p.getLong(PetalCounter.ADS_WATCHED.key, 0L)
        val lvl = PetalXp.level(p.getLong(KEY_XP, 0L))
        val met = ads >= u.adsNeeded || lvl >= u.levelNeeded
        if (met) persistUnlock(ctx, u)
        return met
    }

    /** Call only after the SDK confirms the ad was fully watched. */
    fun onAdCompleted(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        init(ctx)
        if (adBlockReason(ctx, now) != null) return false
        val p = sp(ctx)
        val stamps = p.getString(KEY_AD_STAMPS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it < PetalXp.DAY_MS } + now
        p.edit().putString(KEY_AD_STAMPS, stamps.joinToString(",")).apply()
        bump(ctx, PetalCounter.ADS_WATCHED, 1L, xpGain = PetalXp.AD_XP)
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
