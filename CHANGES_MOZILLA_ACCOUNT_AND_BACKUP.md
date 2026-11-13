# Mozilla account + backup/restore rework

## What was wrong
- "Sign in with email directly" created a signed-in session from a random code. No authentication.
- The web sign-in sheet fell back to `user@firefox.com` and a random code if the redirect lacked data.
- Sync used placeholder keys and unsigned requests to Mozilla storage; real Firefox Sync needs OAuth scoped keys, Hawk-signed requests and Sync15 encryption, so nothing could ever sync.
- Backup wrote FxA access/refresh tokens into the plain JSON; reading-list flag, wallpapers, fonts, userscripts and avatars were not backed up; restore logic was duplicated; the "Export Passwords" button wrote plaintext passwords to Downloads while the row claimed to include the vault.

## What changed
Account / sync (official android-components, same wiring as Fenix BackgroundServices)
- `PetalFxaServices.kt` (new): the only file touching `FxaAccountManager`, `ServerConfig/DeviceConfig/SyncConfig`, `GlobalSyncableStoreProvider`, observers, Send Tab.
- `PetalPlacesSyncBridge.kt` (new): push Petal -> Places, Mozilla sync, pull Places -> Petal. Bookmarks use a 3-way merge by URL so deletes propagate both ways; history mirrors each visit; tabs via RemoteTabsStorage.
- `FxAccountManager.kt`, `PetalMozillaSyncManager.kt`, `PetalTabSyncBridge.kt`: rewritten as thin facades (UI API kept).
- Deleted: `MozillaSyncClient`, `MozillaWebChannelClient`, `CryptoEngine`, `PetalBookmarkSyncBridge`, `PetalHistorySyncBridge`.
- `FirefoxAuthOverlaySheet.kt`: gets Mozilla's real sign-in URL, shows loading/error/retry, completes with the real `code` + `state`, no fallbacks.
- `PetalTabViewController.kt`: redirect handled only for a sign-in this app started.
- Old stored tokens/snapshots are wiped on first launch.

Sign-in UX
- One primary button "Sign in or create account"; secondary "Scan QR from Firefox on your computer" (existing Petal scanner -> real pairing flow); fake email dialog and paste-URL dialog removed.
- (i) info icon on the card + "New here?" link open `FxaGuideSheet` (3 steps, what syncs, QR help). Shown once automatically on first visit.

Backup / restore
- Single `applyBackupJson` used by both restore paths; merges, never wipes; returns a summary toast.
- Reading list flag saved/restored; new `files` section (wallpapers, custom font, userscripts, avatars; size-capped, path-validated) under the Settings toggle.
- Secret preference files (`petal_fx*`, `fxa*`, `mozac*`, tokens, crash) excluded from backup and ignored on restore.
- Passwords: new "Passwords (encrypted)" row; export/import with a user password (AES-GCM, existing vault code). Plaintext export removed.

## Must verify on a real build (could not compile here)
1. `gradle.properties: appServicesVersion` must equal the appservices version of `service-firefox-accounts:159.0.1`:
   `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep appservices`
2. Any signature drift in `PetalFxaServices.kt` (ServerConfig/FxaServer, beginAuthentication, registerForAccountEvents, SyncConfig). Compare with Fenix `BackgroundServices.kt`. Errors will all be in that file or `PetalPlacesSyncBridge.kt`.
3. Sign-in redirect: uses `https://accounts.firefox.com/oauth/success/a2270f727f45f648` (Fennec-era). If Mozilla rejects it, switch `REDIRECT_URL` to Fenix's current webchannel redirect and add the webchannel feature.
4. Release APK grows by the application-services native library (several MB per ABI).
