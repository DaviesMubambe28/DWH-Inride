-- Recovered from the development project migration history after the hosted version was found missing from Git.
-- The statements below preserve the recorded applied migration in their original order.

begin;

alter table private.pilot_service_settings
  add column if not exists pricing_config_version bigint not null default 1;

alter table public.trips
  add column if not exists fare_quote_id uuid,
  add column if not exists accepted_fare_snapshot jsonb;

create table if not exists private.pilot_pricing_versions (
  version bigint primary key,
  pricing_mode text not null check (pricing_mode in ('FLAT_PILOT', 'DISTANCE_TIME')),
  pricing_mode_approved boolean not null,
  base_fare_zmw numeric(12, 2) not null,
  rate_per_km_zmw numeric(12, 2) not null,
  rate_per_minute_zmw numeric(12, 2) not null,
  minimum_fare_zmw numeric(12, 2) not null,
  approved_adjustments_zmw numeric(12, 2) not null,
  changed_by uuid references public.profiles(id),
  changed_at timestamptz not null default clock_timestamp()
);

create table if not exists private.pilot_pricing_audit (
  id bigint generated always as identity primary key,
  actor_id uuid not null references public.profiles(id),
  action text not null check (action in ('CONFIGURATION_UPDATED')),
  pricing_version bigint not null,
  before_config jsonb not null,
  after_config jsonb not null,
  created_at timestamptz not null default clock_timestamp()
);

create table if not exists private.pilot_pricing_audit_guard (
  singleton boolean primary key default true check (singleton),
  config_update_in_progress boolean not null default false
);

insert into private.pilot_pricing_audit_guard(singleton, config_update_in_progress)
values (true, false)
on conflict (singleton) do nothing;

revoke all on private.pilot_pricing_audit_guard from public, anon, authenticated;

create table if not exists private.fare_quotes (
  id uuid primary key default extensions.gen_random_uuid(),
  passenger_id uuid not null references public.profiles(id) on delete cascade,
  pickup_lat numeric(9, 6) not null check (pickup_lat between -90 and 90),
  pickup_lng numeric(9, 6) not null check (pickup_lng between -180 and 180),
  pickup_address text not null check (length(btrim(pickup_address)) between 1 and 300),
  dropoff_lat numeric(9, 6) not null check (dropoff_lat between -90 and 90),
  dropoff_lng numeric(9, 6) not null check (dropoff_lng between -180 and 180),
  dropoff_address text not null check (length(btrim(dropoff_address)) between 1 and 300),
  route_distance_meters integer not null check (route_distance_meters > 0),
  route_duration_seconds integer not null check (route_duration_seconds > 0),
  fare_amount numeric(12, 2) not null check (fare_amount > 0),
  service_fee numeric(12, 2) not null check (service_fee >= 0),
  driver_fare numeric(12, 2) not null check (driver_fare >= 0 and service_fee + driver_fare = fare_amount),
  pricing_mode text not null check (pricing_mode in ('FLAT_PILOT', 'DISTANCE_TIME')),
  pricing_version bigint not null references private.pilot_pricing_versions(version),
  expires_at timestamptz not null,
  accepted_trip_id uuid unique references public.trips(id),
  accepted_idempotency_key uuid,
  created_at timestamptz not null default clock_timestamp(),
  check (expires_at > created_at)
);

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'trips_fare_quote_id_fkey') THEN
    ALTER TABLE public.trips
      ADD CONSTRAINT trips_fare_quote_id_fkey FOREIGN KEY (fare_quote_id) REFERENCES private.fare_quotes(id);
  END IF;
END $$;

create index if not exists fare_quotes_passenger_expiry_idx on private.fare_quotes(passenger_id, expires_at desc);

create index if not exists fare_quotes_expiry_idx on private.fare_quotes(expires_at);

revoke all on private.pilot_pricing_versions, private.pilot_pricing_audit, private.pilot_pricing_audit_guard, private.fare_quotes
  from public, anon, authenticated, service_role;

insert into private.pilot_pricing_versions (
  version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
  rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw
)
select pricing_config_version, pricing_mode, pricing_mode_approved, base_fare_zmw,
  rate_per_km_zmw, rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw
from private.pilot_service_settings where singleton
on conflict (version) do nothing;

