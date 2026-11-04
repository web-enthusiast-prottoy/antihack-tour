import {createRequire} from 'node:module';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
if(!process.argv.includes('--apply'))throw Error('Explicit --apply required for production preview cleanup.');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506',base=`projects/${project}/databases/(default)/documents`;
const api=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'}),writes=[],drafts=[];
for(const id of ['match-solo-bermuda','match-squad-clash','match-duo-purgatory','match-clash-kalahari']){
 const m=(await api.get(`${base}/matches/${id}`)).body;
 if(m.fields.status?.stringValue!=='open'||m.fields.updatedBy?.stringValue!=='firebase-owner-deployment'||Number(m.fields.joined?.integerValue)!==0)continue;
 const rows=(await api.post(`${base}:runQuery`,{structuredQuery:{from:[{collectionId:'entries'}],where:{fieldFilter:{field:{fieldPath:'match'},op:'EQUAL',value:{stringValue:id}}},limit:1}})).body;
 if(rows.some(x=>x.document))continue;
 const revision=Number(m.fields.revision.integerValue)+1;
 writes.push({update:{name:m.name,fields:{status:{stringValue:'draft'},revision:{integerValue:String(revision)},updatedBy:{stringValue:'firebase-owner-deployment'}}},updateMask:{fieldPaths:['status','revision','updatedBy']},currentDocument:{updateTime:m.updateTime},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]});
 writes.push({update:{name:`${base}/matches/${id}/history/${revision}`,fields:{from:{stringValue:'open'},to:{stringValue:'draft'},reason:{stringValue:''}}},currentDocument:{exists:false},updateTransforms:[{fieldPath:'at',setToServerValue:'REQUEST_TIME'}]});
 writes.push({update:{name:`${base}/auditRecords/match_${id}_${revision}`,fields:{action:{stringValue:'archivePreviewMatch'},path:{stringValue:'matches/'+id},actor:{stringValue:'firebase-owner-deployment'}}},currentDocument:{exists:false},updateTransforms:[{fieldPath:'at',setToServerValue:'REQUEST_TIME'}]});drafts.push(id);
}
if(writes.length)await api.post(`${base}:commit`,{writes});
console.log(JSON.stringify({project,previewMatchesPreservedAsDrafts:drafts,existingRegistrationsChanged:false,matchDetailsPreserved:true}));
