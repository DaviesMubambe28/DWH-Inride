begin;

create table public.incidents (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid references public.trips(id) on delete set null,
  reporter_id uuid references public.profiles(id) on delete set null,
  recorded_by uuid not null references public.profiles(id),
  assigned_admin_id uuid references public.profiles(id),
  category text not null check (category in ('safety', 'service', 'conduct', 'payment', 'other')),
  severity text not null check (severity in ('low', 'medium', 'high', 'critical')),
  summary text not null check (char_length(btrim(summary)) between 10 and 2000),
  status text not null default 'open' check (status in ('open', 'in_review', 'resolved')),
  resolution_note text check (resolution_note is null or char_length(btrim(resolution_note)) between 10 and 2000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resolved_at timestamptz,
  constraint incident_resolution_complete check (
    (status = 'resolved' and resolution_note is not null and resolved_at is not null)
    or (status <> 'resolved' and resolution_note is null and resolved_at is null)
  )
);

comment on table public.incidents is
  'Durable operator incident records. Clients cannot write directly; trusted admin RPCs enforce validation and audit changes.';

create index incidents_status_created_idx on public.incidents(status, created_at desc);
create index incidents_trip_idx on public.incidents(trip_id) where trip_id is not null;

alter table public.incidents enable row level security;
alter table public.incidents force row level security;
revoke all on public.incidents from public, anon, authenticated;
grant select on public.incidents to authenticated;
grant all on public.incidents to service_role;

create policy incidents_admin_select on public.incidents for select to authenticated
using ((select private.is_admin()));

create table private.incident_audit (
  id bigint generated always as identity primary key,
  incident_id uuid not null references public.incidents(id) on delete cascade,
  actor_id uuid not null references public.profiles(id),
  action text not null check (action in ('recorded', 'updated')),
  recorded_at timestamptz not null default now()
);
revoke all on private.incident_audit from public, anon, authenticated;

create function public.admin_record_incident(
  p_trip_id uuid,
  p_reporter_id uuid,
  p_category text,
  p_severity text,
  p_summary text
) returns uuid language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  created_id uuid;
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  if p_category is null or p_category not in ('safety', 'service', 'conduct', 'payment', 'other') then
    raise exception 'invalid incident category' using errcode = '22023';
  end if;
  if p_severity is null or p_severity not in ('low', 'medium', 'high', 'critical') then
    raise exception 'invalid incident severity' using errcode = '22023';
  end if;
  if char_length(btrim(coalesce(p_summary, ''))) not between 10 and 2000 then
    raise exception 'incident summary must contain 10 to 2000 characters' using errcode = '22023';
  end if;
  if p_trip_id is not null and not exists (select 1 from public.trips where id = p_trip_id) then
    raise exception 'trip not found' using errcode = '22023';
  end if;
  if p_reporter_id is not null and not exists (select 1 from public.profiles where id = p_reporter_id) then
    raise exception 'reporter not found' using errcode = '22023';
  end if;
  if p_trip_id is not null and p_reporter_id is not null and not exists (
    select 1 from public.trips
    where id = p_trip_id and p_reporter_id in (passenger_id, rider_id)
  ) then
    raise exception 'reporter is not a trip participant' using errcode = '22023';
  end if;

  insert into public.incidents(trip_id, reporter_id, recorded_by, category, severity, summary)
  values (p_trip_id, p_reporter_id, actor, p_category, p_severity, btrim(p_summary))
  returning id into created_id;
  insert into private.incident_audit(incident_id, actor_id, action)
  values (created_id, actor, 'recorded');
  return created_id;
end
$$;

create function public.admin_update_incident(
  p_incident_id uuid,
  p_status text,
  p_severity text,
  p_assigned_admin_id uuid,
  p_resolution_note text default null
) returns text language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  if p_status is null or p_status not in ('open', 'in_review', 'resolved') then
    raise exception 'invalid incident status' using errcode = '22023';
  end if;
  if p_severity is null or p_severity not in ('low', 'medium', 'high', 'critical') then
    raise exception 'invalid incident severity' using errcode = '22023';
  end if;
  if p_assigned_admin_id is not null and not exists (
    select 1 from public.profiles where id = p_assigned_admin_id and role = 'admin'
  ) then
    raise exception 'assigned operator must be an admin' using errcode = '22023';
  end if;
  if p_status = 'resolved' and char_length(btrim(coalesce(p_resolution_note, ''))) not between 10 and 2000 then
    raise exception 'resolution note must contain 10 to 2000 characters' using errcode = '22023';
  end if;

  update public.incidents set
    status = p_status,
    severity = p_severity,
    assigned_admin_id = p_assigned_admin_id,
    resolution_note = case when p_status = 'resolved' then btrim(p_resolution_note) else null end,
    resolved_at = case when p_status = 'resolved' then now() else null end,
    updated_at = now()
  where id = p_incident_id;
  if not found then raise exception 'incident not found' using errcode = '22023'; end if;
  insert into private.incident_audit(incident_id, actor_id, action)
  values (p_incident_id, actor, 'updated');
  return p_status;
end
$$;

revoke all on function public.admin_record_incident(uuid, uuid, text, text, text) from public, anon;
revoke all on function public.admin_update_incident(uuid, text, text, uuid, text) from public, anon;
grant execute on function public.admin_record_incident(uuid, uuid, text, text, text) to authenticated;
grant execute on function public.admin_update_incident(uuid, text, text, uuid, text) to authenticated;

commit;
