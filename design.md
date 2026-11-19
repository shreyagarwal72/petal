# Petal Browser Visual Design & UI System (design.md)

## 1. Design Direction: Material 3 Expressive & LastWave Liquid Glass
Petal Browser implements Google's **Material 3 Expressive** design specification (`androidx.compose.material3:material3:1.5.0-alpha22`) paired with the **LastWave Liquid Glass** engine, prioritizing vibrant dynamic geometry, real backdrop blur, living visual hierarchy, playful spring animations, and tactile responsiveness.

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

## 3. LastWave Liquid Header Glass Engine
- **Backdrop Blur & Refraction**: On Android 12+ (API 31+), headers, sheets, and dialogs utilize real-time render effect backdrop blurs with customizable sheen intensity, specular highlights, and chromatic aberration.
- **Glass Header Architecture (`PetalGlassHeaderLayout`)**: The page content acts as the backdrop source while the header floats above it. Search rows, tab strips, and filters sit seamlessly inside the header's `bottomContent` slot, eliminating content overlap and page jumps.
- **Hardware-Aware Fallback**: Automatically falls back to high-contrast solid surfaces on low-end devices (`MotionGate.isLowEndDevice`) or when disabled in Settings.

---

## 4. App Identity, Selectable App Icons & Themes
- **Adaptive Icon Themes**: Choose how Petal looks on your home screen in Settings > App Icon (Bloom, Classic Swirl, Tab Bloom, Petal P, Lens, Shield) with adaptive monochrome styling and dynamic wallpaper coloring.
- **Dynamic Launch Splash Screens**: Synchronized animated splash screens on Android 13+ with smooth exit transitions.

---

## 5. Omnibox, Address Bar Collapse & Navigation Mechanics
- **Collapsing Address Bar**:
  - Located at `ComposeEngineViewport.kt` and `PetalAddressBar.kt`.
  - Driven by scroll offset deltas from GeckoView.
  - When scrolled downwards, the address bar collapses into a compact pill, waiting ~110 ms so GeckoView has painted the freed area before fading out, completely preventing black/gray viewport flashes or page jumps.
  - Scrolling upwards smoothly restores the full omnibox pill with sequential cross-fading.
- **Interactive Pill Enhancements**:
  - Horizontal swipe gestures across the pill trigger rapid tab switching with micro-haptic ticks.
  - Long-press triggers an Expressive Modal Bottom Sheet providing Clean URL Copying (stripping tracking parameters), Paste & Go, Quick Bookmark, and Hard Refresh.
- **Expressive Header & Navigation**:
  - Unified `ExpressiveHeader` supporting back navigation with frosted `GlassBackButton`, dynamic typography scaling, and actions.

---

## 6. Color Palettes, AMOLED Pure Black & Typography Tokens
- **Theme Definitions (`com.petal.browser.ui.theme.PetalTheme.kt`)**:
  - **Dynamic Palette**: Extracts system wallpaper tones on Android 12+ (API 31+) via `dynamicLightColorScheme` and `dynamicDarkColorScheme`.
  - **Fallback Palettes**: Hand-crafted color harmonies including `Petal`, `Tide`, `Sakura`, `Emerald`, and `Nord`.
  - **AMOLED Mode (`applyAmoled()`)**: Replaces dark surfaces with true pitch black (`#000000`) across all elevation layers, saving battery life on OLED displays.
- **Motion & Spring Easing**:
  - Spring physics parameters: `dampingRatio = Spring.DampingRatioLowBouncy` (0.75f) with `stiffness = Spring.StiffnessMediumLow`.
  - Emphasized transitions: `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)` over 350ms duration for enter and exit animations.
- **Tactile Waveform Haptics (`PetalHapticEngine.kt`)**:
  - Synthesizes distinct vibration patterns (`CLICK`, `TICK`, `DOUBLE_CLICK`, `HEAVY_CLICK`) mapped to address bar actions, swipe thresholds, and seek gestures.

---

## 7. Developer Checklist for New Screens

When building or updating any screen, sheet, dialog, or component in Petal, verify against this checklist:
- [ ] **No Outlines/Borders**: No `BorderStroke` or `Modifier.border` used for decoration. Borders only permitted for 2dp primary focus rings or selected tab thumbnails.
- [ ] **No Outlined Material 3 Components**: No `OutlinedButton`, `OutlinedTextField`, or `OutlinedCard`. Use tonal filled variants (`TextField`, `FilledTonalButton`, `Card` / `Surface`).
- [ ] **Unified Button Groups**: Side-by-side buttons use `PetalConnectedButtonGroup` or `PetalChoiceButtonGroup` with 6dp spacing and 50dp/6dp corner morphing.
- [ ] **Standardized Shapes**: Standard buttons use pills (`CircleShape` or 50dp); icon buttons use circles; cards use 16dp rounded corners.
- [ ] **No Hardcoded Hex Colors**: All colors use `MaterialTheme.colorScheme` tokens.
- [ ] **Transient Surfaces**: Bottom sheets use `PetalSheet`; dialogs use `PetalDialog` or `PetalMaterialAlertDialog`.
- [ ] **Touch Targets**: All interactive elements maintain a minimum touch target of 48.dp.
- [ ] **Accessibility & Ellipsis**: Text labels have `maxLines = 1` and `TextOverflow.Ellipsis` where bounded, with descriptive content descriptions.
- [ ] **Calm Motion**: Transitions use `MotionScheme.expressive()` or fast effects specs with no aggressive overshoot.
