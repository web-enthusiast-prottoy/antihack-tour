# Update discovery repair — player 1.0.10

The owner reported no update alert on player 1.0.8. Reproduced a 504 from its only manifest URL, even though the release and assets existed and anonymous API downloads worked. The old checker swallowed the failure and waited an hour. The previous 1.0.9 publication did not repair this client transport, and its alert delivery was not verified end to end.

The repaired client reads GitHub's public latest-release API. New releases carry the identical manifest in a hidden release-note comment, avoiding an asset download just to discover a version. Older releases use the public manifest-asset API. The original manifest URL is retained as fallback for API outages/rate limits. Package, version, release tag and repository APK URL remain validated; responses are size-bounded. Failed checks retry after one minute, doubling to at most 30 minutes; successful checks remain hourly and foreground entry checks again. No credential is embedded in the app.

The publisher still creates a draft, uploads APK and player-update.json, then makes the complete release Latest. It also embeds that same manifest in the API-visible release body. The fallback metadata is not a separate release or an invented future version.

An installed 1.0.8 client cannot acquire new updater code from an endpoint it cannot read. A one-time manual upgrade over the existing app is required for affected clients. Do not uninstall. No production backend or financial change is needed. Unfinished admin/reward changes remain excluded through an isolated checkout.

Validation completed:
- Signed live debug/release build passed. All 57 unit tests in each variant passed, including registration and three new response-limit/retry tests. Both lint runs have zero errors and 21 existing warnings.
- Ten Android device tests passed in 47.967 seconds: embedded API metadata despite a failed gateway, legacy asset API discovery, API outage fallback, current-version silence, offline failure propagation, draft/tag rejection, JSON parsing, Download/Later, 200% Bangla and a real public-release fetch that rendered the update alert for installed version code20 (1.0.8). The real test uses the production fetch routine and dialog with installed version20 supplied to the fetch routine; it does not publish a fake release. It fetched the currently published 1.0.9 before this release was published.
- The test app uses a temporary Gradle init script giving debug builds the .uitest suffix because the default debug signing key cannot replace a production-signed installation. Release source/configuration and the production app's data were preserved. The init script does not alter production sources or the release artifact.
- aapt verifies bd.arena.prototype, version1.0.10/code22, non-debuggable. apksigner verifies the unchanged production certificate: b9f75e282e0effa35941178cc8520185387780e0703d5e9bd534906c0d3f6e50.
- Signed APK SHA-256: 13ec445a0d22daa7ef8e27c1bc768b30881cecdb69010a1f38e5266700545f41 (6240100 bytes).

Public release metadata/hash checks and a post-publication real-release alert rerun are retained locally in verification/player-1.0.10. Physical-phone acceptance is not claimed.
