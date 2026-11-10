import {test} from 'node:test';
import assert from 'node:assert/strict';
import {announcementMessage, dispatchAnnouncement} from './announcement-message.mjs';
const notice = {published: true, revision: 1, title: 'Sample update', body: 'Local sample announcement'};
test('published creates, revisions and republication notify every subscribed installation', () => {
  const message = announcementMessage('sample', undefined, notice);
  assert.equal(message.topic, 'announcements');
  assert.deepEqual(message.data, {type: 'announcement', noticeId: 'sample', revision: '1', title: notice.title, body: notice.body});
  assert.equal(message.android.priority, 'high');
  assert.ok(announcementMessage('sample', notice, {...notice, revision: 2}));
  assert.ok(announcementMessage('sample', {...notice, published: false}, {...notice, revision: 2}));
});
test('drafts, hiding, deletes, unchanged and malformed revisions send nothing', () => {
  for (const after of [undefined, {...notice, published: false}, {...notice, revision: 0}, {...notice, title: ''}])
    assert.equal(announcementMessage('sample', undefined, after), null);
  assert.equal(announcementMessage('sample', notice, notice), null);
});
test('late events cannot broadcast hidden or superseded content', async () => {
  for (const current of [undefined, {...notice, published: false}, {...notice, revision: 2}]) {
    assert.equal(await dispatchAnnouncement('sample', undefined, notice, {
      readCurrent: async () => current, send: async () => assert.fail('stale send')
    }), false);
  }
});
test('sender errors propagate for retry; repeated events carry the same client deduplication key', async () => {
  const sent = [];
  const deps = {readCurrent: async () => notice, send: async message => sent.push(message)};
  await dispatchAnnouncement('sample', undefined, notice, deps);
  await dispatchAnnouncement('sample', undefined, notice, deps);
  assert.deepEqual(sent[0], sent[1]);
  await assert.rejects(dispatchAnnouncement('sample', undefined, notice, {...deps, send: async () => {throw Error('offline');}}), /offline/);
});
