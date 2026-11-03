<div align="center">

<img src="docs/readme/hero-light.png" alt="Petal Browser" width="100%">

<br><br>

# Petal

**A private, fast Android browser on Firefox's GeckoView engine, designed with Material 3 Expressive.**

<br>

<p align="center">
  <a href="https://github.com/shreyagarwal72/petal/releases/latest"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-download.svg"><img src="docs/readme/light/btn-download.svg" alt="Download APK" height="50"></picture></a>
  <a href="https://github.com/shreyagarwal72/petal"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-github.svg"><img src="docs/readme/light/btn-github.svg" alt="GitHub Source" height="50"></picture></a>
  <a href="https://github.com/shreyagarwal72/petal/issues"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-issues.svg"><img src="docs/readme/light/btn-issues.svg" alt="Report an Issue" height="50"></picture></a>
  <a href="https://t.me/petalbrowser"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/btn-telegram.svg"><img src="docs/readme/light/btn-telegram.svg" alt="Telegram Channel" height="50"></picture></a>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-gecko.svg"><img src="docs/readme/light/badge-gecko.svg" alt="GeckoView 159" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-rustadblock.svg"><img src="docs/readme/light/badge-rustadblock.svg" alt="Rust Adblock" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-m3e.svg"><img src="docs/readme/light/badge-m3e.svg" alt="Material 3 Expressive" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-adfree.svg"><img src="docs/readme/light/badge-adfree.svg" alt="100% Ad-Free" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-privacy.svg"><img src="docs/readme/light/badge-privacy.svg" alt="Zero Telemetry" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-extensions.svg"><img src="docs/readme/light/badge-extensions.svg" alt="Add-ons Ready" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-android.svg"><img src="docs/readme/light/badge-android.svg" alt="Android 8.0+" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-kotlin.svg"><img src="docs/readme/light/badge-kotlin.svg" alt="Kotlin Compose" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-gpl.svg"><img src="docs/readme/light/badge-gpl.svg" alt="GPL-3.0" height="28"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/badge-prs.svg"><img src="docs/readme/light/badge-prs.svg" alt="PRs Welcome" height="28"></picture>
</p>

<br>

