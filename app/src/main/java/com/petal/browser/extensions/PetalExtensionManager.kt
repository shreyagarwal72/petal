package com.petal.browser.extensions

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.view.PetalToast
import mozilla.components.feature.addons.ui.translateName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.engine.gecko.webextension.GeckoWebExtension
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.feature.addons.Addon
import mozilla.components.lib.state.ext.flow
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/**
 * PetalExtensionManager
 * ─────────────────────────────────────────────────────────────────────────
 * Thin, app-facing adapter over Mozilla Android Components' add-on stack.
 *
 *  - Install / uninstall / enable / disable / private-browsing / update all go through
 *    [PetalEngineStore.getAddonManager] (`AddonManager` + `AMOAddonsProvider` on
 *    `PetalOkHttpFetchClient` + `DefaultAddonUpdater`).
 *  - Gecko's runtime delegates (install and update permission prompts, tabs, browser/page
 *    actions, popups) are owned by `WebExtensionSupport` (see [PetalEngineStore.getStore]) and
 *    reach Petal through [PetalWebExtensionDelegate]; this object no longer installs any raw
 *    `WebExtensionController` delegate of its own.
 *  - Per-tab action state (badge / title / enabled / icon) is read from
 *    `BrowserState.extensions` and `TabSessionState.extensionState` and published as
 *    [activeTabActionStates].
 *  - [pendingPrompt] / [pendingPopup] stay the state the Compose UI renders (install and update
 *    permission dialogs, extension popup host), so no caller has to change.
 */
object PetalExtensionManager {

    private const val TAG = "PetalExtensionManager"

    /** A permission confirmation the UI must resolve before install/update proceeds. */
    data class PendingPrompt(
        val extensionId: String,
        val displayName: String,
        val permissions: List<String>,
        val origins: List<String>,
        val isUpdate: Boolean,
        val respond: (granted: Boolean) -> Unit
    )

    /** A request from an extension to show its browser/page action popup. */
    data class PendingPopup(
        val extensionId: String,
        val extensionName: String,
        val session: GeckoSession,
        /** The real browsing tab that opened this popup, when Gecko supplied one. */
        val sourceSession: GeckoSession? = null,
        /** The Android Components session that owns [session]; handed back to Gecko as the popup target. */
        val engineSession: EngineSession? = null
    )

    /** State of an extension's browser or page action for the active tab (badge, icon, enabled). */
    data class ExtensionActionState(
        val title: String? = null,
        val badgeText: String? = null,
        val badgeBackgroundColor: Int? = null,
        val badgeTextColor: Int? = null,
        val enabled: Boolean = true,
        val icon: Bitmap? = null
    )

    data class InstalledExtension(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val enabled: Boolean,
        val temporary: Boolean,
        val allowedInPrivateBrowsing: Boolean,
        val homepageUrl: String?,
        val amoListingUrl: String?,
        val optionsPageUrl: String?,
        val supportsPopup: Boolean,
        val icon: Bitmap?,
        val raw: WebExtension,
        /** Mozilla [Addon] for this extension when the AMO listing has been resolved (may be null offline). */
        val addon: Addon? = null
    )

    /** Small curated set of well-known, Android-compatible extensions from addons.mozilla.org. */
    data class CatalogEntry(
        val id: String,
        val name: String,
        val description: String,
        val amoSlug: String,
        val amoListingUrl: String
    ) {
        /**
         * AMO's permanent "always the latest signed build" redirect for this add-on.
         * NOTE: this must be the "/firefox/downloads/latest/" path - AMO has no
         * "/android/downloads/" endpoint. Firefox and Firefox for Android share the
         * same add-on catalog/redirect; only the *listing* pages have an "/android/"
         * variant, not the download links.
         */
        val downloadUrl: String get() = "https://addons.mozilla.org/firefox/downloads/latest/$amoSlug/latest.xpi"
    }

