# Production APK installation and publisher verification

Updated 8 November 2026.

The supplied vivo X200 Pro screenshot says **App registration verification failed** in **vivo Super Care** for player 1.0.6 received through WhatsApp. It also says vivo has not manually tested the app. This screenshot identifies a registration/review block, rather than reporting a named malware detection. It does not establish that the APK is malware-free or that other manufacturers use the same classification.

## What the release verifies

Production builds retain the original package and signing certificate, increment the version, disable debugging and cleartext traffic, and use the existing Firebase production backend. Only the player APK should go to players; keep the staff APK with staff. The APK signature and extracted permissions are recorded with the release. These checks do not substitute for manufacturer/store approval or an antivirus verdict.

## Required publisher action

There is currently no verified developer account. APK code cannot create or claim verified registration on the owner's behalf.

1. Use [vivo's developer portal](https://developer.vivo.com/) and its [account verification guide](https://developer.vivo.com/doc?id=14). Complete the requested owner/business verification privately, using accurate details. The global portal describes enterprise verification, APK submission and app review. If the owner is not eligible for that route, contact vivo developer support for the supported alternative; do not invent a company or filing.
2. Ask vivo which registry the displayed registration check uses for the phone's software region. The precise OriginOS/Funtouch version and device region remain unconfirmed. Do not assume Google verification or a mainland-China filing alone resolves this screenshot.
3. Supply the signed player APK, its package/version and public signing fingerprint, screenshot, accurate publisher/contact details and any required privacy/policy materials. The prepared review ZIP contains the release artifacts and an honest request for instructions; it contains no private signing key, account password or customer records.
4. Complete the required registration/review. If the app is already correctly registered and the block remains, use the warning screen's Appeal route with the matching package/signing identity and registration proof.
5. Retest installation through the actual customer download route on affected physical phones after approval. Approval for one manufacturer does not guarantee every manufacturer's verdict.

The account/identity verification, external review and confirmation on the affected phone are outstanding until completed. This release does not claim that the vivo warning is removed. Renaming the APK, changing the signing key, rebuilding the app or disabling phone protection is not a substitute for registration.

Official references:
- [vivo developer admission and support](https://developer.vivo.com/) — account, enterprise verification, upload and app review; developer support listed as globaldev@vivo.com.
- [Android developer verification](https://developer.android.com/developer-verification) — a separate verified-publisher/package-registration system; do not conflate it with this unconfirmed vivo check.
- [Google's warning guidance](https://developers.google.com/android/play-protect/warning-dev-guidance) — distinguishes unknown-app scan requests, sensitive-permission blocks, harmful-app classifications and their review routes.
