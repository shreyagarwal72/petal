# Petal Motion Modernization

## Overview
A 4-phase motion overhaul for Petal Browser (Android Compose + Views), unifying motion tokens into `PetalSpring`, gating heavy graphics (liquid glass/blur) via `MotionGate`, and implementing fluid, interruptible screen, tab, and chrome transitions.

## Milestones & Phases
- **Phase 1: Motion Foundation** (COMPLETED)
  - Unified `PetalSpring` single source of truth tokens.
  - Implemented `MotionGate` counter-based gating and liquid glass / blur simplification.
  - Snapshot policy for heavy content before transition starts.
  - Jank tracking integration.
- **Phase 2: Tab Transitions** (COMPLETED)
  - Button-origin container morph for new tabs (`PetalNewTabOrigin`).
  - Tab close shrink, fade, and neighbour slide (`animateItem` with `listReorder`).
  - Horizontal slide + fade-through category switch.
  - Interruptible hero morph & predictive back.
- **Phase 3: Address Bar and Page Chrome** (IN PROGRESS)
  - Address bar -> omnibox container transform mirroring in both directions with `BarGhost` & `PetalOmniboxOrigin`.
  - Scroll-to-pill collapse driven directly by scroll offset without timers, settling with spring on stop/reverse.
  - Page load progress and favicon synchronized fade using progress token.
  - Omnibox suggestions stable list-reorder animations without layout jumps while typing.
- **Phase 4: Screen Transitions** (UPCOMING)
