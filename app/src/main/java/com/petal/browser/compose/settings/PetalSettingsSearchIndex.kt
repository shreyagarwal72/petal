/*
 * PetalSettingsSearchIndex.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Comprehensive search index and typo-tolerant search engine for Petal Settings.
 * Indexes all preferences, toggles, controls, and features across all 10 categories.
 */

package com.petal.browser.compose.settings

import kotlin.math.min

/**
 * A searchable setting item representing a specific preference, toggle, or feature.
 */
data class SettingsSearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: SettingsCategory,
    val keywords: List<String>
)

/**
 * Result of a settings search, including matching categories, matching items, and optional spelling correction.
 */
data class SettingsSearchResult(
    val query: String,
    val didYouMean: String? = null,
    val matchingCategories: List<SettingsCategory> = emptyList(),
    val matchingItems: List<SettingsSearchItem> = emptyList()
)

object PetalSettingsSearchIndex {

    val allItems: List<SettingsSearchItem> = listOf(
        // ==================== API & INTEGRATIONS ====================
        SettingsSearchItem(
            id = "ai_provider",
            title = "AI Model Provider",
            subtitle = "Choose between OpenAI, Gemini, Claude, Groq, Ollama, DeepSeek, Mistral, OpenRouter, Perplexity, Cohere, xAI or Custom Endpoint",
            category = SettingsCategory.API_INTEGRATIONS,
            keywords = listOf("ai", "artificial intelligence", "model", "provider", "gpt", "gemini", "claude", "groq", "ollama", "deepseek", "mistral", "openrouter", "perplexity", "cohere", "xai", "grok", "custom", "llm", "chat")
        ),
        SettingsSearchItem(
            id = "ai_api_key",
            title = "API Key & Custom Endpoint",
            subtitle = "Configure API keys, custom base URLs, and custom model IDs for AI chat and research",
            category = SettingsCategory.API_INTEGRATIONS,
            keywords = listOf("api", "key", "token", "auth", "secret", "custom endpoint", "base url", "endpoint", "url", "model name")
        ),
        SettingsSearchItem(
            id = "ai_model_selection",
            title = "AI Model Selection",
            subtitle = "Select specific models (GPT-4o, Claude 3.5 Sonnet, Gemini 1.5 Pro, Llama 3, DeepSeek R1, etc.)",
            category = SettingsCategory.API_INTEGRATIONS,
            keywords = listOf("model", "gpt-4o", "sonnet", "haiku", "llama", "deepseek", "flash", "pro", "r1", "v3", "qwen")
        ),
        SettingsSearchItem(
            id = "api_live_suggestions",
            title = "Live Search Suggestions",
            subtitle = "Query search suggestion API dynamically as you type in the address bar",
            category = SettingsCategory.API_INTEGRATIONS,
            keywords = listOf("live suggestions", "query suggestions", "autocomplete", "search api", "typeahead")
        ),

        // ==================== APPEARANCE & THEME ====================
        SettingsSearchItem(
            id = "appearance_theme_mode",
            title = "App Theme Mode",
            subtitle = "Switch between Follow System, Light Mode, and Dark Mode",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("theme", "dark mode", "light mode", "system default", "day", "night", "follow system")
        ),
        SettingsSearchItem(
            id = "appearance_amoled",
            title = "Pure Black AMOLED Mode",
            subtitle = "True pitch-black #000000 backgrounds for OLED / AMOLED battery savings",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("amoled", "oled", "pure black", "pitch black", "black", "battery", "dark")
        ),
        SettingsSearchItem(
            id = "appearance_dynamic_color",
            title = "Material You Dynamic Color",
            subtitle = "Sample accent colors dynamically from your Android device wallpaper (Android 12+)",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("material you", "dynamic color", "monet", "wallpaper colors", "system accent")
        ),
        SettingsSearchItem(
            id = "appearance_fonts",
            title = "App Typography & Custom Fonts",
            subtitle = "Select built-in fonts (Petal, Product Sans, Roboto, Inter, Outfit, Open Sans, Space Grotesk) or import custom TTF/OTF",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("font", "typography", "custom font", "product sans", "roboto", "inter", "outfit", "typeface", "ttf", "otf", "text style")
        ),
        SettingsSearchItem(
            id = "appearance_font_sliders",
            title = "Font Width, Weight & Roundness",
            subtitle = "Fine-tune font variable axes: width stretching, bold weight, and corner roundness",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("font weight", "font width", "font roundness", "bold", "variable font", "stretch", "sliders")
        ),
        SettingsSearchItem(
            id = "appearance_color_styles",
            title = "Material 3 Color Styles & Palettes",
            subtitle = "Choose color style (Tonal Spot, Neutral, Vibrant, Expressive, Rainbow, Fruit Salad) and curated palettes",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("color style", "palette", "tonal spot", "vibrant", "expressive", "neutral", "fruit salad", "rainbow", "accents")
        ),
        SettingsSearchItem(
            id = "appearance_floating_tab_bar",
            title = "Floating Tab Bar",
            subtitle = "Modern floating island tab bar at the bottom with smooth spring animations",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("floating tab bar", "bottom bar", "tabs", "island bar", "tab bar", "navigation")
        ),
        SettingsSearchItem(
            id = "appearance_refresh_rate",
            title = "High Refresh Rate (90Hz / 120Hz / 144Hz)",
            subtitle = "Force peak display refresh rate for buttery-smooth scrolling and animations",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("refresh rate", "120hz", "90hz", "144hz", "fps", "smooth", "display rate", "hz")
        ),
        SettingsSearchItem(
            id = "appearance_search_widget",
            title = "Search Widget Styling",
            subtitle = "Configure home screen search widget style, transparency, and search engine",
            category = SettingsCategory.APPEARANCE,
            keywords = listOf("widget", "search widget", "home screen", "launcher widget", "transparency")
        ),

        // ==================== PRIVACY & SECURITY ====================
        SettingsSearchItem(
            id = "privacy_adblock",
            title = "AdBlock & Tracker Protection",
            subtitle = "Block invasive advertisements, tracking scripts, and malicious popups",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("adblock", "ads", "block ads", "trackers", "ad blocker", "filters", "advertisement", "ublock")
        ),
        SettingsSearchItem(
            id = "privacy_private_dns",
            title = "Secure Private DNS (DoH)",
            subtitle = "DNS-over-HTTPS via Cloudflare, AdGuard, Google, Quad9, Mullvad, or custom DNS",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("dns", "private dns", "doh", "dns over https", "cloudflare", "adguard", "quad9", "mullvad", "secure dns")
        ),
        SettingsSearchItem(
            id = "privacy_https_only",
            title = "HTTPS-Only Mode",
            subtitle = "Automatically upgrade all connections to secure HTTPS and warn on insecure HTTP",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("https", "https-only", "ssl", "tls", "secure connection", "insecure", "encryption")
        ),
        SettingsSearchItem(
            id = "privacy_cookies",
            title = "Block Third-Party Cookies",
            subtitle = "Prevent cross-site tracking cookies from monitoring your browsing across the web",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("cookies", "third-party cookies", "tracking cookies", "cross-site", "cookie blocking")
        ),
        SettingsSearchItem(
            id = "privacy_fingerprinting",
            title = "Fingerprint Protection",
            subtitle = "Protect against canvas, audio, and hardware browser fingerprinting techniques",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("fingerprint", "fingerprinting", "canvas", "hardware", "anonymity", "privacy shield")
        ),
        SettingsSearchItem(
            id = "privacy_webrtc",
            title = "WebRTC Leak Protection",
            subtitle = "Prevent local and public IP address leaks via WebRTC peer connections",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("webrtc", "ip leak", "vpn leak", "webrtc leak", "real ip")
        ),
        SettingsSearchItem(
            id = "privacy_dnt_gpc",
            title = "Do Not Track & Global Privacy Control (GPC)",
            subtitle = "Send DNT and GPC headers requesting websites not to sell or share your personal data",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("dnt", "gpc", "do not track", "global privacy control", "opt out", "data selling")
        ),
        SettingsSearchItem(
            id = "privacy_trim_referrers",
            title = "Trim Referrers",
            subtitle = "Strip URL query paths and parameters from HTTP referrers when navigating between sites",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("referrer", "trim referrers", "strip referrers", "http headers", "url leak")
        ),
        SettingsSearchItem(
            id = "privacy_webauthn",
            title = "WebAuthn & Passkeys",
            subtitle = "Sign in securely to websites using device biometric credentials and passkeys",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("webauthn", "passkey", "passkeys", "biometrics", "fingerprint login", "credentials")
        ),
        SettingsSearchItem(
            id = "privacy_popups",
            title = "Block Pop-ups & Redirects",
            subtitle = "Block unwanted pop-up windows and abusive new tab redirects",
            category = SettingsCategory.PRIVACY,
            keywords = listOf("popups", "block popups", "redirects", "new windows", "spam")
        ),

        // ==================== SEARCH & HOMEPAGE ====================
        SettingsSearchItem(
            id = "search_engine",
            title = "Default Search Engine",
            subtitle = "Select default engine: Google, DuckDuckGo, Bing, Brave, Yahoo, Ecosia, StartPage, Baidu, Yandex, or Qwant",
            category = SettingsCategory.SEARCH_HOMEPAGE,
            keywords = listOf("search engine", "google", "duckduckgo", "bing", "brave", "startpage", "ecosia", "yahoo", "baidu", "yandex", "qwant")
        ),
        SettingsSearchItem(
            id = "search_homepage_type",
            title = "Homepage & New Tab Layout",
            subtitle = "Choose between Petal Expressive Home, Blank Page, or Custom URL",
            category = SettingsCategory.SEARCH_HOMEPAGE,
            keywords = listOf("homepage", "new tab", "home", "start page", "custom url", "blank page")
        ),
        SettingsSearchItem(
            id = "search_custom_homepage_url",
            title = "Custom Homepage URL",
            subtitle = "Set any custom website address to open whenever you press the home button or open a new tab",
            category = SettingsCategory.SEARCH_HOMEPAGE,
            keywords = listOf("custom homepage", "start url", "homepage url", "web address")
        ),
        SettingsSearchItem(
            id = "search_background_play",
            title = "Background Audio & Video Play",
            subtitle = "Keep YouTube, Spotify, and media playing seamlessly when switching apps or locking screen",
            category = SettingsCategory.SEARCH_HOMEPAGE,
            keywords = listOf("background play", "background audio", "media", "youtube", "music", "lock screen audio")
        ),
        SettingsSearchItem(
            id = "search_auto_pip",
            title = "Automatic Picture-in-Picture (PiP)",
            subtitle = "Automatically pop playing video into a floating PiP overlay window when leaving the browser",
            category = SettingsCategory.SEARCH_HOMEPAGE,
            keywords = listOf("pip", "picture in picture", "floating video", "video popout", "mini player")
        ),
        SettingsSearchItem(
            id = "search_force_dark_web",
            title = "Force Dark Mode for Web Content",
            subtitle = "Invert web page colors using Blink Chromium dark algorithm for comfortable night reading",
            category = SettingsCategory.SEARCH_HOMEPAGE,
            keywords = listOf("force dark", "dark web", "invert colors", "web dark mode", "night mode web")
        ),

        // ==================== DISPLAY & ACCESSIBILITY ====================
        SettingsSearchItem(
            id = "display_haptics",
            title = "Touch & Feedback Haptics",
            subtitle = "Rich tactile vibrational feedback for tabs, buttons, gestures, and long-presses",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("haptics", "vibration", "touch feedback", "tactile", "buzz", "click haptics")
        ),
        SettingsSearchItem(
            id = "display_predictive_back",
            title = "Predictive Back Animations",
            subtitle = "Smooth Android 14+ predictive back gestures with live destination preview sheets",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("predictive back", "back gesture", "animations", "gesture navigation", "back preview")
        ),
        SettingsSearchItem(
            id = "display_depth_blur",
            title = "Depth Blur & Frosted Glass",
            subtitle = "Render real-time blur and frosted glass layers behind dialogs, bottom sheets, and toolbars",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("blur", "depth blur", "frosted glass", "translucency", "glassmorphism", "visual effects")
        ),
        SettingsSearchItem(
            id = "display_font_scale",
            title = "Text Scaling & Font Size",
            subtitle = "Adjust web page font size percentage from 50% up to 200% for readability",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("font scale", "text size", "font size", "text scale", "zoom text", "larger text", "magnify")
        ),
        SettingsSearchItem(
            id = "display_zoom_level",
            title = "Page Zoom Level & Force Zoom",
            subtitle = "Override website viewport zoom limits and zoom in or out freely on any web page",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("zoom", "page zoom", "force zoom", "pinch to zoom", "zoom level", "viewport")
        ),
        SettingsSearchItem(
            id = "display_reader_mode",
            title = "Automatic Reader Mode Detection",
            subtitle = "Detect article pages automatically and show reader view chip for clutter-free reading",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("reader mode", "reading view", "articles", "distraction free", "readability")
        ),
        SettingsSearchItem(
            id = "display_caret_browsing",
            title = "Caret Browsing",
            subtitle = "Navigate web pages and select text with a movable cursor caret like a text editor",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("caret", "caret browsing", "keyboard cursor", "text cursor", "select text")
        ),
        SettingsSearchItem(
            id = "display_address_bar_gestures",
            title = "Address Bar Swipe Gestures",
            subtitle = "Swipe horizontally on the address bar to switch between open browser tabs rapidly",
            category = SettingsCategory.DISPLAY_ZOOM,
            keywords = listOf("swipe tabs", "address bar swipe", "switch tabs", "gestures", "swipe navigation")
        ),

        // ==================== EXPERIMENTAL & ADVANCED ====================
        SettingsSearchItem(
            id = "exp_language",
            title = "App Language",
            subtitle = "Choose language override for Petal Browser independent of Android system language",
            category = SettingsCategory.EXPERIMENTAL,
            keywords = listOf("language", "locale", "translation", "english", "spanish", "french", "german", "chinese", "hindi", "arabic")
        ),
        SettingsSearchItem(
            id = "exp_address_bar_position",
            title = "Address Bar Position (Top vs Bottom)",
            subtitle = "Place the URL address bar and controls at the bottom for easy one-handed reach or top",
            category = SettingsCategory.EXPERIMENTAL,
            keywords = listOf("address bar position", "bottom bar", "top bar", "url bar position", "one-handed")
        ),
        SettingsSearchItem(
            id = "exp_app_lock",
            title = "App Lock & Passcode Protection",
            subtitle = "Secure Petal Browser with a passcode PIN or fingerprint authentication upon opening",
            category = SettingsCategory.EXPERIMENTAL,
            keywords = listOf("app lock", "passcode", "pin", "lock", "security lock", "biometric lock", "protect")
        ),
        SettingsSearchItem(
            id = "exp_double_back_exit",
            title = "Double Tap Back to Exit",
            subtitle = "Require double pressing back button within 2 seconds to prevent accidental closing",
            category = SettingsCategory.EXPERIMENTAL,
            keywords = listOf("double back", "exit", "close app", "accidental exit", "back button")
        ),

        // ==================== MISCELLANEOUS ====================
        SettingsSearchItem(
            id = "misc_default_download_manager",
            title = "Default Download Manager",
            subtitle = "Choose between Petal's high-speed in-app downloader or external download managers (1DM, ADM, AB DM, Navi)",
            category = SettingsCategory.MISCELLANEOUS,
            keywords = listOf("download manager", "default download", "external download", "1dm", "adm", "ab download manager", "navi", "external downloader", "download engine", "in-app downloader")
        ),
        SettingsSearchItem(
            id = "misc_auto_open_apps",
            title = "Open Links in External Apps",
            subtitle = "Automatically launch native installed apps for supported URLs (YouTube, Maps, Twitter, Reddit)",
            category = SettingsCategory.MISCELLANEOUS,
            keywords = listOf("external apps", "open in apps", "native apps", "youtube app", "deep links", "intent")
        ),
        SettingsSearchItem(
            id = "misc_check_updates_launch",
            title = "Check for Updates on App Launch",
            subtitle = "Automatically verify latest GitHub releases and notify when a new APK update is available",
            category = SettingsCategory.MISCELLANEOUS,
            keywords = listOf("update on launch", "auto check updates", "version check", "release notify")
        ),

        // ==================== DATA & BACKUP ====================
        SettingsSearchItem(
            id = "data_export_backup",
            title = "Export Full Backup (JSON)",
            subtitle = "Backup bookmarks, browsing history, start sites, tab sessions, saved sites, and settings to a JSON file",
            category = SettingsCategory.DATA_STORAGE,
            keywords = listOf("backup", "export backup", "json backup", "save data", "full backup", "data export")
        ),
        SettingsSearchItem(
            id = "data_restore_backup",
            title = "Restore from Backup File",
            subtitle = "Restore all your browser data, tabs, history, and bookmarks from a previously saved JSON file",
            category = SettingsCategory.DATA_STORAGE,
            keywords = listOf("restore", "import backup", "restore json", "load backup", "data import")
        ),
        SettingsSearchItem(
            id = "data_export_bookmarks_saf",
            title = "Export Bookmarks (HTML / JSON)",
            subtitle = "Export bookmarks in standard HTML format (for Chrome, Firefox, Safari) or JSON format",
            category = SettingsCategory.DATA_STORAGE,
            keywords = listOf("export bookmarks", "html bookmarks", "netscape bookmarks", "saf", "save bookmarks")
        ),
        SettingsSearchItem(
            id = "data_import_bookmarks_saf",
            title = "Import Bookmarks (HTML / JSON)",
            subtitle = "Import bookmarks from other browsers via standard Netscape HTML file or JSON",
            category = SettingsCategory.DATA_STORAGE,
            keywords = listOf("import bookmarks", "html import", "chrome bookmarks", "firefox bookmarks", "saf import")
        ),
        SettingsSearchItem(
            id = "data_clear_browsing_data",
            title = "Clear Browsing Data & Cache",
            subtitle = "Delete browsing history, cookies, cached images, web storage, and form autofill data",
            category = SettingsCategory.DATA_STORAGE,
            keywords = listOf("clear data", "clear cache", "delete history", "clear cookies", "wipe data", "storage")
        ),

        // ==================== UPDATER & DIAGNOSTICS ====================
        SettingsSearchItem(
            id = "updater_check_now",
            title = "Check for Updates Now",
            subtitle = "Query GitHub release API for new Petal Browser versions and changelogs",
            category = SettingsCategory.UPDATER,
            keywords = listOf("check for updates", "update app", "github release", "latest version", "apk update")
        ),
        SettingsSearchItem(
            id = "updater_crash_reporting",
            title = "Crash Reporting & Diagnostics",
            subtitle = "View crash logs, diagnostics stack traces, and manage error reporting",
            category = SettingsCategory.UPDATER,
            keywords = listOf("crash logs", "diagnostics", "stack trace", "error logs", "bug report", "reporting")
        ),

        // ==================== ABOUT & DEVELOPER ====================
        SettingsSearchItem(
            id = "about_petal_version",
            title = "App Version & Build Information",
            subtitle = "View current Petal Browser release version, versionCode, architecture, and engine",
            category = SettingsCategory.ABOUT,
            keywords = listOf("version", "build", "about", "architecture", "v2.0", "license", "package")
        ),
        SettingsSearchItem(
            id = "about_developer_github",
            title = "Developer GitHub & Source Code",
            subtitle = "Browse the open-source repository, star the project, or report issues on GitHub",
            category = SettingsCategory.ABOUT,
            keywords = listOf("developer", "github", "source code", "open source", "repository", "issues")
        )
    )

