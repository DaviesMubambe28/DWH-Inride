begin;

alter table private.pilot_service_settings
  add column if not exists free_waiting_minutes integer not null default 3 check (free_waiting_minutes between 0 and 60),
  add column if not exists waiting_rate_zmw numeric(12,2) not null default 1.00 check (waiting_rate_zmw between 0 and 1000),
  add column if not exists rider_share_percent numeric(5,2) not null default 85.00,
  add column if not exists platform_share_percent numeric(5,2) not null default 15.00,
  add column if not exists rain_pricing_enabled boolean not null default false,
  add column if not exists night_surge_zmw numeric(12,2) not null default 10.00,
  add column if not exists busy_surge_zmw numeric(12,2) not null default 5.00,
  add column if not exists rain_surge_zmw numeric(12,2) not null default 10.00,
  add column if not exists maximum_surge_zmw numeric(12,2) not null default 15.00,
  add constraint pilot_revenue_share_complete check (rider_share_percent + platform_share_percent = 100.00);

alter table private.pilot_pricing_versions
  add column if not exists free_waiting_minutes integer not null default 3,
  add column if not exists waiting_rate_zmw numeric(12,2) not null default 1.00,
  add column if not exists rider_share_percent numeric(5,2) not null default 85.00,
  add column if not exists platform_share_percent numeric(5,2) not null default 15.00,
  add column if not exists rain_pricing_enabled boolean not null default false,
  add column if not exists night_surge_zmw numeric(12,2) not null default 10.00,
  add column if not exists busy_surge_zmw numeric(12,2) not null default 5.00,
  add column if not exists rain_surge_zmw numeric(12,2) not null default 10.00,
  add column if not exists maximum_surge_zmw numeric(12,2) not null default 15.00;

alter table private.fare_quotes
  add column if not exists fare_breakdown jsonb not null default '{}'::jsonb,
  add column if not exists surge_charge numeric(12,2) not null default 0,
  add column if not exists rain_pricing_applied boolean not null default false;

alter table public.trips
  add column if not exists final_fare_breakdown jsonb,
  add column if not exists waiting_duration_seconds integer check (waiting_duration_seconds is null or waiting_duration_seconds >= 0),
  add column if not exists commission_settlement_status text not null default 'outstanding'
    check (commission_settlement_status in ('outstanding', 'submitted', 'settled'));

comment on column private.fare_quotes.fare_breakdown is
  'Server-authored quote components in ZMW. Distance and journey-time components are rounded to the nearest ngwee; pickup waiting rounds up to whole minutes after three free minutes.';
comment on column public.trips.final_fare_breakdown is
  'Server-authored completion snapshot. Surge is fixed at quote time; waiting and actual journey duration are finalized from trusted trip timestamps.';

update private.pilot_pricing_audit_guard set config_update_in_progress = true where singleton;
update private.pilot_service_settings set
  pricing_mode = 'DISTANCE_TIME', pricing_mode_approved = true,
  base_fare_zmw = 21.00, rate_per_km_zmw = 5.00, rate_per_minute_zmw = 1.00,
  minimum_fare_zmw = 21.00, approved_adjustments_zmw = 0,
  free_waiting_minutes = 3, waiting_rate_zmw = 1.00,
  rider_share_percent = 85.00, platform_share_percent = 15.00,
  rain_pricing_enabled = false, night_surge_zmw = 10.00,
  busy_surge_zmw = 5.00, rain_surge_zmw = 10.00, maximum_surge_zmw = 15.00,
  pricing_config_version = pricing_config_version + 1
where singleton;
update private.pilot_pricing_audit_guard set config_update_in_progress = false where singleton;

insert into private.pilot_pricing_versions (
  version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
  rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw,
  free_waiting_minutes, waiting_rate_zmw, rider_share_percent, platform_share_percent,
  rain_pricing_enabled, night_surge_zmw, busy_surge_zmw, rain_surge_zmw, maximum_surge_zmw
)
select pricing_config_version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
  rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw,
  free_waiting_minutes, waiting_rate_zmw, rider_share_percent, platform_share_percent,
  rain_pricing_enabled, night_surge_zmw, busy_surge_zmw, rain_surge_zmw, maximum_surge_zmw
from private.pilot_service_settings where singleton
on conflict (version) do nothing;

