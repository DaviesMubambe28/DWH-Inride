begin;

-- All trip mutations already have server-authoritative RPCs. Column privileges
-- are the boundary even when an authenticated SQL caller changes custom GUCs.
revoke insert, update, delete on public.trips from authenticated;
revoke insert, update, delete on public.profiles from authenticated;
grant update (full_name, phone_number) on public.profiles to authenticated;

-- CURRENT_ROLE is a SQL keyword (type name), so do not use it as a PL/pgSQL
-- variable. It caused the existing rider check to compare name with app_role.
create or replace function public.apply_as_driver()
returns public.verification_status
language plpgsql security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
  actor_role public.app_role;
  actor_approval public.verification_status;
begin
  if actor is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  select p.role, p.approval_status into actor_role, actor_approval
  from public.profiles p where p.id = actor;
  if actor_role is distinct from 'rider'::public.app_role then
    raise exception 'rider access must be assigned by an admin' using errcode = '42501';
  end if;
  return actor_approval;
end;
$$;
revoke all on function public.apply_as_driver() from public, anon;
grant execute on function public.apply_as_driver() to authenticated;

commit;
