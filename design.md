# Petal Browser Visual Design & UI System (design.md)

## 1. Design Direction: Material 3 Expressive
Petal Browser implements Google's **Material 3 Expressive** design specification (`androidx.compose.material3:material3:1.5.0-alpha17`), prioritizing vibrant dynamic geometry, living visual hierarchy, playful spring animations, and tactile responsiveness.

---

## 2. Dynamic 35 Material 3 Expressive Shapes Catalog
Implemented in `com.petal.browser.ui.theme.PetalMaterialShapes.kt` using `androidx.graphics.shapes.RoundedPolygon`:
Every interactive chip, web shortcut pill, FAB, and dialog accent is mapped across 35 distinct geometric shapes:
1. `Circle`
2. `Square`
3. `Slanted`
4. `Arch`
5. `SemiCircle`
6. `Oval`
7. `Pill`
8. `Triangle`
9. `Arrow`
10. `Fan`
11. `Diamond`
12. `ClamShell`
13. `Pentagon`
14. `Gem`
15. `Sunny`
16. `VerySunny`
17. `Cookie4Sided`
18. `Cookie6Sided`
19. `Cookie7Sided`
20. `Cookie9Sided`
21. `Cookie12Sided`
22. `Ghostish`
23. `Clover4Leaf`
24. `Clover8Leaf`
25. `Burst`
26. `SoftBurst`
27. `Boom`
28. `SoftBoom`
29. `Flower`
30. `Puffy`
31. `PuffyDiamond`
32. `PixelCircle`
33. `PixelTriangle`
34. `Bun`
35. `Heart`

The helper extension `RoundedPolygon.toShape()` renders smooth Compose-native vector paths with zero bitmap distortion.

---

## 3. App Identity & Adaptive Flower Icon
- **Vector Foreground**: Defined in `res/drawable/ic_launcher_foreground.xml` with companion drawables in `res/mipmap-*/`.
- **Icon Visual Motif**: Styled after the blossoming petal flower silhouette using layered geometry:
  - Deep midnight expressive background (`#1A0D1E`).
  - Concentric radial glow layers (`#2D1537` and `#3E1A4A`).
  - Adaptive Android icon support with full compatibility across API 26 through API 36.

---

## 4. Omnibox, Address Bar Collapse & Navigation Mechanics
- **Collapsing Address Bar**:
  - Located at `R.id.compose_address_bar` / `activity_main.xml`.
  - Driven by scroll offset deltas from `PetalGeckoView` and `NinjaWebView` (`animateAddressBarCollapse`).
  - When scrolled downwards, the address bar collapses with a spring exit transition, revealing an unobtrusive floating action bubble (`fab_bubble`).
  - Scrolling upwards smoothly restores the full omnibox pill.
- **Interactive Pill Enhancements**:
  - Horizontal swipe gestures across the pill trigger rapid tab switching with micro-haptic ticks.
  - Long-press triggers an Expressive Modal Bottom Sheet providing Clean URL Copying (stripping tracking parameters), Paste & Go, Quick Bookmark, and Hard Refresh.
  - Inline progress line morphing directly along the bottom contour of the omnibox container.
- **Top-Anchored Overflow Menu**:
  - Compact Material 3 Expressive sheet anchored at the top-right toolbar.
  - Displays quick toggles for Desktop Mode, JavaScript, AdBlock, and Dark Mode, flanked by primary navigation actions.

---

## 5. Color Palettes, AMOLED Pure Black & Typography Tokens
- **Theme Definitions (`com.petal.browser.ui.theme.PetalTheme.kt`)**:
  - **Dynamic Palette**: Extracts system wallpaper tones on Android 12+ (API 31+) via `dynamicLightColorScheme` and `dynamicDarkColorScheme`.
  - **Fallback Palettes**: Hand-crafted color harmonies including `Petal`, `Tide`, `Sakura`, `Emerald`, and `Nord`.
  - **Color Styles (`ColorStyles.kt`)**: Full support for Material 3 tonal variants: `TONAL_SPOT`, `EXPRESSIVE`, `FRUIT_SALAD`, `RAINBOW`, `VIBRANT`, and `MONOCHROME`.
  - **AMOLED Mode (`applyAmoled()`)**: Replaces dark surfaces with true pitch black (`#000000`) across all elevation layers (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainerHigh`), saving battery life on OLED displays.
- **Motion & Spring Easing**:
  - Spring physics parameters: `dampingRatio = Spring.DampingRatioLowBouncy` (0.75f) with `stiffness = Spring.StiffnessMediumLow`.
  - Emphasized transitions: `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)` over 350ms duration for enter and exit animations.
- **Tactile Waveform Haptics (`PetalHapticEngine.kt`)**:
  - Synthesizes distinct vibration patterns (`CLICK`, `TICK`, `DOUBLE_CLICK`, `HEAVY_CLICK`) mapped to address bar actions, swipe thresholds, and seek gestures.

---

## 6. Material 3 Expressive UI Redesign Specifications & Final Rules

### 6.1 Unified Shape Hierarchy
- **Pill Buttons & Chips**: Standardized on `CircleShape` or `RoundedCornerShape(50.dp)` with min 48dp touch target height.
- **Icon-Only Buttons**: Standardized on `CircleShape`.
- **Cards & Content Blocks**: Standardized on `RoundedCornerShape(16.dp)` or position-aware corner grouping (18dp outer, 6dp inner).
- **Bottom Sheets**: `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`.
- **Dialogs**: `RoundedCornerShape(28.dp)` centered with 0.92f width limit.
- **Expressive Shapes Policy**: The 35 expressive shapes (Cookie, Burst, Flower, Boom, Clover, etc.) are strictly prohibited on ordinary buttons, chips, and list rows. They are reserved exclusively for hero or empty-state moments (e.g. `EmptyStateBlob`, `PetalDeveloperHeroSection`, `PetalIncognitoHomeScreen`).

### 6.2 4dp Spacing Grid
- `space.xs` = 4.dp
- `space.sm` = 8.dp
- `space.md` = 12.dp
- `space.lg` = 16.dp
- `space.xl` = 24.dp
- `space.xxl` = 32.dp
- Sheet horizontal padding: 18.dp to 24.dp
- Stacked group item gap: 4.dp
- Connected button segment gap: 6.dp

### 6.3 Semantic Color Roles & AMOLED Compliance
- All UI colors strictly resolve through `MaterialTheme.colorScheme` tokens. Zero hardcoded colors (`Color(0x...)`, `Color.Black`, `Color.White`).
- **Sheet Background**: `MaterialTheme.colorScheme.surfaceContainerHigh`
- **Dialog Background**: `MaterialTheme.colorScheme.surfaceContainerLow`
- **Stacked Content Blocks (Unselected)**: `MaterialTheme.colorScheme.surfaceContainerHighest` or `surfaceContainerHigh`
- **Stacked Content Blocks (Selected)**: `MaterialTheme.colorScheme.primaryContainer` or `secondaryContainer`
- **Title Badge**: `MaterialTheme.colorScheme.surfaceContainerLowest` container with `primary` text
- **Actions**: Filled `primary` for confirmation; tonal `surfaceContainerHighest` for dismissal
- **AMOLED**: Full compatibility via `PetalTheme.applyAmoled()` re-mapping surface containers to `#000000`.

