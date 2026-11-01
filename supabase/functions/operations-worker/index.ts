import {createClient} from 'npm:@supabase/supabase-js@2.118.0';
import {authorizedWorker} from '../_shared/notification-worker.ts';
import {eraseAccount,type ErasureJob} from '../_shared/account-erasure.ts';
import {reportError} from '../_shared/monitoring.ts';
Deno.serve(async req=>{
 if(req.method!=='POST')return new Response(null,{status:405});
 if(!await authorizedWorker(req.headers.get('Authorization'),Deno.env.get('OPERATIONS_WORKER_TOKEN')??''))return new Response(null,{status:401});
 // Action is a fixed URL query, not an arbitrary message or target identity.
 const action=new URL(req.url).searchParams.get('action')??'retention';
 if(!['retention','probe','health','errors'].includes(action))return new Response(null,{status:400});
 if(action==='probe'){
  const result=await reportError('monitoring_probe','operations',Deno.env.get('SENTRY_DSN')??'');
  return Response.json(result,{status:result.delivered?200:503,headers:{'Cache-Control':'no-store'}});
 }
 const client=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false,autoRefreshToken:false},global:{fetch:(input,init)=>fetch(input,{...init,signal:AbortSignal.timeout(15000)})}});
 try {
  if(action==='errors'){
   const {data,error}=await client.rpc('claim_client_errors');if(error)throw Error('CRASH_QUEUE_FAILED');
   let delivered=0;
   for(const event of data??[]){
    const result=await reportError('app_crashed',event.component,Deno.env.get('SENTRY_DSN')??'',fetch,record=>console.error(JSON.stringify(record)),event.event_id.replaceAll('-',''));
    if(result.delivered){const {data:ack,error}=await client.rpc('complete_client_error',{p_event_id:event.event_id,p_lease:event.lease});if(error||ack!==true)throw Error('CRASH_ACK_FAILED');delivered++;}
   }
   const {error:heartbeatError}=await client.rpc('record_operations_run',{p_worker:'client-errors',p_processed:(data??[]).length,p_failed:(data??[]).length-delivered});
   if(heartbeatError)throw Error('CRASH_HEARTBEAT_FAILED');
   return Response.json({processed:(data??[]).length,delivered},{status:delivered===(data??[]).length?200:503});
  }
  if(action==='health'){const {data,error}=await client.rpc('operations_health');if(error)throw Error('HEALTH_FAILED');return Response.json(data,{headers:{'Cache-Control':'no-store'}});}
  const {data:counts,error:retentionError}=await client.rpc('run_retention_batch');
  if(retentionError)throw Error('RETENTION_FAILED');
  const {data:jobs,error}=await client.rpc('claim_account_erasure');
  if(error)throw Error('ERASURE_CLAIM_FAILED');
  let erased=0;
  for(const job of (jobs??[]) as ErasureJob[]){
   const bucket=client.storage.from('rider-documents');
   const outcome=await eraseAccount(job,{
    list:async(prefix,offset)=>{const {data,error}=await bucket.list(prefix,{limit:100,offset,sortBy:{column:'name',order:'asc'}});if(error||!data)throw Error('STORAGE_LIST_FAILED');return data;},
    remove:async paths=>{const {error}=await bucket.remove(paths);if(error)throw Error('STORAGE_REMOVE_FAILED');},
    finalize:async()=>{const {data,error}=await client.rpc('finalize_account_erasure',{p_user_id:job.user_id,p_lease:job.lease});if(error)throw Error('ERASURE_FINALIZE_FAILED');return data===true;},
    deleteIdentity:async()=>{const {error}=await client.auth.admin.deleteUser(job.user_id);if(error && error.status!==404)throw Error('AUTH_ERASURE_FAILED');},
    complete:async()=>{const {data,error}=await client.rpc('complete_account_erasure',{p_user_id:job.user_id,p_lease:job.lease});if(error)throw Error('ERASURE_ACK_FAILED');return data===true;}
   });
   if(outcome==='erased')erased++;
  }
  const {error:heartbeatError}=await client.rpc('record_operations_run',{p_worker:'data-lifecycle',p_processed:erased,p_failed:0});
  if(heartbeatError)throw Error('HEARTBEAT_FAILED');
  return Response.json({retention:counts,erased},{headers:{'Cache-Control':'no-store'}});
 }catch{
  await reportError('retention_failed','data-lifecycle',Deno.env.get('SENTRY_DSN')??'');
  return Response.json({error:'RETENTION_FAILED'},{status:503});
 }
});
