import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,limit,writeBatch,runTransaction,serverTimestamp}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-participant-rewards',firestore:{host:'127.0.0.1',port:Number(process.env.ARENA_RULES_PORT??8080),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env?.clearFirestore();await env?.cleanup();});
const staff=(uid='operator')=>env.authenticatedContext(uid).firestore();
const id=uid=>({one:'P',two:'Q',three:'R',reward_one:'S'}[uid].repeat(20));
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db),now=new Date();
  for(const uid of ['operator','other'])batch.set(doc(db,`staffRoles/${uid}`),{admin:true});
  batch.set(doc(db,'matches/m'),{status:'review',revision:6,cancellationReason:'',description:'Sample only',image:'solo',entryPaisa:0,rules:'Sample',cancellationTerms:'Sample',updatedAt:now,updatedBy:'operator',prizeRank:2,prizeMaxRank:2,title:'Sample kill rewards',mode:'solo',map:'Bermuda',startsAt:now,joined:3,capacity:48,prizePaisa:80000,perKillPaisa:500,prizesEnabled:true});
  for(const [rank,amount] of [[1,50000],[2,30000]])batch.set(doc(db,`matches/m/prizes/${rank}`),{rank,amountPaisa:amount});
  for(const uid of ['one','two','three']){
   batch.set(doc(db,`entries/${uid}_m`),{uid,match:'m',status:'confirmed',username:'Same game name',roster:['Same game name']});
   batch.set(doc(db,`wallets/${uid}`),{balance:7000,winnings:100,reservedWinnings:300});
  }
  await batch.commit();
 });
});
async function save(uid,kills=0,options={}) {
 const db=staff(),entry=`${uid}_m`,ref=doc(db,`killDrafts/${entry}`),old=(await getDoc(ref)).data()??{},revision=(old.revision??0)+1;
 const batch=writeBatch(db);
 batch.set(ref,{uid,match:'m',publicId:id(uid),kills,memberKills:[kills],revision,updatedAt:serverTimestamp(),updatedBy:'operator',...options});
 batch.set(doc(db,`auditRecords/kills_${entry}_${revision}`),{action:'saveKillDraft',path:`killDrafts/${entry}`,actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
async function pay(uid,kind,options={}) {
 const db=staff(options.actor),entry=`${uid}_m`,key=`${entry}_${kind}`,result=id(uid),rank=options.rank??1;
 return runTransaction(db,async tx=>{
  const refs={credit:doc(db,`rewardCredits/${key}`),result:doc(db,`results/${result}`),draft:doc(db,`killDrafts/${entry}`),wallet:doc(db,`wallets/${uid}`),progress:doc(db,'matchSettlements/m'),legacy:doc(db,`prizeCredits/${entry}`),match:doc(db,'matches/m'),prize:doc(db,`matches/m/prizes/${rank}`)};
  const snapshots=await Promise.all(Object.values(refs).map(ref=>tx.get(ref)));
  const records=Object.fromEntries(Object.keys(refs).map((k,i)=>[k,snapshots[i].data()??{}]));
  if(snapshots[0].exists())return 'existing';
  const {result:old,draft,wallet,progress,legacy,match,prize}=records;
  const kills=kind==='kills'?draft.kills:old.kills??0,previousKillPaisa=old.killPaisa??(legacy.amountPaisa??0)-(legacy.placementPaisa??0),placement=kind==='placement'?rank:old.placement??0;
  const amount=kind==='placement'?prize.amountPaisa:kills*match.perKillPaisa-previousKillPaisa,credited=(old.creditedPaisa??0)+amount,balanceAfter=wallet.winnings+amount,processedDelta=kind==='kills'&&!legacy.result?1:0;
  const summary={...(Object.keys(old).length?old:{match:'m',title:match.title,titleSearch:match.title.toLowerCase(),mode:match.mode,map:match.map,startsAt:match.startsAt,username:'Same game name',avatar:'solo',publishedAt:serverTimestamp()}),rewardVersion:2,placement,kills,placementPaisa:kind==='placement'?amount:old.placementPaisa??legacy.placementPaisa??0,killPaisa:kind==='kills'?kills*match.perKillPaisa:previousKillPaisa,awardPaisa:credited,creditedPaisa:credited,settlementStatus:credited>0?'credited':'not_applicable',killFinalized:kind==='kills'||old.killFinalized===true,lastReward:key,...options.result};
  const writes={
   credit:[refs.credit,{entry,uid,match:'m',result,kind,placement,kills,previousKills:old.kills??0,previousKillPaisa,amountPaisa:amount,placementPaisa:kind==='placement'?amount:0,processedDelta,draftRevision:kind==='kills'?draft.revision:0,reason:options.reason??'',balanceAfter,at:serverTimestamp(),actor:options.actor??'operator',...options.credit}],
   result:[refs.result,summary],
   progress:[refs.progress,{processed:(progress.processed??0)+processedDelta,placementPaisa:(progress.placementPaisa??0)+(kind==='placement'?amount:0),creditedPaisa:(progress.creditedPaisa??0)+amount,lastEntry:entry,lastReward:key,updatedAt:serverTimestamp(),...options.progress}],
   audit:[doc(db,`auditRecords/reward_${key}`),{action:'creditParticipantReward',path:`rewardCredits/${key}`,actor:options.actor??'operator',at:serverTimestamp()}]
  };
  if(!Object.keys(draft).length)writes.draft=[refs.draft,{uid,match:'m',publicId:result,kills:0,memberKills:[],revision:1,updatedAt:serverTimestamp(),updatedBy:options.actor??'operator'}];
  if(!Object.keys(old).length)writes.link=[doc(db,`resultLinks/${result}`),{uid,match:'m',entry,result,titleSearch:summary.titleSearch,publishedAt:serverTimestamp()}];
  if(kind==='placement')for(const collection of ['matchPlacements','publishedPlacements'])writes[collection]=[doc(db,`${collection}/m_${rank}`),{entry,result,match:'m',placement:rank,at:serverTimestamp()}];
  if(amount>0){
   writes.wallet=[refs.wallet,{...wallet,winnings:balanceAfter,lastReward:key,...options.wallet}];
   writes.ledger=[doc(db,`walletLedger/reward_${key}`),{uid,delta:amount,type:'prize',status:'completed',currency:'BDT',match:'m',result,reference:'m',reward:key,component:kind,balanceAfter,at:serverTimestamp(),...options.ledger}];
  }
  for(const [name,[ref,value]] of Object.entries(writes))if(name!==options.omit)tx.set(ref,value);
  return 'credited';
 });
}
test('kills pay non-winners without inventing a placement; placement can follow',async()=>{
 await save('one',4);await assertSucceeds(pay('one','kills'));
 assert.equal((await getDoc(doc(staff(),`results/${id('one')}`))).data().placement,0);
 await assertSucceeds(pay('one','placement'));
 const r=(await getDoc(doc(staff(),`results/${id('one')}`))).data();
 assert.equal(r.placement,1);assert.equal(r.kills,4);assert.equal(r.placementPaisa,50000);assert.equal(r.killPaisa,2000);assert.equal(r.creditedPaisa,52000);
 assert.deepEqual((await getDoc(doc(staff(),'wallets/one'))).data(),{balance:7000,winnings:52100,reservedWinnings:300,lastReward:'one_m_placement'});
 assert.equal((await getDoc(doc(staff(),'matchSettlements/m'))).data().processed,1);
});
test('placement can pay first and blank/default-zero kills finalize without wallet money',async()=>{
 await assertSucceeds(pay('one','placement'));
 await save('one',0);await assertSucceeds(pay('one','kills'));
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,50100);
 assert.equal((await getDoc(doc(staff(),'walletLedger/reward_one_m_kills'))).exists(),false);
 assert.equal((await getDoc(doc(staff(),'matchSettlements/m'))).data().processed,1);
 await save('two',3);await pay('two','kills');
 assert.equal((await getDoc(doc(staff(),'wallets/two'))).data().winnings,1600);
});
test('a kill-only match needs no placement prize configuration',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'matches/m'),{prizesEnabled:false,prizePaisa:0},{merge:true}));
 await save('three',2);await assertSucceeds(pay('three','kills'));
 assert.equal((await getDoc(doc(staff(),'wallets/three'))).data().winnings,1100);
});
test('immutable component credits and draft locks prevent replay and changing paid kills',async()=>{
 await save('one',4);await pay('one','kills');assert.equal(await pay('one','kills'),'existing');
 await assertFails(save('one',5));
 await assertFails(setDoc(doc(staff(),'rewardCredits/one_m_kills'),{amountPaisa:999},{merge:true}));
 await assertFails(setDoc(doc(staff(),'wallets/one'),{winnings:4100,lastReward:'one_m_kills'},{merge:true}));
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,2100);
});
test('missing transaction members cannot create false credits or money',async()=>{
 await save('one',2);
 for(const omit of ['credit','result','link','progress','audit','wallet','ledger'])await assertFails(pay('one','kills',{omit}));
 for(const omit of ['matchPlacements','publishedPlacements','draft'])await assertFails(pay('two','placement',{omit}));
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,100);
});
test('forged amounts, recipients, totals, fields and mismatched draft revisions are denied',async()=>{
 await save('one',2);
 for(const options of [{credit:{amountPaisa:1001}},{credit:{uid:'two'}},{credit:{kills:99}},{credit:{draftRevision:2}},{credit:{previousKillPaisa:1}},{credit:{processedDelta:0}},{wallet:{balance:8000}},{wallet:{reservedWinnings:0}},{ledger:{uid:'two'}},{ledger:{delta:1001}},{result:{username:'Someone else'}},{result:{kills:99}},{result:{placement:1}},{progress:{processed:3}},{progress:{creditedPaisa:2000}}])await assertFails(pay('one','kills',options));
});
test('two winners cannot claim the same placement or give one participant two placement prizes',async()=>{
 await pay('one','placement');await assertFails(pay('two','placement'));
 assert.equal(await pay('one','placement',{rank:2}),'existing');
 await assertFails(setDoc(doc(staff(),'rewardCredits/one_m_placement'),{placement:2},{merge:true}));
 assert.equal((await getDoc(doc(staff(),`results/${id('one')}`))).data().placement,1);
});
test('two staff racing one component pay once',async()=>{
 await save('one',3);
 async function retry(f){for(let n=0;;n++)try{return await f();}catch(e){if(n===2||e.code!=='permission-denied')throw e;}}
 await Promise.all([retry(()=>pay('one','kills')),retry(()=>pay('one','kills',{actor:'other'}))]);
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,1600);
 assert.equal((await getDoc(doc(staff(),'matchSettlements/m'))).data().processed,1);
});
test('team draft totals must match member counts and preserve the entry account recipient',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'entries/one_m'),{roster:['Captain','Teammate']},{merge:true}));
 await assertFails(save('one',5,{memberKills:[2,2]}));await assertFails(save('one',-1,{memberKills:[-1,0]}));
 await assertSucceeds(save('one',5,{memberKills:[2,3]}));await pay('one','kills');
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,2600);
});
test('legacy combined credits remain immutable and only the missing kill amount is added',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db),now=new Date();
  batch.set(doc(db,'resultDrafts/one_m'),{publicId:id('one')});
  batch.set(doc(db,`results/${id('one')}`),{match:'m',title:'Old result',titleSearch:'old result',mode:'solo',map:'Bermuda',startsAt:now,username:'Same game name',avatar:'solo',publishedAt:now,placement:1,kills:2,awardPaisa:51000,creditedPaisa:51000,settlementStatus:'credited'});
  batch.set(doc(db,`resultLinks/${id('one')}`),{entry:'one_m',uid:'one',match:'m',result:id('one')});
  batch.set(doc(db,'prizeCredits/one_m'),{uid:'one',match:'m',result:id('one'),placement:1,kills:2,amountPaisa:51000,placementPaisa:50000});
  batch.set(doc(db,'wallets/one'),{balance:7000,winnings:51100,reservedWinnings:300});
  batch.set(doc(db,'matchSettlements/m'),{processed:1,placementPaisa:50000,creditedPaisa:51000,lastEntry:'one_m',updatedAt:now});
  await batch.commit();
 });
 await assertFails(save('one',1));await save('one',4);
 await assertFails(pay('one','kills'));await assertSucceeds(pay('one','kills',{reason:'Missing kill counts'}));
 assert.equal((await getDoc(doc(staff(),'wallets/one'))).data().winnings,52100);
 assert.equal((await getDoc(doc(staff(),'prizeCredits/one_m'))).data().kills,2);
 assert.equal((await getDoc(doc(staff(),'matchSettlements/m'))).data().processed,1);
 assert.equal((await getDoc(doc(staff(),`results/${id('one')}`))).data().creditedPaisa,52000);
});
test('players cannot save or credit results and private drafts remain staff-only',async()=>{
 await save('one',2);
 const player=env.authenticatedContext('one').firestore(),other=env.authenticatedContext('two').firestore();
 await assertFails(getDoc(doc(player,'killDrafts/one_m')));
 await assertFails(pay('one','kills',{actor:'one'}));await pay('one','kills');
 await assertSucceeds(getDoc(doc(player,'rewardCredits/one_m_kills')));await assertFails(getDoc(doc(other,'rewardCredits/one_m_kills')));
 await assertSucceeds(getDocs(query(collection(player,'walletLedger'),where('uid','==','one'),limit(20))));
});
test('reward-like account IDs retain their exact audit and credit identity',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db);
  batch.set(doc(db,'entries/reward_one_m'),{uid:'reward_one',match:'m',status:'confirmed',username:'Same game name',roster:['Same game name']});
  batch.set(doc(db,'wallets/reward_one'),{balance:0,winnings:0,reservedWinnings:0});
  batch.set(doc(db,'matches/m'),{joined:4},{merge:true});
  await batch.commit();
 });
 await save('reward_one',2);await assertSucceeds(pay('reward_one','kills'));
 assert.equal((await getDoc(doc(staff(),'wallets/reward_one'))).data().winnings,1000);
});
