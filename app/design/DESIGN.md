# Player UI refresh · remaining pages · 11 November 2026

The remaining reviewed player designs are implemented. This section supersedes the older single-balance Wallet, expanded transaction list, inline Profile form, full announcement-card and single Matches-list descriptions below. Earlier Home/authentication, Explore and details/joining stages are retained.

- My matches uses Upcoming/Results tabs, compact saved-artwork cards with the quiet top-right countdown and separate details/room actions. Confirmed past matches move to the existing owned history; unresolved entries remain reachable. The room shortcut checks the same current ban status and uses the existing server-release controller. Its credentials retain background/offline clearing and Copy/Refresh/Hide behaviour.
- Results preserve My/All published scopes, title/ID search, debounce, cache/errors, pagination and pending matches. Compact thumbnail cards show placement, kills and announced award. Actual credited winnings remain separate. Opening a reward breakdown retains player name, match ID/time, reward components, kill-finalization and settlement states.
- Wallet shows Balance and Winnings together, their spendable total and reserved winnings separately. Added-money and winnings labels follow the reviewed wording; whole amounts omit .00 while actual fractional paisa remain exact. A balance-information sheet explains spending/withdrawal rules. Withdraw stays disabled for unverified/zero winnings.
- Activity uses compact icon/title/status/time/amount rows and a loaded-record filter. Opening a row reveals complete original transaction/provider references and withdrawal details. Existing owner queries, per-source paging, cache disclosure and linked deposit/withdrawal/ledger deduplication remain intact.
- Add money uses the three existing validated fields, compact receiving-number/copy card and short approval note. Current-server-number, busy, persisted request ID, verification timeout and server-only approval checks remain. Payment outcomes display the actual server-derived message. Withdrawal uses a separate form, full review step and actual request receipt; payout, fee and reserved total stay distinct. No pending request is presented as money credited or paid out.
- Profile uses the real email, game name and muted FF UID, with editing in a sheet. Contact/WhatsApp, language selection and explicit sign-out confirmation follow the reviewed groups. Existing guest/private scope, optional profile fields, limits, save method and language preferences remain.
- Notifications group existing announcements by their actual Dhaka date, use compact unread-aware previews and a full selectable reader. Revision-based read keys, mark-all-read, cached/error/empty states, retry and system notification settings remain. Published Contact/Rules/About/Privacy pages share readable selectable content and retain the existing enabled/publication/link checks and offline fallback; no removed Profile links are reintroduced.
- Native sheet, ripple, tab and route motion is retained. Large text stacks balance/stat/action layouts and the activity toolbar, with English/Bangla resources, readable long text, keyboard navigation and 48dp controls. No new production data, backend fields, credentials or signing configuration are required.

Cross-page verification and remaining live/device acceptance limits are recorded in [FINAL-PLAYER-REFRESH.md](../FINAL-PLAYER-REFRESH.md).

# Player UI refresh · stage 3 · 11 November 2026

Match details and joining now implement the approved minimal designs. This section supersedes the older full-width countdown and single long details-page requirements for these screens. Home, authentication and Explore remain as previously implemented; My matches, Wallet and other later stages remain pending.

- Details use saved match artwork with the quiet top-right countdown, the current title/mode/map/date, three compact metrics and Overview/Prizes/Players tabs. Large text switches metrics to readable labelled rows and wraps the tab controls. The fixed join action shows the actual fee while preserving the existing account, profile, ban, entry, cache and availability checks.
- Overview leads with saved match rules, expanded by default with a short native disclosure transition. Room access follows: guests/pending entries get a compact disclosure; confirmed entries retain the existing server-checked credentials and Copy/Refresh/Hide actions. Room release polling, offline/background clearing, authentication and privacy logic are unchanged. Match ID/status use muted label-size text below these sections. Saved description, cancellation reason and registered roster remain available.
- Prizes use a simple sorted position/amount table, highlighting first place and retaining exact fractional paisa, per-kill data and all cache/loading/unpublished/error/retry states. The admin editor and data subscriptions are preserved.
- Players use numbered name rows, a tiny muted FF UID line and the existing teammates. Only the existing public participant document is read. Current public records omit ffUid, so legacy/missing values omit the UID line; no private profile reads, copied account UIDs or fabricated game IDs are introduced. Pagination, loaded-count disclosure and refresh/retry remain available.
- Joining uses one compact match summary, fee/available balance after entry, required teammate fields for Duo/Squad and one confirmation action. The removed “Your player” panel is not recreated. Names, roster validation, keyboard navigation, restoration, pending/error/retry and exact existing Firebase submission remain intact. The view also disables confirmation if live availability, entry status or verified paid balance changes while open. Missing-profile/funding routes retain their existing destinations and pending-match continuation.

