# Petal download engines - audit, Mozilla comparison, and master prompt (2026-11-07)
Static audit only; nothing compiled or run. Mozilla comparison is limited to the official feature-downloads README
(searchfox, firefox-android/android-components/components/feature/downloads/README.md) - the Kotlin source of
AbstractFetchDownloadService / DownloadMiddleware was NOT read, so behaviours not in the README are marked "verify".

## 1. What exists
| # | Engine | Files | Role today |
|---|---|---|---|
| 1 | Mozilla pipeline (AbstractFetchDownloadService, FetchDownloadManager, DownloadMiddleware, GeckoViewFetchClient) | download/PetalMozillaDownloadService, PetalMozillaFetchClient, engine/gecko/PetalEngineStore:124, PetalFetchDownloadBridge.enqueueMozillaDownload, PetalUpdateManager:190 | Primary for ALL browser downloads via PetalDownloadRouter (comments say Mozilla is the "only engine") |
| 2 | Petal parallel Range layer | download/PetalParallelFetchClient (524 lines) | Wraps the Mozilla client; segmented Range, ETag/206/Content-Range verified, adaptive backoff, max 8 connections. Well designed |
| 3 | Fetch2 (tonyodev) + OkHttp | download/PetalDownloadEngine.java, PetalFetchDownloadBridge (downloadsMap, refresh, fetchInstance), compose/downloads/PetalDownloadService | LEGACY, still alive: bridge keeps a second state map (Fetch2 `downloadsMap`) next to `mozillaDownloadsMap`; PetalDownloadEngine.getInstance used once. UI (manager screen, banner, live alerts) must merge both |
| 4 | OkHttp fallback client | PetalOkHttpFetchClient, PetalFallbackFetchClient | Used if GeckoViewFetchClient fails OR throws any Throwable (retries the whole request) |
| 5 | yt-dlp + aria2c + ffmpeg | media/ytdlp/PetalYtDlpEngine, PetalSocialDownloadService | Social/media. Init fails in the supplied logs (`class tv is not a concrete class` -> R8) |
| 6 | Torrent | torrent/PetalTorrentEngineManager (122 lines) | Only hands web seeds / .torrent URLs back to the router; no real torrent engine |
| 7 | External managers | unit/ExternalDownloadManagerHelper | 1DM/ADM/etc. hand-off |
Other: PetalSmartDownloadRouter (folder routing; effectively a no-op on Android 10+ because MediaStore needs the public Downloads dir), PetalDownloadHeaderRegistry (per-URL header cache, in memory, 12h TTL), media sniffer (detection feeding the router), DownloadMiddleware wired in PetalEngineStore with `deleteFileFromStorage = { true }`.

## 2. Findings vs Mozilla's official design
Matches Mozilla (good): Gecko network stack via GeckoViewFetchClient (Gecko cookies/private/proxy apply); AbstractFetchDownloadService owns notifications/pause/resume; DownloadMiddleware in the BrowserStore; RestoreDownloadsStateAction at startup; manifest service declares foregroundServiceType=dataSync and FOREGROUND_SERVICE(_DATA_SYNC) permissions as the README requires.
Gaps / bugs:
1. WRONG COOKIE JAR. PetalFetchDownloadBridge.enqueueGeckoDownload (:210), PetalDownloadRouter (:83), ImageActionHelper, ExternalDownloadManagerHelper and the media/yt-dlp paths read android.webkit.CookieManager. Gecko never writes to it, so cookies are empty/stale. For Gecko-fetched downloads the Gecko client already sends Gecko cookies (manually injecting a second Cookie header is useless or conflicting); for OkHttp fallback, yt-dlp, and external managers there are no real session cookies -> logged-in downloads fail. Fix: read cookies from Gecko (StorageController / a cookie-bridge via WebExtension cookies API or GeckoSession) and pass the right ones per path.
2. Router over-writes request headers: always sets Referer=url when none, Accept=text/html..., Cache-Control, Connection, and a hard-coded UA (rv:128) - wrong for media/binary and differs from the real Gecko UA. Mozilla only sends the page referrer. Send only referrer + registered signed-URL headers; use the live session UA.
3. Header registry is per URL (collides for same URL, e.g. two tabs/private vs normal) and in memory (lost on process death, so resumed downloads lose Referer/Cookie/UA -> 403). Persist per download id (DownloadState has no header field - store in DB keyed by download id/URL+private flag).
4. Fallback client swallows every Throwable and re-fetches with OkHttp without Gecko cookies/private mode: a transient network error can start a second, cookie-less request and leak private downloads outside Gecko. Fallback only when GeckoViewFetchClient cannot be created, never in private mode.
5. Two state systems (Mozilla store + Fetch2). Duplicated IDs (`id.hashCode().toLong()`), isMozillaOwned checks, two notification owners. Collision-prone hash IDs. Remove Fetch2 or make it the plugin for non-browser sources only.
6. SmartDownloadRouter is a no-op on API 29+; implement via MediaStore.Downloads RELATIVE_PATH or remove the setting.
7. DownloadMiddleware `deleteFileFromStorage = { true }` is a stub: verify deleting a download removes the file/MediaStore row (verify against Mozilla source).
8. yt-dlp init failure (R8) and missing libadblock_petal.so are separate packaging bugs (see below).
9. PetalTorrentEngineManager is not an engine - either integrate libtorrent (jlibtorrent) or drop the claim in UI.
10. Verify (not in README): resume with If-Range/ETag, disk-space check, checksum, retry/backoff in the Mozilla service path, AbstractFetchDownloadService behaviour on process death.

