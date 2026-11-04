import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Owner sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506';
const rules=new Client({urlPrefix:'https://firebaserules.googleapis.com',apiVersion:'v1'});
const release=(await rules.get(`projects/${project}/releases/cloud.firestore`)).body;
const deployed=(await rules.get(release.rulesetName)).body;
const local=await readFile(new URL('../firestore.rules',import.meta.url),'utf8');
const source=deployed.source.files.find(f=>f.name.endsWith('firestore.rules'))?.content;
if(source!==local)throw Error('Deployed rules do not match tested source');
const config=await require('firebase-tools/lib/gcp/identityPlatform').getConfig(project);
const result={project,ruleset:release.rulesetName,rulesMatchLocal:true,sourceSha256:createHash('sha256').update(local).digest('hex'),emailPasswordEnabled:config.signIn?.email?.enabled===true};
if(!result.emailPasswordEnabled)throw Error('Email/password authentication is disabled');
const google=JSON.parse(await readFile(new URL('../../android/app/google-services.json',import.meta.url),'utf8'));
const client=google.client.find(c=>c.client_info.android_client_info.package_name==='bd.arena.prototype');
const key=client.api_key[0].current_key;
let token,uid,cleanupError;
const owner=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
try{
 const signUp=await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${key}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:`wallet-check-${randomUUID()}@example.invalid`,password:randomUUID()+'Aa1!',returnSecureToken:true})});
 const data=await signUp.json();if(!signUp.ok)throw Error('Client authentication check failed: '+(data.error?.message||signUp.status));
 token=data.idToken;uid=data.localId;result.clientSignUp=true;
 const path=`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
 const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
 const setting=await fetch(`${path}/paymentInstructions/bkash`,{headers});
 if(![200,404].includes(setting.status))throw Error('Player cannot read receiving-number setting: '+setting.status);
 result.playerInstructionsRead=true;
 if(setting.status===200){const body=await setting.json();result.receivingNumber=body.fields?.number?.stringValue;}else result.receivingNumber='01889898989';
 const wallet=await fetch(`${path}/wallets/${uid}`,{headers});if(![200,404].includes(wallet.status))throw Error('Player wallet read failed: '+wallet.status);result.playerWalletRead=true;
 const forged=await fetch(`${path}:commit`,{method:'POST',headers,body:JSON.stringify({writes:[{update:{name:`projects/${project}/databases/(default)/documents/wallets/${uid}`,fields:{balance:{integerValue:'1'}}}}]})});
 if(forged.status!==403)throw Error('Independent player balance write was not denied: '+forged.status);
 result.playerBalanceForgeryDenied=true;
}finally{
 if(uid){try{await owner.delete(`projects/${project}/databases/(default)/documents/wallets/${uid}`)}catch(e){if(e.status!==404&&e.context?.response?.statusCode!==404)cleanupError=e; }}
 if(token){const removed=await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${key}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idToken:token})});result.temporaryAuthUserRemoved=removed.ok;if(!removed.ok)throw Error('Temporary auth-user cleanup failed');}
}
if(cleanupError)throw cleanupError;
await writeFile('/tmp/arena-live-wallet-verification.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
