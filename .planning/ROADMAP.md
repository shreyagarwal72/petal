# Roadmap: Petal Browser GeckoView Stabilization

## Milestone 1: Core GeckoView Stabilization & Parity

### Phase 1: Verification of Done Items
- [x] Audit R8 / ProGuard rules for reflection survival
- [x] Audit codebase for unkept reflection targets
- [x] Complete fingerprint coverage in `PetalGeckoRuntime.kt`

### Phase 2: Cold-Restart History Preservation
- [x] Fix `rebuildTabsFromBrowserStore` and `showAlbum` so `loadUrl` doesn't overwrite restored `EngineSessionState`
- [x] Implement gated Mozilla session restore (`sp_mozilla_session_restore`)
- [x] Connect `goBack` to `BrowserStore.canGoBack` to prevent Home drops

### Phase 3: Website Rendering & Theming
- [x] Enforce theme precedence contract & extension suppression on dark-native pages
- [x] Fix white flash during tab launch and tab switching
- [x] Support web `theme-color` meta tinting on status bar
- [x] Check surface modes and eliminate main-thread per-frame allocations

### Phase 4: Runtime & Settings Parity with Fenix
- [x] Generate Gecko settings comparison matrix vs Fenix
- [x] Migrate desktop site preference keys to `sp_desktop_site`
- [x] Unify session settings builder across popups and PWAs

### Phase 5: Error Handling & Dead Code Cleanup
- [x] Triage silent catch blocks across engine and browser controllers
- [x] Attach error handlers to naked `GeckoResult` promises
- [x] Clean unused WebView parameters
- [x] Migrate main-thread `SharedPreferences.commit()` to asynchronous `apply()`

### Phase 6: Mozilla Components Migration & Packaging
- [x] Evaluate candidate Mozilla features and sequence replacement roadmap
- [x] Document Findings Table, Decisions Needed, and Verification Scripts
