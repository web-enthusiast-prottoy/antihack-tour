# Modern Tournament App — Implementation Plan

Updated: 3 November 2026

## Goal and current decisions

Build an independent Android esports tournament app inspired by ExtremeTour, with a modern interface, smooth performance, simple administration, and the lowest practical operating cost. The owner is new to app development and hosting: favor managed services, clear instructions, and few moving parts.

This document supersedes the earlier custom NestJS/PostgreSQL hosting proposal for the initial release. Preserve the complete feature roadmap, but start with Firebase and manual operations. REA is an analysis tool, not the runtime for the new app. Do not reuse the original app's backend, private data, credentials, branding, or artwork.

Confirmed payment requirement: users send money to a designated bKash number, then submit the sending number, transaction ID, and amount. Admin verifies receipt and approves the wallet credit. Payment approval automation is optional future work.

Working assumptions: Android first; Free Fire full-map and Clash Squad; solo/duo/squad; Bangladesh audience; BDT currency; Bangla and English. Confirm business rules before implementing money flows. PUBG references in the original APK do not establish that it is an active supported game.

## Honest cost and reliability expectations

- Aim for zero hosting charges during development and a small pilot using Firebase Spark, without attaching billing.
- Free quotas are limited. A busy app can exceed them and lose service until quotas reset. Free is not unlimited or guaranteed uninterrupted operation.
- No developer can promise zero bugs. Use focused tests, release-device measurements, small beta releases, diagnostics, and recovery procedures.
- No billing upgrade, paid infrastructure, domain purchase, store publication, or external payment commitment without the owner's explicit authorization.
- Real money processing has operational responsibilities even when approval is manual. Confirm the receiving account's suitability, tournament permissions, applicable requirements, and distribution eligibility before a public paid launch.
- Google Play acceptance of paid-entry cash-prize tournaments must not be assumed. Do not disguise features to avoid review. Free development APK testing can proceed independently of publication.

## Minimal architecture

| Area | Initial choice | Reason |
| --- | --- | --- |
| Android | Kotlin + Jetpack Compose | Native interface and direct Android performance tooling |
| Authentication | Firebase Authentication, email/password | Avoid custom password handling and SMS costs |
| Password reset | Firebase reset email link | Avoid building custom OTP infrastructure |
| Database | Cloud Firestore | Managed database with offline caching |
| Administration | Native Kotlin/Compose screens in the Android app | Admin bottom tab visible only for server-verified staff accounts |
| Admin hosting | None | Native screens connect directly to Firebase client SDKs |
| Diagnostics | Firebase Crashlytics and Performance Monitoring | Detect crashes and slow operations |
| Notifications | In-app inbox; optional console-sent FCM announcements | Avoid a deployed notification server initially |
| Images | Bundled game artwork and preset avatars | Avoid upload infrastructure |
| Development | Local Firebase emulators | Test accounts, data, and rules without production traffic |

Do not introduce NestJS, PostgreSQL, Redis, Kubernetes, microservices, or another hosting provider for the first version. Do not add Room merely to duplicate Firestore's existing cache; introduce a separate local database only if a demonstrated requirement warrants it.

The app and admin dashboard use Firebase's client SDKs. Authorization must be enforced in Firestore Security Rules, not just hidden buttons. Never embed service-account credentials or payment secrets in either application. A trusted local provisioning tool may bootstrap staff roles; privileged credentials stay outside source control and client builds.

## Current Firebase limits that affect the design

Verified against official documentation on 2 November 2026; recheck before setup and release.

- Firestore free allowance: 1 GiB stored data, 50,000 document reads/day, 20,000 writes/day, 20,000 deletes/day, and 10 GiB outbound transfer/month. One database per project qualifies for the free quota.
- Deployed Cloud Functions require the Blaze billing plan, despite no-cost usage allowances on that plan.
- Cloud Storage for Firebase requires Blaze. Do not plan profile-photo or evidence-file uploads on Spark.
- Firebase Hosting is distinct from Firebase App Hosting. Use static Hosting; do not accidentally choose billing-dependent App Hosting.
- Email/password authentication has usage and email-sending limits. Avoid phone/SMS authentication for the zero-cost version.
- FCM delivery is available without a charge, but automatic event-driven sending still needs trusted sending infrastructure. In-app notices and manually sent announcements are enough initially.
- Billing alerts on a future paid plan are notifications, not a guaranteed spending cap.

Quota budgeting must be based on actual reads/writes per session, not guessed user counts. Paginate queries, avoid repeated listeners, and inspect usage during beta.

## Player interface

Five main destinations:

1. Home: next registered match, featured games, notices.
2. Matches (Explore): match listings and filters for mode, time, fee, and availability.
3. My Matches: requested, confirmed, ongoing, and completed entries.
4. Wallet: separate balances, deposit requests, withdrawal requests, ledger history.
5. Profile: display name, username, game IDs, preset avatar, statistics, referrals, settings, profile editing, secure password change, and support.

Use a coherent dark theme, one accent color, readable text, consistent spacing, and modest animations. Add light mode if it does not delay the core journey. Support accessibility, font scaling, Bangla text, and status labels that do not rely on color alone. Home may feature a staff-managed promotional banner and visual game/mode category tiles, but keep the first release limited to the confirmed Free Fire formats; reference-app categories are not evidence that we should support those modes.

