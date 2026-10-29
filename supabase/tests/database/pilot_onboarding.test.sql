begin;
create extension if not exists pgtap with schema extensions;
select no_plan();
insert into auth.users(id, raw_user_meta_data)
select ('20000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  jsonb_build_object('full_name', 'Pilot Onboarding ' || n, 'phone_number', '+26097200000' || n)
from generate_series(1, 3) n;
update public.profiles set role = 'admin' where id = '20000000-0000-0000-0000-000000000002';

set local role authenticated;
select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-000000000003', true);
select throws_ok($$select public.admin_provision_pilot_rider('20000000-0000-0000-0000-000000000001', 'Test make', 'Test model', 2025, 'TEST-001', 'Blue')$$,
  '42501', null, 'non-admin cannot provision a rider');
select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-000000000002', true);
select is(public.admin_provision_pilot_rider('20000000-0000-0000-0000-000000000001', 'Test make', 'Test model', 2025, 'TEST-001', 'Blue'),
  '20000000-0000-0000-0000-000000000001'::uuid, 'admin provisions the existing identity');
select is((select approval_status from public.profiles where id = '20000000-0000-0000-0000-000000000001'),
  'pending'::public.verification_status, 'provisional rider starts pending');
select is((select count(*) from public.motorcycles where rider_id = '20000000-0000-0000-0000-000000000001'), 1::bigint, 'motorcycle is recorded atomically');
select throws_ok($$select public.admin_set_driver_approval('20000000-0000-0000-0000-000000000001', 'approved')$$,
  '42501', null, 'legacy approval cannot bypass the onboarding checks');
select throws_ok($$select public.admin_activate_pilot_rider('20000000-0000-0000-0000-000000000001', true, false, true)$$,
  '22023', null, 'activation requires a completed safety briefing');
select throws_ok($$select public.admin_activate_pilot_rider('20000000-0000-0000-0000-000000000001', null, true, true)$$,
  '22023', null, 'null document confirmation cannot activate a rider');

select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-000000000001', true);
select is(public.apply_as_driver(), 'pending'::public.verification_status, 'provisioned rider check uses the correct role variable');
select throws_ok($$select public.set_driver_online(true)$$, '42501', null, 'provisional rider cannot go online');
select throws_ok($$select public.admin_activate_pilot_rider('20000000-0000-0000-0000-000000000001', true, true, true)$$,
  '42501', null, 'rider cannot activate their own account');
select throws_ok($$select * from private.pilot_rider_checks$$, '42501', null, 'rider cannot read or forge private attestations');

select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-000000000002', true);
select is(public.admin_activate_pilot_rider('20000000-0000-0000-0000-000000000001', true, true, true),
  'approved'::public.verification_status, 'admin can activate after every manual check');
select is((select is_online from public.profiles where id = '20000000-0000-0000-0000-000000000001'), false, 'activation does not silently put the rider online');
select throws_ok($$select public.admin_provision_pilot_rider('20000000-0000-0000-0000-000000000001', 'Make', 'Model', 2025, 'OTHER-001', 'Blue')$$,
  '22023', null, 'duplicate provisioning is rejected');
reset role;
select is((select count(*) from private.pilot_operations_audit where subject_id = '20000000-0000-0000-0000-000000000001'),
  2::bigint, 'provisioning and activation retain an audit record');
select * from finish();
rollback;
