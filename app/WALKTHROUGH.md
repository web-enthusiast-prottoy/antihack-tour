Current status: the owner subsequently authorized production deployment. The live backend and signed release are now covered by [PRODUCTION-RELEASE.md](PRODUCTION-RELEASE.md). The local verification below records the earlier completed milestone.

# Completed local implementation — 4 November 2026

The approved Spacious design and the prize-to-withdrawal flow are implemented in the local Android build. No production deployment or real payment was performed.

## Player experience

- Home contains announcements and category image tiles. The logo aligns with the left content gutter and the notification badge stays within the header, including large unread counts and 200% text.
- Explore shows matches from every category. Tapping a Home category applies that category; entering Explore directly clears browsing filters. Bounded pagination keeps matches and categories beyond the first 100 reachable.
- Spacious match cards retain the saved match image, clear countdown separation and grouped fee/prize/participation information. The whole card opens details; there is no View match details button. Confirmed entries retain View room details as a state label.
- Match details show the saved placement prizes and per-kill reward. Admins may add positions beyond first/second/third, within match capacity.
- Winnings and deposits are separate. Wallet includes withdrawal review, request statuses and transaction history. Pending/Approved requests reserve winnings; Rejected releases them; Sent records the final payout reference.

## Staff workflow

1. Create or edit a match, then add position prizes in the editor or the match's Prizes tab. The placement total updates from saved rows. Terms lock after the first confirmed entry. Starting positions for a legacy unallocated pool requires an explicit replacement confirmation.
2. Move the match to Results review. Choose a confirmed participant, enter placement and kills, save the draft and review the calculated award. The recipient is the registered account linked to that entry, even if two accounts share a game name.
3. Publish and credit. Published positions are unique per match. Duplicate taps, reconnects and competing staff cannot pay the same result twice. Pending credits can be retried. Complete settlement only after every confirmed entry has a settled result.
4. Open Withdrawal settings to choose the minimum and fee and enable requests. These are operator decisions, without invented commercial defaults or payout deadlines.
5. Review requests under Withdrawals. Approve retains the reservation. After manually sending through bKash, confirm success and record the unique reference to mark Sent. Reject only with a reason and confirmation no payout occurred. Uncertain transfer outcomes remain Approved until reconciled.

## Verification

| Check | Result |
| --- | --- |
| Local debug APK, unit/registration tests and lint | Passed; 40 unit tests, zero failures/errors, zero lint errors/fatal findings |
| Permission and transaction tests | 89 passed, including prize positions, winner conflicts, concurrent credits, private payout details, reservations and repeat-safe decisions |
| Final device acceptance | Five passing cases across two runs: withdrawal journey with recovery/rejection/approval/Sent and Bangla 200% landscape; winnings through completion/history; normal and large-count Home header; category/whole-card/all-category Explore navigation |
| Earlier targeted evidence | Prize editor/public table/term locking; 205-record paging; Spacious English/Bangla/large-text/reduced-motion checks |

The withdrawal acceptance test exposed a landscape sheet issue; the form now retains its opening state on rotation and opens fully expanded so the action remains reachable. Earlier failed test selectors and stopped local test services were corrected before retaining passing acceptance logs. Negative permission tests intentionally contain denied-write logs.

Detailed evidence and actual screenshots:

- [Home header](verification/home-header/REVIEW.md)
- [Spacious cards and routing](verification/spacious-matches/REVIEW.md)
- [Paging](verification/paged-browsing/REVIEW.md)
- [Prize positions](verification/prize-positions/REVIEW.md)
- [Winner credits](verification/winnings-settlement/REVIEW.md)
- [Withdrawals](verification/withdrawals/REVIEW.md)

Original summary audit: the compact sign-in card, numbered participant rows, teammate chips, shortened offline text, refresh spinner and removal of redundant headings/availability text exist in source. The participant refresh FAB is anchored to the roster footer; it is not a permanently screen-pinned overlay. The old Home Next match block is entirely removed by the later categories-only requirement. Historical build duration/APK claims do not describe the latest build; use the evidence above.

APK: `releases/antihack-tour-local.apk`. It requires the local Firebase services described in README.md. Payouts remain manual; there is no provider payout API. Published outcomes and completed financial records cannot be overwritten; future corrections require separately audited adjustment records. Production rules/index rollout, physical-phone and TalkBack acceptance are outside the completed local implementation.
