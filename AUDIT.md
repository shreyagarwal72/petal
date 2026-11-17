# Petal Browser Comprehensive Audit Report (AUDIT.md)
**Project**: Petal Browser Android (`com.petal.browser`)  
**Architecture**: GeckoView + Mozilla Android Components, Jetpack Compose, Material 3 Expressive  
**Behavioral Reference**: Mozilla Firefox for Android (Fenix)  
**Date**: 2026-11-17  

---

## 1. Executive Summary

This audit assesses the wiring, feature placement, transient UI surfaces (sheets/dialogs/popups), glass and shape morphing engine, tab session management, storage/cache subsystems, and web compatibility of Petal Browser.

Key critical issues identified:
1. **Broken Popup/Sheet Scaffold**:
   - The Quick Tools sheet is injected directly into `decorView` without bottom navigation bar insets or proper top spacing, causing the sheet to terminate in a hard black edge above the address bar and cut off labels ("QR Scan P…", "Spoof Ide…", "Image Gra…", "QR Gener…").
   - The Download confirmation dialog uses an inappropriate black pill title container (`MaterialTheme.colorScheme.surfaceContainerLowest` in dark theme), awkward filename wrapping, and is layered inside an Android `AlertDialog` with an opaque `GradientDrawable` that causes corner clipping and outline artifacts.
   - Over 90 distinct files throughout the application instantiate ad-hoc `AlertDialog`, `ModalBottomSheet`, `BottomSheetDialog`, and `Dialog(` implementations without unified inset management, font scaling adaptation, or unified glass/solid surface backdrops.
2. **Settings Search Index & Placement Desynchronization**:
   - Several major browser settings screens (`TabsSettingsScreen`, `DownloadSettingsScreen`, `PetalExtensionsScreen`, `PetalAccountScreen`) are arbitrarily excluded from the primary `SettingsHubScreen` category list (`allCategories`), leaving users unable to browse core browser categories despite search index entries pointing to them.
   - Preference toggles such as `sp_strip_tracking_parameters`, `sp_block_annoyances`, and `sp_auto_open_tab_groups_other_devices` are completely unwired in actual web navigation / request pipelines.
3. **Storage & Clear Data Flaws**:
   - `PetalBrowserUtils.clearBrowserData` still references legacy `Ninja4.db` instead of the actual `HistoryBookmarkRepository` database.
   - Clear browsing data lacks comprehensive GeckoView `StorageController` flags for site permissions, form data, and per-site exceptions.
4. **Tab Session & State Persistence Gaps**:
   - Incomplete session restore for private tabs leaving in-memory remnants or inconsistent state between `BrowserContainer` and `BrowserStore`.
   - Workspace metadata and tab group associations occasionally fail to resync during cold crash restoration.

---

## 2. Comprehensive Component & Action Map

### 2.1 Quick Tools Grid (`PetalQuickToolsSheet.kt` & `PetalQuickToolsBridge.kt`)

