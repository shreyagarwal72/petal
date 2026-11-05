# Phase 03: Address Bar and Page Chrome Plan

## Goal
Implement polished, interruptible motion for address bar, omnibox, scroll-to-pill, progress/favicon, and suggestions list using `PetalSpring` tokens, `MotionGate`, and GSD spec-driven workflow.

## Tasks
1. **Omnibox Container Morph Symmetrical Token**:
   - Update `PetalMotion.java` `runContainer` to use `EMPHASIZED` for both opening and closing.
   - Unify exit duration in `BrowserActivity.java` (`collapseOmniboxThen`) to match `PB_TRANSITION_DURATION_MS`.
2. **Scroll-to-Pill Direct Offset Driving & Spring Settle**:
   - Update `PetalTabViewController.kt` to drive `BarCollapseState.collapsedFraction` directly by delta scroll Y without cooldown timers.
   - When scroll stops or reverses, trigger spring settle to target (0f or 1f) using `settlePillFraction`.
   - Ensure top bar, bottom bar, and merged bar all react seamlessly.
3. **Synchronized Progress & Favicon Fade**:
   - Update `Material3ExpressiveProgressIndicator` in `PetalProgressBarBridge.kt` to use `PetalSpring.progressFade()`.
   - Update `AddressPill.kt` to ensure favicon fade synchronizes with progress fade using `PetalSpring.faviconFade()`.
4. **Omnibox Suggestions List Reordering**:
   - In `PetalOmniboxPage.kt`, apply stable keys and `Modifier.animateItem(placementSpec = PetalSpring.listReorder(), fadeInSpec = PetalSpring.fadeIn(), fadeOutSpec = PetalSpring.fadeOut())` to suggestion items.
   - Prevent height/layout shifts during typing.

## Verification
- Verify omnibox container morph opens and closes smoothly from the exact bar position.
- Verify scroll-to-pill follows finger scroll without delay and snaps to pill or expanded bar with spring settle.
- Verify page load progress bar and favicon fade together without pop-in.
- Verify suggestions animate smoothly without layout jump when typing.
