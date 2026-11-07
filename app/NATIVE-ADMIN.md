# Antihack Admin · Operations Desk

Install **Antihack Admin Local** beside **Antihack Tour Local**. Staff sign in from the admin launcher with an existing account. The player app has its own sign-in and profile.

## Local build and setup

1. Run `./scripts/build.sh local` from the app directory. The script writes both local APKs to `releases/`.
2. Start Auth and Firestore with `./scripts/admin.sh run emulators`. In a second terminal, run `./scripts/admin.sh run seed`. Start `./scripts/local-operations.sh` for sample deposit submission and review.
3. Install `antihack-admin-local.apk` and `antihack-player-local.apk` on the Android emulator. Use the disposable staff sign-in printed by the seed script. Both apps connect to the same local data.
4. Open **Settings → Payments** for the receiving bKash number and withdrawal policy. Use **Settings → Announcements** to publish or hide a notice, and **Settings → Access & activity** for audit history. Open Settings → Support → WhatsApp support to edit the Profile support chat number.
5. Open **Finance** for deposit and withdrawal queues. Select an amount to review its details. Match rooms, prizes, participants, results, and refunds live inside the selected match under **Matches**.

To run device tests after both APKs are installed, run `connectedPlayerDebugAndroidTest` and `connectedAdminDebugAndroidTest` from `android/` with the local services running. The admin announcement round-trip uses the installed player app.

## Live build handoff

- Use the existing Firebase project and an Android app registration for `bd.arena.prototype.admin`. Supply its configuration as `android/app/src/admin/google-services.json`.
- Supply the admin release key and `.signing/admin-release.properties` locally. The properties are `storeFile` (relative to `.signing/` or absolute), `storePassword`, `keyAlias`, and `keyPassword`.
- Run `./scripts/build.sh admin-live` to build with the admin release key. To explicitly use the existing Antihack project key, run `./scripts/build.sh admin-live -PadminUseProjectSigning=true`. Verify it on the existing backend before switching staff to the standalone app. Build the player candidate with `./scripts/build.sh live` for the subsequent player update. The current production player update is version 1.0.7 / code 19; admin is 1.0.12 / code 24.
- The local build and device tests use disposable sample data. Follow the release checklist separately for distribution.

## Historical native admin notes

# Native admin design update · 0.11

Open Profile → Administration, then use the section picker to choose Matches, deposits, announcements, staff or audit. The picker shows only the sections allowed by the staff role. Match entry/prize/per-kill fields now accept taka, with up to two decimal places; storage remains integer paisa. Invalid extra decimals produce an explanation beside Save and do not change stored values. Room/result operations remain under each match's **Room and results** action.

See [verification/minimal-v0.11/REVIEW.md](verification/minimal-v0.11/REVIEW.md) for the current design checks. Earlier native-admin notes follow.

# Current native administration · 0.10

Administration is inside the Android app, entered from Profile after server-confirmed staff access. Matches now include a **Room and results** action: audited room/release editing, private result drafts, saved-draft review and immutable publication. Deposits and paid-entry approvals remain emulator-service-only; withdrawals remain unfinished. Use [TOURNAMENT-WORKFLOW.md](TOURNAMENT-WORKFLOW.md) for the current step-by-step workflow and limits.

The following notes describe the earlier 0.5/0.6 setup and its historical APKs.

# Native Android administration (emulator only)

The app contains native Compose screens. There is no embedded website or WebView, and no admin hosting/server is required.

1. In `app`, start Firebase with `./scripts/admin.sh run emulators`. If it is already running, keep that process.
2. Run `./scripts/admin.sh run seed` to provision the disposable owner account.
3. Install `releases/arena-native-admin-v0.5.apk` on the Android emulator on this Mac.
4. Open Profile and sign in using `admin@arena.test` / `ArenaLocal123!` (local disposable test account only).
5. Once Firebase confirms the admin role, **Admin** appears in the bottom navigation. Open it for dashboard, matches, announcements, configuration, review queues, staff and audit.

Ordinary accounts can sign in using the same Profile form but never see Admin. Signing out removes the tab and exits its screens. Cached roles do not grant access: a server-confirmed staff role is required. Firestore rules independently deny ordinary accounts access to protected records.

Only the provisioned owner sees Staff. Create an account in the Firebase Auth emulator first, then enter its UID in Staff and enable admin access. Disable access in the same screen to revoke it. Owners cannot change their own owner role through the app. No passwords are stored in Firestore.

Matches support Bangla title, description, bundled image preset, mode, map, Bangladesh start time, capacity, integer-paisa fees/prizes, rules, cancellation terms and status. Native admin typography uses bundled Hind Siliguri (OFL license in design/Hind-Siliguri-OFL.txt). Notices can be published or hidden; configuration can be saved. Writes append audit entries.

Payments, withdrawals, players and results are read-only review lists. Accounting, payouts, settlement, restricted rooms and player match/configuration synchronization remain unfinished, as before. No real money or production Firebase is connected. Status metadata never moves money. Lists are capped at 100 records, or 50 notices; pagination remains pending. Physical-phone emulator connectivity needs separate configuration; this build uses Android emulator host `10.0.2.2`.

