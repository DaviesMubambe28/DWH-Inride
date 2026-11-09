begin;
create extension if not exists pgtap with schema extensions;
select plan(30);

select has_table('private', 'pilot_pricing_versions', 'versioned pricing configuration exists');
select has_table('private', 'fare_quotes', 'private fare quote storage exists');
select has_function('public', 'create_fare_quote', array['uuid','numeric','numeric','text','numeric','numeric','text','integer','integer'], 'trusted quote RPC exists');
select has_function('public', 'accept_fare_quote', array['uuid','uuid'], 'passenger quote acceptance RPC exists');
select has_function('public', 'admin_update_pilot_pricing', array['text','numeric','numeric','numeric','numeric','numeric','boolean'], 'admin pricing update RPC exists');
select is(has_function_privilege('anon', 'public.create_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)', 'EXECUTE'), false, 'anonymous users cannot create trusted quotes');
select is(has_function_privilege('authenticated', 'public.create_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)', 'EXECUTE'), false, 'mobile clients cannot create trusted quotes');
select is(has_function_privilege('anon', 'public.accept_fare_quote(uuid,uuid)', 'EXECUTE'), false, 'anonymous users cannot accept quotes');
select is(has_function_privilege('authenticated', 'public.accept_fare_quote(uuid,uuid)', 'EXECUTE'), true, 'authenticated passengers can accept their own quotes');

update private.pilot_service_settings set enabled = true, opens = '00:00', closes = '23:59:59.999999',
  weekdays = array[1,2,3,4,5,6,7], center_latitude = -15.3292, center_longitude = 28.6821,
  radius_km = 100, support_phone = '+260979000001', support_email = 'fixture@test.invalid',
  fare_policy_approved = true, cancellation_policy_approved = true;

insert into auth.users(id, raw_user_meta_data)
values
  ('74000000-0000-0000-0000-000000000001', '{"full_name":"Quote Passenger","phone_number":"+260976200001"}'),
  ('74000000-0000-0000-0000-000000000002', '{"full_name":"Other Passenger","phone_number":"+260976200002"}'),
  ('74000000-0000-0000-0000-000000000003', '{"full_name":"Pricing Admin","phone_number":"+260976200003"}');
update public.profiles set role = 'admin', approval_status = null
where id = '74000000-0000-0000-0000-000000000003';

select set_config('request.jwt.claim.role', 'service_role', true);
set local role service_role;
create temporary table quote_record as
select * from public.create_fare_quote(
  '74000000-0000-0000-0000-000000000001',
  -15.3292, 28.6821, 'Quote pickup', -15.33, 28.69, 'Quote destination', 4000, 600
);
reset role;
select set_config('request.jwt.claim.role', '', true);
grant select on quote_record to authenticated;

select is((select fare_amount from quote_record), 20.00::numeric, 'flat quote preserves the approved ZMW 20 fare');
select is((select service_fee from quote_record), 3.00::numeric, 'quote records the 15 percent platform fee');
select is((select driver_fare from quote_record), 17.00::numeric, 'quote records rider earnings after the fee');
select is((select pricing_mode from quote_record), 'FLAT_PILOT', 'quote records the effective pricing mode');
select ok((select expires_at > clock_timestamp() from quote_record), 'new quote has a future expiry');

set local role authenticated;
select set_config('request.jwt.claim.sub', '74000000-0000-0000-0000-000000000002', true);
select throws_ok(
  $$select * from public.accept_fare_quote((select quote_id from quote_record), '74100000-0000-0000-0000-000000000001')$$,
  '42501', 'fare quote not found', 'another passenger cannot accept the quote'
);

