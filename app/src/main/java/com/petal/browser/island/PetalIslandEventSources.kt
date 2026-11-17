package com.petal.browser.island

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.activity.SettingsActivity
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.live.LiveEventAction
import com.petal.browser.live.LiveEventKind
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.containment.isLiquidGlassMaster

/**
 * App-wide NowBar event sources that need no changes in feature code:
 *  - major setting changes (with Undo), the ad blocker switch, and the one-time Liquid Glass intro,
 *    all from one SharedPreferences listener
 *  - network lost / restored
 *  - low storage
 *  - ad blocker "blocked on this site" milestones (called from [PetalAdBlockEngine])
 *
 * Features that own their moment (backup, tabs, sync, ...) post to [PetalLiveActionBus] directly.
 */
object PetalIslandEventSources {

    private const val TAG = "PetalIslandEvents"
    private const val KEY_LG_INTRO_SEEN = "sp_island_lg_intro_seen"
    private const val SETTING_COOLDOWN_MS = 3_000L

    private lateinit var app: Application
    private var started = false
    private val main = Handler(Looper.getMainLooper())

    // Held strongly: SharedPreferences keeps listeners only weakly.
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val snapshot = HashMap<String, Any?>()
    private val settingCooldown = PetalCooldownGate(SETTING_COOLDOWN_MS)
    private val undoing = HashMap<String, Long>()
    private val blockedPerHost = HashMap<String, Int>()

    /** Major settings worth a card, with the label shown to the user. */
    private val MAJOR_SETTINGS: Map<String, String> = linkedMapOf(
        "sp_adblock_protection_mode" to "Ad blocker mode",
        "sp_https_only" to "HTTPS-only mode",
        "sp_search_engine" to "Search engine",
        "sp_javascript" to "JavaScript",
        "sp_desktop_site" to "Desktop site",
        "sp_private_dns_mode" to "Private DNS",
        "sp_webrtc_protection" to "WebRTC protection",
        "sp_block_popups" to "Block pop-ups",
        "sp_cookie_banner_auto_reject" to "Auto-reject cookie banners",
        "sp_cookies_isolate" to "Cookie isolation",
        "sp_app_lock_enabled" to "App lock",
        "sp_biometric_lock" to "Biometric lock",
        "sp_clear_quit" to "Clear data on quit",
        "sp_clear_on_exit" to "Clear data on exit",
        "sp_amoled" to "AMOLED black",
        "sp_force_dark_mode" to "Force dark mode",
        "sp_ui_experience" to "UI experience",
        "sp_liquid_glass_enabled" to "Liquid Glass",
        "sp_font_size_scale" to "Text size",
        "sp_reader_mode_detection" to "Reader mode detection",
    )

    private const val KEY_ADBLOCK = "sp_ad_block"
    private val TRACKED = MAJOR_SETTINGS.keys + KEY_ADBLOCK

    fun init(application: Application) {
        if (started) return
        started = true
        app = application
        try { watchPreferences() } catch (t: Throwable) { Log.w(TAG, "prefs watcher failed", t) }
        try { watchNetwork() } catch (t: Throwable) { Log.w(TAG, "network watcher failed", t) }
        try { watchStorage() } catch (t: Throwable) { Log.w(TAG, "storage watcher failed", t) }
    }

    // ───────────────────────────── Settings, ad blocker, Liquid Glass ─────────────────────────────

    private fun watchPreferences() {
        val sp = PreferenceManager.getDefaultSharedPreferences(app)
        val all = sp.all
        TRACKED.forEach { snapshot[it] = all[it] }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            try {
                onPreferenceChanged(prefs, key)
            } catch (t: Throwable) {
                Log.w(TAG, "pref change handling failed for $key", t)
            }
        }
        prefListener = listener
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun onPreferenceChanged(sp: SharedPreferences, key: String) {
        // Liquid Glass: first time the master switch turns on, show the intro once.
        if (key == "sp_ui_experience" || key == "sp_liquid_glass_enabled") {
            maybePostLiquidGlassIntro(sp)
        }
        if (key !in TRACKED) return

        val old = snapshot[key]
        val new = sp.all[key]
        snapshot[key] = new
        if (old == new) return

        val now = System.currentTimeMillis()
        val undoAt = undoing[key]
        if (undoAt != null && now - undoAt < 2_000L) {
            undoing.remove(key)
            return // this change IS an Undo; don't announce it again
        }

        if (key == KEY_ADBLOCK) {
            postAdBlock(sp.getBoolean(KEY_ADBLOCK, true))
            return
        }

        if (!settingCooldown.tryPass(key)) return

        val label = MAJOR_SETTINGS[key] ?: return
        PetalLiveActionBus.postSettingChanged(
            key = key,
            label = label,
            oldValue = display(old),
            newValue = display(new),
            undo = { undo(key, old) }
        )
    }

