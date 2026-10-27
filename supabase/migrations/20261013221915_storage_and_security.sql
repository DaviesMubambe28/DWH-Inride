begin;

create schema if not exists private;
revoke all on schema private from public;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'rider-documents',
  'rider-documents',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'application/pdf']::text[]
)
on conflict (id) do update
set
  name = excluded.name,
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create or replace function private.hash_trip_pin(p_pin text)
returns text
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if p_pin is null or p_pin !~ '^[0-9]{4}$' then
    raise exception 'trip PIN must contain exactly four digits'
      using errcode = '22023';
  end if;

  return extensions.crypt(p_pin, extensions.gen_salt('bf', 12));
end;
$$;

comment on function private.hash_trip_pin(text) is
  'Returns a salted bcrypt hash for a four-digit trip PIN. Server-only.';

create or replace function private.verify_trip_pin(p_trip_id uuid, p_pin text)
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(
    (
      select
        p_pin ~ '^[0-9]{4}$'
        and t.trip_pin_hash is not null
        and extensions.crypt(p_pin, t.trip_pin_hash) = t.trip_pin_hash
      from public.trips as t
      where t.id = p_trip_id
    ),
    false
  );
$$;

comment on function private.verify_trip_pin(uuid, text) is
  'Checks a trip PIN against its salted hash. Server-only; does not expose the hash.';

revoke all on function private.hash_trip_pin(text) from public, anon, authenticated;
revoke all on function private.verify_trip_pin(uuid, text) from public, anon, authenticated;

grant usage on schema private to service_role;
grant execute on function private.hash_trip_pin(text) to service_role;
grant execute on function private.verify_trip_pin(uuid, text) to service_role;

commit;
