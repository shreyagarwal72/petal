# Verification: Phase 7 — PWA & Custom Tabs Parity

## Status: COMPLETE

### 1. PWA Tab Session Memory Leak Resolution
- **Root Cause**: In [`PetalPwaActivity.java`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/pwa/PetalPwaActivity.java), `setupGeckoView()` created a new tab session in `PetalEngineStore` via `PetalEngineStore.createTabSession(...)` using a local variable `tabId`. On activity destruction (`onDestroy()`), the view was removed and destroyed, but `tabId` was never unregistered from `BrowserStore`, leaving orphan tab sessions accumulated in memory.
- **Fix**: Stored the tab identifier in `pwaTabId` and explicitly called `PetalEngineStore.removeTab(this, pwaTabId)` in `onDestroy()`.

### 2. Custom Tabs Incognito / Private Session Support
- **Root Cause**: [`PetalCustomTabActivity.kt`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/customtabs/PetalCustomTabActivity.kt) hardcoded `isIncognito = false` during session initialization, ignoring incognito custom tab intents from external applications.
- **Fix**: Parsed `androidx.browser.customtabs.extra.INCOGNITO`, `org.mozilla.fenix.EXTRA_IS_PRIVATE`, and `com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB` extras and passed `isIncognito = isIncognitoCustomTab` to `createTabSession(...)`, establishing isolated private browsing sessions for incognito custom tabs.

### 3. WebAuthn & ActivityDelegate Passkey Handling
- Verified that both [`PetalPwaActivity.java`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/pwa/PetalPwaActivity.java) and [`PetalCustomTabActivity.kt`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/customtabs/PetalCustomTabActivity.kt) implement foreground activity registration in `onResume`/`onPause` and dispatch `PetalGeckoRuntime.handleActivityResult(requestCode, resultCode, data)` in `onActivityResult`, ensuring cross-domain passkeys and FIDO authenticators succeed across all browsing contexts.
