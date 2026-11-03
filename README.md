<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/hero-dark.png">
    <img src="docs/readme/hero-light.png" alt="Petal, a private open source browser for Android in Material 3 Expressive" width="100%">
  </picture>
</p>

<p align="center">
  <a href="https://github.com/shreyagarwal72/petal/stargazers"><img src="https://img.shields.io/github/stars/shreyagarwal72/petal?style=flat-square&logo=github&color=B81D63&labelColor=FFD9E3" alt="Stars"></a>
  <a href="https://github.com/shreyagarwal72/petal/network/members"><img src="https://img.shields.io/github/forks/shreyagarwal72/petal?style=flat-square&logo=github&color=0F6A6F&labelColor=FFD9E3" alt="Forks"></a>
</p>

<p align="center">
  A private, fast browser for Android that runs on Firefox's engine, blocks ads by default, and looks like it belongs on your phone.<br>
  <strong>No account. No news feed. No sign-in wall.</strong>
</p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/chips-dark.png">
    <img src="docs/readme/chips-light.png" alt="Firefox engine, Jetpack Compose, Material 3 Expressive, ad blocking, open source" width="720">
  </picture>
</p>

<br>

<p align="center">
  <a href="https://github.com/shreyagarwal72/petal/releases/latest">
    <img src="https://img.shields.io/github/v/release/shreyagarwal72/petal?style=for-the-badge&logo=github&label=Version&color=B81D63&labelColor=7B2949" alt="Latest version">
  </a>
  <a href="https://github.com/shreyagarwal72/petal/releases">
    <img src="https://img.shields.io/github/downloads/shreyagarwal72/petal/total?style=for-the-badge&logo=android&logoColor=white&label=Downloads&color=0F6A6F&labelColor=0B4F53" alt="Total downloads">
  </a>
  <a href="LICENSE.md">
    <img src="https://img.shields.io/badge/License-GPLv3-6D4AA8?style=for-the-badge&labelColor=4A3578" alt="GPLv3 license">
  </a>
</p>

<br>

<details>
<summary><b>Petal and trackers: what is in the app?</b></summary>

<br>

Privacy scanners such as Exodus Privacy or TrackerControl may flag Petal, so here is the full picture.

**Ad SDK.** Petal includes the **Unity Ads SDK** for the optional "Fuel the Code" support quest in About, where you can watch a rewarded video to support the developer. All the code that uses it is in `account/progress/PetalUnityRewardedAds.kt`. Scanners flag Unity Ads by name, so Petal will show up as having an ad library.

**Everything else that uses the network, besides the sites you visit:**

- **Firefox account sync (optional).** If you sign in, synced data goes to Mozilla's sync servers.
- **Petal AI (optional).** You add your own key for a provider such as Gemini, OpenAI, Anthropic, Groq or Ollama. Text you ask about goes to that provider.
- **Updates.** The built-in updater checks GitHub releases.
- **Crash reports.** Nothing is sent automatically. After a crash you can choose to report it on GitHub, copy the log or share it as a zip.

Petal has no analytics SDK in its dependencies. You can check this yourself in `app/build.gradle` and by searching the network calls under `app/src/main/java/com/petal/browser/`.

</details>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-1.svg" height="26" align="top"> Overview

Petal is an Android browser built on **Mozilla GeckoView**, the engine behind Firefox, instead of the Chromium WebView that most Android browsers use. The interface is written in Kotlin and Jetpack Compose and follows **Material 3 Expressive**: dynamic colour from your wallpaper, spring motion, and shapes that morph as you touch them.

> Petal is for people who want a fast, good-looking browser that stays out of the way and gives them the controls.

At a glance:

- Firefox's GeckoView engine, with Firefox add-on support
- Built-in ad and tracker blocking with per-site controls
- Liquid glass panels, dynamic colour and a true-black AMOLED theme
- Grid tab switcher, tab groups, private tabs and a password manager

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-2.svg" height="26" align="top"> What makes Petal different

Most Android browsers are Chromium wrappers with a sign-in screen, a news feed and a pile of services behind them. Petal keeps the browser small and moves the important parts into code you can read: the ad blocker is a Rust engine in `native/adblock_rs`, the filter lists are public, and the interface is plain Compose.