create or replace function private.calculate_official_fare(
  p_distance_meters integer,
  p_journey_seconds integer,
  p_waiting_seconds integer,
  p_priced_at timestamptz,
  p_rain_pricing boolean
)
returns table (
  fare_amount numeric, service_fee numeric, driver_fare numeric,
  base_fare numeric, distance_charge numeric, time_charge numeric,
  waiting_charge numeric, surge_charge numeric,
  night_applied boolean, busy_applied boolean, rain_applied boolean
)
language plpgsql stable security definer set search_path = '' as $$
declare
  cfg private.pilot_service_settings%rowtype;
  local_time time;
  base_ngwee bigint;
  distance_ngwee bigint;
  time_ngwee bigint;
  waiting_ngwee bigint;
  surge_ngwee bigint;
  total_ngwee bigint;
  platform_ngwee bigint;
begin
  select * into strict cfg from private.pilot_service_settings where singleton;
  if p_distance_meters is null or p_distance_meters <= 0
    or p_journey_seconds is null or p_journey_seconds <= 0
    or p_waiting_seconds is null or p_waiting_seconds < 0
    or p_priced_at is null then
    raise exception 'VALIDATED_ROUTE_REQUIRED' using errcode = '22023';
  end if;
  local_time := p_priced_at at time zone 'Africa/Lusaka';
  night_applied := local_time >= time '21:00' or local_time < time '05:00';
  busy_applied := (local_time >= time '07:00' and local_time < time '08:30')
    or (local_time >= time '16:30' and local_time < time '18:30');
  rain_applied := coalesce(p_rain_pricing, false);

  base_ngwee := round(cfg.base_fare_zmw * 100);
  distance_ngwee := round((p_distance_meters::numeric / 1000) * cfg.rate_per_km_zmw * 100);
  time_ngwee := round((p_journey_seconds::numeric / 60) * cfg.rate_per_minute_zmw * 100);
  waiting_ngwee := greatest(0, ceil(p_waiting_seconds::numeric / 60)::bigint - cfg.free_waiting_minutes)
    * round(cfg.waiting_rate_zmw * 100);
  surge_ngwee := least(
    round(cfg.maximum_surge_zmw * 100),
    (case when night_applied then round(cfg.night_surge_zmw * 100) else 0 end)
      + (case when busy_applied then round(cfg.busy_surge_zmw * 100) else 0 end)
      + (case when rain_applied then round(cfg.rain_surge_zmw * 100) else 0 end)
  );
  total_ngwee := greatest(round(cfg.minimum_fare_zmw * 100), base_ngwee + distance_ngwee + time_ngwee + waiting_ngwee)
    + surge_ngwee;
  platform_ngwee := round(total_ngwee * cfg.platform_share_percent / 100);

  fare_amount := total_ngwee / 100.0;
  service_fee := platform_ngwee / 100.0;
  driver_fare := (total_ngwee - platform_ngwee) / 100.0;
  base_fare := base_ngwee / 100.0;
  distance_charge := distance_ngwee / 100.0;
  time_charge := time_ngwee / 100.0;
  waiting_charge := waiting_ngwee / 100.0;
  surge_charge := surge_ngwee / 100.0;
  return next;
end;
$$;

revoke all on function private.calculate_official_fare(integer,integer,integer,timestamptz,boolean)
  from public, anon, authenticated;
grant execute on function private.calculate_official_fare(integer,integer,integer,timestamptz,boolean) to service_role;

create or replace function private.apply_official_quote_amounts()
returns trigger language plpgsql security definer set search_path = '' as $$
declare f record; rain_enabled boolean;
begin
  select rain_pricing_enabled into strict rain_enabled
  from private.pilot_service_settings where singleton;
  select * into strict f from private.calculate_official_fare(
    new.route_distance_meters, new.route_duration_seconds, 0,
    coalesce(new.created_at, clock_timestamp()), rain_enabled
  );
  new.fare_amount := f.fare_amount;
  new.service_fee := f.service_fee;
  new.driver_fare := f.driver_fare;
  new.surge_charge := f.surge_charge;
  new.rain_pricing_applied := f.rain_applied;
  new.fare_breakdown := jsonb_build_object(
    'base_fare_zmw', f.base_fare,
    'distance_charge_zmw', f.distance_charge,
    'time_charge_zmw', f.time_charge,
    'waiting_charge_zmw', 0,
    'surge_charge_zmw', f.surge_charge,
    'night_applied', f.night_applied,
    'busy_applied', f.busy_applied,
    'rain_applied', f.rain_applied,
    'free_waiting_minutes', 3,
    'rounding_policy', 'distance and journey time round to nearest ngwee; waiting rounds up to each started minute after 3 free minutes'
  );
  return new;
