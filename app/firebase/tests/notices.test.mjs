import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,limit,orderBy,updateDoc,deleteDoc,serverTimestamp,runTransaction,writeBatch,getDocFromServer,startAfter,documentId,getCountFromServer}=require('firebase/firestore');
let env;
const notice=(uid,overrides={})=>({title:'Tonight’s update',body:'Registration opens at 8 PM.',published:true,createdAt:serverTimestamp(),updatedAt:serverTimestamp(),createdBy:uid,updatedBy:uid,revision:1,...overrides});
before(async()=>{env=await initializeTestEnvironment({projectId:process.env.ARENA_RULES_PROJECT||'demo-arena',firestore:{host:'127.0.0.1',port:Number(process.env.ARENA_RULES_PORT||8080),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env?.clearFirestore();await env?.cleanup();});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'staffRoles','publisher'),{notices:true});});});
const staff=()=>env.authenticatedContext('publisher').firestore();
const player=()=>env.authenticatedContext('player').firestore();
const guest=()=>env.unauthenticatedContext().firestore();
test('publisher creates a notice and guests can read published notices',async()=>{await assertSucceeds(setDoc(doc(staff(),'notices','one'),notice('publisher')));await assertSucceeds(getDoc(doc(guest(),'notices','one')));});
test('guests can query only published notices with a bounded feed',async()=>{await setDoc(doc(staff(),'notices','one'),notice('publisher'));const snap=await assertSucceeds(getDocs(query(collection(guest(),'notices'),where('published','==',true),orderBy('updatedAt','desc'),limit(20))));assert.equal(snap.size,1);});
test('unfiltered or unbounded player queries are rejected',async()=>{await assertFails(getDocs(query(collection(guest(),'notices'),limit(20))));await assertFails(getDocs(query(collection(guest(),'notices'),where('published','==',true))));});
test('guest and ordinary player cannot publish',async()=>{await assertFails(setDoc(doc(guest(),'notices','one'),notice('player')));await assertFails(setDoc(doc(player(),'notices','one'),notice('player')));});
test('players cannot grant themselves publisher access',async()=>{await assertFails(setDoc(doc(player(),'staffRoles','player'),{notices:true}));await assertFails(setDoc(doc(staff(),'staffRoles','publisher'),{notices:true,finance:true}));});
test('hidden notices cannot be read by players',async()=>{await setDoc(doc(staff(),'notices','hidden'),notice('publisher',{published:false}));await assertFails(getDoc(doc(guest(),'notices','hidden')));await assertSucceeds(getDoc(doc(staff(),'notices','hidden')));});
test('publisher can unpublish and the public feed removes the notice',async()=>{const ref=doc(staff(),'notices','one');await setDoc(ref,notice('publisher'));await assertSucceeds(updateDoc(ref,{published:false,updatedAt:serverTimestamp(),updatedBy:'publisher',revision:2}));await assertFails(getDoc(doc(guest(),'notices','one')));const snap=await getDocs(query(collection(guest(),'notices'),where('published','==',true),limit(20)));assert.equal(snap.size,0);});
test('empty, whitespace and oversized content are rejected',async()=>{for(const bad of [{title:''},{title:'  '},{title:'a'.repeat(81)},{body:' '},{body:'a'.repeat(601)}])await assertFails(setDoc(doc(staff(),'notices','invalid'),notice('publisher',bad)));});
test('extra fields, forged authors and fake timestamps are rejected',async()=>{for(const bad of [{admin:true},{createdBy:'other'},{updatedBy:'other'},{updatedAt:new Date(0)}])await assertFails(setDoc(doc(staff(),'notices','invalid'),notice('publisher',bad)));});
test('updates preserve original author and increment revision',async()=>{const ref=doc(staff(),'notices','one');await setDoc(ref,notice('publisher'));await assertFails(updateDoc(ref,{title:'Changed',updatedAt:serverTimestamp(),updatedBy:'publisher'}));await assertFails(updateDoc(ref,{createdBy:'other',updatedAt:serverTimestamp(),updatedBy:'publisher',revision:2}));await assertSucceeds(updateDoc(ref,{title:'Changed',updatedAt:serverTimestamp(),updatedBy:'publisher',revision:2}));});
test('notice deletion and unknown collections are denied',async()=>{const ref=doc(staff(),'notices','one');await setDoc(ref,notice('publisher'));await assertFails(deleteDoc(ref));await assertFails(setDoc(doc(staff(),'wallets','x'),{amount:1}));});

const matchData=(uid)=>({revision:1,cancellationReason:'',title:'বাংলা ম্যাচ',description:'পরীক্ষা',image:'solo',mode:'solo',map:'Bermuda',startsAt:new Date(),capacity:48,entryPaisa:0,prizePaisa:0,perKillPaisa:0,rules:'নিয়ম',cancellationTerms:'শর্ত',status:'draft',updatedAt:serverTimestamp(),updatedBy:uid});
async function provision(){await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'paymentInstructions','bkash'),{number:'01889898989',enabled:true,revision:0});await setDoc(doc(c.firestore(),'staffRoles','owner'),{owner:true,admin:true,notices:true});await setDoc(doc(c.firestore(),'staffRoles','operator'),{admin:true,notices:true});});}
test('admin saves Bangla match details; players cannot read drafts or edit',async()=>{await provision();const db=env.authenticatedContext('operator').firestore();await assertSucceeds(writeMatch(db,'m',matchData('operator'),'none'));await assertFails(getDoc(doc(player(),'matches','m')));await assertFails(setDoc(doc(player(),'matches','m'),matchData('player')));await assertFails(writeMatch(db,'bad',{...matchData('operator'),entryPaisa:1.5},'none'));});
test('only owner grants access; self escalation and owner changes denied',async()=>{await provision();const db=env.authenticatedContext('owner').firestore();const grant={admin:true,notices:true,updatedAt:serverTimestamp(),updatedBy:'owner'};await assertSucceeds(setDoc(doc(db,'staffRoles','selected'),grant));await assertFails(setDoc(doc(db,'staffRoles','owner'),grant));await assertFails(setDoc(doc(env.authenticatedContext('operator').firestore(),'staffRoles','selected'),{...grant,updatedBy:'operator'}));await assertFails(setDoc(doc(db,'staffRoles','selected'),{...grant,owner:true}));});
test('revoked access denies match writes and finance remains immutable',async()=>{await provision();const ownerDb=env.authenticatedContext('owner').firestore();await assertSucceeds(setDoc(doc(ownerDb,'staffRoles','operator'),{admin:false,notices:false,updatedAt:serverTimestamp(),updatedBy:'owner'}));await assertFails(setDoc(doc(env.authenticatedContext('operator').firestore(),'matches','m'),matchData('operator')));await assertFails(setDoc(doc(ownerDb,'wallets','x'),{amount:100}));await assertFails(setDoc(doc(ownerDb,'depositRequests','x'),{status:'approved'}));});
test('audit records cannot be overwritten or removed',async()=>{await provision();const db=env.authenticatedContext('owner').firestore();const ref=doc(db,'auditRecords','a');const value={action:'create',path:'matches/a',actor:'owner',at:serverTimestamp()};await assertSucceeds(setDoc(ref,value));await assertFails(setDoc(ref,value));await assertFails(deleteDoc(ref));});

test('players own their profiles but cannot grant roles or funds',async()=>{
 const profile={displayName:'Player',ffUsername:'FF name',ffUid:'',avatar:'solo'};
 await assertSucceeds(setDoc(doc(player(),'users','player'),profile));
 await assertFails(setDoc(doc(player(),'users','other'),profile));
 await assertFails(updateDoc(doc(player(),'users','player'),{balance:10000}));
 await assertFails(setDoc(doc(player(),'wallets','player'),{balance:10000}));
 await assertFails(setDoc(doc(player(),'entries','entry'),{uid:'player',status:'confirmed'}));
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'wallets','player'),{balance:100});await setDoc(doc(c.firestore(),'users','other'),profile);});
 await assertSucceeds(getDoc(doc(player(),'wallets','player')));
 await assertFails(getDoc(doc(player(),'users','other')));
});
test('published match query works and drafts remain private',async()=>{
 await provision();const db=env.authenticatedContext('operator').firestore();
 await assertSucceeds(writeMatch(db,'open',matchData('operator'),'none'));
 await assertSucceeds(writeMatch(db,'open',{...matchData('operator'),status:'open',revision:2},'draft'));
 await assertSucceeds(getDocs(query(collection(player(),'matches'),where('status','in',['open','closed','live','review','settled','cancelled']),limit(100))));
});