    private fun maybePostLiquidGlassIntro(sp: SharedPreferences) {
        if (!isLiquidGlassMaster(sp)) return
        // Once only, and not during startup or a backup restore: the intro belongs to a real user action.
        if (!PetalOneTimeGuard.shouldShow(sp.getBoolean(KEY_LG_INTRO_SEEN, false), PetalLiveActionBus.isQuiet())) return
        sp.edit().putBoolean(KEY_LG_INTRO_SEEN, true).apply()
        PetalLiveActionBus.postLiquidGlassIntro { openSettings("APPEARANCE") }
    }

    private fun postAdBlock(enabled: Boolean) {
        PetalLiveActionBus.postAdBlockState(
            enabled = enabled,
            blockedOnPage = 0,
            domain = null,
            onToggle = { PetalAdBlockEngine.setAdBlockEnabled(app, it) },
            openSettings = { openSettings("PRIVACY") }
        )
    }

    /** Shows the ad blocker card on demand (e.g. a shield button in the address bar). */
    @JvmStatic
    fun showAdBlockCard() {
        if (!started) return
        postAdBlock(PetalAdBlockEngine.isAdBlockEnabled(app))
    }

    /**
     * Called by the ad blocker for every blocked request. Cheap: it only posts a card when a site
     * reaches 10, 25, 50, 100 blocked, then every further 100, so browsing never spams the bar.
     */
    @JvmStatic
    fun onAdBlocked(pageUrl: String?) {
        if (!started || pageUrl.isNullOrBlank()) return
        val host = try { Uri.parse(pageUrl).host } catch (_: Throwable) { null } ?: return
        val count = synchronized(blockedPerHost) {
            val c = (blockedPerHost[host] ?: 0) + 1
            blockedPerHost[host] = c
            if (blockedPerHost.size > 64) blockedPerHost.clear()
            c
        }
        if (count == 10 || count == 25 || count == 50 || count % 100 == 0) {
            PetalLiveActionBus.postSimple(
                kind = LiveEventKind.ADBLOCK_BLOCKED,
                key = "adblock_blocked",
                title = "$count blocked",
                subtitle = "Ads and trackers stopped on ${host.removePrefix("www.")}",
                action = LiveEventAction("Ad blocker", true) { openSettings("PRIVACY") }
            )
        }
    }

    private fun undo(key: String, old: Any?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(app)
        undoing[key] = System.currentTimeMillis()
        val e = sp.edit()
        when (old) {
            null -> e.remove(key)
            is Boolean -> e.putBoolean(key, old)
            is String -> e.putString(key, old)
            is Int -> e.putInt(key, old)
            is Long -> e.putLong(key, old)
            is Float -> e.putFloat(key, old)
            is Set<*> -> e.putStringSet(key, old.filterIsInstance<String>().toSet())
            else -> { undoing.remove(key); return }
        }
        e.apply()
    }

    private fun display(v: Any?): String = when (v) {
        null -> "default"
        is Boolean -> if (v) "On" else "Off"
        else -> v.toString().let { if (it.length > 22) it.take(21) + "…" else it }
    }

    // ───────────────────────────── Network ─────────────────────────────

    private var networkLostShown = false

    private fun watchNetwork() {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // Wait a moment: switching Wi-Fi to mobile data fires onLost for a split second.
                main.postDelayed({
                    val offline = try { cm.activeNetwork == null } catch (_: Throwable) { false }
                    if (offline && !networkLostShown) {
                        networkLostShown = true
                        PetalLiveActionBus.postSimple(
                            kind = LiveEventKind.NETWORK_LOST,
                            key = "network",
                            title = "You're offline",
                            subtitle = "Pages will load again when you reconnect"
                        )
                    }
                }, 2_500L)
            }

            override fun onAvailable(network: Network) {
                main.post {
                    if (networkLostShown) {
                        networkLostShown = false
                        PetalLiveActionBus.postSimple(
                            kind = LiveEventKind.NETWORK_RESTORED,
                            key = "network",
                            title = "Back online"
                        )
                    }
                }
            }
        })
    }

    // ───────────────────────────── Storage ─────────────────────────────

    private fun watchStorage() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_DEVICE_STORAGE_LOW -> PetalLiveActionBus.postSimple(
                        kind = LiveEventKind.LOW_STORAGE,
                        key = "storage",
                        title = "Storage almost full",
                        subtitle = "Downloads and backups may fail",
                        action = LiveEventAction("Manage storage", true) {
                            try {
                                app.startActivity(
                                    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Throwable) {}
                        }
                    )
                    Intent.ACTION_DEVICE_STORAGE_OK -> PetalLiveActionBus.dismissEvent("storage")
                }
            }
        }
        val filter = IntentFilter().apply {
            @Suppress("DEPRECATION") addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
            @Suppress("DEPRECATION") addAction(Intent.ACTION_DEVICE_STORAGE_OK)
        }
        app.registerReceiver(receiver, filter)
    }

    // ───────────────────────────── Helpers for call sites ─────────────────────────────

    /** Opens a Settings category (name of a SettingsCategory constant, e.g. "PRIVACY"). */
    @JvmStatic
    fun openSettings(category: String) {
        try {
            app.startActivity(
                Intent(app, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_SETTINGS_CATEGORY, category)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "openSettings failed", t)
        }
    }
}
