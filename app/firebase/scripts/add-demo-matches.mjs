import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const auth=require('firebase-tools/lib/auth'),{Client}=require('firebase-tools/lib/apiv2');
if(!process.argv.includes('--apply'))throw Error('Use --apply for the owner-requested live demo listings.');
const account=auth.getGlobalDefaultAccount();if(!account)throw Error('Firebase owner sign-in required');auth.setActiveAccount({},account);
const project='arena-ff-34506',base=`projects/${project}/databases/(default)/documents`,actor='firebase-owner-demo-seed';
const api=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const specs=[
 {id:'demo-20261105-solo',title:'DEMO · Island Solo Practice',mode:'solo',categoryId:'cat-full-map',map:'Bermuda',capacity:48,hour:18,image:'solo'},
 {id:'demo-20261105-duo',title:'DEMO · River Valley Duo',mode:'duo',categoryId:'cat-duo-battle',map:'Purgatory',capacity:24,hour:20,image:'duo'},
 {id:'demo-20261105-squad',title:'DEMO · Sunset Clash Squad',mode:'squad',categoryId:'cat-clash-squad',map:'Kalahari',capacity:12,hour:22,image:'squad'}
];
const get=async path=>{try{return (await api.get(`${base}/${path}`)).body;}catch(e){if(e.status===404||e.context?.response?.statusCode===404)return null;throw e;}};
const encode=values=>Object.fromEntries(Object.entries(values).map(([key,value])=>[key,typeof value==='number'?{integerValue:String(value)}:{stringValue:value}]));
const create=(path,values,time)=>({update:{name:`${base}/${path}`,fields:values},currentDocument:{exists:false},updateTransforms:[{fieldPath:time,setToServerValue:'REQUEST_TIME'}]});
const audit=(id,revision)=>create(`auditRecords/match_${id}_${revision}`,encode({action:'saveMatch',path:`matches/${id}`,actor}),'at');
const result=[];
for(const spec of specs){
 const category=await get(`categories/${spec.categoryId}`);if(category?.fields.active?.booleanValue!==true)throw Error('Demo category is not active: '+spec.categoryId);
 const bytes=await readFile(new URL(`../../assets/demo-matches-2026-11-05/${spec.image}.webp`,import.meta.url));
 if(bytes.length<1||bytes.length>80000||bytes.toString('ascii',0,4)!=='RIFF'||bytes.toString('ascii',8,12)!=='WEBP')throw Error('Invalid optimized artwork');
 const artworkId=`demo-20261105-${spec.image}-${sha(bytes).slice(0,12)}`;
 let match=await get(`matches/${spec.id}`);
 if(!match){
  const startsAt=`2026-11-05T${String(spec.hour-6).padStart(2,'0')}:00:00.000Z`; // Bangladesh UTC+06:00.
  const values={...encode({title:spec.title,description:'Demo listing for previewing Antihack Tour. Free entry, zero cash prizes and no hosted game room. The scheduled time is an example.',categoryId:spec.categoryId,image:spec.image,artworkId,mode:spec.mode,map:spec.map,capacity:spec.capacity,joined:0,entryPaisa:0,prizePaisa:0,perKillPaisa:0,rules:'Demo preview only. Free entry with no cash or per-kill rewards. No room credentials will be issued. Use game names only; keep phone numbers and email private.',cancellationTerms:'Demo listing only; no payment is collected and no refund or cash prize applies.',status:'draft',revision:1,cancellationReason:'',updatedBy:actor}),startsAt:{timestampValue:startsAt}};
  const writes=[];
  const artwork=await get(`artwork/${artworkId}`);
  if(!artwork)writes.push(create(`artwork/${artworkId}`,{data:{bytesValue:bytes.toString('base64')},contentType:{stringValue:'image/webp'},createdBy:{stringValue:actor}},'createdAt'));
  else if(sha(Buffer.from(artwork.fields.data.bytesValue,'base64'))!==sha(bytes))throw Error('Artwork ID already has other content');
  writes.push(create(`matches/${spec.id}`,values,'updatedAt'),create(`matches/${spec.id}/history/1`,encode({from:'none',to:'draft',reason:''}),'at'),audit(spec.id,1));
  await api.post(`${base}:commit`,{writes});match=await get(`matches/${spec.id}`);
 }
 // Resume only our untouched initial draft; never overwrite admin edits or registrations.
 if(match.fields.status.stringValue==='draft'&&match.fields.updatedBy.stringValue===actor&&match.fields.revision.integerValue==='1'&&match.fields.artworkId.stringValue===artworkId){
  const entries=(await api.post(`${base}:runQuery`,{structuredQuery:{from:[{collectionId:'entries'}],where:{fieldFilter:{field:{fieldPath:'match'},op:'EQUAL',value:{stringValue:spec.id}}},limit:1}})).body;
  if(entries.some(row=>row.document))throw Error('Demo already has entries; preserve and review it.');
  await api.post(`${base}:commit`,{writes:[
   {update:{name:match.name,fields:encode({status:'open',revision:2,updatedBy:actor})},updateMask:{fieldPaths:['status','revision','updatedBy']},currentDocument:{updateTime:match.updateTime},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]},
   create(`matches/${spec.id}/history/2`,encode({from:'draft',to:'open',reason:''}),'at'),audit(spec.id,2)
  ]});
 }
 const publicMatch=await fetch(`https://firestore.googleapis.com/v1/${base}/matches/${spec.id}`);
 if(!publicMatch.ok)throw Error('Demo is not publicly readable: '+spec.id);
 const data=(await publicMatch.json()).fields;
 if(data.entryPaisa.integerValue!=='0'||data.prizePaisa.integerValue!=='0'||data.perKillPaisa.integerValue!=='0'||!data.title.stringValue.startsWith('DEMO'))throw Error('Demo terms differ; leave admin data unchanged.');
 const art=await fetch(`https://firestore.googleapis.com/v1/${base}/artwork/${data.artworkId.stringValue}`);
 if(!art.ok)throw Error('Artwork is not publicly readable');
 const actual=Buffer.from((await art.json()).fields.data.bytesValue,'base64');if(sha(actual)!==sha(bytes))throw Error('Published image differs from the generated asset');
 result.push({id:spec.id,title:data.title.stringValue,categoryId:data.categoryId.stringValue,mode:data.mode.stringValue,status:data.status.stringValue,startsAt:data.startsAt.timestampValue,entryPaisa:0,prizePaisa:0,perKillPaisa:0,capacity:Number(data.capacity.integerValue),joined:Number(data.joined.integerValue),artworkId:data.artworkId.stringValue,artworkBytes:actual.length,artworkSha256:sha(actual)});
}
const evidence={project,addedDemoMatches:result,existingMatchesOverwritten:false,financialSettingsChanged:false};
await writeFile('/tmp/antihack-demo-matches-verification.json',JSON.stringify(evidence,null,2));console.log(JSON.stringify(evidence));
