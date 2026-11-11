# Player feedback fixes — 11 November 2026

## Behaviour

- Upcoming no longer offers the public Explore feed's Load more button. It checks additional public pages automatically when an owned match has not been loaded; an empty owned list has no paging action. Explore retains paging.
- Results places search above My results / All published filters. A removed or private historical match no longer invalidates other owned history, and revoked document access clears its previously rendered data. A small notice explains inaccessible historical records. Ownership-feed and service failures still show Retry. Existing published ownership links continue to preserve published results.
- Unpublished or unreleased room access uses a neutral waiting message instead of “Room unavailable”. Authenticated room requests, confirmed-entry/release rules, ten-second checks and background credential clearing remain in place. The server intentionally conceals missing and unauthorized room documents, so the client cannot distinguish those from unreleased rooms; it does not claim a room is published before access is granted. Authentication and connection failures remain explicit errors.
- Match details has a fixed bottom refresh icon. Refresh reloads match data, match-access checks, room access, prizes and players. Description appears first in Overview as an expanded section, followed by expanded rules, room access and muted match metadata. The registered roster appears only in Players.
- Wallet has one right-aligned Activity filter and a fixed bottom refresh icon instead of a text refresh action. Large-font layouts keep the filter aligned right on its own row. Refresh rechecks the server wallet and reloads activity without resetting the selected filter. Live wallet listeners remain the single writer of displayed balances; no monetary write or payment workflow changed.
- New player messages have English and Bangla resources. Missing public FFUID lines remain omitted.

## Verification

The history failure was reproduced using mocked Firestore SDK callbacks before the fix: a denied historical match broke the whole history, and revoked access retained stale data. The regression suite exercises these paths, ownership-feed and transient errors, link recovery, old callbacks and invalid legacy references. Wallet tests check server reads, duplicate-refresh suppression, retained live balances and retries without database writes. Native Compose tests cover filter/search order, bottom refresh reachability and busy state, Overview structure, disclosure state, room-response states and large Bangla layouts.

Final verification passed: 183 player tests and 82 admin tests; player/admin debug builds; both instrumentation APK builds; player/admin lint with zero errors (11/17 existing warnings). Instrumentation APK assembly is a compile check; no device or live production Firebase session was available. No rules, indexes, backend deployment or production data were changed.
