import {createRequire} from 'node:module';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
if(!process.argv.includes('--apply'))throw Error('Use --apply only for the owner-authorized production release.');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase owner sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506',base=`projects/${project}/databases/(default)/documents`;
const api=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const get=async path=>{try{return (await api.get(`${base}/${path}`)).body;}catch(e){if(e.status===404||e.context?.response?.statusCode===404)return null;throw e;}};
const marker='auditRecords/production_setup_1_0_0';
if(await get(marker)){console.log('Production setup already applied; operator settings preserved.');process.exit(0);}
const fields=values=>Object.fromEntries(Object.entries(values).map(([k,v])=>[k,typeof v==='boolean'?{booleanValue:v}:typeof v==='number'?{integerValue:String(v)}:{stringValue:v}]));
const writes=[];
function write(path,values,old,time='updatedAt'){
 writes.push({update:{name:`${base}/${path}`,fields:fields(values)},currentDocument:old?{updateTime:old.updateTime}:{exists:false},updateTransforms:[{fieldPath:time,setToServerValue:'REQUEST_TIME'}]});
}
function audit(id,action,path){write('auditRecords/'+id,{action,path,actor:'firebase-owner-deployment'},null,'at');}
const policy=await get('withdrawalPolicy/bkash');
if(!policy){write('withdrawalPolicy/bkash',{minimumPaisa:5000,feePaisa:500,enabled:true,currency:'BDT',revision:1},null);audit('withdrawal_policy_bkash_1','saveWithdrawalPolicy','withdrawalPolicy/bkash');}
const payment=await get('paymentInstructions/bkash');
if(!payment||payment.fields?.number?.stringValue==='01889898989'){
 const revision=Number(payment?.fields?.revision?.integerValue??0)+1;
 write('paymentInstructions/bkash',{number:'',enabled:false,revision},payment);audit(`payment_bkash_${revision}`,'savePaymentInstructions','paymentInstructions/bkash');
}
// These four prefilled preview counters have no corresponding registrations. Never delete entries.
const seeded={'match-solo-bermuda':12,'match-squad-clash':8,'match-duo-purgatory':6,'match-clash-kalahari':4};
const repaired=[];
for(const [id,expected] of Object.entries(seeded)){
 const match=await get('matches/'+id);if(!match||match.fields.status?.stringValue!=='open'||Number(match.fields.joined?.integerValue)!==expected)continue;
 const entries=(await api.post(`${base}:runQuery`,{structuredQuery:{from:[{collectionId:'entries'}],where:{compositeFilter:{op:'AND',filters:[{fieldFilter:{field:{fieldPath:'match'},op:'EQUAL',value:{stringValue:id}}},{fieldFilter:{field:{fieldPath:'status'},op:'EQUAL',value:{stringValue:'confirmed'}}}]}},limit:1}})).body;
 if(entries.some(x=>x.document))continue;
 const revision=Number(match.fields.revision?.integerValue??0)+1;
 writes.push({update:{name:match.name,fields:{joined:{integerValue:'0'},revision:{integerValue:String(revision)},updatedBy:{stringValue:'firebase-owner-deployment'}}},updateMask:{fieldPaths:['joined','revision','updatedBy']},currentDocument:{updateTime:match.updateTime},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]});
 write(`matches/${id}/history/${revision}`,{from:'open',to:'open',reason:''},null,'at');
 audit(`match_${id}_${revision}`,'reconcilePreviewCount',`matches/${id}`);repaired.push(id);
}
audit('production_setup_1_0_0','productionSetup','production/1.0.0');
await api.post(`${base}:commit`,{writes});
console.log(JSON.stringify({project,setupApplied:true,withdrawals:policy?'existing operator policy preserved':{minimumTaka:50,feeTaka:5,enabled:true},demoDepositRecipientRemoved:!payment||payment.fields?.number?.stringValue==='01889898989',previewCountersReconciled:repaired,realBalancesChanged:false}));
