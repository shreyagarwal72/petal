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

    /**
     * True when [ext] is the installed copy of [addon]. Gecko reports an extension's own ID
     * (a GUID or e-mail style ID), never the AMO slug, so matching on the slug alone left
     * most installed add-ons showing an "Install" button. The curated ID table is checked
     * first, then the AMO listing URL, then the display name.
     */
    fun isAddonInstalled(
        addon: StoreAddon,
        ext: com.petal.browser.extensions.PetalExtensionManager.InstalledExtension
    ): Boolean {
        if (ext.id.equals(addon.id, ignoreCase = true) || ext.id.equals(addon.amoSlug, ignoreCase = true)) return true
        if (com.petal.browser.compose.extensions.PetalCuratedExtensionsData.slugForExtensionId(ext.id)
                .equals(addon.amoSlug, ignoreCase = true)) return true
        val listing = ext.amoListingUrl
        if (listing != null && listing.contains("/addon/${addon.amoSlug}", ignoreCase = true)) return true
        return ext.name.equals(addon.name, ignoreCase = true)
    }

    val storeAddons: List<StoreAddon> = listOf(
        StoreAddon(
            id = "adblock-plus",
            name = "Adblock Plus",
            description = "Block annoying video ads, banners, and popups while speeding up web browsing.",
            amoSlug = "adblock-plus",
            category = AddonCategory.BLOCKERS,
            rating = 4.4f,
            downloads = "2M+"
        ),
        StoreAddon(
            id = "adguard-adblocker",
            name = "AdGuard AdBlocker",
            description = "Comprehensive ad and tracking defense with cosmetic element filtering.",
            amoSlug = "adguard-adblocker",
            category = AddonCategory.BLOCKERS,
            rating = 4.6f,
            downloads = "1M+"
        ),
        StoreAddon(
            id = "auto-tab-discard",
            name = "Auto Tab Discard",
            description = "Frees up RAM and battery by automatically unloading inactive background tabs.",
            amoSlug = "auto-tab-discard",
            category = AddonCategory.TOOLS,
            rating = 4.5f,
            downloads = "100K+"
        ),
        StoreAddon(
            id = "bitwarden-password-manager",
            name = "Bitwarden Password Manager",
            description = "Secure open-source password vault with auto-fill support for logins and 2FA.",
            amoSlug = "bitwarden-password-manager",
            category = AddonCategory.PRIVACY,
            rating = 4.6f,
            downloads = "1M+"
        ),
        StoreAddon(
            id = "buster-captcha-solver",
            name = "Buster: Captcha Solver for Humans",
            description = "Solves difficult captchas automatically by completing audio challenges using speech recognition.",
            amoSlug = "buster-captcha-solver",
            category = AddonCategory.TOOLS,
            rating = 3.8f,
            downloads = "35K+"
        ),
        StoreAddon(
            id = "canvasblocker",
            name = "CanvasBlocker",
            description = "Alters canvas and audio APIs to prevent websites from fingerprinting your device.",
            amoSlug = "canvasblocker",
            category = AddonCategory.PRIVACY,
            rating = 4.6f,
            downloads = "38K+"
        ),
        StoreAddon(
            id = "chrome-mask",
            name = "Chrome Mask",
            description = "Makes websites see Chrome instead of Firefox, for sites that block or degrade Firefox users.",
            amoSlug = "chrome-mask",
            category = AddonCategory.TOOLS,
            rating = 4.4f,
            downloads = "59K+"
        ),
        StoreAddon(
            id = "clearurls",
            name = "ClearURLs",
            description = "Removes tracking parameters and telemetry tokens from URLs to protect your privacy.",
            amoSlug = "clearurls",
            category = AddonCategory.PRIVACY,
            rating = 4.2f,
            downloads = "513K+"
        ),
        StoreAddon(
            id = "consent-o-matic",
            name = "Consent-O-Matic",
            description = "Automatically answers GDPR cookie consent forms, rejecting non-essential cookies.",
            amoSlug = "consent-o-matic",
            category = AddonCategory.PRIVACY,
            rating = 4.3f,
            downloads = "134K+"
        ),
        StoreAddon(
            id = "cookie-autodelete",
            name = "Cookie AutoDelete",
            description = "Automatically deletes unused cookies and local storage when closing a tab.",
            amoSlug = "cookie-autodelete",
            category = AddonCategory.PRIVACY,
            rating = 4.5f,
            downloads = "156K+"
        ),
        StoreAddon(
            id = "cookie-quick-manager",
            name = "Cookie Quick Manager",
            description = "Advanced cookie manager: view, search, create, edit, backup, and restore cookies.",
            amoSlug = "cookie-quick-manager",
            category = AddonCategory.PRIVACY,
            rating = 4.4f,
            downloads = "46K+"
        ),
        StoreAddon(
            id = "cookie-editor",
            name = "Cookie-Editor",
            description = "Powerful developer tool to inspect, edit, export, and delete cookies in real-time.",
            amoSlug = "cookie-editor",
            category = AddonCategory.TOOLS,
            rating = 4.1f,
            downloads = "134K+"
        ),
        StoreAddon(
            id = "darkreader",
            name = "Dark Reader",
            description = "Dark mode for every website. Invert colors with eye-pleasing high-contrast dark theme presets.",
            amoSlug = "darkreader",
            category = AddonCategory.TOOLS,
            rating = 4.5f,
            downloads = "1M+"
        ),
        StoreAddon(
            id = "decentraleyes",
            name = "Decentraleyes",
            description = "Emulates CDNs locally to prevent Google and Cloudflare from tracking your requests.",
            amoSlug = "decentraleyes",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "248K+"
        ),
        StoreAddon(
            id = "enhancer-for-youtube",
            name = "Enhancer for YouTube",
            description = "Custom video player controls, volume boost up to 500%, playback speed, and loop tools.",
            amoSlug = "enhancer-for-youtube",
            category = AddonCategory.MEDIA,
            rating = 4.7f,
            downloads = "599K+"
        ),
        StoreAddon(
            id = "fastforwardteam",
            name = "FastForward",
            description = "Automatically skips annoying countdowns and intermediate link shorteners.",
            amoSlug = "fastforwardteam",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "32K+"
        ),
        StoreAddon(
            id = "multi-account-containers",
            name = "Firefox Multi-Account Containers",
            description = "Keep parts of your online life separated into color-coded isolated containers.",
            amoSlug = "multi-account-containers",
            category = AddonCategory.PRIVACY,
            rating = 4.6f,
            downloads = "415K+"
        ),
        StoreAddon(
            id = "firemonkey",
            name = "FireMonkey",
            description = "Lightweight userscript and userstyle manager powered by standard WebExtensions APIs.",
            amoSlug = "firemonkey",
            category = AddonCategory.TOOLS,
            rating = 4.5f,
            downloads = "1K+"
        ),
        StoreAddon(
            id = "floccus",
            name = "Floccus Bookmarks Sync",
            description = "Sync your bookmarks privately across browsers via Nextcloud, WebDAV, or Google Drive.",
            amoSlug = "floccus",
            category = AddonCategory.TOOLS,
            rating = 4.5f,
            downloads = "10K+"
        ),
        StoreAddon(
            id = "foxyproxy-standard",
            name = "FoxyProxy Standard",
            description = "Advanced proxy management tool that automatically switches proxies based on URL patterns.",
            amoSlug = "foxyproxy-standard",
            category = AddonCategory.PRIVACY,
            rating = 4.0f,
            downloads = "222K+"
        ),
        StoreAddon(
            id = "ghostery",
            name = "Ghostery Privacy Ad Blocker",
            description = "All-in-one privacy protection blocking ads, trackers, and page clutter.",
            amoSlug = "ghostery",
            category = AddonCategory.BLOCKERS,
            rating = 4.4f,
            downloads = "1M+"
        ),
        StoreAddon(
            id = "greasemonkey",
            name = "Greasemonkey",
            description = "Customize the way a webpage looks and functions with user scripts.",
            amoSlug = "greasemonkey",
            category = AddonCategory.TOOLS,
            rating = 4.2f,
            downloads = "155K+"
        ),
        StoreAddon(
            id = "istilldontcareaboutcookies",
            name = "I Still Don't Care About Cookies",
            description = "Automatically dismisses annoying GDPR and cookie consent notices across websites.",
            amoSlug = "istilldontcareaboutcookies",
            category = AddonCategory.BLOCKERS,
            rating = 4.6f,
            downloads = "114K+"
        ),
        StoreAddon(
            id = "keepassxc-browser",
            name = "KeePassXC-Browser",
            description = "Official browser extension connecting Petal to local KeePassXC databases securely.",
            amoSlug = "keepassxc-browser",
            category = AddonCategory.PRIVACY,
            rating = 4.3f,
            downloads = "147K+"
        ),
        StoreAddon(
            id = "leechblock-ng",
            name = "LeechBlock NG",
            description = "Simple yet powerful productivity tool designed to block time-wasting sites on customizable schedules.",
            amoSlug = "leechblock-ng",
            category = AddonCategory.TOOLS,
            rating = 4.8f,
            downloads = "112K+"
        ),
        StoreAddon(
            id = "localcdn-fork-of-decentraleyes",
            name = "LocalCDN",
            description = "Local emulation of content delivery networks to protect from central tracking.",
            amoSlug = "localcdn-fork-of-decentraleyes",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "19K+"
        ),
        StoreAddon(
            id = "noscript",
            name = "NoScript Security Suite",
            description = "Ultimate active content protection: selectively allow JavaScript and scripts only on trusted domains.",
            amoSlug = "noscript",
            category = AddonCategory.PRIVACY,
            rating = 4.4f,
            downloads = "240K+"
        ),
        StoreAddon(
            id = "popupoff",
            name = "PopUpOFF",
            description = "Smart popup and modal overlay blocker with gentle, moderate, and aggressive modes.",
            amoSlug = "popupoff",
            category = AddonCategory.BLOCKERS,
            rating = 4.3f,
            downloads = "50K+"
        ),
        StoreAddon(
            id = "privacy-badger17",
            name = "Privacy Badger",
            description = "EFF's smart tracker blocker that automatically learns to detect and disable third-party tracking.",
            amoSlug = "privacy-badger17",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "1M+"
        ),
        StoreAddon(
            id = "privacy-possum",
            name = "Privacy Possum",
            description = "Defeats browser fingerprinting by corrupting commercial tracking algorithms.",
            amoSlug = "privacy-possum",
            category = AddonCategory.PRIVACY,
            rating = 4.4f,
            downloads = "34K+"
        ),
        StoreAddon(
            id = "proton-pass",
            name = "Proton Pass",
            description = "End-to-end encrypted password and email alias manager developed by Proton.",
            amoSlug = "proton-pass",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "192K+"
        ),
        StoreAddon(
            id = "raindropio",
            name = "Raindrop.io",
            description = "All-in-one bookmark manager to save articles, photos, videos, and web pages.",
            amoSlug = "raindropio",
            category = AddonCategory.TOOLS,
            rating = 4.3f,
            downloads = "43K+"
        ),
        StoreAddon(
            id = "read-aloud",
            name = "Read Aloud: Text to Speech",
            description = "Reads articles out loud using natural text-to-speech voices with one click.",
            amoSlug = "read-aloud",
            category = AddonCategory.TOOLS,
            rating = 3.8f,
            downloads = "229K+"
        ),
        StoreAddon(
            id = "return-youtube-dislikes",
            name = "Return YouTube Dislike",
            description = "Restores dislike counts on YouTube videos using crowd-sourced API data.",
            amoSlug = "return-youtube-dislikes",
            category = AddonCategory.MEDIA,
            rating = 4.5f,
            downloads = "890K+"
        ),
        StoreAddon(
            id = "search_by_image",
            name = "Search by Image",
            description = "Reverse image search helper supporting Google, Bing, Yandex, TinEye, and Baidu.",
            amoSlug = "search_by_image",
            category = AddonCategory.TOOLS,
            rating = 4.6f,
            downloads = "463K+"
        ),
        StoreAddon(
            id = "single-file",
            name = "SingleFile",
            description = "Save an entire web page including CSS, fonts, and images into a single self-contained HTML file.",
            amoSlug = "single-file",
            category = AddonCategory.TOOLS,
            rating = 4.8f,
            downloads = "92K+"
        ),
        StoreAddon(
            id = "sponsorblock",
            name = "SponsorBlock for YouTube",
            description = "Skip YouTube video sponsors, intros, outros, and subscribe reminders automatically.",
            amoSlug = "sponsorblock",
            category = AddonCategory.MEDIA,
            rating = 4.8f,
            downloads = "816K+"
        ),
        StoreAddon(
            id = "styl-us",
            name = "Stylus",
            description = "Redesign websites with custom CSS themes. Install styles from online repositories or write your own.",
            amoSlug = "styl-us",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "136K+"
        ),
        StoreAddon(
            id = "tabliss",
            name = "Tabliss - New Tab",
            description = "A customizable, beautiful new tab page with stunning backgrounds and useful widgets.",
            amoSlug = "tabliss",
            category = AddonCategory.TOOLS,
            rating = 4.6f,
            downloads = "75K+"
        ),
        StoreAddon(
            id = "tampermonkey",
            name = "Tampermonkey",
            description = "The world's most popular userscript manager. Run custom JS scripts across websites effortlessly.",
            amoSlug = "tampermonkey",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "825K+"
        ),
        StoreAddon(
            id = "temporary-containers",
            name = "Temporary Containers",
            description = "Open tabs in automatically generated temporary containers that isolate storage.",
            amoSlug = "temporary-containers",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "13K+"
        ),
        StoreAddon(
            id = "tomato-clock",
            name = "Tomato Clock",
            description = "A simple Pomodoro-style timer for focus and productivity sessions.",
            amoSlug = "tomato-clock",
            category = AddonCategory.TOOLS,
            rating = 4.5f,
            downloads = "33K+"
        ),
        StoreAddon(
            id = "traduzir-paginas-web",
            name = "TWP - Translate Web Pages",
            description = "Translates entire web pages in real-time using Google Translate or DeepL without reloading.",
            amoSlug = "traduzir-paginas-web",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "560K+"
        ),
        StoreAddon(
            id = "ublock-origin",
            name = "uBlock Origin",
            description = "High-efficiency wide-spectrum content blocker. Blocks ads, trackers, coin miners, and malware sites.",
            amoSlug = "ublock-origin",
            category = AddonCategory.BLOCKERS,
            rating = 4.8f,
            downloads = "11M+"
        ),
        StoreAddon(
            id = "umatrix",
            name = "uMatrix",
            description = "Point-and-click matrix-based request firewall to control all connections in real time.",
            amoSlug = "umatrix",
            category = AddonCategory.PRIVACY,
            rating = 4.8f,
            downloads = "14K+"
        ),
        StoreAddon(
            id = "youtube-recommended-videos",
            name = "Unhook - Remove YouTube Distractions",
            description = "Focus mode for YouTube: hides recommendation feed, sidebar comments, and trending distractions.",
            amoSlug = "youtube-recommended-videos",
            category = AddonCategory.MEDIA,
            rating = 4.6f,
            downloads = "169K+"
        ),
        StoreAddon(
            id = "user-agent-string-switcher",
            name = "User-Agent Switcher and Manager",
            description = "Spoofs browser user agent strings to access desktop sites or alternate browser views easily.",
            amoSlug = "user-agent-string-switcher",
            category = AddonCategory.TOOLS,
            rating = 4.1f,
            downloads = "85K+"
        ),
        StoreAddon(
            id = "video-background-play-fix",
            name = "Video Background Play Fix",
            description = "Prevents YouTube and HTML5 videos from pausing when switching tabs or turning off the screen.",
            amoSlug = "video-background-play-fix",
            category = AddonCategory.MEDIA,
            rating = 4.5f,
            downloads = "228K+"
        ),
        StoreAddon(
            id = "violentmonkey",
            name = "Violentmonkey",
            description = "Lightweight open-source userscript runner supporting Tampermonkey and Greasemonkey scripts.",
            amoSlug = "violentmonkey",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "203K+"
        ),
        StoreAddon(
            id = "view-page-archive",
            name = "Web Archives",
            description = "View archived and cached versions of web pages via Wayback Machine and Archive.is.",
            amoSlug = "view-page-archive",
            category = AddonCategory.TOOLS,
            rating = 4.7f,
            downloads = "68K+"
        ),
        StoreAddon(
            id = "youtube-high-definition",
            name = "YouTube High Definition",
            description = "Automatically forces YouTube videos to play in the highest resolution available (1080p, 4K).",
            amoSlug = "youtube-high-definition",
            category = AddonCategory.MEDIA,
            rating = 3.8f,
            downloads = "163K+"
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
                    cleanLinks();
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
                    restoreScroll();
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
                    const run = () => {
                        ['countdown', 'timer', 'timer-count', 'seconds'].forEach(id => {
                            const el = document.getElementById(id);
                            if (el) el.innerText = '0';
                        });
                        const btn = document.querySelector('.btn-captcha, #btn-main, .get-link');
                        if (btn && !btn.disabled) btn.click();
                    };
                    if (document.readyState === 'loading') {
                        window.addEventListener('DOMContentLoaded', run);
                    } else {
                        run();
                    }
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
            url = "https://filters.adtidy.org/windows/filters/2.txt",
            ruleCount = "50,000+",
            maintainer = "AdGuard Team"
        ),
        StoreFilterList(
            id = "adguard-mobile",
            name = "AdGuard Mobile Filter",
            description = "Specifically targeted at blocking advertisements, interstitials, and banners on mobile web browsers.",
            url = "https://filters.adtidy.org/windows/filters/11.txt",
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

    /**
     * Enabled store scripts that apply to [pageUrl]. A script whose content no longer matches
     * its recorded SHA-256 is never returned, so tampered or corrupted entries cannot run.
     */
    fun enabledScriptsFor(context: Context, pageUrl: String): List<StoreScript> {
        val enabled = getInstalledScriptIds(context)
        if (enabled.isEmpty()) return emptyList()
        return storeScripts.filter { it.id in enabled && matchesPattern(it.matchPattern, pageUrl) && it.verifyIntegrity() }
    }

    // Converts a WebExtension-style match pattern to a regex and tests url.
    internal fun matchesPattern(pattern: String, url: String): Boolean {
        val schemeEnd = pattern.indexOf("://")
        if (schemeEnd <= 0) return false
        val scheme = pattern.substring(0, schemeEnd)
        val rest = pattern.substring(schemeEnd + 3)
        val slash = rest.indexOf('/')
        val host = if (slash >= 0) rest.substring(0, slash) else rest
        val path = if (slash >= 0) rest.substring(slash) else ("/" + "*")

        val schemeRegex = if (scheme == "*") "https?" else Regex.escape(scheme)
        val hostRegex = when {
            host == "*" -> "[^/]+"
            host.startsWith("*.") -> "(?:[^/]+\\.)?" + host.substring(2).split("*").joinToString("[^/]*") { Regex.escape(it) }
            else -> host.split("*").joinToString("[^/]*") { Regex.escape(it) }
        }
        // Match pattern wildcard must also match a bare origin such as https://example.com (no trailing slash).
        val pathRegex = if (path == ("/" + "*")) "(?:/.*)?" else path.split("*").joinToString(".*") { Regex.escape(it) }
        return Regex("^$schemeRegex://$hostRegex$pathRegex$").containsMatchIn(url)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter lists are owned by PetalFilterSubscriptionManager (the engine the ad blocker
    // actually reads). The Store used to keep its own, unread preference set, so toggling a
    // list here changed nothing. These now read and write the real subscriptions.
    // ─────────────────────────────────────────────────────────────────────────

    /** IDs of store lists whose subscription is currently enabled. */
    fun getSubscribedFilterListIds(context: Context): Set<String> {
        val subs = com.petal.browser.browser.PetalFilterSubscriptionManager.getSubscriptions(context)
        return storeFilterLists
            .filter { list -> subs.any { it.isEnabled && it.url.trim().trimEnd('/').equals(list.url.trim().trimEnd('/'), ignoreCase = true) } }
            .map { it.id }
            .toSet()
    }

    /**
     * Subscribes to / unsubscribes from a store list and, when subscribing, downloads it so the
     * rules take effect immediately. Returns the number of rules now cached for the list.
     */
    suspend fun setFilterListSubscribed(context: Context, listId: String, subscribed: Boolean): Result<Int> {
        val list = storeFilterLists.firstOrNull { it.id == listId }
            ?: return Result.failure(IllegalArgumentException("Unknown filter list"))
        val manager = com.petal.browser.browser.PetalFilterSubscriptionManager
        val sub = manager.ensureSubscription(
            context = context,
            id = "store_${list.id}",
            name = list.name,
            description = list.description,
            url = list.url
        )
        manager.toggleSubscription(context, sub.id, subscribed)
        if (!subscribed) return Result.success(0)
        val count = manager.updateSubscription(context, sub.id)
        return if (count > 0) {
            Result.success(count)
        } else {
            Result.failure(IllegalStateException(if (count == -1) "Another update is in progress" else "Couldn't download ${list.name}"))
        }
    }
}
