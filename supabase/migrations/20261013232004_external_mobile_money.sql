begin;

alter table public.trips
  drop constraint if exists trips_cash_only;

alter table public.trips
  add constraint trips_payment_method_supported
  check (payment_method in ('cash', 'mobile_money_external'));

alter table public.trips
  add column if not exists payment_status text not null default 'unpaid',
  add column if not exists passenger_marked_paid_at timestamptz,
  add column if not exists rider_confirmed_paid_at timestamptz;

alter table public.trips
  drop constraint if exists trips_payment_status_supported;

alter table public.trips
  add constraint trips_payment_status_supported
  check (payment_status in ('unpaid', 'passenger_marked_paid', 'rider_confirmed', 'disputed'));

comment on column public.trips.payment_method is
  'cash or mobile_money_external. DHW-inRide never handles the external mobile-money transfer.';

comment on column public.trips.payment_status is
  'Tracks participant acknowledgements only; it is not proof from a mobile-money provider.';

grant select (
  payment_status,
  passenger_marked_paid_at,
  rider_confirmed_paid_at
) on table public.trips to authenticated;

grant update (payment_status) on table public.trips to authenticated;

drop policy if exists trips_insert_passenger_request on public.trips;
create policy trips_insert_passenger_request
on public.trips
for insert
to authenticated
with check (
  passenger_id = (select auth.uid())
  and rider_id is null
  and status = 'requesting'
  and fare_amount is null
  and trip_pin_hash is null
  and payment_method in ('cash', 'mobile_money_external')
  and payment_status = 'unpaid'
  and passenger_marked_paid_at is null
  and rider_confirmed_paid_at is null
  and started_at is null
  and completed_at is null
);

create or replace function private.enforce_client_trip_status_transition()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  -- Let payment-only updates continue to their dedicated payment trigger.
  if new.status is not distinct from old.status then
    return new;
  end if;

  -- Trusted database and service-role operations do not carry a user ID.
  if actor_id is null then
    return new;
  end if;

  if actor_id = old.passenger_id then
    if new.status <> 'cancelled'
       or old.status not in ('requesting', 'accepted', 'arrived') then
      raise exception 'passenger is not allowed to perform this trip transition'
        using errcode = '42501';
    end if;
    return new;
  end if;

  if actor_id = old.rider_id and (select private.is_rider()) then
    if not (old.status = 'accepted' and new.status = 'arrived') then
      raise exception 'rider is not allowed to perform this trip transition'
        using errcode = '42501';
    end if;
    return new;
  end if;

  raise exception 'user is not a participant in this trip'
    using errcode = '42501';
end;
$$;

create or replace function private.enforce_external_payment_confirmation()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
begin
  if new.payment_status is not distinct from old.payment_status then
    return new;
  end if;

  -- Trusted server operations remain responsible for disputes and corrections.
  if actor_id is null then
    return new;
  end if;

  if old.status <> 'completed' then
    raise exception 'payment may only be confirmed after trip completion'
      using errcode = '23514';
  end if;

  if actor_id = old.passenger_id
     and old.payment_method = 'mobile_money_external'
     and old.payment_status = 'unpaid'
     and new.payment_status = 'passenger_marked_paid' then
    new.passenger_marked_paid_at := now();
    return new;
  end if;

  if actor_id = old.rider_id
     and (select private.is_rider())
     and new.payment_status = 'rider_confirmed'
     and (
       (old.payment_method = 'cash' and old.payment_status = 'unpaid')
       or
       (old.payment_method = 'mobile_money_external' and old.payment_status = 'passenger_marked_paid')
     ) then
    new.rider_confirmed_paid_at := now();
    return new;
  end if;

  raise exception 'payment confirmation transition is not allowed'
    using errcode = '42501';
end;
$$;

revoke all on function private.enforce_external_payment_confirmation()
from public, anon, authenticated;

drop trigger if exists enforce_external_payment_confirmation on public.trips;
create trigger enforce_external_payment_confirmation
before update of payment_status on public.trips
for each row
execute function private.enforce_external_payment_confirmation();

create index if not exists trips_unreconciled_completed_idx
on public.trips (completed_at desc, payment_method, payment_status)
where status = 'completed' and payment_status <> 'rider_confirmed';

commit;
