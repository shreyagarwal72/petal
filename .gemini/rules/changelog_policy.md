# Changelog & Versioning Policy for Petal Browser

Whenever implementing major changes, new features, UI refinements, or bug fixes in Petal Browser:

1. **Automatic Changelog Updates**:
   - Always update `CHANGELOG.md` at the root of the repository before committing.
   - Group entries into clear descriptive categories (e.g., `#### 🚀 Features & Enhancements`, `#### 🛠️ Bug Fixes & Refinements`, `#### 💎 Liquid Glass & UI`).
   - Write clear, technical, user-friendly notes detailing what was changed and why.

2. **Fastlane Release Metadata Sync**:
   - Whenever `versionCode` in `app/build.gradle` is updated or a release is prepared, ensure the corresponding fastlane release note exists at:
     `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   - Keep this file aligned with the release notes in `CHANGELOG.md` so GitHub Actions automatically populates GitHub Release notes.

3. **Version Progression**:
   - Follow semantic versioning (`versionName "x.y.z"`, `versionCode xyz`).
   - After a release is tagged and published, immediately prepare the next development milestone (`versionCode + 1`, `versionName "x.y.(z+1)"`) with an `In Development` section in `CHANGELOG.md`.
