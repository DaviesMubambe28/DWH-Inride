begin;
alter table public.trips add column accepted_at timestamptz, add column arrived_at timestamptz;
create function private.stamp_pilot_trip_times()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if old.status = 'requesting' and new.status = 'accepted' then new.accepted_at := clock_timestamp(); end if;
  if old.status = 'accepted' and new.status = 'arrived' then new.arrived_at := clock_timestamp(); end if;
  return new;
end;
$$;
revoke all on function private.stamp_pilot_trip_times() from public, anon, authenticated;
-- Runs after the existing client-transition guard; timestamps are server assigned.
create trigger zz_stamp_pilot_trip_times before update of status on public.trips
for each row execute function private.stamp_pilot_trip_times();

create table public.pilot_cancellation_reviews (
  trip_id uuid primary key references public.trips(id),
  actor_id uuid not null references public.profiles(id),
  reason text not null check (reason in ('passenger_cancelled', 'passenger_no_show', 'motorcycle_problem', 'safety_concern')),
  late_warning boolean not null default false,
  review_required boolean not null default false,
  created_at timestamptz not null default now(),
  reviewed_by uuid references public.profiles(id),
  reviewed_at timestamptz,
  check ((reviewed_at is null) = (reviewed_by is null))
);
alter table public.pilot_cancellation_reviews enable row level security;
revoke all on public.pilot_cancellation_reviews from public, anon, authenticated;
grant select on public.pilot_cancellation_reviews to authenticated;
create policy cancellation_reviews_admin_read on public.pilot_cancellation_reviews
for select to authenticated using ((select private.is_admin()));

create function public.cancel_pilot_trip(p_trip_id uuid, p_reason text)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  ride public.trips%rowtype;
  late boolean := false;
  needs_review boolean := false;
begin
  if actor is null then raise exception 'authentication required' using errcode = '42501'; end if;
  select * into ride from public.trips where id = p_trip_id for update;
  if not found then return false; end if;
  if ride.passenger_id = actor and private.is_passenger() then
    if p_reason is distinct from 'passenger_cancelled' then
      raise exception 'invalid passenger cancellation reason' using errcode = '22023';
    end if;
    if ride.status not in ('requesting', 'accepted', 'arrived') then return false; end if;
    late := ride.accepted_at is not null and clock_timestamp() > ride.accepted_at + interval '2 minutes';
    needs_review := late or (ride.status <> 'requesting' and ride.accepted_at is null);
  elsif ride.rider_id = actor and private.is_approved_rider() then
    if ride.status not in ('accepted', 'arrived', 'in_progress') then return false; end if;
    if p_reason = 'passenger_no_show' then
      if ride.status <> 'arrived' or ride.arrived_at is null or clock_timestamp() < ride.arrived_at + interval '5 minutes' then
        raise exception 'wait five minutes after arrival before recording passenger no-show' using errcode = '22023';
      end if;
    elsif p_reason in ('motorcycle_problem', 'safety_concern') then needs_review := true;
    else raise exception 'select a valid rider cancellation reason' using errcode = '22023';
    end if;
  else return false;
  end if;
  perform set_config('app.trip_transition_authorized', 'true', true);
  update public.trips set status = 'cancelled' where id = p_trip_id;
  insert into public.pilot_cancellation_reviews(trip_id, actor_id, reason, late_warning, review_required)
  values (p_trip_id, actor, p_reason, late, needs_review);
  -- Warning/review only: no fee, debt, payout deduction, or next-ride surcharge.
  return true;
end;
$$;
create or replace function public.cancel_trip(p_trip_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
begin
  if (select auth.uid()) is null then raise exception 'authentication required' using errcode = '42501'; end if;
  if not private.is_passenger() then return false; end if;
  return public.cancel_pilot_trip(p_trip_id, 'passenger_cancelled');
end;
$$;
create function public.admin_review_pilot_cancellation(p_trip_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
begin
  if not private.is_admin() then raise exception 'admin access required' using errcode = '42501'; end if;
  update public.pilot_cancellation_reviews set reviewed_by = (select auth.uid()), reviewed_at = clock_timestamp()
  where trip_id = p_trip_id and review_required and reviewed_at is null;
  return found;
end;
$$;
revoke all on function public.cancel_pilot_trip(uuid,text), public.admin_review_pilot_cancellation(uuid) from public, anon;
grant execute on function public.cancel_pilot_trip(uuid,text), public.admin_review_pilot_cancellation(uuid) to authenticated;
commit;
