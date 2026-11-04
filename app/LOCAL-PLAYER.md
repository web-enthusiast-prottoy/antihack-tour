# Arena local player workflow · 0.11

Build: `JAVA_HOME="$PWD/.tools/amazon-corretto-21.jdk/Contents/Home" ./scripts/build.sh local` from app (or use your installed Java 17+). Install `releases/arena-local.apk`. This build connects to the Android emulator host at 10.0.2.2; it needs the local services below. It is not a standalone phone or production build. It now installs as Arena Local, separately from the live preview.

1. `./scripts/admin.sh run emulators` starts Firebase Auth and Firestore.
2. `./scripts/admin.sh run seed` provisions the local owner. Credentials are in admin/README.md and the seed script; these belong only to the emulator.
3. `./scripts/local-operations.sh` starts the trusted transaction service on port 8787.
4. Sign in as owner, open Profile → Administration, create a match with status `open`. Player feeds show published records, not fixed samples. Create a separate ordinary player using the launch sign-up screen.
5. Fill the player's Free Fire username in Profile. UID is optional. Try a free entry (direct Firestore transaction, no operations service needed), or submit a fictitious deposit from Wallet. Owner reviews Deposits → Approve after review. Paid entries appear under Entries, awaiting a separate approval.

The operations service refuses production project configuration and always uses demo-arena emulators. Never enter real transfer details. No real payment or withdrawal is enabled. Only staff can approve. Approval writes a deterministic ledger record and guards transfer reference uniqueness, wallet availability and final seat capacity in transactions. Submitting a deposit never credits funds. Paid requests do not reserve seats or funds; confirmation can fail if either has changed. Retry uses the existing request rather than charging again.

Profiles, entries, balances and language survive app restarts. To retain server data across emulator shutdowns, use Firebase's `--import ../firebase/local-state --export-on-exit ../firebase/local-state` when starting the emulators, and stop them gracefully. This directory contains private test data; do not publish it. The normal emulator command starts with transient server state.

Verification: Android debug build, unit tests and lint; 17 Firestore rules tests; transaction integration tests covering duplicate approval/charge, duplicate transfer references, unauthorized approval, free and paid last-seat concurrency, missing username/funds and revoked staff access; instrumented registration/mismatch/sign-out/sign-in/session-recreation, authenticated deposit submission and persistent language switching test. Physical phone, small-screen/large-font/reduced-motion acceptance and comprehensive offline scenarios still need manual checks. Historical admin/announcement instrumented tests refer to the previous navigation and need migration before running the complete device suite.

## Build isolation and free-registration checks (0.9)

`./scripts/build.sh` defaults to local. `./scripts/build.sh live` updates only `releases/arena-live.apk`; it requires matching Firebase Android configuration, contains no local transaction endpoint, and keeps paid entries/deposits unavailable. Install IDs are separate so local sessions cannot carry into live preview. The previous 0.8 build script copied a live APK to both filenames; rebuild/reinstall rather than relying on an older filename.

Free entry now commits the entry and seat together through the Android Firestore SDK, enforced by rules. Update/restart local Firestore with the current rules before testing. Free joins do not need the operations service. Paid test entry review remains local-service-only. No production rules deployment or production join verification is claimed.

For the new device acceptance test, start local Auth and Firestore, run `./scripts/admin.sh exec -- node ../firebase/tests/device-fixture.mjs`, then run `FreeRegistrationUiTest` with `noticeBackend=emulator`. The fixture explicitly loads local rules and resets only the `device-free-registration` match (fictitious test data). Run it only when no earlier acceptance run is in progress. The test creates a disposable player, joins through the real UI, checks immutable entry/count, verifies offline failure without false success, and retries online without consuming another seat.

The rules suite accepts `ARENA_RULES_PORT` for testing in an isolated emulator without clearing a running development database.

## Rooms and results (0.10)

Current free-match operations, admin navigation, room access, result drafts/publication and player result browsing are documented in TOURNAMENT-WORKFLOW.md. They use Firestore directly, need the current rules/indexes, and do not need the local financial operations service. Publication never credits a wallet. Production deployment remains pending.

`TournamentWorkflowUiTest` requires local Auth/Firestore with current rules and the seeded local owner. It creates its own fictitious match/player, exercises native room/result forms and player screens, verifies offline/background room hiding and zero wallet credit, and checks Bangla/large-text/landscape/reduced-motion behavior. The test restores system text/motion/orientation settings in its configuration-check cleanup.

## Minimal design update (0.11)

Profile has a summary and Edit profile action; incomplete setup opens its fields directly. Wallet → Add money opens the fictitious-request form in a sheet. My Matches opens player entries and Results; result search mode is chosen from the menu below its search field. Admin navigation uses a section picker. Recovery/password-management expansion is excluded at the owner's request. Functional backend limits remain unchanged.
