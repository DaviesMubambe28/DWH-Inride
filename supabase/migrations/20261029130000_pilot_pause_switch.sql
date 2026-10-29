begin;
alter table private.pilot_operations_audit drop constraint pilot_operations_audit_action_check;
alter table private.pilot_operations_audit add constraint pilot_operations_audit_action_check
  check (action in ('rider_provisioned', 'rider_activated', 'pilot_paused'));

create function public.admin_pause_pilot()
returns boolean language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  update private.pilot_service_settings set enabled = false where singleton;
  insert into private.pilot_operations_audit(actor_id, subject_id, action)
  values (actor, actor, 'pilot_paused');
  return true;
end;
$$;
revoke all on function public.admin_pause_pilot() from public, anon;
grant execute on function public.admin_pause_pilot() to authenticated;
commit;
