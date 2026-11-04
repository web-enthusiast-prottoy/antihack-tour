import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {operate} from '../scripts/operations.mjs';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {getFirestore}=require('firebase-admin/firestore');
const {getApps,deleteApp}=require('firebase-admin/app');
const db=getFirestore();
let testPrefix='';const extraPrefixes=[];
after(async()=>{
 if(testPrefix) for(const collection of await db.listCollections()) for(const d of (await collection.get()).docs) {
  if([d.id,d.get('uid'),d.get('actor'),d.get('request')].some(v=>typeof v==='string' && [testPrefix,...extraPrefixes].some(prefix=>v.toLowerCase().startsWith(prefix.toLowerCase())))) await d.ref.delete();
 }
 await Promise.all(getApps().map(deleteApp));
});
test('atomic finance and capacity, retries, and unauthorized calls',async()=>{
 const prefix='test'+Date.now(); testPrefix=prefix; const uid=prefix+'player',admin=prefix+'admin',m=prefix+'match';
 await db.doc(`staffRoles/${admin}`).set({admin:true});
 await db.doc(`users/${uid}`).set({displayName:'Player',ffUsername:'Player',ffUid:'',avatar:'solo'});
 await db.doc(`matches/${m}`).set({status:'open',capacity:1,entryPaisa:5000,joined:0});
 await operate(uid,'deposit',{sender:'01700000000',amount:10000,reference:prefix});
 assert.equal((await db.doc(`wallets/${uid}`).get()).exists,false);
 await assert.rejects(operate(uid,'approveDeposit',{receiptConfirmed:true,verifiedAmount:10000,verifiedReference:prefix,verifiedSender:'01700000000',verifiedReceiver:'01889898989',id:uid+'_'+prefix.toUpperCase()}));
 await Promise.all([1,2,3].map(()=>operate(admin,'approveDeposit',{receiptConfirmed:true,verifiedAmount:10000,verifiedReference:prefix,verifiedSender:'01700000000',verifiedReceiver:'01889898989',id:uid+'_'+prefix.toUpperCase()})));
 assert.equal((await db.doc(`wallets/${uid}`).get()).get('balance'),10000);
 await Promise.all([1,2].map(()=>operate(uid,'join',{match:m})));
 assert.equal((await db.doc(`entries/${uid}_${m}`).get()).get('status'),'requested');
 assert.equal((await db.doc(`matches/${m}`).get()).get('joined'),0);
 await Promise.all([1,2,3].map(()=>operate(admin,'confirmEntry',{id:uid+'_'+m})));
 assert.equal((await db.doc(`wallets/${uid}`).get()).get('balance'),5000);
 assert.equal((await db.doc(`matches/${m}`).get()).get('joined'),1);
 const published=await db.collection(`matches/${m}/participants`).get();assert.equal(published.size,1);assert.deepEqual(Object.keys(published.docs[0].data()).sort(),['avatar','roster','username']);
 await operate(uid,'deposit',{sender:'01700000000',amount:10000,reference:prefix+'x'});
 await operate(uid+'other','deposit',{sender:'01700000000',amount:10000,reference:prefix+'x'});
 await operate(admin,'approveDeposit',{receiptConfirmed:true,verifiedAmount:10000,verifiedReference:prefix+'x',verifiedSender:'01700000000',verifiedReceiver:'01889898989',id:uid+'_'+(prefix+'x').toUpperCase()});
 await assert.rejects(operate(admin,'approveDeposit',{receiptConfirmed:true,verifiedAmount:10000,verifiedReference:prefix+'x',verifiedSender:'01700000000',verifiedReceiver:'01889898989',id:uid+'other_'+(prefix+'x').toUpperCase()}));
 const a=prefix+'a',b=prefix+'b',free=prefix+'free';
 for(const p of [a,b]) await db.doc(`users/${p}`).set({ffUsername:p});
 await db.doc(`matches/${free}`).set({status:'open',capacity:1,entryPaisa:0,joined:0});
 const results=await Promise.allSettled([a,b].map(p=>operate(p,'join',{match:free})));
 assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
 assert.equal((await db.doc(`matches/${free}`).get()).get('joined'),1);
 const paid=prefix+'paid';
 await db.doc(`matches/${paid}`).set({status:'open',capacity:1,entryPaisa:1000,joined:0});
 await assert.rejects(operate(prefix+'missing','join',{match:paid}),/username/);
 await assert.rejects(operate(a,'join',{match:paid}),/balance/);
 for(const p of [a,b]) await db.doc(`wallets/${p}`).set({balance:1000});
 await Promise.all([a,b].map(p=>operate(p,'join',{match:paid})));
 const confirmations=await Promise.allSettled([a,b].map(p=>operate(admin,'confirmEntry',{id:p+'_'+paid})));
 assert.equal(confirmations.filter(x=>x.status==='fulfilled').length,1);
 const balances=await Promise.all([a,b].map(async p=>(await db.doc(`wallets/${p}`).get()).get('balance')));
 assert.equal(balances.reduce((a,b)=>a+b,0),1000);
 assert.equal((await db.doc(`matches/${paid}`).get()).get('joined'),1);
 await db.doc(`staffRoles/${admin}`).set({admin:false});
 await assert.rejects(operate(admin,'confirmEntry',{id:a+'_'+paid}),/access/);

});

