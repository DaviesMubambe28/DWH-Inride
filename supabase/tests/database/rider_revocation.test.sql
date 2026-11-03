begin;
create extension if not exists pgtap with schema extensions;
select plan(6);
update private.pilot_service_settings set enabled=true,opens='00:00',closes='23:59:59.999999',weekdays=array[1,2,3,4,5,6,7] where singleton;
insert into auth.users(id,raw_user_meta_data) values
 ('69000000-0000-0000-0000-000000000001','{"full_name":"Synthetic PIN Passenger","phone_number":"+260979690001"}'),
 ('69000000-0000-0000-0000-000000000002','{"full_name":"Synthetic PIN Rider","phone_number":"+260979690002"}');
update public.profiles set role='rider',approval_status='approved' where id='69000000-0000-0000-0000-000000000002';
select * from public.create_routed_trip('69000000-0000-0000-0000-000000000001',-15.3292,28.6820,'Synthetic pickup',-15.3300,28.6900,'Synthetic destination',1000,300,'cash') \gset fixture_
update public.trips set rider_id='69000000-0000-0000-0000-000000000002',status='arrived' where id=:'fixture_trip_id';
-- Approval must still be valid when an assigned rider tries to start.
update public.profiles set approval_status='rejected' where id='69000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','69000000-0000-0000-0000-000000000002',true);
select throws_ok(format('select public.verify_trip_pin_and_start(%L,%L)',:'fixture_trip_id',:'fixture_pickup_pin'),'42501',null,'rejected assigned rider cannot start with the correct PIN');
reset role;
select set_config('request.jwt.claim.sub','',true);
select is((select status::text from public.trips where id=:'fixture_trip_id'),'arrived','revocation leaves the trip unstarted');
update public.trips set status='arrived' where id=:'fixture_trip_id';
update public.profiles set approval_status='pending' where id='69000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','69000000-0000-0000-0000-000000000002',true);
select throws_ok(format('select public.verify_trip_pin_and_start(%L,%L)',:'fixture_trip_id',:'fixture_pickup_pin'),'42501',null,'pending assigned rider cannot start with the correct PIN');
reset role;
select set_config('request.jwt.claim.sub','',true);
update public.trips set status='arrived' where id=:'fixture_trip_id';
update public.profiles set approval_status='approved' where id='69000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','69000000-0000-0000-0000-000000000002',true);
select ok(public.verify_trip_pin_and_start(:'fixture_trip_id',:'fixture_pickup_pin'),'approved assigned rider may start');
select throws_ok(format('update public.trips set fare_amount=1 where id=%L',:'fixture_trip_id'),'42501',null,'protected RPC never grants direct fare-write permission');
reset role;
select set_config('request.jwt.claim.sub','',true);
select is(has_function_privilege('anon','public.verify_trip_pin_and_start(uuid,text)','EXECUTE'),false,'anonymous start remains denied');
select * from finish();
rollback;
