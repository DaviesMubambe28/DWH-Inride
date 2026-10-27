begin;

-- Available requests are visible only while unassigned. Keep the rider's approval
-- and online checks in PostgreSQL; the mobile filters are not authorization.
drop policy if exists trips_select_available_for_approved_rider on public.trips;
create policy trips_select_available_for_approved_rider
on public.trips
for select
to authenticated
using (
  status = 'requesting'
  and rider_id is null
  and (select private.is_approved_rider())
  and exists (
    select 1
    from public.profiles as p
    where p.id = (select auth.uid())
      and p.is_online
  )
);

commit;
