# Player 1.0.9 — match visibility and participant history

Owner explicitly authorized production publication and GitHub Release distribution on 9 November 2026.

The release is prepared from an isolated checkout of the last published source, e37b3a239aeb76fb0699c9018dc55f4aafa6c538. It includes only the five-minute browse cutoff, participant result history, English/Bangla copy, boundary/history tests and player version bump. Uncommitted admin/reward work is excluded. No backend deployment or financial mutation is required.

Live configuration is arena-ff-34506; FIREBASE_USE_EMULATOR and LOCAL_OPERATIONS are false. Package bd.arena.prototype, version 1.0.9/code21. Debug and release unit suites each passed 54 tests with zero failures/errors, including registration and five new timing/history tests. Both lint runs report zero errors and 21 existing warnings. Checks use scripts/build.sh live.

Cutoff: a known schedule remains visible through exactly start + 300000 milliseconds, then is excluded from Explore. Home remains categories/announcements. Own results uses bounded live pages of private entries and result links; confirmed past participation is included before score publication, and published metadata preserves older history. Server match status, joining permissions and rewards do not change. Display timing uses the device clock.

The previous local pending→published instrumented journey remains unverified because the existing Firestore emulator is unresponsive. The earlier debug source build covered the broader unfinished workspace; the release unit suites above cover this isolated release source. Physical-device acceptance is not claimed.

Final signed live debug/release build, both 54-test suites, and both lint tasks passed. Release APK SHA-256: 7f15e18dce16f807202be68ac2c5ef197e4b7072930ef76eefe8e4bb1648b4e2. Production certificate SHA-256: b9f75e282e0effa35941178cc8520185387780e0703d5e9bd534906c0d3f6e50; verified against the previous live player APK. aapt confirms package/version and non-debuggable release. No private signing/configuration material is included in source changes or release assets.

Release target: https://github.com/web-enthusiast-prottoy/antihack-tour/releases/tag/player-v1.0.9 . The publisher uploads the APK and player-update.json to a draft before making it Latest. Post-publication anonymous download/hash checks are retained locally in public-verification.json.
