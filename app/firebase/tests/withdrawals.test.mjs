import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,setDoc,updateDoc,getDocs,collection,query,where,limit,runTransaction,writeBatch,serverTimestamp}=require('firebase/firestore');
let env;
const id='A'.repeat(20),otherId='B'.repeat(20);
const client=(uid='player')=>env.authenticatedContext(uid).firestore();
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-arena-withdrawals',firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env.clearFirestore();await env.cleanup();});
beforeEach(async()=>{
 await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(),batch=writeBatch(db);
  batch.set(doc(db,'staffRoles/operator'),{admin:true});batch.set(doc(db,'staffRoles/other'),{admin:true});
  batch.set(doc(db,'users/player'),{ffUsername:'Sample winner',ffUid:''});
  batch.set(doc(db,'wallets/player'),{balance:50000,winnings:100000,reservedWinnings:0});
  batch.set(doc(db,'withdrawalPolicy/bkash'),{minimumPaisa:10000,feePaisa:500,enabled:true,currency:'BDT',revision:1,updatedAt:new Date()});
  await batch.commit();
 });
});
async function request(requestId=id,amount=20000,options={}) {
 const db=client(),ref=doc(db,`withdrawals/${requestId}`);
 return runTransaction(db,async tx=>{
  const old=await tx.get(ref);
  if(old.exists()){assert.equal(old.data().amountPaisa,amount);return 'existing';}
  const policy=(await tx.get(doc(db,'withdrawalPolicy/bkash'))).data(),walletRef=doc(db,'wallets/player'),wallet=(await tx.get(walletRef)).data(),total=amount+policy.feePaisa;
  const record={uid:'player',username:'Sample winner',requestId,provider:'bkash',currency:'BDT',amountPaisa:amount,feePaisa:policy.feePaisa,totalPaisa:total,minimumPaisa:policy.minimumPaisa,policyRevision:policy.revision,destination:'01700000000',status:'pending',revision:1,createdAt:serverTimestamp(),...options.record};
  if(options.omit!=='request')tx.set(ref,record);
  if(options.omit!=='wallet')tx.update(walletRef,{winnings:wallet.winnings-total,reservedWinnings:wallet.reservedWinnings+total,lastWithdrawal:requestId,...options.wallet});
  return 'created';
 });
}
async function decide(requestId=id,target='approved',options={}) {
 const actor=options.actor??'operator',db=client(actor),ref=doc(db,`withdrawals/${requestId}`);
 return runTransaction(db,async tx=>{
  const old=(await tx.get(ref)).data();
  if(old.status===target)return 'existing';
  const walletRef=doc(db,`wallets/${old.uid}`),wallet=(await tx.get(walletRef)).data(),revision=old.revision+1;
  const patch={status:target,revision,decidedAt:serverTimestamp(),decidedBy:actor,reason:target==='rejected'?'Sample confirmed no transfer.':'',...options.patch};
  if(target==='rejected')patch.noTransferConfirmed=options.noTransfer??true;
  if(target==='sent')patch.payoutReference=options.reference??'SAMPLEPAYOUT1';
  if(options.omit!=='request')tx.update(ref,patch);
  if(target!=='approved'&&options.omit!=='wallet')tx.update(walletRef,{reservedWinnings:wallet.reservedWinnings-old.totalPaisa,winnings:wallet.winnings+(target==='rejected'?old.totalPaisa:0),lastWithdrawal:requestId,...options.wallet});
  if(target==='sent') {
   if(options.omit!=='payout')tx.set(doc(db,`payoutReferences/bkash_${patch.payoutReference}`),{withdrawal:requestId,amountPaisa:old.amountPaisa,destination:old.destination,at:serverTimestamp()});
   if(options.omit!=='ledger')tx.set(doc(db,`walletLedger/withdrawal_${requestId}`),{uid:old.uid,delta:-old.totalPaisa,type:'withdrawal',status:'completed',currency:'BDT',withdrawal:requestId,reference:patch.payoutReference,balanceAfter:wallet.winnings,at:serverTimestamp(),...options.ledger});
  }
  if(options.omit!=='audit')tx.set(doc(db,`auditRecords/withdrawal_${requestId}_${revision}`),{action:target==='approved'?'approveWithdrawal':target==='sent'?'sendWithdrawal':'rejectWithdrawal',path:`withdrawals/${requestId}`,actor,at:serverTimestamp()});
  return 'decided';
 });
}
const wallet=async()=>(await getDoc(doc(client(),'wallets/player'))).data();
test('requests reserve winnings plus the disclosed fee, preserve deposits and retry once',async()=>{
 await assertSucceeds(request());assert.equal(await request(),'existing');
 assert.deepEqual(await wallet(),{balance:50000,winnings:79500,reservedWinnings:20500,lastWithdrawal:id});
});
test('approval holds funds, sent finalizes once with a unique transfer reference and ledger',async()=>{
 await request();await assertSucceeds(decide());assert.equal((await wallet()).reservedWinnings,20500);
 await assertSucceeds(decide(id,'sent'));assert.equal(await decide(id,'sent'),'existing');
 assert.deepEqual(await wallet(),{balance:50000,winnings:79500,reservedWinnings:0,lastWithdrawal:id});
 const ledger=(await getDoc(doc(client(),'walletLedger/withdrawal_'+id))).data();assert.equal(ledger.delta,-20500);assert.equal(ledger.reference,'SAMPLEPAYOUT1');
});
test('rejection needs a reason and confirmed no-transfer, then releases the reservation once',async()=>{
 await request();await assertFails(decide(id,'rejected',{noTransfer:false}));await assertFails(decide(id,'rejected',{patch:{reason:''}}));
 await assertSucceeds(decide(id,'rejected'));assert.equal(await decide(id,'rejected'),'existing');
 assert.equal((await wallet()).winnings,100000);assert.equal((await wallet()).reservedWinnings,0);
 await request(otherId);await decide(otherId);await assertSucceeds(decide(otherId,'rejected'));
 assert.equal((await wallet()).winnings,100000);
});
test('partial requests, forged policy/identity/fees and use of deposits are denied',async()=>{
 for(const options of [{omit:'wallet'},{omit:'request'},{record:{feePaisa:0}},{record:{totalPaisa:1}},{record:{uid:'other'}},{record:{destination:'wrong'}},{wallet:{balance:99999}}])await assertFails(request(id,20000,options));
 await assertFails(request(id,9999));await assertFails(request(id,100000));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'wallets/player'),{winnings:0}));
 await assertFails(request());assert.equal((await wallet()).balance,50000);
});
test('partial decisions and direct pending-to-sent cannot release or mark money as sent',async()=>{
 await request();await assertFails(decide(id,'sent'));await decide();
 for(const omit of ['wallet','request','ledger','payout','audit'])await assertFails(decide(id,'sent',{omit}));
 for(const omit of ['wallet','request','audit'])await assertFails(decide(id,'rejected',{omit}));
 assert.equal((await wallet()).reservedWinnings,20500);
});
test('sent records are immutable and one provider transfer cannot pay two requests',async()=>{
 await request();await decide();await decide(id,'sent');await request(otherId);await decide(otherId);
 await assertFails(decide(otherId,'sent'));await assertFails(decide(id,'rejected'));
 await assertFails(updateDoc(doc(client('operator'),`withdrawals/${id}`),{destination:'01900000000'}));
 assert.equal((await wallet()).reservedWinnings,20500);
});
test('policy changes stop new requests but do not rewrite an existing request or its retry',async()=>{
 await request();await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'withdrawalPolicy/bkash'),{enabled:false,feePaisa:900,revision:2}));
 assert.equal(await request(),'existing');await assertFails(request(otherId));await decide();await assertSucceeds(decide(id,'sent'));
 assert.equal((await wallet()).winnings,79500);
});
test('players cannot review requests or read another user payout number',async()=>{
 await request();await assertFails(decide(id,'approved',{actor:'player'}));
 await assertFails(getDoc(doc(client('stranger'),`withdrawals/${id}`)));
 await assertSucceeds(getDocs(query(collection(client(),'withdrawals'),where('uid','==','player'),limit(20))));
 await assertFails(getDocs(query(collection(client('stranger'),'withdrawals'),where('uid','==','player'),limit(20))));
 await assertFails(getDocs(query(collection(client(),'withdrawals'),where('uid','==','player'))));
});
test('only staff can change exact minimum and fee settings with a revision and matching audit',async()=>{
 async function policy(actor,patch={},omitAudit=false){
  const db=client(actor),batch=writeBatch(db);
  batch.set(doc(db,'withdrawalPolicy/bkash'),{minimumPaisa:20000,feePaisa:0,enabled:false,currency:'BDT',revision:2,updatedAt:serverTimestamp(),...patch});
  if(!omitAudit)batch.set(doc(db,'auditRecords/withdrawal_policy_bkash_2'),{action:'saveWithdrawalPolicy',path:'withdrawalPolicy/bkash',actor,at:serverTimestamp()});
  return batch.commit();
 }
 await assertFails(policy('player'));await assertFails(policy('operator',{},true));
 for(const patch of [{minimumPaisa:0},{minimumPaisa:1.5},{feePaisa:-1},{feePaisa:1.5},{minimumPaisa:9007199254740991,feePaisa:1},{revision:3},{currency:'USD'}])await assertFails(policy('operator',patch));
 await assertSucceeds(policy('operator'));
 await assertFails(setDoc(doc(client('operator'),'auditRecords/withdrawal_policy_bkash_3'),{action:'saveWithdrawalPolicy',path:'withdrawalPolicy/bkash',actor:'operator',at:serverTimestamp()}));
});
test('decisions cannot forge wallet amounts, immutable request details or payout ledger totals',async()=>{
 await request();await assertFails(decide(id,'approved',{omit:'audit'}));
 await assertFails(decide(id,'approved',{patch:{revision:100}}));
 await assertFails(decide(id,'approved',{patch:{amountPaisa:1}}));await decide();
 for(const options of [{wallet:{winnings:99999}},{wallet:{reservedWinnings:0,balance:99999}},{ledger:{delta:-1}},{ledger:{uid:'other'}},{reference:'invalid'}])await assertFails(decide(id,'sent',options));
 await assertFails(decide(id,'rejected',{patch:{reason:'   '}}));
 await assertFails(setDoc(doc(client('operator'),'auditRecords/withdrawal_'+id+'_3'),{action:'sendWithdrawal',path:'withdrawals/'+id,actor:'operator',at:serverTimestamp()}));
 assert.equal((await wallet()).reservedWinnings,20500);
});
async function retryRace(operation) {for(let i=0;;i++)try{return await operation();}catch(error){if(i>=2||error.code!=='permission-denied')throw error;}}
test('concurrent reservations cannot overspend and racing sent decisions debit once',async()=>{
 const claims=await Promise.allSettled([retryRace(()=>request(id,60000)),retryRace(()=>request(otherId,60000))]);
 assert.equal(claims.filter(r=>r.status==='fulfilled').length,1);
 const winner=claims[0].status==='fulfilled'?id:otherId;await decide(winner);
 await Promise.all([retryRace(()=>decide(winner,'sent')),retryRace(()=>decide(winner,'sent',{actor:'other'}))]);
 assert.equal((await wallet()).winnings,39500);assert.equal((await wallet()).reservedWinnings,0);assert.equal((await wallet()).balance,50000);
});
