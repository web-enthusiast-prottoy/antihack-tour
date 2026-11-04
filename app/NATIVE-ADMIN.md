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
