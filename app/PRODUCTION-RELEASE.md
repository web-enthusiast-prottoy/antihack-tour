# Update detection repair — 9 November 2026

Player **1.0.10 / code 22** adds GitHub API release discovery, preserves the download-manifest fallback, and retries failed checks after one minute with bounded backoff. The publisher includes update metadata in the release API response. Existing 1.0.8 installations affected by a failing download endpoint need one manual upgrade, keeping their current app installed. This update retains 1.0.9 match/history behavior and requires no production backend or financial changes.

# Match visibility and participant history — 9 November 2026

Player **1.0.9 / code 21** hides matches in Explore more than five minutes after the scheduled start. Matches → Results → My results shows the signed-in player's past confirmed participation, including Results pending until a score is published. Published history remains readable through existing private ownership links.

This release uses the existing live Firebase project, production package and signing identity. It requires no backend deployment. Install over the existing app; the GitHub update record makes it discoverable to 1.0.8 users. [Release](https://github.com/web-enthusiast-prottoy/antihack-tour/releases/tag/player-v1.0.9). Verification is recorded in `verification/player-1.0.9/REVIEW.md`.

# Daily schedules and combined entry balances — 8 November 2026

Production updates: [Player 1.0.7 / code 19](releases/antihack-tour-1.0.7.apk) and [Admin 1.0.12 / code 24](releases/antihack-admin-1.0.12.apk). Both are signed non-debuggable live APKs retaining the production signing identity. Install over the existing production apps; distribute only the player APK to players and update all staff phones.

The matching Firestore rules are deployed and verified byte-for-byte. Scheduling creates one day's fixed-interval matches together, with each later day's batch created on that day by the online admin app. Paid entry uses deposits first, then available winnings. Withdrawal reserves cannot fund entry, withdrawals remain winnings-only, and cancellation refunds restore their original funding sources. Existing wallet balances, deposit/withdrawal settings and histories were preserved.

All 157 local database/security checks passed. Live debug/release unit suites and lint passed for both apps. Signed player update/startup and private production access/scheduling checks passed; all disposable verification accounts/records were removed. No real financial transaction, public verification match, new host, billing upgrade or permanent credential was introduced. Evidence and rollback: [release review](verification/daily-batches-production-2026-11-08/REVIEW.md).

The reported vivo Super Care **App registration verification failed** block remains an external publisher/app-registration review issue. This release does not claim it is cleared. The owner has no publisher account yet; see [registration/review steps](INSTALLATION-VERIFICATION.md) and the prepared [vivo technical review package](releases/antihack-tour-vivo-review-1.0.7.zip).

The following sections describe earlier releases.

# Open-by-default match publication fix — 7 November 2026

Signed **Admin 1.0.11/code23**: [antihack-admin-1.0.11.apk](releases/antihack-admin-1.0.11.apk). Main 1.0.6 is unchanged. Install the admin update over the existing app.

Ordinary creation defaults to Open. Scheduled matches and their prizes/history/run/progress are now published Open in one transaction, removing the intermediate draft and revision race. Existing legacy scheduled drafts also recover after an admin edit or earlier blocked state. Unchanged match times retain their exact stored seconds/nanoseconds; save errors preserve actionable validation/conflict messages. Due windows, winner-credit delay, offline pause/resume and duplicate protection are retained.

Compatible production permissions are deployed and match the tested source byte-for-byte. All 145 database checks, both debug builds, registration/unit suites and lint passed. Six targeted device tests passed; three scheduler checks were rerun after the final legacy-recovery change. The signed release, 56 release unit tests and lint passed. Live private compatibility checks passed and disposable records were removed. No real match, wallet or operating setting was changed by verification. Rollback and evidence: [publication fix review](verification/match-publication-fix-v1.0.11/REVIEW.md).

The following sections record earlier releases.

# Online scheduling and winner update — 7 November 2026

Owner-authorized live backend deployment is complete. Signed updates: **main 1.0.6/code18** at [antihack-tour-1.0.6.apk](releases/antihack-tour-1.0.6.apk) and **admin 1.0.10/code22** at [antihack-admin-online-scheduling-1.0.10.apk](releases/antihack-admin-online-scheduling-1.0.10.apk). Both retain the production certificate and are non-debuggable. Install over existing apps.

The main app retains drafts/sheets across app switching and silently checks account access while opening Home. Admin supports Set winner and automatic once-only winnings credit for every match status; the published rules now permit that tested flow. Repeating schedules run in the connected signed-in admin app, pause offline and resume on reconnect, waiting for all positive prize-position winner credits plus the configured delay. Branded bKash SMS and complete supported notification receipts are accepted; pending-deposit verification reconnects and retries automatically on the receiving owner phone.

The deployed rules match the tested source byte-for-byte; all ten required indexes are READY. Live account/query/security checks and private future-dated scheduling checks passed, with all disposable accounts/records removed. No public test match, real financial credit, receiving-number/policy change, new permanent credential, host or billing change was introduced. Debug/release builds, registration/unit suites, lint and signed main-app launch checks passed. Earlier local verification covers minimized/offline recovery, winner payouts, duplicate prevention and payment/wallet invariants. Real bKash delivery and external transfers still require receiving-phone/operator confirmation; this release does not execute external bank payouts.

Keep auto-approval enabled in Payment settings on the receiving owner phone with the required receipt access. Reopen the admin app after Android force-stop/shutdown. Rollback rules and verification: [production rollout](verification/production-live-2026-11-07/REVIEW.md). [Scheduling instructions](MATCH-SCHEDULING.md).

The following sections record earlier releases.

# Moderation update and login repair — 6 November 2026

Signed moderation APKs: **main 1.0.5/code17** (`releases/antihack-tour.apk`) and **admin 1.0.7/code19** (`releases/antihack-admin.apk`). Their hashes match the tested candidates; they retain the production certificate and are non-debuggable. Older `*-live.apk` aliases remain main 1.0.4/code16 and admin 1.0.6/code18.

The prepared update adds participant View player / match-ban / account-ban / lift-ban controls with player-visible reasons. App-wide bans show the reason, Retry and Sign out; match bans show a private reason on the affected match and disable joining and room access there. Reasons stay private to the affected account and staff. Public guest browsing remains public. Account bans do not identify or block another account on the same phone, and do not remove someone already in the game.

Debug/release builds, registration/unit suites and lint passed. Local database/service checks: 127 passed. Two presentation tests passed in four phone/tablet portrait/landscape configurations (8 executions), with large English/Bangla text and reduced motion. Native system-dialog checks and clean screenshot review passed after recovery of an emulator-only System UI error. Old rules and APKs are backed up; all ten existing indexes are ready.

**Live rules are now deployed and verified.** The owner reported that every login showed “Connect to verify account access” and requested urgent repair. The old rules denied the new ban-status check; no live ban records existed. The matching tested rules were deployed through the normal Firebase CLI, read back byte-for-byte, and verified with a disposable account for normal access, ban reason visibility, blocked private access and restored access after lifting. All temporary records were removed; no real player or balance changed. Already-installed users can tap Retry or reopen the app; no reinstall is needed. [Incident evidence](verification/access-verification-fix-2026-11-06/REVIEW.md), [moderation acceptance](verification/moderation-live-2026-11-06/REVIEW.md).

The following sections record earlier releases.

# Timed room release update — 5 November 2026

Current signed APKs: **Admin 1.0.6/code 18** (`releases/antihack-admin-live.apk`) and **main 1.0.4/code 16** (`releases/antihack-tour-live.apk`). Install both updates over existing apps.

Admin room release now uses calendar and clock dialogs in Bangladesh time. Confirmed participants automatically receive enabled room credentials after release while matches remain open, closed or live. The player card checks the server every ten seconds while visible and reopens after background/resume. Hide stops checks. Existing disabled rooms must be explicitly enabled.

The tested permission update is deployed and verified. All 117 database/transaction tests, device timed-release/resume/Hide/Show checks, debug/release unit/registration suites and lint passed. Previous rules and APKs are backed up. Evidence: [timed room release review](verification/timed-room-release-2026-11-05/REVIEW.md).

# Deposit verification update — 5 November 2026

Updated live APKs: **Admin 1.0.5/code 17** at `releases/antihack-admin-live.apk` and **main 1.0.3/code 15** at `releases/antihack-tour-live.apk`. Both retain the production signing certificate. Previous versions are archived.

Main submission now shows circular verification for up to 30 seconds, then server-confirmed success/rejection or pending admin review. Owner-enabled SMS matching runs in a visible foreground service on the receiving phone, with optional notification permission, battery-settings guidance, restart and reboot startup. Android restrictions, force-stop, unavailable network and unsupported receipts can pause automation; this is not guaranteed always-on provider verification. Real bKash receipts and physical-phone acceptance remain unverified. Backend credit rules are unchanged.

Debug/release builds, unit/registration tests and lint passed. Emulator tests passed for approval behind Home, player success/pending timeout and offline recovery with retry-safe sample credit. Evidence: [background deposit review](verification/background-deposit-2026-11-05/REVIEW.md).

# Admin update — 5 November 2026

Live admin **1.0.4/code 16** is available at `releases/antihack-admin-live.apk`, signed with the same certificate as admin 1.0.3. Install over the existing admin app. Main player 1.0.2 is unchanged.

Match creation now supports independent Total prize, per-kill and initial placement prizes saved atomically. Owner deletion is available for every match status; history remains stored and deletion does not issue refunds. Updated rules are deployed and verified byte-for-byte against tested source. All 116 local database/transaction checks, live client smoke checks, admin debug/release builds, unit tests and lint passed. No real balances changed. Evidence and rollback rules: [admin update review](verification/admin-prizes-live-2026-11-05/REVIEW.md).

# Current release — 5 November 2026

Production rules are deployed and verified for the current main **1.0.2/code 14** and admin **1.0.3/code 15** APKs: `releases/antihack-tour-live.apk` and `releases/antihack-admin-live.apk`. Both retain the production signing certificate; prior APKs are archived under `releases/archive/`.

Paid entry now charges and confirms on player confirmation. Prize positions remain editable after registration; existing credits are preserved. Profile Support opens WhatsApp at the owner-provided initial number; Admin Settings → Support → WhatsApp support can now update the shared number. Staff navigation has been removed. Profile email is tied to the authenticated email and fills when each player signs in using the updated app.

The deployment passed all 114 local security/transaction tests and live client smoke checks. All ten indexes are ready. Existing deposits/withdrawals are configured and were preserved. No real wallet balances were changed. The reported Oppo installation failure remains unconfirmed; this is not a universal-device acceptance claim. Full deployment evidence, rollback rules and remaining limits are in [the current release review](verification/production-update-2026-11-05/REVIEW.md).

The historical release notes below describe earlier behavior and are superseded where they conflict with this section.

# Antihack Tour production release · 1.0.0

Owner explicitly authorized the live release on 4 November 2026, superseding the earlier local-only milestone. The production backend is Firebase project `arena-ff-34506`. Distribute `releases/antihack-tour-live.apk`; the `.local` APK is a separate emulator build.

## Live operating settings

Withdrawals are enabled with a **৳50 minimum payout and ৳5 fee**, as requested. A minimum request reserves ৳55 from available winnings. Administration → Withdrawal settings can change the minimum, fee or enabled state. Existing requests retain the fee reviewed when submitted.

**Deposits await the real receiving number.** The owner confirmed the previous number was a demo. Its public instruction was removed and new deposit claims are disabled. Before accepting deposits: Profile → Administration → Payment settings, enter the actual receiving bKash number, turn on Enable deposit instructions, then Save number. Players never see a fallback demo number in the live form. Staff can edit or disable these instructions later. Previously submitted claims retain their own recipient details and can still be reviewed.

Admin deposits remain manual: check actual successful receipt before approving. Paid-entry requests reserve neither money nor a seat. Admin → Entries → Approve after review shows the account, match and exact fee; Confirm and charge entry atomically charges deposits, confirms the roster and claims one seat. Rejection requires a reason without a debit. Cancelling a paid match stops registration; its Entry refunds tab returns each original entry fee to deposit balance, with immutable history and repeat protection. If interrupted, repeat Refund remaining entries to finish the outstanding entries.

Prize positions remain editable in the match editor and match-specific Prizes tab until the first confirmed entry. Results selects the confirmed account and computes placement plus kill rewards. Publication claims a unique placement; credit and whole-match settlement preserve the tested separate winnings ledger. Approve a withdrawal to hold its funds; manually transfer through bKash, then record the actual reference and confirmation to mark Sent. An uncertain transfer remains Approved/reserved. Reject only after confirming no payout occurred. The app does not execute bank transfers.

## Deployment and data

- Tested Firestore rules and all ten declared composite indexes are deployed; every required index was verified READY. Artwork blobs are excluded from indexing.
- Live email/password signup, authenticated/guest queries and the enabled staff Auth account were verified. Ordinary-user wallet/role forgery is rejected by the actual deployed backend.
- Owner-approved withdrawal policy and the disabled/empty deposit instruction were installed with deployment audit records. An idempotent marker prevents rerunning setup from overwriting later admin changes.
- Four prefilled match counters (12, 8, 6 and 4) had no confirmed registrations. The known preview counters were reconciled to zero using current-document preconditions, preserving match details and recording history/audit. The four known preview matches are preserved as private admin drafts, so users cannot join placeholder tournaments. Review actual schedules, fees and prizes in Admin → Matches and publish each match when ready. No entry, real wallet, deposit or payout history was removed or altered.
- No emulator accounts, example balances or sample winnings were copied into production. No billing upgrade, Functions, additional hosting or automatic payment integration was introduced.

## APK and updates

The APK is an optimized, signed, non-debuggable release: package `bd.arena.prototype`, version 1.0.0 / code 12, Android 8.0+. Cleartext network traffic is disabled. Its Firebase project is the real production project; it has no local transaction-server endpoint. Both certificate fingerprints are registered with the existing Firebase Android app.

The first stable production signing key differs from the old debug preview key. An existing debug preview installation may require a one-time uninstall before this production APK installs. Firebase accounts and server records remain intact; sign in again after installation. Future production updates must use the same `.signing/antihack-release.jks` and a higher version code.

**Keep a private backup of the entire `.signing` folder**, including its password/configuration files. It is excluded from version control and is never bundled in the APK. Distribute only the APK. The public certificate fingerprint and exact APK hash are in `verification/production-release/`.

Build with `scripts/build.sh live`: compiles debug and release, runs both unit suites and lint, and copies the signed release APK. The current script writes a separate player candidate APK. See [standalone admin handoff](NATIVE-ADMIN.md) for the admin build and rollout order. Production deployment uses the explicitly selected project and Firebase rules/index configuration. Do not run the old `seed-live.mjs` preview seeder against production.

## Evidence

`verification/production-release/` retains deployment logs, pre-deployment rules, production policy/client verification, the exact release certificate/manifest/APK hash, build/lint/unit evidence and device results. The rules suite has 96 passing cases, including paid confirmations/refunds and disabled recipient handling. Both debug and release have 40 passing unit tests and zero lint errors/fatal findings. Device acceptance covers native payment configuration, paid confirmation/refund, reviewed winner credits and the withdrawal journey with Bangla double-text/landscape. The signed APK was installed and verified against the real backend with a disposable zero-balance account: live sign-in, wallet access, hidden demo deposit instructions, sign-out and Home loading passed. The temporary account is removed afterward. An empty-sign-in crash discovered during release checking was fixed by validating credentials before invoking Firebase, with a separate passing device regression. Phone automation now verifies field focus and submitted input rather than tapping fields hidden behind the keyboard.

The earlier local walkthrough and evidence remain historical records. Production API/Android verification does not simulate real receipt or external payout; operators must perform and verify those manually.
