begin;

alter table private.pilot_service_settings
  add column if not exists pricing_mode text not null default 'FLAT_PILOT'
    constraint pilot_service_settings_pricing_mode_check check (pricing_mode in ('FLAT_PILOT', 'DISTANCE_TIME')),
  add column if not exists pricing_mode_approved boolean not null default false,
  add column if not exists base_fare_zmw numeric(12,2) not null default 20.00,
  add column if not exists rate_per_km_zmw numeric(12,2) not null default 0.00,
  add column if not exists rate_per_minute_zmw numeric(12,2) not null default 0.00,
  add column if not exists minimum_fare_zmw numeric(12,2) not null default 20.00,
  add column if not exists approved_adjustments_zmw numeric(12,2) not null default 0.00;

update private.pilot_service_settings
set enabled = false,
    pricing_mode = 'FLAT_PILOT',
    pricing_mode_approved = false,
    base_fare_zmw = 20.00,
    rate_per_km_zmw = 0.00,
    rate_per_minute_zmw = 0.00,
    minimum_fare_zmw = 20.00,
    approved_adjustments_zmw = 0.00
where singleton;

create or replace function private.calculate_trip_financials(p_distance_meters integer, p_duration_seconds integer)
returns table (fare_amount numeric, service_fee numeric, driver_fare numeric)
language sql immutable security invoker set search_path = '' as $$
  with cfg as (
    select
      coalesce((select pricing_mode from private.pilot_service_settings where singleton), 'FLAT_PILOT') as pricing_mode,
      coalesce((select base_fare_zmw from private.pilot_service_settings where singleton), 20.00) as base_fare_zmw,
      coalesce((select rate_per_km_zmw from private.pilot_service_settings where singleton), 0.00) as rate_per_km_zmw,
      coalesce((select rate_per_minute_zmw from private.pilot_service_settings where singleton), 0.00) as rate_per_minute_zmw,
      coalesce((select minimum_fare_zmw from private.pilot_service_settings where singleton), 20.00) as minimum_fare_zmw,
      coalesce((select approved_adjustments_zmw from private.pilot_service_settings where singleton), 0.00) as approved_adjustments_zmw
  ), computed as (
    select case
      when pricing_mode = 'DISTANCE_TIME' then greatest(
        minimum_fare_zmw,
        base_fare_zmw
          + (p_distance_meters::numeric / 1000.0) * rate_per_km_zmw
          + (p_duration_seconds::numeric / 60.0) * rate_per_minute_zmw
          + approved_adjustments_zmw
      )
      else 20.00::numeric
    end as total_fare
    from cfg
  )
  select
    round(total_fare, 2) as fare_amount,
    round(total_fare * 0.15, 2) as service_fee,
    round(total_fare * 0.85, 2) as driver_fare
  from computed;
$$;

create or replace function public.pilot_service_status()
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'available', private.pilot_service_open(),
    'service_24_7', true,
    'riders_available', exists (
      select 1
      from public.profiles p
      join private.pilot_rider_presence presence on presence.rider_id = p.id
      where p.role = 'rider' and p.approval_status = 'approved' and p.is_online
        and private.pilot_service_open()
        and presence.recorded_at >= now() - interval '2 minutes'
        and private.pilot_point_allowed(presence.latitude, presence.longitude)
    ),
    'message', case when private.pilot_service_open()
      then 'Service available 24/7. Rider availability may vary; check rider status before requesting.'
      else 'Service temporarily paused or not configured. Please try again later.' end,
    'cash_only', true,
    'fare_zmw', coalesce((select minimum_fare_zmw from private.pilot_service_settings where singleton), 20.00),
    'pricing_mode', coalesce((select pricing_mode from private.pilot_service_settings where singleton), 'FLAT_PILOT'),
    'pricing_mode_approved', coalesce((select pricing_mode_approved from private.pilot_service_settings where singleton), false),
    'base_fare_zmw', coalesce((select base_fare_zmw from private.pilot_service_settings where singleton), 20.00),
    'rate_per_km_zmw', coalesce((select rate_per_km_zmw from private.pilot_service_settings where singleton), 0.00),
    'rate_per_minute_zmw', coalesce((select rate_per_minute_zmw from private.pilot_service_settings where singleton), 0.00),
    'minimum_fare_zmw', coalesce((select minimum_fare_zmw from private.pilot_service_settings where singleton), 20.00),
    'approved_adjustments_zmw', coalesce((select approved_adjustments_zmw from private.pilot_service_settings where singleton), 0.00),
    'center_latitude', -15.3292,
    'center_longitude', 28.6820,
    'radius_km', 4.0,
    'support_phone', '+260770001203',
    'support_secondary_phone', '+260975461121',
    'support_email', 'gershmukuka1@gmail.com'
  );
$$;

commit;
