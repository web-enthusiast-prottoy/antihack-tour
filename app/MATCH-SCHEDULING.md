# Single-date match schedules

Updated 10 November 2026. Signed live Admin 1.0.15 / code 27 and the matching scheduling rules are now available. Verification: `verification/single-date-schedules-live/REVIEW.md`.

## Creation

Matches → Create match → Scheduling → Create matches for one day.

Choose one **Match date**, From/Until times, minutes between match starts, and a **Release date**. There is no first/last date range in the new form. All dates and times use Bangladesh time (`Asia/Dhaka`). Release date must be on or before the match date; no release-time input is needed because release starts at **12:00 AM**.

For example, Match date 12 November and Release date 11 November opens all matches from midnight at the start of 11 November, allowing registration before the games. With 09:00–23:00 and a 20-minute interval, the batch has 43 starts on 12 November only: 09:00, 09:20, …, 23:00. Release date 12 November instead opens the same batch from the start of match day. The end time is included only when aligned with the interval. Overnight windows are unsupported.

If the release date has already started, saving releases the remaining upcoming matches immediately. A future release date queues the batch. Every generated match opens for registration with the saved category, artwork, mode/map, capacity, entry fee, prizes and rules. Participants, rooms and results start fresh. These are app match records; staff still manage Free Fire rooms and results normally.

## Reliability and management

Keep automatic date/time enabled on the admin phone and an authorized admin app signed in and online at release. The existing visible scheduling service checks about every 15 seconds, including while minimized. Midnight is the earliest release time, not an exact-time delivery guarantee: Android or network delays can postpone creation. Reopen the app to resume if Android stops it. No billing, host or new credentials are needed.

A batch uses successive atomic match transactions. Each includes its prizes, public opening, history, audit, deterministic run ID and progress. Interrupted creation resumes without duplicates. On late creation or recovery, starts already past are skipped, and no matches move to another date. An expired schedule cannot be newly saved.

Schedules shows the match date, release date at midnight, time window, interval and generated-match history. A future release displays Waiting for release date and Scheduled release. Pause/Stop prevents remaining creation; existing matches remain. All matches created refers to creation, independently of game completion. Stop and create a new schedule to change immutable timing/template details.

## Compatibility and rollout

New records retain equal `dateFrom`/`dateTo` values for the single match date and add `releaseDate`. Previously saved records without `releaseDate` remain readable and keep their existing daily-range behavior. The server validates single-day release records, rejects creation of scheduled runs before release midnight, and restricts new runs to the configured match date, time window and interval. Release date cannot be changed through Pause/Resume.

The matching Firestore rules were deployed with owner approval and read-back verified. Update all admin phones: older versions do not understand advance release and can delay a batch until match day. No live player data, operating settings or billing were changed.

## Wallet entry update

Paid entry uses deposit balance first and available winnings for the remaining fee. Pending withdrawal reserves are excluded. The seat, entry, wallet charge, public participant and immutable ledger commit together; retries charge once. Ledger records preserve both funding portions and both resulting balances. Cancellation refunds return each portion to its original balance. Historical deposit-only entries retain deposit-only refunds.

Withdrawals remain winnings-only; deposit funds cannot be withdrawn. Entry and withdrawal transactions both read the wallet, so competing operations cannot spend the same funds.

## Previous production delivery

Production activation was verified on 8 November 2026 with player 1.0.7/code19, admin 1.0.12/code24 and the matching deployed rules. Upgrade both apps and all staff phones. Current evidence is in `verification/daily-batches-production-2026-11-08/`; the earlier 7 November deployment describes the superseded winner-dependent behavior.