// These use the client SDK and deployed rules, not the privileged local operations service.
async function seedFreeMatch(overrides={}) {
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();
  await setDoc(doc(db,'matches','free'),{...matchData('operator'),status:'open',joined:0,capacity:1,...overrides});
  for(const uid of ['player','other']) await setDoc(doc(db,'users',uid),{displayName:uid,ffUsername:uid,ffUid:'',avatar:'solo'});
 });
}
function freeEntry(uid,overrides={}) {return {uid,match:'free',username:uid,status:'confirmed',fee:0,createdAt:serverTimestamp(),...overrides};}
async function clientFreeJoin(uid) {
 const db=env.authenticatedContext(uid).firestore();
 return runTransaction(db,async tx=>{
  const entry=doc(db,'entries',uid+'_free');
  if((await tx.get(entry)).exists()) return;
  const match=doc(db,'matches','free');
  const snapshot=await tx.get(match);
  tx.set(entry,freeEntry(uid));
  tx.update(match,{joined:snapshot.data().joined+1});
 });
}
test('free client registration is atomic and retry does not consume another seat',async()=>{
 await seedFreeMatch({capacity:2});
 await assertSucceeds(clientFreeJoin('player'));
 await assertSucceeds(clientFreeJoin('player'));
 assert.equal((await getDoc(doc(player(),'matches','free'))).data().joined,1);
 assert.equal((await getDoc(doc(player(),'entries','player_free'))).data().status,'confirmed');
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'entries','player_free')));
 await assertFails(updateDoc(doc(player(),'entries','player_free'),{status:'requested'}));
 await assertFails(deleteDoc(doc(player(),'entries','player_free')));
});
test('concurrent free client registrations cannot oversell the final seat',async()=>{
 await seedFreeMatch();
 const results=await Promise.allSettled(['player','other'].map(clientFreeJoin));
 assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
 assert.equal((await getDoc(doc(player(),'matches','free'))).data().joined,1);
});
test('free registration requires both seat and entry in the same commit',async()=>{
 await seedFreeMatch();
 await assertFails(updateDoc(doc(player(),'matches','free'),{joined:1}));
 await assertFails(setDoc(doc(player(),'entries','player_free'),freeEntry('player')));
 const db=player(),batch=writeBatch(db);
 batch.update(doc(db,'matches','free'),{joined:1,title:'Forged title'});
 batch.set(doc(db,'entries','player_free'),freeEntry('player'));
 await assertFails(batch.commit());
 for(const joined of [-1,0,1.5,2]) {
  const invalid=writeBatch(db);
  invalid.update(doc(db,'matches','free'),{joined});
  invalid.set(doc(db,'entries','player_free'),freeEntry('player'));
  await assertFails(invalid.commit());
 }
});
test('players cannot use free registration to enter paid, closed, full or draft matches',async()=>{
 for(const override of [{entryPaisa:100},{status:'closed'},{status:'live'},{status:'draft'},{joined:1}]) {
  await seedFreeMatch(override);
  await assertFails(clientFreeJoin('player'));
 }
});
test('free registration rejects forged identity, fees, fields, and timestamps',async()=>{
 await seedFreeMatch();
 for(const override of [{uid:'other'},{username:'Forged'},{fee:-100},{status:'requested'},{admin:true},{createdAt:new Date(0)}]) {
  const db=player(),batch=writeBatch(db);
  batch.set(doc(db,'entries','player_free'),freeEntry('player',override));
  batch.update(doc(db,'matches','free'),{joined:1});
  await assertFails(batch.commit());
 }
});
test('free registration requires a complete player profile and authenticated access',async()=>{
 await seedFreeMatch();
 await env.withSecurityRulesDisabled(async c=>{await deleteDoc(doc(c.firestore(),'users','player'));});
 await assertFails(clientFreeJoin('player'));
 await assertFails(setDoc(doc(guest(),'entries','player_free'),freeEntry('player')));
});

async function seedWorkflow(status='closed') {
 await provision();
 await seedFreeMatch({status,prizePaisa:10000});
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'entries','player_free'),{uid:'player',match:'free',username:'player',status:'confirmed',fee:0,createdAt:new Date()});
 });
}
const operator=()=>env.authenticatedContext('operator').firestore();
function auditData(action,path){return {action,path,actor:'operator',at:serverTimestamp()};}
function roomData(overrides={}){return {roomId:'TEST-ROOM',password:'FAKE-PASSWORD',releaseAt:new Date(Date.now()-60000),enabled:true,revision:1,updatedAt:serverTimestamp(),updatedBy:'operator',...overrides};}
async function writeRoom(overrides={},audited=true){const db=operator(),batch=writeBatch(db);batch.set(doc(db,'rooms','free'),roomData(overrides));if(audited)batch.set(doc(db,'auditRecords','room_free_'+(overrides.revision||1)),auditData('saveRoom','rooms/free'));return batch.commit();}
const publicId='R'.repeat(20);
function draftData(overrides={}){return {uid:'player',match:'free',publicId,username:'player',avatar:'solo',placement:1,kills:2,awardPaisa:1000,revision:1,updatedAt:serverTimestamp(),updatedBy:'operator',...overrides};}
async function writeDraft(overrides={},audited=true){const db=operator(),batch=writeBatch(db);batch.set(doc(db,'resultDrafts','player_free'),draftData(overrides));if(audited)batch.set(doc(db,'auditRecords','draft_player_free_'+(overrides.revision||1)),auditData('saveResultDraft','resultDrafts/player_free'));return batch.commit();}
async function publicationData(overrides={}) {
 const m=(await getDoc(doc(operator(),'matches','free'))).data();
 return {match:'free',title:m.title,titleSearch:m.title.toLowerCase(),mode:m.mode,map:m.map,startsAt:m.startsAt,username:'player',avatar:'solo',placement:1,kills:2,awardPaisa:1000,creditedPaisa:0,settlementStatus:'pending',publishedAt:serverTimestamp(),...overrides};
}
async function publishResult(overrides={},omit='') {
 const db=operator(),batch=writeBatch(db),data=await publicationData(overrides);
 if(omit!=='result')batch.set(doc(db,'results',publicId),data);
 if(omit!=='link')batch.set(doc(db,'resultLinks',publicId),{uid:'player',match:'free',entry:'player_free',result:publicId,titleSearch:data.titleSearch,publishedAt:serverTimestamp()});
 if(omit!=='placement')batch.set(doc(db,'publishedPlacements',`free_${data.placement}`),{entry:'player_free',result:publicId,match:'free',placement:data.placement,at:serverTimestamp()});
 if(omit!=='audit')batch.set(doc(db,'auditRecords','result_'+publicId),auditData('publishResult','results/'+publicId));
 return batch.commit();
}
test('room release enforces server time, confirmed membership and active match state',async()=>{
 await seedWorkflow();
 await assertSucceeds(writeRoom());
 await assertSucceeds(getDoc(doc(player(),'rooms','free')));
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'rooms','free')));
 await assertFails(getDoc(doc(guest(),'rooms','free')));
 await assertFails(getDocs(query(collection(player(),'rooms'),limit(20))));
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'rooms','free'),{releaseAt:new Date(Date.now()+3600000)});});
 await assertFails(getDoc(doc(player(),'rooms','free')));
 await assertSucceeds(getDoc(doc(operator(),'rooms','free')));
});
test('enabled room releases by server time while registration stays open without another admin write',async()=>{
 await seedWorkflow();
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'matches','free'),{status:'open'}));
 await assertSucceeds(writeRoom({releaseAt:new Date(Date.now()+2000)}));
 await assertFails(getDoc(doc(player(),'rooms','free')));
 await new Promise(resolve=>setTimeout(resolve,2200));
 await assertSucceeds(getDoc(doc(player(),'rooms','free')));
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'rooms','free')));
});
test('disabled, cancelled and requested-entry rooms are denied',async()=>{
 await seedWorkflow();await writeRoom();
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'entries','player_free'),{status:'requested'});});
 await assertFails(getDoc(doc(player(),'rooms','free')));
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'entries','player_free'),{status:'confirmed'});await updateDoc(doc(c.firestore(),'matches','free'),{status:'cancelled'});});
 await assertFails(getDoc(doc(player(),'rooms','free')));
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'matches','free'),{status:'closed'});await updateDoc(doc(c.firestore(),'rooms','free'),{enabled:false});});
 await assertFails(getDoc(doc(player(),'rooms','free')));
});
test('room saves require staff, schema, revision and immutable audit',async()=>{
 await seedWorkflow();
 await assertFails(writeRoom({},false));
 await assertFails(writeRoom({email:'private@example.test'}));
 await assertFails(writeRoom({updatedBy:'other'}));
 await assertFails(setDoc(doc(player(),'rooms','free'),roomData()));
 await assertSucceeds(writeRoom());
 await assertFails(writeRoom());
 await assertSucceeds(writeRoom({revision:2,password:'NEW-FAKE-PASSWORD'}));
 await assertFails(deleteDoc(doc(operator(),'rooms','free')));
});
test('result drafts require confirmed entries and stay private',async()=>{
 await seedWorkflow('live');
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'entries','player_free'),{status:'requested'});});
 await assertFails(writeDraft());
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'entries','player_free'),{status:'confirmed'});});
 await assertFails(writeDraft({},false));
 await assertFails(writeDraft({username:'Forged'}));
 await assertFails(writeDraft({placement:0}));
 await assertFails(writeDraft({awardPaisa:999999}));
 await assertSucceeds(writeDraft());
 await assertFails(getDoc(doc(player(),'resultDrafts','player_free')));
 await assertFails(getDocs(query(collection(player(),'resultDrafts'),limit(20))));
 assert.equal((await assertSucceeds(getDoc(doc(player(),'results',publicId)))).exists(),false);
});
test('publication atomically requires public result, private ownership link and audit',async()=>{
 await seedWorkflow('review');await writeDraft();
 for(const omit of ['result','link','audit'])await assertFails(publishResult({},omit));
 await assertSucceeds(publishResult());
 const published=(await assertSucceeds(getDoc(doc(player(),'results',publicId)))).data();
 assert.equal(published.creditedPaisa,0);assert.equal(published.settlementStatus,'pending');
 assert.equal('uid' in published,false);assert.equal('email' in published,false);assert.equal('entry' in published,false);
 await assertSucceeds(getDocs(query(collection(player(),'resultLinks'),where('uid','==','player'),limit(20))));
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'resultLinks',publicId)));
 await assertFails(getDocs(query(collection(player(),'resultLinks'),limit(20))));
});
test('public results cannot contain private fields, forged prizes or claimed wallet credits',async()=>{
 await seedWorkflow('review');await writeDraft();
 await assertFails(publishResult({},'placement'));
 for(const bad of [{uid:'player'},{email:'private@example.test'},{creditedPaisa:1000},{awardPaisa:2000},{kills:3},{username:'Forged'},{settlementStatus:'completed'},{title:'Forged title'}])await assertFails(publishResult(bad));
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'matches','free'),{prizePaisa:500});});
 await assertFails(publishResult());
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'matches','free'),{prizePaisa:10000});});
 await assertSucceeds(publishResult());
 await assertFails(updateDoc(doc(operator(),'results',publicId),{placement:2}));
 await assertFails(deleteDoc(doc(operator(),'results',publicId)));
 await assertFails(writeDraft({revision:2,placement:2}));
 await assertFails(deleteDoc(doc(operator(),'resultLinks',publicId)));
});
test('results support bounded published feeds and title or match ID search',async()=>{
 await seedWorkflow('review');await writeDraft();await publishResult();
 await assertSucceeds(getDocs(query(collection(player(),'results'),orderBy('publishedAt','desc'),limit(20))));
 assert.equal((await assertSucceeds(getDocs(query(collection(player(),'results'),where('match','==','free'),orderBy('publishedAt','desc'),limit(20))))).size,1);
 assert.equal((await assertSucceeds(getDocs(query(collection(player(),'results'),orderBy('titleSearch'),where('titleSearch','>=','বাংলা'),where('titleSearch','<=','বাংলা\uf8ff'),limit(20))))).size,1);
 await assertFails(getDocs(collection(player(),'results')));
 await assertSucceeds(getDocs(query(collection(guest(),'results'),limit(20))));
});
test('revoked staff cannot save rooms, drafts or publish results',async()=>{
 await seedWorkflow('review');await writeDraft();
 await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),'staffRoles','operator'),{admin:false});});
 await assertFails(writeRoom());await assertFails(writeDraft({revision:2}));await assertFails(publishResult());
});

