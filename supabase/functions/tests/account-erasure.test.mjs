import {test} from 'node:test';
import assert from 'node:assert/strict';
import {eraseAccount,collectPrivateObjects} from '../_shared/account-erasure.ts';
const job={user_id:'62000000-0000-0000-0000-000000000001',lease:'lease',state:'processing'};
test('erasure removes Storage bytes before rows, Auth and completion',async()=>{
 const steps=[];
 const result=await eraseAccount(job,{list:async prefix=>prefix===job.user_id?[{name:'doc.pdf',id:'file'}]:[],remove:async paths=>steps.push(['storage',paths]),finalize:async()=>{steps.push(['rows']);return true;},deleteIdentity:async()=>steps.push(['auth']),complete:async()=>{steps.push(['complete']);return true;}});
 assert.equal(result,'erased');assert.deepEqual(steps.map(s=>s[0]),['storage','rows','auth','complete']);
});
test('failed Storage or false finalization never deletes Auth',async()=>{
 let auth=false;
 const ports={list:async()=>[],remove:async()=>{},finalize:async()=>false,deleteIdentity:async()=>{auth=true;},complete:async()=>true};
 await assert.rejects(()=>eraseAccount(job,ports),/ERASURE_NOT_FINALIZED/); assert.equal(auth,false);
 await assert.rejects(()=>eraseAccount(job,{...ports,list:async()=>{throw Error('storage failure');}})); assert.equal(auth,false);
});
test('listing is bounded, recursive and cannot escape the account prefix',async()=>{
 const files=await collectPrivateObjects(job.user_id,async prefix=>prefix===job.user_id?[{name:'nested',id:null}]:[{name:'file.pdf',id:'file'}]);
 assert.deepEqual(files.paths,[job.user_id+'/nested/file.pdf']);assert.equal(files.complete,true);
 await assert.rejects(()=>collectPrivateObjects(job.user_id,async()=>[{name:'../other',id:'bad'}]),/UNSAFE_OBJECT/);
 const many=await collectPrivateObjects(job.user_id,async(_prefix,offset)=>Array.from({length:100},(_,i)=>({name:String(offset+i),id:'file'})),150);
 assert.equal(many.complete,false);assert.equal(many.paths.length,150);
});
test('partial batches and deletion failures are resumable without premature completion',async()=>{
 let finalized=false;
 const result=await eraseAccount(job,{list:async(_p,offset)=>Array.from({length:100},(_,i)=>({name:String(offset+i),id:'f'})),remove:async()=>{},finalize:async()=>{finalized=true;return true;},deleteIdentity:async()=>{},complete:async()=>true});
 assert.equal(result,'pending');assert.equal(finalized,false);
});
