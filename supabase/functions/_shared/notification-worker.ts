// No credentials, provider response bodies or device tokens may enter logs.
export async function authorizedWorker(header: string | null, secret: string): Promise<boolean> {
  if (!secret || !header?.startsWith('Bearer ')) return false;
  const hash = async (s: string) => new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)));
  const [a,b] = await Promise.all([hash(header.slice(7)),hash(secret)]);
  let difference=0;
  for(let i=0;i<a.length;i++) difference |= a[i]^b[i];
  return difference===0;
}
export function classifyFcm(status: number, body: unknown): string {
  const error = (body as {error?: {details?: {errorCode?:string}[]}} | null)?.error;
  if (status===404 && Array.isArray(error?.details) && error.details.some(d=>d.errorCode==='UNREGISTERED')) return 'invalid_token';
  if (status===401 || status===403) return 'configuration';
  if (status===429 || status>=500) return 'retry';
  return 'permanent';
}
export function retryDelay(attempt: number): number { return Math.min(3600,30*2**Math.min(10,Math.max(0,attempt-1))); }
export type Delivery = {id:string; trip_id:string; kind:string; fcm_token:string; attempts:number};
export async function processDelivery(job: Delivery, send:(body:unknown)=>Promise<Response>) {
  let outcome='retry';
  let providerDelay=0;
  try {
    const response=await send({message:{token:job.fcm_token,
      data:{event_id:job.id,trip_id:job.trip_id,title:'DHW-inRide',body:job.kind==='offer'?'A ride request may be available. Open the app to refresh.':'Your trip status changed. Open the app to refresh.'},
      android:{priority:'high',ttl:'120s',collapse_key:job.id}}});
    let body:unknown=null; try {body=await response.json();} catch { /* Never forward provider body. */ }
    if(response.ok) {
      const name=(body as {name?:unknown}|null)?.name;
      if(typeof name==='string' && /^projects\/[^/]+\/messages\/[^/]+$/.test(name)) return {outcome:'sent',delay:0};
    } else {
      outcome=classifyFcm(response.status,body);
      const retryAfter=response.headers.get('Retry-After');
      const seconds=retryAfter===null?0:/^\d+$/.test(retryAfter)?Number(retryAfter):Math.ceil((Date.parse(retryAfter)-Date.now())/1000);
      providerDelay=Math.min(3600,Math.max(response.status===429?60:0,Number.isFinite(seconds)?seconds:0));
    }
  } catch { /* Timeout has an ambiguous delivery outcome; stable event ID supports deduplication. */ }
  if((outcome==='retry' || outcome==='configuration') && job.attempts>=6) outcome='dead';
  return {outcome,delay:Math.max(providerDelay,retryDelay(job.attempts))};
}
