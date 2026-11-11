begin;
create extension if not exists pgtap with schema extensions;
select plan(4);

update private.pilot_service_settings set enabled = true;
insert into auth.users(id, raw_user_meta_data)
values ('76000000-0000-0000-0000-000000000001', '{"full_name":"Quote Retry Passenger","phone_number":"+260976600001"}');

select set_config('request.jwt.claim.role', 'service_role', true);
set local role service_role;
create temporary table first_quote as select public.create_official_fare_quote(
  '76000000-0000-0000-0000-000000000001',
  -15.327476, 28.681337, 'Chongwe Hospital',
  -15.331000, 28.690000, 'Chongwe destination', 1500, 300
) as value;
create temporary table retried_quote as select public.create_official_fare_quote(
  '76000000-0000-0000-0000-000000000001',
  -15.327476, 28.681337, 'Chongwe Hospital',
  -15.331000, 28.690000, 'Chongwe destination', 1505, 305
) as value;
create temporary table other_quote as select public.create_official_fare_quote(
  '76000000-0000-0000-0000-000000000001',
  -15.327476, 28.681337, 'Chongwe Hospital',
  -15.334000, 28.694000, 'Different destination', 2100, 420
) as value;
reset role;
select set_config('request.jwt.claim.role', '', true);

select is(
  (select value->>'quote_id' from retried_quote),
  (select value->>'quote_id' from first_quote),
  'retrying the same passenger route reuses the active quote'
);
select is(
  (select count(*) from private.fare_quotes where passenger_id = '76000000-0000-0000-0000-000000000001'),
  2::bigint,
  'a retry creates no duplicate while a different destination creates a quote'
);
select isnt(
  (select value->>'quote_id' from other_quote),
  (select value->>'quote_id' from first_quote),
  'a different destination receives a distinct quote'
);
select is(
  has_function_privilege('authenticated', 'public.create_official_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)', 'EXECUTE'),
  false,
  'mobile clients cannot bypass the trusted quote service'
);

select * from finish();
rollback;
