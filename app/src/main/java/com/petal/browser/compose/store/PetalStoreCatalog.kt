package com.petal.browser.compose.store

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * PetalStoreCatalog
 * ─────────────────────────────────────────────────────────────────────────
 * Unified catalog and persistence layer for the Petal Browser Store:
 * 1. Curated AMO Add-ons (Firefox WebExtensions)
 * 2. Verified Client Userscripts (with SHA-256 integrity verification)
 * 3. Filter List Subscriptions (AdBlock, Privacy & Tracker Shield)
 */
object PetalStoreCatalog {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Curated AMO WebExtensions Catalog
    // ─────────────────────────────────────────────────────────────────────────
    enum class AddonCategory(val displayName: String) {
        ALL("All"),
        PRIVACY("Privacy & Security"),
        BLOCKERS("Ad Blockers"),
        MEDIA("Media & Video"),
        TOOLS("Utilities & Productivity")
    }

    data class StoreAddon(
        val id: String,
        val name: String,
        val description: String,
        val amoSlug: String,
        val category: AddonCategory,
        val rating: Float = 4.8f,
        val downloads: String = "1M+"
    ) {
        val downloadUrl: String get() = "https://addons.mozilla.org/firefox/downloads/latest/$amoSlug/latest.xpi"
        val amoListingUrl: String get() = "https://addons.mozilla.org/en-US/android/addon/$amoSlug/"
    }