create or replace function private.calculate_trip_financials(p_distance_meters integer, p_duration_seconds integer)
returns table (fare_amount numeric, service_fee numeric, driver_fare numeric)
language plpgsql stable security definer set search_path = '' as $$
declare cfg private.pilot_service_settings%rowtype;
begin
  select * into strict cfg from private.pilot_service_settings where singleton;
  if p_distance_meters is null or p_distance_meters <= 0
    or p_duration_seconds is null or p_duration_seconds <= 0 then
    raise exception 'VALIDATED_ROUTE_REQUIRED' using errcode = '22023';
  end if;

  fare_amount := round(case when cfg.pricing_mode = 'DISTANCE_TIME' and cfg.pricing_mode_approved then greatest(
    cfg.minimum_fare_zmw,
    cfg.base_fare_zmw
      + (p_distance_meters::numeric / 1000) * cfg.rate_per_km_zmw
      + (p_duration_seconds::numeric / 60) * cfg.rate_per_minute_zmw
      + cfg.approved_adjustments_zmw
  ) else 20.00::numeric end, 2);
  service_fee := round(fare_amount * 0.15, 2);
  driver_fare := fare_amount - service_fee;
  return next;
end;
$$;

revoke all on function private.calculate_trip_financials(integer, integer) from public, anon, authenticated;

grant execute on function private.calculate_trip_financials(integer, integer) to service_role;

create or replace function public.pilot_service_status()
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'configured', exists (select 1 from private.pilot_service_settings where singleton),
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
    'message', case
      when not exists (select 1 from private.pilot_service_settings where singleton)
        then 'Service is not configured. Please contact pilot support.'
      when private.pilot_service_open()
        then 'Service available 24/7. Rider availability may vary; check rider status before requesting.'
      else 'New bookings are intentionally paused. Please try again later.'
    end,
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

revoke all on function public.pilot_service_status() from public;

grant execute on function public.pilot_service_status() to anon, authenticated, service_role;

create or replace function public.admin_get_pilot_pricing()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare cfg private.pilot_service_settings%rowtype;
begin
  if (select auth.uid()) is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  select * into strict cfg from private.pilot_service_settings where singleton;
  return jsonb_build_object(
    'pricing_mode', cfg.pricing_mode,
    'pricing_mode_approved', cfg.pricing_mode_approved,
    'base_fare_zmw', cfg.base_fare_zmw,
    'rate_per_km_zmw', cfg.rate_per_km_zmw,
    'rate_per_minute_zmw', cfg.rate_per_minute_zmw,
    'minimum_fare_zmw', cfg.minimum_fare_zmw,
    'approved_adjustments_zmw', cfg.approved_adjustments_zmw,
    'pricing_config_version', cfg.pricing_config_version
  );
end;
$$;

create or replace function private.prevent_pricing_audit_mutation()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  raise exception 'pricing audit history is append-only' using errcode = '42501';
end;
$$;

revoke all on function private.prevent_pricing_audit_mutation() from public, anon, authenticated;

drop trigger if exists prevent_pricing_audit_mutation on private.pilot_pricing_audit;

create trigger prevent_pricing_audit_mutation before update or delete on private.pilot_pricing_audit
for each row execute function private.prevent_pricing_audit_mutation();

