# Petal Browser Project Phases & Delivery Status (phases.md)

This document reconstructs the realistic development phases of Petal Browser based strictly on code evidence, commit logs, and configuration state within the repository.

---

## Phase 1: Core Browsing Foundation & FOSS Browser Baseline
- **Status**: Complete ✅
- **Evidenced Features**:
  - Fork of FOSS Browser with baseline Android WebKit architecture.
  - Multi-tab navigation with `AlbumController` and `BrowserController` contracts.
  - SQLite persistence for bookmarks, browsing history, and favicons (`FaviconHelper.java`).
  - Privacy controls: local cookie management, incognito mode, basic ad/tracker domain blocklists (`BannerBlock.java`).
  - Standard download hooks and PDF webpage export support.

---

## Phase 2: Material 3 Expressive UI & Compose Modernization
- **Status**: Complete ✅
- **Evidenced Features**:
  - Full rewrite of secondary surfaces into Jetpack Compose:
    - Modular Settings Hub with tokenized multi-category search indexing (`PetalSettingsScreen.kt`).
    - Tab switcher grid with live snapshot thumbnails and incognito isolation.
    - Downloads management screen backed by Fetch2 engine (`PetalDownloadScreen.kt`).
    - History and Bookmarks management screens with batch deletion.
  - 35 Material 3 Expressive dynamic shapes catalog (`PetalMaterialShapes.kt`).
  - Living dynamic background with daylight response and procedural gradients.
  - Dynamic palette theming (Android 12+ wallpaper extraction, AMOLED pure black, and color styles).

---

## Phase 3: Mozilla GeckoView 154 Modern Standalone Engine
- **Status**: Complete ✅
- **Evidenced Features**:
  - Upgraded dependency to Mozilla GeckoView 154 (`org.mozilla.geckoview:geckoview:154.0.20260824154132`).
  - Process-wide singleton `PetalGeckoRuntime.kt` managing engine preferences, strict content blocking, and ETP.
  - Custom `PetalGeckoView.kt` tab controller implementing `AlbumController` and `NestedScrollingChild3`.
  - Rich Material 3 Expressive context menus for GeckoView web elements (links, images, videos, audio).
  - Integration with `BrowserWebViewController.kt`.

---

## Phase 4: Native Fullscreen Media Player & Dynamic Picture-in-Picture
- **Status**: Complete ✅
- **Evidenced Features**:
  - Native Compose video overlay controls (`PetalVideoPlayerOverlay.kt`) featuring:
    - Wavy animated squiggly seekbar inspired by mpvEx.
    - Double-tap seek gestures (10s rewind / forward) with pill animations and haptic ticks.
    - Vertical gestures for left brightness HUD and right volume HUD.
    - Speed selector (0.5x to 2.0x) and PiP action.
  - Bidirectional media synchronization bridge (`PetalMediaBridge.java` and `PetalVideoPlayerOverlayBridge.kt`).
  - Aspect-ratio aware Picture-in-Picture transitions with media control notification actions.
  - YouTube bypass rule to prevent UI collisions on native YouTube playback.

---

## Phase 5: Predictive Back Gestures & Edge Gesture Handling
- **Status**: In Progress / Active Diagnostic Phase ⚠️
- **Evidenced Features & Known Issues**:
  - Split predictive back architecture:
    - Java `BrowserActivity.java` via `OnBackPressedCallback`.
    - Compose `PetalPredictiveJunction.kt` via `PredictiveBackHandler` with real-time blur snapshots.
  - Edge gesture collision mitigation: Enforced empty system gesture exclusion rects on Android 10+ (API 29+).
  - **Active Diagnostic Note**: Lightweight diagnostic tracing (`PETAL_BACK_DEBUG`) active in `v2.7.4` to isolate intermittent edge-swipe dispatch anomalies between WebView/GeckoView touch boundaries and system back dispatcher.
  - *Status*: Core transitions working; edge-swipe touch arbitration under fine-tuning.

---

## Phase 6: Ecosystem Power Features & Security Bridges
- **Status**: Mixed Implementation / Partial Verification
  - **PWA Manager (`com.petal.browser.pwa`)**: Implemented ✅ (Manifest parsing, homescreen shortcut installation, and PWA display modes).
  - **Passkey & Biometric Lock (`com.petal.browser.passkey`, `com.petal.browser.security`)**: Implemented ✅ (Biometric prompt integration for private tabs and app lock).
  - **Fetch2 Resumable Downloads (`com.petal.browser.download`)**: Implemented ✅ (Background downloading with pause/resume and notification progress).
  - **Google Account & Sync (`com.petal.browser.account`)**: Implemented with Fallbacks ⚠️ (Credential Manager + Legacy Play Services Auth to handle upstream Google error `[16]`).
  - **Native Torrent Engine (`com.petal.browser.torrent`)**: Basic scaffolding present; *status unclear from code, needs developer confirmation on active UI integration*.
  - **Petal AI Hub (`com.petal.browser.lens`, `PetalAiResearchBridge`)**: Contextual text actions and research bridge implemented; full multimodal agent capabilities *status unclear from code, needs developer confirmation*.
