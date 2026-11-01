import {createClient} from 'npm:@supabase/supabase-js@2.118.0';
import {reportError} from '../_shared/monitoring.ts';
Deno.serve(async req=>{
 if(req.method!=='POST')return new Response(null,{status:405});
 // The subject always comes from validated Auth, never from request JSON.
 const bearer=req.headers.get('Authorization')??'';
 if(!bearer.startsWith('Bearer '))return new Response(null,{status:401});
 try {
  const url=Deno.env.get('SUPABASE_URL')!;
  const client=createClient(url,Deno.env.get('SUPABASE_ANON_KEY')!,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
  const {data:identity,error:authError}=await client.auth.getUser(bearer.slice(7));
  if(authError||!identity.user)return new Response(null,{status:401});
  const {data:deadline,error}=await client.rpc('request_my_account_deletion');
  if(error)return Response.json({error:error.code==='55000'?'FINISH_ACTIVE_TRIP':'CLOSURE_DENIED'},{status:error.code==='55000'?409:403});
  const admin=createClient(url,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false,autoRefreshToken:false}});
  const {error:banError}=await admin.auth.admin.updateUserById(identity.user.id,{ban_duration:'876000h'});
  // RLS and RPC write guards already close access if the Auth call is unavailable.
  if(banError)await reportError('deletion_failed','data-lifecycle',Deno.env.get('SENTRY_DSN')??'');
  return Response.json({status:'closed_pending_retention',erase_after:deadline,identity_disabled:!banError},{status:202,headers:{'Cache-Control':'no-store'}});
 }catch{
  await reportError('deletion_failed','data-lifecycle',Deno.env.get('SENTRY_DSN')??'');
  return Response.json({error:'CLOSURE_UNAVAILABLE'},{status:503});
 }
});