create or replace function public.admin_update_pilot_pricing(
  p_pricing_mode text,
  p_base_fare_zmw numeric,
  p_rate_per_km_zmw numeric,
  p_rate_per_minute_zmw numeric,
  p_minimum_fare_zmw numeric,
  p_approved_adjustments_zmw numeric,
  p_approve_distance_time boolean default false
)
returns bigint language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  before_cfg jsonb;
  new_version bigint;
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  if p_pricing_mode is null or p_pricing_mode not in ('FLAT_PILOT', 'DISTANCE_TIME')
    or p_base_fare_zmw is null or p_base_fare_zmw < 0
    or p_rate_per_km_zmw is null or p_rate_per_km_zmw < 0
    or p_rate_per_minute_zmw is null or p_rate_per_minute_zmw < 0
    or p_minimum_fare_zmw is null or p_minimum_fare_zmw < 0
    or p_approved_adjustments_zmw is null or p_approved_adjustments_zmw < 0
    or p_base_fare_zmw > 10000 or p_rate_per_km_zmw > 10000
    or p_rate_per_minute_zmw > 10000 or p_minimum_fare_zmw > 10000
    or p_approved_adjustments_zmw > 10000
    or scale(p_base_fare_zmw) > 2 or scale(p_rate_per_km_zmw) > 2
    or scale(p_rate_per_minute_zmw) > 2 or scale(p_minimum_fare_zmw) > 2
    or scale(p_approved_adjustments_zmw) > 2 then
    raise exception 'invalid pricing configuration' using errcode = '22023';
  end if;
  if p_pricing_mode = 'DISTANCE_TIME' and not p_approve_distance_time then
    raise exception 'explicit DISTANCE_TIME approval is required' using errcode = '42501';
  end if;
  if p_pricing_mode = 'FLAT_PILOT'
    and (p_base_fare_zmw <> 20.00 or p_rate_per_km_zmw <> 0
      or p_rate_per_minute_zmw <> 0 or p_minimum_fare_zmw <> 20.00
      or p_approved_adjustments_zmw <> 0) then
    raise exception 'FLAT_PILOT must retain the approved ZMW 20 fare' using errcode = '22023';
  end if;
  if p_pricing_mode = 'DISTANCE_TIME'
    and (p_base_fare_zmw = 0 or p_minimum_fare_zmw = 0) then
    raise exception 'DISTANCE_TIME base and minimum fares must be greater than zero' using errcode = '22023';
  end if;

  select jsonb_build_object(
    'pricing_mode', pricing_mode, 'pricing_mode_approved', pricing_mode_approved,
    'base_fare_zmw', base_fare_zmw, 'rate_per_km_zmw', rate_per_km_zmw,
    'rate_per_minute_zmw', rate_per_minute_zmw, 'minimum_fare_zmw', minimum_fare_zmw,
    'approved_adjustments_zmw', approved_adjustments_zmw, 'pricing_config_version', pricing_config_version
  ) into before_cfg
  from private.pilot_service_settings where singleton for update;

  update private.pilot_pricing_audit_guard set config_update_in_progress = true where singleton;
  update private.pilot_service_settings
  set pricing_mode = p_pricing_mode,
      pricing_mode_approved = (p_pricing_mode = 'DISTANCE_TIME' and p_approve_distance_time),
      base_fare_zmw = p_base_fare_zmw,
      rate_per_km_zmw = p_rate_per_km_zmw,
      rate_per_minute_zmw = p_rate_per_minute_zmw,
      minimum_fare_zmw = p_minimum_fare_zmw,
      approved_adjustments_zmw = p_approved_adjustments_zmw,
      pricing_config_version = pricing_config_version + 1
  where singleton
  returning pricing_config_version into new_version;

  insert into private.pilot_pricing_versions (
    version, pricing_mode, pricing_mode_approved, base_fare_zmw, rate_per_km_zmw,
    rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw, changed_by
  )
  select pricing_config_version, pricing_mode, pricing_mode_approved, base_fare_zmw,
    rate_per_km_zmw, rate_per_minute_zmw, minimum_fare_zmw, approved_adjustments_zmw, actor
  from private.pilot_service_settings where singleton;

  insert into private.pilot_pricing_audit(actor_id, action, pricing_version, before_config, after_config)
  values (
    actor, 'CONFIGURATION_UPDATED', new_version, before_cfg,
    (select public.admin_get_pilot_pricing())
  );
  update private.pilot_pricing_audit_guard set config_update_in_progress = false where singleton;
  return new_version;
end;
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
  ) and not exists (
    select 1 from private.pilot_pricing_audit_guard
    where singleton and config_update_in_progress
  ) then
    raise exception 'pricing changes require the audited admin RPC' using errcode = '42501';
  end if;
  return new;
end;
$$;

revoke all on function private.prevent_pricing_settings_mutation() from public, anon, authenticated;

drop trigger if exists prevent_pricing_settings_mutation on private.pilot_service_settings;

create trigger prevent_pricing_settings_mutation before update on private.pilot_service_settings
for each row execute function private.prevent_pricing_settings_mutation();

create or replace function public.admin_simulate_pilot_fare(p_distance_meters integer, p_duration_seconds integer)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare totals record;
begin
  if (select auth.uid()) is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  select * into totals from private.calculate_trip_financials(p_distance_meters, p_duration_seconds);
  return jsonb_build_object(
    'fare_zmw', totals.fare_amount,
    'service_fee_zmw', totals.service_fee,
    'rider_earnings_zmw', totals.driver_fare
  );
end;
$$;

create or replace function public.admin_pilot_pricing_history()
returns setof jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if (select auth.uid()) is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  return query
    select jsonb_build_object(
      'version', version, 'pricing_mode', pricing_mode,
      'pricing_mode_approved', pricing_mode_approved,
      'base_fare_zmw', base_fare_zmw, 'rate_per_km_zmw', rate_per_km_zmw,
      'rate_per_minute_zmw', rate_per_minute_zmw,
      'minimum_fare_zmw', minimum_fare_zmw,
      'approved_adjustments_zmw', approved_adjustments_zmw,
      'changed_by', changed_by, 'changed_at', changed_at,
      'before_config', (
        select audit.before_config from private.pilot_pricing_audit audit
        where audit.pricing_version = private.pilot_pricing_versions.version
      ),
      'after_config', (
        select audit.after_config from private.pilot_pricing_audit audit
        where audit.pricing_version = private.pilot_pricing_versions.version
      )
    )
    from private.pilot_pricing_versions order by version desc limit 50;
