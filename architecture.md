# Petal Browser Architecture (architecture.md)

## 1. Unified Engine Architecture: Single Engine View & BrowserStore
Petal Browser employs a consolidated, production-grade browser engine architecture modeled after Firefox / Fenix. All tabs, browser surfaces, and WebExtensions run on a single unified engine stack:
- **Engine Core**: `org.mozilla.geckoview:geckoview` 159.0.20261102144711
- **Component Stack**: Mozilla Android Components 159.0.1 (`concept-engine`, `browser-engine-gecko`, `browser-state`, `browser-store`, `feature-downloads`, `service-firefox-accounts`)
- **Runtime Singleton**: `com.petal.browser.engine.gecko.PetalGeckoRuntime` configures Firefox Enhanced Tracking Protection (ETP), Total Cookie Protection, cookie banner blocker, safe browsing, and WebExtensions.
- **Authoritative Cookie Bridge**: `PetalGeckoCookieBridge` interfaces with GeckoView's `StorageController` with strict isolation between standard and private tab contexts.
- **Built-in Network Blocker**: `petal_network_blocker` WebExtension intercepts all subresource network requests at `webRequest.onBeforeRequest` and runs early cosmetic CSS injection at `document_start`.
- **Unified Tab Surface**: `PetalTabSurface` defines the tab contract across active `PetalTabViewController` and background `PlaceholderTabController` instances.

### Single-Engine View Architecture (Fenix Model)
Instead of allocating a separate `GeckoView` / `SurfaceView` texture per open tab (which caused GPU memory exhaustion and render-thread stall ANRs), Petal uses a **single active tab viewport**:
1. **Single View Controller (`PetalTabViewController`)**:
   - `BrowserActivity` maintains one retained `singleTabViewController` instance mounted in `contentFrame`.
   - When switching tabs (`showAlbum()`), the outgoing tab is demoted to a lightweight `PlaceholderAlbumController` (preserving its title, URL, favicon, and thumbnail) while its engine session remains managed in `BrowserStore`.
   - The newly selected tab session is bound and rendered in `singleTabViewController.render(session)`.
2. **Background Tabs**:
   - Background tabs do NOT allocate native Android views or SurfaceViews.
   - Background tab sessions are tracked inside `BrowserStore`. Inactive tabs can be suspended to session state envelopes by `PetalTabSuspender` under memory pressure.
3. **Consolidated Engine Bridge (`PetalEngineBridge`)**:
   - Centralizes static engine utilities, download dispatching, intent routing, profile resolution, and user agent generation.
   - Replaced legacy static helpers from deprecated `PetalGeckoView`.

```mermaid
graph TD
    A[BrowserActivity.java] --> B[PetalTabViewController.kt (Single Active View)]
    A --> C[PetalEngineStore / BrowserStore]
    C --> D[GeckoEngine / EngineSession Pool]
    D --> E[GeckoRuntime Singleton / GeckoSession]
    B --> E
    A --> F[BrowserContainer (Tab Manager)]
    F --> G[PlaceholderAlbumController (Background Tabs)]
    A --> H[ComposeView Bridging Container]
    H --> I[Jetpack Compose M3 UI Screens]
```

---

## 2. Delegate Ownership & Activity Recreation Rebinding
Petal browser delegates implement the `PetalOwnedDelegate` contract to survive Android Activity recreations without leaking stale closures or tearing down live Gecko sessions:
- `PetalPromptDelegateWrapper`: Expressive Material 3 prompts (alerts, auth, file pickers, choices, credit card prompts) with fallback forwarding.
- `PetalPermissionDelegateWrapper`: Runtime permissions (camera, microphone, geolocation) with automatic Android permission requests.
- `PetalContentDelegateWrapper`: Dynamic title, URL, crash recovery, and security changes.
- **Dynamic Rebinding**: When an Activity is recreated, `PetalTabViewController.bindTab()` calls `rebind()` on all owned delegates, updating the volatile activity supplier without re-instantiating delegates on active Gecko sessions.

---

## 3. Store-Driven State & APZ Touch Filtering
State synchronization is strictly unidirectional and reactive:
1. **BrowserStore Observation**:
   - `BrowserActivity` subscribes to `PetalEngineStore.observeManually()` in `onCreate()`.
   - Loading completion, refresh resets, omnibox updates, and back stack states are driven directly from store actions.
2. **APZ (Async Pan Zoom) Overscroll Arbitration**:
   - Pull-to-refresh checks `PanZoomController.onTouchEventForDetailResult` (`canOverscrollTop()`) in `PetalTabViewController`.
   - Distinguishes document-level overscroll top from inner scroll containers (overflow divs, carousels), preventing false refresh triggers.

---

## 4. ComposeView Bridging Pattern & ViewTree Requirements
Petal embeds Jetpack Compose modular screens into `BrowserActivity` using dynamic `ComposeView` bridging:
- Primary container is `PullToRefreshFrameLayout contentFrame` in `activity_main.xml`.
- Sub-screens (Settings, Tab Switcher, Downloads, History, Bookmarks, Omnibox, Credits, Reader, Incognito Home) are generated via companion bridge objects.
- ViewTree lifecycle, ViewModel store, and saved state registry owners are explicitly wired:
```kotlin
composeView.apply {
    setViewTreeLifecycleOwner(activity)
    setViewTreeViewModelStoreOwner(activity)
    setViewTreeSavedStateRegistryOwner(activity)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
}
```

---

## 5. Subpackage Directory Structure
- `com.petal.browser.activity`: Core activities (`BrowserActivity`, `SettingsActivity`, `PetalPwaActivity`).
- `com.petal.browser.browser`: Core browser tab contracts (`PetalTabViewController`, `PlaceholderAlbumController`, `AlbumController`, `BrowserContainer`).
- `com.petal.browser.engine.gecko`: Engine integrations (`PetalEngineStore`, `PetalEngineBridge`, `PetalGeckoRuntime`, `PetalTabSuspender`).
- `com.petal.browser.compose`: Jetpack Compose UI surfaces (settings, tabs, omnibox, context menus, video player).
- `com.petal.browser.predictive`: Predictive back gesture coordination, blur snapshots, and transition curves.
- `com.petal.browser.media`: Video overlays, PiP coordination, and `PetalMediaBridge`.
- `com.petal.browser.download`: Background file download management leveraging Fetch2.
- `com.petal.browser.pwa`: Progressive Web App (PWA) manifest installation and notification manager.
- `com.petal.browser.passwords`: Hardware-backed credential vault and autofill.
- `com.petal.browser.ui.glass`: Liquid glass material rendering and background blurs.
- `com.petal.browser.ui.theme`: Material 3 Expressive theme tokens and anti-slop design system.
