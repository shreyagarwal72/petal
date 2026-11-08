# Project: Petal Browser GeckoView Stabilization

## Overview
Stabilization and architectural alignment of Petal Browser (`com.petal.browser`) running on GeckoView `159.0.20261102144711` and Mozilla Android Components `159.0.1`. The goal is to maximize web compatibility, eliminate rendering jank and startup blanking, ensure persistent tab navigation history across cold restarts, and align with upstream Mozilla components.

## Hard Constraints
- **Address Bar & Navigation Padding**: Do NOT touch `BrowserActivity.java` address bar or bottom-nav padding logic.
- **GeckoView Version Alignment**: Never ship two GeckoView versions. Keep `geckoview` and every `org.mozilla.components:*` artifact strictly on one version.
- **Distribution Boundaries**: Strictly follow FOSS / Play flavor boundaries outlined in `AGENTS.md`.
- **Environment & Verification**: Do not run local Gradle builds. Verify by reading code. One shell command per invocation (no `&&` or `||` chaining).
- **UI Integrity**: No UI or animation changes that were not explicitly requested. Liquid glass and Material 3 Expressive design remain intact.
- **Change Management**: Each step is its own change set. Each risky step is gated behind a debug flag (default `false`) until verified. Record a one-line reason and touched files for every change.

## Scope & Milestones
- **Phase 1**: Verify Done Items (R8 rules, reflection audit, fingerprint keys)
- **Phase 2**: Cold-Restart History Preservation (session restore, Back traversal, prevent Home drop)
- **Phase 3**: Website Rendering & Theming (dark mode precedence, white flash elimination, theme-color tint)
- **Phase 4**: Runtime & Settings Parity with Fenix (GeckoRuntime/Session settings matrix, desktop site key migration)
- **Phase 5**: Error Handling & Dead Code Cleanup (silent catch triage, GeckoResult error handlers, thread safety)
- **Phase 6**: Replace Custom Code with Mozilla Components (flagged migration to AC modules)