Match cards show start time and countdown, entry fee, reward structure, mode/map, participant count, remaining capacity, and a clear Upcoming/Full/Closed state. A full match has a disabled join action; an open match has a join action. Keep the planned mode, time, fee, and availability filters. Details show the same core information, prize distribution (including per-kill rewards where configured), match rules, and the participant roster before confirmation. Show only public game names/handles and preset avatars in rosters; never expose email, phone, or other private profile fields. Every feature needs loading, empty, offline, pending, error, and retry states. Cached information must be distinguishable from server-confirmed status.

The player-facing match area includes separate My Matches and Results destinations. Results can switch between the player's own results and all published results, with search by match ID or title. A result row can show match title/mode/map/date, match ID, placement, kills, and credited prize. Prize breakdowns and match results must agree with the published settlement and wallet ledger.

## Manual bKash deposits — first implementation

### Player flow

1. Open Add Money and read the configured receiving number and instructions.
2. Send money through bKash outside the app.
3. Enter sending number, transaction ID, and BDT amount.
4. Submit once and receive a request reference with Pending status.
5. See Approved or Rejected status, with an explanation when rejected.

Transaction history must link the amount, timestamp, request/reference, and final state to the deposit request. A typed transaction ID or confirmation banner is not proof that a payment was received.

Do not collect the user's bKash PIN, OTP, password, or account login. A screenshot is unnecessary for the initial flow and is not proof of receipt.

### Admin flow

1. View pending requests in a queue.
2. Check the actual receiving account's transaction history through its official channel.
3. Match transaction ID, amount, sender details where available, recipient, and successful receipt.
4. Approve the verified amount or reject with a reason. If details differ, record the discrepancy and require correction rather than silently changing the claim.
5. Record who decided, when, and the reason.

### Accounting and duplicate protection

- Use integer paisa values; never floating-point currency calculations.
- Players can create their own pending deposit requests but cannot approve them, edit balances, create credits, assign staff roles, or modify completed records.
- Store a canonical transaction reference with a uniqueness record scoped to the payment provider/receiving account. Apply normalization according to the actual provider's identifier format.
- A pending duplicate must not permanently block the genuine payer. Admin can reject fraudulent claims; only verified receipt is credited.
- Approving a deposit atomically creates an immutable ledger credit, updates the balance summary, marks the request approved, and claims the verified transaction reference.
- An already approved request returns its existing outcome. Repeated taps, reconnects, two admins, or app restarts must never credit it twice.
- Corrections use explicit reversal/adjustment records, never deletion of financial history.
- Admin roles are trusted financial operators. Keep staff access narrow and auditable. Security Rules must block ordinary users regardless of modified app code.
- Keep the scope manageable: one receiving account and one currency initially.

Firestore transactions, restrictive rules, and trusted manual admin actions can support this small pilot without deployed Cloud Functions. Test atomic approval and authorization in the emulator before enabling real deposits. Do not claim this is an unlimited financial platform.

### Possible automation later

Automate validation, formatting, duplicate flags, queue sorting, and reconciliation assistance first. These do not establish that money arrived.

Automatic approval requires an authorized verification integration that can establish actual receipt. A typed transaction ID, user screenshot, or the app reading its own request is insufficient. Do not scrape banking apps or collect payment-account credentials. Reassess provider availability, fees, and trusted backend requirements before adding receipt-verification automation. Keep manual review for unresolved cases.

## Match registration, results, and withdrawals

Match lifecycle: draft → open → registration closed → live → results under review → settled; cancellation is a separate recorded outcome.

- Free joins: authenticated registration with duplicate protection and atomic capacity enforcement, tested through Security Rules and transactions.
- For the simplest initial paid pilot, a player submits an entry request; admin confirms it and atomically charges the wallet and reserves capacity. The UI must say Requested until confirmed. State this manual step clearly; do not imply an immediate guaranteed place.
- Later, instant paid joining can be added only with verified transaction/rule invariants or a trusted backend. Never permit unrestricted client balance writes for convenience.
- Validate roster size, match state, capacity, sufficient eligible funds, and duplicate entries.
- Only confirmed participants can read released room credentials. Store credentials separately from public match documents. Enforce release time and membership in rules, not only UI.
- Results are entered and checked by staff. Publication and settlement are distinct, traceable actions. Duplicate settlement must not pay twice. Player result history should show placement, kills, match ID, and prize alongside match metadata; the app may show the player's own results and all published results, but never reveal unpublished results.
- Owner-approved result entry (8 November 2026): keep placement winners and all-participant kill entry separate. Unset kills are zero. Review and credit kill rewards in batches, allow either reward order, preserve earlier combined payments, and never pay the same component twice. Team member counts pay the registering account. Public results show the component breakdown without an invented placement.
- Maintain deposit balance, winnings, bonus restrictions, and pending withdrawal reserves explicitly.
- Withdrawals initially use a manual queue: request → reserve eligible funds → admin review → manual payout → record verified payout reference → complete. The player form collects the configured payout method and destination account/number, shows the configured minimum and processing expectations, and requires confirmation of the destination before submission. Show the user's pending withdrawal requests and clear instructions; do not promise a fixed service time until the operator sets one. A rejected request releases its reserve once. An uncertain payout stays pending until reconciled.
- Wallet activity is a dated, paginated ledger with readable type, signed amount, related match/payment/withdrawal reference, and state (for example pending, completed, rejected, or reversed). Recent activity links to the full history. Include entry fees, prizes, deposits, withdrawals, refunds, bonuses, adjustments, and any later-approved transfers as distinct ledger types. A balance summary is derived/reconciled against ledger records; never treat a displayed total as the accounting source of truth.
- Player-to-player balance sharing is not part of the first release. If considered later, require explicit product approval and written limits/fee policy, a username lookup that reveals no private data, a confirmation showing recipient, amount, and fee, atomic debit/credit with immutable paired ledger records, repeat-safe transfer IDs, abuse controls, and dispute/reversal procedures. Do not copy a reference app's displayed fee or enable transfers by default.
- Match cancellation produces traceable refunds according to the agreed rules. Money-changing actions need atomic and repeat-safe behavior.
- Game rooms are created/operated by staff in the game initially. Do not assume a publisher API exists or that this app can detect all in-game cheating.

