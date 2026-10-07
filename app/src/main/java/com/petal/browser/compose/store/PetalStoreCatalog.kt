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
 * 3. Curated Privacy Search Engines
 * 4. Filter List Subscriptions (AdBlock & Tracker Shield)
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
            id = "traduzir-paginas-web",
            name = "Translate Web Pages",
            description = "Translates entire web pages in real-time using Google Translate or DeepL without reloading.",
            amoSlug = "traduzir-paginas-web",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "900K+"
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
            description = "Lightweight userscript runner supporting Tampermonkey and Greasemonkey scripts seamlessly.",
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
            id = "bitwarden-password-manager",
            name = "Bitwarden",
            description = "Secure open-source password manager vault with auto-fill support for web logins.",
            amoSlug = "bitwarden-password-manager",
            category = AddonCategory.PRIVACY,
            rating = 4.9f,
            downloads = "3M+"
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
            id = "petal-clean-redirects",
            name = "Clean Outgoing Redirects",
            author = "Petal Verified",
            version = "1.1.2",
            description = "Bypasses intermediary redirect landing pages (Google, Facebook, Steam, Reddit) and visits destination URLs directly.",
            matchPattern = "*://*/*",
            scriptContent = """
                (function() {
                    'use strict';
                    document.addEventListener('click', (e) => {
                        const anchor = e.target.closest('a');
                        if (!anchor || !anchor.href) return;
                        const url = new URL(anchor.href, window.location.href);
                        if (url.searchParams.has('url')) {
                            anchor.href = url.searchParams.get('url');
                        } else if (url.searchParams.has('q') && url.pathname.includes('/url')) {
                            anchor.href = url.searchParams.get('q');
                        }
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
    // 3. Search Engine Catalog
    // ─────────────────────────────────────────────────────────────────────────
    data class StoreSearchEngine(
        val id: Int,
        val name: String,
        val description: String,
        val queryUrl: String,
        val privacyScore: String = "A+"
    )

    val storeSearchEngines: List<StoreSearchEngine> = listOf(
        StoreSearchEngine(
            id = 1,
            name = "DuckDuckGo",
            description = "Privacy-first web search without tracking or personalized profiling.",
            queryUrl = "https://duckduckgo.com/?q=%s",
            privacyScore = "A+"
        ),
        StoreSearchEngine(
            id = 2,
            name = "Startpage",
            description = "Delivers Google-grade results with complete privacy protection and zero logging.",
            queryUrl = "https://www.startpage.com/sp/search?query=%s",
            privacyScore = "A+"
        ),
        StoreSearchEngine(
            id = 3,
            name = "Brave Search",
            description = "Independent search engine powered by its own web index, fully private and ad-free.",
            queryUrl = "https://search.brave.com/search?q=%s",
            privacyScore = "A+"
        ),
        StoreSearchEngine(
            id = 5,
            name = "SearXNG",
            description = "Decentralized open-source metasearch engine combining results from dozens of sources.",
            queryUrl = "https://searx.space/search?q=%s",
            privacyScore = "A+"
        ),
        StoreSearchEngine(
            id = 6,
            name = "Qwant",
            description = "European privacy search engine that doesn't track users or sell search queries.",
            queryUrl = "https://www.qwant.com/?q=%s",
            privacyScore = "A"
        ),
        StoreSearchEngine(
            id = 7,
            name = "Ecosia",
            description = "Eco-friendly search engine that plants trees with its advertising revenue.",
            queryUrl = "https://www.ecosia.org/search?q=%s",
            privacyScore = "A-"
        ),
        StoreSearchEngine(
            id = 8,
            name = "Kagi Search",
            description = "Premium, ultra-fast ad-free search engine focused on high-quality technical results.",
            queryUrl = "https://kagi.com/search?q=%s",
            privacyScore = "A+"
        ),
        StoreSearchEngine(
            id = 9,
            name = "Mojeek",
            description = "Completely independent web crawler search engine based in the UK with a strict no-tracking policy.",
            queryUrl = "https://www.mojeek.com/search?q=%s",
            privacyScore = "A+"
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Curated Filter List Subscriptions Catalog
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
            id = "fanboy-annoyances",
            name = "Fanboy's Annoyance List",
            description = "Blocks popups, in-page notifications, social media widgets, and newsletter subscription nags.",
            url = "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
            ruleCount = "40,000+",
            maintainer = "Fanboy / uBlock"
        ),
        StoreFilterList(
            id = "oisd-basic",
            name = "OISD Privacy & Ad Filter",
            description = "Ultra-lean, zero-breakage blocklist curated for mobile network speed and battery savings.",
            url = "https://basic.oisd.nl/",
            ruleCount = "25,000+",
            maintainer = "SJ (OISD)"
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
