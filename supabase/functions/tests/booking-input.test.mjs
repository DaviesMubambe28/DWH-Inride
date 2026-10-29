import test from 'node:test';
import assert from 'node:assert/strict';
import { readBookingInput, routeMetrics, bookingFailure } from '../_shared/booking-input.ts';

const valid={pickup:{latitude:-15.3292,longitude:28.6821,address:' Market '},dropoff:{latitude:-15.33,longitude:28.69,address:' Clinic '},paymentMethod:'cash'};
const request=value=>new Request('http://localhost/request-trip',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(value)});
test('valid booking normalizes addresses and defaults to cash',async()=>{
  const result=await readBookingInput(request({...valid,paymentMethod:undefined}));
  assert.equal(result.pickup.address,'Market');assert.equal(result.paymentMethod,'cash');
});
test('null, arrays, invalid coordinates and mobile money are rejected',async()=>{
  for(const body of [null,[],{}, {...valid,pickup:{...valid.pickup,latitude:91}}, {...valid,paymentMethod:'mobile_money_external'}]) {
    await assert.rejects(readBookingInput(request(body)),e=>e.status===400);
  }
});
test('streamed and declared oversized requests fail before being parsed',async()=>{
  await assert.rejects(readBookingInput(new Request('http://localhost',{method:'POST',headers:{'Content-Type':'application/json','Content-Length':'9000'},body:'{}'})),e=>e.status===413);
  await assert.rejects(readBookingInput(request({...valid,padding:'x'.repeat(9000)})),e=>e.status===413);
});
test('malformed JSON and non-JSON content types are recoverable client errors',async()=>{
  await assert.rejects(readBookingInput(new Request('http://localhost',{method:'POST',headers:{'Content-Type':'application/json'},body:'{'})),e=>e.status===400);
  await assert.rejects(readBookingInput(new Request('http://localhost',{method:'POST',body:'{}'})),e=>e.status===415);
});
test('routing data must contain finite positive numeric metrics',()=>{
  assert.deepEqual(routeMetrics({code:'Ok',routes:[{distance:1234.5,duration:321.4}]}),{distanceMeters:1235,durationSeconds:321});
  for(const data of [null,{}, {code:'NoRoute'}, {code:'Ok',routes:[{distance:'4000',duration:300}]}, {code:'Ok',routes:[{distance:Infinity,duration:300}]}]) assert.equal(routeMetrics(data),null);
});
test('pilot denials become useful errors without leaking database details',()=>{
  assert.equal(bookingFailure({message:'PILOT_UNAVAILABLE'}).status,503);
  assert.equal(bookingFailure({message:'PILOT_OUTSIDE_SERVICE_AREA'}).status,422);
  assert.equal(bookingFailure({code:'23505'}).status,409);
  assert.equal(bookingFailure({message:'private SQL detail'}).error,'The trip could not be created.');
});
