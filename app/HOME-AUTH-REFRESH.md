# Home and authentication refresh · stage 1

Scope: the approved Home, Sign in and Create account designs only. The later Explore, details, room, result, wallet, payment, profile and notification redesigns have not been applied in this stage.

## Preserved data and behaviour

- `AdminSession.login`, `AdminSession.register`, credential validation, Auth persistence and account/ban checks are unchanged. The redesigned view dispatches to those same Firebase methods. Guest dismissal and the existing post-authentication join continuation remain wired through `PlayerApp`.
- Category queries, active filtering, order, automatic bounded pagination, IDs and artwork document lookup/cache are unchanged. Category actions pass the current category ID into the existing Explore filter. The general Explore action resets the existing filters.
- Notice queries, revision-based read tracking, notification count semantics/capping, full notice opening and retry subscriptions are unchanged. Home shows a compact preview, including cached/error recovery.
- No Firestore rule/index, backend operation, balance, transaction, production document, Firebase endpoint, application ID, release version or signing configuration was changed. Fixture text/data exists only in tests.

## Regression coverage

`HomeAuthUiTest` runs native Compose components on Android 14 under Robolectric, with the production player theme/resources. It exercises entered credentials and submission mode, busy/duplicate-submit protection, unchanged registration validation, keyboard actions, email/mode restoration without saved passwords, switching/back/guest actions, outgoing form submission protection, Bangla double-size landscape reachability, updated category/notice payload callbacks, cached announcement retry, large-count/large-text header bounds, reduced-motion loading and native rendering. It does not stand in for a physical phone or live Firebase integration test.

Existing device account/registration tests now target the submit control rather than ambiguous text shared by the new page title and button. They are compiled as part of the player instrumentation APK.

Native component render artifacts use explicitly labelled sample fixtures, not live account or tournament records. They are written to the executor's temporary `antihack-home-auth-ui` directory.

## Commands and environment

Use the project JDK/SDK configuration from `/workspace/onboarding/activate.sh` in this managed executor. Its ordinary Java user directory is read-only; the local init script `/workspace/work/antihack-redesign/managed-test-cache.gradle` directs Robolectric's lock/cache to a writable workspace directory. This init script is environment-specific and does not change the app or production runtime. Normal development environments with a writable Java user directory do not need it.

```sh
./gradlew -I /workspace/work/antihack-redesign/managed-test-cache.gradle \
  -PbuildRoot=/tmp/rea-android-build-local \
  assemblePlayerDebug testPlayerDebugUnitTest lintPlayerDebug \
  assemblePlayerDebugAndroidTest \
  assembleAdminDebug testAdminDebugUnitTest lintAdminDebug
```

## Completed validation

The complete command passed on 10 November 2026:

| Check | Result |
| --- | --- |
| Player debug APK | Built successfully |
| Player unit suite | 78 passed; 0 failures/errors/skips |
| New native UI suite within player tests | 16 passed |
| Player instrumentation APK | Built successfully; not executed on a device in this executor |
| Player lint | 0 errors/fatal findings; 12 warnings |
| Admin debug APK and unit suite | Built successfully; 70 passed |
| Admin lint | 0 errors/fatal findings; 17 warnings |
| Diff whitespace check | Passed |
| Firebase/data-layer source comparison | Unchanged session, player data, notice, artwork, pagination, entry/payment/wallet/room and Firebase backend files; unchanged category feed/query implementation |

The native suite also covers password Show/Hide in both registration fields, credential retention after server failure and long database category names at double text size. A stale sign-in callback is explicitly rejected after switching to registration. Native render capture uses the Android view renderer under Robolectric; these are component renders, not device screenshots or evidence of live connectivity.

Checked palette contrast is 16.72:1 for primary text/background, 7.76:1 for secondary text/card, 12.80:1 for sage/background and 4.04:1 for outline/background. Normal 360dp portrait, Bangla double-text landscape, large-text notification badge bounds and reduced-motion loading have automated coverage. Full physical-device and TalkBack acceptance are not claimed.

## Live-release limits

This checkout does not contain the existing production `google-services.json` or `.signing` release files required by the existing live build. A local emulator-flavour APK must not be distributed as the live player update. No alternate Firebase project or signing identity was substituted.

The managed network policy denies `storage.googleapis.com`; a direct request for the official Firestore emulator JAR returned HTTP 403. Consequently the Firestore-backed device integration suite cannot be run in this executor as currently configured. The native UI tests and existing unit tests run independently of that unavailable service. No production accounts, documents or balances were changed for testing. Physical-device, full TalkBack and live Firebase acceptance remain separate checks.
