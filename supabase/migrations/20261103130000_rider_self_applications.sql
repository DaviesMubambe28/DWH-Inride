begin;

create table public.rider_applications (
  applicant_id uuid primary key references public.profiles(id) on delete cascade,
  status public.verification_status not null default 'pending',
  motorcycle_make text not null check (char_length(btrim(motorcycle_make)) between 1 and 80),
  motorcycle_model text not null check (char_length(btrim(motorcycle_model)) between 1 and 80),
  motorcycle_year smallint not null check (motorcycle_year between 1950 and 2100),
  motorcycle_plate text not null check (char_length(btrim(motorcycle_plate)) between 2 and 32),
  motorcycle_color text not null check (char_length(btrim(motorcycle_color)) between 1 and 40),
  driving_licence_number text not null check (char_length(btrim(driving_licence_number)) between 3 and 64),
  reviewed_by uuid references public.profiles(id),
  review_note text check (review_note is null or char_length(review_note) <= 500),
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint rider_application_review_fields check (
    (status = 'pending' and reviewed_by is null and reviewed_at is null)
    or (status in ('approved', 'rejected') and reviewed_by is not null and reviewed_at is not null)
  )
);

comment on table public.rider_applications is
  'Rider self-applications. A row never grants rider or trip access; trusted review and activation remain separate.';
comment on column public.rider_applications.driving_licence_number is
  'Private application data visible only to the applicant and administrators.';

create unique index rider_applications_active_plate_idx
on public.rider_applications (upper(btrim(motorcycle_plate)))
where status in ('pending', 'approved');

alter table public.rider_applications enable row level security;
revoke all on public.rider_applications from public, anon, authenticated;
grant select on public.rider_applications to authenticated;
grant all on public.rider_applications to service_role;

create policy rider_applications_select_owner_or_admin
on public.rider_applications for select to authenticated
using (applicant_id = (select auth.uid()) or (select private.is_admin()));

create policy closed_account_boundary on public.rider_applications
as restrictive for all to authenticated
using (private.account_is_open((select auth.uid())))
with check (private.account_is_open((select auth.uid())));

create trigger guard_closed_account_write
before insert or update on public.rider_applications
for each row execute function private.guard_closed_account_write();

-- Serialize booking creation with rider provisioning. This closes the window in
-- which a passenger could start a booking while an administrator changes their
-- role after the active-trip check.
create function private.guard_trip_participant_roles()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform 1 from public.profiles
  where id in (new.passenger_id, new.rider_id)
  order by id for update;
  if not exists (
    select 1 from public.profiles where id = new.passenger_id and role = 'passenger'
  ) then
    raise exception 'passenger profile is required' using errcode = '42501';
  end if;
  if new.rider_id is not null and not exists (
    select 1 from public.profiles
    where id = new.rider_id and role = 'rider' and approval_status = 'approved'
  ) then
    raise exception 'approved rider profile is required' using errcode = '42501';
  end if;
  return new;
end
$$;
revoke all on function private.guard_trip_participant_roles() from public, anon, authenticated;
create trigger guard_trip_participant_roles
before insert on public.trips
for each row execute function private.guard_trip_participant_roles();

-- Use the same profile-row lock as trip creation before checking active trips.
-- Whichever transaction obtains the lock first establishes the authoritative
-- role/trip state; the other transaction rechecks after it resumes.
create or replace function public.admin_provision_pilot_rider(
  p_user_id uuid, p_make text, p_model text, p_year integer, p_plate text, p_color text
) returns uuid language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  perform 1 from public.profiles
  where id = p_user_id and role = 'passenger'
  for update;
  if not found then
    raise exception 'select an existing passenger account for provisional rider access' using errcode = '22023';
  end if;
  if exists (select 1 from public.trips where passenger_id = p_user_id
    and status in ('requesting', 'accepted', 'arrived', 'in_progress')) then
    raise exception 'finish or cancel the passenger trip before provisioning' using errcode = '22023';
  end if;
  update public.profiles set role = 'rider', approval_status = 'pending', is_online = false
  where id = p_user_id and role = 'passenger';
  if not found then
    raise exception 'select an existing passenger account for provisional rider access' using errcode = '22023';
  end if;
  insert into public.motorcycles(rider_id, make, model, year, plate_number, color)
  values (p_user_id, btrim(p_make), btrim(p_model), p_year::smallint, btrim(p_plate), btrim(p_color));
  insert into private.pilot_operations_audit(actor_id, subject_id, action)
  values (actor, p_user_id, 'rider_provisioned');
  return p_user_id;
