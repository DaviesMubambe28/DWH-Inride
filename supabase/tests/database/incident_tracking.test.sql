begin;
create extension if not exists pgtap with schema extensions;
select plan(14);

insert into auth.users(id, raw_user_meta_data)
select ('72000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
  jsonb_build_object('full_name', 'Incident Test ' || n, 'phone_number', '+26097600000' || n)
from generate_series(1, 3) n;
update public.profiles set role = 'admin' where id = '72000000-0000-0000-0000-000000000001';

select is(has_function_privilege('anon', 'public.admin_record_incident(uuid,uuid,text,text,text)', 'EXECUTE'), false, 'anonymous cannot record incidents');
select is(has_table_privilege('authenticated', 'public.incidents', 'INSERT'), false, 'clients cannot insert incident rows directly');
select is(has_table_privilege('authenticated', 'public.incidents', 'UPDATE'), false, 'clients cannot update incident rows directly');

set local role authenticated;
select set_config('request.jwt.claim.sub', '72000000-0000-0000-0000-000000000002', true);
select throws_ok($$select public.admin_record_incident(null, null, 'safety', 'high', 'Passenger reported unsafe riding')$$,
  '42501', null, 'non-admin cannot record an incident');
select is((select count(*) from public.incidents), 0::bigint, 'non-admin cannot read incident rows');

select set_config('request.jwt.claim.sub', '72000000-0000-0000-0000-000000000001', true);
create temporary table new_incident as select public.admin_record_incident(
  null, '72000000-0000-0000-0000-000000000002', 'safety', 'high', 'Passenger reported unsafe riding'
) as id;
grant select on new_incident to authenticated;
select is((select count(*) from public.incidents), 1::bigint, 'admin records a durable incident');
select is((select summary from public.incidents where id = (select id from new_incident)), 'Passenger reported unsafe riding', 'summary is normalized and retained');
select throws_ok($$select public.admin_update_incident((select id from new_incident), 'resolved', 'high', null, 'short')$$,
  '22023', null, 'resolved incidents require a meaningful resolution note');
select throws_ok($$select public.admin_update_incident((select id from new_incident), 'in_review', 'high', '72000000-0000-0000-0000-000000000002', null)$$,
  '22023', null, 'incident assignment requires an admin');
select is(public.admin_update_incident((select id from new_incident), 'resolved', 'high', '72000000-0000-0000-0000-000000000001', 'Operator completed the safety review'), 'resolved', 'admin resolves with assignment and note');
select ok((select resolved_at is not null from public.incidents where id = (select id from new_incident)), 'resolution timestamp is recorded');

reset role;
select is((select count(*) from private.incident_audit where incident_id = (select id from new_incident)), 2::bigint, 'record and update actions are audited');
set local role authenticated;
select set_config('request.jwt.claim.sub', '72000000-0000-0000-0000-000000000003', true);
select is((select count(*) from public.incidents), 0::bigint, 'unrelated users cannot read incidents');
select throws_ok($$update public.incidents set summary = 'tampered incident record'$$, '42501', null, 'direct incident updates remain denied');

select * from finish();
rollback;
