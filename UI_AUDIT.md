# Petal Browser UI Inventory & Inconsistency Audit (UI_AUDIT.md)

**App**: Petal Browser Android (`com.petal.browser`)  
**Scope**: 634 Kotlin files, ~208,323 lines of code  
**Goal**: Complete scan of all UI primitives, containers, dialogs, controls, typography, motion, and screens to identify every distinct design variant and highlight visual/structural inconsistencies before establishing the permanent design system.

---

## 1. Icons

### 1.1 Icon Sources & Sets in Use

| Icon Source Variant | Usages (Files) | Primary File Paths | Appearance & Description | Inconsistency Notes |
| :--- | :--- | :--- | :--- | :--- |
| **A. Material Icons Rounded (`Icons.Rounded.*`)** | ~177 files (~950 call sites) | `ui/components/PetalOverflowMenuSheet.kt`, `compose/settings/screens/SettingsHubScreen.kt`, `compose/bookmarks/PetalBookmarksScreen.kt` | Soft, rounded corner caps and joins; 2dp stroke appearance; modern M3 aesthetic. | Dominant standard across the app, but mixed with Filled in several core screens. |
| **B. Material Icons Filled (`Icons.Filled.*`)** | 2 files (6 call sites) | `compose/tabs/PetalTabGridSwitcher.kt:52`, `ui/components/PetalOverflowMenuSheet.kt:25-30` | Sharp, solid geometric fills (`Tab`, `DesktopWindows`, `FindInPage`, `History`, `Share`, `Shield`). | **Clashes with Rounded icons** sitting right next to them in the same menu/switcher! |
| **C. ImageToolbox Custom Icons (`ImageToolboxIcons.*`)** | 2 files (42 imports) | `compose/settings/screens/AppearanceSettingsScreen.kt`, `compose/settings/screens/PetalLayoutSettingsSection.kt` | Specialized system vector glyphs (`HyperOS`, `IOS`, `Windows`, `Android`, `Animation`, `Cube`, `Slider`, `ToggleOn`, `Water`, `Percent`). | Visually distinct line weights and optical sizes compared to standard Material Rounded icons. |
| **D. Android Vector XMLs (`painterResource(R.drawable.*)`)** | 38 files | `res/drawable/ic_petal_*.xml`, `ui/components/ExpressiveHeader.kt`, `compose/home/PetalHomeScreen.kt` | Custom Petal brand icons, search engine logos, security shield states. | Varying stroke widths (1.5dp to 2.5dp) and viewport viewBox definitions (24x24 vs 48x48). |
| **E. Raw Text / Emoji Glyphs** | 12 files | `compose/tabs/PetalTabItem.kt`, `compose/tabs/PetalWorkspaceSheet.kt`, `compose/settings/screens/PetalFeaturedCarousel.kt` | System unicode emojis used as category or workspace indicators (e.g., 🌐, 🔒, 📁, 🎨). | Inconsistent rendering across Android OEM font engines (Samsung vs Pixel vs Xiaomi). |

### 1.2 Icon Sizes
Scanned across 634 files, icons use **10 different arbitrary sizes**:
- `14.dp` / `16.dp` (x48): Small metadata indicators, inline chips, and URL status badges.
- `18.dp` (x35): Dense secondary buttons, omnibox action icons.
- `20.dp` (x120): Standard dialog header leading icons and list row trailing actions.
- `22.dp` (x64): Bottom navigation bar icons and Quick Tools grid.
- `24.dp` (x210): Default Material 3 icon size.
- `28.dp` (x45): Dialog header hero badges and primary alert icons.
- `32.dp` (x28): Feature explainer and media picker thumbnails.
- `36.dp` / `40.dp` (x32): Large card badge containers.
- `48.dp` (x15): Settings Hub category card hero icons.

