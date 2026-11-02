begin;
create table private.worker_health (
 worker text primary key check(worker in ('notifications','data-lifecycle')),
 last_run timestamptz not null default now(), last_success timestamptz,
 processed int not null default 0 check(processed>=0),failed int not null default 0 check(failed>=0)
);
revoke all on private.worker_health from public,anon,authenticated;
create function public.record_operations_run(p_worker text,p_processed int,p_failed int) returns void language sql security definer set search_path='' as $$
 insert into private.worker_health(worker,last_run,last_success,processed,failed)
 values(p_worker,now(),case when p_failed=0 then now() else null end,p_processed,p_failed)
 on conflict(worker) do update set last_run=excluded.last_run,last_success=coalesce(excluded.last_success,private.worker_health.last_success),processed=excluded.processed,failed=excluded.failed
$$;
create function public.operations_health() returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object(
  'push_pending',(select count(*) from private.push_outbox where state in ('pending','sending')),
  'push_dead',(select count(*) from private.push_outbox where state='dead'),
  'push_oldest_pending_at',(select min(created_at) from private.push_outbox where state in ('pending','sending')),
  'expired_push_leases',(select count(*) from private.push_outbox where state='sending' and leased_until<now()),
  'closure_pending',(select count(*) from private.account_closures where state<>'erased'),
  'retention_approved',(select approved from private.retention_policy where id),
  'workers',coalesce((select jsonb_agg(to_jsonb(h)) from private.worker_health h),'[]'::jsonb))
$$;
revoke all on function public.record_operations_run(text,int,int),public.operations_health() from public,anon,authenticated;
grant execute on function public.record_operations_run(text,int,int),public.operations_health() to service_role;
commit;
