// Uses disposable private records only; never changes a real player, match, wallet or payment.
import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506';
const base=`projects/${project}/databases/(default)/documents`;
const owner=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const config=JSON.parse(await readFile(new URL('../../android/app/google-services.json',import.meta.url),'utf8'));
if(config.project_info.project_id!==project)throw Error('Wrong project');
const key=config.client.find(c=>c.client_info.android_client_info.package_name==='bd.arena.prototype').api_key[0].current_key;
const authUrl=method=>`https://identitytoolkit.googleapis.com/v1/accounts:${method}?key=${key}`;
const output=new URL('moderation-client-check.json',new URL('../../verification/moderation-live-2026-11-06/',import.meta.url));
const result={project,realPlayersChanged:false,realMatchesChanged:false,realBalancesChanged:false};
const match=`moderation-verification-${randomUUID()}`;
let user;
try {
 const email=`moderation-verification-${randomUUID()}@example.invalid`,password=randomUUID()+'Aa1!';
 const response=await fetch(authUrl('signUp'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
 user=await response.json();if(!response.ok||!user.idToken)throw Error('Temporary live signup failed');
 const uid=user.localId;
 async function client(path,expected,method='GET',body){
  const r=await fetch(`https://firestore.googleapis.com/v1/${base}/${path}`,{method,headers:{Authorization:`Bearer ${user.idToken}`,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
  if(r.status!==expected)throw Error(`Expected ${expected}, received ${r.status} for ${method} ${path.split('/')[0]}`);
  return r;
 }
 const fields={email:{stringValue:email},ffUsername:{stringValue:''},ffUid:{stringValue:''}};
 await client(`users/${uid}`,200,'PATCH',{fields});
 await client(`accountBans/${uid}`,404);result.unbannedAccountCanCheckAccess=true;
 const reason='Temporary release verification: confirmed match rule violation.';
 const banFields={uid:{stringValue:uid},active:{booleanValue:true},reason:{stringValue:reason},revision:{integerValue:'1'},updatedAt:{timestampValue:new Date().toISOString()},updatedBy:{stringValue:'release-verification'}};
 // Privileged fixture setup, scoped to the temporary UID; staff writes are tested through client rules locally.
 await owner.patch(`${base}/accountBans/${uid}`,{fields:banFields});
 const ban=await (await client(`accountBans/${uid}`,200)).json();
 if(ban.fields.reason.stringValue!==reason)throw Error('Ban reason did not round-trip');
 result.bannedPlayerCanReadOwnReason=true;
 await client(`users/${uid}`,403);await client(`users/${uid}`,403,'PATCH',{fields});result.bannedPrivateReadAndWriteDenied=true;
 await client(`wallets/${uid}`,403,'PATCH',{fields:{balance:{integerValue:'100'}}});result.bannedWalletForgeryDenied=true;
 await client(`accountBans/other-${randomUUID()}`,403);result.otherAccountReasonPrivate=true;
 await owner.patch(`${base}/matches/${match}/bans/${uid}`,{fields:banFields});
 const matchBan=await (await client(`matches/${match}/bans/${uid}`,200)).json();
 if(matchBan.fields.reason.stringValue!==reason)throw Error('Match reason did not round-trip');
 result.ownMatchReasonReadableWhileAccountBanned=true;
 await owner.patch(`${base}/accountBans/${uid}`,{fields:{...banFields,active:{booleanValue:false},revision:{integerValue:'2'},reason:{stringValue:'Release verification complete.'}}});
 await client(`users/${uid}`,200);result.liftingAccountBanRestoresPrivateRead=true;
} finally {
 if(user?.idToken){
  const cleanup=await Promise.allSettled([
   ...[`accountBans/${user.localId}`,`matches/${match}/bans/${user.localId}`,`users/${user.localId}`].map(async path=>{
    try{await owner.delete(`${base}/${path}`);}catch(error){if(error.status!==404&&error.context?.response?.statusCode!==404)throw error;}
   }),
   (async()=>{const r=await fetch(authUrl('delete'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idToken:user.idToken})});if(!r.ok)throw Error('Temporary Auth cleanup failed');})()
  ]);
  result.temporaryRecordsRemoved=cleanup.every(r=>r.status==='fulfilled');
  if(!result.temporaryRecordsRemoved)throw Error(`Temporary verification cleanup incomplete for UID ${user.localId}`);
 }
 result.checkedAt=new Date().toISOString();await writeFile(output,JSON.stringify(result,null,2));
}
console.log(JSON.stringify(result));