    private val allCategories: List<SettingsCategory> = listOf(
        SettingsCategory.API_INTEGRATIONS,
        SettingsCategory.APPEARANCE,
        SettingsCategory.PRIVACY,
        SettingsCategory.SEARCH_HOMEPAGE,
        SettingsCategory.DISPLAY_ZOOM,
        SettingsCategory.EXPERIMENTAL,
        SettingsCategory.MISCELLANEOUS,
        SettingsCategory.DATA_STORAGE,
        SettingsCategory.UPDATER,
        SettingsCategory.ABOUT
    )

    // Dictionary of common settings terms for spelling correction
    private val dictionary: List<String> = listOf(
        "adblock", "ads", "theme", "fonts", "dark", "light", "amoled", "oled", "dynamic",
        "search", "google", "engine", "homepage", "privacy", "dns", "cloudflare", "adguard",
        "cookies", "https", "fingerprint", "webrtc", "haptics", "vibration", "zoom",
        "reader", "caret", "language", "backup", "restore", "export", "import", "bookmarks",
        "history", "updater", "updates", "developer", "about", "model", "provider", "groq",
        "gemini", "claude", "ollama", "deepseek", "openai", "token", "key", "gestures",
        "swipe", "lock", "passcode", "blur", "scaling", "audio", "video", "pip", "cache"
    )

