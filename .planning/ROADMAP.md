# Roadmap: Petal Browser GeckoView Stabilization

## Milestone 1: Core GeckoView Stabilization & Parity
- [x] **Phase 1: Verification of Done Items**
  - [x] Audit R8 / ProGuard rules for reflection survival
  - [x] Audit codebase for unkept reflection targets
  - [x] Complete fingerprint coverage in `PetalGeckoRuntime.kt`
- [x] **Phase 2: Cold-Restart History Preservation**
  - [x] Fix `rebuildTabsFromBrowserStore` and `showAlbum` so `loadUrl` doesn't overwrite restored `EngineSessionState`
  - [x] Implement gated Mozilla session restore (`sp_mozilla_session_restore`)
  - [x] Connect `goBack` to `BrowserStore.canGoBack` to prevent Home drops
- [x] **Phase 3: Website Rendering & Theming**
  - [x] Enforce theme precedence contract & extension suppression on dark-native pages
  - [x] Fix white flash during tab launch and tab switching
  - [x] Support web `theme-color` meta tinting on status bar
  - [x] Check surface modes and eliminate main-thread per-frame allocations
- [x] **Phase 4: Runtime & Settings Parity with Fenix**
  - [x] Generate Gecko settings comparison matrix vs Fenix
  - [x] Migrate desktop site preference keys to `sp_desktop_site`
  - [x] Unify session settings builder across popups and PWAs
- [x] **Phase 5: Error Handling & Dead Code Cleanup**
  - [x] Triage silent catch blocks across engine and browser controllers
  - [x] Attach error handlers to naked `GeckoResult` promises
  - [x] Clean unused WebView parameters
  - [x] Migrate main-thread `SharedPreferences.commit()` to asynchronous `apply()`
- [x] **Phase 6: Mozilla Components Migration & Packaging**
  - [x] Evaluate candidate Mozilla features and sequence replacement roadmap
  - [x] Document Findings Table, Decisions Needed, and Verification Scripts

## Milestone 2: Deep Mozilla Upstream Parity & Component Integration
- [x] **Phase 7: PWA, Custom Tabs & WebAuthn Cross-Activity Parity**
  - [x] Eliminate PWA tab session leak in `PetalPwaActivity.onDestroy()`
  - [x] Support incognito custom tabs via `isIncognitoCustomTab` intent parsing
  - [x] Verify WebAuthn / Passkey `ActivityDelegate` across all activity hosts
- [x] **Phase 8: Reader Mode Parity (Mozilla Readability Integration)**
  - [x] Integrate Mozilla Readability metadata parsing (OpenGraph, Twitter, Schema.org)
  - [x] Add DOM clone sanitization and noise tag pruning
  - [x] Structure readable headings, paragraphs, blockquotes, and lists
  - [x] Gate with `sp_mozilla_readability` (default `true`) while preserving Compose UI
- [x] **Phase 9: Ad Blocking Synergy & uBlock Origin Recommended Flow**
  - [x] Retain built-in Trie blocker as high-speed default baseline
  - [x] Add 1-tap uBlock Origin recommendation card in Privacy Shield sheet
  - [x] Add uBlock Origin active indicator and dashboard popup launcher
- [x] **Phase 10: Find In Page Headless Integration**
  - [x] Gate Mozilla `EngineSession` find abstraction behind `sp_mozilla_find_in_page`
  - [x] Strictly preserve `BrowserActivity.java` address bar and bottom-nav padding calculations
- [x] **Phase 11: Background Media Playback & Controls**
  - [x] Confirm `PetalMediaSessionService` `MediaSessionCompat` controls & actions
  - [x] Gate foreground session startup behind `sp_mozilla_media_session` (default `true`)
  - [x] Maintain tab media state synchronization with `BrowserStore`
- [x] **Phase 12: W3C Web Notification Actions & Native Crash Log Hook**
  - [x] Add reflection-safe `WebNotification.actions` parsing and `NotificationCompat.Action` buttons
  - [x] Handle action button clicks via `ACTION_BUTTON_CLICK`
  - [x] Hook Gecko native crashes/kills into `PetalAppLogger` for local diagnostics & GitHub issues

## Milestone 3: Official Firefox WebExtensions Parity
- [x] **Phase 13: WebExtension Action Reactive State & Live Toolbar Badges**
  - [x] Define `ExtensionActionState` with reactive badges, titles, icons, and colors
  - [x] Expose `activeTabActionStates` StateFlow in `PetalExtensionManager`
  - [x] Maintain tab-session action synchronization on tab switch and reload
- [x] **Phase 14: Dedicated In-Browser Extensions Menu Sheet**
  - [x] Create `PetalExtensionsMenuSheet` with Liquid Glass containment and M3 styling
  - [x] Render active tab extensions with live action badges (e.g. uBlock blocked counts)
  - [x] Wire 1-tap browser action popup triggers and direct options page shortcuts
  - [x] Connect browser overflow menu "Extensions" item directly to `PetalExtensionsMenuSheet`
- [x] **Phase 15: Replace Faux-Extensions & Overhaul Add-ons Manager**
  - [x] Purge legacy Petal faux-extension lists and custom dialogs from `PetalExtensionsScreen`
  - [x] Structure pure 3-tab Firefox manager: Installed, Explore (Curated Catalog), and Tweaks
  - [x] Keep silent engine background extensions running without polluting UI
