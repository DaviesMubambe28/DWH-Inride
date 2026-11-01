begin;
-- Closed identities remain non-login tombstones so retained counterpart trip and
-- audit records keep referential integrity after the Auth identity is erased.
alter table public.profiles drop constraint profiles_id_fkey;
alter table public.profiles add column auth_user_id uuid unique references auth.users(id) on delete set null;
update public.profiles set auth_user_id=id;
create function private.link_profile_auth() returns trigger language plpgsql set search_path='' as $$
begin new.auth_user_id:=new.id; return new; end $$;
create trigger link_profile_auth before insert on public.profiles for each row execute function private.link_profile_auth();
revoke all on function private.link_profile_auth() from public,anon,authenticated;

-- PostgreSQL compares interval months as 30 days. Checking merely >= '1 year'
-- would accidentally admit 360 days, shorter than a calendar year.
create function private.valid_retention_period(p interval) returns boolean language sql immutable set search_path='' as $$
 select extract(year from p)>=1 and extract(month from p)>=0 and extract(day from p)>=0
  and extract(hour from p)>=0 and extract(minute from p)>=0 and extract(second from p)>=0
$$;
revoke all on function private.valid_retention_period(interval) from public,anon,authenticated;
grant execute on function private.valid_retention_period(interval) to service_role;
create table private.retention_policy (
 id boolean primary key default true check(id), approved boolean not null default false,
 account_retention interval not null default interval '1 year' check(private.valid_retention_period(account_retention)),
 trip_retention interval not null default interval '1 year' check(private.valid_retention_period(trip_retention)),
 location_retention interval check(private.valid_retention_period(location_retention))
);
insert into private.retention_policy(id) values(true);
create table private.account_closures (
 user_id uuid primary key references public.profiles(id),requested_at timestamptz not null default now(),
 erase_after timestamptz not null, legal_hold boolean not null default false,
 state text not null default 'requested' check(state in ('requested','processing','anonymized','erased')),
 lease uuid,leased_until timestamptz,
 check(erase_after>=requested_at+interval '1 year')
);
create table private.data_holds (
 user_id uuid primary key references public.profiles(id),
 reason text not null check(reason in ('dispute','safety','legal','licensing'))
);
revoke all on private.retention_policy,private.account_closures,private.data_holds from public,anon,authenticated;
create function private.account_is_open(p_user uuid) returns boolean language sql stable security definer set search_path='' as $$
 select p_user is not null and not exists(select 1 from private.account_closures where user_id=p_user)
$$;
revoke all on function private.account_is_open(uuid) from public,anon;
grant execute on function private.account_is_open(uuid) to authenticated,service_role;
-- A restrictive policy prevents a still-valid cached JWT reading/writing public
-- tables even if revocation or the Auth ban is delayed. Definer RPCs also need guards.
do $$ declare t record; begin
 for t in select tablename from pg_tables where schemaname='public' and rowsecurity loop
  execute format('create policy closed_account_boundary on public.%I as restrictive for all to authenticated using (private.account_is_open((select auth.uid()))) with check (private.account_is_open((select auth.uid())))',t.tablename);
 end loop;
end $$;
create or replace function private.is_admin() returns boolean language sql stable security definer set search_path='' as $$
 select private.account_is_open((select auth.uid())) and exists(select 1 from public.profiles where id=(select auth.uid()) and role='admin')
$$;
create function private.guard_closed_account_write() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if (select auth.uid()) is not null and not private.account_is_open((select auth.uid())) then
  raise exception 'account is closed' using errcode='42501';
 end if;
 if tg_table_name='trips' then
 if tg_op='INSERT' or new.status in ('requesting','accepted','arrived','in_progress') then
  -- Same profile lock as closure prevents booking/acceptance racing closure.
  perform 1 from public.profiles where id in (new.passenger_id,new.rider_id) order by id for update;
  if not private.account_is_open(new.passenger_id) or (new.rider_id is not null and not private.account_is_open(new.rider_id)) then
   raise exception 'trip participant account is closed' using errcode='42501';
  end if;
 end if;
 end if;
 return new;
end $$;
-- Trigger every mutable client/RPC data surface, including functions bypassing RLS.
do $$ declare name text; begin
 foreach name in array array['profiles','motorcycles','rider_documents','trips','trip_locations','device_push_tokens','trip_ratings'] loop
  execute format('create trigger guard_closed_account_write before insert or update on public.%I for each row execute function private.guard_closed_account_write()',name);
 end loop;
end $$;
revoke all on function private.guard_closed_account_write() from public,anon,authenticated;
create policy closed_account_boundary on storage.objects as restrictive for all to authenticated
 using (private.account_is_open((select auth.uid()))) with check (private.account_is_open((select auth.uid())));


