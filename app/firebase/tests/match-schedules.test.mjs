import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {validateTiming,nextWindowTime} from '../scripts/match-schedule-time.mjs';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,limit,query,writeBatch,serverTimestamp}=require('firebase/firestore');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
const projectId='demo-arena-schedules',id='S'.repeat(20),ms=value=>Date.parse(value);
const timing={dateFrom:'2026-11-07',dateTo:'2026-11-15',fromMinute:540,toMinute:1380,delayMinutes:20,timezone:'Asia/Dhaka'};
const template={title:'Sample scheduled match',description:'Local sample only.',image:'solo',mode:'solo',map:'Bermuda',capacity:48,entryPaisa:0,prizePaisa:10000,perKillPaisa:100,rules:'Sample',cancellationTerms:'Sample',categoryId:'',artworkId:''};
let env,app,db;
before(async()=>{
 process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
 env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
 app=initializeApp({projectId},'match-scheduler-tests');db=getFirestore(app);
});
after(async()=>{await env?.clearFirestore();await env?.cleanup();if(app)await deleteApp(app);});
beforeEach(async()=>{await env.clearFirestore();await db.doc('staffRoles/operator').set({admin:true});});
const staff=()=>env.authenticatedContext('operator').firestore();
async function create(options={},actor=staff(),audit=true){
 const batch=writeBatch(actor);
 batch.set(doc(actor,`matchSchedules/${id}`),{...timing,template,prizes:[5000,2500],state:'active',revision:1,createdBy:'operator',updatedBy:'operator',createdAt:serverTimestamp(),updatedAt:serverTimestamp(),...options});
 if(audit)batch.set(doc(actor,`auditRecords/schedule_${id}_1`),{action:'saveMatchSchedule',path:`matchSchedules/${id}`,actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
async function state(next,patch={}) {
 const actor=staff(),old=(await getDoc(doc(actor,`matchSchedules/${id}`))).data(),revision=old.revision+1,batch=writeBatch(actor);
 batch.update(doc(actor,`matchSchedules/${id}`),{state:next,revision,updatedBy:'operator',updatedAt:serverTimestamp(),...patch});
 batch.set(doc(actor,`auditRecords/schedule_${id}_${revision}`),{action:'saveMatchSchedule',path:`matchSchedules/${id}`,actor:'operator',at:serverTimestamp()});return batch.commit();
}
test('fixed Bangladesh intervals include aligned end times without waiting for winners',()=>{
 const day={...timing,dateTo:timing.dateFrom,toMinute:1140};
 let next=nextWindowTime(day,ms('2026-11-06T18:00:00Z')),starts=[];
 while(next!==null){starts.push(next);next=nextWindowTime(day,next+1);}
 assert.equal(starts.length,31);
 assert.equal(starts[0],ms('2026-11-07T03:00:00Z'));
 assert.equal(starts.at(-1),ms('2026-11-07T13:00:00Z'));
 assert.ok(starts.slice(1).every((value,index)=>value-starts[index]===20*60000));
 assert.equal(nextWindowTime(day,ms('2026-11-07T03:01:00Z')),starts[1]);
 assert.equal(nextWindowTime({...day,toMinute:1139},ms('2026-11-07T12:41:00Z')),null);
 for(const bad of [{dateFrom:'2026-02-30'},{dateTo:'2026-11-06'},{fromMinute:1380,toMinute:540},{delayMinutes:0}])assert.throws(()=>validateTiming({...timing,...bad}));
});
test('staff create audited private schedules; players cannot create or read them',async()=>{
 await assertFails(create({},staff(),false));await assertSucceeds(create());
 const guest=env.unauthenticatedContext().firestore(),player=env.authenticatedContext('player').firestore();
 await assertFails(getDoc(doc(guest,`matchSchedules/${id}`)));await assertFails(getDoc(doc(player,`matchSchedules/${id}`)));
 await assertFails(create({},player));await assertFails(getDocs(collection(staff(),'matchSchedules')));
 assert.equal((await assertSucceeds(getDocs(query(collection(staff(),'matchSchedules'),limit(20))))).size,1);
 await assertFails(setDoc(doc(staff(),`matchScheduleProgress/${id}`),{sequence:99,lastMatch:'forged'}));
 await assertFails(setDoc(doc(staff(),'schedulerStatus/matches'),{heartbeatAt:serverTimestamp()}));
 await assertFails(setDoc(doc(staff(),`matchSchedules/${id}/runs/1`),{match:'forged'}));
});
test('timing schema and immutable templates cannot be altered through state controls',async()=>{
 for(const patch of [{delayMinutes:0},{toMinute:540},{dateTo:'2026-11-06'},{timezone:'UTC'},{template:{...template,entryPaisa:0.5}},{template:{...template,roomId:'private'}}])await assertFails(create(patch));
 await create();await assertFails(state('paused',{template:{...template,title:'Changed'}}));await assertFails(state('paused',{delayMinutes:1}));
 await assertSucceeds(state('paused'));await assertSucceeds(state('active'));await assertSucceeds(state('stopped'));await assertFails(state('active'));
});

async function draft(actor=staff(),{omit='',sequence=1,change={},status='draft',startsAt=null}={}) {
 const match=`schedule_${id}_${sequence}`,batch=writeBatch(actor),stamp=serverTimestamp();
 if(omit!=='match')batch.set(doc(actor,`matches/${match}`),{...template,startsAt:startsAt??stamp,joined:0,status,revision:1,cancellationReason:'',independentPrizeTotal:true,prizesEnabled:true,prizeRank:0,prizeMaxRank:2,updatedAt:stamp,updatedBy:'operator',...change});
 for(const [index,amountPaisa] of [5000,2500].entries())batch.set(doc(actor,`matches/${match}/prizes/${index+1}`),{rank:index+1,amountPaisa,matchRevision:1,updatedAt:stamp});
 batch.set(doc(actor,`matches/${match}/history/1`),{from:'none',to:status,reason:'',at:stamp});
 batch.set(doc(actor,`auditRecords/match_${match}_1`),{action:'saveMatch',path:`matches/${match}`,actor:'operator',at:stamp});
 if(omit!=='run')batch.set(doc(actor,`matchSchedules/${id}/runs/${sequence}`),{sequence,match,createdAt:stamp,scheduleRevision:1,...(startsAt?{startsAt}:{})});
 if(omit!=='progress')batch.set(doc(actor,`matchScheduleProgress/${id}`),{sequence,lastMatch:match,phase:status==='open'?'waiting':'publishing',nextAt:null,error:'',updatedAt:stamp});
 return batch.commit();
}
async function publish() {
 const actor=staff(),match=`schedule_${id}_1`,batch=writeBatch(actor),stamp=serverTimestamp();
 batch.update(doc(actor,`matches/${match}`),{status:'open',revision:2,updatedAt:stamp,updatedBy:'operator'});
 batch.set(doc(actor,`matches/${match}/history/2`),{from:'draft',to:'open',reason:'',at:stamp});
 batch.set(doc(actor,`auditRecords/match_${match}_2`),{action:'saveMatch',path:`matches/${match}`,actor:'operator',at:stamp});
 batch.update(doc(actor,`matchScheduleProgress/${id}`),{phase:'waiting',updatedAt:stamp});
 return batch.commit();
}
test('online admin atomically reserves a private draft and resumes publication without another run',async()=>{
 await create();await assertSucceeds(draft());
 const player=env.authenticatedContext('player').firestore(),match=`schedule_${id}_1`;
 await assertFails(getDoc(doc(player,`matches/${match}`)));
 await assertFails(getDoc(doc(player,`matchScheduleProgress/${id}`)));
 await assertFails(setDoc(doc(staff(),`matchScheduleProgress/${id}`),{sequence:1,lastMatch:match,phase:'waiting',nextAt:null,error:'',updatedAt:serverTimestamp()}));
 await assertSucceeds(publish());assert.equal((await getDoc(doc(player,`matches/${match}`))).data().status,'open');
 await assertFails(draft());
});
test('missing tuple, skipped sequence and different template cannot reserve a scheduled run',async()=>{
 await create();
 for(const omit of ['match','run','progress'])await assertFails(draft(staff(),{omit}));
 await assertFails(draft(staff(),{sequence:2}));
 await assertFails(draft(staff(),{change:{title:'Different match'}}));
 await assertSucceeds(draft());await assertFails(draft(staff(),{sequence:2}));
});
test('players, paused schedules and revoked staff cannot generate matches',async()=>{
 await create();await assertFails(draft(env.authenticatedContext('player').firestore()));
 await state('paused');await assertFails(draft());await state('active');
 await db.doc('staffRoles/operator').update({admin:false});await assertFails(draft());
 assert.equal((await db.collection('matches').get()).size,0);
});
test('concurrent admin devices reserve the same deterministic run only once',async()=>{
 await create();const results=await Promise.allSettled([draft(),draft()]);
 assert.equal(results.filter(row=>row.status==='fulfilled').length,1);
 assert.equal((await db.collection('matches').get()).size,1);
 assert.equal((await db.collection(`matchSchedules/${id}/runs`).get()).size,1);
});
test('progress cannot be replaced with an unrelated match or extra fields',async()=>{
 await create();await draft();await publish();
 const actor=staff(),ref=doc(actor,`matchScheduleProgress/${id}`),p=(await getDoc(ref)).data();
 for(const patch of [{lastMatch:'other'},{sequence:100},{phase:'unrecognized'},{roomPassword:'private'}])await assertFails(setDoc(ref,{...p,...patch,updatedAt:serverTimestamp()}));
 await assertSucceeds(setDoc(ref,{...p,phase:'queued',nextAt:new Date(Date.now()+60000),updatedAt:serverTimestamp()}));
 await assertFails(setDoc(doc(actor,`matchSchedules/${id}/runs/1`),{sequence:1,match:'other',createdAt:serverTimestamp(),scheduleRevision:1}));
});

test('scheduled runs are immediately public open matches without a draft publication step',async()=>{
 await create();await assertSucceeds(draft(staff(),{status:'open'}));
 const actor=env.unauthenticatedContext().firestore(),match=`schedule_${id}_1`;
 assert.equal((await getDoc(doc(actor,`matches/${match}`))).data().status,'open');
 assert.equal((await getDoc(doc(staff(),`matchScheduleProgress/${id}`))).data().phase,'waiting');
 assert.equal((await getDocs(query(collection(staff(),`matches/${match}/history`),limit(20)))).size,1);
});

test('a daily batch creates every future match immediately without any results',async()=>{
 await create();const start=new Date(Date.now()+86400000);
 for(let sequence=1;sequence<=31;sequence++)await assertSucceeds(draft(staff(),{sequence,status:'open',startsAt:new Date(start.getTime()+(sequence-1)*20*60000)}));
 assert.equal((await db.collection('matches').get()).size,31);
 assert.equal((await db.collection(`matchSchedules/${id}/runs`).get()).size,31);
 assert.equal((await db.collection('prizeCredits').get()).size,0);
});