test('paid team entries validate roster at request and confirmation',async()=>{
 const prefix='roster'+Date.now(),uid=prefix+'player',admin=prefix+'admin',match=prefix+'match';
 try {
  await db.doc(`staffRoles/${admin}`).set({admin:true});
  await db.doc(`users/${uid}`).set({ffUsername:'Captain'});
  await db.doc(`wallets/${uid}`).set({balance:500});
  await db.doc(`matches/${match}`).set({mode:'duo',status:'open',capacity:2,joined:0,entryPaisa:100});
  for(const roster of [undefined,['Captain'],['Captain','CAPTAIN'],['Captain',' Friend']])await assert.rejects(operate(uid,'join',{match,roster}),/roster/);
  await operate(uid,'join',{match,roster:['Captain','Friend']});
  await db.doc(`matches/${match}`).update({mode:'squad'});
  await assert.rejects(operate(admin,'confirmEntry',{id:uid+'_'+match}),/roster/);
  assert.equal((await db.doc(`wallets/${uid}`).get()).get('balance'),500);
  await db.doc(`matches/${match}`).update({mode:'duo'});
  await operate(admin,'confirmEntry',{id:uid+'_'+match});
  assert.deepEqual((await db.doc(`entries/${uid}_${match}`).get()).get('roster'),['Captain','Friend']);
  assert.equal((await db.doc(`wallets/${uid}`).get()).get('balance'),400);
 }finally{
  for(const collection of await db.listCollections())for(const doc of (await collection.get()).docs)if([doc.id,doc.get('uid'),doc.get('actor')].some(v=>typeof v==='string'&&v.startsWith(prefix)))await doc.ref.delete();
 }
});