end;
$$;
revoke all on function private.apply_official_quote_amounts() from public, anon, authenticated;
drop trigger if exists apply_official_quote_amounts on private.fare_quotes;
create trigger apply_official_quote_amounts before insert on private.fare_quotes
for each row execute function private.apply_official_quote_amounts();

create or replace function public.create_official_fare_quote(
  p_passenger_id uuid,
  p_pickup_lat numeric, p_pickup_lng numeric, p_pickup_address text,
  p_dropoff_lat numeric, p_dropoff_lng numeric, p_dropoff_address text,
  p_route_distance_meters integer, p_route_duration_seconds integer
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare created record; quote private.fare_quotes%rowtype;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'trusted quote service required' using errcode = '42501';
  end if;
  select * into strict created from public.create_fare_quote(
    p_passenger_id, p_pickup_lat, p_pickup_lng, p_pickup_address,
    p_dropoff_lat, p_dropoff_lng, p_dropoff_address,
    p_route_distance_meters, p_route_duration_seconds
  );
  select * into strict quote from private.fare_quotes where id = created.quote_id;
  return jsonb_build_object(
    'quote_id', quote.id, 'fare_amount', quote.fare_amount,
    'service_fee', quote.service_fee, 'driver_fare', quote.driver_fare,
    'route_distance_meters', quote.route_distance_meters,
    'route_duration_seconds', quote.route_duration_seconds,
    'pricing_mode', quote.pricing_mode, 'pricing_version', quote.pricing_version,
    'expires_at', quote.expires_at, 'fare_breakdown', quote.fare_breakdown
  );
end;
$$;
revoke all on function public.create_official_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)
  from public, anon, authenticated;
grant execute on function public.create_official_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)
  to service_role;

create or replace function private.enrich_accepted_fare_snapshot()
returns trigger language plpgsql security definer set search_path = '' as $$
declare quote_breakdown jsonb;
begin
  if old.fare_quote_id is null and new.fare_quote_id is not null then
    select fare_breakdown into strict quote_breakdown
    from private.fare_quotes where id = new.fare_quote_id;
    new.accepted_fare_snapshot := coalesce(new.accepted_fare_snapshot, '{}'::jsonb)
      || jsonb_build_object('fare_breakdown', quote_breakdown);
  end if;
  return new;
end;
$$;
revoke all on function private.enrich_accepted_fare_snapshot() from public, anon, authenticated;
drop trigger if exists enrich_accepted_fare_snapshot on public.trips;
create trigger enrich_accepted_fare_snapshot before update on public.trips
for each row execute function private.enrich_accepted_fare_snapshot();

create or replace function private.calculate_trip_financials(p_distance_meters integer, p_duration_seconds integer)
returns table (fare_amount numeric, service_fee numeric, driver_fare numeric)
language sql stable security definer set search_path = '' as $$
  select f.fare_amount, f.service_fee, f.driver_fare
  from private.calculate_official_fare(
    p_distance_meters, p_duration_seconds, 0, clock_timestamp(),
    (select rain_pricing_enabled from private.pilot_service_settings where singleton)
  ) f
$$;

