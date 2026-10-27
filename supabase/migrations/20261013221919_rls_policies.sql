begin;

create schema if not exists private;
revoke all on schema private from public;

create or replace function private.is_admin()
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
        and p.role = 'admin'
    ),
    false
  );
$$;

create or replace function private.is_rider()
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
    ),
    false
  );
$$;

revoke all on function private.is_admin() from public, anon;
revoke all on function private.is_rider() from public, anon;
grant usage on schema private to authenticated, service_role;
grant execute on function private.is_admin() to authenticated, service_role;
grant execute on function private.is_rider() to authenticated, service_role;

alter table public.profiles enable row level security;
alter table public.motorcycles enable row level security;
alter table public.rider_documents enable row level security;
alter table public.trips enable row level security;
alter table public.trip_locations enable row level security;

revoke all on table public.profiles from anon, authenticated;
revoke all on table public.motorcycles from anon, authenticated;
revoke all on table public.rider_documents from anon, authenticated;
revoke all on table public.trips from anon, authenticated;
revoke all on table public.trip_locations from anon, authenticated;

grant usage on schema public to authenticated, service_role;
grant all on table public.profiles to service_role;
grant all on table public.motorcycles to service_role;
grant all on table public.rider_documents to service_role;
grant all on table public.trips to service_role;
grant all on table public.trip_locations to service_role;
grant usage, select on sequence public.trip_locations_id_seq to service_role;

grant select on table public.profiles to authenticated;
grant insert (id, full_name, phone_number) on table public.profiles to authenticated;
grant update (full_name, phone_number, is_online) on table public.profiles to authenticated;

grant select, insert, update, delete on table public.motorcycles to authenticated;
grant select, insert, delete on table public.rider_documents to authenticated;

grant select (
  id,
  passenger_id,
  rider_id,
  status,
  pickup_lat,
  pickup_lng,
  pickup_address,
  dropoff_lat,
  dropoff_lng,
  dropoff_address,
  fare_amount,
  payment_method,
  started_at,
  completed_at,
  created_at
) on table public.trips to authenticated;

grant insert (
  passenger_id,
  pickup_lat,
  pickup_lng,
  pickup_address,
  dropoff_lat,
  dropoff_lng,
  dropoff_address,
  payment_method
) on table public.trips to authenticated;

grant update (status) on table public.trips to authenticated;
grant select, insert on table public.trip_locations to authenticated;
grant usage, select on sequence public.trip_locations_id_seq to authenticated;

drop policy if exists profiles_select_self_or_admin on public.profiles;
create policy profiles_select_self_or_admin
on public.profiles
for select
to authenticated
using (
  id = (select auth.uid())
  or (select private.is_admin())
);

drop policy if exists profiles_insert_self_as_passenger on public.profiles;
create policy profiles_insert_self_as_passenger
on public.profiles
for insert
to authenticated
with check (
  id = (select auth.uid())
  and role = 'passenger'
  and is_online = false
  and rating = 0
  and total_trips = 0
);

drop policy if exists profiles_update_self_safe_fields on public.profiles;
create policy profiles_update_self_safe_fields
on public.profiles
for update
to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

drop policy if exists motorcycles_select_owner_or_admin on public.motorcycles;
create policy motorcycles_select_owner_or_admin
on public.motorcycles
for select
to authenticated
using (
  rider_id = (select auth.uid())
  or (select private.is_admin())
);

drop policy if exists motorcycles_insert_owner on public.motorcycles;
create policy motorcycles_insert_owner
on public.motorcycles
for insert
to authenticated
with check (
  rider_id = (select auth.uid())
  and (select private.is_rider())
);

drop policy if exists motorcycles_update_owner on public.motorcycles;
create policy motorcycles_update_owner
on public.motorcycles
for update
to authenticated
using (
  rider_id = (select auth.uid())
  and (select private.is_rider())
)
with check (
  rider_id = (select auth.uid())
  and (select private.is_rider())
);

drop policy if exists motorcycles_delete_owner on public.motorcycles;
create policy motorcycles_delete_owner
on public.motorcycles
for delete
to authenticated
using (
  rider_id = (select auth.uid())
  and (select private.is_rider())
);

drop policy if exists rider_documents_select_owner_or_admin on public.rider_documents;
create policy rider_documents_select_owner_or_admin
on public.rider_documents
for select
to authenticated
using (
  rider_id = (select auth.uid())
  or (select private.is_admin())
);

