import {createRequire} from 'node:module';
import {mkdir,writeFile} from 'node:fs/promises';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506',db=`projects/${project}/databases/(default)`;
const api=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const backup='/tmp/antihack-production-inspection-'+new Date().toISOString().replace(/[:.]/g,'-');await mkdir(backup,{recursive:true,mode:0o700});
const rows={};
for(const collection of ['staffRoles','matches','categories','entries','wallets','walletDeposits','walletLedger','withdrawals','withdrawalPolicy','paymentInstructions','configuration']){
 const response=await api.post(`${db}/documents:runQuery`,{structuredQuery:{from:[{collectionId:collection}],limit:101}});
 rows[collection]=response.body.filter(x=>x.document).map(x=>x.document);
 await writeFile(`${backup}/${collection}.json`,JSON.stringify(rows[collection],null,2),{mode:0o600});
 console.log(JSON.stringify({collection,loaded:rows[collection].length,truncated:rows[collection].length===101}));
}
for(const d of rows.matches){const f=d.fields;console.log(JSON.stringify({match:d.name.split('/').at(-1),status:f.status?.stringValue,joined:Number(f.joined?.integerValue??0),confirmedEntries:rows.entries.filter(e=>e.fields.match?.stringValue===d.name.split('/').at(-1)&&e.fields.status?.stringValue==='confirmed').length,entryPaisa:Number(f.entryPaisa?.integerValue??0)}));}
for(const name of ['withdrawalPolicy','paymentInstructions'])for(const d of rows[name])console.log(JSON.stringify({setting:name,id:d.name.split('/').at(-1),fields:d.fields}));
const indices=(await api.get(`${db}/collectionGroups/-/indexes`)).body;await writeFile(`${backup}/indexes.json`,JSON.stringify(indices,null,2),{mode:0o600});
console.log(JSON.stringify({indexes:(indices.indexes??[]).map(i=>({collection:i.name.split('/collectionGroups/')[1]?.split('/')[0],state:i.state}))}));
const rules=new Client({urlPrefix:'https://firebaserules.googleapis.com',apiVersion:'v1'});
const release=(await rules.get(`projects/${project}/releases/cloud.firestore`)).body;
const source=(await rules.get(release.rulesetName)).body;
await writeFile(`${backup}/rules.json`,JSON.stringify(source,null,2),{mode:0o600});
console.log(JSON.stringify({project,rulesBackedUp:true,backup}));
