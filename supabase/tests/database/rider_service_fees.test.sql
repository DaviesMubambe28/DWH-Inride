begin;
create extension if not exists pgtap with schema extensions;
select plan(29);

update private.pilot_service_settings set enabled = true, opens = '00:00', closes = '23:59:59.999999',
  weekdays = array[1,2,3,4,5,6,7], center_latitude = -15.3292, center_longitude = 28.6821,
  radius_km = 100, fare_policy_approved = true, cancellation_policy_approved = true;

select has_table('public', 'rider_service_fee_ledger', 'service fee ledger exists');
select has_table('public', 'rider_service_fee_payments', 'payment submissions exist');
select has_function('public', 'get_my_service_fee_account', array[]::text[], 'rider account RPC exists');
select has_function('public', 'submit_service_fee_payment', array['numeric','text','text'], 'payment submission RPC exists');
select has_function('public', 'admin_review_service_fee_payment', array['uuid','text','text'], 'admin review RPC exists');
select has_view('public', 'admin_service_fee_payment_config', 'admin payment configuration view exists');
select is(has_table_privilege('authenticated', 'public.rider_service_fee_ledger', 'INSERT'), false, 'riders cannot insert ledger entries');
select is(has_table_privilege('authenticated', 'public.rider_service_fee_payments', 'UPDATE'), false, 'riders cannot approve payments directly');
select is(has_function_privilege('anon', 'public.submit_service_fee_payment(numeric,text,text)', 'EXECUTE'), false, 'anonymous users cannot submit payments');

insert into auth.users(id, raw_user_meta_data)
values
  ('73000000-0000-0000-0000-000000000001', '{"full_name":"Fee Passenger","phone_number":"+260976100001"}'),
  ('73000000-0000-0000-0000-000000000002', '{"full_name":"Fee Rider","phone_number":"+260976100002"}'),
  ('73000000-0000-0000-0000-000000000003', '{"full_name":"Fee Admin","phone_number":"+260976100003"}'),
  ('73000000-0000-0000-0000-000000000004', '{"full_name":"Second Passenger","phone_number":"+260976100004"}');
update public.profiles set role = 'rider', approval_status = 'approved' where id = '73000000-0000-0000-0000-000000000002';
update public.profiles set role = 'admin', approval_status = null where id = '73000000-0000-0000-0000-000000000003';

insert into public.trips(id, passenger_id, rider_id, status, pickup_lat, pickup_lng, pickup_address,
  dropoff_lat, dropoff_lng, dropoff_address, route_distance_meters, route_duration_seconds, payment_method, started_at)
values
  ('73100000-0000-0000-0000-000000000001', '73000000-0000-0000-0000-000000000001', '73000000-0000-0000-0000-000000000002', 'in_progress', -15.3, 28.6, 'A', -15.4, 28.7, 'B', 4000, 600, 'cash', now()),
  ('73100000-0000-0000-0000-000000000002', '73000000-0000-0000-0000-000000000004', '73000000-0000-0000-0000-000000000002', 'in_progress', -15.3, 28.6, 'A', -15.4, 28.7, 'B', 4000, 600, 'cash', now());

set local role authenticated;
select set_config('request.jwt.claim.sub', '73000000-0000-0000-0000-000000000002', true);
select ok(public.complete_trip('73100000-0000-0000-0000-000000000001'), 'first completion succeeds');
select is((select count(*) from public.rider_service_fee_ledger), 1::bigint, 'completion creates exactly one charge');
select is((select amount_zmw from public.rider_service_fee_ledger), 3.00::numeric, 'charge uses server-calculated trip service fee');
select is(public.complete_trip('73100000-0000-0000-0000-000000000001'), false, 'repeated completion is idempotently rejected');
select is((select count(*) from public.rider_service_fee_ledger), 1::bigint, 'repeated completion cannot duplicate charge');
select ok(public.complete_trip('73100000-0000-0000-0000-000000000002'), 'second completion succeeds');
select is((public.get_my_service_fee_account()->>'outstanding_zmw')::numeric, 6.00::numeric, 'multiple trip fees accumulate');

create temporary table partial_payment as select public.submit_service_fee_payment(2.00, 'FEE-PARTIAL-001', null) as id;
grant select on partial_payment to authenticated;
select throws_ok($$select public.submit_service_fee_payment(1.00, 'FEE-PARTIAL-001', null)$$,
  '23505', null, 'duplicate rider transaction reference is rejected');
select is((select status from public.rider_service_fee_payments where id = (select id from partial_payment)), 'pending_verification', 'submission remains pending and does not clear balance');
select is((public.get_my_service_fee_account()->>'outstanding_zmw')::numeric, 6.00::numeric, 'unverified submission leaves balance unchanged');
select throws_ok($$select public.admin_review_service_fee_payment((select id from partial_payment), 'verified', 'rider self approval')$$,
  '42501', null, 'rider cannot verify own payment');
select throws_ok($$select public.admin_update_service_fee_payment_config('Fake', '0000', 'Rider supplied destination')$$,
  '42501', null, 'rider cannot change official payment destination');

select set_config('request.jwt.claim.sub', '73000000-0000-0000-0000-000000000003', true);
select ok(public.admin_update_service_fee_payment_config('MTN MoMo', '+260000000000', 'Send the exact amount and use the displayed reference.'), 'admin configures payment destination');
select is((public.admin_review_service_fee_payment((select id from partial_payment), 'verified', 'Matched operator statement')->>'remaining_outstanding_zmw')::numeric, 4.00::numeric, 'partial verified payment only reduces its amount');
select throws_ok($$select public.admin_review_service_fee_payment((select id from partial_payment), 'verified', 'review twice')$$,
  '22023', null, 'verified payment cannot be processed twice');

select set_config('request.jwt.claim.sub', '73000000-0000-0000-0000-000000000002', true);
create temporary table rejected_payment as select public.submit_service_fee_payment(1.00, 'FEE-REJECT-001', null) as id;
grant select on rejected_payment to authenticated;
select set_config('request.jwt.claim.sub', '73000000-0000-0000-0000-000000000003', true);
select is((public.admin_review_service_fee_payment((select id from rejected_payment), 'rejected', 'Reference not found')->>'remaining_outstanding_zmw')::numeric, 4.00::numeric, 'rejected payment leaves balance unchanged');

select set_config('request.jwt.claim.sub', '73000000-0000-0000-0000-000000000002', true);
create temporary table final_payment as select public.submit_service_fee_payment(4.00, 'FEE-FINAL-001', null) as id;
grant select on final_payment to authenticated;
select set_config('request.jwt.claim.sub', '73000000-0000-0000-0000-000000000003', true);
select is((public.admin_review_service_fee_payment((select id from final_payment), 'verified', 'Full remainder received')->>'remaining_outstanding_zmw')::numeric, 0.00::numeric, 'full verified remainder clears balance to zero');

select set_config('request.jwt.claim.sub', '73000000-0000-0000-0000-000000000002', true);
select is((public.get_my_service_fee_account()->>'payment_recipient'), '+260000000000', 'rider receives backend-configured payment destination');
select throws_ok($$update public.rider_service_fee_ledger set amount_zmw = 99$$,
  '42501', null, 'ledger history is immutable to rider clients');
reset role;
select is((select count(*) from private.service_fee_payment_audit), 6::bigint, 'submissions and reviews are audited');

select * from finish();
rollback;