Verification is recorded in [MATCH-DETAILS-JOIN-REFRESH.md](../MATCH-DETAILS-JOIN-REFRESH.md).

# Player UI refresh · stage 2 · 11 November 2026

Explore now implements the reviewed compact match-feed concept. This section supersedes the historical 190dp artwork and full-width sage countdown requirements for Explore only; My matches and details retain their current implementation until their own stages.

- A clearable ID/title search sits beside the filter action. Small All/Solo/Duo/Squad pills share the existing mode state with the full filter sheet. Material's 32dp visual pills retain 48dp touch bounds and wrap at large text sizes. Category, fee, availability and date filtering remain available in the existing sheet.
- Each whole-card action opens the same match ID. Saved match artwork fills a reserved 108dp banner. The approved quiet, dark countdown sits at its top-right, with a sage clock and tabular numbers; days, reached-start and live/review/completed/cancelled states are explicit. The banner can grow for accessibility text rather than clipping the badge. No bright countdown panel or separate timer row is used.
- Match title and the existing mode/map/date are followed by Entry fee / Prize pool, a thin sage participation bar and exact joined/capacity text. Whole-taka amounts omit .00; fractional paisa remain exact. Confirmed/pending entries, full, closed and cancelled labels remain visible. The decorative progress bar clamps its fill without changing the displayed counts.
- Empty first loads use the shared restrained skeleton; existing cards stay visible during later-page loading. Filter selection uses native state transitions and participation changes animate for 220ms. Platform animation scaling and reduced-motion skeleton behaviour remain in use.
- Firebase queries, field mapping, live listeners, artwork reads/cache, category handoff, search/filter paging, cache/error/empty/retry handling, past-match visibility and join workflows are preserved. No backend/schema or production configuration changes are required.

Stage-specific verification and live-test limits are recorded in [EXPLORE-REFRESH.md](../EXPLORE-REFRESH.md).

# Player UI refresh · stage 1 · 10 November 2026

The owner approved the reviewed charcoal/sage concepts and requested a staged implementation. This stage updates only player Home, Sign in and Create account. The remaining reviewed pages are design proposals for subsequent stages.

- Home retains Firebase announcements, active/sorted/paged categories and their saved artwork. The 76dp logo gains the ANTIHACK / TOUR wordmark without “Esports”; category cards display only their existing names. A compact latest-announcement card, “Choose your arena” heading, portrait category artwork and direct Explore shortcut implement the reviewed composition. Category selection still filters Explore; the general Explore shortcut clears filters, like direct Explore navigation.
- Initial Home loading uses stable skeletons; cached data, empty/error states and retry remain explicit. Card content enters with a short fade and 8dp movement. Skeleton shimmer is disabled when Android animators are disabled; Compose transitions retain platform animation-duration scaling. No fabricated progress or match data is introduced.
- Authentication uses the existing island artwork and logo, simple Sign in/Create account titles, email/password/confirmation fields, inline validation, password visibility, keyboard Next/Done, a Bangla/English action and Continue browsing. Submitting still calls the unchanged Firebase session methods. Busy controls prevent duplicate submissions; outgoing animated forms cannot submit. Email/mode survive saved-state restoration; passwords remain unsaved. Closing returns to the opening route; successful authentication retains the existing resume-join behaviour.
- The native theme, 48dp targets, safe-area/keyboard insets, large-text scrolling and bundled Bangla typography remain in use. Decorative wordmark text does not repeat the logo's accessibility name. Other player screens and the administrative app's workflows are unchanged.

