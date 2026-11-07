# Petal engine consolidation - master prompt (paste into Claude Code at the repo root)

You are working in the Petal Android browser repo (com.petal.browser). Read docs/migration_audit.md first.
Goal: consolidate onto ONE tab/engine stack (android-components GeckoEngine + BrowserStore wrapping the real GeckoView 159 / GeckoRuntime), remove the legacy PetalGeckoView/AlbumController path, and keep every Petal custom feature, UI and extension working. You have a compiler and a device/emulator: build and test after EVERY stage, and do not move on until the stage passes. Work through all stages in one session without asking for approval between them.

## Already done (uncompiled - build first and fix any errors)
- Pull-to-refresh fixes: BrowserActivity.onActivePageLoadFinished() called from PetalTabViewController.onLoadingStateChange(!loading) when active; bringToFront() only once in onPull; RefreshBarLoadingIndicatorLazy (progress read lazily); PullToRefreshFrameLayout ignores child disallow-intercept while startedAtTop.
- Stage 1 (partial): scroll, history (onHistoryStateChange) and content-blocking delegates in PetalTabViewController forward to the engine's delegates (PetalOwnedDelegate marker, captured once).
- Known still-open: ANRs in the supplied logs are main thread blocked in nSyncAndDrawFrame during touch MOVE (render thread stall); re-test with the glass TextureView backend and Compose shadows after the fixes. Separate unrelated issue: yt-dlp init fails with "class tv is not a concrete class" (likely R8/obfuscation of youtubedl-android; check proguard rules).

## Hard constraints
- One GeckoView version only (geckoview 159.0.20261102144711 + android-components 159.0.1).
- Do not touch BrowserActivity address bar / bottom nav padding logic.
- Keep Petal's own UI: address bars (top/bottom/merge/pill), tab manager + container-transform animations, liquid-glass bars, context menu + selection toolbar, prompts/sheets, downloads UI, island/now bar, predictive back styles. Mozilla Feature classes may supply logic only, never default UI where Petal has its own.
- Extensions keep working: install, enable/disable, popups, toolbar actions, messaging, built-in WebCompat.
- Keep sp_gecko_engine_experimental until the final stage.

## Stages
1. DELEGATE OWNERSHIP: finish it. promptDelegate and permissionDelegate in PetalTabViewController.bindTab still replace the engine's; make Petal's forward to (or deliberately take sole ownership and feed) the engine/store. Verify content/navigation wrappers forward. Confirm every delegate type has exactly one owner.
2. SINGLE ENGINE VIEW: one engine view bound to the selected tab (Fenix model: render()/release() on tab switch; background tabs are EngineSession only). Keep thumbnails, tab-manager shrink/expand transition, glass backend. Fix per-tab views in BrowserActivity (~2453, 7813, 7925, 8037) and PetalSessionRestoreManager (459).
3. STORE-DRIVEN STATE: BrowserStore as source of truth for URL, title, loading, progress, back/forward, security, refresh. Replace direct activity callbacks (resetRefreshState, updateOmniBox pushes) with store observers. Replace PullToRefreshFrameLayout logic with SwipeRefreshFeature-style InputResultDetail checks (handles inner scrollers), keep Petal's indicator UI + haptics. No recomposition/relayout per touch MOVE.
4. MOVE LEGACY SURFACES to PetalTabViewController: PWA (PetalPwaActivity), page preview (PetalPagePreviewBridge), BrowserWebViewController/PetalWebExtensionDelegate popups, PetalExtensionsScreen preview, extension popup sessions (adopt through engine/store). Preserve cookies/auth and download-engine behaviour.
5. REMOVE LEGACY: delete PetalGeckoView, AlbumController and all instanceof branches (~145 sites; AlbumController 542 refs, currentAlbumController 374, activeGeckoView 135), remove the flag, update architecture.md and CHANGELOG.md.

## Checks after every stage
- ./gradlew assembleDebug clean, no new warnings; run unit/instrumentation tests; add tests for delegate ownership, tab-switch render/release, refresh clears on load, cold-restart back stack (Google -> example.com -> example.com/xyz), session restore.
- Extension smoke test: install, open popup, use toolbar action, send message, WebCompat active.
- Manual: context menu (link/image/text), selection toolbar, permission prompts, file picker, downloads, find in page, fullscreen video, PiP, passkeys, autofill, incognito isolation, desktop mode, reader mode, pull-to-refresh on a long page and on a page with an inner scroller.
- Main-thread health: 60s scripted scroll/pull/tab-switch with no ANR (baseline: attached ANR logs).

## Deliverable
Per-stage report: what changed, tests run, anything not verified. Commit per stage on a branch.
