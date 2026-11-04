import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,limit,writeBatch,serverTimestamp,deleteDoc}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-prizes',firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
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
 if(!options.noHistory)batch.set(doc(db,`matches/m/history/${revision}`),{from:old.status,to:old.status,reason:'',at:serverTimestamp()});
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
test('registered entries and non-registration match states lock prize positions',async()=>{
 await assertSucceeds(edit(1,100));
 for(const patch of [{joined:1},{status:'closed'},{status:'review'},{status:'cancelled'},{status:'settled'}]) {
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m'),patch,{merge:true}));
  await assertFails(edit(1,200));
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