end;
$$;

create or replace function public.create_fare_quote(
  p_passenger_id uuid,
  p_pickup_lat numeric, p_pickup_lng numeric, p_pickup_address text,
  p_dropoff_lat numeric, p_dropoff_lng numeric, p_dropoff_address text,
  p_route_distance_meters integer, p_route_duration_seconds integer
)
returns table (
  quote_id uuid, fare_amount numeric, service_fee numeric, driver_fare numeric,
  route_distance_meters integer, route_duration_seconds integer,
  pricing_mode text, pricing_version bigint, expires_at timestamptz
)
language plpgsql security definer set search_path = '' as $$
declare
  cfg private.pilot_service_settings%rowtype;
  amounts record;
  created_quote private.fare_quotes%rowtype;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'trusted quote service required' using errcode = '42501';
  end if;
  if not exists (select 1 from public.profiles where id = p_passenger_id and role = 'passenger') then
    raise exception 'passenger profile is required' using errcode = '42501';
  end if;
  if not private.pilot_service_open() then
    raise exception 'PILOT_UNAVAILABLE' using errcode = 'P0001';
  end if;
  if p_pickup_lat is null or p_pickup_lng is null or p_dropoff_lat is null or p_dropoff_lng is null
    or not private.pilot_point_allowed(p_pickup_lat::double precision, p_pickup_lng::double precision)
    or not private.pilot_point_allowed(p_dropoff_lat::double precision, p_dropoff_lng::double precision) then
    raise exception 'PILOT_OUTSIDE_SERVICE_AREA' using errcode = 'P0001';
  end if;
  if p_route_distance_meters is null or p_route_distance_meters <= 0 or p_route_distance_meters > 100000
    or p_route_duration_seconds is null or p_route_duration_seconds <= 0 or p_route_duration_seconds > 86400
    or length(btrim(p_pickup_address)) not between 1 and 300
    or length(btrim(p_dropoff_address)) not between 1 and 300 then
    raise exception 'VALIDATED_ROUTE_REQUIRED' using errcode = '22023';
  end if;

  select * into strict cfg from private.pilot_service_settings where singleton for share;
  select * into strict amounts from private.calculate_trip_financials(p_route_distance_meters, p_route_duration_seconds);
  insert into private.fare_quotes (
    passenger_id, pickup_lat, pickup_lng, pickup_address, dropoff_lat, dropoff_lng,
    dropoff_address, route_distance_meters, route_duration_seconds, fare_amount,
    service_fee, driver_fare, pricing_mode, pricing_version, expires_at
  ) values (
    p_passenger_id, p_pickup_lat, p_pickup_lng, btrim(p_pickup_address),
    p_dropoff_lat, p_dropoff_lng, btrim(p_dropoff_address),
    p_route_distance_meters, p_route_duration_seconds, amounts.fare_amount,
    amounts.service_fee, amounts.driver_fare,
    case when cfg.pricing_mode = 'DISTANCE_TIME' and cfg.pricing_mode_approved then cfg.pricing_mode else 'FLAT_PILOT' end,
    cfg.pricing_config_version, clock_timestamp() + interval '2 minutes'
  ) returning * into created_quote;

  return query select created_quote.id, created_quote.fare_amount, created_quote.service_fee,
    created_quote.driver_fare, created_quote.route_distance_meters,
    created_quote.route_duration_seconds, created_quote.pricing_mode,
    created_quote.pricing_version, created_quote.expires_at;
end;
$$;

create or replace function public.accept_fare_quote(p_quote_id uuid, p_idempotency_key uuid)
returns table (
  trip_id uuid, pickup_pin text, fare_amount numeric, service_fee numeric,
  driver_fare numeric, route_distance_meters integer, route_duration_seconds integer
)
language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  quote private.fare_quotes%rowtype;
  created record;
  snapshot jsonb;
  previous_transition_authorized text := coalesce(current_setting('app.trip_transition_authorized', true), '');
