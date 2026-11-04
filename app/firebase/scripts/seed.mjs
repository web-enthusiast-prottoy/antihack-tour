import { createRequire } from 'node:module';
const require = createRequire(new URL('../../admin/package.json', import.meta.url));
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
// This script intentionally cannot provision a production project.
if (process.env.GCLOUD_PROJECT && process.env.GCLOUD_PROJECT !== 'demo-arena') throw Error('Local demo only');
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
initializeApp({projectId:'demo-arena'});
const email='admin@arena.test';
const auth=getAuth();
const user=await auth.getUserByEmail(email).catch(()=>auth.createUser({email,password:'ArenaLocal123!'}));
await getFirestore().doc(`staffRoles/${user.uid}`).set({notices:true,admin:true,owner:true});
const ref=getFirestore().doc('notices/welcome');
if (!(await ref.get()).exists) await ref.set({title:'Welcome to Antihack Tour',body:'Local announcements are ready. Your admin can share match updates here.',published:true,createdAt:FieldValue.serverTimestamp(),updatedAt:FieldValue.serverTimestamp(),createdBy:user.uid,updatedBy:user.uid,revision:1});
console.log('Local demo publisher ready. Sign in: admin@arena.test / ArenaLocal123!');

// Clearly labelled, disposable preview records; never overwrite staff edits.
const db=getFirestore();
const fixtures={
 'categories/sample-full-map':{name:'Full Map',order:0,active:true,updatedAt:FieldValue.serverTimestamp(),updatedBy:user.uid},
 'categories/sample-clash-squad':{name:'Clash Squad',order:1,active:true,updatedAt:FieldValue.serverTimestamp(),updatedBy:user.uid},
 'categories/sample-duo-battle':{name:'Duo Battle',order:2,active:true,updatedAt:FieldValue.serverTimestamp(),updatedBy:user.uid},
 ['users/'+user.uid]:{ffUsername:'Local owner',ffUid:''},
 'matches/sample-solo':{title:'Sample · Island Solo',categoryId:'sample-full-map',mode:'solo',image:'solo',map:'Bermuda',description:'Local sample match for design verification.',rules:'Sample rules. No real tournament or money.',cancellationTerms:'Local sample only.',startsAt:new Date(Date.now()+86400000),joined:0,capacity:48,entryPaisa:0,prizePaisa:100000,perKillPaisa:500,status:'open',revision:2,cancellationReason:'',updatedAt:FieldValue.serverTimestamp(),updatedBy:user.uid},
 'matches/sample-squad':{title:'Sample · Clash Squad',categoryId:'sample-clash-squad',mode:'squad',image:'squad',map:'Bermuda',description:'Local sample match for design verification.',rules:'Sample rules. No real tournament or money.',cancellationTerms:'Local sample only.',startsAt:new Date(Date.now()+172800000),joined:4,capacity:12,entryPaisa:5000,prizePaisa:200000,perKillPaisa:0,status:'open',revision:2,cancellationReason:'',updatedAt:FieldValue.serverTimestamp(),updatedBy:user.uid},
 'matches/sample-duo':{title:'Sample · Duo Survival',categoryId:'sample-duo-battle',mode:'duo',image:'duo',map:'Purgatory',description:'Local sample match for design verification.',rules:'Sample rules. No real tournament or money.',cancellationTerms:'Local sample only.',startsAt:new Date(Date.now()+129600000),joined:2,capacity:24,entryPaisa:2000,prizePaisa:80000,perKillPaisa:200,status:'open',revision:2,cancellationReason:'',updatedAt:FieldValue.serverTimestamp(),updatedBy:user.uid},
 'results/sample-result':{title:'Sample · Completed Solo',titleSearch:'sample · completed solo',match:'sample-completed',mode:'solo',map:'Bermuda',startsAt:new Date(),username:'SamplePlayer',avatar:'solo',placement:1,kills:5,awardPaisa:0,creditedPaisa:0,settlementStatus:'not_applicable',publishedAt:FieldValue.serverTimestamp()},
 'matches/sample-solo/participants/sample-player':{username:'SamplePlayer',roster:['SamplePlayer'],avatar:'solo'},
 ['wallets/'+user.uid]:{balance:25000},
 ['ledger/sample-credit-'+user.uid]:{uid:user.uid,delta:25000,type:'deposit',status:'completed',currency:'BDT',request:'sample-deposit',reference:'SAMPLE123',at:FieldValue.serverTimestamp()}
};
for(const [path,value] of Object.entries(fixtures)){const ref=db.doc(path);if(!(await ref.get()).exists)await ref.set(value);}
console.log('Labelled local preview categories, matches, results and wallet activity ready.');