    val catalog: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = "adblock-plus",
            name = "Adblock Plus",
            description = "Block annoying video ads, banners, and popups while speeding up web browsing.",
            amoSlug = "adblock-plus",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/adblock-plus/"
        ),
        CatalogEntry(
            id = "adguard-adblocker",
            name = "AdGuard AdBlocker",
            description = "Comprehensive ad and tracking defense with cosmetic element filtering.",
            amoSlug = "adguard-adblocker",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/adguard-adblocker/"
        ),
        CatalogEntry(
            id = "auto-tab-discard",
            name = "Auto Tab Discard",
            description = "Frees up RAM and battery by automatically unloading inactive background tabs.",
            amoSlug = "auto-tab-discard",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/auto-tab-discard/"
        ),
        CatalogEntry(
            id = "bitwarden-password-manager",
            name = "Bitwarden Password Manager",
            description = "Secure open-source password vault with auto-fill support for logins and 2FA.",
            amoSlug = "bitwarden-password-manager",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/bitwarden-password-manager/"
        ),
        CatalogEntry(
            id = "buster-captcha-solver",
            name = "Buster: Captcha Solver for Humans",
            description = "Solves difficult captchas automatically by completing audio challenges using speech recognition.",
            amoSlug = "buster-captcha-solver",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/buster-captcha-solver/"
        ),
        CatalogEntry(
            id = "canvasblocker",
            name = "CanvasBlocker",
            description = "Alters canvas and audio APIs to prevent websites from fingerprinting your device.",
            amoSlug = "canvasblocker",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/canvasblocker/"
        ),
        CatalogEntry(
            id = "chrome-mask",
            name = "Chrome Mask",
            description = "Makes websites see Chrome instead of Firefox, for sites that block or degrade Firefox users.",
            amoSlug = "chrome-mask",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/chrome-mask/"
        ),
        CatalogEntry(
            id = "clearurls",
            name = "ClearURLs",
            description = "Removes tracking parameters and telemetry tokens from URLs to protect your privacy.",
            amoSlug = "clearurls",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/clearurls/"
        ),
        CatalogEntry(
            id = "consent-o-matic",
            name = "Consent-O-Matic",
            description = "Automatically answers GDPR cookie consent forms, rejecting non-essential cookies.",
            amoSlug = "consent-o-matic",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/consent-o-matic/"
        ),
        CatalogEntry(
            id = "cookie-autodelete",
            name = "Cookie AutoDelete",
            description = "Automatically deletes unused cookies and local storage when closing a tab.",
            amoSlug = "cookie-autodelete",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/cookie-autodelete/"
        ),
        CatalogEntry(
            id = "cookie-quick-manager",
            name = "Cookie Quick Manager",
            description = "Advanced cookie manager: view, search, create, edit, backup, and restore cookies.",
            amoSlug = "cookie-quick-manager",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/cookie-quick-manager/"
        ),
        CatalogEntry(
            id = "cookie-editor",
            name = "Cookie-Editor",
            description = "Powerful developer tool to inspect, edit, export, and delete cookies in real-time.",
            amoSlug = "cookie-editor",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/cookie-editor/"
        ),
        CatalogEntry(
            id = "darkreader",
            name = "Dark Reader",
            description = "Dark mode for every website. Invert colors with eye-pleasing high-contrast dark theme presets.",
            amoSlug = "darkreader",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/darkreader/"
        ),
        CatalogEntry(
            id = "decentraleyes",
            name = "Decentraleyes",
            description = "Emulates CDNs locally to prevent Google and Cloudflare from tracking your requests.",
            amoSlug = "decentraleyes",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/decentraleyes/"
        ),
        CatalogEntry(
            id = "enhancer-for-youtube",
            name = "Enhancer for YouTube",
            description = "Custom video player controls, volume boost up to 500%, playback speed, and loop tools.",
            amoSlug = "enhancer-for-youtube",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/enhancer-for-youtube/"
        ),
        CatalogEntry(
            id = "fastforwardteam",
            name = "FastForward",
            description = "Automatically skips annoying countdowns and intermediate link shorteners.",
            amoSlug = "fastforwardteam",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/fastforwardteam/"
        ),
        CatalogEntry(
            id = "multi-account-containers",
            name = "Firefox Multi-Account Containers",
            description = "Keep parts of your online life separated into color-coded isolated containers.",
            amoSlug = "multi-account-containers",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/multi-account-containers/"
        ),
        CatalogEntry(
            id = "firemonkey",
            name = "FireMonkey",
            description = "Lightweight userscript and userstyle manager powered by standard WebExtensions APIs.",
            amoSlug = "firemonkey",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/firemonkey/"
        ),
        CatalogEntry(
            id = "floccus",
            name = "Floccus Bookmarks Sync",
            description = "Sync your bookmarks privately across browsers via Nextcloud, WebDAV, or Google Drive.",
            amoSlug = "floccus",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/floccus/"
        ),
        CatalogEntry(
            id = "foxyproxy-standard",
            name = "FoxyProxy Standard",
            description = "Advanced proxy management tool that automatically switches proxies based on URL patterns.",
            amoSlug = "foxyproxy-standard",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/foxyproxy-standard/"
        ),
        CatalogEntry(
            id = "ghostery",
            name = "Ghostery Privacy Ad Blocker",
            description = "All-in-one privacy protection blocking ads, trackers, and page clutter.",
            amoSlug = "ghostery",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/ghostery/"
        ),
        CatalogEntry(
            id = "greasemonkey",
            name = "Greasemonkey",
            description = "Customize the way a webpage looks and functions with user scripts.",
            amoSlug = "greasemonkey",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/greasemonkey/"
        ),
        CatalogEntry(
            id = "istilldontcareaboutcookies",
            name = "I Still Don't Care About Cookies",
            description = "Automatically dismisses annoying GDPR and cookie consent notices across websites.",
            amoSlug = "istilldontcareaboutcookies",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/istilldontcareaboutcookies/"
        ),
        CatalogEntry(
            id = "keepassxc-browser",
            name = "KeePassXC-Browser",
            description = "Official browser extension connecting Petal to local KeePassXC databases securely.",
            amoSlug = "keepassxc-browser",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/keepassxc-browser/"
        ),
        CatalogEntry(
            id = "leechblock-ng",
            name = "LeechBlock NG",
            description = "Simple yet powerful productivity tool designed to block time-wasting sites on customizable schedules.",
            amoSlug = "leechblock-ng",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/leechblock-ng/"
        ),
        CatalogEntry(
            id = "localcdn-fork-of-decentraleyes",
            name = "LocalCDN",
            description = "Local emulation of content delivery networks to protect from central tracking.",
            amoSlug = "localcdn-fork-of-decentraleyes",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/localcdn-fork-of-decentraleyes/"
        ),
        CatalogEntry(
            id = "noscript",
            name = "NoScript Security Suite",
            description = "Ultimate active content protection: selectively allow JavaScript and scripts only on trusted domains.",
            amoSlug = "noscript",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/noscript/"
        ),
        CatalogEntry(
            id = "popupoff",
            name = "PopUpOFF",
            description = "Smart popup and modal overlay blocker with gentle, moderate, and aggressive modes.",
            amoSlug = "popupoff",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/popupoff/"
        ),
        CatalogEntry(
            id = "privacy-badger17",
            name = "Privacy Badger",
            description = "EFF's smart tracker blocker that automatically learns to detect and disable third-party tracking.",
            amoSlug = "privacy-badger17",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/privacy-badger17/"
        ),
        CatalogEntry(
            id = "privacy-possum",
            name = "Privacy Possum",
            description = "Defeats browser fingerprinting by corrupting commercial tracking algorithms.",
            amoSlug = "privacy-possum",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/privacy-possum/"
        ),
        CatalogEntry(
            id = "proton-pass",
            name = "Proton Pass",
            description = "End-to-end encrypted password and email alias manager developed by Proton.",
            amoSlug = "proton-pass",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/proton-pass/"
        ),
        CatalogEntry(
            id = "raindropio",
            name = "Raindrop.io",
            description = "All-in-one bookmark manager to save articles, photos, videos, and web pages.",
            amoSlug = "raindropio",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/raindropio/"
        ),
        CatalogEntry(
            id = "read-aloud",
            name = "Read Aloud: Text to Speech",
            description = "Reads articles out loud using natural text-to-speech voices with one click.",
            amoSlug = "read-aloud",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/read-aloud/"
        ),
        CatalogEntry(
            id = "return-youtube-dislikes",
            name = "Return YouTube Dislike",
            description = "Restores dislike counts on YouTube videos using crowd-sourced API data.",
            amoSlug = "return-youtube-dislikes",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/return-youtube-dislikes/"
        ),
        CatalogEntry(
            id = "search_by_image",
            name = "Search by Image",
            description = "Reverse image search helper supporting Google, Bing, Yandex, TinEye, and Baidu.",
            amoSlug = "search_by_image",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/search_by_image/"
        ),
        CatalogEntry(
            id = "single-file",
            name = "SingleFile",
            description = "Save an entire web page including CSS, fonts, and images into a single self-contained HTML file.",
            amoSlug = "single-file",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/single-file/"
        ),
        CatalogEntry(
            id = "sponsorblock",
            name = "SponsorBlock for YouTube",
            description = "Skip YouTube video sponsors, intros, outros, and subscribe reminders automatically.",
            amoSlug = "sponsorblock",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/sponsorblock/"
        ),
        CatalogEntry(
            id = "styl-us",
            name = "Stylus",
            description = "Redesign websites with custom CSS themes. Install styles from online repositories or write your own.",
            amoSlug = "styl-us",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/styl-us/"
        ),
        CatalogEntry(
            id = "tabliss",
            name = "Tabliss - New Tab",
            description = "A customizable, beautiful new tab page with stunning backgrounds and useful widgets.",
            amoSlug = "tabliss",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/tabliss/"
        ),
        CatalogEntry(
            id = "tampermonkey",
            name = "Tampermonkey",
            description = "The world's most popular userscript manager. Run custom JS scripts across websites effortlessly.",
            amoSlug = "tampermonkey",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/tampermonkey/"
        ),
        CatalogEntry(
            id = "temporary-containers",
            name = "Temporary Containers",
            description = "Open tabs in automatically generated temporary containers that isolate storage.",
            amoSlug = "temporary-containers",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/temporary-containers/"
        ),
        CatalogEntry(
            id = "tomato-clock",
            name = "Tomato Clock",
            description = "A simple Pomodoro-style timer for focus and productivity sessions.",
            amoSlug = "tomato-clock",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/tomato-clock/"
        ),
        CatalogEntry(
            id = "traduzir-paginas-web",
            name = "TWP - Translate Web Pages",
            description = "Translates entire web pages in real-time using Google Translate or DeepL without reloading.",
            amoSlug = "traduzir-paginas-web",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/traduzir-paginas-web/"
        ),
        CatalogEntry(
            id = "ublock-origin",
            name = "uBlock Origin",
            description = "High-efficiency wide-spectrum content blocker. Blocks ads, trackers, coin miners, and malware sites.",
            amoSlug = "ublock-origin",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/ublock-origin/"
        ),
        CatalogEntry(
            id = "umatrix",
            name = "uMatrix",
            description = "Point-and-click matrix-based request firewall to control all connections in real time.",
            amoSlug = "umatrix",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/umatrix/"
        ),
        CatalogEntry(
            id = "youtube-recommended-videos",
            name = "Unhook - Remove YouTube Distractions",
            description = "Focus mode for YouTube: hides recommendation feed, sidebar comments, and trending distractions.",
            amoSlug = "youtube-recommended-videos",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/youtube-recommended-videos/"
        ),
        CatalogEntry(
            id = "user-agent-string-switcher",
            name = "User-Agent Switcher and Manager",
            description = "Spoofs browser user agent strings to access desktop sites or alternate browser views easily.",
            amoSlug = "user-agent-string-switcher",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/user-agent-string-switcher/"
        ),
        CatalogEntry(
            id = "video-background-play-fix",
            name = "Video Background Play Fix",
            description = "Prevents YouTube and HTML5 videos from pausing when switching tabs or turning off the screen.",
            amoSlug = "video-background-play-fix",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/video-background-play-fix/"
        ),
        CatalogEntry(
            id = "violentmonkey",
            name = "Violentmonkey",
            description = "Lightweight open-source userscript runner supporting Tampermonkey and Greasemonkey scripts.",
            amoSlug = "violentmonkey",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/violentmonkey/"
        ),
        CatalogEntry(
            id = "view-page-archive",
            name = "Web Archives",
            description = "View archived and cached versions of web pages via Wayback Machine and Archive.is.",
            amoSlug = "view-page-archive",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/view-page-archive/"
        ),
        CatalogEntry(
            id = "youtube-high-definition",
            name = "YouTube High Definition",
            description = "Automatically forces YouTube videos to play in the highest resolution available (1080p, 4K).",
            amoSlug = "youtube-high-definition",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/youtube-high-definition/"
        )
    )

        /** AMO's Android extensions catalog - opened by "Find more extensions". */
    const val amoAndroidBrowseUrl = "https://addons.mozilla.org/en-US/android/"


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _extensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val extensions: StateFlow<List<InstalledExtension>> = _extensions.asStateFlow()

    /** Installed and available [Addon]s from `AddonManager.getAddons` (AMO featured list merged with installed). */
    private val _addons = MutableStateFlow<List<Addon>>(emptyList())
    val addons: StateFlow<List<Addon>> = _addons.asStateFlow()

    private val _pendingPrompt = MutableStateFlow<PendingPrompt?>(null)
    val pendingPrompt: StateFlow<PendingPrompt?> = _pendingPrompt.asStateFlow()

    private val _pendingPopup = MutableStateFlow<PendingPopup?>(null)
    val pendingPopup: StateFlow<PendingPopup?> = _pendingPopup.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _activeTabActionStates = MutableStateFlow<Map<String, ExtensionActionState>>(emptyMap())
    /** Reactive map of extension ID -> [ExtensionActionState] for the selected tab, read from the BrowserStore. */
    val activeTabActionStates: StateFlow<Map<String, ExtensionActionState>> = _activeTabActionStates.asStateFlow()

    @Volatile private var attached = false
    @Volatile private var appContext: Context? = null

    private val trustedOneTapExtensionIds = setOf("adguardadblocker@adguard.com")

    /** Tracks pending installs explicitly started with 1-tap autoConfirm = true. */
    private val autoConfirmInstallUris = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** Extension ids already registered with DefaultAddonUpdater's periodic work. */
    private val updateRegistered = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    // ── Popup lifecycle helpers ──────────────────────────────────────────────
    private val popupLock = Any()
    @Volatile private var lastPopupOpenUptimeMs = 0L
    private const val POPUP_REOPEN_DEBOUNCE_MS = 450L

    /** Atomically takes the pending popup (if any) and clears it, so a close can never run twice. */
    private fun takePendingPopup(): PendingPopup? = synchronized(popupLock) {
        val current = _pendingPopup.value
        _pendingPopup.value = null
        current
    }

    /** Same extension tapped again within the debounce window: reuse the popup that is already up. */
    private fun recentPopupFor(extensionId: String): PendingPopup? {
        val current = _pendingPopup.value ?: return null
        val age = android.os.SystemClock.uptimeMillis() - lastPopupOpenUptimeMs
        return if (current.extensionId == extensionId && age in 0 until POPUP_REOPEN_DEBOUNCE_MS) current else null
    }

    /**
     * Closes a popup session. The source tab is never deactivated while a popup is up, so this only
     * has to make sure it is active again. Every step is guarded, so calling it twice is harmless.
     */
    private fun releasePopup(popup: PendingPopup) {
        stopPopupWatchdog()
        try {
            val session = popup.session
            if (session.isOpen) {
                session.setActive(false)
            }
            popup.engineSession?.close()
            if (session.isOpen) session.close()
        } catch (t: Throwable) {
            Log.d(TAG, "Popup session close failed", t)
        }
        val source = popup.sourceSession ?: return
        try {
            val ctx = appContext
            val visible = if (ctx != null) currentBrowserSession(ctx) else null
            if (source.isOpen && (visible == null || visible === source)) source.setActive(true)
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to reactivate browser session after popup", t)
        }
    }

    // ── TEMPORARY diagnostics: main-thread watchdog while a popup is up ──────
    // Logs the main thread's stack if it stops answering for more than 1.5 s. Remove once the
    // popup hang is confirmed fixed on the affected phone. Filter logcat with tag "PetalExtensionManager".
    private val watchdogMain = Handler(Looper.getMainLooper())
    @Volatile private var watchdogBeat = 0L
    @Volatile private var watchdogRunning = false
    private var watchdogThread: Thread? = null
    private val watchdogBeater = object : Runnable {
        override fun run() {
            watchdogBeat = android.os.SystemClock.uptimeMillis()
            if (watchdogRunning) watchdogMain.postDelayed(this, 250L)
        }
    }

    private fun startPopupWatchdog() {
        stopPopupWatchdog()
        watchdogRunning = true
        watchdogBeat = android.os.SystemClock.uptimeMillis()
        watchdogMain.post(watchdogBeater)
        watchdogThread = Thread({
            var reported = false
            while (watchdogRunning) {
                try { Thread.sleep(500L) } catch (_: InterruptedException) { return@Thread }
                val stall = android.os.SystemClock.uptimeMillis() - watchdogBeat
                if (stall > 1500L && !reported) {
                    reported = true
                    Log.w(TAG, "PopupWatchdog: main thread stalled ${stall}ms\n" +
                        Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "    at $it" })
                } else if (stall < 500L) {
                    reported = false
                }
            }
        }, "petal-popup-watchdog").apply { isDaemon = true; start() }
    }

    private fun stopPopupWatchdog() {
        watchdogRunning = false
        watchdogThread?.interrupt()
        watchdogThread = null
        watchdogMain.removeCallbacks(watchdogBeater)
    }

    fun interface PopupRequestListener {
        fun onPopupRequest(popup: PendingPopup)
    }

    private var onPopupRequestListener: PopupRequestListener? = null

    @JvmStatic
    fun setPopupRequestListener(listener: PopupRequestListener?) {
        onPopupRequestListener = listener
    }

    private fun notifyPopupRequested(popup: PendingPopup) {
        val listener = onPopupRequestListener ?: return
        Handler(Looper.getMainLooper()).post {
            listener.onPopupRequest(popup)
        }
    }


    // ── Per-tab action state, read from the BrowserStore ─────────────────────
    // WebExtensionSupport mirrors every extension's default browser/page action into
    // BrowserState.extensions and every per-tab override into TabSessionState.extensionState, so
    // no GeckoSession bookkeeping is needed here.

    private data class ActionSnapshot(
        val extensions: Map<String, mozilla.components.browser.state.state.WebExtensionState>,
        val tabExtensionState: Map<String, mozilla.components.browser.state.state.WebExtensionState>?
    )

    private fun observeStore(store: mozilla.components.browser.state.store.BrowserStore) {
        scope.launch {
            store.flow()
                .map { state: BrowserState ->
                    ActionSnapshot(state.extensions, state.selectedTab?.extensionState)
                }
                .distinctUntilChanged()
                .collect { snap -> publishActionStates(snap) }
        }
    }

    private fun publishActionStates(snap: ActionSnapshot) {
        val previous = _activeTabActionStates.value
        val result = LinkedHashMap<String, ExtensionActionState>()
        for ((id, ext) in snap.extensions) {
            if (!ext.enabled) continue
            val base = ext.browserAction ?: ext.pageAction ?: continue
            val tabState = snap.tabExtensionState?.get(id)
            val override = tabState?.browserAction ?: tabState?.pageAction
            // Firefox rebuilds the full action by merging the tab's partial override over the default.
            val merged = if (override != null) {
                try { base.copyWithOverride(override) } catch (_: Throwable) { override }
            } else base
            result[id] = ExtensionActionState(
                title = merged.title?.takeIf { it.isNotBlank() },
                badgeText = merged.badgeText?.takeIf { it.isNotBlank() },
                badgeBackgroundColor = merged.badgeBackgroundColor,
                badgeTextColor = merged.badgeTextColor,
                enabled = merged.enabled ?: true,
                // Keep the previous icon until the new one decodes so badges never flicker.
                icon = previous[id]?.icon
            )
            val loader = merged.loadIcon
            if (loader != null) {
                scope.launch(Dispatchers.IO) {
                    val bmp = try { loader(48) } catch (_: Throwable) { null }
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            val current = _activeTabActionStates.value
                            val prev = current[id] ?: return@withContext
                            _activeTabActionStates.value = current + (id to prev.copy(icon = bmp))
                        }
                    }
                }
            }
        }
        _activeTabActionStates.value = result
    }

    /** The merged action of [extensionId] for the selected tab, or null when it has none. */
    private fun currentAction(ctx: Context, extensionId: String): mozilla.components.concept.engine.webextension.Action? {
        val state = PetalEngineStore.getStore(ctx).state
        val base = state.extensions[extensionId]?.let { it.browserAction ?: it.pageAction } ?: return null
        val tabState = state.selectedTab?.extensionState?.get(extensionId)
        val override = tabState?.browserAction ?: tabState?.pageAction ?: return base
        return try { base.copyWithOverride(override) } catch (_: Throwable) { override }
    }

    // ── User-intent gate ───────────────────────────────────────────────────────────────────
    // Firefox only lets an extension open its popup / options page / own pages in response to a
    // user gesture (toolbar tap, extensions menu, a click inside the extension's own UI) or right
    // after install. Background scripts and content scripts that fire on ordinary websites must
    // not be able to pop the extension's settings in front of the page. Petal forwarded every
    // Gecko request unconditionally, so any site that woke an extension (AdGuard, etc.) made its
    // settings/options tab or popup appear. Every UI entry point calls [noteUserIntent]; Gecko
    // driven opens are honoured only inside this window.
    @Volatile private var lastUserIntentUptimeMs: Long = 0L
    private val installIntentUptimeMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val USER_INTENT_WINDOW_MS = 3_000L
    private const val INSTALL_INTENT_WINDOW_MS = 30_000L

    @JvmStatic
    fun noteUserIntent() { lastUserIntentUptimeMs = android.os.SystemClock.uptimeMillis() }

    @JvmStatic
    fun noteInstallIntent(extensionId: String) {
        installIntentUptimeMs[extensionId] = android.os.SystemClock.uptimeMillis()
    }

    private fun hasRecentUserIntent(): Boolean =
        android.os.SystemClock.uptimeMillis() - lastUserIntentUptimeMs < USER_INTENT_WINDOW_MS

    private fun hasRecentInstall(extensionId: String): Boolean {
        val t = installIntentUptimeMs[extensionId] ?: return false
        return android.os.SystemClock.uptimeMillis() - t < INSTALL_INTENT_WINDOW_MS
    }

    /** True while the user is looking at a page/popup that belongs to this extension. */
    private fun userIsInsideExtensionUi(extensionId: String): Boolean {
        if (_pendingPopup.value?.extensionId == extensionId) return true
        val ctx = appContext ?: return false
        val base = findInstalledExtension(extensionId)?.raw?.metaData?.baseUrl ?: return false
        val url = try {
            val st = com.petal.browser.engine.gecko.PetalEngineStore.getStore(ctx)
            st.state.selectedTabId?.let { id -> st.state.tabs.firstOrNull { it.id == id } }?.content?.url
        } catch (_: Throwable) { null }
        return url != null && url.startsWith(base)
    }

    /** An extension-initiated open is allowed only for a user gesture, the extension's own UI, or a fresh install. */
    @JvmStatic
    fun isUserInitiatedOpen(extensionId: String): Boolean = allowExtensionInitiatedOpen(extensionId)

    private fun allowExtensionInitiatedOpen(extensionId: String): Boolean =
        hasRecentUserIntent() || hasRecentInstall(extensionId) || userIsInsideExtensionUi(extensionId)


    /** Idempotent - safe to call from every Activity#onCreate. */
    @JvmStatic
    @Synchronized
    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (attached) {
            refresh()
            return
        }
        attached = true
        // getStore() initialises WebExtensionSupport, which owns every runtime-level delegate.
        observeStore(PetalEngineStore.getStore(app))
        refresh()
    }

    /** Re-reads the installed extensions from the engine and republishes [extensions]. */
    fun refresh() {
        val ctx = appContext ?: return
        try {
            PetalEngineStore.getEngine(ctx).listInstalledWebExtensions(
                onSuccess = { list -> scope.launch { applyInstalled(ctx, list) } },
                onError = { Log.w(TAG, "Failed to list extensions", it) }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "refresh failed", t)
        }
    }

    private fun applyInstalled(
        ctx: Context,
        list: List<mozilla.components.concept.engine.webextension.WebExtension>
    ) {
        try {
            val previous = _extensions.value.associateBy { it.id }
            val natives = list.mapNotNull { (it as? GeckoWebExtension)?.nativeExtension }
            val items = natives.mapNotNull { toInstalled(it) }.map { item ->
                val prev = previous[item.id]
                item.copy(icon = prev?.icon, addon = prev?.addon)
            }
            _extensions.value = items

            // Periodic update checks: DefaultAddonUpdater schedules one WorkManager job per extension.
            val updater = PetalEngineStore.getAddonUpdater(ctx)
            items.forEach { if (updateRegistered.add(it.id)) runCatching { updater.registerForFutureUpdates(it.id) } }
            updateRegistered.retainAll(items.map { it.id }.toSet())

            // Icons arrive asynchronously (best-effort, 96px) and are patched in once ready.
            natives.forEach { ext ->
                try {
                    ext.metaData?.icon?.getBitmap(96)?.accept({ bmp ->
                        if (bmp != null) {
                            _extensions.value = _extensions.value.map {
                                if (it.id == ext.id) it.copy(icon = bmp) else it
                            }
                        }
                    }, { /* icon optional */ })
                } catch (_: Throwable) {}
            }
            enrichAddons(ctx, force = false)
        } catch (t: Throwable) {
            Log.w(TAG, "applyInstalled failed", t)
        }
    }

    @Volatile private var lastEnrichUptimeMs = 0L

    /** Resolves the Mozilla [Addon] listing for installed extensions (network, throttled, offline-safe). */
    private fun enrichAddons(ctx: Context, force: Boolean) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastEnrichUptimeMs < 60_000L) return
        lastEnrichUptimeMs = now
        scope.launch {
            try {
                val all = withContext(Dispatchers.IO) {
                    PetalEngineStore.getAddonManager(ctx).getAddons(waitForPendingActions = false)
                }
                _addons.value = all
                val byId = all.filter { it.isInstalled() }.associateBy { it.id }
                _extensions.value = _extensions.value.map { it.copy(addon = byId[it.id] ?: it.addon) }
            } catch (t: Throwable) {
                Log.d(TAG, "Addon listing unavailable (offline?): ${t.message}")
            }
        }
    }

    /** AMO browse: featured/available add-ons merged with the installed ones, via AddonManager. */
    suspend fun browseAddons(forceRefresh: Boolean = false): List<Addon> {
        val ctx = appContext ?: return emptyList()
        return try {
            val all = withContext(Dispatchers.IO) {
                PetalEngineStore.getAddonManager(ctx).getAddons(waitForPendingActions = false, allowCache = !forceRefresh)
            }
            _addons.value = all
            all
        } catch (t: Throwable) {
            Log.w(TAG, "browseAddons failed", t)
            emptyList()
        }
    }

    /** Minimal [Addon] handle for AddonManager operations that address an add-on by id. */
    private fun addonFor(id: String): Addon =
        _extensions.value.firstOrNull { it.id == id }?.addon ?: Addon(id = id)

    private fun displayName(ctx: Context, addon: Addon): String =
        try {
            addon.translateName(ctx).ifBlank { addon.translatableName.values.firstOrNull() ?: addon.id }
        } catch (_: Throwable) {
            addon.translatableName.values.firstOrNull() ?: addon.id
        }

    // ── Hooks called by PetalWebExtensionDelegate ────────────────────────────

    /** Install prompts are auto-confirmed only for explicit 1-tap curated installs matching verified extension IDs. */
    internal fun shouldAutoConfirm(extensionId: String, @Suppress("UNUSED_PARAMETER") name: String?): Boolean =
        autoConfirmInstallUris.isNotEmpty() &&
            trustedOneTapExtensionIds.any { it.equals(extensionId.trim(), ignoreCase = true) }

    /** Surfaces a permission/install prompt to the Compose dialog and reports the answer back. */
    internal fun showPrompt(
        extensionId: String,
        name: String?,
        permissions: List<String>,
        origins: List<String>,
        isUpdate: Boolean,
        respond: (granted: Boolean) -> Unit
    ) {
        _pendingPrompt.value = PendingPrompt(
            extensionId = extensionId,
            displayName = name ?: extensionId,
            permissions = permissions,
            origins = origins,
            isUpdate = isUpdate
        ) { granted ->
            _pendingPrompt.value = null
            respond(granted)
        }
    }

    internal fun onExtensionInstalled(extensionId: String, name: String?) {
        val ctx = appContext
        // Content blockers get full private-browsing coverage by default.
        if (ctx != null && (extensionId.contains("adguard", ignoreCase = true) ||
                name?.contains("AdGuard", ignoreCase = true) == true ||
                extensionId.contains("ublock", ignoreCase = true))
        ) {
            runCatching {
                PetalEngineStore.getAddonManager(ctx).setAddonAllowedInPrivateBrowsing(
                    addonFor(extensionId), true, onSuccess = { refresh() }, onError = { }
                )
            }
        }
        com.petal.browser.live.PetalLiveActionBus.postSimple(
            com.petal.browser.live.LiveEventKind.EXTENSION_INSTALLED, "ext_$extensionId", "Extension installed", name ?: extensionId
        )
        if (ctx != null) enrichAddons(ctx, force = true)
        refresh()
    }

    internal fun onExtensionUninstalled(extensionId: String, name: String?) {
        com.petal.browser.live.PetalLiveActionBus.postSimple(
            com.petal.browser.live.LiveEventKind.EXTENSION_REMOVED, "ext_$extensionId", "Extension removed", name ?: extensionId
        )
        refresh()
    }

    /**
     * Creates the popup [EngineSession] Gecko loads an extension's `moz-extension://` popup page
     * into, and publishes it as [pendingPopup] for the Compose popup host. Returns null when the
     * open was not user initiated (same gate as before) or the engine refused.
     */
    internal fun createPopupEngineSession(extensionId: String, extensionName: String?, allowedInPrivate: Boolean): EngineSession? {
        if (!allowExtensionInitiatedOpen(extensionId)) {
            Log.i(TAG, "Suppressed non-user popup from $extensionId")
            return null
        }
        recentPopupFor(extensionId)?.engineSession?.let { return it }
        val ctx = appContext ?: return null
        takePendingPopup()?.let { releasePopup(it) }
        val sourceTab = PetalEngineStore.getStore(ctx).state.selectedTab
        val sourceIsPrivate = sourceTab?.content?.private ?: false
        if (sourceIsPrivate && !allowedInPrivate) {
            Log.i(TAG, "Extension $extensionId is not allowed in private browsing")
            return null
        }
        val isPrivate = sourceIsPrivate && allowedInPrivate
        val engineSession = try {
            PetalEngineStore.getEngine(ctx).createSession(private = isPrivate)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create extension popup session", t)
            return null
        }
        // Extension pages need JavaScript and mobile viewport scaling regardless of per-site desktop prefs.
        try { engineSession.settings.javascriptEnabled = true } catch (_: Throwable) {}
        val popupGecko = com.petal.browser.engine.gecko.PetalEngineBridge.extractGeckoSession(engineSession)
        if (popupGecko == null) {
            Log.e(TAG, "Popup EngineSession exposes no GeckoSession")
            return null
        }
        try {
            popupGecko.settings.viewportMode = org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            popupGecko.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        } catch (_: Throwable) {}
        // Open before handing the session back so the popup page starts loading immediately.
        try {
            if (!popupGecko.isOpen) popupGecko.open(com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(ctx))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open extension popup session", t)
            return null
        }
        popupGecko.promptDelegate = popupPromptDelegate()
        val popup = PendingPopup(
            extensionId = extensionId,
            extensionName = extensionName ?: extensionId,
            session = popupGecko,
            sourceSession = currentBrowserSession(ctx),
            engineSession = engineSession
        )
        lastPopupOpenUptimeMs = android.os.SystemClock.uptimeMillis()
        _pendingPopup.value = popup
        startPopupWatchdog()
        notifyPopupRequested(popup)
        return engineSession
    }

    /** Password managers use alert/confirm during unlock; unhandled prompts leave the popup blank. */
    private fun popupPromptDelegate() = object : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AlertPrompt
        ): org.mozilla.geckoview.GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
            org.mozilla.geckoview.GeckoResult.fromValue(prompt.dismiss())

        override fun onButtonPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ButtonPrompt
        ): org.mozilla.geckoview.GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
            org.mozilla.geckoview.GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
    }

    @JvmStatic
    fun dismissPopup() {
        // Idempotent: the popup is taken atomically, so a second call (double tap, back plus
        // window.close(), activity stop) finds nothing and returns.
        val popup = takePendingPopup() ?: return
        releasePopup(popup)
    }

    /**
     * Resolves an installed extension by exact ID, AMO slug, or case-insensitive name.
     * Guarantees seamless resolution for curated extensions like AdGuard AdBlocker.
     */
    fun findInstalledExtension(idOrSlug: String): InstalledExtension? {
        val clean = idOrSlug.trim()
        val all = _extensions.value
        return all.find { it.id.equals(clean, ignoreCase = true) }
            ?: all.find { ext ->
                val slug = com.petal.browser.compose.extensions.PetalCuratedExtensionsData.slugForExtensionId(ext.id)
                slug != null && slug.equals(clean, ignoreCase = true)
            }
            ?: if (clean.contains("adguard", ignoreCase = true)) {
                all.find { it.id.contains("adguard", ignoreCase = true) || it.name.contains("AdGuard", ignoreCase = true) }
            } else null
            ?: all.find { it.name.equals(clean, ignoreCase = true) }
    }

    /** Returns the installed AdGuard AdBlocker extension if present. */
    fun getAdGuardExtension(): InstalledExtension? =
        findInstalledExtension("adguard-adblocker")

    /** Whether the AdGuard AdBlocker extension is currently installed in Petal. */
    fun isAdGuardInstalled(): Boolean =
        getAdGuardExtension() != null

    /** Whether the AdGuard AdBlocker extension is installed and enabled. */
    fun isAdGuardEnabled(): Boolean =
        getAdGuardExtension()?.enabled == true



    /**
     * Manually triggers the browser-action (toolbar popup) for an installed extension from the UI.
     *
     * Same as tapping the toolbar button in Firefox: the action recorded in the BrowserStore is
     * clicked, and Gecko answers with a popup through [PetalWebExtensionDelegate.onToggleActionPopup]
     * (or runs the extension's background handler when it has no popup). When no action is known
     * yet, the extension's own default_popup URL is opened directly.
     */
    @JvmOverloads
    fun triggerBrowserAction(extensionId: String, context: Context? = null) {
        noteUserIntent()
        val extItem = findInstalledExtension(extensionId) ?: run {
            _lastError.value = "Extension not found."
            return
        }
        if (!extItem.enabled) return
        val rawExt = extItem.raw
        val ctx = context ?: appContext ?: return

        val action = currentAction(ctx, extItem.id)
        if (action != null) {
            try {
                action.onClick()
                // Extensions that declare a popup get a fallback if Gecko's popup callback never arrives.
                if (extensionHasPopup(rawExt)) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (_pendingPopup.value == null) {
                            try {
                                val directUrl = resolveExtensionPopupUrl(rawExt)
                                if (!directUrl.isNullOrBlank()) openDirectPopup(extItem, directUrl, ctx)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Delayed extension popup fallback failed for ${extItem.id}", t)
                            }
                        }
                    }, 700L)
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to click extension action for ${extItem.id}, falling back to direct popup load", e)
            }
        }

        val popupUrl = resolveExtensionPopupUrl(rawExt)
        if (popupUrl != null) {
            openDirectPopup(extItem, popupUrl, ctx)
            return
        }

        val optionsUrl = extItem.optionsPageUrl ?: resolveExtensionOptionsUrl(rawExt)
        if (optionsUrl != null) {
            openOptionsPage(extensionId, ctx)
        } else {
            val msg = "${extItem.name} does not have a popup interface."
            _lastError.value = msg
            PetalToast.show(ctx, msg, PetalToast.LENGTH_SHORT)
        }
    }

    /** Loads [popupUrl] (the extension's own moz-extension:// page) into a fresh popup EngineSession. */
    private fun openDirectPopup(extension: InstalledExtension, popupUrl: String, ctx: Context) {
        if (recentPopupFor(extension.id) != null) return
        val session = createPopupEngineSession(extension.id, extension.name, extension.allowedInPrivateBrowsing) ?: return
        try {
            session.loadUrl(popupUrl)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load popup page for ${extension.id}", t)
            dismissPopup()
        }
    }

    /**
     * Opens the options/settings page of an extension in a real foreground browser tab, matching
     * Firefox for Android. Falls back to the popup when there is no separate settings page.
     */
    fun openOptionsPage(extensionId: String, context: Context) {
        noteUserIntent()
        val extItem = findInstalledExtension(extensionId) ?: run {
            _lastError.value = "Extension not found."
            return
        }
        val optionsUrl = extItem.optionsPageUrl ?: resolveExtensionOptionsUrl(extItem.raw)
        if (optionsUrl.isNullOrBlank()) {
            val popupUrl = resolveExtensionPopupUrl(extItem.raw)
            if (popupUrl != null) {
                triggerBrowserAction(extItem.id, context)
            } else {
                val msg = "No settings page available for ${extItem.name}."
                _lastError.value = msg
                PetalToast.show(context, msg, PetalToast.LENGTH_SHORT)
            }
            return
        }

        val activity = findMainActivity(context)
        if (activity != null) {
            activity.runOnUiThread {
                activity.addAlbum("${extItem.name} Settings", optionsUrl, true)
            }
        } else {
            try {
                val tabId = "tab_" + System.currentTimeMillis() + "_" + Math.abs(java.util.UUID.randomUUID().hashCode())
                PetalEngineStore.createTabSession(
                    context = context,
                    tabId = tabId,
                    url = optionsUrl,
                    title = "${extItem.name} Settings",
                    isIncognito = false,
                    select = true
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to open options page in new tab: ${t.message}", t)
                openDirectPopup(extItem, optionsUrl, context)
            }
        }
    }

    private fun currentBrowserSession(context: Context): GeckoSession? {
        return try {
            val store = PetalEngineStore.getStore(context)
            val selectedTab = store.state.selectedTabId?.let { id -> store.state.tabs.firstOrNull { it.id == id } }
                ?: store.state.tabs.firstOrNull()
            com.petal.browser.engine.gecko.PetalEngineBridge.extractGeckoSession(selectedTab?.engineState?.engineSession)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findMainActivity(context: Context): com.petal.browser.activity.MainActivity? {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is com.petal.browser.activity.MainActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // Manifest reads are a zip open on the calling thread, and popup taps reach them on the main
    // thread. Parse each extension file once; the cache is keyed by location so updates re-read.
    private val manifestCache = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()

    fun getManifest(extension: WebExtension): JSONObject? {
        val key = extension.location ?: return null
        manifestCache[key]?.let { return it }
        return readManifest(extension)?.also { manifestCache[key] = it }
    }

    private fun readManifest(extension: WebExtension): JSONObject? {
        return try {
            val loc = extension.location ?: return null
            val rawPath = when {
                loc.startsWith("jar:file:", ignoreCase = true) ->
                    loc.removePrefix("jar:file:").substringBefore("!")
                loc.startsWith("file:", ignoreCase = true) ->
                    loc.removePrefix("file:").substringBefore("!")
                else -> loc.substringBefore("!")
            }
            val cleanPath = if (rawPath.startsWith("//")) rawPath.removePrefix("//") else rawPath
            val decodedPath = Uri.decode(cleanPath)
            val file = File(decodedPath)
            val manifestText = if (file.isDirectory) {
                File(file, "manifest.json").takeIf { it.isFile }?.readText()
            } else if (file.isFile) {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("manifest.json") ?: return null
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                }
            } else null
            manifestText?.let { JSONObject(it) }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get manifest for ${extension.id}", e)
            null
        }
    }

    fun resolveExtensionPopupUrl(extension: WebExtension): String? {
        val ext = extension ?: return null
        val manifest = getManifest(ext) ?: return null
        val popupPath = manifest.optJSONObject("browser_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("page_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: return null

        val baseUrl = ext.metaData?.baseUrl ?: return null
        val cleanRel = popupPath.trim().removePrefix("/")
        return if (baseUrl.endsWith("/")) "$baseUrl$cleanRel" else "$baseUrl/$cleanRel"
    }

    fun resolveExtensionOptionsUrl(extension: WebExtension?): String? {
        val ext = extension ?: return null
        val metaUrl = ext.metaData?.optionsPageUrl
        if (!metaUrl.isNullOrBlank()) return metaUrl

        val manifest = getManifest(ext) ?: return null
        val optionsPath = manifest.optJSONObject("options_ui")?.optString("page")?.takeIf { it.isNotBlank() }
            ?: manifest.optString("options_page").takeIf { it.isNotBlank() }
            ?: return null

        val baseUrl = ext.metaData?.baseUrl ?: return null
        val cleanRel = optionsPath.trim().removePrefix("/")
        return if (baseUrl.endsWith("/")) "$baseUrl$cleanRel" else "$baseUrl/$cleanRel"
    }



    // ── Install / uninstall / enable / update, all through AddonManager ───────

    /**
     * Installs a `.xpi` file the user opened from *outside* the browser - tapped in Downloads, a
     * file manager, shared from another app. The incoming [uri] is usually a `content://` Uri whose
     * read grant only lasts for the original intent, and Gecko's installer cannot resolve content
     * providers, so the bytes are copied into the app cache first and the plain `file://` path is
     * installed through `AddonManager.installAddon(... FROM_FILE)`.
     */
    @JvmStatic
    @JvmOverloads
    fun installFromContentUri(context: Context, uri: Uri, onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }) {
        val appCtx = context.applicationContext
        attach(appCtx)
        _busy.value = true
        _lastError.value = null
        try {
            val resolver = appCtx.contentResolver
            var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "extension.xpi"
            var mimeType: String? = null

            if (uri.scheme.equals("content", ignoreCase = true)) {
                try {
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) cursor.getString(nameIdx)?.let { displayName = it }
                            val typeIdx = cursor.getColumnIndex("mime_type")
                            if (typeIdx != -1) cursor.getString(typeIdx)?.let { mimeType = it }
                        }
                    }
                } catch (ignored: Exception) {}
                mimeType = mimeType ?: resolver.getType(uri)
            }

            val cacheDir = File(appCtx.cacheDir, "extension-installs").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
                if (it.endsWith(".xpi", ignoreCase = true)) it else "$it.xpi"
            }
            val destFile = File(cacheDir, "install_${System.currentTimeMillis()}_$safeName")

            val input = if (uri.scheme.equals("file", ignoreCase = true)) {
                java.io.FileInputStream(File(requireNotNull(uri.path)))
            } else {
                resolver.openInputStream(uri)
            }
            if (input == null) {
                _busy.value = false
                val message = "Petal couldn't access that file."
                _lastError.value = message
                onResult(false, message)
                return
            }

            input.use { stream ->
                destFile.outputStream().use { output -> stream.copyTo(output) }
            }
            try { destFile.setReadable(true, false) } catch (_: Exception) {}

            // A valid WebExtension XPI is a ZIP archive containing manifest.json. Also accept a name,
            // path or mime type that explicitly declares xpi.
            val isKnownXpiZip = try {
                ZipFile(destFile).use { zip -> zip.getEntry("manifest.json") != null }
            } catch (_: Exception) {
                false
            }
            val isXpi = isKnownXpiZip ||
                displayName.endsWith(".xpi", ignoreCase = true) ||
                uri.path?.endsWith(".xpi", ignoreCase = true) == true ||
                uri.lastPathSegment?.endsWith(".xpi", ignoreCase = true) == true ||
                mimeType.equals("application/x-xpinstall", ignoreCase = true)

            if (!isXpi) {
                try { destFile.delete() } catch (_: Exception) {}
                val message = "Please select a valid Firefox extension file (.xpi)."
                _busy.value = false
                _lastError.value = message
                onResult(false, message)
                return
            }

            val fileUri = Uri.fromFile(destFile.canonicalFile).toString()
            install(fileUri, InstallationMethod.FROM_FILE, autoConfirm = false) { success, message ->
                try { destFile.delete() } catch (ignored: Exception) {}
                onResult(success, message)
            }
        } catch (e: Exception) {
            _busy.value = false
            val message = "Couldn't read that extension file."
            _lastError.value = message
            Log.w(TAG, "installFromContentUri failed", e)
            onResult(false, message)
        }
    }

    /** Installs a `.xpi` from any https URL (an AMO listing's download link, or a direct file). */
    @JvmOverloads
    fun install(
        uri: String,
        autoConfirm: Boolean = false,
        onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        install(uri, null, autoConfirm, onResult)
    }

    private fun install(
        uri: String,
        installationMethod: InstallationMethod?,
        autoConfirm: Boolean,
        onResult: (success: Boolean, message: String?) -> Unit
    ) {
        val installUri = normalizeInstallUri(uri)
        if (installUri == null) {
            val message = "Use a secure Firefox .xpi download link, an addons.mozilla.org add-on page, or import an .xpi file."
            _busy.value = false
            _lastError.value = message
            onResult(false, message)
            return
        }
        val ctx = appContext ?: return
        attach(ctx)
        val trustedCatalogAutoConfirm = autoConfirm && catalog.any {
            it.id == "adguard-adblocker" && it.downloadUrl.equals(installUri, ignoreCase = true)
        }
        if (trustedCatalogAutoConfirm) autoConfirmInstallUris.add(installUri)
        _busy.value = true
        _lastError.value = null
        PetalEngineStore.getAddonManager(ctx).installAddon(
            url = installUri,
            installationMethod = installationMethod,
            onSuccess = { addon ->
                autoConfirmInstallUris.remove(installUri)
                _busy.value = false
                noteInstallIntent(addon.id)
                if (trustedOneTapExtensionIds.any { it.equals(addon.id, ignoreCase = true) }) {
                    runCatching {
                        PetalEngineStore.getAddonManager(ctx).setAddonAllowedInPrivateBrowsing(
                            addon, true, onSuccess = { refresh() }, onError = { }
                        )
                    }
                }
                refresh()
                onResult(true, displayName(ctx, addon))
            },
            onError = { throwable ->
                autoConfirmInstallUris.remove(installUri)
                _busy.value = false
                val message = describeInstallError(throwable)
                _lastError.value = message
                Log.w(TAG, "Extension installation failed: ${throwable.message}")
                com.petal.browser.live.PetalLiveActionBus.postSimple(
                    com.petal.browser.live.LiveEventKind.EXTENSION_ERROR, "ext_failed", "Extension install failed", message.take(80)
                )
                onResult(false, message)
            }
        )
    }

    /**
     * Maps whatever the engine reports (Mozilla's WebExtensionInstallException family, or a raw
     * GeckoView InstallException as its cause) to a user-readable sentence.
     */
    private fun describeInstallError(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(4).toList()
        chain.firstNotNullOfOrNull { it as? WebExtension.InstallException }?.let { e ->
            return when (e.code) {
                WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                    "Couldn't download the extension - check your connection and try again."
                WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
                    "The downloaded file didn't match its expected signature."
                WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE ->
                    "That extension package is corrupt or not a valid .xpi file."
                WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS ->
                    "Petal couldn't access that file."
                WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                    "That extension isn't signed by Mozilla, so it can't be installed."
                WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE,
                WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE ->
                    "This package is not a supported WebExtension for GeckoView. Firefox desktop-only add-on types cannot run in an Android browser."
                WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
                    "This extension has been blocklisted by Mozilla for safety reasons."
                WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                    "This extension isn't compatible with Petal's engine version."
                WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ->
                    "Installation was canceled."
                else -> "Couldn't install that extension (error ${e.code})."
            }
        }
        // Mozilla's own exception classes: matched by name so every release's subtype set is covered.
        val names = chain.joinToString(" ") { it.javaClass.simpleName }
        return when {
            names.contains("UserCancelled", true) || names.contains("Cancel", true) -> "Installation was canceled."
            names.contains("Blocklist", true) -> "This extension has been blocklisted by Mozilla for safety reasons."
            names.contains("NetworkFailure", true) -> "Couldn't download the extension - check your connection and try again."
            names.contains("Corrupt", true) -> "That extension package is corrupt or not a valid .xpi file."
            names.contains("Incompatible", true) -> "This extension isn't compatible with Petal's engine version."
            names.contains("Unsupported", true) || names.contains("UnexpectedAddonType", true) ->
                "This package is not a supported WebExtension for GeckoView. Firefox desktop-only add-on types cannot run in an Android browser."
            names.contains("SignedState", true) -> "That extension isn't signed by Mozilla, so it can't be installed."
            else -> error.message ?: "Installation failed."
        }
    }

    fun uninstall(extension: WebExtension, onResult: (Boolean) -> Unit = {}) {
        val ctx = appContext ?: return
        PetalEngineStore.getAddonManager(ctx).uninstallAddon(
            addonFor(extension.id),
            onSuccess = {
                refresh()
                onResult(true)
            },
            onError = { _, _ -> onResult(false) }
        )
    }

    fun setEnabled(extension: WebExtension, enabled: Boolean) {
        val ctx = appContext ?: return
        val manager = PetalEngineStore.getAddonManager(ctx)
        val addon = addonFor(extension.id)
        if (enabled) {
            manager.enableAddon(addon, onSuccess = { refresh() }, onError = { refresh() })
        } else {
            manager.disableAddon(addon, onSuccess = { refresh() }, onError = { refresh() })
        }
    }

    fun setAllowedInPrivateBrowsing(extension: WebExtension, allowed: Boolean) {
        val ctx = appContext ?: return
        PetalEngineStore.getAddonManager(ctx).setAddonAllowedInPrivateBrowsing(
            addonFor(extension.id), allowed, onSuccess = { refresh() }, onError = { refresh() }
        )
    }

    /** Checks one extension for an update (AMO via AddonManager); a permission change prompts like an install. */
    @JvmOverloads
    fun updateExtension(extensionId: String, onResult: (String) -> Unit = {}) {
        val ctx = appContext ?: return
        PetalEngineStore.getAddonManager(ctx).updateAddon(extensionId) { status ->
            refresh()
            onResult(status.toString())
        }
    }

    /** Runs [updateExtension] for every installed extension (the periodic worker does the same in the background). */
    fun checkForUpdates() {
        _extensions.value.forEach { updateExtension(it.id) }
    }

    private fun extensionHasPopup(extension: WebExtension): Boolean {
        val manifest = getManifest(extension) ?: return false
        val popup = manifest.optJSONObject("browser_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("page_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
        return popup != null
    }

    private fun toInstalled(ext: WebExtension?): InstalledExtension? {
        if (ext == null) return null
        return try {
            // Gecko hands back Java platform types: any of these can be null at runtime.
            val extId: String = (ext.id as String?) ?: return null
            val meta = ext.metaData
            val metaName: String? = meta?.name
            val metaDesc: String? = meta?.description
            val metaVersion: String? = meta?.version
            val optionsUrl = try { resolveExtensionOptionsUrl(ext) } catch (t: Throwable) { null }
            val hasPopup = try {
                resolveExtensionPopupUrl(ext) != null || extensionHasPopup(ext)
            } catch (t: Throwable) { false }
            InstalledExtension(
                id = extId,
                name = metaName ?: extId,
                description = metaDesc ?: "",
                version = metaVersion ?: "",
                enabled = meta?.enabled ?: true,
                temporary = meta?.temporary ?: false,
                allowedInPrivateBrowsing = meta?.allowedInPrivateBrowsing ?: false,
                homepageUrl = meta?.homepageUrl,
                amoListingUrl = meta?.amoListingUrl,
                optionsPageUrl = optionsUrl,
                supportsPopup = hasPopup,
                icon = null,
                raw = ext
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Skipping extension that failed to map", t)
            null
        }
    }

    /**
     * Queries AMO (addons.mozilla.org) for a custom user collection by user ID and collection slug.
     * Returns a list of CatalogEntry items corresponding to the collection's add-ons.
     */
    suspend fun fetchCustomCollection(userId: String, collectionSlug: String): Result<List<CatalogEntry>> = withContext(Dispatchers.IO) {
        try {
            val uId = userId.trim()
            val slug = collectionSlug.trim()
            if (uId.isEmpty() || slug.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("User ID and Collection Name cannot be blank"))
            }

            val url = "https://addons.mozilla.org/api/v4/accounts/account/$uId/collections/$slug/addons/?page_size=50"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:158.0) Gecko/158.0 Firefox/158.0")
                .build()

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("HTTP ${response.code}: Collection not found or private"))
            }

            val body = response.body?.string().orEmpty()
            val json = org.json.JSONObject(body)
            val results = json.optJSONArray("results") ?: org.json.JSONArray()
            val entries = mutableListOf<CatalogEntry>()

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val addon = item.optJSONObject("addon") ?: continue
                val guid = addon.optString("guid", "")
                val name = addon.optJSONObject("name")?.optString("en-US", "")?.ifEmpty { null }
                    ?: addon.optString("name", "")
                val summary = addon.optJSONObject("summary")?.optString("en-US", "")?.ifEmpty { null }
                    ?: addon.optString("summary", "")
                val addonSlug = addon.optString("slug", "")

                if (addonSlug.isNotEmpty()) {
                    entries.add(
                        CatalogEntry(
                            id = if (guid.isNotEmpty()) guid else addonSlug,
                            name = if (name.isNotEmpty()) name else addonSlug,
                            description = summary,
                            amoSlug = addonSlug,
                            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/$addonSlug/"
                        )
                    )
                }
            }
            Result.success(entries)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load custom AMO collection: ${t.message}")
            Result.failure(t)
        }
    }

    private fun normalizeInstallUri(value: String): String? {
        val parsed = try { Uri.parse(value.trim()) } catch (_: Exception) { return null }

        // A local `.xpi` package already resolved to a real file path (see
        // installFromContentUri, used when the user opens a downloaded/shared .xpi from
        // outside the browser) - GeckoView's installer reads these directly. Never rewrite
        // this into an AMO url.
        if (parsed.scheme.equals("file", ignoreCase = true)) {
            return parsed.toString()
        }

        if (!parsed.scheme.equals("https", ignoreCase = true)) return null

        // A direct XPI URL can be hosted anywhere (for example an extension developer's
        // release server or a GitHub release). Do not artificially restrict installs to AMO;
        // Gecko validates the package, signature, compatibility and blocklist itself.
        val path = parsed.path.orEmpty()
        if (path.endsWith(".xpi", ignoreCase = true) ||
            path.contains(".xpi/", ignoreCase = true) ||
            path.contains(".xpi", ignoreCase = true)
        ) {
            return parsed.toString()
        }

        val host = parsed.host?.lowercase() ?: return null
        if (host != "addons.mozilla.org" && host != "www.addons.mozilla.org") return null

        // AMO listing pages (Android or desktop) are resolved to AMO's permanent latest
        // signed package URL. The package itself is still validated by GeckoView.
        val segments = parsed.pathSegments
        val addonIndex = segments.indexOf("addon")
        val slug = segments.getOrNull(addonIndex + 1)?.takeIf { it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]*")) }
        return if (addonIndex >= 0 && slug != null) {
            "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"
        } else null
    }
}
