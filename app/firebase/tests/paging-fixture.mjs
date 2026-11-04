import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment}=require('@firebase/rules-unit-testing');
const {doc,writeBatch}=require('firebase/firestore');
const {initializeApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
// Dedicated disposable project; never clears the interactive preview's demo-arena data.
const projectId='demo-arena-paging';
process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
// The Auth emulator routes API-key sign-in to its default demo-arena project.
const app=initializeApp({projectId:'demo-arena'});
const auth=getAuth(app);
const user=await auth.getUserByEmail('admin@arena.test');
const env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
try {
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db),now=new Date();
  batch.set(doc(db,'staffRoles',user.uid),{admin:true,owner:true});
  for(let i=0;i<205;i++) {
   const id='page'+String(i).padStart(3,'0');
   batch.set(doc(db,'categories',id),{name:`Sample category ${i}`,order:i,active:true,updatedAt:now,updatedBy:user.uid});
   batch.set(doc(db,'matches',id),{title:`Sample match ${i}`,description:'Disposable paging test.',image:'solo',mode:'solo',map:'Bermuda',startsAt:now,joined:0,capacity:48,entryPaisa:0,prizePaisa:0,perKillPaisa:0,rules:'Sample only.',cancellationTerms:'Sample only.',status:'open',revision:2,cancellationReason:'',updatedAt:now,updatedBy:user.uid});
  }
  batch.set(doc(db,'categories','hidden'),{name:'Hidden sample',order:0,active:false,updatedAt:now,updatedBy:user.uid});
  batch.set(doc(db,'matches','hidden'),{status:'draft'});
  await batch.commit();
 });
 console.log('Dedicated paging project: 205 public matches and categories, hidden records and local staff role.');
} finally {await env.cleanup();}