end
$$;
revoke all on function public.admin_provision_pilot_rider(uuid, text, text, integer, text, text) from public, anon;
grant execute on function public.admin_provision_pilot_rider(uuid, text, text, integer, text, text) to authenticated;

create or replace function private.is_rider_applicant()
returns boolean language sql stable security definer set search_path = '' as $$
  select private.account_is_open((select auth.uid())) and exists (
    select 1 from public.rider_applications
    where applicant_id = (select auth.uid()) and status in ('pending', 'approved')
  )
$$;
revoke all on function private.is_rider_applicant() from public, anon;
grant execute on function private.is_rider_applicant() to authenticated, service_role;

-- Rebuild signup profile creation so a rider intent creates only a pending
-- application. Untrusted Auth metadata can never set profile.role or approval.
create or replace function private.handle_new_auth_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  full_name_value text := btrim(coalesce(new.raw_user_meta_data ->> 'full_name', ''));
  phone_value text := btrim(coalesce(new.raw_user_meta_data ->> 'phone_number', ''));
  rider_intent boolean := coalesce(new.raw_user_meta_data ->> 'account_intent', '') = 'rider';
  make_value text := btrim(coalesce(new.raw_user_meta_data ->> 'motorcycle_make', ''));
  model_value text := btrim(coalesce(new.raw_user_meta_data ->> 'motorcycle_model', ''));
  year_value integer;
  plate_value text := upper(btrim(coalesce(new.raw_user_meta_data ->> 'motorcycle_plate', '')));
  color_value text := btrim(coalesce(new.raw_user_meta_data ->> 'motorcycle_color', ''));
  licence_value text := upper(btrim(coalesce(new.raw_user_meta_data ->> 'driving_licence_number', '')));
begin
  if char_length(full_name_value) not between 2 and 120 then
    raise exception using errcode = '23514', message = 'Signup requires a full name between 2 and 120 characters.';
  end if;
  if phone_value !~ '^\+260[0-9]{9}$' then
    raise exception using errcode = '23514', message = 'Signup requires a canonical Zambian phone number.';
  end if;

  insert into public.profiles(id, role, full_name, phone_number)
  values(new.id, 'passenger', full_name_value, phone_value)
  on conflict(id) do nothing;

  if rider_intent then
    begin year_value := (new.raw_user_meta_data ->> 'motorcycle_year')::integer;
    exception when invalid_text_representation then
      raise exception using errcode = '23514', message = 'Rider signup requires a valid motorcycle year.';
    end;
    if char_length(make_value) not between 1 and 80
      or char_length(model_value) not between 1 and 80
      or year_value not between 1950 and 2100
      or char_length(plate_value) not between 2 and 32
      or char_length(color_value) not between 1 and 40
      or char_length(licence_value) not between 3 and 64 then
      raise exception using errcode = '23514', message = 'Rider application details are incomplete or invalid.';
    end if;
    insert into public.rider_applications(
      applicant_id, motorcycle_make, motorcycle_model, motorcycle_year,
      motorcycle_plate, motorcycle_color, driving_licence_number
    ) values (
      new.id, make_value, model_value, year_value, plate_value, color_value, licence_value
    );
  end if;
  return new;
end
$$;
revoke all on function private.handle_new_auth_user() from public, anon, authenticated;

create or replace function public.admin_review_rider_application(
  p_applicant_id uuid, p_decision public.verification_status, p_review_note text default null
) returns public.verification_status
language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  application public.rider_applications%rowtype;
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  if p_decision is null or p_decision not in ('approved', 'rejected') then
    raise exception 'decision must be approved or rejected' using errcode = '22023';
  end if;
  if p_decision = 'rejected' and nullif(btrim(coalesce(p_review_note, '')), '') is null then
    raise exception 'a rejection reason is required' using errcode = '22023';
  end if;
  if p_review_note is not null and char_length(btrim(p_review_note)) > 500 then
    raise exception 'review note is too long' using errcode = '22023';
  end if;

  select * into application from public.rider_applications
  where applicant_id = p_applicant_id for update;
  if not found then
    raise exception 'rider application not found' using errcode = '22023';
  end if;
  if application.status = 'approved' then
    raise exception 'approved application is already provisioned' using errcode = '22023';
  end if;

  if p_decision = 'approved' then
    perform public.admin_provision_pilot_rider(
      p_applicant_id, application.motorcycle_make, application.motorcycle_model,
      application.motorcycle_year, application.motorcycle_plate, application.motorcycle_color
    );
  end if;

  update public.rider_applications
  set status = p_decision, reviewed_by = actor, review_note = nullif(btrim(p_review_note), ''),
      reviewed_at = now(), updated_at = now()
  where applicant_id = p_applicant_id;
  return p_decision;
