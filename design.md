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