Verification and the distinction between local checks and live-release readiness are recorded in [HOME-AUTH-REFRESH.md](../HOME-AUTH-REFRESH.md).

# Antihack Admin · Operations Desk

The standalone staff app uses charcoal `#101311` and `#1B211D` surfaces, one sage action color `#C4DF9B`, and a gold-edged shield launcher with a silver A, matching the main Antihack crest. The same vector mark appears on staff sign-in, Overview and the wide-layout sidebar. Phones show five destinations across the bottom. At 840dp of available width the navigation moves to a sidebar. At large system text sizes the compact navigation uses labelled icon semantics.

Overview prioritizes actionable review queues and the next three scheduled matches. Lists show a title or amount, relevant date, and status. Record identifiers and private payment details belong in the opened record. Settings groups payments, announcements, content, staff access, and audit history. Keep role and connection checks, field errors, and confirmation text where staff make a decision.

# Antihack Tour — current design

Owner-approved update, 4 November 2026: Home's browsing content is active categories only (no hero, next match or match feed); announcements remain above the grid. Category taps open filtered Explore, while direct Explore navigation clears browsing filters and shows the match feed without category image tiles. Spacious match cards use each match's saved artwork in a 190dp banner, rounded 20dp corners, 20dp content padding, 24dp between sections and 8dp label/value gaps. Fee/prize occupy two columns and participation/status wrap below. The entire card opens details; no separate details button. Details use matching artwork and grouped information with clear countdown separation. This supersedes conflicting Home/card descriptions below.

This direction implements the owner’s full redesign and supersedes the historical milestone notes below.

Public list reachability: categories load automatically through bounded 100-record live pages. Explore's unfiltered feed offers Load more; active searches and filters continue through all remaining pages. Counts identify loaded/cached data until complete. Page updates rebase downstream cursors without duplicate cards; permission rules retain their existing bounds.

Owner clarification, 4 November 2026: Explore means matches from every category, not a category image grid. Its direct navigation clears category filters. The Home logo uses a left-aligned 76dp square; the notification action reserves at least 56dp and places its unread badge outside the button's clipping layer while keeping it inside the header. Counts cap visually at 99+ and expose the exact count to accessibility.

Prize distribution: match details place a separate, spaced numeric position/amount table below the overview, with a per-kill line only here. Staff edit the same positions in the match editor and per-match Prizes tab. Amounts use exact taka entry, the placement total is derived/read-only, and the first confirmed entry locks terms. Prize positions save separately from other match fields; starting a breakdown explicitly replaces a legacy unallocated pool. Public rows omit zero-value removed prizes. Cache, loading, validation, retry and locked states have English/Bangla text.

Winner and withdrawal flow: staff review placement/kills against saved prizes, then publish and credit the linked registered account. Wallet separates deposit balance, available winnings and pending reserves. Withdrawal forms disclose destination, payout, fee and reserved total before confirmation; full-height sheets retain their opening state on rotation and remain scrollable at 200% Bangla text. Admin settings explicitly choose minimum/fee/enabled state. Approved holds funds; Sent records the manually verified payout reference; Rejected requires a reason and no-transfer confirmation. Private account/destination details stay within authenticated owner/staff views. Local test evidence is in verification/winnings-settlement/ and verification/withdrawals/.

