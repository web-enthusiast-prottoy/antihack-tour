import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment}=require('@firebase/rules-unit-testing');
const {doc,setDoc}=require('firebase/firestore');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
const app=initializeApp({projectId:'demo-arena'});
const auth=getAuth(app),email='room-release@arena.test';
const user=await auth.getUserByEmail(email).catch(()=>auth.createUser({email,password:'RoomLocal123!'}));
const env=await initializeTestEnvironment({projectId:'demo-arena',firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
try {await env.withSecurityRulesDisabled(async c=>{
 const db=c.firestore();
 await setDoc(doc(db,'matches/room-release-auto'),{title:'Local timed room sample',status:'open'});
 await setDoc(doc(db,`entries/${user.uid}_room-release-auto`),{uid:user.uid,match:'room-release-auto',status:'confirmed'});
 await setDoc(doc(db,'rooms/room-release-auto'),{roomId:'TIMED-SAMPLE-ROOM',password:'TIMED-SAMPLE-PASS',enabled:true,releaseAt:new Date(Date.now()+3600000)});
});} finally {await env.cleanup();await deleteApp(app);}
