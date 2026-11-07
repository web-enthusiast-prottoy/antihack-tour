import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,limit,writeBatch,serverTimestamp,deleteDoc}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-prizes',firestore:{host:'127.0.0.1',port:Number(process.env.PAID_TEST_PORT??8080),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env.clearFirestore();await env.cleanup();});
const staff=()=>env.authenticatedContext('operator').firestore(),guest=()=>env.unauthenticatedContext().firestore();
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'staffRoles/operator'),{admin:true});
  await setDoc(doc(c.firestore(),'matches/m'),{title:'Sample prizes',description:'Local test',image:'solo',mode:'solo',map:'Bermuda',startsAt:new Date(),capacity:1000,joined:0,entryPaisa:0,prizePaisa:999,perKillPaisa:0,rules:'Sample',cancellationTerms:'Sample',status:'open',revision:2,cancellationReason:'',updatedAt:new Date(),updatedBy:'operator'});
 });
});
async function edit(rank,amount,overrides={},rowOverrides={},options={}) {
 const db=staff(),ref=doc(db,'matches/m'),old=(await getDoc(ref)).data(),row=doc(db,`matches/m/prizes/${rank}`),before=await getDoc(row);
 const revision=old.revision+1,total=(old.prizesEnabled?old.prizePaisa:0)-(before.data()?.amountPaisa??0)+amount;
 const batch=writeBatch(db);
 batch.update(ref,{prizesEnabled:true,prizeRank:rank,prizeMaxRank:Math.max(old.prizeMaxRank??0,rank),prizePaisa:total,revision,updatedAt:serverTimestamp(),updatedBy:'operator',...overrides});
 if(!options.noRow)batch.set(row,{rank,amountPaisa:amount,matchRevision:revision,updatedAt:serverTimestamp(),...rowOverrides});
 if(!options.noHistory)batch.set(doc(db,`matches/m/history/${revision}`),{from:old.status,to:old.status,reason:old.cancellationReason??'',at:serverTimestamp()});
 if(!options.noAudit)batch.set(doc(db,`auditRecords/match_m_${revision}`),{action:'saveMatch',path:'matches/m',actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
test('arbitrary positions update an exact total atomically and are public-safe',async()=>{
 for(const [rank,amount] of [[1,50000],[2,30000],[3,15000],[4,5000],[1000,100]])await assertSucceeds(edit(rank,amount));
 assert.equal((await getDoc(doc(staff(),'matches/m'))).data().prizePaisa,100100);
 const rows=await assertSucceeds(getDocs(query(collection(guest(),'matches/m/prizes'),limit(100))));
 assert.equal(rows.size,5);assert.deepEqual(Object.keys(rows.docs[0].data()).sort(),['amountPaisa','matchRevision','rank','updatedAt']);
 await assertSucceeds(edit(2,25000));await assertSucceeds(edit(4,0));
 assert.equal((await getDoc(doc(staff(),'matches/m'))).data().prizePaisa,90100);
});
test('row, derived total, immutable history and private audit are coupled',async()=>{
 await assertFails(edit(1,100,{prizePaisa:101}));
 for(const options of [{noRow:true},{noHistory:true},{noAudit:true}])await assertFails(edit(1,100,{}, {},options));
 await assertFails(setDoc(doc(staff(),'matches/m/prizes/1'),{rank:1,amountPaisa:100,matchRevision:3,updatedAt:serverTimestamp()}));
 await assertSucceeds(edit(1,100));
 await assertFails(edit(2,100,{prizePaisa:100}));
 await assertFails(deleteDoc(doc(staff(),'matches/m/prizes/1')));
});
test('ranks, integer paisa, record identity and capacity are enforced',async()=>{
 for(const [rank,amount] of [[0,100],[1001,100],[1,-1],[1,1.5],[1,9007199254740992]])await assertFails(edit(rank,amount));
 await assertFails(edit(1,100,{}, {rank:2}));
 await assertSucceeds(edit(1,9007199254740991));await assertFails(edit(2,1));
});
test('prize positions remain editable after registration and across match states',async()=>{
 await assertSucceeds(edit(1,100));
 for(const patch of [{joined:1},{status:'closed'},{status:'review'},{status:'settled'}]) {
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m'),patch,{merge:true}));
  await assertSucceeds(edit(1,200));
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m'),{joined:0,status:'open'},{merge:true}));
 }
});
test('players cannot configure prizes, drafts stay private and list queries remain bounded',async()=>{
 await assertFails(setDoc(doc(guest(),'matches/m/prizes/1'),{rank:1,amountPaisa:100,matchRevision:3,updatedAt:serverTimestamp()}));
 await assertSucceeds(edit(1,100));
 await assertFails(getDocs(collection(guest(),'matches/m/prizes')));
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m'),{status:'draft'},{merge:true}));
 await assertFails(getDoc(doc(guest(),'matches/m/prizes/1')));
 await assertSucceeds(getDoc(doc(staff(),'matches/m/prizes/1')));
});
test('normal match edits preserve derived totals, configured ranks and stale-write protection',async()=>{
 await assertSucceeds(edit(4,500));
 async function patch(values) {
  const db=staff(),ref=doc(db,'matches/m'),old=(await getDoc(ref)).data(),revision=old.revision+1,batch=writeBatch(db);
  batch.update(ref,{revision,updatedAt:serverTimestamp(),updatedBy:'operator',...values});
  batch.set(doc(db,`matches/m/history/${revision}`),{from:old.status,to:old.status,reason:'',at:serverTimestamp()});
  batch.set(doc(db,`auditRecords/match_m_${revision}`),{action:'saveMatch',path:'matches/m',actor:'operator',at:serverTimestamp()});
  return batch.commit();
 }
 for(const values of [{prizePaisa:501},{prizesEnabled:false},{prizeMaxRank:1},{capacity:3}])await assertFails(patch(values));
 await assertSucceeds(patch({title:'Sample revised title'}));
 await assertFails(edit(1,100,{revision:3}));
 assert.equal((await getDoc(doc(staff(),'matches/m'))).data().prizePaisa,500);
});

test('cancelled-match prize edits preserve the cancellation reason and timestamp',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m'),{status:'cancelled',cancellationReason:'Sample cancellation',cancelledAt:new Date(1000)},{merge:true}));
 await assertSucceeds(edit(1,1234));
 const m=(await getDoc(doc(staff(),'matches/m'))).data();assert.equal(m.cancellationReason,'Sample cancellation');assert.equal(m.cancelledAt.toMillis(),1000);
});
test('profile email must match the authenticated account and remains private',async()=>{
 const player=env.authenticatedContext('p',{email:'player@example.test'}).firestore();
 await assertSucceeds(setDoc(doc(player,'users/p'),{ffUsername:'Player',ffUid:'12345',email:'player@example.test'}));
 await assertFails(setDoc(doc(player,'users/p'),{ffUsername:'Player',ffUid:'12345',email:'other@example.test'}));
 await assertSucceeds(getDoc(doc(staff(),'users/p')));await assertFails(getDoc(doc(guest(),'users/p')));
});

test('create a match with an independent total and multiple placement prizes atomically',async()=>{
 const db=staff(),base=(await getDoc(doc(db,'matches/m'))).data();
 async function create(id,extra={}) {
  const batch=writeBatch(db),ref=doc(db,`matches/${id}`);
  batch.set(ref,{...base,status:'draft',revision:1,prizePaisa:123456,perKillPaisa:250,independentPrizeTotal:true,prizesEnabled:true,prizeRank:0,prizeMaxRank:3,updatedAt:serverTimestamp(),...extra});
  for(const [rank,amount] of [[1,5000],[2,2500],[3,1000]])batch.set(doc(db,`matches/${id}/prizes/${rank}`),{rank,amountPaisa:amount,matchRevision:1,updatedAt:serverTimestamp()});
  batch.set(doc(db,`matches/${id}/history/1`),{from:'none',to:'draft',reason:'',at:serverTimestamp()});
  batch.set(doc(db,`auditRecords/match_${id}_1`),{action:'saveMatch',path:`matches/${id}`,actor:'operator',at:serverTimestamp()});
  return batch.commit();
 }
 await assertSucceeds(create('created'));
 assert.equal((await getDoc(doc(db,'matches/created'))).data().prizePaisa,123456);
 assert.equal((await getDocs(query(collection(db,'matches/created/prizes'),limit(20)))).size,3);
 await assertFails(create('bad-capacity',{capacity:2}));
 await assertFails(create('bad-total',{prizePaisa:-1}));
});

test('independent total remains unchanged when adding or editing placement prizes',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m'),{independentPrizeTotal:true},{merge:true}));
 await assertSucceeds(edit(1,500,{prizePaisa:999}));
 await assertSucceeds(edit(2,200,{prizePaisa:999}));
 await assertSucceeds(edit(1,300,{prizePaisa:999}));
 assert.equal((await getDoc(doc(staff(),'matches/m'))).data().prizePaisa,999);
});

test('new open matches publish their initial prizes atomically with audit and history',async()=>{
 const db=staff(),id='open-on-create',batch=writeBatch(db),stamp=serverTimestamp();
 batch.set(doc(db,`matches/${id}`),{title:'Public new match',description:'Local only',image:'solo',mode:'solo',map:'Bermuda',startsAt:new Date(),capacity:48,joined:0,entryPaisa:0,prizePaisa:10000,perKillPaisa:0,rules:'Sample',cancellationTerms:'Sample',status:'open',revision:1,cancellationReason:'',independentPrizeTotal:true,prizesEnabled:true,prizeRank:0,prizeMaxRank:1,updatedAt:stamp,updatedBy:'operator'});
 batch.set(doc(db,`matches/${id}/prizes/1`),{rank:1,amountPaisa:5000,matchRevision:1,updatedAt:stamp});
 batch.set(doc(db,`matches/${id}/history/1`),{from:'none',to:'open',reason:'',at:stamp});
 batch.set(doc(db,`auditRecords/match_${id}_1`),{action:'saveMatch',path:`matches/${id}`,actor:'operator',at:stamp});
 await assertSucceeds(batch.commit());
 assert.equal((await getDoc(doc(guest(),`matches/${id}`))).data().status,'open');
 assert.equal((await getDoc(doc(guest(),`matches/${id}/prizes/1`))).data().amountPaisa,5000);
});