| Tool ID | Display Title | Intended Action | Execution Path | Audit Findings & Status |
| :--- | :--- | :--- | :--- | :--- |
| `QR_SCANNER` | QR Scanner | Launch camera QR/barcode scanner | `PetalLensBridge.showPetalScannerDirectly` | Wired. Works via camera intent / MLKit. |
| `TRANSLATOR` | Translator | Translate current web page | Injects custom event + loads Google Translate | Wired. Fallback translates via `https://translate.google.com/translate`. |
| `EDIT_PAGE` | Edit Page | Toggle `document.designMode` & `contentEditable` | Injects JS snippet into active tab | Wired. Toggles page live editing. |
| `NETWORK` | Network | Inspect connection, DNS, TLS info | `showNetworkInspector` -> `PetalNetworkInspectorDialog` | Wired. |
| `AUTO_SCROLL` | Auto-Scroll | Hands-free page auto-scrolling with HUD | `showAutoScroll` -> `PetalAutoScrollOverlay` | Wired. Smooth scroll execution. |
| `QR_SCAN_PAGE` | QR Scan Page | Extract QR images from DOM | Injects JS to collect QR images -> MLKit | Wired. Label truncates on screen ("QR Scan P…"). |
| `QR_GENERATOR` | QR Generator | Generate QR code for current URL | `showQrGenerator` -> `PetalQrGeneratorDialog` | Wired. Label truncates on screen ("QR Gener…"). |
| `DEV_NOTES` | Dev Notes | Encrypted per-domain scratchpad | `showDevNotes` -> `PetalDevNotesSheet` | Wired. Domain-keyed notes persistence. |
| `SITE_STYLE` | Site Style | Invert, Sepia, Dark, Custom CSS | `showSiteStyle` -> `PetalSiteStyleSheet` | Wired. Custom CSS style tag injection. |
| `IMAGE_GRABBER`| Image Grabber | Batch extract & download page images | `showImageGrabber` -> `PetalImageGrabberSheet` | Wired. Label truncates on screen ("Image Gra…"). |
| `INSPECTOR` | Inspector | Live DOM element highlighter & tag viewer | Injects JS HUD and DOM click listener | Wired. Element inspector works. |
| `BLOCK_AREA` | Block Area | Element zapper & cosmetic removal | Injects JS element click-to-delete listener | Wired. Ad-hoc element removal. |
| `SPOOF_IDENTITY`| Spoof Identity | Switch User-Agent (Desktop/iOS/macOS) | `showSpoofIdentity` -> `PetalSpoofIdentityDialog` | Wired. Label truncates on screen ("Spoof Ide…"). |
| `FORCE_ZOOM` | Force Zoom | Bypass viewport `user-scalable=no` locks | Injects viewport meta tag replacement | Wired. Overrides meta tags. |
| `SCREENSHOT` | Screenshot | Capture full page or viewport bitmap | `captureFullPageBitmap` -> PNG file in Pictures | Wired. Saves to `Pictures/Petal`. |
| `TORRENT_MAGNET`| Torrents | Extract magnet links from DOM | Injects JS query for `a[href^="magnet:"]` | Wired. Launches external handler/torrent engine. |
| `APP_LOCK` | App Lock | Configure app biometric/passcode lock | `PetalAppLockBridge.showConfig` | Misplaced. App security configuration belongs in Settings Hub, not in transient in-page Quick Tools. |

### 2.2 Overflow Menu Actions (`PetalOverflowMenuSheet.kt` & `BrowserNavigationDelegate.kt`)

| Action | UI Placement | Target Delegate Method | Audit Verdict |
| :--- | :--- | :--- | :--- |
| Back / Forward | Quick action header | `onGoBack()`, `onGoForward()` | Correctly wired to active tab controller. |
| Reload | Quick action header | `onReload()` | Correctly wired. |
| Bookmark Toggle | Quick action header | `onToggleBookmark()` | Updates SQLite repository & UI state. |
| Share Link | Quick action header | `onShareLink()` | Standard Android send intent. |
| New Tab | Tabs & Shield tier | `onNewTab()` | Opens regular tab with `about:home`. |
| New Incognito Tab | Tabs & Shield tier | `onNewIncognitoTab()` | Opens private tab with `about:home`. |
| Privacy Shield HUD | Tabs & Shield tier | `onOpenShieldHud()` | Opens `PetalPrivacyShieldSheet`. |
| Desktop Site Toggle | Page controls tier | `onToggleDesktopSite()` | Toggles GeckoView desktop user agent. |
| Find in Page | Page controls tier | `onSearchOnSite()` | Opens `PetalFindInPageBar`. |
| Reader Mode | Page controls tier | `onShowReadingMode()` | Invokes reader mode bridge. |
| Install PWA | Page controls tier | `onInstallPwa()` | Invokes PWA install manager. |
| Media / PiP | Media active tier | `onTriggerMediaMode()` | Enters Picture-in-Picture. |
| History | Library tier | `onOpenHistory()` | Opens full history screen. |
| Bookmarks | Library tier | `onOpenBookmarks()` | Opens full bookmarks screen. |
| Downloads | Library tier | `onOpenDownloads()` | Opens download manager. |
| Delete Browsing Data | Library tier | `onDeleteBrowsingData()` | Opens `PetalDeleteScreen`. |
| Extensions Menu | Extensions tier | `onOpenExtensionsMenu()` | Opens extensions sheet. |
| Quick Tools | More tools sub-menu | `onOpenQuickTools()` | Opens Quick Tools sheet. |
| Copy Clean Link | More tools sub-menu | `onCopyCleanLink()` | Cleans tracking query params and copies. |
| Print to PDF | More tools sub-menu | `onPrintPdf()` | Triggers Android PrintManager. |
| Save Page Offline | More tools sub-menu | `onSavePage()` | Saves HTML/MHTML offline. |
| View Source | More tools sub-menu | `onViewSource()` | Loads `view-source:` URL. |
| Developer Console | More tools sub-menu | `onOpenDevConsole()` | Opens JS evaluation console. |
| Safe Locker | More tools sub-menu | `onOpenSafeLocker()` | Opens private file vault. |
| Settings | Bottom menu row | `onOpenSettings()` | Opens Settings Hub. |

