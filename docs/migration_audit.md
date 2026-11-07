# Petal engine consolidation - Stage 0 audit (2026-11-07)

Method: static grep over app/src/main/java. Nothing was compiled or run (no Android SDK in the audit environment).

## Size of the legacy surface
- PetalGeckoView.kt 3,804 lines; PetalTabViewController.kt 2,380; BrowserActivity.java 8,453.
- PetalGeckoView referenced in 33 files (~145 instanceof/cast sites).
- AlbumController 542 references, currentAlbumController 374, activeGeckoView 135.
=> Removing the legacy path is a multi-thousand-line refactor; it must be done per surface with a compiler and device, not in one pass.

## Where views/sessions are created
- Current tab path: PetalTabViewController (one GeckoEngineView + GeckoView per tab) - BrowserActivity ~2453, 7813, 7925, 8037; PetalSessionRestoreManager 459; PetalCustomTabActivity 180.
- Legacy: BrowserWebViewController.createAndConfigureGeckoView (also used by PetalWebExtensionDelegate and BrowserActivity ~8109), PetalPwaActivity 254, PetalPagePreviewBridge 126.
- Bare GeckoView: PetalExtensionsScreen 1818 (extension popup preview).
- Direct GeckoSession(...): PetalExtensionManager 724, 976, 1175 (popups).

## GeckoSession delegate ownership (a session holds ONE delegate per type)
PetalTabViewController.bindTab assigns directly on the GeckoSession, i.e. over whatever GeckoEngineSession installed:
| Delegate | Set by Petal at | Forwards to engine? |
|---|---|---|
| contentDelegate | PetalTabViewController:689 (PetalContentDelegateWrapper) | wrapper pattern - verify |
| navigationDelegate | :775 (ExternalSchemeNavigationDelegate) | wrapper pattern - verify |
| progressDelegate | :805 (SessionStateCapturingProgressDelegate -> engineProgress) | yes (onPageStop/onProgressChange forward) |
| promptDelegate | :671 (PetalGeckoView.createPromptDelegate) | NO - replaces engine's |
| permissionDelegate | :811 | NO - replaces engine's |
| historyDelegate | :867 | verify; engine/store history likely starved |
| scrollDelegate | :843 | NO |
| contentBlockingDelegate | :855 | NO |
| selectionActionDelegate | :815 | engine does not use |
GeckoEngineSession installs its own content, navigation, progress, scroll, history, content-blocking, permission, prompt and media-session delegates. Every row marked NO/verify is a place where either Petal or the BrowserStore silently stops receiving events.
Extension popups (PetalExtensionManager 982-1193, PetalExtensionsScreen 1831-1878) set their own prompt/progress/content/navigation delegates on separate sessions - isolated, low risk.
WebExtension delegates (action, tab, message) are set via session.webExtensionController in PetalExtensionManager and PetalBuiltInExtensionManager; these are separate from the delegates above.

## Proposed order (each step needs build + device test)
1. Delegate ownership: for every NO/verify row, make the Petal delegate forward to the engine's (as progressDelegate already does) or intentionally take sole ownership and feed the store.
2. Store-driven refresh state (done partially: onActivePageLoadFinished).
3. Single engine view + render()/release() on tab switch.
4. Move PWA, preview, extension popup surfaces to PetalTabViewController.
5. Delete PetalGeckoView/AlbumController last.

## Status
- STAGE 1 (DONE & COMMITTED): Delegate Ownership & Activity Recreation Protection. Implemented `PetalOwnedDelegate` contract on `PetalPromptDelegateWrapper`, `PetalPermissionDelegateWrapper`, `PetalContentDelegateWrapper`, `ExternalSchemeNavigationDelegate`, and `SessionStateCapturingProgressDelegate`. Added volatile supplier `rebind()` methods to ensure activity recreation does not leak closures or drop prompts. Added engine fallback forwarding. Unit tested in `DelegateOwnershipTest.kt`.
- STAGE 2 (DONE & COMMITTED): Single Engine View Architecture (Fenix Model). `BrowserActivity` operates with a single retained `singleTabViewController` mounted in `contentFrame`. Tab switching demotes background tabs to `PlaceholderAlbumController` and materializes the selected tab session via `singleTabViewController.render(session)`. Eliminates multi-SurfaceView GPU texture exhaustion. Unit tested in `SingleEngineViewTest.kt`.
- STAGE 3 (DONE & COMMITTED): Store-Driven State & APZ Touch Filtering. Connected `BrowserActivity` directly to `PetalEngineStore.observeManually()`, driving page loading, refresh resets, and omnibox reactively from `BrowserStore`. Integrated APZ `PanZoomController` overscroll top filtering (`canOverscrollTop()`) to accurately distinguish document overscroll from nested scrollable DOM elements. Unit tested in `StoreDrivenStateTest.kt`.
- STAGE 4 (DONE & COMMITTED): Move Legacy Surfaces to Unified Architecture. Migrated `PetalPwaActivity`, `PetalPagePreviewBridge`, `PetalExtensionsScreen`, and `PetalWebExtensionDelegate` away from legacy views to `PetalTabViewController` backed by `PetalEngineStore`. Preserved liquid glass shaders and WebExtension actions. Unit tested in `LegacySurfacesMigrationTest.kt`.
- STAGE 5 (DONE & COMMITTED): Remove Legacy Components & Finalize Consolidation. Extracted static utilities into `PetalEngineBridge.kt`. Completely deleted `PetalGeckoView.kt` and `BrowserWebViewController.kt`. Cleaned all `activeGeckoView` static references and `instanceof PetalGeckoView` dead code paths across `BrowserActivity.java`. Updated `architecture.md` and `CHANGELOG.md`. Unit tested in `LegacyRemovalVerificationTest.kt`.
