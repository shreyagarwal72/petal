# Petal Architecture & Unified Repository Guidelines

## 1. Unified Repository & Product Flavors

The `petal` and `petal_browser` repositories have been consolidated into this single repository (`petal`). Distribution variants are managed via Gradle product flavors (`distribution` dimension):

- **`github` (GitHub Release Variant)**:
  - Direct APK distribution via GitHub Releases.
  - Self-contained in-app updater (`UpdateUnit.java`, `PetalUpdateInstallerReceiver.kt`, `UpdaterSettingsScreen.kt`).
  - Uses `src/ads/java` and `src/noPlay/java`.
  - Requires `REQUEST_INSTALL_PACKAGES` permission declared in `src/github/AndroidManifest.xml`.
  - BuildConfig: `IS_PLAY_STORE_BUILD = false`, `IS_FDROID_BUILD = false`, `DISTRIBUTION_CHANNEL = "github"`.

- **`play` (Google Play Store Variant)**:
  - Distributed via Google Play Store as an AAB bundle.
  - **No In-App APK Updater**: Play Policy compliance strictly disallows downloading/installing APKs outside Google Play.
  - Official Google Play In-App Updates powered by `com.google.android.play:app-update-ktx` (`PetalPlayUpdateManager.kt` in `src/play/java`).
  - Monetization & Supportive Ads: Opt-in Google AdMob rewarded ads (`src/ads/java`), strictly consent-gated, never triggered in private/incognito tabs.
  - Publishing & metadata managed via `fastlane/metadata`.
  - Omits `REQUEST_INSTALL_PACKAGES` in `src/play/AndroidManifest.xml`.
  - BuildConfig: `IS_PLAY_STORE_BUILD = true`, `IS_FDROID_BUILD = false`, `DISTRIBUTION_CHANNEL = "play"`.

- **`fdroid` (Pure FOSS Variant)**:
  - Distributed via F-Droid repository.
  - Zero Google Play SDKs and zero Ads (`src/noAds/java` and `src/noPlay/java`).
  - BuildConfig: `IS_PLAY_STORE_BUILD = false`, `IS_FDROID_BUILD = true`, `DISTRIBUTION_CHANNEL = "fdroid"`.

## 2. Shared Engine & Architecture Boundaries

- **Single Engine & Downloader**: Mozilla's `AbstractFetchDownloadService` + `GeckoViewFetchClient` serves as the sole byte-moving HTTP engine for downloads across all variants (inheriting cookies, auth, private mode, and proxy directly from Gecko).
- **Source Separation**: Variant-specific code stays strictly partitioned across source sets (`src/play`, `src/noPlay`, `src/ads`, `src/noAds`, `src/github`). Common browser and engine logic stays in `src/main`.
- **Updater Rules**: Keep the in-app updater self-contained for GitHub builds. Never inject Google Play SDKs into non-Play flavors or common modules.

## 3. File Reading & Execution Hygiene

- Inspect targets precisely once, synthesize necessary context, and immediately apply concrete practical fixes.
- **One command per invocation**, no command chaining (`&&` / `||`).
- **Zero local Gradle builds on Termux**. Verification is performed through static code analysis and tests.
- Keep edits small, modular, and reviewable.
