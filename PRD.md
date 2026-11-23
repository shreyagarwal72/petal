# Petal Browser Product Requirements Document (PRD.md)

## 1. Executive Summary & Product Positioning
**Petal Browser** is a fast, ultra-lightweight, and privacy-focused Android web browser built upon a modern architecture combining a Mozilla GeckoView engine core with a Material 3 Expressive Jetpack Compose user interface and LastWave Liquid Glass theming.

Petal Browser retains non-invasive, open-source privacy values while delivering modern Android design paradigms, predictive gesture motion, full WebExtension support, and advanced media playback capabilities.

---

## 2. Target Platform & Runtime Constraints
- **Operating System Baseline**: Android 8.0 (API Level 26, `Oreo`) minimum.
- **Target SDK**: Android 15 / 16 (API Level 36).
- **Target Architectures**: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (Universal APK and ABI-split builds).
- **Hardware Integration**:
  - Android 13+ (API 33+) Live Wallpaper Lens Refraction and Per-App Language Preferences.
  - Android 12+ (API 31+) Real Backdrop Blur, Dynamic Wallpaper Color Extraction (`Monet`), and LastWave Liquid Header Glass.
  - Android 10+ (API 29+) System Gesture Navigation & Gesture Exclusion APIs.
  - Android 8.0+ Picture-in-Picture (PiP) Windowing.
  - Hardware Biometrics (`androidx.biometric:biometric:1.2.0-alpha05`).
- **Telemetry & Tracking Policy**: Strict Zero-Telemetry guarantee. No third-party analytical SDKs, advertising beacons, or background reporting services are bundled.

---

## 3. Core Differentiators & Product Features
As evidenced strictly by the current codebase implementation:

### 3.1 Mozilla GeckoView 161 Standalone Browsing Core
- **Mozilla GeckoView Beta 161**: Standalone engine core (`161.0.20261118090429`) with Mozilla Android Components `161.0b5` providing modern web standard compliance, independent of system WebView bugs or manufacturer delays.
- **Strict Enhanced Tracking Protection (ETP)**: Integrated content blocking configured to reject third-party tracking cookies, cryptominers, and malicious fingerprinting domains.
- **URL Tracking Parameter Stripping**: Automatic query parameter sanitizer (`utm_*`, `fbclid`, `gclid`, `mc_eid`) stripping trackers before page requests dispatch.
- **Total Cookie Protection & AMO Extensions**: Isolated cookie jars per site, full support for Firefox WebExtensions (AMO) and native Petal built-in extensions.

### 3.2 Material 3 Expressive UI & LastWave Liquid Glass
- **LastWave Liquid Header Glass**: Real backdrop frosted glass with lens refraction across headers, sheets, and dialogs with smooth fallback on low-end devices.
- **35 Dynamic Shapes**: Interactive shortcuts and UI components adapt across 35 dynamic polygon geometries (`PetalMaterialShapes.kt`).
- **Living Ambient Mesh**: Day/night dynamic backdrop responding smoothly to daylight and palette shifts.
- **AMOLED Pitch-Black Support**: Genuine `#000000` surface theming across all elevated containers.
- **Waveform Tactile Haptics**: Rich micro-haptic ticks on scrolling, address bar tab swiping, and gesture triggers (`PetalHapticEngine`).

### 3.3 Advanced Native Media Player & Cutout Island
- **mpvEx-Inspired Overlay**: Native Compose video controls overlay featuring auto-hide, dual time readouts, speed selector, and brightness/volume vertical slide gestures.
- **Wavy Animated Seekbar**: Dynamic sine-wave propagation with instant flattening during scrubbing and pause.
- **Dynamic Picture-in-Picture**: Source aspect ratio calculation with playback skip/pause action triggers.
- **Cutout Island Player**: Floating interactive media island with burn-in protection, wavy progress, and quick controls.

### 3.4 Standalone Viewers & Native Predictive Back
- **Task Affinity Isolation**: Standalone viewers for PDF, media, image, and file viewing in dedicated recents tasks (`documentLaunchMode="always"`).
- **Native Predictive Back**: Fluid back-gesture animations with system-level back handlers (`enableOnBackInvokedCallback="true"`).

### 3.5 Decentralized Folder Sync
- **User-Owned Encrypted Sync**: Safely sync bookmarks, history, passwords, and open tabs via Storage Access Framework (SAF) to any cloud folder (Drive, Nextcloud, Syncthing) without third-party accounts.
- **Android JobService**: Scheduled background syncing with network constraint handling.

---

## 4. Architectural Boundaries & Non-Functional Requirements
1. **Zero Data Loss Guarantee**: Strict backward compatibility for bookmarks, session tabs, passwords, and user settings across all updates.
2. **Modular Compose Bridging**: Seamless embedding of Compose screens into the Activity navigation shell without breaking lifecycle or ViewModel scoping.
3. **Open-Source Compliance**: Fully licensed under GPL-3.0 with transparent attribution for all upstream libraries.