create or replace function private.prevent_pricing_settings_mutation()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if (
    new.pricing_mode is distinct from old.pricing_mode
    or new.pricing_mode_approved is distinct from old.pricing_mode_approved
    or new.base_fare_zmw is distinct from old.base_fare_zmw
    or new.rate_per_km_zmw is distinct from old.rate_per_km_zmw
    or new.rate_per_minute_zmw is distinct from old.rate_per_minute_zmw
    or new.minimum_fare_zmw is distinct from old.minimum_fare_zmw
    or new.approved_adjustments_zmw is distinct from old.approved_adjustments_zmw
    or new.pricing_config_version is distinct from old.pricing_config_version
    or new.free_waiting_minutes is distinct from old.free_waiting_minutes
    or new.waiting_rate_zmw is distinct from old.waiting_rate_zmw
    or new.rider_share_percent is distinct from old.rider_share_percent
    or new.platform_share_percent is distinct from old.platform_share_percent
    or new.rain_pricing_enabled is distinct from old.rain_pricing_enabled
    or new.night_surge_zmw is distinct from old.night_surge_zmw
    or new.busy_surge_zmw is distinct from old.busy_surge_zmw
    or new.rain_surge_zmw is distinct from old.rain_surge_zmw
    or new.maximum_surge_zmw is distinct from old.maximum_surge_zmw
  ) and not exists (
    select 1 from private.pilot_pricing_audit_guard where singleton and config_update_in_progress
  ) then
    raise exception 'pricing changes require the audited admin RPC' using errcode = '42501';
  end if;
  return new;
end;
$$;

create or replace function private.prevent_accepted_fare_mutation()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if old.accepted_fare_snapshot is not null and (
    new.accepted_fare_snapshot is distinct from old.accepted_fare_snapshot
    or new.fare_quote_id is distinct from old.fare_quote_id
    or (
      coalesce(current_setting('app.fare_finalization_authorized', true), '') <> 'true'
      and (new.fare_amount, new.service_fee, new.driver_fare,
        new.route_distance_meters, new.route_duration_seconds,
        new.final_fare_breakdown, new.waiting_duration_seconds)
      is distinct from
        (old.fare_amount, old.service_fee, old.driver_fare,
        old.route_distance_meters, old.route_duration_seconds,
        old.final_fare_breakdown, old.waiting_duration_seconds)
    )
  ) then
    raise exception 'accepted fare snapshot is immutable outside trusted finalization' using errcode = '42501';
  end if;
  return new;
end;
$$;

