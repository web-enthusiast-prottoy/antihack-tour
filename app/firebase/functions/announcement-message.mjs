// Announcements are public content. Never include staff IDs or device tokens.
export function announcementMessage(id, before, after) {
  if (!after?.published || after.revision === before?.revision) return null;
  if (!Number.isSafeInteger(after.revision) || after.revision < 1 ||
      typeof after.title !== 'string' || !after.title.trim() ||
      typeof after.body !== 'string' || !after.body.trim()) return null;
  return {
    topic: 'announcements',
    data: {type: 'announcement', noticeId: id, revision: String(after.revision), title: after.title, body: after.body},
    android: {priority: 'high', ttl: 24 * 60 * 60 * 1000, collapseKey: `announcement_${id}`}
  };
}

export async function dispatchAnnouncement(id, before, after, {readCurrent, send}) {
  const message = announcementMessage(id, before, after);
  if (!message) return false;
  // Firestore events can arrive late or out of order. Hidden/superseded content is skipped.
  const current = await readCurrent();
  if (!current?.published || current.revision !== after.revision) return false;
  await send(message);
  return true;
}
