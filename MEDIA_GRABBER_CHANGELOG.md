# Media Grabber rebuild

## Added
- `media/ytdlp/PetalYtDlpEngine.kt`: native yt-dlp + ffmpeg (youtubedl-android). Init, binary update, format listing, download with progress/cancel/retry, Gecko cookies, saved to the normal Downloads folder. Progress is a `DownloadSource.YTDLP` row in the download manager and live alerts.
- `media/ytdlp/PetalYtDlpModels.kt`: option builder (quality list, size estimates, audio options) and progress-speed parser.
- `media/ytdlp/PetalMediaGrabberSheet.kt`: new grabber sheet on `PetalSheet` + `PetalSheetPill` (the style of the tab-actions sheet). Header with thumbnail and title, Video/Audio toggle, quality picker, Download pill, Page resources shortcut, Copy link. Because it uses `PetalSheet`, liquid glass and blur follow the existing bottom-sheet settings; no new settings were added.
- Unit tests: `YtDlpOptionBuilderTest`.

## Changed
- `PetalMediaSnifferOverlay` now opens `PetalMediaGrabberSheet` (address bar button, Quick Tools and banner all go through it).
- `app/build.gradle`: youtubedl-android `library` and `ffmpeg` 0.18.1 (jitpack is already a repo). Existing legacy native packaging kept.
- `proguard-rules.pro`: keep rules for yt-dlp (release builds minify).

## Kept as is
- Capture pipeline (`media/sniffer/capture`, `MediaInterceptor`, `PetalNativeSnifferEngine`): it already contains the Clint-style manifest parser and metadata probe. `PetalResourceSnifferSheet` is reached from "Page resources".
- `YouTubeExtractor` remains a fallback for direct streams in the sniffer list.

## Needs a device build to verify
- The youtubedl-android version and the `VideoFormat`/`VideoInfo` accessors (formatId, fileSize, fileSizeApproximate, fps, etc.) were written from memory of the library; I had no network or compiler here.
- First launch unpacks yt-dlp, so the first fetch is slower.

## Fix: sheet never opened from the address bar
- Root cause: `PetalMediaSnifferOverlayBridge.bind()` was never called anywhere, so the old overlay was never hosted and `openMediaSheet()` flipped a flag nobody read.
- `PetalRootApp` now hosts `PetalMediaGrabberSheet` and shows it when `isSheetForcedOpen` is set (address bar, quick tools, overflow menu).
- `openPlayer()` extracted into the bridge so Play works from the sheet.
- Address bar media button now also shows on known yt-dlp sites (YouTube, Vimeo, X, Instagram, TikTok, etc.) before anything is sniffed.
