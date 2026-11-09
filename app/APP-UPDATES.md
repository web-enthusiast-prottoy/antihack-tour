# Player app updates

Antihack Tour 1.0.8 (version code 20) introduces automatic checks for published player updates. Install this version once over the existing production app to enable future checks.

Since 1.0.10, the live player app checks GitHub’s public release API on foreground entry and hourly while open. A failed check retries after one minute, then backs off to a maximum of 30 minutes while the app remains open. The release-download manifest is retained as an independent fallback. Local sample builds do not check. Missing releases, invalid records and network errors never prevent using the app. A newer valid player version shows an English/Bangla update dialog with Download update and Later. Download opens the browser; users open the downloaded APK and approve Android installation. This is automatic detection, not silent installation or automatic downloading. Android may require permission to install from the browser. Existing publisher/device verification restrictions still apply.

Latest download: https://github.com/web-enthusiast-prottoy/antihack-tour/releases/latest/download/antihack-tour-1.0.10.apk

Release record: https://github.com/web-enthusiast-prottoy/antihack-tour/releases/latest/download/player-update.json

The record contains `packageId`, integer `versionCode`, `versionName`, `apkUrl`, and the APK `sha256` for independent verification. The updater accepts only higher player version codes and HTTPS APK links under this repository's release downloads. Android verifies the APK signing identity during installation. The app does not itself verify the optional hash because downloading/installation is handled by the browser and Android.

## Publishing subsequent versions

1. Increase the live player version code and version name in `android/app/build.gradle.kts`. Preserve the existing production package and signing key.
2. Run `./scripts/build.sh live`. This runs debug/release builds, unit suites including registration, and lint. Verify the update behavior on a device/emulator.
3. Commit and push the source used for the APK. Write release notes to a local text file.
4. Run `python3 scripts/publish-player-release.py --notes /path/to/notes.md --commit FULL_PUSHED_COMMIT`.

The publisher uses the existing GitHub sign-in, validates a signed non-debuggable player APK, creates a draft, uploads the APK and record, then publishes it as Latest. Do not mark an admin-only or unrelated release as Latest: the latest release must include `player-update.json` for player checks to work. Failed uploads leave a draft; inspect/remove the draft before retrying with the same tag. Never upload signing material or private account configuration.

Share each release's APK link, or the repository's latest release page. The APK filename changes per version; the update record URL stays the same.

## Repairing an installation stuck on 1.0.8

Version 1.0.8 has only the release-download URL and waits an hour after a failed check. It cannot acquire replacement updater code while that URL is unavailable. Install the signed 1.0.10 APK once over 1.0.8, without uninstalling. Later versions use the public API and faster failure retries.

The publisher embeds the same player-update.json record in an `antihack-player-update` HTML comment in release notes so the repaired client can read it in one API request. The separate manifest asset remains available for legacy clients. Older releases without the embedded record use the public manifest asset API. Clients validate package, version, release tag and repository APK URL. Requests remain anonymous; no GitHub credential is embedded in the app.
