begin;

-- The protected payment RPCs use a two-party acknowledgement for cash as well
-- as external mobile money. The older trigger admitted passenger acknowledgement
-- only for mobile money, making the current cash journey impossible to finish.
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
  if actor_id is null then
    return new;
  end if;
  if old.status <> 'completed' then
    raise exception 'payment may only be confirmed after trip completion'
      using errcode = '23514';
  end if;

  if actor_id = old.passenger_id
     and old.payment_method in ('cash', 'mobile_money_external')
     and old.payment_status = 'unpaid'
     and new.payment_status = 'passenger_marked_paid' then
    new.passenger_marked_paid_at := now();
    return new;
  end if;

  if actor_id = old.rider_id
     and (select private.is_approved_rider())
     and old.payment_method in ('cash', 'mobile_money_external')
     and old.payment_status = 'passenger_marked_paid'
     and new.payment_status = 'rider_confirmed' then
    new.rider_confirmed_paid_at := now();
    return new;
  end if;

  raise exception 'payment confirmation transition is not allowed'
    using errcode = '42501';
end;
$$;
revoke all on function private.enforce_external_payment_confirmation() from public, anon, authenticated;

commit;
