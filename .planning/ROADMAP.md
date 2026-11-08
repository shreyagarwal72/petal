# Roadmap: Petal Browser GeckoView Stabilization

## Milestone 1: Core GeckoView Stabilization & Parity

### Phase 1: Verification of Done Items
- [x] Audit R8 / ProGuard rules for reflection survival
- [x] Audit codebase for unkept reflection targets
- [ ] Complete fingerprint coverage in `PetalGeckoRuntime.kt`

### Phase 2: Cold-Restart History Preservation
- [ ] Fix `rebuildTabsFromBrowserStore` and `showAlbum` so `loadUrl` doesn't overwrite restored `EngineSessionState`
- [ ] Implement gated Mozilla session restore (`sp_mozilla_session_restore`)
- [ ] Connect `goBack` to `BrowserStore.canGoBack` to prevent Home drops

### Phase 3: Website Rendering & Theming
- [ ] Enforce theme precedence contract & extension suppression on dark-native pages
- [ ] Fix white flash during tab launch and tab switching
- [ ] Support web `theme-color` meta tinting on status bar
- [ ] Check surface modes and eliminate main-thread per-frame allocations

### Phase 4: Runtime & Settings Parity with Fenix
- [ ] Generate Gecko settings comparison matrix vs Fenix
- [ ] Migrate desktop site preference keys to `sp_desktop_site`
- [ ] Unify session settings builder across popups and PWAs

### Phase 5: Error Handling & Dead Code Cleanup
- [ ] Triage silent catch blocks across engine and browser controllers
- [ ] Attach error handlers to naked `GeckoResult` promises
- [ ] Clean unused WebView parameters
- [ ] Migrate main-thread `SharedPreferences.commit()` to asynchronous `apply()`

### Phase 6: Mozilla Components Migration & Packaging
- [ ] Evaluate and wire candidate Mozilla features behind feature flags
- [ ] Document Findings Table, Decisions Needed, and Verification Scripts