### 1.3 Icon Containers
Icon badges are constructed in 5 conflicting ways:
```
Variant 1: PetalGroupIconBadge (44-48dp, 12-16dp radius squircle, tonal container)
Variant 2: ExpressiveButtonShape Surface (36-42dp, smooth polygon/pill, primaryContainer)
Variant 3: CircleShape Box (32-40dp, 50% radius circular clip)
Variant 4: Raw RoundedCornerShape (8-16dp, arbitrary corners)
Variant 5: Uncontained Bare Icon (no background, floating directly on surface)
```
- **Variant 1 (`PetalGroupIconBadge`)**: Used in `SettingsHubScreen.kt`, `PetalPrivacyShieldSheet.kt`, `PetalExtensionsMenuSheet.kt`. 44–48dp box with `RoundedCornerShape(12.dp)` or `16.dp` and semantic color tokens (`PRIMARY`, `SECONDARY`, `TERTIARY`, `SURFACE_TONAL`).
- **Variant 2 (`Surface(shape = ExpressiveButtonShape)`)**: Used in `PetalDownloadConfirmationDialog.kt`, `PetalQuickToolsSheet.kt`, `PetalSiteInfoBottomSheet.kt`. Uses smooth polygon morph shapes.
- **Variant 3 (`CircleShape Box`)**: Used in `PetalWelcomeScreen.kt`, `PetalBookmarksScreen.kt`, `PetalHistoryScreen.kt`. Hardcoded `clip(CircleShape)` with fixed background.
- **Variant 4 (`RoundedCornerShape(8.dp)` to `14.dp`)**: Used in `PetalUploadChooser.kt`, `PetalQrGeneratorDialog.kt`.
- **Variant 5 (Bare Icons)**: Used in `PetalOverflowMenuSheet.kt` and `PetalAddressBar.kt`. Direct icons without background containment.

---

## 2. Containment Surfaces

### 2.1 Card, Container & Panel Styles

| Containment Variant | Usages | Primary File Paths | Appearance & Description | Inconsistency Notes |
| :--- | :--- | :--- | :--- | :--- |
| **A. `PetalGroup` (Segmented Group)** | 653 | `ui/containment/PetalContainment.kt`, `compose/settings/screens/TabsSettingsScreen.kt`, `compose/settings/screens/PrivacySettingsScreen.kt` | iOS/M3 Expressive connected card container. Top row has 24dp top corners + 6dp bottom; middle has 6dp; bottom has 6dp top + 24dp bottom; single row has 24dp all around. | The most polished list pattern in the app. However, several settings screens bypass it with raw cards. |
| **B. `PetalHeroCard` (Hero Banner)** | 80 | `account/PetalAccountScreen.kt`, `ui/components/PetalPrivacyShieldSheet.kt`, `compose/extensions/PetalExtensionsMenuSheet.kt` | Prominent 28–32dp rounded card, tonal container color, subtle outline or glass tint, 0–4dp elevation. | Used for top-level summaries, but corner radius varies between 28dp, 32dp, and `ExpressiveButtonShape`. |
| **C. `Surface` with `liquidGlassChrome`** | 362 | `ui/glass/`, `ui/containment/`, `ui/components/PetalAddressBar.kt`, `ui/components/PetalQuickToolsSheet.kt` | Live refractive glass: records window behind it every frame, adds directional specular sheen, chromatic edge blur, and subtle border. Falls back to solid tonal container. | Only active when user enables liquid glass. Sheen intensity varies wildly (0.12f to 0.65f) between screens. |
| **D. Generic `Card` / `Surface`** | ~2,535 | App-wide (e.g. `PetalDownloadManagerScreen.kt`, `PetalBookmarksScreen.kt`) | Plain Material 3 `Surface` or `Card` with hardcoded 12dp, 16dp, or 20dp corners, flat elevation, and default surfaceContainer colors. | Bypasses the Petal containment system entirely; creates inconsistent vertical rhythm. |
| **E. Outlined Container** | 45 | `compose/tabs/PetalTabGridSwitcher.kt`, `compose/home/PetalHomeScreen.kt` | 1dp border (`outline` or `outlineVariant.copy(alpha = 0.42f)`), 0dp elevation. | Some screens use 1dp borders, while others use 0dp borders with pure tonal separation. |

