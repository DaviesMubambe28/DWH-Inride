begin;
create extension if not exists pgtap with schema extensions;
\ir ../support/open-pilot-fixture.sql
select no_plan();

insert into auth.users (id, raw_user_meta_data)
select ('10000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  jsonb_build_object('full_name', 'Security Test ' || n, 'phone_number', '+26097100000' || n)
from generate_series(1, 5) n;
update public.profiles set role = 'rider', approval_status = 'approved', is_online = true
where id in ('10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004');
update public.profiles set role = 'admin' where id = '10000000-0000-0000-0000-000000000005';
insert into private.pilot_rider_presence(rider_id, latitude, longitude)
select id, -15.3292, 28.6821 from public.profiles where role = 'rider';

set local role service_role;
do $$ begin
  perform public.create_routed_trip('10000000-0000-0000-0000-000000000001',
    -15.3292, 28.6821, 'Test pickup', -15.33, 28.69, 'Test destination', 4000, 600, 'cash');
end $$;
reset role;
create temporary table target_trip as select id from public.trips
where passenger_id = '10000000-0000-0000-0000-000000000001';
grant select on target_trip to authenticated, anon;

select is(has_function_privilege('anon', 'public.accept_trip(uuid)', 'EXECUTE'), false, 'anonymous callers cannot execute trip acceptance');
select is(has_table_privilege('authenticated', 'public.trips', 'INSERT'), false, 'clients cannot create trips directly');
select is(has_table_privilege('authenticated', 'public.trips', 'UPDATE'), false, 'clients cannot bypass trip RPCs with direct updates');

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000002', true);
select is((select count(*) from public.authorized_trip_details where id = (select id from target_trip)), 0::bigint, 'unrelated passenger cannot read trip details');
select is(public.cancel_trip((select id from target_trip)), false, 'unrelated passenger cannot cancel a trip');
select throws_ok($$select public.apply_as_driver()$$, '42501', null, 'passenger cannot self-promote to rider');
select throws_ok($$select public.admin_set_driver_approval('10000000-0000-0000-0000-000000000003', 'approved')$$, '42501', null, 'passenger cannot approve riders');
select is((select count(*) from public.rider_available_trips), 0::bigint, 'passenger cannot read dispatch offers');
select throws_ok($$select trip_pin_hash from public.trips$$, '42501', null, 'clients cannot read PIN hashes');
select throws_ok($$select * from private.trip_pickup_pins$$, '42501', null, 'clients cannot read private pickup PINs');
select throws_ok($$update public.profiles set role = 'admin' where id = '10000000-0000-0000-0000-000000000002'$$, '42501', null, 'passenger cannot directly change role');
select set_config('app.profile_write_authorized', 'true', true);
select throws_ok($$update public.profiles set role = 'admin' where id = '10000000-0000-0000-0000-000000000002'$$, '42501', null, 'a caller-supplied GUC never grants profile privileges');
select set_config('app.profile_write_authorized', '', true);
select throws_ok($$select public.submit_trip_rating((select id from target_trip), 5::smallint, null)$$, '42501', null, 'unrelated passenger cannot rate a trip');

select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);
select throws_ok($$select public.submit_trip_rating((select id from target_trip), 5::smallint, null)$$, '42501', null, 'trip owner cannot rate an incomplete trip');

select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000003', true);
select is((select count(*) from public.rider_available_trips), 1::bigint, 'approved online rider sees the available request');
select ok(public.accept_trip((select id from target_trip)), 'first eligible rider accepts the request');
select throws_ok($$update public.trips set status = 'in_progress' where id = (select id from target_trip)$$, '42501', null, 'assigned rider cannot skip PIN verification using direct writes');

select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000004', true);
select is(public.accept_trip((select id from target_trip)), false, 'another rider cannot accept the assigned trip');
select is((select count(*) from public.authorized_trip_details where id = (select id from target_trip)), 0::bigint, 'unassigned rider cannot read participant details');
select is(public.mark_trip_arrived((select id from target_trip)), false, 'unassigned rider cannot mark arrival');

reset role;
set local role anon;
select throws_ok($$select public.accept_trip((select id from target_trip))$$, '42501', null, 'anonymous acceptance fails at the execution boundary');
reset role;
select * from finish();
rollback;