- Native Android Compose, charcoal background `#101311`, card `#1B211D`, raised/input surface `#273029`, sage action accent `#C4DF9B`, primary text `#F1F3ED`, secondary text `#ABB5AD`. Semantic Material theme colors provide shared states. Text contrast and control boundaries are checked independently.
- System sans typography with bundled Hind Siliguri for Bangla, adjusted to 90% of the English type size and 92% of its line height for a closer visual match. System accessibility text scaling remains enabled. Clear page/title/body/metadata hierarchy, 16dp phone gutters, 20dp section gaps, 8–16dp internal spacing, restrained 12–24dp shapes. Native 48dp targets and keyboard/system-bar insets remain mandatory.
- Home: compact original island hero, a large two-column category image grid, upcoming match cards and compact announcements; signed-in players get a next-entry shortcut. Guest browsing starts here.
- Explore: compact 48dp-minimum search surface, adjacent filter icon with active-count badge, large category image cards while browsing and focused results after selecting a category or entering search. Filters open in a scrollable bottom sheet. Clearing filters includes the category.
- Shared match cards show artwork as match artwork, category/title/format, date and aligned fee/prize/participant metrics. Detail screens keep their fixed join action and public participants. No player avatars or display names appear in profile, participant or result UI.
- Results: one clearable search input for title/ID, keyboard Search and debounce, no search-mode menu. Independent database queries behind that one input preserve pagination. My results is authenticated; published results are public.
- Wallet: one balance card and Add money action, then a divider-separated chronological transaction list. Each row includes type/status, signed amount, timestamp and transaction ID, with a separately labelled provider reference. Linked requests/credits are deduplicated. No request implies credit before approval.
- Profile: email at the top with game username/UID, edit on demand, Contact, language and sign out. Verified staff see Administration. Removed Support requests, Rules, About developer and Privacy links. Missing setup prompts occur only in the join journey.
- Administration: drawer on phones, persistent 240dp sidebar from 840dp available width, bounded readable content. Dashboard totals and staff activity come from the database. Sorted/paginated record lists support explicit loaded-record search. Match editing has grouped details/schedule/fees/rules/publication fields, category selection, native date/time pickers and exact taka validation. Staff permissions, room privacy, immutable results and financial review safeguards stay enforced.
- Guest navigation omits Wallet and displays account prompts only from Profile/Join. Public subscriptions are separate from private profile/wallet/entry reads; signing out clears private composition.

UI/UX Pro Max’s relevant native accessibility, touch, progressive-disclosure, list and theming guidance was applied. Its generic neon/3D gaming-marketing output did not fit this owner’s minimal native direction. The original match artwork is retained. The owner supplied Antihack Tour Esports Emblem.png for the launcher/adaptive icon and all branded headers. No credentials, billing or publication were introduced.

The owner’s follow-up adds image selection to category and match editing. Uploaded artwork is decoded off the main thread, respects photo orientation, resized to at most 640px, and compressed to WebP at no more than 80,000 bytes. Immutable, staff-written artwork documents are separate from listing metadata and cached locally; the blob field is excluded from indexes. This small-pilot approach uses the existing Firestore setup without enabling Cloud Storage or billing. Document limits reference: https://firebase.google.com/docs/firestore/quotas.

Every match card and details screen has a compact solid-sage countdown strip: Starts in on the left and a tabular HH:MM:SS clock on the right, with a day prefix when necessary. Live/review/completed/cancelled states are explicit. The strip wraps at large accessibility sizes instead of clipping.

Confirmed participants get the room card immediately below the match header. Current server-released ID and password appear in separate selectable fields with labelled Copy actions and temporary Copied feedback. Pending entries explain confirmation/release requirements. Credentials clear offline and when backgrounded, and can be refreshed or hidden. Payment confirmation and staff release remain mandatory.

Verification is collected in `verification/antihack-tour/`. Local sample fixtures are explicitly labelled. Historical records below remain evidence for earlier versions, not current acceptance.

---

# Current design direction · 0.11

Owner brief: minimal, modern and intuitive across the features already implemented. Account recovery is explicitly excluded. The rules below supersede conflicting historical descriptions further down.

- Native Compose surfaces, a dark background, one sage action accent, white primary text and muted secondary text. Raised the outline token to `#6B786D` for clearer control boundaries; image text has stronger scrims.
- Keep the original home hero and square mode artwork. Match feeds use 72dp square thumbnails; details use an 88dp square overview with readable fee/prize/participant rows instead of another full-screen image.
- Use 16dp page/card padding, 20dp section gaps, 8–16dp internal spacing, 48dp native touch targets and restrained 8/16/20/24dp shapes. Wide content is bounded at 640dp.
- Keep five main destinations, with a compact Matches label and My matches screen title. A shared header places Back at the left and secondary refresh/notification icons at the right. Hidden large-text labels retain icon accessibility names; decorative icons beside visible labels have no duplicate descriptions.
- Reveal profile fields only for setup/editing. Show a stable profile summary otherwise. Open Add Money in a sheet and keep pending activity readable. Preserve the fictitious-payment explanation and disabled live finance controls.
- Results retain My/All Published scope; use one search field, a clearable input, a search-mode menu and a header refresh action. Do not imply credited prizes before settlement.
- Admin sections use one picker instead of a long chip strip. Staff edit taka values, while stored amounts remain integer paisa. Reject excess decimals without rounding and show validation beside Save. Keep private-room and publication safeguards.