### 2.2 Corner Radii Scattering
The codebase has **15 different corner radii** defined across components:
```
 2.dp:   8 usages  (tight dividers, tiny badges)
 6.dp:  17 usages  (inner segmented joins in PetalGroup)
 8.dp:  23 usages  (legacy buttons, small chips)
10.dp:  25 usages  (context menus, search suggestions)
12.dp: 145 usages  (standard cards, action items)
14.dp:  85 usages  (quick tools items, omnibox chips)
16.dp: 134 usages  (M3 cards, dialog internal cards)
18.dp:  25 usages  (specialty buttons)
20.dp:  83 usages  (prominent cards, dialog icon badges)
22.dp:  10 usages  (bottom bar elements)
24.dp:  38 usages  (PetalGroup outer caps)
26.dp:   7 usages  (legacy cards)
28.dp:  20 usages  (M3 Expressive dialogs, sheets)
32.dp:  22 usages  (hero cards, large floating menus)
50.dp:  24 usages  (full pill buttons, search bars, FABs)
```
- **Inconsistency**: There is no unified token scale (`Small = 12dp`, `Medium = 16dp`, `Large = 24dp`, `ExtraLarge = 32dp`, `Full = 50dp`). Instead, developers arbitrarily chose 10, 12, 14, 16, 18, 20, or 22dp.

### 2.3 Surface Elevation & Borders
- **Elevation**: Ranges arbitrarily from `0.dp`, `1.dp`, `2.dp`, `4.dp`, `6.dp`, `8.dp`, up to `12.dp`.
- **Borders**: Some cards feature `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))`, some have `border = null`, and some use `Modifier.border(...)` directly.

---

## 3. Dialogs, Sheets & Popups

### 3.1 Dialog & Sheet Implementations

```
┌────────────────────────────────────────────────────────────────────────┐
│ Dialog / Sheet Variant Breakdown                                      │
├───────────────────────────────────┬───────────────┬────────────────────┤
│ Variant Type                      │ Files Count   │ Total Call Sites   │
├───────────────────────────────────┼───────────────┼────────────────────┤
│ PetalSheet (Unified Sheet)        │ 50 files      │ 204 calls          │
│ ModalBottomSheet (Raw M3)         │ 31 files      │ 52 calls           │
│ AlertDialog (Android View/M3)     │ 49 files      │ 123 calls          │
│ PetalMaterialAlertDialog          │ 16 files      │ 41 calls           │
│ Dialog( (Ad-hoc Compose Dialog)   │ 78 files      │ 219 calls          │
│ PetalDialog (Unified Dialog)      │ 9 files       │ 10 calls           │
│ PetalExpressiveDialog / Alert     │ 8 files       │ 17 calls           │
│ BasicAlertDialog (M3 Basic)       │ 4 files       │ 4 calls            │
└───────────────────────────────────┴───────────────┴────────────────────┘
```

### 3.2 Key Inconsistencies Identified
1. **Raw `ModalBottomSheet` vs `PetalSheet`**:
   - `PetalSheet` handles `max = 640.dp`, wallpaper glass backdrops, user layout corner multipliers, drag settle springs, and system insets.
   - 31 files (e.g. `PetalImageViewerScreen.kt`, `PetalApkInstallSheet.kt`, `PetalAvatarCropSheet.kt`, `PetalQueueBottomSheet.kt`) still call raw `ModalBottomSheet` directly, bypassing glass backdrops and layout corner settings.
2. **Raw `AlertDialog` / `Dialog(` vs `PetalDialog`**:
   - 49 files call Android `AlertDialog` or M3 `AlertDialog` directly without safe insets, IME padding, or scrolling, risking clipping in landscape mode.
   - 78 files call raw `Dialog(properties = ...)` with custom inner boxes, creating inconsistent dim amounts (0.12f vs 0.35f vs 0.60f) and mismatched window backgrounds.
3. **Action Button Layout Inconsistencies**:
   - **Pattern A (Asymmetric Connected Pair)**: `topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp` (Cancel) + `topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp` (Confirm). Used in `PetalExpressivePopups.kt`, `PetalConfirmBottomSheet.kt`.
   - **Pattern B (Independent Rounded Buttons)**: Two separate buttons with `RoundedCornerShape(12.dp)` or `16.dp` and 8dp spacing. Used in `PetalDeleteScreen.kt`, `PetalCrashReportingDialog.kt`.
   - **Pattern C (Stacked Buttons)**: Full-width stacked buttons with 8dp vertical spacing. Used in `PetalLensBottomSheet.kt`.
   - **Pattern D (Text Buttons)**: Trailing flat text buttons (`TextButton`). Used in `PetalSettingsScreen.kt`.

---

## 4. Buttons and Controls