## Full feature roadmap

| Capability | First release / pilot | Later improvement |
| --- | --- | --- |
| Accounts | Email registration, login, logout, reset link, secure password change | Other sign-in methods if justified |
| Profile | Editable name/game IDs, username, preset avatar, statistics | Photo uploads when storage is available |
| Matches | Lists, details, rules, filters, participant roster, registrations | Additional supported games and formats |
| Teams | Roster entry for the selected formats | Persistent teams, invites, substitutions |
| Rooms | Restricted room ID/password | Additional reminders and release automation |
| Results | Player history and searchable all-published results | Imports and richer statistics |
| Wallet | Ledger, recent/full transaction history, and manual bKash review | Authorized receipt verification; consider player-to-player transfers only after policy, abuse, and accounting review |
| Withdrawals | Manual requests, destination details, pending status, and payout tracking | Approved provider payout integration |
| Leaderboard | Staff-published snapshots, ranked by an explicitly named measure such as total prize money | Scheduled daily/weekly/monthly computation |
| Referrals | Track code and referral relationship | Verified, abuse-resistant reward settlement |
| Notifications | In-app notices, manual push campaigns | Automatic match/payment notifications |
| Support | Instructions, contact links, text disputes, Rules, About/Developer Info, and Privacy Policy pages | Attachments, richer support tooling, and optional guided/AI help after content and operating review |
| Community | No public chat in the first release | Moderated community chat only after staffing, abuse reporting, retention, and moderation rules are defined |
| Content | Rules, promotional banners, notices, maintenance flag, and legal/about-page content | More flexible content management |
| Game services | No third-party top-up flow in the first release | Evaluate a Free Fire top-up provider only after authorization, provider terms, fees, refund handling, and payment/accounting integration are reviewed |
| Updates | Version notice and supported update route | Distribution-specific update improvements |
| Administration | Matches, finance queues, users, results, audit history | More staff roles, reports, automation |

Do not silently remove deferred features or present placeholders as completed functionality. Referral rewards must not be issued solely because a user entered a code.

## Initial database boundaries

Suggested collections: public games/matches/configuration; private users/game profiles; entry requests/confirmed entries; restricted room credentials; deposit requests; verified payment references; wallet summaries; immutable ledger records; withdrawals; results; notices; referrals; disputes; staff roles; audit records.

If a later release approves player-to-player transfers, add transfer requests/records with paired immutable ledger entries and uniqueness/idempotency keys. Keep any fee policy in trusted configuration and validate it server-side/rules-side; never let a player set the fee or credit another wallet directly.

Separate public fields from private ones because Firestore reads operate at document level. Users access only their own private records; participants access their own authorized match information; staff have explicit role-based access. Default-deny rules apply to all unrecognized paths. Validate allowed fields, types, ownership, and state transitions. Staff cannot grant themselves broader roles through the public dashboard.

## Performance plan

- Benchmark release builds on an agreed low/mid-range Android reference phone; emulator speed is not proof of real-device performance.
- Target usable cached home within 1.5 seconds cold launch, visible touch feedback near 100 ms, and consistently smooth 60 Hz scrolling. These are targets to measure, not promises.
- Use Firestore cache for browsing; confirm financial writes and registrations online. Never show an offline queued write as a completed payment or confirmed entry.
- Paginate lists and detach listeners when screens stop observing them. Subscribe only where freshness is necessary.
- Bundle small optimized artwork, resize remote images if introduced, and avoid video backgrounds and heavy effects.
- Keep network and parsing work off the main thread. Avoid blocking startup with nonessential initialization.
- Use release shrinking, Baseline Profiles, and Macrobenchmark tests for launch, match scrolling, and navigation.
- Track crashes, slow screens, failed operations, quota consumption, and staff queue delays.

## Implementation sequence and acceptance criteria

### 1. Confirm specification

Finalize branding, supported modes, match rules, deposit instructions, refund terms, withdrawal eligibility, and manual response expectations. Supply plain-language account/setup instructions. Inspect existing workspace before creating app files; keep the REA source and extracted evidence separate.

### 2. Build a local visual prototype

Create the Compose design system and main screens with clearly labeled sample data. Produce an installable debug APK and test on the owner's phone. No live money. Validate navigation, readable Bangla, layout, and scrolling before expanding.

### 3. Connect the free Firebase foundation

Use local emulators first; configure a Spark project owned by the user when account access is available. Add Authentication, Firestore rules/indexes, environment separation, and caching. Bundle avatars and artwork. Explain console steps and expected results. Do not enable billing automatically.

### 4. Complete one free tournament end to end

Admin creates match → player registers → admin confirms where applicable → room credentials become available → staff publishes result → player sees history. Verify full/closed matches, duplicate attempts, unauthorized reads, and network failures.

