import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,setDoc,updateDoc,writeBatch,runTransaction,serverTimestamp}=require('firebase/firestore');
let env;const db=(uid='player')=>env.authenticatedContext(uid).firestore();
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-paid',firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env.clearFirestore();await env.cleanup();});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 const d=c.firestore(),b=writeBatch(d);
 b.set(doc(d,'staffRoles/operator'),{admin:true});b.set(doc(d,'staffRoles/other'),{admin:true});
 b.set(doc(d,'matches/m'),{status:'open',mode:'solo',entryPaisa:1000,joined:0,capacity:1});
 for(const uid of ['player','second']){b.set(doc(d,'users/'+uid),{ffUsername:uid,avatar:'solo'});b.set(doc(d,'wallets/'+uid),{balance:3000,winnings:500,reservedWinnings:100});}
 await b.commit();
});});
async function request(uid='player',patch={}){const d=db(uid);return setDoc(doc(d,`entries/${uid}_m`),{uid,match:'m',username:uid,roster:[uid],status:'requested',fee:1000,createdAt:serverTimestamp(),...patch});}
async function decide(uid='player',target='confirmed',options={}){
 const actor=options.actor??'operator',d=db(actor),id=uid+'_m',ref=doc(d,'entries/'+id);
 return runTransaction(d,async tx=>{
  const e=(await tx.get(ref)).data();if(e.status===target)return;
  const walletRef=doc(d,'wallets/'+uid),w=(await tx.get(walletRef)).data(),matchRef=doc(d,'matches/m'),m=(await tx.get(matchRef)).data();
  const refund=target==='refunded',reject=target==='rejected',action=refund?'refundEntry':reject?'rejectEntry':'confirmEntry';
  const patch={status:target,reviewedBy:actor,reviewedAt:serverTimestamp(),...options.patch};
  if(reject)patch.decisionReason=options.reason??'Local sample rejection.';
  else if(!refund){patch.publicId=(uid==='player'?'A':'B').repeat(20);patch.avatar='solo';}
  if(options.omit!=='entry')tx.update(ref,patch);
  if(!reject){
   if(options.omit!=='wallet')tx.update(walletRef,{balance:w.balance+(refund?1000:-1000),[refund?'lastRefund':'lastEntry']:id,...options.wallet});
   if(!refund){
    if(options.omit!=='seat')tx.update(matchRef,{joined:m.joined+1,lastEntry:id});
    if(options.omit!=='public'){
     tx.set(doc(d,'participantLinks/'+patch.publicId),{entry:id});
     tx.set(doc(d,'matches/m/participants/'+patch.publicId),{username:uid,roster:[uid],avatar:'solo'});
    }
   }
   if(options.omit!=='ledger')tx.set(doc(d,`walletLedger/${refund?'refund':'entry'}_${id}`),{uid,delta:refund?1000:-1000,type:refund?'refund':'entry_fee',status:'completed',currency:'BDT',entry:id,match:'m',reference:'m',balanceAfter:w.balance+(refund?1000:-1000),at:serverTimestamp(),...options.ledger});
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
test('requests cannot forge identity/fee/roster or bypass profile, funds and capacity',async()=>{
 for(const patch of [{uid:'second'},{fee:1},{status:'confirmed'},{username:'other'},{roster:['player','second']},{createdAt:new Date(0)}])await assertFails(request('player',patch));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{balance:999}));await assertFails(request());
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{balance:3000}));
 await request();await assertFails(decide('player','confirmed',{actor:'player'}));await decide();await assertFails(request('second'));
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
