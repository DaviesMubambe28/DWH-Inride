import {test} from 'node:test';
import assert from 'node:assert/strict';
import {googleAccessToken} from '../_shared/fcm.ts';
test('Google exchange signs a constrained JWT and ignores supplied token endpoints',async()=>{
 const keys=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
 const privateKey=Buffer.from(await crypto.subtle.exportKey('pkcs8',keys.privateKey)).toString('base64');
 const account={project_id:'test-project',client_email:'sender@test-project.iam.gserviceaccount.com',private_key:'-----BEGIN PRIVATE KEY-----\n'+privateKey+'\n-----END PRIVATE KEY-----',token_uri:'https://attacker.invalid'};
 const result=await googleAccessToken(account,async(url,init)=>{
  assert.equal(url,'https://oauth2.googleapis.com/token');
  const jwt=init.body.get('assertion');const [header,claims,signature]=jwt.split('.');
  const payload=JSON.parse(Buffer.from(claims,'base64url'));
  assert.equal(payload.scope,'https://www.googleapis.com/auth/firebase.messaging');
  assert.equal(payload.aud,'https://oauth2.googleapis.com/token');assert.equal(payload.exp-payload.iat,3600);
  assert.equal(await crypto.subtle.verify('RSASSA-PKCS1-v1_5',keys.publicKey,Buffer.from(signature,'base64url'),new TextEncoder().encode(header+'.'+claims)),true);
  return Response.json({access_token:'ephemeral-test-only'});
 });
 assert.equal(result,'ephemeral-test-only');
 await assert.rejects(()=>googleAccessToken(account,async()=>new Response('private provider body',{status:403})),/FCM_CONFIGURATION/);
 await assert.rejects(()=>googleAccessToken({...account,project_id:'../other'},async()=>{throw Error('not called');}),/FCM_CONFIGURATION/);
});
