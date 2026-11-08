# State: Petal Browser GeckoView Stabilization

## Current Status
- **Milestone**: 1 (Core GeckoView Stabilization & Parity)
- **Active Phase**: Phase 1 (Verification of Done Items) -> Transitioning to Phase 2
- **Completed**:
  - ProGuard rules audit: Verified `GeckoEngineSessionState` and `GeckoEngineSession` are kept hard.
  - Reflection audit: Verified `Shizuku`, `PlayerView`, and GeckoView sessions have explicit `-keep` declarations in `proguard-rules.pro`.
- **Next Up**:
  - Update `syncFingerprint` in `PetalGeckoRuntime.kt` to cover `sp_privacy_profile`, `sp_security_slider`, and custom DoH URL keys.
  - Implement cold-restart history fixes in Phase 2.
