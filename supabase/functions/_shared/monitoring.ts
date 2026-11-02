const EVENTS = new Set(['app_crashed','monitoring_probe','routing_unavailable','booking_write_failed','push_delivery_failed','push_configuration_failed','worker_failed','deletion_failed','retention_failed']);
const COMPONENTS = new Set(['passenger','rider','request-trip','operations','notifications','data-lifecycle']);
// Deliberately no caller-provided message, exception, URL, user ID or stack.
export async function reportError(code:string,component:string,dsn:string,
 send:typeof fetch=fetch, log:(record:unknown)=>void=record=>console.error(JSON.stringify(record)), stableEventId?:string) {
 if(!EVENTS.has(code)||!COMPONENTS.has(component)) throw Error('UNKNOWN_EVENT');
 const eventId=stableEventId && /^[a-f0-9]{32}$/.test(stableEventId)?stableEventId:crypto.randomUUID().replaceAll('-','');
 log({level:'error',event:code,component,event_id:eventId});
 if(!dsn)return {eventId,delivered:false};
 try {
  const url=new URL(dsn);
  if(url.protocol!=='https:' || !url.username || url.password || !/^\/\d+$/.test(url.pathname) || url.search || url.hash) return {eventId,delivered:false};
  const project=url.pathname.slice(1),key=decodeURIComponent(url.username);
  const origin=url.origin;
  const envelope=JSON.stringify({event_id:eventId,dsn})+'\n'+JSON.stringify({type:'event'})+'\n'+JSON.stringify({event_id:eventId,timestamp:new Date().toISOString(),level:'error',platform:'javascript',message:code,tags:{component},logger:'dhw-inride',environment:'configured-edge'});
  const response=await send(origin+'/api/'+project+'/envelope/',{method:'POST',headers:{'Content-Type':'application/x-sentry-envelope','X-Sentry-Auth':'Sentry sentry_version=7,sentry_key='+key},body:envelope,signal:AbortSignal.timeout(5000)});
  return {eventId,delivered:response.ok};
 } catch {return {eventId,delivered:false};}
}
