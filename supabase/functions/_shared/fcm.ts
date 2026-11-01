// Only fixed Google endpoints are used; never trust token_uri from uploaded JSON.
export type ServiceAccount={project_id:string;client_email:string;private_key:string};
const encode=(bytes:Uint8Array)=>btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
export async function googleAccessToken(account:ServiceAccount, send:typeof fetch=fetch):Promise<string> {
  if(!/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(account.project_id) || !account.client_email.endsWith('.gserviceaccount.com')) throw Error('FCM_CONFIGURATION');
  const now=Math.floor(Date.now()/1000);
  const part=(data:unknown)=>encode(new TextEncoder().encode(JSON.stringify(data)));
  const unsigned=part({alg:'RS256',typ:'JWT'})+'.'+part({iss:account.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600});
  const pem=account.private_key.replace(/-----[^-]+-----/g,'').replace(/\s/g,'');
  const key=await crypto.subtle.importKey('pkcs8',Uint8Array.from(atob(pem),c=>c.charCodeAt(0)),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
  const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(unsigned));
  const response=await send('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:unsigned+'.'+encode(new Uint8Array(signature))}),signal:AbortSignal.timeout(8000)});
  if(!response.ok) throw Error('FCM_CONFIGURATION');
  const body=await response.json();
  if(typeof body.access_token!=='string' || !body.access_token)throw Error('FCM_CONFIGURATION');
  return body.access_token;
}
