begin;
create extension if not exists pgtap with schema extensions;
select plan(13);
update private.pilot_service_settings set enabled=true,opens='00:00',closes='23:59:59.999999',weekdays=array[1,2,3,4,5,6,7] where singleton;
insert into auth.users(id,raw_user_meta_data) values
 ('69000000-0000-0000-0000-000000000001','{"full_name":"Synthetic PIN Passenger","phone_number":"+260979690001"}'),
 ('69000000-0000-0000-0000-000000000002','{"full_name":"Synthetic PIN Rider","phone_number":"+260979690002"}');
update public.profiles set role='rider',approval_status='approved' where id='69000000-0000-0000-0000-000000000002';
select * from public.create_routed_trip('69000000-0000-0000-0000-000000000001',-15.3292,28.6820,'Synthetic pickup',-15.3300,28.6900,'Synthetic destination',1000,300,'cash') \gset fixture_
update public.trips set rider_id='69000000-0000-0000-0000-000000000002',status='arrived' where id=:'fixture_trip_id';
select case when :'fixture_pickup_pin'='0000' then '0001' else '0000' end as wrong \gset fixture_
set local role authenticated;
select set_config('request.jwt.claim.sub','69000000-0000-0000-0000-000000000002',true);
select is(public.verify_trip_pin_and_start(:'fixture_trip_id',null),false,'null PIN cannot start a trip');
select is(public.verify_trip_pin_and_start(:'fixture_trip_id','99999'),false,'malformed PIN cannot start a trip');
select lives_ok(format('select public.verify_trip_pin_and_start(%L,%L)',:'fixture_trip_id',:'fixture_wrong'),'wrong four-digit PIN records an attempt without a server error');
reset role;
select is((select pin_failed_attempts::int from public.trips where id=:'fixture_trip_id'),1,'wrong PIN counter persists');
select is((select status::text from public.trips where id=:'fixture_trip_id'),'arrived','wrong PIN cannot start the trip');
set local role authenticated;
select lives_ok(format('do $x$ begin for n in 1..4 loop perform public.verify_trip_pin_and_start(%L,%L); end loop; end $x$',:'fixture_trip_id',:'fixture_wrong'),'four more wrong PINs are counted');
reset role;
select is((select pin_failed_attempts::int from public.trips where id=:'fixture_trip_id'),5,'five attempts persist');
select ok((select pin_locked_until>now() from public.trips where id=:'fixture_trip_id'),'five attempts set a future lock');
set local role authenticated;
select throws_ok(format('select public.verify_trip_pin_and_start(%L,%L)',:'fixture_trip_id',:'fixture_pickup_pin'),'42501',null,'correct PIN remains blocked during lockout');
select throws_ok(format('update public.trips set pin_failed_attempts=0 where id=%L',:'fixture_trip_id'),'42501',null,'a prior RPC never grants direct counter-write privileges');
reset role;
-- Simulate expiration only in this rolled-back local synthetic fixture.
update public.trips set pin_locked_until=now()-interval '1 second' where id=:'fixture_trip_id';
set local role authenticated;
select ok(public.verify_trip_pin_and_start(:'fixture_trip_id',:'fixture_pickup_pin'),'correct PIN starts after the lock expires');
reset role;
select is((select pin_failed_attempts::int from public.trips where id=:'fixture_trip_id'),0,'successful start clears counter');
select ok((select status='in_progress' and pin_locked_until is null from public.trips where id=:'fixture_trip_id'),'successful start persists state and clears lock');
select * from finish();
rollback;
