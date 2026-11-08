# Verification: Phase 3 — Website Rendering and Theming

## Status: COMPLETE

### 1. Dark Mechanism Precedence & Mutual Exclusion
- **Mechanism Hierarchy**:
  1. `sp_force_dark_mode`: Direct override setting `rt.settings.preferredColorScheme = COLOR_SCHEME_DARK`.
  2. Engine `preferredColorScheme`: Dictates browser color scheme to CSS media query `(prefers-color-scheme: dark)` and `color-scheme` declarations.
  3. `dark_webpages.js` Smart Dark extension:
     - Waits for DOM ready if the engine is already dark to avoid double inversion during initial parse.
     - Performs strict detection via `isPageAlreadyDark()` checking root/body `color-scheme: dark` and computed background/foreground luminances.
     - If dark is already present, `removeDarkStyle()` immediately yields rendering to native styling.
     - Extension applies CSS inversion filters ONLY if the page is light and lacks native dark theme styling.
     - Body mutations and dynamic `matchMedia('(prefers-color-scheme: dark)')` listeners adapt to client-side theme switches without reloading.

### 2. Elimination of White Flash on Tab Open / Switch
- **Surface Color Initialization**:
  - `PetalTabViewController.init`: Sets background color of both the view and `engineView.asView()` to Material 3 `colorSurface` before any child view is laid out.
  - `PetalTabViewController.bindTab`: Sets background color on `this`, `engineView.asView()`, and `findGeckoView(...)` to `colorSurface` prior to `engineView.render(session)` before first paint.
  - `BrowserActivity.showAlbum`: Sets `contentFrame.setBackgroundColor(surfaceColor)` upon switching active surfaces.

### 3. Website `theme-color` Meta Tinting
- **Status Bar Integration**:
  - `BrowserActivity` observes `selectedTab.getContent().getThemeColor()`.
  - When non-null and not on the Home surface, applies `setStatusBarColor` with luminance-aware light/dark icon adjustment via `WindowInsetsControllerCompat`.
  - Restores system theme defaults when navigating to Home or when no meta theme color is defined.

### 4. Scroll & Animation Jank Mitigation
- Debounced `updatePreviewCache`: Suppresses `capturePixels` if user scrolled within the last 400ms (`now - lastScrollTime < 400L`), eliminating GPU buffer readback stalls and frame drops during scrolling.
