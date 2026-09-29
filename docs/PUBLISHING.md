# Publishing Petal

## Current release

- Application ID: `com.petal.browser`
- Version: `3.6` (`versionCode 360`)
- Source: `https://github.com/shreyagarwal72/petal`
- GitHub release: `v3.6`

## F-Droid

F-Droid publication is a review process in the separate `fdroiddata` repository; it is
not an upload to this GitHub repository.

Before submission, resolve these likely inclusion blockers in the FOSS variant:

- `credentials-play-services-auth`
- `googleid`
- `play-services-auth`
- Google ML Kit text recognition and barcode scanning

These dependencies need a F-Droid-compatible implementation or must be removed/disabled
from the FOSS build. Do not submit a release that still depends on unavailable Google
Play artifacts.

After that work:

1. Install and authenticate GitLab CLI: `glab auth login`.
2. Fork `https://gitlab.com/fdroid/fdroiddata`.
3. Create metadata at `metadata/com.petal.browser.yml` using the official F-Droid
   inclusion template.
4. Point the metadata `SourceCode` field to this repository and use a signed, immutable release tag.
5. Run F-Droid metadata/build checks locally or in GitLab CI.
6. Open a merge request against `fdroiddata` and respond to maintainer review.

F-Droid signs the published APK with its own repository key. Do not upload the GitHub
release APK as though it were an F-Droid build.

## Orion Store

No Orion Store developer endpoint, account, or CLI is configured in this workspace.
Create the developer account first and obtain its documented upload method/API token.
Then upload the same release APK only if Orion accepts GitHub-sourced APKs and the
package/signing identity matches the release. Never commit the token or keystore.

## GitHub release automation

`.github/workflows/android_build.yml` already builds and signs release APKs and uploads
them to GitHub Releases. Configure these repository secrets before using a production
signing key:

- `SIGNING_KEY` — base64-encoded keystore
- `ALIAS`
- `KEY_STORE_PASSWORD`
- `KEY_PASSWORD`

The current fallback key generation is for CI survivability only and must not be used
for a public store release.
