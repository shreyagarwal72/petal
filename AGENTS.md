# Petal repository roles

- `petal` is the main FOSS Petal repository.
- `petal_browser` is the Play Store-specific Petal variant. It intentionally omits
  the built-in app updater and retains ad compatibility needed for Play Store
  distribution.

## Release and synchronization rules

- Treat `petal` as the primary FOSS source repository.
- Keep fixes in `petal_browser` aligned with `petal` when they are compatible
  with the Play Store variant.
- Do not add the in-app updater to `petal_browser`.
- Preserve the GitHub Actions workflow in `petal_browser` that builds a
  Play Store-compatible Android App Bundle (`.aab`).
- Before mirroring a feature, verify it does not harm `petal_browser` ad
