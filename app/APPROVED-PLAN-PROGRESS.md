Production follow-up: owner authorized live release on 4 November 2026. Firestore rules/indexes are deployed; live withdrawal policy is ৳50 minimum plus ৳5 fee, editable in Admin. The demo deposit number is removed; Admin must enter and enable the real number. Paid-entry confirmations/cancellation refunds now use direct Firebase transactions. Signed APK and current evidence: PRODUCTION-RELEASE.md.

# Approved match, prize and withdrawal plan — completed locally

Owner approved Spacious with match artwork and whole-card navigation, then requested sequential implementation of the complete prize/winner/withdrawal plan. The latest clarification is authoritative: Home has category tiles; Explore has matches from every category, not a category grid.

## Completed

- Home announcements/categories only; aligned logo and unclipped notification badge. Normal and 200% text/large unread-count checks pass.
- Image-led Spacious whole-click match cards and matching details, with grouped information and space below the countdown. No separate details button.
- Category-to-filtered-Explore navigation, direct Explore resetting filters, and bounded live pagination beyond the first 100 matches/categories. Earlier 205-record tests pass.
- Configurable position prizes in both the match editor and per-match Prizes tab; exact derived placement total; public breakdown; positions within capacity; lock at first confirmed entry; explicit handling of legacy pools.
- Confirmed participant selection, placement/kills, reviewed publication and calculated prizes. Unique publication and credit claims prevent duplicate places/payments. Recipient comes from the registered account link. Zero-prize and legacy budget cases are explicit.
- Separate deposits, available winnings and withdrawal reserves. Immutable credit/ledger/audit records, progress counters, retries and whole-match completion are enforced in rules and transactions.
- Player amount/number/fee review and paginated private request status history. Persistent pending intent recovers an existing server request after interruption.
- Explicit minimum/fee/enabled withdrawal settings. Pending reserves the exact payout plus fee; Approved holds it; Rejected requires reason/no-transfer confirmation and releases once; Sent requires a recorded unique payout reference and finalizes once. Uncertain outcomes remain reserved for reconciliation. Payouts are manual outside the app.
- English/Bangla labels, inline validation, loading/error/reload states, sign-out privacy and full-height withdrawal sheets that remain usable after rotation at double text size.

## Verification

40 unit tests including registration, debug build and lint pass with zero errors/fatal lint findings. 89 security/transaction cases pass. Final device acceptance comprises five passing cases across clean retained runs: complete withdrawal/recovery/admin decisions plus Bangla 200% landscape; winner credit/completion/wallet/public result; normal Home bounds; large unread badge at double text; Spacious/category/all-category navigation. Earlier targeted prize/paging/accessibility evidence remains retained separately.

See WALKTHROUGH.md for the requirement audit, operator steps and evidence links. New local financial evidence is in verification/winnings-settlement/ and verification/withdrawals/. The latest APK is releases/antihack-tour-local.apk.

The existing preview Firestore database was preserved. Stopped local Android/Auth services were restarted, and missing-only seeding restored a disposable test account. No real funds, production rules/index deployment, billing, publication or credentials were introduced. Physical-device/TalkBack and live rollout are not claimed. Corrections cannot overwrite published outcomes or financial history; a future adjustment workflow must be explicitly audited.
