---
name: petal-browser-engine
description: Runbook and architectural practices for Petal Browser GeckoView engine, session management, gesture navigation, and pull-to-refresh.
---

# Petal Browser Engine Workflow & Diagnostics

## Overview
This skill provides runbooks and conventions for working with Petal Browser's GeckoView implementation, Compose integrations, touch gesture routing, and tab session management.

## 1. Back Gesture & Session Management Runbook
When diagnosing back gestures:
1. Ensure `browserBackCallback` receives system back events.
2. In `BrowserActivity.java`, `performBackNavigation()` must check:
   - Overlay/dialog dismissals first.
   - Active web controller `hasBackHistory()` (`canGoBackVal` from `GeckoSession.NavigationDelegate.onCanGoBack`).
   - If no history exists on a web page, navigate back to `petal://home` or `about:blank`.
   - If already on `petal://home` and multiple tabs exist (`BrowserContainer.size() > 1`), close current tab via `removeAlbum(currentAlbumController)`.
   - If only 1 tab exists on Home, trigger exit confirmation / double-back exit.
3. System exclusion rects: Call `resetGestureExclusionRects()` on both `PetalGeckoView` and all its child views to prevent Android edge gestures from being blocked.

## 2. Pull-To-Refresh Diagnostics
When diagnosing pull-to-refresh:
1. `PullToRefreshFrameLayout` filters gestures:
   - Rejects multi-touch and pinch-to-zoom gestures.
   - Requires vertical dominance (`dy > Math.abs(dx) * 1.35f`).
   - Checks `canChildScrollUp()` and `canPull()`.
2. In `BrowserActivity.java`, `canPull()`:
   - Allows Home page (`isPetalHomeSurfaceShowing || isHomePage(currentUrl)`).
   - Allows web pages when `getPageScrollY() <= 0`.
   - Disallows native overlays (`settings`, `history`, `downloads`).
3. On pull release:
   - If on Home, posts a delayed refresh of `showAlbum(currentAlbumController, "petal://home")`.
   - If on Web, calls `((PetalGeckoView) currentAlbumController).reload()`.

## 3. Build & Verify
Always verify changes with:
```bash
./gradlew assembleDebug
```
