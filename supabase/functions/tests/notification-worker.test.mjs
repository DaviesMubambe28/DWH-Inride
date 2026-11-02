import { test } from 'node:test';
import assert from 'node:assert/strict';
import { authorizedWorker, classifyFcm, processDelivery, retryDelay } from '../_shared/notification-worker.ts';

test('worker authorization requires exact, nonempty bearer credential', async () => {
  assert.equal(await authorizedWorker('Bearer correct', 'correct'), true);
  for (const header of [null, 'Bearer wrong', 'correct', 'Bearer correct extra']) assert.equal(await authorizedWorker(header, 'correct'), false);
  assert.equal(await authorizedWorker('Bearer ', ''), false);
});
test('only explicit unregistered tokens are deleted; failures are classified safely', () => {
  assert.equal(classifyFcm(404, {error:{details:[{errorCode:'UNREGISTERED'}]}}), 'invalid_token');
  assert.equal(classifyFcm(400, {error:{status:'INVALID_ARGUMENT'}}), 'permanent');
  assert.equal(classifyFcm(401, {}), 'configuration');
  for (const status of [429,500,503]) assert.equal(classifyFcm(status, {}), 'retry');
  assert.equal(retryDelay(1),30);
  assert.equal(retryDelay(20),3600);
});
test('delivery sends a stable id and generic content; retries never expose provider body', async () => {
  const job={id:'job',trip_id:'trip',kind:'trip_update',fcm_token:'private',attempts:1};
  let payload;
  assert.deepEqual(await processDelivery(job, async p => {payload=p; return Response.json({name:'projects/test/messages/accepted'});}), {outcome:'sent',delay:0});
  assert.equal(payload.message.data.event_id, 'job');
  assert.equal(payload.message.android.collapse_key,'job');
  assert.equal(JSON.stringify(payload).includes('pickup'),false);
  assert.equal((await processDelivery(job, async()=>{throw Error('private-token');})).outcome,'retry');
  assert.equal((await processDelivery({...job,attempts:6},async()=>new Response('{}',{status:503}))).outcome,'dead');
});
test('unregistered, auth and malformed provider responses are handled', async () => {
  const job={id:'j',trip_id:'t',kind:'offer',fcm_token:'private',attempts:1};
  assert.equal((await processDelivery(job,async()=>Response.json({error:{details:[{errorCode:'UNREGISTERED'}]}},{status:404}))).outcome,'invalid_token');
  assert.equal((await processDelivery(job,async()=>new Response('sensitive',{status:403}))).outcome,'configuration');
  assert.equal((await processDelivery(job,async()=>new Response('bad',{status:500}))).outcome,'retry');
});

test('malformed success is not accepted as delivered and quota backoff honors Retry-After',async()=>{
 const job={id:'j',trip_id:'t',kind:'offer',fcm_token:'private',attempts:1};
 assert.equal((await processDelivery(job,async()=>Response.json({}))).outcome,'retry');
 assert.equal((await processDelivery(job,async()=>new Response('{}',{status:429,headers:{'Retry-After':'180'}}))).delay,180);
});
