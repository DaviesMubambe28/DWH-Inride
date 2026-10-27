begin;

-- DHW-inRide access hardening for the existing schema.
-- This deliberately keeps public.trips and its existing `requesting` status;
-- the Android client and existing RPCs use those names. `requesting` is the
-- existing schema's equivalent of the requested state.

-- Existing migrations already create these columns. These additive guards make
-- this migration safe to apply to an older copy of the existing schema too.
alter table public.profiles
  add column if not exists approval_status public.verification_status;

alter table public.profiles
  add column if not exists is_approved boolean generated always as
    (role = 'rider' and approval_status = 'approved') stored;

alter table public.trips
  add column if not exists passenger_id uuid references public.profiles(id) on delete restrict,
  add column if not exists rider_id uuid references public.profiles(id) on delete set null,
  add column if not exists status public.trip_status not null default 'requesting',
  add column if not exists pickup_lat numeric(9,6),
  add column if not exists pickup_lng numeric(10,6),
  add column if not exists pickup_address text,
  add column if not exists dropoff_lat numeric(9,6),
  add column if not exists dropoff_lng numeric(10,6),
  add column if not exists dropoff_address text;

-- Passenger name and phone remain in profiles.full_name / profiles.phone_number.
-- Do not duplicate PII into trips.

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated, service_role;

create or replace function private.is_admin()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.role = 'admin'
  );
$$;

create or replace function private.is_approved_rider()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.role = 'rider'
      and p.is_approved
  );
$$;

create or replace function private.is_passenger()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.role = 'passenger'
  );
$$;

create or replace function private.is_rider()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.role = 'rider'
  );
$$;

create or replace function private.is_online_approved_rider()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.role = 'rider'
      and p.is_approved
      and p.is_online
  );
$$;

revoke all on function private.is_admin() from public, anon;
revoke all on function private.is_approved_rider() from public, anon;
revoke all on function private.is_passenger() from public, anon;
revoke all on function private.is_rider() from public, anon;
revoke all on function private.is_online_approved_rider() from public, anon;
grant execute on function private.is_admin() to authenticated, service_role;
grant execute on function private.is_approved_rider() to authenticated, service_role;
grant execute on function private.is_passenger() to authenticated, service_role;
grant execute on function private.is_rider() to authenticated, service_role;
grant execute on function private.is_online_approved_rider() to authenticated, service_role;

alter table public.profiles enable row level security;
alter table public.trips enable row level security;
alter table public.rider_documents enable row level security;
alter table public.trip_locations enable row level security;

-- Remove the old permissive policies so the policies below are the complete
-- authenticated-user policy set for these four tables.
drop policy if exists profiles_select_self_or_admin on public.profiles;
drop policy if exists profiles_insert_self_as_passenger on public.profiles;
drop policy if exists profiles_update_self_safe_fields on public.profiles;
drop policy if exists profiles_admin_all on public.profiles;

drop policy if exists trips_select_participant_or_admin on public.trips;
drop policy if exists trips_select_available_for_approved_rider on public.trips;
drop policy if exists trips_insert_passenger_request on public.trips;
drop policy if exists trips_update_passenger on public.trips;
drop policy if exists trips_update_assigned_rider on public.trips;
drop policy if exists trips_admin_all on public.trips;

drop policy if exists rider_documents_select_owner_or_admin on public.rider_documents;
drop policy if exists rider_documents_insert_owner on public.rider_documents;
drop policy if exists rider_documents_delete_pending_owner on public.rider_documents;
drop policy if exists rider_documents_admin_all on public.rider_documents;

drop policy if exists trip_locations_select_participant_or_admin on public.trip_locations;
drop policy if exists trip_locations_insert_assigned_rider on public.trip_locations;
drop policy if exists trip_locations_update_assigned_rider on public.trip_locations;
drop policy if exists trip_locations_admin_all on public.trip_locations;

-- Column grants alone cannot authorize a column differently for admins and
-- non-admins, so protected changes are additionally enforced by triggers below.
revoke all on public.profiles, public.trips, public.rider_documents, public.trip_locations
  from anon, authenticated;
grant all on public.profiles, public.trips, public.rider_documents, public.trip_locations
  to service_role;
