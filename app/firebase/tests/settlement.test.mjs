import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,limit,writeBatch,runTransaction,serverTimestamp,updateDoc,deleteDoc}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-settlement',firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env.clearFirestore();await env.cleanup();});
const staff=(uid='operator')=>env.authenticatedContext(uid).firestore();
const resultId=uid=>({one:'A',two:'B',three:'C',four:'D'}[uid].repeat(20));
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db),now=new Date();
  batch.set(doc(db,'staffRoles/operator'),{admin:true});batch.set(doc(db,'staffRoles/other'),{admin:true});
  batch.set(doc(db,'matches/m'),{status:'review',revision:6,cancellationReason:'',description:'Local sample.',image:'solo',entryPaisa:0,rules:'Sample',cancellationTerms:'Sample',updatedAt:now,updatedBy:'operator',prizeRank:3,prizeMaxRank:3,title:'Sample settlement',mode:'solo',map:'Bermuda',startsAt:now,joined:4,capacity:48,prizePaisa:100000,perKillPaisa:500,prizesEnabled:true});
  for(const [rank,amount] of [[1,50000],[2,30000],[3,20000]])batch.set(doc(db,`matches/m/prizes/${rank}`),{rank,amountPaisa:amount});
  for(const [uid,placement,kills,award] of [['one',1,4,52000],['two',2,0,30000],['three',3,1,20500],['four',4,2,1000]]) {
   const id=resultId(uid),entry=uid+'_m';
   batch.set(doc(db,`entries/${entry}`),{uid,match:'m',status:'confirmed',username:'Same game name'});
   batch.set(doc(db,`results/${id}`),{match:'m',title:'Sample settlement',titleSearch:'sample settlement',username:'Same game name',placement,kills,awardPaisa:award,creditedPaisa:0,settlementStatus:'pending',publishedAt:now});
   batch.set(doc(db,`resultLinks/${id}`),{uid,match:'m',entry,result:id,titleSearch:'sample settlement',publishedAt:now});
  }
  batch.set(doc(db,'wallets/one'),{balance:7000});
  batch.set(doc(db,'wallets/two'),{balance:9000,winnings:1000,reservedWinnings:300,lastDeposit:'historical'});
  await batch.commit();
 });
});
async function credit(uid='one',options={}) {
 const match=options.match??'m',db=staff(options.actor),entry=`${uid}_${match}`,creditRef=doc(db,`prizeCredits/${entry}`),id=options.id??resultId(uid);
 return runTransaction(db,async tx=>{
  if((await tx.get(creditRef)).exists())return 'existing';
  const r=(await tx.get(doc(db,`results/${id}`))).data(),m=(await tx.get(doc(db,`matches/${match}`))).data();
  const old=(await tx.get(doc(db,`wallets/${uid}`))).data()??{},counter=(await tx.get(doc(db,`matchSettlements/${match}`))).data()??{};
  const amount=r.awardPaisa,base=Math.max(0,amount-m.perKillPaisa*r.kills),balanceAfter=(old.winnings??0)+amount;
  const data={uid,match,result:id,placement:r.placement,kills:r.kills,amountPaisa:amount,placementPaisa:base,balanceAfter,at:serverTimestamp(),...options.credit};
  const writes={
   credit:[creditRef,data],
   result:[doc(db,`results/${id}`),{creditedPaisa:amount,settlementStatus:amount>0?'credited':'not_applicable',...options.result},true],
   claim:[doc(db,`matchPlacements/${match}_${r.placement}`),{entry,result:id,match,placement:r.placement,at:serverTimestamp()}],
   counter:[doc(db,`matchSettlements/${match}`),{processed:(counter.processed??0)+1,placementPaisa:(counter.placementPaisa??0)+base,creditedPaisa:(counter.creditedPaisa??0)+amount,lastEntry:entry,updatedAt:serverTimestamp(),...options.counter}],
   audit:[doc(db,`auditRecords/prize_${entry}`),{action:'creditPrize',path:`prizeCredits/${entry}`,actor:options.actor??'operator',at:serverTimestamp()}]
  };
  if(amount>0){
   writes.wallet=[doc(db,`wallets/${uid}`),{...(!Object.keys(old).length?{balance:0,reservedWinnings:0}:{}),winnings:balanceAfter,lastPrize:entry,...options.wallet},true];
   writes.ledger=[doc(db,`walletLedger/prize_${entry}`),{uid,delta:amount,type:'prize',status:'completed',currency:'BDT',match,result:id,reference:match,balanceAfter,at:serverTimestamp(),...options.ledger}];
  }
  for(const [name,[ref,value,merge]] of Object.entries(writes))if(name!==options.omit)tx.set(ref,value,merge?{merge:true}:{});
  return 'credited';
 });
}
test('awards credit the linked account exactly once and preserve deposits and reserves',async()=>{
 await assertSucceeds(credit());assert.equal(await credit(),'existing');
 const wallet=(await getDoc(doc(staff(),'wallets/one'))).data();assert.equal(wallet.balance,7000);assert.equal(wallet.winnings,52000);
 await assertSucceeds(credit('two'));
 assert.deepEqual((await getDoc(doc(staff(),'wallets/two'))).data(),{balance:9000,winnings:31000,reservedWinnings:300,lastDeposit:'historical',lastPrize:'two_m'});
 await assertSucceeds(credit('three'));await assertSucceeds(credit('four'));
 assert.equal((await getDoc(doc(staff(),'wallets/four'))).data().winnings,1000);
 assert.equal((await getDoc(doc(staff(),'wallets/four'))).data().balance,0);
 const counter=(await getDoc(doc(staff(),'matchSettlements/m'))).data();assert.equal(counter.processed,4);assert.equal(counter.placementPaisa,100000);assert.equal(counter.creditedPaisa,103500);
});
test('missing tuple members cannot create partial credits or false credited results',async()=>{
 for(const omit of ['credit','result','claim','counter','audit','wallet','ledger']) {
  await assertFails(credit('one',{omit}));
  assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,undefined);
 }
});
test('forged recipient, amount, outcome, wallet fields and totals are denied',async()=>{
 for(const options of [{credit:{uid:'two'}},{credit:{amountPaisa:52001}},{credit:{placementPaisa:1}},{credit:{placement:2}},
  {wallet:{balance:99999}},{wallet:{winnings:52001}},{wallet:{reservedWinnings:1}},
  {ledger:{uid:'two'}},{ledger:{delta:52001}},{result:{kills:999}},{counter:{processed:4}},{counter:{placementPaisa:0}}])await assertFails(credit('one',options));
});
test('duplicate placements cannot credit a second entry',async()=>{
 await credit('one');
 await env.withSecurityRulesDisabled(async c=>{
  await updateDoc(doc(c.firestore(),`results/${resultId('two')}`),{placement:1,awardPaisa:50000});
 });
 await assertFails(credit('two'));
 assert.equal((await getDoc(doc(staff(),'wallets/two'))).data().winnings,1000);
});
test('ordinary players cannot mint winnings and only owners read private records',async()=>{
 const player=env.authenticatedContext('one').firestore(),other=env.authenticatedContext('two').firestore(),guest=env.unauthenticatedContext().firestore();
 await assertFails(setDoc(doc(player,'wallets/one'),{balance:7000,winnings:100000}));
 await assertFails(credit('one',{actor:'one'}));await credit('one');
 await assertSucceeds(getDoc(doc(player,'prizeCredits/one_m')));await assertFails(getDoc(doc(other,'prizeCredits/one_m')));await assertFails(getDoc(doc(guest,'prizeCredits/one_m')));
 await assertSucceeds(getDocs(query(collection(player,'walletLedger'),where('uid','==','one'),limit(20))));
 await assertFails(getDocs(query(collection(other,'walletLedger'),where('uid','==','one'),limit(20))));
});
async function raceRetry(operation) {
 for(let attempt=0;;attempt++)try{return await operation();}catch(error){if(attempt>=2||error.code!=='permission-denied')throw error;}
}
test('two staff retries on one result create one credit and cannot replay its wallet pointer',async()=>{
 await Promise.all([raceRetry(()=>credit('one')),raceRetry(()=>credit('one',{actor:'other'}))]);
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,52000);
 assert.equal((await getDoc(doc(staff(),'matchSettlements/m'))).data().processed,1);
 await assertFails(updateDoc(doc(staff(),'wallets/one'),{winnings:104000,lastPrize:'one_m'}));
 await assertFails(updateDoc(doc(staff(),'matchSettlements/m'),{processed:2,creditedPaisa:104000,placementPaisa:100000,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(staff(),`results/${resultId('one')}`),{creditedPaisa:0,settlementStatus:'pending'}));
});
test('credits from two matches serialize against the same winnings wallet',async()=>{
 const id='E'.repeat(20);
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db),m=(await getDoc(doc(db,'matches/m'))).data(),r=(await getDoc(doc(db,`results/${resultId('one')}`))).data();
  batch.set(doc(db,'matches/m2'),{...m,joined:1,prizePaisa:1000,perKillPaisa:0});
  batch.set(doc(db,'matches/m2/prizes/1'),{rank:1,amountPaisa:1000});
  batch.set(doc(db,'entries/one_m2'),{uid:'one',match:'m2',status:'confirmed',username:'Same game name'});
  batch.set(doc(db,`results/${id}`),{...r,match:'m2',kills:0,awardPaisa:1000});
  batch.set(doc(db,`resultLinks/${id}`),{uid:'one',match:'m2',entry:'one_m2',result:id});
  await batch.commit();
 });
 await Promise.all([raceRetry(()=>credit('one')),raceRetry(()=>credit('one',{actor:'other',match:'m2',id}))]);
 const wallet=(await getDoc(doc(staff(),'wallets/one'))).data();assert.equal(wallet.winnings,53000);assert.equal(wallet.balance,7000);
});
test('zero awards settle progress without minting a wallet or fake positive ledger',async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`results/${resultId('four')}`),{kills:0,awardPaisa:0,settlementStatus:'not_applicable'}));
 await assertSucceeds(credit('four'));
 assert.equal((await getDoc(doc(staff(),'wallets/four'))).exists(),false);
 assert.equal((await getDoc(doc(staff(),'walletLedger/prize_four_m'))).exists(),false);
 assert.equal((await getDoc(doc(staff(),'matchSettlements/m'))).data().processed,1);
});
test('legacy unallocated awards share one placement budget and respect wallet limits',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  await updateDoc(doc(c.firestore(),'matches/m'),{prizesEnabled:false});
  await updateDoc(doc(c.firestore(),`results/${resultId('one')}`),{awardPaisa:102000});
 });
 await assertSucceeds(credit('one'));await assertFails(credit('two'));
 assert.equal((await getDoc(doc(staff(),'wallets/two'))).data().winnings,1000);
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/three'),{winnings:9007199254740991}).catch(()=>setDoc(doc(c.firestore(),'wallets/three'),{balance:0,winnings:9007199254740991})));
 await assertFails(credit('three'));
});
test('configured draft awards must equal the saved position plus kill reward',async()=>{
 await env.withSecurityRulesDisabled(async c=>{await deleteDoc(doc(c.firestore(),`results/${resultId('one')}`));await deleteDoc(doc(c.firestore(),`resultLinks/${resultId('one')}`));});
 const db=staff(),id='X'.repeat(20);
 function draft(overrides={}) {
  const batch=writeBatch(db);
  batch.set(doc(db,'resultDrafts/one_m'),{uid:'one',match:'m',publicId:id,username:'Same game name',avatar:'solo',placement:1,kills:4,awardPaisa:52000,revision:1,updatedAt:serverTimestamp(),updatedBy:'operator',...overrides});
  batch.set(doc(db,'auditRecords/draft_one_m_1'),{action:'saveResultDraft',path:'resultDrafts/one_m',actor:'operator',at:serverTimestamp()});
  return batch.commit();
 }
 await assertFails(draft({awardPaisa:52001}));await assertFails(draft({placement:5,kills:0,awardPaisa:0}));await assertSucceeds(draft());
});
async function complete(empty=false) {
 const db=staff(),ref=doc(db,'matches/m'),old=(await getDoc(ref)).data();
 if(old.status==='settled')return 'existing';
 const revision=old.revision+1,batch=writeBatch(db);
 batch.update(ref,{status:'settled',revision,updatedAt:serverTimestamp(),updatedBy:'operator'});
 batch.set(doc(db,`matches/m/history/${revision}`),{from:'review',to:'settled',reason:'',at:serverTimestamp()});
 batch.set(doc(db,`auditRecords/match_m_${revision}`),{action:'saveMatch',path:'matches/m',actor:'operator',at:serverTimestamp()});
 if(empty)batch.set(doc(db,'matchSettlements/m'),{processed:0,placementPaisa:0,creditedPaisa:0,lastEntry:'',updatedAt:serverTimestamp()});
 await batch.commit();return 'complete';
}
test('whole-match completion requires every confirmed entry and does not pay twice',async()=>{
 await assertFails(complete());await credit('one');await assertFails(complete());
 await credit('two');await credit('three');await credit('four');
 await assertSucceeds(complete());assert.equal(await complete(),'existing');assert.equal(await credit('one'),'existing');
 assert.equal((await getDoc(doc(staff(),'matches/m'))).data().status,'settled');
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,52000);
});
test('an empty match can complete with an explicit zero settlement and no wallet changes',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db);
  batch.update(doc(db,'matches/m'),{joined:0});
  for(const uid of ['one','two','three','four']){batch.delete(doc(db,`entries/${uid}_m`));batch.delete(doc(db,`results/${resultId(uid)}`));batch.delete(doc(db,`resultLinks/${resultId(uid)}`));}
  await batch.commit();
 });
 await assertFails(complete());await assertSucceeds(complete(true));
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().balance,7000);
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,undefined);
});