### 4.1 Buttons
- **`com.petal.browser.ui.kit.Button` vs `androidx.compose.material3.Button`**:
  - The custom UI kit (`com.petal.browser.ui.kit.*`) wraps Material 3 buttons with glass and haptic integration. However, roughly 50% of screens still import `androidx.compose.material3.Button` directly.
- **Button Shapes**:
  - Split between `ExpressiveButtonShape` (smooth squircle), `RoundedCornerShape(50.dp)` (full pill), `RoundedCornerShape(12.dp)` (squircle), and `RoundedCornerShape(16.dp)`.
- **Button Heights**:
  - Ranges from `36.dp` (compact chip buttons), `40.dp` (standard tool buttons), `48.dp` (recommended M3 minimum touch target), to `56.dp` (prominent actions).

### 4.2 Switches
- **Split Breakdown**:
  - `Switch` (M3 standard): 40 files (e.g., `TabsSettingsScreen.kt`, `DownloadSettingsScreen.kt`).
  - `IconSwitch` (Custom switch with internal checkmark/cross vector icons in the thumb): 39 files (e.g., `PetalSiteInfoBottomSheet.kt`, `AppearanceSettingsScreen.kt`).
- **Inconsistency**: Half the settings screens feature plain switches, while the other half feature expressive switches with check/close glyphs inside the thumb!

### 4.3 Sliders
- **Variant 1 (`PetalVariableSlider`)**: Used in `AppearanceSettingsScreen.kt`, `SettingsComponents.kt`. Encapsulated inside `PetalGroup` with title, real-time value label, min/max range, and glass surface.
- **Variant 2 (`PetalSlider`)**: Used in `PetalLayoutSettingsSection.kt`. Custom animated thumb slider with tick marks.
- **Variant 3 (Raw M3 `Slider`)**: Used in `PetalMediaSnifferSheet.kt` and `PetalDevNotesSheet.kt`. Plain uncontained slider without value readout container.

### 4.4 Chips
- `PetalFilterChip` / `PetalSuggestionChip` / `PetalAssistChip` / `PetalInputChip` (defined in `PetalContainment.kt`): Styled with 12–16dp corners, glass/tonal surface, and subtle border.
- M3 `FilterChip` / `AssistChip` (from `androidx.compose.material3.*`): Hardcoded in `PetalBookmarksScreen.kt` and `PetalHistoryScreen.kt` with default M3 8dp corners, creating a visual mismatch with the rest of the browser.

### 4.5 List & Settings Rows
The app has **4 distinct row implementations**:
1. `PetalSettingsToggleRow`: Title, subtitle, icon, and switch, automatically adjusting top/bottom corners based on `PetalGroupPosition`.
2. `PetalGroupRow`: Generic row with leading icon badge, title, subtitle, and trailing custom composable.
3. `PetalGroupListRow`: Navigation row with chevron arrow, leading badge, and title/subtitle.
4. Ad-hoc `Row` + `clickable`: Raw Compose row with manual padding and ripple, ignoring `PetalGroupPosition`.

---

## 5. Shapes & Morphing

### 5.1 Expressive Shape System
- **`ExpressiveButtonShape`**: Custom shape definition used throughout `ui/theme/` and `ui/components/`.
- **`SmoothRoundedShape`**: Continuous curvature squircle implementation (`ui/theme/SmoothRoundedCornerShape.kt`).
- **`PetalContainmentShapes`**:
  - `Hero`: `RoundedCornerShape(28.dp)`
  - `Standard`: `RoundedCornerShape(20.dp)`
  - `Compact`: `RoundedCornerShape(14.dp)`
  - `Pill`: `RoundedCornerShape(50.dp)`
  - `Segmented`: `RoundedCornerShape(6.dp)`

### 5.2 Leftover Rigid Geometry
- 23 files still use rigid `RoundedCornerShape(8.dp)` from Material 2 era.
- 54 files use `CircleShape` for items that are squircles everywhere else (e.g., icon badges, avatar crops, action buttons).
- Missing morphing transitions: While `PetalSpring.containerMorph` is defined, several bottom sheets and dialogs snap open without shape morphing.

---

## 6. Typography & Colors

### 6.1 Typography Hierarchy
- **Font Families in `AppFont`**:
  - `GS_FLEX` (Google Sans Flex)
  - `PETAL` (Petal Expressive Variable Font)
  - `SYSTEM` (Android System Roboto / OEM font)
  - `ROBOTO` / `INTER`