grant select, insert, update, delete on public.profiles to authenticated;
grant insert, update, delete on public.trips to authenticated;
grant select (
  id, passenger_id, rider_id, status,
  pickup_lat, pickup_lng, dropoff_lat, dropoff_lng,
  fare_amount, payment_method, payment_status,
  started_at, completed_at, created_at,
  route_distance_meters, route_duration_seconds, driver_fare, service_fee
) on public.trips to authenticated;
grant select, insert, update, delete on public.rider_documents, public.trip_locations to authenticated;
grant usage, select on sequence public.trip_locations_id_seq to authenticated, service_role;

-- PROFILE POLICIES -----------------------------------------------------------
create policy profiles_select_self_or_admin_or_assigned_passenger
on public.profiles for select to authenticated
using (
  id = (select auth.uid())
  or (select private.is_admin())
  or (
    (select private.is_approved_rider())
    and exists (
      select 1 from public.trips t
      where t.passenger_id = profiles.id
        and t.rider_id = (select auth.uid())
        and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled')
    )
  )
);

create policy profiles_insert_self_passenger
on public.profiles for insert to authenticated
with check (
  id = (select auth.uid())
  and role = 'passenger'
  and is_online = false
  and rating = 0
  and total_trips = 0
);

create policy profiles_admin_insert
on public.profiles for insert to authenticated
with check ((select private.is_admin()));

create policy profiles_update_self_or_admin
on public.profiles for update to authenticated
using (id = (select auth.uid()) or (select private.is_admin()))
with check (id = (select auth.uid()) or (select private.is_admin()));

create policy profiles_admin_delete
on public.profiles for delete to authenticated
using ((select private.is_admin()));

create or replace function private.guard_profile_sensitive_fields()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
  trusted_write boolean := coalesce(current_setting('app.profile_write_authorized', true), '') = 'true'
    or coalesce(current_setting('app.trip_transition_authorized', true), '') = 'true';
begin
  if actor is null or (select private.is_admin()) or trusted_write then
    return new;
  end if;

  if new.role is distinct from old.role then
    raise exception 'only an existing admin may change profiles.role' using errcode = '42501';
  end if;
  if new.approval_status is distinct from old.approval_status then
    raise exception 'only an admin may change rider approval' using errcode = '42501';
  end if;
  if new.is_online is distinct from old.is_online then
    raise exception 'use the protected rider availability operation' using errcode = '42501';
  end if;
  if new.rating is distinct from old.rating or new.total_trips is distinct from old.total_trips then
    raise exception 'profile statistics are server managed' using errcode = '42501';
  end if;
  if new.id is distinct from old.id or new.created_at is distinct from old.created_at then
    raise exception 'profile identity fields cannot be changed' using errcode = '42501';
  end if;
  return new;
end;
$$;
revoke all on function private.guard_profile_sensitive_fields() from public, anon, authenticated;
drop trigger if exists guard_profile_sensitive_fields on public.profiles;
create trigger guard_profile_sensitive_fields
before update on public.profiles
for each row execute function private.guard_profile_sensitive_fields();

-- TRIP POLICIES --------------------------------------------------------------
create policy trips_select_passenger_assigned_rider_or_admin
on public.trips for select to authenticated
using (
  (passenger_id = (select auth.uid()) and (select private.is_passenger()))
  or (rider_id = (select auth.uid()) and (select private.is_approved_rider()))
  or (select private.is_admin())
);

create policy trips_select_open_requests_for_approved_online_riders
on public.trips for select to authenticated
using (
  status = 'requesting'
  and rider_id is null
  and (select private.is_approved_rider())
  and (select private.is_online_approved_rider())
);

-- Booking creation remains server-side through create_routed_trip().
-- Passengers may cancel only their own still-requesting ride.
create policy trips_passenger_cancel_requested
on public.trips for update to authenticated
using (
  passenger_id = (select auth.uid())
  and status = 'requesting'
  and (select private.is_passenger())
)
with check (passenger_id = (select auth.uid()) and status = 'cancelled' and rider_id is null);

create policy trips_rider_accept_or_update_assigned
on public.trips for update to authenticated
using (
  (select private.is_approved_rider())
  and (
    (status = 'requesting' and rider_id is null and (select private.is_online_approved_rider()))
    or rider_id = (select auth.uid())
  )
)
with check (
  (select private.is_approved_rider())
  and rider_id = (select auth.uid())
  and status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled')
);

create policy trips_admin_all
on public.trips for all to authenticated
using ((select private.is_admin()))
with check ((select private.is_admin()));

