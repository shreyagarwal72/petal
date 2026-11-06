# Phase 05: Web-Facing GeckoView and Permissions Fixes

## Goal
Fix four web-facing bugs in Petal Browser:
1. Real error pages with retry and advanced/accept-risk paths instead of blank pages on load errors.
2. Android runtime permissions requested dynamically through activity bridges (`BrowserActivity`, `PetalCustomTabActivity`, `PetalPwaActivity`) with callback resolution, removing legacy dialogs and handling combined camera/mic requests and sensor cleanup.
3. Explicit content permission handling for autoplay (inaudible auto-allowed, audible blocked by default unless site-whitelisted), storage access, XR, tracking, and local device/network access with origin-keyed rules.
4. Prompt hang prevention when host activity is null, and file chooser support across `BrowserActivity`, `PetalCustomTabActivity`, and `PetalPwaActivity`.

## Tasks
- [x] Task 1: Add `org.mozilla.components:browser-errorpages:159.0.1` and implement `RequestInterceptor` in `PetalEngineStore.kt` via `DefaultSettings`. Update `PetalGeckoView.onLoadError` to return error-page URI for PWA and preview paths while preserving HTTPS-Only toast.
- [x] Task 2: Implement `PetalRuntimePermissionBridge` and override `onAndroidPermissionsRequest` in `PetalGeckoPermissionDelegate.kt`. Connect permission result callbacks in `BrowserActivity`, `PetalCustomTabActivity`, and `PetalPwaActivity`. Remove legacy `PetalAndroidUtils.grantPermissions*` calls, combine camera+mic prompts, and clean up `PetalActiveMediaSensors` on navigation and close.
- [x] Task 3: In `PetalGeckoPermissionDelegate.kt`, explicitly handle autoplay (`PERMISSION_AUTOPLAY_INAUDIBLE`, `PERMISSION_AUTOPLAY_AUDIBLE`), storage (`PERMISSION_PERSISTENT_STORAGE`, `PERMISSION_STORAGE_ACCESS`), XR, tracking, and local network/device access with proper site-rule persistence.
- [x] Task 4: Fix prompt hangs on null activity in `PetalGeckoView.createPromptDelegate` for `onAlertPrompt` and `onButtonPrompt`. Support file choosing in `PetalCustomTabActivity` and `PetalPwaActivity` in addition to `BrowserActivity`.