test('two competing winners cannot publish the same placement or poison its claim',async()=>{
 await seedWorkflow('review');await writeDraft();
 const second='S'.repeat(20),db=operator(),data=await publicationData();
 await env.withSecurityRulesDisabled(async c=>{
  const raw=c.firestore();await updateDoc(doc(raw,'matches/free'),{joined:2});
  await setDoc(doc(raw,'entries/second_free'),{uid:'second',match:'free',username:'player',status:'confirmed'});
  await setDoc(doc(raw,'resultDrafts/second_free'),draftData({uid:'second',publicId:second}));
 });
 await assertFails(setDoc(doc(db,'publishedPlacements/free_1'),{entry:'player_free',result:publicId,match:'free',placement:1,at:serverTimestamp()}));
 async function publishSecond(){const batch=writeBatch(db);
  batch.set(doc(db,'results',second),data);
  batch.set(doc(db,'resultLinks',second),{uid:'second',match:'free',entry:'second_free',result:second,titleSearch:data.titleSearch,publishedAt:serverTimestamp()});
  batch.set(doc(db,'publishedPlacements/free_1'),{entry:'second_free',result:second,match:'free',placement:1,at:serverTimestamp()});
  batch.set(doc(db,'auditRecords','result_'+second),auditData('publishResult','results/'+second));return batch.commit();
 }
 const outcomes=await Promise.allSettled([publishResult(),publishSecond()]);assert.equal(outcomes.filter(r=>r.status==='fulfilled').length,1);
 assert.equal((await getDocs(query(collection(db,'results'),limit(20)))).size,1);
 await assertFails(updateDoc(doc(db,'publishedPlacements/free_1'),{entry:'forged'}));
 await assertFails(deleteDoc(doc(db,'publishedPlacements/free_1')));
});
test('simultaneous publication retries create one public result and never credit a wallet',async()=>{
 await seedWorkflow('review');await writeDraft();
 const data=await publicationData(),db=operator();
 async function retrySafePublish(){
 let failure;
 try {await runTransaction(db,async tx=>{
  const result=doc(db,'results',publicId);
  if((await tx.get(result)).exists())return;
  tx.set(result,data);
  tx.set(doc(db,'resultLinks',publicId),{uid:'player',match:'free',entry:'player_free',result:publicId,titleSearch:data.titleSearch,publishedAt:serverTimestamp()});
  tx.set(doc(db,'publishedPlacements','free_1'),{entry:'player_free',result:publicId,match:'free',placement:1,at:serverTimestamp()});
  tx.set(doc(db,'auditRecords','result_'+publicId),auditData('publishResult','results/'+publicId));
 });}catch(error){failure=error;}
 const result=await getDocFromServer(doc(db,'results',publicId));
 const link=await getDocFromServer(doc(db,'resultLinks',publicId));
 if(!result.exists()||link.data()?.entry!=='player_free')throw failure||Error('Publication missing');
 }
 const attempts=await Promise.allSettled([retrySafePublish(),retrySafePublish(),retrySafePublish()]);
 assert.equal(attempts.filter(r=>r.status==='fulfilled').length,3,JSON.stringify(attempts));
 assert.equal((await getDocs(query(collection(player(),'results'),limit(20)))).size,1);
 assert.equal((await getDocs(query(collection(db,'auditRecords'),where('action','==','publishResult'),limit(20)))).size,1);
 assert.equal((await getDoc(doc(player(),'wallets','player'))).exists(),false);
 assert.equal((await getDocs(query(collection(player(),'ledger'),where('uid','==','player'),limit(20)))).size,0);
 await assertFails(updateDoc(doc(db,'matches','free'),{roomId:'SECRET-IN-PUBLIC'}));
});


