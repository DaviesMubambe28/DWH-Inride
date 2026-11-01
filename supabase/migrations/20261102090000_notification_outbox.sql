begin;
create table private.push_outbox (
 id uuid primary key default gen_random_uuid(),
 trip_id uuid not null references public.trips(id) on delete cascade,
 recipient_id uuid not null references public.profiles(id) on delete cascade,
 fcm_token text not null,
 kind text not null check(kind in ('offer','trip_update')),
 event_key text not null,
 state text not null default 'pending' check(state in ('pending','sending','sent','dead','cancelled')),
 attempts int not null default 0,
 next_attempt_at timestamptz not null default now(),
 lease uuid, leased_until timestamptz,
 created_at timestamptz not null default now(),
 unique(event_key,fcm_token)
);
create index push_outbox_pending_idx on private.push_outbox(next_attempt_at) where state in ('pending','sending');
revoke all on private.push_outbox from public,anon,authenticated;

create function private.enqueue_trip_push() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if tg_op='UPDATE' and new.status is not distinct from old.status then return new; end if;
 if tg_op='INSERT' and new.status='requesting' then
   insert into private.push_outbox(trip_id,recipient_id,fcm_token,kind,event_key)
   select new.id,p.id,d.fcm_token,'offer',new.id::text||':offer'
   from public.profiles p join public.device_push_tokens d on d.user_id=p.id and d.app_id='zm.co.dhwinride.rider'
   join private.pilot_rider_presence r on r.rider_id=p.id
   where p.role='rider' and p.approval_status='approved' and p.is_online
    and private.pilot_service_open() and r.recorded_at>=now()-interval '2 minutes'
    and private.pilot_point_allowed(r.latitude,r.longitude)
   on conflict do nothing;
 elsif tg_op='UPDATE' then
   insert into private.push_outbox(trip_id,recipient_id,fcm_token,kind,event_key)
   select new.id,d.user_id,d.fcm_token,'trip_update',new.id::text||':'||new.status::text
   from public.device_push_tokens d where (d.user_id=new.passenger_id and d.app_id='zm.co.dhwinride.passenger')
    or (d.user_id=new.rider_id and d.app_id='zm.co.dhwinride.rider')
   on conflict do nothing;
 end if;
 return new;
end $$;
create trigger enqueue_trip_push after insert or update of status on public.trips for each row execute function private.enqueue_trip_push();
revoke all on function private.enqueue_trip_push() from public,anon,authenticated;

create function public.claim_push_deliveries(p_limit int default 20)
returns table(id uuid,lease uuid,trip_id uuid,kind text,fcm_token text,attempts int)
language plpgsql security definer set search_path='' as $$
begin
 -- Never send a token after logout/reassignment, or stale offers after assignment/pause.
 update private.push_outbox q set state='cancelled',fcm_token=''
 where q.state in ('pending','sending') and (q.leased_until is null or q.leased_until<now()) and (
  not exists(select 1 from public.device_push_tokens d where d.fcm_token=q.fcm_token and d.user_id=q.recipient_id)
  or (q.kind='offer' and (q.created_at<now()-interval '2 minutes' or not private.pilot_service_open()
   or not exists(select 1 from public.trips t where t.id=q.trip_id and t.status='requesting')
   or not exists(select 1 from public.profiles p join private.pilot_rider_presence r on r.rider_id=p.id
      where p.id=q.recipient_id and p.role='rider' and p.approval_status='approved' and p.is_online
       and r.recorded_at>=now()-interval '2 minutes' and private.pilot_point_allowed(r.latitude,r.longitude)))));
 update private.push_outbox q set state='dead',fcm_token='' where q.state in ('pending','sending') and q.attempts>=6 and (q.leased_until is null or q.leased_until<now());
 return query
 with candidates as (
  select q.id from private.push_outbox q where q.attempts<6 and q.next_attempt_at<=now()
   and (q.state='pending' or (q.state='sending' and q.leased_until<now()))
   order by q.next_attempt_at for update skip locked limit greatest(0,least(coalesce(p_limit,20),50))
 ) update private.push_outbox q set state='sending',lease=gen_random_uuid(),leased_until=now()+interval '2 minutes',attempts=q.attempts+1
 from candidates c where q.id=c.id returning q.id,q.lease,q.trip_id,q.kind,q.fcm_token,q.attempts;
end $$;

create function public.finish_push_delivery(p_id uuid,p_lease uuid,p_outcome text,p_delay int)
returns boolean language plpgsql security definer set search_path='' as $$
declare target private.push_outbox%rowtype;
begin
 if p_outcome not in ('sent','retry','configuration','dead','invalid_token','permanent') then raise exception 'invalid outcome' using errcode='22023'; end if;
 select * into target from private.push_outbox where id=p_id and lease=p_lease and state='sending' for update;
 if not found then return false; end if;
 if p_outcome='invalid_token' then
  delete from public.device_push_tokens where fcm_token=target.fcm_token and user_id=target.recipient_id;
 end if;
 update private.push_outbox set state=case when p_outcome='sent' then 'sent' when p_outcome in ('retry','configuration') and attempts<6 then 'pending' else 'dead' end,
  next_attempt_at=now()+make_interval(secs=>greatest(30,least(coalesce(p_delay,30),3600))),lease=null,leased_until=null,
  fcm_token=case when p_outcome in ('retry','configuration') and attempts<6 then fcm_token else '' end
 where id=p_id;
 return true;
end $$;
revoke all on function public.claim_push_deliveries(int),public.finish_push_delivery(uuid,uuid,text,int) from public,anon,authenticated;
grant execute on function public.claim_push_deliveries(int),public.finish_push_delivery(uuid,uuid,text,int) to service_role;
commit;
