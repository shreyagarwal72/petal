# Requirements: Petal Browser GeckoView Stabilization

## Phase 1: Verification of Done Items
- [x] **R8-01**: Confirm in release (R8) build config that `GeckoEngineSessionState` and `GeckoEngineSession` survive via `-keep class mozilla.components.browser.engine.gecko.**`.
- [x] **REFL-01**: Audit all reflection targets (`Class.forName`, `getDeclaredField`, `getDeclaredConstructor`, `isAccessible`) across `app/src/main` and verify R8 rules exist.
- [ ] **FING-01**: Audit `syncPreferences` fingerprint in `PetalGeckoRuntime.kt` to ensure 100% key coverage for all read preferences.

## Phase 2: Cold-Restart History (Highest Value)
- [ ] **HIST-01**: Trace `PetalSessionRestoreManager` and `BrowserActivity` to prevent `loadUrl` from clobbering restored `EngineSessionState` history.
- [ ] **HIST-02**: Add gated Mozilla session restoration via `sp_mozilla_session_restore` (default `false`) using `browser-session-storage` + `SessionUseCases`/`TabsUseCases`.
- [ ] **HIST-03**: Ensure `PetalTabViewController.goBack` reliably utilizes the BrowserStore's `canGoBack` state and eliminates drops to Home when history is present.

## Phase 3: Website Rendering & Theming
- [ ] **THEME-01**: Formalize dark mode precedence: engine `preferredColorScheme` -> `sp_force_dark_mode` -> Smart Dark extension; prevent double inversion.
- [ ] **THEME-02**: Eliminate white flash on tab open and switch by styling `GeckoView` surface with active theme background before initial paint.
- [ ] **THEME-03**: Handle website `theme-color` meta via engine delegate/`ContentState` with smooth status bar tinting.
- [ ] **PERF-01**: Mitigate layout jumps from dynamic toolbars/IME and optimize main-thread per-frame allocations during scroll/animations.

## Phase 4: Runtime & Settings Parity with Fenix
- [ ] **PARITY-01**: Produce comparison matrix of `GeckoRuntimeSettings` / `GeckoSessionSettings` against Fenix defaults and apply stability improvements.
- [ ] **KEY-01**: Consolidate desktop mode to `sp_desktop_site` and run one-time migration for legacy `sp_desktop` and profile keys.
- [ ] **SESS-01**: Unify popup, extension-popup, and PWA session configuration through a shared builder pipeline.

## Phase 5: Error Handling & Dead Code Cleanup
- [ ] **ERR-01**: Triage silent `catch (_: Throwable) {}` blocks, document intentional ignores, log warnings, and properly rethrow `CancellationException`.
- [ ] **ERR-02**: Add failure callbacks to unhandled `GeckoResult.then/accept` invocations.
- [ ] **CLEAN-01**: Remove dead `WebView` parameters from `PetalCacheManager` and `BrowsingDataManager`.
- [ ] **ASYNC-01**: Shift blocking `SharedPreferences.commit()` to `apply()` off the main thread (retaining only critical process-death cleanup flags).

## Phase 6: Replace Custom Code with Mozilla Components
- [ ] **AC-01**: Plan and selectively adopt Mozilla components (`feature-findinpage`, `feature-readerview`, etc.) behind feature flags (default off).