test('published pagination preserves every row when publication times are identical',async()=>{
 await provision();
 const now=new Date();
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db);
  for(let i=0;i<24;i++){
   const match='page_'+i,id='P'.repeat(16)+String(i).padStart(4,'0'),entry='player_'+match;
   const m={...matchData('operator'),status:'review',joined:1,title:'Practice '+i};
   batch.set(doc(db,'matches',match),m);
   batch.set(doc(db,'entries',entry),{uid:'player',match,username:'player',status:'confirmed',fee:0,createdAt:now});
   batch.set(doc(db,'resultDrafts',entry),draftData({match,publicId:id,awardPaisa:0}));
   batch.set(doc(db,'results',id),{match,title:m.title,titleSearch:m.title.toLowerCase(),mode:'solo',map:'Bermuda',startsAt:now,username:'player',avatar:'solo',placement:1,kills:2,awardPaisa:0,creditedPaisa:0,settlementStatus:'not_applicable',publishedAt:now});
   batch.set(doc(db,'resultLinks',id),{uid:'player',match,entry,result:id,titleSearch:m.title.toLowerCase(),publishedAt:now});
   batch.set(doc(db,'auditRecords','result_'+id),{action:'publishResult',path:'results/'+id,actor:'operator',at:now});
  }
  await batch.commit();
 });
 const db=player(),base=query(collection(db,'results'),orderBy('publishedAt','desc'));
 const first=await assertSucceeds(getDocs(query(base,limit(20))));
 const second=await assertSucceeds(getDocs(query(base,startAfter(first.docs.at(-1)),limit(20))));
 assert.equal(first.size,20);assert.equal(second.size,4);
 assert.equal(new Set([...first.docs,...second.docs].map(d=>d.id)).size,24);
 const links=await assertSucceeds(getDocs(query(collection(db,'resultLinks'),where('uid','==','player'),orderBy('publishedAt','desc'),limit(21))));
 const ids=links.docs.slice(0,20).map(d=>d.get('result'));
 assert.equal((await assertSucceeds(getDocs(query(collection(db,'results'),where(documentId(),'in',ids),limit(20))))).size,20);
 await assertFails(getDocs(query(collection(db,'resultLinks'),where('uid','==','other'),limit(20))));
});

// Rosters stay in private entries; users cannot bypass format checks with modified clients.
test('duo and squad joins enforce roster sizes, captain and distinct names',async()=>{
 for(const mode of ['duo','squad']) {
  await seedFreeMatch({mode,capacity:10});
  const valid=mode==='duo'?['player','Teammate']:['player','Two','Three','Four'];
  const db=player();
  for(const roster of [undefined,[],['player'],[...valid,'extra'],['impostor',...valid.slice(1)],[...valid.slice(0,-1),'PLAYER'],[...valid.slice(0,-1),'   '],[...valid.slice(0,-1),' player '],[...valid.slice(0,-1),'x'.repeat(81)]]) {
   const batch=writeBatch(db);
   batch.set(doc(db,'entries','player_free'),freeEntry('player',roster===undefined?{}:{roster}));
   batch.update(doc(db,'matches','free'),{joined:1});
   await assertFails(batch.commit());
  }
  const batch=writeBatch(db);
  batch.set(doc(db,'entries','player_free'),freeEntry('player',{roster:valid}));
  batch.update(doc(db,'matches','free'),{joined:1});
  await assertSucceeds(batch.commit());
  assert.deepEqual((await getDoc(doc(db,'entries','player_free'))).data().roster,valid);
  await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'entries','player_free')));
  await env.withSecurityRulesDisabled(async c=>deleteDoc(doc(c.firestore(),'entries','player_free')));
 }
});

function publicJoinBatch(db,overrides={},omit='') {
 const id='PublicParticipant001',batch=writeBatch(db);
 batch.set(doc(db,'entries','player_free'),freeEntry('player',{roster:['player'],avatar:'solo',publicId:id}));
 batch.update(doc(db,'matches','free'),{joined:1});
 if(omit!=='link')batch.set(doc(db,'participantLinks',id),{entry:'player_free'});
 if(omit!=='public')batch.set(doc(db,'matches','free','participants',id),{username:'player',roster:['player'],avatar:'solo',...overrides});
 return batch;
}
test('free registration publishes only public game identity atomically',async()=>{
 await seedFreeMatch();
 for(const omit of ['link','public'])await assertFails(publicJoinBatch(player(),{},omit).commit());
 for(const extra of [{email:'private@example.test'},{uid:'player'},{username:'Forged'},{roster:['Forged']},{avatar:'unknown'}])await assertFails(publicJoinBatch(player(),extra).commit());
 await assertSucceeds(publicJoinBatch(player()).commit());
 const other=env.authenticatedContext('other').firestore();
 assert.deepEqual((await assertSucceeds(getDoc(doc(other,'matches','free','participants','PublicParticipant001')))).data(),{username:'player',roster:['player'],avatar:'solo'});
 await assertFails(getDoc(doc(other,'participantLinks','PublicParticipant001')));
 await assertFails(getDoc(doc(other,'entries','player_free')));
 await assertFails(getDoc(doc(other,'users','player')));
 await assertFails(updateDoc(doc(player(),'matches','free','participants','PublicParticipant001'),{username:'Changed'}));
 await assertFails(deleteDoc(doc(player(),'matches','free','participants','PublicParticipant001')));
 await assertFails(setDoc(doc(player(),'matches','free','participants','AnotherParticipant01'),{username:'player',roster:['player'],avatar:'solo'}));
});
test('staff can publish a legacy confirmed entry without changing history',async()=>{
 await provision();await seedFreeMatch();await clientFreeJoin('player');
 const db=operator(),id='LegacyParticipant001';
 const batch=writeBatch(db);
 batch.update(doc(db,'entries','player_free'),{publicId:id,avatar:'solo'});
 batch.set(doc(db,'participantLinks',id),{entry:'player_free'});
 batch.set(doc(db,'matches','free','participants',id),{username:'player',roster:['player'],avatar:'solo'});
 // The matching audit must accompany a legacy entry update.
 await assertFails(batch.commit());
 const auditedBatch=writeBatch(db);
 auditedBatch.update(doc(db,'entries','player_free'),{publicId:id,avatar:'solo'});
 auditedBatch.set(doc(db,'participantLinks',id),{entry:'player_free'});
 auditedBatch.set(doc(db,'matches','free','participants',id),{username:'player',roster:['player'],avatar:'solo'});
 auditedBatch.set(doc(db,'auditRecords','participant_'+id),{action:'publishParticipant',path:'matches/free/participants/'+id,actor:'operator',at:serverTimestamp()});
 await assertSucceeds(auditedBatch.commit());
 await assertFails(updateDoc(doc(db,'entries','player_free'),{fee:100}));
 await assertFails(updateDoc(doc(db,'entries','player_free'),{publicId:'AnotherParticipant01'}));
 await assertFails(updateDoc(doc(db,'matches','free','participants',id),{username:'Changed'}));
});
test('participant browsing is bounded, paginated and excludes drafts and guests',async()=>{
 await seedFreeMatch();
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db);
  for(let i=0;i<24;i++)batch.set(doc(db,'matches','free','participants',String(i).padStart(20,'0')),{username:'Player '+i,roster:['Player '+i],avatar:'solo'});
  await batch.commit();
 });
 const base=collection(player(),'matches','free','participants');
 const first=await assertSucceeds(getDocs(query(base,orderBy(documentId()),limit(20))));
 const second=await assertSucceeds(getDocs(query(base,orderBy(documentId()),startAfter(first.docs.at(-1)),limit(20))));
 assert.equal(first.size,20);assert.equal(second.size,4);
 assert.equal(new Set([...first.docs,...second.docs].map(d=>d.id)).size,24);
 await assertFails(getDocs(base));await assertFails(getDocs(query(base,limit(22))));
 await assertSucceeds(getDoc(doc(guest(),'matches','free','participants','00000000000000000000')));
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'matches','free'),{status:'draft'}));
 await assertFails(getDocs(query(base,limit(20))));
});