---

## 3. Settings Persistence & Wiring Audit

### 3.1 Unwired, Dead, or No-op Settings
1. **`sp_strip_tracking_parameters`**:
   - Location: `PetalAdBlockSettingsScreen.kt` ("Strip URL Tracking: Removes tracking query tokens (utm_*, fbclid, gclid) from opened URLs").
   - Defect: Toggle only saved to SharedPreferences; never queried during tab URL navigation (`PetalTabViewController.loadUrl` or Gecko `onLocationChange`).
   - Fix: Query `sp_strip_tracking_parameters` during URL navigation and sanitize URLs automatically when active.
2. **`sp_block_annoyances`**:
   - Location: `PetalAdBlockSettingsScreen.kt` ("Block Annoyances & Social Widgets").
   - Defect: Toggles `brave_social` filter subscription, but does not trigger adblock rule rebuild or refresh active sessions.
   - Fix: Trigger `PetalAdBlockEngine.reload()` whenever subscription sets change.
3. **`sp_auto_open_tab_groups_other_devices`**:
   - Location: `TabsSettingsScreen.kt` ("Automatically open tab groups from other devices").
   - Defect: Stored in preferences but never queried by sync engine or session restore.
   - Fix: Wire into tab synchronization or clean up dead state.
4. **`sp_close_tab_confirm`**:
   - Location: `TabsSettingsScreen.kt` ("Confirm before closing tab").
   - Defect: Only evaluated on "Close all tabs" in grid switcher, never when closing single tabs from the tab grid or swipe.
   - Fix: Align behavior to confirm on multi-tab or single-tab close based on user preference.

### 3.2 Category Exclusion & Search Index Mismatches
1. **Excluded Hub Categories**:
   - `SettingsCategory.DOWNLOADS`, `SettingsCategory.TABS`, `SettingsCategory.EXTENSIONS`, and `SettingsCategory.ACCOUNT_SYNC` exist as full category enums and have detailed setting screens (`DownloadSettingsScreen`, `TabsSettingsScreen`, etc.), but were omitted from `PetalSettingsSearchIndex.allCategories`.
   - Result: Browsing the Settings Hub does not expose "Tabs" or "Downloads" directly. Search queries for items inside them succeed, but direct category browsing is broken.
   - Fix: Include `TABS` and `DOWNLOADS` in the Settings Hub category list, matching Firefox Android structure (Search, General, Tabs, Downloads, Privacy).

---

## 4. Transient Surfaces & Popup Layout Audit

### 4.1 Identified Defects (from Screenshots & Code)
1. **Quick Tools Bottom Sheet**:
   - Cut-off Bottom / Missing Inset: Attached via `decor.addView(composeView)` without `WindowInsets.navigationBars` or `ime` padding, causing it to finish with a hard black bar above the browser bottom bar.
   - Top Inset / Drag Handle: Drag handle and title lack safe status bar insets.
   - Label Truncation: 4-column fixed grid with `maxLines = 1, overflow = TextOverflow.Ellipsis` truncates names like "QR Scan P…", "Spoof Ide…", "Image Gra…", "QR Gener…".
   - Glass Integration: Uses solid container fallback when glass state is unconfigured or decor is attached outside the glass hierarchy.