### 5. Add manual finance in test mode

Implement pending bKash submissions, verified transaction uniqueness, atomic approval/credit, ledger history, withdrawal reservations, rejection, refund, and audit records. Use fictitious transactions first. Test denied client writes and concurrent approvals. Keep test balances separate from live balances.

### 6. Add remaining pilot features

Introduce leaderboards, referrals, notices, contact support, maintenance and version notices. Implement the native admin area inside the Android app. Add diagnostics and manual push announcements if needed. Include match search and published-result browsing, participant lists using public game identity only, complete ledger history, and profile Help/About/Privacy pages in the player experience. Keep community chat, AI support, third-party top-ups, and wallet sharing deferred until their operating, privacy, moderation, provider, and accounting requirements are approved.

### 7. Beta and launch decision

Run a small closed beta, measure quota use and real-device performance, and verify financial reconciliation. A public real-money launch follows resolution of receiving-account, tournament, distribution, and operating requirements. Free quotas may support a small pilot, but must be measured before expanding.

No schedule is guaranteed yet. The earlier 12–18 week estimate covered a broader custom backend. Re-estimate after the prototype and finalized manual operations; provider/account decisions and testing affect elapsed time.

## Required verification before money is enabled

- A user cannot read another user's private transactions or change roles/balances.
- Submitting a deposit never credits money automatically.
- The same verified payment cannot credit twice, even under simultaneous approval.
- Two confirmations for the last place cannot oversell capacity.
- Repeated join, refund, withdrawal, and settlement requests cannot duplicate effects.
- Closing the app or losing connectivity after success gives the existing result on retry.
- A withdrawal prevents those funds being spent elsewhere; rejection releases them once.
- Room credentials are absent from public documents and unavailable to nonparticipants.
- Ledger totals reconcile with summaries and actual manually verified transfers.
- Match list counts, remaining slots, Full/Upcoming states, countdowns, match details, participant counts, prize breakdowns, and published results remain consistent as registrations and settlement change.
- Public participant/result displays expose no private account or contact fields and hide staff drafts/unpublished results.
- Every wallet activity type has a traceable history entry; a rejected or reversed action remains visible with its final state and never disappears from the audit trail.
- Quota exhaustion and backend errors produce clear recoverable states, not false success.

Use emulator authorization/integration tests and real-device release checks. Maintain a release checklist and an operator guide for reconciliation, disputes, staff access removal, and recovery. Spark does not provide the paid managed Firestore backup features; plan a trusted manual data-export procedure appropriate to the pilot before holding real balances, and test restoration rather than assuming data can be recovered.

## Owner inputs and handoff requirements

The owner supplies a Google account/project ownership, app name, approved artwork, receiving bKash details, operating rules, and a person to review deposits/results/withdrawals. Never request payment PINs or OTPs. Provide step-by-step setup and daily operation instructions without assuming server knowledge.

Maintain this document and a progress checklist as implementation proceeds. Record decisions, test results, limitations, and the next runnable milestone. Do not report code as working until the corresponding build and meaningful checks have run.

Current next milestone: finish lifecycle/roster validation around the verified core free-entry/room/result journey, then complete test-mode accounting, withdrawals, refunds and settlement. Historical prototype milestones below are retained for traceability.

## Reference-app video audit — 3 November 2026

Reviewed the full 2m14s `WhatsApp Video 2026-11-03 at 00.15.39.mp4`, sampling the complete clip at one frame per second and inspecting the visible screens. The app identifies itself as FinalZone BD. This is a feature/layout reference only; its branding, artwork, credentials, financial rules, and implementation are not to be copied. Some parts of the clip show the Android launcher while the app is reopening. This inventory records only features visibly presented; a screen or label does not prove the underlying operation works.

### Feature inventory and comparison

