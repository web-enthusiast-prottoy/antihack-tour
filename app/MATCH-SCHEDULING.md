# Daily match batches

Updated 8 November 2026. This replaces winner-dependent scheduling in the updated admin app.

## Creation

Matches → Create match → Scheduling → Repeat this match automatically.

Choose a daily start/end time, minutes between match starts, and inclusive first/last dates. All dates and times use Bangladesh time (`Asia/Dhaka`). Overnight windows are unsupported. For example, 09:00–19:00 with a 20-minute interval creates 31 matches: 09:00, 09:20, …, 18:40, 19:00. An end time that does not fall on the interval does not add an extra match.

Saving creates all remaining slots for today immediately, including slots later in the day. The first day skips times before the schedule was saved. For a future first date, creation waits until that date. A multi-day range creates one day's batch at a time: at Bangladesh midnight the online admin runner creates that day's matches. It never pre-creates later days and never waits for winners, credits or the preceding match to end.

Every generated match opens for registration with the saved category, artwork, mode/map, capacity, entry fee, prizes and rules. Participants, rooms and results start fresh. These are app match records; staff still manage Free Fire rooms and results normally.

## Reliability and management

Keep automatic date/time enabled on the admin phone and an authorized admin app signed in and online for each daily batch. The existing visible scheduling service checks about every 15 seconds, including while minimized. Android may stop or delay background work; reopen the app to resume. No billing, host or new credentials are needed.

A batch uses successive atomic match transactions, so a large batch takes some time to finish. Each match includes its prizes, public opening, history, audit, deterministic run ID and progress. Saving reports success after today's batch finishes. Interrupted creation resumes from the last committed match without duplicates; the background runner can also resume it. If the app was unavailable across multiple days, it creates the current day's batch and skips missed dates. A batch resumed late on a subsequent day retains that day's full configured times, including earlier times that day.

Schedules shows generation status, next daily batch, interval and generated-match history with scheduled start times. Pause/Stop prevents remaining creation; existing matches remain. Completed means all slots have been created, not that the games have finished. Stop and create a new schedule to change immutable timing/template details.

Older active schedules use daily batches when processed by the updated admin app. Previously generated matches are retained and new fixed slots continue after the last generated match's saved start. Upgrade all admin phones together: older app versions still run the previous winner-dependent behavior.

## Wallet entry update

Paid entry uses deposit balance first and available winnings for the remaining fee. Pending withdrawal reserves are excluded. The seat, entry, wallet charge, public participant and immutable ledger commit together; retries charge once. Ledger records preserve both funding portions and both resulting balances. Cancellation refunds return each portion to its original balance. Historical deposit-only entries retain deposit-only refunds.

Withdrawals remain winnings-only; deposit funds cannot be withdrawn. Entry and withdrawal transactions both read the wallet, so competing operations cannot spend the same funds.

## Delivery status

Production activation was verified on 8 November 2026 with player 1.0.7/code19, admin 1.0.12/code24 and the matching deployed rules. Upgrade both apps and all staff phones. Current evidence is in `verification/daily-batches-production-2026-11-08/`; the earlier 7 November deployment describes the superseded winner-dependent behavior.