Applied the local UI UX Pro Max skill's native accessibility/interaction/form/navigation checklist and verified Compose semantics search guidance. Its disclosure query returned only an unrelated web-typography result, so disclosure choices use its general form/navigation defaults and the owner's minimal brief. No neon/3D marketing style or new artwork was introduced.

Verification and actual screen captures: ../verification/minimal-v0.11/REVIEW.md. Physical-phone and full TalkBack acceptance remain distinct from the emulator checks.

---

# Arena UI — 0.2

Direction: minimal dark native interface, one muted sage accent, original atmospheric mode artwork, consistent outline icons and 24dp page gutters. System typography keeps Bangla fallback and font scaling. User preference for minimalism takes precedence over the skill's contradictory entertainment recommendation to avoid minimalism.

UI UX Pro Max was installed locally in `.agents/skills/ui-ux-pro-max`; source checkout `ui-ux-pro-max-skill` at 3a13158ff1f32156cbe144108de48eda2ecaed9a. Applied its minimal style, native touch sizes, semantic labels, card hierarchy, safe-area, contrast and progressive-disclosure guidance. Its generated marketing-page structure, neon palette and gaming fonts were not suitable for the requested native minimal app.

Home: mode images, upcoming matches and a compact Demo indicator. No large promotional box or repeated warnings. Match cards are whole-card touch targets with thumbnail, time, availability and a subtle chevron. Details get a dedicated screen, readable rules and one fixed join action. Profile owns the language setting. Wallet keeps inactive actions clearly disabled.

## Image provenance

Original Solo, Duo and Squad illustrations generated with the built-in image-generation tool; no original game's artwork or logos reused. Originals: `assets/solo-original.png`, `assets/duo-original.png`, `assets/squad-original.png`. Optimized bundled 640px JPEGs: `../android/app/src/main/res/drawable-nodpi/mode_solo.jpg`, `mode_duo.jpg`, `mode_squad.jpg`.

Final prompt template (one tool call per mode; counts one/two/four):

Use case: stylized-concept. Asset type: mobile esports tournament mode card illustration. Create a premium editorial 3D game illustration, landscape 4:3 composition. Subject: [one solitary competitor / two teammates / four teammates], seen from behind, wearing contemporary charcoal tactical sportswear with understated sage-green details, standing on a rocky ridge overlooking a lush tropical island tournament arena with low buildings and distant mountains. Original fictional people and setting, no existing game characters. Restrained cinematic composition with ample atmospheric space, soft pale sunlight, desaturated forest green and warm stone palette, sophisticated and calm, clean silhouettes clearly communicating [Solo / Duo / Squad] mode even at thumbnail size. Characters centrally grouped in the lower two-thirds, upper portion misty sky. No text, logos, UI, badges, weapons, flames, neon or watermarks. Cohesive high-quality game key art for a minimal dark mobile app.

## Homepage iteration — 0.3

Mode artwork is now recomposed as true 1:1 images and every mode-card image, match thumbnail and match-details image uses a square frame. The separate homepage hero keeps its original 16:9 composition. New island-arena artwork, a compact notification bell with unread count, a latest-announcement card, and a dedicated notification list extend the existing minimal dark design.

Bundled assets: `../android/app/src/main/res/drawable-nodpi/home_hero.jpg`, `mode_solo.jpg`, `mode_duo.jpg`, `mode_squad.jpg`. Project originals: `assets/home-hero-v0.3-original.png`, `assets/solo-square-v0.3-original.png`, `assets/duo-square-v0.3-original.png`, `assets/squad-square-v0.3-original.png`. All created/edited with the built-in image-generation tool and optimized for bundling.