    /**
     * Searches categories and detailed preferences using tokenized multi-keyword matching
     * and Levenshtein distance typo/misspelling detection.
     */
    fun search(rawQuery: String): SettingsSearchResult {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            return SettingsSearchResult(
                query = "",
                didYouMean = null,
                matchingCategories = allCategories,
                matchingItems = emptyList()
            )
        }

        val tokens = trimmed.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

        // Find matching categories
        val matchingCategories = allCategories.filter { cat ->
            val target = "${cat.title} ${cat.subtitle}".lowercase()
            tokens.all { token -> target.contains(token) }
        }

        // Find matching detailed items
        val matchingItems = allItems.filter { item ->
            val target = "${item.title} ${item.subtitle} ${item.keywords.joinToString(" ")}".lowercase()
            tokens.all { token -> target.contains(token) }
        }

        // Check for typo/misspelling if no or few results found
        var didYouMean: String? = null
        if (matchingCategories.isEmpty() && matchingItems.isEmpty()) {
            val suggestedTokens = tokens.map { token ->
                findBestMatchInDictionary(token) ?: token
            }
            val suggestedQuery = suggestedTokens.joinToString(" ")
            if (!suggestedQuery.equals(trimmed, ignoreCase = true)) {
                // Verify that the suggested query actually yields results
                val testTokens = suggestedQuery.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val testHasCat = allCategories.any { cat ->
                    val t = "${cat.title} ${cat.subtitle}".lowercase()
                    testTokens.all { tok -> t.contains(tok) }
                }
                val testHasItems = allItems.any { item ->
                    val t = "${item.title} ${item.subtitle} ${item.keywords.joinToString(" ")}".lowercase()
                    testTokens.all { tok -> t.contains(tok) }
                }
                if (testHasCat || testHasItems) {
                    didYouMean = suggestedQuery
                }
            }
        }

        return SettingsSearchResult(
            query = trimmed,
            didYouMean = didYouMean,
            matchingCategories = matchingCategories,
            matchingItems = matchingItems
        )
    }

    private fun findBestMatchInDictionary(word: String): String? {
        if (word.length <= 2) return null
        var bestCandidate: String? = null
        var bestDistance = Int.MAX_VALUE

        // Max allowed edits: 1 for short words (3-5), 2 for longer words (>5)
        val maxAllowedDistance = if (word.length <= 5) 1 else 2

        for (dictWord in dictionary) {
            val distance = levenshteinDistance(word.lowercase(), dictWord.lowercase())
            if (distance <= maxAllowedDistance && distance < bestDistance) {
                bestDistance = distance
                bestCandidate = dictWord
            }
        }
        return bestCandidate
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                )
            }
        }
        return dp[m][n]
    }
}
