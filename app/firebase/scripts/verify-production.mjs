import {createRequire} from 'node:module';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506',base=`projects/${project}/databases/(default)/documents`,temporary='/tmp/antihack-production-phone-user.json';
const google=JSON.parse(await readFile(new URL('../../android/app/google-services.json',import.meta.url),'utf8'));
const app=google.client.find(c=>c.client_info.android_client_info.package_name==='bd.arena.prototype');
if(google.project_info.project_id!==project||!app)throw Error('Live Android project mismatch');
const key=app.api_key[0].current_key;
const owner=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const authUrl=method=>`https://identitytoolkit.googleapis.com/v1/accounts:${method}?key=${key}`;
if(process.argv.includes('--cleanup')){
 const user=JSON.parse(await readFile(temporary,'utf8'));
 const wallet=await owner.get(`${base}/wallets/${user.uid}`).catch(e=>{if(e.status===404||e.context?.response?.statusCode===404)return null;throw e;});
 if(wallet)throw Error('Temporary user unexpectedly has a wallet; preserve it for reconciliation.');
 const login=await fetch(authUrl('signInWithPassword'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:user.email,password:user.password,returnSecureToken:true})});
 const fresh=await login.json();if(!login.ok||fresh.localId!==user.uid)throw Error('Cannot reauthenticate the disposable cleanup account');
 const response=await fetch(authUrl('delete'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idToken:fresh.idToken})});
 if(!response.ok)throw Error('Temporary Auth cleanup failed');
 // Login does not create a profile. Delete only this disposable user's optional empty setup document.
 const profile=await owner.get(`${base}/users/${user.uid}`).catch(e=>{if(e.status===404||e.context?.response?.statusCode===404)return null;throw e;});
 if(profile){if(profile.body.fields?.ffUsername?.stringValue)throw Error('Temporary profile has unexpected game data; inspect before deleting.');await owner.delete(`${base}/users/${user.uid}`);}
 await unlink(temporary);console.log(JSON.stringify({temporaryAuthUserRemoved:true,realBalancesChanged:false}));process.exit(0);
}
const rules=new Client({urlPrefix:'https://firebaserules.googleapis.com',apiVersion:'v1'});
const release=(await rules.get(`projects/${project}/releases/cloud.firestore`)).body;
const deployed=(await rules.get(release.rulesetName)).body;
const local=await readFile(new URL('../firestore.rules',import.meta.url),'utf8');
if(!deployed.source.files.some(f=>f.content===local))throw Error('Production rules differ from tested source');
const indexes=(await owner.get(`projects/${project}/databases/(default)/collectionGroups/-/indexes`)).body.indexes??[];
const expected=JSON.parse(await readFile(new URL('../firestore.indexes.json',import.meta.url),'utf8')).indexes;
const ready=expected.map(spec=>({collection:spec.collectionGroup,ready:indexes.some(i=>i.name.includes('/collectionGroups/'+spec.collectionGroup+'/')&&i.state==='READY'&&JSON.stringify(i.fields.filter(f=>f.fieldPath!=='__name__'))===JSON.stringify(spec.fields))}));
if(ready.some(i=>!i.ready))throw Error('Indexes still building: '+JSON.stringify(ready.filter(i=>!i.ready)));
const config=await require('firebase-tools/lib/gcp/identityPlatform').getConfig(project);
if(config.signIn?.email?.enabled!==true)throw Error('Email/password sign-in disabled');
const roles=(await owner.post(`${base}:runQuery`,{structuredQuery:{from:[{collectionId:'staffRoles'}],limit:100}})).body.filter(x=>x.document?.fields?.admin?.booleanValue===true);
const ids=roles.map(x=>x.document.name.split('/').at(-1));if(!ids.length)throw Error('No live administrator role');
const identity=new Client({urlPrefix:'https://identitytoolkit.googleapis.com',apiVersion:'v1'});
const staff=(await identity.post(`projects/${project}/accounts:lookup`,{localId:ids})).body.users??[];
if(!staff.some(u=>!u.disabled&&ids.includes(u.localId)))throw Error('No enabled administrator Auth account');
let user;
try{user=JSON.parse(await readFile(temporary,'utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
if(!user){
 const email=`release-check-${randomUUID()}@example.invalid`,password=randomUUID()+'Aa1!';
 const response=await fetch(authUrl('signUp'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
 const result=await response.json();if(!response.ok)throw Error('Live signup check failed: '+(result.error?.message??response.status));
 user={email,password,uid:result.localId,token:result.idToken};await writeFile(temporary,JSON.stringify(user),{mode:0o600});
}
const headers={Authorization:`Bearer ${user.token}`,'Content-Type':'application/json'};
async function get(path,status=200){const r=await fetch(`https://firestore.googleapis.com/v1/${base}/${path}`,{headers});if(r.status!==status)throw Error(`Client ${path.split('/')[0]} read: ${r.status}`);return status===200?r.json():null;}
const payment=await get('paymentInstructions/bkash'),policy=await get('withdrawalPolicy/bkash');
if(payment.fields?.enabled?.booleanValue!==false||payment.fields?.number?.stringValue!=='')throw Error('Demo receiving number has not been disabled/removed');
if(Number(policy.fields?.minimumPaisa?.integerValue)!==5000||Number(policy.fields?.feePaisa?.integerValue)!==500||policy.fields?.enabled?.booleanValue!==true)throw Error('Owner-approved withdrawal policy mismatch');
await get(`wallets/${user.uid}`,404);await get(`staffRoles/${user.uid}`,404);
const queries=[
 {from:[{collectionId:'categories'}],where:{fieldFilter:{field:{fieldPath:'active'},op:'EQUAL',value:{booleanValue:true}}},limit:100},
 {from:[{collectionId:'matches'}],where:{fieldFilter:{field:{fieldPath:'status'},op:'IN',value:{arrayValue:{values:['open','closed','live','review','settled','cancelled'].map(stringValue=>({stringValue}))}}}},limit:100},
 ...[['walletLedger','at'],['withdrawals','createdAt'],['resultLinks','publishedAt']].map(([collectionId,fieldPath])=>({from:[{collectionId}],where:{fieldFilter:{field:{fieldPath:'uid'},op:'EQUAL',value:{stringValue:user.uid}}},orderBy:[{field:{fieldPath},direction:'DESCENDING'}],limit:20}))
];
for(const structuredQuery of queries){const r=await fetch(`https://firestore.googleapis.com/v1/${base}:runQuery`,{method:'POST',headers,body:JSON.stringify({structuredQuery})});if(!r.ok)throw Error(`Live ${structuredQuery.from[0].collectionId} query failed: ${r.status} ${await r.text()}`);}
for(const [path,fields] of [[`wallets/${user.uid}`,{balance:{integerValue:'1'}}],[`staffRoles/${user.uid}`,{admin:{booleanValue:true}}]]){
 const r=await fetch(`https://firestore.googleapis.com/v1/${base}:commit`,{method:'POST',headers,body:JSON.stringify({writes:[{update:{name:`${base}/${path}`,fields}}]})});if(r.status!==403)throw Error('Unauthorized write was not denied: '+path.split('/')[0]);
}
const result={project,rulesMatchTestedSource:true,rulesSha256:createHash('sha256').update(local).digest('hex'),allRequiredIndexesReady:true,indexCount:expected.length,emailPasswordEnabled:true,enabledStaffAccountVerified:true,temporaryLiveSignupVerified:true,publicAndPrivateClientQueriesVerified:true,forgedWalletAndRoleWritesDenied:true,withdrawalMinimumTaka:50,withdrawalFeeTaka:5,withdrawalsEnabled:true,demoDepositRecipientRemoved:true,depositsAwaitRealAdminNumber:true,temporaryPhoneUserPrepared:true,realBalancesChanged:false};
await writeFile('/tmp/antihack-production-verification.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