create or replace function public.complete_trip(p_trip_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  actor_id uuid := (select auth.uid());
  trip_row public.trips%rowtype;
  quote private.fare_quotes%rowtype;
  final_amounts record;
  completed_at_value timestamptz := clock_timestamp();
  waiting_seconds integer;
  journey_seconds integer;
begin
  if actor_id is null or not private.is_approved_rider() then
    raise exception 'only the approved assigned rider can complete trips' using errcode = '42501';
  end if;
  select * into trip_row from public.trips
  where id = p_trip_id and rider_id = actor_id for update;
  if not found or trip_row.status <> 'in_progress' then return false; end if;
  if trip_row.fare_quote_id is null then
    -- Preserve pre-quote trips already active during this migration. Their
    -- previously accepted amount is not silently replaced by the new policy.
    perform set_config('app.trip_transition_authorized', 'true', true);
    update public.trips set status = 'completed', completed_at = completed_at_value
    where id = trip_row.id;
    insert into public.rider_service_fee_ledger(rider_id, entry_type, amount_zmw, trip_id)
    values (actor_id, 'trip_charge', trip_row.service_fee, trip_row.id)
    on conflict (trip_id) where trip_id is not null do nothing;
    update public.profiles set total_trips = total_trips + 1
    where id in (actor_id, trip_row.passenger_id);
    return true;
  end if;
  if trip_row.arrived_at is null or trip_row.started_at is null then
    raise exception 'trusted trip timing is required' using errcode = '22023';
  end if;
  select * into strict quote from private.fare_quotes where id = trip_row.fare_quote_id;
  waiting_seconds := greatest(0, extract(epoch from (trip_row.started_at - trip_row.arrived_at))::integer);
  journey_seconds := greatest(1, extract(epoch from (completed_at_value - trip_row.started_at))::integer);
  select * into strict final_amounts from private.calculate_official_fare(
    quote.route_distance_meters, journey_seconds, waiting_seconds,
    quote.created_at, quote.rain_pricing_applied
  );
  perform set_config('app.trip_transition_authorized', 'true', true);
  perform set_config('app.fare_finalization_authorized', 'true', true);
  update public.trips set
    status = 'completed', completed_at = completed_at_value,
    fare_amount = final_amounts.fare_amount,
    service_fee = final_amounts.service_fee,
    driver_fare = final_amounts.driver_fare,
    route_duration_seconds = journey_seconds,
    waiting_duration_seconds = waiting_seconds,
    final_fare_breakdown = jsonb_build_object(
      'base_fare_zmw', final_amounts.base_fare,
      'distance_charge_zmw', final_amounts.distance_charge,
      'time_charge_zmw', final_amounts.time_charge,
      'waiting_charge_zmw', final_amounts.waiting_charge,
      'surge_charge_zmw', final_amounts.surge_charge,
      'night_applied', final_amounts.night_applied,
      'busy_applied', final_amounts.busy_applied,
      'rain_applied', final_amounts.rain_applied,
      'waiting_seconds', waiting_seconds,
      'journey_seconds', journey_seconds,
      'pricing_version', quote.pricing_version
    )
  where id = trip_row.id;
  insert into public.rider_service_fee_ledger(rider_id, entry_type, amount_zmw, trip_id)
  values (actor_id, 'trip_charge', final_amounts.service_fee, trip_row.id)
  on conflict (trip_id) where trip_id is not null do nothing;
  update public.profiles set total_trips = total_trips + 1
  where id in (actor_id, trip_row.passenger_id);
  return true;
end;
$$;
revoke all on function public.complete_trip(uuid) from public, anon;
grant execute on function public.complete_trip(uuid) to authenticated;

create or replace function public.admin_set_rain_pricing(p_enabled boolean)
returns bigint language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid()); before_cfg jsonb; new_version bigint;
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  select to_jsonb(s) into before_cfg from private.pilot_service_settings s where singleton for update;
  update private.pilot_pricing_audit_guard set config_update_in_progress = true where singleton;
  update private.pilot_service_settings set rain_pricing_enabled = p_enabled,
    pricing_config_version = pricing_config_version + 1 where singleton
    returning pricing_config_version into new_version;
  insert into private.pilot_pricing_versions (
    version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
    rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw,
    free_waiting_minutes, waiting_rate_zmw, rider_share_percent, platform_share_percent,
    rain_pricing_enabled, night_surge_zmw, busy_surge_zmw, rain_surge_zmw, maximum_surge_zmw, changed_by
  ) select pricing_config_version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
    rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw,
    free_waiting_minutes, waiting_rate_zmw, rider_share_percent, platform_share_percent,
    rain_pricing_enabled, night_surge_zmw, busy_surge_zmw, rain_surge_zmw, maximum_surge_zmw, actor
  from private.pilot_service_settings where singleton;
  insert into private.pilot_pricing_audit(actor_id, action, pricing_version, before_config, after_config)
  select actor, 'CONFIGURATION_UPDATED', new_version, before_cfg, to_jsonb(s)
  from private.pilot_service_settings s where singleton;
  update private.pilot_pricing_audit_guard set config_update_in_progress = false where singleton;
  return new_version;
end;
$$;
revoke all on function public.admin_set_rain_pricing(boolean) from public, anon, authenticated;
grant execute on function public.admin_set_rain_pricing(boolean) to authenticated;