create or replace function private.enforce_client_trip_transition()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
  trusted_write boolean := coalesce(current_setting('app.trip_transition_authorized', true), '') = 'true';
  rider_accept boolean;
begin
  if actor is null or (select private.is_admin()) or trusted_write then
    return new;
  end if;

  rider_accept := old.status = 'requesting'
    and old.rider_id is null
    and new.status = 'accepted'
    and new.rider_id = actor
    and (select private.is_approved_rider());

  if actor = old.passenger_id then
    if old.status <> 'requesting' or new.status <> 'cancelled'
       or (to_jsonb(new) - 'status') is distinct from (to_jsonb(old) - 'status') then
      raise exception 'passengers may only cancel their own requested ride' using errcode = '42501';
    end if;
    return new;
  end if;

  if not (select private.is_approved_rider()) then
    raise exception 'an approved rider is required' using errcode = '42501';
  end if;

  if rider_accept then
    if (to_jsonb(new) - array['status', 'rider_id'])
       is distinct from (to_jsonb(old) - array['status', 'rider_id']) then
      raise exception 'ride details cannot be changed while accepting' using errcode = '42501';
    end if;
    return new;
  end if;

  if old.rider_id <> actor or new.rider_id is distinct from old.rider_id then
    raise exception 'only the assigned rider may update this ride' using errcode = '42501';
  end if;
  if not (
    (old.status = 'accepted' and new.status in ('arrived', 'cancelled'))
    or (old.status = 'arrived' and new.status = 'cancelled')
    or (old.status = 'in_progress' and new.status = 'cancelled')
  ) then
    raise exception 'invalid rider trip status transition' using errcode = '42501';
  end if;
  if (to_jsonb(new) - 'status') is distinct from (to_jsonb(old) - 'status') then
    raise exception 'ride details cannot be changed by the assigned rider' using errcode = '42501';
  end if;
  return new;
end;
$$;
revoke all on function private.enforce_client_trip_transition() from public, anon, authenticated;
drop trigger if exists enforce_client_trip_status_transition on public.trips;
drop trigger if exists enforce_client_trip_transition on public.trips;
create trigger enforce_client_trip_transition
before update on public.trips
for each row execute function private.enforce_client_trip_transition();

-- RIDER DOCUMENT POLICIES ----------------------------------------------------
create policy rider_documents_owner_or_admin_select
on public.rider_documents for select to authenticated
using (
  (select private.is_admin())
  or (rider_id = (select auth.uid()) and (select private.is_rider()))
);

create policy rider_documents_owner_or_admin_insert
on public.rider_documents for insert to authenticated
with check (
  (select private.is_admin())
  or (
    rider_id = (select auth.uid())
    and (select private.is_rider())
    and verification_status = 'pending'
    and storage_url like ((select auth.uid())::text || '/%')
  )
);

create policy rider_documents_owner_or_admin_update
on public.rider_documents for update to authenticated
using (
  (select private.is_admin())
  or (rider_id = (select auth.uid()) and (select private.is_rider()))
)
with check (
  (select private.is_admin())
  or (rider_id = (select auth.uid())
      and storage_url like ((select auth.uid())::text || '/%')
      and (select private.is_rider()))
);

create policy rider_documents_owner_or_admin_delete
on public.rider_documents for delete to authenticated
using (
  (select private.is_admin())
  or (rider_id = (select auth.uid()) and (select private.is_rider()))
);

create or replace function private.guard_rider_document_approval()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  if (select auth.uid()) is not null and not (select private.is_admin()) then
    if tg_op = 'DELETE' then return old; end if;
    if tg_op = 'UPDATE' and new.verification_status is distinct from old.verification_status then
      raise exception 'only an admin may change rider document verification status' using errcode = '42501';
    end if;
    if tg_op = 'INSERT' and new.verification_status <> 'pending' then
      raise exception 'rider documents must start pending' using errcode = '42501';
    end if;
  end if;
  if tg_op = 'DELETE' then return old; end if;
  return new;
end;
$$;
revoke all on function private.guard_rider_document_approval() from public, anon, authenticated;
drop trigger if exists guard_rider_document_approval on public.rider_documents;
create trigger guard_rider_document_approval
before insert or update or delete on public.rider_documents
for each row execute function private.guard_rider_document_approval();

