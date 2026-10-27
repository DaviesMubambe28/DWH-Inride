begin;

create schema if not exists extensions;
create schema if not exists private;

revoke all on schema private from public;

create extension if not exists pgcrypto with schema extensions;

do $$
begin
  create type public.app_role as enum ('passenger', 'rider', 'admin');
exception
  when duplicate_object then null;
end
$$;

do $$
begin
  create type public.rider_document_type as enum (
    'nrc',
    'license',
    'vehicle_registration'
  );
exception
  when duplicate_object then null;
end
$$;

do $$
begin
  create type public.verification_status as enum (
    'pending',
    'approved',
    'rejected'
  );
exception
  when duplicate_object then null;
end
$$;

do $$
begin
  create type public.trip_status as enum (
    'requesting',
    'accepted',
    'arrived',
    'in_progress',
    'completed',
    'cancelled'
  );
exception
  when duplicate_object then null;
end
$$;

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  role public.app_role not null default 'passenger',
  full_name text not null,
  phone_number text not null unique,
  is_online boolean not null default false,
  rating numeric(3, 2) not null default 0,
  total_trips bigint not null default 0,
  created_at timestamptz not null default now(),
  constraint profiles_full_name_length check (char_length(btrim(full_name)) between 2 and 120),
  constraint profiles_phone_number_length check (char_length(btrim(phone_number)) between 7 and 32),
  constraint profiles_rating_range check (rating between 0 and 5),
  constraint profiles_total_trips_nonnegative check (total_trips >= 0)
);

comment on column public.profiles.role is
  'Authorization role. Client applications cannot insert or update this column.';

create table if not exists public.motorcycles (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.profiles (id) on delete cascade,
  make text not null,
  model text not null,
  year smallint not null,
  plate_number text not null unique,
  color text not null,
  constraint motorcycles_make_length check (char_length(btrim(make)) between 1 and 80),
  constraint motorcycles_model_length check (char_length(btrim(model)) between 1 and 80),
  constraint motorcycles_year_range check (year between 1950 and 2100),
  constraint motorcycles_plate_length check (char_length(btrim(plate_number)) between 2 and 32),
  constraint motorcycles_color_length check (char_length(btrim(color)) between 1 and 40)
);

create table if not exists public.rider_documents (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.profiles (id) on delete cascade,
  document_type public.rider_document_type not null,
  storage_url text not null,
  verification_status public.verification_status not null default 'pending',
  submitted_at timestamptz not null default now(),
  constraint rider_documents_storage_path_not_empty check (char_length(btrim(storage_url)) > 0),
  constraint rider_documents_storage_path_is_private check (storage_url !~* '^https?://')
);

comment on column public.rider_documents.storage_url is
  'Private object path in the rider-documents bucket, not a public or signed URL.';

create table if not exists public.trips (
  id uuid primary key default gen_random_uuid(),
  passenger_id uuid not null references public.profiles (id) on delete restrict,
  rider_id uuid references public.profiles (id) on delete set null,
  status public.trip_status not null default 'requesting',
  pickup_lat numeric(9, 6) not null,
  pickup_lng numeric(10, 6) not null,
  pickup_address text not null,
  dropoff_lat numeric(9, 6) not null,
  dropoff_lng numeric(10, 6) not null,
  dropoff_address text not null,
  fare_amount numeric(12, 2),
  payment_method text not null default 'cash',
  trip_pin_hash text,
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  constraint trips_id_rider_unique unique (id, rider_id),
  constraint trips_pickup_lat_range check (pickup_lat between -90 and 90),
  constraint trips_pickup_lng_range check (pickup_lng between -180 and 180),
  constraint trips_dropoff_lat_range check (dropoff_lat between -90 and 90),
  constraint trips_dropoff_lng_range check (dropoff_lng between -180 and 180),
  constraint trips_pickup_address_not_empty check (char_length(btrim(pickup_address)) > 0),
  constraint trips_dropoff_address_not_empty check (char_length(btrim(dropoff_address)) > 0),
  constraint trips_fare_nonnegative check (fare_amount is null or fare_amount >= 0),
  constraint trips_cash_only check (payment_method = 'cash'),
  constraint trips_completed_after_start check (
    completed_at is null
    or (started_at is not null and completed_at >= started_at)
  ),
  constraint trips_assignment_required_after_request check (
    status in ('requesting', 'cancelled') or rider_id is not null
  )
);

comment on column public.trips.trip_pin_hash is
  'Salted bcrypt hash only. Never expose this column through client grants.';

create table if not exists public.trip_locations (
  id bigint generated always as identity primary key,
  trip_id uuid not null,
  rider_id uuid not null references public.profiles (id) on delete restrict,
  lat numeric(9, 6) not null,
  lng numeric(10, 6) not null,
  recorded_at timestamptz not null default now(),
  constraint trip_locations_trip_rider_fkey
    foreign key (trip_id, rider_id)
    references public.trips (id, rider_id)
    on update cascade
    on delete cascade,
  constraint trip_locations_lat_range check (lat between -90 and 90),
  constraint trip_locations_lng_range check (lng between -180 and 180)
);

comment on table public.trip_locations is
  'Append-only, throttled rider locations for assigned active trips.';

commit;