Hero prompt:
Use case: stylized-concept. Asset type: original hero artwork for a premium minimal Android esports tournament homepage. Wide landscape 16:9 composition. A striking futuristic tropical tournament island seen from above at blue-hour sunset, a circular open-air competitive arena nestled among dramatic emerald cliffs, warm golden rim lighting, thin mist and calm water, elegant monumental architecture, restrained sage green and charcoal with a small warm amber accent, polished high-end cinematic game environment concept art. No people in close-up. Memorable beautiful focal point in the upper right two thirds, deep dark forest foreground and atmospheric negative space on the lower left for separately rendered UI text. This is a new original fictional world, not an existing game map. Clean composition, sophisticated rather than busy, crisp island silhouette that reads at mobile banner size. No text, letters, logos, UI, flags, badges, weapons or watermarks.

Square edit prompt template (one call per mode, referencing that mode’s original):
Recompose this illustration into a true square 1:1 mobile match-mode image. Preserve the original cinematic style, tropical island setting, charcoal-and-sage outfits and original people. Show [one competitor / two teammates / all four teammates] clearly and fully inside the central 75% of the square, seen from behind at a slightly wider distance, with breathing room at the edges. Do not stretch or distort the existing image. Adjust environment framing to make the square feel intentionally composed. Keep the people readable at small square thumbnail sizes. No text, UI, logos or watermarks. Output a square image.

## Native admin — 0.5

Owner explicitly replaced the web approach with native Compose administration. Profile has a shared account sign-in. Only a server-verified admin sees the Admin bottom destination, with native section chips and native form controls. Sign-out/revocation removes the destination and protected composition. No WebView remains. Bundled Hind Siliguri renders admin Bangla. Dark colors, outlined forms, 48dp controls and scrolling match the player application. The extra Admin destination is conditional; the five existing player destinations remain available.

## 0.7 — authenticated player journey

Owner's new brief supersedes 24dp gutters and repeated sample labels: page gutters are 16dp with 20dp section gaps and 12–16dp form/card spacing. Authentication reuses the original island artwork under a dark gradient. Saved profiles own the player identity, and published match records own entry prices and rules. English is default; Bangla uses bundled Hind Siliguri and localized resources. Native sheet motion and 180–220ms fades use Compose's platform motion duration scaling. Tab scroll states are retained during navigation. Local testing appears discreetly in Profile; funding explains fictitious transactions at the point of use.


## Native match operations — 0.10

The new room/result screens retain the established dark/sage system and bundled avatars. The skill's generic neon/3D marketing-page recommendation remains unsuitable for this owner's native minimal direction; relevant Compose state/accessibility guidance was used. New result/room cards use white primary text, muted metadata and sage award emphasis. Controls use native semantics and touch targets; action rows wrap, admin forms scroll as one surface and content width is capped at 640dp. Large-font navigation keeps accessible labelled icons while omitting crowded visual labels. Admin remains entered through Profile, whose navigation item stays selected within administration.

Player room state is explicit (hidden/loading/unavailable/server-confirmed). No cached room password is rendered, and backgrounding clears it. Published-result search states its exact-ID/title-prefix behavior; announced awards are labelled separately from credits. Publication confirmation shows the concrete saved player/result values before committing. Verification images and the scope of device checks are retained under verification/workflow-v0.10.


## Add Money simplification — owner clarification

Only three player inputs: amount already sent (৳), transaction ID, sender mobile number. Put concise send-first instructions below them, alongside a readable receiving number and Copy number action. Keep optional helper text hidden until a field is invalid; use relevant number/phone keyboards and one full-width Submit request button. Explain that balance changes after manual review in one short sentence. The test build retains a compact do-not-send-real-money notice. Native Payment settings edits only the receiving number; staff changes are audited. Current default: 01889898989.

## Admin workflow — 5 November 2026