function pageWrite(db,id='help',overrides={},audit=true) {
 const data={title:'Help',body:'Contact the practice operator.',enabled:true,revision:1,updatedAt:serverTimestamp(),link:'https://example.com/help',...overrides};
 const batch=writeBatch(db);batch.set(doc(db,'playerPages',id),data);
 if(audit)batch.set(doc(db,'auditRecords',`page_${id}_${data.revision}`),{action:'savePage',path:'playerPages/'+id,actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
test('player pages require staff, valid content and atomic audit',async()=>{
 await provision();
 await assertFails(pageWrite(player()));
 await assertFails(pageWrite(operator(),'help',{},false));
 for(const bad of [{title:''},{body:''},{body:'x'.repeat(12001)},{updatedBy:'operator'},{email:'private@example.test'},{revision:2},{link:'javascript:alert(1)'},{link:'http://example.com'},{link:'https://example.com/has space'}])await assertFails(pageWrite(operator(),'help',bad));
 await assertFails(pageWrite(operator(),'unknown'));
 await assertSucceeds(pageWrite(operator()));
 await assertFails(pageWrite(operator()));
 await assertSucceeds(pageWrite(operator(),'help',{body:'Updated guidance',revision:2}));
 assert.equal((await assertSucceeds(getDoc(doc(player(),'playerPages','help')))).data().body,'Updated guidance');
 await assertFails(deleteDoc(doc(operator(),'playerPages','help')));
});
test('players can read published known pages but not disabled pages or staff configuration',async()=>{
 await provision();
 for(const id of ['rules','help','about','privacy'])await assertSucceeds(pageWrite(operator(),id));
 await assertSucceeds(getDoc(doc(player(),'playerPages','rules')));
 await assertFails(getDoc(doc(guest(),'playerPages','rules')));
 await assertFails(getDocs(collection(player(),'playerPages')));
 await assertSucceeds(pageWrite(operator(),'help',{enabled:false,revision:2}));
 await assertFails(getDoc(doc(player(),'playerPages','help')));
 await assertSucceeds(getDoc(doc(operator(),'playerPages','help')));
 await assertFails(getDoc(doc(player(),'configuration','privacy')));
});

const supportRequest=(overrides={})=>({uid:'player',subject:'Match dispute',body:'First line\nSecond line',reference:'match-ref',createdAt:serverTimestamp(),updatedAt:serverTimestamp(),status:'open',revision:0,response:'',...overrides});
function supportReply(db,revision=1,body='Reviewed\nPlease check the match rules.',status='review',audit=true) {
 const batch=writeBatch(db);
 batch.update(doc(db,'disputes','ticket'),{response:body,status,revision,updatedAt:serverTimestamp()});
 batch.set(doc(db,'disputes','ticket','responses',String(revision)),{body,status,at:serverTimestamp()});
 if(audit)batch.set(doc(db,'auditRecords',`support_ticket_${revision}`),{action:'replySupport',path:'disputes/ticket',actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
test('support requests are owned, private, bounded and immutable to players',async()=>{
 await assertFails(setDoc(doc(guest(),'disputes','ticket'),supportRequest()));
 for(const override of [{uid:'other'},{status:'resolved'},{revision:1},{response:'Forged reply'},{body:' \n '},{body:'x'.repeat(4001)},{subject:''},{reference:'x'.repeat(201)},{admin:true}])await assertFails(setDoc(doc(player(),'disputes','ticket'),supportRequest(override)));
 await assertSucceeds(setDoc(doc(player(),'disputes','ticket'),supportRequest()));
 await assertSucceeds(getDoc(doc(player(),'disputes','ticket')));
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'disputes','ticket')));
 await assertFails(updateDoc(doc(player(),'disputes','ticket'),{body:'Changed'}));
 await assertFails(deleteDoc(doc(player(),'disputes','ticket')));
 await assertSucceeds(getDocs(query(collection(player(),'disputes'),where('uid','==','player'),orderBy('createdAt','desc'),limit(21))));
 await assertFails(getDocs(query(collection(player(),'disputes'),limit(21))));
 await assertFails(getDocs(query(collection(player(),'disputes'),where('uid','==','player'),limit(22))));
});
test('staff replies require atomic immutable history and audit and cannot alter original requests',async()=>{
 await provision();await assertSucceeds(setDoc(doc(player(),'disputes','ticket'),supportRequest()));
 const db=operator();
 await assertFails(supportReply(db,1,'Reply','review',false));
 await assertFails(supportReply(player()));
 for(const [revision,body,status] of [[2,'Reply','review'],[1,' \n ','review'],[1,'x'.repeat(4001),'review'],[1,'Reply','paid']])await assertFails(supportReply(db,revision,body,status));
 await assertFails(updateDoc(doc(db,'disputes','ticket'),{status:'resolved',response:'Reply',revision:1,updatedAt:serverTimestamp()}));
 await assertSucceeds(supportReply(db));
 await assertFails(supportReply(db,1,'Overwritten'));
 await assertSucceeds(supportReply(db,2,'Resolved after review','resolved'));
 await assertSucceeds(supportReply(db,3,'Reopened for investigation','review'));
 const thread=await assertSucceeds(getDocs(query(collection(player(),'disputes','ticket','responses'),orderBy('at','desc'),limit(21))));
 assert.equal(thread.size,3);
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'disputes','ticket','responses','1')));
 await assertFails(updateDoc(doc(db,'disputes','ticket'),{body:'Tampered'}));
 await assertFails(updateDoc(doc(db,'disputes','ticket','responses','1'),{body:'Tampered'}));
 await assertFails(deleteDoc(doc(db,'disputes','ticket')));
 await assertFails(deleteDoc(doc(db,'disputes','ticket','responses','1')));
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'staffRoles','operator'),{admin:false}));
 await assertFails(supportReply(db,4,'Revoked staff'));
});
test('support pagination returns every request across equal creation times',async()=>{
 const at=new Date();await env.withSecurityRulesDisabled(async c=>{const db=c.firestore(),batch=writeBatch(db);for(let i=0;i<24;i++)batch.set(doc(db,'disputes',String(i).padStart(20,'0')),supportRequest({createdAt:at,updatedAt:at}));await batch.commit();});
 const q=query(collection(player(),'disputes'),where('uid','==','player'),orderBy('createdAt','desc'));
 const first=await assertSucceeds(getDocs(query(q,limit(20))));const second=await assertSucceeds(getDocs(query(q,startAfter(first.docs.at(-1)),limit(20))));
 assert.equal(first.size,20);assert.equal(second.size,4);assert.equal(new Set([...first.docs,...second.docs].map(d=>d.id)).size,24);
});

test('two staff replies cannot overwrite the same revision',async()=>{
 await provision();await assertSucceeds(setDoc(doc(player(),'disputes','ticket'),supportRequest()));
 const results=await Promise.allSettled([supportReply(operator(),1,'First decision'),supportReply(operator(),1,'Second decision')]);
 assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
 const request=await getDoc(doc(player(),'disputes','ticket'));
 const response=await getDoc(doc(player(),'disputes','ticket','responses','1'));
 assert.equal(request.data().revision,1);assert.equal(request.data().response,response.data().body);
 assert.equal(request.data().body,'First line\nSecond line');
});

