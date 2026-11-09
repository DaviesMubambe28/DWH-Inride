begin;

create table public.rider_service_fee_payments (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.profiles(id),
  amount_zmw numeric(12,2) not null check (amount_zmw > 0),
  transaction_reference text not null check (char_length(btrim(transaction_reference)) between 4 and 120),
  proof_storage_path text check (proof_storage_path is null or char_length(proof_storage_path) between 3 and 500),
  status text not null default 'pending_verification' check (status in ('pending_verification', 'verified', 'rejected')),
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references public.profiles(id),
  admin_note text check (admin_note is null or char_length(btrim(admin_note)) between 3 and 1000),
  constraint service_fee_payment_review_complete check (
    (status = 'pending_verification' and reviewed_at is null and reviewed_by is null)
    or (status in ('verified', 'rejected') and reviewed_at is not null and reviewed_by is not null)
  )
);

create unique index rider_service_fee_payment_reference_unique
on public.rider_service_fee_payments(rider_id, lower(btrim(transaction_reference)));
create index rider_service_fee_payments_status_idx
on public.rider_service_fee_payments(status, submitted_at desc);

create table public.rider_service_fee_ledger (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.profiles(id),
  entry_type text not null check (entry_type in ('trip_charge', 'verified_payment')),
  amount_zmw numeric(12,2) not null check (amount_zmw > 0),
  trip_id uuid references public.trips(id),
  payment_id uuid references public.rider_service_fee_payments(id),
  created_at timestamptz not null default now(),
  constraint service_fee_ledger_source check (
    (entry_type = 'trip_charge' and trip_id is not null and payment_id is null)
    or (entry_type = 'verified_payment' and trip_id is null and payment_id is not null)
  )
);

create unique index rider_service_fee_one_charge_per_trip
on public.rider_service_fee_ledger(trip_id) where trip_id is not null;
create unique index rider_service_fee_one_credit_per_payment
on public.rider_service_fee_ledger(payment_id) where payment_id is not null;
create index rider_service_fee_ledger_rider_created_idx
on public.rider_service_fee_ledger(rider_id, created_at desc);

create table private.service_fee_payment_settings (
  singleton boolean primary key default true check (singleton),
  payment_method text,
  payment_recipient text,
  payment_instructions text,
  updated_at timestamptz not null default now(),
  updated_by uuid references public.profiles(id),
  constraint service_fee_settings_complete check (
    (payment_method is null and payment_recipient is null and payment_instructions is null)
    or (char_length(btrim(payment_method)) between 2 and 80
        and char_length(btrim(payment_recipient)) between 4 and 120
        and char_length(btrim(payment_instructions)) between 10 and 1000)
  )
);
insert into private.service_fee_payment_settings(singleton) values (true);

create table private.service_fee_payment_audit (
  id bigint generated always as identity primary key,
  payment_id uuid not null references public.rider_service_fee_payments(id),
  actor_id uuid not null references public.profiles(id),
  action text not null check (action in ('submitted', 'verified', 'rejected')),
  recorded_at timestamptz not null default now()
);

alter table public.rider_service_fee_payments enable row level security;
alter table public.rider_service_fee_payments force row level security;
alter table public.rider_service_fee_ledger enable row level security;
alter table public.rider_service_fee_ledger force row level security;
revoke all on public.rider_service_fee_payments, public.rider_service_fee_ledger from public, anon, authenticated;
revoke all on private.service_fee_payment_settings, private.service_fee_payment_audit from public, anon, authenticated;
grant select on public.rider_service_fee_payments, public.rider_service_fee_ledger to authenticated;
grant all on public.rider_service_fee_payments, public.rider_service_fee_ledger to service_role;

create policy service_fee_payments_read on public.rider_service_fee_payments
for select to authenticated using (rider_id = (select auth.uid()) or (select private.is_admin()));
create policy service_fee_ledger_read on public.rider_service_fee_ledger
for select to authenticated using (rider_id = (select auth.uid()) or (select private.is_admin()));

create function private.service_fee_outstanding(p_rider_id uuid)
returns numeric language sql stable security definer set search_path = '' as $$
  select greatest(coalesce(sum(case when entry_type = 'trip_charge' then amount_zmw else -amount_zmw end), 0), 0)
  from public.rider_service_fee_ledger where rider_id = p_rider_id
$$;
revoke all on function private.service_fee_outstanding(uuid) from public, anon, authenticated;

create function private.guard_service_fee_history()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(current_setting('app.service_fee_write_authorized', true), '') <> 'true' then
    raise exception 'service fee records are immutable outside protected operations' using errcode = '42501';
  end if;
  if tg_op = 'DELETE' then return old; end if;
  return new;
end
$$;
revoke all on function private.guard_service_fee_history() from public, anon, authenticated;
create trigger guard_service_fee_ledger before update or delete on public.rider_service_fee_ledger
for each row execute function private.guard_service_fee_history();
create trigger guard_service_fee_payments before update or delete on public.rider_service_fee_payments
for each row execute function private.guard_service_fee_history();

