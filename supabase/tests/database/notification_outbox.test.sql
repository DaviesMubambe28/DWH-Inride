begin;
create extension if not exists pgtap with schema extensions;
select no_plan();
select has_table('private','push_outbox','durable delivery outbox exists');
set local role authenticated;
select throws_ok($$select * from public.claim_push_deliveries(10)$$,'42501',null,'clients cannot claim messages');
reset role;
insert into auth.users(id,raw_user_meta_data) values
 ('61000000-0000-0000-0000-000000000001','{"full_name":"Push Passenger","phone_number":"+260976100001"}'),
 ('61000000-0000-0000-0000-000000000002','{"full_name":"Push Rider","phone_number":"+260976100002"}');
update public.profiles set role='rider',approval_status='approved',is_online=true where id='61000000-0000-0000-0000-000000000002';
update private.pilot_service_settings set enabled=true,opens='00:00',closes='23:59:59.999999',weekdays=array[1,2,3,4,5,6,7],center_latitude=-15.3292,center_longitude=28.6821,radius_km=100,fare_policy_approved=true,cancellation_policy_approved=true;
insert into private.pilot_rider_presence(rider_id,latitude,longitude) values ('61000000-0000-0000-0000-000000000002',-15.3292,28.6821);
insert into public.device_push_tokens(fcm_token,user_id,app_id) values ('synthetic-push-token-123456','61000000-0000-0000-0000-000000000002','zm.co.dhwinride.rider');
select * from public.create_routed_trip('61000000-0000-0000-0000-000000000001',-15.3292,28.6821,'Test',-15.33,28.69,'Test',4000,600,'cash') \gset created_
select is((select count(*)::int from private.push_outbox),1,'one eligible device gets one offer');
create temp table claimed as select * from public.claim_push_deliveries(10);
select is((select count(*)::int from claimed),1,'worker claims one message');
select is((select count(*)::int from public.claim_push_deliveries(10)),0,'a live lease prevents duplicate claim');
select is(public.finish_push_delivery((select id from claimed),gen_random_uuid(),'sent',0),false,'wrong lease cannot acknowledge');
select is(public.finish_push_delivery((select id from claimed),(select lease from claimed),'retry',30),true,'retry accepted');
select is((select count(*)::int from public.claim_push_deliveries(10)),0,'backoff is respected');
update private.push_outbox set next_attempt_at=now()-interval '1 second';
drop table claimed;
create temp table claimed as select * from public.claim_push_deliveries(10);
select is((select attempts from claimed),2,'retry increments bounded attempt count');
select is(public.finish_push_delivery((select id from claimed),(select lease from claimed),'invalid_token',0),true,'invalid token acknowledged');
select is((select count(*)::int from public.device_push_tokens),0,'unregistered token removed');
select is(public.finish_push_delivery((select id from claimed),(select lease from claimed),'sent',0),false,'duplicate acknowledgement has no effect');
-- Crash recovery: a claimed delivery is released only after lease expiry.
insert into public.device_push_tokens(fcm_token,user_id,app_id) values ('synthetic-push-token-lease','61000000-0000-0000-0000-000000000002','zm.co.dhwinride.rider');
insert into private.push_outbox(trip_id,recipient_id,fcm_token,kind,event_key)
select id,'61000000-0000-0000-0000-000000000002','synthetic-push-token-lease','trip_update',id::text||':lease' from public.trips;
drop table claimed;
create temp table claimed as select * from public.claim_push_deliveries(10);
update private.push_outbox set leased_until=now()-interval '1 second' where id in (select id from claimed);
create temp table reclaimed as select * from public.claim_push_deliveries(10);
select is((select count(*)::int from reclaimed),1,'expired lease is reclaimed');
select is(public.finish_push_delivery((select id from claimed),(select lease from claimed),'sent',0),false,'old worker cannot acknowledge reclaimed delivery');
select ok((select lease from reclaimed)<>(select lease from claimed),'reclaim uses new fence token');
update private.push_outbox set attempts=6,leased_until=now()-interval '1 second' where id in (select id from reclaimed);
select is((select count(*)::int from public.claim_push_deliveries(10)),0,'retry exhaustion prevents another send');
select is((select state from private.push_outbox where id=(select id from reclaimed)),'dead','exhausted delivery is visible as dead');
select * from finish();
rollback;
