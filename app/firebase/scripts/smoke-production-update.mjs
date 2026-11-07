import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506',base=`projects/${project}/databases/(default)/documents`;
const owner=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const config=JSON.parse(await readFile(new URL('../../android/app/google-services.json',import.meta.url),'utf8'));
if(config.project_info.project_id!==project)throw Error('Wrong project');
const key=config.client.find(c=>c.client_info.android_client_info.package_name==='bd.arena.prototype').api_key[0].current_key;
const authUrl=method=>`https://identitytoolkit.googleapis.com/v1/accounts:${method}?key=${key}`;
const roles=(await owner.post(`${base}:runQuery`,{structuredQuery:{from:[{collectionId:'staffRoles'}],limit:100}})).body.filter(row=>row.document?.fields?.admin?.booleanValue===true).map(row=>row.document.name.split('/').at(-1));
const identity=new Client({urlPrefix:'https://identitytoolkit.googleapis.com',apiVersion:'v1'});
const staff=roles.length?(await identity.post(`projects/${project}/accounts:lookup`,{localId:roles})).body.users??[]:[];
if(!staff.some(u=>!u.disabled))throw Error('No enabled staff account');
const email=`deployment-check-${randomUUID()}@example.invalid`,password=randomUUID()+'Aa1!';
let user,profileCreated=false;
const result={project,enabledStaffAccount:true};
try {
 const signup=await fetch(authUrl('signUp'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
 user=await signup.json();if(!signup.ok||!user.idToken)throw Error('Live signup failed');
 const uid=user.localId,headers={Authorization:`Bearer ${user.idToken}`,'Content-Type':'application/json'};
 async function request(path,method='GET',body,expected=[200],authenticated=true){
  const response=await fetch(`https://firestore.googleapis.com/v1/${base}${path}`,{method,headers:authenticated?headers:{'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
  if(!expected.includes(response.status))throw Error(`Unexpected ${method} status ${response.status} at ${path.split('/')[1]}`);
  return response;
 }
 await request('/supportSettings/whatsapp','GET',null,[200,404],false);result.publicWhatsAppRead=true;
 const fields={ffUsername:{stringValue:''},ffUid:{stringValue:''},email:{stringValue:email}};
 await request(`/users/${uid}`,'PATCH',{fields});profileCreated=true;result.authenticatedProfileEmailWrite=true;
 await request(`/users/${uid}`,'PATCH',{fields:{...fields,email:{stringValue:'forged@example.invalid'}}},[403]);result.forgedEmailDenied=true;
 await request(`/wallets/${uid}`,'PATCH',{fields:{balance:{integerValue:'0'}}},[403]);result.independentWalletWriteDenied=true;
 await request(`/staffRoles/${uid}`,'PATCH',{fields:{admin:{booleanValue:true}}},[403]);result.staffEscalationDenied=true;
 await request('/supportSettings/whatsapp','PATCH',{fields:{number:{stringValue:'01812345678'},revision:{integerValue:'1'}}},[403]);result.playerSupportRedirectDenied=true;
 for(const structuredQuery of [
  {from:[{collectionId:'matches'}],where:{fieldFilter:{field:{fieldPath:'status'},op:'IN',value:{arrayValue:{values:['open','closed','live','review','settled','cancelled'].map(stringValue=>({stringValue}))}}}},limit:100},
  {from:[{collectionId:'walletLedger'}],where:{fieldFilter:{field:{fieldPath:'uid'},op:'EQUAL',value:{stringValue:uid}}},orderBy:[{field:{fieldPath:'at'},direction:'DESCENDING'}],limit:20}
 ])await request(':runQuery','POST',{structuredQuery});
 result.clientQueries=true;
 await request(`/wallets/${uid}`,'GET',null,[404]);result.noTestWalletCreated=true;
} finally {
 if(user?.idToken){
  if(profileCreated)await owner.delete(`${base}/users/${user.localId}`);
  const response=await fetch(authUrl('delete'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idToken:user.idToken})});
  if(!response.ok)throw Error('Temporary auth cleanup failed');
  result.temporaryAccountRemoved=true;
 }
}
result.realBalancesChanged=false;result.checkedAt=new Date().toISOString();
await writeFile(new URL('live-client-check.json',new URL(process.env.ARENA_RELEASE_EVIDENCE || '../../verification/production-update-2026-11-05/',import.meta.url)),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