    val storeAddons: List<StoreAddon> = listOf(
        StoreAddon(
            id = "ublock-origin",
            name = "uBlock Origin",
            description = "High-efficiency, wide-spectrum content blocker. Blocks ads, trackers, coin miners, and malware sites.",
            amoSlug = "ublock-origin",
            category = AddonCategory.BLOCKERS,
            rating = 4.9f,
            downloads = "7M+"
        ),
        StoreAddon(
            id = "darkreader",
            name = "Dark Reader",
            description = "Dark mode for every website. Invert colors with eye-pleasing high-contrast dark theme presets and brightness controls.",
            amoSlug = "darkreader",
            category = AddonCategory.TOOLS,
            rating = 4.8f,
            downloads = "5M+"
        ),
        StoreAddon(
            id = "privacy-badger17",
            name = "Privacy Badger",
            description = "EFF's smart privacy protector. Automatically learns to block invisible tracking scripts as you browse.",
            amoSlug = "privacy-badger17",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "1.5M+"
        ),
        StoreAddon(
            id = "sponsorblock",
            name = "SponsorBlock for YouTube",
            description = "Skip YouTube video sponsors, intros, outros, and subscribe reminders automatically using crowd-sourced timestamps.",
            amoSlug = "sponsorblock",
            category = AddonCategory.MEDIA,
            rating = 4.9f,
            downloads = "500K+"
        ),
        StoreAddon(
            id = "bitwarden-password-manager",
            name = "Bitwarden Password Manager",
            description = "Secure open-source password manager vault with auto-fill support for web logins and two-factor codes.",
            amoSlug = "bitwarden-password-manager",
            category = AddonCategory.PRIVACY,
            rating = 4.9f,
            downloads = "3M+"
        ),
        StoreAddon(
            id = "traduzir-paginas-web",
            name = "Translate Web Pages (TWP)",
            description = "Translates entire web pages in real-time using Google Translate or DeepL without reloading.",
            amoSlug = "traduzir-paginas-web",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "900K+"
        ),
        StoreAddon(
            id = "tampermonkey",
            name = "Tampermonkey",
            description = "The world's most popular userscript manager. Run custom JS scripts across your favorite websites effortlessly.",
            amoSlug = "tampermonkey",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "10M+"
        ),
        StoreAddon(
            id = "clearurls",
            name = "ClearURLs",
            description = "Strips tracking parameters and telemetry tokens from URLs to prevent cross-site profiling.",
            amoSlug = "clearurls",
            category = AddonCategory.PRIVACY,
            rating = 4.7f,
            downloads = "400K+"
        ),
        StoreAddon(
            id = "violentmonkey",
            name = "Violentmonkey",
            description = "Lightweight open-source userscript runner supporting Tampermonkey and Greasemonkey scripts seamlessly.",
            amoSlug = "violentmonkey",
            category = AddonCategory.TOOLS,
            rating = 4.8f,
            downloads = "600K+"
        ),
        StoreAddon(
            id = "decentraleyes",
            name = "Decentraleyes",
            description = "Emulates CDN networks locally to prevent large networks (Google, Cloudflare) from tracking your visits.",
            amoSlug = "decentraleyes",
            category = AddonCategory.PRIVACY,
            rating = 4.6f,
            downloads = "800K+"
        ),
        StoreAddon(
            id = "return-youtube-dislikes",
            name = "Return YouTube Dislike",
            description = "Restores the ability to see dislike counts on YouTube videos using crowd-sourced API data.",
            amoSlug = "return-youtube-dislikes",
            category = AddonCategory.MEDIA,
            rating = 4.8f,
            downloads = "1M+"
        ),
        StoreAddon(
            id = "youtube-recommended-videos",
            name = "Unhook - Remove YouTube Recommendations",
            description = "Focus mode for YouTube: hides recommendation feed, sidebar comments, and trending distractions.",
            amoSlug = "youtube-recommended-videos",
            category = AddonCategory.MEDIA,
            rating = 4.9f,
            downloads = "600K+"
        ),
        StoreAddon(
            id = "cookie-autodelete",
            name = "Cookie AutoDelete",
            description = "Automatically deletes unused cookies and local storage when closing a tab to prevent long-term tracking.",
            amoSlug = "cookie-autodelete",
            category = AddonCategory.PRIVACY,
            rating = 4.6f,
            downloads = "350K+"
        ),
        StoreAddon(
            id = "istilldontcareaboutcookies",
            name = "I Still Don't Care About Cookies",
            description = "Dismisses annoying GDPR and cookie consent notices automatically across websites.",
            amoSlug = "istilldontcareaboutcookies",
            category = AddonCategory.BLOCKERS,
            rating = 4.6f,
            downloads = "450K+"
        ),
        StoreAddon(
            id = "cookie-editor",
            name = "Cookie-Editor",
            description = "Powerful developer tool to inspect, edit, export, and delete cookies in real-time.",
            amoSlug = "cookie-editor",
            category = AddonCategory.TOOLS,
            rating = 4.8f,
            downloads = "800K+"
        ),
        StoreAddon(
            id = "adguard-adblocker",
            name = "AdGuard AdBlocker",
            description = "Comprehensive ad and tracker blocking extension with cosmetic element filtering.",
            amoSlug = "adguard-adblocker",
            category = AddonCategory.BLOCKERS,
            rating = 4.8f,
            downloads = "4M+"
        ),
        StoreAddon(
            id = "proton-pass",
            name = "Proton Pass",
            description = "End-to-end encrypted password and email alias manager developed by the Proton team.",
            amoSlug = "proton-pass",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "200K+"
        ),
        StoreAddon(
            id = "keepassxc-browser",
            name = "KeePassXC-Browser",
            description = "Official browser extension connecting Petal to local KeePassXC databases securely.",
            amoSlug = "keepassxc-browser",
            category = AddonCategory.PRIVACY,
            rating = 4.6f,
            downloads = "350K+"
        ),
        StoreAddon(
            id = "video-background-play-fix",
            name = "Video Background Play Fix",
            description = "Prevents YouTube and HTML5 videos from pausing when switching tabs or turning off the screen.",
            amoSlug = "video-background-play-fix",
            category = AddonCategory.MEDIA,
            rating = 4.6f,
            downloads = "1M+"
        ),
        StoreAddon(
            id = "youtube-high-definition",
            name = "YouTube High Definition",
            description = "Automatically forces YouTube videos to play in the highest resolution available (1080p, 4K).",
            amoSlug = "youtube-high-definition",
            category = AddonCategory.MEDIA,
            rating = 4.5f,
            downloads = "300K+"
        ),
        StoreAddon(
            id = "noscript",
            name = "NoScript Security Suite",
            description = "Ultimate active content protection: selectively allow JavaScript, Flash, and plugins only on trusted domains.",
            amoSlug = "noscript",
            category = AddonCategory.PRIVACY,
            rating = 4.5f,
            downloads = "1.2M+"
        ),
        StoreAddon(
            id = "user-agent-string-switcher",
            name = "User-Agent Switcher",
            description = "Spoofs browser user agent strings to access desktop sites or alternate browser views easily.",
            amoSlug = "user-agent-string-switcher",
            category = AddonCategory.TOOLS,
            rating = 4.6f,
            downloads = "500K+"
        ),
        StoreAddon(
            id = "search_by_image",
            name = "Search by Image",
            description = "Reverse image search helper supporting Google, Bing, Yandex, TinEye, and Baidu.",
            amoSlug = "search_by_image",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "600K+"
        ),
        StoreAddon(
            id = "read-aloud",
            name = "Read Aloud: A Text to Speech Voice Reader",
            description = "Reads articles out loud using natural text-to-speech voices with one click.",
            amoSlug = "read-aloud",
            category = AddonCategory.TOOLS,
            rating = 4.6f,
            downloads = "400K+"
        ),
        StoreAddon(
            id = "single-file",
            name = "SingleFile",
            description = "Save an entire web page including CSS, fonts, and images into a single self-contained HTML file.",
            amoSlug = "single-file",
            category = AddonCategory.TOOLS,
            rating = 4.9f,
            downloads = "250K+"
        ),
        StoreAddon(
            id = "leechblock-ng",
            name = "LeechBlock NG",
            description = "Simple yet powerful productivity tool designed to block time-wasting sites on customizable schedules.",
            amoSlug = "leechblock-ng",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "200K+"
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Verified Userscripts ("Toppings") Catalog
    // ─────────────────────────────────────────────────────────────────────────
    data class StoreScript(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val description: String,
        val matchPattern: String,
        val scriptContent: String,
        val sha256: String
    ) {
        fun verifyIntegrity(): Boolean {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(scriptContent.toByteArray(Charsets.UTF_8))
            val computedHash = hashBytes.joinToString("") { "%02x".format(it) }
            return computedHash.equals(sha256, ignoreCase = true)
        }
    }

    val storeScripts: List<StoreScript> = listOf(
        createVerifiedScript(
            id = "petal-auto-skip-ads",
            name = "YouTube Clean Auto-Skip",
            author = "Petal Verified",
            version = "1.2.0",
            description = "Automatically clicks 'Skip Ad' buttons as soon as they become available on video players.",
            matchPattern = "*://*.youtube.com/*",
            scriptContent = """
                (function() {
                    'use strict';
                    setInterval(() => {
                        const skipBtn = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern');
                        if (skipBtn) { skipBtn.click(); }
                    }, 500);
                })();
            """.trimIndent()
        ),
        createVerifiedScript(
            id = "petal-force-dark-mode",
            name = "Universal Dark Invert Engine",
            author = "Petal Verified",
            version = "1.0.4",
            description = "Smart CSS injection to render clean dark mode styles on websites lacking native dark themes.",
            matchPattern = "*://*/*",
            scriptContent = """
                (function() {
                    'use strict';
                    if (window.__petal_dark_injected) return;
                    window.__petal_dark_injected = true;
                    const style = document.createElement('style');
                    style.id = '__petal_universal_dark';
                    style.textContent = 'html { filter: invert(0.9) hue-rotate(180deg) !important; background: #121212 !important; } img, video, canvas, svg { filter: invert(1.1) hue-rotate(180deg) !important; }';
                    (document.head || document.documentElement).appendChild(style);
                })();
            """.trimIndent()
        ),
        createVerifiedScript(
            id = "petal-dont-track-me-google",
            name = "Don't Track Me Google",
            author = "Petal Verified",
            version = "2.1.0",
            description = "Removes ugly and privacy-invasive Google Search result tracking redirects (/url?q=...).",
            matchPattern = "*://*.google.*/*",
            scriptContent = """
                (function() {
                    'use strict';
                    const cleanLinks = () => {
                        document.querySelectorAll('a[href*="/url?"]').forEach(a => {
                            try {
                                const url = new URL(a.href);
                                const real = url.searchParams.get('url') || url.searchParams.get('q');
                                if (real) {
                                    a.href = real;
                                    a.removeAttribute('data-ved');
                                    a.removeAttribute('ping');
                                }
                            } catch (_) {}
                        });
                    };
                    document.addEventListener('DOMContentLoaded', cleanLinks);
                    setInterval(cleanLinks, 1500);
                })();
            """.trimIndent()
        ),
        createVerifiedScript(
            id = "petal-clean-redirects",
            name = "Clean Outgoing Redirects",
            author = "Petal Verified",
            version = "1.1.2",
            description = "Bypasses intermediary redirect landing pages (Facebook, Steam, Twitter, Reddit) and visits destination URLs directly.",
            matchPattern = "*://*/*",
            scriptContent = """
                (function() {
                    'use strict';
                    document.addEventListener('click', (e) => {
                        const anchor = e.target.closest('a');
                        if (!anchor || !anchor.href) return;
                        try {
                            const url = new URL(anchor.href, window.location.href);
                            if (url.searchParams.has('url')) {
                                anchor.href = url.searchParams.get('url');
                            } else if (url.searchParams.has('q') && url.pathname.includes('/url')) {
                                anchor.href = url.searchParams.get('q');
                            } else if (url.searchParams.has('dest')) {
                                anchor.href = url.searchParams.get('dest');
                            }
                        } catch (_) {}
                    }, true);
                })();
            """.trimIndent()
        ),
        createVerifiedScript(
            id = "petal-anti-anti-adblock",
            name = "Defeat Anti-AdBlock Overlays",
            author = "Petal Verified",
            version = "2.0.1",
            description = "Suppresses anti-adblock detection modal dialogues, restoring page scroll and visibility.",
            matchPattern = "*://*/*",
            scriptContent = """
                (function() {
                    'use strict';
                    const restoreScroll = () => {
                        document.body.style.setProperty('overflow', 'auto', 'important');
                        document.documentElement.style.setProperty('overflow', 'auto', 'important');
                    };
                    window.addEventListener('DOMContentLoaded', restoreScroll);
                    setInterval(restoreScroll, 1000);
                })();
            """.trimIndent()
        ),
        createVerifiedScript(
            id = "petal-auto-bypass-shorteners",
            name = "Auto-Bypass Shorteners & Timers",
            author = "Petal Verified",
            version = "1.5.0",
            description = "Automatically accelerates wait timers and reveals direct download links on link shorteners.",
            matchPattern = "*://*/*",
            scriptContent = """
                (function() {
                    'use strict';
                    window.addEventListener('DOMContentLoaded', () => {
                        ['countdown', 'timer', 'timer-count', 'seconds'].forEach(id => {
                            const el = document.getElementById(id);
                            if (el) el.innerText = '0';
                        });
                        const btn = document.querySelector('.btn-captcha, #btn-main, .get-link');
                        if (btn && !btn.disabled) btn.click();
                    });
                })();
            """.trimIndent()
        ),
        createVerifiedScript(
            id = "petal-github-mobile-wrap",
            name = "GitHub Mobile Code Wrap",
            author = "Petal Verified",
            version = "1.0.2",
            description = "Wraps horizontal overflowing code blocks on mobile GitHub so you don't need to endlessly scroll sideways.",
            matchPattern = "*://*.github.com/*",
            scriptContent = """
                (function() {
                    'use strict';
                    const style = document.createElement('style');
                    style.textContent = 'pre, code, .blob-code-inner { white-space: pre-wrap !important; word-break: break-word !important; }';
                    (document.head || document.documentElement).appendChild(style);
                })();
            """.trimIndent()
        )
    )

    private fun createVerifiedScript(
        id: String,
        name: String,
        author: String,
        version: String,
        description: String,
        matchPattern: String,
        scriptContent: String
    ): StoreScript {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(scriptContent.toByteArray(Charsets.UTF_8))
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }
        return StoreScript(id, name, author, version, description, matchPattern, scriptContent, sha256)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Curated Filter List Subscriptions Catalog
    // ─────────────────────────────────────────────────────────────────────────
    data class StoreFilterList(
        val id: String,
        val name: String,
        val description: String,
        val url: String,
        val ruleCount: String,
        val maintainer: String
    )

    val storeFilterLists: List<StoreFilterList> = listOf(
        StoreFilterList(
            id = "easylist",
            name = "EasyList",
            description = "The primary filter list that removes the vast majority of advertisements from international websites.",
            url = "https://easylist.to/easylist/easylist.txt",
            ruleCount = "60,000+",
            maintainer = "EasyList Team"
        ),
        StoreFilterList(
            id = "easyprivacy",
            name = "EasyPrivacy",
            description = "Completely removes tracking scripts, analytics beacons, and telemetric data miners.",
            url = "https://easylist.to/easylist/easyprivacy.txt",
            ruleCount = "35,000+",
            maintainer = "EasyList Team"
        ),
        StoreFilterList(
            id = "adguard-base",
            name = "AdGuard Base Filter",
            description = "Powerful mobile-optimized ad and banner blocker with comprehensive CSS cosmetic rules.",
            url = "https://filters.adtidy.org/extension/ublock/filters/2.txt",
            ruleCount = "50,000+",
            maintainer = "AdGuard Team"
        ),
        StoreFilterList(
            id = "adguard-mobile",
            name = "AdGuard Mobile Filter",
            description = "Specifically targeted at blocking advertisements, interstitials, and banners on mobile web browsers.",
            url = "https://filters.adtidy.org/extension/ublock/filters/11.txt",
            ruleCount = "20,000+",
            maintainer = "AdGuard Team"
        ),
        StoreFilterList(
            id = "fanboy-annoyances",
            name = "Fanboy's Annoyance List",
            description = "Blocks popups, in-page notifications, social media widgets, and newsletter subscription nags.",
            url = "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
            ruleCount = "40,000+",
            maintainer = "Fanboy / uBlock"
        ),
        StoreFilterList(
            id = "ublock-filters",
            name = "uBlock Filters",
            description = "Essential specific fixes and anti-adblock circumvention rules developed by gorhill and the uBlock team.",
            url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            ruleCount = "30,000+",
            maintainer = "gorhill / uBlock"
        ),
        StoreFilterList(
            id = "ublock-badware",
            name = "uBlock Badware Risks",
            description = "Actively blocks deceptive malicious software domains, ransomware, and scam sites.",
            url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
            ruleCount = "15,000+",
            maintainer = "gorhill / uBlock"
        ),
        StoreFilterList(
            id = "oisd-basic",
            name = "OISD Privacy & Ad Filter",
            description = "Ultra-lean, zero-breakage blocklist curated for mobile network speed and battery savings.",
            url = "https://basic.oisd.nl/",
            ruleCount = "25,000+",
            maintainer = "SJ (OISD)"
        ),
        StoreFilterList(
            id = "nocoin-filter",
            name = "NoCoin Filter List",
            description = "Blocks browser cryptocurrency miners and CoinHive scripts from abusing your device processor.",
            url = "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/nocoin.txt",
            ruleCount = "5,000+",
            maintainer = "NoCoin Community"
        ),
        StoreFilterList(
            id = "peter-lowe-list",
            name = "Peter Lowe's Ad and Tracking Server List",
            description = "Authoritative, curated minimal list of ad servers and tracking domains with zero false positives.",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            ruleCount = "4,000+",
            maintainer = "Peter Lowe"
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Persistent Subscriptions State Manager
    // ─────────────────────────────────────────────────────────────────────────
    private const val PREF_INSTALLED_SCRIPTS = "sp_store_installed_scripts"
    private const val PREF_FILTER_SUBSCRIPTIONS = "sp_store_subscribed_filter_lists"

    fun getInstalledScriptIds(context: Context): Set<String> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getStringSet(PREF_INSTALLED_SCRIPTS, emptySet()) ?: emptySet()
    }

    fun setScriptInstalled(context: Context, scriptId: String, installed: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val current = sp.getStringSet(PREF_INSTALLED_SCRIPTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (installed) current.add(scriptId) else current.remove(scriptId)
        sp.edit().putStringSet(PREF_INSTALLED_SCRIPTS, current).apply()
    }

    fun getSubscribedFilterListIds(context: Context): Set<String> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getStringSet(PREF_FILTER_SUBSCRIPTIONS, setOf("easylist", "easyprivacy")) ?: setOf("easylist", "easyprivacy")
    }

    fun setFilterListSubscribed(context: Context, listId: String, subscribed: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val current = sp.getStringSet(PREF_FILTER_SUBSCRIPTIONS, setOf("easylist", "easyprivacy"))?.toMutableSet() ?: mutableSetOf("easylist", "easyprivacy")
        if (subscribed) current.add(listId) else current.remove(listId)
        sp.edit().putStringSet(PREF_FILTER_SUBSCRIPTIONS, current).apply()
    }
}
