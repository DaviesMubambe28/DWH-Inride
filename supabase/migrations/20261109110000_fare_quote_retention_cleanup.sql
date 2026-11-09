begin;

-- Accepted trips and their quotes reference each other. Use SET NULL in both
-- directions so retention and synthetic-fixture cleanup can remove either
-- record without discarding the immutable pricing history that remains.
alter table private.fare_quotes
  drop constraint if exists fare_quotes_accepted_trip_id_fkey,
  add constraint fare_quotes_accepted_trip_id_fkey
    foreign key (accepted_trip_id) references public.trips(id) on delete set null;

alter table public.trips
  drop constraint if exists trips_fare_quote_id_fkey,
  add constraint trips_fare_quote_id_fkey
    foreign key (fare_quote_id) references private.fare_quotes(id) on delete set null;

commit;