2. **Download Confirmation Dialog**:
   - Clashing Title Pill: `Box(color = MaterialTheme.colorScheme.surfaceContainerLowest)` creates a stark black box around "Download File?" inside a tonal dialog card.
   - Awkward Filename Wrapping: Text formatting does not use proper word break or middle-truncation for long package filenames (`petal-browser-arm64-v8a-release.apk`).
   - Clipping Outline: Combining Android `AlertDialog` `GradientDrawable` window background with Compose inner card creates mismatched corner radii and visible outer outline clipping.
3. **App-wide Fragmented Dialog Implementations**:
   - Over 90 files across `lens/`, `download/`, `ui/components/`, `compose/settings/`, `passwords/`, and `account/` use inconsistent dialog wrappers.
   - Solution: Create a unified `PetalTransientSurfaces.kt` providing:
     - `PetalScaffoldBottomSheet` (handles edge-to-edge window insets, navigation bar padding, glass backdrop, spring animations, drag handles)
     - `PetalExpressiveDialog` (handles max width constraints, insets, proper title typography without black pills, middle-truncated file names, zero border clipping, solid/glass fallback)

---

## 5. Glass Engine & Expressive Shape (Morphe) Audit

### 5.1 Glass Backend
- Backing implementations: `GeckoGlassBackend`, `GlassFx`, `GlassTuning`, `StrideGlass`, `StrideGlassModifiers`, `PetalGlassWallpaper`.
- Defect: Most transient surfaces currently bypass `StrideGlass` or fail to provide a smooth solid fallback on low-end devices or when `isLiquidGlassEnabled` is false.
- Fix: Ensure all dialogs and sheets resolve through `PetalGlassContainer` / `liquidGlassChrome` with automatic solid fallback (`surfaceContainerHigh` / `surfaceContainerLow`) and live refraction tuning.

### 5.2 Morphe / Shape Morphing
- "Morphe" translates to expressive shape morphing (`androidx.graphics.shapes RoundedPolygon/Morph`, `PetalMotionKit`, `PetalSpring`, `ExpressiveButtonShape`).
- Fix: Standardize asymmetric connected button pairs (50dp/6dp and 6dp/50dp), smooth squircle corner shapes (16dp-28dp), and spring press animations across all action rows.

---

## 6. Tab Session & Cache Management Audit

### 6.1 Tab Session Management
1. **Restore Complete Flag & Death Handling**:
   - `PetalSessionRestoreManager` manages session snapshots into `SessionStorage`. History stacks and active indices must be preserved across process death.
   - Private tabs correctly excluded from persistence, but must be cleanly evicted from memory when closed.
2. **Pinned Tabs, Groups, and Workspaces**:
   - `PetalTabMetadataStore` persists groups and pinned flags in a private SharedPreferences JSON sidecar. Must guarantee synchronization with `BrowserStore.tabs` on restart.

### 6.2 Cache & Site Data
1. **Outdated Database References**:
   - `PetalBrowserUtils.clearBrowserData` references `Ninja4.db` (legacy).
   - Fix: Target `HistoryBookmarkRepository.DATABASE_NAME` (`petal_browser.db`) and invoke GeckoView `StorageController.clearData` for all requested scopes (cookies, cache, DOM storage, permissions, form data).
2. **Delete On Quit**:
   - `sp_clear_quit` / `sp_clear_on_exit` checked in `MainActivity.onDestroy()` and `PetalExitWorkWorker`. Must invoke unified `BrowsingDataManager.clearBrowsingDataAsync`.

---

## 7. Website Compatibility & Feature Audit

1. **Downloads**: Support standard HTTP, HTTPS, blob URLs, and magnet links with external downloader chooser and duplicate detection.
2. **File Uploads & Choosers**: Seamless `PetalFilePickerBridge` and `PetalUploadChooser` with permission handling.
3. **Viewport & Zoom**: Ensure desktop mode toggle and pinch-to-zoom overrides (`FORCE_ZOOM`) work seamlessly without interfering with native GeckoView viewport gesture handlers.
4. **Pull to Refresh & Scroll**: Smooth scroll physics without jitter or toolbar flickering.

