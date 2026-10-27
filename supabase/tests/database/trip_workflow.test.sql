begin;

create extension if not exists pgtap with schema extensions;
select plan(22);

select has_column('public', 'profiles', 'approval_status', 'driver approval status exists');
select has_column('public', 'trips', 'service_fee', 'trip commission is persisted');
select has_function('public', 'accept_trip', array['uuid'], 'accept RPC exists');
select has_function('public', 'verify_trip_pin_and_start', array['uuid', 'text'], 'PIN RPC exists');
select function_privs_are(
  'public', 'create_routed_trip',
  array['uuid', 'numeric', 'numeric', 'text', 'numeric', 'numeric', 'text', 'integer', 'integer', 'text'],
  'authenticated', array[]::text[],
  'authenticated clients cannot call trusted trip creation'
);
select has_function('public', 'submit_trip_rating', array['uuid', 'smallint', 'text'], 'rating RPC exists');

insert into auth.users (id, instance_id, aud, role, email, encrypted_password, created_at, updated_at)
values
  ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'passenger@test.local', '', now(), now()),
  ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'driver@test.local', '', now(), now()),
  ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'admin@test.local', '', now(), now());

insert into public.profiles (id, role, full_name, phone_number, approval_status)
values
  ('00000000-0000-0000-0000-000000000001', 'passenger', 'Test Passenger', '+260970000001', null),
  ('00000000-0000-0000-0000-000000000002', 'rider', 'Test Driver', '+260970000002', 'pending'),
  ('00000000-0000-0000-0000-000000000003', 'admin', 'Test Admin', '+260970000003', null);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select throws_ok(
  $$ select public.set_driver_online(true) $$,
  '42501',
  'only an approved driver can change online status',
  'pending driver cannot go online'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select is(
  public.admin_set_driver_approval('00000000-0000-0000-0000-000000000002', 'approved'),
  'approved'::public.verification_status,
  'admin can approve a driver'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select ok(public.set_driver_online(true), 'approved driver can go online');

reset role;
set local role service_role;
select * from public.create_routed_trip(
  '00000000-0000-0000-0000-000000000001',
  -15.3292, 28.6821, 'Chongwe Market',
  -15.3875, 28.3228, 'Lusaka CBD',
  4000, 600, 'cash'
);
reset role;

select is((select fare_amount from public.trips limit 1), 30.00::numeric, 'fare formula is server authoritative');
select is((select service_fee from public.trips limit 1), 4.50::numeric, 'platform commission is 15 percent');
select is((select driver_fare from public.trips limit 1), 25.50::numeric, 'driver fare is net of commission');
select matches((select pin from private.trip_pickup_pins limit 1), '^[0-9]{4}$', 'pickup PIN has four numeric digits');

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select ok(public.accept_trip((select id from public.trips limit 1)), 'approved online driver accepts trip');
select ok(public.mark_trip_arrived((select id from public.trips limit 1)), 'accepted trip transitions to arrived');
select is(public.verify_trip_pin_and_start((select id from public.trips limit 1), '99999'), false, 'invalid PIN format is rejected');
reset role;

create temporary table expected_pin as select pin from private.trip_pickup_pins limit 1;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select ok(
  public.verify_trip_pin_and_start(
    (select id from public.trips limit 1),
    (select pin from expected_pin limit 1)
  ),
  'correct PIN starts the trip'
);
select ok(public.complete_trip((select id from public.trips limit 1)), 'in-progress trip can be completed');
select is((select status::text from public.trips limit 1), 'completed', 'trip finishes in completed state');

set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
select isnt(
  public.submit_trip_rating((select id from public.trips limit 1), 5::smallint, 'Safe ride'),
  null,
  'passenger can rate a completed trip once'
);
select is(
  (select rating_value from public.trip_ratings limit 1),
  5::smallint,
  'rating value is persisted'
);
select throws_ok(
  $$ select public.submit_trip_rating((select id from public.trips limit 1), 4::smallint, null) $$,
  '23505',
  'this trip has already been rated',
  'duplicate passenger rating is rejected'
);

reset role;
select * from finish();
rollback;
