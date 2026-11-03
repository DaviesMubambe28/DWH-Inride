begin;
create extension if not exists pgtap with schema extensions;
select plan(12);
-- Synthetic database fixtures only; these are not real rider/device attestations.
update private.pilot_service_settings set enabled=true,opens='00:00',closes='23:59:59.999999',
 weekdays=array[1,2,3,4,5,6,7] where singleton;
insert into auth.users(id,raw_user_meta_data) values
 ('68000000-0000-0000-0000-000000000001','{"full_name":"Synthetic Cash Passenger","phone_number":"+260979680001"}'),
 ('68000000-0000-0000-0000-000000000002','{"full_name":"Synthetic Cash Rider","phone_number":"+260979680002"}'),
 ('68000000-0000-0000-0000-000000000003','{"full_name":"Synthetic Cash Outsider","phone_number":"+260979680003"}');
update public.profiles set role='rider',approval_status='approved' where id in ('68000000-0000-0000-0000-000000000002','68000000-0000-0000-0000-000000000003');
select * from public.create_routed_trip('68000000-0000-0000-0000-000000000001',
 -15.3292,28.6820,'Synthetic pickup',-15.3300,28.6900,'Synthetic destination',1000,300,'cash') \gset cash_
set local role authenticated;
select set_config('request.jwt.claim.sub','68000000-0000-0000-0000-000000000001',true);
select is(public.mark_passenger_paid(:'cash_trip_id'),false,'payment cannot be marked before completion');
reset role;
update public.trips set rider_id='68000000-0000-0000-0000-000000000002',status='completed',started_at=now(),completed_at=now()
 where id=:'cash_trip_id';
set local role authenticated;
select set_config('request.jwt.claim.sub','68000000-0000-0000-0000-000000000002',true);
select is(public.confirm_rider_payment(:'cash_trip_id'),false,'rider cannot confirm before passenger acknowledgement');
select set_config('request.jwt.claim.sub','68000000-0000-0000-0000-000000000003',true);
select is(public.mark_passenger_paid(:'cash_trip_id'),false,'another passenger cannot mark cash paid');
select throws_ok(format('update public.trips set payment_status=''rider_confirmed'' where id=%L',:'cash_trip_id'),
 '42501',null,'direct payment mutation remains denied');
select set_config('request.jwt.claim.sub','68000000-0000-0000-0000-000000000001',true);
select lives_ok(format('select public.mark_passenger_paid(%L)',:'cash_trip_id'),
 'own passenger may acknowledge cash after completion');
select is((select payment_status from public.trips where id=:'cash_trip_id'),'passenger_marked_paid','cash acknowledgement persisted');
select is(public.mark_passenger_paid(:'cash_trip_id'),false,'duplicate passenger acknowledgement is harmless');
select set_config('request.jwt.claim.sub','68000000-0000-0000-0000-000000000003',true);
select is(public.confirm_rider_payment(:'cash_trip_id'),false,'other approved rider cannot confirm this cash');
select set_config('request.jwt.claim.sub','68000000-0000-0000-0000-000000000002',true);
select ok(public.confirm_rider_payment(:'cash_trip_id'),'assigned rider confirms acknowledged cash');
select is((select payment_status from public.trips where id=:'cash_trip_id'),'rider_confirmed','final cash status persisted');
select is(public.confirm_rider_payment(:'cash_trip_id'),false,'duplicate rider confirmation is harmless');
select is((select fare_amount from public.trips where id=:'cash_trip_id'),20.00::numeric,'cash acknowledgement cannot change fare');
reset role;
select * from finish();
rollback;