- **Text Styles**:
  - Standard M3 typography (`headlineSmall`, `titleLarge`, `titleMedium`, `bodyMedium`, `bodySmall`, `labelMedium`, `labelSmall`) is used in ~70% of Compose code.
  - **Inconsistency**: Over 180 call sites use inline hardcoded font sizes:
    - `fontSize = 11.sp`, `12.sp`, `13.sp`, `14.sp`, `15.sp`, `fontWeight = FontWeight(750)`.
    - This bypasses Android system font scaling and creates uneven text density.

### 6.2 Colors & Themes
- **M3 Semantic Tokens**: `primary`, `onPrimary`, `primaryContainer`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `outlineVariant`.
- **Hardcoded Color Pollution**:
  - **379 hardcoded color declarations** found across 94 files!
  - `Color.White` (x166) and `Color.Black` (x105) are used directly for text and backgrounds, leading to contrast failures and the black-pill bug on dark themes.
  - `Color(0xE61A1C1E)` and `Color(0xE6F2F4F7)` are manually repeated in 8 different files as glass fallback tints instead of referencing a shared theme token.
- **Theme Modes Supported**:
  - Light
  - Dark
  - AMOLED (Pure `#000000` pitch black background)
  - Dynamic Color (Material You monet palette)
  - Expressive Color Palettes (`ColorStyles.kt`: 12 custom curated palettes)

---

## 7. Motion & Animation

### 7.1 Motion Specs in Use
- **`PetalSpring` Specs (`PetalSpring.kt`)**:
  - `spatial`: `stiffness = 380f, dampingRatio = 0.78f` (UI transitions, expansions)
  - `press`: `stiffness = 500f, dampingRatio = 0.75f` (Touch down/up scale)
  - `sheetDragSettle`: Spring settle for sheet dismissal
  - `containerMorph`: Shape expansion animations
  - `enter` / `exit`: Modal scale & fade transitions
- **Ad-Hoc Motion Declarations**:
  - 92 instances of inline `spring()` or `tween()` without referencing `PetalSpring`:
    - `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`
    - `spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium)`
    - `tween(300, easing = FastOutSlowInEasing)`
    - `tween(200)`
- **Inconsistency**: Different parts of the app feel bouncy, stiff, or linear depending on who authored the screen.

---

## 8. Pages & Key Screens Inventory

| Page / Screen | File Path | Containment Style | Header Style | Inconsistencies & Issues |
| :--- | :--- | :--- | :--- | :--- |
| **Settings Hub** | `compose/settings/screens/SettingsHubScreen.kt` | `PetalGroup` + `liquidGlassChrome` | `ExpressiveHeader` with search box | High visual quality; serves as a strong candidate for settings gold standard. |
| **Appearance Settings** | `compose/settings/screens/AppearanceSettingsScreen.kt` | `PetalSettingsSection` + `PetalVariableSlider` | `ExpressiveHeader` | Mixes `ImageToolboxIcons` with `Icons.Rounded`; uses specialty layout pickers. |
| **Tabs Settings** | `compose/settings/screens/TabsSettingsScreen.kt` | `PetalSettingsSection` + `PetalGroup` | `ExpressiveHeader` | Uses standard `Switch` instead of `IconSwitch`. Clean segmented groups. |
| **Downloads Manager** | `compose/downloads/PetalDownloadManagerScreen.kt` | Custom date-grouped cards + `Surface` | Custom top bar with search & actions | Uses ad-hoc cards and raw `ModalBottomSheet` for file details instead of `PetalSheet`. |
| **Bookmarks Page** | `compose/bookmarks/PetalBookmarksScreen.kt` | `PetalGroupListRow` + raw `Card` | `ExpressiveHeader` | Mixes M3 `FilterChip` (8dp) with Petal squircles; uses `CircleShape` for favicons. |
| **History Page** | `compose/history/PetalHistoryScreen.kt` | Date-grouped `PetalGroupListRow` | `ExpressiveHeader` | Mixes M3 `FilterChip` with Petal containment; inline date labels. |
| **Tab Grid Switcher** | `compose/tabs/PetalTabGridSwitcher.kt` | Custom grid cards with elevation & border | Bottom floating action pill | **Imports `Icons.Filled.Tab`**; uses custom long-press gesture cards. |
| **Omnibox Page** | `ui/components/PetalOmniboxPage.kt` | Full-screen predictive-back sheet | Top search box pill + suggestions | Polished Chrome-style layout; uses `ExpressiveButtonShape` and query chips. |
| **Welcome Screen** | `ui/components/PetalWelcomeScreen.kt` | Full-screen Pager with cards | Step indicator dots + bottom pill | Uses `CircleShape` extensively and raw `AlertDialog` for legacy fallback. |
| **Site Info Sheet** | `ui/components/PetalSiteInfoBottomSheet.kt` | `PetalSheet` + `PetalSheetCard` | `PetalSheetHeader` (80dp hero badge) | High-quality live-glass sheet; uses `IconSwitch` and `PetalSheetPill`. |
| **Extensions Screen** | `compose/extensions/PetalExtensionsScreen.kt` | `PetalGroup` + custom extension cards | `ExpressiveHeader` | Uses raw `ModalBottomSheet` for extension options in 2 places. |
| **Extensions Menu Sheet** | `compose/extensions/PetalExtensionsMenuSheet.kt` | `PetalHeroCard` inside bottom dialog | Custom header badge | Clean bottom sheet HUD; uses `PetalGroupIconBadge`. |
| **Quick Tools Sheet** | `ui/components/PetalQuickToolsSheet.kt` | `PetalSheetGlassBackdrop` + 4-column grid | Top drag handle + title | Recently redesigned: 2-line wrapped labels, live glass, safe insets. |
| **Overflow Menu Sheet** | `ui/components/PetalOverflowMenuSheet.kt` | `Surface` with `liquidGlassChrome` | Quick action top row (Back, Forward, Reload, Star) | **Imports 5 `Icons.Filled.*` icons**; mixes filled and rounded glyphs. |

