begin;
create table private.client_error_limits (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 last_event timestamptz not null
);
create table private.client_errors (
 event_id uuid primary key, component text not null check(component in ('passenger','rider')),
 created_at timestamptz not null default now(), delivered boolean not null default false,
 lease uuid,leased_until timestamptz
);
revoke all on private.client_error_limits,private.client_errors from public,anon,authenticated;
create function public.report_my_app_crash(p_event_id uuid) returns boolean language plpgsql security definer set search_path='' as $$
declare actor uuid:=(select auth.uid()); actor_role text;begin
 if not private.account_is_open(actor) then raise exception 'active authenticated account required' using errcode='42501'; end if;
 select role::text into actor_role from public.profiles where id=actor;
 if actor_role not in ('passenger','rider') or actor_role is null or p_event_id is null then raise exception 'app identity required' using errcode='42501'; end if;
 if exists(select 1 from private.client_errors where event_id=p_event_id) then return true; end if;
 insert into private.client_error_limits(user_id,last_event) values(actor,now())
 on conflict(user_id) do update set last_event=excluded.last_event where private.client_error_limits.last_event<now()-interval '10 minutes';
 if not found then return false; end if;
 insert into private.client_errors(event_id,component) values(p_event_id,actor_role) on conflict do nothing;
 return true;
end $$;
create function public.claim_client_errors() returns table(event_id uuid,component text,lease uuid) language sql security definer set search_path='' as $$
 with batch as (select e.event_id from private.client_errors e where not e.delivered and (e.leased_until is null or e.leased_until<now()) order by e.created_at for update skip locked limit 10)
 update private.client_errors e set lease=gen_random_uuid(),leased_until=now()+interval '2 minutes' from batch b where e.event_id=b.event_id returning e.event_id,e.component,e.lease
$$;
create function public.complete_client_error(p_event_id uuid,p_lease uuid) returns boolean language plpgsql security definer set search_path='' as $$
begin update private.client_errors set delivered=true,lease=null,leased_until=null where event_id=p_event_id and lease=p_lease and not delivered;return found;end $$;
revoke all on function public.report_my_app_crash(uuid) from public,anon;
grant execute on function public.report_my_app_crash(uuid) to authenticated;
revoke all on function public.claim_client_errors(),public.complete_client_error(uuid,uuid) from public,anon,authenticated;
grant execute on function public.claim_client_errors(),public.complete_client_error(uuid,uuid) to service_role;
-- Reports have no user identifier or exception payload. The rate-limit link is
-- removed at closure, so no identity remains in the telemetry queue.
create function private.clear_client_error_identity() returns trigger language plpgsql security definer set search_path='' as $$
begin delete from private.client_error_limits where user_id=new.user_id;return new;end $$;
create trigger clear_client_error_identity after insert on private.account_closures for each row execute function private.clear_client_error_identity();
revoke all on function private.clear_client_error_identity() from public,anon,authenticated;
commit;