[Features](#features) · [Screenshots](#screenshots) · [Quick Facts](#quick-facts) · [Permissions](#permissions) · [Build](#build-it-yourself) · [FAQ](FAQs.md)

</div>

<br>

Petal is a private, open-source browser for Android built from the ground up on Mozilla's GeckoView engine instead of standard Chromium WebView. It blocks ads, tracking beacons, and crypto miners before they execute, combined with fluid Material 3 Expressive spring physics, adaptive dynamic coloring, and deep Firefox add-on integration.

<br>

<h2 id="quick-facts"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-lightning.svg"><img src="docs/readme/light/icon-lightning.svg" width="24" height="24" align="center"></picture> Quick Facts</h2>

<table width="100%">
<thead><tr><th align="left" width="28%">Attribute</th><th align="left">Details</th></tr></thead>
<tbody>
<tr><td><b>App Name</b></td><td>Petal Browser</td></tr>
<tr><td><b>Platform</b></td><td>Android 8.0 and above (API 26+, target API 36)</td></tr>
<tr><td><b>Engine</b></td><td>Mozilla GeckoView 159 (Independent standards engine)</td></tr>
<tr><td><b>Ad Blocking</b></td><td>High-performance native Rust engine (Brave <code>adblock-rust</code>) + uBlock & Hagezi lists</td></tr>
<tr><td><b>UI System</b></td><td>Jetpack Compose & Material 3 Expressive (Spring physics, morphing shapes, dynamic color)</td></tr>
<tr><td><b>Extensions</b></td><td>Firefox WebExtensions / Add-ons support</td></tr>
<tr><td><b>Privacy</b></td><td>Zero accounts, local biometric password vault, per-site shield, zero telemetry</td></tr>
<tr><td><b>AI Assistant</b></td><td>Optional BYOK (Gemini, OpenAI, Anthropic, Groq, Ollama)</td></tr>
<tr><td><b>License</b></td><td>GNU GPL v3.0</td></tr>
</tbody>
</table>

<br>

<h2 id="screenshots"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-phone.svg"><img src="docs/readme/light/icon-phone.svg" width="24" height="24" align="center"></picture> Screenshots</h2>

<div align="center">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-1.png" width="19%" alt="Petal home">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-2.png" width="19%" alt="Petal tabs">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-3.png" width="19%" alt="Petal shield">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-4.png" width="19%" alt="Petal AI">
  <img src="https://raw.githubusercontent.com/shreyagarwal72/petal-assets/main/screenshots/petal-playstore-5.png" width="19%" alt="Petal settings">
</div>

<br>

<h2 id="features"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-sparkles.svg"><img src="docs/readme/light/icon-sparkles.svg" width="24" height="24" align="center"></picture> Features</h2>

<table width="100%">
<tr>
<td width="50%" valign="top">
<h4><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-gecko.svg"><img src="docs/readme/light/icon-gecko.svg" width="20" height="20" align="center"></picture> Firefox GeckoView Engine</h4>
Independent from Android WebView. Complete Gecko 159 web standards, CSS grids, and extensions runtime.
</td>
<td width="50%" valign="top">
<h4><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-shield.svg"><img src="docs/readme/light/icon-shield.svg" width="20" height="20" align="center"></picture> Native Rust Adblocking</h4>
Powered by <code>adblock-rust</code> compiled via NDK. Real-time cosmetic hiding and script blocking before download.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<h4><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-palette.svg"><img src="docs/readme/light/icon-palette.svg" width="20" height="20" align="center"></picture> Material 3 Expressive</h4>
Dynamic color extracted from wallpaper, morphing bottom pill bar, fluid spring curves, and true-black AMOLED mode.
</td>
<td width="50%" valign="top">
<h4><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-lock.svg"><img src="docs/readme/light/icon-lock.svg" width="20" height="20" align="center"></picture> Hardware-Backed Privacy</h4>
BiometricPrompt-protected password vault, granular per-site tracking shield, and zero background analytics.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<h4><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-download.svg"><img src="docs/readme/light/icon-download.svg" width="20" height="20" align="center"></picture> Segmented Downloader</h4>
Multi-threaded background download manager, floating picture-in-picture media player, and native PDF viewer.
</td>
<td width="50%" valign="top">
<h4><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-bot.svg"><img src="docs/readme/light/icon-bot.svg" width="20" height="20" align="center"></picture> Bring-Your-Own-Key AI</h4>
Optional on-device and cloud companion. Connect Gemini, OpenAI, Claude, or local Ollama instances to query pages.
</td>
</tr>
</table>

<br>

<h2 id="permissions"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-key.svg"><img src="docs/readme/light/icon-key.svg" width="24" height="24" align="center"></picture> Permissions</h2>

Petal asks only when a feature is actively invoked:

| Permission | Purpose |
| :--- | :--- |
| `INTERNET` & `ACCESS_NETWORK_STATE` | Loading web pages and detecting connection state. |
| `CAMERA` & `RECORD_AUDIO` | Requested only when a web page prompts for camera/mic access or QR code scanning. |
| `ACCESS_FINE_LOCATION` | Optional; prompted strictly per site with granular allow/block toggles. |
| `READ_MEDIA_*` & storage | Uploading files, viewing downloaded PDFs, and local storage management. |
| `POST_NOTIFICATIONS` & foreground | Foreground services for ongoing downloads and background media playback. |
| `USE_BIOMETRIC` | Unlocking your local password safe with biometric authentication. |
| `VIBRATE` | Material 3 Expressive spring motion haptics and touch feedback. |
| `REQUEST_INSTALL_PACKAGES` | Optional built-in seamless updater for GitHub releases. |
| `SYSTEM_ALERT_WINDOW` | Optional floating island on devices running versions without Android 16 Live Updates. |

<br>

<h2 id="tech-stack"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-layers.svg"><img src="docs/readme/light/icon-layers.svg" width="24" height="24" align="center"></picture> Tech Stack</h2>

<table width="100%">
<thead><tr><th align="left" width="25%">Layer</th><th align="left" width="35%">Library / Tool</th><th align="left">Role</th></tr></thead>
<tbody>
<tr><td><b>Language</b></td><td>Kotlin 2.x · Rust (NDK)</td><td>Expressive, null-safe app code with high-performance native adblock filtering.</td></tr>
<tr><td><b>UI System</b></td><td>Jetpack Compose · Material 3 Expressive</td><td>Dynamic container transforms, custom spring specs, and expressive motion tokens.</td></tr>
<tr><td><b>Browser Engine</b></td><td>Mozilla GeckoView 159</td><td>Independent browser engine and WebExtension runtime.</td></tr>
<tr><td><b>Ad Blocking</b></td><td><code>adblock-rust</code> (NDK JNI)</td><td>High-performance URL rule parsing and cosmetic element filtering.</td></tr>
<tr><td><b>Architecture</b></td><td>MVVM · StateFlow · Flow</td><td>Clean separation of reactive UI state and GeckoView engine events.</td></tr>
<tr><td><b>Dependency Injection</b></td><td>Dagger Hilt</td><td>Modular singleton and Android lifecycle dependency injection.</td></tr>
<tr><td><b>Storage & Vault</b></td><td>Room SQLite · SQLCipher · Biometrics</td><td>Encrypted vault for credentials, history, and bookmarks.</td></tr>
<tr><td><b>Privileged APIs</b></td><td>Shizuku API</td><td>Elevated system integration for package installs & overlays without root.</td></tr>
<tr><td><b>Platform Targets</b></td><td>Min SDK 26 (Android 8.0) · Target SDK 36</td><td>Broad compatibility from Android 8 through modern Android versions.</td></tr>
</tbody>
</table>

<br>

<h2 id="build-it-yourself"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/icon-rocket.svg"><img src="docs/readme/light/icon-rocket.svg" width="24" height="24" align="center"></picture> Build it yourself</h2>

**Prerequisites:**
* Android Studio (Ladybug or newer)
* Android SDK 36+
* JDK 17 or JDK 21

```bash
git clone [https://github.com/shreyagarwal72/petal.git](https://github.com/shreyagarwal72/petal.git)
cd petal

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
ckoView Engine" width="430"></picture> | <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/card-adblock.svg"><img src="docs/readme/light/card-adblock.svg" alt="Rust Native Adblock" width="430"></picture> |
| <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/card-privacy.svg"><img src="docs/readme/light/card-privacy.svg" alt="Privacy Shield & Biometrics" width="430"></picture> | <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/card-addons.svg"><img src="docs/readme/light/card-addons.svg" alt="Firefox Addons Support" width="430"></picture> |
| <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/card-ai.svg"><img src="docs/readme/light/card-ai.svg" alt="BYOK AI Web Companion" width="430"></picture> | <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/card-theme.svg"><img src="docs/readme/light/card-theme.svg" alt="Expressive Dynamic Theme" width="430"></picture> |
| <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/card-media.svg"><img src="docs/readme/light/card-media.svg" alt="Media Sniffer & Player" width="430"></picture> | <picture><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/card-tabs.svg"><img src="docs/readme/light/card-tabs.svg" alt="Smart Tabs & Groups" width="430"></picture> |

</div>

<table width="100%"><thead><tr><th align="left" width="28%">Domain</th><th align="left">Capabilities & Architecture</th></tr></thead><tbody>
<tr><td><b>GeckoView Core</b></td><td>True independence from Android System WebView. Full Gecko 158 web standards, CSS grids, WebAssembly, and modern security patches.</td></tr>
<tr><td><b>Rust Adblocking</b></td><td>Powered by Brave's <code>adblock-rust</code> compiled via NDK. Real-time cosmetic filtering, network request blocking, and anti-circumvention scriptlets.</td></tr>
<tr><td><b>Expressive Navigation</b></td><td>Collapsible address bar, bottom controls for one-handed operation, horizontal address bar swipe to change tabs, and responsive grid tab layout.</td></tr>
<tr><td><b>Integrated Tools</b></td><td>Multi-threaded segmented background download manager, native PDF reader, video floating picture-in-picture (PiP), and media asset grabber.</td></tr>
<tr><td><b>AI Assistant (BYOK)</b></td><td>Bring Your Own Key integration with Gemini, Groq, OpenAI, Anthropic, and local Ollama instances. Summarize long reads, extract structured tables, or chat with the page.</td></tr>
<tr><td><b>Privacy & Passwords</b></td><td>Encrypted local SQLite vault protected by Android BiometricPrompt. Per-site privacy controls, cookie sandboxing, and user-agent spoofing.</td></tr>
<tr><td><b>Live Island</b></td><td>Floating interactive status pill on top of the screen (using Shizuku or system overlays) for downloads and background media.</td></tr>
</tbody></table>

<h3 id="permissions"><picture><source media="(prefers-color-scheme: dark) and (max-width: 640px)" srcset="docs/readme/dark/hm-permissions.svg"><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/h-permissions.svg"><source media="(max-width: 640px)" srcset="docs/readme/light/hm-permissions.svg"><img src="docs/readme/light/h-permissions.svg" alt="Permissions: Transparent and minimal" width="100%"></picture></h3>

Petal requests runtime permissions only at the point of interaction:

| Permission | Purpose |
| :--- | :--- |
| `INTERNET` & `ACCESS_NETWORK_STATE` | Loading web pages and detecting network conditions. |
| `CAMERA` & `RECORD_AUDIO` | Requested only when a web app prompts (e.g. video calls, QR scanning, voice input). |
| `ACCESS_FINE_LOCATION` | Optional; prompted strictly per-site with granular permission toggles. |
| `READ_MEDIA_*` & storage | Uploading web files, saving PDF documents, and managing downloads. |
| `POST_NOTIFICATIONS` & foreground | Foreground services for ongoing downloads and background media playback. |
| `USE_BIOMETRIC` | Unlocking your local password safe with fingerprint or face ID. |
| `VIBRATE` | Material 3 Expressive spring motion haptics and touch feedback. |
| `REQUEST_INSTALL_PACKAGES` | Optional built-in seamless updater for GitHub releases. |
| `SYSTEM_ALERT_WINDOW` | Optional floating island on devices running Android versions without Live Updates. |

<h3 id="stack"><picture><source media="(prefers-color-scheme: dark) and (max-width: 640px)" srcset="docs/readme/dark/hm-stack.svg"><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/h-stack.svg"><source media="(max-width: 640px)" srcset="docs/readme/light/hm-stack.svg"><img src="docs/readme/light/h-stack.svg" alt="Tech Stack: Native Android & Rust architecture" width="100%"></picture></h3>

<table width="100%"><thead><tr><th align="left" width="25%">Layer</th><th align="left" width="35%">Library / Tool</th><th align="left">Role</th></tr></thead><tbody>
<tr><td><b>Language</b></td><td>Kotlin 2.x · Rust (NDK)</td><td>Expressive, null-safe client layer with bare-metal Rust filtering logic.</td></tr>
<tr><td><b>UI System</b></td><td>Jetpack Compose · Material 3 Expressive</td><td>Dynamic container transforms, custom spring specs, and expressive motion.</td></tr>
<tr><td><b>Browser Engine</b></td><td>Mozilla GeckoView 158</td><td>Full independent web rendering engine and extension runtime.</td></tr>
<tr><td><b>Ad Blocking</b></td><td><code>adblock-rust</code> (NDK JNI)</td><td>High-performance URL rule parsing and sub-millisecond element hiding.</td></tr>
<tr><td><b>Architecture</b></td><td>MVVM · StateFlow · Flow</td><td>Clean separation of reactive UI state and GeckoView engine events.</td></tr>
<tr><td><b>Dependency Injection</b></td><td>Dagger Hilt</td><td>Modular singleton and Android lifecycle dependency management.</td></tr>
<tr><td><b>Storage & Vault</b></td><td>Room SQLite · SQLCipher · BiometricPrompt</td><td>Hardware-backed encrypted vault for credentials, history, and bookmarks.</td></tr>
<tr><td><b>Privileged APIs</b></td><td>Shizuku API</td><td>Optional elevated system integration for package installs & overlays.</td></tr>
<tr><td><b>Performance</b></td><td>Baseline Profiles · R8 ProGuard</td><td>Optimized DEX compilation for instant launch times and low RAM usage.</td></tr>
<tr><td><b>Platform Targets</b></td><td>Min SDK 26 (Android 8.0) · Target SDK 36</td><td>Compatible across legacy Android devices through Android 16+.</td></tr>
</tbody></table>

<h3 id="build"><picture><source media="(prefers-color-scheme: dark) and (max-width: 640px)" srcset="docs/readme/dark/hm-build.svg"><source media="(prefers-color-scheme: dark)" srcset="docs/readme/dark/h-build.svg"><source media="(max-width: 640px)" srcset="docs/readme/light/hm-build.svg"><img src="docs/readme/light/h-build.svg" alt="Build from source" width="100%"></picture></h3>

**Requirements:**
* Android Studio Meerkat / Ladybug or newer
* Android SDK 36+
* JDK 17 or JDK 21
* Rust toolchain with `cargo` and `cargo-ndk` (if modifying `native/adblock_rs`)

```bash
# Clone the repository
git clone [https://github.com/shreyagarwal72/petal.git](https://github.com/shreyagarwal72/petal.git)
cd petal

# Compile debug APK
./gradlew assembleDebug

# Compile production release APK
./gradlew assembleRelease
ent design, glass effects and the theme</td>
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
valign="top"><img src="docs/readme/screenshots/03-back.jpg" alt="Five back-gesture animation styles" width="100%"><br><sub>Back-gesture styles</sub></td>
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
