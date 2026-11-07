# Petal Material 3 Expressive Onboarding Architecture Plan

This document outlines the architecture and phased implementation plan for the new Material 3 Expressive Welcome and Setup flow in Petal Android Browser.
As mandated, this flow **does not reuse or inherit from the legacy welcome, setup, or permission pages** (`ActivityWelcome`, `PetalSetupBridge`, `dialog_welcome.xml`), providing a clean, Compose-native experience.

---

## 1. Design & Interaction Principles
- **Material 3 Expressive Design System**: Incorporates fluid spring physics (`spring(dampingRatio = 0.8f, stiffness = 300f)`), expressive morphing pill/chip shapes (`PetalMaterialShapes`), and dynamic color schemes derived from the user's wallpaper.
- **Composable Stepper Architecture**: Encapsulated in a single top-level `PetalOnboardingScreen` hosted in a dedicated `ComposeView` with state managed via a Kotlin StateFlow model (`OnboardingViewModel`).
- **Skip & Fast-Track**: Every step provides an explicit, accessible skip/continue option; completed configuration is atomically committed to `SharedPreferences` upon completion.

---

## 2. Seven-Stage Onboarding Progression

### Step 1: Language Selection (English vs. Hinglish)
- **UI**: High-contrast, tactile dual-tile picker featuring English (Standard) and Hinglish (Romanized Hindi: *Hindi words in Latin script*).
- **Behavior**: Dynamically reconfigures the locale configuration for the app context without requiring a full process restart.

### Step 2: Essential Permissions & System Roles
- **UI**: Expressive permission grant cards with animated iconography:
  - Notifications (`POST_NOTIFICATIONS` for Android 13+ download completions).
  - Role Manager (`RoleManager.ROLE_BROWSER`) prompt to set Petal as default browser, with an explicit "Not now" action.
- **Behavior**: Uses AndroidX Activity Result Contracts; gracefully continues regardless of grant state.

### Step 3: Built-in Extension Suite Activation
- **UI**: Toggleable feature pills for Petal's privacy extensions:
  - *Petal Network Blocker* (MV2 WebRequest ad & tracker shield).
  - *Clean Link* (strips tracking parameters, ref codes, and redirect debouncers).
  - *Cookie & Consent Defuser* (auto-handles GDPR/CCPA modal popups).
- **Behavior**: Writes initial toggles directly to `PetalBuiltInExtensionManager` preferences.

### Step 4: Petal AI Assistant Configuration
- **UI**: Visual selector for intelligent assistant integration:
  - Local On-Device / Hybrid / Disabled.
  - Quick action configuration (Summarize, Translate, Key Takeaways).
- **Behavior**: Initializes AI feature flags with zero background telemetry.

### Step 5: Mozilla Account & Firefox Sync
- **UI**: Firefox Sync login tile utilizing OAuth PKCE flow (`FirefoxAuthOverlaySheet`).
  - Securely synchronizes Bookmarks, History, and Passwords.
  - Prominent "Set up later in Settings" button.

### Step 6: Profile & Persona Customization
- **UI**: Avatar selection matrix featuring vibrant expressive vectors, Material You monet avatars, or custom gallery image upload, accompanied by a Profile Name input.
- **Behavior**: Stores user avatar in private app storage (`app_profile/avatar.png`).

### Step 7: Default Search Engine Selection
- **UI**: Card carousel for default search engines:
  - DuckDuckGo (Privacy Default)
  - Google
  - Brave Search
  - SearXNG
  - Startpage
  - Custom OpenSearch provider option
- **Behavior**: Configures `sp_search_engine` and preloads search suggestion caches.

---

## 3. Transition to Main Browser
- Upon completing Step 7, a seamless expressive circular reveal transition expands into `BrowserActivity` with the homepage / default new-tab page loaded.
- Sets `sp_welcome_shown = true` and `sp_setup_v2_done = true` in `SharedPreferences`.