## 3. Master prompt (paste into Claude Code at repo root, with a compiler and device)
You are in the Petal Android browser repo. Read docs/DOWNLOAD_AUDIT_AND_MASTER_PROMPT.md. Goal: ONE reliable download core = Mozilla AbstractFetchDownloadService + GeckoViewFetchClient + PetalParallelFetchClient, with yt-dlp, torrent and external managers as thin plugins behind one DownloadSource interface, one state store, one notification owner. Work through all stages in one session; build and test after each; do not change browser engine code outside downloads.
Constraints: keep Petal download UI (manager screen, banner, island/live alerts, confirmation dialog); never run two GeckoView versions; keep PetalParallelFetchClient safety rules (206 + Content-Range + ETag + exact length); private downloads must never leave Gecko's network stack or touch non-private storage.
Stage A - Cookies/headers: replace every android.webkit.CookieManager read in download paths with a Gecko-backed cookie provider; stop injecting Cookie into Gecko-fetched requests; router sends only referrer + registered headers + real session UA; persist header registry (per download id + private flag) so resume after process death works.
Stage B - Fallback safety: OkHttp fallback only when Gecko client creation fails, never in private mode, never after a mid-stream failure.
Stage C - Single state: remove Fetch2 and PetalDownloadEngine for browser downloads (or confine it to plugins); one repository keyed by stable IDs (no hashCode); UI reads that repository only; delete mozillaDownloadsMap/downloadsMap duplication.
Stage D - Plugins: define DownloadSource (HTTP/Mozilla, yt-dlp, torrent, external). Fix yt-dlp R8 (keep rules for youtubedl-android/Chaquopy, verify initPython works in release). Either integrate a real torrent engine or remove the feature claim.
Stage E - Quality: free-space check before start, ETag/If-Range resume verification, retry with backoff, optional checksum, SmartDownloadRouter via MediaStore.Downloads RELATIVE_PATH (or remove), verify DownloadMiddleware delete actually removes files, Android 14+ dataSync timeout handling (onTimeout), battery-optimization prompt.
Stage F - Compare against Mozilla source: read AbstractFetchDownloadService.kt, DownloadMiddleware.kt, DownloadStorage, DownloadsFeature in the official repo for the exact pinned version (android-components 159.0.1) and fix any deviation; document differences you keep on purpose.
Checks: assembleDebug/Release clean; unit tests for header registry persistence, fallback rules, repository merge; instrumented: logged-in download, private download, pause/resume across process kill, 4GB file via Range, server without Range, 403/429 degradation, notification actions (pause/resume/cancel/open), Android 14 foreground service timeout, yt-dlp release build download, external manager hand-off.
Deliverable: per-stage report (changed, tested, unverified), commit per stage on a branch.
