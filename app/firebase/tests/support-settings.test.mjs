import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,limit,writeBatch,serverTimestamp,deleteDoc}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-support-settings',firestore:{host:'127.0.0.1',port:Number(process.env.PAID_TEST_PORT??8080),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env.clearFirestore();await env.cleanup();});
const staff=()=>env.authenticatedContext('operator').firestore(),guest=()=>env.unauthenticatedContext().firestore();
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'staffRoles/operator'),{admin:true}));});
async function save(db,number='01703883397',revision=1,audit=true,extra={}){
 const batch=writeBatch(db),ref=doc(db,'supportSettings/whatsapp');
 batch.set(ref,{number,revision,updatedAt:serverTimestamp(),...extra});
 if(audit)batch.set(doc(db,'auditRecords/support_whatsapp_'+revision),{action:'saveSupportSettings',path:ref.path,actor:'operator',at:serverTimestamp()});
 return batch.commit();
}
test('staff save an audited support number readable before sign-in',async()=>{
 await assertSucceeds(getDoc(doc(guest(),'supportSettings/whatsapp')));
 await assertSucceeds(save(staff()));await assertSucceeds(save(staff(),'01812345678',2));
 assert.equal((await getDoc(doc(guest(),'supportSettings/whatsapp'))).data().number,'01812345678');
});
test('players cannot redirect support or remove its settings',async()=>{
 const player=env.authenticatedContext('player').firestore();await assertFails(save(player));
 await save(staff());await assertFails(deleteDoc(doc(player,'supportSettings/whatsapp')));await assertFails(deleteDoc(doc(staff(),'supportSettings/whatsapp')));
});
test('missing audit, stale revisions, invalid numbers and private fields are rejected',async()=>{
 await assertFails(save(staff(),'01703883397',1,false));
 for(const number of ['','+8801703883397','https://example.com','01203883397','01703883397?text=hi'])await assertFails(save(staff(),number));
 await assertFails(save(staff(),'01703883397',1,true,{uid:'private'}));
 await save(staff());await assertFails(save(staff(),'01812345678',1));
 assert.equal((await getDoc(doc(guest(),'supportSettings/whatsapp'))).data().number,'01703883397');
});
test('only the fixed public document can be read',async()=>{
 await assertFails(getDoc(doc(guest(),'supportSettings/private')));await assertFails(getDocs(collection(guest(),'supportSettings')));
});
