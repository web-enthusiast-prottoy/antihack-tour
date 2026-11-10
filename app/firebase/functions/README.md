# Optional instant push sender — inactive

The released player app now uses native background checks and foreground announcement updates without deploying this sender or enabling billing. It checks published revisions about every 15 minutes when Android permits. Notifications require the updated app to be opened online once and notification permission. The first check establishes a server timestamp baseline without alerting for historical announcements. Failed/interrupted checks retain their cursor, and displayed revisions are deduplicated. The background approach uses existing Firestore reads and its quotas.

The sender below remains an optional future instant-delivery path; it is not required for current notifications.

Publishes and later revisions of published `notices/{id}` broadcast public title/body to the `announcements` FCM topic. Draft/hidden/deleted announcements do not send alerts. Updated player installs subscribe without signing in; the admin app does not subscribe.

No service, billing or credentials were enabled during implementation. The default Firebase configuration deliberately does not include Functions. The optional `../push.firebase.json` is used only for a separately authorized deployment.

Before activation:

1. Obtain owner approval for trusted sender hosting. This implementation targets Cloud Functions for Firebase (requires Blaze billing). FCM itself is free. If billing remains prohibited, use an approved trusted environment instead; instant automatic sending is not active on the current Spark-only setup.
2. Install the dependencies in this directory using `npm ci`. Run `npm test`.
3. Use the same Firebase project as the player/admin app. Enable its FCM HTTP v1 API and grant the runtime service account Firebase Cloud Messaging send permission. Use the platform's runtime identity; never download a service-account key into an APK or source control.
4. Set `ANNOUNCEMENT_PUSH_ENABLED=true` in the deployment environment (Firebase Functions supports an untracked `.env.<project-id>` file). With this unset, the sender returns without sending.
5. After explicit deployment authorization, deploy only `functions:announcements` using `firebase deploy --config firebase/push.firebase.json --project <project-id> --only functions:announcements` from the app root. No deploy was run for this change.
6. Distribute the updated player app. Open it once with internet and allow notifications. Older installed APKs have no FCM subscription and cannot receive these alerts. Google Play services, connectivity, OS permissions and battery restrictions affect delivery.
7. Publish a sample announcement, edit it while still published, hide it, and republish it. Test foreground/background, terminated app (not force-stopped), offline/reconnect, denied permission, token renewal and notification tap. Tap opens the existing Notifications inbox. Force-stopped apps must be opened again.

Events can be retried or reordered. The sender rereads the current document and skips hidden/superseded revisions. Data messages carry an announcement ID/revision; Android persists the highest displayed revision so duplicate or older deliveries do not alert again. Offline FCM may collapse revisions to the latest per announcement. FCM is best effort, not guaranteed delivery of every intermediate edit. The existing inbox remains the source for current published content. A server send that races a hide can still arrive after hiding.

The client collects no user device token into Firestore; subscription is installation-wide and includes guests. Messages contain public announcement content only. The notification channel and Android permission can be changed in Notifications → Notification settings.

Local verification: `node --test announcement-message.test.mjs` validates payload decisions and sender failures without contacting FCM. Emulator security tests verify notice publisher access separately. Actual push delivery requires the activation/device checks above.

References: https://firebase.google.com/docs/cloud-messaging/android/get-started, https://firebase.google.com/docs/cloud-messaging/manage-topic-subscriptions, https://firebase.google.com/docs/functions/firestore-events, https://firebase.google.com/docs/functions/get-started.
