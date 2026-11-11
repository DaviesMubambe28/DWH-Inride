begin;

create or replace function public.create_official_fare_quote(
  p_passenger_id uuid,
  p_pickup_lat numeric, p_pickup_lng numeric, p_pickup_address text,
  p_dropoff_lat numeric, p_dropoff_lng numeric, p_dropoff_address text,
  p_route_distance_meters integer, p_route_duration_seconds integer
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  created record;
  quote private.fare_quotes%rowtype;
  current_pricing_version bigint;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'trusted quote service required' using errcode = '42501';
  end if;
  if not exists (select 1 from public.profiles where id = p_passenger_id and role = 'passenger') then
    raise exception 'passenger profile is required' using errcode = '42501';
  end if;
  if not private.pilot_service_open() then
    raise exception 'PILOT_UNAVAILABLE' using errcode = 'P0001';
  end if;
  if p_pickup_lat is null or p_pickup_lng is null or p_dropoff_lat is null or p_dropoff_lng is null
    or not private.pilot_point_allowed(p_pickup_lat::double precision, p_pickup_lng::double precision)
    or not private.pilot_point_allowed(p_dropoff_lat::double precision, p_dropoff_lng::double precision) then
    raise exception 'PILOT_OUTSIDE_SERVICE_AREA' using errcode = 'P0001';
  end if;
  if p_route_distance_meters is null or p_route_distance_meters <= 0 or p_route_distance_meters > 100000
    or p_route_duration_seconds is null or p_route_duration_seconds <= 0 or p_route_duration_seconds > 86400
    or coalesce(length(btrim(p_pickup_address)), 0) not between 1 and 300
    or coalesce(length(btrim(p_dropoff_address)), 0) not between 1 and 300 then
    raise exception 'VALIDATED_ROUTE_REQUIRED' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_passenger_id::text, 9104));
  select pricing_config_version into strict current_pricing_version
  from private.pilot_service_settings where singleton;

  select * into quote
  from private.fare_quotes
  where passenger_id = p_passenger_id
    and accepted_trip_id is null
    and expires_at > clock_timestamp()
    and pricing_version = current_pricing_version
    and pickup_lat = p_pickup_lat and pickup_lng = p_pickup_lng
    and dropoff_lat = p_dropoff_lat and dropoff_lng = p_dropoff_lng
    and pickup_address = btrim(p_pickup_address)
    and dropoff_address = btrim(p_dropoff_address)
  order by created_at desc
  limit 1;

  if quote.id is null then
    select * into strict created from public.create_fare_quote(
      p_passenger_id, p_pickup_lat, p_pickup_lng, p_pickup_address,
      p_dropoff_lat, p_dropoff_lng, p_dropoff_address,
      p_route_distance_meters, p_route_duration_seconds
    );
    select * into strict quote from private.fare_quotes where id = created.quote_id;
  end if;

  return jsonb_build_object(
    'quote_id', quote.id, 'fare_amount', quote.fare_amount,
    'service_fee', quote.service_fee, 'driver_fare', quote.driver_fare,
    'route_distance_meters', quote.route_distance_meters,
    'route_duration_seconds', quote.route_duration_seconds,
    'pricing_mode', quote.pricing_mode, 'pricing_version', quote.pricing_version,
    'expires_at', quote.expires_at, 'fare_breakdown', quote.fare_breakdown
  );
end;
$$;

revoke all on function public.create_official_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)
  from public, anon, authenticated;
grant execute on function public.create_official_fare_quote(uuid,numeric,numeric,text,numeric,numeric,text,integer,integer)
  to service_role;

commit;