| Video area | Features visible in the reference app | Comparison and plan decision |
| --- | --- | --- |
| Navigation and home (about 12–25s, 42–49s, 119–125s) | Home, Matches, Wallet, Results, and Profile destinations; signed-in profile shortcut in the top bar; welcome greeting; rotating promotional banner; image tiles for game/match categories. Visible categories include BR Match, Clash Squad, Katana vs Katana, CS 2v2, Lone Wolf, Lose to Win, Lone Wolf 1v1, Proleague Match, BR Survival, and Free Match. | Five destinations and promotional content were already in the plan. Banner/category presentation is now explicit. Do not copy the reference app's branding/assets or add its unconfirmed modes; retain the approved Free Fire formats only. |
| Sign-in and registration (about 26–41s) | Login/register switch; first and last name, username, email, password and confirmation fields; password visibility control; Forgot Password link; sign-in/create-account actions. A demo credential card is displayed. | Account flows were already planned. Add an explicit profile password-change action. Never include demo credentials in the shipped app; password reset/change must use the account's secure authentication flow. |
| Profile and help (about 0–6s, 49–53s) | Profile banner/avatar, display name, username and email; Edit Info and Change Password actions; match/win/balance summary; Top Players leaderboard, Community Chat; AI Support, Support (Telegram contact), Rules; Developer Info, Privacy Policy; Log out. | Profile, stats, leaderboard, support, rules, and sign-out were already covered generally. The plan now calls out edit/password actions and About/Developer Info and Privacy Policy pages. AI help and public chat remain deferred pending privacy, moderation, and staffing review. Do not expose email or other private account fields in public player lists. |
| My Matches (about 54–56s) | Dedicated My Matches page, refresh action, and explanatory empty state directing players to join matches. | My Matches was already a main destination. Its empty/loading/retry states are now included in shared screen-state requirements; refresh must not imply that an offline or stale response is current. |
| Wallet overview and history (about 57–65s, 69s, 72s, 76s) | Total balance in BDT; Deposit, Withdraw, and Share Balance actions; shortcut labelled AI Topup / Free Fire Diamond Recharge; Recent Transactions with View All; sample Prize credits and Entry Fee debits, signed amounts, timestamps, match references, and Completed labels. | Wallet and ledger were planned, but the visible recent/full history, transaction labels, timestamps, related references, and statuses were underspecified. Those are now explicit. AI top-up is deferred for provider/terms/accounting review. |
| Deposit (about 66–68s) | Amount entry with a displayed minimum; Proceed to Payment; instructions describe a secure payment page, exact amount, transaction ID submission, and claim automatic processing. | Our confirmed requirement remains manual bKash submission and staff verification. Reuse only the need for a clear amount/minimum/instruction screen and a traceable status. The clip does not show a completed payment, receipt verification, or whether the automatic-processing claim is true; do not infer or implement auto-credit from it. Do not adopt its minimum or payment provider. |
| Share Balance (about 70–71s) | Recipient lookup by username, BDT amount, displayed 5 BDT fee, and Share Balance action. The clip does not show confirmation or settlement screens. | Missing from the plan and now recorded as a later-only candidate. It is not enabled for the pilot. Any future implementation needs a reviewed fee/limit policy, recipient confirmation, atomic paired ledger entries, anti-abuse controls, and dispute handling; the displayed fee is not adopted. |
| Withdraw (about 73–75s) | Available balance, amount and displayed minimum, payment-method selection, payout account/number field, Submit Withdrawal Request, pending-withdrawal list/empty state, instructions and warning to check the destination. The text claims processing within 24 hours. | Manual withdrawals were planned but the player form, destination confirmation, visible pending requests, and policy-controlled minimum/timing were underspecified. These are now explicit. Do not reuse its minimum or promise 24-hour service unless the operator confirms that service level. |
| Results (about 62–65s) | My Matches and All Matches tabs, search by match ID/title, refresh/loading placeholders, result rows with match title, mode/map, date, placement, match ID, kills, and prize amount. | Results/history were planned but the browse scope and displayed fields were missing. The player experience now includes own/all-published tabs, search, and these result fields. All Matches means published results only; no drafts or private participant details. |
| Match listing (about 79–92s, 103–109s, 114–118s) | Available-match count and refresh; image-led cards; Upcoming/Full labels, match ID, title, mode/map, entry fee, prize pool, participant count/capacity, progress bar, scheduled date/time, live countdown, Prizes shortcut, disabled Match Full state or Join Now action. The count shown changes as the list is refreshed. | List/details/reward/capacity were planned; prominent status/countdown/slot details were underspecified. The plan now makes those explicit. A changing count must reflect a fresh server result and not a cached estimate presented as live. |
| Match details (about 93–102s, 110–113s) | Match hero and ID; status; entry/prize pool/players; date/time, open slots, countdown; ranked prize distribution; Match Rules section; Participants section with joined/capacity count, numbered public names/handles and avatars. Visible examples include placement rewards and a per-kill reward. | Match rules, capacity and rewards were already planned generally. The detail layout, countdown, explicit prize rows/per-kill rewards, and participant list are now stated. Participant identity is limited to public game names/handles and preset avatars; details in the clip do not justify exposing user profile contact data. |
| Community, legal, and game-service links | Community chat, AI support, external support contact, rules, developer/about page, privacy policy, and a third-party Free Fire top-up shortcut appear in profile/wallet. | Rules/contact were planned; legal/about pages are now explicit. Chat, AI support, and top-up are recorded as deferred candidates with operating and provider review requirements, not pilot commitments. |

### Not demonstrated by the clip

The video does not show an actual successful deposit or withdrawal, transaction receipt verification, transfer confirmation/settlement, a successful match join, room ID/password release, live match operation, result administration/publication, or admin screens. Treat these as unverified, not as capabilities established by the reference. Existing security, manual-reconciliation, idempotency, and authorization requirements remain controlling even where the reference UI suggests an automated action.

## Official references

- Firebase pricing: https://firebase.google.com/pricing
- Firestore quotas: https://firebase.google.com/docs/firestore/quotas
- Storage billing requirements: https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024
- Firebase authentication limits: https://firebase.google.com/docs/auth/limits
- Firebase local emulators: https://firebase.google.com/docs/emulator-suite
- Android architecture: https://developer.android.com/topic/architecture/recommendations
- Compose performance: https://developer.android.com/develop/ui/compose/performance/baseline-profiles
- bKash business services: https://www.bkash.com/en/business
- Google Play developer policy: https://support.google.com/googleplay/android-developer/answer/18265346?hl=en

## Existing analysis evidence

- `analysis/extremetour6/rea-package.json`: decoded package/manifest evidence.
- `analysis/extremetour6/app-strings.txt`: embedded app strings; clues do not prove live behavior.
- `REA-ENVIRONMENT.md`: local REA runtime instructions.
- `bin/rea`: isolated dependency launcher; this is not an Android execution sandbox.

## Implementation progress — 2 November 2026