-- LIVE LOCATION POLICIES -----------------------------------------------------
create policy trip_locations_select_own_active_or_passenger_or_admin
on public.trip_locations for select to authenticated
using (
  (rider_id = (select auth.uid()) and (select private.is_approved_rider()) and exists (
    select 1 from public.trips t
    where t.id = trip_locations.trip_id
      and t.rider_id = (select auth.uid())
      and t.status in ('accepted', 'arrived', 'in_progress')
  ))
  or (select private.is_admin())
  or exists (
    select 1 from public.trips t
    where t.id = trip_locations.trip_id
      and t.passenger_id = (select auth.uid())
      and t.status in ('accepted', 'arrived', 'in_progress')
  )
);

create policy trip_locations_insert_assigned_active_rider
on public.trip_locations for insert to authenticated
with check (
  rider_id = (select auth.uid())
  and (select private.is_approved_rider())
  and exists (
    select 1 from public.trips t
    where t.id = trip_locations.trip_id
      and t.rider_id = (select auth.uid())
      and t.status in ('accepted', 'arrived', 'in_progress')
  )
);

create policy trip_locations_update_assigned_active_rider
on public.trip_locations for update to authenticated
using (
  rider_id = (select auth.uid())
  and (select private.is_approved_rider())
  and exists (
    select 1 from public.trips t
    where t.id = trip_locations.trip_id
      and t.rider_id = (select auth.uid())
      and t.status in ('accepted', 'arrived', 'in_progress')
  )
)
with check (
  rider_id = (select auth.uid())
  and (select private.is_approved_rider())
  and exists (
    select 1 from public.trips t
    where t.id = trip_locations.trip_id
      and t.rider_id = (select auth.uid())
      and t.status in ('accepted', 'arrived', 'in_progress')
  )
);

create policy trip_locations_admin_all
on public.trip_locations for all to authenticated
using ((select private.is_admin()))
with check ((select private.is_admin()));

create or replace function private.guard_trip_location_identity()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  if (select auth.uid()) is not null and not (select private.is_admin()) then
    if tg_op = 'UPDATE' and (
      new.id is distinct from old.id
      or new.trip_id is distinct from old.trip_id
      or new.rider_id is distinct from old.rider_id
      or new.recorded_at is distinct from old.recorded_at
    ) then
      raise exception 'rider location identity fields are immutable' using errcode = '42501';
    end if;
    if tg_op = 'DELETE' then
      raise exception 'only an admin may delete rider location history' using errcode = '42501';
    end if;
  end if;
  if tg_op = 'DELETE' then return old; end if;
  return new;
end;
$$;
revoke all on function private.guard_trip_location_identity() from public, anon, authenticated;
drop trigger if exists guard_trip_location_identity on public.trip_locations;
create trigger guard_trip_location_identity
before update or delete on public.trip_locations
for each row execute function private.guard_trip_location_identity();

-- RLS cannot hide selected columns. Revoke trip address columns on the base
-- table and expose safe, role-filtered views instead. These views are SECURITY
-- DEFINER by default; every row has an explicit auth/role predicate below.
revoke select (pickup_address, dropoff_address) on public.trips from authenticated, anon;

drop view if exists public.rider_available_trips;
create view public.rider_available_trips
with (security_barrier = true)
as
select t.id, t.status, t.pickup_lat, t.pickup_lng, t.dropoff_lat, t.dropoff_lng, t.created_at
from public.trips t
where t.status = 'requesting'
  and t.rider_id is null
  and (select private.is_approved_rider())
  and (select private.is_online_approved_rider());