select set_config('request.jwt.claim.sub', '74000000-0000-0000-0000-000000000001', true);
create temporary table accepted_trip as
select * from public.accept_fare_quote(
  (select quote_id from quote_record), '74100000-0000-0000-0000-000000000001'
);
grant select on accepted_trip to authenticated;
select ok((select trip_id is not null from accepted_trip), 'owner accepts the quote and creates a trip');
reset role;
select is(
  (select fare_quote_id from public.trips where id = (select trip_id from accepted_trip)),
  (select quote_id from quote_record),
  'trip references the accepted quote'
);
select ok(
  (select accepted_fare_snapshot is not null from public.trips where id = (select trip_id from accepted_trip)),
  'trip stores an immutable accepted fare snapshot'
);
select set_config('app.trip_transition_authorized', 'true', true);
select throws_ok(
  $$update public.trips set fare_amount = 99 where id = (select trip_id from accepted_trip)$$,
  '42501', 'accepted fare snapshot is immutable', 'accepted fare fields cannot be changed afterward'
);
select set_config('app.trip_transition_authorized', '', true);

set local role authenticated;
select set_config('request.jwt.claim.sub', '74000000-0000-0000-0000-000000000001', true);
select is(
  (select trip_id from public.accept_fare_quote((select quote_id from quote_record), '74100000-0000-0000-0000-000000000001')),
  (select trip_id from accepted_trip),
  'replaying the same idempotency key returns the original trip'
);
select throws_ok(
  $$select * from public.accept_fare_quote((select quote_id from quote_record), '74100000-0000-0000-0000-000000000002')$$,
  '23505', 'fare quote has already been accepted', 'a different idempotency key cannot duplicate the trip'
);
reset role;
select lives_ok(
  $$delete from public.trips where id = (select trip_id from accepted_trip)$$,
  'retention can remove an accepted trip without a fare-quote foreign-key deadlock'
);
select is(
  (select accepted_trip_id from private.fare_quotes where id = (select quote_id from quote_record)),
  null::uuid,
  'deleting a retained trip detaches its quote while preserving the pricing record'
);
set local role authenticated;
select set_config('request.jwt.claim.sub', '74000000-0000-0000-0000-000000000001', true);
select throws_ok($$select public.admin_get_pilot_pricing()$$,
  '42501', 'admin access required', 'passenger cannot read protected pricing administration');
select throws_ok($$select public.admin_update_pilot_pricing('DISTANCE_TIME', 10, 2, 1, 15, 0, true)$$,
  '42501', 'admin access required', 'passenger cannot alter pricing');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', '74000000-0000-0000-0000-000000000003', true);
select throws_ok(
  $$select public.admin_update_pilot_pricing('DISTANCE_TIME', 10, 2, 1, 15, 0, false)$$,
  '42501', 'explicit DISTANCE_TIME approval is required', 'distance/time pricing requires explicit approval'
);
select is(
  public.admin_update_pilot_pricing('DISTANCE_TIME', 10, 2, 1, 15, 0, true),
  2::bigint,
  'admin approval creates the next immutable pricing version'
);
select is((public.admin_simulate_pilot_fare(4000, 600)->>'fare_zmw')::numeric, 28.00::numeric,
  'approved distance/time simulation uses server pricing');
select is((select count(*) from public.admin_pilot_pricing_history()), 2::bigint,
  'admin sees initial and updated pricing history');
reset role;

select set_config('request.jwt.claim.role', 'service_role', true);
set local role service_role;
create temporary table expired_quote as
select * from public.create_fare_quote(
  '74000000-0000-0000-0000-000000000001',
  -15.3292, 28.6821, 'Expired pickup', -15.33, 28.69, 'Expired destination', 4000, 600
);
reset role;
select set_config('request.jwt.claim.role', '', true);
grant select on expired_quote to authenticated;
update private.fare_quotes
set created_at = clock_timestamp() - interval '5 minutes', expires_at = clock_timestamp() - interval '1 minute'
where id = (select quote_id from expired_quote);
set local role authenticated;
select set_config('request.jwt.claim.sub', '74000000-0000-0000-0000-000000000001', true);
select throws_ok(
  $$select * from public.accept_fare_quote((select quote_id from expired_quote), '74100000-0000-0000-0000-000000000003')$$,
  'P0001', 'FARE_QUOTE_EXPIRED', 'expired quotes cannot create trips'
);

select * from finish();
rollback;
