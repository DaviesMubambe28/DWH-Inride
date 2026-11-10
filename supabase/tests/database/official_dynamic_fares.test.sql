begin;
create extension if not exists pgtap with schema extensions;
select plan(30);

select has_column('private', 'pilot_service_settings', 'free_waiting_minutes', 'free-waiting policy is stored');
select has_column('private', 'pilot_service_settings', 'rain_pricing_enabled', 'rain toggle is stored');
select has_column('private', 'fare_quotes', 'fare_breakdown', 'quotes store an auditable breakdown');
select has_column('public', 'trips', 'final_fare_breakdown', 'completed trips store final breakdown');
select has_column('public', 'trips', 'commission_settlement_status', 'commission settlement is distinct from passenger payment');
select has_function('public', 'create_official_fare_quote', array['uuid','numeric','numeric','text','numeric','numeric','text','integer','integer'], 'trusted official quote RPC exists');
select has_function('public', 'admin_set_rain_pricing', array['boolean'], 'authorized rain toggle exists');
select has_function('public', 'admin_revenue_summary', array[]::text[], 'admin revenue summary exists');
select is(has_function_privilege('authenticated', 'public.create_official_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)', 'EXECUTE'), false, 'mobile clients cannot forge trusted quotes');

select is((select pricing_mode from private.pilot_service_settings where singleton), 'DISTANCE_TIME', 'official distance/time mode is configured');
select is((select base_fare_zmw from private.pilot_service_settings where singleton), 21.00::numeric, 'base fare is K21');
select is((select rate_per_km_zmw from private.pilot_service_settings where singleton), 5.00::numeric, 'distance rate is K5 per km');
select is((select rate_per_minute_zmw from private.pilot_service_settings where singleton), 1.00::numeric, 'journey time rate is K1 per minute');
select is((select rider_share_percent + platform_share_percent from private.pilot_service_settings where singleton), 100.00::numeric, 'revenue shares add to 100 percent');

select is((select fare_amount from private.calculate_official_fare(500,60,0,'2026-11-10 12:00 Africa/Lusaka',false)), 24.50::numeric, '500 metre normal quote uses K21 plus distance and time');
select is((select fare_amount from private.calculate_official_fare(1000,60,0,'2026-11-10 12:00 Africa/Lusaka',false)), 27.00::numeric, 'one kilometre normal quote is deterministic');
select is((select fare_amount from private.calculate_official_fare(5000,720,0,'2026-11-10 07:30 Africa/Lusaka',false)), 63.00::numeric, 'five kilometre busy quote includes fixed K5 surge');
select is((select surge_charge from private.calculate_official_fare(5000,720,0,'2026-11-10 21:00 Africa/Lusaka',true)), 15.00::numeric, 'rain plus night surge is capped at K15');
select is((select surge_charge from private.calculate_official_fare(1000,60,0,'2026-11-10 05:00 Africa/Lusaka',false)), 0.00::numeric, 'night boundary excludes 05:00');
select is((select surge_charge from private.calculate_official_fare(1000,60,0,'2026-11-10 08:30 Africa/Lusaka',false)), 0.00::numeric, 'morning busy boundary excludes 08:30');
select is((select waiting_charge from private.calculate_official_fare(1000,60,180,'2026-11-10 12:00 Africa/Lusaka',false)), 0.00::numeric, 'exactly three waiting minutes are free');
select is((select waiting_charge from private.calculate_official_fare(1000,60,181,'2026-11-10 12:00 Africa/Lusaka',false)), 1.00::numeric, 'the first started minute after free waiting costs K1');
select is((select service_fee + driver_fare from private.calculate_official_fare(5000,720,600,'2026-11-10 07:30 Africa/Lusaka',false)), 70.00::numeric, '85/15 allocation adds exactly to the K70 example fare');
select is((select service_fee from private.calculate_official_fare(5000,720,600,'2026-11-10 07:30 Africa/Lusaka',false)), 10.50::numeric, 'K70 example records a K10.50 platform commission');
select is((select service_fee + driver_fare from private.calculate_official_fare(500,60,0,'2026-11-10 12:00 Africa/Lusaka',false)), 24.50::numeric, 'revenue allocation preserves an odd-ngwee total exactly');
select is(
  (select surge_charge from private.calculate_official_fare(1000,60,0,'2026-11-10 23:59 Africa/Lusaka',false)),
  (select surge_charge from private.calculate_official_fare(1000,60,0,'2026-11-11 00:01 Africa/Lusaka',false)),
  'night pricing remains stable when a quote crosses midnight'
);
select throws_ok($$select * from private.calculate_official_fare(null,60,0,'2026-11-10 12:00 Africa/Lusaka',false)$$,
  '22023', 'VALIDATED_ROUTE_REQUIRED', 'missing trusted route information cannot produce a fare');

insert into auth.users(id, raw_user_meta_data) values
  ('79000000-0000-0000-0000-000000000001', '{"full_name":"Pricing Passenger","phone_number":"+260976900001"}'),
  ('79000000-0000-0000-0000-000000000002', '{"full_name":"Pricing Admin","phone_number":"+260976900002"}');
update public.profiles set role = 'admin' where id = '79000000-0000-0000-0000-000000000002';

set local role authenticated;
select set_config('request.jwt.claim.sub', '79000000-0000-0000-0000-000000000001', true);
select throws_ok($$select public.admin_set_rain_pricing(true)$$,
  '42501', 'admin access required', 'passengers cannot toggle rain pricing');
reset role;
select throws_ok($$update private.pilot_service_settings set rate_per_km_zmw = 6 where singleton$$,
  '42501', 'pricing changes require the audited admin RPC', 'direct pricing mutation is denied');
set local role authenticated;
select set_config('request.jwt.claim.sub', '79000000-0000-0000-0000-000000000002', true);
select throws_ok($$select public.admin_update_official_pricing(5, 1, 21, 3, 1, 90, 15)$$,
  '22023', 'invalid official pricing configuration', 'admin cannot create revenue shares that exceed 100 percent');
reset role;

select * from finish();
rollback;
