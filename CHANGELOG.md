### v4.7.0 — 🚀 Unified Single-Engine Architecture & Fenix Stack Consolidation

> *"A monumental engine modernization release consolidating Petal Browser onto a single, high-performance tab and engine architecture powered by Mozilla Android Components and GeckoView 159. Completely eliminates GPU texture leaks and render-thread stall ANRs by adopting the single-viewport Fenix model, enforces safe delegate ownership across Activity recreations, and fully retires legacy PetalGeckoView while 100% preserving Petal's bespoke Liquid Glass design, pull-to-refresh mechanics, and WebExtension capabilities."*

#### 🏗️ Single Engine View Architecture (Fenix Model)
- Consolidated open tab management onto a single active `PetalTabViewController` mounted in `contentFrame`.
- Demoted background tabs to lightweight `PlaceholderAlbumController` instances with state retained in `BrowserStore`, avoiding multi-SurfaceView texture bloat and GPU memory exhaustion.
- Dynamic tab switching seamlessly rebinds the selected engine session using `singleTabViewController.render(session)`.

#### 🛡️ Robust Delegate Ownership & Activity Recreation Protection
- Implemented `PetalOwnedDelegate` contract on `PetalPromptDelegateWrapper`, `PetalPermissionDelegateWrapper`, and `PetalContentDelegateWrapper`.
- Dynamic `rebind()` pattern updates the volatile Activity provider across configuration changes without re-instantiating delegates on live Gecko sessions.
- Added graceful fallback forwarding to prevent missed background prompts.

#### ⚡ Store-Driven Reactive State & APZ Touch Arbitration
- Bound `BrowserActivity` state observation directly to `PetalEngineStore.observeManually()`, driving loading progress, omnibox, and back stack reactively from `BrowserStore`.
- Integrated Async Pan Zoom (APZ) `PanZoomController` overscroll detection (`canOverscrollTop()`) to accurately differentiate document-level pull-to-refresh from nested scrollable elements.

#### 🧹 Legacy Removal & Engine Bridge Centralization
- Extracted all static engine helpers, download routing, intent handling, profile lookups, and user-agent generation into `PetalEngineBridge`.
- Completely removed legacy `PetalGeckoView.kt` and `BrowserWebViewController.kt`.
- Cleaned all legacy `activeGeckoView` static references and `instanceof PetalGeckoView` branches across `BrowserActivity`.

### v4.6.0 — ✨ More Expressive & Tactile Layout Controls

> *"The landmark 'More Expressive' release elevates Petal Browser to new heights of Material 3 Expressive design, fluid reactive theming, and tactile user customization: introducing a comprehensive ImageToolbox-style Layout & Controls suite with live component styling, seamless Liquid Glass auto-adaptation, PixelMusic-grade expressive fast-scrollbars, overhauled Mix Palettes with one-tap harmony recipes, bi-directional Send Tab to Device across all connected Firefox instances, and a robust Reader Mode with Read Aloud text-to-speech."*

#### 🎁 Google AdMob Rewarded Ads Integration (`PetalAdMobRewardedAds.kt`, `PetalFuelTheCodeSheet.kt`, `PetalApplication.kt`, `build.gradle`, `AndroidManifest.xml`)
- **Migrated from Unity Ads to Google Mobile Ads SDK**:
  - Replaced legacy `com.unity3d.ads:unity-ads` dependency with official `com.google.android.gms:play-services-ads:23.6.0`.
  - Implemented `PetalAdMobRewardedAds.kt` conforming strictly to `PetalRewardedAds` interface with safe MainThread dispatch, watchdog timeouts, and friendly error translation.
  - Added official Google Test Rewarded Ad Unit ID (`ca-app-pub-3940256099942544/5224354917`) on debug builds to safeguard against AdMob policy violations during development.
  - Configured user's production AdMob App ID (`ca-app-pub-5680314597533936~2825634534`) and Rewarded Ad Unit ID (`ca-app-pub-5680314597533936/6148934278`) for release builds.
  - Updated ad network integrity checks in `PetalFuelTheCodeSheet.kt` to target Google DoubleClick endpoints, providing clear DNS ad-blocker detection.

#### 🌌 App-Level Quantum Web Launch Animation Architecture (`PetalSplashOverlay.kt`, `BrowserActivity.java`, `PetalHomeScreen.kt`, `AppearanceSettingsScreen.kt`)
- **Universal Cold-Start Coverage**:
  - Re-architected the "Quantum Web Launch Animation" (`sp_app_launch_animation`) from a local `PetalHomeScreen.kt` composition into a universal app-level overlay in `BrowserActivity` via `PetalSplashOverlay.kt`.
  - The animation now plays on cold start across all launch surfaces — whether the app starts on Petal Home, rehydrates existing restored website tabs, or launches directly from external web links (`ACTION_VIEW`).
  - Mounted before tab restoration and intent dispatch with full-screen `translationZ = 2000f` and `elevation = 2000f`, rendering smoothly over GeckoView `SurfaceView` and `TextureView` without delaying or blocking background page rehydration.
