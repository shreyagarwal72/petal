package com.petal.browser.flags

/**
 * PetalFlagsRegistry
 * ─────────────────────────────────────────────────────────────────────────
 * Official catalog of curated, high-impact experimental flags for Petal Browser
 * and the Mozilla GeckoView Quantum engine.
 */
object PetalFlagsRegistry {

    val flags: List<PetalFlagDefinition> = listOf(
        PetalFlagDefinition(
            id = "enable-webrender-all",
            title = "Hardware GPU WebRender Rasterization",
            description = "Forces Mozilla GeckoView to route all painting through the WebRender GPU compositor, unlocking 120Hz smooth scrolling and eliminating software frame drops.",
            category = FlagCategory.RENDERING,
            prefKey = "gfx.webrender.all"
        ),
        PetalFlagDefinition(
            id = "enable-http3-quic",
            title = "HTTP/3 & QUIC 0-RTT Transport",
            description = "Enables the next-generation UDP-based HTTP/3 transport layer for near-instant web page handshakes and resilient mobile connection migration.",
            category = FlagCategory.NETWORK,
            prefKey = "network.http.http3.enable"
        ),
        PetalFlagDefinition(
            id = "enable-smooth-scrolling",
            title = "Fluid Kinetic Smooth Scrolling",
            description = "Applies physics-based kinetic scrolling curves to web pages, reducing touch jitter on high-refresh-rate displays.",
            category = FlagCategory.PERFORMANCE,
            prefKey = "general.smoothScroll"
        ),
        PetalFlagDefinition(
            id = "enable-webrtc-leak-protection",
            title = "WebRTC Local & VPN IP Leak Protection",
            description = "Blocks WebRTC peer connections from discovering and exposing private local LAN and active VPN IP addresses.",
            category = FlagCategory.PRIVACY,
            prefKey = "media.peerconnection.ice.no_host"
        ),
        PetalFlagDefinition(
            id = "enable-canvas-letterboxing",
            title = "Tor-Style Canvas Letterboxing",
            description = "Quantizes web viewport dimensions into standardized 200x100px stepped margins, preventing websites from fingerprinting your exact device screen resolution.",
            category = FlagCategory.PRIVACY,
            prefKey = "privacy.resistFingerprinting.letterboxing"
        ),
        PetalFlagDefinition(
            id = "enable-resist-fingerprinting",
            title = "Firefox Strict Resist Fingerprinting (RFP)",
            description = "Enforces Tor & Mullvad-style uniform browser fingerprinting defenses: spoofs timezone to UTC, standardizes navigator properties, and masks system hardware details.",
            category = FlagCategory.PRIVACY,
            prefKey = "privacy.resistFingerprinting"
        ),
        PetalFlagDefinition(
            id = "enable-wasm-simd",
            title = "WebAssembly SIMD & JIT Acceleration",
            description = "Enables Single Instruction Multiple Data (SIMD) execution inside SpiderMonkey's WebAssembly engine for high-speed emulation and 3D web games.",
            category = FlagCategory.PERFORMANCE,
            prefKey = "javascript.options.wasm_simd"
        ),
        PetalFlagDefinition(
            id = "enable-fission-isolation",
            title = "Fission Full Site Process Isolation",
            description = "Runs cross-origin iframes and sub-frames in strictly isolated content processes to defend against Spectre-style side-channel memory leaks.",
            category = FlagCategory.PRIVACY,
            prefKey = "fission.autostart"
        ),
        PetalFlagDefinition(
            id = "enable-back-forward-cache",
            title = "Parent-Level Back-Forward Cache (bfcache)",
            description = "Keeps parsed DOM snapshots of visited pages in parent process memory for instant, zero-reload back and forward navigation transitions.",
            category = FlagCategory.PERFORMANCE,
            prefKey = "fission.bfcacheInParent"
        ),
        PetalFlagDefinition(
            id = "enable-lazy-image-loading",
            title = "Automatic Native Lazy Image Loading",
            description = "Defers loading off-screen images until scrolled near the viewport, saving cellular data and boosting first contentful paint speeds.",
            category = FlagCategory.PERFORMANCE,
            prefKey = "dom.image-lazy-loading.enabled"
        ),
        PetalFlagDefinition(
            id = "enable-zero-copy-textures",
            title = "Zero-Copy GPU Buffer Textures",
            description = "Bypasses CPU memory buffer duplication by binding Android HardwareBuffers directly to Gecko GPU textures.",
            category = FlagCategory.RENDERING,
            prefKey = "layers.acceleration.force-enabled"
        ),
        PetalFlagDefinition(
            id = "enable-cookie-banner-blocker",
            title = "Native Automated Cookie Banner Defuser",
            description = "Instructs Gecko's internal cookie engine to automatically detect and reject GDPR/cookie banners before they appear on screen.",
            category = FlagCategory.PRIVACY,
            prefKey = "cookiebanners.service.mode",
            type = FlagType.INT,
            enabledValue = "2",
            disabledValue = "0"
        ),
        PetalFlagDefinition(
            id = "enable-autoplay-blocker",
            title = "Strict Media Autoplay Silencer",
            description = "Completely blocks background audio and video autoplay on newly opened web pages until explicitly tapped.",
            category = FlagCategory.PERFORMANCE,
            prefKey = "media.autoplay.default",
            type = FlagType.INT,
            enabledValue = "5",
            disabledValue = "0"
        ),
        PetalFlagDefinition(
            id = "enable-font-fingerprint-defense",
            title = "Disable Remote Downloadable Web Fonts",
            description = "Disables external web font downloads, forcing system standard typography to eliminate font-enumeration fingerprinting.",
            category = FlagCategory.PRIVACY,
            prefKey = "gfx.downloadable_fonts.enabled",
            enabledValue = "false",
            disabledValue = "true"
        ),
        PetalFlagDefinition(
            id = "enable-network-cache-compression",
            title = "Gecko HTTP Disk Cache Compression",
            description = "Compresses on-disk cached web assets using LZ4, saving storage space and reducing wear on flash storage.",
            category = FlagCategory.NETWORK,
            prefKey = "browser.cache.compression_level",
            type = FlagType.INT,
            enabledValue = "1",
            disabledValue = "0"
        ),
        PetalFlagDefinition(
            id = "enable-dom-perf-memory",
            title = "Restrict DOM Performance Memory Timing",
            description = "Neutralizes high-resolution microsecond timer attacks in performance.now() to defeat JavaScript timing side-channels.",
            category = FlagCategory.PRIVACY,
            prefKey = "dom.enable_performance",
            enabledValue = "false",
            disabledValue = "true"
        ),
        PetalFlagDefinition(
            id = "enable-dark-mode-inversion",
            title = "Gecko Forced High-Contrast Web Darkening",
            description = "Forces the internal Gecko layout engine to automatically invert light backgrounds on sites that lack native dark themes.",
            category = FlagCategory.RENDERING,
            prefKey = "layout.force_dark"
        ),
        PetalFlagDefinition(
            id = "enable-parallel-downloads",
            title = "Petal Segmented Parallel Range Downloads",
            description = "Splits HTTP Range-supporting file downloads into multiple parallel connection segments via PetalParallelFetchClient.",
            category = FlagCategory.NETWORK,
            prefKey = "petal.download.parallel"
        ),
        PetalFlagDefinition(
            id = "enable-tab-sleeping",
            title = "Intelligent Background Tab Suspender",
            description = "Automatically suspends inactive background tabs after 15 minutes of inactivity to reclaim RAM.",
            category = FlagCategory.PERFORMANCE,
            prefKey = "petal.tabs.suspender"
        ),
        PetalFlagDefinition(
            id = "enable-scriptlet-defuser",
            title = "Petal Built-in Scriptlet Defuser Engine",
            description = "Injects native anti-adblock and tracker defusing scriptlets before DOMContentLoaded.",
            category = FlagCategory.EXTENSIONS,
            prefKey = "petal.scriptlet.defuser"
        ),
        PetalFlagDefinition(
            id = "ac-phase1",
            title = "Mozilla AC Phase 1 (AppLinks, WebNotifications, FindInPage, Thumbnails/Icons)",
            description = "Switches link intent dispatch, web notifications, find-in-page, and thumbnails/favicons to official Mozilla Android Components.",
            category = FlagCategory.SYSTEM,
            prefKey = "sp_ac_phase1"
        ),
        PetalFlagDefinition(
            id = "ac-phase2",
            title = "Mozilla AC Phase 2 (SitePermissions, Prompts, Search, Top Sites)",
            description = "Switches site permissions, web prompts, search engine management, and top sites to official Mozilla Android Components.",
            category = FlagCategory.SYSTEM,
            prefKey = "sp_ac_phase2"
        ),
        PetalFlagDefinition(
            id = "ac-phase3",
            title = "Mozilla AC Phase 3 (Places Storage, Logins, Session Restore)",
            description = "Switches browser history, bookmarks, password autocomplete vault, and tab session save/restore to official Mozilla Android Components.",
            category = FlagCategory.SYSTEM,
            prefKey = "sp_ac_phase3"
        ),
        PetalFlagDefinition(
            id = "ac-phase4",
            title = "Mozilla AC Phase 4 (Add-ons, PWA, Custom Tabs, Suspension)",
            description = "Switches WebExtensions management, progressive web apps, and custom tabs to official Mozilla Android Components.",
            category = FlagCategory.SYSTEM,
            prefKey = "sp_ac_phase4"
        )
    )

    fun findById(id: String): PetalFlagDefinition? = flags.firstOrNull { it.id == id }
    fun findByPrefKey(key: String): PetalFlagDefinition? = flags.firstOrNull { it.prefKey == key }
    fun isEnabled(context: android.content.Context, id: String): Boolean = PetalFlagsStore.isEnabled(context, id)
}