create function public.get_my_service_fee_account()
returns jsonb language sql stable security definer set search_path = '' as $$
  with actor as (select auth.uid() as id),
  totals as (
    select
      count(*) filter (where l.entry_type = 'trip_charge')::integer as completed_trips,
      coalesce(sum(l.amount_zmw) filter (where l.entry_type = 'trip_charge'), 0) as total_fees,
      coalesce(sum(l.amount_zmw) filter (where l.entry_type = 'verified_payment'), 0) as total_paid
    from public.rider_service_fee_ledger l, actor where l.rider_id = actor.id
  ),
  ride_totals as (
    select coalesce(sum(t.fare_amount), 0) as total_ride_fare
    from public.trips t, actor where t.rider_id = actor.id and t.status = 'completed'
  ),
  payments as (
    select coalesce(jsonb_agg(jsonb_build_object(
      'id', p.id, 'amount_zmw', p.amount_zmw, 'transaction_reference', p.transaction_reference,
      'status', p.status, 'submitted_at', p.submitted_at, 'reviewed_at', p.reviewed_at,
      'admin_note', p.admin_note
    ) order by p.submitted_at desc), '[]'::jsonb) as items
    from public.rider_service_fee_payments p, actor where p.rider_id = actor.id
  )
  select case when a.id is null or not exists (
    select 1 from public.profiles where id = a.id and role = 'rider'
  ) then null else jsonb_build_object(
    'completed_trips', totals.completed_trips,
    'total_ride_fare_zmw', ride_totals.total_ride_fare,
    'total_fees_zmw', totals.total_fees,
    'total_paid_zmw', totals.total_paid,
    'outstanding_zmw', greatest(totals.total_fees - totals.total_paid, 0),
    'payment_method', settings.payment_method,
    'payment_recipient', settings.payment_recipient,
    'payment_instructions', settings.payment_instructions,
    'payments', payments.items
  ) end
  from actor a cross join totals cross join ride_totals cross join payments
  cross join private.service_fee_payment_settings settings
$$;
revoke all on function public.get_my_service_fee_account() from public, anon;
grant execute on function public.get_my_service_fee_account() to authenticated;

create function public.submit_service_fee_payment(
  p_amount_zmw numeric,
  p_transaction_reference text,
  p_proof_storage_path text default null
) returns uuid language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  payment_id uuid;
  available numeric;
begin
  if actor is null or not exists (select 1 from public.profiles where id = actor and role = 'rider') then
    raise exception 'rider access required' using errcode = '42501';
  end if;
  if p_amount_zmw is null or p_amount_zmw <= 0 or scale(p_amount_zmw) > 2 then
    raise exception 'payment amount must be a positive ZMW amount with at most two decimal places' using errcode = '22023';
  end if;
  if char_length(btrim(coalesce(p_transaction_reference, ''))) not between 4 and 120 then
    raise exception 'transaction reference must contain 4 to 120 characters' using errcode = '22023';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(actor::text, 8011));
  available := private.service_fee_outstanding(actor) - coalesce((
    select sum(amount_zmw) from public.rider_service_fee_payments
    where rider_id = actor and status = 'pending_verification'
  ), 0);
  if p_amount_zmw > available then
    raise exception 'payment amount exceeds the unsubmitted outstanding balance' using errcode = '22023';
  end if;
  insert into public.rider_service_fee_payments(rider_id, amount_zmw, transaction_reference, proof_storage_path)
  values (actor, p_amount_zmw, btrim(p_transaction_reference), nullif(btrim(p_proof_storage_path), ''))
  returning id into payment_id;
  insert into private.service_fee_payment_audit(payment_id, actor_id, action)
  values (payment_id, actor, 'submitted');
  return payment_id;
exception when unique_violation then
  raise exception 'this transaction reference has already been submitted' using errcode = '23505';
end
$$;
revoke all on function public.submit_service_fee_payment(numeric, text, text) from public, anon;
grant execute on function public.submit_service_fee_payment(numeric, text, text) to authenticated;

create function public.admin_review_service_fee_payment(
  p_payment_id uuid,
  p_decision text,
  p_admin_note text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  payment public.rider_service_fee_payments%rowtype;
  remaining numeric;
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  if p_decision not in ('verified', 'rejected') then
    raise exception 'decision must be verified or rejected' using errcode = '22023';
  end if;
  if char_length(btrim(coalesce(p_admin_note, ''))) not between 3 and 1000 then
    raise exception 'admin note must contain 3 to 1000 characters' using errcode = '22023';
  end if;
  select * into payment from public.rider_service_fee_payments where id = p_payment_id for update;
  if not found then raise exception 'payment submission not found' using errcode = '22023'; end if;
  if payment.status <> 'pending_verification' then
    raise exception 'payment submission has already been reviewed' using errcode = '22023';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(payment.rider_id::text, 8011));
  if p_decision = 'verified' and payment.amount_zmw > private.service_fee_outstanding(payment.rider_id) then
    raise exception 'verified amount exceeds the current outstanding balance' using errcode = '22023';
  end if;
  perform set_config('app.service_fee_write_authorized', 'true', true);
  update public.rider_service_fee_payments set status = p_decision, reviewed_at = now(),
    reviewed_by = actor, admin_note = btrim(p_admin_note) where id = payment.id;
  if p_decision = 'verified' then
    insert into public.rider_service_fee_ledger(rider_id, entry_type, amount_zmw, payment_id)
    values (payment.rider_id, 'verified_payment', payment.amount_zmw, payment.id);
  end if;
  insert into private.service_fee_payment_audit(payment_id, actor_id, action)
  values (payment.id, actor, p_decision);
  remaining := private.service_fee_outstanding(payment.rider_id);
  return jsonb_build_object('status', p_decision, 'remaining_outstanding_zmw', remaining);