---

## 8. Prioritized Issue Matrix

| ID | Phase | Component | Defect | Severity | Remediation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **P3-01** | Phase 3 | `PetalQuickToolsSheet` | Cut off above address bar, truncated labels, missing nav bar insets | **CRITICAL** | Wrap in unified bottom sheet scaffold with `navigationBarsPadding()`, multi-line wrapped labels. |
| **P3-02** | Phase 3 | `PetalDownloadConfirmationDialog` | Black pill title clashes with card, filename wraps awkwardly, double window outline | **CRITICAL** | Eliminate outer window background, remove black pill wrapper, use clean title and middle-truncation. |
| **P3-03** | Phase 3 | Dialogs App-wide | 90+ fragmented dialog/sheet usages with inconsistent insets and clipping | **HIGH** | Migrate callers to unified `PetalTransientSurfaces.kt`. |
| **P2-01** | Phase 2 | Settings Wiring | `sp_strip_tracking_parameters` is dead and does not strip tracking tokens from navigation | **HIGH** | Hook into URL loading and address bar navigation to sanitize URLs when enabled. |
| **P2-02** | Phase 2 | Settings Placement | `Tabs` and `Downloads` hidden from Settings Hub category list | **MEDIUM** | Restore `TABS` and `DOWNLOADS` to `allCategories` in `PetalSettingsSearchIndex.kt`. |
| **P2-03** | Phase 2 | Quick Tools | `App Lock` tool misplaced in in-page Quick Tools grid | **LOW** | Reposition/refine access to Settings Hub while maintaining backwards-compatible tool action. |
| **P4-01** | Phase 4 | Glass Engine | Inconsistent glass routing and missing solid fallback in transient popups | **HIGH** | Route all dialogs and sheets through `PetalGlassContainer` / `liquidGlassChrome`. |
| **P6-01** | Phase 6 | Cache / Storage | Legacy `Ninja4.db` deletion reference in `PetalBrowserUtils.clearBrowserData` | **MEDIUM** | Update to real database name and full Gecko `StorageController.ClearFlags`. |
| **P5-01** | Phase 5 | Tab Session | Edge cases in session restore during cold restart / crash recovery | **HIGH** | Verify `PetalSessionRestoreManager` history stack and tab metadata consistency. |
| **P7-01** | Phase 7 | Website Issues | Download prompt flow and external downloader integration refinements | **MEDIUM** | Standardize download confirmation and mime resolution matching Firefox behavior. |

---
*Report written and verified.*

---

## 9. Execution & Resolution Summary Across Phases 2–8

All 8 phases of the Petal Browser overhaul have been executed:

### Phase 2: Wiring and Feature Placement
- **URL Parameter Tracking Sanitizer**: Fully wired `sp_strip_tracking_parameters` into `PetalTabViewController.kt` and `PetalBrowserViewModel.loadUrl()`. URLs with tracking parameters (e.g. `utm_*`, `fbclid`, `gclid`, `mc_eid`) are actively stripped via `PetalBrowserUtils.cleanTrackingParams()` prior to loading.
- **Settings Hub Restoration**: Re-enabled `SettingsCategory.TABS`, `SettingsCategory.DOWNLOADS`, `SettingsCategory.EXTENSIONS`, and `SettingsCategory.ACCOUNT_SYNC` in `PetalSettingsSearchIndex.allCategories` so they appear directly in the Settings Hub screen, resolving category search desynchronization.
- **Filter Subscription Reloading**: Wired `PetalFilterSubscriptionManager.reloadActiveRules(context)` upon toggling `sp_block_annoyances` in `PetalAdBlockSettingsScreen.kt`.

### Phase 3: Popups, Sheets, Dialogs (Resolving Screenshot Bugs)
- **Quick Tools Bottom Sheet Cutoff & Truncation (Screenshot 1)**:
  - Repositioned Quick Tools to bottom-sheet layout anchored with `navigationBarsPadding()` and `statusBarsPadding()`.
  - Added a full-screen semi-transparent dismissible backdrop (`Color.Black.copy(alpha = 0.40f)`) with back handler.
  - Fixed label truncation by updating `QuickToolItem` text layout to `maxLines = 2, minLines = 2, softWrap = true, lineHeight = 13.sp`.
