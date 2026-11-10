import {initializeApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';
import {getMessaging} from 'firebase-admin/messaging';
import {onDocumentWritten} from 'firebase-functions/v2/firestore';
import {dispatchAnnouncement} from './announcement-message.mjs';

initializeApp();
export const announcePublishedNotice = onDocumentWritten({
  document: 'notices/{noticeId}', region: 'asia-southeast1', retry: true, maxInstances: 2
}, async event => {
  // Explicit activation is required; deployment alone does not start sending.
  if (process.env.ANNOUNCEMENT_PUSH_ENABLED !== 'true') return;
  await dispatchAnnouncement(event.params.noticeId, event.data?.before.data(), event.data?.after.data(), {
    readCurrent: async () => (await getFirestore().doc(`notices/${event.params.noticeId}`).get()).data(),
    send: message => getMessaging().send(message)
  });
});
