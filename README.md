<div align="center">

<img alt="Petal, a private Material 3 Expressive browser for Android" src="docs/readme/banner.png" width="100%">

<br><br>

# Petal

**A private, fast Android browser on Firefox's engine, designed with Material 3 Expressive.**

<br>

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-B81D63?style=flat-square&labelColor=2D1537&logo=android&logoColor=white)
![GeckoView 158](https://img.shields.io/badge/GeckoView-158-B81D63?style=flat-square&labelColor=2D1537&logo=firefoxbrowser&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-B81D63?style=flat-square&labelColor=2D1537&logo=kotlin&logoColor=white)
[![Release](https://img.shields.io/github/v/release/shreyagarwal72/petal?style=flat-square&color=B81D63&labelColor=2D1537)](https://github.com/shreyagarwal72/petal/releases/latest)
[![Stars](https://img.shields.io/github/stars/shreyagarwal72/petal?style=flat-square&color=B81D63&labelColor=2D1537)](https://github.com/shreyagarwal72/petal/stargazers)
[![License](https://img.shields.io/badge/License-GPLv3-B81D63?style=flat-square&labelColor=2D1537)](LICENSE.md)

<br>

<a href="https://github.com/shreyagarwal72/petal/releases/latest"><img src="docs/readme/badges/download.svg" height="48" alt="Download the latest APK"></a>

<br><br>

[Features](#features) ·
[Screenshots](#screenshots) ·
[Install](#install) ·
[Permissions](#permissions) ·
[Build](#build-it-yourself) ·
[FAQ](FAQs.md)

</div>

<br>

Petal is a free, open-source browser for Android. It runs on Mozilla's GeckoView engine instead of the Chromium WebView, blocks ads and trackers by default, and follows the Material 3 Expressive design language: dynamic colour, springy motion and shapes that morph.

It has no account, no news feed and no built-in analytics.

<br>

## Screenshots

<div align="center">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-1.png" width="19%" alt="Petal screenshot 1">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-2.png" width="19%" alt="Petal screenshot 2">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-3.png" width="19%" alt="Petal screenshot 3">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-4.png" width="19%" alt="Petal screenshot 4">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-5.png" width="19%" alt="Petal screenshot 5">
</div>

<br>

## Features

<table>
<tr>
<td width="50%" valign="top">
<img src="docs/readme/icons/engine.svg" width="44" height="44" alt=""><br>
<b>Firefox engine</b><br>
GeckoView 158 with full web standards support. It does not depend on your phone's system WebView.
</td>
<td width="50%" valign="top">
<img src="docs/readme/icons/adblock.svg" width="44" height="44" alt=""><br>
<b>Ad and tracker blocking</b><br>
Blocks ads, trackers, crypto miners and fingerprinting scripts before they load, so pages are faster and use less data.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<img src="docs/readme/icons/glass.svg" width="44" height="44" alt=""><br>
<b>Material 3 Expressive</b><br>
Dynamic colour from your wallpaper, ready-made palettes, true-black AMOLED mode and spring motion with haptic feedback.
</td>
<td width="50%" valign="top">
<img src="docs/readme/icons/tabs.svg" width="44" height="44" alt=""><br>
<b>Tabs that stay organised</b><br>
Grid tab switcher, tab groups, private tabs, and sessions that survive a restart.
</td>
</tr>
</table>

**Browsing**
- **Collapsing address bar.** It hides while you scroll. Swipe it sideways to switch tabs.
- **Quick actions.** Long-press the address bar to copy a clean link, paste and go, or bookmark.
- **Firefox add-ons.** Install WebExtensions from the Mozilla add-ons site.
- **PDF viewer.** Open PDFs inside the browser.

**Privacy**
- **Tracking protection on by default.** No cloud account is needed.
- **Privacy shield.** See what a site is blocking, and change it per site.
- **Password manager.** Save and fill logins, with biometric unlock.
- **Site identity switching.** Change how a site sees your browser.

**Tools**
- **Download manager.** Multi-threaded, resumable downloads that keep running in the background.
- **Media grabber.** Find and save media on a page.
- **Built-in player.** Full-screen playback with gesture controls.
- **Optional AI helper.** Summarise or ask about a page. You bring your own provider key (such as Gemini, OpenAI, Anthropic, Groq or Ollama). Page text is sent to that provider only when you use it.
- **Widgets.** Home-screen widgets for quick search.

<br>

## Install

**GitHub Releases (recommended)**

Download the latest APK from the [Releases page](https://github.com/shreyagarwal72/petal/releases/latest), open it, and allow installs from your browser or file manager when Android asks. Petal can update itself from GitHub releases.

**With ADB**

```bash
adb install -r Petal-release.apk
```

<br>

## Permissions

Petal asks only when a feature needs it. The main ones:

| Permission | Why it is used |
| :--- | :--- |
| Internet and network state | Loading pages and checking your connection. |
| Camera and microphone | Only when a site asks, and for QR and voice search. |
| Location | Only when a site asks, and you can say no per site. |
| Photos, media and storage | Uploading files and saving downloads. |
| Notifications and foreground service | Download progress and background media playback. |
| Biometric | Unlocking saved passwords. |
| Vibrate | Haptic feedback. |
| Install packages | The built-in updater for GitHub releases. |
| Draw over other apps (optional) | The floating island on devices without Android 16 Live Updates. Off until you turn it on. |

<br>

## Build it yourself

You need JDK 17 or newer and the Android SDK.

```bash
git clone https://github.com/shreyagarwal72/petal.git
cd petal

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

The release APK is written to `app/build/outputs/apk/release/`.

<br>

## Built with

| | |
| :--- | :--- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 Expressive |
| Engine | Mozilla GeckoView 158 |
| Architecture | MVVM, Kotlin Coroutines and Flow, Hilt |
| Privileged actions | Shizuku (optional) |
| Platform | minSdk 26 (Android 8.0), targetSdk 36 |

More detail is in [architecture.md](architecture.md) and [design.md](design.md).

<br>

## Contributing

Bug reports, ideas and pull requests are welcome. Please read the [code of conduct](CODE_OF_CONDUCT.md) first, and open an issue before starting a large change.

## Thanks

- [Mozilla GeckoView](https://mozilla.github.io/geckoview/) for the engine.
- [Material 3 Expressive](https://m3.material.io/) for the design language.
- [Shizuku](https://shizuku.rikka.app/) for the optional privileged API.

## License

Petal is free software under the [GNU General Public License v3.0](LICENSE.md).

Copyright (C) 2024-2026 Vanshu Agarwal and contributors.