The design follows the same idea. Petal does not hide settings behind a cloud account. It gives you the controls, such as blocking per site, back-gesture styles and glass strength, and remembers them on your phone.

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-3.svg" height="26" align="top"> Feature atlas

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-engine.svg" width="64" alt="Firefox engine icon"><br>
      <strong>Firefox engine</strong><br>
      GeckoView 158 with full web standards support. Petal does not depend on your phone's system WebView, so pages behave the same on every device.<br><br>
      <code>GeckoView 158</code> <code>Multi-process</code> <code>Web standards</code>
    </td>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-adblock.svg" width="64" alt="Ad blocking icon"><br>
      <strong>Ad and tracker blocking</strong><br>
      A native Rust blocking engine with public filter lists. The privacy shield shows what a page blocked and lets you trust or filter each site.<br><br>
      <code>Rust engine</code> <code>Per-site rules</code> <code>Shield HUD</code>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-design.svg" width="64" alt="Expressive design icon"><br>
      <strong>Expressive design</strong><br>
      Liquid glass panels with adjustable blur and refraction, colours that follow your wallpaper, true-black AMOLED, and five back-gesture animation styles.<br><br>
      <code>Liquid glass</code> <code>Dynamic colour</code> <code>AMOLED</code> <code>Predictive back</code>
    </td>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-tabs.svg" width="64" alt="Tabs icon"><br>
      <strong>Tabs that stay organised</strong><br>
      A visual grid switcher, tab groups with drag to merge, private tabs, and sessions that come back after a restart. Swipe the address bar to switch tabs.<br><br>
      <code>Grid switcher</code> <code>Groups</code> <code>Private tabs</code>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-privacy.svg" width="64" alt="Passwords and privacy icon"><br>
      <strong>Passwords and privacy</strong><br>
      A built-in password manager with biometric unlock and passkey support, plus site identity switching when you want a site to see a different browser.<br><br>
      <code>Passwords</code> <code>Passkeys</code> <code>Biometric unlock</code>
    </td>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-extensions.svg" width="64" alt="Extensions icon"><br>
      <strong>Firefox add-ons</strong><br>
      Install WebExtensions from the Mozilla add-ons site. Petal also ships a few of its own, such as dark pages, clean links and universal copy.<br><br>
      <code>WebExtensions</code> <code>Dark pages</code> <code>Clean links</code>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-downloads.svg" width="64" alt="Downloads icon"><br>
      <strong>Downloads and media</strong><br>
      A download manager with resume and background downloads, a media grabber for pages with video or audio, and a full-screen player with picture-in-picture.<br><br>
      <code>Resumable</code> <code>Media grabber</code> <code>Picture-in-picture</code>
    </td>
    <td width="50%" valign="top">
      <img src="docs/readme/feature-ai.svg" width="64" alt="Petal AI icon"><br>
      <strong>Petal AI</strong><br>
      Ask a question or search from the same box. Bring your own key for Gemini, OpenAI, Anthropic or Groq, or point it at Ollama. Web grounding is a switch you control.<br><br>
      <code>Bring your own key</code> <code>Web grounding</code> <code>Optional</code>
    </td>
  </tr>
</table>

### And a lot more

- **Collapsing address bar:** it springs out of the way while you scroll. Long-press it for clean link copy, paste and go, and bookmark.
- **PDF viewer:** open PDFs inside the browser.
- **Firefox account sync:** optional sync through Mozilla's servers.
- **Install as an app:** add sites to your home screen as web apps.
- **QR codes and widgets:** make a QR code for a page, and use home-screen widgets for quick search.
- **Haptics:** distinct vibration patterns for taps, swipes and seeking.

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-4.svg" height="26" align="top"> Screenshots

<table>
  <tr>
    <td width="33%" align="center" valign="top"><img src="docs/readme/screenshots/01-home.jpg" alt="Home screen with liquid glass panels" width="100%"><br><sub>Home</sub></td>
    <td width="33%" align="center" valign="top"><img src="docs/readme/screenshots/02-glass.jpg" alt="Liquid glass settings with live preview" width="100%"><br><sub>Tune the glass</sub></td>
    <td width="33%" align="center" valign="top"><img src="docs/readme/screenshots/03-back.jpg" alt="Five back-gesture animation styles" width="100%"><br><sub>Back-gesture styles</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center" valign="top"><img src="docs/readme/screenshots/04-ai.jpg" alt="Petal AI chat and search" width="100%"><br><sub>Petal AI</sub></td>
    <td width="33%" align="center" valign="top"><img src="docs/readme/screenshots/05-privacy.jpg" alt="Privacy shield with per-site controls" width="100%"><br><sub>Privacy shield</sub></td>
    <td width="33%" align="center" valign="middle">
      <strong>Get Petal</strong><br><br>
      <a href="https://github.com/shreyagarwal72/petal/releases/latest"><img src="https://img.shields.io/badge/Download-APK-B81D63?style=for-the-badge&logo=android&logoColor=white&labelColor=7B2949" alt="Download the APK"></a>
    </td>
  </tr>
