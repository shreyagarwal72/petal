# State: Petal Browser GeckoView Stabilization

## Current Status
- **Milestones**:
  - **Milestone 1**: Core GeckoView Stabilization & Parity (Phases 1-6: COMPLETE & VERIFIED)
  - **Milestone 2**: Deep Mozilla Upstream Parity & Component Integration (Phases 7-12: COMPLETE & VERIFIED)
- **Active Phase**: All Phases (Phase 1 through Phase 12) Complete, Documented, and Verified
- **Completed Deliverables**:
  - **Phase 1**: Verified R8 keep rules, audited reflection targets, and completed preference fingerprint keys in `PetalGeckoRuntime.kt`.
  - **Phase 2**: Fixed history wiping in `rebuildTabsFromBrowserStore` and `showAlbum`; gated Mozilla session restore (`sp_mozilla_session_restore`); connected `goBack` to `BrowserStore.canGoBack` and eliminated premature 4-second timeout drop to Home.
  - **Phase 3**: Enforced dark mode precedence; eliminated white flash on tab open/switch by styling `GeckoView` surface with `colorSurface` before first paint; added status bar `theme-color` meta tinting; debounced `capturePixels` during active scrolling (<400ms).
  - **Phase 4**: Generated Fenix parity matrix; unified session settings builder in `PetalEngineBridge.createSessionSettings`; canonicalized desktop mode to `sp_desktop_site` with automatic legacy key migration.
  - **Phase 5**: Fixed unhandled `GeckoResult.then()` in finder; removed dead `WebView` parameters from `PetalCacheManager` and `BrowsingDataManager`; rethrown `CancellationException` in coroutines; migrated `commit()` to `apply()`.
  - **Phase 6**: Evaluated Mozilla components substitution order; prepared comprehensive Findings Table, Diff Log, Needs-Decision List, and Verification Test Scripts in `REPORT.md`.
  - **Phase 7**: Eliminated PWA tab session leak in `PetalPwaActivity.onDestroy()`; supported incognito custom tabs via `isIncognitoCustomTab` intent parsing; verified cross-activity WebAuthn passkey handling.
  - **Phase 8**: Upgraded article parsing in `PetalReaderBridge.kt` with Mozilla Readability algorithms (metadata parsing, clone sanitization, readable block collection); gated behind `sp_mozilla_readability`.
  - **Phase 9**: Retained built-in Trie blocker as high-speed default baseline; added 1-tap uBlock Origin recommendation and dashboard launcher in Privacy Shield sheet.
  - **Phase 10**: Gated Mozilla `EngineSession` find abstraction behind `sp_mozilla_find_in_page` with zero edits to address bar/padding logic.
  - **Phase 11**: Verified `PetalMediaSessionService` `MediaSessionCompat` controls and gated foreground session startup behind `sp_mozilla_media_session`.
  - **Phase 12**: Added reflection-safe W3C `WebNotification.actions` parsing and action buttons; hooked native Gecko content process crashes and OS kills into `PetalAppLogger`.
