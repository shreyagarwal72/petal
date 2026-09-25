# 🌸 Petal Browser

<div align="center">

  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" height="128" alt="Petal Browser App Icon" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.15);" />

  <h2>Petal Browser</h2>

  <p><strong>Fast, Modern, Privacy-First Android Browser powered by Mozilla GeckoView &amp; Jetpack Compose Material 3 Expressive Design</strong></p>

  <p>
    <a href="https://play.google.com/store/apps/details?id=com.petal.browser" target="_blank">
      <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="58" alt="Get it on Google Play" />
    </a>
    <a href="https://github.com/shreyagarwal72/petal/releases/latest" target="_blank">
      <img src="https://img.shields.io/badge/Get%20App%20Here-Direct%20APK%20Download-0075ff?style=for-the-badge&logo=android&logoColor=white" height="42" alt="Get App Here (Direct APK)" />
    </a>
  </p>

  <p>
    <a href="https://github.com/shreyagarwal72/petal/releases/latest"><img src="https://img.shields.io/github/v/release/shreyagarwal72/petal?color=0075ff&style=for-the-badge&logo=github" alt="Latest Release" /></a>
    <a href="https://github.com/shreyagarwal72/petal/actions/workflows/android_build.yml"><img src="https://img.shields.io/github/actions/workflow/status/shreyagarwal72/petal/android_build.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white" alt="Build Status" /></a>
    <a href="https://t.me/championworkspace"><img src="https://img.shields.io/badge/Telegram-Champion%20Workspace-0088cc?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
    <a href="LICENSE.md"><img src="https://img.shields.io/github/license/shreyagarwal72/petal?color=7952b3&style=for-the-badge" alt="License" /></a>
    <a href="https://github.com/sponsors/shreyagarwal72"><img src="https://img.shields.io/badge/Sponsor%20Petal-GitHub%20Sponsors-ea4aaa?style=for-the-badge&logo=githubsponsors&logoColor=white" alt="Sponsor" /></a>
  </p>

</div>

---

## 🚀 Download & Installation

Choose your preferred installation method:

<div align="center">