Build and verify using Java 21 from `.tools/amazon-corretto-21.jdk/Contents/Home`:

```
cd android
./gradlew -PnoticeBackend=emulator -PbuildRoot=/private/tmp/arena-native-admin assembleDebug testDebugUnitTest lintDebug
```

The standalone `demo` build retains its sample preview and does not initialize the emulator account client.

## User registration (0.6)

In the emulator app, open Profile → নতুন ব্যবহারকারী? অ্যাকাউন্ট খুলুন. Enter an email address, password (at least six characters) and matching confirmation, then choose অ্যাকাউন্ট তৈরি করুন. Firebase automatically signs in the new regular account. Sign out from Profile and use সাইন ইন to return later. Passwords are kept by Firebase Authentication, never Firestore. New accounts cannot set staff roles or access Admin. Profile displays the UID for owner-managed grants.

This remains local-emulator authentication only. Accounts persist across app restarts while the emulator retains its data, but the current emulator launch command discards accounts when Firebase stops. The standalone sample APK still has no connected backend. No production account creation, reset-email delivery or email verification has been configured.

## Sample bKash SMS matching

In **Antihack Admin Local**, sign in as the sample owner and open **Settings → Payment settings → Enable sample SMS matching**. Grant SMS access on the Android test device. The matcher is tied to the current receiving number. If you change the receiving number, enable matching again for that account.

Submitting a sample claim starts verification against recent receipts from `25274`. The admin app retries while its session is running and checks pending claims when reopened. Exact, unique matches from the last seven days can credit the sample wallet once. iBanking receipts and unmatched claims stay in the review queue. This path requires the local operations service and sample Firebase data.

## Live bKash SMS auto-approval

On the phone receiving bKash messages, sign in as the owner and configure the live receiving number in **Settings → Payment settings**. Enable deposits, then select **Enable SMS auto-approval** and grant SMS access. Android requires the installer to allowlist SMS access. Use manual review if the phone cannot grant the permission.

The owner opts in for that receiving number. Exact, unique recent matches can credit real wallets. Claims with missing or conflicting evidence remain in review, and the existing receipt lock prevents duplicate credits. The app retries while its session is running and checks pending claims when reopened. SMS matching trusts the receipt reported by the receiving phone and does not query bKash directly.

## Check deposits from SMS

The owner can open **Finance → Deposits → Check deposits from SMS** to check all pending requests against receipts on the receiving phone. The button works independently of the background matching toggle once SMS permission is granted and current payment instructions are enabled. Matching requests can credit wallets; the panel reports processed requests and leaves other claims for manual review. Both paths use the seven-day receipt window, exact sender/amount/reference matching, server request timestamps and existing duplicate receipt protection. See `verification/sms-deposit-check/REVIEW.md` for evidence and the unresolved older-receipt report.

## Cleanup and simpler deposit confirmation · 1.0.2

Manual deposit review now displays the saved details. Check that successful receipt was verified, then choose **Approve deposit**; no amount, reference or phone-number re-entry is required. Rejection still requires a reason.

Owners can delete unused categories from their editor after saving them inactive and moving linked matches to another category. **Matches → select any match → Delete match** lets the owner permanently remove a match in any status after confirmation, including matches with registrations. Deletion does not refund entry fees. Registration/payment history and audit records remain protected. The app does not recursively erase historical documents or shared artwork.

Deletion permissions were activated on the existing Firebase project on 5 November 2026 after the owner reconnected Firebase. Use the signed admin 1.0.2 APK; no further app update is needed. See `verification/admin-cleanup/REVIEW.md` and `verification/admin-cleanup/live-deployment.json` for checks and activation evidence.

New matches can configure Total prize, per-kill rewards and placement prizes directly in the creation form’s Prizes tab. Add prize position adds #1, #2 and further positions; Save commits match and prize rows atomically. New-match totals are independent of placement sums. Existing legacy matches retain derived placement totals. Values use taka inputs and integer paisa storage.

## Background SMS verification

On the actual receiving phone, sign in as owner and open Settings → Payment settings. Confirm the receiving number, enable SMS auto-approval and grant SMS access. Allow background-verification notifications and use Battery settings to permit unrestricted background operation for Antihack Admin. Keep the phone online, powered on and signed in. Restart background verification is available after restrictions or permission changes.

The visible service watches pending deposits using Firestore and matches exact recent receipts from 25274. Mismatches, ambiguous/duplicate claims and unavailable receipts remain pending for manual review. Player submission waits up to 30 seconds for a server approval, then shows the pending fallback. Approval may happen later and appears in deposit history. Force-stop, power-off, Android restrictions or unsupported receipt formats can pause matching; notification/battery permissions cannot guarantee always-running service. No public phone webhook is required.

## Scheduled room release

Matches → selected match → Match room: enter ID/password, choose Release · Bangladesh time using the calendar and clock, leave Enable room release on, and Save room. Confirmed participants automatically see the credentials after release while the match is open, closed or live. Closing registration is no longer required. Existing saved disabled rooms must be explicitly enabled. Participants need the updated main app for automatic checks (within about ten seconds while viewing the card) and resume behavior.

