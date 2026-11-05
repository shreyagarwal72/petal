# Current Project State

## Active Phase
- **Phase**: 04
- **Name**: Menus, Sheets, Settings and Audit
- **Status**: Completed

## All 4 Phases Complete
1. **Phase 1: Motion Foundation**:
   - `PetalSpring` single source of truth tokens.
   - `MotionGate` counter-based coordinator dropping blur/glass to static fill.
   - Snapshot policy for heavy content.
   - `PetalJankTracker` and Baseline Profile coverage.
2. **Phase 2: Tab Transitions**:
   - Button-origin new tab grow & reverse (`PetalNewTabOrigin`).
   - Tab close card shrink & fade (`listReorder` neighbour slide, velocity swipe-to-close).
   - Category switch horizontal slide + fade-through.
   - Interruptible hero morph & predictive back.
3. **Phase 3: Address Bar & Chrome**:
   - Symmetrical container morph for omnibox enter/exit (`BarGhost`).
   - Scroll-to-pill direct offset driving without timers, spring settle on stop/reverse.
   - Synchronized progress bar and favicon fade (`progressFade`).
   - Omnibox suggestions stable keys & list-reorder animations.
4. **Phase 4: Menus, Sheets, Settings & Audit**:
   - Menus: origin-anchored scale (`menuScale`), item fade (`menuItemFade`), fast close (`menuCloseScale`).
   - Bottom sheets: drag-follow with `sheetDragSettle` spring and fling/threshold dismiss.
   - Settings & Downloads: row expand container morph into detail page and reverse on back.
   - Dialogs: shared `dialogScaleIn` / `dialogScaleOut` and `dialogFadeIn` / `dialogFadeOut`.
   - Comprehensive audit of codebase for inline springs/tweens, layout property animations, and composition-phase animation reads.
   - Written `docs/motion.md` motion guide.
