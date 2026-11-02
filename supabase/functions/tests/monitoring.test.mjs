import {test} from 'node:test';
import assert from 'node:assert/strict';
import {reportError} from '../_shared/monitoring.ts';
test('monitoring uses fixed taxonomy and never accepts private messages or stack traces',async()=>{
 const logs=[];let envelope;
 const result=await reportError('routing_unavailable','request-trip','https://publickey@monitor.example/42',async(url,options)=>{assert.equal(url,'https://monitor.example/api/42/envelope/');envelope=options.body;return new Response('',{status:200});},record=>logs.push(record));
 assert.equal(result.delivered,true);assert.equal(logs.length,1);
 assert.match(envelope,/routing_unavailable/);assert.doesNotMatch(envelope,/password|fcm_token|coordinates/);
 await assert.rejects(()=>reportError('arbitrary private message','request-trip',''),/UNKNOWN_EVENT/);
});
test('unconfigured and failed reporting are explicit, not a false delivery pass',async()=>{
 assert.equal((await reportError('monitoring_probe','operations','',async()=>{throw Error('not called')},()=>{})).delivered,false);
 assert.equal((await reportError('monitoring_probe','operations','https://key@monitor.example/1',async()=>new Response('',{status:503}),()=>{})).delivered,false);
 assert.equal((await reportError('monitoring_probe','operations','http://key@monitor.example/1',async()=>{throw Error('not called')},()=>{})).delivered,false);
});
