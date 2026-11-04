import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth');
const {Client}=require('firebase-tools/lib/apiv2');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase owner sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506';
const client=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const base=`projects/${project}/databases/(default)/documents`;
for(const collection of ['staffRoles','wallets','walletDeposits','verifiedReceipts','paymentReferences']){
 const response=await client.post(`${base}:runQuery`,{structuredQuery:{from:[{collectionId:collection}],limit:20}});
 const docs=response.body.filter(row=>row.document).map(row=>row.document);
 console.log(JSON.stringify({collection,sampleCount:docs.length,...(collection==='staffRoles'?{admins:docs.filter(d=>d.fields?.admin?.booleanValue===true).length}:{} )}));
 if(collection==='staffRoles')await writeFile('/tmp/arena-live-staff-inspection.json',JSON.stringify(docs),{mode:0o600});
}
const rules=new Client({urlPrefix:'https://firebaserules.googleapis.com',apiVersion:'v1'});
try{
 const release=await rules.get(`projects/${project}/releases/cloud.firestore`);
 const existing=await rules.get(release.body.rulesetName);
 await writeFile('/tmp/arena-pre-wallet-rules-backup.json',JSON.stringify(existing.body),{mode:0o600});
 console.log(JSON.stringify({rulesBackupSaved:true}));
}catch(e){console.log(JSON.stringify({rulesBackupSaved:false,error:e.message}));throw e;}
