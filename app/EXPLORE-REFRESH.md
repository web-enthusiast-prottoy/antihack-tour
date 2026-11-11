# Explore refresh · stage 2 · 11 November 2026

The approved Explore design is implemented in the native player app. No APK handoff, deployment, Firebase document update or production configuration change was requested or performed.

## Design and preserved behaviour

- Compact All/Solo/Duo/Squad pills share the existing saved mode state with the filter sheet. ID/title search, clear and keyboard Search retain the same callbacks. Filter counts remain accessible; the count badge uses the sage accent.
- Explore has its own compact card, with saved artwork in a 108dp banner and the approved dark countdown at its top-right. The existing whole-card action opens the same match ID. My matches and details keep their prior card implementation.
- Title, mode/map/date, entry fee, prize pool and participation all use the existing `Tournament` fields mapped from Firestore. Whole-taka .00 is hidden in this view; nonzero fractional paisa remain exact. Fee/prize amounts remain aligned when large Bangla labels wrap. Entry status, room-details indication, closed/full and cancellation states remain visible.
- The existing shared loading placeholder now also serves Explore's empty first load. Later-page loading keeps existing cards visible. Native chip transitions and a 220ms participation animation respect Compose's platform motion scaling; skeleton shimmer is disabled when Android animators are disabled.
- Category handoff from Home, direct Explore reset, combined category/mode/fee/availability/date filters, search through additional pages, Load more, cached-result disclosure, loaded-count wording, error/retry, empty results and the five-minute past-match visibility boundary remain in place.
- `PlayerData`, `AdminSession`, `BoundedFeed`, artwork loading/cache, `Tournament` filtering, entry/payment/wallet/room logic, Firestore rules/indexes and backend files are unchanged. Existing Home/authentication work is retained; its placeholder helper was renamed for reuse without changing rendering or motion behaviour.

## Tests

`ExploreUiTest` contains 14 native Compose host tests under Robolectric/Android 14. Coverage includes all four mode actions, external mode changes, 48dp touch bounds, search/clear/IME/filter callbacks, whole-card navigation, exact field/price rendering, replaced observed match values, entry/full/closed/cancelled labels, countdown second/day/start/status boundaries, zero/overfilled capacity, English and Bangla 200% text at 320dp, landscape reachability, reduced-motion loading and native render captures. These are component tests using local fixtures, not evidence of a live Firestore connection.

Three additional `MatchBrowseTest` cases cover combined category/search/mode filtering, changed price/availability/date values and the exact visibility/sort boundary. Existing Home/authentication, registration, payment/room and other unit tests remain in the full suite.

The device suite also gains a read-only Firebase-fixture journey covering mode pills, selection through the full filter sheet, ID search and card navigation. It is compiled with the instrumentation APK; it has not been executed here.

## Verification command

In this executor, activate the project JDK/SDK with `/workspace/onboarding/activate.sh`. The environment-specific Gradle init script directs Robolectric's Java user-directory cache into writable workspace storage; it is not an app/runtime change.

```sh
./gradlew -I /workspace/work/antihack-redesign/managed-test-cache.gradle \
  -PbuildRoot=/tmp/rea-android-build-local \
  assemblePlayerDebug testPlayerDebugUnitTest lintPlayerDebug \
  assemblePlayerDebugAndroidTest \
  assembleAdminDebug testAdminDebugUnitTest lintAdminDebug
```

The final command passed on 11 November 2026 (Asia/Dhaka):

| Check | Result |
| --- | --- |
| Player debug build | Passed |
| Full player unit suite, including Home/authentication | 95 passed; no failures/errors/skips |
| New Explore native UI suite within player tests | 14 passed |
| Player instrumentation build | Passed; device execution remains unavailable |
| Player lint | No errors/fatal findings; 12 existing warnings |
| Admin debug build and unit suite | Passed; 73 tests passed |
| Admin lint | No errors/fatal findings; 17 existing warnings |
| Diff whitespace check | Passed |
| Stage-specific source-hash audit | 49 Firebase/data-layer/backend files unchanged |

Local build logs/source-hash audit are in `/workspace/work/antihack-explore`; native component renders are in the executor's temporary `antihack-explore-ui` directory. They use sample records rather than production data.

## Database/device acceptance limits

The existing live build requires production `google-services.json` and release signing files that are absent from this checkout. No alternate Firebase project or identity was substituted. The managed network denies the official Firestore emulator download from `storage.googleapis.com` with HTTP 403, and this executor has no attached Android device. Consequently live-database and device integration acceptance cannot be certified here. No production accounts, documents or balances were used for tests. Physical-device and full TalkBack acceptance remain outstanding.
