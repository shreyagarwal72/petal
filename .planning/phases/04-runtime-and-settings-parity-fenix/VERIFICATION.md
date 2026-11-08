# Verification: Phase 4 — Runtime and Settings Parity with Fenix

## Status: COMPLETE

### 1. Fenix vs Petal Settings Parity Matrix
| Setting | Fenix Default | Petal Value | Parity Decision & Rationale |
| :--- | :--- | :--- | :--- |
| `aboutConfigEnabled` | Nightly/Beta only | `sp_enable_about_config` (default `isDebug`) | **Retained**: User configurable toggle for power users. |
| `isolatedProcessEnabled` | `true` (Android 10+) | `false` | **Retained**: Isolated process causes fatal crashes on Android 16 (SDK 36) and OEM ROMs (Realme/Oppo ART image restrictions in isolated UIDs). Critical for stability. |
| `extensionsProcessEnabled` | `!isLowRamDevice` | `!isLowRamDevice` | **Parity Match**: Optimal memory utilization on low-spec hardware. |
| `extensionsWebAPIEnabled` | `true` | `true` | **Parity Match**: Full WebExtensions web API support. |
| `loginAutofillEnabled` | `true` | `sp_login_autofill_enabled` (default `true`) | **Parity Match**: Permits password manager extensions to intercept and autofill login forms. |
| `safeBrowsing` | `DEFAULT` (phishing + malware) | `DEFAULT` | **Parity Match**: Official Mozilla phishing and malware protection. |
| `cookieBehavior` | TCP / Isolation | TCP / Isolation (`ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS`) | **Parity Match**: Aligned startup and runtime sync to prevent tracking cookies without breaking logins. |
| `preferredColorScheme` | System / Light / Dark | System / Light / Dark + Force Dark | **Parity Match**: Respects website media queries and user preferences. |
| `viewportMode` | `VIEWPORT_MODE_MOBILE` | `VIEWPORT_MODE_MOBILE` | **Parity Match**: Standard mobile viewport geometry. |

### 2. Desktop Mode Key Canonicalization
- Consolidated all desktop site toggles and checks to `sp_desktop_site`.
- Created `PetalEngineBridge.migrateDesktopKeys(sp)` to migrate legacy `sp_desktop`, `profileStandard_desktop`, and `profileIncognito_desktop` keys into `sp_desktop_site`.
- Updated `PetalTabViewController`, `BrowserActivity`, `BrowserNavigationDelegate`, and `PetalSiteInfoBottomSheet` to use `sp_desktop_site`.

### 3. Unified Session Settings Builder
- Created `PetalEngineBridge.createSessionSettings(context, isPrivate, isExtensionPopup, overrideDesktop)`.
- Replaced separate inline `GeckoSessionSettings.Builder()` calls in `PetalTabViewController` and `PetalExtensionManager` with the single shared builder pipeline, preventing settings divergence between regular tabs, popups, and extension actions.
