# Verification: Phase 2 — Cold-Restart History Preservation

## Status: COMPLETE

### 1. Root Cause Analysis
- **Engine State Wipe on Restoration**: `materializeTabSession` successfully invoked `newSession.restoreState(state)` to restore the complete back/forward history stack from `SessionStorage`. However, immediately following this, both `rebuildTabsFromBrowserStore` and `showAlbum` unconditionally invoked `activeView.loadUrl(url)`. This aborted Gecko's in-progress state restoration and triggered a fresh navigation, obliterating the tab's back/forward history.
- **Accidental Blanking**: In `showAlbum`, if `overrideUrl == null` and `needsReloadOrRecovery()` evaluated to false, it fell into `activeView.loadUrl("about:blank")`.
- **Premature History Expiry & Home Drop**: `hasBackHistory()` checked `isHistoryPending()`, which expired on a rigid 4000ms wall-clock timer. If a user pressed Back after 4 seconds before Gecko had emitted a new navigation state change, `hasBackHistory()` returned false, dropping through Tier 4 directly to Home.

### 2. Implementation Details
- **`PetalTabViewController.kt`**:
  - Exposes `isHistoryRestoring()` to allow consumers to check if state restoration is active.
  - Aligns `hasBackHistory()` with `historyPending` until Gecko explicitly speaks via `onNavigationStateChange`.
  - Supports gated Mozilla `SessionUseCases.goBack` execution behind `sp_mozilla_session_restore`.
- **`BrowserActivity.java`**:
  - In `showAlbum`: suppressed redundant `loadUrl` when `activeView.isHistoryRestoring()` is true; eliminated the fallback `loadUrl("about:blank")`.
  - In `rebuildTabsFromBrowserStore`: guarded initial `loadUrl` with `!activeView.isHistoryRestoring()`.
- **`PetalSessionRestoreManager.kt`**:
  - `restoreSession`: Added gated Mozilla `TabsUseCases.restore` support behind `sp_mozilla_session_restore` (default `false`), falling back to direct dispatch.
  - `restoreTabSilently`: Uses `materializeTabSession` and checks `isHistoryRestoring()` to preserve history when restoring tabs.