## Participant moderation · live update, 6 November 2026

Open **Matches → selected match → Participants**, then tap a participant card to **View player**. The private view shows the registered account identity and current ban status. Enter **Reason shown to player**, choose **Ban from match** or **Ban app account**, then confirm. The entered reason is shared with that account, so keep internal-only notes elsewhere. The same view offers **Lift match ban** and **Lift account ban**, with a reason required for either change. Staff accounts are protected from this participant workflow.

A match ban prevents that account from registering or fetching room credentials for that match. An account ban blocks signed-in app features. Database rules enforce these restrictions; the updated player app also checks account access online. Bans retain entries, occupied seats, public registration snapshots and financial history. They do not refund fees, remove someone already in Free Fire or erase a room password already seen. Remove the player in-game and change shared room credentials separately when needed. Staff can still process supported cancellation refunds for banned accounts.

These are account bans, not phone bans: a new account on the same phone is not detected. No MAC address or other hardware identifier is collected. Android restricts ordinary apps from reading hardware MAC addresses. Stronger returning-device protection would require a trusted server integration; Google Play Integrity device recall currently also requires beta approval and a Play-licensed installation. See [Android identifier guidance](https://developer.android.com/identity/user-data-ids) and [device recall](https://developer.android.com/google/play/integrity/device-recall).

An account-banned player sees the saved reason, Retry and Sign out, with signed-in private features blocked. A match-banned player sees the reason on that match; registration and room access are disabled there while other account features remain available. Signing out still permits existing public guest browsing. Cached reasons are labelled, and current access always needs a server check. A missing reason is described as unavailable, not replaced with an invented accusation.

Signed moderation APKs are main 1.0.5/code17 (`releases/antihack-tour.apk`) and admin 1.0.7/code19 (`releases/antihack-admin.apk`). Both retain the existing production certificate. Debug/release builds, registration/unit suites, lint, 127 local database/service checks and eight emulator UI executions passed. Visual inspection was repeated after dismissing an emulator System UI error; the final screenshots and native-window checks are unobstructed. Phone/tablet portrait/landscape, 200% English/Bangla text and reduced motion were checked, and display/animation settings restored.

The matching live rules were deployed after the owner reported an all-account access verification failure and requested urgent repair. Live client checks confirm normal access, private ban reasons, blocked banned access and restored access after lifting; temporary verification records were removed. Existing installations can tap Retry or reopen the app. The older `*-live.apk` aliases were not changed. Evidence: `verification/access-verification-fix-2026-11-06/REVIEW.md`; build/UI acceptance and remaining physical-device/TalkBack checks: `verification/moderation-live-2026-11-06/REVIEW.md`.

## Receipt verification fix — 1.0.8

The reader now accepts both **25274** and **bKash** SMS senders. The earlier address-only query silently excluded branded bKash messages even when SMS access was granted and the foreground notification was visible. Settings → Payment settings now shows Verification status, last update, source permissions, expandable scan/hold details and Copy verification report. Finance → Deposits → **Check deposits now** processes matching existing receipts without waiting for a new message. Duplicate claims remain in review; reject duplicate/incorrect requests before retrying.

On the receiving phone, install Admin 1.0.8, sign in as owner and enable auto-approval or choose Restart background verification. Keep SMS permission enabled. Optionally use **Open bKash notification access** to allow the Android listener; use the configured receiving account in the bKash app. Only complete supported incoming receipts from the official bKash customer app can supplement SMS. Hidden/abbreviated/masked notifications stay pending. The report contains counts/reasons, not private receipt text or transaction identifiers. Evidence and limits: `verification/receipt-verification-v1.0.8/REVIEW.md`. No new production deployment or payment service is required for this client fix.

## Online repeating match schedules — 1.0.11

Create match → Scheduling saves the filled match/category/prizes with a daily time range, delay and inclusive date range. A foreground service in the signed-in, connected admin app waits for every positive prize-position winner to be credited, then creates the next fresh match within the allowed window. It pauses offline and resumes on reconnect, including while minimized. Android force-stop/shutdown requires reopening the app. No external scheduler or billing is needed. Matches → Schedules shows local service status, progress, pause/resume/stop and generated history.

Deposit verification also reconnects and rescans pending claims automatically, retries watcher errors, and preserves exact receipt matching and once-only credits. Keep owner-enabled auto-approval on the receiving phone with the required receipt permissions.

The owner-authorized production rollout is complete as of 7 November 2026. Scheduling and status-independent winner permissions are deployed and verified against the tested rules; indexes and live client checks pass. Install releases/antihack-admin-1.0.11.apk. New ordinary matches start Open; scheduled matches open atomically when due. Existing edited/blocked scheduled drafts are recovered. Match editing preserves unchanged timestamp precision and reports validation/conflict messages instead of a generic Save failed error. Real balances, operating settings, credentials and billing were preserved. See MATCH-SCHEDULING.md and verification/production-live-2026-11-07/REVIEW.md. The historical external server package is superseded.