---

## 9. Comprehensive Inconsistency Summary Matrix

| Category | Primary Established Pattern | Stray / Inconsistent Variants Found | Impact / Severity |
| :--- | :--- | :--- | :--- |
| **1. Icons** | `Icons.Rounded.*` (177 files) | `Icons.Filled.*` (6 call sites), `ImageToolboxIcons` (42 imports), raw emojis | High visual friction; filled icons look harsh next to rounded ones. |
| **2. Icon Badges** | `PetalGroupIconBadge` (12–16dp squircle, 44–48dp) | `CircleShape`, uncontained bare icons, `ExpressiveButtonShape` | Icon anchors have completely different visual weights across screens. |
| **3. Corner Radii** | 12dp / 16dp / 24dp / 28dp | 15 distinct radii from 2dp to 50dp (including 6, 8, 10, 14, 18, 20, 22, 26dp) | Lack of unified token hierarchy; arbitrary rounding throughout. |
| **4. Containment** | `PetalGroup` (Segmented connected rows) | Generic `Card`, raw `Surface`, plain `Box` with background | Broken vertical rhythm; disjointed list row presentations. |
| **5. Dialogs** | `PetalDialog` / `PetalMaterialAlertDialog` | Raw `AlertDialog` (49 files), `Dialog(` (78 files), `BasicAlertDialog` (4 files) | Inconsistent window dimming, clipping on landscape, missing safe insets. |
| **6. Sheets** | `PetalSheet` (Unified glass/tonal sheet) | Raw `ModalBottomSheet` (31 files) | Raw sheets lack glass backdrops, drag-settle springs, and max-width caps. |
| **7. Switches** | `IconSwitch` (check/cross icons in thumb) | Standard `Switch` (plain thumb) | 50/50 split across settings screens; jarring functional difference. |
| **8. Button Bars** | Connected asymmetric pair (50dp/6dp & 6dp/50dp) | Dual 12dp buttons, stacked vertical buttons, flat text buttons | Action confirmations look completely different between dialogs. |
| **9. Colors** | M3 Semantic tokens (`primary`, `surfaceContainer*`) | 379 hardcoded color calls (`Color.White`, `Color.Black`, hex codes) | Causes dark-theme bugs (black/white clashing pills) and broken AMOLED mode. |
| **10. Motion** | `PetalSpring` (`spatial`, `enter`, `press`) | 92 ad-hoc `spring(...)` / `tween(...)` inline declarations | Inconsistent physics: some screens bounce excessively while others are stiff. |

---
*UI Inventory complete. Proceeding to Checkpoint (Step 2).*
