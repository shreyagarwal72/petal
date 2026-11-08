# State: Petal Browser GeckoView Stabilization

## Current Status
- **Milestone**: 1 (Core GeckoView Stabilization & Parity)
- **Active Phase**: All Phases (Phase 1 through Phase 6) Complete & Verified
- **Completed**:
  - **Phase 1**: Verified R8 keep rules, audited reflection targets, and completed preference fingerprint keys in `PetalGeckoRuntime.kt`.
  - **Phase 2**: Fixed history wiping in `rebuildTabsFromBrowserStore` and `showAlbum`; gated Mozilla session restore (`sp_mozilla_session_restore`); connected `goBack` to `BrowserStore.canGoBack` and eliminated premature 4-second timeout drop to Home.
  - **Phase 3**: Enforced dark mode precedence; eliminated white flash on tab open/switch by styling `GeckoView` surface with `colorSurface` before first paint; added status bar `theme-color` meta tinting; debounced `capturePixels` during active scrolling (<400ms).
  - **Phase 4**: Generated Fenix parity matrix; unified session settings builder in `PetalEngineBridge.createSessionSettings`; canonicalized desktop mode to `sp_desktop_site` with automatic legacy key migration.
  - **Phase 5**: Fixed unhandled `GeckoResult.then()` in finder; removed dead `WebView` parameters from `PetalCacheManager` and `BrowsingDataManager`; rethrown `CancellationException` in coroutines; migrated `commit()` to `apply()`.
  - **Phase 6**: Evaluated Mozilla components substitution order; prepared comprehensive Findings Table, Diff Log, Needs-Decision List, and Verification Test Scripts.