Match editing uses Details, Schedule, Prizes, Rules and Publication sections with a fixed Save action. Administrative refresh controls register with one bottom-end icon action, outside scrollable content and clear of navigation. Participant cards show private account email and Free Fire UID when available; public participant documents remain game-name-only. Refunds keep the awaiting count, refund action and operational feedback.

Results use a placement selector and numeric kills input, with a highlighted calculated award above Save and credit. That action automatically saves, publishes and credits through the existing retry-safe stages; interrupted publication/credit stays visible and can be retried. Match status remains manual, explained within Results. Prize positions are editable across match states, with audited revisions; changing prizes does not rewrite previously credited awards. Account emails sync from authenticated identity on sign-in and remain staff/owner-private.

## WhatsApp support — 5 November 2026

Profile adds a 56dp-minimum Support · WhatsApp row, available to guests and signed-in players, opening a user-initiated HTTPS WhatsApp chat. Admin Settings → Support → WhatsApp support edits the shared number through an online, audited update. Initial number: 01703883397. Both surfaces have English/Bangla labels and error feedback. This is separate from private in-app support requests and the existing Contact page.

## Prize setup during creation — 5 November 2026

The match creation Prizes tab includes Total prize (taka), Per kill (taka), and sequential placement amounts added with Add prize position. Remove last position reverses an addition. All creation fields save together; placement amounts do not recalculate the independent total of new matches. Existing legacy match totals retain their previous calculation behavior. Native decimal inputs, English/Bangla labels and inline placement validation follow the established minimal form style.

## Deposit verification — 5 November 2026

The Add Money form shows circular feedback while submitting, then a centred Verifying deposit state for up to 30 seconds after server submission. Only server-confirmed approval reports success. Timeout explains that the saved request awaits admin approval and is visible in history. Admin Payment settings offers notification permission, a battery-settings link and background verification restart after owner opt-in. Keep English/Bangla support and visible ongoing service status; never imply guaranteed receipt verification while offline.

## Timed room release — 5 November 2026

Admin room release reuses the match schedule's native calendar and clock dialogs, with a Release · Bangladesh time label. Enabled room credentials unlock for confirmed participants after the saved time even while registration remains open. The visible player card checks the server every ten seconds, clears on background, rechecks on resume and stops checking when explicitly hidden. Server rules retain membership, enabled and active-match restrictions.

## App continuity and winner selection — 6 November 2026

Account-ban checks run without replacing the player screen while connecting. A confirmed active ban still shows its reason and blocks signed-in features. Open player sheets, entered payment/profile details, filters, notices and feed scroll positions restore with Android saved state; reconnecting must not close forms. Room credentials retain their existing background-clear and server-recheck policy.

Per-match Results opens with the saved prize positions in numeric order. Tapping a position opens a full-height, scrollable sheet with a Set winner control and a participant dropdown (game username, Free Fire UID and account UUID), kill count and calculated winnings. Save and credit publishes the selected result and automatically credits its placement prize plus per-kill reward. Match status does not gate this action or change as a side effect. Saved positions show their winner and credit status; an interrupted credit has a retry action. Existing published winners remain immutable and retries cannot pay twice. Other participant results remain available below prize positions for per-kill and whole-match settlement workflows. English and Bangla controls share the existing theme.

## Receipt diagnostics — 7 November 2026

Payment settings keeps automatic receipt verification beside its source permissions and one-line status/last-update display. Scan counts, unmatched reasons and notification results are under View verification details; Copy verification report stays directly available. Enable auto-approval, Restart background verification and optional bKash notification-access controls use the existing native buttons and scrollable layout. Finance → Deposits uses Check deposits now for both supported receipt sources. Never report credit before the server confirms it; unknown receipt formats and failed operations show a reason. The diagnostic export omits private SMS/notification bodies, phone numbers and transaction IDs.

## Repeating schedules — 7 November 2026

New-match creation adds a Scheduling tab: an explicit repeating switch, daily From/Until native time pickers, delay in minutes, and inclusive First/Last native date pickers. The selected category remains in Details. The footer changes to Create schedule while repeating is enabled; single-match creation remains available. Generated matches open automatically and copy the saved template, with fresh participants/rooms/results.