create function public.request_my_account_deletion() returns timestamptz language plpgsql security definer set search_path='' as $$
declare actor uuid:=(select auth.uid());deadline timestamptz;
begin
 if actor is null then raise exception 'authentication required' using errcode='42501'; end if;
 perform 1 from public.profiles where id=actor for update;
 if not found then raise exception 'profile required' using errcode='42501'; end if;
 select erase_after into deadline from private.account_closures where user_id=actor;
 if found then return deadline; end if;
 if exists(select 1 from public.trips where (passenger_id=actor or rider_id=actor) and status in ('requesting','accepted','arrived','in_progress')) then
  raise exception 'finish or cancel active trips before closing account' using errcode='55000';
 end if;
 -- Update before closure is recorded so the trigger permits self-deactivation.
 update public.profiles set is_online=false where id=actor;
 delete from public.device_push_tokens where user_id=actor;
 delete from private.pilot_rider_presence where rider_id=actor;
 update private.push_outbox set state='cancelled',fcm_token='',lease=null,leased_until=null where recipient_id=actor and state in ('pending','sending');
 select greatest(now()+account_retention,now()+trip_retention,now()+coalesce(location_retention,account_retention)) into deadline from private.retention_policy where id;
 insert into private.account_closures(user_id,erase_after) values(actor,deadline);
 return deadline;
end $$;
revoke all on function public.request_my_account_deletion() from public,anon;
grant execute on function public.request_my_account_deletion() to authenticated;

create function public.claim_account_erasure() returns table(user_id uuid,lease uuid,state text)
language plpgsql security definer set search_path='' as $$
begin
 perform 1 from private.retention_policy where id for share;
 return query with target as (
  select c.user_id from private.account_closures c,private.retention_policy p
  where p.id and p.approved and c.erase_after<=now()
   and greatest(c.requested_at+p.account_retention,c.requested_at+p.trip_retention,c.requested_at+coalesce(p.location_retention,p.account_retention))<=now() and not c.legal_hold
   and not exists(select 1 from private.data_holds h where h.user_id=c.user_id)
   and not exists(select 1 from public.pilot_cancellation_reviews r join public.trips t on t.id=r.trip_id where r.review_required and r.reviewed_at is null and (t.passenger_id=c.user_id or t.rider_id=c.user_id))
   and c.state<>'erased' and (c.leased_until is null or c.leased_until<now())
   and not exists(select 1 from public.trips t where (t.passenger_id=c.user_id or t.rider_id=c.user_id) and t.status in ('requesting','accepted','arrived','in_progress'))
  order by c.erase_after for update of c skip locked limit 1
 ) update private.account_closures c set lease=gen_random_uuid(),leased_until=now()+interval '10 minutes',state=case when c.state='anonymized' then c.state else 'processing' end
 from target t where c.user_id=t.user_id returning c.user_id,c.lease,c.state;
end $$;
create function public.finalize_account_erasure(p_user_id uuid,p_lease uuid) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform 1 from private.account_closures where user_id=p_user_id and lease=p_lease and leased_until>now() and state in ('processing','anonymized') and not legal_hold for update;
 if not found or exists(select 1 from private.data_holds where user_id=p_user_id) then return false; end if;
 -- Worker must remove actual Storage bytes through the API BEFORE this call.
 if exists(select 1 from storage.objects where bucket_id='rider-documents' and name like p_user_id::text||'/%') then
  raise exception 'private object bytes must be deleted through Storage first' using errcode='55000';
 end if;
 delete from public.rider_documents where rider_id=p_user_id;
 delete from public.motorcycles where rider_id=p_user_id;
 delete from public.device_push_tokens where user_id=p_user_id;
 delete from private.pilot_rider_presence where rider_id=p_user_id;
 delete from public.trip_locations where rider_id=p_user_id or trip_id in (select id from public.trips where passenger_id=p_user_id);
 -- Remove free-text rating comments; keep aggregate/accounting and audit tombstones.
 update public.trip_ratings set comment=null where rater_id=p_user_id or rated_user_id=p_user_id;
 update public.trips set pickup_lat=0,pickup_lng=0,dropoff_lat=0,dropoff_lng=0,pickup_address='Removed',dropoff_address='Removed',trip_pin_hash=null
 where passenger_id=p_user_id or rider_id=p_user_id;
 update public.profiles set full_name='Deleted account',phone_number=replace(p_user_id::text,'-',''),is_online=false where id=p_user_id;
 update private.account_closures set state='anonymized' where user_id=p_user_id;
 return true;
end $$;
create function public.complete_account_erasure(p_user_id uuid,p_lease uuid) returns boolean language plpgsql security definer set search_path='' as $$
begin
 if exists(select 1 from auth.users where id=p_user_id) then return false; end if;
 update private.account_closures set state='erased',lease=null,leased_until=null where user_id=p_user_id and lease=p_lease and state='anonymized';
 return found;
