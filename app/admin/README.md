# Retired web prototype / Firebase development tools

The owner selected native Android administration. The React prototype in this directory is retired and is not opened by the app. No Vite server or website is required. This directory still supplies the emulator, seed and Security Rules test dependencies.

Current setup: see [Native admin guide](../NATIVE-ADMIN.md).

The following notes describe the superseded web prototype only.

# Local announcement publisher

This page publishes to the local Firebase emulators only. It is not hosted publicly and is not connected to a real Firebase project. Players cannot publish or grant themselves staff access.

## Start on this Mac

From the `app` folder:

1. Run `./scripts/admin.sh run emulators` in one terminal and keep it open.
2. In a second terminal, run `./scripts/admin.sh run seed` once to create the disposable test publisher.
3. Run `./scripts/admin.sh run dev -- --port 5173` in another terminal.
4. Open http://127.0.0.1:5173 and sign in with `admin@arena.test` / `ArenaLocal123!`. These credentials are intentionally public test credentials and work only against the local emulator.
5. Enter a title and message, then choose **Publish announcement**. Use **Remove from feed** to hide it; **Publish again** restores it.

Use `releases/arena-local-notifications-v0.3.apk` on the standard Android emulator on this Mac to receive notices. It connects to the host’s Firestore emulator using Android’s special `10.0.2.2` address. The normal `arena-demo-v0.3.apk` is the standalone phone preview with sample notices. A physical phone does not use `10.0.2.2` to reach this Mac.

The publisher needs a confirmed server connection to publish. A failed action keeps the text in the form; retry after the connection is restored. Only the latest 50 announcements are loaded in the admin page. Android loads the latest 20 published notices, keeps unread state on that installation, and listens while Home or Notifications is in the foreground. Removing a notice hides it from the online player feed; cached offline information is explicitly labeled as cached.

Stopping the emulators discards their test data. Run the seed command again on the next fresh start. No real accounts or money are involved.

## Tools and checks

The convenience script uses the downloaded Node 24 and Java 21 in `.tools` when available. On another machine, install Node >=24.12 and Java >=21 for the Firebase tools, then run `npm ci` in `admin`. Android builds continue to use Java 17 for the existing Gradle toolchain.

- `./scripts/admin.sh run build`: TypeScript check and static bundle.
- `./scripts/admin.sh run test:rules`: eleven emulator authorization/validation tests. Stop the running Firestore emulator first, because this command starts a disposable one on the same port.
- Android integration test: run `connectedDebugAndroidTest` with `-PnoticeBackend=emulator` while the Auth/Firestore emulators are running, seeded with the welcome message and the documented test message “Tonight’s practice update”. It checks delivery, full-message reading, and unread state. The title/body are fixtures for that test, not hardcoded production content.

No billing, production project, public hosting, or operating-system push messages have been enabled. After the owner creates Firebase, a separate connection/configuration step must replace the emulator-only configuration, provision real staff accounts, deploy rules/indexes, and verify the hosted environment before distributing a live app.

## Primary references

- Firebase Android setup: https://firebase.google.com/docs/android/setup
- Firestore emulator connection: https://firebase.google.com/docs/emulator-suite/connect_firestore
- Firestore realtime listeners: https://firebase.google.com/docs/firestore/query-data/listen
- Security Rules testing: https://firebase.google.com/docs/rules/unit-tests

## Admin console 0.4 (local emulator)

The page now includes dashboard match counts, editable matches, payment/withdrawal review lists, players, results list, announcements, text configuration, owner-managed staff access, and append-only activity records. Bangla uses bundled, OFL-licensed Hind Siliguri, including when offline. Match images select the three bundled presets; file uploads are not implemented.

Run the same emulator and seed commands above. The seed account is now the owner. In **স্টাফ**, enter an existing Firebase Authentication UID to grant access, or edit an existing role to revoke it. Create test accounts using the Auth emulator REST API or Firebase SDK first. Passwords stay in Firebase Auth; the dashboard never stores them in Firestore. Only the provisioned owner can change staff access, and cannot change the owner's own role. Role revocation updates the console and database permissions.

For Android, run `./scripts/admin.sh run dev -- --port 5173 --strictPort`, build with `-PnoticeBackend=emulator`, then open Profile → অ্যাডমিন প্যানেল. The embedded console connects through `10.0.2.2`. The Vite development server binds to local network interfaces for this emulator connection; stop it after development. This entry is absent from the standalone sample build. No public hosting is configured.

Working: match and content CRUD (without deletion), notices publishing/hiding, owner-managed roles, read-only queues and profiles. Limits: lists load at most 100 records (50 announcements), no pagination; player-facing matches still use sample data; configuration is stored but not consumed by player screens; results publication, private rooms, ledger accounting, deposit approval, withdrawal payouts, refunds, disputes, leaderboards, and settlement remain unfinished. Match status changes are metadata only and never move money. Audit entries accompany dashboard writes but rules do not yet require every write to include an audit entry. Do not use this build for real balances.