Started the local native prototype in `app/android`; detailed status is in `app/PROGRESS.md`. Requested Karpathy guidelines installed in `app/andrej-karpathy-skills` and applied through local project instructions. Five sample screens and local free registration are implemented. Debug APK is `app/releases/arena-demo.apk`. Debug build, four registration unit tests and Android lint passed. Phone visual acceptance remains pending: the installed emulator could not create its data partition with available Mac disk space. Firebase and finance remain future milestones; no live money or billing enabled.

UI iteration 0.2: installed the requested UI UX Pro Max skill and simplified the native prototype with original mode artwork, compact match rows, consistent outline icons, dedicated details and adaptive large-text layout. Debug build, four registration tests and lint passed. Actual emulator screenshots now captured; physical phone acceptance remains pending. See app/PROGRESS.md and app/design/DESIGN.md.

Homepage iteration 0.3: mode art and mode frames are now square; Home has a new original hero and a notification panel/inbox. The owner selected local-first because no Firebase project exists. Implemented and tested a narrow local Firebase foundation for announcements only: staff Auth/role checks, admin publisher, Firestore rules/indexes and a connected Android test build. Seven Android unit tests, eleven rules tests, admin build, Android build/lint and the admin-to-Android instrumented notification test passed. Public deployment and the rest of Authentication/tournament workflows remain pending. Detailed instructions are in app/admin/README.md.

Admin iteration 0.4: local Bangla admin console implemented with match metadata editing, announcements, text configuration, owner-selected staff access and read-only operations queues. Android emulator builds expose the console from Profile. Hind Siliguri is bundled for offline Bangla rendering. See app/admin/README.md for exact functional limits; finance execution, player match synchronization and results/room workflows remain pending.

Owner correction — native administration: the Android app now uses native Compose sign-in and admin screens. A server-verified admin role reveals an Admin bottom-navigation item; ordinary accounts never receive it. Sign-out and role removal hide the destination. The former web prototype is retired and no web server is required. Firebase emulators remain the only connected backend.

Native accounts iteration 0.6: Profile now supports regular-user email/password registration, password confirmation, automatic sign-in, sign-out and repeat sign-in through Firebase Auth emulators. Registration creates no staff roles or financial records. Production authentication, email verification/reset delivery and player profile storage remain pending.

Player workflow iteration 0.7: local build now opens dedicated illustrated authentication, restores sessions, saves player profiles and language, reads published native-admin matches, and routes missing join requirements to Profile/Wallet with a return action. Local trusted transaction service handles deposit review and paid-entry confirmation with idempotent credits/charges, global transfer-reference uniqueness and atomic seat checks. Debug build, 15 Android unit tests, lint, 17 rules tests, finance/concurrency integration checks and the instrumented account/deposit/language/restart journey passed. APK: app/releases/arena-local.apk. Setup and remaining physical-device/offline acceptance checks: app/LOCAL-PLAYER.md. Production connectivity, real payments and withdrawals remain disabled.


## Stability and free registration — 3 November 2026, iteration 0.9

The code now includes 0.8 live Firebase configuration; this was missing from the plan's earlier progress summary. Configuration/build success does not establish deployed rules, operational production staff access, live joins or finance readiness.

- Separated local/live build outputs and install IDs. Default builds are local; live configuration is parsed/validated, cleartext traffic is disabled, and the local transaction endpoint cannot be configured for live accounts.
- Free registration now uses an Android Firestore transaction and restrictive rules requiring the immutable player entry and exact seat increment in one commit. It does not require the trusted local transaction server. Duplicate retries return the existing confirmation; transactions must be online.
- Live preview explains that paid joins and deposits are unavailable; the emulator-only approval service is retained for fictitious transactions. Staff review controls use their own operation state for feedback/retry.
- Verification evidence and current limits are recorded in app/verification/stability-v0.9/REVIEW.md.

Remaining pilot work includes restricted rooms, team rosters/format validation, results/publication/settlement, cancellations/refunds, full bKash collection and rejection flows, ledger/balance categories, withdrawals/reserves, account recovery/password change, participant lists/reward breakdowns/filtering, leaderboards/referrals/support/legal content, content/maintenance/version integration, diagnostics, pagination/offline consistency and physical-device beta/recovery acceptance. Deferred features in the roadmap remain deferred. No paid infrastructure, production deployment or real money is enabled by this iteration.


## Native rooms/results — 3 November 2026, iteration 0.10

Implemented audited room/release editing, server-only player room display, private result drafts with saved-draft review, and atomic immutable publication. Public results contain game identity/performance and match metadata; private ownership links stay separate. My/All Published results support title-prefix/exact-ID search and 20-row pagination, with labelled cached public browsing. Room rules check confirmed membership, server time, enabled state and active match status; UI credentials clear on offline/background/navigation/account changes.

Publication is separate from settlement: announced awards have zero credited amount and pending/not-applicable settlement status, and cannot be marked as wallet credits by staff clients. Concurrent retries verify the existing server result/ownership link. Current unit/security/device evidence is in app/verification/workflow-v0.10/REVIEW.md; operator instructions are in app/TOURNAMENT-WORKFLOW.md.

The core free-entry → room → reviewed public result/history journey is implemented locally. This does not finish lifecycle enforcement, whole-match completeness, team rosters, participant/prize tables, correction/refund/settlement flows, production deployment or physical-phone acceptance. The full remaining pilot scope stays in app/IMPLEMENTATION-CHECKLIST.md. No real money or paid services were enabled.


## Owner scope and design correction — 3 November 2026

