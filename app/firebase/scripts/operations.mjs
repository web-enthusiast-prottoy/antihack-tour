import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {pathToFileURL} from 'node:url';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
if(process.env.GCLOUD_PROJECT && process.env.GCLOUD_PROJECT!=='demo-arena') throw Error('Local environment required');
const firestoreHost=process.env.FIRESTORE_EMULATOR_HOST||'127.0.0.1:8080';
if(!/^127\.0\.0\.1:\d+$/.test(firestoreHost)) throw Error('Local emulator required');
process.env.FIRESTORE_EMULATOR_HOST=firestoreHost;
process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
initializeApp({projectId:'demo-arena'});
const db=getFirestore();
function checkedRoster(mode, raw, captain) {
 const names=raw??[captain], size={solo:1,duo:2,squad:4}[mode??'solo'];
 if(!size||!Array.isArray(names)||names.length!==size||names[0]!==captain||names.some(n=>typeof n!=='string'||!n.trim()||n!==n.trim()||n.length>80)||new Set(names.map(n=>n.toLowerCase())).size!==size) throw Error('Check roster size and distinct game names.');
 return names;
}
function publishRoster(tx,entry,data,avatar) {
 const publicId=db.collection('participantLinks').doc().id;
 tx.set(db.doc(`participantLinks/${publicId}`),{entry:entry.id});
 tx.set(db.doc(`matches/${data.match}/participants/${publicId}`),{username:data.username,roster:data.roster??[data.username],avatar});
 return {publicId,avatar};
}
export async function operate(uid, action, data) {
 const user=db.doc(`users/${uid}`), wallet=db.doc(`wallets/${uid}`);
 if(action==='deposit') {
  const amount=Number(data.amount),reference=String(data.reference||'').trim().toUpperCase(),sender=String(data.sender||'').trim();
  const requestId=String(data.requestId||reference).trim().toUpperCase();
  if(!Number.isSafeInteger(amount)||amount<=0||amount>10000000||!reference.match(/^[A-Z0-9-]{6,80}$/)||!requestId.match(/^[A-Z0-9-]{6,80}$/)||!sender.match(/^01[3-9][0-9]{8}$/)) throw Error('Enter a valid amount, sending number and transfer reference.');
  const provider='demo-bkash',ref=db.doc(`depositRequests/${uid}_${requestId}`);
  const status=await db.runTransaction(async tx=>{
   const old=await tx.get(ref),instructions=await tx.get(db.doc('paymentInstructions/bkash'));
   const receiver=instructions.get('number')||'01889898989';
   if(old.exists) {
    const d=old.data();
    if(d.uid!==uid||d.amount!==amount||d.reference!==reference||d.sender!==sender||d.provider!==provider||(data.receiver!==undefined&&d.receiver!==data.receiver)) throw Error('This request was saved with different details. Start a new request.');
    return d.status;
   }
   if(data.receiver!==undefined&&data.receiver!==receiver)throw Error('The receiving number changed. Reopen Add Money and check it before submitting.');
   tx.create(ref,{uid,amount,reference,sender,provider,receiver,currency:'BDT',status:'pending',createdAt:FieldValue.serverTimestamp()});return 'pending';
  });
  return status==='pending'?'Deposit submitted for review. No funds have been credited.':`Existing deposit is ${status}. No new credit was created.`;
 }
 if(action==='join') {
  const id=String(data.match||''); if(!id.match(/^[\w-]+$/)) throw Error('Invalid match');
  const match=db.doc(`matches/${id}`), entry=db.doc(`entries/${uid}_${id}`);
  await db.runTransaction(async tx=>{
   const [m,p,w,e]=await Promise.all([tx.get(match),tx.get(user),tx.get(wallet),tx.get(entry)]);
   if(e.exists) return;
   const d=m.data(); if(!d||d.status!=='open'||(d.joined||0)>=d.capacity) throw Error('Match full or closed.');
   if(!p.get('ffUsername')?.trim()) throw Error('Add your Free Fire username first.');
   const roster=checkedRoster(d.mode,data.roster,p.get('ffUsername'));
   if((w.get('balance')||0)<d.entryPaisa) throw Error('Insufficient wallet balance.');
   const published=d.entryPaisa?{}:publishRoster(tx,entry,{match:id,username:p.get('ffUsername'),roster},p.get('avatar')||'solo');
   tx.create(entry,{uid,match:id,username:p.get('ffUsername'),roster,...published,status:d.entryPaisa?'requested':'confirmed',fee:d.entryPaisa,createdAt:FieldValue.serverTimestamp()});
   if(!d.entryPaisa) tx.update(match,{joined:(d.joined||0)+1});
  }); return 'Entry saved. Paid entries await confirmation.';
 }
 if(!['approveDeposit','rejectDeposit','confirmEntry'].includes(action)) throw Error('Unknown action');
 if(!(await db.doc(`staffRoles/${uid}`).get()).get('admin')) throw Error('Staff access required.');
 const path=action==='confirmEntry'?'entries':'depositRequests';
 const id=String(data.id||''); if(!id.match(/^[\w-]+$/)) throw Error('Invalid request');
 const ref=db.doc(`${path}/${id}`);
 await db.runTransaction(async tx=>{
  if(!(await tx.get(db.doc(`staffRoles/${uid}`))).get('admin')) throw Error('Staff access required.');
  const r=await tx.get(ref); if(!r.exists) throw Error('Request missing'); const d=r.data();
  if(d.status===(action==='approveDeposit'?'approved':action==='rejectDeposit'?'rejected':'confirmed')) return;
  if(d.status!==(action==='confirmEntry'?'requested':'pending')) throw Error('Request is no longer pending');
  if(action==='rejectDeposit') {
   const reason=String(data.reason||'').trim();if(!reason||reason.length>1000)throw Error('Enter a rejection or discrepancy reason (up to 1000 characters).');
   tx.update(ref,{status:'rejected',decisionReason:reason,reviewedBy:uid,reviewedAt:FieldValue.serverTimestamp()});
   tx.create(db.doc(`auditRecords/rejectDeposit_${id}`),{action,path:ref.path,actor:uid,at:FieldValue.serverTimestamp(),reason});return;
  }
  const wref=db.doc(`wallets/${d.uid}`), w=await tx.get(wref), balance=w.get('balance')??0;
  if(!Number.isSafeInteger(balance)||balance<0)throw Error('Wallet balance requires reconciliation.');
  if(action==='approveDeposit') {
   if(!Number.isSafeInteger(d.amount)||d.amount<=0||d.amount>10000000||!/^01[3-9][0-9]{8}$/.test(d.receiver)||data.receiptConfirmed!==true||Number(data.verifiedAmount)!==d.amount||String(data.verifiedReference||'').trim().toUpperCase()!==d.reference||String(data.verifiedSender||'').trim()!==d.sender||data.verifiedReceiver!==d.receiver||d.provider!=='demo-bkash'||d.currency!=='BDT')throw Error('Receipt details differ. Reject with a discrepancy reason; do not change the claim.');
   if(!Number.isSafeInteger(balance+d.amount))throw Error('Wallet balance limit exceeded.');
   const unique=db.doc(`paymentReferences/${d.provider}_${d.receiver}_${d.reference}`), used=await tx.get(unique),legacy=await tx.get(db.doc(`paymentReferences/${d.reference}`)),oldScoped=await tx.get(db.doc(`paymentReferences/demo-bkash_LOCAL-TEST-RECEIVER_${d.reference}`));
   if(used.exists||legacy.exists||oldScoped.exists) throw Error('This transfer reference has already been credited.');
   tx.create(unique,{request:id,provider:d.provider,receiver:d.receiver,reference:d.reference,verifiedBy:uid,verifiedAt:FieldValue.serverTimestamp()}); tx.set(wref,{balance:balance+d.amount},{merge:true});
   tx.create(db.doc(`ledger/deposit_${id}`),{uid:d.uid,delta:d.amount,type:'deposit',status:'completed',currency:'BDT',request:id,reference:d.reference,provider:d.provider,receiver:d.receiver,balanceAfter:balance+d.amount,at:FieldValue.serverTimestamp()});
   tx.update(ref,{status:'approved',decisionReason:'Test receipt checked against the submitted claim.',reviewedBy:uid,reviewedAt:FieldValue.serverTimestamp()});
  } else {
   const mref=db.doc(`matches/${d.match}`), m=await tx.get(mref), p=await tx.get(db.doc(`users/${d.uid}`));
   if(m.get('status')!=='open'||(m.get('joined')||0)>=m.get('capacity')) throw Error('Match full or closed');
   checkedRoster(m.get('mode'),d.roster,d.username);
   if(!Number.isSafeInteger(d.fee)||d.fee<0||!p.get('ffUsername')?.trim()||balance<d.fee||m.get('entryPaisa')!==d.fee) throw Error('Profile, funds or entry fee changed.');
   tx.set(wref,{balance:balance-d.fee},{merge:true}); tx.update(mref,{joined:(m.get('joined')||0)+1});
   tx.create(db.doc(`ledger/entry_${id}`),{uid:d.uid,delta:-d.fee,type:'entry_fee',status:'completed',currency:'BDT',match:d.match,request:id,balanceAfter:balance-d.fee,at:FieldValue.serverTimestamp()});
   const published=publishRoster(tx,ref,d,p.get('avatar')||'solo');
   tx.update(ref,{status:'confirmed',reviewedBy:uid,...published});
  }
  tx.create(db.collection('auditRecords').doc(),{action,path:ref.path,actor:uid,at:FieldValue.serverTimestamp()});
 }); return action==='rejectDeposit'?'Rejected. No funds were credited.':'Approved.';
}
const server=createServer(async(req,res)=>{
 res.setHeader('Content-Type','application/json');
 try {
  if(req.method!=='POST') throw Error('POST required');
  const token=(req.headers.authorization||'').replace(/^Bearer /,'');
  const claims=await getAuth().verifyIdToken(token);
  let body=''; for await(const chunk of req) { body+=chunk; if(body.length>4096) throw Error('Request too large'); }
  const {action,...data}=JSON.parse(body);
  res.end(JSON.stringify({message:await operate(claims.uid,action,data)}));
 } catch(e) {res.statusCode=400;res.end(JSON.stringify({error:e.message}));}
});
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) server.listen(8787,'0.0.0.0',()=>console.log('Local Antihack Tour operations on 8787; emulator project demo-arena only.'));
