import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment}=require('@firebase/rules-unit-testing');
// Explicitly local; never use this fixture against a real Firebase project.
const env=await initializeTestEnvironment({projectId:'demo-arena',firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
const {doc,collection,query,where,getDocs,writeBatch}=require('firebase/firestore');
try {
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();
  const old=await getDocs(query(collection(db,'entries'),where('match','==','device-free-registration')));
  if(old.size>498) throw Error('Too many old fixture entries; clean up this local fixture before resetting it.');
  const batch=writeBatch(db);
  old.forEach(entry=>batch.delete(entry.ref));
  batch.set(doc(db,'matches','device-free-registration'),{
   title:'Device free registration',description:'Local automated acceptance fixture.',image:'solo',mode:'solo',map:'Bermuda',
   startsAt:new Date('2030-01-01T14:00:00Z'),capacity:3,joined:0,entryPaisa:0,prizePaisa:0,perKillPaisa:0,
   rules:'Local test only.',cancellationTerms:'No money involved.',status:'open',updatedAt:new Date(),updatedBy:'device-fixture'
  });
  await batch.commit();
 });
} finally { await env.cleanup(); }
