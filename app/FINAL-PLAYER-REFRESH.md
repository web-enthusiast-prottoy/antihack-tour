# Remaining approved player designs · 11 November 2026

This completes the reviewed player-page implementation in source. Home, Sign in/Create account, Explore and match details/joining remain from their earlier tested stages. This stage covers My matches/room shortcuts, results/reward breakdowns, Wallet/balance information/activity details, Add money/verification/outcomes, withdrawal/review/request receipt, Profile/editor/language/sign-out, notifications/readers and published information pages. The standalone staff app is not redesigned.

## Preserved behaviour and data

- My matches still uses the user's existing entries and saved match artwork. The existing owned history serves confirmed past matches; unresolved entries remain accessible. Room shortcuts check current bans and use the unchanged authenticated room-release controller.
- Result scopes, ownership links, query/search/paging and unpublished/pending matches remain. Announced awards, components and credited money are explicitly separate. No display value credits a wallet.
- Wallet keeps deposits, available winnings and reserved winnings distinct. Its spendable total excludes reserves. Whole-taka amounts omit .00; real nonzero fractional paisa are preserved. Activity retains every original ID, timestamp, provider reference and withdrawal state in its detail sheet. Filters apply to loaded records; manual paging remains available. Deposit/withdrawal/ledger deduplication is unchanged.
- Deposit submission uses the original exact amount parser, sender/reference validation, receiver verification, persisted request ID and operation payload. Its server-only status listener and deadline remain identical. Payment feedback shows the actual callback message. Cached/unverified payment instructions cannot enable submission or copying.
- Withdrawal keeps the original server policy/wallet/request reads, exact validation, persisted intent, stable ID and submit operation. Review is now an in-sheet step and survives restoration. Confirmation requires verified current settings and valid payout/fee/total, and busy controls block duplicates. Request receipts distinguish held funds from a sent payout.
- Profile writes still call `PlayerData.save`; optional fields and length limits are retained. Profile/language/sign-out sheets use saved UI state and the original preference/auth callbacks. Guest access stays public.
- Announcements retain the same published query, revision keys, read tracking and actual body. Preview truncation never truncates the reader. Date headings use Asia/Dhaka; missing dates are not invented. Published information retains enabled checks, validated links and cache/error handling.

A stage-specific hash audit verifies 58 Firebase/data/backend files are unchanged. It also compares Wallet query/paging/deduplication code, withdrawal server reads/validation, published information reads/fallback and deposit verification listener/deadline/callback code against the pre-stage source. The earlier authentication, Home, Explore and match-details functionality remains covered by the full regression suite. Production documents, accounts and balances were not used or modified for tests.

## UI verification

`RemainingPagesUiTest` adds native Android 14/Robolectric coverage for navigation callbacks, room eligibility, result settlement distinctions, exact balances and reserves, ledger-kind filtering, payment validation/IME/copy/busy states, unavailable payment instructions, payout review/receipt states, Profile validation/restoration, language/sign-out actions, unread revisions, Dhaka date grouping, announcement/error/empty/retry states and published text/links. Narrow 320dp English/Bangla double-size text and landscape reachability cover financial forms, balances, Profile, cards, readers and long information.

Native render fixtures are captured for My matches, results/reward breakdown, Wallet/activity/transaction details, Add money, withdrawal/review/receipt, Profile, notifications/readers and information. These are component renders using local test records, not live Firebase or physical-device screenshots. Existing device journeys were updated for new tabs, activity sheets, language choices and sign-out confirmation. Activity selection uses the full source-document path, so identical IDs in different ledger collections retain independent detail actions.

```sh
source /workspace/onboarding/activate.sh
cd /workspace/antihack-tour/app/android
./gradlew -I /workspace/work/antihack-redesign/managed-test-cache.gradle \
  -PbuildRoot=/tmp/rea-android-build-local \
  assemblePlayerDebug testPlayerDebugUnitTest lintPlayerDebug \
  assemblePlayerDebugAndroidTest \
  assembleAdminDebug testAdminDebugUnitTest lintAdminDebug \
  assembleAdminDebugAndroidTest
```

The init script is environment-specific and only directs Robolectric's cache into writable workspace storage. It does not alter the app or Firebase configuration.

The final combined command passed on 11 November 2026 (Asia/Dhaka):

| Check | Result |
| --- | --- |
| Player debug build and full unit suite | Passed; 161 tests, no failures/errors/skips |
| New remaining-page native UI suite | 44 passed |
| Admin debug build and full unit suite | Passed; 73 tests, no failures/errors/skips |
| Player and admin instrumentation builds | Passed; not executed on a device |
| Player lint | No errors/fatal findings; 11 existing warnings |
| Admin lint | No errors/fatal findings; 17 existing warnings |
| Data/controller and prior-screen source comparisons | Passed |
| Whitespace diff check | Passed |

Logs, pre-stage source and the comparison audit are in `/workspace/work/antihack-remaining`. Component captures are in the executor's temporary `antihack-remaining-ui` directory. No APK handoff was performed.

## Acceptance limits

Local checks do not certify live Firebase/device behaviour. Production Firebase configuration and signing files remain absent. This managed environment blocks the official Firestore emulator download and has no attached Android device, so the database-backed device tests are compiled rather than executed. Physical-device/full TalkBack acceptance remains outstanding. No alternate Firebase project, dummy live configuration, billing or deployment was introduced.

The earlier FF UID data limitation remains: legacy public participant records omit it, so the UID line is hidden. This refresh does not read private profiles, fabricate game IDs or change public data publication.
