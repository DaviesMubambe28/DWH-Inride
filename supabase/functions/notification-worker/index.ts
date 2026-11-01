import { createClient } from 'npm:@supabase/supabase-js@2.118.0';
import { authorizedWorker, processDelivery, type Delivery } from '../_shared/notification-worker.ts';
import { googleAccessToken, type ServiceAccount } from '../_shared/fcm.ts';
import { reportError } from '../_shared/monitoring.ts';

Deno.serve(async req=>{
 if(req.method!=='POST')return new Response(null,{status:405});
 if(!await authorizedWorker(req.headers.get('Authorization'),Deno.env.get('OPERATIONS_WORKER_TOKEN')??''))return new Response(null,{status:401});
 const report=(code:string)=>reportError(code,'notifications',Deno.env.get('SENTRY_DSN')??'');
 try {
  const raw=Deno.env.get('FCM_SERVICE_ACCOUNT_JSON');
  if(!raw) {await report('push_configuration_failed');return Response.json({error:'FCM_CONFIGURATION'},{status:503});}
  const account=JSON.parse(raw) as ServiceAccount;
  const accessToken=await googleAccessToken(account); // Acquire before leasing jobs.
  const client=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false,autoRefreshToken:false}});
  const {data,error}=await client.rpc('claim_push_deliveries',{p_limit:10});
  if(error)throw Error('QUEUE_UNAVAILABLE');
  const jobs=(data??[]) as (Delivery & {lease:string})[];
  const results=await Promise.all(jobs.map(async job=>{
   const result=await processDelivery(job,body=>fetch('https://fcm.googleapis.com/v1/projects/'+account.project_id+'/messages:send',{
    method:'POST',headers:{Authorization:'Bearer '+accessToken,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(8000)}));
   const {data:ack,error:ackError}=await client.rpc('finish_push_delivery',{p_id:job.id,p_lease:job.lease,p_outcome:result.outcome,p_delay:result.delay});
   if(ackError || ack!==true)throw Error('ACK_UNAVAILABLE');
   return result.outcome;
  }));
  const {error:heartbeatError}=await client.rpc('record_operations_run',{p_worker:'notifications',p_processed:results.length,p_failed:results.filter(r=>r!=='sent').length});
  if(heartbeatError)throw Error('HEARTBEAT_FAILED');
  if(results.some(r=>r!=='sent'))await report('push_delivery_failed');
  return Response.json({processed:results.length,sent:results.filter(r=>r==='sent').length,retry_or_failed:results.filter(r=>r!=='sent').length},{headers:{'Cache-Control':'no-store'}});
 } catch {await report('worker_failed');return Response.json({error:'WORKER_FAILED'},{status:503});}
});