The owner explicitly requested skipping the account-recovery work and focusing on a minimal, modern, intuitive experience across the features already implemented, following UI UX Pro Max. The unfinished recovery/password-management source has been removed; it is not an outstanding task for this requested scope. Email registration, sign-in and sign-out remain. Earlier recovery/password-change roadmap references are historical and superseded by this decision. The current task is a design/UX review and polish of existing functionality, without enabling payments, deploying services or adding further roadmap features.


## Existing-feature UI polish — 3 November 2026, iteration 0.11

Following the owner's scope correction and the local UI UX Pro Max skill, refined current player/admin functionality without adding recovery or enabling more backend workflows. The UI now has compact square-art feeds, a shorter details overview, consistent back/refresh headings, restored notification access, clearer My Matches navigation, profile editing and deposit forms on demand, structured request activity, leaner result search and an admin section picker. Staff enter taka values; exact conversion/validation preserves integer-paisa storage and rejects unwanted rounding. Accessibility includes single navigation names, labelled icon controls, larger-text layout, stronger borders and image scrims.

The unfinished recovery/password-management sources were removed. The owner’s current request is a bounded UI/UX polish of implemented features; the remaining roadmap is preserved separately. Evidence and scope limits are in app/verification/minimal-v0.11/REVIEW.md. No paid/live money services or production deployment were enabled.


## Missing-feature implementation — match browsing, 3 November 2026

Added case-insensitive ID/title search, combined mode/fee/availability/date filters, Bangladesh-calendar Today/Tomorrow boundaries and scheduled-time sorting over the currently loaded match list. Cards show map/prize data and scheduled countdowns; details include match ID/status/map/remaining capacity/per-kill reward. Countdown uses the device clock and does not establish that a match is live. Cached match data is labelled, and existing online transaction checks remain authoritative for joining. Filter labels have English/Bangla resources.

This is progress toward the full missing-feature scope, not completion. Match pagination beyond the current 100-record listener, full prize tables/rosters/lifecycle, finance, withdrawals, support/content, diagnostics and deployment/device acceptance remain tracked in app/IMPLEMENTATION-CHECKLIST.md. Password recovery stays excluded by the owner's earlier decision; later-only roadmap items stay deferred. No live payments or production deployment were enabled. Verification for this increment: app/verification/match-browsing/REVIEW.md.


## Missing-feature implementation — team rosters, 3 November 2026

Added roster review before joining, with captain from the saved Free Fire name and one/two/four distinct game names for Solo/Duo/Squad. Free joins validate against the current match mode inside the transaction and Security Rules; local paid entry requests and confirmations validate the roster again before reserving capacity or charging. Capacity remains measured in registered entries (one entry slot per roster), stated explicitly in the form. Saved rosters are visible only to their owner and staff through private entries. Existing historical solo entries remain readable without a roster field; new Duo/Squad entries require a roster. Existing immutable team entries are not silently rewritten.

Both requested skills guided the implementation. Public participant publication remains outstanding rather than exposing private entry documents. Full-map/Clash Squad configuration, broader lifecycle work, finance and the rest of the pilot remain in the checklist. Verification and limitations: app/verification/team-rosters/REVIEW.md. No production rules were deployed and no live finance was enabled.


## Missing-feature implementation — public participants, 3 November 2026

Match details now include a paginated public participant list before joining. New free confirmations publish only game names/roster and preset avatar atomically with entry/capacity; the emulator-only paid service publishes only on confirmation. Public document IDs are random and private links/entries/profiles remain restricted. Registration review explains which game identity will be public. Rules reject private/extra fields, mismatched identity and edits/deletions to published snapshots. Staff can publish older confirmed entries through an audited transaction that only adds public ID/avatar metadata to their private entry. Legacy clients/entries remain compatible and may need staff publication; the UI states this rather than implying a complete list.

Public and staff participant lists load 20-row pages. Offline public browsing is labelled incomplete and does not establish current participation. Public correction/removal policy, runtime/accessibility checks and the remaining pilot features stay outstanding. Production rules were not deployed and live money remains disabled. Evidence: app/verification/public-participants/REVIEW.md.


## Missing-feature implementation — player information pages, 3 November 2026

Profile now opens Rules, Help/contact, About/Developer and Privacy pages. A new native Player pages admin section publishes four fixed page IDs with title/body, optional HTTPS link, enabled state and revision. Public page documents omit staff/account identity; restrictive rules require a matching private audit record for each revision. Players can read only published pages, with explicit loading/unpublished/error/retry and labelled cached/offline states. Staff-only configuration stays private. Links open only on an explicit user tap and the UI validates HTTPS URLs without embedded login information.

No operator policies or contact details were invented. Owner-approved text/links and runtime/device acceptance remain required. The implementation checklist separates the completed page/publishing framework from that outstanding content. This increment does not add legal advice, disputes, AI help, deployment or live finance. Evidence: app/verification/player-pages/REVIEW.md.


## Missing-feature implementation — private support requests, 3 November 2026

Added player text disputes/support requests with subject, original description, optional related reference, persistent request ID, status and immutable staff reply history. Staff have a native paginated Support queue. Reply/status changes atomically write immutable history and audit records; original player text/ownership cannot be edited and concurrent decisions cannot overwrite the same revision. Open/review/resolved statuses do not change financial records or establish a payout. Players see only their own requests/replies. Submission and staff decisions use online transactions with stable retry IDs; lists/history require a server connection and provide retry states.