end
$$;
revoke all on function public.admin_review_rider_application(uuid, public.verification_status, text) from public, anon;
grant execute on function public.admin_review_rider_application(uuid, public.verification_status, text) to authenticated;

-- Applicants can submit private evidence before provisioning. Status changes
-- remain guarded by the existing admin-only document trigger.
drop policy if exists rider_documents_owner_or_admin_select on public.rider_documents;
create policy rider_documents_owner_or_admin_select on public.rider_documents for select to authenticated
using ((select private.is_admin()) or (rider_id = (select auth.uid()) and ((select private.is_rider()) or (select private.is_rider_applicant()))));
drop policy if exists rider_documents_owner_or_admin_insert on public.rider_documents;
create policy rider_documents_owner_or_admin_insert on public.rider_documents for insert to authenticated
with check ((select private.is_admin()) or (
  rider_id = (select auth.uid()) and ((select private.is_rider()) or (select private.is_rider_applicant()))
  and verification_status = 'pending' and storage_url like ((select auth.uid())::text || '/%')
));
drop policy if exists rider_documents_owner_or_admin_update on public.rider_documents;
create policy rider_documents_owner_or_admin_update on public.rider_documents for update to authenticated
using ((select private.is_admin()) or (rider_id = (select auth.uid()) and ((select private.is_rider()) or (select private.is_rider_applicant()))))
with check ((select private.is_admin()) or (
  rider_id = (select auth.uid()) and storage_url like ((select auth.uid())::text || '/%')
  and ((select private.is_rider()) or (select private.is_rider_applicant()))
));
drop policy if exists rider_documents_owner_or_admin_delete on public.rider_documents;
create policy rider_documents_owner_or_admin_delete on public.rider_documents for delete to authenticated
using ((select private.is_admin()) or (rider_id = (select auth.uid()) and ((select private.is_rider()) or (select private.is_rider_applicant()))));

drop policy if exists rider_documents_objects_select_owner_or_admin on storage.objects;
create policy rider_documents_objects_select_owner_or_admin on storage.objects for select to authenticated
using (bucket_id = 'rider-documents' and (((storage.foldername(name))[1] = (select auth.uid())::text
  and ((select private.is_rider()) or (select private.is_rider_applicant()))) or (select private.is_admin())));
drop policy if exists rider_documents_objects_insert_owner on storage.objects;
create policy rider_documents_objects_insert_owner on storage.objects for insert to authenticated
with check (bucket_id = 'rider-documents' and (storage.foldername(name))[1] = (select auth.uid())::text
  and ((select private.is_rider()) or (select private.is_rider_applicant())));
drop policy if exists rider_documents_objects_update_owner on storage.objects;
create policy rider_documents_objects_update_owner on storage.objects for update to authenticated
using (bucket_id = 'rider-documents' and (storage.foldername(name))[1] = (select auth.uid())::text
  and ((select private.is_rider()) or (select private.is_rider_applicant())))
with check (bucket_id = 'rider-documents' and (storage.foldername(name))[1] = (select auth.uid())::text
  and ((select private.is_rider()) or (select private.is_rider_applicant())));
drop policy if exists rider_documents_objects_delete_owner on storage.objects;
create policy rider_documents_objects_delete_owner on storage.objects for delete to authenticated
using (bucket_id = 'rider-documents' and (storage.foldername(name))[1] = (select auth.uid())::text
  and ((select private.is_rider()) or (select private.is_rider_applicant())));

create function private.erase_rider_application_after_retention()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.state = 'anonymized' and new.state is distinct from old.state then
    delete from public.rider_applications where applicant_id = new.user_id;
  end if;
  return new;
end
$$;
revoke all on function private.erase_rider_application_after_retention() from public, anon, authenticated;
create trigger erase_rider_application_after_retention
after update of state on private.account_closures
for each row execute function private.erase_rider_application_after_retention();

commit;
