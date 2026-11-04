# Antihack Tour pilot implementation checklist

Updated 3 November 2026. This tracks the complete pilot scope in ../APP-IMPLEMENTATION-PLAN.md. A code implementation is not physical-device or production acceptance. Evidence for the current increment: verification/antihack-tour/REVIEW.md (earlier milestone records are historical).

## Owner redesign and follow-up

- [x] Antihack Tour name, supplied adaptive launcher logo and branded headers.
- [x] Guest Home/browsing/results/participants, protected Wallet and on-demand authentication.
- [x] Join-only setup; simplified email/game profile without player images/display name; Contact-only content link.
- [x] Main-screen redesign, compact search, filter sheet, simple result search and highlighted countdown panels.
- [x] Smaller normal Bangla typography; compact high-contrast timer; server-released room card with independent copy controls and paid-entry wallet-debit verification.
- [x] Staff-managed categories, large image grids, category/match image selection and match category assignment.
- [x] Chronological wallet rows with time, amount and transaction/reference IDs.
- [x] Database-backed native admin counts/queues, pagination, grouped editor, exact currency validation and adaptive sidebar.
- [x] Local/connected debug builds, 34 unit tests per environment, lint, 59 rules tests and seven current device checks. See verification/antihack-tour/REVIEW.md for evidence and rollout limits.
- [ ] Roll out the new rules/indexes to production when separately authorized; physical-phone release acceptance.

## Foundation and design

- [x] Kotlin/Compose Android foundation, original bundled artwork, native main screens.
- [x] Email/password signup, sign-in/out, persistent private player profiles and language.
- [x] Native admin access from a server-verified role; staff management, matches/notices/content editing.
- [x] Separate local/live APK identities, sessions, build outputs and transaction-service boundaries.
- [x] Minimal existing-feature UI review: compact feeds/details, on-demand forms, shared navigation, result search, admin section picker and taka entry.
- [ ] Final approved name/icon, supported formats, operating rules and payment/refund/withdrawal policy.
- Account recovery/password-management expansion skipped at the owner’s request on 3 November 2026; unfinished source removed.
- [ ] Player app username, real statistics and referral relationship tracking.
- [ ] Full localization and accessibility acceptance, including large text, screen readers and reduced motion.
- [ ] Reconcile planned Admin navigation with current Profile entry and test staff revocation throughout the current UI.

## Free tournament journey

- [x] Admin-published match listing/details and rules.
- [x] Direct Firestore free registration: immutable entry + atomic capacity, repeat-safe retry, server-confirmed success.
- [x] Rules tests for capacity races, forgery, private entries and denied unpaired writes.
- [x] Android free-join and offline/reconnect verification.
- [x] Solo/duo/squad roster review, exact-size/distinct-name validation in app/rules/local operations, and private player/staff saved-roster display.
- [ ] Explicit full-map/Clash Squad format configuration and roster device acceptance.
- [x] Match ID/title search and mode/time/fee/availability filters over the loaded match list; Bangladesh date boundaries covered by unit tests.
- [x] Scheduled countdown, map, prize pool and per-kill metadata in player views; cached match availability labelled.
- [ ] Complete match pagination, prize distribution tables and device acceptance of the new browsing controls.
- [x] Paginated public participant list of game names/preset avatars, atomic publication for new confirmations, private ownership links and audited staff publication of legacy entries.
- [ ] Review/publish legacy confirmed entries and complete participant-list device/accessibility acceptance.
- [x] Restricted room credentials with participant/release-time rules, audited staff editor and server-only player display.
- [x] Private staff result drafts, reviewed/immutable publication, own/all-published result views, title-prefix/exact-ID search and pagination.
- [x] Publication retries recover the existing immutable outcome; public rows contain no private ownership fields and do not credit wallets.
- [ ] Prize breakdowns/per-kill results consistent with settlement and credited history.
- [x] Core free-entry journey verified on Android: join → staff room setup → player room access → reviewed publication → player result history.
- [x] Enforced draft/open/closed/live/review transitions, audited immutable match history, stale-edit protection, entry-term locking and recorded free-match cancellation.
- [ ] Whole-match completeness, paid cancellation/refunds, financial settlement and result corrections; lifecycle device acceptance.
- [ ] Admin participant picker pagination beyond 100 entries.

## Finance (fictitious transactions first)

- [x] Emulator-only pending deposits and repeat-safe staff approval/credit.
- [x] Emulator-only requested paid entries and atomic staff charge/seat confirmation.
- [x] Live preview prevents calls to the local transaction service and explains unavailable paid actions.
- [ ] Production-capable finance architecture within the approved hosting/billing constraints.
- [x] Test-mode sender collection, scoped demo provider/receiver references, explicit receipt-detail matching, discrepancy rejection, immutable decision reason/time and correction via a new request ID.
- [x] Owner-authorized receiving number, direct live deposit/manual-approval transactions and deployed accounting/authorization rules.
- [ ] Provider receipt operations/normalization acceptance, complete accounting/reconciliation, physical-device paid-pilot acceptance and remaining finance workflows.
- [ ] Distinct deposit/winnings/bonus/reserved balances and policy-based spending eligibility.
- [ ] Full dated, paginated ledger with references/types/final states and reconciliation.
- [ ] Withdrawal form/policy/destination confirmation; reserves, manual review/payout, rejection/release, uncertain payout handling.
- [ ] Repeat-safe prize settlement, cancellation refunds, reversals and adjustments.
- [ ] Complete decisions/audit records, test/live separation and finance authorization/concurrency/recovery tests.

## Remaining pilot experience and operations

- [x] In-app notices and persistent read state.
- [ ] Staff-published leaderboard and referral display.
- [x] Private text support/dispute requests, player status/reply history, paginated staff queue, audited replies/status changes and preserved original requests.
- [ ] Support staffing/response policy, actual configured contact route and device/accessibility acceptance.
- [x] Rules, Help/contact, About/Developer and Privacy page navigation, safe HTTPS contact links, audited staff publishing and labelled offline reading.
- [ ] Owner-approved Rules/Help/About/Privacy text and actual contact link; content/device/accessibility acceptance.
- [ ] Staff-managed promotional content, maintenance and version/update integration.
- [ ] Pagination, screen-scoped listeners and consistent cached/offline/loading/pending/error states.
- [ ] Crash/performance diagnostics, Baseline Profiles/Macrobenchmark and release-device measurements.
- [ ] Migrate historical announcement/admin device tests to current authenticated navigation.
- [ ] Verify live Auth setup, deploy rules/indexes and securely provision production staff when authorized.
- [ ] Signed release packaging/toolchain/icon review, closed beta and quota measurements.
- [ ] Reconciliation/dispute/recovery/staff-access operator guide and tested export/restore procedure.
- [ ] Owner phone acceptance and distribution/receiving-account/tournament/public paid-launch decisions.

## Explicitly deferred

Automatic payment verification, player-to-player transfers, public chat, AI support, third-party top-ups, additional games, photo uploads, persistent teams, result imports and scheduled leaderboard computation remain later roadmap items with the approvals and operating requirements in the plan. They are not pilot-completion requirements.