function writeMatch(db,id,data,from,audit=true,history=true) {
 const batch=writeBatch(db);batch.set(doc(db,'matches',id),data);
 if(history)batch.set(doc(db,'matches',id,'history',String(data.revision)),{from,to:data.status,reason:data.cancellationReason??'',at:serverTimestamp()});
 if(audit)batch.set(doc(db,'auditRecords',`match_${id}_${data.revision}`),{action:'saveMatch',path:'matches/'+id,actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
test('match lifecycle permits open creation and requires sequential transitions, audit and immutable history',async()=>{
 await provision();const db=operator();const data=matchData('operator');
 await assertSucceeds(writeMatch(db,'open-first',{...data,status:'open'},'none'));
 await assertFails(writeMatch(db,'lifecycle',data,'none',false));await assertFails(writeMatch(db,'lifecycle',data,'none',true,false));
 await assertSucceeds(writeMatch(db,'lifecycle',data,'none'));
 await assertFails(writeMatch(db,'lifecycle',{...data,status:'live',revision:2},'draft'));
 let previous='draft',revision=1;
 for(const status of ['open','closed','live','review']){revision++;await assertSucceeds(writeMatch(db,'lifecycle',{...data,status,revision},previous));previous=status;}
 await assertFails(writeMatch(db,'lifecycle',{...data,status:'settled',revision:revision+1},'review'));
 await assertFails(writeMatch(db,'lifecycle',{...data,status:'open',revision:revision+1},'review'));
 await assertFails(writeMatch(db,'lifecycle',{...data,status:'cancelled',revision:revision+1,cancellationReason:'Late cancellation',cancelledAt:serverTimestamp()},'review'));
 await assertFails(updateDoc(doc(db,'matches','lifecycle','history','2'),{to:'live'}));
 await assertFails(deleteDoc(doc(db,'matches','lifecycle')));
});
test('registered entry terms are locked but staff may correct presentation',async()=>{
 await provision();await seedFreeMatch();await clientFreeJoin('player');const db=operator();
 const old=(await getDoc(doc(db,'matches','free'))).data();
 for(const changed of [{mode:'duo'},{map:'Changed map'},{startsAt:new Date(0)},{capacity:20},{entryPaisa:100},{perKillPaisa:100},{rules:'New rules'},{cancellationTerms:'New terms'}])await assertFails(writeMatch(db,'free',{...old,...changed,revision:2,updatedAt:serverTimestamp()},'open'));
 await assertSucceeds(writeMatch(db,'free',{...old,title:'Corrected title',description:'Corrected description',revision:2,updatedAt:serverTimestamp()},'open'));
});
test('cancellation records reason and time for free and paid matches; terminal edits stay blocked',async()=>{
 await provision();await seedFreeMatch();await clientFreeJoin('player');const db=operator();const old=(await getDoc(doc(db,'matches','free'))).data();
 const cancelled={...old,status:'cancelled',revision:2,cancellationReason:'Operator unavailable',cancelledAt:serverTimestamp(),updatedAt:serverTimestamp()};
 await assertFails(writeMatch(db,'free',{...cancelled,cancellationReason:''},'open'));
 await assertFails(writeMatch(db,'free',{...cancelled,cancelledAt:new Date(0)},'open'));
 await assertSucceeds(writeMatch(db,'free',cancelled,'open'));
 assert.equal((await getDoc(doc(player(),'matches','free'))).data().cancellationReason,'Operator unavailable');
 await assertFails(writeMatch(db,'free',{...cancelled,revision:3,updatedAt:serverTimestamp()},'cancelled'));
 await env.withSecurityRulesDisabled(async c=>{await deleteDoc(doc(c.firestore(),'auditRecords','match_free_2'));await deleteDoc(doc(c.firestore(),'matches','free','history','2'));});
 await seedFreeMatch({entryPaisa:100});const paid=(await getDoc(doc(db,'matches','free'))).data();
 await assertSucceeds(writeMatch(db,'free',{...paid,status:'cancelled',revision:2,cancellationReason:'Entry fees will be refunded',cancelledAt:serverTimestamp(),updatedAt:serverTimestamp()},'open'));
});

test('deposit sender details and accounting remain private and client-immutable',async()=>{
 await provision();await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();await setDoc(doc(db,'depositRequests','private-deposit'),{uid:'player',amount:1000,sender:'01700000000',reference:'LOCALREF',provider:'demo-bkash',receiver:'LOCAL-TEST-RECEIVER',status:'pending'});
  await setDoc(doc(db,'ledger','private-credit'),{uid:'player',delta:1000,type:'deposit',request:'private-deposit'});
  await setDoc(doc(db,'paymentReferences','private-reference'),{request:'private-deposit'});
 });
 await assertSucceeds(getDoc(doc(player(),'depositRequests','private-deposit')));
 await assertSucceeds(getDoc(doc(operator(),'depositRequests','private-deposit')));
 const other=env.authenticatedContext('other').firestore();await assertFails(getDoc(doc(other,'depositRequests','private-deposit')));await assertFails(getDoc(doc(guest(),'depositRequests','private-deposit')));
 await assertFails(getDoc(doc(other,'ledger','private-credit')));
 for(const db of [player(),operator()]) {
  await assertFails(updateDoc(doc(db,'depositRequests','private-deposit'),{status:'approved'}));
  await assertFails(updateDoc(doc(db,'depositRequests','private-deposit'),{sender:'01800000000'}));
  await assertFails(deleteDoc(doc(db,'depositRequests','private-deposit')));
  await assertFails(updateDoc(doc(db,'ledger','private-credit'),{delta:2000}));
  await assertFails(getDoc(doc(db,'paymentReferences','private-reference')));
 }
});

function writePaymentNumber(db,number='01889898989',revision=1,audit=true,extra={}) {
 const batch=writeBatch(db);batch.set(doc(db,'paymentInstructions','bkash'),{number,enabled:true,revision,updatedAt:serverTimestamp(),...extra});
 if(audit)batch.set(doc(db,'auditRecords',`payment_bkash_${revision}`),{action:'savePaymentInstructions',path:'paymentInstructions/bkash',actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
test('receiving number is readable by players and editable only through audited staff saves',async()=>{
 await provision();await assertSucceeds(getDoc(doc(player(),'paymentInstructions','bkash')));
 await assertFails(writePaymentNumber(player()));await assertFails(writePaymentNumber(operator(),'01889898989',1,false));
 await assertFails(writePaymentNumber(operator(),'invalid'));
 await assertFails(writePaymentNumber(operator(),'01889898989',1,true,{secret:'private'}));
 await assertSucceeds(writePaymentNumber(operator()));
 assert.equal((await getDoc(doc(player(),'paymentInstructions','bkash'))).data().number,'01889898989');
 await assertFails(getDoc(doc(guest(),'paymentInstructions','bkash')));
 await assertFails(writePaymentNumber(operator()));
 await assertSucceeds(writePaymentNumber(operator(),'01700000000',2));
 await assertFails(deleteDoc(doc(operator(),'paymentInstructions','bkash')));
});

function liveClaim(uid='player',requestId='LIVE-REQUEST',overrides={}) {return {uid,requestId,amount:1000,reference:'BKASHREF01',sender:'01700000000',receiver:'01889898989',provider:'bkash',currency:'BDT',status:'pending',createdAt:serverTimestamp(),...overrides};}
async function approveLive(uid='player',requestId='LIVE-REQUEST',omit='',overrides={}) {
 const db=operator(),id=uid+'_'+requestId,ref=doc(db,'walletDeposits',id);
 return runTransaction(db,async tx=>{
  const snap=await tx.get(ref);const d=snap.data();if(d.status==='approved')return;
  assert.equal(d.status,'pending');
  const w=doc(db,'wallets',uid),wallet=await tx.get(w),receipt=doc(db,'verifiedReceipts',d.receiver+'_'+d.reference);
  if((await tx.get(receipt)).exists())throw Error('Duplicate receipt');
  const balance=wallet.data()?.balance??0;
  if(omit!=='request')tx.update(ref,{status:'approved',decisionReason:'Receipt checked.',reviewedBy:'operator',reviewedAt:serverTimestamp()});
  if(omit!=='wallet')tx.set(w,{balance:balance+d.amount,lastDeposit:id,...overrides},{merge:true});
  if(omit!=='receipt')tx.set(receipt,{deposit:id,provider:'bkash',receiver:d.receiver,reference:d.reference});
  if(omit!=='ledger')tx.set(doc(db,'walletLedger','live_deposit_'+id),{uid,delta:d.amount,type:'deposit',status:'completed',currency:'BDT',deposit:id,reference:d.reference,receiver:d.receiver,balanceAfter:balance+d.amount,at:serverTimestamp()});
  if(omit!=='audit')tx.set(doc(db,'auditRecords','live_deposit_'+id),{deposit:id,action:'approveDeposit',path:ref.path,actor:'operator',at:serverTimestamp()});
 });
}
test('live claims accept only owned pending claims with the current receiving account',async()=>{
 await provision();
 for(const bad of [{uid:'other'},{amount:0},{amount:1.5},{amount:-1},{status:'approved'},{receiver:'01700000000'},{provider:'demo-bkash'},{currency:'USD'},{sender:'invalid'},{reference:'lowercase'},{reviewedBy:'operator'}])await assertFails(setDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),liveClaim('player','LIVE-REQUEST',bad)));
 await assertFails(setDoc(doc(guest(),'walletDeposits','player_LIVE-REQUEST'),liveClaim()));
 await assertSucceeds(setDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),liveClaim()));
 assert.equal((await getDoc(doc(player(),'wallets','player'))).exists(),false);
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'walletDeposits','player_LIVE-REQUEST')));
 await assertFails(updateDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),{status:'approved'}));
 await assertFails(updateDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),{amount:2000}));
 await assertFails(deleteDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST')));
 await assertSucceeds(getDocs(query(collection(player(),'walletDeposits'),where('uid','==','player'),limit(100))));
 await assertFails(getDocs(query(collection(player(),'walletDeposits'),limit(100))));
});
test('disabled or missing deposit settings never accept new claims or publish a fallback recipient',async()=>{
 await provision();await setDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),liveClaim());
 await assertSucceeds(writePaymentNumber(operator(),'',1,true,{enabled:false}));
 await assertFails(setDoc(doc(player(),'walletDeposits','player_NEW-REQUEST'),liveClaim('player','NEW-REQUEST')));
 await assertSucceeds(approveLive());
 await assertSucceeds(writePaymentNumber(operator(),'01700000000',2));
 await assertFails(setDoc(doc(player(),'walletDeposits','player_NEW-REQUEST'),liveClaim('player','NEW-REQUEST')));
 await assertSucceeds(setDoc(doc(player(),'walletDeposits','player_NEW-REQUEST'),liveClaim('player','NEW-REQUEST',{receiver:'01700000000'})));
 await env.withSecurityRulesDisabled(c=>deleteDoc(doc(c.firestore(),'paymentInstructions','bkash')));
 await assertFails(setDoc(doc(player(),'walletDeposits','player_THIRD-REQUEST'),liveClaim('player','THIRD-REQUEST')));
});
test('live approval requires all atomic records and credits exactly once on retries',async()=>{
 await provision();await setDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),liveClaim());
 for(const omit of ['request','wallet','receipt','ledger','audit'])await assertFails(approveLive('player','LIVE-REQUEST',omit));
 await assertFails(approveLive('player','LIVE-REQUEST','',{balance:2000}));
 await assertFails(approveLive('player','LIVE-REQUEST','',{winnings:1000}));
 await assertSucceeds(approveLive());await assertSucceeds(approveLive());
 const wallet=await getDoc(doc(player(),'wallets','player'));assert.equal(wallet.data().balance,1000);
 assert.equal((await getDocs(query(collection(player(),'walletLedger'),where('uid','==','player'),limit(20)))).size,1);
 await assertFails(updateDoc(doc(operator(),'walletDeposits','player_LIVE-REQUEST'),{status:'pending'}));
 await assertFails(updateDoc(doc(operator(),'walletLedger','live_deposit_player_LIVE-REQUEST'),{delta:2000}));
 await assertFails(deleteDoc(doc(operator(),'verifiedReceipts','01889898989_BKASHREF01')));
 await assertFails(updateDoc(doc(player(),'wallets','player'),{balance:2000}));
 await assertFails(updateDoc(doc(operator(),'wallets','player'),{balance:2000}));
});
test('two live requests cannot credit the same receipt even across different wallets',async()=>{
 await provision();await setDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),liveClaim());
 const other=env.authenticatedContext('other').firestore();await setDoc(doc(other,'walletDeposits','other_LIVE-REQUEST'),liveClaim('other'));
 const results=await Promise.allSettled([approveLive(),approveLive('other')]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
 let total=0;await env.withSecurityRulesDisabled(async c=>{const docs=await getDocs(collection(c.firestore(),'wallets'));total=docs.docs.reduce((sum,d)=>sum+d.data().balance,0);});assert.equal(total,1000);
});
test('live rejection preserves the claim, needs audit and creates no credit',async()=>{
 await provision();await setDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),liveClaim());const db=operator(),ref=doc(db,'walletDeposits','player_LIVE-REQUEST');
 const changes={status:'rejected',decisionReason:'Receipt not found.',reviewedBy:'operator',reviewedAt:serverTimestamp()};
 await assertFails(updateDoc(ref,changes));
 const batch=writeBatch(db);batch.update(ref,changes);batch.set(doc(db,'auditRecords','live_deposit_player_LIVE-REQUEST'),{deposit:'player_LIVE-REQUEST',action:'rejectDeposit',path:'walletDeposits/player_LIVE-REQUEST',actor:'operator',at:serverTimestamp()});await assertSucceeds(batch.commit());
 assert.equal((await getDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'))).data().amount,1000);
 assert.equal((await getDoc(doc(player(),'wallets','player'))).exists(),false);
 await assert.rejects(approveLive());
 await assertFails(deleteDoc(ref));
});
test('revoked staff cannot approve live deposits and overflowing credits are denied',async()=>{
 await provision();await setDoc(doc(player(),'walletDeposits','player_LIVE-REQUEST'),liveClaim());
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'wallets','player'),{balance:9007199254740991}));
 await assertFails(approveLive());
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'staffRoles','operator'),{admin:false}));
 await assertFails(approveLive());
});