### 6.4 Border Policy & Deliberate Exceptions
Thin 1dp/1.5dp decorative outline borders (`BorderStroke` or `Modifier.border`) are removed across the application. Borders are strictly restricted to:
1. **Keyboard Focus Ring**: Clean 2dp `primary` ring (no glow) dynamically visible upon focus.
2. **Selected Tab Thumbnail**: Clean 2dp `primary` ring on the active thumbnail in `PetalTabGridSwitcher.kt:1889`.
3. **Tab Drag-and-Merge Target**: 2dp `primary` ring highlighting the drop target during tab grouping in `PetalTabGridSwitcher.kt:1887`.
4. **Tab Counter Badge**: Micro 1dp indicator in `PetalBottomNavBar.kt:607`.
5. **Protected Containment**: Original core containment architecture inside `ui/containment/PetalContainment.kt` (excluding `PetalConnectedButtonGroup` and `PetalSheet` which were upgraded).

### 6.5 Connected Segmented Button Controls (`PetalConnectedButtonGroup`)
Modeled directly after PixelPlayer's `AnimatedPlaybackControls`:
- Outer corners: 50dp / inner corners: 6dp (`petalConnectedShape`)
- Horizontal spacing: 6.dp
- Touch height: Minimum 48.dp
- Weight expansion physics: Selected/pressed segment animates to `weight(1.1f)` while adjacent segments shrink to `weight(0.65f)` over a 240ms `FastOutSlowInEasing` tween.
- Tactile feedback: `PetalHapticEngine.Pattern.TICK` on every segment selection.
- All button group components (`PetalConnectedButtonGroup`, `ExpressiveButtonGroup`, `PetalChoiceButtonGroup`) share unified shape, gap, and color specifications.

### 6.6 Bottom Sheet & Dialog Architecture
All dialogs, alerts, menus, and bottom sheets adhere to the unified PixelPlayer transient layout:
- Flat `surfaceContainerHigh` container with normal drag handle.
- Top corners at 28.dp; zero borders, gradients, or glows.
- Title pill in `surfaceContainerLowest` with `primary` typography.
- Content structured in stacked tonal blocks with 18.dp outer and 6.dp inner corners.
- Action row built as connected 2-segment pill group (cancel = tonal, confirm = filled primary).

### 6.7 Calm Motion
- Single spatial spring: `MotionScheme.expressive().defaultSpatialSpec<Dp>()`
- Emphasized easing: `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)` over 350ms
- No bouncy overshoot on dialogs; no looping decorative animations.
- Full support for `LocalReduceMotionEnabled` falling back to zero duration.

---

## 7. Developer Checklist for New Screens

When building or updating any screen, sheet, dialog, or component in Petal, verify against this checklist:
- [ ] **No Outlines/Borders**: No `BorderStroke` or `Modifier.border` used for decoration. Borders only permitted for 2dp primary focus rings or selected tab thumbnails.
- [ ] **No Outlined Material 3 Components**: No `OutlinedButton`, `OutlinedTextField`, or `OutlinedCard`. Use tonal filled variants (`TextField`, `FilledTonalButton`, `Card` / `Surface`).
- [ ] **Unified Button Groups**: Side-by-side buttons use `PetalConnectedButtonGroup` or `PetalChoiceButtonGroup` with 6dp spacing and 50dp/6dp corner morphing.
- [ ] **Standardized Shapes**: Standard buttons use pills (`CircleShape` or 50dp); icon buttons use circles; cards use 16dp rounded corners. No Cookie, Burst, Flower, or Boom shapes.
- [ ] **No Hardcoded Hex Colors**: All colors use `MaterialTheme.colorScheme` tokens.
- [ ] **Transient Surfaces**: Bottom sheets use `PetalSheet`; dialogs use `PetalAlertDialog`; menus use `PetalPopupMenu`.
- [ ] **Touch Targets**: All interactive elements maintain a minimum touch target of 48.dp.
- [ ] **Accessibility & Ellipsis**: Text labels have `maxLines = 1` and `TextOverflow.Ellipsis` where bounded, with descriptive content descriptions.
- [ ] **Calm Motion**: Transitions use `MotionScheme.expressive()` or fast effects specs with no aggressive overshoot.
