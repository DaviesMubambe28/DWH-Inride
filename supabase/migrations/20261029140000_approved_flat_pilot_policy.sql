begin;

-- Approved operating parameters. Readiness verification still controls enablement.
alter table private.pilot_service_settings add column support_secondary_phone text;
update private.pilot_service_settings set enabled = false,
  center_latitude = -15.3292, center_longitude = 28.6820, radius_km = 4.0,
  support_phone = '+260971793185', support_secondary_phone = '+260761741656',
  support_email = 'garumitech@gmail.com', fare_policy_approved = true,
  cancellation_policy_approved = true where singleton;

create or replace function private.calculate_trip_financials(p_distance_meters integer, p_duration_seconds integer)
returns table (fare_amount numeric, service_fee numeric, driver_fare numeric)
language sql immutable security invoker set search_path = '' as $$
  -- Preserve the existing internal 15% split; passenger pays ZMW 20 total.
  select 20.00::numeric, 3.00::numeric, 17.00::numeric
$$;

create or replace function public.pilot_service_status()
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('available', private.pilot_service_open(),
    'message', case when private.pilot_service_open() then 'Pilot service is available. ZMW 20 flat, cash only.'
      else 'Service currently unavailable. Pilot hours are 06:00–20:00 CAT, Monday–Saturday.' end,
    'cash_only', true, 'fare_zmw', 20,
    'center_latitude', -15.3292, 'center_longitude', 28.6820, 'radius_km', 4.0,
    'support_phone', '+260971793185', 'support_secondary_phone', '+260761741656',
    'support_email', 'garumitech@gmail.com')
$$;
commit;