</table>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-5.svg" height="26" align="top"> Install

**From GitHub Releases (recommended).** Download the latest APK from the [Releases page](https://github.com/shreyagarwal72/petal/releases/latest), open it, and allow installs from your browser or file manager when Android asks. After that, Petal can update itself from GitHub releases.

**With ADB:**

```bash
adb install -r Petal-release.apk
```

### Permissions

Petal asks only when a feature needs it.

| Permission | Why it is used |
| :--- | :--- |
| Internet and network state | Loading pages and checking your connection. |
| Camera and microphone | Only when a site asks, and for QR and voice search. |
| Location | Only when a site asks. You can say no per site. |
| Photos, media and storage | Uploading files and saving downloads. |
| Notifications and foreground service | Download progress and background media playback. |
| Biometric | Unlocking saved passwords. |
| Vibrate | Haptic feedback. |
| Install packages | The built-in updater for GitHub releases. |
| Draw over other apps (optional) | The floating island on phones without Android 16 Live Updates. Off until you turn it on. |

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-6.svg" height="26" align="top"> How the project is structured

Petal is a single-app Kotlin project with a native Rust module. A tour of the code lives in [architecture.md](architecture.md), and the visual rules are in [design.md](design.md).

Paths below are inside <code>app/src/main/java/com/petal/browser/</code> unless noted.

<table>
  <tr>
    <td><strong>Area</strong></td>
    <td><strong>Responsibility</strong></td>
  </tr>
  <tr>
    <td><code>ui/</code></td>
    <td>Shared components, the containment design, glass effects and the theme</td>
  </tr>
  <tr>
    <td><code>compose/</code></td>
    <td>Screens: home, tabs, settings, downloads, PDF viewer, AI and extensions</td>
  </tr>
  <tr>
    <td><code>engine/</code></td>
    <td>GeckoView integration and the ad-blocking layer</td>
  </tr>
  <tr>
    <td><code>media/</code> <code>download/</code></td>
    <td>The media player and sniffer, and the download service</td>
  </tr>
  <tr>
    <td><code>passwords/</code> <code>privacy/</code> <code>sync/</code></td>
    <td>Password storage, the privacy shield and account sync</td>
  </tr>
  <tr>
    <td><code>native/adblock_rs/</code></td>
    <td>The Rust ad-blocking engine, built on Brave's adblock-rust (repo root)</td>
  </tr>
  <tr>
    <td><code>baselineprofile/</code></td>
    <td>Startup and scroll performance profile (repo root)</td>
  </tr>
  <tr>
    <td><code>site/</code></td>
    <td>The project website (repo root)</td>
  </tr>
</table>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-7.svg" height="26" align="top"> Building

You need **JDK 17 or newer** and the Android SDK.

```bash
git clone https://github.com/shreyagarwal72/petal.git
cd petal

./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK
```

The release APK is written to `app/build/outputs/apk/release/`. Or open the folder in Android Studio and run the `app` configuration on a device.

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## <img src="docs/readme/shape-8.svg" height="26" align="top"> Contributing

Bug reports, ideas and pull requests are welcome, especially for browsing reliability, interface polish, privacy and documentation. Read the [code of conduct](CODE_OF_CONDUCT.md) first, and open an issue before starting a large change. For the visual rules, start with [design.md](design.md).

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/readme/divider-dark.svg">
    <img src="docs/readme/divider-light.svg" alt="" width="100%">
  </picture>
</p>

## Credits

- **[Mozilla GeckoView](https://mozilla.github.io/geckoview/)** for the browser engine.
- **[adblock-rust](https://github.com/brave/adblock-rust)** by Brave (MPL-2.0) for the ad-blocking engine.
- **[Hagezi](https://github.com/hagezi/dns-blocklists)** and **[uBlock Origin uAssets](https://github.com/uBlockOrigin/uAssets)** for the filter lists. Their licences are in `site/assets/licenses/`.
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** by RikkaApps for the optional privileged API.
- **[Material 3 Expressive](https://m3.material.io/)** for the design language.

## License

Petal is free software under the [GNU General Public License v3.0](LICENSE.md).

<p align="center">
  Made by <strong>Vanshu Agarwal</strong> at NextUp Studio
</p>
