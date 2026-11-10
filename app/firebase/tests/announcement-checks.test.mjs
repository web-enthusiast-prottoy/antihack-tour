import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDocs,collection,query,where,orderBy,limit,startAfter,Timestamp,writeBatch}=require('firebase/firestore');
let env;
const since=new Timestamp(100,10);
before(async()=>{
 env=await initializeTestEnvironment({projectId:'demo-arena-announcement-checks',firestore:{host:'127.0.0.1',port:Number(process.env.ARENA_RULES_PORT??8080),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async context=>{
  const db=context.firestore(),batch=writeBatch(db);
  for(let i=0;i<63;i++)batch.set(doc(db,`notices/sample-${i}`),{published:true,revision:1,title:'Local sample',body:'Local sample announcement',updatedAt:since});
  batch.set(doc(db,'notices/older'),{published:true,revision:1,updatedAt:new Timestamp(99,0)});
  batch.set(doc(db,'notices/hidden'),{published:false,revision:2,updatedAt:new Timestamp(101,0)});
  await batch.commit();
 });
});
after(async()=>{await env?.clearFirestore();await env?.cleanup();});
test('guest background checks page every matching published notice including timestamp ties',async()=>{
 const db=env.unauthenticatedContext().firestore();
 const changes=query(collection(db,'notices'),where('published','==',true),where('updatedAt','>=',since),orderBy('updatedAt','desc'));
 const first=await getDocs(query(changes,limit(50)));
 const second=await getDocs(query(changes,startAfter(first.docs.at(-1)),limit(50)));
 assert.equal(first.size,50);assert.equal(second.size,13);
 const ids=new Set([...first.docs,...second.docs].map(d=>d.id));
 assert.equal(ids.size,63);assert.equal(ids.has('older'),false);assert.equal(ids.has('hidden'),false);
});
test('background clients still cannot query drafts or omit the read bound',async()=>{
 const db=env.unauthenticatedContext().firestore();
 await assertFails(getDocs(query(collection(db,'notices'),where('updatedAt','>=',since),limit(50))));
 await assertFails(getDocs(query(collection(db,'notices'),where('published','==',true),where('updatedAt','>=',since))));
});