test('different approved live receipts accumulate safely in one existing wallet',async()=>{
 await provision();await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'wallets','player'),{balance:700,bonus:50}));
 await setDoc(doc(player(),'walletDeposits','player_FIRST-LIVE'),liveClaim('player','FIRST-LIVE',{reference:'RECEIPT001'}));
 await setDoc(doc(player(),'walletDeposits','player_SECOND-LIVE'),liveClaim('player','SECOND-LIVE',{reference:'RECEIPT002'}));
 async function retryApprove(id,attempt=0){try{return await approveLive('player',id)}catch(e){if(e.code==='permission-denied'&&attempt<2)return retryApprove(id,attempt+1);throw e;}}
 await assertSucceeds(Promise.all([retryApprove('FIRST-LIVE'),retryApprove('SECOND-LIVE')]));
 const wallet=(await getDoc(doc(player(),'wallets','player'))).data();assert.equal(wallet.balance,2700);assert.equal(wallet.bonus,50);
 const ledger=await getDocs(query(collection(player(),'walletLedger'),where('uid','==','player'),limit(20)));assert.equal(ledger.size,2);assert.deepEqual(ledger.docs.map(d=>d.data().balanceAfter).sort((a,b)=>a-b),[1700,2700]);
});

test('guest browsing exposes published matches, results and rosters while private data stays protected',async()=>{
 await seedFreeMatch();
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();
  await setDoc(doc(db,'matches','draft'),{...matchData('operator'),status:'draft'});
  await setDoc(doc(db,'matches/free/participants','public-player'),{username:'player',roster:['player'],avatar:'solo'});
  await setDoc(doc(db,'results','published'),{title:'Published result',match:'free',publishedAt:new Date()});
  await setDoc(doc(db,'wallets','player'),{balance:100});
 });
 const db=guest();
 await assertSucceeds(getDoc(doc(db,'matches','free')));
 await assertSucceeds(getDocs(query(collection(db,'matches'),where('status','in',['open','closed','live','review','settled','cancelled']),limit(100))));
 await assertSucceeds(getDocs(query(collection(db,'matches/free/participants'),limit(21))));
 await assertSucceeds(getDocs(query(collection(db,'results'),limit(20))));
 await assertFails(getDoc(doc(db,'matches','draft')));
 await assertFails(getDoc(doc(db,'users','player')));
 await assertFails(getDoc(doc(db,'wallets','player')));
 await assertFails(getDocs(query(collection(db,'entries'),limit(20))));
 await assertFails(getDocs(query(collection(db,'resultLinks'),limit(20))));
 await assertFails(updateDoc(doc(db,'matches','free'),{joined:1}));
});
test('player profile supports game identity without display name or avatar',async()=>{
 await assertSucceeds(setDoc(doc(player(),'users','player'),{ffUsername:'Player',ffUid:''}));
 await assertFails(setDoc(doc(player(),'users','other'),{ffUsername:'Player',ffUid:''}));
 await assertFails(updateDoc(doc(player(),'users','player'),{admin:true}));
});
test('staff manage categories and guests read only active categories',async()=>{
 await provision();const db=env.authenticatedContext('operator').firestore();
 const category={name:'Clash Squad',order:1,active:true,updatedAt:serverTimestamp(),updatedBy:'operator'};
 await assertSucceeds(setDoc(doc(db,'categories','cs'),category));
 await assertSucceeds(getDocs(query(collection(guest(),'categories'),where('active','==',true),limit(100))));
 await assertFails(setDoc(doc(player(),'categories','forged'),{...category,updatedBy:'player'}));
 await assertFails(setDoc(doc(db,'categories','invalid'),{...category,name:' '}));
 await assertSucceeds(updateDoc(doc(db,'categories','cs'),{active:false,updatedAt:serverTimestamp()}));
 await assertFails(getDoc(doc(guest(),'categories','cs')));
 await assertSucceeds(getDoc(doc(db,'categories','cs')));
});

test('admin dashboard aggregates database totals and ordinary users cannot count private users',async()=>{
 await provision();await seedFreeMatch();
 const db=env.authenticatedContext('operator').firestore();
 const count=await assertSucceeds(getCountFromServer(collection(db,'matches')));assert.equal(count.data().count,1);
 await assertSucceeds(getCountFromServer(collection(db,'users')));
 await assertSucceeds(getCountFromServer(query(collection(db,'walletDeposits'),where('status','==','pending'))));
 await assertFails(getCountFromServer(collection(player(),'users')));
});

