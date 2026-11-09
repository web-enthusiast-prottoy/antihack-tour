# Update detection repair — 1.0.10

Repairs update detection when the GitHub release-download gateway fails, using the public API, a separate fallback and shorter failure retries. If 1.0.8 never shows an alert, install 1.0.10 once over the existing app to repair its checker. [Latest player release](https://github.com/web-enthusiast-prottoy/antihack-tour/releases/latest).

# Player match history — 1.0.9

Matches leave Explore more than five minutes after their scheduled start. Matches → Results → My results includes the signed-in player's past confirmed participation, with Results pending until scores are published. Published history is retained through private ownership links. [Latest player release](https://github.com/web-enthusiast-prottoy/antihack-tour/releases/latest).

# GitHub player updates — 1.0.8

The player app checks automatically for newer public GitHub releases when opened/resumed and hourly while open. Users choose Download update, then open the APK and approve Android installation. Install 1.0.8 once to enable future notices. [Latest player release](https://github.com/web-enthusiast-prottoy/antihack-tour/releases/latest). [Update and publishing instructions](APP-UPDATES.md).

# Production update — 8 November 2026

Live signed APKs: [Antihack Tour 1.0.7](releases/antihack-tour-1.0.7.apk) and [Antihack Admin 1.0.12](releases/antihack-admin-1.0.12.apk). Updated scheduling and combined-balance entry rules are deployed and verified. See [production release notes](PRODUCTION-RELEASE.md).

The vivo registration block is not claimed cleared by an APK rebuild. Publisher verification/review is still required: [installation and registration](INSTALLATION-VERIFICATION.md).

# Antihack Tour

The separate player and native admin apps use the live Firebase backend. Current signed updates: [Main 1.0.6](releases/antihack-tour-1.0.6.apk) and [Admin 1.0.11](releases/antihack-admin-1.0.11.apk). Install over existing production apps. Scheduling and status-independent winner permissions were deployed and verified on 7 November 2026. New ordinary/scheduled matches default to Open; scheduled creation publishes in one transaction. The main update includes state retention and smooth startup; the admin update includes Set winner, branded bKash/notification receipt matching, automatic reconnect recovery and online-admin repeating schedules.

See the [production release notes](PRODUCTION-RELEASE.md), [verified rollout](verification/production-live-2026-11-07/REVIEW.md) and [scheduling setup](MATCH-SCHEDULING.md). Scheduling requires an online signed-in admin app; automatic receipt approval requires owner-enabled verification on the receiving phone. No external scheduler or billing upgrade is needed. Local builds remain separate sample environments; use `./scripts/build.sh local` and the [staff setup guide](NATIVE-ADMIN.md).

Local test services: `./scripts/admin.sh run emulators`, then `./scripts/admin.sh run seed`. The seed creates disposable, clearly labelled sample categories, matches, results and wallet history. Staff can sign in with the documented local test account below. Start `./scripts/local-operations.sh` for fictitious payment/paid-entry workflows. Never send real money while using the local build.

Current behavior: Home shows announcements and category image tiles; Explore shows matches from all categories with whole-card navigation. Production withdrawal limits/fees and receiving bKash instructions are managed in Admin. Production deposits and withdrawals were confirmed enabled during this rollout; existing operating settings were preserved. Prefilled preview matches are preserved as admin drafts until their real schedules and prizes are reviewed and published. Home, Explore, published results and participant names are public. Wallet and personal records require login. Joining requests login and any missing game details at that point. Profile contains email/game details, Contact, language and sign out; administration uses its separate app. Categories are maintained by staff and assigned to matches; category and match artwork can be selected from the device. Large category image cards and compact highlighted match countdowns use the shared design. Bangla text is optically reduced to match English more closely while preserving system font scaling. Confirmed participants see released room credentials at the top of match details, with separate Copy actions. The owner’s supplied logo is used in branded headers and the launcher icon. Currency is stored as integer paisa.

Owner-requested demo data: three labelled, free/zero-prize DEMO matches with newly generated banners are now public in Explore. Their example schedules are 5 November 2026 at 6 PM, 8 PM and 10 PM Bangladesh time. [Demo verification](verification/demo-matches/REVIEW.md) and [artwork/prompts](assets/demo-matches-2026-11-05/README.md).

The Android application and Firebase identifiers retain their historical names to preserve existing connections and installed-app updates. Historical milestone notes and evidence follow.

---

# Live manual bKash wallet

The live Arena APK now submits player deposits directly to Firebase and supports manual staff approval/rejection. Install `releases/arena-live.apk` and sign in with a live account. Administration → Deposits opens pending claims; approval credits the live wallet atomically with its receipt claim, immutable ledger entry and staff audit. Receiving number: 01889898989 by default, editable under Payment settings.

Rules have been deployed to arena-ff-34506. Verification: verification/live-wallet/REVIEW.md. Operating instructions and scope: LIVE-WALLET.md. Local APKs remain a separate emulator test environment. Earlier milestone notes below are historical.

# Arena · 0.11 minimal native design

Refined the existing player/admin screens using the project UI UX Pro Max skill: compact square-art match rows, shorter match details, consistent back/refresh controls, clearer Matches navigation, profile editing on demand, an Add Money sheet, structured request activity and a simpler result-search layout. Administration uses a section picker and taka inputs with exact two-decimal validation. The dark/sage direction and original artwork are retained.

The unfinished account-recovery/password-management work was removed at the owner's request. Registration, sign-in and sign-out remain.

Build with `./scripts/build.sh local` or `./scripts/build.sh live`. Both output their matching APK under `releases`. The local APK requires the local Firebase services in [LOCAL-PLAYER.md](LOCAL-PLAYER.md). Production deployment and real money remain unavailable. Design evidence and limits: [minimal-v0.11/REVIEW.md](verification/minimal-v0.11/REVIEW.md).

Earlier milestone notes follow.

# Arena · 0.10 native rooms and results

Free-entry matches now support restricted room release and reviewed result publication inside the Android app. Players have separate My Results and All Published views, exact match-ID/title-prefix search, pagination and labelled offline public-result browsing. Room credentials require current server authorization and disappear when offline/backgrounded. Announced awards are explicitly separate from wallet credits.

Use `./scripts/build.sh local` for local testing or `./scripts/build.sh live` for the Firebase-connected preview. Current operator instructions: [TOURNAMENT-WORKFLOW.md](TOURNAMENT-WORKFLOW.md). Verification: [workflow-v0.10/REVIEW.md](verification/workflow-v0.10/REVIEW.md). Production rules/index deployment and live acceptance remain pending; no real finance is enabled.

Earlier milestone notes follow.

# Arena · 0.9 stability and free registration

Build a local test APK with `./scripts/build.sh local` (the default), or a Firebase-connected preview with `./scripts/build.sh live`. Each command runs compilation, unit tests and lint before updating only its matching APK. Local installs as **Arena Local** (`bd.arena.prototype.local`), separately from **Arena** (`bd.arena.prototype`). Accounts and cached data are isolated between installs.

Free registration now uses a Firestore client transaction and matching Security Rules: a confirmed entry and one seat are committed together, without a wallet write or the local operations server. Latest rules must be deployed before this works on the live project; production deployment has not been verified or performed in this iteration. Room release, results and settlement remain unfinished.

Live previews have no transaction-service address, disallow cleartext network traffic, and explain that paid entries/deposits are unavailable. Local test deposits and staff approvals still require the emulator-only operations service. No real payments or withdrawals are enabled.

See [LOCAL-PLAYER.md](LOCAL-PLAYER.md), [PROGRESS.md](PROGRESS.md), and [verification/stability-v0.9/REVIEW.md](verification/stability-v0.9/REVIEW.md) for setup and verification limits.

The notes below describe earlier milestones and historical builds.

# Arena · 0.7 local player build

Current build: [releases/arena-local.apk](releases/arena-local.apk). See [LOCAL-PLAYER.md](LOCAL-PLAYER.md) for required Firebase emulators, transaction service, owner setup, and verification limits. Authentication, persistent profiles, shared published matches, join requirements, pending deposits and atomic staff approvals are now connected locally. Real payments and withdrawals are unavailable.

The notes below describe earlier milestones and their historical APKs.

# Arena Android prototype · 0.3

First implementation milestone from [the plan](../APP-IMPLEMENTATION-PLAN.md). Arena is a temporary name. The original app's code, branding and backend are not used.

## Try the app

Open `releases/arena-demo.apk` on an Android 8.0 or newer phone and allow installation from your file manager if prompted. This is a debug build for private testing.

- Home: illustrated Solo/Duo/Squad shortcuts and compact upcoming matches.
- Explore: filter Solo, Duo and Squad; tap a match row for a dedicated details screen and demo rules.
- Join an available free demo match; see it in My Matches.
- Full and closed matches cannot be joined; repeat joins are blocked.
- Wallet: zero sample balances; money controls disabled.
- Profile: guest information and English/Bangla preview labels.

Everything is sample data. Joining does not reserve an actual tournament seat. Registrations survive screen rotation but are not a durable account. The Bangla toggle is a partial preview, not complete localization. Player authentication, released rooms, real results, deposit approval and withdrawals are not connected yet. Announcement publishing is connected only in the separate local emulator build described below.

## Build

Install Java 17 and Android SDK Platform 34 / Build Tools 34.0.0. Android Studio can manage these. Open `android` as the project. Set the local SDK path in `android/local.properties` (`sdk.dir=...`); this file is machine-specific and ignored.

From the app folder, run `./scripts/build.sh`. It builds the debug APK, runs registration unit tests and Android lint, then places the APK in `releases`. Generated build files use temporary storage to avoid AppleDouble metadata errors on this external drive. Set `REA_BUILD_ROOT` if a different native filesystem location is needed. Build tools and dependencies require internet on the first run. No Firebase account or billing is required.

## Announcements

The homepage now includes a latest-announcement panel, notification bell, full messages and persistent read state. The standard phone APK uses sample announcements. A second APK, `releases/arena-local-notifications-v0.3.apk`, connects to the local Firebase emulators. The working local admin publisher and instructions are in [admin/README.md](admin/README.md). No Firebase project or live phone publishing is connected yet.

## UI design

UI UX Pro Max is installed in `.agents/skills/ui-ux-pro-max` and available on the next turn. The rules were read and applied during this update. Source checkout is in `ui-ux-pro-max-skill`. See `design/DESIGN.md` for decisions, original image locations and the exact generation prompt. The mode images are true squares and a separate wide hero image introduces the homepage. Optimized artwork is bundled; no remote image requests are needed.

## Coding rules

Requested source repository: https://github.com/multica-ai/andrej-karpathy-skills.git
Pinned checkout: `2c606141936f1eeef17fa3043a72095b4765b9c2` in `andrej-karpathy-skills`.
Rules copied into `AGENTS.md`, `CLAUDE.md` and `.agents/skills/karpathy-guidelines/SKILL.md` for project use. The local skill is available for discovery on the next turn; its instructions were read and applied during this implementation.

## Next acceptance checks

Install on the owner's phone, inspect all five screens, increase system font size, switch preview language, scroll and rotate, filter matches, join twice, and inspect full/closed match details. Verify clear sample labels and disabled finance. Emulator captures are saved in `verification/screenshots`; physical phone checks remain required before accepting the visual milestone. The next development milestone is Authentication and a complete free tournament journey using Firebase emulators.

Native admin setup and current limitations: [NATIVE-ADMIN.md](NATIVE-ADMIN.md). The Admin bottom tab appears only for authenticated, server-verified staff in the Firebase emulator build. The web prototype is retired.