| Platform | Channel | Link |
| :--- | :--- | :--- |
| **Google Play Store** | Official Store Release | [![Get it on Google Play](https://img.shields.io/badge/Google_Play-414141?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.petal.browser) |
| **GitHub Releases** | Direct Universal / ABI APKs | [![Download APK](https://img.shields.io/badge/Get_App_Here-Direct_APK-0075ff?style=for-the-badge&logo=android&logoColor=white)](https://github.com/shreyagarwal72/petal/releases/latest) |
| **Telegram Channel** | Direct Updates & Beta Builds | [![Telegram](https://img.shields.io/badge/Telegram-Beta_Channel-0088cc?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/championworkspace) |

</div>

---

## 🌟 Comprehensive Architecture & Feature Audit

Petal Browser combines Mozilla GeckoView with a reactive UI built in Jetpack Compose and Material 3 Expressive Design.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Petal Browser Architecture                        │
├──────────────────────────────────────┬──────────────────────────────────────┤
│          UI & Interaction Layer      │          Engine & Storage Layer      │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ • Jetpack Compose M3 Expressive      │ • Mozilla GeckoView 155.0.1 Engine   │
│ • 35 Dynamic Polygon Geometric Shapes│ • Firefox Android Components Stack   │
│ • Predictive Back & Depth Blur       │ • Strict Firefox Tab Thumbnail Cache │
│ • Dual Living Variable Backdrops     │ • Multi-threaded uAssets & HaGeZi AdB│
│ • Compose Multi-Engine Omnibox       │ • yt-dlp & Fetch2 Background Engine  │
│ • Inbuilt WebExtensions Manager      │ • Passkey, WebAuthn & Biometric Lock │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

---

## ✨ Key Capabilities & Highlights

### 🦊 Mozilla GeckoView 155 Engine & Full Web Standards
- **Real Desktop & Mobile Web Rendering**: Powered by modern Mozilla GeckoView (v155 release channel) and Mozilla Android Components (`browser-engine-gecko`, `concept-engine`, `browser-session-storage`).
- **Complete Extension Ecosystem**: Supports Firefox Add-ons as well as Petal Inbuilt Builtin Extensions (MediaGrabber, Universal Copy, AI Blocker, Clean Link, Dark Webpages, Petal Translate, and Google Search Fixer).
- **Standards-Compliant Progressive Web Apps (PWA)**: Recreated Web App installation workflow matching Firefox with live icon preview, manifest parsing, and standalone home-screen launch.

### 🖼️ Tab Thumbnail Cache System (Firefox Parity)
- **Multi-Tiered LRU Storage**: Sized dynamically to application RAM limits (8MB - 48MB) with aspect-ratio preserving downscaling.
- **Strict Private Browsing Isolation**: Incognito/private thumbnails are maintained strictly in volatile RAM and **never written to disk storage**, purged instantly upon session close.
- **Zero-Leak Eviction**: Multi-identifier eviction (`tabId`, `hashCode`, URL) guarantees closed tabs leave zero footprint in memory or disk.
- **Async Non-Blocking I/O**: Multi-threaded disk cache persistence with stale write cancellation prevents UI thread stutters during tab switcher scrolling.

### 🎨 Material 3 Expressive Design & Fluid Motion
- **35 Material 3 Expressive Shapes**: Every card, search pill, button, and shortcut renders with dynamic polygon geometry (squircle, petal, diamond, flower, cookie, and pill).
- **Living Variable Background**: Dynamic procedural ambient backdrop responding smoothly to daylight and palette shifts.
- **Predictive Back & Depth Blur Physics**: Smooth back gesture with real-time GPU background scaling and dual-surface depth blur.
- **Dynamic Theming & AMOLED Pure Black**: Full support for Android 12+ wallpaper dynamic color extraction, tonal palettes, and true AMOLED `#000000` pitch black mode.
- **Expressive Typography**: Dynamic variable font scaling with real-time preview and instant layout switching.

### 🛡️ Privacy, Content Filtering & Security Settings
- **Multi-Tier Tracking Protection**: Native GeckoView tracking protection with strict cookie sandboxing and fingerprinting defenses.
- **Integrated Content Filtering**: Built-in EasyList, EasyPrivacy, HaGeZi Multi PRO, and uAssets cosmetic and network blocking rules.
- **Hardware Biometric Lock**: Secure tab locker and app lock powered by AndroidX Biometric and Keystore encryption.
- **Zero Telemetry**: No background tracking, analytical SDKs, or third-party behavioral profiling.

### 📥 High-Speed Resumable Download Manager & Social Downloader
- **Multi-Threaded Resumable Engine**: Built on Fetch2 and OkHttp with chunked downloads, pause, resume, speed indicators, and background fetch services.
- **Active Download Lifecycle Management**: Instant cancellation and notification removal upon deletion, with stale download filtering and auto-retry on reconnect.
- **Social Downloader Support**: Built-in media extraction powered by `youtubedl-android` (yt-dlp, FFmpeg, aria2c) for video, audio, and media grabber workflows.

### 🤖 Petal AI Hub
- **Multi-Model Integration**: Connect your own API keys for **Google Gemini**, **Grok (xAI)**, **OpenAI**, **Anthropic Claude**, or **Groq / OpenRouter**.
- **Page Summaries & Deep Research**: Summarize complex web pages or run in-depth research queries with citations in a single tap.
- **Contextual In-Page Actions**: Select text on any webpage to translate, explain, rephrase, or query AI assistants instantly.

---

## 📊 Technical Specifications

| Component | Specification |
| :--- | :--- |
| **Target Android OS** | Android 15 & 16 Ready (API 35/36) |
| **Minimum Android OS** | Android 8.0 Oreo (API 26) |
| **Web Rendering Engine** | Mozilla GeckoView 155.0.1 Release Channel |
| **UI Framework** | Jetpack Compose BOM 2026.06.01 / Material 3 Expressive 1.5.0 |
| **Languages** | Kotlin 2.0+ & Java 17 |
| **Architecture** | MVVM + Clean Architecture + Dagger Hilt 2.58 |
| **Image Loading** | Coil 2.7.0 (Kotlin Coroutines) |
| **Download Stack** | Fetch2 3.4.1 + OkHttp 4.12.0 + youtubedl-android 0.18.1 |
| **ABI Support** | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (Universal APK included) |

---

## 🛠️ Building From Source

To build the APK locally:

```bash
# 1. Clone the repository
git clone https://github.com/shreyagarwal72/petal.git
cd petal

# 2. Build the Debug APK
./gradlew assembleDebug

# 3. Locate the output APK at:
# app/build/outputs/apk/debug/app-debug.apk
```

To assemble a universal release APK:
```bash
./gradlew assembleRelease
```

---

## 💖 Upstream Projects & Open Source Credits

Petal Browser is open-source software built on top of incredible community foundations:

- **[Mozilla Firefox & GeckoView](https://github.com/mozilla-mobile/firefox-android)** by *Mozilla* — Advanced GeckoView rendering engine, WebExtensions architecture, and Android Components.
- **[Zenith](https://github.com/1372Slash/Zenith)** by *1372Slash* — Material Design 3 Expressive motion physics & digital wellbeing framework.
- **[LastWave](https://github.com/duxtami/LastWave-native)** by *duxtami* — Hi-Res lossless audio streaming architecture & Material 3 design.
- **[Aurora Store](https://github.com/whyorean/AuroraStore)** by *whyorean (Rahul Patel)* — Modern Material design patterns & elegant app architecture.
- **[RvSystem-Monitor](https://github.com/Rve27/RvSystem-Monitor)** by *Rve27* — Real-time system monitoring & Compose hardware insights.
- **[Ever-Haptics](https://github.com/hari161008/Ever-Haptics)** by *hari161008* — Waveform haptic vibration synthesis & tactile interaction.
- **[PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer)** by *PixelPlayerHQ* — Dynamic palette styling and morphing squircle motion framework.
- **[Fetch](https://github.com/tonyofrancis/Fetch)** by *tonyofrancis* — Multi-threaded background download engine.
- **[Coil](https://github.com/coil-kt/coil)** by *coil-kt* — Image and favicon caching pipeline.
- **[Material 3 Expressive](https://m3.material.io)** by *Google Android Jetpack Team* — Expressive component geometry and dynamic color palettes.

---

## 📜 License

Licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE.md)**.  
Free and Open Source Software. You are welcome to redistribute and modify it under the terms of the GPL-3.0.
