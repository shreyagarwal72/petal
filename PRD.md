# Petal Browser Product Requirements Document (PRD.md)

## 1. Executive Summary & Product Positioning
**Petal Browser** is a fast, ultra-lightweight, and privacy-focused Android web browser built upon a modern hybrid architecture combining a Mozilla GeckoView engine core with a Material 3 Expressive Jetpack Compose user interface.

Forked originally from FOSS Browser, Petal Browser retains the non-invasive, open-source privacy values of its predecessor while delivering modern Android design paradigms, predictive gesture motion, and advanced media playback capabilities.

---

## 2. Target Platform & Runtime Constraints
- **Operating System Baseline**: Android 8.0 (API Level 26, `Oreo`) minimum.
- **Target SDK**: Android 15 / 16 (API Level 36).
- **Target Architectures**: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (Universal APK and ABI-split builds).
- **Hardware Integration**:
  - Android 12+ (API 31+) Dynamic Wallpaper Color Extraction (`Monet`).
  - Android 10+ (API 29+) System Gesture Navigation & Gesture Exclusion APIs.
  - Android 8.0+ Picture-in-Picture (PiP) Windowing.
  - Hardware Biometrics (`androidx.biometric:biometric:1.2.0-alpha05`).
- **Telemetry & Tracking Policy**: Strict Zero-Telemetry guarantee. No third-party analytical SDKs, advertising beacons, or background reporting services are bundled.

---

## 3. Core Differentiators & Product Features
As evidenced strictly by the current codebase implementation:

### 3.1 Dual-Engine Standalone Browsing Core
- **Mozilla GeckoView 154 Integration**: Complete standalone engine providing modern web standard compliance, independent of system WebView bugs or manufacturer delays.
- **Strict Enhanced Tracking Protection (ETP)**: Integrated content blocking configured to reject third-party tracking cookies, cryptominers, and malicious fingerprinting domains.
- **Legacy WebView Fallback**: Maintained via `NinjaWebView` for fallback compatibility.

### 3.2 Material 3 Expressive UI & Physics-Based Motion
- **35 Dynamic Shapes**: Interactive shortcuts and UI components adapt across 35 dynamic polygon geometries (`PetalMaterialShapes.kt`).
- **Living Ambient Mesh**: Day/night dynamic backdrop responding smoothly to daylight and palette shifts.
- **AMOLED Pitch-Black Support**: Genuine `#000000` surface theming across all elevated containers.
- **Waveform Tactile Haptics**: Rich micro-haptic ticks on scrolling, address bar tab swiping, and gesture triggers (`PetalHapticEngine`).

### 3.3 Advanced Native Media Player
- **mpvEx-Inspired Overlay**: Native Compose video controls overlay featuring auto-hide, dual time readouts, speed selector, and brightness/volume vertical slide gestures.
- **Wavy Animated Seekbar**: Dynamic sine-wave propagation with instant flattening during scrubbing and pause.
- **Dynamic Picture-in-Picture**: Source aspect ratio calculation with playback skip/pause action triggers.
- **Smart YouTube Bypass**: Gracefully avoids overlay collisions on native YouTube playback.

### 3.4 Predictive Back Gestures
- Dual-surface depth blur and corner-radius morphing during back navigation swipes.
- System edge-swipe touch protection ensuring browser gestures never interfere with Android system back.

### 3.5 Power Tools & Ecosystem Bridges
- **PWA Manager**: First-class support for installing Progressive Web Apps directly to the home screen.
- **Passkey Support**: Hardware-backed FIDO2 / Passkey authentication bridge (`com.petal.browser.passkey`).
- **Fetch2 Resumable Downloads**: Robust multi-threaded file downloader with pause, resume, and real-time speed monitoring.
- **Torrent Subsystem**: Package structure and contracts present (`com.petal.browser.torrent`); full UI availability *needs developer confirmation*.
- **Petal AI Hub**: On-device AI research and summary bridges (`com.petal.browser.lens`).

---

## 4. Architectural Boundaries & Non-Functional Requirements
1. **Zero Data Loss Guarantee**: Strict backward compatibility for bookmarks, session tabs, passwords, and user settings across all updates.
2. **Modular Compose Bridging**: Seamless embedding of Compose screens into the Java Activity navigation shell without breaking lifecycle or ViewModel scoping.
3. **Open-Source Compliance**: Fully licensed under GPL-3.0 with transparent attribution for all upstream libraries.
