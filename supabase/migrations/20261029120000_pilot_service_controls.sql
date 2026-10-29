begin;

-- Remains paused until the owner supplies and approves the real operating data.
create table private.pilot_service_settings (
  singleton boolean primary key default true check (singleton),
  enabled boolean not null default false,
  opens time not null default '06:00',
  closes time not null default '20:00',
  weekdays integer[] not null default array[1,2,3,4,5,6],
  center_latitude double precision check (center_latitude between -90 and 90),
  center_longitude double precision check (center_longitude between -180 and 180),
  radius_km double precision check (radius_km > 0 and radius_km < 'Infinity'::double precision),
  support_phone text,
  support_email text,
  fare_policy_approved boolean not null default false,
  cancellation_policy_approved boolean not null default false,
  check (opens < closes),
  check (cardinality(weekdays) > 0 and weekdays <@ array[1,2,3,4,5,6,7]),
  check (not enabled or (center_latitude is not null and center_longitude is not null
    and radius_km is not null and support_phone is not null and support_email is not null and support_phone ~ '^\+260[0-9]{9}$'
    and support_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
    and fare_policy_approved and cancellation_policy_approved))
);
insert into private.pilot_service_settings(singleton) values (true);
revoke all on private.pilot_service_settings from public, anon, authenticated;

create function private.pilot_service_open(p_at timestamptz default now())
returns boolean language sql stable security definer set search_path = '' as $$
  select coalesce((select enabled
    and extract(isodow from p_at at time zone 'Africa/Lusaka')::integer = any(weekdays)
    and (p_at at time zone 'Africa/Lusaka')::time >= opens
    and (p_at at time zone 'Africa/Lusaka')::time < closes
  from private.pilot_service_settings where singleton), false)
$$;
create function private.pilot_point_allowed(p_lat double precision, p_lng double precision)
returns boolean language sql stable security definer set search_path = '' as $$
  select coalesce((select p_lat between -90 and 90 and p_lng between -180 and 180
    and 6371.0088 * 2 * asin(sqrt(least(1.0, greatest(0.0,
      power(sin(radians(p_lat - center_latitude) / 2), 2)
      + cos(radians(center_latitude)) * cos(radians(p_lat))
      * power(sin(radians(p_lng - center_longitude) / 2), 2))))) <= radius_km
  from private.pilot_service_settings where singleton), false)
$$;
revoke all on function private.pilot_service_open(timestamptz), private.pilot_point_allowed(double precision, double precision) from public, anon;
grant execute on function private.pilot_service_open(timestamptz), private.pilot_point_allowed(double precision, double precision) to authenticated, service_role;

create function public.pilot_service_status()
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('available', private.pilot_service_open(),
    'message', case when private.pilot_service_open() then 'Pilot service is available.'
      else 'Service currently unavailable. Pilot hours are 06:00–20:00 CAT, Monday–Saturday.' end,
    'cash_only', true)
$$;
revoke all on function public.pilot_service_status() from public;
grant execute on function public.pilot_service_status() to anon, authenticated, service_role;

create function private.enforce_pilot_booking()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if not private.pilot_service_open() then
    raise exception 'PILOT_UNAVAILABLE' using errcode = 'P0001';
  end if;
  if new.payment_method <> 'cash' then
    raise exception 'PILOT_CASH_ONLY' using errcode = 'P0001';
  end if;
  if not private.pilot_point_allowed(new.pickup_lat::double precision, new.pickup_lng::double precision)
     or not private.pilot_point_allowed(new.dropoff_lat::double precision, new.dropoff_lng::double precision) then
    raise exception 'PILOT_OUTSIDE_SERVICE_AREA' using errcode = 'P0001';
  end if;
  return new;
end;
$$;
revoke all on function private.enforce_pilot_booking() from public, anon, authenticated;
create trigger enforce_pilot_booking before insert on public.trips
for each row execute function private.enforce_pilot_booking();

create table private.pilot_rider_presence (
  rider_id uuid primary key references public.profiles(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  recorded_at timestamptz not null default now()
);
revoke all on private.pilot_rider_presence from public, anon, authenticated;
create function public.record_pilot_rider_presence(p_lat double precision, p_lng double precision)
returns void language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_approved_rider() then
    raise exception 'approved rider access required' using errcode = '42501';
  end if;
  perform 1 from public.profiles where id = actor and is_online for update;
  if not found then
    raise exception 'go online before publishing rider availability' using errcode = '42501';
  end if;
  if p_lat is null or p_lng is null or not(p_lat between -90 and 90 and p_lng between -180 and 180) then
    raise exception 'valid rider coordinates required' using errcode = '22023';
  end if;
  insert into private.pilot_rider_presence(rider_id, latitude, longitude, recorded_at)
  values (actor, p_lat, p_lng, clock_timestamp())
  on conflict (rider_id) do update set latitude = excluded.latitude,
    longitude = excluded.longitude, recorded_at = excluded.recorded_at;
end;
$$;
revoke all on function public.record_pilot_rider_presence(double precision, double precision) from public, anon;
grant execute on function public.record_pilot_rider_presence(double precision, double precision) to authenticated;

create function private.clear_offline_pilot_presence()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if not new.is_online then delete from private.pilot_rider_presence where rider_id = new.id; end if;
  return new;
end;
$$;
revoke all on function private.clear_offline_pilot_presence() from public, anon, authenticated;
create trigger clear_offline_pilot_presence after update of is_online on public.profiles
for each row execute function private.clear_offline_pilot_presence();

create or replace function private.is_online_approved_rider()
returns boolean language sql stable security definer set search_path = '' as $$
  select private.pilot_service_open() and exists (
    select 1 from public.profiles p join private.pilot_rider_presence r on r.rider_id = p.id
    where p.id = (select auth.uid()) and p.role = 'rider'
      and p.approval_status = 'approved' and p.is_online
      and r.recorded_at >= now() - interval '2 minutes'
      and private.pilot_point_allowed(r.latitude, r.longitude)
  )
$$;

commit;