test('deposit corrections, rejection, verified receipt matching and decision races are repeat-safe',async()=>{
 const prefix='deposit'+Date.now();extraPrefixes.push(prefix);const admin=prefix+'admin',uid=prefix+'player',fraud=prefix+'other',reference=prefix.toUpperCase();
 await db.doc(`staffRoles/${admin}`).set({admin:true});
 const claim={amount:2500,sender:'01700000000',reference};
 const request=uid+'_'+reference;
 const verified={id:request,receiptConfirmed:true,verifiedAmount:2500,verifiedReference:reference,verifiedSender:claim.sender,verifiedReceiver:'01889898989'};
 await assert.rejects(operate(uid,'deposit',{...claim,sender:'invalid'}),/sending number/);
 await operate(uid,'deposit',claim);await operate(uid,'deposit',claim);
 await assert.rejects(operate(uid,'deposit',{...claim,amount:2600}),/different details/);
 for(const changed of [{verifiedAmount:2600},{verifiedReference:'WRONGREF'},{verifiedSender:'01800000000'},{verifiedReceiver:'WRONG'},{receiptConfirmed:false}])await assert.rejects(operate(admin,'approveDeposit',{...verified,...changed}),/differ/);
 assert.equal((await db.doc(`wallets/${uid}`).get()).exists,false);
 await assert.rejects(operate(uid,'rejectDeposit',{id:request,reason:'Forged'}),/access/);
 await assert.rejects(operate(admin,'rejectDeposit',{id:request,reason:' '}),/reason/);
 await operate(admin,'rejectDeposit',{id:request,reason:'Amount does not match test receipt.'});
 await operate(admin,'rejectDeposit',{id:request,reason:'Changed reason must not overwrite.'});
 const rejected=await db.doc(`depositRequests/${request}`).get();assert.equal(rejected.get('decisionReason'),'Amount does not match test receipt.');assert.ok(rejected.get('reviewedAt'));assert.equal(rejected.get('reviewedBy'),admin);
 assert.equal((await db.doc(`paymentReferences/demo-bkash_01889898989_${reference}`).get()).exists,false);
 await assert.rejects(operate(admin,'approveDeposit',verified),/no longer pending/);
 const corrected=reference+'-CORRECTED';await operate(uid,'deposit',{...claim,requestId:corrected});
 const correctId=uid+'_'+corrected;
 await Promise.all([1,2,3].map(()=>operate(admin,'approveDeposit',{...verified,id:correctId})));
 assert.equal((await db.doc(`wallets/${uid}`).get()).get('balance'),2500);
 const ledger=await db.doc(`ledger/deposit_${correctId}`).get();assert.equal(ledger.get('request'),correctId);assert.equal(ledger.get('reference'),reference);assert.equal(ledger.get('type'),'deposit');assert.equal(ledger.get('status'),'completed');assert.equal(ledger.get('balanceAfter'),2500);
 await operate(fraud,'deposit',claim);await assert.rejects(operate(admin,'approveDeposit',{...verified,id:fraud+'_'+reference}),/already been credited/);
 const legacyReference=reference+'LEGACY';await db.doc(`paymentReferences/${legacyReference}`).set({request:uid+'_OLD'});await operate(uid,'deposit',{...claim,reference:legacyReference});
 await assert.rejects(operate(admin,'approveDeposit',{...verified,id:uid+'_'+legacyReference,verifiedReference:legacyReference}),/already been credited/);
 assert.equal((await db.doc(`wallets/${uid}`).get()).get('balance'),2500);
 const raceReference=reference+'RACE',raceId=uid+'_'+raceReference;await operate(uid,'deposit',{...claim,reference:raceReference});
 const race=await Promise.allSettled([operate(admin,'approveDeposit',{...verified,id:raceId,verifiedReference:raceReference}),operate(admin,'rejectDeposit',{id:raceId,reason:'Review rejected this claim.'})]);
 assert.equal(race.filter(r=>r.status==='fulfilled').length,1);
 const final=(await db.doc(`depositRequests/${raceId}`).get()).get('status');assert.ok(['approved','rejected'].includes(final));assert.equal((await db.doc(`wallets/${uid}`).get()).get('balance'),final==='approved'?5000:2500);
});
test('deposit credit refuses unsafe balance overflow',async()=>{
 const prefix='overflow'+Date.now();extraPrefixes.push(prefix);const admin=prefix+'admin',uid=prefix+'player',reference=prefix.toUpperCase();
 await db.doc(`staffRoles/${admin}`).set({admin:true});await db.doc(`wallets/${uid}`).set({balance:Number.MAX_SAFE_INTEGER});await operate(uid,'deposit',{amount:1,sender:'01700000000',reference});
 await assert.rejects(operate(admin,'approveDeposit',{id:uid+'_'+reference,receiptConfirmed:true,verifiedAmount:1,verifiedReference:reference,verifiedSender:'01700000000',verifiedReceiver:'01889898989'}),/limit/);
 assert.equal((await db.doc(`depositRequests/${uid}_${reference}`).get()).get('status'),'pending');
});

test('deposit records the displayed receiving number and preserves retry after an admin change',async()=>{
 const prefix='recipient'+Date.now();extraPrefixes.push(prefix);const uid=prefix+'player';
 const claim={amount:100,sender:'01700000000',reference:prefix,receiver:'01889898989'};
 try {
  await operate(uid,'deposit',claim);
  await db.doc('paymentInstructions/bkash').set({number:'01700000000',revision:1});
  await operate(uid,'deposit',claim);
  assert.equal((await db.doc(`depositRequests/${uid}_${prefix.toUpperCase()}`).get()).get('receiver'),'01889898989');
  await assert.rejects(operate(uid,'deposit',{...claim,requestId:prefix+'new'}),/receiving number changed/);
  await operate(uid,'deposit',{...claim,receiver:'01700000000',requestId:prefix+'new'});
  assert.equal((await db.doc(`depositRequests/${uid}_${(prefix+'new').toUpperCase()}`).get()).get('receiver'),'01700000000');
 }finally{await db.doc('paymentInstructions/bkash').delete();}
});
