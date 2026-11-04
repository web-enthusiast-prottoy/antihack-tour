import { createRequire } from 'node:module';
const require = createRequire(new URL('../../admin/package.json', import.meta.url));
const auth = require('firebase-tools/lib/auth');
const { Client } = require('firebase-tools/lib/apiv2');

const account = auth.getGlobalDefaultAccount();
if (!account) throw Error('Firebase owner sign-in required');
auth.setActiveAccount({}, account);

const project = 'arena-ff-34506';
const adminUid = 'j06hQrhBeBUAz0caXs9usCBUykQ2';
const client = new Client({ urlPrefix: 'https://firestore.googleapis.com', apiVersion: 'v1' });
const base = `projects/${project}/databases/(default)/documents`;

const now = Date.now();
const toIso = (ms) => new Date(ms).toISOString();

// 1. Categories
const categories = [
  {
    id: 'cat-full-map',
    name: 'Full Map',
    order: 0,
    active: true,
  },
  {
    id: 'cat-clash-squad',
    name: 'Clash Squad',
    order: 1,
    active: true,
  },
  {
    id: 'cat-duo-battle',
    name: 'Duo Battle',
    order: 2,
    active: true,
  },
];

for (const cat of categories) {
  const docPath = `${base}/categories/${cat.id}`;
  await client.patch(docPath, {
    fields: {
      name: { stringValue: cat.name },
      order: { integerValue: String(cat.order) },
      active: { booleanValue: cat.active },
      updatedBy: { stringValue: adminUid },
      updatedAt: { timestampValue: new Date().toISOString() },
    },
  });
  console.log(`Category seeded: ${cat.id} (${cat.name})`);
}

// 2. Matches
const matches = [
  {
    id: 'match-solo-bermuda',
    title: 'Island Solo Battle · Bermuda',
    categoryId: 'cat-full-map',
    mode: 'solo',
    image: 'solo',
    map: 'Bermuda',
    description: 'Free Fire Solo tournament on Bermuda map. Survival of the fittest.',
    rules: 'No teaming, no hacks, mobile players only. Lobby credentials shared before match.',
    cancellationTerms: 'Free cancellation up to 1 hour before start.',
    startsAt: toIso(now + 24 * 60 * 60 * 1000), // +24h
    capacity: 48,
    joined: 12,
    entryPaisa: 0,
    prizePaisa: 100000, // 1000 BDT
    perKillPaisa: 500,  // 5 BDT
    status: 'open',
    revision: 1,
  },
  {
    id: 'match-squad-clash',
    title: 'Clash Squad 4v4 Showdown',
    categoryId: 'cat-clash-squad',
    mode: 'squad',
    image: 'squad',
    map: 'Bermuda',
    description: 'Intense 4v4 Clash Squad battle. Bring your best team.',
    rules: 'Standard competitive Clash Squad settings. 7 rounds to win.',
    cancellationTerms: 'Refund credited if cancelled before match starts.',
    startsAt: toIso(now + 48 * 60 * 60 * 1000), // +48h
    capacity: 16,
    joined: 8,
    entryPaisa: 5000,   // 50 BDT
    prizePaisa: 200000, // 2000 BDT
    perKillPaisa: 0,
    status: 'open',
    revision: 1,
  },
  {
    id: 'match-duo-purgatory',
    title: 'Duo Survival · Purgatory',
    categoryId: 'cat-duo-battle',
    mode: 'duo',
    image: 'duo',
    map: 'Purgatory',
    description: 'Duo battle royale on Purgatory. Partner up and claim victory.',
    rules: 'Both players must be in the lobby 10 minutes before start time.',
    cancellationTerms: 'Standard refund terms apply.',
    startsAt: toIso(now + 36 * 60 * 60 * 1000), // +36h
    capacity: 24,
    joined: 6,
    entryPaisa: 2000,  // 20 BDT
    prizePaisa: 80000, // 800 BDT
    perKillPaisa: 200, // 2 BDT
    status: 'open',
    revision: 1,
  },
  {
    id: 'match-clash-kalahari',
    title: 'Desert Rumble · Kalahari',
    categoryId: 'cat-clash-squad',
    mode: 'squad',
    image: 'squad',
    map: 'Kalahari',
    description: 'High-stakes 4v4 clash in the desert of Kalahari.',
    rules: 'Mobile devices only. All gun skins allowed.',
    cancellationTerms: 'Standard refund terms apply.',
    startsAt: toIso(now + 72 * 60 * 60 * 1000), // +72h
    capacity: 12,
    joined: 4,
    entryPaisa: 3000,   // 30 BDT
    prizePaisa: 120000, // 1200 BDT
    perKillPaisa: 0,
    status: 'open',
    revision: 1,
  },
];

for (const m of matches) {
  const docPath = `${base}/matches/${m.id}`;
  await client.patch(docPath, {
    fields: {
      title: { stringValue: m.title },
      categoryId: { stringValue: m.categoryId },
      mode: { stringValue: m.mode },
      image: { stringValue: m.image },
      map: { stringValue: m.map },
      description: { stringValue: m.description },
      rules: { stringValue: m.rules },
      cancellationTerms: { stringValue: m.cancellationTerms },
      startsAt: { timestampValue: m.startsAt },
      capacity: { integerValue: String(m.capacity) },
      joined: { integerValue: String(m.joined) },
      entryPaisa: { integerValue: String(m.entryPaisa) },
      prizePaisa: { integerValue: String(m.prizePaisa) },
      perKillPaisa: { integerValue: String(m.perKillPaisa) },
      status: { stringValue: m.status },
      revision: { integerValue: String(m.revision) },
      cancellationReason: { stringValue: '' },
      updatedBy: { stringValue: adminUid },
      updatedAt: { timestampValue: new Date().toISOString() },
    },
  });

  // History subcollection
  await client.patch(`${docPath}/history/1`, {
    fields: {
      from: { stringValue: 'none' },
      to: { stringValue: 'open' },
      reason: { stringValue: '' },
      at: { timestampValue: new Date().toISOString() },
    },
  });

  console.log(`Match seeded: ${m.id} (${m.title})`);
}

// 3. Welcome notice
await client.patch(`${base}/notices/welcome`, {
  fields: {
    title: { stringValue: 'Welcome to Antihack Tour' },
    body: { stringValue: 'Tournaments are now live! Browse open matches in Full Map, Clash Squad, and Duo Battle, register your squad, and get ready for battle.' },
    published: { booleanValue: true },
    revision: { integerValue: '1' },
    createdBy: { stringValue: adminUid },
    updatedBy: { stringValue: adminUid },
    createdAt: { timestampValue: new Date().toISOString() },
    updatedAt: { timestampValue: new Date().toISOString() },
  },
});
console.log('Notice seeded: notices/welcome');

// 4. Payment instructions
await client.patch(`${base}/paymentInstructions/bkash`, {
  fields: {
    number: { stringValue: '01889898989' },
    updatedBy: { stringValue: adminUid },
    updatedAt: { timestampValue: new Date().toISOString() },
  },
});
console.log('Payment instructions seeded: paymentInstructions/bkash');

console.log('Seeding live database arena-ff-34506 complete.');
