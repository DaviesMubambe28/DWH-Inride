-- The mobile clients decode these trip fields when refreshing passenger and
-- rider feeds. Keep access column-scoped so the private trip PIN hash remains
-- unavailable to authenticated clients.
grant select (
  id,
  passenger_id,
  rider_id,
  status,
  pickup_lat,
  pickup_lng,
  pickup_address,
  dropoff_lat,
  dropoff_lng,
  dropoff_address,
  fare_amount,
  driver_fare,
  service_fee,
  route_distance_meters,
  route_duration_seconds,
  created_at
) on table public.trips to authenticated;