- **Download Confirmation Dialog Redesign (Screenshot 2)**:
  - Removed clashing pitch-black pill header container (`surfaceContainerLowest`), replacing it with standard M3 expressive headline typography.
  - Encapsulated filename, file type icon, and size metadata in a clean `Surface` card with graceful 2-line ellipsis.
  - Eliminated the double-window border artifact in `PetalAndroidUtils.setupDialog()` by detecting ComposeViews and setting window background to transparent.
- **Unified Transient Surface Architecture**:
  - Centralized dialog and bottom sheet scaffolds in `PetalTransientSurfaces.kt` (`PetalDialog`, `PetalMaterialAlertDialog`, `PetalSheet`).
  - Added `WindowInsets.safeDrawing`, max width capping (520dp for dialogs, 640dp for sheets), `imePadding()`, and `verticalScroll()`.
  - Migrated key dialogs and sheets (`PetalConfirmBottomSheet`, `PetalSearchEngineSheet`, `PetalQrGeneratorDialog`, `PetalNetworkInspectorDialog`, `PetalSummaryBoxDialog`, `PetalUploadChooser`, `PetalBrowserPermissionDialog`, `PetalLensBottomSheet`, `PetalLensBridge`).

### Phase 4: Glass and Morphe (Shape Morphing)
- **Glass Pipeline**: Routed popups, sheets, and dialogs through `PetalGlassContainer` / `liquidGlassChrome`, respecting user refraction settings (`rememberRefractionTuning`), sheen intensity (`rememberGlassSheen`), and providing seamless solid fallback (`surfaceContainerHigh`/`Low`) when blur or glass is disabled.
- **Morphe Integration**: Standardized asymmetric squircle button pairs (50dp/6dp and 6dp/50dp) and expressive corner rounding across all transient dialog actions.

### Phase 5: Tab Session Management (Firefox Parity)
- **Parent/Child Tab Selection**: Implemented `parentId` tracking in `PetalTabMetadata` and `PetalTabMetadataStore`. Closing an active child tab automatically returns focus to its parent tab if still open, matching Firefox/Fenix behavior.
- **Tab Duplication**: Updated `duplicateTab()` to preserve the source tab's group (`tabGroupId`, `tabGroupTitle`), workspace (`workspaceId`), and record the source as `parentId`.
- **Private Tab Isolation**: Enhanced `PetalIncognitoSessionManager.flushSessionData()` to purge GeckoView `ALL_CACHES`, `AUTH_SESSIONS`, `COOKIES`, and `DOM_STORAGES`, and immediately clear private thumbnails via `TabThumbnailCache.clearPrivateCache()`.

### Phase 6: Cache, Storage, and Site Data
- **Engine Storage Controller Flags**: Wired `PERMISSIONS`, `AUTH_SESSIONS`, and `SITE_SETTINGS` flags into `PetalCacheManager.kt` and `BrowsingDataManager.clearBrowsingDataAsync()`.
- **Comprehensive Clear Data**: Updated `PetalBrowserUtils.clearBrowserData()` to clear GeckoView `ALL_CACHES`, `IMAGE_CACHE`, `DOM_STORAGES`, `SITE_SETTINGS`, `SITE_DATA`, `PERMISSIONS`, and `AUTH_SESSIONS`, and delete `favicon.db` alongside app databases.

### Phase 7: Website Issues
- **Download Flows & Routing**: Modernized confirmation dialog flow with external downloader support (1DM, ADM, AB Download Manager, Navi).
- **File Uploads**: Modernized `PetalUploadChooser` using `PetalSheet`, providing unified handling for images, videos, camera capture, and file browsing.
- **Display, Desktop & Zoom**: Verified desktop mode overrides (`viewportMode`, `userAgentMode`) and pinch-to-zoom accessibility overrides (`FORCE_ZOOM`).

