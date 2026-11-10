# Native rooms and results · 0.11

This workflow is connected to Firebase client SDKs. Local testing uses the Auth and Firestore emulators; no transaction service is needed for free registration, rooms or results. Production rules/index deployment and live acceptance remain pending. Real payments and withdrawals remain unavailable.

## Start locally

From `app`, build with `./scripts/build.sh local`, start Auth/Firestore with `./scripts/admin.sh run emulators` if they are not already running, then provision the disposable local owner with `./scripts/admin.sh run seed`. Install `releases/arena-local.apk` on the Android emulator. See LOCAL-PLAYER.md for connection limits and NATIVE-ADMIN.md for the local account instructions.

## Run a practice match

1. Sign in as the local owner, open Profile → Administration, then choose Matches from the section picker. Create a Solo practice match with entry, prize and per-kill amounts set to zero. Amounts in this editor are in taka. Fill schedule, capacity, rules and cancellation terms. Save as draft, check it, then edit its status to open.
2. Use a regular player account, save its Free Fire username, find the match and join. Review the one/two/four-player roster for Solo/Duo/Squad and confirm it. Game names and the captain’s preset avatar are public; never enter contact information. Wait for **Registration confirmed**. A queued/cached response is not a new confirmation. Check My Matches.
3. As staff, close registration (or set the match live). Open its **Room and results** action → **Match room**. Enter a fictitious room ID/password and release time in Bangladesh time. Check the values and enable release, then save. Each save adds an audit record and rejects stale edits.
4. A confirmed player opens match details → **Show room**. Rules enforce membership, release time, enabled state and closed/live match status. Other players, requested entries and early requests are denied. Credentials are never read from public match records or shown from offline cache. The app hides them when backgrounded; the player must request them again after returning.
5. Open the match’s **Results**. **Winners** assigns each saved prize position and credits its placement prize. **Player kills** lists all confirmed participants; unset counts are 0. Enter kills (expand new team rosters for member counts), optionally **Save draft**, then **Review kill credits** and confirm. The review includes all pending entries, including zeros, and excludes money already paid. Either reward can be credited first. Retrying a partially completed batch does not pay successful entries again. Existing combined results can receive one audited supplemental kill credit, with a reason, for only the unpaid amount. New finalized kill counts cannot be edited. Move the match to **review** and complete settlement separately after all entries and prize winners are settled.
6. Players open My Matches → **Results**. **My results** uses a private ownership link; **All published** exposes only public game identity and performance. Choose exact match ID or title prefix from the search-mode menu. Use the header refresh icon to reload. Lists load 20 rows per page and offer Load more; saved public results are labelled when offline.

Announced awards are distinct from wallet credits. Publication writes no wallet or ledger entry and shows an award as awaiting settlement when it is nonzero. Zero-award practice results show that no prize credit applies. Settled cannot be selected in the staff editor. It remains unavailable until the financial settlement workflow exists.

## Current limits

- Drafts can be edited before publication. Published rows and ownership links are immutable. Result correction/reversal and wallet settlement workflows remain to be implemented.
- Publication is per participant; the operator must check that all expected participants have been reviewed. There is no automatic final-rank calculation, whole-match prize-total validation or settlement.
- The admin participant picker loads up to 100 confirmed entries and labels that limit. General admin/match list pagination, whole-match completeness and settlement remain pending.
- Room passwords are temporarily visible only after server authorization; once disclosed to a legitimate participant they cannot be made unknown to that person. The UI hides cached/backgrounded room details and responds to server access changes.
- Team roster entry and public participant browsing are implemented. Explicit full-map/Clash Squad configuration, prize distribution tables, phone acceptance, screen-reader checks and money workflows remain tracked in IMPLEMENTATION-CHECKLIST.md.

## Verification

See verification/workflow-v0.10/REVIEW.md for emulator security/concurrency tests, Android journey and layout checks, unit/lint evidence and remaining limits. Test fixtures use fictitious details and never target a real Firebase project.

## Public participant list

New free registrations publish a public snapshot atomically with the confirmed entry and capacity update. Local paid confirmations do the same; pending paid requests do not appear publicly. Players see the list on match details before joining, with 20-row pages, refresh/retry and an explicitly incomplete offline cache state. Rows represent registered entries, so a team occupies one row with its submitted game names.

For older confirmed entries, staff opens the match's Room and results action → Participants, reviews the submitted game names, then uses Publish participant. Publishing adds only a random public ID and preset avatar to the private entry, creates the safe public snapshot/private link and writes an audit record atomically. It does not change fees, match membership or wallet accounting. Published participant snapshots are immutable. This is not a name-editing or moderation tool; correction/removal policy remains to be defined.

The new participant rules must be applied to a project before new Android client registrations can write their public snapshots there. Production deployment has not been performed by this increment. Current security/build evidence and device limits: verification/public-participants/REVIEW.md.

## Enforced lifecycle and cancellation

New matches must be saved as draft first. Edit and save to move through open → closed → live → review. You may save presentation corrections within the same nonterminal state, but cannot skip or reverse lifecycle stages. Once one entry is confirmed, mode, map, start time, capacity, fees, prize/per-kill amounts, rules and cancellation terms are locked. Title, description and artwork remain editable. Concurrent staff edits require reloading the latest revision; a registration that arrives during an edit can also cause a protected change to be rejected.

Staff can cancel a zero-entry-fee match from draft/open/closed/live by selecting cancelled and entering a reason. Cancellation atomically records its time/reason, immutable history and private staff audit. Players see Cancelled and the supplied reason; joining and room access are unavailable. This does not create a refund or wallet credit. Paid cancellation is blocked until refunds exist. Cancellation after results review and generic Settled edits are also blocked. Cancelled/settled matches cannot be edited or reopened through this editor.

Room and results → Match history shows recorded state changes and metadata saves in 20-row pages. Private audit records retain the staff identity. Older matches retain their existing state and can enter the next supported transition with revision/history starting at their next save; earlier history is not fabricated. Production rules are not deployed by this increment. Current evidence: verification/match-lifecycle/REVIEW.md.