end $$;
alter table public.trips add column retention_ended_at timestamptz;
update public.trips set retention_ended_at=greatest(created_at,coalesce(completed_at,now())) where status in ('completed','cancelled');
create function private.record_trip_retention_end() returns trigger language plpgsql set search_path='' as $$
begin
 if new.status in ('completed','cancelled') and old.status not in ('completed','cancelled') then new.retention_ended_at:=now();
 else new.retention_ended_at:=old.retention_ended_at; end if;
 return new;
end $$;
create trigger record_trip_retention_end before update on public.trips for each row execute function private.record_trip_retention_end();
revoke all on function private.record_trip_retention_end() from public,anon,authenticated;
create function public.run_retention_batch() returns jsonb language plpgsql security definer set search_path='' as $$
declare n int;trips_deleted int;begin
 if not exists(select 1 from private.retention_policy where id and approved) then return jsonb_build_object('approved',false,'locations_deleted',0,'trips_deleted',0); end if;
 delete from private.pilot_rider_presence where recorded_at<now()-interval '2 minutes';
 delete from private.push_outbox where state in ('sent','dead','cancelled') and created_at<now()-interval '1 year';
 with candidates as (
 select l.id from public.trip_locations l join public.trips t on t.id=l.trip_id,private.retention_policy p
 where p.id and p.location_retention is not null and t.status in ('completed','cancelled')
  and t.retention_ended_at<now()-p.location_retention
  and not exists(select 1 from public.pilot_cancellation_reviews r where r.trip_id=t.id and r.review_required and r.reviewed_at is null)
  and not exists(select 1 from private.data_holds h where h.user_id in (t.passenger_id,t.rider_id))
  and not exists(select 1 from private.account_closures c where c.user_id in (t.passenger_id,t.rider_id) and c.legal_hold)
 order by l.id limit 1000
 ) delete from public.trip_locations l using candidates c where l.id=c.id;
 get diagnostics n=row_count;
 -- A pending cancellation dispute is a continuing purpose: never purge it.
 create temporary table if not exists retention_trip_batch(id uuid primary key) on commit drop;
 truncate retention_trip_batch;
 insert into retention_trip_batch
 select t.id from public.trips t,private.retention_policy p where p.id
  and t.status in ('completed','cancelled') and t.retention_ended_at<now()-p.trip_retention
  and not exists(select 1 from private.data_holds h where h.user_id in (t.passenger_id,t.rider_id))
  and not exists(select 1 from private.account_closures c where c.user_id in (t.passenger_id,t.rider_id) and c.legal_hold)
  and not exists(select 1 from public.pilot_cancellation_reviews r where r.trip_id=t.id and r.review_required and r.reviewed_at is null)
 order by t.retention_ended_at limit 100 for update of t skip locked;
 delete from public.pilot_cancellation_reviews where trip_id in (select id from retention_trip_batch);
 delete from public.trips where id in (select id from retention_trip_batch);
 get diagnostics trips_deleted=row_count;
 return jsonb_build_object('approved',true,'locations_deleted',n,'trips_deleted',trips_deleted);
end $$;
revoke all on function public.claim_account_erasure(),public.finalize_account_erasure(uuid,uuid),public.complete_account_erasure(uuid,uuid),public.run_retention_batch() from public,anon,authenticated;
grant execute on function public.claim_account_erasure(),public.finalize_account_erasure(uuid,uuid),public.complete_account_erasure(uuid,uuid),public.run_retention_batch() to service_role;
create function private.guard_erasure_hold() returns trigger language plpgsql security definer set search_path='' as $$
begin
 perform 1 from private.account_closures where user_id=new.user_id for update;
 if exists(select 1 from private.account_closures c where c.user_id=new.user_id and c.state in ('processing','anonymized') and c.leased_until>now()) then
  raise exception 'pause worker and wait for erasure lease before changing a hold' using errcode='55000';
 end if;
 return new;
end $$;
create trigger guard_erasure_hold before insert or update on private.data_holds for each row execute function private.guard_erasure_hold();
create trigger guard_erasure_hold before update of legal_hold on private.account_closures for each row when (new.legal_hold is distinct from old.legal_hold) execute function private.guard_erasure_hold();
revoke all on function private.guard_erasure_hold() from public,anon,authenticated;
create function private.guard_retention_policy_change() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if exists(select 1 from private.account_closures where state in ('processing','anonymized') and leased_until>now()) then
  raise exception 'pause worker and wait for active erasure leases before changing policy' using errcode='55000';
 end if;
 return new;
end $$;
create trigger guard_retention_policy_change before update on private.retention_policy for each row execute function private.guard_retention_policy_change();
revoke all on function private.guard_retention_policy_change() from public,anon,authenticated;
commit;
