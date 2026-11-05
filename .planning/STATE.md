# Current Project State

## Active Phase
- **Phase**: 04 (Follow-up)
- **Name**: Motion Refinement & Proper Back Transitions
- **Status**: Completed

## All 4 Phases & Back Motion Refinement Complete
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
5. **Phase-Specific Back Motion Refinement**:
   - **Omnibox**: Replaced generic predictive card-scale in `PetalOmniboxPage.kt` and `BrowserActivity.java` with symmetrical reverse container morph into the origin address bar pill via `collapseOmniboxThen()`.
   - **Settings Screen**: Intercepted back at the category level with `BackHandler`, animating `popCategory()` with `PetalSpring.containerMorph()` so categories collapse smoothly into list rows.
   - **Downloads Screen**: Intercepted back in download settings with `BackHandler`, reversing cleanly into the downloads list.
   - **Bottom Sheets**: Removed `PetalPredictiveBackSurface` from `PetalAboutDeveloperSheet.kt`, `PetalInactiveTabsSheet.kt`, and `PetalRecentlyClosedVault.kt` to ensure sheets dismiss downward rather than scaling down as desktop cards.