end
$$;
revoke all on function public.admin_review_service_fee_payment(uuid, text, text) from public, anon;
grant execute on function public.admin_review_service_fee_payment(uuid, text, text) to authenticated;

create function public.admin_update_service_fee_payment_config(
  p_payment_method text,
  p_payment_recipient text,
  p_payment_instructions text
) returns boolean language plpgsql security definer set search_path = '' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not private.is_admin() then
    raise exception 'admin access required' using errcode = '42501';
  end if;
  update private.service_fee_payment_settings set
    payment_method = btrim(p_payment_method), payment_recipient = btrim(p_payment_recipient),
    payment_instructions = btrim(p_payment_instructions), updated_at = now(), updated_by = actor
  where singleton;
  return true;
end
$$;
revoke all on function public.admin_update_service_fee_payment_config(text, text, text) from public, anon;
grant execute on function public.admin_update_service_fee_payment_config(text, text, text) to authenticated;

create or replace function public.complete_trip(p_trip_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  actor_id uuid := (select auth.uid());
  completed_trip public.trips%rowtype;
begin
  if actor_id is null or not private.is_rider() then
    raise exception 'only the assigned driver can complete trips' using errcode = '42501';
  end if;
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips set status = 'completed', completed_at = now()
  where id = p_trip_id and rider_id = actor_id and status = 'in_progress'
  returning * into completed_trip;
  if not found then return false; end if;
  if completed_trip.service_fee is null or completed_trip.service_fee <= 0 then
    raise exception 'completed trip has no valid service fee' using errcode = '22023';
  end if;
  insert into public.rider_service_fee_ledger(rider_id, entry_type, amount_zmw, trip_id)
  values (actor_id, 'trip_charge', completed_trip.service_fee, completed_trip.id)
  on conflict (trip_id) where trip_id is not null do nothing;
  update public.profiles set total_trips = total_trips + 1
  where id in (actor_id, completed_trip.passenger_id);
  return true;
end
$$;
revoke all on function public.complete_trip(uuid) from public, anon;
grant execute on function public.complete_trip(uuid) to authenticated;

create view public.admin_service_fee_overview with (security_barrier = true) as
select p.id as rider_id, p.full_name, p.phone_number, p.total_trips,
  count(l.id) filter (where l.entry_type = 'trip_charge')::integer as fee_trip_count,
  coalesce(sum(l.amount_zmw) filter (where l.entry_type = 'trip_charge'), 0) as total_fees_zmw,
  coalesce(sum(l.amount_zmw) filter (where l.entry_type = 'verified_payment'), 0) as total_paid_zmw,
  greatest(coalesce(sum(case when l.entry_type = 'trip_charge' then l.amount_zmw else -l.amount_zmw end), 0), 0) as outstanding_zmw,
  (select count(*)::integer from public.rider_service_fee_payments sp where sp.rider_id = p.id and sp.status = 'pending_verification') as pending_payments,
  (select max(sp.reviewed_at) from public.rider_service_fee_payments sp where sp.rider_id = p.id and sp.status = 'verified') as last_payment_at
from public.profiles p left join public.rider_service_fee_ledger l on l.rider_id = p.id
where p.role = 'rider' and (select private.is_admin())
group by p.id, p.full_name, p.phone_number, p.total_trips;
revoke all on public.admin_service_fee_overview from public, anon;
grant select on public.admin_service_fee_overview to authenticated;

create view public.admin_service_fee_payment_config with (security_barrier = true) as
select payment_method, payment_recipient, payment_instructions, updated_at
from private.service_fee_payment_settings where singleton and (select private.is_admin());
revoke all on public.admin_service_fee_payment_config from public, anon;
grant select on public.admin_service_fee_payment_config to authenticated;

do $$ begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'rider_service_fee_payments') then
    alter publication supabase_realtime add table public.rider_service_fee_payments;
  end if;
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'rider_service_fee_ledger') then
    alter publication supabase_realtime add table public.rider_service_fee_ledger;
  end if;
end $$;

comment on table public.rider_service_fee_ledger is
  'Immutable ZMW service-fee debits and verified-payment credits. This migration deliberately does not charge historical trips retroactively.';

commit;