create or replace function public.admin_update_official_pricing(
  p_rate_per_km_zmw numeric,
  p_rate_per_minute_zmw numeric,
  p_minimum_fare_zmw numeric,
  p_free_waiting_minutes integer,
  p_waiting_rate_zmw numeric,
  p_rider_share_percent numeric,
  p_platform_share_percent numeric
)
returns bigint language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid()); before_cfg jsonb; new_version bigint;
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  if p_rate_per_km_zmw is null or p_rate_per_km_zmw < 0 or p_rate_per_km_zmw > 1000
    or p_rate_per_minute_zmw is null or p_rate_per_minute_zmw < 0 or p_rate_per_minute_zmw > 1000
    or p_minimum_fare_zmw is null or p_minimum_fare_zmw < 21 or p_minimum_fare_zmw > 10000
    or p_free_waiting_minutes is null or p_free_waiting_minutes not between 0 and 60
    or p_waiting_rate_zmw is null or p_waiting_rate_zmw < 0 or p_waiting_rate_zmw > 1000
    or p_rider_share_percent is null or p_platform_share_percent is null
    or p_rider_share_percent <= 0 or p_platform_share_percent <= 0
    or p_rider_share_percent + p_platform_share_percent <> 100
    or scale(p_rate_per_km_zmw) > 2 or scale(p_rate_per_minute_zmw) > 2
    or scale(p_minimum_fare_zmw) > 2 or scale(p_waiting_rate_zmw) > 2
    or scale(p_rider_share_percent) > 2 or scale(p_platform_share_percent) > 2 then
    raise exception 'invalid official pricing configuration' using errcode = '22023';
  end if;
  select to_jsonb(s) into before_cfg from private.pilot_service_settings s where singleton for update;
  update private.pilot_pricing_audit_guard set config_update_in_progress = true where singleton;
  update private.pilot_service_settings set
    pricing_mode = 'DISTANCE_TIME', pricing_mode_approved = true,
    base_fare_zmw = 21.00,
    rate_per_km_zmw = p_rate_per_km_zmw,
    rate_per_minute_zmw = p_rate_per_minute_zmw,
    minimum_fare_zmw = p_minimum_fare_zmw,
    free_waiting_minutes = p_free_waiting_minutes,
    waiting_rate_zmw = p_waiting_rate_zmw,
    rider_share_percent = p_rider_share_percent,
    platform_share_percent = p_platform_share_percent,
    pricing_config_version = pricing_config_version + 1
  where singleton returning pricing_config_version into new_version;
  insert into private.pilot_pricing_versions (
    version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
    rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw,
    free_waiting_minutes, waiting_rate_zmw, rider_share_percent, platform_share_percent,
    rain_pricing_enabled, night_surge_zmw, busy_surge_zmw, rain_surge_zmw, maximum_surge_zmw, changed_by
  ) select pricing_config_version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
    rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw,
    free_waiting_minutes, waiting_rate_zmw, rider_share_percent, platform_share_percent,
    rain_pricing_enabled, night_surge_zmw, busy_surge_zmw, rain_surge_zmw, maximum_surge_zmw, actor
  from private.pilot_service_settings where singleton;
  insert into private.pilot_pricing_audit(actor_id, action, pricing_version, before_config, after_config)
  select actor, 'CONFIGURATION_UPDATED', new_version, before_cfg, to_jsonb(s)
  from private.pilot_service_settings s where singleton;
  update private.pilot_pricing_audit_guard set config_update_in_progress = false where singleton;
  return new_version;
end;
$$;
revoke all on function public.admin_update_official_pricing(numeric,numeric,numeric,integer,numeric,numeric,numeric)
  from public, anon, authenticated;
grant execute on function public.admin_update_official_pricing(numeric,numeric,numeric,integer,numeric,numeric,numeric)
  to authenticated;

create or replace function public.admin_get_pilot_pricing()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare cfg private.pilot_service_settings%rowtype;
begin
  if (select auth.uid()) is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  select * into strict cfg from private.pilot_service_settings where singleton;
  return jsonb_build_object(
    'pricing_mode', cfg.pricing_mode, 'pricing_mode_approved', cfg.pricing_mode_approved,
    'base_fare_zmw', cfg.base_fare_zmw, 'rate_per_km_zmw', cfg.rate_per_km_zmw,
    'rate_per_minute_zmw', cfg.rate_per_minute_zmw, 'minimum_fare_zmw', cfg.minimum_fare_zmw,
    'free_waiting_minutes', cfg.free_waiting_minutes, 'waiting_rate_zmw', cfg.waiting_rate_zmw,
    'rider_share_percent', cfg.rider_share_percent, 'platform_share_percent', cfg.platform_share_percent,
    'rain_pricing_enabled', cfg.rain_pricing_enabled, 'night_surge_zmw', cfg.night_surge_zmw,
    'busy_surge_zmw', cfg.busy_surge_zmw, 'rain_surge_zmw', cfg.rain_surge_zmw,
    'maximum_surge_zmw', cfg.maximum_surge_zmw,
    'pricing_config_version', cfg.pricing_config_version
  );
end;
$$;

create or replace view public.admin_trip_financials with (security_barrier = true) as
with rider_payments as (
  select rider_id, coalesce(sum(amount_zmw), 0) as paid_zmw
  from public.rider_service_fee_ledger where entry_type = 'verified_payment' group by rider_id
), completed as (
  select t.*,
    sum(t.service_fee) over (partition by t.rider_id order by t.completed_at, t.id) as cumulative_commission_zmw
  from public.trips t where t.status = 'completed'
)
select t.id as trip_id, t.created_at, t.completed_at, t.status, t.passenger_id, t.rider_id,
  t.fare_amount as passenger_revenue_zmw, t.driver_fare as rider_earnings_zmw,
  t.service_fee as platform_commission_zmw,
  case when t.cumulative_commission_zmw <= coalesce(p.paid_zmw, 0) then 'settled' else 'outstanding' end
    as commission_settlement_status,
  t.accepted_fare_snapshot, t.final_fare_breakdown
