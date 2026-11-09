begin;

alter table private.pilot_service_settings
  alter column opens set default '00:00',
  alter column closes set default '23:59:59.999999',
  alter column weekdays set default array[1,2,3,4,5,6,7];

do $$
begin
  update private.pilot_service_settings
  set enabled = false,
      opens = '00:00',
      closes = '23:59:59.999999',
      weekdays = array[1,2,3,4,5,6,7]
  where singleton;
  if not found then
    raise exception 'pilot service settings row is missing; refusing 24/7 migration';
  end if;
end;
$$;

-- Keep the timestamp parameter for existing callers; operations are gated by enabled, not local time.
create or replace function private.pilot_service_open(p_at timestamptz default now())
returns boolean language sql stable security definer set search_path = '' as $$
  select coalesce((select enabled
    from private.pilot_service_settings where singleton), false)
$$;
revoke all on function private.pilot_service_open(timestamptz) from public, anon;
grant execute on function private.pilot_service_open(timestamptz) to authenticated, service_role;

create or replace function public.pilot_service_status()
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
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
    'message', case when private.pilot_service_open()
      then 'Service available 24/7. Rider availability may vary; check rider status before requesting.'
      else 'Service temporarily paused or not configured. Please try again later.' end,
    'cash_only', true, 'fare_zmw', 20,
    'center_latitude', -15.3292, 'center_longitude', 28.6820, 'radius_km', 4.0,
    'support_phone', '+260770001203', 'support_secondary_phone', '+260975461121',
    'support_email', 'gershmukuka1@gmail.com')
$$;

alter table private.pilot_operations_audit
  drop constraint if exists pilot_operations_audit_action_check;
alter table private.pilot_operations_audit
  add constraint pilot_operations_audit_action_check
  check (action in ('rider_provisioned', 'rider_activated', 'pilot_paused', 'pilot_resumed'));

create or replace function public.admin_resume_pilot()
returns boolean language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;

  update private.pilot_service_settings set enabled = true where singleton;
  if not found then
    raise exception 'pilot service configuration is unavailable' using errcode = '23514';
  end if;

  insert into private.pilot_operations_audit(actor_id, subject_id, action)
  values (actor, actor, 'pilot_resumed');
  return true;
end;
$$;
revoke all on function public.admin_resume_pilot() from public, anon, authenticated;
grant execute on function public.admin_resume_pilot() to authenticated;

create or replace function public.admin_pilot_operations_status()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;

  return jsonb_build_object(
    'available', private.pilot_service_open(),
    'service_24_7', true,
    'message', case when private.pilot_service_open()
      then 'Service available 24/7. Rider availability may vary.'
      else 'Service temporarily paused or not configured. Please try again later.' end,
    'online_riders', (
      select count(*) from public.profiles
      where role = 'rider' and approval_status = 'approved' and is_online
    ),
    'available_riders', (
      select count(*)
      from public.profiles p
      join private.pilot_rider_presence presence on presence.rider_id = p.id
      where p.role = 'rider' and p.approval_status = 'approved' and p.is_online
        and private.pilot_service_open()
        and presence.recorded_at >= now() - interval '2 minutes'
        and private.pilot_point_allowed(presence.latitude, presence.longitude)
    )
  );
end;
$$;
revoke all on function public.admin_pilot_operations_status() from public, anon, authenticated;
grant execute on function public.admin_pilot_operations_status() to authenticated;

commit;
