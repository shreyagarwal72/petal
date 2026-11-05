# IzzyOnDroid Inclusion Request for Petal Browser

> Open the submission form directly:  
> 👉 **[Submit App Inclusion Request on Codeberg](https://codeberg.org/IzzyOnDroid/repodata/issues/new?template=app-inclusion-request.yaml&title=%5BAppRequest%5D+Petal+Browser)**  
> *(You can sign in to Codeberg with 1 click using your GitHub account!)*

---

### Form Fields (Ready to Copy/Paste)

#### 1. Guidelines
- [x] I am the developer of the app.
- [x] The app complies with the [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/).
- [x] The app is not already listed in the repo or issue tracker.
- [x] The [Fastlane](https://izzyondroid.org/docs/general/Fastlane/) folder is available in the app's repo.

#### 2. App meta-data
- **Link to the source code**:
  `https://github.com/shreyagarwal72/petal`
- **Link to app in another app store**:
  `https://play.google.com/store/apps/details?id=com.petal.browser`
- **License used**:
  `GPL-3.0-only`
- **Categories**:
  `Internet`
- **Summary**:
  `Fast, private Android web browser on GeckoView with Material 3 Expressive UI.`
- **Description**:
  ```text
  Petal is a modern, privacy-focused Android web browser powered by Mozilla's GeckoView engine and crafted with Jetpack Compose and Material 3 Expressive.

  Key Features:
  * Mozilla GeckoView Web Engine: Independent, standards-compliant web rendering without Google Chromium monoculture. Full support for desktop and mobile web extensions including uBlock Origin, Dark Reader, and Privacy Badger.
  * Built-in Rust Adblocker & Anti-Tracking: Native high-performance content blocking with EasyList, AdGuard, and tracking protection rules built in. Block intrusive ads, tracking scripts, cryptominers, and cookie banners without external add-ons.
  * Material 3 Expressive Design: Fluid spring physics, liquid glass navigation, dynamic color palette tinting, customizable corner radius shapes, and 60fps gesture animations.
  * Predictive Back Navigation: Native Android predictive back gesture support with real-time underlay preview, background depth blur, and symmetrical container morph transitions.
  * Zero Telemetry & Private Vault: No user tracking, no phone-home analytics, and no mandatory cloud accounts. Includes an encrypted biometric vault for recently closed tabs and downloaded media.
  * Inbuilt Download Manager: Multi-threaded downloading, background pause/resume, file category sorting, and in-app media viewing.
  * 100% Free and Open Source: Licensed under GNU General Public License v3.0 (GPL-3.0). Free to inspect, audit, and contribute.
  ```

#### 3. Technical instructions
- **Build instructions**:
  ```bash
  git clone --recurse-submodules https://github.com/shreyagarwal72/petal.git
  cd petal
  ./gradlew assembleGithubRelease
  ```
  *(Or track GitHub Releases directly: APK assets `petal-browser-arm64-v8a-release.apk` and `petal-browser-universal-release.apk`)*

#### 4. AI Tools Usage
- **Assistance Level**:
  `Substantial – Used throughout development`
- **"AI" Tool(s)**:
  `Google Deepmind Antigravity`
- **What did the tools help with, and how?**:
  `Architecture planning, Jetpack Compose UI refinement, MotionGate / spring physics tuning, documentation, and debugging.`
- **AI Accountability**:
  - [x] The human developer(s) reviewed and edited all "AI"-generated outputs
  - [x] The human developer(s) ran manual tests and manually verified all changes

#### 5. Further Notices
```text
Fastlane metadata (title, short_description, full_description, icon.png, changelogs) is configured in fastlane/metadata/android/en-US/. Releases are automated via GitHub Actions with signed APKs (arm64-v8a, universal, armeabi-v7a, x86, x86_64).
```
