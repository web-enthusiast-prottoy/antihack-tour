import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,setDoc,updateDoc,writeBatch,runTransaction,serverTimestamp}=require('firebase/firestore');
let env;const db=(uid='player')=>env.authenticatedContext(uid).firestore();
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-paid',firestore:{host:'127.0.0.1',port:Number(process.env.PAID_TEST_PORT??8080),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env.clearFirestore();await env.cleanup();});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 const d=c.firestore(),b=writeBatch(d);
 b.set(doc(d,'staffRoles/operator'),{admin:true});b.set(doc(d,'staffRoles/other'),{admin:true});
 b.set(doc(d,'matches/m'),{status:'open',mode:'solo',entryPaisa:1000,joined:0,capacity:1});
 for(const uid of ['player','second']){b.set(doc(d,'users/'+uid),{ffUsername:uid,avatar:'solo'});b.set(doc(d,'wallets/'+uid),{balance:3000,winnings:500,reservedWinnings:100});}
 await b.commit();
});});
// Historical pending entries remain test fixtures for staff decisions/refunds.
async function request(uid='player',patch={}){return env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),`entries/${uid}_m`),{uid,match:'m',username:uid,roster:[uid],status:'requested',fee:1000,createdAt:serverTimestamp(),...patch}));}
async function decide(uid='player',target='confirmed',options={}){
 const actor=options.actor??'operator',d=db(actor),id=uid+'_m',ref=doc(d,'entries/'+id);
 return runTransaction(d,async tx=>{
  const e=(await tx.get(ref)).data();if(e.status===target)return;
  const walletRef=doc(d,'wallets/'+uid),w=(await tx.get(walletRef)).data(),matchRef=doc(d,'matches/m'),m=(await tx.get(matchRef)).data();
  const refund=target==='refunded',reject=target==='rejected',action=refund?'refundEntry':reject?'rejectEntry':'confirmEntry';
  const original=refund?(await tx.get(doc(d,'walletLedger/entry_'+id))).data():null;
  const deposit=refund?(original?.depositPortion??1000):Math.min(w.balance,1000),winnings=1000-deposit;
  const patch={status:target,reviewedBy:actor,reviewedAt:serverTimestamp(),...options.patch};
  if(reject)patch.decisionReason=options.reason??'Local sample rejection.';
  else if(!refund){patch.publicId=(uid==='player'?'A':'B').repeat(20);patch.avatar='solo';}
  if(options.omit!=='entry')tx.update(ref,patch);
  if(!reject){
   if(options.omit!=='wallet')tx.update(walletRef,{balance:w.balance+(refund?deposit:-deposit),winnings:w.winnings+(refund?winnings:-winnings),[refund?'lastRefund':'lastEntry']:id,...options.wallet});
   if(!refund){
    if(options.omit!=='seat')tx.update(matchRef,{joined:m.joined+1,lastEntry:id});
    if(options.omit!=='public'){
     tx.set(doc(d,'participantLinks/'+patch.publicId),{entry:id});
     tx.set(doc(d,'matches/m/participants/'+patch.publicId),{username:uid,roster:[uid],avatar:'solo'});
    }
   }
   if(options.omit!=='ledger')tx.set(doc(d,`walletLedger/${refund?'refund':'entry'}_${id}`),{uid,delta:refund?1000:-1000,type:refund?'refund':'entry_fee',status:'completed',currency:'BDT',entry:id,match:'m',reference:'m',balanceAfter:w.balance+(refund?deposit:-deposit),depositPortion:deposit,winningsPortion:winnings,winningsAfter:w.winnings+(refund?winnings:-winnings),at:serverTimestamp(),...options.ledger});
  }
  if(options.omit!=='audit')tx.set(doc(d,`auditRecords/${action}_${id}`),{action,path:'entries/'+id,actor,at:serverTimestamp()});
 });
}
const wallet=async(uid='player')=>(await getDoc(doc(db(uid),'wallets/'+uid))).data();
test('paid requests hold no seat or money; staff confirmation charges deposits once',async()=>{
 await assertSucceeds(request());assert.equal((await wallet()).balance,3000);
 await assertSucceeds(decide());await assertSucceeds(decide());
 assert.deepEqual(await wallet(),{balance:2000,winnings:500,reservedWinnings:100,lastEntry:'player_m'});
 assert.equal((await getDoc(doc(db(),'matches/m'))).data().joined,1);
});
test('partial confirmations and forged charge/ledger/roster/state are denied',async()=>{
 await request();
 for(const omit of ['entry','wallet','seat','public','ledger','audit'])await assertFails(decide('player','confirmed',{omit}));
 for(const options of [{wallet:{balance:2999}},{wallet:{winnings:0}},{ledger:{delta:1}},{patch:{fee:1}},{patch:{roster:['other']}}])await assertFails(decide('player','confirmed',options));
 assert.equal((await wallet()).balance,3000);
});
test('new pending requests are denied; legacy decisions remain staff-only',async()=>{
 const d=db();await assertFails(setDoc(doc(d,'entries/player_m'),{uid:'player',match:'m',username:'player',roster:['player'],status:'requested',fee:1000,createdAt:serverTimestamp()}));
 await request();await assertFails(decide('player','confirmed',{actor:'player'}));
});
test('rejection is audited, reasoned and leaves funds unchanged',async()=>{
 await request();await assertFails(decide('player','rejected',{reason:' '}));await assertFails(decide('player','rejected',{omit:'audit'}));
 await assertSucceeds(decide('player','rejected'));assert.equal((await wallet()).balance,3000);await assertFails(decide());
});
test('cancelled paid entries refund the original deposit fee once with complete history',async()=>{
 await request();await decide();await assertFails(decide('player','refunded'));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'matches/m'),{status:'cancelled'}));
 for(const omit of ['entry','wallet','ledger','audit'])await assertFails(decide('player','refunded',{omit}));
 await assertSucceeds(decide('player','refunded'));await assertSucceeds(decide('player','refunded'));
 assert.deepEqual(await wallet(),{balance:3000,winnings:500,reservedWinnings:100,lastEntry:'player_m',lastRefund:'player_m'});
 await assertFails(decide());
});
async function retry(f){for(let n=0;;n++)try{return await f();}catch(e){if(n===2||e.code!=='permission-denied')throw e;}}
test('competing confirmations cannot overfill the match and racing refunds return money once',async()=>{
 await request();await request('second');const r=await Promise.allSettled([retry(()=>decide()),retry(()=>decide('second'))]);assert.equal(r.filter(x=>x.status==='fulfilled').length,1);
 const winner=r[0].status==='fulfilled'?'player':'second';await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'matches/m'),{status:'cancelled'}));
 await Promise.all([retry(()=>decide(winner,'refunded')),retry(()=>decide(winner,'refunded',{actor:'other'}))]);assert.equal((await wallet(winner)).balance,3000);
});
test('staff cannot confirm a banned participant; rejection and refunds remain available',async()=>{
 await request();
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m/bans/player'),{active:true}));
 await assertFails(decide());assert.equal((await wallet()).balance,3000);
 await assertSucceeds(decide('player','rejected'));
});
test('account bans prevent staff confirmation without trapping cancellation refunds',async()=>{
 await request();
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'accountBans/player'),{active:true}));
 await assertFails(decide());
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'accountBans/player'),{active:false}));
 await decide();
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'accountBans/player'),{active:true});await updateDoc(doc(c.firestore(),'matches/m'),{status:'cancelled'});});
 await assertSucceeds(decide('player','refunded'));
 await env.withSecurityRulesDisabled(async c=>assert.equal((await getDoc(doc(c.firestore(),'wallets/player'))).data().balance,3000));
});

test('mixed-balance refunds restore the original sources and cannot convert deposits to winnings',async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{balance:300,winnings:700}));
 await request();await assertSucceeds(decide());
 assert.deepEqual(await wallet(),{balance:0,winnings:0,reservedWinnings:100,lastEntry:'player_m'});
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'matches/m'),{status:'cancelled'}));
 await assertFails(decide('player','refunded',{wallet:{balance:0,winnings:1000}}));
 await assertFails(decide('player','refunded',{wallet:{balance:1000,winnings:0}}));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{winnings:Number.MAX_SAFE_INTEGER-750}));
 await assertFails(decide('player','refunded'));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{winnings:0}));
 await assertSucceeds(decide('player','refunded'));await assertSucceeds(decide('player','refunded'));
 assert.deepEqual(await wallet(),{balance:300,winnings:700,reservedWinnings:100,lastEntry:'player_m',lastRefund:'player_m'});
});
