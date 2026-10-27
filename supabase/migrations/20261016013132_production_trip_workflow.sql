begin;

-- Driver approval is deliberately separate from per-document verification.
alter table public.profiles
  add column if not exists approval_status public.verification_status;

update public.profiles
set approval_status = 'pending'
where role = 'rider'
  and approval_status is null;

alter table public.profiles
  drop constraint if exists profiles_driver_approval_required;
alter table public.profiles
  add constraint profiles_driver_approval_required check (
    (role = 'rider' and approval_status is not null)
    or (role <> 'rider' and approval_status is null)
  );

comment on column public.profiles.approval_status is
  'Admin-controlled driver approval. Pending/rejected drivers cannot go online or accept trips.';

alter table public.trips
  add column if not exists route_distance_meters integer,
  add column if not exists route_duration_seconds integer,
  add column if not exists service_fee numeric(12, 2),
  add column if not exists driver_fare numeric(12, 2),
  add column if not exists pin_failed_attempts smallint not null default 0,
  add column if not exists pin_locked_until timestamptz;

alter table public.trips
  drop constraint if exists trips_route_metrics_positive;
alter table public.trips
  add constraint trips_route_metrics_positive check (
    (route_distance_meters is null and route_duration_seconds is null)
    or (route_distance_meters > 0 and route_duration_seconds > 0)
  );

alter table public.trips
  drop constraint if exists trips_financials_nonnegative;
alter table public.trips
  add constraint trips_financials_nonnegative check (
    (service_fee is null or service_fee >= 0)
    and (driver_fare is null or driver_fare >= 0)
    and (fare_amount is null or service_fee + driver_fare = fare_amount)
  );

alter table public.trips
  drop constraint if exists trips_pin_attempts_range;
alter table public.trips
  add constraint trips_pin_attempts_range check (pin_failed_attempts between 0 and 5);

create table if not exists private.trip_pickup_pins (
  trip_id uuid primary key references public.trips (id) on delete cascade,
  passenger_id uuid not null references public.profiles (id) on delete cascade,
  pin text not null,
  created_at timestamptz not null default now(),
  constraint trip_pickup_pins_format check (pin ~ '^[0-9]{4}$')
);

revoke all on table private.trip_pickup_pins from public, anon, authenticated;
grant select, insert, delete on table private.trip_pickup_pins to service_role;

-- 1. Drop ONLY the overloaded variant causing the ambiguity.
-- DO NOT drop the 0-argument version, because the policy depends on it.
drop function if exists private.is_approved_rider(uuid);

-- 2. Recreate the 0-argument function.
create or replace function private.is_approved_rider()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    exists (
      select 1
      from public.profiles as p
      where p.id = (select auth.uid())
        and p.role = 'rider'
        and p.approval_status = 'approved'
    ),
    false
  );
$$;

revoke all on function private.is_approved_rider() from public, anon;
grant execute on function private.is_approved_rider() to authenticated, service_role;

create or replace function private.calculate_trip_financials(
  p_distance_meters integer,
  p_duration_seconds integer
)
returns table (
  fare_amount numeric,
  service_fee numeric,
  driver_fare numeric
)
language sql
immutable
security invoker
set search_path = ''
as $$
  with calculated as (
    select round(
      greatest(
        20::numeric,
        15::numeric
          + greatest((p_distance_meters::numeric / 1000) - 2, 0) * 5
          + greatest((p_duration_seconds::numeric / 60) - 5, 0)
      ),
      2
    ) as total
  )
  select
    total,
    round(total * 0.15, 2),
    total - round(total * 0.15, 2)
  from calculated;
$$;

revoke all on function private.calculate_trip_financials(integer, integer) from public, anon, authenticated;
grant execute on function private.calculate_trip_financials(integer, integer) to service_role;

create or replace function private.set_trip_financials()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  amounts record;
begin
  if new.route_distance_meters is null or new.route_duration_seconds is null then
    raise exception 'trusted route distance and duration are required'
      using errcode = '22023';
  end if;

  select * into amounts
  from private.calculate_trip_financials(
    new.route_distance_meters,
    new.route_duration_seconds
  );

  new.fare_amount := amounts.fare_amount;
  new.service_fee := amounts.service_fee;
  new.driver_fare := amounts.driver_fare;
  return new;
end;
$$;

revoke all on function private.set_trip_financials() from public, anon, authenticated;

drop trigger if exists set_trip_financials on public.trips;
create trigger set_trip_financials
before insert or update of route_distance_meters, route_duration_seconds
on public.trips
for each row execute function private.set_trip_financials();

