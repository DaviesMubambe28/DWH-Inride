begin;

-- Owner-approved monitored contacts. Keep bookings disabled while readiness gates remain open.
update private.pilot_service_settings
set support_phone = '+260770001203',
    support_secondary_phone = '+260975461121',
    support_email = 'gershmukuka1@gmail.com'
where singleton;

create or replace function public.pilot_service_status()
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('available', private.pilot_service_open(),
    'message', case when private.pilot_service_open() then 'Pilot service is available. ZMW 20 flat, cash only.'
      else 'Service currently unavailable. Pilot hours are 06:00–20:00 CAT, Monday–Saturday.' end,
    'cash_only', true, 'fare_zmw', 20,
    'center_latitude', -15.3292, 'center_longitude', 28.6820, 'radius_km', 4.0,
    'support_phone', '+260770001203', 'support_secondary_phone', '+260975461121',
    'support_email', 'gershmukuka1@gmail.com')
$$;

commit;
