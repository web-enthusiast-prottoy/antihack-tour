// Authorized release check: private, future-dated fixtures only. No public matches or financial writes.
import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const credentials=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
const account=credentials.getGlobalDefaultAccount();if(!account)throw Error('Existing Firebase sign-in required');credentials.setActiveAccount({},account);
const {initializeApp,deleteApp}=require('firebase/app');
const {getAuth,createUserWithEmailAndPassword,deleteUser}=require('firebase/auth');
const {getFirestore,doc,getDocFromServer,getDocsFromServer,collection,query,where,limit,writeBatch,serverTimestamp,terminate}=require('firebase/firestore');
const project='arena-ff-34506',base=`projects/${project}/databases/(default)/documents`;
const output=new URL(process.env.ARENA_RELEASE_EVIDENCE || '../../verification/production-live-2026-11-07/',import.meta.url);
const config=JSON.parse(await readFile(new URL('../../android/app/src/admin/google-services.json',import.meta.url),'utf8'));
if(config.project_info.project_id!==project||process.env.FIRESTORE_EMULATOR_HOST)throw Error('Production project/environment mismatch');
const android=config.client.find(c=>c.client_info.android_client_info.package_name==='bd.arena.prototype.admin');
if(!android)throw Error('Admin Firebase configuration missing');
const owner=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const app=initializeApp({projectId:project,apiKey:android.api_key[0].current_key},'scheduling-release-'+randomUUID());
const auth=getAuth(app),db=getFirestore(app),id=randomUUID().replaceAll('-','').slice(0,20),match=`schedule_${id}_1`;
const cleanup=new Set([`matchSchedules/${id}`,`matchSchedules/${id}/runs/1`,`matchScheduleProgress/${id}`,`auditRecords/schedule_${id}_1`,`auditRecords/schedule_${id}_2`,`matches/${match}`,`matches/${match}/prizes/1`,`matches/${match}/history/1`,`auditRecords/match_${match}_1`]);
const result={project,publicMatchesCreated:false,realPlayersChanged:false,financialWrites:false};
let failure;
try {
 const created=await createUserWithEmailAndPassword(auth,`schedule-release-${randomUUID()}@example.invalid`,randomUUID()+'Aa1!');
 const uid=created.user.uid;cleanup.add(`staffRoles/${uid}`);
 async function denied(operation) {try{await operation();}catch(error){if(error.code==='permission-denied')return;throw error;}throw Error('Expected an access denial');}
 const ref=doc(db,`matchSchedules/${id}`),progress=doc(db,`matchScheduleProgress/${id}`);
 await denied(()=>getDocFromServer(ref));result.playerScheduleAccessDenied=true;
 await denied(()=>writeBatch(db).set(doc(db,`wallets/${uid}`),{balance:1}).commit());result.walletForgeryDenied=true;
 // Temporary staff role is scoped to this disposable user and removed in finally.
 await owner.patch(`${base}/staffRoles/${uid}`,{fields:{admin:{booleanValue:true}}});
 const template={title:'Private release verification',description:'Temporary private fixture',image:'solo',mode:'solo',map:'Bermuda',capacity:2,entryPaisa:0,prizePaisa:100,perKillPaisa:0,rules:'Private verification only',cancellationTerms:'Private verification only',categoryId:'',artworkId:''};
 const schedule={template,prizes:[100],dateFrom:'2099-01-01',dateTo:'2099-01-01',fromMinute:540,toMinute:1380,delayMinutes:20,timezone:'Asia/Dhaka',state:'active',revision:1,createdBy:uid,createdAt:serverTimestamp(),updatedBy:uid,updatedAt:serverTimestamp()};
 const audit=(path,action)=>({path,action,actor:uid,at:serverTimestamp()});
 let batch=writeBatch(db);
 batch.set(ref,schedule);batch.set(doc(db,`auditRecords/schedule_${id}_1`),audit(ref.path,'saveMatchSchedule'));
 await batch.commit();await getDocFromServer(ref);result.auditedScheduleCreate=true;
 await getDocsFromServer(query(collection(db,'matchSchedules'),where('state','==','active'),limit(100)));result.activeScheduleQuery=true;
 batch=writeBatch(db);
 batch.set(progress,{sequence:0,lastMatch:'',phase:'queued',nextAt:new Date('2099-01-01T03:00:00Z'),error:'',updatedAt:serverTimestamp()});
 await batch.commit();result.onlineProgressWrite=true;
 // The distant window prevents an already-online admin runner from publishing this draft.
 batch=writeBatch(db);
 batch.set(doc(db,`matches/${match}`),{...template,startsAt:serverTimestamp(),joined:0,status:'draft',revision:1,cancellationReason:'',independentPrizeTotal:true,prizesEnabled:true,prizeRank:0,prizeMaxRank:1,updatedAt:serverTimestamp(),updatedBy:uid});
 batch.set(doc(db,`matches/${match}/prizes/1`),{rank:1,amountPaisa:100,matchRevision:1,updatedAt:serverTimestamp()});
 batch.set(doc(db,`matches/${match}/history/1`),{from:'none',to:'draft',reason:'',at:serverTimestamp()});
 batch.set(doc(db,`auditRecords/match_${match}_1`),audit(`matches/${match}`,'saveMatch'));
 batch.set(doc(db,`matchSchedules/${id}/runs/1`),{sequence:1,match,createdAt:serverTimestamp(),scheduleRevision:1});
 batch.set(progress,{sequence:1,lastMatch:match,phase:'publishing',nextAt:null,error:'',updatedAt:serverTimestamp()});
 await batch.commit();result.coupledPrivateDraftReservation=true;
 await getDocsFromServer(query(collection(db,`matchSchedules/${id}/runs`),limit(20)));result.generatedHistoryRead=true;
 await denied(()=>writeBatch(db).set(doc(db,`matchSchedules/${id}/runs/1`),{sequence:1,match:'forged',createdAt:serverTimestamp(),scheduleRevision:1}).commit());result.runHistoryImmutable=true;
 batch=writeBatch(db);batch.update(ref,{state:'paused',revision:2,updatedAt:serverTimestamp(),updatedBy:uid});
 batch.set(doc(db,`auditRecords/schedule_${id}_2`),audit(ref.path,'saveMatchSchedule'));await batch.commit();result.pauseSchedule=true;
 if((await getDocFromServer(doc(db,`matches/${match}`))).data()?.status!=='draft')throw Error('Verification match must stay private');
 await owner.delete(`${base}/staffRoles/${uid}`);
 await denied(()=>getDocFromServer(ref));result.revokedStaffDenied=true;
} catch(error) {failure=error;}
finally {
 const user=auth.currentUser;
 // Delete only exact fixture paths created/attempted by this run. Never query-delete production collections.
 const removed=await Promise.allSettled([...cleanup].map(async path=>{
  try{await owner.delete(`${base}/${path}`);}catch(error){if(error.status!==404&&error.context?.response?.statusCode!==404)throw error;}
 }));
 let accountRemoved=!user;
 if(user)try{await deleteUser(user);accountRemoved=true;}catch(error){failure??=error;}
 result.temporaryRecordsRemoved=removed.every(row=>row.status==='fulfilled')&&accountRemoved;
 if(!result.temporaryRecordsRemoved)failure??=Error('Private verification cleanup incomplete');
 await terminate(db);await deleteApp(app);
 result.checkedAt=new Date().toISOString();result.passed=!failure;
 await writeFile(new URL('scheduling-client-check.json',output),JSON.stringify(result,null,2));
}
console.log(JSON.stringify(result));
if(failure)throw failure;
