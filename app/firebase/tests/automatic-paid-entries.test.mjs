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
async function join(uid='player',options={}) {
 const d=db(uid),id=uid+'_m',publicId=(uid==='player'?'A':'B').repeat(20);
 return runTransaction(d,async tx=>{
  const ref=doc(d,'entries/'+id),existing=await tx.get(ref);if(existing.exists())return;
  const w=(await tx.get(doc(d,'wallets/'+uid))).data(),m=(await tx.get(doc(d,'matches/m'))).data();
  const deposit=Math.min(w.balance,1000),winnings=1000-deposit;
  if(options.omit!=='entry')tx.set(ref,{uid,match:'m',username:uid,roster:[uid],status:'confirmed',fee:1000,createdAt:serverTimestamp(),publicId,avatar:'solo',...options.entry});
  if(options.omit!=='wallet')tx.update(doc(d,'wallets/'+uid),{balance:w.balance-deposit,winnings:w.winnings-winnings,lastEntry:id,...options.wallet});
  if(options.omit!=='seat')tx.update(doc(d,'matches/m'),{joined:m.joined+1,lastEntry:id});
  if(options.omit!=='public'){
   tx.set(doc(d,'participantLinks/'+publicId),{entry:id});
   tx.set(doc(d,'matches/m/participants/'+publicId),{username:uid,roster:[uid],avatar:'solo'});
  }
  if(options.omit!=='ledger')tx.set(doc(d,'walletLedger/entry_'+id),{uid,delta:-1000,type:'entry_fee',status:'completed',currency:'BDT',entry:id,match:'m',reference:'m',balanceAfter:w.balance-deposit,depositPortion:deposit,winningsPortion:winnings,winningsAfter:w.winnings-winnings,at:serverTimestamp(),...options.ledger});
 });
}
const balance=async(uid='player')=>(await getDoc(doc(db(uid),'wallets/'+uid))).data();
test('player confirms and is charged once with an immediate seat',async()=>{
 await assertSucceeds(join());await assertSucceeds(join());
 assert.deepEqual(await balance(),{balance:2000,winnings:500,reservedWinnings:100,lastEntry:'player_m'});
 assert.equal((await getDoc(doc(db(),'matches/m'))).data().joined,1);
});
test('partial or forged joins cannot charge funds or reserve seats',async()=>{
 for(const omit of ['entry','wallet','seat','public','ledger'])await assertFails(join('player',{omit}));
 for(const options of [{wallet:{balance:2999}},{wallet:{winnings:0}},{ledger:{delta:1}},{entry:{fee:1}},{entry:{roster:['other']}},{entry:{uid:'second'}}])await assertFails(join('player',options));
 assert.equal((await balance()).balance,3000);
});
test('insufficient funds, changed fee, closed and full matches reject atomically',async()=>{
 for(const patch of [{entryPaisa:2000},{status:'closed'},{joined:1}]){
  await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'matches/m'),patch));await assertFails(join());
  await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'matches/m'),{entryPaisa:1000,status:'open',joined:0}));
 }
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{balance:499}));await assertFails(join());
 assert.equal((await balance()).balance,499);
});
test('concurrent joins only take the final seat once',async()=>{
 const results=await Promise.allSettled([join(),join('second')]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
 assert.equal((await balance()).balance+(await balance('second')).balance,5000);
});
test('match bans reject paid joins without charging or reserving a seat',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m/bans/player'),{active:true}));
 await assertFails(join());
 assert.equal((await balance()).balance,3000);
 assert.equal((await getDoc(doc(db(),'matches/m'))).data().joined,0);
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'matches/m/bans/player'),{active:false}));
 await assertSucceeds(join());
});
test('account bans reject paid joins and do not affect another account',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'accountBans/player'),{active:true}));
 await assertFails(join());await assertSucceeds(join('second'));
});

test('winnings-only and mixed entry funds are available; withdrawal reserves stay untouched',async()=>{
 for(const deposit of [0,300]) {
  await env.withSecurityRulesDisabled(async c=>{
   await updateDoc(doc(c.firestore(),'wallets/player'),{balance:deposit,winnings:1000-deposit});
  });
  await assertSucceeds(join());await assertSucceeds(join());
  assert.deepEqual(await balance(),{balance:0,winnings:0,reservedWinnings:100,lastEntry:'player_m'});
  const ledger=(await getDoc(doc(db(),'walletLedger/entry_player_m'))).data();
  assert.equal(ledger.depositPortion,deposit);assert.equal(ledger.winningsPortion,1000-deposit);
  await env.withSecurityRulesDisabled(async c=>{
   for(const path of ['entries/player_m','walletLedger/entry_player_m','participantLinks/'+ 'A'.repeat(20),'matches/m/participants/'+ 'A'.repeat(20)])await require('firebase/firestore').deleteDoc(doc(c.firestore(),path));
   await updateDoc(doc(c.firestore(),'matches/m'),{joined:0});
  });
 }
});
test('reserved winnings and a forged funding split cannot pay an entry',async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{balance:300,winnings:699,reservedWinnings:5000}));
 await assertFails(join());
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{winnings:700}));
 await assertFails(join('player',{wallet:{reservedWinnings:0}}));
 await assertFails(join('player',{ledger:{depositPortion:1000,winningsPortion:0}}));
 await assertFails(join('player',{wallet:{balance:100,winnings:0}}));
 assert.equal((await balance()).reservedWinnings,5000);
});
