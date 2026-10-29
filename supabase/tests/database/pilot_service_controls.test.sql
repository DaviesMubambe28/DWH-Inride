begin;
create extension if not exists pgtap with schema extensions;
select no_plan();
select is(private.pilot_service_open(), false, 'pilot starts paused');
select is((public.pilot_service_status()->>'available')::boolean, false, 'clients see unavailable until owner configuration is complete');
select throws_ok($$update private.pilot_service_settings set enabled = true, center_latitude = null$$, '23514', null, 'incomplete service-area and policy configuration cannot enable traffic');
select throws_ok($$update private.pilot_service_settings set enabled = true, center_latitude = -15.3292,
  center_longitude = 28.6821, radius_km = 5, support_phone = null, fare_policy_approved = true, cancellation_policy_approved = true$$,
  '23514', null, 'SQL NULL contact fields cannot bypass the enable constraint');

update private.pilot_service_settings set center_latitude = -15.3292, center_longitude = 28.6821,
  radius_km = 5, support_phone = '+260973000001', support_email = 'fixture@test.invalid',
  fare_policy_approved = true, cancellation_policy_approved = true, enabled = true;
select ok(private.pilot_service_open('2026-10-26 04:00:00+00'), 'Monday opens at 06:00 CAT');
select is(private.pilot_service_open('2026-10-26 03:59:59+00'), false, 'Monday before 06:00 CAT is closed');
select is(private.pilot_service_open('2026-10-26 18:00:00+00'), false, 'new requests stop at 20:00 CAT');
select is(private.pilot_service_open('2026-11-01 10:00:00+00'), false, 'Sunday is closed');
select ok(private.pilot_service_open('2026-10-31 04:00:00+00'), 'Saturday is an operating day');
select ok(private.pilot_point_allowed(-15.3292, 28.6821), 'zone centre is allowed');
select is(private.pilot_point_allowed(0, 0), false, 'locations outside the approved boundary are rejected');
select is(private.pilot_point_allowed('NaN', 28.6821), false, 'non-finite coordinates cannot pass the geofence');

insert into auth.users(id, raw_user_meta_data)
select ('30000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  jsonb_build_object('full_name', 'Pilot Service ' || n, 'phone_number', '+26097300000' || n)
from generate_series(1, 2) n;
update public.profiles set role = 'rider', approval_status = 'approved', is_online = true
where id = '30000000-0000-0000-0000-000000000002';
update private.pilot_service_settings set enabled = false;
set local role service_role;
select throws_ok($$select * from public.create_routed_trip('30000000-0000-0000-0000-000000000001', -15.3292, 28.6821, 'Pickup', -15.33, 28.69, 'Destination', 4000, 600, 'cash')$$,
  'P0001', 'PILOT_UNAVAILABLE', 'trusted booking cannot bypass the paused pilot');
reset role;
\ir ../support/open-pilot-fixture.sql
update private.pilot_service_settings set radius_km = 5;
set local role service_role;
select throws_ok($$select * from public.create_routed_trip('30000000-0000-0000-0000-000000000001', -15.3292, 28.6821, 'Pickup', -15.33, 28.69, 'Destination', 4000, 600, 'mobile_money_external')$$,
  'P0001', 'PILOT_CASH_ONLY', 'pilot bookings cannot select mobile money');
select throws_ok($$select * from public.create_routed_trip('30000000-0000-0000-0000-000000000001', -15.3292, 28.6821, 'Pickup', 0, 0, 'Outside', 4000, 600, 'cash')$$,
  'P0001', 'PILOT_OUTSIDE_SERVICE_AREA', 'trusted booking cannot bypass the service boundary');
select lives_ok($$select * from public.create_routed_trip('30000000-0000-0000-0000-000000000001', -15.3292, 28.6821, 'Pickup', -15.33, 28.69, 'Destination', 4000, 600, 'cash')$$,
  'cash booking inside the configured open pilot succeeds');
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '30000000-0000-0000-0000-000000000002', true);
select is((select count(*) from public.rider_available_trips), 0::bigint, 'rider without a current position receives no dispatch');
select public.record_pilot_rider_presence(0, 0);
select is((select count(*) from public.rider_available_trips), 0::bigint, 'rider outside the boundary receives no dispatch');
select public.record_pilot_rider_presence(-15.3292, 28.6821);
select is((select count(*) from public.rider_available_trips), 1::bigint, 'rider with a fresh in-zone position receives dispatch');
reset role;
update private.pilot_rider_presence set recorded_at = now() - interval '3 minutes';
set local role authenticated;
select is((select count(*) from public.rider_available_trips), 0::bigint, 'stale rider positions cannot receive dispatch');
select public.set_driver_online(false);
select throws_ok($$select public.record_pilot_rider_presence(-15.3292, 28.6821)$$, '42501', null, 'offline riders cannot publish availability');
reset role;
select is((select count(*) from private.pilot_rider_presence), 0::bigint, 'going offline removes the availability position');
set local role authenticated;
select public.set_driver_online(true);
select public.record_pilot_rider_presence(-15.3292, 28.6821);
select ok(public.accept_trip((select id from public.trips limit 1)), 'eligible rider accepts before service pauses');
select ok(public.mark_trip_arrived((select id from public.trips limit 1)), 'assigned rider can mark arrival');
reset role;
create temporary table completion_pin as select pin from private.trip_pickup_pins limit 1;
grant select on completion_pin to authenticated;
set local role authenticated;
select ok(public.verify_trip_pin_and_start((select id from public.trips limit 1), (select pin from completion_pin)), 'verified PIN starts the active trip');
reset role;
update private.pilot_service_settings set enabled = false;
set local role authenticated;
select ok(public.complete_trip((select id from public.trips limit 1)), 'existing active trip completes naturally after service pauses');
select set_config('request.jwt.claim.sub', '30000000-0000-0000-0000-000000000001', true);
select throws_ok($$select public.record_pilot_rider_presence(-15.3292, 28.6821)$$, '42501', null, 'passenger cannot publish a rider position');
select throws_ok($$select * from private.pilot_rider_presence$$, '42501', null, 'clients cannot read raw rider availability locations');
select throws_ok($$select public.admin_pause_pilot()$$, '42501', null, 'passenger cannot operate the pilot pause switch');
select set_config('request.jwt.claim.sub', '', true);
reset role;
insert into auth.users(id, raw_user_meta_data) values ('30000000-0000-0000-0000-000000000003',
  '{"full_name":"Pilot Pause Operator","phone_number":"+260973000003"}');
update public.profiles set role = 'admin' where id = '30000000-0000-0000-0000-000000000003';
update private.pilot_service_settings set enabled = true;
set local role authenticated;
select set_config('request.jwt.claim.sub', '30000000-0000-0000-0000-000000000003', true);
select ok(public.admin_pause_pilot(), 'authorized operator can pause new bookings');
reset role;
select is((select enabled from private.pilot_service_settings), false, 'pause switch disables the service');
select is((select count(*) from private.pilot_operations_audit where action = 'pilot_paused'), 1::bigint, 'pause action has an accountable audit record');
select * from finish();
rollback;