-- The Edge Function is the only caller. Route metrics are obtained from Mapbox
-- before this short transaction starts, then the database authoritatively prices it.
create or replace function public.create_routed_trip(
  p_passenger_id uuid,
  p_pickup_lat numeric,
  p_pickup_lng numeric,
  p_pickup_address text,
  p_dropoff_lat numeric,
  p_dropoff_lng numeric,
  p_dropoff_address text,
  p_route_distance_meters integer,
  p_route_duration_seconds integer,
  p_payment_method text default 'cash'
)
returns table (
  trip_id uuid,
  pickup_pin text,
  fare_amount numeric,
  service_fee numeric,
  driver_fare numeric,
  route_distance_meters integer,
  route_duration_seconds integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  generated_pin text;
  inserted_trip public.trips%rowtype;
  random_bytes bytea;
  random_number integer;
begin
  if not exists (
    select 1 from public.profiles as p
    where p.id = p_passenger_id and p.role = 'passenger'
  ) then
    raise exception 'passenger profile is required' using errcode = '42501';
  end if;

  if exists (
    select 1 from public.trips as t
    where t.passenger_id = p_passenger_id
      and t.status not in ('completed', 'cancelled')
  ) then
    raise exception 'passenger already has an active trip' using errcode = '23505';
  end if;

  random_bytes := extensions.gen_random_bytes(2);
  random_number := get_byte(random_bytes, 0) * 256 + get_byte(random_bytes, 1);
  generated_pin := lpad((random_number % 10000)::text, 4, '0');

  insert into public.trips (
    passenger_id,
    pickup_lat,
    pickup_lng,
    pickup_address,
    dropoff_lat,
    dropoff_lng,
    dropoff_address,
    route_distance_meters,
    route_duration_seconds,
    payment_method,
    trip_pin_hash
  ) values (
    p_passenger_id,
    p_pickup_lat,
    p_pickup_lng,
    btrim(p_pickup_address),
    p_dropoff_lat,
    p_dropoff_lng,
    btrim(p_dropoff_address),
    p_route_distance_meters,
    p_route_duration_seconds,
    p_payment_method,
    private.hash_trip_pin(generated_pin)
  ) returning * into inserted_trip;

  insert into private.trip_pickup_pins (trip_id, passenger_id, pin)
  values (inserted_trip.id, p_passenger_id, generated_pin);

  return query select
    inserted_trip.id,
    generated_pin,
    inserted_trip.fare_amount,
    inserted_trip.service_fee,
    inserted_trip.driver_fare,
    inserted_trip.route_distance_meters,
    inserted_trip.route_duration_seconds;
end;
$$;

revoke all on function public.create_routed_trip(uuid, numeric, numeric, text, numeric, numeric, text, integer, integer, text)
  from public, anon, authenticated;
grant execute on function public.create_routed_trip(uuid, numeric, numeric, text, numeric, numeric, text, integer, integer, text)
  to service_role;

create or replace function public.get_trip_pickup_pin(p_trip_id uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select p.pin
  from private.trip_pickup_pins as p
  join public.trips as t on t.id = p.trip_id
  where p.trip_id = p_trip_id
    and t.passenger_id = (select auth.uid())
    and t.status not in ('completed', 'cancelled');
$$;

revoke all on function public.get_trip_pickup_pin(uuid) from public, anon;
grant execute on function public.get_trip_pickup_pin(uuid) to authenticated;

create or replace function public.apply_as_driver()
returns public.verification_status
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  if actor_id is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;

  update public.profiles
  set role = 'rider', approval_status = 'pending', is_online = false
  where id = actor_id and role = 'passenger';

  if not found and not exists (
    select 1 from public.profiles where id = actor_id and role = 'rider'
  ) then
    raise exception 'driver application is not allowed for this account' using errcode = '42501';
  end if;

  return (
    select p.approval_status from public.profiles as p where p.id = actor_id
  );
end;
$$;

revoke all on function public.apply_as_driver() from public, anon;
grant execute on function public.apply_as_driver() to authenticated;

create or replace function public.set_driver_online(p_online boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  if actor_id is null or not private.is_approved_rider() then
    raise exception 'only an approved driver can change online status' using errcode = '42501';
  end if;

  update public.profiles set is_online = p_online where id = actor_id;
  return p_online;
end;
$$;

revoke all on function public.set_driver_online(boolean) from public, anon;
grant execute on function public.set_driver_online(boolean) to authenticated;

create or replace function public.admin_set_driver_approval(
  p_rider_id uuid,
  p_status public.verification_status
)
returns public.verification_status
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;

  update public.profiles
  set approval_status = p_status,
      is_online = case when p_status = 'approved' then is_online else false end
  where id = p_rider_id and role = 'rider';

  if not found then
    raise exception 'driver profile not found' using errcode = 'P0002';
  end if;

  return p_status;
end;
$$;

revoke all on function public.admin_set_driver_approval(uuid, public.verification_status) from public, anon;
grant execute on function public.admin_set_driver_approval(uuid, public.verification_status) to authenticated;

create or replace function public.cancel_trip(p_trip_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips
  set status = 'cancelled'
  where id = p_trip_id
    and passenger_id = (select auth.uid())
    and status in ('requesting', 'accepted', 'arrived');
  return found;
end;
$$;

create or replace function public.accept_trip(p_trip_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  if actor_id is null or not private.is_approved_rider() then
    raise exception 'only an approved driver can accept trips' using errcode = '42501';
  end if;
  if not exists (select 1 from public.profiles where id = actor_id and is_online) then
    raise exception 'driver must be online to accept trips' using errcode = '42501';
  end if;

  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips
  set rider_id = actor_id, status = 'accepted'
  where id = p_trip_id and status = 'requesting' and rider_id is null;
  return found;
end;
$$;

create or replace function public.mark_trip_arrived(p_trip_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  if actor_id is null or not private.is_rider() then
    raise exception 'only the assigned driver can update trips' using errcode = '42501';
  end if;
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips
  set status = 'arrived'
  where id = p_trip_id and rider_id = actor_id and status = 'accepted';
  return found;
end;
$$;

create or replace function public.verify_trip_pin_and_start(p_trip_id uuid, p_pin text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
  trip_row public.trips%rowtype;
begin
  if actor_id is null or not private.is_rider() then
    raise exception 'only the assigned driver can start trips' using errcode = '42501';
  end if;
  if p_pin !~ '^[0-9]{4}$' then
    return false;
  end if;

  select * into trip_row
  from public.trips
  where id = p_trip_id and rider_id = actor_id
  for update;

  if not found or trip_row.status <> 'arrived' then
    return false;
  end if;
  if trip_row.pin_locked_until is not null and trip_row.pin_locked_until > now() then
    raise exception 'PIN verification is temporarily locked' using errcode = '42501';
  end if;

  if not private.verify_trip_pin(p_trip_id, p_pin) then
    update public.trips
    set pin_failed_attempts = least(pin_failed_attempts + 1, 5),
        pin_locked_until = case when pin_failed_attempts + 1 >= 5 then now() + interval '15 minutes' else null end
    where id = p_trip_id;
    return false;
  end if;

  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips
  set status = 'in_progress', started_at = now(), pin_failed_attempts = 0, pin_locked_until = null
  where id = p_trip_id;
  delete from private.trip_pickup_pins where trip_id = p_trip_id;
  return true;
end;
$$;

create or replace function public.complete_trip(p_trip_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  if actor_id is null or not private.is_rider() then
    raise exception 'only the assigned driver can complete trips' using errcode = '42501';
  end if;
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips
  set status = 'completed', completed_at = now()
  where id = p_trip_id and rider_id = actor_id and status = 'in_progress';
  if found then
    update public.profiles set total_trips = total_trips + 1 where id in (
      actor_id,
      (select passenger_id from public.trips where id = p_trip_id)
    );
    return true;
  end if;
  return false;
end;
$$;

revoke all on function public.cancel_trip(uuid) from public, anon;
revoke all on function public.accept_trip(uuid) from public, anon;
revoke all on function public.mark_trip_arrived(uuid) from public, anon;
revoke all on function public.verify_trip_pin_and_start(uuid, text) from public, anon;
revoke all on function public.complete_trip(uuid) from public, anon;
grant execute on function public.cancel_trip(uuid) to authenticated;
grant execute on function public.accept_trip(uuid) to authenticated;
grant execute on function public.mark_trip_arrived(uuid) to authenticated;
grant execute on function public.verify_trip_pin_and_start(uuid, text) to authenticated;
grant execute on function public.complete_trip(uuid) to authenticated;

create or replace function private.enforce_client_trip_status_transition()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (new.status, new.rider_id, new.started_at, new.completed_at)
     is distinct from
     (old.status, old.rider_id, old.started_at, old.completed_at)
     and coalesce(current_setting('app.trip_transition_authorized', true), '') <> 'true'
     and (select auth.uid()) is not null then
    raise exception 'trip transitions must use the protected server operation'
      using errcode = '42501';
  end if;
  return new;
end;
$$;

revoke insert on table public.trips from authenticated;
revoke update (status) on table public.trips from authenticated;
revoke update (is_online) on table public.profiles from authenticated;
grant update (full_name, phone_number) on table public.profiles to authenticated;

drop policy if exists trips_insert_passenger_request on public.trips;
drop policy if exists trips_update_passenger on public.trips;
drop policy if exists trips_update_assigned_rider on public.trips;

drop policy if exists trips_select_available_for_approved_rider on public.trips;
create policy trips_select_available_for_approved_rider
on public.trips
for select
to authenticated
using (
  status = 'requesting'
  and (select private.is_approved_rider())
  and exists (
    select 1 from public.profiles as p
    where p.id = (select auth.uid()) and p.is_online
  )
);

drop policy if exists trip_locations_insert_assigned_rider on public.trip_locations;
create policy trip_locations_insert_assigned_rider
on public.trip_locations
for insert
to authenticated
with check (
  rider_id = (select auth.uid())
  and (select private.is_rider())
  and exists (
    select 1
    from public.trips as t
    where t.id = trip_locations.trip_id
      and t.rider_id = (select auth.uid())
      and t.status in ('accepted', 'arrived', 'in_progress')
  )
);

create index if not exists profiles_approved_online_riders_idx
  on public.profiles (id)
  where role = 'rider' and approval_status = 'approved' and is_online;

create unique index if not exists trips_one_active_trip_per_passenger_idx
  on public.trips (passenger_id)
  where status not in ('completed', 'cancelled');

create index if not exists trips_available_requests_idx
  on public.trips (created_at)
  where status = 'requesting' and rider_id is null;

commit;