from completed t left join rider_payments p on p.rider_id = t.rider_id
where (select private.is_admin());
revoke all on public.admin_trip_financials from public, anon;
grant select on public.admin_trip_financials to authenticated;

create or replace view public.rider_available_trips with (security_barrier = true) as
select t.id, t.status, t.pickup_lat, t.pickup_lng, t.dropoff_lat, t.dropoff_lng, t.created_at,
  t.pickup_address, t.dropoff_address, t.fare_amount, t.driver_fare, t.service_fee,
  t.route_distance_meters, t.route_duration_seconds, t.accepted_fare_snapshot
from public.trips t
where t.status = 'requesting' and t.rider_id is null
  and (select private.is_approved_rider()) and (select private.is_online_approved_rider());
revoke all on public.rider_available_trips from public, anon;
grant select on public.rider_available_trips to authenticated;

create or replace view public.authorized_trip_details with (security_barrier = true) as
select
  t.id, t.passenger_id, t.rider_id, t.status,
  t.pickup_lat, t.pickup_lng, t.pickup_address,
  t.dropoff_lat, t.dropoff_lng, t.dropoff_address,
  t.fare_amount, t.driver_fare, t.service_fee,
  t.route_distance_meters, t.route_duration_seconds,
  t.payment_method, t.payment_status,
  t.started_at, t.completed_at, t.created_at,
  case when (select private.is_admin()) or (t.rider_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled')) then passenger.full_name end as passenger_name,
  case when (select private.is_admin()) or (t.rider_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled')) then passenger.phone_number end as passenger_phone,
  case when (select private.is_admin()) or (t.passenger_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled')) then rider.full_name end as rider_name,
  case when (select private.is_admin()) or (t.passenger_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled')) then rider.phone_number end as rider_phone,
  t.accepted_fare_snapshot, t.final_fare_breakdown, t.waiting_duration_seconds,
  t.commission_settlement_status
from public.trips t
left join public.profiles passenger on passenger.id = t.passenger_id
left join public.profiles rider on rider.id = t.rider_id
where (t.passenger_id = (select auth.uid()) and (select private.is_passenger()))
   or (t.rider_id = (select auth.uid()) and (select private.is_approved_rider())
       and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled'))
   or (select private.is_admin());
revoke all on public.authorized_trip_details from public, anon;
grant select on public.authorized_trip_details to authenticated;

create or replace function public.admin_revenue_summary()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare total_commission numeric; verified_paid numeric;
begin
  if (select auth.uid()) is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  select coalesce(sum(service_fee), 0) into total_commission
  from public.trips where status = 'completed';
  select coalesce(sum(amount_zmw), 0) into verified_paid
  from public.rider_service_fee_ledger where entry_type = 'verified_payment';
  return (
    select jsonb_build_object(
      'total_passenger_revenue_zmw', coalesce(sum(fare_amount), 0),
      'total_rider_earnings_zmw', coalesce(sum(driver_fare), 0),
      'total_platform_commission_zmw', coalesce(sum(service_fee), 0),
      'outstanding_commission_zmw', greatest(total_commission - verified_paid, 0),
      'settled_commission_zmw', least(total_commission, verified_paid),
      'daily_revenue_zmw', coalesce(sum(fare_amount) filter (where completed_at >= date_trunc('day', now() at time zone 'Africa/Lusaka') at time zone 'Africa/Lusaka'), 0),
      'weekly_revenue_zmw', coalesce(sum(fare_amount) filter (where completed_at >= date_trunc('week', now() at time zone 'Africa/Lusaka') at time zone 'Africa/Lusaka'), 0),
      'monthly_revenue_zmw', coalesce(sum(fare_amount) filter (where completed_at >= date_trunc('month', now() at time zone 'Africa/Lusaka') at time zone 'Africa/Lusaka'), 0)
    ) from public.trips where status = 'completed'
  );
end;
$$;
revoke all on function public.admin_revenue_summary() from public, anon, authenticated;
grant execute on function public.admin_revenue_summary() to authenticated;

commit;
