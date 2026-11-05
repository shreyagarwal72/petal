# Petal Motion Design & Implementation Guide

This guide establishes the single source of truth for motion and animations across Petal Browser (Jetpack Compose and Android Views).

---

## 1. Motion Tokens (`PetalSpring.kt` & `PetalMotion.java`)

All motion specifications live in [`PetalSpring.kt`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/ui/components/PetalSpring.kt) for Compose and [`PetalMotion.java`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/motion/PetalMotion.java) for Views.

| Token | Type | Characteristics | Use Cases |
| :--- | :--- | :--- | :--- |
| `container()` / `containerMorph()` | Spring (`damping=NoBouncy`, `stiffness=520f`) | Fast, critically damped, interruptible | Address bar ↔ Omnibox, Grid tile ↔ Page hero, Settings row drilldown |
| `sheetDragSettle()` | Spring (`damping=0.86f`, `stiffness=350f`) | Smooth tactile settle with release velocity | Bottom sheets when finger lifts or settles |
| `menuPop()` / `menuScale()` | Spring (`damping=0.82f`, `stiffness=450f`) | Crisp origin-anchored expansion with gentle settle | Popup menus, overflow menus, context menus |
| `menuCloseScale()` / `menuCloseFade()` | Tween (110ms / 90ms, `FastOutLinearIn`) | Snappy, closes faster than it opens | Menu dismissal |
| `listReorder()` | Spring (`damping=NoBouncy`, `stiffness=420f`) | Calm, stable item positioning | Tab card reordering, Omnibox suggestions list |
| `fadeThroughIn()` | Tween (210ms, delay 90ms, `EmphasizedDecelerate`) | Material 3 Expressive fade-through enter | Tab category switches (Regular / Groups / Incognito) |
| `fadeThroughOut()` | Tween (90ms, `EmphasizedAccelerate`) | Snappy exit before incoming content | Tab category switches |
| `progressFade()` / `faviconFade()` | Tween (150ms, `FastOutSlowIn`) | Synchronous opacity fade without pop-in | Page load progress bar & favicon crossfade |
| `dialogScaleIn()` / `dialogFadeIn()` | Spring (`damping=0.82f`, `stiffness=450f`) | Origin pop with subtle overshoot | Alert & confirmation dialogs pop-in |
| `dialogScaleOut()` / `dialogFadeOut()` | Tween (130ms / 120ms, `FastOutLinearIn`) | Rapid dismissal | Dialog exits |
| `segment()` | Spring (`damping=NoBouncy`, `stiffness=500f`) | Velocity-preserving retargeting | Tab bar segment switches, press feedback |

---

## 2. Core Performance Rules

1. **Transform Only GPU Properties**:
   - Animate **only** `scale`, `translation`, `alpha`, and `clip`.
   - Never animate layout properties per frame (`width`, `height`, `padding`, `margin`).
2. **Read Animation Values in Render Blocks**:
   - Always read animation states inside `.graphicsLayer { ... }` or `drawBehind { ... }`.
   - Never read animated values during the composition phase (e.g. `Modifier.padding(animatedDp)`).
3. **Interruptibility**:
   - Use springs for any interaction driven by touch or gestures.
   - Cancel previous coroutine animation jobs before starting a new one (`currentJob?.cancel()`), preserving current position and velocity.
4. **Snapshot Policy for Heavy Content**:
   - Capture heavyweight page bitmaps (WebViews/GeckoView) **once** before the transition starts.
   - Reuse the cached bitmap for the entire animation.
   - Release the snapshot and flush deferred tasks (thumbnail saves, favicon loads) only after all motion finishes.
5. **Reduced Motion & Low-End Fallbacks**:
   - If `MotionGate.isReduceMotion()` is true, replace spatial translations and expansions with a short, immediate crossfade.
   - If `MotionGate.isLowEndDevice()` is true, avoid blur passes and simplify glass layers to solid translucent fills.

---

## 3. MotionGate Coordination

[`MotionGate`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/motion/MotionGate.kt) coordinates overlapping transitions and controls expensive visual effects:

```kotlin
// Wrap any transition block:
MotionGate.withMotion("transition_name") {
    animatable.animateTo(targetValue, PetalSpring.containerMorph())
}

// In Compose, observe or simplify expensive effects:
if (MotionGate.shouldSimplifyEffects()) {
    // Drop liquid glass / dynamic blur to static translucent fill
}
```

---

## 4. How to Add a New Transition

1. **Add Token (if unique)**:
   Define the animation spec in `PetalSpring.kt` (and equivalent interpolator in `PetalMotion.java` if used in Views).
2. **Acquire MotionGate**:
   Wrap the animation in `MotionGate.withMotion("my_transition")`.
3. **Structure the Modifier**:
   Apply `.graphicsLayer { ... }` and read the animated values inside the lambda.
4. **Check Accessibility**:
   Provide a `reduceMotion` branch (e.g. instant snap or short `fadeIn()` / `fadeOut()`).