drop policy if exists rider_documents_insert_owner on public.rider_documents;
create policy rider_documents_insert_owner
on public.rider_documents
for insert
to authenticated
with check (
  rider_id = (select auth.uid())
  and (select private.is_rider())
  and verification_status = 'pending'
  and storage_url like ((select auth.uid())::text || '/%')
);

drop policy if exists rider_documents_delete_pending_owner on public.rider_documents;
create policy rider_documents_delete_pending_owner
on public.rider_documents
for delete
to authenticated
using (
  rider_id = (select auth.uid())
  and (select private.is_rider())
  and verification_status = 'pending'
);

drop policy if exists trips_select_participant_or_admin on public.trips;
create policy trips_select_participant_or_admin
on public.trips
for select
to authenticated
using (
  passenger_id = (select auth.uid())
  or rider_id = (select auth.uid())
  or (select private.is_admin())
);

drop policy if exists trips_insert_passenger_request on public.trips;
create policy trips_insert_passenger_request
on public.trips
for insert
to authenticated
with check (
  passenger_id = (select auth.uid())
  and rider_id is null
  and status = 'requesting'
  and fare_amount is null
  and trip_pin_hash is null
  and payment_method = 'cash'
  and started_at is null
  and completed_at is null
);

drop policy if exists trips_update_passenger on public.trips;
create policy trips_update_passenger
on public.trips
for update
to authenticated
using (passenger_id = (select auth.uid()))
with check (passenger_id = (select auth.uid()));

drop policy if exists trips_update_assigned_rider on public.trips;
create policy trips_update_assigned_rider
on public.trips
for update
to authenticated
using (
  rider_id = (select auth.uid())
  and (select private.is_rider())
)
with check (
  rider_id = (select auth.uid())
  and (select private.is_rider())
);

create or replace function private.enforce_client_trip_status_transition()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  -- Trusted database and service-role operations do not carry a user ID.
  if actor_id is null then
    return new;
  end if;

  if actor_id = old.passenger_id then
    if new.status <> 'cancelled'
       or old.status not in ('requesting', 'accepted', 'arrived') then
      raise exception 'passenger is not allowed to perform this trip transition'
        using errcode = '42501';
    end if;
    return new;
  end if;

  if actor_id = old.rider_id and (select private.is_rider()) then
    if not (old.status = 'accepted' and new.status = 'arrived') then
      raise exception 'rider is not allowed to perform this trip transition'
        using errcode = '42501';
    end if;
    return new;
  end if;

  raise exception 'user is not a participant in this trip'
    using errcode = '42501';
end;
$$;

revoke all on function private.enforce_client_trip_status_transition() from public, anon, authenticated;

drop trigger if exists enforce_client_trip_status_transition on public.trips;
create trigger enforce_client_trip_status_transition
before update on public.trips
for each row
execute function private.enforce_client_trip_status_transition();

drop policy if exists trip_locations_select_participant_or_admin on public.trip_locations;
create policy trip_locations_select_participant_or_admin
on public.trip_locations
for select
to authenticated
using (
  rider_id = (select auth.uid())
  or (select private.is_admin())
  or exists (
    select 1
    from public.trips as t
    where t.id = trip_locations.trip_id
      and t.passenger_id = (select auth.uid())
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

grant select, insert, update, delete on table storage.objects to authenticated;

drop policy if exists rider_documents_objects_select_owner_or_admin on storage.objects;
create policy rider_documents_objects_select_owner_or_admin
on storage.objects
for select
to authenticated
using (
  bucket_id = 'rider-documents'
  and (
    (
      (storage.foldername(name))[1] = (select auth.uid())::text
      and (select private.is_rider())
    )
    or (select private.is_admin())
  )
);

drop policy if exists rider_documents_objects_insert_owner on storage.objects;
create policy rider_documents_objects_insert_owner
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'rider-documents'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (select private.is_rider())
);

drop policy if exists rider_documents_objects_update_owner on storage.objects;
create policy rider_documents_objects_update_owner
on storage.objects
for update
to authenticated
using (
  bucket_id = 'rider-documents'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (select private.is_rider())
)
with check (
  bucket_id = 'rider-documents'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (select private.is_rider())
);

drop policy if exists rider_documents_objects_delete_owner on storage.objects;
create policy rider_documents_objects_delete_owner
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'rider-documents'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (select private.is_rider())
);

commit;
