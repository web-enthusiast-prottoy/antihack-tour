# Player information pages

Open Profile → Administration → Player pages. Choose Add new and enter one of these page IDs:

| ID | Player destination | Operator supplies |
| --- | --- | --- |
| rules | Rules | Approved competition, entry, conduct and cancellation rules |
| help | Help and contact | Support contact, instructions and actual response expectations |
| about | About and developer info | App/operator/developer identity and relevant contact information |
| privacy | Privacy policy | Approved data collection, use, access, retention and contact policy |

Enter a title and body. The optional contact/information link must use HTTPS and contain no embedded login details. Players see the link's host and can explicitly open it in their browser. Put no credentials, private player information, PINs or OTPs in any page. The content is rendered as plain selectable text, not HTML. Write in the language appropriate to your audience; the app translates navigation/state labels but does not translate staff-authored content.

Enable Published / active and save. Each save atomically writes the next page revision and a private audit record. Use Edit on an existing page to change it or turn publication off. Disabled pages cannot be read by regular players. Page deletion is unavailable. Staff configuration remains separate and private.

Players open the four pages from Profile. Reading starts with a server request. A network failure may show a saved published copy with an outdated/offline warning; an access-denied response never substitutes old cached content. If no cached copy exists, reconnect. Refresh requests the latest server copy. An offline device may retain an earlier public copy; unpublishing does not erase information already disclosed.

No actual operator policies or contact details were supplied during implementation, so none are seeded or invented. Publish approved content and verify each page and contact route before release. This is a content-management workflow, not legal advice or a complete public-launch policy review. Private text support requests are available separately from Profile → Support requests; see SUPPORT-WORKFLOW.md for staff review and remaining limits.

Local testing uses Firebase emulators. These security rules have not been deployed to production by this increment. See verification/player-pages/REVIEW.md for checks and remaining device acceptance.