begin
  if actor is null or p_idempotency_key is null then
    raise exception 'authentication and idempotency key are required' using errcode = '42501';
  end if;
  select * into quote from private.fare_quotes where id = p_quote_id for update;
  if not found or quote.passenger_id <> actor then
    raise exception 'fare quote not found' using errcode = '42501';
  end if;
  if quote.accepted_trip_id is not null then
    if quote.accepted_idempotency_key = p_idempotency_key then
      return query
      select t.id, pin.pin, t.fare_amount, t.service_fee, t.driver_fare,
        t.route_distance_meters, t.route_duration_seconds
      from public.trips t
      join private.trip_pickup_pins pin on pin.trip_id = t.id
      where t.id = quote.accepted_trip_id and t.passenger_id = actor;
      return;
    end if;
    raise exception 'fare quote has already been accepted' using errcode = '23505';
  end if;
  if quote.expires_at <= clock_timestamp() then
    raise exception 'FARE_QUOTE_EXPIRED' using errcode = 'P0001';
  end if;
  if not private.pilot_service_open() then
    raise exception 'PILOT_UNAVAILABLE' using errcode = 'P0001';
  end if;

  perform 1 from public.profiles where id = actor and role = 'passenger' for update;
  if not found then raise exception 'passenger profile is required' using errcode = '42501'; end if;
  select * into created from public.create_routed_trip(
    actor, quote.pickup_lat, quote.pickup_lng, quote.pickup_address,
    quote.dropoff_lat, quote.dropoff_lng, quote.dropoff_address,
    quote.route_distance_meters, quote.route_duration_seconds, 'cash'
  );

  snapshot := jsonb_build_object(
    'quote_id', quote.id, 'pricing_mode', quote.pricing_mode,
    'pricing_version', quote.pricing_version,
    'fare_amount_zmw', quote.fare_amount,
    'service_fee_zmw', quote.service_fee,
    'rider_earnings_zmw', quote.driver_fare,
    'route_distance_meters', quote.route_distance_meters,
    'route_duration_seconds', quote.route_duration_seconds,
    'accepted_at', clock_timestamp()
  );
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips set
    fare_amount = quote.fare_amount,
    service_fee = quote.service_fee,
    driver_fare = quote.driver_fare,
    fare_quote_id = quote.id,
    accepted_fare_snapshot = snapshot
  where id = created.trip_id and passenger_id = actor;
  perform set_config('app.trip_transition_authorized', previous_transition_authorized, true);
  update private.fare_quotes
    set accepted_trip_id = created.trip_id, accepted_idempotency_key = p_idempotency_key
    where id = quote.id;

  return query select created.trip_id, created.pickup_pin, quote.fare_amount,
    quote.service_fee, quote.driver_fare, quote.route_distance_meters,
    quote.route_duration_seconds;
end;
$$;

create or replace function private.prevent_accepted_fare_mutation()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if old.accepted_fare_snapshot is not null and (
    new.accepted_fare_snapshot is distinct from old.accepted_fare_snapshot
    or new.fare_quote_id is distinct from old.fare_quote_id
    or new.fare_amount is distinct from old.fare_amount
    or new.service_fee is distinct from old.service_fee
    or new.driver_fare is distinct from old.driver_fare
    or new.route_distance_meters is distinct from old.route_distance_meters
    or new.route_duration_seconds is distinct from old.route_duration_seconds
  ) then
    raise exception 'accepted fare snapshot is immutable' using errcode = '42501';
  end if;
  return new;
end;
$$;

revoke all on function private.prevent_accepted_fare_mutation() from public, anon, authenticated;

drop trigger if exists prevent_accepted_fare_mutation on public.trips;

create trigger prevent_accepted_fare_mutation before update on public.trips
for each row execute function private.prevent_accepted_fare_mutation();

revoke all on function public.create_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)
  from public, anon, authenticated;

grant execute on function public.create_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)
  to service_role;

revoke all on function public.accept_fare_quote(uuid,uuid) from public, anon;

grant execute on function public.accept_fare_quote(uuid,uuid) to authenticated, service_role;

revoke all on function public.admin_get_pilot_pricing(), public.admin_update_pilot_pricing(text,numeric,numeric,numeric,numeric,numeric,boolean),
  public.admin_simulate_pilot_fare(integer,integer), public.admin_pilot_pricing_history()
  from public, anon, authenticated;

grant execute on function public.admin_get_pilot_pricing(), public.admin_update_pilot_pricing(text,numeric,numeric,numeric,numeric,numeric,boolean),
  public.admin_simulate_pilot_fare(integer,integer), public.admin_pilot_pricing_history()
  to authenticated;

commit;