Matches → Schedules shows the local online-admin service status, each saved range/delay, waiting/queued/paused/completed/attention state, next known creation and paged generated-match history. Pause/Resume and confirmed Stop affect future creation. A date/time range always names Bangladesh time. Current result completion means every positive prize-position winner has an immutable prize credit; the delay begins at the latest credit. Future starts are not fabricated before winners are complete. English/Bangla copy, native 48dp controls, saved form state and compact 200% Bangla layout are verified. Offline pauses automatically and reconnecting resumes. A visible ongoing notification explains background scheduling; reopening the app recovers Android stops. Database rollout limits are in MATCH-SCHEDULING.md.

## Daily schedules and wallet entry — 8 November 2026

Scheduling uses minutes between fixed match starts. Saving creates today's remaining slots together; a date range creates each later day's batch on that day in Bangladesh time. Helper text explains the inclusive aligned end time and the online admin requirement. Schedule cards show next daily batch, generation state and scheduled run times. All matches created refers to creation, independent of game completion.

Paid-entry review explains deposit-first charging followed by available winnings. Join eligibility uses both balances, excludes withdrawal reserves and waits for the current wallet to load. Wallet explains that only winnings can be withdrawn. Cancellation refunds restore the original funding sources. English/Bangla copy follows the existing native controls and layout.


## Participant kill rewards — 8 November 2026

Results now separates Winners from Player kills. Winners retains prize-position cards and the confirmed-participant picker; its Save and credit action pays only the placement amount. Player kills lists every confirmed entry, supports game-name/Free Fire UID/account search, direct numeric entry, team-member counts, private server drafts, and a batch review before crediting. The owner's follow-up overrides the earlier proposal: unset or empty counts are zero and are finalized as zero when confirming the batch. Native lists scroll independently of the fixed draft/review actions, and state is saved across match-section changes.

Placement and kills have separate immutable credit keys. Either may be paid first, and the public result aggregates the two components without fabricating a finishing position for non-winners. Existing combined credits remain immutable; a documented supplemental kill credit adds only the unpaid amount. A legacy team total is kept as an aggregate rather than inventing member-level history. New team counts sum into the registering account's reward. No match status changes as a side effect of paying rewards. English and Bangla use existing theme tokens and native accessible controls.

Verification and rollout limitations are recorded in verification/participant-kills/REVIEW.md. No live rules or app publication is part of this change.

## Player match history — 9 November 2026

Public browsing hides a match more than five minutes after its scheduled start. Home remains categories/announcements. Matches → Results defaults to My results for signed-in users: confirmed past participation appears newest first, with Results pending until a public score exists. Publication updates that same match row with the existing placement/kills/reward details. Search covers ID/title; saved history is labelled and errors offer retry. Keep existing My Matches room access and the separate All published view. English/Bangla copy uses the established native cards and semantic theme colors.


## Single-date schedules — 10 November 2026

Scheduling replaces First/Last date with one Match date and a separate Release date, using the existing native calendar dialogs. The selected From/Until times apply only to the match date. Release has no time input: helper text states 12:00 AM Bangladesh time, online-admin requirements and late-start skipping. Release after the match date shows an inline error and blocks saving. Schedule cards show both dates and a midnight release; legacy saved ranges stay readable. English/Bangla strings retain the current charcoal/sage style and saved form state. The UI UX Pro Max Compose searches returned no verified match; its general labelled-form, native control and scrolling guidance was applied.


## Player feedback — 11 November 2026

Upcoming has no public-feed Load more action. Results presents search before scope filters and preserves accessible history when an older match is removed or private. Description leads Overview as a default-expanded section, followed by rules and room access; match ID/status stay muted, and the registered roster belongs under Players. Unreleased room details use neutral waiting copy with a refresh action. Match details and Wallet use fixed bottom-end refresh icons outside scrolling content. Wallet Activity keeps one filter at the far right, moving it below the heading at large font sizes. Refresh preserves the selected activity filter. See PLAYER-FEEDBACK-FIXES.md for verification and database-access limits.