- [x] **Phase 16: Extension Details, Settings & Permissions Inspector**
  - [x] Build comprehensive permissions inspector mapping host patterns and WebExtension APIs
  - [x] Expose dedicated "Run in Private Windows" toggle backed by `WebExtensionController`
  - [x] Add direct "Settings" launcher opening extension options pages in real browser tabs
  - [x] Add safe uninstall flow with confirmation dialog
- [x] **Phase 17: Firefox Power Tweaks — Local XPI Loader, Custom Collections & Dev Mode**
  - [x] Add system file picker import for local `.xpi` packages via `installFromContentUri`
  - [x] Support Custom AMO Add-on Collections via Mozilla AMO v4 API (Fenix feature)
  - [x] Implement WebExtension Developer Mode with formatted `manifest.json` inspector
- [x] **Phase 18: Extension Popup Host & Focus Polish**
  - [x] Verify mobile-responsive viewport CSS injection bypassing content CSP
  - [x] Support full prompt delegate suite (alert, button, text, share) for password managers
  - [x] Ensure reliable focus cleanup and parent tab reactivation on dismiss

## Milestone 4: App-Wide Architectural Parity & Anti-Pattern Remediation
- [x] **Phase 19: Security & Credential Protection (P0)**
  - [x] Delete insecure `petal_password_autofill` WebExtension (prevent plaintext credentials leaking via DOM `petal:login_submitted` events)
  - [x] Rely exclusively on GeckoView's native C++ `Autocomplete.StorageDelegate` via `PetalAutofillGeckoDelegate` and Android System Autofill Framework
  - [x] Remove `petal_password_autofill` registration from `PetalBuiltInExtensionManager`
- [x] **Phase 20: Memory & Zombie Runtime Cleanup (P1)**
  - [x] Purge `android.webkit.*` (`WebStorage`, `WebView`) and `androidx.webkit.*` (`ProfileStore`, `WebViewFeature`) from `PetalCacheManager.kt`
  - [x] Unify storage and browsing data clearing exclusively under GeckoView's asynchronous `StorageController.clearData()`
  - [x] Save 30–50MB native RAM per process by preventing Chromium `libmonochrome.so` background initialization
- [x] **Phase 21: Native Dark Mode & Content Script De-escalation (P1)**
  - [x] Migrate dark theme completely to GeckoView native engine settings (`preferredColorScheme` and `forceUserSpecifiedColors`)
  - [x] De-escalate and remove `petal_dark_webpages` content script to eliminate double-inversion white flashes when running Dark Reader or visiting dark-native pages
- [x] **Phase 22: Search, Copy & Web Compatibility Fixes (P2)**
  - [x] Fix `GeckoSession.Finder.clear()` highlight leak in `PetalTabViewController.clearFindMatches()`
  - [x] De-escalate `petal_universal_copy` from global capture-phase injection to an on-demand contextual action to protect Google Docs, Google Sheets, and web code editors
  - [x] De-escalate `petal_clean_link` from click-time `href` mutation to GeckoView's native Enhanced Tracking Protection query stripping (`privacy.query_stripping.enabled`)
- [x] **Phase 23: Media Sniffing & Downloader Network Architecture (P2)**
  - [x] Remove out-of-band Java `HttpURLConnection` HEAD probes in `PetalNativeSnifferEngine.kt` that bypass Gecko's network stack and trigger anti-bot CAPTCHAs
  - [x] Wire PWA discovery directly to GeckoView's native `ContentDelegate.onWebAppManifest` instead of DOM scraping in `media_grabber`

## Milestone 5: Zen Browser-Style Tab Organization
- [x] **Phase 24: Workspaces Data Architecture & Metadata Persistence**
  - [x] Define `PetalWorkspace` data class and presets (General, Personal, Work, Research)
  - [x] Implement `PetalWorkspaceManager.kt` with thread-safe JSON persistence and active workspace StateFlow
  - [x] Update `PetalTabMetadataStore.kt` with `workspaceId`, `isPinned`, `isPinnedGlobal`, and default workspace fallback
- [x] **Phase 25: Zen Workspace Carousel & Management Sheet**
  - [x] Build `PetalWorkspaceCarousel.kt` with segmented pills, vector icons, and Liquid Glass container
  - [x] Build `PetalWorkspaceSheet.kt` for creating, editing, recoloring, and deleting workspaces
  - [x] Connect active workspace switching with haptic feedback and spring transitions
- [x] **Phase 26: Zen Essentials / Pinned Tabs Shelf**
  - [x] Implement `PetalPinnedTabsShelf.kt` with compact icon tiles and active indicators
  - [x] Support Global vs Workspace-specific pinned tabs
  - [x] Add 1-tap switching and long-press pin/unpin toggles
- [x] **Phase 27: Collapsible Tab Folders within Workspaces**
  - [x] Upgrade `PetalTabGroupManager.kt` with `workspaceId` and `isCollapsed` state
  - [x] Render collapsible accordion headers in `PetalTabGridSwitcher.kt` (`PetalTabFolderHeader.kt`)
  - [x] Add folder batch actions and smooth spring expand/collapse
- [x] **Phase 28: Zen Tab Glance / Peek Sheet**
  - [x] Build `PetalTabGlanceSheet.kt` with live preview, metadata header, and 8-action grid
  - [x] Wire split-view launcher, workspace transfer, group assignment, mute audio, and duplicate tab actions
- [x] **Phase 29: End-to-End Integration, Anti-Slop Audit & CI Verification**
  - [x] Connect workspace filtering in `PetalTabSwitcherSheet.kt` and `PetalTabGridSwitcher.kt`
  - [x] Verify persistence across cold restarts
  - [x] Verify GitHub Actions remote CI build passes (Build `42479669304` green)


