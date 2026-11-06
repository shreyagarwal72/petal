# Current Project State

## Active Phase
- **Phase**: 05
- **Name**: Web-Facing GeckoView and Permissions Fixes
- **Status**: Completed

## All 5 Phases Complete
1. **Phase 1: Motion Foundation**:
   - `PetalSpring` single source of truth tokens.
   - `MotionGate` counter-based coordinator dropping blur/glass to static fill.
   - Snapshot policy for heavy content.
   - `PetalJankTracker` and Baseline Profile coverage.
2. **Phase 2: Tab Transitions**:
   - Button-origin new tab grow & reverse (`PetalNewTabOrigin`).
   - Tab close card shrink & fade (`listReorder` neighbour slide, velocity swipe-to-close).
   - Category switch horizontal slide + fade-through.
   - Interruptible hero morph & predictive back.
3. **Phase 3: Address Bar & Chrome**:
   - Symmetrical container morph for omnibox enter/exit (`BarGhost`).
   - Scroll-to-pill direct offset driving without timers, spring settle on stop/reverse.
   - Synchronized progress bar and favicon fade (`progressFade`).
   - Omnibox suggestions stable keys & list-reorder animations.
4. **Phase 4: Menus, Sheets, Settings & Audit**:
   - Menus: origin-anchored scale (`menuScale`), item fade (`menuItemFade`), fast close (`menuCloseScale`).
   - Bottom sheets: drag-follow with `sheetDragSettle` spring and fling/threshold dismiss.
   - Settings & Downloads: row expand container morph into detail page and reverse on back.
   - Dialogs: shared `dialogScaleIn` / `dialogScaleOut` and `dialogFadeIn` / `dialogFadeOut`.
   - Comprehensive audit of codebase for inline springs/tweens, layout property animations, and composition-phase animation reads.
   - Written `docs/motion.md` motion guide.
5. **Phase 5: Web-Facing GeckoView and Permissions Fixes**:
   - Error pages: Added `org.mozilla.components:browser-errorpages:159.0.1` and `RequestInterceptor` in `PetalEngineStore.kt` via `DefaultSettings`. Updated `PetalGeckoView.onLoadError` to generate URL-encoded error pages with retry and advanced/accept-risk paths while preserving HTTPS-Only bypass toast.
   - Android runtime permissions: Implemented `PetalRuntimePermissionBridge` and `onAndroidPermissionsRequest` in `PetalGeckoPermissionDelegate.kt` with activity hooks across `BrowserActivity`, `PetalCustomTabActivity`, and `PetalPwaActivity`. Removed legacy `PetalAndroidUtils.grantPermissions*` dialog calls. Combined camera & microphone requests into a unified prompt. Cleaned up `PetalActiveMediaSensors` on navigation and session destroy.
   - Autoplay and content permissions: Handled `PERMISSION_AUTOPLAY_INAUDIBLE` (auto-allow) and `PERMISSION_AUTOPLAY_AUDIBLE` (blocked by default unless allowed by site rule, no dialog). Mapped persistent storage, storage access, XR, tracking, and local device/network permissions to distinct prompts and origin-scoped rules without falling back to storage text.
   - Prompt hangs and uploads: Fixed uncompleted `GeckoResult` in `onAlertPrompt` and `onButtonPrompt` when activity is null. Extended `onFilePrompt` to `ComponentActivity` so file upload pickers work in `PetalCustomTabActivity` and `PetalPwaActivity` as well as `BrowserActivity`, honoring the camera capture hint.