- **Surface & Dialog Coordination**:
  - Maintained `PetalSplashState.splashActive` gating across the browser: keeps address bar, bottom navigation, pull-to-refresh, bar collapse animations, and startup dialogs (`PetalSetupBridge`, `PetalCrashReportingBridge`, and What's New update dialogs) completely hidden and deferred until the splash animation cleanly dissolves.
  - Deferral of `LiquidRippleEffect` ensures the liquid launch wave radiates only after the Quantum Web splash finishes rather than colliding with the entrance animation.
  - Integrated with biometric and PIN app lock (`sp_app_lock_enabled`): securely unlocks first, then smoothly plays the launch animation into the browser.
- **Activity Recreation & Gesture Integrity**:
  - Preserved `PetalSplashState.hasShownThisSession` so orientation changes, theme toggles, and returning from recents never replay the animation.
  - Attached non-blocking back dispatcher interceptor and full-screen touch sink to consume back gestures and accidental taps during playback without risk of getting stuck on mid-flight recreation.
  - Added 3.5s safety timeout fallback ensuring the overlay view is always cleanly dismissed and disposed under any circumstance.
- **Live Preview in Appearance Settings**:
  - Added interactive "Preview Animation" action button and full-screen preview dialog inside Appearance Settings (`AppearanceSettingsScreen.kt`), allowing users to test and experience the Quantum Web icon stream on demand.
  - Removed duplicate copy from `PetalHomeScreen.kt`, preventing double-playback or conflicting lifecycle state.

#### 🚀 Web Page Scroll Stabilization, Dynamic Toolbar Decoupling & Touch Integrity (`BrowserActivity.java`, `PetalTabViewController.kt`, `PullToRefreshFrameLayout.java`, `GeckoGlassBackend.kt`, `PetalGeckoView.kt`)
- **Firefox/Fenix Dynamic Toolbar Decoupling & Fixed Viewport Layout**:
  - Eliminated viewport reflows and layout shifting during scroll by stopping `animateContentPadding` during active scrolls; `main_content` maintains a stable layout size.
  - Aligned with Mozilla Fenix architecture: GeckoView owns the dynamic toolbar offset via `setDynamicToolbarMaxHeight` and smoothed `setVerticalClipping` clamped to actual bar dimensions.
  - Refactored spring translations to smoothly redirect running springs via `setFinalPosition` rather than cancelling and restarting, using critically damped spring forces (`DAMPING_RATIO_NO_BOUNCY`) to eliminate bounce oscillations.
  - Included `pillSpringAnim` and `pillMorphAnimator` in `areBarsAnimating()` / `isBarAnimationRunning()`, preventing collapse animations from colliding with pill morphing.
- **High-Stability Frame-Coalesced Scroll Direction Detector**:
  - Replaced unthrottled per-event UI runnable dispatch with atomic frame coalescing onto the main thread.
  - Implemented viewport layout cooldown (350 ms) to ignore synthetic APZ scroll deltas caused by keyboard/overlay appearance or orientation changes.
  - Added bar state change cooldown (350 ms), hysteresis thresholds (56dp collapse / 24dp restore), and sustained directional verification (minimum 2 consecutive steps in same direction) to eliminate rapid oscillation flickers during flings.
- **SurfaceView Default & Live Liquid Glass Backend Management**:
  - Restored high-performance hardware-composited `BACKEND_SURFACE_VIEW` as default for all tabs on Android 12+, eliminating GPU overhead and compositor latency when Liquid Glass is disabled.
  - TextureView backend is applied dynamically only when Liquid Glass is enabled and actively sampling webpage content behind translucent bars.
  - Fixed `FrameQuietListener` to properly forward `onSurfaceTextureUpdated`, resolving undrawn grey strips and compositor stutters.
  - Added live settings listener to dynamically transition GeckoView backends when Liquid Glass settings are toggled in real time.
- **Hardened Pull-To-Refresh Touch Stream Protection & Fast-Flick Response**:
  - Replaced unreliable `view.canScrollVertically(-1)` with real APZ compositor scroll offset (`currentScrollY <= 2`) tracked from `GeckoSession.ScrollDelegate`.
  - Enforced strict `startedAtTop` validation exclusively evaluated at `ACTION_DOWN`; touches starting mid-page can never hijack touch streams into pull-to-refresh.
  - Guarded touch interception: requires clear downward travel (`dy > touchSlop && dy > Math.abs(dx) * 1.5f`), immediately surrendering touch control to web content during horizontal swipes, carousels, text selection, pinch-to-zoom, and upward scrolls.
  - Resolved indicator clipping and visual lag on fast pull flicks in `ContainedLoadingIndicator.kt`: indicator remains warm in composition, with offset, scale, and alpha calculated directly inside `graphicsLayer` during touch drags without recomposition latency.
- **Codebase Cleanup**:
  - Removed dead `BrowserMomentumRecoveryRules.kt`.

#### 🦊 Firefox/Fenix Mozilla Android Components Session Architecture Migration (`BrowserStore`, `AutoSave`, `SessionStorage`)
- **BrowserStore as Single Source of Truth**:
  - Replaced legacy custom tab session persistence and restore systems (`PetalTabSessionManager` and `PetalTabStateStore`) with the standard Firefox/Fenix architecture powered by Mozilla Android Components 159.
  - Eliminated temporary patches (restoreFinished save gate, suspendSaving, PREF_SESSION_JSON_BACKUP fallback, releaseEngineTabs, and createTabSession stale ID collision removals).
- **Process-Level AutoSave Gating**:
  - Bound Mozilla `AutoSave` directly to the application lifecycle with `whenGoingToBackground()` and `whenSessionsChange()`, ensuring all atomic persistence to `mozilla_components_session_storage_gecko.json` is handled automatically without manual save triggers.
- **EngineSession Linking & Back/Forward Navigation Integrity**:
  - Linked all active and restored engine sessions to `BrowserStore` via `EngineAction.LinkEngineSessionAction` so `LinkingMiddleware` actively tracks and synchronizes `EngineSessionState`, preserving full back/forward history stacks, scroll offsets, and form state across restarts.
  - Suspended background tabs preserve full engine state via `EngineAction.SuspendEngineSessionAction` and rehydrate lazily with `materializeTabSession()`.
- **Sidecar Metadata Store & One-Time Legacy Migration**:
  - Introduced `PetalTabMetadataStore` sidecar to preserve tab group IDs, titles, colors, and custom titles keyed by tab ID, automatically pruned to existing tabs.
  - Built-in one-time migration seamlessly converts legacy JSON, SQLite session rows, and `petal_tab_state` files into Mozilla SessionStorage, followed by a clean wipe of legacy data.

#### 🎨 Official Material 3 Expressive Progress Bar & Merged Stack Relocation (`PetalProgressBarBridge.kt`, `PetalMergedBar.kt`, `AddressPill.kt`, `BrowserActivity.java`)
- **Official Material 3 Expressive Design**:
  - Completely redesigned website loading linear progress bar to match official Google Material 3 Expressive specifications with 4dp height, rounded capsule active bar, dynamic 4dp gap, rounded track, and a 4dp circular stop indicator dot at the terminus.
- **Relocated in Merged Address Bar Stack Group**:
  - Relocated the progress bar in bottom stacked merged bar mode from the detached full-screen line down into the merged card directly between the `AddressPill` and the action buttons row, perfectly matching the pill's width and shape.
- **Dynamic Layout & Size Matching**:
  - Synchronized progress bar positioning and horizontal margins across all navigation modes (Top Docked, Top Floating, Bottom Docked, Bottom Floating, Merged Single Row, Merged Stacked) so the progress bar seamlessly tracks the address bar width rather than stretching disconnected across the screen.

#### 🛡️ Zero Duplicate Key Safety in Bottom Sheet Lazy Layouts (`PetalMediaPickerBottomSheet.kt`, `PetalLensBottomSheet.kt`, `PetalImageGrabberSheet.kt`, `PetalInactiveTabsSheet.kt`)
- **Resolved Fatal `IllegalArgumentException: Key was already used`**:
  - Eliminated duplicate key crashes across Bottom Sheet lazy layouts when querying Android `MediaStore` (where images and videos or different storage volumes share identical numeric `_ID` primary keys like `10974`).
  - Replaced ambiguous numeric IDs with unique qualified URI keys (`${item.uri}_$index`) and added automatic `.distinctBy { it.uri.toString() }` deduplication in `PetalMediaPickerBottomSheet` and `PetalLensBottomSheet`.
  - Hardened `PetalImageGrabberSheet` with URL deduplication and index disambiguation (`"${url}_$index"`) to prevent collisions on webpages with duplicate image URLs.
  - Hardened all bottom sheet and dialog lazy lists (`PetalInactiveTabsSheet`, `PetalTabSwitcherSheet`, `PetalRecentlyClosedVault`, `SafeLockerSheet`, `PetalQueueBottomSheet`, `PetalDownloadManagerScreen`, `PetalTabGridSwitcher`) with index disambiguation and list deduplication.

#### 💎 Live Liquid Glass in Merge Address Bar & UI Build Fixes (`PetalMergedBar.kt`, `AddressPill.kt`, `PetalFlagsHubScreen.kt`)
- **Live Liquid Glass in Merge Bar**:
  - Bound Merge Bar to live hardware backdrop refraction and specular chrome with support for both `sp_liquid_glass_address_bar` and `sp_liquid_glass_bottom_nav`.
  - Added full floating pill support when bottom merged bar is paired with `sp_floating_tab_bar`, allowing webpage content to flow seamlessly underneath the floating glass bar without creating black boxes.
  - Enhanced `AddressPill` with clear translucent glass fill and beveled top specular edge gradient replacing flat opaque gray washes.
  - Upgraded `BarActionButton` with tactile bouncy spring scaling, haptic click feedback, and glass touch-target styling.
- **CI Build Fix**:
  - Fixed missing `CompositionLocalProvider` import in `PetalFlagsHubScreen.kt` resolving GitHub Actions compilation failure.


#### 🎛️ ImageToolbox-Style Expressive Layout & Controls (`PetalLayoutSettingsSection.kt`, `PetalSwitchType.kt`, `PetalSliderType.kt`, `PetalShapeType.kt`, `PetalFlingType.kt`)
- **Seamless Live Layout Settings**:
  - Added dedicated **"Expressive Layout & Controls"** section to Appearance Settings matching ImageToolbox with 100% real-time reactivity (no app restart required).
  - **Switch Type**: Modal sheet selector supporting **Compose**, **Material You**, **Pixel**, **Fluent**, **Cupertino**, **HyperOS**, and **OneUI** toggle styles.
  - **Slider Type**: Modal sheet selector featuring **Fancy** (scalloped cookie thumb rolling inside track), **Material You** (pill thumb with dynamic fill), **Material** (rail circle thumb), and **HyperOS** (capsule thumb).
  - **Shape Type**: Modal sheet offering geometric corner variations: **Rounded**, **Cut**, **Squircle**, **Smooth**, **Wavy**, **Scoop**, and **Notch**.
  - **Corners Size**: Live slider (`0.0x` to `2.0x`) with live numeric badge scaling UI surface corner radiuses.
  - **Corners Animation Throttle**: Interactive debounce slider (`0` to `500 ms`) with millisecond badge for tactile touch response calibration.
  - **Fling Type**: Configurable scroll decay physics (**Android Native**, **Smooth**, **iOS Style**, **Snappy**, **Floaty**, and **Reduced Motion**).
  - **Motion Duration Scale**: Live transition speed multiplier slider (`0.25x` to `3.0x`) with discrete tick stops.
- **Liquid Glass Auto-Adaptation**:
  - Removed manual "Liquid Glass" toggle option from the picker; switches and sliders now automatically adapt to translucent frosted glass refraction, sheen, and glowing indicators whenever the Liquid Glass theme is enabled.
- **Media Settings Sliders Upgrade (`MediaSettingsScreen.kt`)**:
  - Replaced all legacy AndroidX `Slider` composables (Horizontal Fine-Tuning, Vertical Margin Down, and Notch Flare Curvature) with `PetalSlider`, bringing full Liquid Glass chrome and tactile rolling cookie thumb physics to media island settings.

#### 📜 PixelMusic-Grade Expressive Vertical Fast-Scrollbar (`PetalExpressiveScrollBar.kt`, `PetalScrollBarMetrics.kt`)
- **Fluid Morphing Fast-Scrollbar**:
  - Integrated a vertical scroll indicator with morphing kinetic thumb, track guidance line, and touch drag scaling ported directly from PixelMusic.
  - **Dynamic Fast-Scroll Alphabet Badge**: Floating expressive badge showing item initials, dates, and categories during high-speed drag scrubbing.
  - **Haptic Detents**: Gentle tactile tick feedback when scrubbing past section boundaries and initial changes.
  - **Integrated App-Wide**: Deployed across Bookmarks, History, Downloads, File Pickers, Tab Switcher, and Store subpages.

#### 🎨 Mix Palettes Overhaul & Instant Live Reactivity (`PetalPaletteMix.kt`, `PetalPaletteMixUi.kt`)
- **Resolved Live Toggle Reactivity**:
  - Eliminated the "Silent Auto" bug where turning on Mix Palettes with default settings appeared non-functional. Toggling Mix Palettes ON now automatically pairs harmonious vibrant defaults (`defaultVibrantSources()`), instantly and visibly updating the theme live.
  - Bound the toggle switch to immediate Compose `mutableStateOf` with synchronous `PetalThemePrefs.bump()` for zero-latency, real-time theme updates across the entire application.
- **Curated One-Tap Harmony Recipes**:
  - Added quick harmony recipe chips: **Sunset Glow** (Peach & Amber), **Ocean Forest** (Forest & Mint), **Cyberpunk** (Cyan & Pink), **Cherry Blossom** (Sakura & Sky), and **Autumn Amber** (Amber & Wine).
- **Interactive Component Sandbox (`PetalMixComponentPreview`)**:
  - Integrated a live component preview strip inside Appearance Settings demonstrating how Primary Action Buttons, Secondary Container Chips, and Tertiary Highlight Pills look together before leaving settings.
- **Redesigned Role Cards & Source Dialog**:
  - Expandable Spring animation (`expandVertically` + `fadeIn`) revealing Secondary Accent and Tertiary Highlight cards with live rendered color swatches and an all-inclusive source picker.

#### 📲 Send Tab to Device via Mozilla Sync (`PetalSendToDeviceSheet.kt`, `PetalMozillaSyncManager.kt`)
- **Bi-Directional Cloud Commands**:
  - Dispatches `open-uri` command payloads directly into Mozilla Sync's encrypted `commands` collection.
  - Background polling during sync cycles detects incoming tabs from desktop, laptop, and mobile Firefox instances, surfacing high-priority notifications with instant "Open Tab" actions.
- **Paired Hardware Device Picker**:
  - Material 3 Expressive bottom sheet displaying connected devices (Mac, Windows, Linux, Android, iOS) with hardware icons and active tab counts, plus a *"Send to All Devices"* broadcast option.

#### 📖 Reader Mode Overhaul & Read Aloud TTS Fixes (`PetalReaderBridge.kt`, `PetalReaderScreen.kt`)
- **Robust In-Page Readability Extraction**:
  - Fixed JavaScript evaluation syntax errors in `evaluateJavascript` across `PetalGeckoView.kt` and `PetalTabViewController.kt`.
  - Injected an inline self-contained article extractor that filters noisy elements (`<script>`, `<style>`, `<nav>`, `<aside>`) and extracts clean paragraph streams, bylines, and lead imagery, with reliable timeouts and fallbacks to native Firefox `about:reader`.
- **Text-to-Speech (TTS) Floating Player Integration**:
  - Anchored `PetalFloatingTtsPlayerBar` dynamically at the bottom of the reader screen with sentence-by-sentence reading, speed modulation (0.75x–2.0x), skip $\pm 10\text{s}$, and pause/resume.

---

#### 🌟 The "More Expressive" Milestone Summary (v4.5.1 → v4.6.0)
The **v4.6.0** milestone consolidates extensive architectural and UX advancements across the v4.5.x lineage:
- **🏝️ Dynamic Cutout Island & Android 16 Live Actions (v4.5.1, v4.5.6)**: Hardware-calibrated camera hole-punch alignment, dynamic notch flare, Live Updates for file downloads/media, and zero-configuration AMOLED phosphor burn-in shield.
- **💎 Pure Liquid Glass & Morph Physics (v4.5.2, v4.5.3, v4.5.4)**: Real-time translucent wallpaper refraction across minimized address bars, APK installers, contextual sheets, and profile lock authentication.
- **🎵 PixelPlayer-Grade Media Architecture (v4.5.5, v4.5.7, v4.5.8)**: Scallop cookie artwork physics, synchronized millisecond karaoke lyrics (`[mm:ss.xxx]`) with drag hold-off, interactive queue with animated 3-bar equalizer waves, pitch-preserving speed selector, sleep timer, universal PiP mini-players, and uninterrupted background media services.
- **⚡ Enterprise CI/CD Caching & Build Engine (v4.5.9)**: `setup-gradle@v4` build-cache persistence, Android intermediate cache caching, and 6GB parallel worker pipeline.

---

### v4.5.9 — ⚡ CI/CD Build Resolution, High-Speed Gradle & GitHub Cache Infrastructure

> *"Milestone v4.5.9 resolves all Android build and compilation blockers across Media Settings, Image/PDF Viewers, and Audio Color Palettes, and introduces enterprise-grade GitHub Actions caching with `setup-gradle@v4` and Android build-cache persistence to drastically cut build durations."*

#### 🛠️ Compilation Fixes & File Viewer Stabilizations
- **Media Settings Fixes (`MediaSettingsScreen.kt`)**:
  - Corrected import for `LiveUpdateNotificationManager` (`com.petal.browser.compose.downloads` package).
  - Resolved `PetalGroupRow` parameter usage across alignment modes, animation springs, spring velocity, and bounce rows by supplying standard `icon`, `title`, and `trailing` lambdas.
- **Image & PDF Viewer Fixes (`PetalImageViewerScreen.kt`, `PetalPdfViewerScreen.kt`)**:
  - Added missing `androidx.compose.runtime.DisposableEffect` import to `PetalImageViewerScreen.kt`.
  - Fixed property reference from `currentEntry.title` to `currentEntry.label`.
  - Corrected PDF file-viewer notification parameter to use `displayName`.
- **Audio Palette Backwards Compatibility (`PetalAudioColorEngine.kt`)**:
  - Added accessor `val seed: Color get() = seedColor` to `ExtractedAudioPalette` ensuring seamless compatibility across Compose themes, audio players, and bottom sheets.
- **Appearance Settings Streamlining (`AppearanceSettingsScreen.kt`)**:
  - Removed "Mix palettes" toggle option along with its expandable preview strip and source configuration menus for a simplified, focused theme customization workflow.

#### 🚀 Build Acceleration & GitHub Actions Caching Architecture (`android_build.yml`)
- **Dedicated Gradle Build Cache (`gradle/actions/setup-gradle@v4`)**:
  - Integrated `gradle/actions/setup-gradle@v4` to cache the Gradle wrapper distribution, resolving repeated 150MB downloads per runner.
  - Enabled GitHub Actions persistent remote cache for Gradle build-cache (`build-cache-1`), skipping unchanged tasks and dependencies.
- **Android Intermediate Cache Persistence**:
  - Integrated `actions/cache@v4` for `~/.android/build-cache` and `~/.android/cache` across workflow runs.
- **JVM Heap & Parallel Worker Tuning**:
  - Increased CI heap allocation to 6GB (`-Xmx6144m -XX:MaxMetaspaceSize=1536m`) with parallel worker allocation (`-Dorg.gradle.workers.max=4`) and parallel reference processing (`-XX:+ParallelRefProcEnabled`).

---

### v4.5.8 — 🎵 PixelPlayer Lyrics Engine, Interactive Queue & Advanced Media Controls

> *"Milestone v4.5.8 equips Petal's audio and video media suite with a high-fidelity PixelPlayer experience: synchronized karaoke lyrics with manual-drag hold-off & tap-to-seek, an interactive queue manager with animated equalizer waves, an advanced sleep timer with end-of-track fadeout, detailed track audio specs inspector, fine-grained playback speed selector, and direct audio route switching."*

#### 🎤 Synchronized Karaoke Lyrics Engine (`PetalLyricsEngine.kt`, `PetalLyricsSheet.kt`)
- **Millisecond LRC & ID3 Scanner**:
  - Implemented high-precision timestamp parser supporting both `[mm:ss.xx]` and `[mm:ss.xxx]` formats alongside standard LRC metadata tags (`[ti:]`, `[ar:]`, `[al:]`).
  - Added automatic detection order: scans companion local `.lrc` files (`pathWithoutExt + ".lrc"`), followed by deep scanning of embedded ID3 `USLT` frames within MP3/FLAC/AAC streams.
- **PixelPlayer Karaoke Sheet UI**:
  - Modal bottom sheet displaying synchronized lyrics with smooth auto-scroll to the current singing line.
  - Added smart 3.5s drag-holdoff: allows manual lyrics scrolling without sudden auto-scroll snaps.
  - Interactive tap-to-seek: tapping any lyric line instantly seeks the ExoPlayer engine to that exact millisecond.
  - Embedded bottom mini-player strip directly inside the lyrics sheet with Play/Pause and track title for uninterrupted control.
  - Integrated `.lrc` file picker allowing users to import external lyrics files on-demand.

#### 🎼 PixelPlayer Queue Manager & Bottom Sheet (`PetalQueueManager.kt`, `PetalQueueBottomSheet.kt`)
- **Interactive Play Queue**:
  - Manages active playlists, reordering, adding tracks via Android SAF, and swiping to remove individual tracks.
  - Smart Next/Previous navigation: when multiple items are in the queue, skips between songs; when a single track is playing, seamlessly shifts to 10-second forward/backward skips.
- **Animated 3-Bar Equalizer Wave (`PlayingEqualizerBars`)**:
  - PixelPlayer-style animated 3-bar green equalizer next to the currently playing song in the queue list.
  - Real-time indicator showing active playback state, queue position count, and quick clear queue options.

#### ⚙️ More Options Sub-Sheets Suite (`PetalMoreOptionsSheets.kt`)
- **Sleep Timer (`PetalSleepTimerSheet`)**:
  - Quick chips for 15, 30, 45, 60 minutes, or custom duration with live countdown timer.
  - Includes *Play Until End of Track* toggle to cleanly pause playback and shut down foreground media services once the current song ends.
- **Song Info & Audio Inspector (`PetalSongInfoSheet`)**:
  - Detailed metadata sheet showing Bitrate, Sample Rate, Audio Channel Configuration, Codec type, File Size, and full content/file path with one-tap clipboard copy.
- **Playback Speed Selector (`PetalSpeedSelectorSheet`)**:
  - Fine-grained pitch-preserving playback speed selector ranging from 0.5x to 2.0x with intuitive quick-tap chips and fine slider.
- **Audio Output Route Switcher (`PetalAudioRouteSheet`)**:
  - Inspects connected audio output routes (`AudioManager.getDevices`) including Built-in Speaker, Wired Headphones, Bluetooth A2DP, and USB Audio, with direct launcher for system Bluetooth settings.
- **Equalizer Launcher**:
  - Wired system audio effects intent (`AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`) with fallback notification when no system equalizer is installed.

#### 🎥 Video Player Verification & Control Locking
- Verified video player HUD gestures, 2x long-press speed boost, lock screen slider (`PetalSlideToUnlock`), aspect ratio toggles, and PiP coordination.

---

### v4.5.7 — 🎬 Unified Background Audio Engine, Picture-in-Picture (PiP) for Audio & Video, and Web Player Bridge Fixes

> *"Milestone v4.5.7 brings seamless, uninterrupted background playback and universal Picture-in-Picture (PiP) support across all Petal media players — including standalone audio/music, standalone video, and in-page/fullscreen website HTML5 media."*

#### 🎧 Standalone Music Background Playback & PiP Mini-Player
- **Foreground Media Session Service Integration**:
  - Bound `PetalAudioPlayerScreen` to `PetalMediaSessionService` foreground service with high-resolution artwork, lock screen controls, and system notification transport controls (`MediaStyle`).
  - Added `AUDIO_BECOMING_NOISY` broadcast receiver to automatically pause audio when headphones or Bluetooth disconnect.
  - Ensured ExoPlayer instances remain alive during background execution, lockscreen, or tab switching when `sp_background_play` is enabled.
- **Dedicated Picture-in-Picture (PiP) Mini-Player for Music**:
  - Added PiP action button in `PetalPlayerHeaderTopBar`.
  - Built a custom compact 1:1 square PiP player view featuring ambient artwork gradients, continuous scalloped cookie artwork, and marquee track metadata.
  - Wired remote PiP transport actions (Play/Pause toggle, Skip -10s, Skip +10s) directly to the playback pipeline.

#### 🎥 Video Player PiP & Android 12+ Gesture Auto-Enter
- **Unified PiP Controller (`PetalPipController.kt`)**:
  - Implemented boundary-clamped aspect ratios (`[0.418f..2.39f]`), source rect letterbox/pillarbox calculation, and Android 12+ (API 31+) gesture Auto-Enter (`setAutoEnterEnabled(true)`).
  - Cleaned PiP window transition by hiding all overlay controls, seekbars, and top navigation when in PiP mode.
  - Synchronized remote action taps inside the PiP window to seek and play/pause ExoPlayer dynamically.

#### 🌐 Web Player Media Bridge & Background Stability
- **Uninterrupted Web Playback (`PetalGeckoRuntime.kt`, `PetalGeckoView.kt`)**:
  - Enabled `media.suspend-bkgnd-video.enabled = false` in Gecko profile preferences to eliminate artificial background suspensions on YouTube, Vimeo, and HTML5 players.
  - Injected `MEDIA_JS_INJECTION` hooks on page load to intercept document visibility transitions and keep media playback active when minimized.
  - Connected `PetalGeckoView`'s media bridge automatically so media state and PiP parameters synchronize with `BrowserActivity` on all tabs.

#### ⚙️ Media Settings & Search Indexing
- **Media Engine Controls (`MediaSettingsScreen.kt`)**:
  - Added toggle for *Background Media Playback* (`sp_background_play`) and *Picture-in-Picture (Auto-PiP)* (`sp_auto_pip`).
  - Indexed both settings in `PetalSettingsSearchIndex.kt` under `SettingsCategory.MEDIA`.

---

### v4.5.6 — 🏝️ Expressive Cutout Island Overhaul, AMOLED Burn-In Shield & Unified Services

> *"Milestone v4.5.6 elevates Petal Browser's dynamic cutout island with hardware-calibrated placement, adaptive Light/Dark & OLED theming, automatic zero-configuration AMOLED burn-in protection, full native service integration across Petal Media & File Viewers, and relocation of Island settings to Media Settings."*

#### 🏝️ Expressive Cutout Island & Placement Calibration
- **Physical Camera Cutout Alignment (`CutoutMetrics.kt`)**:
  - Dynamically calculates physical hardware camera offset from center in dp (`cutoutHorizontalOffsetDp`), matching left-punch, center-hole, or right-punch displays accurately.
  - Added placement mode customization (`ALIGN_AUTO`, `CENTER`, `LEFT`, `RIGHT`) with fine-tuning offsets ($X \pm 120\text{dp}$, $Y +0..40\text{dp}$).
  - Introduced notch flare mode (`NotchShape`) for notch displays with seamless status bar integration and configurable flare curve width.

#### 🛡️ Built-in Zero-Configuration AMOLED Burn-In Shield (`PetalBurnProtection.kt`)
- **Automatic Display Detection & Pixel Orbiter**:
  - Automatically identifies OLED/AMOLED hardware panels using display HDR capabilities and system properties without requiring any user toggles.
  - Implements a continuous 90-second cyclical micro-translation drift ($\pm 1\text{px}$ to $\pm 2\text{px}$) preventing static phosphors from wearing unevenly.
  - Automatically clamps peak white luminance on pure OLED displays (`#000000` deep black backdrop with calibrated contrast safety).

#### 📁 Seamless Petal Services & Live Actions Integration
- **Full File Viewer & Media Player Live Updates**:
  - Integrated `PetalAudioPlayerScreen` with live playback status, artwork streaming, and cookie shape icon updates.
  - Integrated `PetalVideoPlayerScreen` with video playback state, resolution badge, and direct resume actions.
  - Added unified `LiveActionItem.FileViewer` across `PetalPdfViewerScreen` (page count / title), `PetalImageViewerScreen` (gallery index / resolution), and `PetalFileViewerScreen` (code / text document viewer).
  - Tapping on the active island expands or returns seamlessly to the corresponding viewer or player.

#### ⚙️ Settings Migration & Interactive Preview Experience
- **Media Settings Suite (`MediaSettingsScreen.kt`)**:
  - Migrated Cutout Island settings from `DownloadSettingsScreen` to `MediaSettingsScreen`, placing it logically alongside Petal Media & Live Updates.
  - Integrated rich interactive preview card (`IslandAnimationPreviewCard`), real-time alignment and offset sliders, and spring motion velocity chips.
  - Indexed cutout island and live updates in `PetalSettingsSearchIndex` under `SettingsCategory.MEDIA`.

---

### v4.5.5 — 🎵 PixelPlayer-Grade Media Architecture, Cookie Shape Album Art & Adaptive Theming

> *"Milestone v4.5.5 brings a major overhaul to Petal Browser's standalone media player, incorporating PixelPlayer's state-of-the-art playback architecture, dynamic cookie-shaped album artwork, seamless M3 Expressive tonal elevation with zero artificial button borders, and intelligent artwork color engine integration."*

#### 🎵 PixelPlayer-Grade Playback & Seekbar Architecture
- **State-of-the-Art Progress Sampling & Scrub Lock**:
  - Re-architected `PetalAudioPlayerScreen` progress handling using PixelPlayer's exact smooth-progress pattern: decoupled polling loop from Compose recomposition ticks.
  - Introduced `sliderDragValue` locking so user scrubbing never fights background polling updates.
  - Implemented `targetSeekFraction` hold-until-catchup with timeout and 4% delta tolerance, completely eliminating seekbar snap-back.
  - Added vertical drag gesture isolation (`detectVerticalDragGestures`) preventing parent containers from intercepting seekbar scrubs.
  - Synchronized real-time time labels via `derivedStateOf` reads directly connected to the smooth fraction provider.

#### 🍪 Dynamic Scalloped Cookie Album Art
- **PixelPlayer Scalloped Cookie Silhouette (`PetalCookieShape.kt`)**:
  - Implemented continuous 8-sided scalloped cookie shape with configurable curvature depth and rotation.
  - Dynamically morphs into the cookie shape during active music playback, and softly relaxes to an expressive squircle (`RoundedCornerShape(32.dp)`) with interactive pause-squish physics when paused.

#### 🎨 Adaptive Dynamic Artwork Color Engine (`PetalAudioColorEngine.kt`)
- **Vibrant Seed Color Extraction & Harmonious Theming**:
  - Built an in-memory LRU-cached color engine that extracts vibrant seed colors from album art without blocking the main UI thread.
  - Dynamically renders subtle ambient vertical gradients, high-contrast M3 container surfaces (`surfaceContainerHighest`, `surfaceContainerHigh`), and themed controls based on track artwork palette.

#### ✨ Border Removal & Authentic Material 3 Surface Hierarchy
- **Elimination of Artificial Borders**:
  - Removed all artificial `1.dp` outlines from playback controls (previous, next), top bar navigation pills (collapse, route, queue), quick actions (lyrics, more options), and compartment rows in `PetalPlayerComponents.kt`.
  - Replaced legacy outlines with authentic Material 3 Expressive tonal elevation, contrast-aware container fills, and fluid spring weights.

---

### v4.5.4 — 🎨 Material 3 Expressive UI & UX Containment, Profile Lock Overhaul & Unified Iconography

> *"Milestone v4.5.4 brings a comprehensive Material 3 Expressive upgrade across Petal Browser: authentic M3 Expressive shape scales and tonal container ladders, an overhaul of the Profile & App Lock security flow with biometric and profile identity badges, 100% unified Rounded iconography with zero colorology drift, position-aware containment capsules, native Firefox flags harvest, and Liquid Glass Custom Tabs."*

#### 🎨 Material 3 Expressive Theme Engine & Color Scheme
- **Official Material 3 Expressive Shapes Scale**:
  - Supplied explicit `Shapes` (`extraSmall = 8.dp`, `small = 12.dp`, `medium = 16.dp`, `large = 24.dp`, `extraLarge = 32.dp`) to `MaterialExpressiveTheme` in `PetalTheme.kt`, giving cards, sheets, buttons, and badges their signature modern curvature.
- **Tonal Elevation Container Hierarchy**:
  - Completely resolved the container flattening bug in `expressiveColors`, restoring distinct surface steps (`surfaceContainerLowest`, `Low`, `Container`, `High`, `Highest`) across Light, Dark, and AMOLED modes.
  - Preserved pure `#000000` AMOLED backdrops while guaranteeing high-contrast subtle container elevation and outline borders on OLED panels.

#### 🔒 Profile & App Lock Security Redesign
- **Dynamic Preference & Auth Routing**:
  - Eliminated the hardcoded `"1234"` fallback passcode bug; now strictly checks actual saved credentials and respects `sp_app_lock_type` (`"FINGERPRINT"` vs `"PASSWORD"`).
  - Automatically launches biometric prompt on launch when configured for fingerprint, and switches smoothly to password PIN pad when requested.
- **Profile Identity Badge**:
  - Added an active profile identity chip badge (`"${activeProfile.name} Profile"`) with avatar accenting above the lock screen header, ensuring users immediately recognize which profile is being secured.
- **Liquid Glass Avatar Surface & Pill Physics**:
  - Upgraded unlock avatar surface with `isLiquidGlassEnabled("sp_liquid_glass_containments")`, `liquidGlassChrome`, and spring scaling on `Bun.toShape()`.
  - Refined biometric action button to standard M3 Expressive pill contour (`RoundedCornerShape(50)`).
- **Redesigned Passcode Modal**:
  - Re-engineered the passcode creation and change dialog using `PetalDialog` with clean vertical spacing, title, hint, and button row.

#### ✨ 100% Unified `Icons.Rounded.*` Iconography (Strict Colorology Preservation)
- **Zero Legacy Iconography**:
  - Migrated every single Compose ImageVector icon from `Icons.Filled.*`, `Icons.Default.*`, `Icons.Outlined.*`, and `Icons.AutoMirrored.Filled.*` to `Icons.Rounded.*` and `Icons.AutoMirrored.Rounded.*`.
  - Upgraded across all layers: Core containment (`PetalContainment.kt`, `PetalTransientSurfaces.kt`, `ExpressiveHeader.kt`), Navigation & Sheets (`PetalOptionsSheet.kt`, `PetalInactiveTabsSheet.kt`, `PetalBookmarksScreen.kt`, `PetalHistoryScreen.kt`, `PetalExtensionsScreen.kt`, `PetalOmniboxPage.kt`), File System (`PetalFilePickerScreen.kt`, `PetalFileViewerScreen.kt`), Media Players (`PetalAspectRatioSheet.kt`, `PetalAudioPlayerScreen.kt`, `PetalCastManager.kt`, `PetalPlayerComponents.kt`, `PetalVideoPlayerOverlay.kt`), Settings & Permissions (`PetalFeaturedCarousel.kt`, `SettingsHubScreen.kt`, `DataBackupSettingsScreen.kt`, `InactiveSettingsScreen.kt`, `PetalDeleteScreen.kt`, `PetalBrowserPermissionDialog.kt`, `PetalWelcomeScreen.kt`, `PetalImageViewerScreen.kt`, `PetalPdfViewerScreen.kt`).
- **Strict Colorology Preservation**:
  - 100% preserved all `primary`, `secondary`, `tertiary`, `containerColor`, and `tint` attributes across all badges, chips, rows, and cards, fulfilling strict visual identity guidelines.

#### 📦 Position-Aware Containment & M3 Hero Cards
- **Position-Aware Sliders (`PetalVariableSlider`)**:
  - Enhanced `PetalVariableSlider` with `position: PetalGroupPosition` shape awareness, AMOLED outline border, and `liquidGlassChrome` sheen.
- **Hero Card Encapsulation**:
  - Enclosed loose settings columns in `AppearanceSettingsScreen.kt` (Shape Change Mode and Motion Blur slider) inside structured `PetalHeroCard` capsules.
  - Enclosed nested zoom and live font preview surfaces in `DisplaySettingsScreen.kt` with M3 Expressive container tokens and outline borders.

#### 🦊 Native Firefox Flags Hub & Wiring Harvest
- **Native Firefox Flags Hub**:
  - Built direct about:config and gecko flags inspection bridge, allowing granular engine tuning directly from Petal Settings.
- **Privacy Hardening**:
  - Hardened tracking protection, isolated storage partitions, and refined Gecko runtime parameters for battery and memory efficiency.
- **Liquid Glass Custom Tabs**:
  - Redesigned custom tab overlay with fluid morphing, bottom toolbar matching, and Liquid Glass chrome.

#### 📑 Complete Bookmarks Feature Restoration & Overhaul
- **Resolved Empty State Rendering Bug**:
  - Fixed condition nesting bug in `PetalBookmarksScreen.kt` where `LazyColumn` was unintentionally placed inside the `isEmpty()` branch, restoring the bookmark list when saved items exist.
- **Bookmark Actions & Options Sheet**:
  - Added Material 3 Expressive action bottom sheet to each bookmark row providing "Open in New Tab", "Edit Bookmark", "Toggle Reading List", "Copy Link", and "Share Link".
- **Bookmark Editing & Reading List Toggling**:
  - Implemented dedicated Edit Bookmark dialog allowing in-place title and URL modifications, alongside instant toggling between standard bookmarks and Reading List.
- **Reliable Star / Bookmark Toggle**:
  - Re-architected bookmark saving and toggling across `BrowserActivity` and overflow menus (`toggleBookmark()`), cleanly toggling saved/removed status and eliminating false "Error: Failed to save" toasts on existing entries.
- **Netscape HTML & JSON Bookmarks Import/Export**:
  - Built native `PetalBookmarkHtmlExporter` and file picker integration in the bookmarks overflow menu for bidirectional Netscape HTML bookmarks export and import with Chrome, Firefox, and Safari.
- **Omnibox Bookmark Search**:
  - Added `SuggestionSource.BOOKMARK` to `PetalOmniboxPage.kt`, allowing live matching of saved bookmarks directly from address bar input.

---

### v4.5.3 — 💎 Live Liquid Glass in Minimized Address Bar & Expressive Morph Animations

> *"Release v4.5.3 brings live Liquid Glass refraction to the minimized address bar pill, spring minimization physics, dynamic find-in-page slotting, and dedicated Google Play AAB bundle support with native in-app updates."*

#### 💎 Live Liquid Glass in Minimized Address Bar & Expressive Morph Animations
- **True Live Liquid Glass in Minimized Pill**:
  - Eliminated opaque container masking (`surfaceContainerHighest.copy(alpha = 0.88f)`) and flat tonal elevation tints in `PetalMinimizedPill`, making the compact pill completely transparent to reveal the authentic, live blurred webpage content scrolling underneath.
  - Allocated dedicated, pre-primed `GlassBar` render targets for the minimized pill in both `PetalAddressBar` and `PetalMergedBar`, guaranteeing instant 120fps refraction rendering without runtime shader recompilation or teardown stalls.
  - Broadened liquid glass preference evaluation in `StrideGlassModifiers` to support `sp_liquid_glass_address_bar` alongside bottom navigation and containment toggles.
- **Material 3 Expressive Spring Minimization Motion**:
  - Replaced linear ValueAnimators with physical `SpringAnimation` (`stiffness = 420f`, `dampingRatio = 0.82f` / Low Bouncy) via `FloatValueHolder` for toolbar minimization and expansion.
  - Engineered fluid spatial morphing between the full address bar and the floating compact pill, replacing abrupt cutoffs with smooth scale, alpha, and translation dynamics.
  - Added directional expand indicator (`KeyboardArrowUp` for bottom address bar, `KeyboardArrowDown` for top bar) and improved domain text clarity with subtle drop shadows over refractive glass.

#### 🔍 Find in Page Bar Alignment & Refractive Liquid Glass
- **Dynamic Slot Matching**: Find-in-page bar now dynamically slots into the address bar's position (top or bottom above navigation bar / keyboard) and sets the address bar to transparent while active to prevent overlapping.
- **Live Liquid Glass Support**: Adapted `PetalFindInPageBar` to sample live webpage content with `Modifier.liquidGlass`, matching the address bar's exact shape, insets, and curvature.
- **Position-Aware Transitions**: Slide-in and slide-out animations now naturally slide from the top or bottom depending on toolbar placement.

#### 📦 Distribution Variants (AAB vs. Standalone APKs)
- **Dedicated Google Play Bundle Variant (`play`)**:
  - Automatically strips sensitive permissions (`API_V23` Shizuku, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) from the Google Play `.aab` manifest via dedicated `app/src/play/AndroidManifest.xml`.
  - Preserves `REQUEST_INSTALL_PACKAGES` across both builds to power Petal's built-in APK installer.
  - Suppressed the background battery optimization dialog in the AAB build to comply with Play Store policies, falling back directly to standard settings without restricted popups.
- **Google Play In-App Updates Engine**:
  - Automatically routes update checks in the Play Store build through `PetalPlayUpdateManager` (`com.google.android.play:app-update`), providing official in-app flexible and immediate update flows.
  - Updates channel tagline to display `Channel: Google Play` and hides manual external APK download progress.
- **Standalone GitHub Variant (`github`)**:
  - Retains full elevated Shizuku capabilities, one-tap battery optimization background protection dialog, and direct GitHub Release APK updates.

---

### v4.5.2 — 💎 Live Liquid Glass APK Installer & Shizuku On-Start Elevation

> *"Release v4.5.2 fixes APK installer layout stacking and overlap bugs, restores full live Liquid Glass refraction with transparent modal containment and background blur reflection, and automatically requests Shizuku authorization on start whenever Shizuku is installed."*

#### 💎 Live Liquid Glass Installer & Layout Fixes
- **Layout Stacking & Box Overlap Resolution**: Fixed root container layout inside `AnimatedContent` for `PetalApkInstallSheet`, enclosing every step (`Ready`, `Inspect`, `Installing`, `Done`, `Failed`) in dedicated `Column(modifier = Modifier.fillMaxWidth())` to eliminate accidental (0, 0) Box overlay collisions between the app header, insights card, and action buttons.
- **True Live Liquid Glass Containment**: Enabled true refractive Liquid Glass for `PetalSheet` by defaulting to dynamic glass containment colors, active wallpaper backdrop capture, and nested `liquidGlassChrome` surfaces across cards, chips, and warnings.
- **Glass Backdrop Fallback**: Added safe window fallback in `PetalSheetGlassBackdrop` so standalone and transparent activities like `PetalApkInstallActivity` seamlessly refract underlying browser snapshots or procedural frosted patterns without black clipping.

#### ⚡ Shizuku Automatic Elevation on Start
- **On-Start Permission Discovery**: Added `checkAndRequestShizuku()` on startup in both `PetalApkInstallActivity` and `BrowserActivity` to check if Shizuku is installed on the device and prompt for authorization immediately.
- **Sticky Binder Integration**: Listens for Shizuku binder arrival with `addBinderReceivedListenerSticky` and refreshes elevated fast install routes automatically.

---

### v4.5.1 — 🏝️ Android 16 Live Actions & Expressive Dynamic Cutout Island

> *"Milestone v4.5.1 delivers native support for Android 16 (API 36 Baklava) Live Actions and Rich Ongoing Notifications, introducing real-time status bar live activity chips. For non-supported devices (Android 10–15 and OEM ROMs), Petal integrates a full Material 3 Expressive Dynamic Cutout Island adapted from expressive-cutout with spatial spring physics, interactive motion previews, and dedicated settings."*

#### ⚡ Android 16 Live Actions (API 36 Rich Ongoing Notifications)
- **Status Bar Live Action Chips (`setShortCriticalText`)**: Promotes active downloads and background web media sessions directly into the Android 16 status bar chip capsule adjacent to the camera cutout and clock, displaying live velocity and progress percentages.
- **ProgressStyle with Themed Segments**: Leverages Android 16's `Notification.ProgressStyle`, tinted dynamically with Material You dynamic colors or Petal signature palette, complete with styled progress tracker icons.
- **Promoted Ongoing Support**: Automatically flags running downloads and media sessions with `FLAG_PROMOTED_ONGOING` and verifies system-level live alert permissions via `NotificationManager.canPostPromotedNotifications()`.
- **Standalone Expressive Cutout Interoperability**: Bundles standard `android.progress`, `android.progressMax`, `android.progressIndeterminate`, and `android.liveAlertText` extras, allowing the standalone `expressive-cutout` companion app to mirror Petal transfers system-wide with zero extra configuration.

#### 🏝️ Material 3 Expressive Dynamic Cutout Island (Adapted from `expressive-cutout`)
- **Seamless Non-Supported Fallback**: For devices on Android 10–15 or custom OEM distributions without status bar chip support, Petal renders a fluid Material 3 Expressive cutout island directly anchored around the hardware punch hole.
- **Compact & Split Satellite Modes**:
  - *Download Pill*: Displays animated progress arrow, completion percentage, live download velocity chip, and a breathing activity dot.
  - *Media Pill*: Shows track title, musical badge, and real-time jumping audio soundwave equalizer bars.
  - *Split Satellite Bubble*: When both media and a download are active simultaneously, splits into a primary capsule and a circular secondary satellite bubble.
- **Expanded Expressive Card**: Tapping the island springs open an expanded Material 3 card featuring rich metadata, interactive Pause/Resume and Cancel actions, ETA and transfer counters, and direct access to the downloads manager.
- **Hardware Cutout & Bezel Geometry (`CutoutMetrics` & `NotchShape`)**: Queries `DisplayCutout`, `cutoutPath`, and display corner radii to accurately anchor around centered or offset punch-holes, with flared concave ear geometry hugging top bezels.

#### 🎛️ Reference App Motion Physics & Animations (`IslandMotion`)
- **Tactile Tap "Boop"**: Authentic spring-driven scale compression to 0.95f under touch and bouncy recovery on release.
- **Spring Stiffness & Damping Controls**: Support for `SLOW` (170f), `DEFAULT` (380f), and `FAST` (800f) spatial springs with customizable damping (`SMALL`, `NORMAL`, and `BIG` overshoot).
- **Liquid Travelling Wavy Progress Bar**: Sinusoidal wavy progress bar with continuous phase shifts and smooth amplitude morphing.

#### ⚙️ Dedicated Download Manager Settings
- **Live Actions & Cutout Island Settings**: Added a new settings group under **Settings → Downloads → Live Actions & Cutout Island**.
- **Interactive Motion Preview**: A live interactive preview card allowing users to tap and test the cutout island's spring boop, expansion animation, and wavy progress in real time.
- **Mode & Motion Selectors**: Instant switching between *Auto*, *Always Cutout Island*, and *Notification Bar Only*, with granular controls over spring speed, damping bounce, and wavy vs. solid progress bars.

---

### v4.5 — 🦊 Unified Mozilla Firefox Download Pipeline, Architectural Cleanup & Pure Liquid Glass

> *"Milestone v4.5 unifies all download pipelines under Mozilla's official Firefox/Fenix engine with Range segmenting, eliminates the Android system DownloadManager, purges legacy third-party brand misnomers (Chrome/Google/Chromium) across the codebase, integrates Torrays torrent search, and elevates Pure & Live Liquid Glass with interactive refraction controls."*

#### ⬇️ Unified Mozilla Firefox Download Pipeline
- **Single Download Gateway (`PetalDownloadRouter`)**: Replaced fragmented legacy download paths with a unified gateway that validates URLs, verifies runtime storage permissions, respects external download manager preferences, and routes directly into Mozilla's download infrastructure.
- **Official Firefox/Fenix Engine (`AbstractFetchDownloadService`)**: All downloads (browser links, media sniffer captures, images, videos, audio, web-seeds, and torrents) now run exclusively on Mozilla's official download pipeline (Android Components `feature-downloads` 158.0 over `GeckoViewFetchClient`).
- **Context-Aware Session Downloads**: Context menu downloads ("Save Image", "Download Video", "Save Audio", "Download Link") pass authenticated session cookies, `Referer`, `User-Agent`, and incognito privacy flags (`isPrivate`) from the active tab, preventing hotlink blocks or failed authenticated downloads.
- **Incognito Download Isolation**: Private browsing downloads are marked private and omitted from persistent history, matching Firefox desktop and mobile privacy guarantees.
- **Elimination of `android.app.DownloadManager`**: Completely removed obsolete legacy WebView download listeners, leaked notification receivers in `BrowserActivity`, and broken system manager queries. Replaced integer status constants with standalone `DownloadStatus` constants.
- **AndroidX FileProvider File Resolution**: Opening and sharing downloaded files now directly resolves files from the public Downloads directory via AndroidX `FileProvider`, eliminating failures caused by querying Android's system `DownloadManager`.
- **External Download Manager Routing**: Full compatibility retained for external download managers (1DM, ADM, AB DM, Navi) when configured in Download Settings.

#### 🧹 Codebase & Architecture Normalization
- **Petal User Profile Modernization**: Renamed `ChromeAccountSyncScreen.kt` ➔ `PetalUserProfileScreen.kt` and `GoogleAccountManager.kt` ➔ `PetalUserProfileManager.kt` (with data model `GoogleUserProfile` ➔ `PetalUserProfile`), removing misleading third-party brand names from Petal's offline FOSS user personalization and avatar preset store.
- **Chromium Artifact Purge**: Removed dead stub `ChromiumNativeEngineCore.java` and obsolete `.github/workflows/chromium_core.yml` from the GeckoView codebase.
- **Java Syntax & Convention Fix**: Renamed `List_standard.java` ➔ `StandardDomainWhitelist.java` to adhere to standard UpperCamelCase naming conventions and Petal architecture.
- **UI Screen & Component Suffix Alignment**:
  - `PetalOptionsDialog.kt` ➔ `PetalOptionsSheet.kt` (ModalBottomSheet)
  - `PetalWelcomeDialog.kt` ➔ `PetalWelcomeScreen.kt` (Full-screen onboarding experience)
  - `PetalFilePicker.kt` ➔ `PetalFilePickerScreen.kt` (Full-screen file browser)
  - `PetalBiometricExpressiveOverlay.kt` ➔ `PetalBiometricExpressiveSheet.kt` (Modal authentication sheet)

#### 🔍 Torrays Torrent Search & Extension Integration
- **Torrays Integration**: Integrated [Torrays](https://torrays.vercel.app/) as an open-source, privacy-respecting torrent search index.
- **Omnibox & Search Engine**: Available as a default or quick search engine option in the Omnibox.
- **Extension Controls & Credits**: Added a toggle switch in Petal Extensions to toggle Torrays in search engines on or off, accompanied by a dedicated information dialog with links to the official site and repository.
- **Automated Web Search Injection**: Added built-in WebExtension and in-page execution to ensure instant search trigger upon opening Torrays.

#### 🫧 Pure & Live Liquid Glass UI
- **Refraction Controls**: Added *Refraction Strength* and *Refraction Width* sliders (0–200%, default 100%) under **Settings → Appearance → Liquid Glass UI**, allowing granular control over rim lens distortion and edge refraction width.
- **Crisp Layering**: Single sharp wallpaper layer combined with a calibrated 7dp frost blur and per-pane edge-refraction lens for maximum text clarity.
- **Live Backdrops**: Modal bottom sheets, popup menus, and the QR scanner record dynamic backdrops every frame to refract underlying page content, animations, or live camera feeds.

#### 🎞️ Predictive Back Gestures & Animation Styles
- **Predictive Back Styles**: Added 5 customizable gesture animation styles under Display settings: **Petal**, **AOSP**, **MIUIX**, **Scale**, and **Classic**, complete with a looping mini-phone live preview.
- **Real-Time Depth Blur**: Background screens smoothly blur and dim as back gestures progress and clear cleanly on gesture completion.

#### ⚡ Performance & Resource Optimizations
- **Faster Cold Start**: Subscription lists, yt-dlp, and ffmpeg initialize asynchronously off the main thread.
- **Low-Overhead Ad-Block Domain Lookup**: Replaced linear rule scans with parent-domain set lookups.
- **Smoother Downloads UI**: Downloads list renders updates only when transfer state changes, avoiding unnecessary recompositions.


---

### v4.4 — 🌸 Material 3 Expressive 3-Dot Menus, Feature Explainers & Smooth Performance

> *"Major UI & UX milestone introducing Material 3 Expressive 3-dot menus with bouncy rotation physics, animated live feature explainer dialogs, Android 14 predictive back with blur across viewers, full Petal Autofill redesign, Torrays torrent index integration, and zero-allocation anti-jank rendering."*

#### 🔍 Torrays Torrent Search & Extension Integration
- **Torrays Integration**: Integrated [Torrays](https://torrays.vercel.app/) as an open-source, ad-free torrent search index.
- **Search Engine & Omnibox**: Added Torrays as a quick search provider in Omnibox and default search engines with dedicated icon branding.
- **Extensions Controls & Credits**: Added a toggle switch in Petal Extensions to dynamically enable/disable Torrays across search engines, plus an interactive information and credits dialog linking to the official site and repository.

#### 🎨 Material 3 Expressive 3-Dot Menus & Bouncy Motion
- **Unified Expressive Menus (`PetalPopupMenu`)**: Redesigned all secondary and item action menus app-wide (Bookmarks, Downloads, Extensions, File Picker, PDF Viewer, Tab Switcher, and Custom Tabs) with:
  - 28dp rounded container shapes and tonal surface container elevation.
  - Interactive squircle icon badges, bold typography, and destructive action tints.
  - Tactile bouncy physics: Spring-driven 180° rotation on the 3-dot trigger button (`Spring.DampingRatioMediumBouncy`) plus scale and fade entrance on menu popup surfaces.

#### 💡 Interactive Material 3 Expressive Feature Explainers
- **Info Icon System (`PetalFeatureInfoButton`)**: Embedded tactile info icon buttons beside complex feature sections in Settings.
- **Animated Explainer Dialogs (`PetalFeatureExplainerDialog`)**: Interactive modal dialogs with live animated micro-previews:
  - *Apple Duo (BETA)*: Live 3D dual-pane foldable preview with real-time perspective tilt and frosted glass crease blur.
  - *Address Bar Position*: Top vs. bottom floating island one-handed reachability demonstration.
  - *Ad & Tracker Shield*: Sub-millisecond Trie algorithm firewall visualization routing clean web traffic while intercepting tracking packets.
  - *Morphing Shapes & Scroll Motion Blur*: Live continuous ambient polygon morphing and velocity-based directional blur demo.

#### 🔙 Android 14 Predictive Back Navigation
- **Predictive Back with Live Blur**: Upgraded all full-screen viewers (PDF viewer, Image viewer, File viewer, and Recently Closed Vault) to adhere strictly to Android 14 predictive back guidelines with real-time scaling and frosted background blur snapshots.

#### 🔐 Petal Autofill Expressive Redesign
- **Expressive Credential Overhaul**: Full Material 3 Expressive UI and UX redesign of password, credit card, and identity autofill prompts with fluid animations, biometrics, and living color harmony.

#### ⚡ Performance & Anti-Jank Optimizations
- **Zero-Allocation Canvas Rendering**: Pre-allocated and reused scratch matrices inside `M3ExpressiveVariableBackground`, eliminating hundreds of short-lived heap allocations per second and eradicating GC pause stutters.
- **Compose Compiler Stability**: Added `@Immutable` stability annotations across core data models (`TabModel`, `DownloadItem`, `PetalShortcut`, `HistoryListItem`), allowing Compose to skip recomposition during high-speed fling scrolling.
- **Recomposition Pruning**: Wrapped dynamic search queries and filter pipelines in `derivedStateOf`, and added explicit `contentType` tags across lazy lists to prevent measure thrashing.
- **Calibrated Spring Curves**: Standardized `PetalSpring.dialog()` and `PetalSpring.sheet()` curves for crisp, snappy settles without sluggish settle times.

---

### v4.3 — ⚡ Latest GeckoView 157 Engine & Expressive Connected Pickers

> *"Major engine upgrade to Mozilla GeckoView & Android Components 157.0.1, coupled with a full redesign of pick-one selectors to Material 3 Expressive connected button groups."*

#### 🌐 Engine & Core Upgrades
- **Mozilla GeckoView 157.0.1**: Upgraded quantum browser engine to `157.0.20261005135250` with enhanced web standards compliance, security enhancements, and optimized multi-process rendering.
- **Mozilla Android Components 157.0.1**: Bumped all components (`browser-engine-gecko`, `concept-engine`, `feature-readerview`, `feature-session`, `feature-customtabs`, `feature-pwa`, `feature-media`, etc.) to stable 157.0.1.

#### 🎨 Material 3 Expressive UI Modernization
- **Unified Connected Button Groups (`PetalChoiceButtonGroup`)**: Replaced all segmented buttons and pick-one selectors with Material 3 Expressive connected toggle buttons:
  - Full-pill morphing shape transitions with spring dynamics.
  - Generous 56dp touch targets, tactile haptic feedback, and accessibility radio button semantics.
  - Seamless horizontal scrolling when options exceed 4 or labels are long, eliminating text clipping.

---

### v4.0.1 — 🚀 Hotfix: Link Navigation & External App Intent Handling

> *"Hotfix release restoring seamless link opening from external apps and fixing blank page issues on website link taps."*

#### 🐛 Bug Fixes & Improvements
- **External App Link Opening**: Fixed links opened from other apps and browsers (via `ACTION_VIEW`) failing to load in Petal. Added robust fallback URL extraction from `EXTRA_TEXT`, added `petal://` scheme intent support, and prevented duplicate lifecycle dispatch collisions in `onNewIntent`.
- **Website Link Navigation & Blank Pages**: Fixed tapping on links in websites (e.g. `target="_blank"`, popups, and JavaScript windows) opening blank pages instead of the target website. Correctly preserved adopted popup window requests and prevented the browser from misclassifying new window sessions as the home page and calling `stopLoading()`.

---

### v3.9 — 🌸 Material 3 Expressive Extensions & Find in Page Milestone

> *"Material 3 Expressive Extensions Manager with dedicated Built-in & Firefox Add-on tabs, real AMO icons, Petal Dark Webpages with whitelist, and website-exclusive floating Find in Page."*

#### 🧩 Extensions Ecosystem Modernization
- **Expressive Multi-Tab Architecture**: Completely redesigned `PetalExtensionsScreen` using Material 3 Expressive design tokens, pill tabs, fluid elevation, and responsive container cards.
  - **Tab 1 — Petal Built-in Extensions**: Dedicated ecosystem for browser-native capabilities:
    - *Dark Webpages*: Native CSS/DOM inversion dark mode engine replacing legacy Dark Reader, featuring per-site whitelist controls and real-time contrast adjustment.
    - *Clean Link*: Automatic real-time stripping of URL marketing trackers, analytics parameters (`utm_*`, `fbclid`, `gclid`, etc.).
    - *Universal Copy*: Enables text selection and copying on sites that aggressively restrict user selection.
    - *AI Overview Blocker*: Filters out intrusive search engine AI-generated preview widgets.
  - **Tab 2 — Firefox Add-ons (AMO)**: Combined Installed and Recommended sections on a single unified canvas with real AMO icons fetched live via Mozilla CDN URLs with disk caching.
- **Dedicated Extension Settings**: Built-in extensions now feature detailed configuration sheets with toggles, domain whitelists, and parameter controls.
- **Menu Simplification**: Consolidated disparate tool toggles from the overflow menu into Petal Built-in Extensions.

#### 🔍 Material 3 Expressive Find in Page
- **Floating Expressive Pill Bar**: Replaced legacy Android search bar with `PetalFindInPageBar` built entirely with Material 3 Expressive styling, soft rounded corners, tonal elevation, and smooth slide animations.
- **Website-Exclusive Display**: Find in Page is now strictly available only while browsing web content, automatically hidden on homepage, bookmarks, and internal views.
- **Fluid Keyboard & Match Traversal**: Instant auto-focus, ime navigation actions, match jumping with directional indicator controls, and responsive dismiss gesture handling.

#### 🛠️ Core Stability & GeckoView Integration
- **Legacy WebView Cleanup**: Resolved residual conditional references to legacy components, unifying all browser tabs under Mozilla GeckoView.
- **Build & Interop Optimization**: Verified Java/Kotlin Composable SAM conversions and Compose lifecycle bindings.

---

### v3.3 — Address Bar & Search Customization

> *Compact address bar, dedicated customization settings, improved bottom positioning, and unlimited custom search engines.*

### v3.1.2 — 🔧 Extensions, Media & Pull-to-Refresh Fixed

> *"Tap an extension, see its popup. Detect media, see the overlay. Pull to refresh, actually refresh."*

#### 🐛 Bug Fixes
- **Extension Popup Not Showing**: Fixed extension browser/page-action popups (uBlock Origin, Bitwarden, AdGuard, 1Password, etc.) never appearing when tapping the extension button. The `PopupRequestListener` bridge was never registered in `BrowserActivity`, so popup sessions were created but silently discarded.
- **Media Grabber Overlay Missing**: Fixed the media-detected banner ("Media found — X sources on this page") never appearing even when the media grabber extension correctly detected playable video/audio. The `media_sniffer_compose` ComposeView existed in the layout but `PetalMediaSnifferOverlayBridge.bind()` was never called.
- **Pull-to-Refresh Permanently Blocked**: Fixed pull-to-refresh being permanently disabled after any GeckoView scroll. GeckoView calls `requestDisallowInterceptTouchEvent(true)` during scroll which was never reset on the next finger-down, blocking all subsequent refresh attempts. Also reduced the pull trigger distance from an unreachable 300dp to a natural 120dp.

#### ⚡ Improvements
- **Extension Popup Responsive Layout**: Extension popup sessions now inject a mobile-responsive CSS + viewport meta tag fix on page load, matching omni's behavior. Extensions that previously overflowed at their desktop fixed-widths (uBlock Origin, Bitwarden, etc.) now render correctly on phone screen widths.
- **Direct Popup Sessions**: `openDirectPopup` (fallback for extensions without a live action delegate) now uses `VIEWPORT_MODE_MOBILE` + `allowJavascript(true)` GeckoSession settings and also injects the responsive CSS fix.

---

### v3.1.1 — 🧭 Address Bar Polish & Navigation Harmony 🪄✨


> *"Pixel-perfect address bar positioning, zero navbar overlap, and seamless scroll dynamics."*

#### 🐛 Bug & UI/UX Fixes
- **Bottom Address Bar Navigation Offset**: Fixed bottom address bar overlapping bottom navigation bars (both floating and non-floating styles). Corrected dynamic margin calculations incorporating system navigation bar insets and scheduled layout settle listeners.
- **Address Bar Scroll Visibility**: Fixed address bar not restoring or failing to show after scrolling in web sessions. Synchronized GeckoView and WebView scroll change listeners to properly animate collapse and expansion together with bottom navigation.
- **State Reset on Page Load**: Guaranteed address bar visibility and uncollapsed state resets on page navigation, reload, and tab switching, hiding orphaned floating bubble toggles.

---

### v3.1 — 📑 Tab Sorcery & Seamless Flow 🪄✨

> *"Instant previews, persistent tabs, and unbreakable navigation flow."*

#### 🐛 Bug & Stability Fixes
- **Persistent Tab Thumbnails**: Fixed Tab Manager preview thumbnail cache eviction. Thumbnails are no longer wiped on background memory trimming (`onTrimMemory`), persisting in memory (32-entry LRU) and disk cache until tabs are explicitly closed or cleared.
- **Tab Navigation & Site Restore**: Resolved issue where tapping tabs in the Tab Manager unexpectedly navigated to "Petal Home" instead of their intended website. Pre-evaluates target and restored URLs before home page checks.
- **Subframe Metadata Isolation**: Prevented invisible `about:blank` subframes and third-party payment widgets (Stripe, PayPal, Ko-fi) in `PetalGeckoView` from mutating the parent tab's title or address to the homepage.
- **Back Gesture Architecture**: Seamless predictive and system back gesture navigation with robust Gecko session history synchronization.

#### ⚡ Performance & Interoperability
- **WebExtension Popup & Delegate Plumbing**: Fixed GeckoView `ContentDelegate` and `NavigationDelegate` coordination for Firefox add-on popups, enabling native window close and external link dispatch into foreground tabs.
- **PWA & Offline Web App Architecture**: Hardened background thread favicon resolution and domain extraction for Progressive Web Apps.
- **Build & CI Pipeline Resilience**: Clean Java/Kotlin interoperability SAM conversions and robust artifact delivery.

---

### v3.0 — 🌸 Petal: The Grand Milestone (v2.0 to v3.0 Evolution) 🚀🦎

> *"Personal Window to the Web — Completely Gecko-Powered, Zero Trackers, Native Viewers & Seamless Custom Tabs."*

Welcome to **Petal 3.0**! This monumental milestone celebrates Petal's complete evolutionary journey from a lightweight browser shell into a modern, privacy-first flagship powerhouse powered by Mozilla GeckoView. Here is everything built, perfected, and unified across the entire v2.0 → v3.0 era:

---

#### 🦎 1. The GeckoView Quantum Leap (v2.8 – v3.0)
- **Bye Bye WebView, Hello Gecko 154!**: Full migration from Android System WebView to Mozilla GeckoView 154. True modern web standards compliance, sandboxed multi-process tab architecture, and zero Google WebView tracking.
- **Gecko MediaSession & Picture-in-Picture**: Bidirectional HTML5 media session bridge supporting lock-screen controls, notification scrubbers, speed controls, and smooth PiP transitions with dynamic aspect ratio tracking.
- **Chrome-Style Session Rehydration**: Bulletproof session restoration preserving open tabs, tab groupings, scroll positions, and navigation history across app restarts.
- **Rock-Solid Gestures & Touch Streams**: Clean edge-swipe back navigation, predictive back gesture support, and touch arbitration across pull-to-refresh containers and GeckoView.

#### 🚀 2. AndroidX Custom Tabs Provider & Lightweight Shell (New in v3.0)
- **Official Custom Tabs Provider**: Petal now acts as an Android Custom Tabs provider via `androidx.browser:browser`. External apps (Twitter/X, Reddit, Gmail, etc.) can now launch Petal as their native in-app browser overlay.
- **Bound Provider Service (`PetalCustomTabsService`)**: Implements session negotiation, warm handshakes, and pre-render signals (`android.support.customtabs.action.CustomTabsService`).
- **Dedicated Overlay Activity (`PetalCustomTabActivity`)**: Lightweight, distraction-free overlay hosting an isolated `PetalGeckoView` session with full tracking protection, secure HTTPS indicator, and a one-tap **"Open in Petal"** button to promote sessions into full multi-tab browsing.

#### 🎨 3. Native Media, File & Document Viewers (v2.7 – v2.9.9)
- **Native Video Player & Wavy Seekbar (v2.7)**: mpvEx-inspired native video overlay featuring an animated squiggly seekbar, vertical gesture HUDs for brightness and volume, 10s skip intervals, and playback speed selectors (with smart bypass for YouTube).
- **Material 3 Expressive Image Viewer (v2.9.8)**: Full-screen offline image viewer with pinch-to-zoom (0.8×–5.0×), double-tap spring zoom, gallery mode, wallpaper setter, image info bottom sheet, and web long-press integration ("View in Petal Viewer").
- **Featherweight PDF Engine (v2.9.9)**: Zero-dependency, native `PdfRenderer`-powered document viewer featuring continuous vertical scrolling, thumbnail skimmer drawer, jump-to-page slider, system printing integration, and metadata inspection.
- **Universal File Viewer 60 FPS Engine (v3.0)**: Smooth virtualized text rendering with horizontal single-scroll containment, dynamic **Word Wrap** toggle, and optimized rendering for DOCX and XLSX tables.

#### 🕵️ 4. Incognito Stealth & Privacy Redefined (New in v3.0)
- **Material 3 Expressive Incognito Redesign**: Chrome-inspired clean layout featuring an ambient-glowing detective fedora hero badge, bulleted privacy breakdowns, and an interactive privacy safeguards dialog.
- **Decoy Omnibox Search Bar**: Pill-shaped search bar with tactile haptic bounce, Google Lens visual search, and voice search integration.
- **Isolated Incognito Session Suggester**: Segregated omnibox query suggestions that vanish completely upon closing private tabs.
- **Expressive Cookie Shield**: One-tap card toggle for blocking third-party tracking cookies with animated thumb transitions.

#### ⚡ 5. Speed, Downloads & Network Privacy (v2.6 – v2.9)
- **High-Speed Segmented Downloader**: 12-thread segmented downloading powered by Fetch2 and OkHttp with auto-resume, RFC 5987 UTF-8 filename decoding, and smart network recovery.
- **Instant External Download Dispatch (v2.9)**: Instant URL handoff to 1DM, ADM, AB Download Manager, and Navi without confirmation dialog interruptions.
- **Advanced Content Blocker**: High-performance request interceptor equipped with EasyList, HaGeZi, and uAssets host filter engines alongside dynamic cosmetic filters.
- **Custom DNS-over-HTTPS (DoH)**: Built-in encrypted DNS support for Cloudflare, NextDNS, and custom user-defined DoH endpoints.
- **Smart Background Navigation**: Automatically open intrusive link redirects in background tabs without disrupting active reading sessions.

#### 🌸 6. Material 3 Expressive UI, Animations & Shell
- **Interactive Address Bar Pill**: Horizontal swipe gestures to cycle tabs, inline loading indicator, and long-press quick action bottom sheet (clean URL copy, paste & go, bookmark, hard refresh).
- **Cinematic Cold-Start Intros**: Optional 3D Prism & Optical Laser Sweep and Blooming Petal splash animations with synchronized micro-haptics.
- **Unified Settings Hub**: Cohesive M3 Expressive container cards, rounded position-aware icon badges, and dynamic keyword search indexing across all preference pages.
- **Tactile Tab Management**: Long-press bottom bar tab pill for instant tab creation, clean 3-dot overflow menu, and grid/vertical tab manager layouts.
- **Zero Data Loss Guarantee**: Strict backward compatibility preserving bookmarks, history, settings, and session tabs across every single release.

---

### v2.9.9 — 📄 Featherweight PDF Engine & Crystal Security ✨

#### 🎨 UI/UX — Pure Material 3 Expressive PDF Viewer (Inspired by ImageToolbox)
- **Built-in Pure M3 Expressive PDF Viewer**: Native, zero-bloat PDF viewing experience powered entirely by Android's native `android.graphics.pdf.PdfRenderer` — no heavy external binary dependencies or tracking.
- **Multi-Page Continuous Scroll & Gestures**: Smooth, hardware-accelerated continuous vertical scrolling with `LazyColumn`, responsive pinch-to-zoom (0.8× to 5.0×), pan gestures, and double-tap zoom toggle (1× ↔ 2.4×) with spring dynamics.
- **Expressive Floating App Bars**: Floating translucent glassmorphism top and bottom action bars with auto-hide timer (4s) and tap-to-toggle gesture.
- **Top Bar Controls**: Quick document title, interactive page indicator pill ("Page X of Y • Tap to jump"), one-tap Android `PrintManager` integration (print to wireless printer or save as PDF), system share sheet, and document info sheet.
- **Thumbnail Skimmer Drawer**: Bottom sheet modal grid (`GridCells.Fixed(3)`) rendering live asynchronous page thumbnails for rapid visual skimming and direct page hopping.
- **Jump to Page Modal**: Interactive page navigation dialog featuring an expressive M3 slider alongside direct numerical input.
- **Document Metadata Inspector**: Detailed modal bottom sheet displaying file name, page count, formatted file size, modification timestamp, MIME type, and local URI path.
- **Integrated System Ingress**:
  - Automatically handles `application/pdf` and `.pdf` file intents via `ACTION_VIEW` and `ACTION_SEND`.
  - Downloads page integration: Tapping any downloaded `.pdf` file opens it instantly in Petal's built-in PDF viewer.

#### 🔍 Omnibox & Search UX Enhancements
- **Intelligent Start Truncation (`...`) for Long Queries**: Long search suggestion strings now gracefully hide starting words with a leading ellipsis while preserving trailing query words, ensuring what you are actively typing and searching remains fully visible.
- **Incognito Search Privacy Isolation**: Omnibox search suggestions in Incognito tabs are strictly segregated into independent session storage, never exposed to regular browsing tabs, and fully wiped on closing Incognito tabs.

#### 🔒 Security & Live Connection Status Fix
- **Live Connection Status**: Fixed the "Connection status unavailable" issue in the site information bottom sheet by resolving `currentAlbumController` and inspecting GeckoView's real-time `SecurityInformation` (`currentSecurityInfo`), HTTPS transport state, and local internal page schemes (`petal:`, `about:`).
- **CI/CD Build Fix**: Restored missing `PreferenceManager` import in `BackupUnit.java`.

---

### v2.9.8 — 🖼️ Peek-a-Petal: The Image Viewer Arrives!

#### 🎨 UI/UX — Built-in Image Viewer
- **Native Material 3 Expressive Image Viewer**: Full-screen offline-capable viewer using `HorizontalPager`, `detectTransformGestures`, and Compose animation — zero new dependencies for local images
- **Pinch-to-zoom** (0.8×–5×), **pan** when zoomed, **double-tap** toggle zoom (1× ↔ 2.5×) with spring animation
- **Gallery mode**: swipe left/right between downloaded images in a session
- **Auto-hide controls**: single tap shows/hides top bar + bottom bar with a 3.5s countdown
- **Top bar**: back button, filename + "X of Y" counter, Share, Copy URL, Open in external app (⋮ menu)
- **Bottom bar**: translucent pill with Rotate (90° CW, animated), Delete (with Undo snackbar), Set as Wallpaper, Info
- **Image Info bottom sheet**: dimensions, file size, modified date, full path, source URL
- **Predictive back** swipe-to-dismiss via `PetalPredictiveBackSurface`

#### 🌐 Web Image Support
- **Long-press any image on a website** → new "View in Petal Viewer" option added as the first item in the image context menu
- Remote images loaded via **Coil** with crossfade — works on any HTTP/HTTPS image URL, with loading/error states

#### 📤 External Source Support
- **Open With**: Petal now appears as an image viewer option when opening images from Files, Gallery, or any app (`ACTION_VIEW image/*`)
- **Share to Petal**: receive shared images from any app directly into the viewer (`ACTION_SEND image/*`)
- Supports `content://` and `file://` URIs with proper `ContentResolver` display name resolution

#### 📥 Downloads Page Integration
- **Preview strip tap** → opens full-screen image viewer instead of system opener
- **Row item tap** → launches viewer for image file types
- **Long-press preview strip** → "View Image" (Petal viewer) + "Open in app" (system) options

#### 🐛 Bug Fixes & Stability
- Merged upstream: progress bar rendering fix, GeckoView touch improvements
- Delete button hidden for remote/external images (can't delete what wasn't downloaded)

---

### v2.9 - Warp Speed Downloads & Background Magic 🚀⚡
- **⚡ Streamlined External Download Dispatch (Issue #6)**:
  - **Instant External Handoff**: When an external download manager (1DM, ADM, AB Download Manager, Navi) is selected, Petal skips redundant in-app download confirmation prompts and dispatches download URLs, cookies, and headers directly to the external app.
  - **Untouched Built-in Downloader**: The built-in multi-threaded Fetch2 downloader continues to present the full download confirmation modal dialog as expected.
- **🔄 Smart Background Redirect Navigation (Issue #6)**:
  - **Open Redirects in Background Tab**: Added setting in *Settings > Privacy & Security* to automatically open link redirects in new background tabs without disrupting current reading sessions.
  - **GeckoView Navigation Routing**: Implemented intelligent cross-origin and window-targeting redirect checks within GeckoView's navigation delegate.
- **🛡️ Custom DNS-over-HTTPS (DoH) Endpoint**:
  - **Custom DoH Provider**: Enhanced Private DNS Protection settings to support custom DoH URLs alongside standard Cloudflare and NextDNS providers.
- **📑 Quick Tab Creation on Long-Press**:
  - **Tactile Long-Press on Tabs Button**: Long-pressing the Tabs counter pill on the bottom navigation bar instantly spawns a new tab with pleasant haptic feedback.
- **🔒 Zero Data Loss Guarantee**:
  - Strictly preserves existing SQLite bookmarks, browsing history, user preferences, and session state.

### v2.8.2
- **🎬 Native GeckoView MediaSession & Picture-in-Picture Engine**:
  - **Gecko MediaSession Integration**: Wired GeckoView's native `GeckoSession.mediaSessionDelegate` (`MediaSession.Delegate`) directly into `PetalMediaBridge`, detecting HTML5 play, pause, stop, position scrubbing, and track duration events across all Gecko tabs.
  - **Fluid Picture-in-Picture Control & Dimension Tracking**: Video aspect ratio, resolution updates, and remote PiP actions (Play/Pause, Rewind 10s, Forward 10s) route bi-directionally to active Gecko media sessions with dynamic source rect hints.
  - **Foreground Media Session Service**: Synchronized notification controls, scrubber progress, speed adjustments, and mute toggles with `PetalMediaSessionService`.
- **🐛 Edge Back Gesture Fix (Issue #6)**:
  - **Resolved False Overlay State**: Fixed back gesture handling in `BrowserActivity` by properly recognizing `PetalGeckoView` as a primary browsing surface rather than an overlay screen, restoring edge back-swipe navigation across all tabs.
- **🔒 Zero Data Loss Guarantee**:
  - Full backward compatibility across saved tabs, bookmarks, SQLite history, and user preferences.

### v2.8.1
- **🐛 PetalMediaBridge NullPointerException & WebView Deferred Binding Fix**:
  - **Deferred WebView Binding**: Resolved launch `NullPointerException` inside `PetalMediaBridge` by implementing safe deferred binding (`attachWebView(WebView)`) and null-safe bridge initialization for GeckoView tabs.
  - **Dynamic Bidirectional Registration**: Ensured `NinjaWebView.setMediaBridge` automatically registers the Javascript media interface whenever a WebView is attached, maintaining full HTML5 media controls and background media notifications.
  - **GeckoView Startup Stability**: Guaranteed clean app startup without crash during tab instantiation and session rehydration.
- **🔒 Zero Data Loss Guarantee**:
  - Full backward compatibility across saved tabs, bookmarks, SQLite history, and user preferences.

### v2.8 - Bye Bye WebView, Hello Gecko Gecko! 🦎💨

- **🦎 Full Mozilla GeckoView 154 Engine Migration**:
  - **Complete Engine Transition**: Fully transitioned web page rendering, session management, and DOM handling across all tabs and popups to Mozilla GeckoView 154. Zero legacy Android System WebView instances are running for browsing.
  - **Identified as Gecko / Firefox**: Web standards test suites and browsers accurately identify Petal as powered by modern Gecko 154.
  - **Chrome-Style Tab Rehydration & Zero Data Loss**: Upgraded session serialization and persistence to save and restore GeckoView tabs, group titles, active indexes, and URLs seamlessly across app restarts.
- **✨ Material 3 Expressive Context Menu**:
  - **Next-Gen Bottom Sheet Context Surfaces**: Long-pressing web links, images, video elements, audio streams, and text selection opens a sleek Material 3 Expressive bottom sheet with rounded action cards and fluid micro-interactions.
  - **Rich Action Suites**: One-tap quick actions for Tab Groups, Incognito tabs, Lens visual search, Petal Scanner, media downloads, copy URL/text, and web search.
- **🎯 Dynamic Address Bar & Gesture Integration**:
  - Bound omnibox status, SSL indicators, favicons, and page progress directly to GeckoView session delegates.
  - Smooth nested scrolling physics for automatic address bar collapse/expand.
- **🔒 Zero Data Loss Guarantee**:
  - Full backward compatibility across saved tabs, bookmarks, SQLite history, and user preferences.

### v2.7.4

- **🔍 Gesture Diagnostics & Stability**:
  - **Back Navigation Trace Telemetry**: Integrated lightweight diagnostic logging (`PETAL_BACK_DEBUG`) to accurately monitor system edge swipe dispatching and `OnBackPressedCallback` invocation states without altering native navigation flows.
- **📜 Compliance & Documentation**:
  - **Privacy Policy Documentation**: Added official repository Privacy Policy documentation covering zero data collection, sandboxed internal storage, and strict on-device data isolation.
- **🔒 Zero Data Loss**:
  - Full backward compatibility guaranteed across bookmarks, browsing history, saved tabs, and user preferences.

### v2.7.3

- **📦 Build & App Bundle Language Compliance**:
  - **Resolved Deprecated Language Qualifier**: Migrated deprecated legacy `values-sh` resource folder to BCP-47 compliant `values-b+sr+Latn` (Serbian Latin), resolving Google Play App Bundle submission errors regarding unrecognized language `sh`.
  - **Explicit Resource Configurations**: Configured explicit Gradle `resConfigs` to whitelist all supported locales and strip any unrecognized or invalid third-party library language resources from final AAB / APK packages.
- **🔒 Zero Data Loss**:
  - All existing user settings, bookmarks, tabs, and history remain strictly intact.

### v2.7.2

- **🐛 Default Search Provider & Settings Sheet Fix**:
  - **Fluid Search Provider Selection**: Resolved touch event interception where the Default Search Provider bottom sheet became unresponsive by removing conflicting nested surface wrappers and nested radio click handlers.
  - **Accurate Selection State**: Correctly synchronizes the initial and updated search engine index state between Settings and the modal sheet.
- **⚡ Performance & Package Optimization**:
  - **APK Footprint Reduction**: Removed duplicate drawable resources and redundant raw filter asset sources (`~2.8 MB` reduction in repository assets).
- **🔒 Build & Platform Stability**:
  - **Zero Data Loss**: User preferences, bookmarks, history, and search engine selections are strictly preserved.

### v2.7.1

- **🐛 Edge Back Gesture Navigation & WebView Touch Fixes**:
  - **Unblocked System Back Gestures**: Prevented Chromium / WebView from claiming system gesture exclusion zones along display edges on Android 10+ (API 29+), allowing full system back gestures and predictive back navigation to fire reliably on any website.
  - **Synchronous Exclusion Reset**: Enforced empty system gesture exclusion rects immediately upon touch interaction (`ACTION_DOWN`, `ACTION_UP`, `ACTION_CANCEL`) and layout recomputation, guaranteeing smooth edge back-swipes without gesture trapping.
  - **Zero Data Loss**: User session tabs, bookmarks, history, and customized settings are strictly preserved.

### v2.7 - New Webview & Video Player update

- **🎨 Native Video Player & Fancy Wavy Seekbar**:
  - **Fluid Overlay Controls**: Implemented modern native fullscreen video overlay controls featuring auto-hiding navigation, quick 10s rewind and forward skip, center play/pause toggle, and playback speed adjustment menu (0.5x to 2.0x).
  - **Fancy Squiggly Wavy Seekbar**: Integrated animated sine wave propagation seekbar inspired by mpvEx, with smooth flattening dynamics during pause/scrubbing and dual interactive time readouts.
  - **Vertical Gesture HUD**: Slide vertically on the left side of the player to adjust screen brightness with live percentage HUD, and on the right side to adjust audio volume.
  - **YouTube Compatibility Protection**: Automatically bypasses custom overlay controls on YouTube (`youtube.com`, `youtu.be`, embedded YouTube frames) to preserve YouTube's native player features and UI.
- **📺 Rebuilt Dynamic Picture-in-Picture (PiP)**:
  - Upgraded Android Picture-in-Picture geometry engine using mpvEx-inspired aspect ratio calculations and letterbox/pillarbox source rect hint for fluid, seamless window transitions.
  - Added remote PiP action buttons: 10s skip backward, dynamic play/pause toggle, and 10s skip forward.
- **⚡ High-Performance Core Web Engine & AdBlocker**:
  - Re-architected WebView touch stream dispatch, smooth physics momentum scrolling, and rapid rendering pipeline.
  - Enhanced content blocking and ad filtering engines for swift, tracker-free web browsing while strictly preserving address bar collapse, context menus, and download confirmations.
- **🌟 Open Source Credits**:
  - Added **mpvEx** (by marlboro-advance) to the Credits section in recognition of video player UI/UX and wavy seekbar design.

### v2.6.8

- **🎨 Interactive Address Bar & Expressive Gestures**:
  - **Horizontal Tab Swiping**: Swipe left or right directly across the address bar pill to switch between tabs with tactile micro-haptic feedback.
  - **Long-Press Quick Actions**: Press and hold the address bar to open a Material 3 bottom sheet with high-value actions:
    - 📋 **Copy Clean URL**: Automatically strips tracking and telemetry query parameters (`utm_*`, `fbclid`, `gclid`, etc.) before copying to clipboard.
    - 🔗 **Paste & Go**: One-tap search or navigation directly from your clipboard content.
    - ⭐️ **Quick Bookmark**: Instantly add or remove the current page from bookmarks with live state feedback.
    - 🔄 **Hard Refresh**: Reload the current page and bypass cache.
  - **Smart Dynamic Visuals**:
    - Embedded site favicon capsule chip with SSL security status icon (Tune / Lock / Incognito).
    - Subtle animated inline loading progress indicator integrated directly into the bottom curve of the address bar pill.
- **♿ Accessibility Preferences**:
  - Added dedicated toggles in **Accessibility Settings** for horizontal tab swiping and long-press quick actions (enabled by default).

### v2.6.7

- **🎨 Unified Settings Subpages Containment & Expressive Theming**:
  - Harmonized visual containment across all sub-settings screens (**Appearance & Theme**, **Privacy & Security**, **Search Engine & Home**, **Accessibility**, **Miscellaneous**, **Experimental**, and **API & Integrations**) to match the design rhythm of the main Settings Hub overview.
  - Upgraded section cards to full-width elevated containers (`surfaceVariant` with 70% alpha, 24dp rounded corners, and zero shadow elevation) featuring integrated top headers with signature 48dp `primary`-accent rounded badges (12dp corner radius), crisp `onPrimary` icons, and semi-bold title typography separated by soft outline dividers.
  - Enhanced all toggle rows with 40dp position-aware rounded icon badges, accent primary container fills when enabled, and refined `surfaceContainerHigh` background surfaces.
  - Standardized slider and selection surfaces with consistent 16dp corner geometry for full visual consistency across the entire application.

### v2.6.6

- **🎨 Multi-Style Cold-Start Intro Animations & Experimental Settings**:
  - Added preference option in **Experimental Settings** to customize the cold-start intro animation:
    - `Off (Default)`: Instant entrance with zero splash or intro delays.
    - `3D Prism & Optical Beam`: High-precision crystalline 3D isometric prism with optical laser sweep and chromatic spectral refraction waves.
    - `The Blooming Petal`: Organic fluid blossoming unfolding into 5 glassmorphic 3D petals with concentric harmonic light ripples.
  - Resolved Android splash screen occlusion: lengthened intro duration to ~950ms and introduced grace buffers so the entire cinematic sequence remains fully visible after the app icon splash dismisses.
  - Integrated dynamic search keyword indexing across Settings Hub for all intro animation terms.

### v2.6.5

- **⚡ 3D Prism & Optical Beam Cold-Start Intro**:
  - Integrated ultra-smooth 3D isometric crystal glass prism intro powered by hardware-accelerated Compose Canvas shader passes.
  - Features real-time optical coherent laser sweep and chromatic spectral refraction across Petal's module palette (Cyan Speed, Violet Privacy, Emerald Engine).
  - Blooming 3D petal emblem transition accompanied by synchronized dual-stage micro-haptic tactile feedback.
  - Seamlessly blends into the Material 3 Expressive Home layout on cold start, skipping on warm returns for 0ms overhead.

### v2.6.4 (Hotfix)

- **🐛 Custom Scheme Handling & Home Page Resolution**:
  - Resolved `net::ERR_UNKNOWN_URL_SCHEME` when opening or restoring the home page after browser restart.
  - Automatically intercepted `petal://` and custom home URLs in `NinjaWebView.loadUrl()`, loading a clean blank canvas while retaining complete Compose Home UI state.
  - Added internal protocol routing in `NinjaWebViewClient.shouldOverrideUrlLoading()` and safeguarded session restoration in `BrowserActivity.java`.

### v2.6.3 (Hotfix)

- **⚡ Unified High-Speed Download Architecture**:
  - Unified best-in-class multi-threaded segmented downloading powered by Fetch2 and OkHttp (12 concurrent threads, transparent retry on network change, pause/resume/cancel).
  - Integrated robust RFC 5987 / UTF-8 filename decoding, control-character stripping, and safe HTTP header sanitization.
  - Automatic P2P and torrent link routing to 1DM when installed, with seamless in-app streaming fallback.
- **🎨 Streamlined Settings & Frictionless Downloads**:
  - Removed manual download engine tweak settings from Miscellaneous Settings to streamline preferences.
  - Removed first-time download engine prompt dialog, allowing downloads to begin directly and smoothly without interruptions.

### v2.6.2 (Hotfix)

- **🛡️ Advanced Content Blocker & Multi-Layer Ad Protection Engine**:
  - Integrated high-performance request interceptor with bundled EasyList, HaGeZi, and uAssets host binary indices.
  - Added procedural cosmetic rules engine (`:has()`, `:has-text()`, `:matches-path()`) running at document start via MutationObserver.
  - Added EasyList and overrides cookie banner removal stylesheet injected directly at page commit.
- **🎥 Web Media & Playback Engine**:
  - Added robust HTML5 video and audio media bridge tracking playback, mute states, and Picture-in-Picture sessions seamlessly.
  - Added video autoplay suppression controls and background media playback coordination.
- **🔐 Native Passkeys & System Credentials Integration**:
  - Configured native `WebAuthentication` (passkeys) and system autofill compatibility directly via `WebSettingsCompat`.
- **⚡ Smooth Momentum Recovery & Navigation Stability**:
  - Added compositor momentum recovery watchdog preventing scroll freezes on high refresh-rate displays.
  - Intelligent cross-site popup and popunder navigation arbitration defusing intrusive redirects.

### v2.6.1 (Hotfix)

- **🔄 Universal Release Inbuilt Updater**:
  - Inbuilt app updater now prioritizes downloading universal APK releases across all device types and ABIs.
- **📜 Seamless ModalBottomSheet Scrolling Physics**:
  - Migrated update and changelog dialogs to pure Compose `ModalBottomSheet` with nested scrolling support, matching `Essentials` scrolling physics seamlessly without touch gesture conflicts.
- **🏗️ Multi-Architecture Release Builds**:
  - GitHub Actions and Gradle now build and release individual APKs for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`, alongside the universal APK.

### v2.6.0 (Official Release)

- **🚀 Complete Removal of Gecko Engine & Extensions**:
  - Completely stripped out Mozilla GeckoView (`geckoview-omni:150`) and all associated runtime holders, sessions, installer, and UI settings.
  - APK size drastically reduced (~100 MB lighter) with lower idle memory footprint.

### v2.5.1 (Hotfix)

- **🐛 Predictive Back Shutter Eliminated — App Lock & About Developer Screens**:
  - Fixed: App Lock overlay and App Lock Config screen caused a single-frame white/black flash (shutter) when a predictive back gesture committed. Root cause: `decor.removeView(composeView)` was called immediately, tearing the View out while Compose's spring exit animation was still in its 200ms settle window.
  - Fix: Added a 160ms `ViewPropertyAnimator` alpha fade (`0f`) before the `removeView` call, so the composable has time to finish its exit choreography before the View is detached.
  - Fixed: About Developer screen was using a raw `BackHandler` call with no `PetalPredictiveBackSurface` / `PetalScreenWrapper` wrapper — predictive back showed a harsh tear. Now properly wrapped matching the Credits screen pattern.

### v2.5 (Official Release)

- **🔖 HTML Bookmark Import & Export (Standard Netscape Format)**:
  - Added full support for importing and exporting bookmarks using the industry-standard Netscape Bookmark File format (`bookmarks.html`), fully compatible with Google Chrome, Mozilla Firefox, Apple Safari, Microsoft Edge, and Brave.
  - Added one-tap **Import (FileUpload)** and **Export (FileDownload)** header action icons directly on the Material 3 Bookmarks screen.
  - Integrated with Storage Access Framework (`CreateDocument` and `OpenDocument`) for seamless folder and file selection across all Android versions.
  - Added dedicated HTML Bookmarks import and export controls under **Settings → Data & Storage → Backup & Restore**.
  - Built-in duplicate detection, timestamp parsing (`ADD_DATE`), and transactional database integrity guarantees.

- **✨ Live Dynamic Predictive Back Previews & Depth Blur**:
  - Replaced static/stale initial screen captures with a reactive `liveSnapshotFlow` stream in `PetalContentSnapshot`, delivering real-time underlays during active predictive back gestures.
  - Synchronized gesture spring physics with `RvSystem-Monitor` and `PixelPlayer` specs (`Spring.StiffnessMedium`, no bounce on commit, low bounce on cancel).

- **⚡ Stutter-Free Website Back Navigation**:
  - Removed synchronous preference re-initialization churn inside `NinjaWebView.goBack()` and `goBackOrForward()`.
  - Replaced heavy web settings resets with clean domain-aware navigation with specialized domain-aware rules, ensuring smooth 60/120fps back navigation without frame drops.

- **🧩 WebExtensions Management & Runtime Fixes**:
  - Stabilized Gecko extension management, add-on discovery prompt delegation, and dynamic counter badges.

### v2.4 (Official Release)

- **🎨 Settings Inner Categories Containment Harmonization**:
  - Unified all inner settings category page cards (`SettingsCategoryCard`) across Appearance, Privacy & Security, Accessibility, and Experimental to match the exact Material 3 `surfaceContainerLow` elevation, `1.dp` outline borders, and `24.dp` rounded shapes of the main settings overview screen.
  - Added the **API & Integrations Hub** directly into the main Overview categories list for seamless navigation.

- **🌐 Dedicated Direct Web Back Navigation**:
  - Replaced predictive back root-view scaling on web pages with a clean, direct web back handler, ensuring zero glitching or displacement during website browsing while strictly preserving predictive back transitions on overlay and sheet screens.

- **🏷️ Bottom Navbar Tab Count Badge Optimization**:
  - Shortened incognito tab counter text to `"Tabs ($count)"` to guarantee crisp visibility inside the floating navigation pill without text truncation.

### v2.3 (Official Release)

- **🎨 Settings & Account Containments & RvSystem-Monitor Icon Overhaul**:
  - Replaced flat and inconsistent card backgrounds across all Settings categories, Account & Profile pages, and UI components with unified Material 3 `surfaceContainerLow` containers, subtle `1.dp` outline borders, and `24.dp` rounded shapes.
  - Replaced legacy settings category and sub-card drawables with the cohesive, expressive vector icon set from `RvSystem-Monitor` (`settings_filled`, `brightness_medium_filled`, `layers_filled`, `home_filled`, `mobile_vibrate_filled`, `build_filled`, `translate`, `download_2_filled`, `backup_filled`, `update_rounded`, `info_filled`).

- **⚡ Buttery Smooth Floating Bottom Navigation Bar**:
  - Fixed stutter and jank in the floating bottom navigation bar by eliminating recomposition tree teardown, switching to stable hoisted state holders, and tuning physics animations.

- **🖼️ Persistent Tab Manager Thumbnail Cache**:
  - Fixed tab thumbnail cache persistence across cold restarts by keying previews with unique persistent tab IDs with URL fallback lookup.

- **🚀 High-Speed Multi-Chunk Download Engine**:
  - Optimized download buffering and concurrency limits up to 12 parallel channels with instant 100ms progress dispatching and aggressive network gain retry resilience.

### v2.1 (Hotfix)

- **🎨 Compose Ripple Modernization & Compilation Hotfix**:
  - Migrated deprecated `rememberRipple` indication in `PetalBottomNavBar.kt` to the official Material 3 `ripple()` API, resolving Kotlin compiler deprecation errors and improving touch indication performance.
  - Seamlessly unified interaction source ripple feedback across floating navigation bar items and gesture elements.

- **📢 First-Launch Update Changelog Showcase**:
  - Added a dedicated one-time "What's New in Petal" interactive bottom sheet popup displayed immediately when the browser is updated from a previous version.
  - Highlights full categorized release notes and improvements, ensuring users never miss new features.

- **📜 Full Changelog History from Official v2.0**:
  - Enriched offline fallback changelog history with complete breakdown of all major v2.0 features (Real-Time Vector Shape Morphing Background, Floating Bottom Nav, PixelPlayer Containment System, Avatar Cropper, Settings Categorization, and 100% Kotlin Modernization).

### v2.0 (Official Release)

- **✨ Real-Time Vector Shape Morphing Background (`androidx.graphics.shapes.Morph`)**:
  - Implemented organic, continuous vector shape morphing between all 35 official Material 3 Expressive shapes (`Sunny`, `Cookie9Sided`, `Flower`, `Clover4Leaf`, `SoftBurst`, etc.).
  - Added luminous multi-stop radial gradient halos across M3 tonal color roles (`primary`, `primaryContainer`, `tertiary`) with gentle kinetic breathing.

- **🎨 Floating Bottom Navigation Bar (Zenith Parity)**:
  - Matched Zenith's floating navigation bar dimensions: `68.dp` height, `RoundedCornerShape(100)` stadium pill, `36.dp` bottom offset, `26.dp` icons, and dynamic indicator selection height.

- **📦 PixelPlayer Expressive Containment System Integration**:
  - Integrated proper Material 3 Expressive containments (`SettingsSection`, `SettingsItem`, `SwitchSettingItem`, `SettingsCardContainer`, `ContainedSelectionCard`).
  - Replaced uncontained / flat toggle rows with elevated `Surface` container cards with `16.dp` rounded shapes and animated switch thumbs.

- **🖼️ Profile Picture & Interactive Cropper Overhaul**:
  - Fixed profile picture custom avatar loading and caching: updated Coil image loader with explicit write-only cache policies and fallback handling across `filesDir` and content URIs so custom avatars update immediately on Home screen and Account profile screen.
  - Interactive Crop Viewport: Added precision circular crop dialog with real-time pinch-to-zoom, drag-to-align viewport, 90° rotation controls, reset button, and live preview.
  - Direct Crop & Media Picker Integration: Tapping the avatar hero card or gallery button immediately opens the photo picker and circular crop editor with instant image saving.

- **🗂️ Clean Settings Categorization & New Miscellaneous Hub**:
  - Reorganized `PetalSettingsScreen.kt` into dedicated, intuitive categories: **Appearance & Theme**, **Accessibility**, **Privacy & Security**, **Experimental**, and newly added **Miscellaneous**.
  - Moved Download Engine (1DM Integration / Embedded Native Downloader) and Auto Open External Apps into the dedicated **Miscellaneous** category.
  - Kept Experimental Flags and advanced developer features strictly isolated under the **Experimental** category.

- **⚡ Complete Kotlin & Jetpack Compose Modernization (100% Kotlin)**:
  - 100% Kotlin codebase with complete Java decoupling across all 8 modular browser subsystem delegates and 42 core background managers.
  - Pure Material 3 Expressive UI design system featuring 35 customizable container shapes, fluid predictive back navigation gestures with 24.dp background depth blur, and adaptive dynamic color theming.

- **🌐 Google Lens & In-App Multi-Volume Media Picker**:
  - Full Google Lens search integration across Omnibox search bar, context menus, and home widgets.
  - Multi-volume device storage indexing supporting `VOLUME_EXTERNAL_PRIMARY`, `VOLUME_EXTERNAL`, and fallback to System Photo Picker.

- **🔒 Advanced Privacy, Passkeys & High-Performance Web Engine**:
  - Full WebAuthn & biometric passkey sign-in support via Android Credential Manager.
  - Built-in AdBlock Trie filtering engine with domain whitelist management, WebRTC IP leak protection, Canvas/Audio fingerprint shield, and strict referrer header trimming.
  - Native 120Hz/144Hz high refresh rate hardware pacing and kinetic scrolling.

### v1.9.9

- **🎨 Material 3 Expressive Color Container System (Zenith Parity)**:
  - Aligned Expressive Container Colors with Zenith's high-contrast container elevation model: toned-down soft background (`surfaceContainerLow`) with crisp floating white containers (`Color.White`) in Light mode, and elevated container tones (`surfaceContainerHigh` / AMOLED contrast ladder) in Dark mode.
  - Bound `expressiveColors` dynamically to SharedPreferences `sp_expressive_colors` default in `PetalTheme.kt` and reactive listeners across `PetalHomeScreen.kt`, `PetalSettingsScreen.kt`, `PetalBottomNavBridge.kt`, and `PetalAddressBarBridge.kt`.

- **📳 Universal Touch Haptics Engine Fix**:
  - Removed restrictive OS-level `HAPTIC_FEEDBACK_ENABLED` blocking from `PetalHapticEngine.java` that was disabling app haptics when system touch sounds were off.
  - Switched to direct, multi-tiered vibration execution for reliable tactile feedback on all Android devices.
  - Made `LocalHapticEnabled` and `PetalHapticFeedback` in `PetalTheme.kt` fully reactive to real-time `sp_touch_haptics` preference changes.

- **🌐 Dynamic Per-App Language & Natural Hinglish Support (GRAMLY Inspired)**:
  - Added Android 13+ `res/xml/locales_config.xml` declaring supported in-app locales and registered `AppLocalesMetadataHolderService` in `AndroidManifest.xml`.
  - Added `attachBaseContext` in `PetalApplication.kt` and improved `HelperUnit.applyLanguage()` with `Locale.Builder` script configuration and `LocaleList` support.
  - Added full, conversational Hinglish (Hindi in English) translations across all 174 string resources in `res/values-b+hi+Latn/strings.xml` following GRAMLY's natural phrasing.

- **📑 Tab Manager Thumbnail Isolation & Auto-Scroll**:
  - Fixed tab preview thumbnails getting stuck on a single screenshot: scoped thumbnail cache keys strictly per tab instance (`hashCode()`) and prevented background tabs from capturing the parent container.
  - Added real-time snapshot capture of outgoing tabs before switching away in `BrowserActivity.java`.
  - Added automatic smooth scrolling in `PetalTabGridSwitcher.kt` (`LazyVerticalGrid` and `LazyColumn`) to jump straight to the active tab upon opening the tab manager.

- **⚡ High Refresh Rate (120Hz/144Hz+) Native Hardware Synchronization**:
  - Added [PetalHighRefreshRateManager.kt](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/unit/PetalHighRefreshRateManager.kt) to lock the highest available hardware display mode (`Display.Mode`), preferred refresh rate bounds (`preferredMinDisplayRefreshRate` & `preferredMaxDisplayRefreshRate`), and `Surface.setFrameRate()` on supported devices.
  - Device refresh rate overlays (Developer Options "Show refresh rate", FPS meters, OEM gaming overlays) now immediately detect and sustain a solid 120Hz/144Hz during browsing, scrolling, and tab switching.
  - Added **"High Refresh Rate (120Hz+)"** preference toggle with real-time detected peak hardware refresh rate readout in *Settings -> Display & Themes*.
  - Synchronized `NestedScrollWebView` and decor surfaces with SurfaceFlinger for zero-jitter, fluid 120 FPS kinetic scrolling.

### v1.9.8

- **⚡ 100% Complete Java-to-Kotlin Migration**:
  - Full, seamless conversion of the entire codebase from Java to Kotlin with 0 remaining `.java` files in `com.petal.browser`.
  - Converted core browser engines and web clients: `NinjaWebViewClient.kt`, `NinjaWebChromeClient.kt`, `BrowserContainer.kt`, `BrowserController.kt`, `AlbumController.kt`, `WebAppInterface.kt`, `AdBlock.kt`, `BannerBlock.kt`, and `NinjaDownloadListener.kt`.
  - Converted all Settings Activities, Fragments, and Preference widgets: `Settings_Activity.kt`, `Settings_Backup.kt`, `Settings_Delete.kt`, `Settings_Filter.kt`, `Settings_Gesture.kt`, `Settings_Menu.kt`, `Settings_Profile.kt`, `Settings_ProfileList.kt`, `Fragment_settings*.kt`, `BasePreferenceFragment.kt`, `EditTextSwitchPreference.kt`, and `ListSwitchPreference.kt`.
  - Converted all 42 Utility, Network, DNS, and Security Managers (`BrowsingDataManager`, `CacheManager`, `TabSessionManager`, `TabThumbnailCache`, `SearchSuggestionsManager`, `ReaderModeManager`, `WikipediaSummaryManager`, `CloudflareDnssecManager`, `NextDnsManager`, `OpenDnsDoHManager`, `Quad9DnsManager`, `NetworkBenchmarkManager`, `DuckAssistManager`, `UrlScanSecurityManager`, `WhoisRdapManager`, `IpGeoAuditManager`, `SslHealthAuditManager`, etc.).
  - Converted custom UI views: `NestedScrollWebView.kt`, `NinjaWebView.kt`, and `PetalSearchWidgetProvider.kt`.

- **🎨 Material 3 Expressive UI/UX & 35 Expressive Shapes**:
  - Full system-wide integration with all 35 official Material 3 Expressive Shapes catalog from `PetalMaterialShapes.kt` (`RoundedSquare`, `Cookie4/6/7/9/12Sided`, `Arch`, `Hexagon`, `Octagon`, `Pill`, `SoftScallop`, `Clover4/8Leaf`, `Sunny`, `Flower`, `Star`, `Gem`).
  - Native Edge-to-Edge window inset control and dynamic color extraction across all settings and browser sheets.

- **🚀 Performance & Architecture**:
  - 120Hz smooth scrolling physics with `NestedScrollingChild3` and hardware-accelerated rendering.
  - Robust runtime type-safety and null-safety across all network requests and background tasks.

### v1.9.7

- **⚡ Complete Kotlin Migration & Subsystem Decoupling**:
  - Fully converted `BrowserActivity.java`, `PetalApplication.java`, and `ActivityShare.java` to Kotlin.
  - Extracted 8 modular Kotlin subsystem delegates (`BrowserContextMenuManager`, `BrowserNavigationDelegate`, `BrowserWebViewController`, `BrowserIntentHandler`, `BrowserMediaDelegate`, `BrowserDialogManager`, `BrowserGestureHandler`, `BrowserActivityExtensions`), reducing core activity boilerplate by over 830 lines.

- **🎨 35 Material 3 Expressive Shapes Catalog Integration**:
  - Integrated the complete **35 Material 3 Expressive Shapes Catalog** (`RoundedSquare`, `Cookie6Sided`, `Arch`, `Hexagon`, `Octagon`, `Pill`, `SoftScallop`, `Clover4Leaf`) from `PetalMaterialShapes.kt` across Jetpack Compose UI sheets and icon containers (`PetalLinkContextMenuSheet.kt`).
  - Saved workspace rule `~/.gemini/config/rules/material3_expressive.md` enforcing Pure Material 3 Expressive components, dynamic colors, motion, and container shapes.

- **🐛 Bug & Compilation Fixes**:
  - Fixed `PetalLinkContextMenuHandler` interface compilation and missing method overrides (`onDownloadVideo`, `onOpenImageInNewTab`, `onCopyImage`, `onSearchWithGoogleLens`) across Web context menu handlers.

### v1.9.4

- **✨ Google Lens Integration**:
  - Integrated **Google Lens** across Petal Browser with Camera Capture, Gallery Photo Picker, and Google Lens Search Intent.
  - Added dedicated Lens search button (`CenterFocusWeak` icon) to the search input field in `PetalOmniboxPage.kt`.
  - Added *"Search image with Google Lens"* option to webpage image long-press context menus (`PetalLinkContextMenuSheet.kt` & `BrowserActivity.java`).
  - Wired Lens shortcut button in Material 3 Expressive `PetalExpressiveGlanceWidget.kt`.

- **⚡ High-Speed Torrent & Magnet Engines**:
  - Built multi-engine Torrent & Magnet downloader supporting **1DM High-Speed P2P Engine (Official)**, **In-App Native WebSeed & Magnet Streamer**, and **System Downloader**.
  - Added real-time Torrent Engine switcher in `PetalSettingsScreen.kt` under *Data & Storage*.
  - Automated interception of `magnet:` links and `.torrent` files across all webpage downloads.

- **🎨 Dynamic Morphing Background & PetalSlider**:
  - Added periodic ambient background shape rotation with user-controlled timer loop (`0 min` to `60 min`).
  - Integrated rolling scalloped `PetalSlider` for shape change intervals in `PetalSettingsScreen.kt`.

- **🔊 System-Wide Haptics (RvSystem-Monitor & Ever-Haptics Inspired)**:
  - Integrated tactile haptic feedback across WebView scrolling (`Pattern.TICK`), bottom navigation, address bar actions, site controls, and predictive back gestures (`PetalHapticEngine.java`).

- **🔤 Custom Font Tweaker Real-Time Variations**:
  - Fixed font variation settings (`toVariationSettings()`) in `Type.kt` so custom `.ttf`/`.otf` font files respond immediately to Weight, Width, Optical Size, Slant, and Roundness sliders in real-time.

- **🔐 App Lock & Profile Security Dialogs**:
  - Added Material 3 Expressive Biometric & Passcode choice dialogs for App Lock and Profile Lock pages matching app-wide predictive back gesture transitions.

### v1.9.3

- **🎨 UI/UX & Predictive Back Polish**:
  - Aligned Predictive Back animations and Depth Blur 1:1 with `RvSystem-Monitor` and `PixelPlayer`.
  - Applied 24.dp depth blur exclusively to background screen (`isBehind = true`) while keeping foreground card crisp (`blur = 0.dp`) with Material 3 Emphasized scaling (`1.0 → 0.85`), 32.dp corner clipping, 16.dp drop shadow, and horizontal slide offset.
  - Rendered real live `PetalHomeScreen` in background with 24.dp depth blur during in-flight back gestures (zero synthetic previews).
  - Added user settings toggles for Predictive Back Animations and Depth Blur Effects in *Accessibility & Display Options*.

- **🐛 Bug & Layout Fixes**:
  - Refactored legacy Java `CustomRedirectsDialog.java` to pure Kotlin Compose Material 3 Expressive implementation in `CustomRedirectsDialog.kt`.
  - Fixed **Auto Clear Data on Exit** to execute reliably on activity finish/stop and default to clearing Browsing History, Cache, and Cookies if no individual sub-filters are checked.
  - Enabled **Force Dark Webpages** dynamically with AndroidX `setAlgorithmicDarkeningAllowed`, `setForceDark`, and `DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING`.

- **⚡ Media & Picture-in-Picture Enhancements**:
  - Fixed background audio & video playback by injecting `PetalMediaBridge.MEDIA_JS_INJECTION` to override Page Visibility API (`hidden: false`, `visibilityState: 'visible'`).
  - Enabled native OS Auto-PiP mode transition on Android 12+ (API 31+) with `setPictureInPictureParams(setAutoEnterEnabled(true))` on media play.

- **📦 Build & Infrastructure**:
  - Added `androidx.webkit:webkit:1.12.0`, `androidx.media:media:1.7.0`, `androidx.media3:media3-session:1.4.1`, and `androidx.media3:media3-exoplayer:1.4.1` dependencies to `build.gradle`.

### v1.7.8

- fix: Refactored website link & image context menus into Material 3 `BottomSheetDialog` (`PetalLinkContextMenuSheet.kt`) to resolve invisible touch interception overlays and wired all actions.
- fix: Resolved ML Kit image recognition background thread state mutation issues by posting callbacks to main thread (`PetalImageScanResultSheet.kt`).
- new: Integrated `RvSystem-Monitor` haptic feedback framework (`LocalHapticEnabled`, `LocalVibrationIntensity`, `rememberHapticOnClick`, `hapticClickable`).
- ui: Aligned icons on Clear Browsing Data screen (`PetalDeleteScreen.kt`) with primary category icons in Settings.
- fix: Bound `TabThumbnailCache` eviction strictly to tab closure and ensured faithful home screen snapshot rendering in tab manager.
- fix: Audited Compose layout braces and fixed Kotlin compiler errors in `ChromeAccountSyncScreen.kt` and `PetalTheme.kt`.

### v1.7.7

- new: Made Google Sans Flex permanent as **Petal Signature** font and added support for loading custom `.ttf`/`.otf` font files directly from device storage.
- new: Introduced custom font tier customizer with independent **Display**, **Headline**, and **Body** level tabs and expressive `PetalSlider` controls for Weight, Width, Optical Size, Slant, and Roundness.
- fix: Updated search bar hint to *"Search or type URL"* across homepage searchbar, omnibox, and widgets.
- fix: Hidden frequently visited shortcuts row on blank home tabs, displaying them only when opening the omnibox from an active website.
- fix: Fixed Java-Kotlin SAM interop for `onDeny` callback in `PetalPermissionDialogBridge` site permission prompts.

### v1.7.6

- new: Reimagined `PetalSearchGlanceWidget.kt` with Material 3 Expressive morphing shape containers (Clover, Scallop, Burst, Cookie) rendered dynamically using `RoundedPolygon` and canvas bitmap drawing.
- new: Integrated `PetalAboutDeveloperSheet.kt` and `PetalAboutDeveloperBridge` featuring expressive hero cards, developer metric badges, tech stack chips, and community hub links.
- fix: Fixed window inset propagation in `PetalTabSwitcherSheet.kt` and `PetalTabGridSwitcher.kt` so `ExpressiveHeader` seamlessly includes status bar top padding.

### v1.7.5

- new: Integrated `Util1DM` utility class to automatically detect 1DM / 1DM+ / 1DM Lite download managers and hand off downloads, headers, cookies, and user-agent without UI changes.
- fix: Resolved Kotlin build errors in `PetalAiHubBridge.kt`, `PetalAiHubScreen.kt`, and `PetalTabGridSwitcher.kt`.

### v1.7.4

- fix: Audited and balanced Compose layout braces in `PetalTabGridSwitcher.kt`, resolving outer `BoxScope.align` and syntax parser cascade errors.
- fix: Created `PetalAiHubBridge.kt`, corrected `Settings_Activity` references in `PetalAiHubScreen.kt`, and aligned `performResearch` callback signature in `PetalSettingsScreen.kt`.

### v1.7.3

- new: Added dedicated **Petal AI & API Keys Hub** category in `PetalSettingsScreen.kt` for configuring Gemini, OpenRouter, OpenAI, Grok, and Groq API keys, model selections, and live connection testing.
- new: Reimagined `PetalAiHubScreen.kt` as the unified home for Petal Native AI (Deep Research, AI search) and web AI tools directory, complete with API key status and 1-tap quick launchers.

### v1.7.2

- new: Upgraded Tab Manager (`PetalTabGridSwitcher.kt`), Chrome Flags (`PetalChromeFlagsScreen.kt`), and Extensions (`PetalExtensionsScreen.kt`) to support Material 3 `ExpressiveHeader`.
- fix: Fixed double status-bar top padding issue on Bookmarks screen (`PetalBookmarksScreen.kt`).

### v1.7.1

- fix: Stored user custom profile picture permanently in `context.filesDir` to prevent deletion when clearing app cache or background tasks (`GoogleAccountManager.kt`).
- new: Built interactive profile picture cropping modal sheet `PetalAvatarCropSheet.kt` with pinch-to-zoom, pan, rotation, and live circular mask preview.

### v1.7.0

- new: Bound `PetalHapticFeedback` to Compose `LocalHapticFeedback` in `PetalTheme.kt` for universal tap feedback across all components.
- fix: Connected `PetalHapticEngine` with Android Accessibility (`TalkBack` Touch Exploration) and OS system haptic settings.
- fix: Added instant vibrator cancellation before triggering new patterns so pattern testing in Settings switches immediately.
- fix: Expanded multi-tiered waveform fallbacks for all haptic patterns on devices lacking hardware primitive composition.

### v1.6.9

- fix: Increased homepage greeting tagline `maxLines` to 4, set `fontSize = 15.sp`, and pruned taglines to prevent ellipsis (`...`) truncation on all screen sizes (`PetalHomeScreen.kt`).

### v1.6.8

- fix: Added `androidx.compose.ui.unit.sp` import to `ExpressiveHeader.kt` for proper `TextUnit` extension resolution.

### v1.6.7

- fix: Resolved Settings unresponsiveness by removing legacy `nestedScroll` connection from `PetalSettingsScreen.kt`.
- fix: Fitted User Accounts header title cleanly onto a single line without truncation across all screen widths (`ExpressiveHeader.kt` & `ChromeAccountSyncScreen.kt`).

### v1.6.6

- fix: Restored synchronous initialization of user profile avatar, name, and preferences on app restart (`BrowserActivity.java` & `AccountViewModel.kt`).

### v1.6.5

- new: Integrated `ExpressiveHeader` across Settings overview and subpages with dynamic category titles and subtitles.
- fix: Resolved double-padding layout issue on Clear Browsing Data page (`PetalDeleteScreen.kt`).
- new: Enforced 1 single line for title and 1 single line for subheading on User Profile & Accounts page.

### v1.6.4

- new: Uniform Material 3 ExpressiveHeader alignment across History, Bookmarks, and Downloads pages.
- new: Chrome-style tab card UI (0.68 aspect ratio, 18dp rounded corners, circular close X button, active accent outline).
- new: Disk-persistent tab preview thumbnails (`petal_tab_thumbnails/{tabId}.png`) that restore on app relaunch.
- fix: Restored OS predictive back edge swipe navigation on web pages via 24dp system gesture exclusion margins.
- new: One-time version update welcome greeting tagline ("Welcome to Petal v1.6.4! 🎉") on home screen after updating.

### v1.5.3

- fix: Improved tab preview thumbnail cache management with safe memory recycling and disk persistence.
- improved: Replaced image cropping with proportional fit scaling to display full uncropped web page previews.
- new: Added Chrome-style bouncy zoom scale-up animation when tapping on tab grid cards.

### v1.5.2

- removed: Removed sync encryption password option from User Profile & Accounts page.
- new: Added support for dedicated app password lock alongside biometric fingerprint authentication options.
- new: Integrated Material 3 Expressive masked shape icons and animations (inspired by Monogram Android & Zenith) for custom app password security.

### v1.5.1

- fix: Solved WebView modal, popup, and carousel black overlay misrendering by explicitly disabling force dark mode & algorithmic darkening.
- new: Added "View All Changelogs" history sheet in App Updates settings (ported from Zenith).
- improved: Upgraded UI containment styling across settings screens to pure Material 3 Expressive UI specifications.

### v1.5.0

- imported: Updated PetalTabGridSwitcher with BackHandler context resolution.
- new: Full Material 3 Expressive UI containment redesign.

### v 24 (WIP)

- improved: default standard profile settings
- improved: AdBlock download
- improved: snackbar with clickable link
- improved: download of blob:/data:-files
- improved: app bar
- improved: search bar
- improved: gesture settings
- improved: menu settings
- improved: search providers
- updated: WIKI + help links in app
- updated: translations


### v 23 Harriet Tubman

*Harriet "Moses" Tubman (1822 – 1913) was an American abolitionist and social activist.*

- fix: Unable to access full tab list #1282
- fix: Hide URL-Bar/Status Bar when Scrolling #1278
- fix: auto hide/show control bar when scrolled up/down #1283
- fix: Highlight entire URL #1269
- fix: Day Mode hides clock, battery and notifications #1270
- fix: Behavior/UI settings crashing #1265
- fix: Update JavaScript alert dialogs #1279
- fix: go back not work when redirect happens #1281
- fix: Cookies Getting Disabled #1273
- fix: Tracking protection needs improvment #1266
- fix: Add ability to view image alt text #1000
- fix: toasts not showing entire URL
- updated: translations
- improved: input of search layouts
- improved: more snackbars instead of dialogs
- removed: redundant settings (autofill, custom javascript)
- removed: StartSite
- new: menu settings


### v 22 Die Edelweißpiraten

- fix: opening UI-settings crashing
- fix: some dialogs closing when clicking profile icon
- fix: profile-icon showing wrong icon
- fix: profile settings
- improved: using of profile button
- improved: redirects control from FastToggleDialog
- improved: layout -> removed tab-button -> opened tabs now in overview
- improved: layout -> using outlined style where possible for better contrast
- new: snackbar instead of dialog on some places
- new: gestures -> open startsite,bookmark, history
- new: option to load default profile on start/always/never (via FastToggleDialog)


### v 21 Astrid Lindgren

- fix: edit text dialogs
- fix: userAgent
- fix: not detecting saved websites in some cases
- fix: not switching to default profile, when going back in history
- fix: Themed icon issue #1251
- improved: moved enable/disable switching to standard profile to top of FastToggleDialog
- improved: theme declaration in manifest
- new: snackbar on download complete


### v 20 Rudi Dutschke

- new: disable webview diagnostic data
- new: set render priority to high
- new: enabling/disabling switching always to standard profile (also in in FastToggleDialog)
- new: enabling/disabling redirects in FastToggleDialog
- new: sort FastSearchDialog alphabetically
- new: some app settings in FastToggleDialog
- fix: removed unused code
- fix: third Party Cookies
- fix: tracking-URL-dialog
- fix: HTTP-dialog
- fix: removed unused strings
- fix: (workaround): Codeberg JavaScript error only on FOSS Browser #1231


### v 19.1 Henry David Thoreau

With this release, it is possible to save settings separate for each website. Look at the WIKI for more Infos: https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki

- new: save settings per website
- new: Error-dialog, when downloading Blob-files
- new: gestures -> toggle redirects, switch to standard profile, open downloads
- fix: not showing most recent entries in overview in some cases
- fix: removed a lot of old code
- fix: Sequences of Java Session Id's appears in URL #1187
- fix: "show on start" doesn't show start #1206
- fix: f-droid information shows old link to github #1204
- fix: Navigation bar not hidden in fullscreen #1099
- fix: Opening URL using window.open() uses default browser instead of Foss Browser #1165
- fix: Input box blocked by the keyboard in the screen #1207
- fix: Themed Icon Missing #1157
- fix: custom search, when containing "#"
- fix: Update Screenshots On F-Droid #1214
- updated: WIKI
- updated: layout
- updated: settings
- updated: translations
- updated: build libraries
- removed: "Open downloads" popup dialog is annoying #1196
- removed: play audio in background, due to Android limitations
- removed: OLED-theme


### v 18.1 "Die rote Zora"

With the release of Android 15 Google introduced a feature called "window insets". Because of that feature there were many layout issues, mainly concerning the system navigation bar. I had to rewrite some layouts. The list of opened tabs, the overview and the search are moved in sidebars.

- layout changes
- updated translations
- some minor code improvements
- fix: tab issues


### v 17 "Michail Alexandrowitsch Bakunin"
- upgrade: Android target SDK
- upgrade: dependencies
- update: translations
- fix: redirecting urls
- fix: minor layout issues
- fix: open links in background #1184
- fix: link on readme.md (thanks to @[Artemka](https://codeberg.org/Artem13327))
- fix: wrong menu contents in landscape orientation #1186
- fix: rename Disable Profiles -> Custom #1178
- fix: Update README #1176
- fix: “Close Tab” icon too small #1156
- fix: crash when clicking overflow with empty URL
- fix: crash with missing notification permission
- fix: Themed Icon Missing #1157
- new: switched from deepl to fairtranslate
- new: edit custom redirects

### v 16 "Alexander Berkmann"
- new: tracking-URL-popup
- fix: context menu in tab overview
- fix: onRenderProcessGone
- fix: http-dialog
- fix: dialogs with long file names
- fix: tab-layout in overflow menu
- updated: translations

### v 15 "Kabale und Liebe"
- new: auto deny cookie-banners (@https://github.com/woheller69/browser)
- new: sort startsite and history by domain
- new: highlight domains in lists and menus
- new: highlight search term
- fix: layout and colors
- fix: Error downloading blob-files (@https://github.com/woheller69/browser)
- fix: Downloading files that requires Referer header #950
- fix: Display errors in dark theme? #1143


### v 14 "Katjuscha" (Protagonistin in Leo Tolstois Auferstehung)
- fix: redirect custom urls
- fix: Back navigation is flawed #1121
- fix: Open in default app #1134
- fix: App crashes when opening settings #1128
- fix: layout/UI improvements
- fix: Deleting duplicated bookmark deletes both #1130
- fix: v13 system theme - colors [re: 1105; 1119] #1126
- fix: profiles -> wildcard support #1133
- fix: Suggestion: Long-press on bookmark icon to save #1140
- fix: Add initial intro #1129
- fix: Splash screen should adapt to theme #1142
- new: simplified receiving text dialog
- updated: translations
- updated: libraries


### v 13 "Friedrich Hecker"
- switched to Google rounded Material You Icons
- fix: improvements in backup/restore
- fix: improvements in custom redirects
- fix: layout improvements
- fix: open deepl.com in normal tab
- fix: lint issues
- fix: theme issues #1105
- fix: WIKI-links
- fix: cleanup strings
- fix: updated translations
- fix: backup on exit
- fix: crashes related to cookie manager #1118 #1111 #1115
- fix: tabs gets multiplied #1117
- fix: Add Initial intro screen #1129
- new: icons in overview menus
- new: fast search on websites (long press search icon in toolbar)
- new: AdBlock -> add custom domains

### v 12 "Gandhi"
- fix: Open Downloads after download complete
- fix: Custom search engine UI/UX #1113
- fix: Cannot import custom Bookmarks #1109
- fix: open links in background
- fix: notification permissions
- fix: infinite loop verification on gitlab.com #1100
- new: backup on exit (optional)
- new: fast search on websites (long press search icon in toolbar)


### v 11 "Bertha von Suttner"
- new: third party cookies control
- new: stop-loading on progress indicator click
- fix: Writing to clipboard from JavaScript doesn't work #853
- fix: recreation of activity on ui-mode change
- fix: translate text from context menu with deepL
- fix: crash on start (A14)
- updated: translation
- updated: layout
- updated: build libraries


### v 10.0 "Clara Zetkin"
- moved repo from Github zu Codeberg
- moved from crowdin to weblate
- fix: cookie/profile settings lost on tab change #1095
- fix: profile settings lost, when going back in history
- fix: Not really a bug... #1075
- fix: Menu Dialog tweaks #1086
- fix: error warnings #1092 and #1082
- fix: removed "open link with" from menus, since it does the same like sharing #1089
- fix: HTTPS or HTTP dialog only asked one time per session #1083
- fix: Gestures: added gestures for "Open Settings" and "Quit app" #1087
- fix: Custom Redirects to sites that don't start with "www." #1097
- fix: lint issues
- new: translate text from context menu with deepL
- updated translations


### v 9.11 "Traven"
- new: context menu in tab overview
- new: icons in menus
- new: delete entries from search
- new: http-warning before loading
- fix: layout in menus
- fix: menus
- fix: onReceivedHttpAuthRequest password showing #1069
- fix: download names #1071


### v 9.10 "Durruti"
- new: Clipboard record preference thanks to @Bnyro
- new: show url on link menus or when clicking menu title
- new: show urls in lists
- new: tab number in overview
- fix: refactored settings
- fix: layout issues on small devices #976
- fix: Password field for protected sites shows entries #1039
- fix: youtube redirects
- fix: onReceivedError
- fix: Settings refactor thanks to @Bnyro
- fix: OLED theme issues #1052


### v 9.6
- removed: redirection for Instagram
- fix: Redirect error from YouTube #991
- fix: loading images fit screen width
- fix: cookie manager, when going back in history
- fix: buttons not reachable on some dialogs
- fix: Auth-request dialog
- fix: download dialog
- fix: startsite is now the wiki #997
- fix: crash when open downloads, while default file manager not installed
- fix: issues with the file name when downloading thanks to @Bnyro
- fix: kill the app on pressing quit thanks to @Bnyro
- fix: theme settings
- fix: suppress DRM notice #1019
- fix: profile icons - red for attached profiles #999
- fix: Youtube videos in full screen mode cropped #1015
- fix: Twitter-to-Nitter redirector matches some unrelated urls #1022
- fix: & not escaped in search URL #959
- fix: lint issues
- new: circular progress - press to stop loading
- new: Swedish translation
- new: material dialogs in the preferences thanks to @Bnyro
- new: monochrome icon for Android 13+ thanks to @Bnyro
- new: custom redirects thanks to @Bnyro
- new: Custom error pages  thanks to @Bnyro


### v 9.5
- fix: can't open URL from outside, if it was closed before
- fix: Active button colors on OLED theme #1006
- new: hebrew translation


### v 9.4
- new: "post on website" in sharing menu
- new: Serbo-Croation translation
- new: Support app split screen #937
- fix: Restored functionality to proceed according to user selection #941
- updated: translations
- updated: build libraries
- removed: swipe to reload -> use gestures instead

### v 9.3.1
- fix: Half screen after orientation change #919
- fix: not all menu items accessible in landscape orientation
- fix: Download dialog does not decode multibyte file name #917
- fix: File name is not shown in the download prompt #916
- fix: Browser crashes when switching embedded video to full screen #925
- fix: Persistent swipe issues #924

### v 9.2
- fix: crash when sharing links #883
- fix: no password field on webserver authentication #891
- fix: some swiping issues #899 by Lakjdf
- fix: support for intent scheme URLs again #898 by Lakjdf
- fix: refactored code to make it much more readable #896 by T8RIN
- fix: share link
- fix: Home-screen links improvment #906
- fix: no password field on authentication dialog #891
- updated: layout and user experience
- updated: settings for night view
- updated: settings for gestures
- updated: settings for editing profiles #909
- updated translations: *Now 21 languages are fully supported!*

### v 9.1
- fix: removed unnecessary toasts in fastToggle
- fix: Browser crash on search with "%" #861
- fix: progressbar visible when searching
- fix: UI of FastToggleDialog #872
- fix: UI of edit/save as dialog #873
- fix: https warning #863
- fix: Images not displayed #881
- new: gesture to copy link to clipboard #868
- new: copy link to clipboard in context menu #878
- new: switched license to APGL
- new: redirect YouTube/Instagram/Twitter links
- new: show number of tabs in overview

### v 9.0
- fix: popup menu in Overview
- fix: searchbar not hiding in back pressing #852
- fix: edit favorites layout not scrolling #854
- fix: open external link shows Overview #858
- updated: translations

### v 8.9
- fix: some webpages show half the screen #817
- fix: Shortcut on the home screen #830
- fix: crash on first start #829 #820
- fix: Settings button does not open settings #823
- fix: cannot connect to some webservices #818
- fix: default user-agent is now received from installed webview
- fix: long press actions on toolbar buttons #866
- fix: https warning #863
- fix: Images not displayed #881
- updated: translations
- new: moved tab-dialog in overview
- new: permanent night mode (optional) #825
- new: help buttons in most important UI-elements

### v 8.8
- new: Wiki integrated in settings and menus
- new: notification when playing audio on background #800
- new: gestures
- fix: mailto and other intents not working
- fix: button "reload " is hidden #803
- fix: play audio in background #800

### v 8.7
- fix: dark theme in settings activities
- fix: night mode (webView)
- new: play audio on background
- new: Quick toggle to keep screen on
- new: gesture settings for long pressing
- new: open links in background
- new: swipe through menu
- updated: F-Droid (screenshots, description)
- updated: Privacy Policy
- updated: translations (17 languages supported!)
- improved: MaterialYou theme
- improved: search in history, bookmarks, ...
- improved: close open tabs dialog after making selection

### v 8.6
- skipped -

### v 8.5.1
- fix: camera issue #729
- fix: profile icon in toolbar #728
- fix: cookie settings when switching tab
- fix: stay at night mode, when openeing bookmark
- fix: close open tabs window after making selection #746
- new: restart and reload tabs
- new: Material You Design #726
- new: OLED dark theme Feature #742
- new: FOSS Browser in context menu of marked text #723
- new: remember night mode per site #669
- new: adding "copy link" button to download dialog #733
- new: remember night mode, when opening new tabs
- new: gesture: open start site
- new: keep screen on
- improved: set favicons (thanks to @woheller69)
- updated: translations
- updated: build libraries

### v 8.4
- new: camera use (thanks to @woheller69)
- new: microphone use
- new: webRTC support
- new: DRM protected video playback (thanks to @woheller69)
- new: support of encrypted backups (thanks to @woheller69)
- new: profiles instead of whitelists
- new: restore tabs on restart (optional)
- new: restore tabs when killed by system (optional) (thanks to @woheller69)
- fix: AutoComplete TextView (thanks to @woheller69)
- updated: setting screens

### v 8.3
- fix: Swipe to reload not working sometimes #654
- fix: Restoring bookmarks does not work properly #653
- fix: some force closes #642 #643
- fix: gestures and text-edit-scrolling collide #633
- fix: Toolbar hides, but reappears if page is refreshed #660
- fix: minor fixes and UIredirects webview
- new: save dektopMode, Javascript, domStorage also for history items
- removed: PlayStore support
- updated: translations

### v 8.2
- fix: chip desktop, javascript, DOM should not be visible in settings when editing filter names (thanks to @woheller69)
- fix: Sometimes Favicon is stored several times in database (thanks to @woheller69)
- fix: permission problems
- fix: download problems
- new: edit startSite and history items
- new: Privacy Enhancement #602 (thanks to @woheller69)
- new: choose preferred theme
- updated: Edit filter names (thanks to @woheller69)
- updated: backup preference

### v 8.1
- new: edit bookmark-url (thanks to @woheller69)
- new: Reload on swipeDown when at top of webpage (thanks to @woheller69)
- new: Added GlobalPrivacyControl (thanks to @woheller69)
- new: Remove device info from useragent, use prefixes like DuckDuckGo browser (thanks to @woheller69)
- new: favicons in bookmarklist (thanks to @woheller69)
- new: option to select blocked content
- updated: do not track (thanks to @woheller69)
- updated: translations
- fix: sorting of tabs in overview
- fix: DOM-storage description in settings
- fix: apply settings on back/forward navigation
- fix: Ad-Blocking also blocks "social" media
- fix: open link externally

### v 8.0
- new: toggle mobile/desktop mode (thanks to @woheller69)
- new: auto update of AdBlock-hosts (thanks to @woheller69)
- new: save desktop mode/javascript/DOM content settings in bookmarks (thanks to @woheller69)
- new: show colored bookmark icon when editing bookmark category (thanks to @woheller69)
- new: Use settings stored in bookmark when a bookmark is found in search (thanks to @woheller69)
- new: Show icon source of item (start page, bookmarks, history) in search (thanks to @woheller69)
- new: save form data/use autofill service in settings
- fix: correct icon colors when importing bookmarks (thanks to @woheller69)
- fix: content hidden by toolbar
- fix: opening new tabs on older Android versions
- fix: Dark mode not applied to all tabs
- fix: toolbar input handling

### v 7.5
- new: right to left layout support (thanks to @M3hdiRostami)
- new: Farsi (Persian) language support (thanks to @M3hdiRostami)
- updated: AdBlock
- updated: AppIcon
- fix: New bookmarks are not shown in red-filtered bookmark view
- fix: Application crash when trying to watch video in full screen mode
- fix: showing keyboard when leaving fullscreen mode
- fix: interaction with url in address line
- fix: tab handling
- fix: toolbar input handling
- even more fixes

### v 7.4
- removed: app shortcuts
- fix: keyboard not showing on large input fields
- fix: app crash when displaying overview on start
- fix: app crash when showing some dialogs
- fix: clearing data
- fix: open fallback urls
- fix: crashing on fullscreen on older Android versions

### v 7.3
- updated: translations (now 16 languages are supported!)
- updated: build and theme libraries
- updated: AdBlock-hosts
- updated: Material Components
- updated: settings activities
- updated: tab handling
- removed: multi-window support
- removed: theme settings
- new: support of: open target="_blank"
- fix: Opting out of metrics collection
- fix: export bookmarks
- fix: tab handling
- fix: apply javaScript, cookies and DOM content whitelists
- fix: switching between system day/night mode
- fix: dialogs in landscape
- fix: alphabetical sorting of overview entries
- fix: search in toolbar
- fix: many other small fixes

### v 7.2
- fix: zooming on websites
- fix: opening new tab when restarting
- new: close search on site by back pressing

### v 7.1
- removed: ability to save password within bookmark
- updated: AdBlock hosts
- updated: translations
- updated: bookmark management (sorry for removing password saving)
- updated: overview, menus and dialogs
- fix: searx.me search
- new: hide overflow button
- new: whitelist for DOM content
- new: backup/restore bookmarks as html
- many stability improvements -> removed ca. 5000 lines of unnecessary code!)

### v 7.0
- new: F-Droid description
- updated: many translations (BIG THANKS TO ALL CROWDIN translators)
- fix: leave video fullscreen with back key
- fix: create windows inside webView

### v 6.9
- fix: custom search engine
- new: Italian translation
- new: custom user agent
- new: removed storage permission for Android 10+
- updated: some translations
- updated: Android libraries

### v 6.8
- new: search in all overview entries
- new: Blank target href open a new tab
- new: removed device model and build number from user agent string
- new: follow system theme
- new: Amoled theme
- new: moving to AndroidX libraries
- new: favorite settings
- updated: handling of ssl-errors
- updated: adblock hosts
- updated: themes
- updated: Turkish translation
- updated: Czech translation
- updated: Brazilian translation
- updated: Russian translation
- updated: settings (especially filter settings)
- updated: saving locations of backups and screenshots
- removed: tinting of toolbar
- removed: open links in background

### v 6.7
- new: backup and restore settings
- fix: saving on startsite
- fix: toolbar not showing title
- fix: making backups

### v 6.6
- fix: storage permission problems
- fix: location permission problems
- fix: lint issues
- fix: sort startsite by time
- fix: favorite site not loading on start
- fix: inputs not loading
- new: add to startsite from link context menu

### v 6.5
- new: more font sizes (thanks @pbui)
- new: ECOSIA search engine
- new: open dialogs always expanded
- fix: exclude notifications from recent apps
- fix: some popup dialogs not opening
- fix: hide nav button

### v 6.4
- new: FAQ site (thanks @HarryHeights)
- new: long click on tab preview to close
- new: add shortcuts to home screen (long press overview entry)
- new: dynamic shortcuts: two last opened websites
- updated: help dialog
- updated: Portuguese translation (thanks @smarquespt)
- updated: French translation (thanks @franco27)
- updated: adblock domain list
- improved: Overview
- improved: rendering speed
- fix: file upload
- fix: input box not showing on some devices
- fix: hide navigation bar in fullscreen mode
- fix: can't go back in history in some cases

### v 6.3
- new: Add Cookies support for download function
- new: option to enable Save-Data header (thanks @SkewedZeppelin)
- new: set blank start site
- new: fast scroll on long lists
- new: show/hide tab preview
- new: animations when showing/hiding views
- new: Qwant search engine (thanks @Tobiplayer3)
- new: option to open new tab instead of exiting app
- new: Ukrainian translation (thanks @Roman Babiy)
- new: Turkish translation (thanks @ali-demirtas)
- fix: UI and minor issues
- updated: long press menu on websites
- updated: Help dialog
- updated: Polish translation (thanks @gh-pmjm)
- updated: Dutch translation (thanks @Vistaus)
- updated: Taiwan Trad. Chinese translation (thanks @elmru)
- updated: Portuguese translation (thanks @smarquespt)
- updated: Overview dialog
- updated: FastToggle dialog

### v 6.2
- new: advanced gesture settings
- new: show overview on start
- fix: light settings theme

### v 6.1
- new: overview instead of startPage
- new: order and filter bookmarks
- new: edit url of bookmark
- new: open favorite website on start
- new: Code of conduct site (thanks @HarryHeights)
- new: Privacy declaration (thanks @HarryHeights)
- updated: adBlock hosts list
- updated: help dialog
- updated: French translation (thanks @Hellohat)
- updated: Portuguese translation (thanks @smarquespt)
- removed: loginData (use bookmarks instead)
- fix: upload files
- fix: not clearing indexed databases on exit
- many more fixes and improvements

### v 6.0
- updated: adBlock hosts list
- updated: Polish translation (thanks @gh-pmjm)
- fix: problems with characters like ä, ü, ö
- new: Taiwan Trad. Chinese translation (thanks @elmru)
- new: Italian translation (thanks @EnricoMonese)
- new: Portuguese translation (thanks @Sérgio Marques)
- many more fixes and improvements (thanks @BO41)

### v 5.9
- updated: Chinese translation (thanks @YC L)
- updated: Russian translation (thanks @Vladimir Kosolapov)
- new: Polish translation (thanks @gh-pmjm)
- new: Dutch translation (thanks @gHeimen Stoffels)
- new: adaptive icon

### v 5.8
- new: "do not track" header
- new: + button in tab preview
- new: save websites as PDF
- new: show link in context menu
- fix: not applying cookie whitelist when switching tabs
- fix: scrolling issue
- fix: screen rotation issues
- updated: UI + dialogs
- updated: Spanish translation (thanks to @Herman Nuñez)
- updated: Chinese translation (thanks to @smallg0at)

### v 5.7
- new: delete indexed databases and local web storage
- new: Spanish translation (thanks to Herman Nunez)
- new: confirmation dialog before making backup
- new: delete separate lists (Startpage, history, ...)
- new: show unsecured connections and try reloading secure
- new: search engines (Startpage DE, Searx)
- new: notification when download or screenshot complete
- new: block DOM content
- improved Chinese translation (thanks: lishoujun)
- removed: Snackbar (replaced with toasts)
- removed: request desktop site
- removed: build in file manager
- fix: some strings (thanks: gr1sh)
- fix: some urls opening search results

### v 5.6
- new: increase font size
- new: close all websites from notification
- new: desktop mode in "Fast toggle dialog"
- improved: link sharing
- improved: light theme
- improved: UI
- fix: some force closes

### v 5.5
- new: option to disable confirmation dialogs on exit (thanks: element54)
- improved: light theme
- improved: startpage
- fix: dark background on some websites
- fix: ok button on "Fast toggle dialog"
- fix: toolbar not showing title
- fix: back handling

### v 5.4
- new: new app name "F|L|OSS Browser"
- new: dark and light theme
- new: startpage with all contents
- new: full support for cookie whitelist
- new: more options for fullscreen browsing
- new: more options visibility Navigation Button
- new: night mode only while browsing
- updated: help dialog
- removed: different light themes
- fix: double entries in history
- fix: toolbar does not work on start
- fix: keyboard issues in some cases (thanks: element54)

### v 5.3.1
- new: initial support for cookie whitelist (fast toggle)

### v 5.3
- improved: tab opening/removing
- improved: back handling
- improved: enter/exit fullscreen
- other improvements and bug fixes

### v 5.2
- new: decide which tab to open on start
- new: backup complete data and settings
- new: double tap "Navigation Button" to hide
- new: option to disable history (settings and fast toggle)
- new: option to hide/show "Navigation Button"
- new: change position of "Navigation Button"
- new: moved menus in bottom dialog
- new: Baidu as default search engine for Chinese users
- new: Indonesian translation (thanks: Secangkir Kopi)
- changed: backups on root of sd-card
- updated: Help dialog (ENG, DE)
- improved: applying settings from fast toggle dialog
- improved: Chinese translation (thanks: lishoujun)

### v 5.1
- new: Chinese translation (thanks: lishoujun)
- new: close current tab in menu
- new: share text to browser
- improved: hide/show toolbar
- fix: night mode (now in UI settings)
- fix: force close when clicking direct download link
- fix: screenshot, when started via holder service
- fix: some strings
- fix: possible force close on Android > Nougat

### v 5.0

With this update "Ninja Browser" is used as base for "Browser". The 
concept remains the old: simple but powerful with a nice looking user interface. 
Main advantage of this step are a better implementation of tabs. Now you can open 
as many tabs as you wish. Also you have a new startpage. Please read the new "Help dialog" 
for more information.
    
- full oreo support

### v 4.5
- new: Chinese translation (Thanks: Jumping Yang)
- new: keyboard go button actions
- new: settings icon in toolbar (click for toggle/long click for main settings)
- new: possibility to temporally hide navigation arrows
- new: auto fill (authentication dialog)
- improved: tab behavior
- fix: load default settings on start
- fix: force close when sharing screenshot
- fix: not showing website title

### v 4.4
- fix: save screenshot
- fix: saving entries containing an apostrophe
- fix: option to restart app to apply swipe gesture
- new: http basic authentication
- improved: behavior of tab preview/behavior

### v 4.3
- fix: title of setting subpages
- fix: crash when selected list at startup
- new: swipe to switch tabs optional (settings/popup settings)

### v 4.2
- removed: backup/restore of passStorage
- fix: hiding keyboard after searching in lists
- fix: history - scroll to latest entry
- improved: code and UI
- new: swipe to switch tabs
- new: settings on different subpages
- new: auto orientation (sensor/device)
- new: offer restart to apply some settings
- new: theme support

### v 4.1
- removed: offline support (use screenshot for saving)
- removed: unnecessary permissions
- fix: reset menu after editing file name
- fix: overwriting of files while renaming
- fix: several code improvements
- fix: delete passStorage
- improved: layout close button
- improved: bundled Notifications
- new: Google encrypted search (override AMP-links)
- new: advanced backup/restore data

### v 4.0
- removed: donation link on Github
- removed: video thumbnail
- improved: file manager (icons, image preview)
- new: scroll to end of website arrow

### v 3.9
- removed: notification actions (to buggy)
- removed: direct opening a donation website&lt;br>
- fix: notification behavior (open multiple links)
- fix: force close on toolbar click
- fix: website partially not visible
- improved: refresh website (long click "history icon")
- improved: help dialog (menu -> more -> Help)
- new: switched to Picasso library to load images
- new: homepage setup (bookmark list)
- new: open links directly (optional)
- new: moved from activities to fragments
- new: bookmarks, readLater, history as startpage (settings)

### v 3.8
- removed: swipe to refresh
- fix: some random force closes
- fix: notifications on Lollipop/Marshmallow devices
- fix: do not scroll to list end after editing entry
- improved: taking screenshots
- improved: long click menu in lists
- new: menu -> reload website
- new: Russian translation
- new: show current url when starting editing mode in toolbar
- new: close tabs from tab preview
- new: set empty page as favorite

### v 3.7
- new: changelog after update
- new: long click on toolbar opens tab overview
- new: click on toolbar opens search/enter URL
- fix: toolbar swipe (lists)
- fix: hide navigation buttons
- fix: contributor title
- fix: always loading homepage when closing settings
- fix: cancel search on site
- improved: cookie settings
- improved: open links in new tab

### v 3.6
- new: always hide statusbar on fullscreen videos
- new: five tabs (@CGSLURP LLC)
- new: toolbar and button animations
- new: toolbar gestures in lists
- new: save link destination
- fix: download pictures
- fix: startpage on javascript whitelist
- improved: portrait layout
- improved: Adblock integration
- improved: Desktop site request
- improved: sorting of lists
- improved: code simplified
- improved: UI (colors, strings, layouts)
- updated: help dialog, introscreen

### v 3.5
- fix: keyboard issue
- new: request desktop site (@CGSLURP LLC)
- new: adblocker (@CGSLURP LLC)

### v 3.4
- improved: sorting of list entries
- fix: keyboard not open (landscape)

### v 3.3
- removed: swipe to navigate
- improved:opening urls
- fixed: pin screen not showing

### v 3.1 - v 3.2
- fix: possible crash an Android < Lollipop when opening settings
- fix: search from toolbar
- fix: crash when website title contains apostrophe

### v 3.0
- fix: F-Droid build error
- lint: disable missing translation
- updated: support libraries

### v 2.8 - 2.9
- new: video thumbnails
- fix: open links from readLater list (opened from notifications)
- fix: error message on start

### v 2.7
- fix: directory up icon
- fix: saving all entries lowercase

### v 2.4 - 2.6
- new: new file manager
- new: sub menus
- removed: toolbar long click
- improved: toolbar in lists
- improved: removed unnecessary code
- fix: sort readLater by date
- fix: sort by title (needs uppercase on first letter in the entries)
- fix: double entries in history
- fix: app icon color
- fix: open shortcuts
- fix: open links from notification
- fix: delete data on exit

### v 2.3
- new: search in lists
- new: no duplicate bookmarks
- improved: UI (lists, colors, buttons)
- improved: database model

### v 2.2
- new: about screen
- new: intro screen
- fixed: save website (passStorage): set title
- fixed: notification not dismissed

### v 2.1
- new: bundled notifications

### v 2.0
- improved: encryption of passStore databases
- fixed: F-Droid build error

### v 1.9
- new: encryption of passStore databases

### v 1.8.1
- fixed: f-droid build failure

### v 1.8
- improved: pin screen layout
- improved: sort dialog behavior
- improved: wsitched to xml icons
- removed: bookmark screen
- new: second tab
- new: set bookmark as start site (long click)
- new: cancel button on some dialogs
- new: cancel dialog when clearing whitelist
- fixed: force close (links without "/")

### v 1.7:
- fixed: F-Droid build error

### v 1.6:
- fixed: some strings
- improved: backup and restore databases

### v 1.0+ (first public release):
- improved: readLater notification (v1.1)
- improved: search results in German
- improved: settings screen
- improved: license dialog
- improved: minor ui/code tweaks
- new: websearch (from shared text)
- new: donate (settings)
- new: changelog (settings) (v1.4)
- new: sort lists by title and date (v1.5)
- fixed: navigation settings (v1.2)
- fixed: screenshot (v1.3)
### v3.7 — GeckoView 156 & Browser Polish

> *Stabilized the Gecko engine dependency and synchronized the latest scanner, popup, tab, diagnostics, and Material You improvements.*

#### Engine
- Kept the latest published compatible GeckoView `155.0.20260903215306` and Mozilla Android Components `155.0.1` coordinates so CI can resolve the release classpath.

#### Improvements
- Added smoother tab transitions and expressive Material 3 popup/undo surfaces.
- Improved omnibox selection, incognito tab continuity, and tab numbering.
- Fixed extension popup dismissal restoring browser touch/input focus.
- Kept diagnostic capture disabled by default while fixing crash handling.
- Continued the Petal QR Scanner, Google Lens, Quick Settings, and adaptive icon improvements.
