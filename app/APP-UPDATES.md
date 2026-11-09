# Player app updates

Antihack Tour 1.0.8 (version code 20) introduces automatic checks for published player updates. Install this version once over the existing production app to enable future checks.

The live player app checks the public GitHub release record on foreground entry and hourly while open. Local sample builds do not check. Missing releases, invalid records and network errors never prevent using the app. A newer valid player version shows an English/Bangla update dialog with Download update and Later. Download opens the browser; users open the downloaded APK and approve Android installation. This is automatic detection, not silent installation or automatic downloading. Android may require permission to install from the browser. Existing publisher/device verification restrictions still apply.

Latest download: https://github.com/web-enthusiast-prottoy/antihack-tour/releases/latest/download/antihack-tour-1.0.9.apk

Release record: https://github.com/web-enthusiast-prottoy/antihack-tour/releases/latest/download/player-update.json

The record contains `packageId`, integer `versionCode`, `versionName`, `apkUrl`, and the APK `sha256` for independent verification. The updater accepts only higher player version codes and HTTPS APK links under this repository's release downloads. Android verifies the APK signing identity during installation. The app does not itself verify the optional hash because downloading/installation is handled by the browser and Android.

## Publishing subsequent versions

1. Increase the live player version code and version name in `android/app/build.gradle.kts`. Preserve the existing production package and signing key.
2. Run `./scripts/build.sh live`. This runs debug/release builds, unit suites including registration, and lint. Verify the update behavior on a device/emulator.
3. Commit and push the source used for the APK. Write release notes to a local text file.
4. Run `python3 scripts/publish-player-release.py --notes /path/to/notes.md --commit FULL_PUSHED_COMMIT`.

The publisher uses the existing GitHub sign-in, validates a signed non-debuggable player APK, creates a draft, uploads the APK and record, then publishes it as Latest. Do not mark an admin-only or unrelated release as Latest: the latest release must include `player-update.json` for player checks to work. Failed uploads leave a draft; inspect/remove the draft before retrying with the same tag. Never upload signing material or private account configuration.

Share each release's APK link, or the repository's latest release page. The APK filename changes per version; the update record URL stays the same.
