begin;

-- A wrong four-digit PIN must persist its counter instead of being rolled back
-- by the client trip-transition guard. Direct table privileges stay revoked.
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
  if p_pin is null or p_pin !~ '^[0-9]{4}$' then
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
    -- This definer RPC already checked the assigned rider and locked the trip.
    -- Authorize only its server-owned attempt-counter write, just as for start.
    perform set_config('app.trip_transition_authorized', 'true', true);
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
revoke all on function public.verify_trip_pin_and_start(uuid,text) from public,anon;
grant execute on function public.verify_trip_pin_and_start(uuid,text) to authenticated;

commit;