This is text support, not public chat, AI help or an attachment flow. The owner still supplies staffing/contact/response expectations; no service time was promised. Player follow-up messages inside an existing request are not implemented; players can submit a new request referencing an earlier ID. Device acceptance and the broader tournament/finance/operations scope remain outstanding. Instructions/evidence: app/SUPPORT-WORKFLOW.md and app/verification/support-requests/REVIEW.md. No production deployment or real money was enabled.


## Missing-feature implementation — enforced lifecycle, 3 November 2026

Staff match saves now require sequential revisions, immutable public-safe history and private staff audit in one transaction. New records start in draft; supported progression is draft → open → closed → live → review. Core entry terms become immutable after the first confirmed registration. The native editor offers only current/next supported states, disables locked terms, rejects stale edits, and prevents terminal edits. A Match history view pages recorded changes.

Free matches can be cancelled before results review with an explicit reason and server timestamp, shown to players. Paid cancellations, review-stage cancellation, generic settled status and terminal reopening are blocked pending the appropriate refund/settlement/correction work. This is enforced lifecycle progress, not whole-match completeness or financial settlement. Historical fixtures were updated to follow the lifecycle through client rules rather than bypassing it. Evidence: app/verification/match-lifecycle/REVIEW.md. No production deployment or live money was enabled.


## Missing-feature implementation — test-mode deposit review, 3 November 2026

Expanded the emulator-only deposit flow to collect a sending number, stable request ID, transaction reference and exact integer-paisa amount. Requests snapshot a fixed demo provider/receiver, currency and time. Pending duplicates do not claim receipt uniqueness; approval claims a provider/account-scoped reference atomically with the wallet/ledger/request/audit decision. Staff enter checked receipt details; mismatches require rejection/correction rather than silently changing the submitted claim. Rejection records immutable reason/operator/time without crediting funds. Corrections use a new request ID, preserving rejected history; repeated decisions and competing approval/rejection do not duplicate credits.

Deposit and entry-fee ledger records now carry readable type/status/currency/related references and balance-after metadata. Wallet deposit activity shows request ID, final reason and decision time. This is not yet the full paginated wallet ledger or split-balance model.

The receiving identifier is explicitly LOCAL-TEST-RECEIVER, not a phone number; instructions say not to send money. Reference uppercasing/format validation are demo conventions, not a verified bKash integration. Actual receiving-account details, provider normalization, live finance architecture, full accounting/reconciliation and real-money acceptance remain required. Live preview still blocks finance. Evidence: app/verification/deposit-review/REVIEW.md; operator instructions: app/DEPOSIT-TEST-WORKFLOW.md.


## Owner clarification — simple Add Money form, 3 November 2026

The owner reconfirmed the player flow: send money through bKash outside the app first, then enter only amount already sent, transaction ID and sender mobile number. No other player inputs or extra payment steps. Instructions belong below the fields, showing the receiving number with a copy action and a short explanation of manual review. The initial receiving number is 01889898989 and must be editable from native administration.

Implemented the three-field layout and native Administration → Payment settings. The receiving number is read from authenticated public-safe payment instructions; audited staff updates control changes. Missing configuration uses the owner-provided initial number. New test claims snapshot the displayed recipient, and changing the configured number does not rewrite prior claims or break identical retries. The test APK continues to state that real money must not be sent; enabling live payments remains outside this UI request. Evidence: app/verification/simple-add-money/REVIEW.md.


## Owner authorization — connect the live wallet, 3 November 2026

The owner explicitly requested live wallet connection. Added direct Firestore transactions for player deposit requests and staff approval/rejection in the live build, without enabling billing or adding hosted backend services. New live collections separate live claims/receipt/ledger records from emulator-operation schemas. Deployed security rules enforce the full credit tuple: pending→approved request, exact wallet increment, immutable credit ledger, unique receiver/transaction reference and staff audit in one commit. Rejections preserve the original claim and create an audited reason without credit. Client-owned balance writes and partial/duplicate credits are denied.

Firebase project arena-ff-34506 already has a free-tier default database and an admin role. The owner completed Firebase CLI sign-in. Existing rules were backed up, all 59 security/transaction checks passed, and tested rules were deployed. Live verification confirmed the deployed source hash, email/password sign-up, authenticated receiving-number and wallet access, denial of player balance forgery and cleanup of the temporary auth account. No fictitious deposit was credited in production. Current evidence: app/verification/live-wallet/REVIEW.md.

The connected APK is app/releases/arena-live.apk; the local APK remains isolated test mode. This connects manual deposits and wallet credit, not automatic bKash verification, withdrawals, refunds or paid-match confirmation. Those remaining features stay tracked separately.


## Single-date scheduling — 10 November 2026

Owner requested one match date and a separate date-only release at Bangladesh midnight. The local admin form and scheduler now support releasing an entire future match day's batch in advance, retain existing saved ranges, and enforce release/date/interval limits in matching rules. Implementation and operating details: `app/MATCH-SCHEDULING.md`. Verification and rollout status: `app/verification/single-date-schedules/REVIEW.md`. No live rule deployment, publication, billing or credentials are part of this change.


## Single-date schedules — live admin delivery, 10 November 2026

Owner requested the signed live APK and explicitly approved scheduling-rule deployment. Admin 1.0.15 / code 27 is built with the existing production signing identity; debug/release builds, all 70 unit tests per variant and lint passed. The scheduling-only live rule diff was deployed and read-back verified. No real player data, financial transaction, operating setting, billing or external APK publication changed. Evidence: `app/verification/single-date-schedules-live/REVIEW.md`.
