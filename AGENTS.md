# Petal Repository Roles & Dual-Synchronization Protocol

## 1. Distinct Repository Roles & Architecture Boundaries

- **`petal` (FOSS / GitHub Release Variant)**:
  - Direct APK distribution via GitHub Releases.
  - Features self-contained in-app updater (`UpdateUnit.java`, `PetalUpdateInstallerReceiver.kt`, `UpdaterSettingsScreen.kt`, and `dialog_petal_update_expressive.xml`).
  - Strict FOSS environment: **Zero Google Play SDKs** (`play-services-ads`, `com.google.android.play:app-update` are strictly forbidden).
  - Requires `REQUEST_INSTALL_PACKAGES` permission to install downloaded APKs.

- **`petal_browser` (Google Play Store Variant)**:
  - Distributed via Google Play Store.
  - **No In-App Self-Updater**: Google Play Developer Program Policy strictly forbids apps downloading or installing executable code/APKs outside Google Play.
  - **Google Play In-App Updates**: Powered by official `com.google.android.play:app-update-ktx` (`PetalPlayUpdateManager.kt`, `PetalPlayUpdatePopup.kt`).
  - **Monetization & Supportive Ads**: Features optional supportive ad banners on the home screen (`SupportiveAdsSettingsScreen.kt`, `PetalSupportiveAdsManager.kt`, `com.google.android.gms:play-services-ads`).
  - **Publishing & Assets**: Tracks Fastlane metadata (`fastlane/`), Google Play graphic assets (`graphics/`, `wiki/`), draft backups (`drafts/`), variable fonts (`inter_variable.ttf`, `lexend_variable.ttf`, etc.), and bundle signing utilities (`.github/scripts/strip_aab_signatures.py`).
  - **Permissions**: Omits `REQUEST_INSTALL_PACKAGES` to maintain Play Store compliance.

## 2. Feature & Fix Synchronization Protocol

- Treat `petal` as the primary browser feature and browsing engine source.
- **Never blindly mirror or fast-forward merge repositories**. Features, engine improvements, bug fixes (such as Firefox Sync, widgets, address bar physics, media players) must be synced while strictly respecting variant-specific boundaries.
- When porting changes:
  1. Never delete or overwrite Play Store variant components (`PetalPlayUpdateManager`, `PetalSupportiveAdsManager`, `SupportiveAdsSettingsScreen`, `fastlane`, variable fonts) in `petal_browser`.
  2. Never copy Play Store SDKs (`play-services-ads`, `app-update`) into `petal`.
  3. Keep settings navigation mapped to `SettingsCategory.ADS` in `petal_browser` and `SettingsCategory.UPDATER` in `petal`.

## 3. File Reading & Execution Hygiene
- Inspect targets precisely once, synthesize necessary context, and immediately apply the concrete practical fix.
- One command per invocation, no command chaining (`&&` / `||`), and zero local gradle builds on Termux.