drop view if exists public.authorized_trip_details;
create view public.authorized_trip_details
with (security_barrier = true)
as
select
  t.id, t.passenger_id, t.rider_id, t.status,
  t.pickup_lat, t.pickup_lng, t.pickup_address,
  t.dropoff_lat, t.dropoff_lng, t.dropoff_address,
  t.fare_amount, t.driver_fare, t.service_fee,
  t.route_distance_meters, t.route_duration_seconds,
  t.payment_method, t.payment_status,
  t.started_at, t.completed_at, t.created_at,
  case when (select private.is_admin()) or (t.rider_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled'))
       then passenger.full_name end as passenger_name,
  case when (select private.is_admin()) or (t.rider_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled'))
       then passenger.phone_number end as passenger_phone,
  case when (select private.is_admin()) or (t.passenger_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled'))
       then rider.full_name end as rider_name,
  case when (select private.is_admin()) or (t.passenger_id = (select auth.uid()) and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled'))
       then rider.phone_number end as rider_phone
from public.trips t
left join public.profiles passenger on passenger.id = t.passenger_id
left join public.profiles rider on rider.id = t.rider_id
where (t.passenger_id = (select auth.uid()) and (select private.is_passenger()))
   or (t.rider_id = (select auth.uid()) and (select private.is_approved_rider())
       and t.status in ('accepted', 'arrived', 'in_progress', 'completed', 'cancelled'))
   or (select private.is_admin());

revoke all on public.rider_available_trips, public.authorized_trip_details from public, anon;
grant select on public.rider_available_trips, public.authorized_trip_details to authenticated;

-- Keep role assignment administrative. The mobile rider application must not
-- promote a passenger account through apply_as_driver(). Admins can promote a
-- rider with an authorized profile update; approval remains a separate step.
create or replace function public.apply_as_driver()
returns public.verification_status
language plpgsql security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
  current_role public.app_role;
  current_approval public.verification_status;
begin
  if actor is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  select p.role, p.approval_status into current_role, current_approval
  from public.profiles p where p.id = actor;
  if current_role is distinct from 'rider'::public.app_role then
    raise exception 'rider access must be assigned by an admin' using errcode = '42501';
  end if;
  return current_approval;
end;
$$;
revoke all on function public.apply_as_driver() from public, anon;
grant execute on function public.apply_as_driver() to authenticated;

-- Passenger cancellation is restricted to a ride that is still requesting.
create or replace function public.cancel_trip(p_trip_id uuid)
returns boolean
language plpgsql security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
begin
  if actor is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips t
  set status = 'cancelled'
  where t.id = p_trip_id
    and t.passenger_id = actor
    and t.status = 'requesting'
    and (select private.is_passenger());
  return found;
end;
$$;
revoke all on function public.cancel_trip(uuid) from public, anon;
grant execute on function public.cancel_trip(uuid) to authenticated;

create or replace function public.mark_passenger_paid(p_trip_id uuid)
returns boolean
language plpgsql security definer
set search_path = ''
as $$
begin
  if (select auth.uid()) is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips t
  set payment_status = 'passenger_marked_paid', passenger_marked_paid_at = now()
  where t.id = p_trip_id
    and t.passenger_id = (select auth.uid())
    and t.status = 'completed'
    and t.payment_status = 'unpaid';
  return found;
end;
$$;
revoke all on function public.mark_passenger_paid(uuid) from public, anon;
grant execute on function public.mark_passenger_paid(uuid) to authenticated;

create or replace function public.confirm_rider_payment(p_trip_id uuid)
returns boolean
language plpgsql security definer
set search_path = ''
as $$
begin
  if (select auth.uid()) is null or not (select private.is_approved_rider()) then
    raise exception 'an approved rider is required' using errcode = '42501';
  end if;
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips t
  set payment_status = 'rider_confirmed', rider_confirmed_paid_at = now()
  where t.id = p_trip_id
    and t.rider_id = (select auth.uid())
    and t.status = 'completed'
    and t.payment_status = 'passenger_marked_paid';
  return found;
end;
$$;
revoke all on function public.confirm_rider_payment(uuid) from public, anon;
grant execute on function public.confirm_rider_payment(uuid) to authenticated;

-- Preserve the app's protected availability RPC while keeping is_online out of
-- direct client updates.
create or replace function public.set_driver_online(p_online boolean)
returns boolean
language plpgsql security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
begin
  if actor is null or not (select private.is_approved_rider()) then
    raise exception 'only an approved rider may change online status' using errcode = '42501';
  end if;
  perform set_config('app.profile_write_authorized', 'true', true);
  update public.profiles set is_online = p_online where id = actor;
  return p_online;
end;
$$;
revoke all on function public.set_driver_online(boolean) from public, anon;
grant execute on function public.set_driver_online(boolean) to authenticated;

-- App-facing trip queries must use authorized_trip_details for addresses and
-- passenger PII, and rider_available_trips for pre-acceptance dispatch. The
-- base trips table intentionally no longer grants these address columns.

create index if not exists profiles_role_approval_idx
  on public.profiles (id, approval_status, is_online);
create index if not exists trips_passenger_status_idx
  on public.trips (passenger_id, status, created_at desc);
create index if not exists trips_rider_status_idx
  on public.trips (rider_id, status, created_at desc)
  where rider_id is not null;
create index if not exists trip_locations_trip_recorded_idx
  on public.trip_locations (trip_id, recorded_at desc, id desc);

commit;
