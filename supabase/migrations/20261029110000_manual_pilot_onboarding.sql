begin;

create table private.pilot_rider_checks (
  rider_id uuid primary key references public.profiles(id),
  reviewed_by uuid not null references public.profiles(id),
  documents_checked boolean not null check (documents_checked),
  safety_briefing_completed boolean not null check (safety_briefing_completed),
  physical_device_tested boolean not null check (physical_device_tested),
  reviewed_at timestamptz not null default now()
);
create table private.pilot_operations_audit (
  id bigint generated always as identity primary key,
  actor_id uuid not null references public.profiles(id),
  subject_id uuid not null references public.profiles(id),
  action text not null check (action in ('rider_provisioned', 'rider_activated')),
  recorded_at timestamptz not null default now()
);
revoke all on private.pilot_rider_checks, private.pilot_operations_audit from public, anon, authenticated;

create function public.admin_provision_pilot_rider(
  p_user_id uuid, p_make text, p_model text, p_year integer, p_plate text, p_color text
) returns uuid language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
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
end;
$$;

create function private.require_pilot_rider_checks()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if (select auth.uid()) is not null
     and new.approval_status = 'approved' and new.approval_status is distinct from old.approval_status
     and not exists (select 1 from private.pilot_rider_checks where rider_id = new.id) then
    raise exception 'complete the pilot documents, safety briefing, and physical device checks first' using errcode = '42501';
  end if;
  return new;
end;
$$;
revoke all on function private.require_pilot_rider_checks() from public, anon, authenticated;
create trigger require_pilot_rider_checks before update of approval_status on public.profiles
for each row execute function private.require_pilot_rider_checks();

create function public.admin_activate_pilot_rider(
  p_rider_id uuid, p_documents_checked boolean, p_safety_briefing_completed boolean, p_physical_device_tested boolean
) returns public.verification_status language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  if p_documents_checked is distinct from true or p_safety_briefing_completed is distinct from true
    or p_physical_device_tested is distinct from true then
    raise exception 'all in-person pilot onboarding checks are required' using errcode = '22023';
  end if;
  if not exists (select 1 from public.profiles where id = p_rider_id and role = 'rider')
    or not exists (select 1 from public.motorcycles where rider_id = p_rider_id) then
    raise exception 'provision the rider account and motorcycle first' using errcode = '22023';
  end if;
  insert into private.pilot_rider_checks(rider_id, reviewed_by, documents_checked, safety_briefing_completed, physical_device_tested)
  values (p_rider_id, actor, true, true, true)
  on conflict (rider_id) do update set reviewed_by = actor, documents_checked = true,
    safety_briefing_completed = true, physical_device_tested = true, reviewed_at = now();
  perform public.admin_set_driver_approval(p_rider_id, 'approved');
  insert into private.pilot_operations_audit(actor_id, subject_id, action)
  values (actor, p_rider_id, 'rider_activated');
  return 'approved'::public.verification_status;
end;
$$;

revoke all on function public.admin_provision_pilot_rider(uuid, text, text, integer, text, text) from public, anon;
revoke all on function public.admin_activate_pilot_rider(uuid, boolean, boolean, boolean) from public, anon;
grant execute on function public.admin_provision_pilot_rider(uuid, text, text, integer, text, text) to authenticated;
grant execute on function public.admin_activate_pilot_rider(uuid, boolean, boolean, boolean) to authenticated;

commit;