test('staff upload bounded immutable artwork and guests can read it but cannot upload',async()=>{
 await provision();const db=env.authenticatedContext('operator').firestore();
 const {Bytes}=require('firebase/firestore');
 const image={data:Bytes.fromUint8Array(new Uint8Array([82,73,70,70,1])),contentType:'image/webp',createdAt:serverTimestamp(),createdBy:'operator'};
 await assertSucceeds(setDoc(doc(db,'artwork','sample'),image));
 await assertSucceeds(getDoc(doc(guest(),'artwork','sample')));
 await assertFails(setDoc(doc(player(),'artwork','forged'),{...image,createdBy:'player'}));
 await assertFails(setDoc(doc(db,'artwork','oversized'),{...image,data:Bytes.fromUint8Array(new Uint8Array(80001))}));
 await assertFails(updateDoc(doc(db,'artwork','sample'),{contentType:'text/html'}));
 await assertSucceeds(setDoc(doc(db,'categories','image-category'),{name:'With image',order:0,active:true,artworkId:'sample',updatedAt:serverTimestamp(),updatedBy:'operator'}));
 await assertFails(setDoc(doc(db,'categories','broken-image'),{name:'Broken',order:0,active:true,artworkId:'missing',updatedAt:serverTimestamp(),updatedBy:'operator'}));
});

test('owner deletes only inactive categories with an atomic audit; inactive categories cannot receive new matches',async()=>{
 await provision();const db=env.authenticatedContext('owner').firestore();
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'categories','unused'),{name:'Unused sample',order:0,active:false,updatedAt:new Date(),updatedBy:'owner'});
  await setDoc(doc(c.firestore(),'categories','active'),{name:'Active sample',order:0,active:true,updatedAt:new Date(),updatedBy:'owner'});
 });
 function remove(db,id){const b=writeBatch(db);b.delete(doc(db,'categories',id));b.set(doc(db,'auditRecords',`deleteCategory_${id}`),{action:'deleteCategory',path:`categories/${id}`,actor:db===dbOwner?'owner':'operator',at:serverTimestamp()});return b.commit();}
 const dbOwner=db;
 await assertFails(deleteDoc(doc(db,'categories','unused')));
 await assertFails(remove(operator(),'unused'));
 await assertFails(remove(db,'active'));
 await assertFails(writeMatch(db,'inactive-category',{...matchData('owner'),categoryId:'unused'},'none'));
 await assertSucceeds(remove(db,'unused'));
 await assertFails(deleteDoc(doc(db,'auditRecords','deleteCategory_unused')));
});

test('owner deletes matches in every status with audit while non-owners remain denied',async()=>{
 await provision();const db=env.authenticatedContext('owner').firestore();
 for(const [id,status,joined] of [['empty','draft',0],['open','open',0],['registered','closed',1],['live','live',2],['review','review',2],['completed','completed',2],['cancelled','cancelled',1]]){
  await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'matches',id),{...matchData('owner'),status,joined}));
 }
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'entries','retained-entry'),{match:'registered',uid:'player',status:'confirmed'});
  await setDoc(doc(c.firestore(),'matches','registered','history','1'),{from:'draft',to:'open'});
 });
 const remove=(db,id,uid)=>{const b=writeBatch(db);b.delete(doc(db,'matches',id));b.set(doc(db,'auditRecords',`deleteMatch_${id}`),{action:'deleteMatch',path:`matches/${id}`,actor:uid,at:serverTimestamp()});return b.commit();};
 await assertFails(deleteDoc(doc(db,'matches','empty')));
 await assertFails(remove(operator(),'empty','operator'));
 await assertFails(remove(player(),'empty','player'));
 for(const id of ['open','registered','live','review','completed','cancelled']) {
  await assertSucceeds(remove(db,id,'owner'));
  assert.equal((await getDoc(doc(db,'matches',id))).exists(),false);
 }
 await assertSucceeds(remove(db,'empty','owner'));
 await env.withSecurityRulesDisabled(async c=>{
  assert.equal((await getDoc(doc(c.firestore(),'entries','retained-entry'))).exists(),true);
  assert.equal((await getDoc(doc(c.firestore(),'matches','registered','history','1'))).exists(),true);
 });
 await assertFails(deleteDoc(doc(db,'auditRecords','deleteMatch_empty')));
});

// Participant moderation uses private documents; public snapshots and financial history stay intact.
async function moderate(uid, scope='account', active=true, revision=1, reason='Confirmed cheating') {
 const db=env.authenticatedContext('operator').firestore();
 const path=scope==='account'?`accountBans/${uid}`:`matches/free/bans/${uid}`;
 const key=scope==='account'?`account_${uid}`:`match_free_${uid}`;
 const batch=writeBatch(db);
 batch.set(doc(db,path),{uid,active,reason,revision,updatedAt:serverTimestamp(),updatedBy:'operator'});
 batch.set(doc(db,`auditRecords/ban_${key}_${revision}`),{action:'moderatePlayer',path,reason,actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
test('account ban blocks private reads, profile changes and registration; lifting restores access',async()=>{
 await provision();await seedFreeMatch();
 await assertSucceeds(moderate('player'));
 const accountBan=await assertSucceeds(getDoc(doc(player(),'accountBans','player')));
 assert.equal(accountBan.data().reason,'Confirmed cheating');
 await assertFails(getDoc(doc(player(),'users','player')));
 await assertFails(updateDoc(doc(player(),'users','player'),{ffUsername:'New name'}));
 await assertFails(clientFreeJoin('player'));
 await assertSucceeds(moderate('player','account',false,2,'Reviewed appeal'));
 await assertSucceeds(clientFreeJoin('player'));
});
test('players and guests cannot forge, lift, delete or inspect someone else’s bans',async()=>{
 await provision();await seedFreeMatch();await moderate('player');
 await assertFails(updateDoc(doc(player(),'accountBans','player'),{active:false}));
 await assertFails(deleteDoc(doc(staff(),'accountBans','player')));
 await assertFails(getDoc(doc(guest(),'accountBans','player')));
 await assertFails(getDoc(doc(player(),'accountBans','other')));
 await assertFails(setDoc(doc(player(),'matches/free/bans/other'),{uid:'other',active:true}));
});
test('bans require reason, sequential revision, protected target and atomic audit',async()=>{
 await provision();await seedFreeMatch();
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'users','operator'),{ffUsername:'Staff'}));
 await assertFails(moderate('player','account',true,1,' '));
 await assertFails(moderate('player','account',true,2));
 await assertFails(moderate('operator'));
 await assertFails(moderate('missing'));
 const db=env.authenticatedContext('operator').firestore();
 await assertFails(setDoc(doc(db,'accountBans','player'),{uid:'player',active:true,reason:'Cheating',revision:1,updatedAt:serverTimestamp(),updatedBy:'operator'}));
 await moderate('player');await assertFails(moderate('player'));
});
test('match ban blocks only this match and lifting permits registration',async()=>{
 await provision();await seedFreeMatch();
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'entries','player_free'),{uid:'player',match:'free',status:'requested'});});
 await assertSucceeds(moderate('player','match'));
 assert.equal((await assertSucceeds(getDoc(doc(player(),'matches/free/bans/player')))).data().reason,'Confirmed cheating');
 await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(),'matches/free/bans/player')));
 await assertSucceeds(getDoc(doc(player(),'users','player')));
 // Remove the pending fixture to attempt a fresh registration under the active ban.
 await env.withSecurityRulesDisabled(async c=>{await deleteDoc(doc(c.firestore(),'entries','player_free'));});
 await assertFails(clientFreeJoin('player'));
 await assertSucceeds(clientFreeJoin('other'));
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'entries','player_free'),{uid:'player',match:'free',status:'requested'});});
 await assertSucceeds(moderate('player','match',false,2,'Appeal accepted'));
 await env.withSecurityRulesDisabled(async c=>{await deleteDoc(doc(c.firestore(),'entries','player_free'));await updateDoc(doc(c.firestore(),'matches','free'),{capacity:2});});
 await assertSucceeds(clientFreeJoin('player'));
});
test('banning a confirmed participant revokes room reads without rewriting entry or seat count',async()=>{
 await provision();await seedFreeMatch();await clientFreeJoin('player');
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'rooms','free'),{enabled:true,releaseAt:new Date(0),roomId:'123',password:'secret'});});
 await assertSucceeds(getDoc(doc(player(),'rooms','free')));
 await moderate('player','match');
 await assertFails(getDoc(doc(player(),'rooms','free')));
 assert.equal((await getDoc(doc(player(),'entries','player_free'))).data().status,'confirmed');
 assert.equal((await getDoc(doc(player(),'matches','free'))).data().joined,1);
 await moderate('player','match',false,2,'Appeal accepted');
 await assertSucceeds(getDoc(doc(player(),'rooms','free')));
});

for(const status of ['draft','open','closed','live','review','settled','cancelled'])test(`staff can save and publish winners while match status is ${status}`,async()=>{
 await seedWorkflow(status);
 await assertSucceeds(writeDraft());
 await assertSucceeds(publishResult());
 assert.equal((await getDoc(doc(operator(),'matches/free'))).data().status,status);
});
