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

Build with `scripts/build.sh live`: compiles debug and release, runs both unit suites and lint, and copies the signed release APK. This command builds; it does not deploy. Production deployment uses the explicitly selected project and Firebase rules/index configuration. Do not run the old `seed-live.mjs` preview seeder against production.

## Evidence

`verification/production-release/` retains deployment logs, pre-deployment rules, production policy/client verification, the exact release certificate/manifest/APK hash, build/lint/unit evidence and device results. The rules suite has 96 passing cases, including paid confirmations/refunds and disabled recipient handling. Both debug and release have 40 passing unit tests and zero lint errors/fatal findings. Device acceptance covers native payment configuration, paid confirmation/refund, reviewed winner credits and the withdrawal journey with Bangla double-text/landscape. The signed APK was installed and verified against the real backend with a disposable zero-balance account: live sign-in, wallet access, hidden demo deposit instructions, sign-out and Home loading passed. The temporary account is removed afterward. An empty-sign-in crash discovered during release checking was fixed by validating credentials before invoking Firebase, with a separate passing device regression. Phone automation now verifies field focus and submitted input rather than tapping fields hidden behind the keyboard.

The earlier local walkthrough and evidence remain historical records. Production API/Android verification does not simulate real receipt or external payout; operators must perform and verify those manually.
