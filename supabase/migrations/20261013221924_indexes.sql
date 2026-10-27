begin;

create index if not exists profiles_role_idx
  on public.profiles (role);

create index if not exists profiles_online_riders_idx
  on public.profiles (id)
  where role = 'rider' and is_online = true;

create index if not exists motorcycles_rider_id_idx
  on public.motorcycles (rider_id);

create index if not exists rider_documents_rider_status_submitted_idx
  on public.rider_documents (rider_id, verification_status, submitted_at desc);

create index if not exists trips_passenger_created_idx
  on public.trips (passenger_id, created_at desc, id);

create index if not exists trips_rider_created_idx
  on public.trips (rider_id, created_at desc, id)
  where rider_id is not null;

create index if not exists trips_status_created_idx
  on public.trips (status, created_at desc, id);

create index if not exists trips_active_rider_status_idx
  on public.trips (rider_id, status, created_at desc)
  where status in ('accepted', 'arrived', 'in_progress');

create index if not exists trips_requesting_created_idx
  on public.trips (created_at, id)
  where status = 'requesting';

-- These support inexpensive rectangular service-area searches without requiring
-- PostGIS. A future dispatch/current-rider-location table can add GiST geography
-- indexes when nearest-rider search is implemented.
create index if not exists trips_pickup_coordinates_idx
  on public.trips (pickup_lat, pickup_lng);

create index if not exists trips_dropoff_coordinates_idx
  on public.trips (dropoff_lat, dropoff_lng);

-- Primary tracking read path: locations for one trip in chronological order.
create index if not exists trip_locations_trip_recorded_idx
  on public.trip_locations (trip_id, recorded_at desc, id desc)
  include (rider_id, lat, lng);

create index if not exists trip_locations_rider_recorded_idx
  on public.trip_locations (rider_id, recorded_at desc, id desc);

-- BRIN stays small and is appropriate for time-correlated append-only writes.
create index if not exists trip_locations_recorded_brin_idx
  on public.trip_locations using brin (recorded_at)
  with (pages_per_range = 32);

commit;
