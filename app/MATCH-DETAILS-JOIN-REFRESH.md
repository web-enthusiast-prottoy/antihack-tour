# Match details and joining refresh · stage 3 · 11 November 2026

Scope: the approved match details, Overview/Prizes/Players panels, room presentation, fixed join action, entry requirements and roster confirmation sheet. Previously completed Home, authentication and Explore designs are retained. No APK handoff, deployment or production document change was performed.

## Updated UI

- Details show saved match artwork, the quiet countdown at its top-right, the real title/mode/map/date and fee/prize/participation metrics. Overview starts with rules expanded by default, followed by room access, then muted label-size match ID/status. Saved description, cancellation reason and registered roster remain available.
- Rules and the guest/pending room explanation use short native disclosure transitions, with accessible expanded/collapsed states. Confirmed entries continue to receive the existing server-checked room UI immediately, including selectable credentials, Copy, Refresh and Hide. Leaving the Overview panel disposes the existing room controller and clears its state.
- Prizes use a sorted position/amount table and per-kill line, with exact paisa and all loading/cache/unpublished/empty/error/retry states. The staff editor is retained.
- Players show numbered names, muted FF UID and existing teammates. The public participant feed retains paging, offline fallback, refresh and retry. Counts identify loaded data when more pages or cached results exist.
- Joining shows one compact match summary, the actual fee and a balance-after-entry preview when a verified balance is available. The “Your player”/captain summary is removed. Duo/Squad still collect the required teammate names. Keyboard Next/Done, saved teammate names, distinct-name/length validation, busy disabling, server errors and retries remain. Availability and verified paid balance changes disable confirmation while open. Missing setup/funding still opens the existing Profile/Wallet flow and retains pending-match continuation.

## Data compatibility

No Firebase endpoint, project, schema, security rule/index, authentication method, registration transaction, paid-entry operation, wallet operation or room release requirement was changed. A stage-specific hash comparison verifies 51 Firebase/data/backend files are unchanged, including `PlayerData`, `AdminSession`, `RoomAccess`, `BoundedFeed`, `Artwork`, `Tournament`, `PaidEntries`, `MatchOperations` and `LiveWallet`.

Additional source comparisons verify that the complete participant feed, room lifecycle/release-polling controller and prize subscriptions/save operations remain identical to the earlier implementation. The root join action retains its existing account/profile/ban/cache/entry/availability guards and dispatches to the same `data.join` method. No production data was used or modified for tests.

**FF UID availability:** current public participant records contain names/rosters and omit `ffUid`. The UID line is omitted when this optional public value is absent or blank, and displays a real value only when supplied by the public record. It never substitutes a Firebase account UID, reads private user profiles, fabricates IDs or publishes private IDs. Populating this value for existing/future public records requires a separately verified data-publication change; this UI stage does not introduce it.

## Automated checks

`MatchDetailsJoinUiTest` adds 22 native Compose host tests using Android 14/Robolectric, production themes/resources and explicitly local sample fixtures. Coverage includes:

- Expanded/collapsed rules, restored disclosure state, changed rules/match fields, metadata hierarchy and tabs.
- Fixed action availability/busy states, minimal Solo payload, Duo duplicate/length/trim validation, Squad keyboard submission and duplicate-submit protection.
- Roster restoration, server-failure retry with retained names and updated fee, changed availability/unknown mode, exact setup/funding shortfall.
- Unconfirmed credential suppression, room loading/unavailable/refresh/hide/show callbacks and exact clipboard values.
- Prize ordering/zero removal/exact amounts/cache/retry/unpublished/empty states; player public IDs/missing values/teammates/paging/cache/error/retry.
- Native render captures, narrow 320dp double-size English text and double-size Bangla landscape reachability.

Existing device journeys were updated to target the new confirmation control and Players tab. The instrumentation APK compiles; these device tests have not been executed in this executor.

```sh
source /workspace/onboarding/activate.sh
cd /workspace/antihack-tour/app/android
./gradlew -I /workspace/work/antihack-redesign/managed-test-cache.gradle \
  -PbuildRoot=/tmp/rea-android-build-local \
  assemblePlayerDebug testPlayerDebugUnitTest lintPlayerDebug \
  assemblePlayerDebugAndroidTest \
  assembleAdminDebug testAdminDebugUnitTest lintAdminDebug
```

The environment-specific init script only directs Robolectric's cache to writable workspace storage; it does not change app runtime behaviour.

| Check | Result |
| --- | --- |
| Player debug build and full unit suite | Passed; 117 tests, no failures/errors/skips |
| New details/joining native UI tests | 22 passed |
| Player instrumentation build | Passed; not executed on a device |
| Player lint | No errors/fatal findings; 12 existing warnings |
| Admin debug build and unit suite | Passed; 73 tests, no failures/errors/skips |
| Admin lint | No errors/fatal findings; 17 existing warnings |
| Stage-specific data/controller comparisons | Passed |
| Whitespace diff check | Passed |

Logs and the source audit are under `/workspace/work/antihack-details`; native component render fixtures are in the executor's temporary `antihack-details-ui` directory. They are not live Firebase or physical-device screenshots.

## Remaining acceptance limits

Live Firebase/device acceptance remains unverified here. Production Firebase configuration and signing files are absent; no alternate project or signing identity was substituted. The managed network policy blocks the official Firestore emulator download from `storage.googleapis.com`, and no Android device is attached. Physical-device and full TalkBack checks remain outstanding. Passing local checks does not certify end-to-end live database behaviour.
